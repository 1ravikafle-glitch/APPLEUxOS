package com.appleux.launcher.apps

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.components.AppleuxToggle
import java.util.TimeZone

/** iOS-lookalike Clock shell: world clock + countdown timer; island slot note. */
class ClockActivity : AppCompatActivity() {
    private val h = Handler(Looper.getMainLooper())
    private var left = 0
    private val tick = object : Runnable {
        override fun run() { if (left > 0) { left--; status("Ends in $left s"); h.postDelayed(this, 1000) } }
    }
    private lateinit var row: AppleuxListItem
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val card = AppleuxCard(this)
        val zones = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (z in listOf("America/New_York", "Europe/London", "Asia/Tokyo", "Australia/Sydney")) {
            zones.addView(AppleuxListItem(this).apply {
                setTitle(z.substringAfter("/").replace("_", " "))
                setSubtitle(TimeZone.getTimeZone(z).displayName)
            })
        }
        card.addView(zones)
        row = AppleuxListItem(this).apply {
            setTitle("Timer"); setSubtitle("Island slot: timer pushes live countdown")
        }
        val tCard = AppleuxCard(this).apply { addView(row) }
        val tgl = AppleuxToggle(this).apply {
            setChecked(false, animate = false)
            setOnCheckedChangeListener { status(if (it) "Island slot active" else "Idle") }
        }
        val secs = EditText(this).apply { hint = "Seconds"; isSingleLine = true }
        val go = Button(this).apply {
            text = "Start"
            setOnClickListener {
                left = secs.text.toString().toIntOrNull() ?: 0
                h.removeCallbacks(tick); h.post(tick)
            }
        }
        col.addView(card); col.addView(tCard); col.addView(tgl); col.addView(secs); col.addView(go)
        setContentView(ScrollView(this).apply { addView(col) })
    }
    private fun status(m: String) {
        row.setSubtitle(m)
        // The live-timer slot used to be pushed to the island by an explicit
        // intent at com.appleux.systemui.AppleuxSystemUIService, which was
        // deleted on 2026-10-17 (deliverables/m23).
        //
        // That push is gone rather than repointed, and the reason matters: the
        // receiver for it lived in the deleted app, so there is nothing to send
        // to. Re-creating it inside SystemUI means adding a broadcast receiver
        // and a lifecycle for a value the Control Center can read directly when
        // it opens. Until the Control Center exists inside SystemUI there is
        // nothing to display it in, so the honest state is: the subtitle
        // updates, nothing consumes it, and that is recorded rather than
        // papered over with a dead intent.
        //
        // TODO(m23): republish via AppleuxNotificationBus-style in-process bus
        // once SystemUI hosts the Control Center.
        android.util.Log.v("AppleuxClock", "timer slot: $m")
    }
    override fun onDestroy() { h.removeCallbacks(tick); super.onDestroy() }
}
