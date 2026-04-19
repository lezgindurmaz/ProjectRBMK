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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.lifecycle.viewmodel.compose.viewModel
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
    vm: ReactorViewModel = viewModel()
) {
    val state by vm.reactorState.collectAsState()
    val uiSt  by vm.uiState.collectAsState()
    val logs  by vm.logEntries.collectAsState()

    LaunchedEffect(level) { vm.initGame(level) }

    val infiniteTransition = rememberInfiniteTransition(label = "alarm")
    val alarmBlink by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 800; 0f at 0; 1f at 400; 0f at 800 }
        ), label = "blink"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            state.reactorDestroyed -> Color(0x667F0000)
            state.hasEmergencyAlarm -> Color(0x0DE53935).copy(alpha = 0.05f + alarmBlink * 0.05f)
            else -> BackgroundDark
        },
        animationSpec = tween(300), label = "bg"
    )

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Üst Bar ────────────────────────────────────────────────────────
            TopStatusBar(state = state, uiState = uiSt, vm = vm)

            // ── Üst Bölüm: Görsel + Göstergeler + Log ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(4.dp)
                ) {
                    ReactorCoreVisualization(state = state, alarmBlink = alarmBlink)
                }
                Box(
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight()
                        .padding(4.dp)
                ) {
                    GaugesPanel(state = state)
                }
                Box(
                    modifier = Modifier
                        .weight(0.25f)
                        .fillMaxHeight()
                        .padding(4.dp)
                ) {
                    LogPanel(logs = logs)
                }
            }

            // Yatay ayırıcı
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PanelBorder))

            // ── Alt Bölüm: Kontrol Paneli ──────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().weight(0.58f)) {
                ControlPanel(state = state, vm = vm)
            }
        }

        // ── Overlay Dialoglar ──────────────────────────────────────────────────
        if (uiSt.showBriefing) {
            BriefingDialog(uiState = uiSt, onDismiss = { vm.dismissBriefing() })
        }
        if (uiSt.showMissionComplete) {
            MissionCompleteDialog(
                score    = uiSt.missionScore,
                levelNum = uiSt.currentLevel,
                onContinue = { vm.dismissMissionComplete() }
            )
        }
        if (uiSt.showMissionFailed) {
            MissionFailedDialog(
                reason  = uiSt.failureReason,
                onRetry = {
                    vm.dismissMissionFailed()
                    vm.initGame(uiSt.currentLevel)
                }
            )
        }
        if (uiSt.showExplosionScreen) {
            ExplosionScreen(level = uiSt.currentLevel, onDismiss = { vm.dismissExplosionScreen() })
        }
        if (uiSt.showInstructions) {
            InstructionsDialog(levelInfo = uiSt.levelInfo, onDismiss = { vm.hideInstructions() })
        }
    }
}

