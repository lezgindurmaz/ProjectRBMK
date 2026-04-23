package com.rbmk.alexandr.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.rbmk.alexandr.model.*
import com.rbmk.alexandr.ui.theme.*
import com.rbmk.alexandr.viewmodel.ReactorViewModel
import com.rbmk.alexandr.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlin.math.*

val Mono = FontFamily.Monospace

// ─── ANA OYUN EKRANI ──────────────────────────────────────────────────────────
@Composable
fun GameScreen(
    level: Int,
    vm: ReactorViewModel,
    onBack: () -> Unit
) {
    val state by vm.reactorState.collectAsState()
    val uiSt  by vm.uiState.collectAsState()
    val logs  by vm.logEntries.collectAsState()

    // Her level değişiminde yeniden başlat
    LaunchedEffect(level) { vm.initGame(level) }

    val infiniteT = rememberInfiniteTransition(label = "alarm")
    val alarmBlink by infiniteT.animateFloat(
        0f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 800; 0f at 0; 1f at 400; 0f at 800 }),
        label = "blink"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            state.reactorDestroyed  -> Color(0x667F0000)
            state.hasEmergencyAlarm -> Color(0x0DE53935).copy(alpha = 0.04f + alarmBlink * 0.06f)
            else                    -> BackgroundDark
        },
        animationSpec = tween(300), label = "bg"
    )

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Üst bar
            TopStatusBar(state = state, uiState = uiSt, vm = vm, onBack = onBack)

            // Üst bölüm: görsel + göstergeler + log
            Row(
                modifier = Modifier.fillMaxWidth().weight(0.42f)
            ) {
                Box(Modifier.weight(0.44f).fillMaxHeight().padding(4.dp)) {
                    ReactorCoreVisualization(state = state, alarmBlink = alarmBlink)
                }
                Box(Modifier.weight(0.31f).fillMaxHeight().padding(4.dp)) {
                    GaugesPanel(state = state)
                }
                Box(Modifier.weight(0.25f).fillMaxHeight().padding(4.dp)) {
                    LogPanel(logs = logs)
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(PanelBorder))

            // Alt bölüm: kontrol paneli
            Box(Modifier.fillMaxWidth().weight(0.58f)) {
                ControlPanel(state = state, vm = vm)
            }
        }

        // Dialoglar
        if (uiSt.showBriefing) {
            BriefingDialog(uiState = uiSt, onDismiss = { vm.dismissBriefing() })
        }
        if (uiSt.showMissionComplete) {
            MissionCompleteDialog(
                score    = uiSt.missionScore,
                levelNum = uiSt.currentLevel,
                onContinue = { vm.dismissMissionComplete() },
                onMenu = { vm.dismissMissionComplete(); onBack() }
            )
        }
        if (uiSt.showMissionFailed) {
            MissionFailedDialog(
                reason = uiSt.failureReason,
                onRetry = { vm.dismissMissionFailed(); vm.initGame(uiSt.currentLevel) },
                onMenu  = { vm.dismissMissionFailed(); onBack() }
            )
        }
        if (uiSt.showExplosionScreen) {
            ExplosionScreen(level = uiSt.currentLevel, onDismiss = {
                vm.dismissExplosionScreen(); onBack()
            })
        }
        if (uiSt.showInstructions) {
            InstructionsDialog(levelInfo = uiSt.levelInfo, onDismiss = { vm.hideInstructions() })
        }
    }
}

// ─── ÜST ÇUBUK ────────────────────────────────────────────────────────────────
@Composable
fun TopStatusBar(state: ReactorStateModel, uiState: UiState, vm: ReactorViewModel, onBack: () -> Unit) {
    val t = state.simulationTime.toLong()
    val timeStr = "%02d:%02d:%02d".format(t / 3600, (t / 60) % 60, t % 60)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(SurfaceVariant)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Geri butonu
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ButtonWarning.copy(alpha = 0.15f))
                .border(1.dp, ButtonWarning.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("◄ GERİ", color = ButtonWarning, fontSize = 7.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        }

        Text(
            "LEVEL ${uiState.currentLevel}: ${uiState.levelInfo?.title ?: ""}",
            color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )

        Text(
            "● ${state.status.displayName}",
            color = Color(state.status.colorCode), fontSize = 11.sp,
            fontWeight = FontWeight.Bold, fontFamily = Mono,
            modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SÜRe: $timeStr", color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
            SmallIconButton("?", ButtonActive) { vm.showInstructions() }
        }
    }
}

@Composable
fun SmallIconButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
    }
}

