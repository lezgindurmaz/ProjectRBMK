#include "reactor_engine.h"
#include <sstream>
#include <iomanip>
#include <cstring>
#include <numeric>

namespace rbmk {

// ─── YARDIMCI: Zaman formatı ─────────────────────────────────────────────────
static std::string formatTime(double t) {
    int h = (int)(t / 3600);
    int m = (int)(t / 60) % 60;
    int s = (int)t % 60;
    std::ostringstream oss;
    oss << std::setfill('0')
        << std::setw(2) << h << ":"
        << std::setw(2) << m << ":"
        << std::setw(2) << s;
    return oss.str();
}

static std::string fmtMW(double mw) {
    std::ostringstream oss;
    oss << std::fixed << std::setprecision(1) << mw << " MW";
    return oss.str();
}

// ─── YAPICI ──────────────────────────────────────────────────────────────────
ReactorEngine::ReactorEngine() : rng_(std::random_device{}()) {
    state_.rod_pos.fill(1.0);     // Başlangıçta tüm çubuklar tam içeride
    state_.rod_target.fill(1.0);
    state_.rod_moving.fill(false);
}

// ─── BAŞLATMA ─────────────────────────────────────────────────────────────────
void ReactorEngine::initialize(bool cold_start, int level) {
    // Sıfırla
    std::memset(&state_.mcp_flow,    0, sizeof(state_.mcp_flow));
    std::memset(&state_.mcp_speed,   0, sizeof(state_.mcp_speed));
    std::memset(&state_.mcp_setpoint,0, sizeof(state_.mcp_setpoint));
    std::memset(&state_.mcp_active,  0, sizeof(state_.mcp_active));
    std::memset(&state_.mcp_failed,  0, sizeof(state_.mcp_failed));

    state_.current_level      = level;
    state_.simulation_time    = 0.0;
    state_.reactor_destroyed  = false;
    state_.mission_failed     = false;
    state_.mission_complete   = false;
    state_.active_alarms.clear();
    log_buffer_.clear();

    if (cold_start) {
        state_.power_mw          = 0.0;
        state_.power_frac        = 0.0;
        state_.neutron_flux      = 0.0;
        state_.precursor         = 0.0;
        state_.fuel_temp         = 20.0;
        state_.coolant_temp_in   = 20.0;
        state_.coolant_temp_out  = 20.0;
        state_.coolant_pressure  = 0.1;   // atmosferik
        state_.steam_pressure    = 0.0;
        state_.void_fraction     = 0.0;
        state_.iodine            = 0.0;
        state_.xenon             = 0.0;
        for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
        state_.turbine_speed[0] = state_.turbine_speed[1] = 0.0;
        state_.turbine_load[0]  = state_.turbine_load[1]  = 0.0;
        state_.status            = ReactorStatus::COLD_SHUTDOWN;
        state_.scram_active      = false;
        state_.eccs_active       = false;
    }

    setLevelConditions(level);
    log("Reaktör motoru başlatıldı. Level " + std::to_string(level),
        "SİSTEM", 0);
}

// ─── LEVEL KOŞULLARI ─────────────────────────────────────────────────────────
void ReactorEngine::setLevelConditions(int level) {
    state_.current_level = level;
    switch (level) {
        case 1:  setupLevel1();  break;
        case 2:  setupLevel2();  break;
        case 3:  setupLevel3();  break;
        case 4:  setupLevel4();  break;
        case 5:  setupLevel5();  break;
        case 6:  setupLevel6();  break;
        case 7:  setupLevel7();  break;
        case 8:  setupLevel8();  break;
        case 9:  setupLevel9();  break;
        case 10: setupLevel10(); break;
        case 11: setupLevel11(); break;
        case 12: setupLevel12(); break;
        case 13: setupLevel13(); break;
        case 14: setupLevel14(); break;
        case 15: setupLevel15(); break;
        case 16: setupLevel16(); break;
    }
}

// ─── LEVEL SETUP: 1 - ORYANTASYON ────────────────────────────────────────────
void ReactorEngine::setupLevel1() {
    // Soğuk kapalı, her şey nominal
    state_.rod_pos.fill(1.0);
    state_.rod_target.fill(1.0);
    state_.power_mw      = 0.0;
    state_.power_frac    = 0.0;
    state_.xenon         = 0.0;
    state_.iodine        = 0.0;
    state_.coolant_temp_in  = 20.0;
    state_.coolant_temp_out = 20.0;
    state_.coolant_pressure = 0.1;
    state_.status = ReactorStatus::COLD_SHUTDOWN;
    log("LEVEL 1: Oryantasyon modu aktif. Tüm kontrolleri tanıyın.", "SİSTEM", 0);
}

// ─── LEVEL SETUP: 2 - SOĞUK BAŞLATMA ────────────────────────────────────────
void ReactorEngine::setupLevel2() {
    state_.rod_pos.fill(1.0);
    state_.rod_target.fill(1.0);
    state_.power_mw = 0.0; state_.power_frac = 0.0;
    state_.neutron_flux = 1e-8;  // Küçük kaynak nötronları
    state_.precursor    = 1e-8;
    state_.xenon = 0.0; state_.iodine = 0.0;
    state_.coolant_temp_in  = 20.0;
    state_.coolant_temp_out = 20.0;
    state_.coolant_pressure = 0.1;
    state_.steam_pressure   = 0.0;
    state_.drum_level[0] = state_.drum_level[1] = 50.0;
    state_.feedwater_setpoint = FEEDWATER_NOM * 0.1;
    state_.status = ReactorStatus::COLD_SHUTDOWN;
    log("LEVEL 2: Soğuk başlatma koşulları yüklendi.", "SİSTEM", 0);
}

// ─── LEVEL SETUP: 3 - GÜÇ ARTIRMA ───────────────────────────────────────────
void ReactorEngine::setupLevel3() {
    // %5 güçte stabil başla
    state_.power_frac = 0.05; state_.power_mw = RATED_POWER_MW * 0.05;
    state_.neutron_flux = 0.05; state_.precursor = 0.05 * BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon  = 0.12; state_.iodine = 0.10;
    state_.coolant_temp_in  = 230.0; state_.coolant_temp_out = 240.0;
    state_.coolant_pressure = 6.9;   state_.steam_pressure   = 6.0;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    // 4 pompa açık (her devreden 2)
    for (int i = 0; i < 4; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM * 0.5;
        state_.mcp_flow[i]     = MCP_FLOW_NOM * 0.5;
        state_.mcp_speed[i]    = 950.0;
    }
    // Çubuklar nominal pozisyonda (%5 güç için ~60 çubuk dışarıda)
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 60; i++) state_.rod_pos[i] = 0.2;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::LOW_POWER;
    state_.feedwater_setpoint = FEEDWATER_NOM * 0.2;
    state_.turbine_online[0] = false;
    state_.steam_pressure = 5.5;
    log("LEVEL 3: %5 güçten güç artırma görevi.", "SİSTEM", 0);
}

// ─── LEVEL SETUP: 4 - NORMAL OPERASYON ───────────────────────────────────────
void ReactorEngine::setupLevel4() {
    state_.power_frac = 0.50; state_.power_mw = RATED_POWER_MW * 0.50;
    state_.neutron_flux = 0.50;
    state_.precursor = 0.50 * BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon  = 0.75; state_.iodine = 0.70;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = 276.0;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM * 0.9;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 6; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM * 0.7;
        state_.mcp_flow[i]     = MCP_FLOW_NOM * 0.7;
        state_.mcp_speed[i]    = 980.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 100; i++) state_.rod_pos[i] = 0.15;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::POWER_ASCENT;
    state_.feedwater_setpoint = FEEDWATER_NOM * 0.7;
    state_.turbine_online[0] = true; state_.turbine_speed[0] = 3000.0;
    state_.turbine_load[0]   = 400.0;
    l4_stable_time_ = 0.0;
    log("LEVEL 4: Normal operasyon görevi. %100 güce ulaşıp vardiyayı tamamlayın.", "SİSTEM", 0);
}

// ─── LEVEL SETUP: 5 - POMPA ARIZASI (ZORG) ───────────────────────────────────
void ReactorEngine::setupLevel5() {
    // %100 güçten başla
    state_.power_frac = 1.00; state_.power_mw = RATED_POWER_MW;
    state_.neutron_flux = 1.0;
    state_.precursor = BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon = 1.0; state_.iodine = 1.0;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = COOLANT_OUT_NOM;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM;
    state_.void_fraction    = 0.08;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 8; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM;
        state_.mcp_flow[i]     = MCP_FLOW_NOM;
        state_.mcp_speed[i]    = 1000.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 165; i++) state_.rod_pos[i] = 0.08;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.feedwater_setpoint = FEEDWATER_NOM;
    state_.turbine_online[0] = state_.turbine_online[1] = true;
    state_.turbine_speed[0]  = state_.turbine_speed[1]  = 3000.0;
    state_.turbine_load[0]   = state_.turbine_load[1]   = 500.0;
    l5_mcp_failed_ = false;
    // MCP arızası 30 saniye sonra tetiklenecek (ana döngüde)
    log("LEVEL 5: %100 güç - Pompa arızasına hazırlıklı olun!", "SİSTEM", 1);
}

