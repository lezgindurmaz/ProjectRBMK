#include "reactor_engine.h"
#include <sstream>
#include <iomanip>
#include <cstring>
#include <algorithm>

namespace rbmk {

static std::string fmtTime(double t) {
    long s=(long)t; char buf[12];
    snprintf(buf,sizeof(buf),"%02ld:%02ld:%02ld",s/3600,(s/60)%60,s%60);
    return buf;
}
static std::string fmtMW(double mw) {
    char buf[32]; snprintf(buf,sizeof(buf),"%.0f MW",mw); return buf;
}
static void setRodsByORM(rbmk::ReactorState& s, double target_orm) {
    double pos = clamp((NUM_CONTROL_RODS - target_orm) / NUM_CONTROL_RODS, 0.0, 1.0);
    s.rod_pos.fill(pos); s.rod_target.fill(pos); s.rod_moving.fill(false);
}

ReactorEngine::ReactorEngine() : rng_(std::random_device{}()) {
    state_.rod_pos.fill(1.0); state_.rod_target.fill(1.0); state_.rod_moving.fill(false);
}

void ReactorEngine::initialize(bool cold_start, int level) {
    memset(state_.mcp_flow,0,sizeof(state_.mcp_flow));
    memset(state_.mcp_speed,0,sizeof(state_.mcp_speed));
    memset(state_.mcp_setpoint,0,sizeof(state_.mcp_setpoint));
    memset(state_.mcp_active,0,sizeof(state_.mcp_active));
    memset(state_.mcp_failed,0,sizeof(state_.mcp_failed));
    state_.current_level=level; state_.simulation_time=0;
    state_.reactor_destroyed=false; state_.mission_failed=false;
    state_.mission_complete=false; state_.mission_score=0;
    state_.active_alarms.clear(); log_buffer_.clear();
    scram_time_=1e9; l_timer1_=l_timer2_=l_timer3_=l_timer4_=0;
    l_flag1_=l_flag2_=l_flag3_=false; l_min_val_=999; l_max_val_=-999;
    if(cold_start) {
        state_.power_frac=0; state_.power_mw=0; state_.neutron_flux=0;
        state_.fuel_temp=20; state_.coolant_temp_in=20; state_.coolant_temp_out=20;
        state_.coolant_pressure=0.1; state_.steam_pressure=0; state_.void_fraction=0;
        state_.iodine=0; state_.xenon=0;
        state_.drum_level[0]=state_.drum_level[1]=50;
        state_.scram_active=false; state_.eccs_active=false; state_.az5_pressed=false;
        state_.coolant_leak=false; state_.leak_rate=0;
        state_.fuel_melt=false; state_.fuel_channel_rupture=false;
        state_.station_blackout=false; state_.diesel_active=false;
        state_.ar_active=true; state_.skala_active=true; state_.az5_blocked=false;
        for(int t=0;t<NUM_TURBINES;t++){
            state_.turbine_speed[t]=state_.turbine_load[t]=0;
            state_.turbine_valve[t]=1; state_.turbine_online[t]=false;
            state_.turbine_trip[t]=state_.turbine_rundown[t]=false;
            state_.turbine_rundown_time[t]=0;
        }
        state_.grid_connected=true; state_.status=ReactorStatus::COLD_SHUTDOWN;
        state_.rod_pos.fill(1.0); state_.rod_target.fill(1.0);
    }
    setLevelConditions(level);
    log("Reaktör motoru başlatıldı. Level "+std::to_string(level),"SİSTEM",0);
}

// ORM = çekilen çubuk sayısı = 211 - sum(rod_pos)
// rod_pos=0.0 → çekilmiş (katkı ORM'a), rod_pos=1.0 → sokulmuş (katkı yok)
int ReactorEngine::calculateORM() const {
    double ins=0;
    for(int i=0;i<NUM_CONTROL_RODS;i++) ins+=state_.rod_pos[i];
    return std::max(0,std::min(NUM_CONTROL_RODS,(int)round(NUM_CONTROL_RODS-ins)));
}

// Reaktivite bileşenleri
// rho=0 → kritik (güç stabil), rho>0 → artan, rho<0 → azalan
double ReactorEngine::calcRodReactivity()     const { return (double)state_.orm*K_ROD_WORTH; }
double ReactorEngine::calcXenonReactivity()   const { return -(state_.xenon*K_XENON); }
double ReactorEngine::calcVoidReactivity()    const { return state_.void_fraction*K_VOID; }
double ReactorEngine::calcDopplerReactivity() const { return (650.0-state_.fuel_temp)*K_DOPPLER; }
double ReactorEngine::calcTotalReactivity()   const {
    return calcRodReactivity()+calcXenonReactivity()+calcVoidReactivity()+calcDopplerReactivity()
           -CRITICAL_ORM_THRESHOLD*K_ROD_WORTH;
}

void ReactorEngine::step(double dt) {
    if(state_.reactor_destroyed) return;
    state_.simulation_time+=dt;
    updateControlRods(dt);
    state_.orm=calculateORM();
    state_.reactivity_rods=calcRodReactivity();
    state_.reactivity_xenon=calcXenonReactivity();
    state_.reactivity_void=calcVoidReactivity();
    state_.reactivity_doppler=calcDopplerReactivity();
    state_.reactivity_total=calcTotalReactivity();
    updateNeutronics(dt);
    updateXenon(dt);
    updateThermalHydraulics(dt);
    updateSteamSystem(dt);
    updatePumps(dt);
    updateTurbines(dt);
    updateSafetySystemsAutomatic(dt);
    updateReactorStatus();
    checkAndUpdateAlarms();
    checkMissionConditions(state_.current_level,dt);
}

void ReactorEngine::updateControlRods(double dt) {
    if(state_.scram_active) {
        bool all_in=true;
        for(int i=0;i<NUM_CONTROL_RODS;i++) {
            if(state_.rod_pos[i] < 0.99) {
                state_.rod_pos[i]=std::min(1.0,state_.rod_pos[i]+(1.0/18.0)*dt);
                state_.rod_moving[i]=true; all_in=false;
            } else { state_.rod_pos[i]=1.0; state_.rod_moving[i]=false; }
        }
        if(all_in) { log("Tüm çubuklar sokuldu.","SİSTEM",1); state_.scram_active=false; }
        return;
    }
    const double step=0.008*dt;
    for(int i=0;i<NUM_CONTROL_RODS;i++) {
        double diff=state_.rod_target[i]-state_.rod_pos[i];
        if(fabs(diff)>0.001) {
            state_.rod_pos[i]=clamp(state_.rod_pos[i]+copysign(std::min(step,fabs(diff)),diff),0.0,1.0);
            state_.rod_moving[i]=true;
        } else state_.rod_moving[i]=false;
    }
}

void ReactorEngine::updateNeutronics(double dt) {
    double rho=state_.reactivity_total;
    // AZ-5 grafit uç etkisi: basımdan sonra ilk 3 saniye geçici pozitif
    if(state_.az5_pressed && state_.simulation_time < scram_time_+3.0) {
        double ts=state_.simulation_time-scram_time_;
        if(ts>0 && ts<1.5) rho+=0.5*(1.0-ts/1.5);
    }
    double P=state_.power_frac;
    double P_prev=P;
    const double Pmin=1e-8;
    double dP=(rho/POWER_TAU)*std::max(P,Pmin)+Pmin*0.01;
    P=std::max(0.0,P+dP*dt);
    state_.power_frac=P; state_.neutron_flux=P;
    state_.power_mw=P*RATED_POWER_MW;
    state_.power_rate_mw_s=(state_.power_mw-P_prev*RATED_POWER_MW)/dt;
    state_.precursor=P;
    if(state_.power_mw>EXPLOSION_POWER && !state_.reactor_destroyed) {
        state_.reactor_destroyed=true;
        state_.mission_failed=(state_.current_level!=16);
        state_.status=ReactorStatus::EXPLOSION;
        state_.failure_reason="REAKTÖR PATLAMASI! Güç "+fmtMW(state_.power_mw);
        addAlarm(AlarmType::REACTOR_EXPLOSION,AlarmSeverity::FATAL,"PATLAMA","Reaktör patladı!");
        log("!!! NÜKLEER PATLAMA !!! "+fmtMW(state_.power_mw),"FATAL",3);
    } else if(state_.power_mw>MELTDOWN_POWER && !state_.fuel_melt) {
        state_.fuel_melt=true;
        addAlarm(AlarmType::HIGH_FUEL_TEMP,AlarmSeverity::FATAL,"ERİME","Yakıt erimesi!");
        log("Yakıt erimesi! "+fmtMW(state_.power_mw),"ACİL",3);
    }
}

void ReactorEngine::updateXenon(double dt) {
    double P=state_.power_frac;
    double lI=LAMBDA_I*XENON_SPEED, lXe=LAMBDA_XE*XENON_SPEED;
    state_.iodine=clamp(state_.iodine+(P-state_.iodine)*lI*dt,0.0,3.0);
    double xe_prod=state_.iodine*lI;
    double xe_loss=state_.xenon*(lXe+XE_BURNOUT*P);
    state_.xenon=clamp(state_.xenon+(xe_prod-xe_loss)*dt,0.0,3.0);
}

void ReactorEngine::updateThermalHydraulics(double dt) {
    double P=state_.power_frac;
    double total_flow=0;
    for(int i=0;i<NUM_MCP;i++) if(!state_.mcp_failed[i]) total_flow+=state_.mcp_flow[i];
    state_.coolant_flow_total=total_flow;
    if(state_.coolant_leak) {
        total_flow=std::max(0.0,total_flow-state_.leak_rate*0.5);
        state_.coolant_pressure=std::max(0.1,state_.coolant_pressure-state_.leak_rate*0.0005*dt);
    }
    if(total_flow>10.0) {
        double dT=P*RATED_POWER_MW*1e6/(total_flow*4200.0);
        state_.coolant_temp_out+=((state_.coolant_temp_in+dT)-state_.coolant_temp_out)*dt/8.0;
    }
    state_.coolant_temp_out=clamp(state_.coolant_temp_out,state_.coolant_temp_in,400.0);
    state_.fuel_temp+=(state_.coolant_temp_out+P*800.0-state_.fuel_temp)*dt/5.0;
    double T_sat=184.0+16.0*state_.coolant_pressure;
    double sh=state_.coolant_temp_out-T_sat;
    double vt=(sh>0)?std::min(0.80,sh/30.0*0.3):0.0;
    if(total_flow<FEEDWATER_NOM*0.5 && P>0.05)
        vt=std::min(0.95,vt+(1.0-total_flow/(FEEDWATER_NOM*0.5))*0.4);
    state_.void_fraction=clamp(state_.void_fraction+(vt-state_.void_fraction)*dt/3.0,0.0,0.99);
    double press_t=std::max(0.1,P*PRESSURE_NOM);
    state_.coolant_pressure+=( press_t-state_.coolant_pressure)*dt/15.0;
}

void ReactorEngine::updateSteamSystem(double dt) {
    double P=state_.power_frac;
    state_.feedwater_flow+=(state_.feedwater_setpoint-state_.feedwater_flow)*dt/8.0;
    double sc=0;
    for(int t=0;t<NUM_TURBINES;t++) if(state_.turbine_online[t]) sc+=state_.turbine_valve[t]*FEEDWATER_NOM*0.5;
    double net=state_.feedwater_flow-sc;
    double dc=net/(80.0*NUM_DRUMS)*dt;
    for(int i=0;i<NUM_DRUMS;i++) state_.drum_level[i]=clamp(state_.drum_level[i]+dc+(P-0.5)*0.06*dt,0.0,100.0);
    bool tc=state_.turbine_online[0]||state_.turbine_online[1];
    double sp=P*STEAM_PRESS_NOM;
    if(!tc && P>0.05) sp=std::min(sp*1.3,MAX_PRESSURE-0.3);
    state_.steam_pressure=clamp(state_.steam_pressure+(sp-state_.steam_pressure)*dt/12.0,0.0,MAX_PRESSURE+0.5);
    state_.steam_flow=P*FEEDWATER_NOM;
}

void ReactorEngine::updatePumps(double dt) {
    for(int i=0;i<NUM_MCP;i++) {
        if(state_.mcp_failed[i]) {
            state_.mcp_flow[i]=std::max(0.0,state_.mcp_flow[i]-60.0*dt);
            state_.mcp_speed[i]=std::max(0.0,state_.mcp_speed[i]-100.0*dt);
            state_.mcp_active[i]=(state_.mcp_speed[i]>10.0);
        } else if(state_.mcp_active[i]) {
            if(state_.station_blackout && !state_.diesel_active) {
                double d=state_.turbine_rundown[0]?15.0:80.0;
                state_.mcp_flow[i]=std::max(0.0,state_.mcp_flow[i]-d*dt);
                state_.mcp_speed[i]=std::max(0.0,state_.mcp_speed[i]-d*1.2*dt);
                if(state_.mcp_speed[i]<5.0) state_.mcp_active[i]=false;
            } else {
                state_.mcp_flow[i]+=(state_.mcp_setpoint[i]-state_.mcp_flow[i])*dt/5.0;
                state_.mcp_speed[i]+=(1000.0*state_.mcp_setpoint[i]/MCP_FLOW_NOM-state_.mcp_speed[i])*dt/5.0;
            }
        } else {
            state_.mcp_flow[i]=std::max(0.0,state_.mcp_flow[i]-120.0*dt);
            state_.mcp_speed[i]=std::max(0.0,state_.mcp_speed[i]-150.0*dt);
        }
    }
}

void ReactorEngine::updateTurbines(double dt) {
    for(int t=0;t<NUM_TURBINES;t++) {
        if(state_.turbine_trip[t]) {
            state_.turbine_speed[t]=std::max(0.0,state_.turbine_speed[t]-250.0*dt);
            state_.turbine_load[t]=std::max(0.0,state_.turbine_load[t]-120.0*dt);
            state_.turbine_online[t]=(state_.turbine_speed[t]>30.0);
        } else if(state_.turbine_rundown[t]) {
            state_.turbine_rundown_time[t]+=dt;
            state_.turbine_speed[t]=std::max(0.0,state_.turbine_speed[t]*(1.0-dt/70.0));
            state_.turbine_load[t]=std::max(0.0,state_.turbine_load[t]*(1.0-dt/70.0));
        } else if(state_.turbine_online[t]) {
            double sok=(state_.steam_pressure>2.5)?1.0:state_.steam_pressure/2.5;
            state_.turbine_speed[t]+=(3000.0-state_.turbine_speed[t])*dt/10.0;
            state_.turbine_load[t]+=(RATED_ELEC_MW*0.5*state_.turbine_valve[t]*sok-state_.turbine_load[t])*dt/6.0;
        }
    }
}

void ReactorEngine::updateSafetySystemsAutomatic(double dt) {
    if(!state_.scram_active) {
        if(state_.power_frac>1.15) triggerScram("Otomatik SCRAM: Güç %115 limitini aştı!");
        else if(state_.steam_pressure>8.5) triggerScram("Otomatik SCRAM: Buhar basıncı kritik!");
        else if(state_.drum_level[0]<8.0 && state_.drum_level[1]<8.0)
            triggerScram("Otomatik SCRAM: Tambur seviyeleri kritik!");
    }
    if(!state_.eccs_active && state_.coolant_pressure<4.0 && state_.power_frac>0.01)
        activateECCS("Basınç <4 MPa — acil soğutma devreye alındı!");
}

void ReactorEngine::updateReactorStatus() {
    if(state_.reactor_destroyed){state_.status=ReactorStatus::EXPLOSION;return;}
    if(state_.fuel_melt)        {state_.status=ReactorStatus::MELTDOWN;return;}
    if(state_.scram_active)     {state_.status=ReactorStatus::SCRAM;return;}
    double P=state_.power_frac;
    if(P<0.001)     state_.status=ReactorStatus::COLD_SHUTDOWN;
    else if(P<0.05) state_.status=ReactorStatus::HOT_SHUTDOWN;
    else if(P<0.10) state_.status=ReactorStatus::STARTUP;
    else if(P<0.30) state_.status=ReactorStatus::LOW_POWER;
    else if(P<0.93) state_.status=ReactorStatus::POWER_ASCENT;
    else if(P<=1.07)state_.status=ReactorStatus::FULL_POWER;
    else            state_.status=ReactorStatus::TRANSIENT;
}

void ReactorEngine::checkAndUpdateAlarms() {
    double P=state_.power_frac; int orm=state_.orm;
    auto add=[&](AlarmType t,AlarmSeverity s,const std::string& c,const std::string& m){
        for(auto& a:state_.active_alarms){if(a.type==t){a.active=true;return;}}
        addAlarm(t,s,c,m);
    };
    auto rem=[&](AlarmType t){ removeAlarm(t); };

    if(P<0.05&&P>0.001) add(AlarmType::LOW_POWER,AlarmSeverity::WARNING,"DÜŞÜK GÜÇ","Güç %5 altında!");
    else rem(AlarmType::LOW_POWER);
    if(P>1.07) add(AlarmType::HIGH_POWER,AlarmSeverity::ALERT,"YÜKSEK GÜÇ","Güç %107 üzerinde!");
    else rem(AlarmType::HIGH_POWER);
    if(orm<MIN_ORM&&orm>=CRITICAL_ORM) add(AlarmType::LOW_ORM,AlarmSeverity::WARNING,"DÜŞÜK ORM","ORM < 15 — Minimum marj!");
    else rem(AlarmType::LOW_ORM);
    if(orm<CRITICAL_ORM) add(AlarmType::CRITICAL_ORM,AlarmSeverity::EMERGENCY,"KRİTİK ORM","ORM < 7 — Acil müdahale!");
    else rem(AlarmType::CRITICAL_ORM);
    if(state_.steam_pressure>MAX_PRESSURE-0.5) add(AlarmType::HIGH_STEAM_PRESS,AlarmSeverity::ALERT,"YÜKSEK BASINÇ","Buhar basıncı kritik!");
    else rem(AlarmType::HIGH_STEAM_PRESS);
    if(state_.steam_pressure<MIN_PRESSURE&&P>0.1) add(AlarmType::LOW_STEAM_PRESS,AlarmSeverity::WARNING,"DÜŞÜK BASINÇ","Buhar basıncı düşük!");
    else rem(AlarmType::LOW_STEAM_PRESS);
    if(state_.coolant_temp_out>310.0) add(AlarmType::HIGH_COOLANT_TEMP,AlarmSeverity::ALERT,"YÜKSEK SICAKLIK","Soğutucu sıcaklığı yüksek!");
    else rem(AlarmType::HIGH_COOLANT_TEMP);
    bool dl=false,dh=false;
    for(int i=0;i<NUM_DRUMS;i++){if(state_.drum_level[i]<10)dl=true;if(state_.drum_level[i]>90)dh=true;}
    if(dl) add(AlarmType::LOW_DRUM_LEVEL,AlarmSeverity::ALERT,"DÜŞÜK TAMBUR","Tambur seviyesi kritik!");
    else rem(AlarmType::LOW_DRUM_LEVEL);
    if(dh) add(AlarmType::HIGH_DRUM_LEVEL,AlarmSeverity::WARNING,"YÜKSEK TAMBUR","Tambur taşma riski!");
    else rem(AlarmType::HIGH_DRUM_LEVEL);
    for(int i=0;i<NUM_MCP;i++) if(state_.mcp_failed[i])
        add(AlarmType::MCP_TRIP,AlarmSeverity::EMERGENCY,"POMPA ARIZASI","Pompa-"+std::to_string(i+1)+" arızalı!");
    if(state_.reactivity_void>0.5&&P>0.01) add(AlarmType::POSITIVE_FEEDBACK,AlarmSeverity::EMERGENCY,"POZİTİF GERİ BESLEME","Void reaktivitesi tehlikeli!");
    else rem(AlarmType::POSITIVE_FEEDBACK);
    if(state_.fuel_temp>1100.0) add(AlarmType::HIGH_FUEL_TEMP,AlarmSeverity::EMERGENCY,"YÜKSEK YAKIT T.","Yakıt sıcaklığı kritik!");
    else rem(AlarmType::HIGH_FUEL_TEMP);
    if(state_.eccs_active) add(AlarmType::ECCS_ACTIVE,AlarmSeverity::ALERT,"SAOR AKTİF","Acil Soğutma Sistemi çalışıyor!");
    if(state_.coolant_leak) add(AlarmType::COOLANT_LEAK,AlarmSeverity::EMERGENCY,"SIZINTI","Soğutucu sızıntısı!");
    if(state_.xenon>1.7&&P<0.05) add(AlarmType::XENON_TRAP,AlarmSeverity::ALERT,"XENON TUZAĞI","Xenon yüksek — yeniden başlatma zor!");
    else rem(AlarmType::XENON_TRAP);
    if(state_.station_blackout) add(AlarmType::MCP_LOW_FLOW,AlarmSeverity::EMERGENCY,"ŞEBEKE KAYBI","Harici şebeke kesildi!");
}

void ReactorEngine::checkMissionConditions(int level, double dt) {
    if(state_.mission_failed||state_.mission_complete) return;
    if(state_.reactor_destroyed&&level!=16){state_.mission_failed=true;state_.failure_reason="Reaktör patladı!";return;}
    if(state_.fuel_melt){state_.mission_failed=true;state_.failure_reason="Yakıt erimesi gerçekleşti!";return;}
    double T=state_.simulation_time, P=state_.power_frac;

    switch(level) {
    case 1:
        if(T>60.0){state_.mission_complete=true;state_.mission_score=90.0;
            log("GÖREV TAMAMLANDI — Oryantasyon başarılı!","SİSTEM",0);}
        break;
    case 2:
        if(P>=0.04&&P<=0.08&&state_.orm>=10){l_timer1_+=dt;
            if(l_timer1_>=90.0){state_.mission_complete=true;state_.mission_score=std::max(60.0,100.0-state_.active_alarms.size()*10.0);
                log("GÖREV TAMAMLANDI — Soğuk başlatma başarılı!","SİSTEM",0);}}
        else if(P<0.04||P>0.08) l_timer1_=0;
        if(state_.orm<7&&state_.orm>0){state_.mission_failed=true;state_.failure_reason="ORM 7'nin altına düştü!";}
        if(P>0.15){state_.mission_failed=true;state_.failure_reason="Güç kontrolsüz arttı!";}
        break;
    case 3:
        if(P>=0.47&&P<=0.55&&state_.orm>=15){l_timer1_+=dt;
            if(l_timer1_>=120.0){state_.mission_complete=true;state_.mission_score=std::max(50.0,100.0-state_.active_alarms.size()*8.0);
                log("GÖREV TAMAMLANDI — Güç artırma başarılı!","SİSTEM",0);}}
        else if(P<0.43||P>0.57) l_timer1_=0;
        if(state_.orm<12){state_.mission_failed=true;state_.failure_reason="ORM 12'nin altına düştü!";}
        break;
    case 4:
        if(P>=0.95&&P<=1.07&&state_.orm>=15){l_timer1_+=dt;
            if(l_timer1_>=180.0){state_.mission_complete=true;state_.mission_score=std::max(60.0,100.0-state_.active_alarms.size()*5.0);
                log("GÖREV TAMAMLANDI — Tam güç vardiyası başarılı!","SİSTEM",0);}}
        else if(P<0.90||P>1.10) l_timer1_=0;
        if(state_.orm<10){state_.mission_failed=true;state_.failure_reason="ORM 10'un altına düştü!";}
        break;
    case 5:
        if(!l_flag1_&&T>30.0){triggerMCPFailure(2);l_flag1_=true;log("ARIZA: Pompa-3 trip!","ACİL",3);}
        if(l_flag1_){
            if(P>=0.50&&P<=0.85&&state_.coolant_temp_out<320.0){l_timer1_+=dt;
                if(l_timer1_>=120.0){state_.mission_complete=true;state_.mission_score=90.0-state_.active_alarms.size()*5.0;
                    log("GÖREV TAMAMLANDI — Pompa arızası yönetildi!","SİSTEM",0);}}
            else l_timer1_=0;
        }
        if(state_.coolant_temp_out>330.0){state_.mission_failed=true;state_.failure_reason="Soğutucu sıcaklığı 330°C'yi aştı!";}
        break;
    case 6:
        if(!l_flag1_&&P>=0.68&&P<=0.82){l_flag1_=true;log("Güç %75 bölgesine girdi. Türbin-2'yi ayırın.","SİSTEM",0);}
        if(l_flag1_&&!l_flag2_&&!state_.turbine_online[1]&&!state_.turbine_trip[1]){
            l_flag2_=true;l_timer2_=0;log("Türbin-2 coast-down başladı.","SİSTEM",0);}
        if(l_flag2_){l_timer2_+=dt;if(l_timer2_>=45.0&&!l_flag3_){l_flag3_=true;log("Coast-down 45s tamamlandı. Gücü artırın.","SİSTEM",0);}}
        if(l_flag3_&&P>=0.93){l_timer1_+=dt;
            if(l_timer1_>=60.0){state_.mission_complete=true;state_.mission_score=85.0;log("GÖREV TAMAMLANDI!","SİSTEM",0);}}
        if(state_.orm<15){state_.mission_failed=true;state_.failure_reason="ORM 15'in altına düştü!";}
        break;
    case 7:
        if(state_.xenon>1.5&&P>=0.28){l_timer1_+=dt;
            if(l_timer1_>=60.0){state_.mission_complete=true;state_.mission_score=80.0;log("GÖREV TAMAMLANDI!","SİSTEM",0);}}
        else if(P<0.25) l_timer1_=0;
        if(state_.orm<8){state_.mission_failed=true;state_.failure_reason="ORM 8'in altına düştü!";}
        break;
    case 8: {
        double dl1=state_.drum_level[0],dl2=state_.drum_level[1];
        bool dok=dl1>18&&dl1<82&&dl2>18&&dl2<82;
        if(!dok){l_timer1_=0;l_flag1_=false;}
        if(!l_flag1_&&P<0.43){l_flag1_=true;log("Güç %40'a indi. %100'e çıkarın.","SİSTEM",0);}
        if(l_flag1_&&P>=0.93&&dok){l_timer1_+=dt;
            if(l_timer1_>=90.0){state_.mission_complete=true;state_.mission_score=85.0;log("GÖREV TAMAMLANDI!","SİSTEM",0);}}
        if((dl1<5||dl1>95||dl2<5||dl2>95)&&T>30){state_.mission_failed=true;state_.failure_reason="Tambur seviyesi kritik sınırı aştı!";}
        break; }
    case 9:
        if(!state_.ar_active&&P>=0.77&&P<=0.83&&state_.orm>=12){l_timer1_+=dt;
            if(l_timer1_>=300.0){state_.mission_complete=true;state_.mission_score=90.0;log("GÖREV TAMAMLANDI!","SİSTEM",0);}}
        else if(P<0.72||P>0.88) l_timer1_=0;
        if(state_.ar_active){state_.mission_failed=true;state_.failure_reason="AR sistemi açıldı! Manuel kontrol leveli.";}
        if(state_.orm<10){state_.mission_failed=true;state_.failure_reason="ORM 10'un altına düştü!";}
        break;
    case 10:
        if(!l_flag1_&&T>20.0){triggerStationBlackout();l_flag1_=true;l_timer1_=T;}
        if(l_flag1_){
            if(!l_flag2_&&state_.diesel_active&&state_.az5_pressed){l_flag2_=true;l_timer2_=0;log("Dizel+SCRAM tamam.","SİSTEM",1);}
            if(l_flag2_){l_timer2_+=dt;if(l_timer2_>=300.0&&state_.coolant_flow_total>1000.0){state_.mission_complete=true;state_.mission_score=80.0;log("GÖREV TAMAMLANDI!","SİSTEM",0);}}
            if(!l_flag2_&&T-l_timer1_>120.0){state_.mission_failed=true;state_.failure_reason="120 saniyede dizel başlatılamadı!";}
        }
        if(state_.coolant_flow_total<300.0&&l_flag1_&&T-l_timer1_>60.0){state_.mission_failed=true;state_.failure_reason="Soğutucu akışı kesildi!";}
        break;
    case 11:
        if(state_.xenon>1.4&&P>=0.50&&P<=0.87&&state_.orm>=8){l_timer1_+=dt;
            if(l_timer1_>=300.0){state_.mission_complete=true;state_.mission_score=85.0;log("GÖREV TAMAMLANDI!","SİSTEM",0);}}
        else if(P<0.45) l_timer1_=0;
        if(state_.orm<7){state_.mission_failed=true;state_.failure_reason="ORM 7'nin altına düştü!";}
        break;
    case 12:
        l_max_val_=std::max(l_max_val_,state_.void_fraction*100.0);
        if(!l_flag1_&&l_max_val_>25.0){l_flag1_=true;log("Void %25'i geçti! Krizi çözün.","ACİL",2);}
        if(l_flag1_&&state_.void_fraction*100.0<15.0){l_timer1_+=dt;
            if(l_timer1_>=300.0){state_.mission_complete=true;state_.mission_score=80.0;log("GÖREV TAMAMLANDI!","SİSTEM",0);}}
        else if(state_.void_fraction*100.0>=15.0) l_timer1_=0;
        if(state_.void_fraction>0.65){state_.mission_failed=true;state_.failure_reason="Void %65'i geçti!";}
        break;
    case 13:
        if(!l_flag1_&&T>5.0){triggerMCPFailure(1);l_flag1_=true;log("ARIZA: Pompa-2!","ACİL",3);}
        if(!l_flag2_&&T>45.0){state_.feedwater_setpoint=FEEDWATER_NOM*0.5;l_flag2_=true;log("ARIZA: Besleme suyu düştü!","ACİL",2);}
        if(!l_flag3_&&T>90.0){state_.turbine_trip[0]=true;state_.turbine_online[0]=false;l_flag3_=true;log("ARIZA: Türbin-1 trip!","ACİL",3);}
        if(l_flag3_){
            bool ok=P>=0.40&&P<=0.95&&state_.coolant_temp_out<310.0&&state_.orm>=12&&state_.drum_level[0]>15.0&&state_.drum_level[1]>15.0;
            if(ok){l_timer1_+=dt;if(l_timer1_>=180.0){state_.mission_complete=true;state_.mission_score=75.0;log("GÖREV TAMAMLANDI!","SİSTEM",0);}}
            else l_timer1_=0;
        }
        if(state_.orm<8&&l_flag3_){state_.mission_failed=true;state_.failure_reason="ORM 8'in altına düştü!";}
        break;
    case 14:
        if(!state_.skala_active&&P>=0.80&&P<=0.97&&state_.orm>=12){l_timer1_+=dt;
            if(l_timer1_>=1800.0){state_.mission_complete=true;state_.mission_score=85.0;log("GÖREV TAMAMLANDI! 30 dakika SKALA'sız!","SİSTEM",0);}}
        else if(P<0.75||P>1.02) l_timer1_=0;
        if(state_.orm<10){state_.mission_failed=true;state_.failure_reason="ORM 10'un altına düştü!";}
        break;
    case 15:
        if(!l_flag1_&&T>15.0){triggerFuelChannelRupture();l_flag1_=true;}
        if(l_flag1_){
            if(!l_flag2_&&state_.eccs_active&&state_.scram_active){l_flag2_=true;l_timer2_=0;log("SCRAM+SAOR aktif. Soğutma başladı.","SİSTEM",1);}
            if(l_flag2_){l_timer2_+=dt;if(l_timer2_>=300.0&&state_.fuel_temp<800.0){state_.mission_complete=true;state_.mission_score=80.0;log("GÖREV TAMAMLANDI!","SİSTEM",0);}}
            if(state_.fuel_temp>1200.0){state_.mission_failed=true;state_.failure_reason="Yakıt 1200°C'yi aştı!";}
        }
        break;
    case 16:
        triggerLevel16Sequence(T,dt);
        break;
    default: break;
    }
}

void ReactorEngine::setupLevel1() {
    state_.rod_pos.fill(1.0);state_.rod_target.fill(1.0);
    state_.power_frac=0;state_.xenon=0;state_.iodine=0;
    state_.coolant_temp_in=20;state_.coolant_temp_out=20;state_.coolant_pressure=0.1;state_.steam_pressure=0;
    state_.status=ReactorStatus::COLD_SHUTDOWN;
    log("Level 1: Oryantasyon. 60 saniye geçince tamamlanır.","SİSTEM",0);
}
void ReactorEngine::setupLevel2() {
    state_.rod_pos.fill(1.0);state_.rod_target.fill(1.0);
    state_.power_frac=0;state_.xenon=0;state_.iodine=0;state_.fuel_temp=20;
    state_.coolant_temp_in=20;state_.coolant_temp_out=20;state_.coolant_pressure=0.1;state_.steam_pressure=0;
    state_.drum_level[0]=state_.drum_level[1]=50;state_.feedwater_setpoint=FEEDWATER_NOM*0.1;
    state_.status=ReactorStatus::COLD_SHUTDOWN;
    log("Level 2: Pompaları başlatın, çubukları çekin, %5 güce ulaşın.","SİSTEM",0);
}
void ReactorEngine::setupLevel3() {
    setRodsByORM(state_,13.0);state_.power_frac=0.05;state_.neutron_flux=0.05;
    state_.xenon=0.10;state_.iodine=0.08;state_.fuel_temp=60;
    state_.coolant_temp_in=230;state_.coolant_temp_out=242;state_.coolant_pressure=6.9;state_.steam_pressure=5.8;state_.void_fraction=0.01;
    state_.drum_level[0]=state_.drum_level[1]=50;
    for(int i=0;i<4;i++){state_.mcp_active[i]=true;state_.mcp_setpoint[i]=MCP_FLOW_NOM*0.5;state_.mcp_flow[i]=MCP_FLOW_NOM*0.5;state_.mcp_speed[i]=900;}
    state_.feedwater_setpoint=FEEDWATER_NOM*0.25;state_.ar_active=true;state_.status=ReactorStatus::STARTUP;
    log("Level 3: %5 güçten. Gücü %50'ye çıkarın.","SİSTEM",0);
}
void ReactorEngine::setupLevel4() {
    setRodsByORM(state_,22.0);state_.power_frac=0.50;state_.neutron_flux=0.50;
    state_.xenon=0.50;state_.iodine=0.45;state_.fuel_temp=420;
    state_.coolant_temp_in=COOLANT_IN_NOM;state_.coolant_temp_out=275;state_.coolant_pressure=PRESSURE_NOM;state_.steam_pressure=STEAM_PRESS_NOM*0.90;state_.void_fraction=0.04;
    state_.drum_level[0]=state_.drum_level[1]=50;
    for(int i=0;i<6;i++){state_.mcp_active[i]=true;state_.mcp_setpoint[i]=MCP_FLOW_NOM*0.72;state_.mcp_flow[i]=MCP_FLOW_NOM*0.72;state_.mcp_speed[i]=975;}
    state_.feedwater_setpoint=FEEDWATER_NOM*0.70;
    state_.turbine_online[0]=true;state_.turbine_speed[0]=3000;state_.turbine_load[0]=350;state_.turbine_valve[0]=0.85;
    state_.ar_active=true;state_.status=ReactorStatus::POWER_ASCENT;
    log("Level 4: %50 güç. %100'e çıkar ve 3 dakika tut.","SİSTEM",0);
}
static void setupFullPower(rbmk::ReactorState& s) {
    setRodsByORM(s,30.0);s.power_frac=1.00;s.neutron_flux=1.0;
    s.xenon=1.0;s.iodine=1.0;s.fuel_temp=650;
    s.coolant_temp_in=rbmk::COOLANT_IN_NOM;s.coolant_temp_out=rbmk::COOLANT_OUT_NOM;
    s.coolant_pressure=rbmk::PRESSURE_NOM;s.steam_pressure=rbmk::STEAM_PRESS_NOM;s.void_fraction=0.08;
    s.drum_level[0]=s.drum_level[1]=50;
    for(int i=0;i<rbmk::NUM_MCP;i++){s.mcp_active[i]=true;s.mcp_setpoint[i]=rbmk::MCP_FLOW_NOM;s.mcp_flow[i]=rbmk::MCP_FLOW_NOM;s.mcp_speed[i]=1000;}
    s.feedwater_setpoint=rbmk::FEEDWATER_NOM;
    for(int t=0;t<rbmk::NUM_TURBINES;t++){s.turbine_online[t]=true;s.turbine_speed[t]=3000;s.turbine_load[t]=500;s.turbine_valve[t]=1.0;}
    s.ar_active=true;s.status=rbmk::ReactorStatus::FULL_POWER;
}
void ReactorEngine::setupLevel5()  { setupFullPower(state_); log("Level 5: 30s sonra pompa arızası!","SİSTEM",1); }
void ReactorEngine::setupLevel6()  { setupFullPower(state_); log("Level 6: Gücü %75'e düşür, türbin-2 testini yap.","SİSTEM",0); }
void ReactorEngine::setupLevel8()  { setupFullPower(state_); log("Level 8: Yük değişimi sırasında tambur seviyelerini yönet.","SİSTEM",0); }
void ReactorEngine::setupLevel10() { setupFullPower(state_); state_.grid_connected=true;state_.station_blackout=false;state_.diesel_active=false; log("Level 10: 20s sonra şebeke kaybı!","SİSTEM",1); }
void ReactorEngine::setupLevel13() { setupFullPower(state_); log("Level 13: Sıralı arızalara hazır ol!","SİSTEM",2); }
void ReactorEngine::setupLevel15() { setupFullPower(state_); log("Level 15: 15s sonra yakıt kanalı patlaması!","SİSTEM",2); }
void ReactorEngine::setupLevel16() { setupFullPower(state_); state_.az5_blocked=false;scram_time_=1e9; log("Level 16: 26 Nisan Gece Testi. Scripted son.","SİSTEM",2); }

void ReactorEngine::setupLevel7() {
    state_.rod_pos.fill(1.0);state_.rod_target.fill(1.0);
    state_.power_frac=0;state_.xenon=1.85;state_.iodine=0.65;state_.fuel_temp=265;
    state_.coolant_temp_in=COOLANT_IN_NOM;state_.coolant_temp_out=COOLANT_IN_NOM+3;state_.coolant_pressure=PRESSURE_NOM;state_.steam_pressure=5.0;state_.void_fraction=0.005;
    state_.drum_level[0]=state_.drum_level[1]=50;
    for(int i=0;i<4;i++){state_.mcp_active[i]=true;state_.mcp_setpoint[i]=MCP_FLOW_NOM*0.4;state_.mcp_flow[i]=MCP_FLOW_NOM*0.4;state_.mcp_speed[i]=680;}
    state_.feedwater_setpoint=FEEDWATER_NOM*0.20;state_.scram_active=false;state_.az5_pressed=false;state_.ar_active=true;state_.status=ReactorStatus::HOT_SHUTDOWN;
    log("Level 7: Xenon 1.85 — dikkatli başlat!","SİSTEM",2);
}
void ReactorEngine::setupLevel9() {
    setRodsByORM(state_,25.0);state_.power_frac=0.80;state_.neutron_flux=0.80;
    state_.xenon=0.90;state_.iodine=0.85;state_.fuel_temp=560;
    state_.coolant_temp_in=COOLANT_IN_NOM;state_.coolant_temp_out=280;state_.coolant_pressure=PRESSURE_NOM;state_.steam_pressure=STEAM_PRESS_NOM*0.95;state_.void_fraction=0.06;
    state_.drum_level[0]=state_.drum_level[1]=50;
    for(int i=0;i<8;i++){state_.mcp_active[i]=true;state_.mcp_setpoint[i]=MCP_FLOW_NOM*0.85;state_.mcp_flow[i]=MCP_FLOW_NOM*0.85;state_.mcp_speed[i]=990;}
    state_.feedwater_setpoint=FEEDWATER_NOM*0.85;
    for(int t=0;t<NUM_TURBINES;t++){state_.turbine_online[t]=true;state_.turbine_speed[t]=3000;state_.turbine_load[t]=440;state_.turbine_valve[t]=0.90;}
    state_.ar_active=false;state_.status=ReactorStatus::FULL_POWER;
    log("Level 9: AR kapalı! Manuel kontrol — %80 güçte 5 dakika.","SİSTEM",1);
}
void ReactorEngine::setupLevel11() {
    setRodsByORM(state_,24.0);state_.power_frac=0.70;state_.neutron_flux=0.70;
    state_.xenon=1.40;state_.iodine=0.85;state_.fuel_temp=500;
    state_.coolant_temp_in=COOLANT_IN_NOM;state_.coolant_temp_out=279;state_.coolant_pressure=PRESSURE_NOM;state_.steam_pressure=STEAM_PRESS_NOM*0.95;state_.void_fraction=0.05;
    state_.drum_level[0]=state_.drum_level[1]=50;
    for(int i=0;i<6;i++){state_.mcp_active[i]=true;state_.mcp_setpoint[i]=MCP_FLOW_NOM*0.80;state_.mcp_flow[i]=MCP_FLOW_NOM*0.80;state_.mcp_speed[i]=985;}
    for(int t=0;t<NUM_TURBINES;t++){state_.turbine_online[t]=true;state_.turbine_speed[t]=3000;state_.turbine_load[t]=380;state_.turbine_valve[t]=0.80;}
    state_.feedwater_setpoint=FEEDWATER_NOM*0.80;state_.ar_active=true;state_.status=ReactorStatus::FULL_POWER;
    log("Level 11: Xenon 1.40 ve yükseliyor!","SİSTEM",2);
}
void ReactorEngine::setupLevel12() {
    setRodsByORM(state_,21.0);state_.power_frac=0.50;state_.neutron_flux=0.50;
    state_.xenon=0.65;state_.iodine=0.60;state_.fuel_temp=420;
    state_.coolant_temp_in=COOLANT_IN_NOM;state_.coolant_temp_out=275;state_.coolant_pressure=PRESSURE_NOM;state_.steam_pressure=STEAM_PRESS_NOM*0.90;state_.void_fraction=0.08;
    state_.drum_level[0]=state_.drum_level[1]=50;
    for(int i=0;i<5;i++){state_.mcp_active[i]=true;state_.mcp_setpoint[i]=MCP_FLOW_NOM*0.62;state_.mcp_flow[i]=MCP_FLOW_NOM*0.50;state_.mcp_speed[i]=920;}
    state_.feedwater_setpoint=FEEDWATER_NOM*0.65;
    state_.turbine_online[0]=true;state_.turbine_speed[0]=3000;state_.turbine_load[0]=420;state_.turbine_valve[0]=0.90;
    state_.ar_active=true;state_.status=ReactorStatus::POWER_ASCENT;
    log("Level 12: Void yüksek, akış düşük — pozitif geri beslemeyi kır!","SİSTEM",2);
}
void ReactorEngine::setupLevel14() {
    setRodsByORM(state_,28.0);state_.power_frac=0.90;state_.neutron_flux=0.90;
    state_.xenon=0.98;state_.iodine=0.94;state_.fuel_temp=620;
    state_.coolant_temp_in=COOLANT_IN_NOM;state_.coolant_temp_out=282;state_.coolant_pressure=PRESSURE_NOM;state_.steam_pressure=STEAM_PRESS_NOM;state_.void_fraction=0.07;
    state_.drum_level[0]=state_.drum_level[1]=50;
    for(int i=0;i<8;i++){state_.mcp_active[i]=true;state_.mcp_setpoint[i]=MCP_FLOW_NOM*0.92;state_.mcp_flow[i]=MCP_FLOW_NOM*0.92;state_.mcp_speed[i]=995;}
    for(int t=0;t<NUM_TURBINES;t++){state_.turbine_online[t]=true;state_.turbine_speed[t]=3000;state_.turbine_load[t]=470;state_.turbine_valve[t]=0.95;}
    state_.feedwater_setpoint=FEEDWATER_NOM*0.92;state_.skala_active=false;state_.ar_active=true;state_.status=ReactorStatus::FULL_POWER;
    log("Level 14: SKALA arızalı! 30 dakika analog göstergelerle idare et.","SİSTEM",2);
}

void ReactorEngine::setLevelConditions(int level) {
    state_.current_level=level;
    switch(level){
        case 1:setupLevel1();break;case 2:setupLevel2();break;case 3:setupLevel3();break;
        case 4:setupLevel4();break;case 5:setupLevel5();break;case 6:setupLevel6();break;
        case 7:setupLevel7();break;case 8:setupLevel8();break;case 9:setupLevel9();break;
        case 10:setupLevel10();break;case 11:setupLevel11();break;case 12:setupLevel12();break;
        case 13:setupLevel13();break;case 14:setupLevel14();break;case 15:setupLevel15();break;
        case 16:setupLevel16();break;
    }
    state_.orm=calculateORM();
}

void ReactorEngine::triggerLevel16Sequence(double T, double dt) {
    if(T>60.0&&!l_flag1_){log("Güç azaltma başlıyor.","OPERATÖR",0);l_flag1_=true;}
    if(T>120.0&&!l_flag2_){state_.xenon=1.60;log("Xenon yükseliyor!","REAKTÖR",2);l_flag2_=true;}
    if(T>180.0&&!l_flag3_&&state_.orm>8){
        for(int i=0;i<200;i++) state_.rod_pos[i]=std::max(0.03,state_.rod_pos[i]-0.05);
        state_.orm=calculateORM();
        log("ORM "+std::to_string(state_.orm)+" — tehlikeli!","ACİL",3);l_flag3_=true;
    }
    if(T>240.0&&!state_.turbine_rundown[0]&&l_flag3_){
        state_.turbine_rundown[0]=true;state_.turbine_online[0]=false;
        log("Türbin-1 coast-down başladı.","OPERATÖR",1);
    }
    if(T>265.0){
        state_.void_fraction=std::min(0.6,state_.void_fraction+0.005*dt);
        for(int i=0;i<4;i++) state_.mcp_flow[i]=std::max(100.0,state_.mcp_flow[i]-20.0*dt);
    }
    if(T>285.0&&!state_.az5_pressed){
        log("AZ-5 BASILDI — çok geç!","OPERATÖR",3);
        state_.az5_pressed=true;state_.scram_active=true;scram_time_=T;state_.rod_target.fill(1.0);
    }
    if(T>287.0&&T<293.0&&state_.scram_active){
        double surge=(T-287.0)*2.5;
        state_.power_frac=std::min(30.0,state_.power_frac+surge*dt);
        state_.power_mw=state_.power_frac*RATED_POWER_MW;
        if(state_.power_mw>5000.0) log("Güç "+fmtMW(state_.power_mw)+" — grafit uç etkisi!","FATAL",3);
    }
    if(T>293.0&&!state_.reactor_destroyed){
        state_.power_frac=30.0;state_.power_mw=RATED_POWER_MW*30.0;
        state_.reactor_destroyed=true;state_.mission_complete=true;state_.mission_failed=false;state_.mission_score=100.0;
        state_.status=ReactorStatus::EXPLOSION;
        addAlarm(AlarmType::REACTOR_EXPLOSION,AlarmSeverity::FATAL,"NÜKLEER PATLAMA","01:23:47 — Reaktör yok edildi!");
        log("!!! NÜKLEER PATLAMA !!! "+fmtMW(state_.power_mw)+" — 26 Nisan senaryosu tamamlandı.","FATAL",3);
    }
}

void ReactorEngine::triggerScram(const std::string& reason) {
    if(state_.scram_active) return;
    state_.scram_active=true;state_.az5_pressed=true;scram_time_=state_.simulation_time;
    state_.rod_target.fill(1.0);
    addAlarm(AlarmType::SCRAM_INITIATED,AlarmSeverity::EMERGENCY,"SCRAM",reason);
    log("SCRAM: "+reason,"ACİL",3);
}
void ReactorEngine::activateECCS(const std::string& reason) {
    if(state_.eccs_active) return;
    state_.eccs_active=true;log("SAOR AKTİF: "+reason,"ACİL",2);
}
void ReactorEngine::triggerMCPFailure(int idx) {
    if(idx<0||idx>=NUM_MCP) return;
    state_.mcp_failed[idx]=true;
    addAlarm(AlarmType::MCP_TRIP,AlarmSeverity::EMERGENCY,"POMPA ARIZASI","Pompa-"+std::to_string(idx+1)+" arızalı!");
    log("Pompa-"+std::to_string(idx+1)+" ARIZA!","ACİL",3);
}
void ReactorEngine::triggerCoolantLeak(double rate) {
    state_.coolant_leak=true;state_.leak_rate=rate;
    addAlarm(AlarmType::COOLANT_LEAK,AlarmSeverity::EMERGENCY,"SIZINTI","Sızıntı: "+std::to_string((int)rate)+" kg/s");
    log("SIZINTI: "+std::to_string((int)rate)+" kg/s!","ACİL",3);
}
void ReactorEngine::triggerFuelChannelRupture() {
    state_.fuel_channel_rupture=true;state_.coolant_leak=true;state_.leak_rate=400.0;
    addAlarm(AlarmType::FUEL_CHANNEL_RUPTURE,AlarmSeverity::FATAL,"TK PATLAMASI","Birincil devre yırtıldı! SCRAM+SAOR!");
    log("YAKIT KANALI PATLAMASI! Acil prosedür!","FATAL",3);
}
void ReactorEngine::triggerStationBlackout() {
    state_.station_blackout=true;state_.grid_connected=false;
    addAlarm(AlarmType::MCP_LOW_FLOW,AlarmSeverity::EMERGENCY,"ŞEBEKE KAYBI","Harici şebeke kesildi!");
    log("ŞEBEKE KAYBI! Dizel jeneratörü başlatın!","ACİL",3);
}
void ReactorEngine::addAlarm(AlarmType type,AlarmSeverity sev,const std::string& code,const std::string& msg) {
    for(auto& a:state_.active_alarms){if(a.type==type){a.active=true;return;}}
    Alarm a; a.type=type;a.severity=sev;a.message=code;a.message_tr=msg;
    a.timestamp=state_.simulation_time;a.acknowledged=false;a.active=true;
    state_.active_alarms.push_back(a);
    log("[ALARM] "+code+": "+msg,"ALARM",(int)sev);
}
void ReactorEngine::removeAlarm(AlarmType type) {
    state_.active_alarms.erase(std::remove_if(state_.active_alarms.begin(),state_.active_alarms.end(),[type](const Alarm& a){return a.type==type;}),state_.active_alarms.end());
}
bool ReactorEngine::hasAlarm(AlarmType type) const {
    for(const auto& a:state_.active_alarms) if(a.type==type) return true; return false;
}
void ReactorEngine::log(const std::string& msg,const std::string& cat,int sev) {
    LogEntry e; e.timestamp=state_.simulation_time; e.message="["+fmtTime(state_.simulation_time)+"] "+msg; e.category=cat; e.severity=sev; log_buffer_.push_back(e);
}
void ReactorEngine::applyControl(const ControlInput& input) {
    if(!state_.scram_active) for(int i=0;i<NUM_CONTROL_RODS;i++) state_.rod_target[i]=clamp(input.rod_targets[i],0.0,1.0);
    for(int i=0;i<NUM_MCP;i++) if(!state_.mcp_failed[i]){state_.mcp_active[i]=input.mcp_active[i];state_.mcp_setpoint[i]=clamp(input.mcp_setpoints[i],0.0,MCP_FLOW_NOM*1.1);}
    state_.feedwater_setpoint=clamp(input.feedwater_setpoint,0.0,FEEDWATER_NOM*1.5);
    for(int t=0;t<NUM_TURBINES;t++){
        if(!state_.turbine_trip[t]) state_.turbine_valve[t]=clamp(input.turbine_valve[t],0.0,1.0);
        if(input.turbine_trip_cmd[t]&&!state_.turbine_trip[t]){state_.turbine_trip[t]=true;state_.turbine_online[t]=false;log("Türbin-"+std::to_string(t+1)+" trip.","OPERATÖR",1);}
    }
    if(input.az5_press&&!state_.az5_blocked) triggerScram("Operatör AZ-5'e bastı.");
    if(input.eccs_request) activateECCS("Operatör SAOR devreye aldı.");
    if(input.diesel_start&&!state_.diesel_active){state_.diesel_active=true;state_.diesel_power=500;log("Dizel başlatıldı.","SİSTEM",1);}
    if(input.ack_alarms) for(auto& a:state_.active_alarms) a.acknowledged=true;
    state_.ar_active=input.ar_enable;
}
void ReactorEngine::setRodGroupTarget(int group,double pos) {
    int s=group*30,e=std::min(s+30,NUM_CONTROL_RODS);
    for(int i=s;i<e;i++) state_.rod_target[i]=clamp(pos,0.0,1.0);
}
ReactorState ReactorEngine::getState() const { return state_; }
std::vector<LogEntry> ReactorEngine::getAndClearLog() { auto t=log_buffer_; log_buffer_.clear(); return t; }
bool ReactorEngine::isReactorSafe() const { return !state_.reactor_destroyed&&!state_.fuel_melt&&state_.orm>=CRITICAL_ORM&&state_.coolant_temp_out<MAX_CLNT_TEMP&&state_.steam_pressure<MAX_PRESSURE; }
bool ReactorEngine::isMissionFailed() const { return state_.mission_failed; }
bool ReactorEngine::isMissionComplete(int) const { return state_.mission_complete; }
double ReactorEngine::getMissionScore(int) const { return state_.mission_failed?0.0:state_.mission_score; }
double ReactorEngine::gaussian_noise(double sigma) { std::normal_distribution<double> d(0.0,sigma); return d(rng_); }

} // namespace rbmk