// ─── ÜST DURUM ÇUBUĞU ────────────────────────────────────────────────────────
@Composable
fun TopStatusBar(state: ReactorStateModel, uiState: UiState, vm: ReactorViewModel) {
    val timeStr = remember(state.simulationTime) {
        val t = state.simulationTime.toLong()
        "%02d:%02d:%02d".format(t / 3600, (t / 60) % 60, t % 60)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(SurfaceVariant)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "LEVEL ${uiState.currentLevel}: ${uiState.levelInfo?.title ?: ""}",
            color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "● ${state.status.displayName}",
            color = Color(state.status.colorCode), fontSize = 11.sp,
            fontWeight = FontWeight.Bold, fontFamily = Mono,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SİM: $timeStr", color = TextSecondary, fontSize = 10.sp, fontFamily = Mono)
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

// ─── REAKTÖR ÇEKİRDEK VİZUALİZASYONU ────────────────────────────────────────
@Composable
fun ReactorCoreVisualization(state: ReactorStateModel, alarmBlink: Float) {
    val coreCol = coreColor(state.powerPercent)
    val animatedPower by animateFloatAsState(
        targetValue = (state.powerPercent / 100f).toFloat().coerceIn(0f, 5f),
        animationSpec = tween(200), label = "power"
    )
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state.powerPercent > 5.0)
                    (1200.0 / (state.powerPercent / 50.0 + 1.0)).toInt().coerceIn(300, 1200)
                else 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GaugeBackground, RoundedCornerShape(6.dp))
            .border(1.dp, PanelBorder, RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Text(
            "РЕАКТОР РБМК-1000 — ВИЗУАЛИЗАЦИЯ",
            color = TextSecondary, fontSize = 8.sp, fontFamily = Mono,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawReactorCore(
                    animatedPower  = animatedPower,
                    pulseScale     = if (state.powerPercent > 1.0) pulseScale else 1f,
                    coreColor      = coreCol,
                    voidFraction   = (state.voidFraction / 100f).toFloat(),
                    blinkAlpha     = if (state.hasEmergencyAlarm) alarmBlink else 0f,
                    scramActive    = state.scramActive,
                    rodPositions   = state.rodPositions,
                    fuelMelt       = state.fuelMelt,
                    destroyed      = state.reactorDestroyed
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniInfoItem("GÜÇ",  "%.1f%%".format(state.powerPercent), powerColor(state.powerPercent))
            MiniInfoItem("ORM",  "${state.orm}", ormColor(state.orm))
            MiniInfoItem("VOID", "%.1f%%".format(state.voidFraction), if (state.voidFraction > 30) AlarmEmergency else GaugeFill)
            MiniInfoItem("Xe",   "%.2f".format(state.xenonLevel),     if (state.xenonLevel > 1.5) AlarmWarning else GaugeFill)
        }
    }
}

private fun DrawScope.drawReactorCore(
    animatedPower: Float,
    pulseScale: Float,
    coreColor: Color,
    voidFraction: Float,
    blinkAlpha: Float,
    scramActive: Boolean,
    rodPositions: DoubleArray,
    fuelMelt: Boolean,
    destroyed: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r  = minOf(cx, cy) * 0.85f

    // Arka plan
    drawRect(color = Color(0xFF060809), size = size)

    if (destroyed) {
        for (i in 1..6) {
            val fr = i / 6f
            drawCircle(color = Color(0xFFFF6F00).copy(alpha = (1f - fr) * 0.8f),
                       radius = r * fr * 1.5f, center = Offset(cx, cy))
        }
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = r * 0.3f, center = Offset(cx, cy))
        return
    }

    // Dış halka
    drawCircle(color = Color(0xFF0D1F2D), radius = r, center = Offset(cx, cy))
    drawCircle(color = Color(0xFF1E3040), radius = r, center = Offset(cx, cy),
               style = Stroke(width = 3f))

    // Güç gradyanı
    val glowR = r * 0.85f * pulseScale
    val pwr   = animatedPower.coerceIn(0f, 1f)
    drawCircle(
        brush  = Brush.radialGradient(
            colors = listOf(
                coreColor.copy(alpha = pwr * 0.9f + 0.05f),
                coreColor.copy(alpha = pwr * 0.4f),
                Color.Transparent
            ),
            center = Offset(cx, cy), radius = glowR
        ),
        radius = glowR, center = Offset(cx, cy)
    )

    // Void kabarcıkları
    if (voidFraction > 0.05f) {
        val rng = java.util.Random(42L)
        val numBubbles = (voidFraction * 30).toInt().coerceIn(0, 30)
        repeat(numBubbles) {
            val angle = rng.nextDouble() * 2 * PI
            val dist  = rng.nextDouble() * r * 0.7
            drawCircle(
                color  = Color(0xFF42A5F5).copy(alpha = voidFraction * 0.6f),
                radius = rng.nextFloat() * 4f + 2f,
                center = Offset(
                    cx + (dist * cos(angle)).toFloat(),
                    cy + (dist * sin(angle)).toFloat()
                )
            )
        }
    }

    // Kontrol çubukları (15×14 ızgara)
    val gridCols = 15; val gridRows = 14
    val gridW = r * 1.6f / gridCols; val gridH = r * 1.6f / gridRows
    val startX = cx - r * 0.8f; val startY = cy - r * 0.7f
    var rodIdx = 0
    outer@ for (row in 0 until gridRows) {
        for (col in 0 until gridCols) {
            if (rodIdx >= rodPositions.size) break@outer
            val rx = startX + col * gridW + gridW / 2f
            val ry = startY + row * gridH + gridH / 2f
            val dist = sqrt((rx - cx) * (rx - cx) + (ry - cy) * (ry - cy))
            if (dist > r * 0.82f) continue
            val pos = rodPositions[rodIdx++].toFloat()
            val rodCol = when {
                pos < 0.1f -> Color(0xFF1565C0).copy(alpha = 0.9f)
                pos > 0.9f -> Color(0xFFE53935).copy(alpha = 0.8f)
                else       -> Color(0xFF42A5F5).copy(alpha = 0.7f)
            }
            drawRect(
                color   = rodCol,
                topLeft = Offset(rx - gridW * 0.35f, ry - gridH * 0.35f),
                size    = Size(gridW * 0.7f, gridH * 0.7f)
            )
        }
    }

    // Yakıt erimesi
    if (fuelMelt) {
        drawCircle(
            color  = Color(0xFFFF6F00).copy(alpha = 0.5f + blinkAlpha * 0.3f),
            radius = r * 0.4f, center = Offset(cx, cy)
        )
    }

    // SCRAM göstergesi - kırmızı bant
    if (scramActive) {
        drawRect(
            color   = Color(0xFFE53935).copy(alpha = 0.3f + blinkAlpha * 0.4f),
            topLeft = Offset(cx - 45f, cy - 13f),
            size    = Size(90f, 26f)
        )
    }

    // Alarm çerçeve halkası
    if (blinkAlpha > 0.1f) {
        drawCircle(
            color  = Color(0xFFE53935).copy(alpha = blinkAlpha * 0.6f),
            radius = r + 4f, center = Offset(cx, cy),
            style  = Stroke(width = 4f)
        )
    }
}

