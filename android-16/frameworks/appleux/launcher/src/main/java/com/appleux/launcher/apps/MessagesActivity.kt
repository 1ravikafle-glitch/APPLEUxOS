package com.appleux.launcher.apps

    // android.R.drawable -> vendored iOS vector (2026-10-17):
    //   com.appleux.design.R.drawable.sf_camera             -> il_ic_camera     iOS camera
    //   com.appleux.design.R.drawable.sf_pencil               -> sf_pencil        NEW: see sf_pencil.xml, created 2026-10-17
    //   com.appleux.design.R.drawable.il_ic_search             -> il_ic_search     iOS magnifying glass
    //   com.appleux.design.R.drawable.sf_next               -> il_ic_share      iOS send is a paper plane; share is the closest vendored glyph
import android.Manifest
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appleux.design.components.AppleuxDialogs
import com.appleux.design.components.AppleuxIconRegistry
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.components.AppleuxNavigationBar
import com.appleux.design.components.AppleuxText
import com.appleux.design.components.LayoutUtils
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.SizingTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.appleux.design.components.AppleuxToast
import com.appleux.launcher.R

/**
 * iOS 27 Messages: thread list + conversation with blue-sent / gray-received
 * bubbles, send via SmsManager. Requests the default-SMS role so sending
 * works without a chooser; without the role it still reads + drafts.
 *
 * Layout follows the iOS 27 HIG:
 *  - Large title "Messages" (34sp) with compose button (pencil, systemBlue)
 *  - Thread list: grouped rows, 72dp height, 56dp avatar + name + preview + time
 *  - Tab bar: Filters / Edit
 *  - Conversation: message bubbles (systemBlue sent, systemFillSecondary received),
 *    17sp body, 16dp corner radius, 12dp padding
 *  - Input bar: rounded pill, camera icon left, send button right
 */
class MessagesActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var threadObserver: ContentObserver? = null

    /**
     * Recently Deleted. Deleting a thread archives every message it contains
     * and only then removes it from the provider — the SMS provider has no
     * recycle bin, so without an archive a "restore" would have nothing to put
     * back and the row would be a lie.
     */
    private val trashPrefs: SharedPreferences by lazy {
        getSharedPreferences(TRASH_PREFS, Context.MODE_PRIVATE)
    }
    private val trash = mutableListOf<TrashedThread>()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        }
        setContentView(root)
        AppleuxAppChrome.apply(this, root)
        if (!have(Manifest.permission.READ_SMS)) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), 31)
        }
        maybeRequestSmsRole()
        // Purge before the first render so the section never offers a restore
        // for a thread that was already auto-deleted.
        trash.addAll(loadTrash())
        purgeExpiredTrash()
        showThreads()
    }

    private fun have(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun isDefaultSms(): Boolean = packageName ==
        Telephony.Sms.getDefaultSmsPackage(this)

    // ---- Dual-SIM infrastructure ---------------------------------------------

    /** One active SIM: subscriptionId, slot index, carrier name, signal. */
    private data class SimInfo(
        val subscriptionId: Int,
        val slotIndex: Int,
        val carrierName: String,
        val signalBars: Int
    )

    /** SIM preferences: stores the user's chosen default SIM for messages. */
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

    /** The SIM the user selected for sending messages. */
    private fun selectedSim(): SimInfo {
        val sims = activeSims()
        if (sims.isEmpty()) return SimInfo(-1, 0, "No SIM", 0)
        val savedId = simPrefs.getInt(KEY_SMS_DEFAULT_SUB, -1)
        return sims.firstOrNull { it.subscriptionId == savedId } ?: sims.first()
    }

    /** Persist the user's SIM choice. */
    private fun saveSelectedSim(subId: Int) {
        simPrefs.edit().putInt(KEY_SMS_DEFAULT_SUB, subId).apply()
    }

    /**
     * Pill-shaped SIM selector shown above the keyboard when dual-SIM is active.
     * Active SIM: systemBlue background, white text. Inactive: systemFillSecondary.
     */
    private fun simSelector(): LinearLayout {
        val sims = activeSims()
        val selected = selectedSim()
        val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
                setMargins(m, 0, m, m)
            }
            for (sim in sims) {
                addView(simPill(sim, sim.subscriptionId == selected.subscriptionId))
            }
        }
    }

    /** One pill in the SIM selector. */
    private fun simPill(sim: SimInfo, active: Boolean): TextView {
        val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_LG, this)
        val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this)
        return TextView(this).apply {
            text = "SIM ${sim.slotIndex + 1}"
            textSize = TypographyTokens.caption1.size
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (active) ColorTokens.white else ColorTokens.label)
            setPadding(padH, padV, padH, padV)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_ROUND, this@MessagesActivity)
                setColor(if (active) ColorTokens.systemBlue else ColorTokens.systemFillSecondary)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@MessagesActivity)
            }
            isClickable = true
            contentDescription = "SIM ${sim.slotIndex + 1} — ${sim.carrierName}"
            setOnClickListener {
                saveSelectedSim(sim.subscriptionId)
                // Re-render to update pill states
                val parent = this.parent as? LinearLayout
                parent?.let { p ->
                    for (i in 0 until p.childCount) {
                        val child = p.getChildAt(i)
                        if (child is TextView) {
                            val childSim = activeSims().getOrNull(i)
                            if (childSim != null) {
                                val isActive = childSim.subscriptionId == sim.subscriptionId
                                child.setTextColor(if (isActive) ColorTokens.white else ColorTokens.label)
                                (child.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                                    if (isActive) ColorTokens.systemBlue else ColorTokens.systemFillSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * SIM status bar: signal strength + carrier name for each SIM.
     * Shown at the top of the thread list when dual-SIM is active.
     */
    private fun simStatusBar(): LinearLayout {
        val sims = activeSims()
        val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            for (sim in sims) {
                addView(simStatusPill(sim))
            }
        }
    }

    /** One status pill: carrier name + signal bars. */
    private fun simStatusPill(sim: SimInfo): TextView {
        val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this)
        val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this)
        val signalLabel = when (sim.signalBars) {
            4 -> "●●●●"
            3 -> "●●●○"
            2 -> "●●○○"
            1 -> "●○○○"
            else -> "○○○○"
        }
        return TextView(this).apply {
            text = "${sim.carrierName} $signalLabel"
            textSize = TypographyTokens.caption2.size
            setTextColor(ColorTokens.secondaryLabel)
            setPadding(padH, padV, padH, padV)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_ROUND, this@MessagesActivity)
                setColor(ColorTokens.systemFillSecondary)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@MessagesActivity)
            }
        }
    }

    private fun maybeRequestSmsRole() {
        if (isDefaultSms()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java) ?: return
                if (rm.isRoleAvailable(RoleManager.ROLE_SMS) &&
                    !rm.isRoleHeld(RoleManager.ROLE_SMS)
                ) {
                    startActivityForResult(
                        rm.createRequestRoleIntent(RoleManager.ROLE_SMS), 32)
                }
            }
        }
    }

    private data class Thread(val id: Long, val snippet: String, val date: Long, val count: Int)

    private fun loadThreads(): List<Thread> {
        if (!have(Manifest.permission.READ_SMS)) return emptyList()
        val out = mutableListOf<Thread>()
        runCatching {
            contentResolver.query(
                Uri.parse("content://sms/conversations"),
                arrayOf(Telephony.Sms.Conversations._ID,
                    Telephony.Sms.Conversations.SNIPPET,
                    Telephony.Sms.Conversations.DATE,
                    Telephony.Sms.Conversations.MESSAGE_COUNT),
                null, null, "${Telephony.Sms.Conversations.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(Thread(c.getLong(0), c.getString(1).orEmpty(),
                        c.getLong(2), c.getInt(3)))
                }
            }
        }
        // Fallback when the conversations table is absent: group inbox.
        if (out.isEmpty()) {
            runCatching {
                contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null, null, "${Telephony.Sms.DATE} DESC"
                )?.use { c ->
                    val seen = linkedMapOf<String, Thread>()
                    while (c.moveToNext()) {
                        val addr = c.getString(0).orEmpty()
                        seen.getOrPut(addr) {
                            Thread(addr.hashCode().toLong(), c.getString(1).orEmpty(), c.getLong(2), 0)
                        }
                    }
                    out.addAll(seen.values)
                }
            }
        }
        return out
    }

    private fun threadAddress(threadId: Long): String {
        var addr = ""
        runCatching {
            contentResolver.query(
                Uri.parse("content://sms/conversations/$threadId"),
                arrayOf(Telephony.Sms.ADDRESS), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) addr = c.getString(0).orEmpty()
            }
        }
        return addr
    }

    // ---- Thread list -------------------------------------------------------

    private fun showThreads() {
        root.removeAllViews()

        // Large title "Messages" (34sp) + compose button (pencil, systemBlue)
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@MessagesActivity)
            setPadding(pad, LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity),
                pad, LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@MessagesActivity))
        }
        titleBar.addView(AppleuxText(this).apply {
            text = "Messages"
            applyTypography(TypographyTokens.largeTitle)
            setTextColor(ColorTokens.label)
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleBar.addView(composeButton())
        root.addView(titleBar)

        // SIM status bar (dual-SIM only)
        if (isDualSim()) {
            root.addView(simStatusBar())
        }

        // Thread list grouped in a card
        val threads = loadThreads()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (t in threads) {
            val label = threadAddress(t.id).ifEmpty { "Thread ${t.id}" }
            col.addView(threadRow(t, label))
        }
        if (threads.isEmpty() && trash.isEmpty()) {
            col.addView(emptyThreadListView())
        }
        appendTrashSection(col)
        val scroll = ScrollView(this)
        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Tab bar: Filters / Edit
        val tabs = AppleuxNavigationBar(this)
        tabs.addItem(AppleuxNavigationBar.NavigationItem(1, "Filters",
            R.drawable.il_ic_search))
        tabs.addItem(AppleuxNavigationBar.NavigationItem(2, "Edit",
            R.drawable.sf_pencil))
        tabs.setOnItemSelectedListener { /* filter/edit action */ }
        root.addView(tabs)

        watchThreads { showThreads() }
    }

    /** Compose button: top-right, systemBlue, pencil icon. */
    private fun composeButton(): ImageButton {
        val d = resources.displayMetrics.density
        return ImageButton(this).apply {
            val pencilRes = AppleuxIconRegistry.getDrawableRes("system_edit")
            if (pencilRes != null) {
                setImageDrawable(ContextCompat.getDrawable(this@MessagesActivity, pencilRes))
            } else {
                setImageResource(R.drawable.sf_pencil)
            }
            setColorFilter(ColorTokens.systemBlue)
            background = null
            val s = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_XL, this@MessagesActivity)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            isClickable = true
            contentDescription = "Compose"
            setOnClickListener { compose(null) }
        }
    }

    /**
     * Thread row: 72dp height, 56dp avatar + name + preview + time.
     * Grouped list style with hairline divider.
     */
    private fun threadRow(t: Thread, label: String): LinearLayout {
        val d = resources.displayMetrics.density
        val rowHeight = LayoutUtils.dpToPx(72f, this)
        val avatarSize = LayoutUtils.dpToPx(56f, this)
        val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this)
        val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = rowHeight
            setPadding(padH, padV, padH, padV)
            isClickable = true
            isFocusable = true

            // Avatar: 56dp circle with initial
            addView(TextView(this@MessagesActivity).apply {
                text = label.firstOrNull()?.uppercase() ?: "?"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(ColorTokens.white)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ColorTokens.systemBlue)
                }
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                    marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
                }
            })

            // Name + preview column
            addView(LinearLayout(this@MessagesActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                addView(AppleuxText(this@MessagesActivity).apply {
                    text = label
                    applyTypography(TypographyTokens.headline)
                    setTextColor(ColorTokens.label)
                    maxLines = 1
                })
                addView(AppleuxText(this@MessagesActivity).apply {
                    text = t.snippet.ifEmpty { "No messages" }
                    applyTypography(TypographyTokens.subheadline)
                    setTextColor(ColorTokens.secondaryLabel)
                    maxLines = 1
                })
            })

            // Time
            addView(AppleuxText(this@MessagesActivity).apply {
                text = formatTime(t.date)
                applyTypography(TypographyTokens.footnote)
                setTextColor(ColorTokens.secondaryLabel)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@MessagesActivity)
                }
            })

            // Divider
            setWillNotDraw(false)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(ColorTokens.systemBackground)
            }

            setOnClickListener { showConversation(t.id, label) }

            // Long-press is the thread's action sheet. The SMS provider has no
            // trash of its own, so this routes through our archive-then-delete
            // rather than deleting outright.
            setOnLongClickListener {
                confirmDeleteThread(t, label)
                true
            }
        }
    }

    /** "No Messages" — centred, secondary label, over a glyph. */
    private fun emptyThreadListView(): LinearLayout {
        val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(ImageView(this@MessagesActivity).apply {
                val res = AppleuxIconRegistry.getDrawableRes("system_messages")
                if (res != null) {
                    setImageDrawable(ContextCompat.getDrawable(this@MessagesActivity, res))
                } else {
                    setImageResource(R.drawable.il_ic_share)
                }
                setColorFilter(ColorTokens.secondaryLabel)
                layoutParams = LinearLayout.LayoutParams(
                    LayoutUtils.dpToPx(48f, this@MessagesActivity),
                    LayoutUtils.dpToPx(48f, this@MessagesActivity)
                ).apply {
                    bottomMargin = LayoutUtils.dpToPx(
                        SpacingTokens.INSETS_MD, this@MessagesActivity)
                }
            })
            addView(AppleuxText(this@MessagesActivity).apply {
                text = "No Messages"
                applyTypography(TypographyTokens.subheadline)
                setTextColor(ColorTokens.secondaryLabel)
            })
        }
    }

    // ---- Conversation ------------------------------------------------------

    private data class Msg(
        val body: String,
        val sent: Boolean,
        val date: Long,
        val simSlot: Int = -1
    )

    private fun loadMessages(threadId: Long, address: String): List<Msg> {
        val out = mutableListOf<Msg>()
        runCatching {
            val uri = Uri.parse("content://sms/conversations/$threadId")
            contentResolver.query(uri,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.TYPE, Telephony.Sms.DATE,
                    Telephony.Sms.SUBSCRIPTION_ID),
                null, null, "${Telephony.Sms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    out.add(Msg(c.getString(0).orEmpty(),
                        c.getInt(1) == Telephony.Sms.MESSAGE_TYPE_SENT, c.getLong(2),
                        c.getInt(3)))
                }
            }
            if (out.isEmpty() && address.isNotEmpty()) {
                for (box in listOf(Telephony.Sms.Inbox.CONTENT_URI, Telephony.Sms.Sent.CONTENT_URI)) {
                    contentResolver.query(box,
                        arrayOf(Telephony.Sms.BODY, Telephony.Sms.TYPE, Telephony.Sms.DATE,
                            Telephony.Sms.SUBSCRIPTION_ID),
                        "${Telephony.Sms.ADDRESS}=?", arrayOf(address),
                        "${Telephony.Sms.DATE} ASC")?.use { c ->
                        while (c.moveToNext()) {
                            out.add(Msg(c.getString(0).orEmpty(),
                                box == Telephony.Sms.Sent.CONTENT_URI, c.getLong(2),
                                c.getInt(3)))
                        }
                    }
                }
                out.sortBy { it.date }
            }
        }
        return out
    }

    private fun showConversation(threadId: Long, address: String) {
        root.removeAllViews()

        // Nav bar: back button + contact name
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
            setPadding(pad, pad, pad, pad)
        }
        navBar.addView(backButton { showThreads() })
        navBar.addView(AppleuxText(this).apply {
            text = address
            applyTypography(TypographyTokens.headline)
            setTextColor(ColorTokens.label)
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
            }
        })
        root.addView(navBar)

        // Message list
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        fun render() {
            list.removeAllViews()
            for (m in loadMessages(threadId, address)) {
                list.addView(bubble(m))
            }
            scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
        }
        render()

        // SIM selector (dual-SIM only) above the input bar
        if (isDualSim()) {
            root.addView(simSelector())
        }

        // Input bar: rounded pill, camera icon left, send button right
        root.addView(inputBar(address) {
            sendSms(address, it)
            render()
        })

        watchThreads { render() }
    }

    /** iOS bubble: blue right for sent, gray left for received. 17sp body, 16dp radius, 12dp padding. */
    private fun bubble(m: Msg): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val margin = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
                setMargins(margin, LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@MessagesActivity),
                    margin, LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@MessagesActivity))
            }
        }
        container.addView(AppleuxText(this).apply {
            text = m.body
            applyTypography(TypographyTokens.body)
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_LG, this@MessagesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
            setPadding(padH, padV, padH, padV)
            if (m.sent) {
                setTextColor(ColorTokens.white)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_CARD, this@MessagesActivity)
                    setColor(ColorTokens.systemBlue)
                }
                gravity = Gravity.END
            } else {
                setTextColor(ColorTokens.label)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_CARD, this@MessagesActivity)
                    setColor(ColorTokens.systemFillSecondary)
                }
                gravity = Gravity.START
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (m.sent) Gravity.END else Gravity.START
            }
        })
        // SIM indicator on messages (dual-SIM only)
        if (isDualSim() && m.simSlot >= 0) {
            container.addView(AppleuxText(this).apply {
                text = "SIM ${m.simSlot + 1}"
                applyTypography(TypographyTokens.caption2)
                setTextColor(ColorTokens.tertiaryLabel)
                gravity = if (m.sent) Gravity.END else Gravity.START
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    val m = LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@MessagesActivity)
                    setMargins(0, m, 0, 0)
                }
            })
        }
        return container
    }

    /** Input bar: rounded pill, camera icon left, send button right. */
    private fun inputBar(address: String, onSend: (String) -> Unit): LinearLayout {
        val d = resources.displayMetrics.density
        val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_ROUND, this@MessagesActivity)
                setColor(ColorTokens.systemFillSecondary)
            }

            // Camera icon (left)
            val cameraRes = AppleuxIconRegistry.getDrawableRes("system_camera")
            addView(ImageButton(this@MessagesActivity).apply {
                if (cameraRes != null) {
                    setImageDrawable(ContextCompat.getDrawable(this@MessagesActivity, cameraRes))
                } else {
                    setImageResource(R.drawable.il_ic_camera)
                }
                setColorFilter(ColorTokens.systemBlue)
                background = null
                val s = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_MD, this@MessagesActivity)
                layoutParams = LinearLayout.LayoutParams(s, s)
                isClickable = true
                contentDescription = "Camera"
            })

            // Text field (center)
            val field = EditText(this@MessagesActivity).apply {
                hint = "iMessage"
                setTextColor(ColorTokens.label)
                setHintTextColor(ColorTokens.tertiaryLabel)
                background = null
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
                    marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
                }
            }
            addView(field)

            // Send button (right)
            addView(ImageButton(this@MessagesActivity).apply {
                setImageResource(R.drawable.il_ic_share)
                setColorFilter(ColorTokens.systemBlue)
                background = null
                val s = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_MD, this@MessagesActivity)
                layoutParams = LinearLayout.LayoutParams(s, s)
                isClickable = true
                contentDescription = "Send"
                setOnClickListener {
                    val text = field.text.toString()
                    if (text.isNotBlank()) {
                        onSend(text)
                        field.setText("")
                    }
                }
            })
        }
    }

    // ---- Recently Deleted ----------------------------------------------------

    private fun loadTrash(): List<TrashedThread> {
        val raw = runCatching {
            JSONArray(trashPrefs.getString(KEY_TRASH, "[]")!!)
        }.getOrDefault(JSONArray())
        val out = mutableListOf<TrashedThread>()
        for (i in 0 until raw.length()) {
            raw.optJSONObject(i)?.let { out.add(TrashedThread.fromJson(it)) }
        }
        return out
    }

    private fun saveTrash() {
        // Newest first and bounded: written on the main thread on every swipe,
        // and each record carries its message archive.
        trash.sortByDescending { it.deletedAt }
        while (trash.size > MAX_TRASH_ENTRIES) trash.removeAt(trash.size - 1)
        trashPrefs.edit()
            .putString(KEY_TRASH, JSONArray(trash.map { it.toJson() }).toString())
            .apply()
    }

    private fun confirmDeleteThread(t: Thread, label: String) {
        AppleuxDialogs.alert(
            this,
            title = "Delete $label?",
            message = "This conversation moves to Recently Deleted, where it stays for " +
                    "30 days. Swipe right on it there to restore.",
            confirmLabel = "Delete",
            cancelLabel = "Cancel",
        ) { moveThreadToTrash(t, label) }
    }

    /**
     * Archive first, delete second.
     *
     * The order is the whole point. The SMS provider deletes rows outright, so
     * if the delete ran first a crash — or simply the absence of an archive —
     * would destroy the conversation with no way back. Persisting the archive
     * first means the worst case is a trashed thread the user can still see.
     */
    private fun moveThreadToTrash(t: Thread, label: String) {
        if (!have(PERM_WRITE_SMS)) {
            requestPermissions(arrayOf(PERM_WRITE_SMS), 34)
            return
        }
        trash.add(0, archiveThread(t.id, label, t.snippet))
        saveTrash()
        runCatching {
            contentResolver.delete(
                Uri.parse("content://sms/conversations/${t.id}"), null, null)
        }
        AppleuxToast.show(this, "Moved to Recently Deleted")
        showThreads()
    }

    /** Copies every message in a thread so the conversation can be rebuilt. */
    private fun archiveThread(threadId: Long, address: String, preview: String): TrashedThread {
        val messages = mutableListOf<ArchivedMessage>()
        runCatching {
            contentResolver.query(
                Uri.parse("content://sms/conversations/$threadId"),
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.TYPE,
                    Telephony.Sms.DATE, Telephony.Sms.SUBSCRIPTION_ID, Telephony.Sms.SUBJECT),
                null, null, "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                while (c.moveToNext() && messages.size < MAX_ARCHIVED_MESSAGES) {
                    messages.add(ArchivedMessage(
                        address = c.getString(0).orEmpty().ifEmpty { address },
                        body = c.getString(1).orEmpty(),
                        type = c.getInt(2),
                        date = c.getLong(3),
                        subscriptionId = c.getInt(4),
                        subject = c.getString(5).orEmpty()
                    ))
                }
            }
        }
        // The conversation row can exist with no reachable message rows. Fall
        // back to matching on the address so the thread is still rebuildable.
        if (messages.isEmpty() && address.isNotEmpty()) {
            runCatching {
                contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.TYPE,
                        Telephony.Sms.DATE, Telephony.Sms.SUBSCRIPTION_ID, Telephony.Sms.SUBJECT),
                    "${Telephony.Sms.ADDRESS}=?", arrayOf(address),
                    "${Telephony.Sms.DATE} ASC"
                )?.use { c ->
                    while (c.moveToNext() && messages.size < MAX_ARCHIVED_MESSAGES) {
                        messages.add(ArchivedMessage(
                            address = c.getString(0).orEmpty(),
                            body = c.getString(1).orEmpty(),
                            type = c.getInt(2),
                            date = c.getLong(3),
                            subscriptionId = c.getInt(4),
                            subject = c.getString(5).orEmpty()
                        ))
                    }
                }
            }
        }
        return TrashedThread(
            threadId = threadId.toString(),
            threadAddress = address,
            preview = preview,
            deletedAt = System.currentTimeMillis(),
            messages = messages
        )
    }

    /**
     * Re-inserts the archived messages. The provider rebuilds the conversation
     * row from them, so the thread reappears in the main list on its own —
     * there is no separate "thread" record to recreate.
     *
     * Messages are inserted oldest-first and in one batch per thread so they
     * land in a single conversation rather than splitting by send time.
     */
    private fun restoreTrashedThread(record: TrashedThread) {
        if (!have(PERM_WRITE_SMS)) {
            requestPermissions(arrayOf(PERM_WRITE_SMS), 34)
            return
        }
        if (record.messages.isEmpty()) {
            // Nothing survived to restore. Drop the record rather than leave a
            // row that "restores" into an empty conversation.
            trash.remove(record)
            saveTrash()
            AppleuxToast.show(this, "Nothing left to restore")
            showThreads()
            return
        }

        var restored = 0
        record.messages.sortedBy { it.date }.forEach { m ->
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, m.address.ifEmpty { record.threadAddress })
                put(Telephony.Sms.BODY, m.body)
                put(Telephony.Sms.TYPE, m.type)
                put(Telephony.Sms.DATE, m.date)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                if (m.subject.isNotEmpty()) put(Telephony.Sms.SUBJECT, m.subject)
                if (Build.VERSION.SDK_INT >= 22 && m.subscriptionId >= 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, m.subscriptionId)
                }
            }
            runCatching { contentResolver.insert(Telephony.Sms.CONTENT_URI, values) }
                .onSuccess { if (it != null) restored++ }
        }

        if (restored > 0) {
            trash.remove(record)
            saveTrash()
            AppleuxToast.show(this, "Restored $restored ${if (restored == 1) "message" else "messages"}")
        } else {
            // Writing to content://sms needs the default-SMS role. Without it
            // the insert is refused, and saying "restored" would be a lie.
            AppleuxToast.show(this, "Cannot restore — set AppleUX Messages as your SMS app")
        }
        showThreads()
    }

    /** Irreversible: the archive is the only copy, and this discards it. */
    private fun deleteTrashedThreadPermanently(record: TrashedThread) {
        trash.remove(record)
        saveTrash()
        AppleuxToast.show(this, "Deleted permanently")
        showThreads()
    }

    /** 30-day auto-purge, run at start-up before the section is rendered. */
    private fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_DAYS * MILLIS_PER_DAY
        val kept = trash.filter { it.deletedAt > cutoff }
        if (kept.size == trash.size) return
        trash.clear()
        trash.addAll(kept)
        saveTrash()
    }

    private fun formatDeletedDate(deletedAt: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(deletedAt))

    /**
     * The Recently Deleted section, appended below the live threads.
     *
     * Hidden when the trash is empty, as iOS hides it. The rows are a
     * RecyclerView only because ItemTouchHelper needs real ViewHolders for the
     * swipe; nested scrolling is off so it measures as one block inside the
     * surrounding ScrollView.
     */
    private fun appendTrashSection(col: LinearLayout) {
        if (trash.isEmpty()) return

        col.addView(AppleuxText(this).apply {
            text = "Recently Deleted (${trash.size})"
            applyTypography(TypographyTokens.subheadlineEmphasized)
            setTextColor(ColorTokens.tertiaryLabel)
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@MessagesActivity)
            setPadding(
                padH,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity),
                padH,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@MessagesActivity)
            )
        })

        val trashAdapter = TrashThreadAdapter().apply { submit(trash) }
        val list = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(this@MessagesActivity)
            adapter = trashAdapter
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
        }
        attachTrashSwipe(list, trashAdapter)
        col.addView(list)
    }

    /**
     * Swipe actions, iOS semantics: right restores (systemGreen), left deletes
     * permanently (systemRed). The row takes the action colour as the finger
     * moves, so it is readable before the user commits.
     */
    private fun attachTrashSwipe(recycler: RecyclerView, trashAdapter: TrashThreadAdapter) {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val position = vh.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val record = trashAdapter.itemAt(position) ?: return
                if (direction == ItemTouchHelper.RIGHT) {
                    restoreTrashedThread(record)
                } else {
                    deleteTrashedThreadPermanently(record)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val row = vh.itemView
                when {
                    dX > 0f -> row.setBackgroundColor(ColorTokens.systemGreen)
                    dX < 0f -> row.setBackgroundColor(ColorTokens.systemRed)
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        })
        helper.attachToRecyclerView(recycler)
    }

    /**
     * Recently Deleted rows: avatar + address + preview + "Deleted <date>",
     * mirroring the live thread row so the section reads as part of the list.
     */
    private inner class TrashThreadAdapter :
        RecyclerView.Adapter<TrashThreadAdapter.Holder>() {

        private var items: List<TrashedThread> = emptyList()

        fun submit(list: List<TrashedThread>) {
            items = list
            notifyDataSetChanged()
        }

        fun itemAt(position: Int): TrashedThread? = items.getOrNull(position)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@MessagesActivity)
            val avatarSize = LayoutUtils.dpToPx(48f, this@MessagesActivity)

            val row = LinearLayout(this@MessagesActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(
                    padH,
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity),
                    padH,
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
                )
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(ColorTokens.systemBackground)
                }
            }

            val av = TextView(this@MessagesActivity).apply {
                textSize = TypographyTokens.headline.size
                gravity = Gravity.CENTER
                setTextColor(ColorTokens.white)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ColorTokens.systemBlue)
                }
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                    marginEnd = LayoutUtils.dpToPx(
                        SpacingTokens.INSETS_MD, this@MessagesActivity)
                }
            }

            val textColumn = LinearLayout(this@MessagesActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val addressView = AppleuxText(this@MessagesActivity).apply {
                applyTypography(TypographyTokens.headline)
                setTextColor(ColorTokens.label)
                maxLines = 1
            }
            val previewView = AppleuxText(this@MessagesActivity).apply {
                applyTypography(TypographyTokens.subheadline)
                setTextColor(ColorTokens.secondaryLabel)
                maxLines = 1
            }
            val deletedView = AppleuxText(this@MessagesActivity).apply {
                applyTypography(TypographyTokens.footnote)
                setTextColor(ColorTokens.tertiaryLabel)
                maxLines = 1
            }
            textColumn.addView(addressView)
            textColumn.addView(previewView)
            textColumn.addView(deletedView)

            row.addView(av)
            row.addView(textColumn)
            return Holder(row, av, addressView, previewView, deletedView)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val label = item.threadAddress.ifEmpty { "Thread ${item.threadId}" }
            holder.avatar.text = label.firstOrNull()?.uppercase() ?: "?"
            holder.address.text = label
            holder.preview.text = item.preview.ifEmpty { "No messages" }
            holder.deleted.text = "Deleted ${formatDeletedDate(item.deletedAt)}"
            // Reset the swipe tint so a recycled row is not left green/red.
            holder.itemView.setBackgroundColor(ColorTokens.systemBackground)
        }

        override fun getItemCount(): Int = items.size

        inner class Holder(
            itemView: View,
            val avatar: TextView,
            val address: AppleuxText,
            val preview: AppleuxText,
            val deleted: AppleuxText
        ) : RecyclerView.ViewHolder(itemView)
    }

    // ---- Compose -----------------------------------------------------------

    private fun compose(prefill: String?) {
        root.removeAllViews()
        root.addView(backButton { showThreads() })

        // Large title "New Message"
        root.addView(AppleuxText(this).apply {
            text = "New Message"
            applyTypography(TypographyTokens.largeTitle)
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@MessagesActivity)
            setPadding(pad, LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity),
                pad, LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@MessagesActivity))
        })

        val to = EditText(this).apply {
            hint = "To:"
            isSingleLine = true
            setTextColor(ColorTokens.label)
            setHintTextColor(ColorTokens.tertiaryLabel)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_CARD, this@MessagesActivity)
                setColor(ColorTokens.systemFillSecondary)
            }
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_LG, this@MessagesActivity)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
                setMargins(m, m, m, m)
            }
        }
        val field = EditText(this).apply {
            hint = "Message"
            setText(prefill.orEmpty())
            setTextColor(ColorTokens.label)
            setHintTextColor(ColorTokens.tertiaryLabel)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_CARD, this@MessagesActivity)
                setColor(ColorTokens.systemFillSecondary)
            }
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_LG, this@MessagesActivity)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
                setMargins(m, m, m, m)
            }
        }
        root.addView(to)
        root.addView(field)

        // SIM selector (dual-SIM only) above the send button
        if (isDualSim()) {
            root.addView(simSelector())
        }

        root.addView(Button(this).apply {
            text = "Send"
            textSize = TypographyTokens.bodyEmphasized.size
            setTextColor(ColorTokens.systemBlue)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_CARD, this@MessagesActivity)
                setColor(ColorTokens.systemFillSecondary)
            }
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_LG, this@MessagesActivity)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@MessagesActivity)
                setMargins(m, m, m, m)
            }
            setOnClickListener {
                if (to.text.isNotBlank() && field.text.isNotBlank()) {
                    sendSms(to.text.toString(), field.text.toString())
                    showThreads()
                }
            }
        })
    }

    // ---- Helpers -----------------------------------------------------------

    private fun sendSms(dest: String, text: String) {
        if (!have(Manifest.permission.SEND_SMS)) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 33)
            AppleuxToast.show(this, "Allow SMS, then send again")
            return
        }
        val sim = selectedSim()
        if (sim.subscriptionId < 0) {
            AppleuxToast.show(this, "No SIM available")
            return
        }
        runCatching {
            val sm = if (Build.VERSION.SDK_INT >= 22) {
                SmsManager.getSmsManagerForSubscriptionId(sim.subscriptionId)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            val parts = sm?.divideMessage(text) ?: listOf(text)
            for (part in parts) {
                sm?.sendTextMessage(dest, null, part, null, null)
            }
            AppleuxToast.show(this, "Sent via SIM ${sim.slotIndex + 1}")
        }.onFailure {
            AppleuxToast.show(this, "Send failed")
        }
    }

    private fun watchThreads(onChange: () -> Unit) {
        threadObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        threadObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                runOnUiThread { onChange() }
            }
        }
        runCatching {
            contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI, true, threadObserver!!)
        }
    }

    private fun formatTime(dateMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - dateMs
        return when {
            diff < 60_000 -> "now"
            diff < 3_600_000 -> "${diff / 60_000}m"
            diff < 86_400_000 -> "${diff / 3_600_000}h"
            diff < 604_800_000 -> "${diff / 86_400_000}d"
            else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(dateMs))
        }
    }

    private fun backButton(back: () -> Unit) = Button(this, null,
        android.R.attr.borderlessButtonStyle).apply {
        text = "‹ Back"
        textSize = TypographyTokens.body.size
        setTextColor(ColorTokens.systemBlue)
        gravity = Gravity.START
        setOnClickListener { back() }
    }

    override fun onDestroy() {
        threadObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        super.onDestroy()
    }

    private companion object {
        const val SIM_PREFS = "appleux_messages_sim"
        const val KEY_SMS_DEFAULT_SUB = "sms_default_sub_id"

        /**
         * Writing to the SMS provider needs WRITE_SMS. There is no public
         * `Manifest.permission.WRITE_SMS` constant — the real permission
         * exists but is absent from the public SDK stub — so the name is
         * spelled out here and declared in AndroidManifest.xml.
         */
        const val PERM_WRITE_SMS = "android.permission.WRITE_SMS"

        /** Recently Deleted: SharedPreferences file `messages_trash`, one JSON
         *  array of [TrashedThread] under [KEY_TRASH]. */
        const val TRASH_PREFS = "messages_trash"
        const val KEY_TRASH = "trash"
        const val TRASH_RETENTION_DAYS = 30L
        const val MILLIS_PER_DAY = 86_400_000L
        const val MAX_TRASH_ENTRIES = 50

        /**
         * Messages archived per thread. Each record lives in one
         * SharedPreferences string written on the main thread, so the archive
         * is capped rather than allowed to grow with conversation length.
         */
        const val MAX_ARCHIVED_MESSAGES = 500
    }
}

