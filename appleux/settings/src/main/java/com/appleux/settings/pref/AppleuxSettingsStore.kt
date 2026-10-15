package com.appleux.settings.pref

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.appleux.design.runtime.AppleuxRuntimeConfig

/**
 * Routes each setting to the store that the rest of the OS can actually see.
 *
 * Launcher and Settings live in different processes and cannot share app-private
 * SharedPreferences. The flags the launcher must honour are written through
 * [AppleuxRuntimeConfig], which uses the `com.appleux.settings.config`
 * ContentProvider. Settings.System cannot be written by an app on Android 16, so
 * it is not a candidate.
 *
 * Brightness and auto-brightness are platform settings already, so they go
 * straight to the platform keys instead of being duplicated in a private file.
 *
 * Anything not listed here stays in app-private preferences.
 *
 * Every setter returns whether the write succeeded. Callers must not assume a
 * toggle took effect; a failed write has to be reflected in the UI.
 */
class AppleuxSettingsStore(context: Context) {

    private val app = context.applicationContext
    private val prefs = AppleuxPreferences.getInstance(app)

    /** True when the app may write platform brightness settings. */
    fun canWriteSystemSettings(): Boolean = try {
        Settings.System.canWrite(app)
    } catch (e: Exception) {
        false
    }

    /** Intent for the "Modify system settings" screen, when it is not granted. */
    fun writeSettingsIntent() = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${app.packageName}")
    )

    private val sharedFlagKeys = setOf(
        AppleuxRuntimeConfig.KEY_ADAPTIVE_PERFORMANCE,
        AppleuxRuntimeConfig.KEY_LOW_POWER_MODE,
        AppleuxRuntimeConfig.KEY_REDUCE_MOTION,
        AppleuxRuntimeConfig.KEY_DARK_MODE
    )

    fun getBoolean(key: String, default: Boolean): Boolean = when (key) {
        in sharedFlagKeys -> AppleuxRuntimeConfig.readBoolean(app.contentResolver, key, default)
        AppleuxPreferences.KEY_AUTO_BRIGHTNESS -> AppleuxRuntimeConfig.isAutoBrightness(app)
        else -> prefs.getBoolean(key, default)
    }

    fun setBoolean(key: String, value: Boolean): Boolean = try {
        when (key) {
            in sharedFlagKeys ->
                AppleuxRuntimeConfig.writeBoolean(app.contentResolver, key, value)
            AppleuxPreferences.KEY_AUTO_BRIGHTNESS ->
                AppleuxRuntimeConfig.setAutoBrightness(app, value)
            else -> {
                prefs.setBoolean(key, value)
                true
            }
        }
    } catch (e: Exception) {
        false
    }

    fun getInt(key: String, default: Int): Int = when (key) {
        AppleuxPreferences.KEY_BRIGHTNESS -> AppleuxRuntimeConfig.readBrightness(app)
        AppleuxRuntimeConfig.KEY_GLASS_INTENSITY ->
            AppleuxRuntimeConfig.readInt(app.contentResolver, key, default)
        else -> prefs.getInt(key, default)
    }

    fun setInt(key: String, value: Int): Boolean = try {
        when (key) {
            AppleuxPreferences.KEY_BRIGHTNESS -> AppleuxRuntimeConfig.writeBrightness(app, value)
            AppleuxRuntimeConfig.KEY_GLASS_INTENSITY ->
                AppleuxRuntimeConfig.writeInt(app.contentResolver, key, value)
            else -> {
                prefs.setInt(key, value)
                true
            }
        }
    } catch (e: Exception) {
        false
    }
}