// ─── REAKTÖR GÖRSELLEŞTİRME ──────────────────────────────────────────────────
@Composable
fun ReactorCoreVisualization(state: ReactorStateModel, alarmBlink: Float) {
    val coreCol = coreColor(state.powerPercent)
    val animPow by animateFloatAsState(
        targetValue = (state.powerPercent.toFloat() / 100f).coerceIn(0f, 5f),
        animationSpec = tween(200), label = "pow"
    )
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.96f, 1.04f,
        infiniteRepeatable(
            tween(if (state.powerPercent > 5) (1000.0 / (state.powerPercent / 40.0 + 1.0)).toInt().coerceIn(250, 1000) else 2000,
                  easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GaugeBackground, RoundedCornerShape(6.dp))
            .border(1.dp, if (state.hasEmergencyAlarm) AlarmEmergency.copy(alpha = alarmBlink * 0.8f) else PanelBorder, RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        // Başlık
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RBMK-1000 REAKTÖR ÇEKİRDEĞİ", color = TextSecondary, fontSize = 8.sp, fontFamily = Mono)
            if (state.scramActive) Text("◉ SCRAM AKTİF", color = AlarmEmergency, fontSize = 8.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))

        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                drawRBMKCore(animPow, if (state.powerPercent > 1) pulse else 1f, coreCol,
                    (state.voidFraction / 100f).toFloat(), if (state.hasEmergencyAlarm) alarmBlink else 0f,
                    state.scramActive, state.rodPositions, state.fuelMelt, state.reactorDestroyed,
                    state.powerPercent)
            }
        }

        Spacer(Modifier.height(4.dp))
        // Anlık bilgiler
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CoreInfoBox("GÜÇ",   "%.1f%%".format(state.powerPercent),          powerColor(state.powerPercent))
            CoreInfoBox("ORM",   "${state.orm} çubuk",                         ormColor(state.orm))
            CoreInfoBox("VOID",  "%.1f%%".format(state.voidFraction),           if (state.voidFraction > 30) AlarmEmergency else GaugeFill)
            CoreInfoBox("Xenon", "%.2f".format(state.xenonLevel),              if (state.xenonLevel > 1.5) AlarmWarning else GaugeFill)
            CoreInfoBox("Yakıt", "%.0f°C".format(state.fuelTemp),              tempColor(state.fuelTemp, 650.0))
        }
    }
}

private fun DrawScope.drawRBMKCore(
    animPow: Float, pulse: Float, coreColor: Color,
    voidFrac: Float, blinkA: Float, scramActive: Boolean,
    rods: DoubleArray, melt: Boolean, destroyed: Boolean, powerPct: Double
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r  = minOf(cx, cy) * 0.88f

    drawRect(color = Color(0xFF04080C), size = size)

    if (destroyed) {
        // Patlama animasyonu
        for (i in 1..8) {
            val fr = i / 8f
            drawCircle(Color(if (i < 4) 0xFFFF6F00L.toInt() else 0xFFFF1744L.toInt()).copy(alpha = (1f - fr) * 0.9f),
                radius = r * fr * 1.6f, center = Offset(cx, cy))
        }
        drawCircle(Color.White.copy(alpha = 0.95f), radius = r * 0.25f, center = Offset(cx, cy))
        return
    }

    // Dış koruyucu halka
    drawCircle(Color(0xFF0A1A28), radius = r * 1.02f, center = Offset(cx, cy))
    drawCircle(Color(0xFF1A3040), radius = r, center = Offset(cx, cy), style = Stroke(width = 4f))
    drawCircle(Color(0xFF0D1F2D), radius = r, center = Offset(cx, cy))

    // İç güç gradyanı
    val pwr = animPow.coerceIn(0f, 1f)
    val glowR = r * 0.88f * pulse
    if (pwr > 0.005f) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    coreColor.copy(alpha = (pwr * 0.85f + 0.08f).coerceIn(0f, 1f)),
                    coreColor.copy(alpha = (pwr * 0.45f).coerceIn(0f, 1f)),
                    coreColor.copy(alpha = (pwr * 0.1f).coerceIn(0f, 1f)),
                    Color.Transparent
                ),
                center = Offset(cx, cy), radius = glowR
            ),
            radius = glowR, center = Offset(cx, cy)
        )
    }

    // Void: buhar kabarcıkları
    if (voidFrac > 0.04f) {
        val rng = java.util.Random(42L)
        repeat((voidFrac * 35).toInt().coerceIn(0, 35)) {
            val angle = rng.nextDouble() * 2 * PI
            val dist  = rng.nextDouble() * r * 0.72
            drawCircle(
                color  = Color(0xFF64B5F6).copy(alpha = (voidFrac * 0.55f).coerceIn(0f, 0.8f)),
                radius = rng.nextFloat() * 5f + 2f,
                center = Offset(cx + (dist * cos(angle)).toFloat(), cy + (dist * sin(angle)).toFloat())
            )
        }
    }

    // Kontrol çubukları ızgara
    val cols = 15; val rows = 14
    val gw = r * 1.6f / cols; val gh = r * 1.6f / rows
    val sx = cx - r * 0.8f;   val sy = cy - r * 0.7f
    var idx = 0
    outer@ for (row in 0 until rows) {
        for (col in 0 until cols) {
            if (idx >= rods.size) break@outer
            val rx = sx + col * gw + gw / 2f
            val ry = sy + row * gh + gh / 2f
            if (sqrt((rx - cx).pow(2) + (ry - cy).pow(2)) > r * 0.83f) continue
            val pos = rods[idx++].toFloat()
            val rc = when {
                pos < 0.05f -> Color(0xFF0D47A1).copy(alpha = 0.95f)  // tamamen sokulmuş: koyu mavi
                pos < 0.30f -> Color(0xFF1565C0).copy(alpha = 0.85f)  // büyük kısmı sokulmuş
                pos < 0.70f -> Color(0xFF42A5F5).copy(alpha = 0.75f)  // ortada
                pos < 0.95f -> Color(0xFFFF7043).copy(alpha = 0.75f)  // büyük kısmı çıkmış
                else        -> Color(0xFFE53935).copy(alpha = 0.85f)  // tamamen çıkmış: kırmızı
            }
            drawRect(rc, Offset(rx - gw * 0.38f, ry - gh * 0.38f), Size(gw * 0.76f, gh * 0.76f))
        }
    }

    // Yakıt erimesi kırmızı yayılma
    if (melt) {
        drawCircle(Color(0xFFFF6F00).copy(alpha = 0.5f + blinkA * 0.3f),
            radius = r * 0.42f, center = Offset(cx, cy))
    }

    // SCRAM kırmızı bant + yazı
    if (scramActive) {
        drawRect(Color(0xFFE53935).copy(alpha = 0.28f + blinkA * 0.35f),
            Offset(cx - 55f, cy - 14f), Size(110f, 28f))
    }

    // Alarm kenar halkası
    if (blinkA > 0.1f) {
        drawCircle(Color(0xFFE53935).copy(alpha = blinkA * 0.65f),
            radius = r + 5f, center = Offset(cx, cy), style = Stroke(width = 5f))
    }

    // Güç yüzdesi yazısı (merkeze büyük)
    if (powerPct > 0.5) {
        // Sadece glow efekti - metin canvas'ta yazmak karmaşık, kutu ile çözüldü
    }
}

