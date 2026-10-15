package com.appleux.launcher

import android.app.Application
import com.appleux.design.runtime.AppleuxRuntimeConfig

class LauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Settings.System is the shared store; observe it once per process so a
        // change made in the Settings app is picked up without a restart.
        AppleuxRuntimeConfig.observe(this)
    }
}
