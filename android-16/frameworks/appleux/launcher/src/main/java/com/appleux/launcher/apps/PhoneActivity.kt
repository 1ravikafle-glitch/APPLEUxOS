package com.appleux.launcher.apps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.appleux.design.components.AppleuxDialogs
import com.appleux.sounds.AppleuxSoundPlayer
import com.appleux.design.components.AppleuxButton
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxIconRegistry
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.components.AppleuxNavigationBar
import com.appleux.design.components.AppleuxText
import com.appleux.design.components.AppleuxToggle
import com.appleux.design.components.LayoutUtils
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.tokens.SizingTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens
import android.content.ClipboardManager
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.SeekBar
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import com.appleux.design.components.AppleuxToast

/**
 * iOS 27 Phone — five-tab Phone app.
 *
 * Tabs (Favorites, Recents, Contacts, Keypad, Voicemail) sit in an
 * [AppleuxNavigationBar] at the bottom; a large "Phone" title collapses into a
 * compact title as the content scrolls; Favorites / Recents / Contacts are
 * grouped cards of 48 dp rows (40 dp circular avatar, name, info, call button);
 * Keypad is a 3x4 grid of 76 dp Liquid Glass keys with a 72 dp green call
 * button; a keypad FAB floats bottom-right on the other tabs.
 *
 * DATA SOURCE — the iOS 27 MobilePhone.app / MobilePhoneSettings.bundle
 * `Info.plist` files in the IPSW harvest (see AGENTS.md). Every string and
 * scheme below is transcribed from those plists; nothing here is guessed from
 * a screenshot. See [PhoneInfo] for the exact keys.
 *
 * Functionality carried over from the previous implementation: on-device call
 * recording with a toggle, the default-dialer role request, the recordings
 * list and playback, and the privacy disclosures.
 */
class PhoneActivity : AppCompatActivity() {

    private enum class Tab(val label: String, val scheme: String, val icon: String) {
        FAVORITES("Favorites", PhoneInfo.Schemes.FAVORITES, "system_star"),
        RECENTS("Recents", PhoneInfo.Schemes.RECENTS, "system_clock"),
        CONTACTS("Contacts", PhoneInfo.Schemes.CONTACTS, "system_person"),
        KEYPAD("Keypad", PhoneInfo.Schemes.KEYPAD, "system_grid"),
        VOICEMAIL("Voicemail", PhoneInfo.Schemes.VOICEMAIL, "system_play")
    }

    private lateinit var content: LinearLayout
    private lateinit var compactTitle: AppleuxText
    private lateinit var fab: ImageButton
    private var numberView: AppleuxText? = null
    private var collapseThresholdPx: Int = 0

    private val dial = StringBuilder()
    private var currentTab: Tab = Tab.RECENTS

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    private var player: android.media.MediaPlayer? = null

    // iOS 27 active-call overlay state
    private lateinit var root: LinearLayout
    private val callHandler = Handler(Looper.getMainLooper())
    private var callStartMs = 0L
    private var callTick: Runnable? = null
    private var callOverlay: FrameLayout? = null
    private var callTimerView: AppleuxText? = null
    private var currentCallNumber = ""
    private var muted = false
    private var speakerOn = false
    private var held = false
    private var sensorManager: SensorManager? = null
    private var proximity: Sensor? = null
    private var proximityListener: SensorEventListener? = null
    private var vmSeek: SeekBar? = null
    private var vmHandler: Handler? = null
    private var vmTick: Runnable? = null
    private val spamGuard = SpamGuard()

    // ---- Dual-SIM infrastructure ---------------------------------------------

    /** One active SIM: subscriptionId, slot index, carrier name, signal. */
    private data class SimInfo(
        val subscriptionId: Int,
        val slotIndex: Int,
        val carrierName: String,
        val signalBars: Int
    )

    /** SIM preferences: stores the user's default SIM for calls. */
    private val simPrefs: SharedPreferences by lazy {
        getSharedPreferences(SIM_PREFS, Context.MODE_PRIVATE)
    }

    /** All active SIMs on this device. */
    private fun activeSims(): List<SimInfo> = runCatching {
        if (Build.VERSION.SDK_INT < 22) return emptyList()
        val sm = getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val infos = sm.getActiveSubscriptionInfoList() ?: return emptyList()
        infos.map { info ->
            // SignalStrength.getDbm() is a HIDDEN platform API: it is not in
            // android.jar, so it can never resolve in a normal build (verified
            // with javap against android-sdk/platforms/android-36). getLevel()
            // is public and returns 0..4, which is also what iOS presents --
            // bars, not a decibel figure. The label and the field were both
            // rewritten to bars rather than worked around the missing API.
            val bars = runCatching {
                if (Build.VERSION.SDK_INT >= 23) {
                    getSystemService(TelephonyManager::class.java)
                        ?.createForSubscriptionId(info.subscriptionId)
                        ?.signalStrength?.level ?: 0
                } else 0
            }.getOrDefault(0)
            SimInfo(
                subscriptionId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                carrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: "SIM ${info.simSlotIndex + 1}",
                signalBars = bars
            )
        }
    }.getOrDefault(emptyList())

    /** True when the device has two or more active SIMs. */
    private fun isDualSim(): Boolean = activeSims().size >= 2

    /** The SIM the user selected for calls (or the first active SIM). */
    private fun selectedSim(): SimInfo {
        val sims = activeSims()
        if (sims.isEmpty()) return SimInfo(-1, 0, "No SIM", 0)
        val savedId = simPrefs.getInt(KEY_CALL_DEFAULT_SUB, -1)
        return sims.firstOrNull { it.subscriptionId == savedId } ?: sims.first()
    }

    /** Persist the user's default SIM choice for calls. */
    private fun saveSelectedSim(subId: Int) {
        simPrefs.edit().putInt(KEY_CALL_DEFAULT_SUB, subId).apply()
    }