@Composable
fun MiniInfoItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        Text(value, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("GÖSTERGELER", color = TextSecondary, fontSize = 8.sp,
             fontFamily = Mono, fontWeight = FontWeight.Bold,
             modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        GaugeBig(
            label  = "TERMAL GÜÇ",  labelRu = "МОЩНОСТЬ",
            value  = state.powerMW,  unit    = "MW",
            color  = powerColor(state.powerPercent),
            pct    = (state.powerPercent / 100.0).coerceIn(0.0, 1.5)
        )
        GaugeMini("ORM", "Стержни",
            "${state.orm} çubuk", ormColor(state.orm),
            (state.orm.toDouble() / 211.0).coerceIn(0.0, 1.0))
        GaugeMini("REAKTİVİTE (β)", "Реактивность",
            "%.4f β".format(state.reactivityTotal),
            if (state.reactivityTotal > 0.5) AlarmEmergency
            else if (state.reactivityTotal > 0) AlarmWarning else GaugeFill,
            ((state.reactivityTotal + 1.0) / 2.0).coerceIn(0.0, 1.0))

        SectionDivider("TERMİK & BASINÇ")
        GaugeMini("SOĞUTUCU GİRİŞ",   "Вход теп.",    "%.1f °C".format(state.coolantTempIn),
                  tempColor(state.coolantTempIn, 265.0),   state.coolantTempIn / 400.0)
        GaugeMini("SOĞUTUCU ÇIKIŞ",   "Выход теп.",   "%.1f °C".format(state.coolantTempOut),
                  tempColor(state.coolantTempOut, 284.0),  state.coolantTempOut / 400.0)
        GaugeMini("YAKITMDA SICAKLIK","Топливо",       "%.0f °C".format(state.fuelTemp),
                  tempColor(state.fuelTemp, 650.0),        state.fuelTemp / 1800.0)
        GaugeMini("SOĞUTUCU BASINCI", "Давление",      "%.2f MPa".format(state.coolantPressure),
                  pressColor(state.coolantPressure),       state.coolantPressure / 10.0)
        GaugeMini("BUHAR BASINCI",    "Пар.давл.",     "%.2f MPa".format(state.steamPressure),
                  pressColor(state.steamPressure),         state.steamPressure / 10.0)
        GaugeMini("BOŞLUK FRAKS.",    "Пустотность",   "%.1f%%".format(state.voidFraction),
                  if (state.voidFraction > 30) AlarmEmergency
                  else if (state.voidFraction > 15) AlarmWarning else GaugeFill,
                  state.voidFraction / 100.0)

        SectionDivider("BUHAR SEPARATÖRLERI")
        GaugeMini("TAMBUR-1 SEVİYE", "БС-1", "%.1f%%".format(state.drumLevel1),
                  if (state.drumLevel1 < 10 || state.drumLevel1 > 90) AlarmEmergency else GaugeFill,
                  state.drumLevel1 / 100.0)
        GaugeMini("TAMBUR-2 SEVİYE", "БС-2", "%.1f%%".format(state.drumLevel2),
                  if (state.drumLevel2 < 10 || state.drumLevel2 > 90) AlarmEmergency else GaugeFill,
                  state.drumLevel2 / 100.0)

        SectionDivider("ZEHİRLENME")
        GaugeMini("XENON-135", "Ксенон", "%.3f (norm.)".format(state.xenonLevel),
                  if (state.xenonLevel > 1.5) AlarmEmergency
                  else if (state.xenonLevel > 1.0) AlarmWarning else GaugeFill,
                  (state.xenonLevel / 2.0).coerceIn(0.0, 1.0))
        GaugeMini("İYOT-135", "Иод", "%.3f (norm.)".format(state.iodineLevel),
                  GaugeFill, (state.iodineLevel / 1.5).coerceIn(0.0, 1.0))

        SectionDivider("TÜRBİNLER")
        for (t in 0..1) {
            GaugeMini("TG-${t + 1} HIZ", "ТГ-${t + 1}",
                "%.0f RPM".format(state.turbineSpeed[t]),
                if (state.turbineTrip[t]) AlarmEmergency
                else if (!state.turbineOnline[t]) AlarmInfo else PumpActive,
                state.turbineSpeed[t] / 3200.0)
        }

        if (state.hasActiveAlarms) {
            SectionDivider("AKTİF ALARMLAR")
            state.alarms.forEach { AlarmRow(alarm = it) }
        }
    }
}

@Composable
fun GaugeBig(label: String, labelRu: String, value: Double, unit: String, color: Color, pct: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(5.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label,   color = TextSecondary, fontSize = 8.sp, fontFamily = Mono)
            Text(labelRu, color = TextRussia,    fontSize = 7.sp, fontFamily = Mono)
        }
        Text("%.1f %s".format(value, unit),
             color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        Box(
            Modifier.fillMaxWidth().height(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(GaugeBorder)
        ) {
            Box(
                Modifier.fillMaxWidth(pct.toFloat().coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp)).background(color)
            )
        }
        Text("%.1f%%".format(pct * 100), color = TextDim, fontSize = 7.sp, fontFamily = Mono)
    }
}