@Composable
fun CoreInfoBox(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(label, color = TextDim,  fontSize = 6.sp, fontFamily = Mono)
        Text(value, color = color,    fontSize = 8.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

// ─── GÖSTERGELER PANELİ ──────────────────────────────────────────────────────
@Composable
fun GaugesPanel(state: ReactorStateModel) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GaugeBackground, RoundedCornerShape(6.dp))
            .border(1.dp, PanelBorder, RoundedCornerShape(6.dp))
            .padding(5.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("PARAMETRE PANELİ", color = TextSecondary, fontSize = 8.sp,
             fontFamily = Mono, fontWeight = FontWeight.Bold,
             modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        GaugeBig("TERMAL GÜÇ", state.powerMW, "MW",
            powerColor(state.powerPercent), (state.powerPercent / 100.0).coerceIn(0.0, 1.5))

        GaugeMini("Operasyonel Reaktivite Marjı (ORM)",
            "${state.orm} çubuk", ormColor(state.orm),
            (state.orm.toDouble() / 211.0).coerceIn(0.0, 1.0))

        GaugeMini("Reaktivite",
            "%.3f β".format(state.reactivityTotal),
            if (state.reactivityTotal > 0.5) AlarmEmergency
            else if (state.reactivityTotal > 0) AlarmWarning else GaugeFill,
            ((state.reactivityTotal + 1.0) / 2.0).coerceIn(0.0, 1.0))

        SectionDivider("TERMAL & BASINÇ")
        GaugeMini("Soğutucu Giriş",    "%.1f°C".format(state.coolantTempIn),
            tempColor(state.coolantTempIn, 265.0),   state.coolantTempIn / 400.0)
        GaugeMini("Soğutucu Çıkış",    "%.1f°C".format(state.coolantTempOut),
            tempColor(state.coolantTempOut, 284.0),  state.coolantTempOut / 400.0)
        GaugeMini("Yakıt Sıcaklığı",   "%.0f°C".format(state.fuelTemp),
            tempColor(state.fuelTemp, 650.0),        state.fuelTemp / 1800.0)
        GaugeMini("Soğutucu Basıncı",  "%.2f MPa".format(state.coolantPressure),
            pressColor(state.coolantPressure),       state.coolantPressure / 10.0)
        GaugeMini("Buhar Basıncı",     "%.2f MPa".format(state.steamPressure),
            pressColor(state.steamPressure),         state.steamPressure / 10.0)
        GaugeMini("Boşluk (Void)",     "%.1f%%".format(state.voidFraction),
            if (state.voidFraction > 30) AlarmEmergency
            else if (state.voidFraction > 15) AlarmWarning else GaugeFill,
            state.voidFraction / 100.0)

        SectionDivider("BUHAR SEPARATÖRLERI")
        GaugeMini("Tambur-1 Seviyesi", "%.1f%%".format(state.drumLevel1),
            if (state.drumLevel1 < 10 || state.drumLevel1 > 90) AlarmEmergency else GaugeFill,
            state.drumLevel1 / 100.0)
        GaugeMini("Tambur-2 Seviyesi", "%.1f%%".format(state.drumLevel2),
            if (state.drumLevel2 < 10 || state.drumLevel2 > 90) AlarmEmergency else GaugeFill,
            state.drumLevel2 / 100.0)

        SectionDivider("ZEHİRLENME")
        GaugeMini("Xenon-135",  "%.3f (norm.)".format(state.xenonLevel),
            if (state.xenonLevel > 1.5) AlarmEmergency
            else if (state.xenonLevel > 1.0) AlarmWarning else GaugeFill,
            (state.xenonLevel / 2.0).coerceIn(0.0, 1.0))
        GaugeMini("İyot-135",   "%.3f (norm.)".format(state.iodineLevel),
            GaugeFill, (state.iodineLevel / 1.5).coerceIn(0.0, 1.0))

        SectionDivider("TÜRBİNLER")
        for (t in 0..1) {
            GaugeMini("Türbin-${t+1} Hızı", "%.0f RPM".format(state.turbineSpeed[t]),
                if (state.turbineTrip[t]) AlarmEmergency
                else if (!state.turbineOnline[t]) AlarmInfo else PumpActive,
                state.turbineSpeed[t] / 3200.0)
        }

        if (state.hasActiveAlarms) {
            SectionDivider("AKTİF ALARMLAR (${state.alarms.size})")
            state.alarms.forEach { AlarmRow(it) }
        }
    }
}

@Composable
fun GaugeBig(label: String, value: Double, unit: String, color: Color, pct: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(5.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 8.sp, fontFamily = Mono)
        Text("%.1f %s".format(value, unit), color = color,
             fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(3.dp)).background(GaugeBorder)) {
            Box(Modifier.fillMaxWidth(pct.toFloat().coerceIn(0f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text("%.1f%%".format(pct * 100), color = TextDim, fontSize = 7.sp, fontFamily = Mono)
    }
}

@Composable
fun GaugeMini(label: String, value: String, color: Color, fillPct: Double) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDim, fontSize = 7.sp, fontFamily = Mono,
             modifier = Modifier.weight(0.52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = color, fontSize = 8.sp, fontFamily = Mono,
             fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.33f),
             textAlign = TextAlign.End, maxLines = 1)
        Box(Modifier.weight(0.15f).height(7.dp).padding(start = 3.dp)
            .clip(RoundedCornerShape(2.dp)).background(GaugeBorder)) {
            Box(Modifier.fillMaxWidth(fillPct.toFloat().coerceIn(0f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
fun SectionDivider(title: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(PanelBorder))
        Text("  $title  ", color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        Box(Modifier.weight(1f).height(1.dp).background(PanelBorder))
    }
}

@Composable
fun AlarmRow(alarm: AlarmModel) {
    val col = alarmColor(alarm.severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(col.copy(alpha = if (alarm.acknowledged) 0.05f else 0.14f), RoundedCornerShape(3.dp))
            .border(1.dp, col.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▲ ${alarm.severityName}", color = col, fontSize = 7.sp,
             fontFamily = Mono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(alarm.codeRu,    color = col.copy(alpha = 0.9f), fontSize = 7.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
            Text(alarm.messageTr, color = TextSecondary, fontSize = 6.sp, fontFamily = Mono, maxLines = 2)
        }
    }
}

// ─── LOG PANELİ ──────────────────────────────────────────────────────────────
@Composable
fun LogPanel(logs: List<LogEntryModel>) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LogBackground, RoundedCornerShape(6.dp))
            .border(1.dp, PanelBorder, RoundedCornerShape(6.dp))
            .padding(4.dp)
    ) {
        Text("SİSTEM LOG", color = LogSystem, fontSize = 8.sp, fontFamily = Mono,
             fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(),
             textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(logs, key = { it.timestamp.toString() + it.message.take(16) }) { entry ->
                Text(
                    text       = entry.message,
                    color      = Color(entry.colorInt),
                    fontSize   = 7.sp,
                    fontFamily = Mono,
                    lineHeight = 10.sp,
                    modifier   = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                )
            }
        }
    }
}

// ─── KONTROL PANELİ ──────────────────────────────────────────────────────────
@Composable
fun ControlPanel(state: ReactorStateModel, vm: ReactorViewModel) {
    Column(Modifier.fillMaxSize().background(SurfaceVariant).padding(4.dp)) {
        Text("KONTROL PANELİ — RBMK-1000", color = TextPrimary,
             fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
             modifier = Modifier.padding(bottom = 3.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(0.28f).fillMaxHeight()) {
                ControlRodsSection(state, vm)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(PanelBorder))
            Column(Modifier.weight(0.20f).fillMaxHeight()) {
                PumpsSection(state, vm)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(PanelBorder))
            Column(Modifier.weight(0.20f).fillMaxHeight()) {
                TurbineAndSteamSection(state, vm)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(PanelBorder))
            Column(Modifier.weight(0.32f).fillMaxHeight()) {
                SafetySection(state, vm)
            }
        }
    }
}

// ─── KONTROL ÇUBUKLARI ────────────────────────────────────────────────────────
@Composable
fun ControlRodsSection(state: ReactorStateModel, vm: ReactorViewModel) {
    val groupNames  = listOf("ACİL KAPATMA (AZ)", "Regülasyon-1", "Regülasyon-2",
                              "Manuel-1", "Manuel-2", "Manuel-3", "Manuel-4")
    val groupColors = listOf(ButtonDanger, ButtonActive, ButtonActive,
                              ButtonOk, ButtonOk, ButtonOk, ButtonOk)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle("KONTROL ÇUBUKLARI")
        // ORM göstergesi — büyük ve belirgin
        Row(
            Modifier
                .fillMaxWidth()
                .background(ormColor(state.orm).copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                .border(1.dp, ormColor(state.orm).copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("ORM: ${state.orm} çubuk", color = ormColor(state.orm),
                 fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text(if (state.orm < 7) "⚠ KRİTİK!" else if (state.orm < 15) "⚠ DÜŞÜK" else "✓ GÜVENLİ",
                 color = ormColor(state.orm), fontSize = 8.sp, fontFamily = Mono)
        }

        groupNames.forEachIndexed { idx, name ->
            val start = idx * 30
            val end   = minOf(start + 30, state.rodPositions.size)
            val avg   = if (end > start) state.rodPositions.slice(start until end).average() else 0.0
            RodGroupSlider(
                name       = name,
                avgPos     = avg,
                color      = groupColors[idx],
                onInsert   = { vm.setRodGroupTarget(idx, (avg + 0.1).coerceIn(0.0, 1.0)) },
                onWithdraw = { vm.setRodGroupTarget(idx, (avg - 0.1).coerceIn(0.0, 1.0)) },
                onFull     = { vm.setRodGroupTarget(idx, 1.0) },
                onZero     = { vm.setRodGroupTarget(idx, 0.0) }
            )
        }

        Spacer(Modifier.weight(1f))
        Text("TOPLU HAREKET:", color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            CtrlBtn("↑ TÜMÜNÜ ÇIKART", ButtonDanger, Modifier.weight(1f)) {
                for (g in 0..6) vm.setRodGroupTarget(g, 0.0)
            }
            CtrlBtn("↓ TÜMÜNÜ SOK", ButtonActive, Modifier.weight(1f)) {
                for (g in 0..6) vm.setRodGroupTarget(g, 1.0)
            }
        }
    }
}

@Composable
fun RodGroupSlider(
    name: String, avgPos: Double, color: Color,
    onInsert: () -> Unit, onWithdraw: () -> Unit,
    onFull: () -> Unit, onZero: () -> Unit
) {
    val ins = avgPos.toFloat().coerceIn(0.01f, 1f)
    val wit = (1f - ins).coerceIn(0.01f, 1f)
    // Çekilen % = wit = ORM'a katkı
    val withdrawnPct = (1.0 - avgPos) * 100.0

    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
            .padding(3.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = color, fontSize = 7.sp, fontFamily = Mono,
                 fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Çekilen:%.0f%%".format(withdrawnPct), color = TextSecondary, fontSize = 6.sp, fontFamily = Mono)
        }
        // Bar: kırmızı=dışarıda(çekilen), mavi=içeride(sokulmuş)
        Row(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp))) {
            Box(Modifier.weight(wit).fillMaxHeight().background(AlarmEmergency.copy(alpha = 0.65f)))
            Box(Modifier.weight(ins).fillMaxHeight().background(RodInserted.copy(alpha = 0.85f)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniBtn("◄◄", ButtonDanger)  { onZero() }
            MiniBtn("◄",  AlarmWarning) { onWithdraw() }
            MiniBtn("►",  RodInserted)  { onInsert() }
            MiniBtn("►►", ButtonActive) { onFull() }
        }
    }
}

@Composable
fun MiniBtn(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .height(15.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 7.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

// ─── POMPALAR ────────────────────────────────────────────────────────────────
@Composable
fun PumpsSection(state: ReactorStateModel, vm: ReactorViewModel) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle("ANA DOLAŞIM POMPALARI")
        Text("Toplam akış: %.0f kg/s".format(state.coolantFlowTotal),
             color = if (state.coolantFlowTotal < 2000) AlarmEmergency else TextSecondary,
             fontSize = 7.sp, fontFamily = Mono)

        Text("1. Devre (Pompa 1-4):", color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        for (i in 0..3) PumpRow(i, state, vm)
        Spacer(Modifier.height(3.dp))
        Text("2. Devre (Pompa 5-8):", color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        for (i in 4..7) PumpRow(i, state, vm)

        Spacer(Modifier.height(6.dp))
        SectionTitle("BESLEME SUYU")
        Text("Akış: %.0f kg/s".format(state.feedwaterFlow), color = TextSecondary, fontSize = 7.sp, fontFamily = Mono)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniBtn("-500", AlarmWarning) { vm.setFeedwaterSetpoint(state.feedwaterFlow - 500) }
            MiniBtn("-100", AlarmWarning) { vm.setFeedwaterSetpoint(state.feedwaterFlow - 100) }
            MiniBtn("+100", ButtonActive) { vm.setFeedwaterSetpoint(state.feedwaterFlow + 100) }
            MiniBtn("+500", ButtonActive) { vm.setFeedwaterSetpoint(state.feedwaterFlow + 500) }
        }
    }
}

@Composable
fun PumpRow(idx: Int, state: ReactorStateModel, vm: ReactorViewModel) {
    val active = state.mcpActive[idx]
    val failed = state.mcpFailed[idx]
    val color  = when { failed -> PumpFailed; active -> PumpActive; else -> PumpInactive }

    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Pompa-${idx+1}", color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text("%.0f kg/s  %.0f RPM".format(state.mcpFlow[idx], state.mcpSpeed[idx]),
                 color = TextDim, fontSize = 6.sp, fontFamily = Mono)
        }
        if (!failed) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) ButtonOk.copy(alpha = 0.25f) else ButtonInactive)
                    .border(1.dp, if (active) ButtonOk else TextDim, RoundedCornerShape(2.dp))
                    .clickable { vm.setMCPActive(idx, !active) },
                contentAlignment = Alignment.Center
            ) {
                Text(if (active) "AÇIK" else "KAPALI",
                     color = if (active) ButtonOk else TextDim,
                     fontSize = 6.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("ARIZA!", color = PumpFailed, fontSize = 7.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── TÜRBİN & BUHAR ──────────────────────────────────────────────────────────
@Composable
fun TurbineAndSteamSection(state: ReactorStateModel, vm: ReactorViewModel) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionTitle("TÜRBİN-JENERATÖRLER")

        for (t in 0..1) {
            val online = state.turbineOnline[t]; val trip = state.turbineTrip[t]
            val speed = state.turbineSpeed[t];   val load = state.turbineLoad[t]
            val valve = state.turbineValve[t]
            val color = if (trip) PumpFailed else if (online) PumpActive else PumpInactive

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(4.dp))
                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Türbin-${t+1}", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                    Text(if (trip) "TRIP" else if (online) "ÇALIŞIYOR" else "DURDU",
                         color = color, fontSize = 8.sp, fontFamily = Mono)
                }
                Text("%.0f RPM  |  %.0f MWe".format(speed, load), color = TextSecondary, fontSize = 7.sp, fontFamily = Mono)
                Text("Buhar valfi: %.0f%%".format(valve * 100), color = TextDim, fontSize = 7.sp, fontFamily = Mono)
                if (!trip) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MiniBtn("-10%", AlarmWarning) { vm.setTurbineValve(t, valve - 0.10) }
                        MiniBtn("-5%",  AlarmWarning) { vm.setTurbineValve(t, valve - 0.05) }
                        MiniBtn("+5%",  ButtonActive) { vm.setTurbineValve(t, valve + 0.05) }
                        MiniBtn("+10%", ButtonActive) { vm.setTurbineValve(t, valve + 0.10) }
                    }
                    CtrlBtn("TRİP — Türbin-${t+1}", ButtonDanger, Modifier.fillMaxWidth()) { vm.tripTurbine(t) }
                }
            }
        }

        Spacer(Modifier.height(3.dp))
        SectionTitle("BUHAR & SU")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InfoLbl("Buhar akışı", "%.0f kg/s".format(state.steamFlow))
            InfoLbl("Besleme suyu", "%.0f kg/s".format(state.feedwaterFlow))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InfoLbl("Tambur-1", "%.1f%%".format(state.drumLevel1))
            InfoLbl("Tambur-2", "%.1f%%".format(state.drumLevel2))
        }

        Spacer(Modifier.height(3.dp))
        SectionTitle("ELEKTRİK")
        Text(
            if (state.stationBlackout) "● ŞEBEKE YOK!"
            else "● Şebeke bağlı",
            color = if (state.stationBlackout) AlarmEmergency else ButtonOk,
            fontSize = 8.sp, fontFamily = Mono
        )
        if (state.dieselActive) Text("● Dizel Jeneratör Aktif", color = AlarmWarning, fontSize = 8.sp, fontFamily = Mono)
    }
}

