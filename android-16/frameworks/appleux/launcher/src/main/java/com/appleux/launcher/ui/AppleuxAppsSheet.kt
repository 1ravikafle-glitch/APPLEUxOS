package com.appleux.launcher.ui

import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView
import com.appleux.design.components.AppleuxDialogs
import com.appleux.design.components.AppleuxListItem

/** "AppleUX Apps" sheet: the 9 launcher-owned iOS-lookalike shells. */
object AppleuxAppsSheet {
    data class Entry(val label: String, val sub: String, val pkg: String)

    fun entries(): List<Entry> = listOf(
        Entry("Phone", "Dialer", "com.appleux.phone"),
        Entry("Messages", "Share", "com.appleux.messages"),
        Entry("Contacts", "People", "com.appleux.contacts"),
        Entry("Photos", "MediaStore", "com.appleux.photos"),
        Entry("Camera", "Capture", "com.appleux.camera"),
        Entry("Clock", "World + timer", "com.appleux.clock"),
        Entry("Calendar", "Month", "com.appleux.calendar"),
        Entry("Files", "Documents", "com.appleux.files"),
        Entry("Browser", "Web", "com.appleux.browser")
    )

    fun launch(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) context.startActivity(intent)
    }

    fun show(context: Context) {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { addView(col) }
        for (e in entries()) {
            col.addView(AppleuxListItem(context).apply {
                setTitle(e.label); setSubtitle(e.sub)
                setOnClickListener { launch(context, e.pkg) }
            })
        }
        AppleuxDialogs.custom(context, content = scroll, title = "AppleUX Apps")
    }
}
