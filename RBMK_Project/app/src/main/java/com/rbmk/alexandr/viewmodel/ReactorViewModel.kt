package com.rbmk.alexandr.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rbmk.alexandr.model.*
import com.rbmk.alexandr.sound.SoundManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import kotlin.math.roundToInt

class ReactorViewModel(app: Application) : AndroidViewModel(app) {

    // ─── JNI ─────────────────────────────────────────────────────────────────
    companion object {
        init { System.loadLibrary("rbmk_reactor") }

        // nativeGetPowerData() → 12 eleman
        const val PD_POWER_MW    = 0
        const val PD_POWER_PCT   = 1
        const val PD_POWER_RATE  = 2
        const val PD_NEUTRON     = 3
        const val PD_REACT_TOT   = 4
        const val PD_REACT_RODS  = 5
        const val PD_REACT_XE    = 6
        const val PD_REACT_VOID  = 7
        const val PD_REACT_DOP   = 8
        const val PD_XENON       = 9
        const val PD_IODINE      = 10
        const val PD_ORM         = 11

        // nativeGetThermalData() → 13 eleman
        const val TD_COOL_IN     = 0
        const val TD_COOL_OUT    = 1
        const val TD_COOL_PRESS  = 2
        const val TD_VOID        = 3
        const val TD_FUEL_TEMP   = 4
        const val TD_STEAM_PRESS = 5
        const val TD_STEAM_FLOW  = 6
        const val TD_FW_FLOW     = 7
        const val TD_DRUM1       = 8
        const val TD_DRUM2       = 9
        const val TD_COOL_FLOW   = 10
        const val TD_LEAK        = 11
        const val TD_LEAK_RATE   = 12

        // nativeGetTurbineData() → 10 eleman düz sıralı:
        // [0]=speed0 [1]=speed1 [2]=load0 [3]=load1
        // [4]=valve0 [5]=valve1 [6]=online0 [7]=online1
        // [8]=trip0  [9]=trip1
        const val TB_SPEED0   = 0;  const val TB_SPEED1   = 1
        const val TB_LOAD0    = 2;  const val TB_LOAD1    = 3
        const val TB_VALVE0   = 4;  const val TB_VALVE1   = 5
        const val TB_ONLINE0  = 6;  const val TB_ONLINE1  = 7
        const val TB_TRIP0    = 8;  const val TB_TRIP1    = 9

        // nativeGetSystemFlags() → 15 eleman
        const val SF_SCRAM       = 0
        const val SF_ECCS        = 1
        const val SF_AZ5         = 2
        const val SF_DESTROYED   = 3
        const val SF_FAILED      = 4
        const val SF_COMPLETE    = 5
        const val SF_BLACKOUT    = 6
        const val SF_DIESEL      = 7
        const val SF_LEAK        = 8
        const val SF_RUPTURE     = 9
        const val SF_MELT        = 10
        const val SF_AR          = 11
        const val SF_SKALA       = 12
        const val SF_AZ5_BLOCKED = 13
        const val SF_STATUS      = 14
    }

    // ─── JNI NATIVE FONKSİYONLARI ─────────────────────────────────────────────
    external fun nativeInit(coldStart: Boolean, level: Int)
    external fun nativeStep(dt: Double)
    external fun nativeApplyControl(
        rodTargets: DoubleArray, mcpSetpoints: DoubleArray, mcpActive: BooleanArray,
        feedwaterSp: Double, turbineValves: DoubleArray, turbineTrips: BooleanArray,
        az5Press: Boolean, eccsRequest: Boolean, arEnable: Boolean,
        ackAlarms: Boolean, dieselStart: Boolean
    )
    external fun nativeGetPowerData(): DoubleArray?
    external fun nativeGetThermalData(): DoubleArray?
    external fun nativeGetMCPData(): DoubleArray?
    external fun nativeGetRodPositions(): DoubleArray?
    external fun nativeGetTurbineData(): DoubleArray?
    external fun nativeGetSystemFlags(): IntArray?
    external fun nativeGetAlarmsJson(): String
    external fun nativeGetLogJson(): String
    external fun nativeGetSimTime(): Double
    external fun nativeGetMissionScore(level: Int): Double
    external fun nativeGetFailureReason(): String
    external fun nativeTriggerMCPFailure(pumpIdx: Int)
    external fun nativeTriggerCoolantLeak(rate: Double)
    external fun nativeTriggerFuelChannelRupture()
    external fun nativeTriggerBlackout()
    external fun nativeSetRodGroup(group: Int, position: Double)
    external fun nativeSetLevel(level: Int)

