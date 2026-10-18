package com.appleux.apps

    // android.R.drawable -> vendored iOS vector (2026-10-17):
    //   com.appleux.design.R.drawable.sf_add                -> sf_add           iOS plus
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.components.AppleuxToast

/**
 * iOS in-call screen (Apple design rules: press-down feedback, large
 * contact name + live timer, symmetric 6-button grid, red End anchor).
 * Incoming: green Accept / red Decline. Buttons toggle live Call state.
 */
class AppleuxCallScreenActivity : AppCompatActivity() {

    private lateinit var nameView: TextView
    private lateinit var stateView: TextView
    private var call: Call? = null
    private val endReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
            if (i?.action == AppleuxInCallService.ACTION_CALL_ENDED) finish()
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        call = AppleuxInCallService.Calls.current
        val c = call
        if (c == null) {
            finish()
            return
        }
        // iOS 27 contact posters: the caller's poster gradient is the incoming
        // call background when one is configured. Falls back to the system
        // background when the contact has no poster or Contacts is unreadable.
        val posterBackground = ContactsPosterStore.backgroundFor(this, labelOf(c))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = posterBackground
                ?: android.graphics.drawable.GradientDrawable().apply {
                    setColor(ColorTokens.systemBackground)
                }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        }
        nameView = TextView(this).apply {
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (posterBackground != null) ColorTokens.white else ColorTokens.label)
            gravity = Gravity.CENTER
            text = labelOf(c)
        }
        stateView = TextView(this).apply {
            textSize = 15f
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
        }
        root.addView(nameView)
        val caption = TextView(this).apply {
            text = "mobile"
            textSize = 15f
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
        }
        root.addView(caption)
        root.addView(stateView)
        if (c.state == Call.STATE_RINGING) {
            root.addView(remindRow(c))
            root.addView(answerRow(c))
        } else {
            root.addView(padGrid(c))
            root.addView(endButton(c))
            renderActive(c, 0L)
        }
        AppleuxInCallService.Calls.onTick = { secs -> renderActive(c, secs) }
        c.registerCallback(object : Call.Callback() {
            override fun onStateChanged(changed: Call, state: Int) {
                runOnUiThread {
                    if (state == Call.STATE_DISCONNECTED) finish()
                    else if (state == Call.STATE_ACTIVE) recreate()
                }
            }
        })
        setContentView(root)
        registerReceiver(endReceiver,
            android.content.IntentFilter(AppleuxInCallService.ACTION_CALL_ENDED),
            RECEIVER_NOT_EXPORTED)
    }

    private fun labelOf(c: Call): String {
        val h = c.details?.handle?.schemeSpecificPart
        return h?.takeIf { it.isNotEmpty() } ?: "Unknown"
    }

    private fun fmt(secs: Long): String =
        "%02d:%02d".format(secs / 60, secs % 60)

    private fun renderActive(c: Call, secs: Long) {
        stateView.text = fmt(secs)
    }

    /** iOS incoming top row: Remind Me / Message text actions. */
    private fun remindRow(c: Call): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 0)
        }
        val remind = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Remind Me"; isAllCaps = false; textSize = 15f
            setTextColor(ColorTokens.systemBlue)
            setOnClickListener {
                AppleuxToast.show(this@AppleuxCallScreenActivity, "Reminder set for 1 hour")
            }
        }
        val msg = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Message"; isAllCaps = false; textSize = 15f
            setTextColor(ColorTokens.systemBlue)
            setOnClickListener {
                runCatching {
                    startActivity(android.content.Intent(
                        android.content.Intent.ACTION_SENDTO,
                        android.net.Uri.parse("smsto:${labelOf(c)}")))
                }
            }
        }
        val gap = TextView(this).apply { width = 120 }
        row.addView(remind); row.addView(gap); row.addView(msg)
        return row
    }

    /** Incoming: red decline left, green accept right, round like iOS. */
    private fun answerRow(c: Call): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 220, 0, 0)
        }
        val accept = roundButton("Accept", ColorTokens.systemGreen) {
            runCatching { c.answer(VideoProfile.STATE_AUDIO_ONLY) }
        }
        val decline = roundButton("Decline", ColorTokens.systemRed) {
            runCatching { c.reject(false, null) }
        }
        val spacer = TextView(this).apply { width = 120 }
        row.addView(decline)
        row.addView(spacer)
        row.addView(accept)
        stateView.text = "Incoming call"
        return row
    }

    /** Ongoing: symmetric 3x2 grid (mute / keypad / speaker / hold / add / record). */
    private fun padGrid(c: Call): GridLayout {
        val grid = GridLayout(this).apply { columnCount = 3 }
        val d = resources.displayMetrics.density
        grid.addView(circleToggle("system_mic", false, "mute") { on ->
            AppleuxInCallService.setMuted(on)
        })
        grid.addView(circleAction("system_grid", "keypad") { toggleDtmfPad() })
        grid.addView(circleToggle("system_volume", false, "speaker") { on ->
            AppleuxInCallService.setRoute(if (on) CallAudioState.ROUTE_SPEAKER
            else CallAudioState.ROUTE_EARPIECE)
        })
        grid.addView(circleToggle("system_pause", c.state == Call.STATE_HOLDING, "hold") { on ->
            runCatching { if (on) c.hold() else c.unhold() }
        })
        grid.addView(circleAction("system_add", "add call") {
            runCatching {
                startActivity(android.content.Intent(
                    android.content.Intent.ACTION_DIAL))
            }
        })
        grid.addView(circleRecord("record") { })
        grid.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = (120 * d).toInt()
        }
        return grid
    }

    private var dtmfPad: GridLayout? = null

    private fun toggleDtmfPad() {
        dtmfPad?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            dtmfPad = null
            return
        }
        val grid = GridLayout(this).apply { columnCount = 3 }
        for (k in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")) {
            grid.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = k
                textSize = 26f
                setOnClickListener {
                    runCatching { call?.playDtmfTone(k[0]) }
                    runCatching { call?.stopDtmfTone() }
                }
            })
        }
        dtmfPad = grid
        (findViewById<ViewGroup>(android.R.id.content)).addView(grid)
    }

    /**
     * iOS round button: translucent glass circle, white SF glyph, inverts
     * when active. Press-down dims instantly (Apple fluid response).
     * Sizing matches the passcode keypad (72dp) for consistency.
     */
    private fun roundGlassButton(
        icon: String?,
        drawRedDot: Boolean = false,
        initial: Boolean = false,
        content: String,
        onFlip: ((Boolean) -> Unit)? = null,
        onTap: (() -> Unit)? = null
    ): android.widget.ImageButton {
        val d = resources.displayMetrics.density
        var on = initial
        fun paint(v: android.widget.ImageButton) {
            v.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(if (on) ColorTokens.white else 0x3DFFFFFF)
            }
            v.setColorFilter(if (on) ColorTokens.black else ColorTokens.white)
        }
        return android.widget.ImageButton(this).apply {
            if (drawRedDot) {
                setImageDrawable(redDot())
            } else {
                val res = com.appleux.design.components.AppleuxIconRegistry
                    .getDrawableRes(icon ?: "system_grid")
                    ?: com.appleux.design.components.AppleuxIconRegistry
                        .getDrawableRes("system_add")
                    ?: com.appleux.design.R.drawable.sf_add
                setImageDrawable(androidx.core.content.ContextCompat.getDrawable(
                    this@AppleuxCallScreenActivity, res))
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt())
            val s = (72 * d).toInt()
            layoutParams = GridLayout.LayoutParams().apply {
                width = s; height = s
                setMargins((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            }
            isClickable = true
            contentDescription = content
            paint(this)
            setOnTouchListener { v, e ->
                if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) v.alpha = 0.6f
                else v.alpha = 1f
                false
            }
            setOnClickListener {
                if (onFlip != null) {
                    on = !on
                    paint(this)
                    onFlip(on)
                } else onTap?.invoke()
            }
        }
    }

    private fun redDot(): android.graphics.drawable.Drawable {
        val d = resources.displayMetrics.density
        val s = (28 * d).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawCircle(s / 2f, s / 2f, s / 2.4f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorTokens.systemRed
            })
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    private fun circleToggle(icon: String, initial: Boolean, content: String, onFlip: (Boolean) -> Unit) =
        roundGlassButton(icon, initial = initial, content = content, onFlip = onFlip)

    private fun circleAction(icon: String, content: String, fn: () -> Unit) =
        roundGlassButton(icon, content = content, onTap = fn)

    private fun circleRecord(content: String, fn: (Boolean) -> Unit): android.widget.ImageButton {
        var on = AppleuxCallRecorder.isRecording
        val btn = roundGlassButton(null, drawRedDot = true, initial = on, content = content, onFlip = null)
        btn.setOnClickListener {
            on = AppleuxCallRecorder.toggle(this)
            btn.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(if (on) ColorTokens.white else 0x3DFFFFFF)
            }
            fn(on)
        }
        return btn
    }

    private fun roundButton(label: String, color: Int, fn: () -> Unit): Button {
        val d = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(ColorTokens.white)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
            }
            layoutParams = LinearLayout.LayoutParams((150 * d).toInt(), (150 * d).toInt())
            setOnClickListener { fn() }
        }
    }

    private fun endButton(c: Call): Button {
        val d = resources.displayMetrics.density
        return Button(this).apply {
            text = "End"
            isAllCaps = false
            setTextColor(ColorTokens.white)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(ColorTokens.systemRed)
            }
            layoutParams = LinearLayout.LayoutParams((150 * d).toInt(), (150 * d).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (48 * d).toInt()
            }
            setOnClickListener { runCatching { c.disconnect() }; finish() }
        }
    }

    override fun onDestroy() {
        AppleuxInCallService.Calls.onTick = null
        runCatching { unregisterReceiver(endReceiver) }
        super.onDestroy()
    }

}