// ─── LEVEL SETUP: 6 - TÜRBİN TESTİ ──────────────────────────────────────────
void ReactorEngine::setupLevel6() {
    // %100 güçten başla, %75'e düşürmek gerekecek
    state_.power_frac = 1.00; state_.power_mw = RATED_POWER_MW;
    state_.neutron_flux = 1.0;
    state_.precursor = BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon = 1.0; state_.iodine = 1.0;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = COOLANT_OUT_NOM;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM;
    state_.void_fraction = 0.08;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 8; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM;
        state_.mcp_flow[i]     = MCP_FLOW_NOM;
        state_.mcp_speed[i]    = 1000.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 165; i++) state_.rod_pos[i] = 0.08;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.feedwater_setpoint = FEEDWATER_NOM;
    state_.turbine_online[0] = state_.turbine_online[1] = true;
    state_.turbine_speed[0] = state_.turbine_speed[1] = 3000.0;
    state_.turbine_load[0] = state_.turbine_load[1] = 500.0;
    l6_test_time_ = 0.0;
    log("LEVEL 6: Türbin-2 coast-down testi için %75 güce indirin.", "SİSTEM", 0);
}

// ─── LEVEL SETUP: 7 - XENON ÇUKURU ───────────────────────────────────────────
void ReactorEngine::setupLevel7() {
    // Reaktör birkaç saat önce kapatılmış, xenon yüksek
    state_.power_frac = 0.0; state_.power_mw = 0.0;
    state_.neutron_flux = 0.0; state_.precursor = 0.0;
    state_.xenon  = 1.85;  // Güç kapatmasından sonra xenon zirve
    state_.iodine = 0.60;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = COOLANT_IN_NOM + 5.0;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = 5.0;
    state_.void_fraction    = 0.01;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 4; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM * 0.4;
        state_.mcp_flow[i]     = MCP_FLOW_NOM * 0.4;
        state_.mcp_speed[i]    = 700.0;
    }
    state_.rod_pos.fill(1.0);  // Tüm çubuklar içeride (SCRAM pozisyonu)
    state_.rod_target.fill(1.0);
    state_.status = ReactorStatus::HOT_SHUTDOWN;
    state_.scram_active = false;  // SCRAM reset edildi
    state_.feedwater_setpoint = FEEDWATER_NOM * 0.2;
    log("LEVEL 7: Reaktör kapalı, Xenon zirveye ulaşıyor. Dikkatli başlatın!", "SİSTEM", 1);
}

// ─── LEVEL SETUP: 8 - BUHAR SEPARATÖRÜ ───────────────────────────────────────
void ReactorEngine::setupLevel8() {
    state_.power_frac = 1.00; state_.power_mw = RATED_POWER_MW;
    state_.neutron_flux = 1.0;
    state_.precursor = BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon = 1.0; state_.iodine = 1.0;
    state_.coolant_temp_in = COOLANT_IN_NOM;
    state_.coolant_temp_out = COOLANT_OUT_NOM;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure = STEAM_PRESS_NOM;
    state_.void_fraction = 0.08;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 8; i++) {
        state_.mcp_active[i] = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM;
        state_.mcp_flow[i]     = MCP_FLOW_NOM;
        state_.mcp_speed[i]    = 1000.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 165; i++) state_.rod_pos[i] = 0.08;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.feedwater_setpoint = FEEDWATER_NOM;
    state_.turbine_online[0] = state_.turbine_online[1] = true;
    state_.turbine_speed[0] = state_.turbine_speed[1] = 3000.0;
    state_.turbine_load[0] = state_.turbine_load[1] = 500.0;
    l8_drum_ok_time_ = 0.0;
    log("LEVEL 8: Yük değişimi sırasında tambur seviyelerini kontrol edin!", "SİSTEM", 0);
}

// ─── LEVEL SETUP: 9 - MANUEL KONTROL ─────────────────────────────────────────
void ReactorEngine::setupLevel9() {
    state_.power_frac = 0.80; state_.power_mw = RATED_POWER_MW * 0.80;
    state_.neutron_flux = 0.80;
    state_.precursor = 0.80 * BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon = 0.90; state_.iodine = 0.85;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = 280.0;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM * 0.95;
    state_.void_fraction = 0.06;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 8; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM * 0.85;
        state_.mcp_flow[i]     = MCP_FLOW_NOM * 0.85;
        state_.mcp_speed[i]    = 990.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 145; i++) state_.rod_pos[i] = 0.10;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.ar_active  = false;   // AR sistemi kapalı - sadece manuel kontrol
    state_.feedwater_setpoint = FEEDWATER_NOM * 0.85;
    state_.turbine_online[0] = state_.turbine_online[1] = true;
    state_.turbine_speed[0] = state_.turbine_speed[1] = 3000.0;
    state_.turbine_load[0] = state_.turbine_load[1] = 450.0;
    l9_manual_time_ = 0.0;
    log("LEVEL 9: AR sistemi devre dışı. Reaktörü MANUEL kontrol edin!", "SİSTEM", 1);
}

// ─── LEVEL SETUP: 10 - İSTASYON KARARTMASI (ZORG) ───────────────────────────
void ReactorEngine::setupLevel10() {
    state_.power_frac = 1.00; state_.power_mw = RATED_POWER_MW;
    state_.neutron_flux = 1.0;
    state_.precursor = BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon = 1.0; state_.iodine = 1.0;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = COOLANT_OUT_NOM;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM;
    state_.void_fraction = 0.08;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 8; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM;
        state_.mcp_flow[i]     = MCP_FLOW_NOM;
        state_.mcp_speed[i]    = 1000.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 165; i++) state_.rod_pos[i] = 0.08;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.grid_connected  = true;
    state_.station_blackout = false;
    state_.diesel_active   = false;
    state_.feedwater_setpoint = FEEDWATER_NOM;
    state_.turbine_online[0] = state_.turbine_online[1] = true;
    state_.turbine_speed[0] = state_.turbine_speed[1] = 3000.0;
    state_.turbine_load[0] = state_.turbine_load[1] = 500.0;
    l10_blackout_time_ = 0.0;
    l10_diesel_done_ = false;
    log("LEVEL 10: %100 güç - Ani şebeke kaybına hazırlıklı olun!", "SİSTEM", 1);
}

// ─── LEVEL SETUP: 11 - XENON ZEHİRLENMESİ ────────────────────────────────────
void ReactorEngine::setupLevel11() {
    // Xenon tuzağına yakalanmış, güç %70'te ama xenon yükseliyor
    state_.power_frac = 0.70; state_.power_mw = RATED_POWER_MW * 0.70;
    state_.neutron_flux = 0.70;
    state_.precursor = 0.70 * BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon  = 1.40;  // Yüksek xenon - dengesiz
    state_.iodine = 0.80;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = 279.0;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM * 0.95;
    state_.void_fraction = 0.05;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 6; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM * 0.80;
        state_.mcp_flow[i]     = MCP_FLOW_NOM * 0.80;
        state_.mcp_speed[i]    = 985.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 130; i++) state_.rod_pos[i] = 0.12;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.feedwater_setpoint = FEEDWATER_NOM * 0.80;
    state_.turbine_online[0] = state_.turbine_online[1] = true;
    state_.turbine_speed[0] = state_.turbine_speed[1] = 3000.0;
    state_.turbine_load[0] = state_.turbine_load[1] = 380.0;
    log("LEVEL 11: Xenon yükseliyor! Reaktiviteyi yönetin ya da SCRAM başlatın.", "SİSTEM", 2);
}

// ─── LEVEL SETUP: 12 - POZİTİF BOŞLUK KATSAYISI ─────────────────────────────
void ReactorEngine::setupLevel12() {
    // %50 güç, bir pompa yavaşlıyor
    state_.power_frac = 0.50; state_.power_mw = RATED_POWER_MW * 0.50;
    state_.neutron_flux = 0.50;
    state_.precursor = 0.50 * BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon = 0.70; state_.iodine = 0.65;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = 276.0;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM * 0.92;
    state_.void_fraction = 0.04;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 6; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM * 0.65;
        state_.mcp_flow[i]     = MCP_FLOW_NOM * 0.65;
        state_.mcp_speed[i]    = 975.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 105; i++) state_.rod_pos[i] = 0.15;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.feedwater_setpoint = FEEDWATER_NOM * 0.65;
    state_.turbine_online[0] = true; state_.turbine_online[1] = false;
    state_.turbine_speed[0] = 3000.0; state_.turbine_speed[1] = 0.0;
    state_.turbine_load[0] = 450.0; state_.turbine_load[1] = 0.0;
    l12_void_peak_ = 0.0;
    log("LEVEL 12: Soğutucu akışı azalıyor - pozitif geri besleme tehlikesi!", "SİSTEM", 2);
}

