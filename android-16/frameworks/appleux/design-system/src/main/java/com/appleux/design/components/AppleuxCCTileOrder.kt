package com.appleux.design.components

import android.content.Context

/**
 * F12 order-only edit state: persisted CC tile order (add/remove comes second).
 * The Control Center reads [orderedIds] on every open; edit UI swaps + saves.
 */
object AppleuxCCTileOrder {
    private const val PREFS = "appleux_cc_tiles"
    private const val KEY = "order"
    val DEFAULT = listOf(
        "wifi", "bluetooth", "airplane", "flashlight",
        "dnd", "brightness", "rotation", "mute", "airdrop"
    )
    fun orderedIds(context: Context): List<String> {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, null)?.toList() ?: return DEFAULT
        // Drop unknown ids, append any new defaults missing from prefs.
        return (saved.filter { it in DEFAULT } + (DEFAULT - saved.toSet()))
    }
    fun saveOrder(context: Context, ids: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, ids.toSet()).apply()
    }
    fun move(ids: MutableList<String>, from: Int, to: Int) {
        if (from !in ids.indices || to !in 0..ids.size) return
        val item = ids.removeAt(from)
        ids.add(to.coerceIn(0, ids.size), item)
    }
}