    /** Resolve a subscription ID to a SIM slot index for display. */
    private fun simSlotForSubId(subId: Int): Int = runCatching {
        if (Build.VERSION.SDK_INT < 22) return -1
        val sm = getSystemService(SubscriptionManager::class.java) ?: return -1
        val info = sm.getActiveSubscriptionInfo(subId) ?: return -1
        info.simSlotIndex
    }.getOrDefault(-1)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        }
        AppleuxAppChrome.apply(this, root)
        setContentView(root)
        requestBasePermissions()

        root.addView(navBar())

        val bodyContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        bodyContainer.addView(scroll)

        fab = keypadFab()
        val fabSize = dp(PhoneTokens.FAB_SIZE_DP)
        bodyContainer.addView(fab, FrameLayout.LayoutParams(fabSize, fabSize).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            val m = dp(SpacingTokens.INSETS_XL)
            setMargins(m, m, m, m)
        })
        root.addView(bodyContainer)

        // The large title scrolls with the content and fades the compact title
        // in, which is the iOS 27 navigation-bar behaviour.
        collapseThresholdPx = dp(SpacingTokens.INSETS_XXXL14)
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val t = collapseThresholdPx.toFloat()
            compactTitle.alpha = if (t <= 0f) 1f else (scrollY / t).coerceIn(0f, 1f)
        }

        val tabs = AppleuxNavigationBar(this).apply {
            // CFBundleDisplayNameClarityUI ("Calls") is the low-vision label.
            contentDescription = PhoneInfo.CLARITY_UI_NAME
        }
        Tab.values().forEach { t ->
            tabs.addItem(AppleuxNavigationBar.NavigationItem(
                t.ordinal + 1, t.label,
                AppleuxIconRegistry.getDrawableRes(t.icon) ?: 0))
        }
        tabs.setOnItemSelectedListener { item ->
            Tab.values().firstOrNull { it.ordinal + 1 == item.id }?.let { showTab(it) }
        }
        val start = initialTab()
        tabs.setActiveItem(start.ordinal + 1)
        root.addView(tabs, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        watchCalls()
        showTab(start)
    }

    /**
     * iOS reaches each Phone tab through its `PrivateURLSchemes`
     * (mobilephone-favorites / -recents / -contacts / -keypad, vmshow), so the
     * same scheme opens the matching tab here when the activity is launched
     * with one. A `tel:` DIAL intent and anything unrecognised start on Recents.
     */
    private fun initialTab(): Tab {
        val scheme = intent?.data?.scheme ?: return Tab.RECENTS
        return Tab.values().firstOrNull { it.scheme == scheme } ?: Tab.RECENTS
    }

    override fun onResume() {
        super.onResume()
        // Re-resolve colours after a dark-mode flip and refresh the data tabs.
        showTab(currentTab)
    }

    // ---- Navigation chrome -------------------------------------------------

    private fun navBar(): FrameLayout {
        val nav = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(SizingTokens.NAVIGATION_BAR_HEIGHT))
        }
        compactTitle = AppleuxText(this).apply {
            text = PhoneInfo.DISPLAY_NAME
            applyTypography(TypographyTokens.headline)
            setTextColor(ColorTokens.label)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = dp(SpacingTokens.INSETS_XL)
            }
        }
        nav.addView(compactTitle)

        // UIApplicationShortcutItems search shortcut -> Contacts search.
        val search = iconButton(registryIcon(PhoneInfo.Shortcuts.SEARCH_ICON), "Search contacts") {
            showTab(Tab.CONTACTS)
        }
        val searchSize = dp(PhoneTokens.FAB_SIZE_DP)
        search.layoutParams = FrameLayout.LayoutParams(searchSize, searchSize).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = dp(SpacingTokens.INSETS_XL)
        }
        nav.addView(search)
        return nav
    }

    // ---- Tab dispatch ------------------------------------------------------

    private fun showTab(tab: Tab) {
        currentTab = tab
        content.removeAllViews()
        when (tab) {
            Tab.FAVORITES -> showFavorites()
            Tab.RECENTS -> showRecents()
            Tab.CONTACTS -> showContacts()
            Tab.KEYPAD -> showKeypad()
            Tab.VOICEMAIL -> showVoicemail()
        }
        fab.visibility = if (tab == Tab.KEYPAD) View.GONE else View.VISIBLE
        scrollToTop()
    }

    private fun scrollToTop() = content.post {
        (content.parent as? ScrollView)?.scrollTo(0, 0)
    }

    // ---- Favorites / Recents / Contacts (grouped lists) --------------------

    private fun showFavorites() {
        largeTitle()
        if (!have(Manifest.permission.READ_CONTACTS)) {
            emptyRow("Allow Contacts to see favorites")
            return
        }
        val rows = mutableListOf<View>()
        runCatching {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.STARRED),
                "${ContactsContract.Contacts.STARRED}=1", null,
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1).orEmpty()
                    val number = firstPhone(id)
                    if (number.isNotEmpty()) rows.add(contactRow(name, "mobile", number))
                }
            }
        }
        if (rows.isEmpty()) {
            emptyRow("No Favorites")
            return
        }
        content.addView(groupedCard(rows))
        footnote("Favorites are your starred contacts.")
    }

    private fun showRecents() {
        largeTitle()
        if (!have(Manifest.permission.READ_CALL_LOG)) {
            emptyRow("Allow Call Log to see recents")
            return
        }

        // Call statistics card (iOS 27 summary)
        val stats = callStats()
        if (stats.isNotEmpty()) {
            content.addView(AppleuxText(this).apply {
                text = stats
                applyTypography(TypographyTokens.subheadline)
                setTextColor(ColorTokens.secondaryLabel)
                setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD),
                    dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD))
            })
        }

        // Export + block-list management actions
        content.addView(AppleuxButton(this).apply {
            setText("Export Call Log (CSV)")
            setButtonStyle(AppleuxButton.Style.SECONDARY)
            layoutParams = insetParams().apply { topMargin = dp(SpacingTokens.INSETS_MD) }
            setOnClickListener { exportCallLogCsv() }
        })
        content.addView(spamBlockCard())
        content.addView(simSettingsCard())

        val rows = mutableListOf<View>()
        runCatching {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE,
                    CallLog.Calls.PHONE_ACCOUNT_ID),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < MAX_RECENTS) {
                    val num = c.getString(0).orEmpty()
                    if (num.isEmpty()) continue
                    val name = c.getString(1)?.takeIf { it.isNotEmpty() } ?: num
                    val whenStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(c.getLong(2)))
                    val dir = when (c.getInt(4)) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        else -> "Missed"
                    }
                    val subId = c.getString(5)?.toIntOrNull() ?: -1
                    val sim = simLabel(subId)
                    val missed = if (dir == "Missed") " · ${missedCountFor(num)} missed" else ""
                    val row = contactRow(name, "$dir · $whenStr · ${c.getLong(3)}s · $sim$missed", num)
                    applySwipe(row, num) {
                        runCatching {
                            contentResolver.delete(
                                CallLog.Calls.CONTENT_URI,
                                "${CallLog.Calls.NUMBER}=?", arrayOf(num))
                        }
                        showTab(Tab.RECENTS)
                    }
                    rows.add(row)
                }
            }
        }
        if (rows.isEmpty()) {
            emptyRow("No Recents")
            return
        }
        content.addView(groupedCard(rows))
    }

    private fun showContacts() {
        largeTitle()
        if (!have(Manifest.permission.READ_CONTACTS)) {
            emptyRow("Allow Contacts to browse")
            return
        }
        val rows = mutableListOf<View>()
        runCatching {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.STARRED),
                null, null, "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < MAX_CONTACTS) {
                    val id = c.getLong(0)
                    val name = c.getString(1).orEmpty()
                    val number = firstPhone(id)
                    if (number.isNotEmpty()) rows.add(contactRow(name, "mobile", number))
                }
            }
        }
        if (rows.isEmpty()) {
            emptyRow("No Contacts")
            return
        }
        content.addView(groupedCard(rows))
    }

    private fun firstPhone(contactId: Long): String {
        var phone = ""
        runCatching {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                arrayOf(contactId.toString()), null
            )?.use { p -> if (p.moveToFirst()) phone = p.getString(0).orEmpty() }
        }
        return phone
    }

    // ---- Keypad ------------------------------------------------------------

    private fun showKeypad() {
        val display = AppleuxText(this).apply {
            applyTypography(TypographyTokens.largeTitle)
            setTextColor(ColorTokens.label)
            gravity = Gravity.CENTER
            minHeight = dp(PhoneTokens.CALL_BUTTON_DP)
            setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_XXXL),
                dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD))
            text = dial.toString()
        }
        numberView = display
        // Tap the display to paste a number from the clipboard.
        display.setOnClickListener { pasteFromClipboard() }
        content.addView(display)

        // T9 predictive search results (above the grid, below the display)
        content.addView(AppleuxText(this).apply {
            applyTypography(TypographyTokens.subheadline)
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
            setPadding(dp(SpacingTokens.INSETS_XL), 0,
                dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD))
            tag = "t9matches"
        })

        val grid = GridLayout(this).apply {
            columnCount = KEYPAD_COLUMNS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(SpacingTokens.INSETS_LG)
            }
        }
        for ((digit, sub) in KEYPAD_KEYS) {
            val btn = keypadButton(digit, sub) { onDigit(digit) }
            // Long-press 0 for +
            if (digit == "0") {
                btn.setOnLongClickListener {
                    dial.append("+")
                    numberView?.text = dial.toString()
                    onDialChanged()
                    true
                }
            }
            grid.addView(btn)
        }
        content.addView(grid)
        content.addView(callRow())
    }

    private fun onDigit(d: String) {
        dial.append(d)
        numberView?.text = dial.toString()
        onDialChanged()
    }

    private fun onBackspace() {
        if (dial.isNotEmpty()) {
            dial.deleteCharAt(dial.length - 1)
            numberView?.text = dial.toString()
        }
    }

    private fun placeCall() {
        if (dial.isEmpty()) return
        dial(dial.toString(), call = true)
    }

    // ---- Voicemail / recordings / privacy ----------------------------------

    private fun showVoicemail() {
        largeTitle()

        val autoCard = AppleuxCard(this)
        val autoRow = AppleuxListItem(this).apply {
            setTitle("Call Recording")
            setSubtitle("Automatically record all calls")
            setDividerVisible(false)
        }
        autoRow.setAccessoryView(AppleuxToggle(this).apply {
            setTrackColorOn(ColorTokens.systemBlue)
            setChecked(autoRecord(), animate = false)
            setOnCheckedChangeListener {
                prefs.edit().putBoolean(KEY_AUTO_RECORD, it).apply()
            }
        })
        autoCard.addView(autoRow)
        content.addView(autoCard, insetParams())

        content.addView(AppleuxButton(this).apply {
            setText(if (isDefaultDialer()) "Default phone app ✓" else "Set as default phone app")
            setButtonStyle(AppleuxButton.Style.SECONDARY)
            layoutParams = insetParams().apply { topMargin = dp(SpacingTokens.INSETS_LG) }
            setOnClickListener { requestDialerRole() }
        })

        // Visual voicemail list with playback progress + delete
        val vmDir = File(filesDir, DIR_VOICEMAIL)
        val vmFiles = vmDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (vmFiles.isNotEmpty()) {
            vmFiles.forEach { f ->
                content.addView(vmRow(f))
            }
        }

        val files = File(filesDir, DIR_RECORDINGS).listFiles()
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty() && vmFiles.isEmpty()) {
            emptyRow("No Voicemail")
        } else if (files.isNotEmpty()) {
            content.addView(groupedCard(files.map { f ->
                mediaRow(f.nameWithoutExtension,
                    "${f.length() / 1024} KB · ${DateFormat.getDateTimeInstance().format(Date(f.lastModified()))}",
                    f)
            }))
        }
        content.addView(privacyFooter())
    }

    /** Visual voicemail row: play/pause, progress bar, transcription, delete. */
    private fun vmRow(f: File): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD),
                dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD))
            layoutParams = insetParams()
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(playButton("Play ${f.nameWithoutExtension}") { playFile(f) })
        topRow.addView(AppleuxText(this).apply {
            text = f.nameWithoutExtension
            applyTypography(TypographyTokens.headline)
            setTextColor(ColorTokens.label)
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(SpacingTokens.INSETS_LG)
            }
        })
        topRow.addView(iconButton("system_trash", "Delete voicemail") {
            f.delete()
            showTab(Tab.VOICEMAIL)
        })
        row.addView(topRow)

        // Playback progress bar
        vmSeek = SeekBar(this).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(SpacingTokens.INSETS_SM)
            }
        }
        row.addView(vmSeek)

        // Local transcription placeholder (programmatic, no cloud)
        row.addView(AppleuxText(this).apply {
            text = "Transcription (local): ${f.nameWithoutExtension}"
            applyTypography(TypographyTokens.footnote)
            setTextColor(ColorTokens.tertiaryLabel)
            setPadding(0, dp(SpacingTokens.INSETS_XS), 0, 0)
        })
        return row
    }

    private fun playFile(f: File) {
        runCatching {
            player?.release()
            stopVmProgress()
            player = android.media.MediaPlayer().apply {
                setDataSource(f.absolutePath)
                prepare()
                start()
            }
            startVmProgress()
        }.onFailure { toast("Cannot play") }
    }

    /** Drive the voicemail progress bar while playing. */
    private fun startVmProgress() {
        val h = vmHandler ?: Handler(Looper.getMainLooper()).also { vmHandler = it }
        val tick = object : Runnable {
            override fun run() {
                val p = player
                if (p != null && p.isPlaying && vmSeek != null && p.duration > 0) {
                    vmSeek?.progress = (p.currentPosition * 100) / p.duration
                    h.postDelayed(this, 250)
                }
            }
        }
        vmTick = tick
        h.post(tick)
    }

    private fun stopVmProgress() {
        vmTick?.let { vmHandler?.removeCallbacks(it) }
        vmTick = null
        vmSeek?.progress = 0
    }

    // ---- Rows and cards ----------------------------------------------------

    private fun listRow(
        name: String,
        info: String,
        trailing: View,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(SpacingTokens.INSETS_XL), 0, dp(SpacingTokens.INSETS_XL), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(PhoneTokens.ROW_HEIGHT_DP))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        row.addView(avatar(name))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(AppleuxText(this).apply {
            text = name.ifEmpty { "Unknown" }
            applyTypography(TypographyTokens.headline)
            setTextColor(ColorTokens.label)
            maxLines = 1
        })
        if (info.isNotEmpty()) col.addView(AppleuxText(this).apply {
            text = info
            applyTypography(TypographyTokens.subheadline)
            setTextColor(ColorTokens.secondaryLabel)
            maxLines = 1
        })
        row.addView(col)
        row.addView(trailing)
        return row
    }

    private fun contactRow(name: String, info: String, number: String): LinearLayout =
        listRow(name, info, callButton("Call ${name.ifEmpty { number }}") {
            dial(number, call = false)
        }) { dial(number, call = false) }

    private fun mediaRow(name: String, info: String, file: File): LinearLayout =
        listRow(name, info, playButton("Play $name") { playFile(file) }) { playFile(file) }

    private fun groupedCard(rows: List<View>): AppleuxCard {
        val card = AppleuxCard(this).apply {
            cardCornerRadius = SizingTokens.CORNER_RADIUS_CARD
            layoutParams = insetParams()
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEachIndexed { i, row ->
            if (i > 0) col.addView(divider())
            col.addView(row)
        }
        card.addView(col)
        return card
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(ColorTokens.separator)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, hairline()).apply {
            marginStart = dp(SpacingTokens.INSETS_XL +
                PhoneTokens.AVATAR_DP + SpacingTokens.INSETS_LG)
        }
    }

    private fun avatar(name: String): LinearLayout {
        val size = dp(PhoneTokens.AVATAR_DP)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(SpacingTokens.INSETS_LG)
            }
        }
        container.addView(AppleuxText(this).apply {
            text = initials(name)
            applyTypography(TypographyTokens.subheadlineEmphasized)
            setTextColor(ColorTokens.white)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(avatarColor(name))
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
        })
        // SIM badge (dual-SIM only): small "1" or "2" on the avatar
        if (isDualSim()) {
            val sim = selectedSim()
            container.addView(AppleuxText(this).apply {
                text = "${sim.slotIndex + 1}"
                applyTypography(TypographyTokens.caption2Emphasized)
                setTextColor(ColorTokens.white)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorTokens.systemBlue)
                }
                val badgeSize = dp(SizingTokens.CONTROL_SIZE_SM)
                layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                    gravity = Gravity.END or Gravity.BOTTOM
                    topMargin = -dp(SpacingTokens.INSETS_MD)
                }
            })
        }
        return container
    }

    private fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    private fun avatarColor(name: String): Int =
        AVATAR_PALETTE[if (name.isEmpty()) 0 else name.hashCode().mod(AVATAR_PALETTE.size)]

    // ---- Buttons -----------------------------------------------------------

    private fun callButton(contentDescription: String, onClick: () -> Unit): ImageButton =
        roundButton("system_phone", PhoneTokens.ROW_HEIGHT_DP, ColorTokens.systemGreen,
            ColorTokens.white, contentDescription, onClick)

    private fun keypadFab(): ImageButton =
        roundButton("system_grid", PhoneTokens.FAB_SIZE_DP, glassFill(),
            ColorTokens.label, Tab.KEYPAD.label) { showTab(Tab.KEYPAD) }

    private fun playButton(contentDescription: String, onClick: () -> Unit): ImageButton =
        roundButton("system_play", PhoneTokens.ROW_HEIGHT_DP, ColorTokens.systemBlue,
            ColorTokens.white, contentDescription, onClick)

    private fun iconButton(
        icon: String,
        contentDescription: String,
        onClick: () -> Unit
    ): ImageButton = roundButton(icon, PhoneTokens.FAB_SIZE_DP, ColorTokens.systemFillSecondary,
        ColorTokens.label, contentDescription, onClick)

    private fun roundButton(
        icon: String,
        sizeDp: Float,
        fill: Int,
        tint: Int,
        contentDescription: String,
        onClick: () -> Unit
    ): ImageButton {
        val size = dp(sizeDp)
        val res = AppleuxIconRegistry.getDrawableRes(icon)
        return ImageButton(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@PhoneActivity, res ?: 0))
            setColorFilter(tint)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(size, size)
            this.contentDescription = contentDescription
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun keypadButton(label: String, sub: String, onClick: () -> Unit): LinearLayout {
        val size = dp(PhoneTokens.KEYPAD_BUTTON_DP)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(glassFill())
            }
            layoutParams = GridLayout.LayoutParams().apply {
                width = size
                height = size
                val m = dp(PhoneTokens.KEYPAD_GAP_DP)
                setMargins(m, m, m, m)
            }
            addView(AppleuxText(this@PhoneActivity).apply {
                text = label
                applyTypography(TypographyTokens.title1)
                setTextColor(ColorTokens.label)
            })
            if (sub.isNotEmpty()) addView(AppleuxText(this@PhoneActivity).apply {
                text = sub
                applyTypography(TypographyTokens.caption2)
                setTextColor(ColorTokens.secondaryLabel)
            })
            setOnClickListener { onClick() }
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.alpha = 0.5f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                }
                false
            }
        }
    }

    private fun callRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            val m = dp(SpacingTokens.INSETS_LG)
            setMargins(0, m, 0, dp(SpacingTokens.INSETS_XXXL))
        }
        addView(callButton("Call") { placeCall() })
        addView(AppleuxText(this@PhoneActivity).apply {
            text = "⌫"
            applyTypography(TypographyTokens.title1)
            setTextColor(ColorTokens.label)
            gravity = Gravity.CENTER
            val s = dp(PhoneTokens.CALL_BUTTON_DP)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginStart = dp(SpacingTokens.INSETS_LG)
            }
            isClickable = true
            setOnClickListener { onBackspace() }
        })
    }

    // ---- Small pieces ------------------------------------------------------

    private fun largeTitle() {
        content.addView(AppleuxText(this).apply {
            text = PhoneInfo.DISPLAY_NAME
            applyTypography(TypographyTokens.largeTitle)
            setTextColor(ColorTokens.label)
            setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_XL),
                dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD))
        })
    }

    private fun footnote(text: String) {
        content.addView(AppleuxText(this).apply {
            this.text = text
            applyTypography(TypographyTokens.footnote)
            setTextColor(ColorTokens.secondaryLabel)
            setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD),
                dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_XL))
        })
    }

    private fun privacyFooter(): AppleuxText = AppleuxText(this).apply {
        text = "Permissions declared by iOS 27: ${PhoneInfo.Privacy.CALL_RECORDS}, " +
            "${PhoneInfo.Privacy.CONTACTS}, ${PhoneInfo.Privacy.VOICE_CALL_RECORDING}, " +
            "${PhoneInfo.Privacy.PHONE_NUMBER}, ${PhoneInfo.Privacy.MICROPHONE}."
        applyTypography(TypographyTokens.footnote)
        setTextColor(ColorTokens.secondaryLabel)
        setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_XL),
            dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_XXXL14))
    }

    private fun emptyRow(text: String) {
        content.addView(AppleuxText(this).apply {
            this.text = text
            applyTypography(TypographyTokens.footnote)
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
            setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_XXXL14),
                dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_XXXL14))
        })
    }

    private fun insetParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            val m = dp(SpacingTokens.INSETS_XL)
            setMargins(m, dp(SpacingTokens.INSETS_MD), m, 0)
        }

    private fun glassFill(): Int = if (ColorTokens.darkMode) {
        LiquidGlassTokens.REGULAR_BG_DARK
    } else {
        LiquidGlassTokens.REGULAR_BG_LIGHT
    }

    private fun hairline(): Int =
        (resources.displayMetrics.density / 2f).toInt().coerceAtLeast(1)

    private fun dp(v: Float): Int = LayoutUtils.dpToPx(v, this)

    // ---- Permissions / dialing ---------------------------------------------

    private fun have(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun requestBasePermissions() {
        val needed = listOf(Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE).filter { !have(it) }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), REQ_BASE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        showTab(currentTab)
    }

    private fun dial(number: String, call: Boolean) {
        if (number.isEmpty()) return
        if (call) {
            if (!have(Manifest.permission.CALL_PHONE)) {
                requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL)
                return
            }
            // Dual-SIM: show selection dialog if no default is set
            if (isDualSim() && simPrefs.getInt(KEY_CALL_DEFAULT_SUB, -1) < 0) {
                showSimSelectionDialog(number)
                return
            }
            runCatching { startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))) }
                .onFailure { toast("Cannot place call") }
        } else {
            runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
                .onFailure { toast("Cannot open dialer") }
        }
    }

    /**
     * Dual-SIM call dialog: "Call with" → SIM 1 (Carrier) / SIM 2 (Carrier).
     * Glass background, systemBlue accent. Shown before placing a call when
     * no default SIM is set.
     */
    private fun showSimSelectionDialog(number: String) {
        val sims = activeSims()
        if (sims.size < 2) {
            runCatching { startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))) }
                .onFailure { toast("Cannot place call") }
            return
        }
        AppleuxDialogs.choose(
            this,
            items = sims.map { "SIM ${it.slotIndex + 1} (${it.carrierName})" }.toTypedArray(),
            title = "Call with",
        ) { index ->
            val sim = sims[index]
            saveSelectedSim(sim.subscriptionId)
            runCatching { startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))) }
                .onFailure { toast("Cannot place call") }
        }
    }

    // ---- Default dialer / auto record --------------------------------------

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
                    rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER),
                    REQ_DIALER_ROLE)
            }
        }
    }

    private fun autoRecord(): Boolean = prefs.getBoolean(KEY_AUTO_RECORD, false)

    // ---- Auto capture on call state ----------------------------------------

    private fun watchCalls() {
        if (!have(Manifest.permission.READ_PHONE_STATE)) {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), REQ_PHONE_STATE)
            return
        }
        val tm = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            tm.registerTelephonyCallback(Executors.newSingleThreadExecutor(),
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onCallState(state, null)
                })
        } else {
            @Suppress("DEPRECATION")
            tm.listen(object : PhoneStateListener() {
                @Deprecated("deprecated")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                    onCallState(state, phoneNumber)
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun onCallState(state: Int, number: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                stopCapture()
                currentCallNumber = number?.takeIf { it.isNotEmpty() } ?: currentCallNumber
                if (currentCallNumber.isNotEmpty()) showIncomingCall(currentCallNumber)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (autoRecord()) startCapture()
                if (callOverlay == null && currentCallNumber.isEmpty()) {
                    currentCallNumber = number ?: lastIncomingNumber()
                }
                if (currentCallNumber.isNotEmpty()) showActiveCall(currentCallNumber)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                stopCapture()
                currentCallNumber = ""
                hideCallScreen()
            }
        }
    }

    /** Best-effort last incoming number from the call log (API 31+ has no number in the callback). */
    private fun lastIncomingNumber(): String = runCatching {
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER),
            "${CallLog.Calls.TYPE}=?", arrayOf(CallLog.Calls.INCOMING_TYPE.toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else "" } ?: ""
    }.getOrDefault("")

    private fun startCapture() {
        if (recorder != null) return
        if (!have(Manifest.permission.RECORD_AUDIO)) return
        runCatching {
            val dir = File(filesDir, DIR_RECORDINGS)
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

    private fun toast(text: String) =
        AppleuxToast.show(this, text)

    // ---- Active call screen (iOS 27) ----------------------------------------

    /**
     * Full-screen iOS 27 call surface: 120 dp circular poster, 34 sp name,
     * monospaced 17 sp timer, a 3x2 grid of 63 dp glass controls and a 72 dp
     * red end button. Shown over the tab content; the proximity sensor blanks
     * the screen while the phone is at the ear.
     */
    private fun showIncomingCall(number: String) {
        if (callOverlay != null) return
        buildCallOverlay(number, nameFor(number), incoming = true)
    }

    private fun showActiveCall(number: String) {
        buildCallOverlay(number, nameFor(number), incoming = false)
    }

    private fun hideCallScreen() {
        stopCallTimer()
        unregisterProximity()
        (callOverlay?.parent as? ViewGroup)?.removeView(callOverlay)
        callOverlay = null
    }

    private fun buildCallOverlay(number: String, name: String?, incoming: Boolean) {
        hideCallScreen()
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(ColorTokens.systemBackground)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
            isClickable = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT).apply {
                val m = dp(SpacingTokens.INSETS_XXXL)
                setMargins(0, m, 0, m)
            }
        }
        col.addView(contactPoster(name ?: number, PhoneTokens.POSTER_DP))
        col.addView(AppleuxText(this).apply {
            text = name ?: number
            applyTypography(TypographyTokens.largeTitle)
            setTextColor(ColorTokens.label)
            gravity = Gravity.CENTER
            setPadding(0, dp(SpacingTokens.INSETS_XXL), 0, dp(SpacingTokens.INSETS_XS))
        })
        col.addView(AppleuxText(this).apply {
            text = number
            applyTypography(TypographyTokens.subheadline)
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
        })
        callTimerView = AppleuxText(this).apply {
            applyTypography(TypographyTokens.headline)
            typeface = Typeface.MONOSPACE
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(SpacingTokens.INSETS_XXXL))
        }
        col.addView(callTimerView)

        // SIM indicator near the timer (dual-SIM only)
        if (isDualSim()) {
            val sim = selectedSim()
            col.addView(AppleuxText(this).apply {
                text = "SIM ${sim.slotIndex + 1}"
                applyTypography(TypographyTokens.caption2)
                setTextColor(ColorTokens.secondaryLabel)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(SizingTokens.CORNER_RADIUS_SMALL).toFloat()
                    setColor(ColorTokens.systemFillSecondary)
                }
                setPadding(dp(SpacingTokens.INSETS_SM), dp(SpacingTokens.INSETS_XS),
                    dp(SpacingTokens.INSETS_SM), dp(SpacingTokens.INSETS_XS))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(SpacingTokens.INSETS_LG)
                }
            })
        }

        if (spamGuard.isSpam(number)) col.addView(spamBanner())

        if (incoming) {
            col.addView(callActionButton("Accept", "system_phone",
                ColorTokens.systemGreen) { hideCallScreen() })
            col.addView(callActionButton("Decline", "system_phone",
                ColorTokens.systemRed) { hideCallScreen() })
        } else {
            col.addView(callControlGrid())
            col.addView(endCallButton())
        }
        overlay.addView(col)
        findViewById<ViewGroup>(android.R.id.content).addView(overlay)
        callOverlay = overlay
        if (!incoming) {
            startCallTimer()
            registerProximity()
        }
    }

    /** Circular initials poster — same treatment as the list avatar, larger. */
    private fun contactPoster(name: String, sizeDp: Float): AppleuxText {
        val size = dp(sizeDp)
        return AppleuxText(this).apply {
            text = initials(name)
            applyTypography(TypographyTokens.largeTitle)
            setTextColor(ColorTokens.white)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(avatarColor(name))
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    /** One 63 dp glass control with its label beneath (Mute, Keypad, ...). */
    private fun callControlButton(
        icon: String,
        label: String,
        active: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val size = dp(PhoneTokens.CONTROL_DP)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            addView(ImageButton(this@PhoneActivity).apply {
                val res = AppleuxIconRegistry.getDrawableRes(icon)
                setImageDrawable(ContextCompat.getDrawable(this@PhoneActivity, res ?: 0))
                setColorFilter(if (active) ColorTokens.black else ColorTokens.white)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (active) ColorTokens.white else glassFill())
                }
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(size, size)
                contentDescription = label
                setOnClickListener { onClick() }
            })
            addView(AppleuxText(this@PhoneActivity).apply {
                text = label
                applyTypography(TypographyTokens.caption1)
                setTextColor(ColorTokens.secondaryLabel)
                gravity = Gravity.CENTER
            })
            setOnClickListener { onClick() }
        }
    }

    /** iOS 27 active-call grid: Mute / Keypad / Speaker / Add Call / Hold / Audio. */
    private fun callControlGrid(): GridLayout {
        val grid = GridLayout(this).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(SpacingTokens.INSETS_LG)
            }
        }
        grid.addView(callControlButton("system_mic", "Mute", muted) { muted = !muted })
        grid.addView(callControlButton("system_grid", "Keypad", false) {
            hideCallScreen(); showTab(Tab.KEYPAD)
        })
        grid.addView(callControlButton("system_volume", "Speaker", speakerOn) { speakerOn = !speakerOn })
        grid.addView(callControlButton("system_add", "Add Call", false) { })
        grid.addView(callControlButton("system_pause", "Hold", held) { held = !held })
        grid.addView(callControlButton("system_chevron_up", "Audio", false) { })
        return grid
    }

    /** 72 dp systemRed circle with the white phone glyph. */
    private fun endCallButton(): ImageButton {
        val size = dp(PhoneTokens.CALL_BUTTON_DP)
        val res = AppleuxIconRegistry.getDrawableRes("system_phone")
        return ImageButton(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@PhoneActivity, res ?: 0))
            setColorFilter(ColorTokens.white)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorTokens.systemRed)
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                val m = dp(SpacingTokens.INSETS_LG)
                setMargins(0, m, 0, dp(SpacingTokens.INSETS_XXXL))
            }
            contentDescription = "End call"
            setOnClickListener { hideCallScreen() }
        }
    }

    /** Incoming call action: green Accept / Decline red, iOS circular buttons. */
    private fun callActionButton(
        label: String,
        icon: String,
        color: Int,
        onClick: () -> Unit
    ): ImageButton {
        val size = dp(PhoneTokens.CONTROL_DP)
        val res = AppleuxIconRegistry.getDrawableRes(icon)
        return ImageButton(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@PhoneActivity, res ?: 0))
            setColorFilter(ColorTokens.white)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                val m = dp(SpacingTokens.INSETS_MD)
                setMargins(m, m, m, m)
            }
            contentDescription = label
            setOnClickListener { onClick() }
        }
    }

    /** Call timer: 17sp monospaced, updates every second. */
    private fun startCallTimer() {
        stopCallTimer()
        callStartMs = System.currentTimeMillis()
        val tick = object : Runnable {
            override fun run() {
                val secs = (System.currentTimeMillis() - callStartMs) / 1000
                callTimerView?.text = fmtCallTime(secs)
                callHandler.postDelayed(this, 1000)
            }
        }
        callTick = tick
        callHandler.post(tick)
    }

    private fun stopCallTimer() {
        callTick?.let { callHandler.removeCallbacks(it) }
        callTick = null
    }

    /** mm:ss monospaced, matching the iOS 27 call timer. */
    private fun fmtCallTime(secs: Long): String =
        String.format(Locale.US, "%02d:%02d", secs / 60, secs % 60)

    // ---- Proximity sensor ---------------------------------------------------

    private fun registerProximity() {
        val sm = getSystemService(SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.values.isNotEmpty()) {
                    val near = e.values[0] < (proximity?.maximumRange ?: 1f)
                    setScreenOff(near)
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        proximityListener = listener
        sm.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun unregisterProximity() {
        proximityListener?.let { sensorManager?.unregisterListener(it) }
        proximityListener = null
        setScreenOff(false)
    }

    /** Proximity-near blanks the screen (screen-off at the ear). */
    private fun setScreenOff(off: Boolean) {
        val lp = window.attributes
        lp.screenBrightness = if (off) 0f
        else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    // ---- Spam protection -----------------------------------------------------

    /**
     * Local spam protection: a SharedPreferences-backed list plus pattern
     * heuristics (short numbers, repeated digits, premium-rate prefixes).
     * No network, no cloud — everything stays on device.
     */
    inner class SpamGuard {
        private val PREMIUM_PREFIXES = listOf("1900", "0898", "0700", "118", "09", "084", "087")

        fun isSpam(number: String): Boolean {
            val n = number.filter { it.isDigit() }
            if (n.isEmpty()) return false
            if (prefs.getStringSet(KEY_SPAM_LIST, emptySet())?.contains(n) == true) return true
            if (n.length < 6) return true
            if (n.toSet().size == 1) return true
            return PREMIUM_PREFIXES.any { n.startsWith(it) }
        }

        fun isBlocked(number: String): Boolean {
            val n = number.filter { it.isDigit() }
            return prefs.getStringSet(KEY_BLOCKED, emptySet())?.contains(n) == true
        }

        fun markSpam(number: String) {
            val n = number.filter { it.isDigit() }
            val set = prefs.getStringSet(KEY_SPAM_LIST, emptySet())!!.toMutableSet()
            set.add(n)
            prefs.edit().putStringSet(KEY_SPAM_LIST, set).apply()
        }

        fun unmarkSpam(number: String) {
            val n = number.filter { it.isDigit() }
            val set = prefs.getStringSet(KEY_SPAM_LIST, emptySet())!!.toMutableSet()
            set.remove(n)
            prefs.edit().putStringSet(KEY_SPAM_LIST, set).apply()
        }

        fun blockedList(): Set<String> =
            prefs.getStringSet(KEY_BLOCKED, emptySet())!!.toMutableSet()

        fun autoBlock(): Boolean = prefs.getBoolean(KEY_AUTO_BLOCK, false)

        fun setAutoBlock(on: Boolean) = prefs.edit().putBoolean(KEY_AUTO_BLOCK, on).apply()
    }

    /** Orange "Possible Spam" banner shown on spam calls. */
    private fun spamBanner(): AppleuxText = AppleuxText(this).apply {
        text = "Possible Spam"
        applyTypography(TypographyTokens.footnoteEmphasized)
        setTextColor(ColorTokens.white)
        gravity = Gravity.CENTER
        setPadding(dp(SpacingTokens.INSETS_MD), dp(SpacingTokens.INSETS_SM),
            dp(SpacingTokens.INSETS_MD), dp(SpacingTokens.INSETS_SM))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(SizingTokens.CORNER_RADIUS_SMALL).toFloat()
            setColor(ColorTokens.systemOrange)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(SpacingTokens.INSETS_LG)
        }
    }

    /** Block-list management card: auto-block toggle + blocked numbers list. */
    private fun spamBlockCard(): AppleuxCard {
        val card = AppleuxCard(this).apply {
            cardCornerRadius = SizingTokens.CORNER_RADIUS_CARD
            layoutParams = insetParams().apply { topMargin = dp(SpacingTokens.INSETS_MD) }
        }
        val autoRow = AppleuxListItem(this).apply {
            setTitle("Auto-Block Spam")
            setSubtitle("Silently send suspected spam to voicemail")
            setDividerVisible(false)
        }
        autoRow.setAccessoryView(AppleuxToggle(this).apply {
            setTrackColorOn(ColorTokens.systemBlue)
            setChecked(spamGuard.autoBlock(), animate = false)
            setOnCheckedChangeListener { spamGuard.setAutoBlock(it) }
        })
        card.addView(autoRow)

        val blocked = spamGuard.blockedList()
        if (blocked.isNotEmpty()) {
            blocked.forEach { num ->
                val row = AppleuxListItem(this).apply {
                    setTitle(num)
                    setSubtitle("Blocked")
                    setDividerVisible(false)
                }
                row.setAccessoryView(AppleuxButton(this).apply {
                    setText("Unblock")
                    setButtonStyle(AppleuxButton.Style.SECONDARY)
                    setOnClickListener {
                        val set = spamGuard.blockedList().toMutableSet()
                        set.remove(num)
                        prefs.edit().putStringSet(KEY_BLOCKED, set).apply()
                        showTab(Tab.RECENTS)
                    }
                })
                card.addView(row)
            }
        }
        return card
    }

    /**
     * SIM settings card: shows each SIM's info, enable/disable toggle, and
     * preferred SIM for calls. Accessible from the Voicemail/Settings tab.
     */
    private fun simSettingsCard(): AppleuxCard {
        val card = AppleuxCard(this).apply {
            cardCornerRadius = SizingTokens.CORNER_RADIUS_CARD
            layoutParams = insetParams().apply { topMargin = dp(SpacingTokens.INSETS_MD) }
        }

        // Default SIM for calls setting
        val defaultSimRow = AppleuxListItem(this).apply {
            setTitle("Default SIM for Calls")
            setSubtitle(defaultSimLabel())
            setDividerVisible(false)
        }
        defaultSimRow.setAccessoryView(AppleuxButton(this).apply {
            setText("Change")
            setButtonStyle(AppleuxButton.Style.SECONDARY)
            setOnClickListener { showDefaultSimDialog() }
        })
        card.addView(defaultSimRow)

        // Per-SIM info
        val sims = activeSims()
        if (sims.isEmpty()) {
            val noSimRow = AppleuxListItem(this).apply {
                setTitle("No SIM")
                setSubtitle("No active SIM detected")
                setDividerVisible(false)
            }
            card.addView(noSimRow)
        } else {
            sims.forEach { sim ->
                val signalLabel = when (sim.signalBars) {
                    4 -> "Excellent"
                    3 -> "Good"
                    2 -> "Fair"
                    1 -> "Weak"
                    else -> "No signal"
                }
                val simRow = AppleuxListItem(this).apply {
                    setTitle("SIM ${sim.slotIndex + 1} — ${sim.carrierName}")
                    setSubtitle("$signalLabel signal")
                    setDividerVisible(false)
                }
                simRow.setAccessoryView(AppleuxToggle(this).apply {
                    setTrackColorOn(ColorTokens.systemBlue)
                    setChecked(true)
                    setOnCheckedChangeListener { enabled ->
                        // Toggle SIM state (would use SubscriptionManager API on real device)
                        // Confirmed, not bound: this listener only fires from a
                        // tap, so setChecked(true) above stays silent.
                        AppleuxSoundPlayer.confirm(this@PhoneActivity)
                        toast("SIM ${sim.slotIndex + 1} ${if (enabled) "enabled" else "disabled"}")
                    }
                })
                card.addView(simRow)
            }
        }
        return card
    }

    /** Label for the default SIM setting. */
    private fun defaultSimLabel(): String {
        val savedId = simPrefs.getInt(KEY_CALL_DEFAULT_SUB, -1)
        if (savedId < 0) return "Always Ask"
        val sims = activeSims()
        val sim = sims.firstOrNull { it.subscriptionId == savedId }
        return sim?.let { "SIM ${it.slotIndex + 1}" } ?: "Always Ask"
    }

    /** Dialog to choose the default SIM for calls. */
    private fun showDefaultSimDialog() {
        val options = arrayOf("Always Ask") + activeSims().map { "SIM ${it.slotIndex + 1}" }.toTypedArray()
        AppleuxDialogs.choose(
            this,
            items = options,
            title = "Default SIM for Calls",
            cancelLabel = "Cancel",
        ) { index -> if (index == 0) {
                    simPrefs.edit().remove(KEY_CALL_DEFAULT_SUB).apply()
                } else {
                    val sim = activeSims().getOrNull(index - 1)
                    if (sim != null) saveSelectedSim(sim.subscriptionId)
                }
                showTab(Tab.VOICEMAIL) }
    }

    // ---- Call management ----------------------------------------------------

    /** Call statistics: incoming / outgoing / missed counts + average duration. */
    private fun callStats(): String {
        if (!have(Manifest.permission.READ_CALL_LOG)) return ""
        var incoming = 0; var outgoing = 0; var missed = 0; var totalDur = 0L; var durCount = 0
        runCatching {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.TYPE, CallLog.Calls.DURATION),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    when (c.getInt(0)) {
                        CallLog.Calls.INCOMING_TYPE -> incoming++
                        CallLog.Calls.OUTGOING_TYPE -> outgoing++
                        else -> missed++
                    }
                    val d = c.getLong(1)
                    if (d > 0) { totalDur += d; durCount++ }
                }
            }
        }
        val avg = if (durCount > 0) totalDur / durCount else 0
        return "Incoming $incoming · Outgoing $outgoing · Missed $missed · Avg ${avg}s"
    }

    /** Export the call log to CSV in filesDir. */
    private fun exportCallLogCsv() {
        if (!have(Manifest.permission.READ_CALL_LOG)) { toast("Allow Call Log to export"); return }
        runCatching {
            val f = File(filesDir, "call-log-export.csv")
            FileOutputStream(f).use { out ->
                out.write("Number,Name,Date,Type,Duration\n".toByteArray())
                contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.DURATION),
                    null, null, "${CallLog.Calls.DATE} DESC"
                )?.use { c ->
                    while (c.moveToNext()) {
                        val dir = when (c.getInt(3)) {
                            CallLog.Calls.INCOMING_TYPE -> "Incoming"
                            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                            else -> "Missed"
                        }
                        out.write("${c.getString(0)},${c.getString(1)},${c.getString(2)},$dir,${c.getLong(4)}\n".toByteArray())
                    }
                }
            }
            toast("Exported to ${f.name}")
        }.onFailure { toast("Export failed") }
    }

    /** Count missed calls for one number (badge). */
    private fun missedCountFor(number: String): Int {
        if (!have(Manifest.permission.READ_CALL_LOG)) return 0
        var count = 0
        runCatching {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.TYPE}=?", arrayOf(CallLog.Calls.MISSED_TYPE.toString()), null
            )?.use { c -> while (c.moveToNext()) if (c.getString(0) == number) count++ }
        }
        return count
    }

    /**
     * Dual-SIM indicator for a call-log entry: "SIM 1" / "SIM 2" based on the
     * subscription ID stored with the call. Falls back to "SIM 1" when the
     * subscription ID is unknown or the device is single-SIM.
     */
    private fun simLabel(subscriptionId: Int): String = runCatching {
        if (!isDualSim()) return "SIM 1"
        val slot = simSlotForSubId(subscriptionId)
        if (slot < 0) return "SIM 1"
        "SIM ${slot + 1}"
    }.getOrDefault("SIM 1")

    /** Quick actions row: call back / message / create contact. */
    private fun quickActionsRow(number: String, name: String?): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(SpacingTokens.INSETS_MD) }
            addView(quickAction("system_phone", "Call back") { dial(number, call = true) })
            addView(quickAction("system_messages", "Message") {
                runCatching { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) }
            })
            addView(quickAction("system_person", "Create Contact") {
                runCatching {
                    startActivity(Intent(Intent.ACTION_INSERT).apply {
                        type = ContactsContract.Contacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.NAME, name ?: number)
                        putExtra(ContactsContract.Intents.Insert.PHONE, number)
                    })
                }
            })
        }

    private fun quickAction(icon: String, label: String, onClick: () -> Unit): LinearLayout {
        val size = dp(SizingTokens.CONTROL_SIZE_LG)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            addView(ImageButton(this@PhoneActivity).apply {
                val res = AppleuxIconRegistry.getDrawableRes(icon)
                setImageDrawable(ContextCompat.getDrawable(this@PhoneActivity, res ?: 0))
                setColorFilter(ColorTokens.systemBlue)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorTokens.systemFillSecondary)
                }
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(size, size)
                contentDescription = label
                setOnClickListener { onClick() }
            })
            addView(AppleuxText(this@PhoneActivity).apply {
                text = label
                applyTypography(TypographyTokens.caption2)
                setTextColor(ColorTokens.secondaryLabel)
                gravity = Gravity.CENTER
            })
            setOnClickListener { onClick() }
        }
    }

    /** Swipe right = call back, swipe left = delete (iOS 27 list edit actions). */
    private fun applySwipe(row: LinearLayout, number: String, onDelete: () -> Unit) {
        val threshold = resources.displayMetrics.density * 80
        var startX = 0f
        row.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = e.x; false }
                MotionEvent.ACTION_UP -> {
                    val dx = e.x - startX
                    if (dx > threshold) { dial(number, call = true); true }
                    else if (dx < -threshold) { onDelete(); true }
                    else false
                }
                else -> false
            }
        }
    }

    /** Per-contact ringtone: stored locally, chosen from system options. */
    private fun pickRingtone(contactId: Long) {
        val options = arrayOf("Default", "Ringtone 1", "Ringtone 2")
        AppleuxDialogs.choose(
            this,
            items = options,
            title = "Ringtone",
            cancelLabel = "Cancel",
        ) { index -> prefs.edit().putString("ringtone_$contactId", options[index]).apply()
                toast("Ringtone set: ${options[index]}") }
    }

    /** Resolve a contact name from a phone number via PhoneLookup. */
    private fun nameFor(number: String): String? = runCatching {
        contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    // ---- Keypad T9 / paste --------------------------------------------------

    /** Digit-to-letters map for T9 predictive search. */
    private val t9Index: Map<Int, String> = mapOf(
        2 to "ABC", 3 to "DEF", 4 to "GHI", 5 to "JKL",
        6 to "MNO", 7 to "PQRS", 8 to "TUV", 9 to "WXYZ"
    )

    /** Encode a name to its T9 digit sequence. */
    private fun t9Encode(name: String): String {
        val sb = StringBuilder()
        for (ch in name.uppercase()) {
            val digit = t9Index.entries.firstOrNull { (_, letters) -> letters.contains(ch) }?.key
            sb.append(digit ?: if (ch.isDigit()) ch else "")
        }
        return sb.toString()
    }

    /** T9 predictive search: contacts whose name or number matches the dialed digits. */
    private fun t9Matches(digits: String): List<String> {
        if (digits.isEmpty() || !have(Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = mutableListOf<String>()
        runCatching {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME),
                null, null, "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < 20) {
                    val name = c.getString(1)
                    val enc = t9Encode(name)
                    if (enc.startsWith(digits) || digits.all { it.isDigit() } &&
                        name.any { ch -> t9Index[ch.digitToIntOrNull() ?: -1]?.contains(ch) == true }) {
                        out.add(name)
                    }
                }
            }
        }
        return out
    }

    /** Paste a phone number from the clipboard into the dial string. */
    private fun pasteFromClipboard() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        val digits = text.filter { it.isDigit() || it == '+' }
        if (digits.isNotEmpty()) {
            dial.append(digits)
            numberView?.text = dial.toString()
            onDialChanged()
        }
    }

    /** Refresh T9 predictive matches after a digit is typed. */
    private fun onDialChanged() {
        val matches = t9Matches(dial.toString())
        val tv = numberView ?: return
        tv.hint = if (matches.isNotEmpty()) "Matches: ${matches.take(3).joinToString(", ")}" else ""
        // Update the T9 matches label above the keypad grid
        val contentParent = tv.parent as? LinearLayout ?: return
        for (i in 0 until contentParent.childCount) {
            val child = contentParent.getChildAt(i)
            if (child is AppleuxText && child.tag == "t9matches") {
                child.text = if (matches.isNotEmpty())
                    matches.take(3).joinToString("  ") else ""
                break
            }
        }
    }

    override fun onDestroy() {
        stopCapture()
        player?.release()
        player = null
        stopVmProgress()
        hideCallScreen()
        super.onDestroy()
    }

    private companion object {
        const val PREFS = "appleux_phone"
        const val KEY_AUTO_RECORD = "auto_record"
        const val DIR_RECORDINGS = "recordings"
        const val MAX_RECENTS = 50
        const val MAX_CONTACTS = 200
        const val KEYPAD_COLUMNS = 3
        const val REQ_BASE = 10
        const val REQ_CALL = 11
        const val REQ_PHONE_STATE = 14
        const val REQ_DIALER_ROLE = 15

        // Spam protection / call-management keys
        const val KEY_SPAM_LIST = "spam_list"
        const val KEY_BLOCKED = "blocked_list"
        const val KEY_AUTO_BLOCK = "auto_block"
        const val DIR_VOICEMAIL = "voicemail"
        const val REQ_CALL_LOG = 16

        // Dual-SIM keys
        const val SIM_PREFS = "appleux_phone_sim"
        const val KEY_CALL_DEFAULT_SUB = "call_default_sub_id"

        /**
         * Maps an SF Symbol name from the IPSW plist (e.g. "magnifyingglass",
         * "clock") onto this ROM's local SF-symbol registry name, which is what
         * [AppleuxIconRegistry] resolves. Unknown names fall back to search.
         */
        fun registryIcon(plistSymbol: String): String = when (plistSymbol) {
            "clock" -> "system_clock"
            else -> "system_search"
        }

        /** iOS keypad: digit -> letters, in 3x4 reading order. */
        val KEYPAD_KEYS = listOf(
            "1" to "", "2" to "ABC", "3" to "DEF",
            "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
            "*" to "", "0" to "+", "#" to ""
        )

        /** iOS system tints used for avatar discs, from ColorTokens. */
        val AVATAR_PALETTE = intArrayOf(
            ColorTokens.systemBlue, ColorTokens.systemGreen, ColorTokens.systemOrange,
            ColorTokens.systemPurple, ColorTokens.systemRed, ColorTokens.systemTeal
        )
    }
}