@Composable
fun InfoLbl(label: String, value: String) {
    Column {
        Text(label, color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        Text(value, color = TextSecondary, fontSize = 8.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

// ─── GÜVENLİK BÖLÜMÜ ─────────────────────────────────────────────────────────
@Composable
fun SafetySection(state: ReactorStateModel, vm: ReactorViewModel) {
    val inf = rememberInfiniteTransition(label = "az5")
    val az5Glow by inf.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g")

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("GÜVENLİK SİSTEMLERİ")

        // ── AZ-5 ────────────────────────────────────────────────────────────
        val az5On   = state.scramActive || state.az5Pressed
        val az5Col  = if (az5On) AlarmEmergency.copy(alpha = az5Glow) else AZ5Red
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (az5On) AZ5Red.copy(alpha = az5Glow * 0.55f) else AZ5Red.copy(alpha = 0.12f))
                .border(if (az5On) 3.dp else 2.dp, az5Col, RoundedCornerShape(6.dp))
                .then(if (!state.az5Pressed && !state.az5Blocked) Modifier.clickable { vm.pressAZ5() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (az5On) "AZ-5 AKTİF" else "AZ-5",
                     color = az5Col, fontSize = if (az5On) 11.sp else 16.sp,
                     fontWeight = FontWeight.Bold, fontFamily = Mono)
                Text(if (az5On) "ACİL DURDURMA AKTİF" else "ACİL DURDURMA",
                     color = az5Col.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = Mono)
                if (state.az5Blocked) Text("[KİLİTLİ]", color = AlarmWarning, fontSize = 7.sp, fontFamily = Mono)
            }
        }

        // ── SAOR / ECCS ──────────────────────────────────────────────────────
        CtrlBtn(
            if (state.eccsActive) "✓ ACİL SOĞUTMA (SAOR) AKTİF" else "ACİL SOĞUTMA (SAOR) — AÇ",
            if (state.eccsActive) ButtonOk else ButtonWarning, Modifier.fillMaxWidth(),
            enabled = !state.eccsActive
        ) { vm.activateECCS() }

        // ── AR SİSTEMİ ───────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("OTOMATİK REGÜLASYON (AR)", color = TextSecondary, fontSize = 8.sp, fontFamily = Mono)
                Text(if (state.arActive) "Aktif — otomatik düzeltme var" else "Kapalı — sadece manuel",
                     color = if (state.arActive) ButtonOk else AlarmWarning, fontSize = 7.sp, fontFamily = Mono)
            }
            Switch(
                checked = state.arActive,
                onCheckedChange = { vm.setAREnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ButtonOk, checkedTrackColor = ButtonOk.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextDim, uncheckedTrackColor = SurfaceDark
                )
            )
        }

        // ── DİZEL JENERATÖR ──────────────────────────────────────────────────
        CtrlBtn(
            if (state.dieselActive) "✓ DİZEL JENERATÖR ÇALIŞIYOR" else "DİZEL JENERATÖR — BAŞLAT",
            if (state.dieselActive) ButtonOk else ButtonWarning, Modifier.fillMaxWidth(),
            enabled = !state.dieselActive
        ) { vm.startDieselGenerator() }

        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelBorder))

        // ── ALARM ONAYI ───────────────────────────────────────────────────────
        CtrlBtn("ALARMLARI ONAYLA (${state.alarms.size})", ButtonWarning, Modifier.fillMaxWidth()) {
            vm.acknowledgeAlarms()
        }

        // ── ALARM LİSTESİ ─────────────────────────────────────────────────────
        val scroll = rememberScrollState()
        if (state.hasActiveAlarms) {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                state.alarms.forEach { alarm ->
                    val ac = alarmColor(alarm.severity)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(ac.copy(alpha = 0.09f), RoundedCornerShape(3.dp))
                            .border(1.dp, ac.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                            .padding(3.dp)
                    ) {
                        Text("▲ ${alarm.codeRu}", color = ac, fontSize = 7.sp,
                             fontFamily = Mono, fontWeight = FontWeight.Bold)
                        Text(alarm.messageTr, color = TextSecondary, fontSize = 6.sp,
                             fontFamily = Mono, maxLines = 2)
                    }
                }
            }
        } else {
            Text("✓ Aktif alarm yok — Sistem normal", color = ButtonOk, fontSize = 8.sp, fontFamily = Mono)
        }

        // Alt bilgi
        Text(if (state.skalaActive) "SKALA: Çalışıyor" else "SKALA: ARIZALI!",
             color = if (state.skalaActive) TextDim else AlarmEmergency, fontSize = 7.sp, fontFamily = Mono)
        if (state.coolantLeak) Text("⚠ SIZINTI: %.1f kg/s".format(state.leakRate),
             color = AlarmEmergency, fontSize = 8.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        if (state.fuelChannelRupture) Text("!!! YAKIT KANALI PATLAMASI !!!",
             color = AlarmFatal, fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

// ─── YARDIMCI BİLEŞENLER ─────────────────────────────────────────────────────
@Composable
fun SectionTitle(title: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = TextPrimary, fontSize = 8.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelBorder))
    }
}

