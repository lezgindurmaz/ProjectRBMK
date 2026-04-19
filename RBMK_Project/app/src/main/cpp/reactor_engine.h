#pragma once
#include <array>
#include <vector>
#include <string>
#include <cmath>
#include <algorithm>
#include <random>

namespace rbmk {

// ─── FİZİKSEL SABİTLER ─────────────────────────────────────────────────────
constexpr double BETA            = 0.0065;    // Gecikmeli nötron fraksiyonu (U-235)
constexpr double LAMBDA_EFF      = 0.08;      // Efektif bozunma sabiti (s⁻¹)
constexpr double NEUTRON_LIFE    = 0.5;       // Anlık nötron ömrü (s) - grafit için uzun
constexpr double RATED_POWER_MW  = 3200.0;    // Nominal termal güç (MW)
constexpr double RATED_ELEC_MW   = 1000.0;    // Nominal elektrik gücü (MWe)

// ─── REAKTÖR YAPISI ─────────────────────────────────────────────────────────
constexpr int NUM_CONTROL_RODS  = 211;  // Toplam kontrol çubuğu
constexpr int NUM_AZ_RODS       = 12;   // Kısaltılmış emici çubuklar (USP)
constexpr int NUM_AR_RODS       = 12;   // Otomatik regülasyon çubukları
constexpr int NUM_MANUAL_RODS   = 187;  // Manuel kontrol çubukları (RR/RM)
constexpr int NUM_MCP           = 8;    // Ana dolaşım pompaları (GTs)
constexpr int NUM_DRUMS         = 2;    // Buhar separatörleri (BS)
constexpr int NUM_TURBINES      = 2;    // Türbin-jeneratörler (TG)

// ─── REAKTİVİTE KATSAYILARI (β biriminde) ──────────────────────────────────
// RBMK'nın en tehlikeli özelliği: POZITIF boşluk katsayısı (düşük güçte)
constexpr double VOID_COEFF_HIGH  =  0.50;   // Yüksek güçte boşluk katsayısı (β/birim)
constexpr double VOID_COEFF_LOW   =  1.20;   // Düşük güçte (<20%) boşluk katsayısı
constexpr double DOPPLER_COEFF    = -0.0001; // Doppler: yakıt sıcaklığı (β/°C, negatif)
constexpr double COOLANT_COEFF    =  0.00005;// Soğutucu sıcaklık katsayısı (β/°C)
constexpr double ROD_WORTH_AVG    =  0.065;  // Ortalama çubuk değeri (β/tam çubuk)
// AZ-5 grafit yerinden oy etme etkisi: çubuk ilk 50cm'de POZİTİF reaktivite ekler
constexpr double GRAPHITE_TIP_REACTIVITY = 0.10; // β (her çubuk için)

// ─── XENON-135 PARAMETRELERİ ────────────────────────────────────────────────
constexpr double LAMBDA_I        = 2.87e-5;  // İyot-135 bozunma sabiti (s⁻¹)  T½=6.7h
constexpr double LAMBDA_XE       = 2.09e-5;  // Xenon-135 bozunma sabiti (s⁻¹) T½=9.2h
constexpr double YIELD_I         = 0.061;    // İyot fraksiyon verimi
constexpr double YIELD_XE        = 0.003;    // Direk Xenon verimi
constexpr double XE_BURNOUT      = 0.080;    // Xenon yakalanma oranı (normalize)
constexpr double MAX_XE_REACTIV  = 0.020;    // Maksimum Xenon reaktivitesi (≈3β)
// Xenon iodine denge değerleri (tam güç, normalize)
constexpr double I_EQUIL         = 1.0;
constexpr double XE_EQUIL        = 1.0;

// ─── NOMINAL ÇALIŞMA PARAMETRELERİ ─────────────────────────────────────────
constexpr double COOLANT_IN_NOM  = 265.0;   // °C giriş soğutucu sıcaklığı
constexpr double COOLANT_OUT_NOM = 284.0;   // °C çıkış soğutucu sıcaklığı
constexpr double PRESSURE_NOM    = 6.90;    // MPa soğutucu basıncı
constexpr double STEAM_PRESS_NOM = 6.50;    // MPa buhar basıncı
constexpr double SAT_TEMP_NOM    = 284.0;   // °C doyma sıcaklığı (6.9 MPa'da)
constexpr double DRUM_LEVEL_NOM  = 50.0;    // % nominal tambur seviyesi
constexpr double MCP_FLOW_NOM    = 850.0;   // kg/s her pompa için nominal akış
constexpr double FEEDWATER_NOM   = 6800.0;  // kg/s toplam besleme suyu
constexpr double FUEL_TEMP_NOM   = 650.0;   // °C tam güçte nominal yakıt sıcaklığı

// ─── GÜVENLİK LİMİTLERİ ────────────────────────────────────────────────────
constexpr int    MIN_ORM         = 15;      // Minimum Operasyonel Reaktivite Marjı
constexpr int    CRITICAL_ORM    = 7;       // Kritik ORM (çok tehlikeli)
constexpr double MAX_POWER_RATE  = 100.0;   // MW/s maksimum güvenli artış hızı
constexpr double MAX_FUEL_TEMP   = 1660.0;  // °C UO₂ erime noktası
constexpr double MAX_CLNT_TEMP   = 350.0;   // °C acil soğutucu limit
constexpr double MAX_PRESSURE    = 8.50;    // MPa basınç kabı limiti
constexpr double MIN_PRESSURE    = 5.00;    // MPa düşük basınç alarmı
constexpr double MIN_DRUM_LVL    = 10.0;    // % minimum tambur seviyesi
constexpr double MAX_DRUM_LVL    = 90.0;    // % maksimum tambur seviyesi
constexpr double MELTDOWN_POWER  = 12000.0; // MW - nükleer erime başlangıcı
constexpr double EXPLOSION_POWER = 32000.0; // MW - reaktör patlaması (10× rated)

// ─── ALARM TİPLERİ ──────────────────────────────────────────────────────────
enum class AlarmType : int {
    NONE = 0,
    LOW_POWER,          // Güç < %10
    HIGH_POWER,         // Güç > %107
    SCRAM_AUTO,         // Güç > %115 - otomatik SCRAM
    LOW_ORM,            // ORM < 15
    CRITICAL_ORM,       // ORM < 7 - çok tehlikeli
    HIGH_STEAM_PRESS,   // Buhar basıncı yüksek
    LOW_STEAM_PRESS,    // Buhar basıncı düşük
    HIGH_COOLANT_TEMP,  // Soğutucu sıcaklığı yüksek
    LOW_DRUM_LEVEL,     // Tambur seviyesi düşük
    HIGH_DRUM_LEVEL,    // Tambur seviyesi yüksek
    MCP_TRIP,           // Pompa arızası
    MCP_LOW_FLOW,       // Düşük pompa akışı
    XENON_TRAP,         // Xenon tuzağı - reaktörü yeniden başlatamıyoruz
    HIGH_FUEL_TEMP,     // Yakıt sıcaklığı yüksek
    SCRAM_INITIATED,    // Emergency shutdown başlatıldı
    POWER_EXCURSION,    // Güç artışı kontrolsüz
    ECCS_ACTIVE,        // Acil Soğutma Sistemi aktif
    COOLANT_LEAK,       // Soğutucu devresi sızıntısı
    FUEL_CHANNEL_RUPTURE, // Yakıt kanalı patlaması
    POSITIVE_FEEDBACK,  // Pozitif geri besleme tespit edildi
    REACTOR_EXPLOSION   // FATAl: Reaktör patladı
};

// ─── ALARM ŞİDDETİ ──────────────────────────────────────────────────────────
enum class AlarmSeverity : int {
    INFO      = 0,
    WARNING   = 1,
    ALERT     = 2,
    EMERGENCY = 3,
    FATAL     = 4
};

// ─── REAKTÖR DURUMU ─────────────────────────────────────────────────────────
enum class ReactorStatus : int {
    COLD_SHUTDOWN   = 0,   // Soğuk kapalı
    HOT_SHUTDOWN    = 1,   // Sıcak kapalı (xenon yüksek)
    STARTUP         = 2,   // Başlatma prosedürü
    LOW_POWER       = 3,   // < %10 güç
    POWER_ASCENT    = 4,   // Güç artışı
    FULL_POWER      = 5,   // %95-107 normal işletme
    POWER_REDUCTION = 6,   // Güç azaltma
    TRANSIENT       = 7,   // Geçici durum
    SCRAM           = 8,   // Acil durdurma
    MELTDOWN        = 9,   // Çekirdek hasarı
    EXPLOSION       = 10   // Reaktör patladı
};

// ─── ALARM YAPISI ───────────────────────────────────────────────────────────
struct Alarm {
    AlarmType    type;
    AlarmSeverity severity;
    std::string  message;      // Rusça sistem kodu
    std::string  message_tr;   // Türkçe açıklama
    double       timestamp;
    bool         acknowledged;
    bool         active;
};

// ─── LOG KAYDI ──────────────────────────────────────────────────────────────
struct LogEntry {
    double      timestamp;      // Oyun içi zaman (s)
    std::string message;        // Log mesajı
    std::string category;       // SİSTEM, REAKTÖR, OPERATÖR, ALARM, ACİL
    int         severity;       // 0=bilgi, 1=uyarı, 2=alert, 3=acil
};

// ─── ROD GRUBU TANIMLARI ────────────────────────────────────────────────────
// RBMK-1000 kontrol çubuğu grupları
struct RodGroup {
    std::string name;       // Grup adı (Rusça)
    std::string name_tr;    // Türkçe
    int         start_idx;  // İlk çubuk indisi
    int         count;      // Çubuk sayısı
    double      worth;      // Toplam reaktivite değeri (β)
};

// ─── TAM REAKTÖR DURUMU ─────────────────────────────────────────────────────
struct ReactorState {
    // ── Güç & Nötronik ──────────────────────────────────────────────────────
    double power_mw          = 0.0;    // Termal güç (MW)
    double power_frac        = 0.0;    // 0.0–1.0+ (nominal'in fraksiyonu)
    double power_rate_mw_s   = 0.0;    // Güç değişim hızı (MW/s)
    double neutron_flux      = 0.0;    // Bağıl nötron akısı
    double precursor         = 0.0;    // Gecikmeli nötron öncü derişimi