// ─── LEVEL SETUP: 13 - ÇOKLU ARIZA ──────────────────────────────────────────
void ReactorEngine::setupLevel13() {
    state_.power_frac = 1.00; state_.power_mw = RATED_POWER_MW;
    state_.neutron_flux = 1.0;
    state_.precursor = BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon = 1.0; state_.iodine = 1.0;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = COOLANT_OUT_NOM;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM;
    state_.void_fraction = 0.08;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 8; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM;
        state_.mcp_flow[i]     = MCP_FLOW_NOM;
        state_.mcp_speed[i]    = 1000.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 165; i++) state_.rod_pos[i] = 0.08;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.feedwater_setpoint = FEEDWATER_NOM;
    state_.turbine_online[0] = state_.turbine_online[1] = true;
    state_.turbine_speed[0] = state_.turbine_speed[1] = 3000.0;
    state_.turbine_load[0] = state_.turbine_load[1] = 500.0;
    log("LEVEL 13: Çoklu arıza senaryosu - hazırlıklı olun!", "SİSTEM", 2);
}

// ─── LEVEL SETUP: 14 - SKALA ARIZASI ─────────────────────────────────────────
void ReactorEngine::setupLevel14() {
    state_.power_frac = 0.90; state_.power_mw = RATED_POWER_MW * 0.90;
    state_.neutron_flux = 0.90;
    state_.precursor = 0.90 * BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon = 0.98; state_.iodine = 0.95;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = 282.0;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM;
    state_.void_fraction = 0.07;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 8; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM * 0.92;
        state_.mcp_flow[i]     = MCP_FLOW_NOM * 0.92;
        state_.mcp_speed[i]    = 995.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 158; i++) state_.rod_pos[i] = 0.09;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.skala_active = false;   // SKALA bilgisayarı çöktü!
    state_.feedwater_setpoint = FEEDWATER_NOM * 0.92;
    state_.turbine_online[0] = state_.turbine_online[1] = true;
    state_.turbine_speed[0] = state_.turbine_speed[1] = 3000.0;
    state_.turbine_load[0] = state_.turbine_load[1] = 480.0;
    log("LEVEL 14: SKALA bilgisayarı çöktü! Manuel kontrol şart!", "SİSTEM", 2);
}

// ─── LEVEL SETUP: 15 - YAKIT KANALI PATLAMASI (ZORG) ─────────────────────────
void ReactorEngine::setupLevel15() {
    state_.power_frac = 1.00; state_.power_mw = RATED_POWER_MW;
    state_.neutron_flux = 1.0;
    state_.precursor = BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon = 1.0; state_.iodine = 1.0;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = COOLANT_OUT_NOM;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM;
    state_.void_fraction = 0.08;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 8; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM;
        state_.mcp_flow[i]     = MCP_FLOW_NOM;
        state_.mcp_speed[i]    = 1000.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 165; i++) state_.rod_pos[i] = 0.08;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.feedwater_setpoint = FEEDWATER_NOM;
    state_.turbine_online[0] = state_.turbine_online[1] = true;
    state_.turbine_speed[0] = state_.turbine_speed[1] = 3000.0;
    state_.turbine_load[0] = state_.turbine_load[1] = 500.0;
    l15_eccs_done_ = false;
    l15_cooling_time_ = 0.0;
    log("LEVEL 15: %100 güç - Yakıt kanalı patlaması yakında!", "SİSTEM", 2);
}

// ─── LEVEL SETUP: 16 - GECE TESTİ (SCRIPTED PATLAMA) ─────────────────────────
void ReactorEngine::setupLevel16() {
    // Çernobil tarzı senaryo: türbin testi için güç azaltılmış
    state_.power_frac = 1.00; state_.power_mw = RATED_POWER_MW;
    state_.neutron_flux = 1.0;
    state_.precursor = BETA / LAMBDA_EFF / NEUTRON_LIFE;
    state_.xenon = 1.0; state_.iodine = 1.0;
    state_.coolant_temp_in  = COOLANT_IN_NOM;
    state_.coolant_temp_out = COOLANT_OUT_NOM;
    state_.coolant_pressure = PRESSURE_NOM;
    state_.steam_pressure   = STEAM_PRESS_NOM;
    state_.void_fraction = 0.08;
    for (int i = 0; i < NUM_DRUMS; i++) state_.drum_level[i] = 50.0;
    for (int i = 0; i < 8; i++) {
        state_.mcp_active[i]   = true;
        state_.mcp_setpoint[i] = MCP_FLOW_NOM;
        state_.mcp_flow[i]     = MCP_FLOW_NOM;
        state_.mcp_speed[i]    = 1000.0;
    }
    state_.rod_pos.fill(1.0);
    for (int i = 0; i < 165; i++) state_.rod_pos[i] = 0.08;
    state_.rod_target = state_.rod_pos;
    state_.status = ReactorStatus::FULL_POWER;
    state_.feedwater_setpoint = FEEDWATER_NOM;
    state_.turbine_online[0] = state_.turbine_online[1] = true;
    state_.turbine_speed[0] = state_.turbine_speed[1] = 3000.0;
    state_.turbine_load[0] = state_.turbine_load[1] = 500.0;
    state_.az5_blocked = false;  // AZ-5 henüz bloke değil
    l16_seq_time_ = 0.0;
    log("LEVEL 16: GECE TESTİ. 26 Nisan 1986 01:23... 2026 yılına uyarlanmış senaryo.",
        "SİSTEM", 2);
}

// ─── ANA SİMÜLASYON ADIMI ────────────────────────────────────────────────────
void ReactorEngine::step(double dt) {
    if (state_.reactor_destroyed) return;
    state_.simulation_time += dt;
    state_.elapsed_real    += dt;

    // Kontrol çubuklarını güncelle (hedef pozisyona doğru hareket)
    updateControlRods(dt);

    // Nötronik fizik (en kritik)
    updateNeutronics(dt);

    // Xenon / İyot dinamikleri
    updateXenon(dt);

    // Termal hidrolik
    updateThermalHydraulics(dt);

    // Buhar sistemi
    updateSteamSystem(dt);

    // Pompalar
    updatePumps(dt);

    // Türbinler
    updateTurbines(dt);

    // Otomatik güvenlik sistemleri
    updateSafetySystemsAutomatic(dt);

    // Reaktör durum güncelleme
    updateReactorStatus();

    // ORM hesapla
    state_.orm = calculateORM();

    // Alarm kontrolleri
    checkAndUpdateAlarms();

    // Level-spesifik olaylar
    checkMissionConditions(state_.current_level);
}

// ─── KONTROL ÇUBUKLARI ────────────────────────────────────────────────────────
void ReactorEngine::updateControlRods(double dt) {
    // Eğer SCRAM aktifse tüm çubukları tam içeri sok
    if (state_.scram_active) {
        bool all_inserted = true;
        for (int i = 0; i < NUM_CONTROL_RODS; i++) {
            if (state_.rod_pos[i] > 0.01) {
                // SCRAM hızı: gerçek RBMK'da ~18-21 saniyede tam iniş
                // RBMK'nın zayıflığı: grafit ucu etkisi ilk 2-3 saniyede POZİTİF reaktivite
                state_.rod_pos[i] -= (1.0 / 18.0) * dt;
                state_.rod_pos[i] = std::max(0.0, state_.rod_pos[i]);
                state_.rod_moving[i] = true;
                all_inserted = false;
            } else {
                state_.rod_moving[i] = false;
            }
        }
        // SCRAM tamamlandıysa kapat
        if (all_inserted) {
            log("ВСЕ СТЕРЖНИ ВВЕДЕНЫ - Tüm kontrol çubukları reaktöre girdi.", "SİSTEM", 1);
            state_.scram_active = false;
        }
        return;
    }

    // Normal hareket: çubukları hedefe doğru hareket ettir
    double rod_step = 0.005 * dt;  // ~% 0.5 per saniye
    for (int i = 0; i < NUM_CONTROL_RODS; i++) {
        double diff = state_.rod_target[i] - state_.rod_pos[i];
        if (std::abs(diff) > 0.001) {
            double move = std::copysign(std::min(rod_step, std::abs(diff)), diff);
            state_.rod_pos[i] += move;
            state_.rod_pos[i] = clamp(state_.rod_pos[i], 0.0, 1.0);
            state_.rod_moving[i] = true;
        } else {
            state_.rod_moving[i] = false;
        }
    }
}