    // ─── STATE AKIŞLARI ───────────────────────────────────────────────────────
    private val _reactorState = MutableStateFlow(ReactorStateModel())
    val reactorState: StateFlow<ReactorStateModel> = _reactorState.asStateFlow()

    private val _logEntries = MutableStateFlow<List<LogEntryModel>>(emptyList())
    val logEntries: StateFlow<List<LogEntryModel>> = _logEntries.asStateFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _controlState = MutableStateFlow(ControlState())
    val controlState: StateFlow<ControlState> = _controlState.asStateFlow()

    // ─── SES YÖNETİCİSİ ──────────────────────────────────────────────────────
    private val soundManager = SoundManager(app)

    // ─── SİMÜLASYON ──────────────────────────────────────────────────────────
    private var simJob: Job? = null
    private val SIM_DT = 0.10
    private val SIM_INTERVAL_MS = 100L

    // ─── LOG BUFFER ───────────────────────────────────────────────────────────
    private val maxLogEntries = 500
    private val allLogs = ArrayDeque<LogEntryModel>(maxLogEntries)

    // ─── TAMAMLANAN LEVELLAR ──────────────────────────────────────────────────
    private val _completedLevels = MutableStateFlow<Set<Int>>(emptySet())
    val completedLevels: StateFlow<Set<Int>> = _completedLevels.asStateFlow()

    // ─── SES ÖNCEKI DURUM ────────────────────────────────────────────────────
    private var prevScram     = false
    private var prevEmergency = false
    private var prevWarning   = false
    private var prevExplosion = false
    private var prevMeltdown  = false

    // ─── BAŞLATMA ────────────────────────────────────────────────────────────
    fun initGame(level: Int, coldStart: Boolean = true) {
        simJob?.cancel()
        allLogs.clear()
        _logEntries.value = emptyList()
        _uiState.update { UiState(currentLevel = level) }
        _controlState.value = ControlState()
        prevScram = false; prevEmergency = false
        prevWarning = false; prevExplosion = false; prevMeltdown = false

        nativeInit(coldStart, level)

        val levelInfo = GameLevels.getLevel(level)
        _uiState.update { it.copy(levelInfo = levelInfo, showBriefing = true) }
        addLocalLog("Seviye $level yüklendi: ${levelInfo?.title}", "SİSTEM", 0)
    }