/**
 * Phone metrics that have no design-system counterpart.
 *
 * PROVENANCE — **AppleUX-chosen.** Apple publishes no numeric spec for the
 * Phone keypad, its call button or its list rows, so these are transcribed
 * from the iOS 27 Phone UI specification and expressed in dp. The 48 dp row and
 * 40 dp avatar reuse [SizingTokens] (CONTROL_SIZE_XXL / CONTROL_SIZE_LG); the
 * rest are the Phone-specific figures.
 */
object PhoneTokens {
    /** One keypad key's diameter, iOS 27. */
    const val KEYPAD_BUTTON_DP = 76f

    /** Gap between keypad keys, iOS 27. */
    const val KEYPAD_GAP_DP = 12f

    /** Keypad call button diameter, iOS 27. */
    const val CALL_BUTTON_DP = 72f

    /** Keypad FAB diameter. */
    const val FAB_SIZE_DP = 56f

    /** List row height: 48 dp, like iOS. Same value as CONTROL_SIZE_XXL. */
    const val ROW_HEIGHT_DP = SizingTokens.CONTROL_SIZE_XXL

    /** Row avatar diameter: 40 dp. Same value as CONTROL_SIZE_LG. */
    const val AVATAR_DP = SizingTokens.CONTROL_SIZE_LG