// ─── NÖTRONİK (Point Kinetics) ────────────────────────────────────────────────
void ReactorEngine::updateNeutronics(double dt) {
    double rho = calcTotalReactivity();
    state_.reactivity_total = rho;

    // Minimum nötron seviyesi (kaynak)
    const double SOURCE_LEVEL = 1e-8;

    // Point kinetics denklemi (tek gecikmeli grup, basitleştirilmiş)
    // dP/dt = ((ρ - β) / Λ) * P + λ * C
    // dC/dt = (β / Λ) * P - λ * C
    double P = state_.neutron_flux;
    double C = state_.precursor;

    // Reaktivite β cinsinden; β = 1.0 olarak normalize
    double rho_norm = rho;  // zaten β biriminde

    double dP_dt = ((rho_norm - 1.0) / NEUTRON_LIFE) * P + LAMBDA_EFF * C + SOURCE_LEVEL;
    double dC_dt = (1.0 / NEUTRON_LIFE) * P - LAMBDA_EFF * C;

    // Euler integrasyonu (küçük dt için yeterli)
    P += dP_dt * dt;
    C += dC_dt * dt;

    // Negatif güç yok
    P = std::max(P, SOURCE_LEVEL);
    C = std::max(C, 0.0);

    state_.neutron_flux = P;
    state_.precursor    = C;

    // Termal güce dönüştür
    double prev_power = state_.power_mw;
    state_.power_frac  = P;
    state_.power_mw    = P * RATED_POWER_MW;
    state_.power_rate_mw_s = (state_.power_mw - prev_power) / dt;

    // Reaktör patladı mı?
    if (state_.power_mw > EXPLOSION_POWER && !state_.reactor_destroyed) {
        state_.reactor_destroyed = true;
        state_.mission_failed    = true;
        state_.status = ReactorStatus::EXPLOSION;
        state_.failure_reason = "REAKTÖR PATLAMASI: Güç " +
            fmtMW(state_.power_mw) + " - Prompt kritik aşıldı!";
        addAlarm(AlarmType::REACTOR_EXPLOSION, AlarmSeverity::FATAL,
                 "МПА РЕАКТОРА", "REAKTÖR PATLADI! Anlık kritik seviye aşıldı!");
        log("!!! ЯДЕРНЫЙ ВЗРЫВ !!! - NÜKLEER PATLAMA MEYDANA GELDİ. " +
            fmtMW(state_.power_mw), "FATAL", 3);
    }
    // Çekirdek hasarı
    else if (state_.power_mw > MELTDOWN_POWER && !state_.fuel_melt) {
        state_.fuel_melt = true;
        log("ЯДЕРНОЕ ТОПЛИВО ПЛАВИТСЯ - Yakıt erime başladı!", "ACİL", 3);
    }
}

// ─── REAKTİVİTE HESAPLAMALARI ────────────────────────────────────────────────
double ReactorEngine::calcRodReactivity() const {
    double rho_rods = 0.0;
    // Tam içerideki çubuk: -ROD_WORTH_AVG katkı (negatif reaktivite)
    // Tam dışarıdaki çubuk: 0 katkı
    // Her çubuk için: katkı = -ROD_WORTH_AVG * rod_pos[i]
    for (int i = 0; i < NUM_CONTROL_RODS; i++) {
        rho_rods -= ROD_WORTH_AVG * state_.rod_pos[i];
    }
    // SCRAM sırasında grafit ucu etkisi: ilk çubuklar girdiğinde geçici POZİTİF etki
    // Bu gerçekçi bir etki - RBMK'nın ölümcül tasarım kusuru
    if (state_.scram_active) {
        // Ortalama rod pozisyonu
        double avg_pos = 0.0;
        for (int i = 0; i < NUM_CONTROL_RODS; i++) avg_pos += state_.rod_pos[i];
        avg_pos /= NUM_CONTROL_RODS;
        // 0.8-1.0 aralığında iken (yeni girerken) geçici pozitif reaktivite
        if (avg_pos > 0.80 && avg_pos < 0.95) {
            double graphite_effect = GRAPHITE_TIP_REACTIVITY *
                                     (NUM_CONTROL_RODS / 100.0) *
                                     (avg_pos - 0.80) / 0.15;
            rho_rods += graphite_effect;
        }
    }
    return rho_rods;
}

double ReactorEngine::calcXenonReactivity() const {
    // Xenon reaktivitesi: her zaman negatif
    return -MAX_XE_REACTIV * (state_.xenon / XE_EQUIL);
}

double ReactorEngine::calcVoidCoefficient() const {
    // RBMK'nın tehlikeli özelliği: güç düştükçe void katsayısı daha pozitif
    double power_factor = state_.power_frac;
    if (power_factor < 0.20) {
        // Düşük güçte çok yüksek pozitif void katsayısı
        return VOID_COEFF_LOW;
    } else {
        // Yüksek güçte de pozitif ama daha az
        return VOID_COEFF_HIGH + (VOID_COEFF_LOW - VOID_COEFF_HIGH) *
               (0.20 - std::min(power_factor, 0.20)) / 0.20;
    }
}

double ReactorEngine::calcVoidReactivity() const {
    return calcVoidCoefficient() * state_.void_fraction;
}

double ReactorEngine::calcDopplerReactivity() const {
    double delta_T = state_.fuel_temp - FUEL_TEMP_NOM;
    return DOPPLER_COEFF * delta_T;
}

double ReactorEngine::calcTotalReactivity() const {
    double rods    = calcRodReactivity();
    double xenon   = calcXenonReactivity();
    double voidr   = calcVoidReactivity();
    double doppler = calcDopplerReactivity();
    // Değerleri kaydet (const olmadığı için mutable yapamayız, doğrudan atıyoruz)
    return rods + xenon + voidr + doppler;
}

// ─── XENON / İYOT DİNAMİKLERİ ────────────────────────────────────────────────
void ReactorEngine::updateXenon(double dt) {
    double P = state_.power_frac;  // Normalize güç

    // İyot-135 (I-135)
    // dI/dt = YIELD_I * P - LAMBDA_I * I
    double dI_dt = YIELD_I * P - LAMBDA_I * state_.iodine * (1.0 / LAMBDA_I);
    // Normalize form:
    dI_dt = (P - state_.iodine) * LAMBDA_I;
    state_.iodine += dI_dt * dt;
    state_.iodine  = std::max(0.0, state_.iodine);

    // Xenon-135 (Xe-135)
    // dXe/dt = YIELD_XE * P + LAMBDA_I * I - (LAMBDA_XE + XE_BURNOUT * P) * Xe
    double dXe_dt = YIELD_XE * (P / YIELD_XE) +    // üretim
                    LAMBDA_I  * state_.iodine -       // iyottan bozunma
                    LAMBDA_XE * state_.xenon -         // xenon bozunması
                    XE_BURNOUT * P * state_.xenon;     // nötron absorpsiyonu

    // Normalize form (daha stabil):
    double xe_prod  = P + state_.iodine * (LAMBDA_I / LAMBDA_XE);
    double xe_loss  = state_.xenon * (1.0 + XE_BURNOUT / LAMBDA_XE * P);
    dXe_dt = (xe_prod - xe_loss) * LAMBDA_XE * 0.5;

    state_.xenon += dXe_dt * dt;
    state_.xenon  = std::max(0.0, state_.xenon);

    // Xenon izotermal zirve ~8-12 saat sonra güç kapatmasından
    // (oyun içi hızlandırılmış simülasyon ile)
}

