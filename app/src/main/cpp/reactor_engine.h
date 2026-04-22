#pragma once
#include <array>
#include <vector>
#include <string>
#include <cmath>
#include <algorithm>
#include <random>

namespace rbmk {

constexpr double BETA            = 0.0065;
constexpr double LAMBDA_EFF      = 0.08;
constexpr double NEUTRON_LIFE    = 0.5;
constexpr double RATED_POWER_MW  = 3200.0;
constexpr double RATED_ELEC_MW   = 1000.0;

constexpr int NUM_CONTROL_RODS  = 211;
constexpr int NUM_MCP           = 8;
constexpr int NUM_DRUMS         = 2;
constexpr int NUM_TURBINES      = 2;

constexpr double LAMBDA_I        = 2.87e-5;
constexpr double LAMBDA_XE       = 2.09e-5;
constexpr double XE_BURNOUT      = 0.080;
constexpr double XENON_SPEED     = 50.0;

constexpr double COOLANT_IN_NOM  = 265.0;
constexpr double COOLANT_OUT_NOM = 284.0;
constexpr double PRESSURE_NOM    = 6.90;
constexpr double STEAM_PRESS_NOM = 6.50;
constexpr double MCP_FLOW_NOM    = 850.0;
constexpr double FEEDWATER_NOM   = 6800.0;
constexpr double FUEL_TEMP_NOM   = 650.0;

constexpr int    MIN_ORM         = 15;
constexpr int    CRITICAL_ORM    = 7;
constexpr double MAX_CLNT_TEMP   = 350.0;
constexpr double MAX_PRESSURE    = 8.50;
constexpr double MIN_PRESSURE    = 5.00;
constexpr double MIN_DRUM_LVL    = 10.0;
constexpr double MAX_DRUM_LVL    = 90.0;
constexpr double MELTDOWN_POWER  = 9600.0;
constexpr double EXPLOSION_POWER = 32000.0;

// Reaktivite model sabitleri
constexpr double K_ROD_WORTH     = 0.10;
constexpr double K_XENON         = 1.5;
constexpr double K_VOID          = 20.0;
constexpr double K_DOPPLER       = 0.0042;
constexpr double POWER_TAU       = 25.0;
constexpr double CRITICAL_ORM_THRESHOLD = 15.0;

static inline double clamp(double v, double lo, double hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

enum class AlarmType : int {
    NONE=0, LOW_POWER, HIGH_POWER, SCRAM_AUTO, LOW_ORM, CRITICAL_ORM,
    HIGH_STEAM_PRESS, LOW_STEAM_PRESS, HIGH_COOLANT_TEMP,
    LOW_DRUM_LEVEL, HIGH_DRUM_LEVEL, MCP_TRIP, MCP_LOW_FLOW, XENON_TRAP,
    HIGH_FUEL_TEMP, SCRAM_INITIATED, POWER_EXCURSION, ECCS_ACTIVE,
    COOLANT_LEAK, FUEL_CHANNEL_RUPTURE, POSITIVE_FEEDBACK, REACTOR_EXPLOSION
};

enum class AlarmSeverity : int { INFO=0, WARNING=1, ALERT=2, EMERGENCY=3, FATAL=4 };

enum class ReactorStatus : int {
    COLD_SHUTDOWN=0, HOT_SHUTDOWN=1, STARTUP=2, LOW_POWER=3,
    POWER_ASCENT=4, FULL_POWER=5, POWER_REDUCTION=6, TRANSIENT=7,
    SCRAM=8, MELTDOWN=9, EXPLOSION=10
};

struct Alarm {
    AlarmType     type;
    AlarmSeverity severity;
    std::string   message;
    std::string   message_tr;
    double        timestamp;
    bool          acknowledged;
    bool          active;
};

struct LogEntry {
    double      timestamp;
    std::string message;
    std::string category;
    int         severity;
};

struct ReactorState {
    double power_mw=0,power_frac=0,power_rate_mw_s=0,neutron_flux=0,precursor=0;
    double reactivity_total=0,reactivity_rods=0,reactivity_xenon=0,reactivity_void=0,reactivity_doppler=0;
    double iodine=0,xenon=0;
    std::array<double,NUM_CONTROL_RODS> rod_pos,rod_target;
    std::array<bool,NUM_CONTROL_RODS>   rod_moving;
    int orm=0;
    double coolant_temp_in=COOLANT_IN_NOM,coolant_temp_out=COOLANT_OUT_NOM;
    double coolant_pressure=PRESSURE_NOM,coolant_flow_total=0,void_fraction=0;
    bool coolant_leak=false; double leak_rate=0;
    double fuel_temp=20; bool fuel_melt=false,fuel_channel_rupture=false;
    double steam_pressure=0,steam_flow=0,feedwater_flow=0,feedwater_setpoint=FEEDWATER_NOM;
    double drum_level[NUM_DRUMS]={50,50};
    double mcp_flow[NUM_MCP]={},mcp_speed[NUM_MCP]={},mcp_setpoint[NUM_MCP]={};
    bool   mcp_active[NUM_MCP]={},mcp_failed[NUM_MCP]={};
    double turbine_speed[NUM_TURBINES]={},turbine_load[NUM_TURBINES]={};
    double turbine_valve[NUM_TURBINES]={1,1};
    bool   turbine_online[NUM_TURBINES]={},turbine_trip[NUM_TURBINES]={};
    bool   turbine_rundown[NUM_TURBINES]={}; double turbine_rundown_time[NUM_TURBINES]={};
    bool scram_active=false,az5_pressed=false,az5_blocked=false;
    bool eccs_active=false,ar_active=true,skala_active=true;
    bool grid_connected=true,station_blackout=false,diesel_active=false;
    double diesel_power=0;
    ReactorStatus status=ReactorStatus::COLD_SHUTDOWN;
    bool reactor_destroyed=false; double simulation_time=0;
    std::vector<Alarm> active_alarms;
    std::string failure_reason;
    int current_level=1; bool mission_failed=false,mission_complete=false;
    double mission_score=0;
};

struct ControlInput {
    std::array<double,NUM_CONTROL_RODS> rod_targets;
    double mcp_setpoints[NUM_MCP]; bool mcp_active[NUM_MCP];
    double feedwater_setpoint;
    double turbine_valve[NUM_TURBINES]; bool turbine_trip_cmd[NUM_TURBINES];
    bool az5_press,eccs_request,ar_enable,ack_alarms,diesel_start;
};

class ReactorEngine {
public:
    ReactorEngine();
    void initialize(bool cold_start=true, int level=1);
    void step(double dt);
    void applyControl(const ControlInput& input);
    ReactorState          getState() const;
    std::vector<LogEntry> getAndClearLog();
    void setLevelConditions(int level);
    void triggerMCPFailure(int pump_idx);
    void triggerCoolantLeak(double rate);
    void triggerFuelChannelRupture();
    void triggerStationBlackout();
    void triggerLevel16Sequence(double T, double dt);
    void setRodGroupTarget(int group, double position);
    int  calculateORM() const;
    bool isReactorSafe() const;
    bool isMissionFailed() const;
    bool isMissionComplete(int) const;
    double getMissionScore(int) const;

private:
    ReactorState          state_;
    std::vector<LogEntry> log_buffer_;
    std::mt19937          rng_;
    double scram_time_=1e9;
    double l_timer1_=0,l_timer2_=0,l_timer3_=0,l_timer4_=0;
    bool   l_flag1_=false,l_flag2_=false,l_flag3_=false;
    double l_min_val_=999,l_max_val_=-999;

    void updateControlRods(double dt);
    void updateNeutronics(double dt);
    void updateXenon(double dt);
    void updateThermalHydraulics(double dt);
    void updateSteamSystem(double dt);
    void updatePumps(double dt);
    void updateTurbines(double dt);
    void updateSafetySystemsAutomatic(double dt);
    void updateReactorStatus();
    void checkMissionConditions(int level, double dt);
    double calcRodReactivity() const;
    double calcXenonReactivity() const;
    double calcVoidReactivity() const;
    double calcDopplerReactivity() const;
    double calcTotalReactivity() const;
    void checkAndUpdateAlarms();
    void addAlarm(AlarmType,AlarmSeverity,const std::string&,const std::string&);
    void removeAlarm(AlarmType);
    bool hasAlarm(AlarmType) const;
    void log(const std::string&,const std::string&,int);
    void triggerScram(const std::string& reason);
    void activateECCS(const std::string& reason);
    void setupLevel1();  void setupLevel2();  void setupLevel3();
    void setupLevel4();  void setupLevel5();  void setupLevel6();
    void setupLevel7();  void setupLevel8();  void setupLevel9();
    void setupLevel10(); void setupLevel11(); void setupLevel12();
    void setupLevel13(); void setupLevel14(); void setupLevel15();
    void setupLevel16();
    double gaussian_noise(double sigma);
};

} // namespace rbmk
