#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <android/log.h>
#include "reactor_engine.h"

#define LOG_TAG "RBMK_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── GLOBAL REAKTÖR MOTORU ──────────────────────────────────────────────────
static std::unique_ptr<rbmk::ReactorEngine> g_engine;
static std::mutex g_mutex;

// ─── YARDIMCI: jstring oluştur ───────────────────────────────────────────────
static jstring mkstr(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

extern "C" {

// ─── MOTOR BAŞLATMA ──────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeInit(
        JNIEnv*, jobject, jboolean cold_start, jint level) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_engine = std::make_unique<rbmk::ReactorEngine>();
    g_engine->initialize(cold_start, level);
    LOGI("Reaktör motoru başlatıldı: cold=%d, level=%d", (int)cold_start, (int)level);
}

// ─── SİMÜLASYON ADIMI ────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeStep(
        JNIEnv*, jobject, jdouble dt) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_engine) g_engine->step(dt);
}

// ─── KONTROL UYGULAMASI ───────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeApplyControl(
        JNIEnv* env, jobject,
        jdoubleArray rod_targets,       // 211 double
        jdoubleArray mcp_setpoints,     // 8 double
        jbooleanArray mcp_active,       // 8 bool
        jdouble feedwater_sp,
        jdoubleArray turbine_valves,    // 2 double
        jbooleanArray turbine_trips,    // 2 bool
        jboolean az5_press,
        jboolean eccs_request,
        jboolean ar_enable,
        jboolean ack_alarms,
        jboolean diesel_start) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return;

    rbmk::ControlInput input;

    // Kontrol çubukları
    jsize rod_len = env->GetArrayLength(rod_targets);
    jdouble* rods = env->GetDoubleArrayElements(rod_targets, nullptr);
    for (int i = 0; i < std::min((int)rod_len, rbmk::NUM_CONTROL_RODS); i++) {
        input.rod_targets[i] = rods[i];
    }
    env->ReleaseDoubleArrayElements(rod_targets, rods, JNI_ABORT);

    // Pompalar
    jdouble* mcp_sp = env->GetDoubleArrayElements(mcp_setpoints, nullptr);
    jboolean* mcp_on = env->GetBooleanArrayElements(mcp_active, nullptr);
    for (int i = 0; i < rbmk::NUM_MCP; i++) {
        input.mcp_setpoints[i] = mcp_sp[i];
        input.mcp_active[i]    = (bool)mcp_on[i];
    }
    env->ReleaseDoubleArrayElements(mcp_setpoints, mcp_sp, JNI_ABORT);
    env->ReleaseBooleanArrayElements(mcp_active, mcp_on, JNI_ABORT);

    // Türbinler
    jdouble*  tv  = env->GetDoubleArrayElements(turbine_valves, nullptr);
    jboolean* tt  = env->GetBooleanArrayElements(turbine_trips, nullptr);
    for (int t = 0; t < rbmk::NUM_TURBINES; t++) {
        input.turbine_valve[t]    = tv[t];
        input.turbine_trip_cmd[t] = (bool)tt[t];
    }
    env->ReleaseDoubleArrayElements(turbine_valves, tv, JNI_ABORT);
    env->ReleaseBooleanArrayElements(turbine_trips, tt, JNI_ABORT);

    input.feedwater_setpoint = feedwater_sp;
    input.az5_press          = (bool)az5_press;
    input.eccs_request       = (bool)eccs_request;
    input.ar_enable          = (bool)ar_enable;
    input.ack_alarms         = (bool)ack_alarms;
    input.diesel_start       = (bool)diesel_start;

    g_engine->applyControl(input);
}

// ─── DURUM OKUMA ─────────────────────────────────────────────────────────────
// Güç değerleri (termal MW, elektrik MWe)
JNIEXPORT jdoubleArray JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetPowerData(
        JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return nullptr;
    auto state = g_engine->getState();
    jdouble data[] = {
        state.power_mw,
        state.power_frac * 100.0,       // %
        state.power_rate_mw_s,
        state.neutron_flux,
        state.reactivity_total,
        state.reactivity_rods,
        state.reactivity_xenon,
        state.reactivity_void,
        state.reactivity_doppler,
        state.xenon,
        state.iodine,
        (double)state.orm
    };
    jdoubleArray arr = env->NewDoubleArray(12);
    env->SetDoubleArrayRegion(arr, 0, 12, data);
    return arr;
}

// Termal hidrolik verisi
JNIEXPORT jdoubleArray JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetThermalData(
        JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return nullptr;
    auto state = g_engine->getState();
    jdouble data[] = {
        state.coolant_temp_in,
        state.coolant_temp_out,
        state.coolant_pressure,
        state.void_fraction * 100.0,    // %
        state.fuel_temp,
        state.steam_pressure,
        state.steam_flow,
        state.feedwater_flow,
        state.drum_level[0],
        state.drum_level[1],
        state.coolant_flow_total,
        (double)(state.coolant_leak ? 1 : 0),
        state.leak_rate
    };
    jdoubleArray arr = env->NewDoubleArray(13);
    env->SetDoubleArrayRegion(arr, 0, 13, data);
    return arr;
}

// Pompa verileri
JNIEXPORT jdoubleArray JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetMCPData(
        JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return nullptr;
    auto state = g_engine->getState();
    // 8 pompa × 4 değer (flow, speed, active, failed) = 32
    jdouble data[32];
    for (int i = 0; i < rbmk::NUM_MCP; i++) {
        data[i*4 + 0] = state.mcp_flow[i];
        data[i*4 + 1] = state.mcp_speed[i];
        data[i*4 + 2] = state.mcp_active[i] ? 1.0 : 0.0;
        data[i*4 + 3] = state.mcp_failed[i] ? 1.0 : 0.0;
    }
    jdoubleArray arr = env->NewDoubleArray(32);
    env->SetDoubleArrayRegion(arr, 0, 32, data);
    return arr;
}

