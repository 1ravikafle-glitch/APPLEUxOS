package com.appleux.launcher.ui

import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView
import com.appleux.design.components.AppleuxDialogs
import com.appleux.design.components.AppleuxListItem

/** "AppleUX Apps" sheet: the 9 launcher-owned iOS-lookalike shells. */
object AppleuxAppsSheet {
    data class Entry(val label: String, val sub: String, val cls: Class<*>)

    fun entries(): List<Entry> = listOf(
        Entry("Phone", "Dialer", com.appleux.launcher.apps.PhoneActivity::class.java),
        Entry("Messages", "Share", com.appleux.launcher.apps.MessagesActivity::class.java),
        Entry("Contacts", "People", com.appleux.launcher.apps.ContactsActivity::class.java),
        Entry("Photos", "MediaStore", com.appleux.launcher.apps.PhotosActivity::class.java),
        Entry("Camera", "Capture", com.appleux.launcher.apps.CameraActivity::class.java),
        Entry("Clock", "World + timer", com.appleux.launcher.apps.ClockActivity::class.java),
        Entry("Calendar", "Month", com.appleux.launcher.apps.CalendarActivity::class.java),
        Entry("Files", "Documents", com.appleux.launcher.apps.FilesActivity::class.java),
        Entry("Browser", "Web", com.appleux.launcher.apps.BrowserActivity::class.java)
    )

    fun show(context: Context) {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { addView(col) }
        for (e in entries()) {
            col.addView(AppleuxListItem(context).apply {
                setTitle(e.label); setSubtitle(e.sub)
                setOnClickListener {
                    context.startActivity(Intent(context, e.cls))
                }
            })
        }
        AppleuxDialogs.custom(context, content = scroll, title = "AppleUX Apps")
    }
}
