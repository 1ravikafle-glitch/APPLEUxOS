package com.appleux.systemui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AppleuxBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Cold start the SystemUI service (also started by the launcher).
                val svc = Intent(context, AppleuxSystemUIService::class.java)
                runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(svc)
                    } else {
                        @Suppress("DEPRECATION")
                        context.startService(svc)
                    }
                }.onFailure { Log.w(TAG, "cannot start service: ${it.message}") }
            }
            Intent.ACTION_SCREEN_ON -> handleScreenOn(context)
            Intent.ACTION_SCREEN_OFF -> handleScreenOff(context)
            Intent.ACTION_USER_PRESENT -> handleUserPresent(context)
            else -> Unit
        }
    }

    private fun handleScreenOn(context: Context) {
        sendToggle(context, AppleuxSystemUIService.ACTION_TOGGLE_NOTIFICATIONS)
    }

    private fun handleScreenOff(context: Context) {
        sendToggle(context, AppleuxSystemUIService.ACTION_TOGGLE_NOTIFICATIONS)
    }

    private fun handleUserPresent(context: Context) {
        sendToggle(context, AppleuxSystemUIService.ACTION_TOGGLE_CONTROL_CENTER)
    }

    private fun sendToggle(context: Context, action: String) {
        val svc = Intent(context, AppleuxSystemUIService::class.java).apply {
            this.action = action
        }
        runCatching { context.startService(svc) }
            .onFailure { Log.w(TAG, "cannot start ${svc.action}: ${it.message}") }
    }

    companion object {
        private const val TAG = "AppleuxSystemUI"
    }
}