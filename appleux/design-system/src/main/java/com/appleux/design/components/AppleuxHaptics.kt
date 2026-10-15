package com.appleux.design.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi

class AppleuxHaptics private constructor(private val context: Context) {

    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val isHapticEnabled: Boolean = true
    private var prepared: Boolean = false

    enum class ImpactStyle {
        LIGHT, MEDIUM, HEAVY, SOFT, RIGID
    }

    enum class NotificationType {
        SUCCESS, WARNING, ERROR
    }

    fun prepare() {
        prepared = true
    }

    fun lightImpact() = impact(ImpactStyle.LIGHT)
    fun mediumImpact() = impact(ImpactStyle.MEDIUM)
    fun heavyImpact() = impact(ImpactStyle.HEAVY)
    fun softImpact() = impact(ImpactStyle.SOFT)
    fun rigidImpact() = impact(ImpactStyle.RIGID)

    fun impact(style: ImpactStyle) {
        if (!isHapticEnabled || vibrator == null) return
        // Haptics are cosmetic. A missing VIBRATE grant must never take down the
        // gesture that triggered them, so every vibration is best-effort.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val (amplitude, timing) = when (style) {
                    ImpactStyle.LIGHT -> Pair(40f, 8L)
                    ImpactStyle.MEDIUM -> Pair(80f, 12L)
                    ImpactStyle.HEAVY -> Pair(120f, 20L)
                    ImpactStyle.SOFT -> Pair(25f, 6L)
                    ImpactStyle.RIGID -> Pair(100f, 15L)
                }
                vibrator.vibrate(
                    VibrationEffect.createOneShot(timing, (amplitude).toInt())
                )
            } else {
                vibrator.vibrate(50)
            }
        } catch (e: SecurityException) {
        }
    }

    fun notification(type: NotificationType) {
        if (!isHapticEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = when (type) {
                    NotificationType.SUCCESS -> longArrayOf(0, 30, 40, 50)
                    NotificationType.WARNING -> longArrayOf(0, 50, 30, 50)
                    NotificationType.ERROR -> longArrayOf(0, 25, 25, 25, 25, 50)
                }
                val amplitudes = pattern.map { if (it > 0) 80 else 0 }.toIntArray()
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (e: SecurityException) {
        }
    }

    fun selectionChanged() {
        if (!isHapticEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(5L, (30f).toInt())
                )
            } else {
                vibrator.vibrate(10)
            }
        } catch (e: SecurityException) {
        }
    }

    fun scrollView() {
        if (!isHapticEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(1L, (20f).toInt())
                )
            }
        } catch (e: SecurityException) {
        }
    }

    fun error() = notification(NotificationType.ERROR)

    companion object {
        @Volatile
        private var instance: AppleuxHaptics? = null

        fun getInstance(context: Context): AppleuxHaptics {
            return instance ?: synchronized(this) {
                instance ?: AppleuxHaptics(context.applicationContext).also {
                    it.prepare()
                    instance = it
                }
            }
        }
    }
}