@Composable
fun GaugeMini(label: String, labelRu: String, value: String, color: Color, fillPct: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(0.50f)) {
            Text(label,   color = TextDim,                   fontSize = 7.sp, fontFamily = Mono,
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(labelRu, color = TextRussia.copy(alpha = 0.6f), fontSize = 6.sp, fontFamily = Mono,
                 maxLines = 1)
        }
        Text(value, color = color, fontSize = 8.sp, fontFamily = Mono,
             fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.35f),
             textAlign = TextAlign.End, maxLines = 1)
        Box(
            Modifier.weight(0.15f).height(8.dp).padding(start = 3.dp)
                .clip(RoundedCornerShape(2.dp)).background(GaugeBorder)
        ) {
            Box(
                Modifier.fillMaxWidth(fillPct.toFloat().coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)).background(color)
            )
        }
    }
}

@Composable
fun SectionDivider(title: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
            .background(col.copy(alpha = if (alarm.acknowledged) 0.05f else 0.12f), RoundedCornerShape(3.dp))
            .border(1.dp, col.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▲ ${alarm.severityName}", color = col, fontSize = 7.sp,
             fontFamily = Mono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(alarm.codeRu,    color = col.copy(alpha = 0.8f), fontSize = 7.sp, fontFamily = Mono)
            Text(alarm.messageTr, color = TextSecondary,          fontSize = 6.sp, fontFamily = Mono, maxLines = 2)
        }
    }
}

// ─── LOG PANELİ ───────────────────────────────────────────────────────────────
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
        Text("СИСТЕМА ЛОГ", color = LogSystem, fontSize = 8.sp, fontFamily = Mono,
             fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(),
             textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(logs, key = { "${it.timestamp}_${it.message.take(20)}" }) { entry ->
                LogEntryRow(entry = entry)
            }
        }
    }
}

@Composable
fun LogEntryRow(entry: LogEntryModel) {
    Text(
        text       = entry.message,
        color      = Color(entry.colorInt),
        fontSize   = 7.sp,
        fontFamily = Mono,
        lineHeight = 10.sp,
        modifier   = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    )
}

// ─── KONTROL PANELİ ───────────────────────────────────────────────────────────
@Composable
fun ControlPanel(state: ReactorStateModel, vm: ReactorViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceVariant)
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ПУЛЬТ УПРАВЛЕНИЯ — ПГВР", color = TextRussia,
                 fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
            Text("KONTROL PANELİ", color = TextSecondary, fontSize = 8.sp, fontFamily = Mono)
        }

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(modifier = Modifier.weight(0.28f).fillMaxHeight()) {
                ControlRodsSection(state = state, vm = vm)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(PanelBorder))
            Column(modifier = Modifier.weight(0.20f).fillMaxHeight()) {
                PumpsSection(state = state, vm = vm)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(PanelBorder))
            Column(modifier = Modifier.weight(0.20f).fillMaxHeight()) {
                TurbineAndSteamSection(state = state, vm = vm)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(PanelBorder))
            Column(modifier = Modifier.weight(0.32f).fillMaxHeight()) {
                SafetySection(state = state, vm = vm)
            }
        }
    }
}

