package org.ioslike.ui

import android.app.Application
import android.content.Intent
import org.ioslike.ui.shell.ShellService

/** Persistent system app: starts the shell as soon as the system process list is up. */
class ShellApp : Application() {
    override fun onCreate() {
        super.onCreate()
        org.ioslike.ui.theme.GlassConfig.autoDetect(this)
        startService(Intent(this, ShellService::class.java))
    }
}