// Kontrol çubuğu pozisyonları (211 adet)
JNIEXPORT jdoubleArray JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetRodPositions(
        JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return nullptr;
    auto state = g_engine->getState();
    jdoubleArray arr = env->NewDoubleArray(rbmk::NUM_CONTROL_RODS);
    env->SetDoubleArrayRegion(arr, 0, rbmk::NUM_CONTROL_RODS,
                              state.rod_pos.data());
    return arr;
}

// Türbin verileri
JNIEXPORT jdoubleArray JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetTurbineData(
        JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return nullptr;
    auto state = g_engine->getState();
    jdouble data[] = {
        state.turbine_speed[0], state.turbine_speed[1],
        state.turbine_load[0],  state.turbine_load[1],
        state.turbine_valve[0], state.turbine_valve[1],
        state.turbine_online[0] ? 1.0 : 0.0,
        state.turbine_online[1] ? 1.0 : 0.0,
        state.turbine_trip[0]   ? 1.0 : 0.0,
        state.turbine_trip[1]   ? 1.0 : 0.0
    };
    jdoubleArray arr = env->NewDoubleArray(10);
    env->SetDoubleArrayRegion(arr, 0, 10, data);
    return arr;
}

// Sistem durumu (boolean flags)
JNIEXPORT jintArray JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetSystemFlags(
        JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return nullptr;
    auto state = g_engine->getState();
    jint data[] = {
        state.scram_active         ? 1 : 0,
        state.eccs_active          ? 1 : 0,
        state.az5_pressed          ? 1 : 0,
        state.reactor_destroyed    ? 1 : 0,
        state.mission_failed       ? 1 : 0,
        state.mission_complete     ? 1 : 0,
        state.station_blackout     ? 1 : 0,
        state.diesel_active        ? 1 : 0,
        state.coolant_leak         ? 1 : 0,
        state.fuel_channel_rupture ? 1 : 0,
        state.fuel_melt            ? 1 : 0,
        state.ar_active            ? 1 : 0,
        state.skala_active         ? 1 : 0,
        state.az5_blocked          ? 1 : 0,
        (int)state.status
    };
    jintArray arr = env->NewIntArray(15);
    env->SetIntArrayRegion(arr, 0, 15, data);
    return arr;
}

// Alarmlar (JSON-like string)
JNIEXPORT jstring JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetAlarmsJson(
        JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return mkstr(env, "[]");
    auto state = g_engine->getState();
    std::string json = "[";
    for (size_t i = 0; i < state.active_alarms.size(); i++) {
        const auto& a = state.active_alarms[i];
        if (i > 0) json += ",";
        json += "{\"type\":" + std::to_string((int)a.type) +
                ",\"sev\":"  + std::to_string((int)a.severity) +
                ",\"code\":\"" + a.message + "\"" +
                ",\"tr\":\""   + a.message_tr + "\"" +
                ",\"ack\":"  + (a.acknowledged ? "true" : "false") + "}";
    }
    json += "]";
    return mkstr(env, json);
}

// Log kayıtları (JSON string)
JNIEXPORT jstring JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetLogJson(
        JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return mkstr(env, "[]");
    auto logs = g_engine->getAndClearLog();
    std::string json = "[";
    for (size_t i = 0; i < logs.size(); i++) {
        const auto& e = logs[i];
        if (i > 0) json += ",";
        // Escape quote karakterleri
        std::string msg = e.message;
        for (size_t j = 0; j < msg.size(); j++) {
            if (msg[j] == '"') { msg.insert(j, "\\"); j++; }
        }
        json += "{\"t\":" + std::to_string(e.timestamp) +
                ",\"msg\":\"" + msg + "\"" +
                ",\"cat\":\"" + e.category + "\"" +
                ",\"sev\":" + std::to_string(e.severity) + "}";
    }
    json += "]";
    return mkstr(env, json);
}

// Simülasyon zamanı
JNIEXPORT jdouble JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetSimTime(
        JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return 0.0;
    return g_engine->getState().simulation_time;
}

// Görev puanı
JNIEXPORT jdouble JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetMissionScore(
        JNIEnv*, jobject, jint level) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return 0.0;
    return g_engine->getMissionScore(level);
}

// Hata mesajı
JNIEXPORT jstring JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeGetFailureReason(
        JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_engine) return mkstr(env, "");
    return mkstr(env, g_engine->getState().failure_reason);
}

// ─── SENARYO TETİKLEYİCİLERİ (Level scripting) ───────────────────────────────
JNIEXPORT void JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeTriggerMCPFailure(
        JNIEnv*, jobject, jint pump_idx) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_engine) g_engine->triggerMCPFailure(pump_idx);
}

JNIEXPORT void JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeTriggerCoolantLeak(
        JNIEnv*, jobject, jdouble rate) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_engine) g_engine->triggerCoolantLeak(rate);
}

JNIEXPORT void JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeTriggerFuelChannelRupture(
        JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_engine) g_engine->triggerFuelChannelRupture();
}

JNIEXPORT void JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeTriggerBlackout(
        JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_engine) g_engine->triggerStationBlackout();
}

// Rod grubu toplu kontrolü
JNIEXPORT void JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeSetRodGroup(
        JNIEnv*, jobject, jint group, jdouble position) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_engine) g_engine->setRodGroupTarget(group, position);
}

// Level değiştir
JNIEXPORT void JNICALL
Java_com_rbmk_alexandr_viewmodel_ReactorViewModel_nativeSetLevel(
        JNIEnv*, jobject, jint level) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_engine) g_engine->setLevelConditions(level);
}

} // extern "C"