// ─── TERMAL HİDROLİK ─────────────────────────────────────────────────────────
void ReactorEngine::updateThermalHydraulics(double dt) {
    double P  = state_.power_frac;
    double Q  = P * RATED_POWER_MW * 1e6;  // Watt

    // Toplam soğutucu akışı
    double total_flow = 0.0;
    for (int i = 0; i < NUM_MCP; i++) {
        if (state_.mcp_active[i] && !state_.mcp_failed[i]) {
            total_flow += state_.mcp_flow[i];
        }
    }
    state_.coolant_flow_total = total_flow;

    // Sızıntı etkisi
    if (state_.coolant_leak) {
        total_flow = std::max(0.0, total_flow - state_.leak_rate);
        // Basınç düşüşü
        state_.coolant_pressure -= state_.leak_rate * 0.001 * dt;
        state_.coolant_pressure = std::max(0.1, state_.coolant_pressure);
    }

    // Soğutucu çıkış sıcaklığı (enerji dengesi)
    double Cp_water = 4200.0;  // J/(kg·K)
    if (total_flow > 0.01) {
        double delta_T = Q / (total_flow * Cp_water);
        double target_T_out = state_.coolant_temp_in + delta_T;
        // Birinci derece gecikmeli tepki (zaman sabiti ~10s)
        state_.coolant_temp_out += (target_T_out - state_.coolant_temp_out) * dt / 10.0;
    }

    // Yakıt sıcaklığı (soğutucudan daha yüksek, hızlı tepki)
    double target_fuel_T = state_.coolant_temp_out + P * 800.0;  // ~800°C fark tam güçte
    state_.fuel_temp += (target_fuel_T - state_.fuel_temp) * dt / 5.0;

    // Boşluk (void) hesabı: çıkış sıcaklığı doyma sıcaklığını aşarsa kaynama
    double P_mpa = state_.coolant_pressure;
    // Doyma sıcaklığı (MPa'dan basitleştirilmiş): T_sat ≈ 184 + 16*P (°C, 5-8 MPa aralığı)
    double T_sat = 184.0 + 16.0 * P_mpa;
    double superheat = state_.coolant_temp_out - T_sat;
    double target_void = 0.0;
    if (superheat > 0) {
        // Kaynama başladı - void artar
        target_void = std::min(0.80, superheat / 30.0 * 0.3);
    } else {
        target_void = 0.0;
    }
    // Akış azaldıkça void artar
    if (total_flow < FEEDWATER_NOM * 0.5 && P > 0.1) {
        target_void = std::min(0.95, target_void + (1.0 - total_flow / (FEEDWATER_NOM * 0.5)) * 0.3);
    }
    state_.void_fraction += (target_void - state_.void_fraction) * dt / 3.0;
    state_.void_fraction = clamp(state_.void_fraction, 0.0, 0.99);

    // Reaktivite bileşenlerini güncelle
    state_.reactivity_rods    = calcRodReactivity();
    state_.reactivity_xenon   = calcXenonReactivity();
    state_.reactivity_void    = calcVoidReactivity();
    state_.reactivity_doppler = calcDopplerReactivity();
    state_.reactivity_total   = state_.reactivity_rods + state_.reactivity_xenon +
                                 state_.reactivity_void + state_.reactivity_doppler;
}

// ─── BUHAR SİSTEMİ ───────────────────────────────────────────────────────────
void ReactorEngine::updateSteamSystem(double dt) {
    double P = state_.power_frac;

    // Buhar üretimi soğutucudan
    double steam_gen = P * FEEDWATER_NOM;  // kg/s

    // Besleme suyu akışı
    double fw = state_.feedwater_setpoint;
    state_.feedwater_flow += (fw - state_.feedwater_flow) * dt / 8.0;

    // Tambur seviyeleri
    double steam_consumed = 0.0;
    for (int t = 0; t < NUM_TURBINES; t++) {
        if (state_.turbine_online[t]) {
            steam_consumed += state_.turbine_valve[t] * FEEDWATER_NOM * 0.5;
        }
    }

    double net_flow = state_.feedwater_flow - steam_consumed;
    double drum_change = net_flow / (100.0 * NUM_DRUMS) * dt;

    for (int i = 0; i < NUM_DRUMS; i++) {
        state_.drum_level[i] += drum_change;
        // Isı etkisi: güç arttıkça buhar üretimi → seviye artar, azalırsa düşer
        state_.drum_level[i] += (P - 0.5) * 0.1 * dt;
        state_.drum_level[i] = clamp(state_.drum_level[i], 0.0, 100.0);
    }

    // Buhar basıncı
    double target_steam_P = P * STEAM_PRESS_NOM;
    if (!state_.turbine_online[0] && !state_.turbine_online[1]) {
        target_steam_P = std::min(target_steam_P * 1.3, MAX_PRESSURE);  // Basınç birikir
    }
    state_.steam_pressure += (target_steam_P - state_.steam_pressure) * dt / 15.0;
    state_.steam_pressure = clamp(state_.steam_pressure, 0.0, MAX_PRESSURE + 1.0);

    state_.steam_flow = steam_gen;
}

// ─── POMPALAR ─────────────────────────────────────────────────────────────────
void ReactorEngine::updatePumps(double dt) {
    for (int i = 0; i < NUM_MCP; i++) {
        if (state_.mcp_failed[i]) {
            // Arızalı pompa: yavaşla
            state_.mcp_flow[i]  = std::max(0.0, state_.mcp_flow[i]  - 50.0 * dt);
            state_.mcp_speed[i] = std::max(0.0, state_.mcp_speed[i] - 100.0 * dt);
            state_.mcp_active[i] = (state_.mcp_speed[i] > 10.0);
        } else if (state_.mcp_active[i]) {
            // Aktif pompa: setpointa doğru git
            double target_flow  = state_.mcp_setpoint[i];
            double target_speed = 1000.0 * (target_flow / MCP_FLOW_NOM);
            state_.mcp_flow[i]  += (target_flow  - state_.mcp_flow[i])  * dt / 5.0;
            state_.mcp_speed[i] += (target_speed - state_.mcp_speed[i]) * dt / 5.0;
            // Şebeke kaybında pompalar yavaşlar
            if (state_.station_blackout && !state_.diesel_active) {
                state_.mcp_flow[i]  = std::max(0.0, state_.mcp_flow[i]  - 30.0 * dt);
                state_.mcp_speed[i] = std::max(0.0, state_.mcp_speed[i] - 50.0 * dt);
                if (state_.mcp_speed[i] < 10.0) state_.mcp_active[i] = false;
            }
        } else {
            state_.mcp_flow[i]  = std::max(0.0, state_.mcp_flow[i]  - 100.0 * dt);
            state_.mcp_speed[i] = std::max(0.0, state_.mcp_speed[i] - 150.0 * dt);
        }
    }
}

// ─── TÜRBİNLER ───────────────────────────────────────────────────────────────
void ReactorEngine::updateTurbines(double dt) {
    for (int t = 0; t < NUM_TURBINES; t++) {
        if (state_.turbine_trip[t]) {
            // Trip: türbini hızla durdur
            state_.turbine_speed[t] = std::max(0.0, state_.turbine_speed[t] - 200.0 * dt);
            state_.turbine_load[t]  = std::max(0.0, state_.turbine_load[t]  - 100.0 * dt);
            state_.turbine_online[t] = (state_.turbine_speed[t] > 50.0);
        } else if (state_.turbine_rundown[t]) {
            // Coast-down testi: türbin yavaşlar ama buhar almadan döner (atalet)
            double inertia_decay = 1.0 / 60.0;  // 60 saniyede durur
            state_.turbine_speed[t] = std::max(0.0, state_.turbine_speed[t] * (1.0 - inertia_decay * dt));
            // Düşen türbin hızı → daha az elektrik → pompalar yavaşlar
            state_.turbine_rundown_time[t] += dt;
        } else if (state_.turbine_online[t]) {
            // Normal operasyon
            double target_speed = 3000.0;
            double steam_available = state_.steam_pressure > 3.0 ? 1.0 :
                                     state_.steam_pressure / 3.0;
            double target_load = RATED_ELEC_MW * 0.5 * state_.turbine_valve[t] * steam_available;
            state_.turbine_speed[t] += (target_speed - state_.turbine_speed[t]) * dt / 10.0;
            state_.turbine_load[t]  += (target_load  - state_.turbine_load[t])  * dt / 5.0;
        }
    }
}

// ─── OTOMATİK GÜVENLİK SİSTEMLERİ ───────────────────────────────────────────
void ReactorEngine::updateSafetySystemsAutomatic(double dt) {
    // Otomatik SCRAM koşulları
    if (!state_.scram_active && !state_.az5_pressed) {
        // 1. Güç > %115
        if (state_.power_frac > 1.15) {
            triggerScram("ЗАЩИТА ПО МОЩНОСТИ", "Otomatik SCRAM: Güç %115 sınırını aştı!");
        }
        // 2. Buhar basıncı > 8.0 MPa
        else if (state_.steam_pressure > 8.0) {
            triggerScram("ЗАЩИТА ПО ДАВЛЕНИЮ", "Otomatik SCRAM: Buhar basıncı kritik seviyede!");
        }
        // 3. Tüm tambur seviyeleri düşük
        bool all_drums_low = true;
        for (int i = 0; i < NUM_DRUMS; i++) {
            if (state_.drum_level[i] > 10.0) { all_drums_low = false; break; }
        }
        if (all_drums_low) {
            triggerScram("ЗАЩИТА ПО УРОВНЮ В БС", "Otomatik SCRAM: Tambur seviyesi kritik!");
        }
    }

    // Otomatik Regülasyon (AR) - güç sapmalarını düzeltir
    if (state_.ar_active && !state_.scram_active && state_.power_frac > 0.05) {
        // AR çubukları (ilk 12) otomatik hareket eder
        double power_error = 0.0;  // AR setpoint yok, sadece dengeleyici
        // Gerçekçi AR: operatör setpoint'e yönelir
        // Burada basit implementasyon: AR çubukları 10-12. çubuklar arasında
        // dalgalanmayı otomatik compensate eder (±%2 hassasiyet)
    }

    // Dizel jeneratör otomatik başlatma (şebeke kaybında)
    if (state_.station_blackout && !state_.diesel_active) {
        // 10-15 saniye sonra dizel başlar
        // (Operatör manuel başlatabilir)
    }

    // ECCS (SAOR) otomatik aktivasyon
    if (!state_.eccs_active) {
        if (state_.coolant_pressure < 4.0 && state_.power_frac > 0.0) {
            activateECCS("Düşük soğutucu basıncı - SAOR otomatik devreye girdi!");
        }
        if (state_.fuel_channel_rupture) {
            activateECCS("Yakıt kanalı patlaması - SAOR devreye alındı!");
        }
    }
}