    fun startSimulation() {
        _uiState.update { it.copy(isRunning = true, showBriefing = false) }
        addLocalLog("Simülasyon başlatıldı.", "SİSTEM", 0)
        simJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val t0 = System.currentTimeMillis()
                applyControlToNative()
                nativeStep(SIM_DT)
                val newState = buildStateModel()
                val newLogs  = readNewLogs()
                withContext(Dispatchers.Main) {
                    _reactorState.value = newState
                    if (newLogs.isNotEmpty()) updateLogs(newLogs)
                    handleSounds(newState)
                    checkGameEvents(newState)
                }
                val elapsed = System.currentTimeMillis() - t0
                delay((SIM_INTERVAL_MS - elapsed).coerceAtLeast(1L))
            }
        }
    }

    fun pauseSimulation() {
        simJob?.cancel()
        _uiState.update { it.copy(isRunning = false) }
    }

    // ─── KONTROL UYGULAMASI ───────────────────────────────────────────────────
    private fun applyControlToNative() {
        val cs = _controlState.value

        // "consumed" bayrakları: önce değeri al, sonra sıfırla
        val doAz5   = cs.az5PressConsumed
        val doEccs  = cs.eccsRequestConsumed
        val doAck   = cs.ackAlarmsConsumed
        val doDiesel = cs.dieselStartConsumed

        if (doAz5 || doEccs || doAck || doDiesel) {
            _controlState.update { it.copy(
                az5PressConsumed    = false,
                eccsRequestConsumed = false,
                ackAlarmsConsumed   = false,
                dieselStartConsumed = false
            )}
        }

        nativeApplyControl(
            rodTargets    = cs.rodTargets,
            mcpSetpoints  = cs.mcpSetpoints,
            mcpActive     = cs.mcpActive,
            feedwaterSp   = cs.feedwaterSetpoint,
            turbineValves = cs.turbineValves,
            turbineTrips  = cs.turbineTrips,
            az5Press      = doAz5,
            eccsRequest   = doEccs,
            arEnable      = cs.arEnabled,
            ackAlarms     = doAck,
            dieselStart   = doDiesel
        )
    }

    // ─── DURUM MODELİ ─────────────────────────────────────────────────────────
    private fun buildStateModel(): ReactorStateModel {
        val pd   = nativeGetPowerData()   ?: DoubleArray(12)
        val td   = nativeGetThermalData() ?: DoubleArray(13)
        val mcpd = nativeGetMCPData()     ?: DoubleArray(32)
        val rod  = nativeGetRodPositions() ?: DoubleArray(211)
        val turb = nativeGetTurbineData() ?: DoubleArray(10)
        val sf   = nativeGetSystemFlags() ?: IntArray(15)
        val alarms = parseAlarms(nativeGetAlarmsJson())
        val score  = nativeGetMissionScore(_uiState.value.currentLevel)
        val failReason = nativeGetFailureReason()

        val statusOrd = sf[SF_STATUS].coerceIn(0, ReactorStatus.values().size - 1)

        val mcpFlow   = DoubleArray(8)  { mcpd[it * 4 + 0] }
        val mcpSpeed  = DoubleArray(8)  { mcpd[it * 4 + 1] }
        val mcpActive = BooleanArray(8) { mcpd[it * 4 + 2] > 0.5 }
        val mcpFailed = BooleanArray(8) { mcpd[it * 4 + 3] > 0.5 }

        // Turbine verisi: düz 10 elemanlı dizi
        val turbineSpeed  = doubleArrayOf(turb[TB_SPEED0],  turb[TB_SPEED1])
        val turbineLoad   = doubleArrayOf(turb[TB_LOAD0],   turb[TB_LOAD1])
        val turbineValve  = doubleArrayOf(turb[TB_VALVE0],  turb[TB_VALVE1])
        val turbineOnline = booleanArrayOf(turb[TB_ONLINE0] > 0.5, turb[TB_ONLINE1] > 0.5)
        val turbineTrip   = booleanArrayOf(turb[TB_TRIP0]   > 0.5, turb[TB_TRIP1]   > 0.5)

        return ReactorStateModel(
            powerMW           = pd[PD_POWER_MW],
            powerPercent      = pd[PD_POWER_PCT],
            powerRateMWs      = pd[PD_POWER_RATE],
            neutronFlux       = pd[PD_NEUTRON],
            reactivityTotal   = pd[PD_REACT_TOT],
            reactivityRods    = pd[PD_REACT_RODS],
            reactivityXenon   = pd[PD_REACT_XE],
            reactivityVoid    = pd[PD_REACT_VOID],
            reactivityDoppler = pd[PD_REACT_DOP],
            xenonLevel        = pd[PD_XENON],
            iodineLevel       = pd[PD_IODINE],
            orm               = pd[PD_ORM].roundToInt(),
            coolantTempIn     = td[TD_COOL_IN],
            coolantTempOut    = td[TD_COOL_OUT],
            coolantPressure   = td[TD_COOL_PRESS],
            voidFraction      = td[TD_VOID],
            fuelTemp          = td[TD_FUEL_TEMP],
            steamPressure     = td[TD_STEAM_PRESS],
            steamFlow         = td[TD_STEAM_FLOW],
            feedwaterFlow     = td[TD_FW_FLOW],
            drumLevel1        = td[TD_DRUM1],
            drumLevel2        = td[TD_DRUM2],
            coolantFlowTotal  = td[TD_COOL_FLOW],
            coolantLeak       = td[TD_LEAK] > 0.5,
            leakRate          = td[TD_LEAK_RATE],
            mcpFlow           = mcpFlow,
            mcpSpeed          = mcpSpeed,
            mcpActive         = mcpActive,
            mcpFailed         = mcpFailed,
            turbineSpeed      = turbineSpeed,
            turbineLoad       = turbineLoad,
            turbineValve      = turbineValve,
            turbineOnline     = turbineOnline,
            turbineTrip       = turbineTrip,
            rodPositions      = rod,
            scramActive       = sf[SF_SCRAM]       == 1,
            eccsActive        = sf[SF_ECCS]        == 1,
            az5Pressed        = sf[SF_AZ5]         == 1,
            reactorDestroyed  = sf[SF_DESTROYED]   == 1,
            missionFailed     = sf[SF_FAILED]      == 1,
            missionComplete   = sf[SF_COMPLETE]    == 1,
            stationBlackout   = sf[SF_BLACKOUT]    == 1,
            dieselActive      = sf[SF_DIESEL]      == 1,
            fuelChannelRupture= sf[SF_RUPTURE]     == 1,
            fuelMelt          = sf[SF_MELT]        == 1,
            arActive          = sf[SF_AR]          == 1,
            skalaActive       = sf[SF_SKALA]       == 1,
            az5Blocked        = sf[SF_AZ5_BLOCKED] == 1,
            status            = ReactorStatus.values()[statusOrd],
            simulationTime    = nativeGetSimTime(),
            alarms            = alarms,
            failureReason     = failReason,
            missionScore      = score
        )
    }

    // ─── ALARM PARSE ──────────────────────────────────────────────────────────
    private fun parseAlarms(json: String): List<AlarmModel> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                AlarmModel(
                    type         = obj.getInt("type"),
                    severity     = obj.getInt("sev"),
                    codeRu       = obj.getString("code"),
                    messageTr    = obj.getString("tr"),
                    acknowledged = obj.getBoolean("ack")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    // ─── LOG OKUMA ───────────────────────────────────────────────────────────
    private fun readNewLogs(): List<LogEntryModel> {
        return try {
            val json = nativeGetLogJson()
            val arr  = JSONArray(json)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                LogEntryModel(
                    timestamp = obj.getDouble("t"),
                    message   = obj.getString("msg"),
                    category  = obj.getString("cat"),
                    severity  = obj.getInt("sev")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun updateLogs(newEntries: List<LogEntryModel>) {
        for (e in newEntries) {
            if (allLogs.size >= maxLogEntries) allLogs.removeFirst()
            allLogs.addLast(e)
        }
        _logEntries.value = allLogs.toList()
    }

    private fun addLocalLog(msg: String, cat: String, sev: Int) {
        val e = LogEntryModel(0.0, msg, cat, sev)
        if (allLogs.size >= maxLogEntries) allLogs.removeFirst()
        allLogs.addLast(e)
        _logEntries.value = allLogs.toList()
    }

    // ─── SES ─────────────────────────────────────────────────────────────────
    private fun handleSounds(state: ReactorStateModel) {
        val hasEmergency = state.hasEmergencyAlarm
        if (hasEmergency && !prevEmergency) soundManager.playEmergencyAlarm()
        else if (!hasEmergency) soundManager.stopEmergencyAlarm()
        prevEmergency = hasEmergency

        val hasWarning = state.hasActiveAlarms && !hasEmergency
        if (hasWarning && !prevWarning) soundManager.playWarningBeep()
        prevWarning = hasWarning

        if (state.scramActive && !prevScram) soundManager.playScram()
        prevScram = state.scramActive

        if (state.reactorDestroyed && !prevExplosion) soundManager.playExplosion()
        prevExplosion = state.reactorDestroyed

        if (state.fuelMelt && !prevMeltdown) soundManager.playMeltdown()
        prevMeltdown = state.fuelMelt
    }

    // ─── OYUN OLAYLARI ────────────────────────────────────────────────────────
    private fun checkGameEvents(state: ReactorStateModel) {
        val ui = _uiState.value
        if (state.missionComplete && !ui.showMissionComplete) {
            _completedLevels.update { it + ui.currentLevel }
            _uiState.update { it.copy(showMissionComplete = true, missionScore = state.missionScore) }
            pauseSimulation()
        }
        if (state.missionFailed && !ui.showMissionFailed) {
            _uiState.update { it.copy(showMissionFailed = true, failureReason = state.failureReason) }
            pauseSimulation()
        }
        if (state.reactorDestroyed && ui.currentLevel == 16 && !ui.showExplosionScreen) {
            _completedLevels.update { it + 16 }
            _uiState.update { it.copy(showExplosionScreen = true) }
            pauseSimulation()
        }
    }

    // ─── OPERATÖR KONTROLLERI ─────────────────────────────────────────────────
    fun setRodGroupTarget(group: Int, position: Double) {
        val startIdx = group * 30
        val endIdx   = minOf(startIdx + 30, 211)
        _controlState.update { cs ->
            val rods = cs.rodTargets.copyOf()
            for (i in startIdx until endIdx) rods[i] = position.coerceIn(0.0, 1.0)
            cs.copy(rodTargets = rods)
        }
        nativeSetRodGroup(group, position)
        addLocalLog("Grup-${group + 1} rod: ${(position * 100).roundToInt()}% içeri", "OPERATÖR", 0)
    }

    fun setMCPActive(index: Int, active: Boolean) {
        _controlState.update { cs ->
            val arr = cs.mcpActive.copyOf()
            arr[index] = active
            cs.copy(mcpActive = arr)
        }
        addLocalLog("GTs-${index + 1} ${if (active) "AÇILDI" else "KAPATILDI"}", "OPERATÖR", 1)
    }

    fun setMCPSetpoint(index: Int, flow: Double) {
        _controlState.update { cs ->
            val arr = cs.mcpSetpoints.copyOf()
            arr[index] = flow.coerceIn(0.0, 935.0)
            cs.copy(mcpSetpoints = arr)
        }
    }

    fun setFeedwaterSetpoint(value: Double) {
        _controlState.update { it.copy(feedwaterSetpoint = value.coerceIn(0.0, 10200.0)) }
    }

    fun setTurbineValve(index: Int, opening: Double) {
        _controlState.update { cs ->
            val arr = cs.turbineValves.copyOf()
            arr[index] = opening.coerceIn(0.0, 1.0)
            cs.copy(turbineValves = arr)
        }
    }

    fun tripTurbine(index: Int) {
        _controlState.update { cs ->
            val arr = cs.turbineTrips.copyOf()
            arr[index] = true
            cs.copy(turbineTrips = arr)
        }
        addLocalLog("TG-${index + 1} TRIP komutu verildi!", "OPERATÖR", 2)
    }

    fun pressAZ5() {
        _controlState.update { it.copy(az5PressConsumed = true) }
        addLocalLog("!!! AZ-5 BASILDI !!! Acil durdurma komutu.", "OPERATÖR", 3)
        soundManager.playScram()
    }

    fun activateECCS() {
        _controlState.update { it.copy(eccsRequestConsumed = true) }
        addLocalLog("SAOR aktivasyonu talep edildi.", "OPERATÖR", 2)
    }

    fun setAREnabled(enabled: Boolean) {
        _controlState.update { it.copy(arEnabled = enabled) }
        addLocalLog("AR sistemi ${if (enabled) "AÇIK" else "KAPALI"}", "OPERATÖR", 0)
    }

    fun acknowledgeAlarms() {
        _controlState.update { it.copy(ackAlarmsConsumed = true) }
    }

    fun startDieselGenerator() {
        _controlState.update { it.copy(dieselStartConsumed = true) }
        addLocalLog("Dizel jeneratör başlatma komutu.", "OPERATÖR", 1)
    }

    // ─── UI YÖNETİMİ ─────────────────────────────────────────────────────────
    fun dismissBriefing()       { _uiState.update { it.copy(showBriefing       = false) }; startSimulation() }
    fun dismissMissionComplete(){ _uiState.update { it.copy(showMissionComplete = false) } }
    fun dismissMissionFailed()  { _uiState.update { it.copy(showMissionFailed   = false) } }
    fun dismissExplosionScreen(){ _uiState.update { it.copy(showExplosionScreen  = false) } }
    fun showInstructions()      { _uiState.update { it.copy(showInstructions     = true) } }
    fun hideInstructions()      { _uiState.update { it.copy(showInstructions     = false) } }

    override fun onCleared() {
        super.onCleared()
        simJob?.cancel()
        soundManager.release()
    }
}

// ─── UI DURUMU ────────────────────────────────────────────────────────────────
data class UiState(
    val currentLevel:        Int        = 1,
    val levelInfo:           LevelInfo? = null,
    val isRunning:           Boolean    = false,
    val showBriefing:        Boolean    = false,
    val showMissionComplete: Boolean    = false,
    val showMissionFailed:   Boolean    = false,
    val showExplosionScreen: Boolean    = false,
    val showInstructions:    Boolean    = false,
    val missionScore:        Double     = 0.0,
    val failureReason:       String     = ""
)

// ─── KONTROL DURUMU ───────────────────────────────────────────────────────────
data class ControlState(
    val rodTargets:          DoubleArray  = DoubleArray(211) { 1.0 },
    val mcpSetpoints:        DoubleArray  = DoubleArray(8)   { 850.0 },
    val mcpActive:           BooleanArray = BooleanArray(8)  { false },
    val feedwaterSetpoint:   Double       = 6800.0,
    val turbineValves:       DoubleArray  = DoubleArray(2)   { 1.0 },
    val turbineTrips:        BooleanArray = BooleanArray(2)  { false },
    val az5PressConsumed:    Boolean      = false,
    val eccsRequestConsumed: Boolean      = false,
    val ackAlarmsConsumed:   Boolean      = false,
    val dieselStartConsumed: Boolean      = false,
    val arEnabled:           Boolean      = true
) {
    // data class equals/hashCode diziler için çalışmaz, manuel yap
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ControlState) return false
        return feedwaterSetpoint == other.feedwaterSetpoint &&
               az5PressConsumed == other.az5PressConsumed &&
               eccsRequestConsumed == other.eccsRequestConsumed &&
               arEnabled == other.arEnabled &&
               ackAlarmsConsumed == other.ackAlarmsConsumed &&
               dieselStartConsumed == other.dieselStartConsumed
    }
    override fun hashCode(): Int {
        var result = feedwaterSetpoint.hashCode()
        result = 31 * result + arEnabled.hashCode()
        return result
    }
}