@Composable
fun CtrlBtn(
    label: String, color: Color, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = if (enabled) 0.16f else 0.05f))
            .border(1.dp, color.copy(alpha = if (enabled) 0.65f else 0.2f), RoundedCornerShape(3.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) color else color.copy(alpha = 0.4f),
             fontSize = 8.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
             maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─── BRİFİNG DİALOGU ─────────────────────────────────────────────────────────
@Composable
fun BriefingDialog(uiState: UiState, onDismiss: () -> Unit) {
    val info = uiState.levelInfo ?: return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)), Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth(0.88f).fillMaxHeight(0.90f)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(2.dp, ButtonActive.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("LEVEL ${info.number}: ${info.title}", color = TextPrimary,
                         fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                    Text(info.subtitle, color = ButtonActive, fontSize = 10.sp, fontFamily = Mono)
                }
                Column {
                    if (info.isHard)  Text("★ ZORG", color = AlarmEmergency, fontSize = 10.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                    if (info.isFinal) Text("★ SON SEVİYE", color = Color(0xFF9C27B0), fontSize = 10.sp, fontFamily = Mono)
                    Text(info.difficulty.label.uppercase(), color = Color(info.difficulty.color),
                         fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(PanelBorder))
            Spacer(Modifier.height(6.dp))

            val scroll = rememberScrollState()
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                Text(info.briefing, color = TextSecondary, fontSize = 9.sp,
                     fontFamily = Mono, lineHeight = 14.sp)
                Spacer(Modifier.height(10.dp))

                Text("GÖREV HEDEFLERİ:", color = ButtonOk, fontSize = 9.sp,
                     fontWeight = FontWeight.Bold, fontFamily = Mono)
                info.objectives.forEachIndexed { i, obj ->
                    Row(Modifier.padding(start = 8.dp, top = 3.dp)) {
                        Text("${i+1}.", color = ButtonOk, fontSize = 8.sp, fontFamily = Mono,
                             fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                        Text(obj, color = TextSecondary, fontSize = 8.sp, fontFamily = Mono)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("İPUÇLARI:", color = AlarmWarning, fontSize = 9.sp,
                     fontWeight = FontWeight.Bold, fontFamily = Mono)
                info.hints.forEach { hint ->
                    Text("• $hint", color = AlarmWarning.copy(alpha = 0.85f), fontSize = 8.sp,
                         fontFamily = Mono, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth().height(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ButtonActive.copy(alpha = 0.2f))
                    .border(2.dp, ButtonActive, RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text("GÖREVE BAŞLA", color = ButtonActive,
                     fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            }
        }
    }
}

// ─── GÖREV TAMAMLANDI ────────────────────────────────────────────────────────
@Composable
fun MissionCompleteDialog(score: Double, levelNum: Int, onContinue: () -> Unit, onMenu: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.80f)), Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth(0.60f)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(2.dp, ButtonOk, RoundedCornerShape(8.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("✓ GÖREV TAMAMLANDI", color = ButtonOk, fontSize = 18.sp,
                 fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text("Level $levelNum başarıyla geçildi!", color = TextPrimary, fontSize = 11.sp, fontFamily = Mono)
            Text("Puan: %.0f / 100".format(score), color = AlarmWarning,
                 fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text(
                when {
                    score >= 90 -> "Mükemmel! Gerçek bir RBMK operatörü gibi."
                    score >= 70 -> "İyi iş! Birkaç küçük hata vardı."
                    score >= 50 -> "Kabul edilebilir. Daha dikkatli olun."
                    else        -> "Zar zor geçti. Tekrar çalışın."
                },
                color = TextSecondary, fontSize = 9.sp, fontFamily = Mono, textAlign = TextAlign.Center
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CtrlBtn("ANA MENÜ", ButtonWarning, Modifier.weight(1f)) { onMenu() }
                CtrlBtn("DEVAM ET →", ButtonOk, Modifier.weight(1f)) { onContinue() }
            }
        }
    }
}

// ─── GÖREV BAŞARISIZ ─────────────────────────────────────────────────────────
@Composable
fun MissionFailedDialog(reason: String, onRetry: () -> Unit, onMenu: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)), Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth(0.60f)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(2.dp, AlarmEmergency, RoundedCornerShape(8.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("✗ GÖREV BAŞARISIZ", color = AlarmEmergency, fontSize = 18.sp,
                 fontWeight = FontWeight.Bold, fontFamily = Mono)
            if (reason.isNotBlank()) {
                Text(reason, color = TextSecondary, fontSize = 9.sp, fontFamily = Mono,
                     textAlign = TextAlign.Center, lineHeight = 14.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CtrlBtn("ANA MENÜ", ButtonWarning, Modifier.weight(1f)) { onMenu() }
                CtrlBtn("YENİDEN DENE", AlarmEmergency, Modifier.weight(1f)) { onRetry() }
            }
        }
    }
}

// ─── PATLAMA EKRANI (Level 16) ────────────────────────────────────────────────
@Composable
fun ExplosionScreen(level: Int, onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { for (i in 1..5) { delay(1500L); step = i } }

    Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.88f).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step >= 1) Text("01:23:44", color = TextGlowing, fontSize = 14.sp, fontFamily = Mono)
            if (step >= 2) Text("REAKTÖR YOK EDİLDİ", color = AlarmFatal,
                                 fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            if (step >= 3) Text(
                if (level == 16)
                    "AZ-5 basıldığında grafit uçlar reaktiviteyi anlık artırdı.\n" +
                    "Güç 3 saniyede 30.000 MW'a ulaştı — nominal gücün 10 katı.\n" +
                    "İki ardışık patlama reaktörü tamamen yok etti."
                else "Reaktör kontrol dışı kaldı ve patladı.",
                color = TextSecondary, fontSize = 9.sp, fontFamily = Mono, textAlign = TextAlign.Center
            )
            if (step >= 4 && level == 16) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(AlarmEmergency.copy(alpha = 0.5f)))
                Text(
                    "Aleksandr Bryuhanov terfi alacağı gece görevden alındı.\n\n" +
                    "\"Sayın Bryuhanov, güvenlik sistemleri devre dışı bırakılarak\n" +
                    "reaktör çalıştırıldı. Bu kararın sonuçları tarihte yerini aldı.\n" +
                    "Görevinize son verilmiştir.\"\n\n" +
                    "— İşletme Genel Müdürü, 26 Nisan saat 04:00",
                    color = AlarmWarning, fontSize = 8.sp, fontFamily = Mono, textAlign = TextAlign.Center
                )
            }
            if (step >= 5) {
                CtrlBtn("ANA MENÜYE DÖN", ButtonActive, Modifier.fillMaxWidth(0.5f)) { onDismiss() }
            }
        }
    }
}