// ─── REAKTÖR DURUM GÜNCELLEMESİ ─────────────────────────────────────────────
void ReactorEngine::updateReactorStatus() {
    if (state_.reactor_destroyed) { state_.status = ReactorStatus::EXPLOSION;  return; }
    if (state_.fuel_melt)         { state_.status = ReactorStatus::MELTDOWN;   return; }
    if (state_.scram_active)      { state_.status = ReactorStatus::SCRAM;      return; }

    double P = state_.power_frac;
    if (P < 0.001)      state_.status = ReactorStatus::COLD_SHUTDOWN;
    else if (P < 0.05)  state_.status = ReactorStatus::HOT_SHUTDOWN;
    else if (P < 0.10)  state_.status = ReactorStatus::STARTUP;
    else if (P < 0.30)  state_.status = ReactorStatus::LOW_POWER;
    else if (P < 0.95)  state_.status = ReactorStatus::POWER_ASCENT;
    else if (P <= 1.07) state_.status = ReactorStatus::FULL_POWER;
    else                state_.status = ReactorStatus::TRANSIENT;
}

// ─── ALARM YÖNETİMİ ──────────────────────────────────────────────────────────
void ReactorEngine::checkAndUpdateAlarms() {
    double P = state_.power_frac;
    int    orm = state_.orm;

    // Güç alarmları
    if (P < 0.05 && P > 0.001)
        addAlarm(AlarmType::LOW_POWER, AlarmSeverity::WARNING,
                 "МАЛАЯ МОЩНОСТЬ", "Düşük güç: %5 altında");
    else removeAlarm(AlarmType::LOW_POWER);

    if (P > 1.07)
        addAlarm(AlarmType::HIGH_POWER, AlarmSeverity::ALERT,
                 "ВЫСОКАЯ МОЩНОСТЬ", "Yüksek güç: %107 üzerinde!");
    else removeAlarm(AlarmType::HIGH_POWER);

    // ORM alarmları
    if (orm < MIN_ORM && orm >= CRITICAL_ORM)
        addAlarm(AlarmType::LOW_ORM, AlarmSeverity::WARNING,
                 "МАЗ МЕНЕЕ 15", "ORM < 15: Minimum marj uyarısı!");
    else removeAlarm(AlarmType::LOW_ORM);

    if (orm < CRITICAL_ORM)
        addAlarm(AlarmType::CRITICAL_ORM, AlarmSeverity::EMERGENCY,
                 "МАЗ КРИТИЧЕСКИЙ", "KRİTİK: ORM < 7 çubuk! DERHAL çubuk ekle!");
    else removeAlarm(AlarmType::CRITICAL_ORM);

    // Basınç alarmları
    if (state_.steam_pressure > MAX_PRESSURE - 0.5)
        addAlarm(AlarmType::HIGH_STEAM_PRESS, AlarmSeverity::ALERT,
                 "ДАВЛЕНИЕ ПАРА ВЫСОКОЕ", "Buhar basıncı kritik seviyeye yaklaşıyor!");
    else removeAlarm(AlarmType::HIGH_STEAM_PRESS);

    if (state_.steam_pressure < MIN_PRESSURE && state_.power_frac > 0.1)
        addAlarm(AlarmType::LOW_STEAM_PRESS, AlarmSeverity::WARNING,
                 "ДАВЛЕНИЕ ПАРА НИЗКОЕ", "Buhar basıncı düşük!");
    else removeAlarm(AlarmType::LOW_STEAM_PRESS);

    // Soğutucu sıcaklık alarmı
    if (state_.coolant_temp_out > 320.0)
        addAlarm(AlarmType::HIGH_COOLANT_TEMP, AlarmSeverity::ALERT,
                 "ТЕМП. ТЕПЛОНОСИТЕЛЯ ВЫС.", "Soğutucu çıkış sıcaklığı yüksek!");
    else removeAlarm(AlarmType::HIGH_COOLANT_TEMP);

    // Tambur seviyesi
    for (int i = 0; i < NUM_DRUMS; i++) {
        if (state_.drum_level[i] < MIN_DRUM_LVL) {
            addAlarm(AlarmType::LOW_DRUM_LEVEL, AlarmSeverity::ALERT,
                     "УРОВЕНЬ БС НИЗКИЙ", "Tambur " + std::to_string(i+1) + " seviyesi kritik!");
            break;
        }
        if (state_.drum_level[i] > MAX_DRUM_LVL) {
            addAlarm(AlarmType::HIGH_DRUM_LEVEL, AlarmSeverity::WARNING,
                     "УРОВЕНЬ БС ВЫСОКИЙ", "Tambur " + std::to_string(i+1) + " taşıyor!");
            break;
        }
    }

    // Pompa alarmları
    for (int i = 0; i < NUM_MCP; i++) {
        if (state_.mcp_failed[i]) {
            addAlarm(AlarmType::MCP_TRIP, AlarmSeverity::EMERGENCY,
                     "ОТКАЗ ГЦН-" + std::to_string(i+1),
                     "GTs-" + std::to_string(i+1) + " (Ana Dolaşım Pompası) ARIZA!");
        }
    }

    // Pozitif geri besleme
    if (state_.reactivity_void > 0.5 && state_.power_frac > 0.0)
        addAlarm(AlarmType::POSITIVE_FEEDBACK, AlarmSeverity::EMERGENCY,
                 "ПОЛОЖИТЕЛЬНАЯ ОБРАТНАЯ СВЯЗЬ",
                 "POZİTİF GERİ BESLEME! Boşluk reaktivitesi kontrol dışı yükseliyor!");
    else removeAlarm(AlarmType::POSITIVE_FEEDBACK);

    // Yakıt sıcaklığı
    if (state_.fuel_temp > 1200.0)
        addAlarm(AlarmType::HIGH_FUEL_TEMP, AlarmSeverity::EMERGENCY,
                 "ТЕМП. ТОПЛИВА КРИТИЧЕСКАЯ", "Yakıt sıcaklığı kritik!");
    else removeAlarm(AlarmType::HIGH_FUEL_TEMP);

    // ECCS
    if (state_.eccs_active)
        addAlarm(AlarmType::ECCS_ACTIVE, AlarmSeverity::ALERT,
                 "САОР АКТИВИРОВАНА", "SAOR (Acil Soğutma Sistemi) aktif!");

    // Sızıntı
    if (state_.coolant_leak)
        addAlarm(AlarmType::COOLANT_LEAK, AlarmSeverity::EMERGENCY,
                 "ТЕЧЬ ПЕРВОГО КОНТУРА", "Birinci devre sızıntısı tespit edildi!");

    // Xenon tuzağı
    if (state_.xenon > 1.7 && state_.power_frac < 0.05)
        addAlarm(AlarmType::XENON_TRAP, AlarmSeverity::ALERT,
                 "КСЕНОНОВОЕ ОТРАВЛЕНИЕ",
                 "Xenon zehirlenmesi: Reaktörü yeniden başlatmak çok zor!");
    else removeAlarm(AlarmType::XENON_TRAP);
}

void ReactorEngine::addAlarm(AlarmType type, AlarmSeverity sev,
                               const std::string& code_ru, const std::string& msg_tr) {
    for (auto& a : state_.active_alarms) {
        if (a.type == type) { a.active = true; return; }
    }
    Alarm a;
    a.type = type; a.severity = sev;
    a.message = code_ru; a.message_tr = msg_tr;
    a.timestamp = state_.simulation_time;
    a.acknowledged = false; a.active = true;
    state_.active_alarms.push_back(a);
    log("[ALARM] " + code_ru + " - " + msg_tr, "ALARM", (int)sev);
}

void ReactorEngine::removeAlarm(AlarmType type) {
    state_.active_alarms.erase(
        std::remove_if(state_.active_alarms.begin(), state_.active_alarms.end(),
                       [type](const Alarm& a) { return a.type == type; }),
        state_.active_alarms.end());
}

bool ReactorEngine::hasAlarm(AlarmType type) const {
    for (const auto& a : state_.active_alarms)
        if (a.type == type) return true;
    return false;
}

