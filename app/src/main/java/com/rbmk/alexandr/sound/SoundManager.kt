package com.rbmk.alexandr.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

class SoundManager(private val context: Context) {

    private var emergencyPlayer: MediaPlayer? = null
    private var backgroundPlayer: MediaPlayer? = null
    private val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 80)
    private val handler = Handler(Looper.getMainLooper())

    // Durum izleme
    private var emergencyPlaying = false
    private var warningBeepJob: Runnable? = null

    // ─── ACİL ALARM ─────────────────────────────────────────────────────────
    fun playEmergencyAlarm() {
        if (emergencyPlaying) return
        emergencyPlaying = true
        // Programatik alarm: sürekli yüksek-alçak ses
        startEmergencyBeepLoop()
    }

    fun stopEmergencyAlarm() {
        if (!emergencyPlaying) return
        emergencyPlaying = false
        handler.removeCallbacksAndMessages(null)
        toneGen.stopTone()
    }

    private fun startEmergencyBeepLoop() {
        if (!emergencyPlaying) return
        try {
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
            handler.postDelayed({
                toneGen.stopTone()
                if (emergencyPlaying) {
                    handler.postDelayed({ startEmergencyBeepLoop() }, 200)
                }
            }, 400)
        } catch (e: Exception) { /* ses sistemi kullanılamıyor */ }
    }

    // ─── UYARI BEEPI ────────────────────────────────────────────────────────
    fun playWarningBeep() {
        try {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (e: Exception) { }
    }

    // ─── SCRAM SESİ ─────────────────────────────────────────────────────────
    fun playScram() {
        // Üç hızlı bip: SCRAM!
        try {
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
            handler.postDelayed({
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
                handler.postDelayed({
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
                }, 250)
            }, 250)
        } catch (e: Exception) { }
    }

    // ─── PATLAMA SESİ ────────────────────────────────────────────────────────
    fun playExplosion() {
        // Uzun yüksek ton + titreşim efekti
        try {
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 3000)
        } catch (e: Exception) { }
    }

    // ─── ÇEKİRDEK HASARI ─────────────────────────────────────────────────────
    fun playMeltdown() {
        try {
            toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 1500)
        } catch (e: Exception) { }
    }

    // ─── POMPA ARIZA SESİ ────────────────────────────────────────────────────
    fun playPumpFault() {
        try {
            toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 500)
        } catch (e: Exception) { }
    }

    // ─── BUTON TIK SESİ ──────────────────────────────────────────────────────
    fun playButtonClick() {
        try {
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 50)
        } catch (e: Exception) { }
    }

    // ─── TEMİZLEME ───────────────────────────────────────────────────────────
    fun release() {
        handler.removeCallbacksAndMessages(null)
        toneGen.stopTone()
        toneGen.release()
        emergencyPlayer?.release()
        backgroundPlayer?.release()
    }
}