// ─── KONTROL ÇUBUKLARI ────────────────────────────────────────────────────────
@Composable
fun ControlRodsSection(state: ReactorStateModel, vm: ReactorViewModel) {
    val groupNames = listOf("АЗ (AZ)", "РР-1", "РР-2", "РМ-1", "РМ-2", "РМ-3", "РМ-4")
    val groupColors = listOf(ButtonDanger, ButtonActive, ButtonActive,
                              ButtonOk, ButtonOk, ButtonOk, ButtonOk)

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PanelSectionTitle("КОНТРОЛЬ СТЕРЖНЕЙ", "Kontrol Çubukları")
        Text(
            "ORM: ${state.orm} / 211  |  ${if (state.orm < 15) "⚠ DÜŞÜK!" else "✓ OK"}",
            color = ormColor(state.orm), fontSize = 8.sp, fontFamily = Mono
        )

        groupNames.forEachIndexed { idx, name ->
            val start = idx * 30
            val end   = minOf(start + 30, state.rodPositions.size)
            val avgPos = if (end > start) state.rodPositions.slice(start until end).average() else 0.0
            RodGroupSlider(
                name       = name,
                avgPos     = avgPos,
                color      = groupColors[idx],
                onInsert   = { vm.setRodGroupTarget(idx, (avgPos + 0.1).coerceIn(0.0, 1.0)) },
                onWithdraw = { vm.setRodGroupTarget(idx, (avgPos - 0.1).coerceIn(0.0, 1.0)) },
                onFull     = { vm.setRodGroupTarget(idx, 1.0) },
                onZero     = { vm.setRodGroupTarget(idx, 0.0) }
            )
        }

        Spacer(Modifier.weight(1f))
        Text("TOPLU HAREKET:", color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            ControlButton("↑ TÜMÜ ÇIKART", ButtonDanger, Modifier.weight(1f)) {
                for (g in 0..6) vm.setRodGroupTarget(g, 0.0)
            }
            ControlButton("↓ TÜMÜ SOK", ButtonActive, Modifier.weight(1f)) {
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
    val insertedPct  = avgPos.toFloat().coerceIn(0.01f, 1f)
    val withdrawnPct = (1f - insertedPct).coerceIn(0.01f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
            .padding(3.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = color, fontSize = 7.sp, fontFamily = Mono,
                 fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("%.0f%%".format(insertedPct * 100), color = TextSecondary, fontSize = 7.sp, fontFamily = Mono)
        }
        Row(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            Box(Modifier.weight(withdrawnPct).fillMaxHeight().background(AlarmEmergency.copy(alpha = 0.6f)))
            Box(Modifier.weight(insertedPct).fillMaxHeight().background(RodInserted.copy(alpha = 0.8f)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TinyButton("◄◄", ButtonDanger) { onZero() }
            TinyButton("◄",  AlarmWarning) { onWithdraw() }
            TinyButton("►",  RodInserted)  { onInsert() }
            TinyButton("►►", ButtonActive) { onFull() }
        }
    }
}

@Composable
fun TinyButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(14.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 7.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

// ─── POMPALAR ────────────────────────────────────────────────────────────────
@Composable
fun PumpsSection(state: ReactorStateModel, vm: ReactorViewModel) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        PanelSectionTitle("ГЛАВНЫЕ НАСОСЫ", "GTs Pompaları")
        Text("Toplam akış: %.0f kg/s".format(state.coolantFlowTotal),
             color = TextSecondary, fontSize = 7.sp, fontFamily = Mono)

        Text("DEVRE-1:", color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        for (i in 0..3) PumpControl(i, state, vm)
        Spacer(Modifier.height(2.dp))
        Text("DEVRE-2:", color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        for (i in 4..7) PumpControl(i, state, vm)

        Spacer(Modifier.height(6.dp))
        PanelSectionTitle("ПИТАТЕЛЬНАЯ ВОДА", "Besleme Suyu")
        Text("Akış: %.0f kg/s".format(state.feedwaterFlow),
             color = TextSecondary, fontSize = 7.sp, fontFamily = Mono)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TinyButton("-500", AlarmWarning) { vm.setFeedwaterSetpoint(state.feedwaterFlow - 500) }
            TinyButton("-100", AlarmWarning) { vm.setFeedwaterSetpoint(state.feedwaterFlow - 100) }
            TinyButton("+100", ButtonActive) { vm.setFeedwaterSetpoint(state.feedwaterFlow + 100) }
            TinyButton("+500", ButtonActive) { vm.setFeedwaterSetpoint(state.feedwaterFlow + 500) }
        }
    }
}

@Composable
fun PumpControl(idx: Int, state: ReactorStateModel, vm: ReactorViewModel) {
    val active = state.mcpActive[idx]
    val failed = state.mcpFailed[idx]
    val flow   = state.mcpFlow[idx]
    val speed  = state.mcpSpeed[idx]
    val color  = when {
        failed -> PumpFailed
        active -> PumpActive
        else   -> PumpInactive
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("ГЦН-${idx + 1}", color = color, fontSize = 8.sp,
                 fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text("%.0f kg/s  %.0f RPM".format(flow, speed),
                 color = TextDim, fontSize = 6.sp, fontFamily = Mono)
        }
        if (!failed) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 16.dp)
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
            Text("ARIZA", color = PumpFailed, fontSize = 7.sp,
                 fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── TÜRBİN & BUHAR ──────────────────────────────────────────────────────────
@Composable
fun TurbineAndSteamSection(state: ReactorStateModel, vm: ReactorViewModel) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PanelSectionTitle("ТУРБОГЕНЕРАТОРЫ", "Türbin-Jeneratörler")

        for (t in 0..1) {
            val online = state.turbineOnline[t]
            val trip   = state.turbineTrip[t]
            val speed  = state.turbineSpeed[t]
            val load   = state.turbineLoad[t]
            val valve  = state.turbineValve[t]
            val color  = if (trip) PumpFailed else if (online) PumpActive else PumpInactive

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(4.dp))
                    .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ТГ-${t + 1}", color = color, fontSize = 9.sp,
                         fontWeight = FontWeight.Bold, fontFamily = Mono)
                    Text(if (trip) "TRIP" else if (online) "ONLINE" else "OFFLINE",
                         color = color, fontSize = 8.sp, fontFamily = Mono)
                }
                Text("%.0f RPM  |  %.0f MWe".format(speed, load),
                     color = TextSecondary, fontSize = 7.sp, fontFamily = Mono)
                Text("Buhar valfi: %.0f%%".format(valve * 100),
                     color = TextDim, fontSize = 7.sp, fontFamily = Mono)
                if (!trip) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TinyButton("-10%", AlarmWarning) { vm.setTurbineValve(t, valve - 0.10) }
                        TinyButton("-5%",  AlarmWarning) { vm.setTurbineValve(t, valve - 0.05) }
                        TinyButton("+5%",  ButtonActive) { vm.setTurbineValve(t, valve + 0.05) }
                        TinyButton("+10%", ButtonActive) { vm.setTurbineValve(t, valve + 0.10) }
                    }
                    ControlButton("TRIP — ТГ-${t + 1}", ButtonDanger, Modifier.fillMaxWidth()) {
                        vm.tripTurbine(t)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        PanelSectionTitle("ПАРОВОДЯНОЙ ТРАКТ", "Buhar-Su")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InfoLabel("Buhar", "%.0f kg/s".format(state.steamFlow))
            InfoLabel("BW",    "%.0f kg/s".format(state.feedwaterFlow))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InfoLabel("BS-1", "%.1f%%".format(state.drumLevel1))
            InfoLabel("BS-2", "%.1f%%".format(state.drumLevel2))
        }

        Spacer(Modifier.height(4.dp))
        PanelSectionTitle("ЭЛЕКТРОСЕТЬ", "Şebeke")
        Text(
            if (state.stationBlackout) "● ŞEBEKE YOK — ПОТЕРЯ ПИТАНИЯ"
            else "● Şebeke Bağlı — СЕТЬ ПОДКЛЮЧЕНА",
            color = if (state.stationBlackout) AlarmEmergency else ButtonOk,
            fontSize = 8.sp, fontFamily = Mono
        )
        if (state.dieselActive) {
            Text("● DİZEL AKTİF — ДИЗЕЛЬ РАБОТАЕТ",
                 color = AlarmWarning, fontSize = 8.sp, fontFamily = Mono)
        }
    }
}

@Composable
fun InfoLabel(label: String, value: String) {
    Column {
        Text(label, color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        Text(value, color = TextSecondary, fontSize = 8.sp, fontFamily = Mono,
             fontWeight = FontWeight.Bold)
    }
}

// ─── GÜVENLİK BÖLÜMÜ ─────────────────────────────────────────────────────────
@Composable
fun SafetySection(state: ReactorStateModel, vm: ReactorViewModel) {
    val infiniteT = rememberInfiniteTransition(label = "az5glow")
    val az5Glow by infiniteT.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "az5g"
    )

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PanelSectionTitle("СИСТЕМЫ БЕЗОПАСНОСТИ", "Güvenlik Sistemleri")

        // ── AZ-5 BUTONU ──────────────────────────────────────────────────────
        val az5Active = state.scramActive || state.az5Pressed
        val az5Color  = if (az5Active) AlarmEmergency.copy(alpha = az5Glow) else AZ5Red
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (az5Active) AZ5Red.copy(alpha = az5Glow * 0.5f)
                    else AZ5Red.copy(alpha = 0.15f)
                )
                .border(
                    width = if (az5Active) 3.dp else 2.dp,
                    color = az5Color,
                    shape = RoundedCornerShape(6.dp)
                )
                .then(
                    if (!state.az5Pressed && !state.az5Blocked)
                        Modifier.clickable { vm.pressAZ5() }
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (az5Active) "АЗ-5 AKTİF" else "АЗ-5",
                    color = az5Color,
                    fontSize = if (az5Active) 12.sp else 16.sp,
                    fontWeight = FontWeight.Bold, fontFamily = Mono
                )
                Text(
                    if (az5Active) "SCRAM AKTİF" else "ACİL DURDURMA",
                    color = az5Color.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = Mono
                )
                if (state.az5Blocked) {
                    Text("[ BLOKE EDİLMİŞ ]", color = AlarmWarning, fontSize = 7.sp, fontFamily = Mono)
                }
            }
        }

        // ── SAOR / ECCS ──────────────────────────────────────────────────────
        ControlButton(
            label   = if (state.eccsActive) "✓ САОР AKTİF" else "САОР — DEVREYE AL",
            color   = if (state.eccsActive) ButtonOk else ButtonWarning,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.eccsActive
        ) { vm.activateECCS() }

        // ── AR SİSTEMİ ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AR SİSTEMİ", color = TextSecondary, fontSize = 8.sp, fontFamily = Mono)
                Text("СИСТЕМА АР", color = TextRussia.copy(alpha = 0.5f), fontSize = 6.sp, fontFamily = Mono)
            }
            Switch(
                checked = state.arActive,
                onCheckedChange = { vm.setAREnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = ButtonOk,
                    checkedTrackColor   = ButtonOk.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextDim,
                    uncheckedTrackColor = SurfaceDark
                )
            )
        }

        // ── DİZEL JENERATÖR ──────────────────────────────────────────────────
        ControlButton(
            label   = if (state.dieselActive) "✓ DİZEL ÇALIŞIYOR" else "DİZEL — BAŞLAT",
            color   = if (state.dieselActive) ButtonOk else ButtonWarning,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.dieselActive
        ) { vm.startDieselGenerator() }

        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelBorder))

        // ── ALARM ONAYI ───────────────────────────────────────────────────────
        ControlButton("ALARM ONAYLA — КВИТИРОВАТЬ", ButtonWarning, Modifier.fillMaxWidth()) {
            vm.acknowledgeAlarms()
        }

        // ── ALARM LİSTESİ ─────────────────────────────────────────────────────
        if (state.hasActiveAlarms) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                state.alarms.forEach { alarm ->
                    val ac = alarmColor(alarm.severity)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ac.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
                            .border(1.dp, ac.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            .padding(3.dp)
                    ) {
                        Text("▲ ${alarm.codeRu}", color = ac, fontSize = 7.sp,
                             fontFamily = Mono, fontWeight = FontWeight.Bold,
                             modifier = Modifier.weight(1f), maxLines = 1,
                             overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Text("● Aktif alarm yok — Все в норме",
                 color = ButtonOk, fontSize = 8.sp, fontFamily = Mono)
        }

        // ── Alt bilgiler ──────────────────────────────────────────────────────
        Text(
            if (state.skalaActive) "SKALA: ÇALIŞIYOR" else "SKALA: ARIZALI!",
            color = if (state.skalaActive) TextDim else AlarmEmergency,
            fontSize = 7.sp, fontFamily = Mono
        )
        if (state.coolantLeak) {
            Text("⚠ SIZINTI: %.1f kg/s".format(state.leakRate),
                 color = AlarmEmergency, fontSize = 8.sp, fontFamily = Mono,
                 fontWeight = FontWeight.Bold)
        }
        if (state.fuelChannelRupture) {
            Text("!!! YAKIT KANALI PATLAMASI !!!",
                 color = AlarmFatal, fontSize = 9.sp, fontFamily = Mono,
                 fontWeight = FontWeight.Bold)
        }
    }
}