// ─── SCRAM / ECCS ─────────────────────────────────────────────────────────────
void ReactorEngine::triggerScram(const std::string& reason_ru, const std::string& reason_tr) {
    if (state_.scram_active) return;
    state_.scram_active = true;
    state_.az5_pressed  = true;
    // Tüm çubukları içeri sok
    state_.rod_target.fill(0.0);
    addAlarm(AlarmType::SCRAM_INITIATED, AlarmSeverity::EMERGENCY,
             "АВАРИЙНАЯ ЗАЩИТА - " + reason_ru, "SCRAM: " + reason_tr);
    log("!!! АЗ-5 СРАБОТАЛА !!! " + reason_ru + " | " + reason_tr, "ACİL", 3);
}

void ReactorEngine::activateECCS(const std::string& reason_tr) {
    if (state_.eccs_active) return;
    state_.eccs_active = true;
    log("САОР АКТИВИРОВАНА - " + reason_tr, "ACİL", 2);
}

// ─── ORM HESAPLAMA ────────────────────────────────────────────────────────────
int ReactorEngine::calculateORM() const {
    // ORM = toplam etkin rod sayısı
    // Her çubuk için: reaktivite değeri × pozisyon (1.0 = tam içeride = -ROD_WORTH_AVG)
    // ORM = toplam çubuğun reaktivite eşdeğeri / ortalama çubuk değeri
    double total_inserted = 0.0;
    for (int i = 0; i < NUM_CONTROL_RODS; i++) {
        total_inserted += state_.rod_pos[i];  // 0=dışarıda, 1=içeride
    }
    return (int)total_inserted;
}

// ─── OPERATöR KONTROL GİRİŞİ ─────────────────────────────────────────────────
void ReactorEngine::applyControl(const ControlInput& input) {
    // Kontrol çubuğu hedefleri
    for (int i = 0; i < NUM_CONTROL_RODS; i++) {
        if (!state_.scram_active) {
            state_.rod_target[i] = clamp(input.rod_targets[i], 0.0, 1.0);
        }
    }
    // Pompa kontrolleri
    for (int i = 0; i < NUM_MCP; i++) {
        if (!state_.mcp_failed[i]) {
            state_.mcp_active[i]   = input.mcp_active[i];
            state_.mcp_setpoint[i] = clamp(input.mcp_setpoints[i], 0.0, MCP_FLOW_NOM * 1.1);
        }
    }
    // Su ve türbin kontrolleri
    state_.feedwater_setpoint = clamp(input.feedwater_setpoint, 0.0, FEEDWATER_NOM * 1.5);
    for (int t = 0; t < NUM_TURBINES; t++) {
        if (!state_.turbine_trip[t]) {
            state_.turbine_valve[t] = clamp(input.turbine_valve[t], 0.0, 1.0);
        }
        if (input.turbine_trip_cmd[t] && !state_.turbine_trip[t]) {
            state_.turbine_trip[t] = true;
            log("ТГ-" + std::to_string(t+1) + " ОТКЛЮЧЕН - Türbin-" +
                std::to_string(t+1) + " trip edildi.", "OPERATÖR", 1);
        }
    }
    // AZ-5
    if (input.az5_press && !state_.az5_blocked) {
        triggerScram("РУЧНОЙ ОСТАНОВ АЗ-5", "Operatör AZ-5 butonuna bastı - SCRAM!");
    }
    // ECCS
    if (input.eccs_request) activateECCS("Operatör SAOR'u devreye aldı.");
    // Dizel
    if (input.diesel_start) {
        state_.diesel_active = true;
        state_.diesel_power = 500.0;  // kW
        log("ДГ ЗАПУЩЕН - Dizel jeneratör başlatıldı.", "SİSTEM", 1);
    }
    // Alarm onay
    if (input.ack_alarms) {
        for (auto& a : state_.active_alarms) a.acknowledged = true;
    }
    state_.ar_active = input.ar_enable;
}

// ─── SENARYO TETİKLEYİCİLERİ ─────────────────────────────────────────────────
void ReactorEngine::triggerMCPFailure(int idx) {
    if (idx < 0 || idx >= NUM_MCP) return;
    state_.mcp_failed[idx] = true;
    addAlarm(AlarmType::MCP_TRIP, AlarmSeverity::EMERGENCY,
             "ОТКАЗ ГЦН-" + std::to_string(idx+1),
             "GTs-" + std::to_string(idx+1) + " ARIZA - Pompa çalışmıyor!");
    log("ГЦН-" + std::to_string(idx+1) + " АВАРИЯ - Pompa trip edildi!", "ACİL", 3);
}

void ReactorEngine::triggerCoolantLeak(double rate) {
    state_.coolant_leak = true;
    state_.leak_rate    = rate;
    addAlarm(AlarmType::COOLANT_LEAK, AlarmSeverity::EMERGENCY,
             "ТЕЧЬ ПЕРВОГО КОНТУРА", "Soğutucu sızıntısı: " +
             std::to_string((int)rate) + " kg/s!");
    log("ТЕЧЬ - Soğutucu sızıntısı tespit edildi: " + std::to_string((int)rate) + " kg/s", "ACİL", 3);
}

void ReactorEngine::triggerFuelChannelRupture() {
    state_.fuel_channel_rupture = true;
    state_.coolant_leak = true;
    state_.leak_rate = 500.0;  // Büyük sızıntı
    addAlarm(AlarmType::FUEL_CHANNEL_RUPTURE, AlarmSeverity::FATAL,
             "РАЗРЫВ ТЕХНОЛОГИЧЕСКОГО КАНАЛА",
             "YAKIT KANALI PATLAMASI! Acil soğutma gerekli!");
    log("!!! РАЗРЫВ ТК !!! - YAKIT KANALI PATLAMASI! " + formatTime(state_.simulation_time),
        "FATAL", 3);
}

void ReactorEngine::triggerXenonTrap() {
    state_.xenon  = 1.90;
    state_.iodine = 0.50;
    log("КСЕНОНОВАЯ ЛОВУШКА - Xenon tuzağı: Yeniden başlatma çok zor!", "SİSTEM", 2);
}

void ReactorEngine::triggerStationBlackout() {
    state_.station_blackout = true;
    state_.grid_connected   = false;
    // Tüm harici güç kaybı → pompalar yavaşlamaya başlar
    addAlarm(AlarmType::MCP_LOW_FLOW, AlarmSeverity::EMERGENCY,
             "ПОТЕРЯ ЭЛЕКТРОПИТАНИЯ", "Şebeke kaybı! İstasyon karartması başladı!");
    log("ПОТЕРЯ ПИТАНИЯ - Şebeke kaybı. Acil sistemler devreye giriyor!", "ACİL", 3);
}

