package com.appleux.settings.pref

import android.content.Context
import android.content.SharedPreferences

class AppleuxPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("appleux_prefs", Context.MODE_PRIVATE)

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    companion object {
        @Volatile private var instance: AppleuxPreferences? = null

        fun getInstance(context: Context): AppleuxPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppleuxPreferences(context.applicationContext).also { instance = it }
            }
        }

        const val KEY_ADAPTIVE_PERFORMANCE = "adaptive_performance"
        const val KEY_LOW_POWER_MODE = "low_power_mode"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_AUTO_BRIGHTNESS = "auto_brightness"
        const val KEY_BRIGHTNESS = "brightness"
    }
}