    // ── Reaktivite Bileşenleri (β biriminde) ────────────────────────────────
    double reactivity_total   = 0.0;
    double reactivity_rods    = 0.0;
    double reactivity_xenon   = 0.0;   // Her zaman negatif
    double reactivity_void    = 0.0;   // RBMK'da POZİTİF!
    double reactivity_doppler = 0.0;   // Her zaman negatif

    // ── Xenon / İyot Zehirlenmesi ───────────────────────────────────────────
    double iodine  = 0.0;   // I-135 konsantrasyonu (normalize, 1.0 = tam güç dengesi)
    double xenon   = 0.0;   // Xe-135 konsantrasyonu (normalize)

    // ── Kontrol Çubukları (211 adet) ────────────────────────────────────────
    // Pozisyon: 0.0 = tamamen dışarı çekilmiş (tepeden), 1.0 = tamamen içeri
    // RBMK'da çubuklar yukarıdan aşağı hareket eder (yerçekimi + motor)
    std::array<double, NUM_CONTROL_RODS> rod_pos;     // Mevcut pozisyon
    std::array<double, NUM_CONTROL_RODS> rod_target;  // Hedef pozisyon
    std::array<bool,   NUM_CONTROL_RODS> rod_moving;  // Hareket ediyor mu?
    double rod_speed = 0.004; // m/s rod hareket hızı (~20 cm/s nominal)
    int    orm       = 0;     // Operasyonel Reaktivite Marjı (çubuk eşdeğeri)

