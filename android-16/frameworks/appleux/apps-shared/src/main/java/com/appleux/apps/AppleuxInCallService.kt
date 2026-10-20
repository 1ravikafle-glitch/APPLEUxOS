package com.appleux.apps

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService

/**
 * System takeover: with ROLE_DIALER held, Telecom binds here (not stock
 * InCallUI) for every call. Forwards to [AppleuxCallScreenActivity].
 * Pattern follows AOSP InCallServiceImpl (Apache 2.0, in our tree).
 */
class AppleuxInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Calls.set(call)
        runCatching {
            startActivity(
                Intent(this, AppleuxCallScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Calls.clear(call)
        runCatching {
            val self = this@AppleuxInCallService
            sendBroadcast(Intent(ACTION_CALL_ENDED).apply {
                setPackage(self.packageName)
            })
        }
    }

    companion object {
        const val ACTION_CALL_ENDED = "com.appleux.launcher.CALL_ENDED"

        @Volatile var service: AppleuxInCallService? = null
            private set

        /** Audio control must run on the bound service, not the activity. */
        fun setMuted(muted: Boolean) {
            runCatching { service?.setMuted(muted) }
        }

        fun setRoute(route: Int) {
            runCatching { service?.setAudioRoute(route) }
        }
    }

    override fun onBind(intent: android.content.Intent?): android.os.IBinder? {
        service = this
        return super.onBind(intent)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        service = null
        return super.onUnbind(intent)
    }

    /** Latest live call, shared with the screen (single-call device). */
    object Calls {
        @Volatile var current: Call? = null
            private set
        private val handler = Handler(Looper.getMainLooper())
        private var timerTick: Runnable? = null
        var onTick: ((Long) -> Unit)? = null
        private var startedAt = 0L

        fun set(call: Call) {
            current = call
            startedAt = System.currentTimeMillis()
            call.registerCallback(object : Call.Callback() {
                override fun onStateChanged(c: Call, state: Int) {
                    if (state == Call.STATE_ACTIVE) startTimer()
                    if (state == Call.STATE_DISCONNECTED) stopTimer()
                }
            })
            if (call.state == Call.STATE_ACTIVE) startTimer()
        }

        fun clear(call: Call) {
            if (current == call) {
                current = null
                stopTimer()
            }
        }

        private fun startTimer() {
            stopTimer()
            startedAt = System.currentTimeMillis()
            val tick = object : Runnable {
                override fun run() {
                    onTick?.invoke((System.currentTimeMillis() - startedAt) / 1000)
                    handler.postDelayed(this, 1000)
                }
            }
            timerTick = tick
            handler.post(tick)
        }

        private fun stopTimer() {
            timerTick?.let { handler.removeCallbacks(it) }
            timerTick = null
        }
    }



}