/** One archived message, enough to re-insert it verbatim. */
private data class ArchivedMessage(
    val address: String,
    val body: String,
    val type: Int,
    val date: Long,
    val subscriptionId: Int,
    val subject: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("address", address)
        put("body", body)
        put("type", type)
        put("date", date)
        put("subscriptionId", subscriptionId)
        put("subject", subject)
    }

    companion object {
        fun fromJson(o: JSONObject) = ArchivedMessage(
            address = o.optString("address"),
            body = o.optString("body"),
            type = o.optInt("type"),
            date = o.optLong("date"),
            subscriptionId = o.optInt("subscriptionId", -1),
            subject = o.optString("subject")
        )
    }
}

/**
 * One Recently Deleted conversation.
 *
 * `{threadId, threadAddress, preview, deletedAt}` identifies the row in the
 * album; `messages` is the archive that makes **restore real**. The SMS
 * provider deletes rows permanently and exposes no undelete, so without the
 * archived bodies this record could only ever be a label for nothing.
 */
private data class TrashedThread(
    val threadId: String,
    val threadAddress: String,
    val preview: String,
    val deletedAt: Long,
    val messages: List<ArchivedMessage> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("threadId", threadId)
        put("threadAddress", threadAddress)
        put("preview", preview)
        put("deletedAt", deletedAt)
        put("messages", JSONArray(messages.map { it.toJson() }))
    }

    companion object {
        fun fromJson(o: JSONObject): TrashedThread {
            val raw = o.optJSONArray("messages")
            val messages = mutableListOf<ArchivedMessage>()
            if (raw != null) {
                for (i in 0 until raw.length()) {
                    raw.optJSONObject(i)?.let { messages.add(ArchivedMessage.fromJson(it)) }
                }
            }
            return TrashedThread(
                threadId = o.optString("threadId"),
                threadAddress = o.optString("threadAddress"),
                preview = o.optString("preview"),
                deletedAt = o.optLong("deletedAt"),
                messages = messages
            )
        }
    }
}