// ─── YARDIMCI BİLEŞENLER ─────────────────────────────────────────────────────
@Composable
fun PanelSectionTitle(titleRu: String, titleTr: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(titleRu, color = TextRussia,   fontSize = 8.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        Text(titleTr, color = TextDim,      fontSize = 7.sp, fontFamily = Mono)
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelBorder))
    }
}

@Composable
fun ControlButton(
    label: String, color: Color, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = if (enabled) 0.15f else 0.05f))
            .border(1.dp, color.copy(alpha = if (enabled) 0.6f else 0.2f), RoundedCornerShape(3.dp))
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
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f).fillMaxHeight(0.88f)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(2.dp, ButtonActive.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text("LEVEL ${info.number}: ${info.title}", color = TextPrimary,
                 fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text(info.subtitle, color = ButtonActive, fontSize = 10.sp, fontFamily = Mono)
            if (info.isHard)  Text("★ ZORG SEVİYESİ", color = AlarmEmergency, fontSize = 9.sp, fontFamily = Mono)
            if (info.isFinal) Text("★ SON SEVİYE",    color = Color(0xFF9C27B0), fontSize = 9.sp, fontFamily = Mono)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(PanelBorder))
            Spacer(Modifier.height(8.dp))

            val scroll = rememberScrollState()
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                Text(info.briefing, color = TextSecondary, fontSize = 9.sp,
                     fontFamily = Mono, lineHeight = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text("GÖREV HEDEFLERİ:", color = ButtonOk, fontSize = 9.sp,
                     fontWeight = FontWeight.Bold, fontFamily = Mono)
                info.objectives.forEachIndexed { i, obj ->
                    Text("${i + 1}. $obj", color = TextSecondary, fontSize = 8.sp,
                         fontFamily = Mono, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("İPUÇLARI:", color = AlarmWarning, fontSize = 9.sp,
                     fontWeight = FontWeight.Bold, fontFamily = Mono)
                info.hints.forEach { hint ->
                    Text("• $hint", color = AlarmWarning.copy(alpha = 0.8f), fontSize = 8.sp,
                         fontFamily = Mono, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ButtonActive.copy(alpha = 0.2f))
                    .border(2.dp, ButtonActive, RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text("GÖREVE BAŞLA — НАЧАТЬ ЗАДАНИЕ", color = ButtonActive,
                     fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            }
        }
    }
}

// ─── GÖREV TAMAMLANDI ────────────────────────────────────────────────────────
@Composable
fun MissionCompleteDialog(score: Double, levelNum: Int, onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.80f)), Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(2.dp, ButtonOk, RoundedCornerShape(8.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("✓ GÖREV TAMAMLANDI", color = ButtonOk, fontSize = 18.sp,
                 fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text("ЗАДАНИЕ ВЫПОЛНЕНО", color = ButtonOk.copy(alpha = 0.6f),
                 fontSize = 10.sp, fontFamily = Mono)
            Text("Level $levelNum başarıyla tamamlandı!", color = TextPrimary,
                 fontSize = 11.sp, fontFamily = Mono)
            Text("Puan: %.0f / 100".format(score), color = AlarmWarning,
                 fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text(
                when {
                    score >= 90 -> "Mükemmel! Gerçek bir RBMK operatörü gibi."
                    score >= 70 -> "İyi iş! Birkaç küçük hata vardı."
                    score >= 50 -> "Geçer not. Daha dikkatli olun."
                    else        -> "Zar zor geçti. Tekrar çalışın."
                },
                color = TextSecondary, fontSize = 9.sp, fontFamily = Mono,
                textAlign = TextAlign.Center
            )
            ControlButton("DEVAM ET — СЛЕДУЮЩИЙ УРОВЕНЬ", ButtonOk, Modifier.fillMaxWidth()) {
                onContinue()
            }
        }
    }
}

// ─── GÖREV BAŞARISIZ ─────────────────────────────────────────────────────────
@Composable
fun MissionFailedDialog(reason: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(2.dp, AlarmEmergency, RoundedCornerShape(8.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("✗ GÖREV BAŞARISIZ", color = AlarmEmergency, fontSize = 18.sp,
                 fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text("ЗАДАНИЕ ПРОВАЛЕНО", color = AlarmEmergency.copy(alpha = 0.6f),
                 fontSize = 10.sp, fontFamily = Mono)
            if (reason.isNotBlank()) {
                Text(reason, color = TextSecondary, fontSize = 9.sp, fontFamily = Mono,
                     textAlign = TextAlign.Center, lineHeight = 14.sp)
            }
            ControlButton("YENİDEN DENE — ПОВТОРИТЬ", AlarmEmergency, Modifier.fillMaxWidth()) {
                onRetry()
            }
        }
    }
}

// ─── PATLAMA EKRANI (Level 16) ────────────────────────────────────────────────
@Composable
fun ExplosionScreen(level: Int, onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 1..5) { delay(1500L); step = i }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.88f).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step >= 1) Text("01:23:44", color = TextGlowing,  fontSize = 14.sp, fontFamily = Mono)
            if (step >= 2) Text("РЕАКТОР УНИЧТОЖЕН", color = AlarmFatal,
                                 fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            if (step >= 3) Text(
                if (level == 16)
                    "AZ-5 basildığında grafit uçlar reaktiviteyi anlık 10 β artırdı.\n" +
                    "Güç 3 saniyede 30.000 MW'a ulaştı — nominal gücün 10 katı.\n" +
                    "İki ardışık patlama reaktörü tamamen yok etti."
                else
                    "Reaktör kontrol dışı kaldı ve patladı.",
                color = TextSecondary, fontSize = 9.sp, fontFamily = Mono,
                textAlign = TextAlign.Center
            )
            if (step >= 4) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(AlarmEmergency.copy(alpha = 0.5f)))
                if (level == 16) {
                    Text(
                        "Aleksandr Bryuhanov terfi alacağı gece görevden alındı.\n\n" +
                        "\"Sayın Bryuhanov, güvenlik sistemleri devre dışı bırakılarak\n" +
                        "reaktör çalıştırıldı. Bu kararın sonuçları tarihte yerini aldı.\n" +
                        "Görevinize son verilmiştir.\"\n\n" +
                        "— İşletme Genel Müdürü, 26 Nisan saat 04:00",
                        color = AlarmWarning, fontSize = 8.sp, fontFamily = Mono,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (step >= 5) {
                ControlButton("DEVAM ET", ButtonActive, Modifier.fillMaxWidth(0.5f)) { onDismiss() }
            }
        }
    }
}

// ─── TALİMATLAR ──────────────────────────────────────────────────────────────
@Composable
fun InstructionsDialog(levelInfo: LevelInfo?, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.80f).fillMaxHeight(0.80f)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, ButtonActive.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Text("OYUN TALİMATLARI", color = ButtonActive, fontSize = 12.sp,
                 fontWeight = FontWeight.Bold, fontFamily = Mono)
            Spacer(Modifier.height(8.dp))
            val scroll = rememberScrollState()
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                InstructionSection("KONTROL ÇUBUKLARI (Sol alt panel)",
                    "◄◄ = Tümü çıkar | ◄ = Kısmen çıkar | ► = Kısmen sok | ►► = Tümü sok\n" +
                    "Çubuk içeride = nötron absorbe ediyor = reaktivite düşük.\n" +
                    "ORM minimum 15 tutun! 7'nin altı KRİTİK tehlike.")
                InstructionSection("POMPALAR (Orta-sol alt panel)",
                    "GTs = Ana Dolaşım Pompası. Açık/Kapalı ile kontrol edin.\n" +
                    "Az akış → yüksek void → RBMK'da büyük tehlike!")
                InstructionSection("AZ-5 ACİL DURDURMA (Sağ panel)",
                    "Büyük kırmızı alan = tüm çubukları anında sok → SCRAM.\n" +
                    "UYARI: RBMK'da AZ-5 ilk 3 saniyede reaktivite ARTIRIR (grafit uç)!")
                InstructionSection("GÖSTERGELER (Üst sağ)",
                    "Güç: MW ve % | ORM: çubuk sayısı | Reaktivite: β biriminde\n" +
                    "Xenon: >1.5 tehlikeli | Void: >30% çok tehlikeli")
                InstructionSection("LOG PANELİ (Üst sağ köşe)",
                    "Gerçek zamanlı sistem olayları.\n" +
                    "Sarı=uyarı | Turuncu=alert | Kırmızı=acil | Beyaz=normal")
                InstructionSection("BUHAR & TÜRBİN",
                    "Tambur seviyeleri %20-80 arasında tutulmalı.\n" +
                    "Türbin valfi: buhar akışını kontrol eder.\n" +
                    "Besleme suyu: tambur seviyesini ayarlar.")
            }
            Spacer(Modifier.height(8.dp))
            ControlButton("KAPAT", ButtonActive, Modifier.fillMaxWidth()) { onDismiss() }
        }
    }
}

@Composable
fun InstructionSection(title: String, content: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(title, color = ButtonActive, fontSize = 9.sp,
             fontFamily = Mono, fontWeight = FontWeight.Bold)
        Text(content, color = TextSecondary, fontSize = 8.sp,
             fontFamily = Mono, lineHeight = 13.sp,
             modifier = Modifier.padding(start = 8.dp, top = 2.dp))
    }
}