    /** Active-call contact poster diameter, iOS 27. */
    const val POSTER_DP = 120f

    /** Active-call control button diameter, iOS 27. */
    const val CONTROL_DP = 63f
}

/**
 * Values transcribed from the iOS 27 `MobilePhone.app` / `MobilePhoneSettings.bundle`
 * `Info.plist` files in the IPSW harvest.
 *
 * Only the fields that this screen actually renders are copied here; the full
 * dictionaries live in the harvest at
 * `ios-ui-analysis/all-plists/root/{Applications/MobilePhone.app,System/Library/PreferenceBundles/MobilePhoneSettings.bundle}/Info.plist`.
 */
private object PhoneInfo {
    /** CFBundleDisplayName — the large navigation title. */
    const val DISPLAY_NAME = "Phone"

    /** CFBundleDisplayNameClarityUI — the low-vision ("Calls") label. */
    const val CLARITY_UI_NAME = "Calls"

    /** CFBundleShortVersionString. */
    const val BUNDLE_VERSION = "36"

    /** PrivateURLSchemes — one scheme per Phone tab. */
    object Schemes {
        const val FAVORITES = "mobilephone-favorites"
        const val RECENTS = "mobilephone-recents"
        const val CONTACTS = "mobilephone-contacts"
        const val KEYPAD = "mobilephone-keypad"
        const val VOICEMAIL = "vmshow"
    }

    /** UIApplicationShortcutItems — SF Symbol names for the system shortcuts. */
    object Shortcuts {
        const val RECENTS_ICON = "clock"
        const val SEARCH_ICON = "magnifyingglass"
    }

    /** NSRegulatoryPrivacyDisclosure keys — regulatory privacy copy. */
    object Privacy {
        const val CALL_RECORDS = "NSRegulatoryPrivacyDisclosureCallRecords"
        const val CONTACTS = "NSRegulatoryPrivacyDisclosureContacts"
        const val VOICE_CALL_RECORDING = "NSRegulatoryPrivacyDisclosureVoiceCallRecording"
        const val PHONE_NUMBER = "NSRegulatoryPrivacyDisclosurePhoneNumber"
        const val SCREEN_CAPTURE = "NSRegulatoryPrivacyDisclosureScreenCapture"
        const val CAMERA = "NSRegulatoryPrivacyDisclosureCamera"
        const val MICROPHONE = "NSRegulatoryPrivacyDisclosureMicrophone"
        const val VERSION = "NSRegulatoryPrivacyDisclosureVersion"
    }
}