    // ── Soğutucu Sistemi ────────────────────────────────────────────────────
    double coolant_temp_in    = COOLANT_IN_NOM;
    double coolant_temp_out   = COOLANT_OUT_NOM;
    double coolant_pressure   = PRESSURE_NOM;
    double coolant_flow_total = 0.0;    // kg/s toplam
    double void_fraction      = 0.0;    // 0.0–1.0 buhar boşluk fraksiyonu
    bool   coolant_leak       = false;
    double leak_rate          = 0.0;    // kg/s sızıntı hızı

    // ── Yakıt ───────────────────────────────────────────────────────────────
    double fuel_temp          = 20.0;   // °C
    bool   fuel_melt          = false;
    bool   fuel_channel_rupture = false;

    // ── Buhar Sistemi ───────────────────────────────────────────────────────
    double steam_pressure     = 0.0;    // MPa
    double steam_flow         = 0.0;    // kg/s
    double feedwater_flow     = 0.0;    // kg/s
    double feedwater_setpoint = FEEDWATER_NOM;
    double drum_level[NUM_DRUMS] = {50.0, 50.0}; // %

    // ── Ana Dolaşım Pompaları (8 adet, 2 devre × 4) ─────────────────────────
    double mcp_flow[NUM_MCP]     = {0};      // kg/s her pompa
    double mcp_speed[NUM_MCP]    = {0};      // RPM
    double mcp_setpoint[NUM_MCP] = {0};      // Akış hedefi kg/s
    bool   mcp_active[NUM_MCP]   = {false};  // Açık/kapalı
    bool   mcp_failed[NUM_MCP]   = {false};  // Arızalı mı?