// ─── LEVEL 16: SCRIPTED SENARYO ──────────────────────────────────────────────
void ReactorEngine::triggerLevel16Sequence(double elapsed) {
    l16_seq_time_ = elapsed;

    // 0-60s: Güç azaltma fazı - her şey normal, operatör test için çubukları çekiyor
    if (elapsed < 60.0) return;

    // 60-120s: Güç çukur - xenon güçü bastırıyor
    if (elapsed >= 60.0 && elapsed < 120.0) {
        if (elapsed < 62.0) {
            state_.xenon = 1.60;  // Xenon giderek yükseliyor
            log("МОЩНОСТЬ УПАЛА - Güç xenon nedeniyle düşüyor: " +
                fmtMW(state_.power_mw), "REAKTÖR", 2);
        }
    }

    // 120-240s: ORM kritik düzeyde düşük, çubuklar çekilmiş
    if (elapsed >= 120.0 && elapsed < 240.0) {
        // Zorla ORM'u düşür (çernobil'deki gibi operatörler çubukları çekiyor)
        if (state_.orm > 8 && elapsed < 125.0) {
            // Çubukların büyük çoğunluğunu dışarı çek
            for (int i = 0; i < 195; i++) state_.rod_pos[i] = 0.05;
            for (int i = 195; i < NUM_CONTROL_RODS; i++) state_.rod_pos[i] = 0.03;
            log("МАЗ КРИТИЧЕСКИЙ (" + std::to_string(calculateORM()) +
                " стержней) - ORM kritik! Sadece " + std::to_string(calculateORM()) +
                " çubuk içeride!", "ACİL", 3);
        }
    }

    // 240-300s: Türbin coast-down testi başlıyor
    if (elapsed >= 240.0 && elapsed < 244.0) {
        state_.turbine_rundown[0] = true;
        state_.turbine_online[0]  = false;
        log("ТГ-1 ОТКЛЮЧЕН - Türbin-1 şebekeden ayrıldı. Coast-down testi başlıyor.",
            "OPERATÖR", 1);
    }

    // 250-290s: Soğutucu akışı azalıyor (türbin yavaşladıkça pompalar da yavaşlar)
    if (elapsed >= 250.0 && elapsed < 290.0) {
        // Pompalar yavaşlıyor (türbin enerji vermiyor)
        for (int i = 0; i < 4; i++) {
            state_.mcp_flow[i] *= (1.0 - 0.005 * (elapsed - 250.0));
        }
        // Void artıyor
        if (state_.void_fraction < 0.50) {
            state_.void_fraction += 0.01 * (elapsed - 250.0) / 40.0;
        }
    }

    // 290s: Void artışı güç artışını tetikliyor (pozitif geri besleme)
    if (elapsed >= 290.0 && elapsed < 295.0) {
        log("МОЩНОСТЬ РАСТЁТ - Void artışı güç artışını başlattı! " +
            fmtMW(state_.power_mw), "ACİL", 3);
    }

    // 295s: AZ-5 basılıyor (operatör fark etti)
    if (elapsed >= 295.0 && elapsed < 296.0) {
        if (!state_.az5_pressed && !state_.az5_blocked) {
            log("АЗ-5 НАЖАТА - AZ-5 basıldı: " + formatTime(state_.simulation_time) +
                " - Ancak çok geç!", "OPERATÖR", 2);
            state_.az5_pressed  = true;
            state_.scram_active = true;
            state_.rod_target.fill(0.0);
        }
    }

    // 295-300s: Grafit ucu etkisi - AZ-5 sonrası anlık güç artışı (ölümcül)
    if (elapsed >= 295.5 && elapsed < 298.0) {
        // Bu kaçınılmaz: grafit uçlar reaktivite ekliyor
        double graphite_surge = 2.0 + (elapsed - 295.5) * 5.0;  // Hızla artan reaktivite
        state_.reactivity_total += graphite_surge;
        state_.power_frac += graphite_surge * 0.5 * (elapsed - 295.5);
        state_.power_mw = state_.power_frac * RATED_POWER_MW;
        if (state_.power_mw > 5000.0 && elapsed > 296.0) {
            log("МОЩНОСТЬ " + fmtMW(state_.power_mw) +
                " - KONTROL MÜMKÜN DEĞİL! Grafit uç etkisi aktif!", "FATAL", 3);
        }
    }

    // 298s: PATLAMA - kaçınılmaz
    if (elapsed >= 298.0 && !state_.reactor_destroyed) {
        state_.power_frac = 30.0;  // 10× rated power
        state_.power_mw   = RATED_POWER_MW * 30.0;
        state_.reactor_destroyed = true;
        state_.mission_failed = false;  // Level 16'da "başarısızlık" değil, senaryo
        state_.mission_complete = true; // Senaryo tamamlandı (patlama scripted)
        state_.status = ReactorStatus::EXPLOSION;
        addAlarm(AlarmType::REACTOR_EXPLOSION, AlarmSeverity::FATAL,
                 "ТЕПЛОВОЙ ВЗРЫВ РЕАКТОРА",
                 "NÜKLEER PATLAMA! 26 Nisan senaryosu tamamlandı.");
        log("!!! РЕАКТОР УНИЧТОЖЕН !!! Güç: " + fmtMW(state_.power_mw) +
            " | 01:23:47 - Nükleer patlama meydana geldi.", "FATAL", 3);
    }
}

// ─── GÖREV KONTROL FONKSİYONLARI ─────────────────────────────────────────────
void ReactorEngine::checkMissionConditions(int level) {
    if (state_.mission_failed || state_.mission_complete) return;

    switch (level) {
    case 5: // Pompa arızası
        // 30 saniye sonra MCP arızasını tetikle
        if (!l5_mcp_failed_ && state_.simulation_time > 30.0) {
            triggerMCPFailure(2);  // GTs-3 arıza
            l5_mcp_failed_ = true;
        }
        // Görev: Soğutucu sıcaklığını < 320°C altında tut, reaktörü stabilize et
        if (state_.coolant_temp_out > 335.0) {
            state_.mission_failed = true;
            state_.failure_reason = "Soğutucu sıcaklığı 335°C'yi aştı - Reaktör hasarı!";
        }
        // 5 dakika soğutucu < 310°C → başarı
        if (state_.coolant_temp_out < 310.0 && state_.simulation_time > 60.0) {
            static double stable_start = 0.0;
            if (stable_start == 0.0) stable_start = state_.simulation_time;
            if (state_.simulation_time - stable_start > 300.0) {
                state_.mission_complete = true;
                log("GÖREV TAMAMLANDI - Pompa arızası başarıyla yönetildi!", "SİSTEM", 0);
            }
        }
        break;

    case 10: // İstasyon karartması
        // 20 saniye sonra karartma
        if (!state_.station_blackout && state_.simulation_time > 20.0) {
            triggerStationBlackout();
            l10_blackout_time_ = state_.simulation_time;
        }
        // Dizel başlatılırsa ve soğutma devam ediyorsa başarı
        if (state_.diesel_active && !l10_diesel_done_) {
            l10_diesel_done_ = true;
            log("DİZEL BAŞARILDI - Acil güç sistemi devrede!", "SİSTEM", 1);
        }
        // Soğutucu akışı sıfıra düşerse başarısız
        if (state_.coolant_flow_total < 500.0 && state_.simulation_time > 60.0) {
            state_.mission_failed = true;
            state_.failure_reason = "Soğutucu akışı kesildi - Reaktör çekirdeği aşırı ısındı!";
        }
        // Dizel + kontrollü shutdown başarısı
        if (l10_diesel_done_ && state_.scram_active && state_.simulation_time > 120.0) {
            state_.mission_complete = true;
        }
        break;

    case 15: // Yakıt kanalı patlaması
        // 15 saniye sonra yakıt kanalı patlar
        if (!state_.fuel_channel_rupture && state_.simulation_time > 15.0) {
            triggerFuelChannelRupture();
        }
        // ECCS + SCRAM yapılırsa ve 5 dakika sonra soğutma yeterliyse başarı
        if (state_.eccs_active && state_.scram_active) {
            l15_cooling_time_ += 1.0;
            if (l15_cooling_time_ > 300.0 && state_.fuel_temp < 600.0) {
                l15_eccs_done_ = true;
                state_.mission_complete = true;
                log("GÖREV TAMAMLANDI - Yakıt kanalı patlaması başarıyla kontrol altına alındı!",
                    "SİSTEM", 0);
            }
        }
        if (state_.fuel_melt) {
            state_.mission_failed = true;
            state_.failure_reason = "Yakıt erimesi gerçekleşti - Yetersiz soğutma!";
        }
        break;

    case 16: // Scripted
        triggerLevel16Sequence(state_.simulation_time);
        break;

    default:
        // Genel başarısızlık koşulları
        if (state_.reactor_destroyed) {
            state_.mission_failed = true;
            state_.failure_reason = "Reaktör patladı!";
        }
        break;
    }
}

// ─── DURUM ERIŞIMI ───────────────────────────────────────────────────────────
ReactorState ReactorEngine::getState() const { return state_; }

std::vector<LogEntry> ReactorEngine::getAndClearLog() {
    auto tmp = log_buffer_;
    log_buffer_.clear();
    return tmp;
}

// ─── LOGGING ─────────────────────────────────────────────────────────────────
void ReactorEngine::log(const std::string& msg, const std::string& cat, int sev) {
    LogEntry e;
    e.timestamp = state_.simulation_time;
    e.message   = "[" + formatTime(state_.simulation_time) + "] " + msg;
    e.category  = cat;
    e.severity  = sev;
    log_buffer_.push_back(e);
}

// ─── ROD GRUBU KONTROLÜ ───────────────────────────────────────────────────────
void ReactorEngine::setRodGroupTarget(int group, double position) {
    // 0=AR, 1-6=Manuel grupları
    // Her grup ~30 çubuk
    int start = group * 30;
    int end   = std::min(start + 30, NUM_CONTROL_RODS);
    for (int i = start; i < end; i++) {
        state_.rod_target[i] = clamp(position, 0.0, 1.0);
    }
}

bool ReactorEngine::isReactorSafe() const {
    return !state_.reactor_destroyed && !state_.fuel_melt &&
           state_.orm >= CRITICAL_ORM &&
           state_.coolant_temp_out < MAX_CLNT_TEMP &&
           state_.steam_pressure < MAX_PRESSURE;
}

bool ReactorEngine::isMissionFailed() const  { return state_.mission_failed; }
bool ReactorEngine::isMissionComplete(int) const { return state_.mission_complete; }

double ReactorEngine::getMissionScore(int level) const {
    if (state_.mission_failed) return 0.0;
    // Temel skor: güvenlik marjlarına ve hız/verimlilik kriterlerine göre
    double score = 100.0;
    // ORM ihlalleri ceza
    if (state_.orm < MIN_ORM) score -= 20.0;
    // Alarm sayısı ceza
    score -= state_.active_alarms.size() * 5.0;
    return std::max(0.0, std::min(100.0, score));
}

double ReactorEngine::gaussian_noise(double sigma) {
    std::normal_distribution<double> dist(0.0, sigma);
    return dist(rng_);
}

} // namespace rbmk
