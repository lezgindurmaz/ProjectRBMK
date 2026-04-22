package com.rbmk.alexandr.ui.theme

import androidx.compose.ui.graphics.Color

// ─── ANA RENKLER ─────────────────────────────────────────────────────────────
val BackgroundDark    = Color(0xFF0A0C0E)
val SurfaceDark       = Color(0xFF111518)
val SurfaceVariant    = Color(0xFF181D22)
val PanelBorder       = Color(0xFF1E2830)
val GridLine          = Color(0xFF162030)

// ─── NÖTRONİK / GÜÇ GÖSTERGELERİ ─────────────────────────────────────────
val PowerNominal      = Color(0xFF00E676)   // %80-107: yeşil
val PowerHigh         = Color(0xFFFF6F00)   // %107-115: turuncu
val PowerCritical     = Color(0xFFE53935)   // >%115: kırmızı
val PowerLow          = Color(0xFF42A5F5)   // <%10: mavi
val PowerZero         = Color(0xFF546E7A)   // 0: gri

// ─── ORM GÖSTERGELERİ ─────────────────────────────────────────────────────
val OrmSafe           = Color(0xFF00E676)   // ≥30: yeşil
val OrmWarning        = Color(0xFFFFEB3B)   // 15-29: sarı
val OrmDanger         = Color(0xFFFF9800)   // 7-14: turuncu
val OrmCritical       = Color(0xFFE53935)   // <7: kırmızı

// ─── ALARM RENKLERİ ─────────────────────────────────────────────────────────
val AlarmInfo         = Color(0xFF78909C)
val AlarmWarning      = Color(0xFFFFEB3B)
val AlarmAlert        = Color(0xFFFF9800)
val AlarmEmergency    = Color(0xFFE53935)
val AlarmFatal        = Color(0xFFFF1744)

// ─── KONTROL RENKLERI ─────────────────────────────────────────────────────
val ButtonActive      = Color(0xFF00B0FF)
val ButtonInactive    = Color(0xFF1E2830)
val ButtonDanger      = Color(0xFFE53935)
val ButtonWarning     = Color(0xFFFF9800)
val ButtonOk          = Color(0xFF00E676)
val AZ5Red            = Color(0xFFD32F2F)
val AZ5Glow           = Color(0xFFFF1744)

// ─── ROD RENKLERI ─────────────────────────────────────────────────────────
val RodInserted       = Color(0xFF1565C0)   // Tam içeride (mavi): absorbe ediyor
val RodWithdrawn      = Color(0xFFE53935)   // Tam dışarıda (kırmızı): absorbe etmiyor
val RodPartial        = Color(0xFF42A5F5)   // Kısmi
val RodMoving         = Color(0xFFFFEB3B)   // Hareket ediyor

// ─── LOG RENKLERI ─────────────────────────────────────────────────────────
val LogSystem         = Color(0xFF546E7A)
val LogOperator       = Color(0xFF42A5F5)
val LogReactor        = Color(0xFF80CBC4)
val LogWarning        = Color(0xFFFFEB3B)
val LogAlert          = Color(0xFFFF9800)
val LogEmergency      = Color(0xFFE53935)
val LogFatal          = Color(0xFFFF1744)
val LogBackground     = Color(0xFF060809)
val LogText           = Color(0xFFB0BEC5)

// ─── GÖSTERGE RENKLERI ────────────────────────────────────────────────────
val GaugeBackground   = Color(0xFF0D1117)
val GaugeFill         = Color(0xFF00E676)
val GaugeDanger       = Color(0xFFE53935)
val GaugeBorder       = Color(0xFF1E2830)
val GaugeText         = Color(0xFFCFD8DC)
val GaugeUnit         = Color(0xFF607D8B)

// ─── POMPA RENKLERI ──────────────────────────────────────────────────────
val PumpActive        = Color(0xFF00E676)
val PumpInactive      = Color(0xFF37474F)
val PumpFailed        = Color(0xFFE53935)
val PumpWarning       = Color(0xFFFF9800)

// ─── REAKTÖR ÇEKİRDEK VİZUALİZASYONU ───────────────────────────────────
val CoreCold          = Color(0xFF1565C0)
val CoreNominal       = Color(0xFF42A5F5)
val CoreHot           = Color(0xFF66BB6A)
val CoreHighPower     = Color(0xFFFFEB3B)
val CoreOverpower     = Color(0xFFFF6F00)
val CoreCritical      = Color(0xFFE53935)
val CoreMeltdown      = Color(0xFFB71C1C)
val CoreExplosion     = Color(0xFFFF1744)

// ─── ACİL DURUM RENKLERI ─────────────────────────────────────────────────
val EmergencyRed      = Color(0xFFE53935)
val EmergencyRedDark  = Color(0xFF7F0000)
val EmergencyYellow   = Color(0xFFFFEB3B)
val ExplosionOrange   = Color(0xFFFF6F00)
val ExplosionYellow   = Color(0xFFFFEB3B)
val ExplosionWhite    = Color(0xFFFFFFFF)

// ─── METİN RENKLERİ ──────────────────────────────────────────────────────
val TextPrimary       = Color(0xFFECEFF1)
val TextSecondary     = Color(0xFF90A4AE)
val TextDim           = Color(0xFF546E7A)
val TextGlowing       = Color(0xFF00E5FF)
val TextRussia        = Color(0xFFFFCDD2)   // Rusça etiketler için
val TextUnit          = Color(0xFF78909C)

// ─── YARDIMCI FONKSİYONLAR ───────────────────────────────────────────────
fun powerColor(pct: Double): Color = when {
    pct > 115.0 -> PowerCritical
    pct > 107.0 -> PowerHigh
    pct > 10.0  -> PowerNominal
    pct > 0.1   -> PowerLow
    else         -> PowerZero
}

fun ormColor(orm: Int): Color = when {
    orm < 7  -> OrmCritical
    orm < 15 -> OrmDanger
    orm < 30 -> OrmWarning
    else     -> OrmSafe
}

fun tempColor(temp: Double, nominal: Double): Color = when {
    temp > nominal * 1.15 -> AlarmEmergency
    temp > nominal * 1.08 -> AlarmAlert
    temp > nominal * 1.03 -> AlarmWarning
    else                   -> GaugeFill
}

fun pressColor(press: Double, nominal: Double = 6.9): Color = when {
    press > 8.0    -> AlarmEmergency
    press > 7.5    -> AlarmAlert
    press < 5.0    -> AlarmWarning
    press < 4.0    -> AlarmEmergency
    else           -> GaugeFill
}

fun coreColor(powerPct: Double): Color = when {
    powerPct > 115.0 -> CoreExplosion
    powerPct > 107.0 -> CoreCritical
    powerPct > 100.0 -> CoreOverpower
    powerPct > 50.0  -> CoreHot
    powerPct > 10.0  -> CoreNominal
    powerPct > 0.0   -> CoreCold
    else             -> Color(0xFF0D1117)
}

fun alarmColor(severity: Int): Color = when (severity) {
    4 -> AlarmFatal
    3 -> AlarmEmergency
    2 -> AlarmAlert
    1 -> AlarmWarning
    else -> AlarmInfo
}