    // ── Türbin-Jeneratörler ─────────────────────────────────────────────────
    double turbine_speed[NUM_TURBINES]     = {0};     // RPM
    double turbine_load[NUM_TURBINES]      = {0};     // MWe
    double turbine_valve[NUM_TURBINES]     = {0};     // 0.0–1.0 buhar valfi açıklığı
    bool   turbine_online[NUM_TURBINES]    = {false};
    bool   turbine_trip[NUM_TURBINES]      = {false};
    bool   turbine_rundown[NUM_TURBINES]   = {false}; // Türbin coast-down modu
    double turbine_rundown_time[NUM_TURBINES] = {0};  // Saniye

    // ── Güvenlik Sistemleri ─────────────────────────────────────────────────
    bool   scram_active        = false;   // AZ-5 / otomatik SCRAM
    bool   az5_pressed         = false;   // Operatör AZ-5'e bastı mı?
    bool   eccs_active         = false;   // Acil Çekirdek Soğutma Sistemi
    bool   az5_blocked         = false;   // Test sırasında AZ-5 bloke edildi mi?
    bool   ar_active           = true;    // Otomatik Regülasyon sistemi
    bool   skala_active        = true;    // SKALA reaktör bilgisayarı

    // ── Elektrik Şebekesi ───────────────────────────────────────────────────
    bool   grid_connected      = true;
    bool   station_blackout    = false;
    bool   diesel_active       = false;
    double diesel_power        = 0.0;    // kW

    // ── Durum ───────────────────────────────────────────────────────────────
    ReactorStatus status       = ReactorStatus::COLD_SHUTDOWN;
    bool   reactor_destroyed   = false;
    double simulation_time     = 0.0;   // saniye
    double elapsed_real        = 0.0;   // Gerçek oyun süresi

    // ── Alarmlar & Loglar ────────────────────────────────────────────────────
    std::vector<Alarm>    active_alarms;
    std::string           failure_reason;

    // ── Level-spesifik ──────────────────────────────────────────────────────
    int    current_level       = 1;
    bool   mission_failed      = false;
    bool   mission_complete    = false;
    double mission_score       = 0.0;   // 0.0–100.0
};

// ─── OPERATÖR KONTROL GİRİŞİ ────────────────────────────────────────────────
struct ControlInput {
    // Kontrol çubuğu hedefleri (0.0 = dışarıda, 1.0 = tam içeride)
    std::array<double, NUM_CONTROL_RODS> rod_targets;

    // Pompa kontrolleri
    double mcp_setpoints[NUM_MCP];
    bool   mcp_active[NUM_MCP];

    // Buhar/su kontrolleri
    double feedwater_setpoint;
    double turbine_valve[NUM_TURBINES];
    bool   turbine_trip_cmd[NUM_TURBINES];

    // Güvenlik komutları
    bool   az5_press;          // AZ-5 acil durdurma
    bool   eccs_request;       // SAOR aktif et
    bool   ar_enable;          // Otomatik regülasyon aç/kapat
    bool   ack_alarms;         // Alarmları onayla
    bool   diesel_start;       // Dizel jeneratör başlat
};

// ─── REAKTÖR FİZİK MOTORU ───────────────────────────────────────────────────
class ReactorEngine {
public:
    ReactorEngine();
    ~ReactorEngine() = default;

    // Başlatma
    void initialize(bool cold_start = true, int level = 1);

    // Simülasyonu dt saniye ilerlet (genellikle 0.05–0.1s)
    void step(double dt);

    // Operatör girişi uygula
    void applyControl(const ControlInput& input);

    // Durum kopyası al (thread-safe)
    ReactorState getState() const;

    // Log kayıtlarını al ve temizle
    std::vector<LogEntry> getAndClearLog();