// ─── TALİMATLAR ──────────────────────────────────────────────────────────────
@Composable
fun InstructionsDialog(levelInfo: LevelInfo?, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)), Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth(0.82f).fillMaxHeight(0.82f)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, ButtonActive.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Text("OYUN TALİMATLARI", color = ButtonActive, fontSize = 12.sp,
                 fontWeight = FontWeight.Bold, fontFamily = Mono)
            Spacer(Modifier.height(6.dp))
            val scroll = rememberScrollState()
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                HelpSection("KONTROL ÇUBUKLARI (sol bölme)",
                    "◄◄ = Grubu tamamen çıkar (ORM artar, reaktivite düşer)\n" +
                    "◄  = Kısmen çıkar\n" +
                    "►  = Kısmen sok (reaktivite düşer)\n" +
                    "►► = Grubu tamamen sok\n\n" +
                    "Çubuk dışarıda (kırmızı) = ORM katkısı var\n" +
                    "Çubuk içeride (mavi) = nötron absorbe ediyor\n\n" +
                    "ORM < 7  → KRİTİK tehlike!\n" +
                    "ORM < 15 → Minimum marj ihlali (uyarı)\n" +
                    "ORM ≥ 30 → İdeal operasyon")
                HelpSection("POMPALAR (orta-sol bölme)",
                    "8 adet Ana Dolaşım Pompası var (2 devre × 4).\n" +
                    "Açık/Kapalı düğmesiyle her pompa ayrı kontrol edilir.\n" +
                    "Az pompa = az soğutucu akışı = void artışı = RBMK'da tehlike!\n" +
                    "Toplam akış > 3000 kg/s olmalı tam güçte.")
                HelpSection("AZ-5 ACİL DURDURMA (sağ bölme)",
                    "Büyük kırmızı alan = tüm çubukları anında sok → SCRAM.\n" +
                    "UYARI: RBMK'da AZ-5 ilk 1-2 saniyede reaktivite ARTIRIR\n" +
                    "(grafit uç etkisi). Sonra düşer.")
                HelpSection("GÖSTERGELER (üst orta)",
                    "Güç: MW ve %100 nominal üzerinden\n" +
                    "ORM: Kaç çubuk dışarıda (reaktivite rezervi)\n" +
                    "Reaktivite β: 0=kritik, +>0=artan, -<0=azalan\n" +
                    "Xenon: >1.5 tehlikeli birikim\n" +
                    "Void: >30% çok tehlikeli (RBMK pozitif katsayı!)")
                HelpSection("BUHAR & TÜRBİN (orta bölme)",
                    "Tambur seviyeleri %20-80 içinde tutulmalı.\n" +
                    "Besleme suyu: tambur seviyesini kontrol eder.\n" +
                    "Türbin valfi: buhar akışını ve yükü ayarlar.\n" +
                    "Güç artarken besleme suyu artır.")
                HelpSection("LOG PANELİ (üst sağ)",
                    "Gerçek zamanlı sistem olayları.\n" +
                    "Renk: Beyaz=bilgi, Sarı=uyarı, Turuncu=alert, Kırmızı=acil")
            }
            Spacer(Modifier.height(6.dp))
            CtrlBtn("KAPAT", ButtonActive, Modifier.fillMaxWidth()) { onDismiss() }
        }
    }
}

@Composable
fun HelpSection(title: String, content: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(title, color = ButtonActive, fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelBorder.copy(alpha = 0.5f)))
        Text(content, color = TextSecondary, fontSize = 8.sp, fontFamily = Mono,
             lineHeight = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 3.dp))
    }
}
