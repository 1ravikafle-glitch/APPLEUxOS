package com.appleux.design.runtime

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Cross-process configuration shared by AppleUX apps.
 *
 * Launcher and Settings run in separate processes and do not share app-private
 * SharedPreferences.
 *
 * The shared store is a ContentProvider owned by the Settings app
 * (`com.appleux.settings.config`), guarded by a signature-level permission.
 * Settings.System is NOT usable here: Android 16 rejects app writes to that
 * table with `IllegalArgumentException: You cannot keep your settings in the
 * secure settings`, and a crash in the Settings UI is not an acceptable way to
 * report a failed write. Settings.System is therefore read-only, and is used
 * only as a fallback for values seeded externally (adb, adb shell settings).
 */
object AppleuxRuntimeConfig {

    const val KEY_ADAPTIVE_PERFORMANCE = "appleux_adaptive_performance"
    const val KEY_LOW_POWER_MODE = "appleux_low_power_mode"
    const val KEY_REDUCE_MOTION = "appleux_reduce_motion"
    const val KEY_DARK_MODE = "appleux_dark_mode"
    const val KEY_GLASS_INTENSITY = "appleux_glass_intensity"

    /** Default glass intensity (0-100). See AppleuxGlass for the scale. */
    const val DEFAULT_GLASS_INTENSITY = 70

    const val DEFAULT_BRIGHTNESS = 128

    /** Must match `AppleuxConfigProvider.AUTHORITY` in the Settings app. */
    private const val CONFIG_AUTHORITY = "com.appleux.settings.config"
    private val CONFIG_URI: Uri = Uri.parse("content://$CONFIG_AUTHORITY")

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cachedAdaptivePerformance: Boolean = true

    @Volatile
    private var cachedLowPowerMode: Boolean = false

    @Volatile
    private var cachedReduceMotion: Boolean = false

    @Volatile
    private var cachedDarkMode: Boolean = false

    @Volatile
    private var cachedGlassIntensity: Int = DEFAULT_GLASS_INTENSITY

    @Volatile
    private var observerRegistered = false

    /**
     * Application context retained by [observe]. Needed because the
     * ContentObserver fires long after the caller's Context is gone.
     */
    @Volatile
    private var appContext: Context? = null

    private val observer = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            reload()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            reload()
        }
    }

    val isAdaptivePerformanceEnabled: Boolean get() = cachedAdaptivePerformance
    val isLowPowerModeEnabled: Boolean get() = cachedLowPowerMode
    val isReduceMotionEnabled: Boolean get() = cachedReduceMotion
    val isDarkModeEnabled: Boolean get() = cachedDarkMode
    val glassIntensity: Int get() = cachedGlassIntensity.coerceIn(0, 100)

    /**
     * Reads [key] from the shared provider, falling back to a read-only
     * Settings.System lookup, then to [default].
     *
     * The per-key URI is used deliberately: querying the authority root returns
     * every key, and taking the first row would alias all four flags to
     * whichever was written first.
     */
    fun readBoolean(resolver: ContentResolver, key: String, default: Boolean): Boolean {
        queryProvider(resolver, key)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(1) != 0
            }
        }
        return readSystemBoolean(resolver, key, default)
    }

    /** Writes [key] to the shared provider. Returns false if the write failed. */
    fun writeBoolean(resolver: ContentResolver, key: String, value: Boolean): Boolean {
        val written = try {
            val values = ContentValues().apply { put(key, if (value) 1 else 0) }
            resolver.update(keyUri(key), values, null, null) > 0
        } catch (e: Exception) {
            false
        }
        if (written) reload()
        return written
    }

    /** Reads an int flag (e.g. glass intensity 0-100) from the shared provider. */
    fun readInt(resolver: ContentResolver, key: String, default: Int): Int {
        queryProvider(resolver, key)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(1)
            }
        }
        return default
    }

    /** Writes an int flag to the shared provider. Returns false on failure. */
    fun writeInt(resolver: ContentResolver, key: String, value: Int): Boolean {
        val written = try {
            val values = ContentValues().apply { put(key, value) }
            resolver.update(keyUri(key), values, null, null) > 0
        } catch (e: Exception) {
            false
        }
        if (written) reload()
        return written
    }

    private fun keyUri(key: String): Uri = Uri.withAppendedPath(CONFIG_URI, key)

    private fun queryProvider(resolver: ContentResolver, key: String): Cursor? = try {
        resolver.query(keyUri(key), arrayOf("key", "value"), null, null, null)
    } catch (e: Exception) {
        null
    }

    private fun readSystemBoolean(
        resolver: ContentResolver,
        key: String,
        default: Boolean
    ): Boolean = try {
        Settings.System.getInt(resolver, key, if (default) 1 else 0) != 0
    } catch (e: Exception) {
        default
    }

    fun readBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                DEFAULT_BRIGHTNESS
            ).coerceIn(1, 255)
        } catch (e: Exception) {
            DEFAULT_BRIGHTNESS
        }
    }

    fun writeBrightness(context: Context, value: Int): Boolean {
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value.coerceIn(1, 255)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setAutoBrightness(context: Context, enabled: Boolean): Boolean {
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isAutoBrightness(context: Context): Boolean {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            false
        }
    }

    /** Re-reads every cached value. Safe to call from onResume. */
    fun reload(context: Context? = null) {
        val resolver = (context ?: appContext)?.contentResolver ?: return
        cachedAdaptivePerformance = readBoolean(resolver, KEY_ADAPTIVE_PERFORMANCE, true)
        cachedLowPowerMode = readBoolean(resolver, KEY_LOW_POWER_MODE, false)
        cachedReduceMotion = readBoolean(resolver, KEY_REDUCE_MOTION, false)
        cachedDarkMode = readBoolean(resolver, KEY_DARK_MODE, false)
        cachedGlassIntensity =
            readInt(resolver, KEY_GLASS_INTENSITY, DEFAULT_GLASS_INTENSITY).coerceIn(0, 100)
    }

    /** Registers the ContentObserver once per process. */
    fun observe(context: Context) {
        val app = context.applicationContext
        if (observerRegistered) {
            reload(app)
            return
        }
        appContext = app
        reload(app)
        try {
            app.contentResolver.registerContentObserver(CONFIG_URI, true, observer)
            app.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer
            )
            observerRegistered = true
        } catch (e: Exception) {
            observerRegistered = false
        }
    }

    fun unobserve(context: Context) {
        if (!observerRegistered) return
        try {
            val resolver = context.applicationContext.contentResolver
            resolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
        }
        observerRegistered = false
    }
}