    // Level koşullarını ayarla
    void setLevelConditions(int level);

    // Senaryo tetikleyiciler (level scripting için)
    void triggerMCPFailure(int pump_idx);
    void triggerCoolantLeak(double rate_kgs);
    void triggerFuelChannelRupture();
    void triggerXenonTrap();
    void triggerStationBlackout();
    void triggerGridDisconnect();
    void triggerLevel16Sequence(double elapsed); // Scripted son bölüm

    // Reaktör rod grubu kontrolü (UI kolaylığı için)
    void setRodGroupTarget(int group, double position);

    // ORM hesapla
    int calculateORM() const;

    // Güvenlik kontrolleri
    bool isReactorSafe()   const;
    bool isMissionFailed() const;
    bool isMissionComplete(int level) const;
    double getMissionScore(int level) const;

private:
    ReactorState state_;
    std::vector<LogEntry> log_buffer_;
    std::mt19937 rng_;

    // Fizik güncelleme adımları
    void updateControlRods(double dt);
    void updateNeutronics(double dt);
    void updateXenon(double dt);
    void updateThermalHydraulics(double dt);
    void updateSteamSystem(double dt);
    void updatePumps(double dt);
    void updateTurbines(double dt);
    void updateSafetySystemsAutomatic(double dt);
    void updateReactorStatus();
    void checkMissionConditions(int level);

    // Reaktivite hesaplamaları
    double calcRodReactivity()     const;
    double calcXenonReactivity()   const;
    double calcVoidReactivity()    const;
    double calcDopplerReactivity() const;
    double calcVoidCoefficient()   const;
    double calcTotalReactivity()   const;

    // Alarm yönetimi
    void checkAndUpdateAlarms();
    void addAlarm(AlarmType type, AlarmSeverity sev,
                  const std::string& code_ru, const std::string& msg_tr);
    void removeAlarm(AlarmType type);
    bool hasAlarm(AlarmType type) const;

    // Logging
    void log(const std::string& msg, const std::string& cat, int sev);

    // Güvenlik sistemleri
    void triggerScram(const std::string& reason_ru, const std::string& reason_tr);
    void activateECCS(const std::string& reason_tr);

    // Yardımcı
    double clamp(double v, double lo, double hi) const {
        return std::max(lo, std::min(hi, v));
    }
    double lerp(double a, double b, double t) const {
        return a + t * (b - a);
    }
    double gaussian_noise(double sigma);

    // Level başlangıç konfigürasyonları
    void setupLevel1();
    void setupLevel2();
    void setupLevel3();
    void setupLevel4();
    void setupLevel5();
    void setupLevel6();
    void setupLevel7();
    void setupLevel8();
    void setupLevel9();
    void setupLevel10();
    void setupLevel11();
    void setupLevel12();
    void setupLevel13();
    void setupLevel14();
    void setupLevel15();
    void setupLevel16();

    // Level görev kontrolleri
    bool checkLevel2Mission() const;
    bool checkLevel3Mission() const;
    bool checkLevel4Mission() const;
    bool checkLevel5Mission() const;
    bool checkLevel6Mission() const;
    bool checkLevel7Mission() const;
    bool checkLevel8Mission() const;
    bool checkLevel9Mission() const;
    bool checkLevel10Mission() const;
    bool checkLevel11Mission() const;
    bool checkLevel12Mission() const;
    bool checkLevel13Mission() const;
    bool checkLevel14Mission() const;
    bool checkLevel15Mission() const;

    // Level-spesifik kontrol değişkenleri
    double l4_stable_time_    = 0.0;   // Level 4: stabil güçte geçen süre
    double l6_test_time_      = 0.0;   // Level 6: türbin test süresi
    double l8_drum_ok_time_   = 0.0;   // Level 8: tambur ok süresi
    double l9_manual_time_    = 0.0;   // Level 9: manuel kontrol süresi
    double l10_blackout_time_ = 0.0;   // Level 10: karartma başlangıcı
    double l12_void_peak_     = 0.0;   // Level 12: maksimum void
    double l15_cooling_time_  = 0.0;   // Level 15: soğutma süresi
    double l16_seq_time_      = 0.0;   // Level 16: senaryo zamanı
    bool   l5_mcp_failed_     = false;
    bool   l10_diesel_done_   = false;
    bool   l15_eccs_done_     = false;
};

} // namespace rbmk
