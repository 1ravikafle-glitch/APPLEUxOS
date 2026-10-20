package com.appleux.settings.config

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Cross-process store for the AppleUX flags.
 *
 * Android 16 refuses app writes to the Settings.System table
 * ("You cannot keep your settings in the secure settings"), so Settings.System
 * cannot be used as the shared store for launcher<->settings values. A
 * ContentProvider is the supported mechanism.
 *
 * The provider is guarded by a signature-level permission. Both APKs are signed
 * with the same key, so the launcher may read it while nothing else can.
 */
class AppleuxConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun prefs(): SharedPreferences =
        requireNotNull(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun getType(uri: Uri): String = TYPE

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val keys = if (MATCHER.match(uri) == KEY_ITEM) {
            listOf(uri.lastPathSegment ?: "")
        } else {
            // The root authority URI has no path segment, so it never matches a
            // registered pattern. It means "every key".
            ALL_KEYS
        }
        val cursor = MatrixCursor(COLUMNS)
        val stored = prefs()
        for (key in keys) {
            if (!stored.contains(key)) continue
            cursor.addRow(arrayOf(key, stored.getInt(key, 0)))
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val key = uri.lastPathSegment ?: return null
        val value = values?.getAsInteger(key) ?: return null
        prefs().edit().putInt(key, value).apply()
        notifyChanged(key)
        return uri
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val key = uri.lastPathSegment ?: return 0
        val value = values?.getAsInteger(key) ?: return 0
        prefs().edit().putInt(key, value).apply()
        notifyChanged(key)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val key = uri.lastPathSegment ?: return 0
        val removed = prefs().edit().remove(key).commit()
        if (removed) notifyChanged(key)
        return if (removed) 1 else 0
    }

    private fun notifyChanged(key: String) {
        context?.contentResolver?.notifyChange(Uri.withAppendedPath(AUTHORITY_URI, key), null)
    }

    companion object {
        const val PERMISSION = "com.appleux.settings.permission.READ_APPLEUX_CONFIG"
        const val AUTHORITY = "com.appleux.settings.config"
        const val TYPE = "vnd.android.cursor.item/vnd.com.appleux.config"

        val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")

        private const val PREFS = "appleux_config"
        private const val KEY_ITEM = 1
        private const val COL_KEY = "key"
        private const val COL_VALUE = "value"
        private val COLUMNS = arrayOf(COL_KEY, COL_VALUE)

        const val KEY_ADAPTIVE_PERFORMANCE = "appleux_adaptive_performance"
        const val KEY_LOW_POWER_MODE = "appleux_low_power_mode"
        const val KEY_REDUCE_MOTION = "appleux_reduce_motion"
        const val KEY_DARK_MODE = "appleux_dark_mode"
        const val KEY_GLASS_INTENSITY = "appleux_glass_intensity"

        val ALL_KEYS = listOf(
            KEY_ADAPTIVE_PERFORMANCE,
            KEY_LOW_POWER_MODE,
            KEY_REDUCE_MOTION,
            KEY_DARK_MODE,
            KEY_GLASS_INTENSITY
        )

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            ALL_KEYS.forEach { addURI(AUTHORITY, it, KEY_ITEM) }
        }
    }
}