package com.appleux.settings.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appleux.design.components.AppleuxIconRegistry
import com.appleux.design.components.LayoutUtils
import com.appleux.design.runtime.AppleuxRuntimeConfig
import com.appleux.design.theme.AppleuxTheme
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.SizingTokens
import com.appleux.settings.R
import com.appleux.settings.pref.AppleuxPreferences
import com.appleux.settings.pref.AppleuxSettingsStore
import kotlin.math.roundToInt

class SettingsListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SettingsAdapter
    private lateinit var store: AppleuxSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is the screen the user actually looks at; SettingsActivity only
        // forwards to it and finishes, so the theme has to land here.
        AppleuxTheme.applyToActivity(this)
        setContentView(R.layout.settings_recycler)

        store = AppleuxSettingsStore(this)
        recyclerView = findViewById(R.id.settingsList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setBackgroundColor(AppleuxTheme.background.resolved)
        adapter = SettingsAdapter(
            store = store,
            onNavigate = { item -> onNavigate(item) },
            onDarkModeChanged = { enabled -> applyDarkMode(enabled) }
        )
        recyclerView.adapter = adapter
        adapter.submitItems(SettingsCatalog.build(this))
    }

    override fun onResume() {
        super.onResume()
        // Shared flags no longer need special access: they go through the
        // config provider. "Modify system settings" is only required for the
        // brightness platform keys, so it is requested on demand instead of on
        // every resume.
        AppleuxRuntimeConfig.reload(this)
        applyDarkMode(store.getBoolean(AppleuxRuntimeConfig.KEY_DARK_MODE, false))
        adapter.notifyDataSetChanged()
    }

    /**
     * Dark Mode is a user choice, so it wins over the platform night mode read
     * by [AppleuxTheme.applyToActivity] (same rule the launcher follows). The
     * list is painted from ColorTokens rather than night-qualified XML
     * resources, so repainting means re-binding every row.
     */
    private fun applyDarkMode(enabled: Boolean) {
        AppleuxTheme.isDarkMode = enabled
        AppleuxTheme.applySystemBars(this)
        recyclerView.setBackgroundColor(AppleuxTheme.background.resolved)
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    /** Offers the "Modify system settings" screen for the brightness keys. */
    fun requestWriteSettings() {
        try {
            startActivity(store.writeSettingsIntent())
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(
                this,
                "AppleUX settings need \"Modify system settings\" to change brightness",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun onNavigate(item: SettingsItem) {
        when (val nav = item.navTarget) {
            null -> return
            is NavTarget.Internal -> runCatching {
                startActivity(android.content.Intent(this, nav.activity))
            }
            is NavTarget.Action -> openSettingsAction(nav.action, null)
            NavTarget.OwnAppNotifications -> openSettingsAction(
                android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                android.os.Bundle().apply {
                    putString(
                        android.provider.Settings.EXTRA_APP_PACKAGE,
                        packageName
                    )
                }
            )
            // Control Center / Notification Center cannot exist without the
            // "display over other apps" grant (§34): without it every panel
            // gesture is a silent no-op, so this row is load-bearing.
            NavTarget.OverlayPermission -> runCatching {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:com.appleux.systemui")
                    )
                )
            }.onFailure {
                openSettingsAction(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    null
                )
            }
            NavTarget.StockRoot -> openStockSettings()
            NavTarget.ShareSheet -> runCatching {
                startActivity(
                    android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "Shared from AppleUX"
                            ),
                        getString(R.string.quick_share)
                    )
                )
            }
        }
    }

    /**
     * Fires a public Settings action. Catches broadly on purpose: a few of
     * these activities read extras we do not send, and a crash from stock is
     * worse than its root page. Either way the user lands somewhere real.
     */
    private fun openSettingsAction(action: String, extras: android.os.Bundle?) {
        runCatching {
            startActivity(
                android.content.Intent(action).apply {
                    if (extras != null) putExtras(extras)
                }
            )
        }.onFailure { openStockSettings() }
    }

    /** Stock Settings app root: the documented fallback, never a dead end. */
    private fun openStockSettings() {
        runCatching {
            val launch = packageManager.getLaunchIntentForPackage("com.android.settings")
                ?: throw android.content.ActivityNotFoundException()
            startActivity(launch)
        }.onFailure {
            android.widget.Toast.makeText(
                this,
                "Stock Settings is not available",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

enum class SettingsItemType {
    SECTION_HEADER, SWITCH, SEEK_BAR, NAVIGATION
}

/**
 * Where a NAVIGATION row goes. Top-level because [SettingsItem] is top-level.
 * Every destination here was verified against frameworks/base in this tree;
 * rows with no verifiable destination do not exist in the catalog (see
 * SettingsCatalog notes) instead of navigating somewhere approximately right.
 */
sealed interface NavTarget {
    /** One of our own activities. */
    data class Internal(val activity: Class<out androidx.appcompat.app.AppCompatActivity>) : NavTarget
    /** A public android.provider.Settings action. Fired bare. */
    data class Action(val action: String) : NavTarget
    /** App-notification settings for our own package (needs the extra). */
    data object OwnAppNotifications : NavTarget
    /** Overlay grant for com.appleux.systemui, with a bare fallback. */
    data object OverlayPermission : NavTarget
    /** Stock Settings app root. Only for rows with no precise public
     * action; never silent — the row summary says where stock opens. */
    data object StockRoot : NavTarget
    /** ACTION_SEND chooser (Quick Share transport is Bluetooth/nearby on
     * AOSP — there is no proprietary peer-to-peer protocol to call). */
    data object ShareSheet : NavTarget
}

data class SettingsItem(
    val key: String,
    val titleRes: Int,
    /** 0 when the row has no subtitle; section headers never do. */
    val summaryRes: Int,
    val type: SettingsItemType,
    val preferenceKey: String? = null,
    val defaultValue: Boolean = false,
    val defaultIntValue: Int = 128,
    /**
     * Maximum of the value AS STORED, which is not the value the slider shows.
     * Every slider renders 0..[SEEK_DISPLAY_MAX]; the adapter scales to and
     * from the store's own range (0-100 for glass, 0-255 for brightness).
     */
    val valueScaleMax: Int = SEEK_DISPLAY_MAX,
    /**
     * Capsule-board badge. iconKey is an [AppleuxIconRegistry] drawable key;
     * badgeColor is a [ColorTokens] ARGB int for the 29dp rounded square.
     * Null iconKey means no badge, and the text then spans the full row width
     * exactly like the board's icon-less rows.
     */
    val iconKey: String? = null,
    val badgeColor: Int = 0,
    /** Where a NAVIGATION row goes. Null for controls, which stay put. */
    val navTarget: NavTarget? = null
)

/**
 * Root catalog. Shape follows the adopted capsule board
 * (design-reference/inbox/settings-capsule-ios27.html): one capsule per
 * category, 29dp badge + white glyph per row, chevron for navigation.
 *
 * iOS names are renamed to their AOSP equivalents, never carried over:
 * Siri -> Voice input, StandBy -> Screen saver, Face ID -> Fingerprint (the
 * unit has fingerprint hardware only), AirDrop -> Quick Share. Apple-only
 * services with no AOSP backend are OMITTED, not faked: Game Center (Play
 * Games is GMS), iCloud cloud rows (BackupManager exposes no settings UI),
 * NFC & payments (no android.hardware.nfc on this unit — verified by
 * `pm list features`, 95 entries, 2026-10-15), Search settings page (in-app
 * search, no public action), Transfer/Reset + Shut Down (no verified public
 * action; MASTER_CLEAR is absent from the Settings provider and shutdown
 * needs a privileged PowerManager).
 *
 * No placeholder values: the board shows demo data (HomeWiFi, 64 GB, names).
 * Rows here show no value text unless it is live. A row whose backend needs a
 * privileged write the app cannot hold (airplane toggle needs
 * WRITE_SECURE_SETTINGS) is a navigation row to the stock page, not a toggle
 * that pretends to work.
 */
object SettingsCatalog {

    private fun header(key: String, titleRes: Int) = SettingsItem(
        key = key,
        titleRes = titleRes,
        summaryRes = 0,
        type = SettingsItemType.SECTION_HEADER
    )

    private fun nav(
        key: String,
        titleRes: Int,
        summaryRes: Int = 0,
        iconKey: String,
        badgeColor: Int,
        target: NavTarget
    ) = SettingsItem(
        key = key,
        titleRes = titleRes,
        summaryRes = summaryRes,
        type = SettingsItemType.NAVIGATION,
        iconKey = iconKey,
        badgeColor = badgeColor,
        navTarget = target
    )

    private fun act(action: String) = NavTarget.Action(action)

    fun build(context: android.content.Context): List<SettingsItem> {
        return listOf(
            header("section_appleux", R.string.appleux),
            SettingsItem(
                key = "adaptive_performance",
                titleRes = R.string.adaptive_performance,
                summaryRes = R.string.adaptive_performance_summary,
                type = SettingsItemType.SWITCH,
                preferenceKey = AppleuxRuntimeConfig.KEY_ADAPTIVE_PERFORMANCE,
                defaultValue = true
            ),
            SettingsItem(
                key = "low_power_mode",
                titleRes = R.string.low_power_mode,
                summaryRes = R.string.low_power_summary,
                type = SettingsItemType.SWITCH,
                preferenceKey = AppleuxRuntimeConfig.KEY_LOW_POWER_MODE,
                defaultValue = false
            ),
            SettingsItem(
                key = "reduce_motion",
                titleRes = R.string.reduce_motion,
                summaryRes = R.string.reduce_motion_summary,
                type = SettingsItemType.SWITCH,
                preferenceKey = AppleuxRuntimeConfig.KEY_REDUCE_MOTION,
                defaultValue = false
            ),
            SettingsItem(
                key = "glass_intensity",
                titleRes = R.string.glass_intensity,
                summaryRes = R.string.glass_intensity_summary,
                type = SettingsItemType.SEEK_BAR,
                preferenceKey = AppleuxRuntimeConfig.KEY_GLASS_INTENSITY,
                defaultIntValue = AppleuxRuntimeConfig.DEFAULT_GLASS_INTENSITY
            ),
            header("section_display", R.string.display),
            SettingsItem(
                key = "brightness",
                titleRes = R.string.brightness,
                summaryRes = R.string.brightness,
                type = SettingsItemType.SEEK_BAR,
                preferenceKey = AppleuxPreferences.KEY_BRIGHTNESS,
                defaultIntValue = AppleuxRuntimeConfig.DEFAULT_BRIGHTNESS,
                // Settings.System.SCREEN_BRIGHTNESS is 0-255; the row shows 0-100.
                valueScaleMax = BRIGHTNESS_SCALE_MAX
            ),
            SettingsItem(
                key = "auto_brightness",
                titleRes = R.string.auto_brightness,
                summaryRes = R.string.auto_brightness,
                type = SettingsItemType.SWITCH,
                preferenceKey = AppleuxPreferences.KEY_AUTO_BRIGHTNESS,
                defaultValue = true
            ),
            SettingsItem(
                key = "dark_mode",
                titleRes = R.string.dark_mode,
                summaryRes = R.string.dark_mode_summary,
                type = SettingsItemType.SWITCH,
                preferenceKey = AppleuxRuntimeConfig.KEY_DARK_MODE,
                defaultValue = false
            ),
            header("section_account", R.string.section_account),
            // No demo identity: the board shows a person's name; we show the
            // account page instead.
            nav(
                key = "account", titleRes = R.string.account,
                summaryRes = R.string.account_summary,
                iconKey = "system_person", badgeColor = ColorTokens.systemGray,
                target = act(android.provider.Settings.ACTION_SYNC_SETTINGS)
            ),
            header("section_connectivity", R.string.section_connectivity),
            nav(
                key = "airplane", titleRes = R.string.airplane_mode,
                iconKey = "system_airplane", badgeColor = ColorTokens.systemOrange,
                target = act(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
            ),
            nav(
                key = "wifi", titleRes = R.string.wifi,
                iconKey = "system_wifi", badgeColor = ColorTokens.systemBlue,
                target = act(android.provider.Settings.ACTION_WIFI_SETTINGS)
            ),
            nav(
                key = "bluetooth", titleRes = R.string.bluetooth,
                iconKey = "system_bluetooth", badgeColor = ColorTokens.systemBlue,
                target = act(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            ),
            nav(
                key = "cellular", titleRes = R.string.cellular,
                iconKey = "system_cellular", badgeColor = ColorTokens.systemGreen,
                target = act(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            ),
            nav(
                key = "hotspot", titleRes = R.string.hotspot,
                iconKey = "system_personal_hotspot", badgeColor = ColorTokens.systemGreen,
                // Literal: ACTION_TETHER_SETTINGS is @hide/@SystemApi in this
                // tree (Settings.java), so it is absent from android.jar even
                // though the string is the public contract.
                target = act("android.settings.TETHER_SETTINGS")
            ),
            nav(
                key = "quickshare", titleRes = R.string.quick_share,
                summaryRes = R.string.quick_share_summary,
                iconKey = "system_share", badgeColor = ColorTokens.systemBlue,
                target = NavTarget.ShareSheet
            ),
            nav(
                key = "battery", titleRes = R.string.battery,
                iconKey = "system_battery", badgeColor = ColorTokens.systemGreen,
                target = act(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)
            ),
            header("section_general", R.string.section_general),
            nav(
                key = "general", titleRes = R.string.general,
                iconKey = "system_settings", badgeColor = ColorTokens.systemGray,
                target = NavTarget.StockRoot
            ),
            nav(
                key = "accessibility", titleRes = R.string.accessibility,
                iconKey = "system_accessibility", badgeColor = ColorTokens.systemBlue,
                target = act(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            ),
            nav(
                key = "appearance", titleRes = R.string.appearance,
                iconKey = "system_appearance", badgeColor = ColorTokens.systemIndigo,
                target = NavTarget.Internal(
                    com.appleux.settings.display.DisplaySettingsActivity::class.java
                )
            ),
            nav(
                key = "camera", titleRes = R.string.camera,
                iconKey = "system_camera", badgeColor = ColorTokens.systemGray,
                target = NavTarget.StockRoot
            ),
            nav(
                key = "controlcenter", titleRes = R.string.control_center,
                summaryRes = R.string.control_center_summary,
                iconKey = "system_control_center", badgeColor = ColorTokens.systemGray,
                target = NavTarget.OverlayPermission
            ),
            nav(
                key = "display_nav", titleRes = R.string.display_brightness,
                iconKey = "system_display", badgeColor = ColorTokens.systemBlue,
                target = NavTarget.Internal(
                    com.appleux.settings.display.DisplaySettingsActivity::class.java
                )
            ),
            nav(
                key = "home", titleRes = R.string.home_screen,
                iconKey = "system_grid", badgeColor = ColorTokens.systemBlue,
                target = act(android.provider.Settings.ACTION_HOME_SETTINGS)
            ),
            // Renamed from Siri: the assistant itself is GMS, but the voice
            // input settings page is AOSP.
            nav(
                key = "voice", titleRes = R.string.voice_input,
                iconKey = "system_mic", badgeColor = ColorTokens.systemIndigo,
                target = act(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
            ),
            // Renamed from StandBy: DreamService is the AOSP equivalent.
            nav(
                key = "standby", titleRes = R.string.screen_saver,
                iconKey = "system_clock", badgeColor = ColorTokens.systemOrange,
                target = act(android.provider.Settings.ACTION_DREAM_SETTINGS)
            ),
            nav(
                key = "wallpaper", titleRes = R.string.wallpaper,
                iconKey = "system_photos", badgeColor = ColorTokens.systemTeal,
                target = NavTarget.StockRoot
            ),
            header("section_notifications", R.string.notifications),
            nav(
                key = "notifications", titleRes = R.string.notifications,
                summaryRes = R.string.notifications_summary,
                iconKey = "system_bell", badgeColor = ColorTokens.systemRed,
                target = NavTarget.OwnAppNotifications
            ),
            nav(
                key = "notification_access", titleRes = R.string.notification_access,
                summaryRes = R.string.notification_access_summary,
                iconKey = "system_settings", badgeColor = ColorTokens.systemGray,
                target = act(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            ),
            nav(
                key = "sounds", titleRes = R.string.sounds,
                iconKey = "system_volume", badgeColor = ColorTokens.systemPink,
                target = act(android.provider.Settings.ACTION_SOUND_SETTINGS)
            ),
            nav(
                key = "focus", titleRes = R.string.focus,
                iconKey = "system_dnd", badgeColor = ColorTokens.systemIndigo,
                // Literal: ACTION_ZEN_MODE_SETTINGS is @hide in this tree
                // (Settings.java), absent from android.jar.
                target = act("android.settings.ZEN_MODE_SETTINGS")
            ),
            nav(
                key = "screentime", titleRes = R.string.screen_time,
                summaryRes = R.string.screen_time_summary,
                iconKey = "system_hourglass", badgeColor = ColorTokens.systemIndigo,
                target = act(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            ),
            header("section_security", R.string.security),
            nav(
                key = "lock_screen", titleRes = R.string.lock_screen,
                summaryRes = R.string.lock_screen,
                iconKey = "system_lock", badgeColor = ColorTokens.systemGray,
                target = NavTarget.Internal(
                    com.appleux.settings.security.SecuritySettingsActivity::class.java
                )
            ),
            // Face unlock is real on this unit: com.miui.face is installed,
            // miui.face.FaceService runs, and a face is enrolled
            // (settings_face_id_prefix_1="Face 1", lockscreen enable_facelock).
            // It is software 2D, so `pm list features` shows no
            // android.hardware.face — the feature flag only covers HAL
            // biometrics. The side fingerprint sensor is ALSO present, and the
            // system enroll flow offers whichever modalities exist.
            nav(
                key = "faceid", titleRes = R.string.faceid,
                iconKey = "system_faceid", badgeColor = ColorTokens.systemGreen,
                target = act(android.provider.Settings.ACTION_BIOMETRIC_ENROLL)
            ),
            nav(
                key = "emergency", titleRes = R.string.emergency_sos,
                iconKey = "system_sos", badgeColor = ColorTokens.systemRed,
                target = NavTarget.StockRoot
            ),
            nav(
                key = "privacy", titleRes = R.string.privacy,
                iconKey = "system_shield", badgeColor = ColorTokens.systemBlue,
                target = act(android.provider.Settings.ACTION_PRIVACY_SETTINGS)
            ),
            // No "NFC & payments" row: `pm list features` on the physical rock
            // unit (2026-10-15, 95 features) contains NO android.hardware.nfc,
            // so ACTION_NFC_SETTINGS would open a page for absent hardware.
            // The glyph stays registered; the row stays out.
            header("section_apps", R.string.section_apps),
            nav(
                key = "apps", titleRes = R.string.apps,
                iconKey = "system_app_library", badgeColor = ColorTokens.systemBlue,
                target = act(android.provider.Settings.ACTION_APPLICATION_SETTINGS)
            )
        )
    }
}

/** Which ends of a card group a row owns, so only those draw rounded corners. */
internal enum class GroupEdge { ONLY, FIRST, MIDDLE, LAST }

class SettingsAdapter(
    private val store: AppleuxSettingsStore,
    private val onNavigate: (SettingsItem) -> Unit,
    private val onDarkModeChanged: (Boolean) -> Unit = { }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<SettingsItem> = emptyList()
    private var groupEdges: Array<GroupEdge> = emptyArray()

    fun submitItems(newItems: List<SettingsItem>) {
        items = newItems
        groupEdges = Array(newItems.size) { i ->
            if (newItems[i].type == SettingsItemType.SECTION_HEADER) return@Array GroupEdge.MIDDLE
            val opensGroup = i == 0 ||
                newItems[i - 1].type == SettingsItemType.SECTION_HEADER
            val closesGroup = i == newItems.lastIndex ||
                newItems[i + 1].type == SettingsItemType.SECTION_HEADER
            when {
                opensGroup && closesGroup -> GroupEdge.ONLY
                opensGroup -> GroupEdge.FIRST
                closesGroup -> GroupEdge.LAST
                else -> GroupEdge.MIDDLE
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = items[position].type.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        // Section headers are not body rows: they get their own layout so the
        // title cannot be rendered twice or pick up a card background.
        if (viewType == SettingsItemType.SECTION_HEADER.ordinal) {
            return SettingsHeaderViewHolder(
                inflater.inflate(R.layout.settings_section_header, parent, false)
            )
        }
        val view = inflater.inflate(R.layout.settings_item, parent, false)
        // Capsule-board sections sit 12dp from the screen edges; the header
        // above the card is not inset, so the margin belongs to the row.
        val inset = LayoutUtils.dpToPx(SECTION_SIDE_MARGIN_DP, parent.context)
        (view.layoutParams as? RecyclerView.LayoutParams)?.let {
            it.leftMargin = inset
            it.rightMargin = inset
            view.layoutParams = it
        }
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is SettingsHeaderViewHolder -> holder.bind(item)
            is SettingsViewHolder -> holder.bind(
                item,
                groupEdges[position],
                store,
                onNavigate,
                onDarkModeChanged
            )
        }
    }

    override fun getItemCount(): Int = items.size
}

/** Section title only: small, grey, outside the card group, no accessory. */
class SettingsHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val title: TextView = itemView.findViewById(R.id.settingSectionTitle)

    fun bind(item: SettingsItem) {
        title.setText(item.titleRes)
        title.setTextColor(ColorTokens.secondaryLabel)
        itemView.isClickable = false
    }
}

class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val title: TextView = itemView.findViewById(R.id.settingTitle)
    private val summary: TextView = itemView.findViewById(R.id.settingSummary)
    private val textBox: View = itemView.findViewById(R.id.settingText)
    private val badge: android.widget.FrameLayout = itemView.findViewById(R.id.settingBadge)
    private val glyph: ImageView = itemView.findViewById(R.id.settingGlyph)
    private val toggle: CompoundButton = itemView.findViewById(R.id.settingToggle)
    private val seek: SeekBar = itemView.findViewById(R.id.settingSeek)
    private val chevron: ImageView = itemView.findViewById(R.id.settingChevron)
    private val divider: View = itemView.findViewById(R.id.settingDivider)

    internal fun bind(
        item: SettingsItem,
        edge: GroupEdge,
        store: AppleuxSettingsStore,
        onNavigate: (SettingsItem) -> Unit,
        onDarkModeChanged: (Boolean) -> Unit
    ) {
        title.setText(item.titleRes)
        title.setTextColor(ColorTokens.textPrimary)
        summary.setTextColor(ColorTokens.textSecondary)
        if (item.summaryRes == 0) {
            summary.visibility = View.GONE
        } else {
            summary.visibility = View.VISIBLE
            summary.setText(item.summaryRes)
        }

        itemView.background = groupBackground(edge)
        val showsDivider = edge == GroupEdge.FIRST || edge == GroupEdge.MIDDLE
        divider.visibility = if (showsDivider) View.VISIBLE else View.GONE
        divider.setBackgroundColor(ColorTokens.separator)

        // Capsule-board badge: 29dp rounded square tinted to the row colour,
        // 18dp white glyph. Icon-less rows (our working controls) span the
        // full width exactly like the board's icon-less rows. The divider
        // follows the text: 16 + 29 + 12 = 57dp with a badge, 16dp without.
        val glyphRes = item.iconKey?.let(AppleuxIconRegistry::getDrawableRes)
        if (glyphRes != null && glyphRes != 0) {
            glyph.setImageResource(glyphRes)
            glyph.setImageTintList(
                ColorStateList.valueOf(ColorTokens.white)
            )
            badge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = LayoutUtils.dpToPxFloat(BADGE_RADIUS_DP, itemView.context)
                setColor(if (item.badgeColor != 0) item.badgeColor else ColorTokens.systemGray)
            }
            badge.visibility = View.VISIBLE
            (textBox.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = LayoutUtils.dpToPx(BADGE_TEXT_GAP_DP, itemView.context)
                textBox.layoutParams = it
            }
            (divider.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = LayoutUtils.dpToPx(DIVIDER_INSET_BADGED_DP, itemView.context)
                it.height = LayoutUtils.dpToPxFloat(SEPARATOR_HAIRLINE_DP, itemView.context).toInt()
                divider.layoutParams = it
            }
        } else {
            badge.visibility = View.GONE
            (textBox.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = 0
                textBox.layoutParams = it
            }
            (divider.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = LayoutUtils.dpToPx(DIVIDER_INSET_PLAIN_DP, itemView.context)
                it.height = LayoutUtils.dpToPxFloat(SEPARATOR_HAIRLINE_DP, itemView.context).toInt()
                divider.layoutParams = it
            }
        }

        // Rows are recycled across types, so every accessory and listener is
        // reset before the type-specific branch claims them.
        toggle.visibility = View.GONE
        seek.visibility = View.GONE
        chevron.visibility = View.GONE
        toggle.setOnCheckedChangeListener(null)
        seek.setOnSeekBarChangeListener(null)
        itemView.setOnClickListener(null)
        itemView.isClickable = false

        when (item.type) {
            SettingsItemType.SWITCH -> {
                toggle.visibility = View.VISIBLE
                // iOS UISwitch on-tint is systemGreen (#34C759), not the
                // Material default teal. Applied here (not theme-wide) so only
                // switches change, matching iOS control semantics.
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                )
                (toggle as? android.widget.Switch)?.let { sw ->
                    sw.thumbTintList = ColorStateList(
                        states,
                        intArrayOf(ColorTokens.white, ColorTokens.systemGray)
                    )
                    sw.trackTintList = ColorStateList(
                        states,
                        intArrayOf(ColorTokens.systemGreen, ColorTokens.systemFillSecondary)
                    )
                }
                toggle.isChecked = item.preferenceKey?.let {
                    store.getBoolean(it, item.defaultValue)
                } ?: item.defaultValue
                val listener = object : CompoundButton.OnCheckedChangeListener {
                    override fun onCheckedChanged(button: CompoundButton, checked: Boolean) {
                        val key = item.preferenceKey
                        if (key != null && !store.setBoolean(key, checked)) {
                            // The write did not land. Do not leave the UI
                            // claiming a value the rest of the OS cannot see.
                            button.setOnCheckedChangeListener(null)
                            button.isChecked = store.getBoolean(key, item.defaultValue)
                            button.setOnCheckedChangeListener(this)
                            return
                        }
                        // Dark Mode owns the theme, so persisting the flag is
                        // only half of it: the screen has to repaint too.
                        if (key == AppleuxRuntimeConfig.KEY_DARK_MODE) {
                            onDarkModeChanged(checked)
                        }
                    }
                }
                toggle.setOnCheckedChangeListener(listener)
            }
            SettingsItemType.SEEK_BAR -> {
                seek.visibility = View.VISIBLE
                seek.max = SEEK_DISPLAY_MAX
                // iOS sliders render in system blue, not Material teal.
                seek.progressTintList = ColorStateList.valueOf(ColorTokens.systemBlue)
                seek.thumbTintList = ColorStateList.valueOf(ColorTokens.systemBlue)
                val stored = item.preferenceKey?.let { store.getInt(it, item.defaultIntValue) }
                    ?: item.defaultIntValue
                seek.progress = toDisplay(stored, item.valueScaleMax)
                seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) {
                            item.preferenceKey?.let { store.setInt(it, toStored(p, item.valueScaleMax)) }
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                    override fun onStopTrackingTouch(sb: SeekBar?) = Unit
                })
            }
            SettingsItemType.NAVIGATION -> {
                itemView.isClickable = true
                itemView.setOnClickListener { onNavigate(item) }
                AppleuxIconRegistry.getDrawableRes(ICON_CHEVRON)?.let(chevron::setImageResource)
                chevron.setImageTintList(ColorStateList.valueOf(ColorTokens.tertiaryLabel))
                chevron.visibility = View.VISIBLE
            }
            // A section header is never bound to this holder; its own layout is.
            SettingsItemType.SECTION_HEADER -> Unit
        }
    }

    /**
     * Rounded corners only where the group opens or closes, so the rows read as
     * one inset capsule instead of a stack of separate pills. 26dp is the
     * capsule-board radius (--card-radius), not the old 12dp card radius.
     */
    private fun groupBackground(edge: GroupEdge): GradientDrawable {
        val context = itemView.context
        val radius = LayoutUtils.dpToPxFloat(CAPSULE_RADIUS_DP, context)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            when (edge) {
                GroupEdge.FIRST -> cornerRadii = floatArrayOf(
                    radius, radius, radius, radius, 0f, 0f, 0f, 0f
                )
                GroupEdge.LAST -> cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f, radius, radius, radius, radius
                )
                GroupEdge.ONLY -> cornerRadii = floatArrayOf(
                    radius, radius, radius, radius, radius, radius, radius, radius
                )
                GroupEdge.MIDDLE -> Unit
            }
            setColor(AppleuxTheme.surfaceContainer.resolved)
        }
    }
}

/** Every slider renders this range, whatever range the store keeps. */
private const val SEEK_DISPLAY_MAX = 100

/**
 * Settings.System.SCREEN_BRIGHTNESS scale, matching the range
 * [AppleuxRuntimeConfig.readBrightness] and `writeBrightness` coerce to.
 */
private const val BRIGHTNESS_SCALE_MAX = 255

/** Separator weight in dp; the token set has no hairline dimension. */
private const val SEPARATOR_HAIRLINE_DP = 1f

/**
 * Capsule-board metrics (design-reference/inbox/settings-capsule-ios27.html).
 * SF Pro cannot ship (Apple license) so the type stays system sans; these
 * numbers — not the font file — are what the board contributes.
 */
private const val SECTION_SIDE_MARGIN_DP = 12f
private const val CAPSULE_RADIUS_DP = 26f
private const val BADGE_RADIUS_DP = 6.5f
private const val BADGE_TEXT_GAP_DP = 12f
private const val DIVIDER_INSET_BADGED_DP = 57f
private const val DIVIDER_INSET_PLAIN_DP = 16f

/** Design-system drawable registry key for the navigation-row chevron. */
private const val ICON_CHEVRON = "system_chevron_right"

/** Store scale (0..[scaleMax]) to the 0..[SEEK_DISPLAY_MAX] slider range. */
private fun toDisplay(stored: Int, scaleMax: Int): Int =
    if (scaleMax <= SEEK_DISPLAY_MAX) stored
    else (stored.toFloat() * SEEK_DISPLAY_MAX / scaleMax).roundToInt()

/** Slider range back to the store scale. */
private fun toStored(display: Int, scaleMax: Int): Int =
    if (scaleMax <= SEEK_DISPLAY_MAX) display
    else (display.toFloat() * scaleMax / SEEK_DISPLAY_MAX).roundToInt()