package com.appleux.launcher.apps

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.components.AppleuxNavigationBar
import com.appleux.design.components.AppleuxToggle
import com.appleux.design.tokens.ColorTokens
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * iOS Phone: Favorites / Recents / Contacts / Keypad tabs (AppleuxNavigationBar),
 * T9 keypad with letters, green call button, in-app auto call recording with
 * an on/off toggle and NO announcement (our own capture path — the platform
 * plays no announcement; GMS dialer warnings do not apply to direct capture).
 */
class PhoneActivity : AppCompatActivity() {

    private lateinit var body: LinearLayout
    private lateinit var number: TextView
    private val dial = StringBuilder()
    private val prefs by lazy { getSharedPreferences("appleux_phone", MODE_PRIVATE) }
    private var recorder: MediaRecorder? = null
    private var recFile: File? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(body)
        AppleuxAppChrome.apply(this, root)
        val tabs = AppleuxNavigationBar(this)
        tabs.addItem(AppleuxNavigationBar.NavigationItem(1, "Recents", android.R.drawable.ic_menu_recent_history))
        tabs.addItem(AppleuxNavigationBar.NavigationItem(2, "Contacts", android.R.drawable.ic_menu_sort_by_size))
        tabs.addItem(AppleuxNavigationBar.NavigationItem(3, "Keypad", android.R.drawable.ic_menu_edit))
        tabs.addItem(AppleuxNavigationBar.NavigationItem(4, "Record", android.R.drawable.ic_btn_speak_now))
        tabs.setOnItemSelectedListener {
            when (it.id) {
                1 -> showRecents()
                2 -> showContacts()
                3 -> showKeypad()
                else -> showRecordings()
            }
        }
        root.addView(tabs)
        setContentView(root)
        watchCalls()
        showKeypad()
    }

    // ---- Keypad ----

    private fun showKeypad() {
        body.removeAllViews()
        number = TextView(this).apply {
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(ColorTokens.label)
            setPadding(0, 32, 0, 16)
            minHeight = 120
        }
        body.addView(number)
        val keys = listOf(
            "1" to "", "2" to "ABC", "3" to "DEF",
            "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
            "*" to "", "0" to "+", "#" to "")
        val cellW = resources.displayMetrics.widthPixels / 3
        val grid = GridLayout(this).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        for ((digit, sub) in keys) {
            val b = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                // Press-down feedback instant (Apple: highlight on down, commit on up).
                text = if (sub.isEmpty()) digit else "$digit\n$sub"
                textSize = 26f
                isAllCaps = false
                setTextColor(ColorTokens.label)
                setOnClickListener { onDigit(digit) }
                setOnTouchListener { v, e ->
                    if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) v.alpha = 0.4f
                    else if (e.actionMasked == android.view.MotionEvent.ACTION_UP ||
                        e.actionMasked == android.view.MotionEvent.ACTION_CANCEL) v.alpha = 1f
                    false
                }
            }
            grid.addView(b, GridLayout.LayoutParams().apply {
                width = cellW
                height = LinearLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
            })
        }
        body.addView(grid)
        body.addView(AppleuxAppChrome.callButton(this) { placeCall() })
        val back = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "⌫"
            setOnClickListener {
                if (dial.isNotEmpty()) {
                    dial.deleteCharAt(dial.length - 1)
                    number.text = dial.toString()
                }
            }
        }
        body.addView(back)
    }

    private fun onDigit(d: String) {
        dial.append(d)
        number.text = dial.toString()
    }

    private fun placeCall() {
        if (dial.isEmpty()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 11)
            return
        }
        runCatching {
            startActivity(android.content.Intent(
                android.content.Intent.ACTION_CALL,
                android.net.Uri.parse("tel:${dial}")
            ))
        }.onFailure {
            Toast.makeText(this, "Cannot place call", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Recents (CallLog) ----

    private fun showRecents() {
        body.removeAllViews()
        title("Recents")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), 12)
            return
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        runCatching {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < 50) {
                    val num = c.getString(0).orEmpty()
                    val name = c.getString(1)?.takeIf { it.isNotEmpty() } ?: num
                    val whenStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(c.getLong(2)))
                    val dir = when (c.getInt(4)) {
                        CallLog.Calls.INCOMING_TYPE -> "↓ "
                        CallLog.Calls.OUTGOING_TYPE -> "↑ "
                        else -> "✕ "
                    }
                    col.addView(AppleuxListItem(this).apply {
                        setTitle("$dir$name")
                        setSubtitle("$num · $whenStr · ${c.getLong(3)}s")
                        setDividerVisible(true)
                        isClickable = true
                        setOnClickListener {
                            dial.clear()
                            dial.append(num)
                            showKeypad()
                            number.text = dial.toString()
                        }
                    })
                }
            }
        }
        val scroll = ScrollView(this)
        scroll.addView(col)
        body.addView(scroll)
    }

    // ---- Contacts tab (compact list, full app is ContactsActivity) ----

    private fun showContacts() {
        body.removeAllViews()
        title("Contacts")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 13)
            return
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        runCatching {
            contentResolver.query(
                android.provider.ContactsContract.Contacts.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.Contacts._ID,
                    android.provider.ContactsContract.Contacts.DISPLAY_NAME,
                    android.provider.ContactsContract.Contacts.STARRED),
                null, null,
                "${android.provider.ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < 100) {
                    val name = c.getString(1).orEmpty()
                    val star = c.getInt(2) == 1
                    col.addView(AppleuxListItem(this).apply {
                        setTitle((if (star) "★ " else "") + name)
                        setDividerVisible(true)
                    })
                }
            }
        }
        val scroll = ScrollView(this)
        scroll.addView(col)
        body.addView(scroll)
        body.addView(Button(this).apply {
            text = "Open Contacts"
            setOnClickListener {
                startActivity(android.content.Intent(this@PhoneActivity, ContactsActivity::class.java))
            }
        })
    }

    // ---- Recordings + auto-record toggle ----

    private fun isDefaultDialer(): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(android.app.role.RoleManager::class.java) ?: return false
            rm.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)
        } else false
    }.getOrDefault(false)

    private fun requestDialerRole() {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(android.app.role.RoleManager::class.java) ?: return
                startActivityForResult(
                    rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER), 15)
            }
        }
    }

    private fun autoRecord(): Boolean = prefs.getBoolean("auto_record", false)

    private fun showRecordings() {
        body.removeAllViews()
        title("Call Recording")
        val card = AppleuxCard(this)
        val row = AppleuxListItem(this).apply {
            setTitle("Auto-record calls")
            setSubtitle("On-device capture, no announcement")
            setDividerVisible(false)
        }
        val toggle = AppleuxToggle(this).apply {
            setChecked(autoRecord(), animate = false)
            setOnCheckedChangeListener { prefs.edit().putBoolean("auto_record", it).apply() }
        }
        row.setAccessoryView(toggle)
        card.addView(row)
        body.addView(card)
        body.addView(Button(this).apply {
            text = if (isDefaultDialer()) "Default phone app ✓" else "Set as default phone app"
            setOnClickListener { requestDialerRole() }
        })
        val dir = File(filesDir, "recordings")
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (files.isEmpty()) {
            col.addView(AppleuxListItem(this).apply { setTitle("No recordings yet") })
        }
        for (f in files) {
            col.addView(AppleuxListItem(this).apply {
                setTitle(f.nameWithoutExtension)
                setSubtitle("${f.length() / 1024} KB · ${DateFormat.getDateTimeInstance().format(Date(f.lastModified()))}")
                setDividerVisible(true)
                isClickable = true
                setOnClickListener { playFile(f) }
            })
        }
        val scroll = ScrollView(this)
        scroll.addView(col)
        body.addView(scroll)
    }

    private var player: android.media.MediaPlayer? = null

    private fun playFile(f: File) {
        runCatching {
            player?.release()
            player = android.media.MediaPlayer().apply {
                setDataSource(f.absolutePath)
                prepare()
                start()
            }
        }.onFailure {
            Toast.makeText(this, "Cannot play", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Auto capture on call state ----

    private fun watchCalls() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 14)
            return
        }
        val tm = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            tm.registerTelephonyCallback(Executors.newSingleThreadExecutor(),
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onCallState(state)
                })
        } else {
            @Suppress("DEPRECATION")
            tm.listen(object : PhoneStateListener() {
                @Deprecated("deprecated")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun onCallState(state: Int) {
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            if (autoRecord()) startCapture()
        } else {
            stopCapture()
        }
    }

    private fun startCapture() {
        if (recorder != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            val dir = File(filesDir, "recordings")
            dir.mkdirs()
            recFile = File(dir, "call-${System.currentTimeMillis()}.m4a")
            recorder = MediaRecorder().apply {
                // VOICE_CALL needs CAPTURE_AUDIO_OUTPUT (priv-app allowlisted);
                // without the grant this throws and the mic path below applies.
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                } catch (e: Exception) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recFile!!.absolutePath)
                prepare()
                start()
            }
        }.onFailure {
            recorder = null
            recFile?.delete()
            recFile = null
        }
    }

    private fun stopCapture() {
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        if (recFile?.length() == 0L) {
            recFile?.delete()
            recFile = null
        }
    }

    private fun title(text: String) {
        body.addView(TextView(this).apply {
            setText(text)
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ColorTokens.label)
            setPadding(32, 24, 32, 8)
        })
    }

    override fun onDestroy() {
        stopCapture()
        player?.release()
        super.onDestroy()
    }
}
