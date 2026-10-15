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
        // System screens (notification access grant) open directly.
        if (item.key == "notifications") {
            runCatching {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    )
                )
            }
            return
        }
        // Control Center / Notification Center cannot exist without the
        // "display over other apps" grant (§34): without it every panel
        // gesture is a silent no-op, so this row is load-bearing, not polish.
        if (item.key == "overlay_permission") {
            runCatching {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:com.appleux.systemui")
                    )
                )
            }.onFailure {
                runCatching {
                    startActivity(
                        android.content.Intent(
                            android.provider.Settings
                                .ACTION_MANAGE_OVERLAY_PERMISSION
                        )
                    )
                }
            }
            return
        }
        val target = when (item.key) {
            "lock_screen" -> com.appleux.settings.security.SecuritySettingsActivity::class.java
            "section_display" -> com.appleux.settings.display.DisplaySettingsActivity::class.java
            else -> null
        } ?: return
        startActivity(android.content.Intent(this, target))
    }
}

enum class SettingsItemType {
    SECTION_HEADER, SWITCH, SEEK_BAR, NAVIGATION
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
    val valueScaleMax: Int = SEEK_DISPLAY_MAX
)

object SettingsCatalog {
    fun build(context: android.content.Context): List<SettingsItem> = listOf(
        SettingsItem(
            key = "section_appleux",
            titleRes = R.string.appleux,
            summaryRes = 0,
            type = SettingsItemType.SECTION_HEADER
        ),
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
            key = "section_display",
            titleRes = R.string.display,
            summaryRes = 0,
            type = SettingsItemType.SECTION_HEADER
        ),
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
        SettingsItem(
            key = "glass_intensity",
            titleRes = R.string.glass_intensity,
            summaryRes = R.string.glass_intensity_summary,
            type = SettingsItemType.SEEK_BAR,
            preferenceKey = AppleuxRuntimeConfig.KEY_GLASS_INTENSITY,
            defaultIntValue = AppleuxRuntimeConfig.DEFAULT_GLASS_INTENSITY
        ),
        SettingsItem(
            key = "section_security",
            titleRes = R.string.security,
            summaryRes = 0,
            type = SettingsItemType.SECTION_HEADER
        ),
        SettingsItem(
            key = "lock_screen",
            titleRes = R.string.lock_screen,
            summaryRes = R.string.lock_screen,
            type = SettingsItemType.NAVIGATION
        ),
        SettingsItem(
            key = "notifications",
            titleRes = R.string.notifications,
            summaryRes = R.string.notifications_summary,
            type = SettingsItemType.NAVIGATION
        ),
        SettingsItem(
            key = "overlay_permission",
            titleRes = R.string.overlay_permission,
            summaryRes = R.string.overlay_permission_summary,
            type = SettingsItemType.NAVIGATION
        )
    )
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
        // iOS grouped lists are inset from the screen edges; the header above the
        // card is not, so the margin belongs to the row.
        val inset = LayoutUtils.dpToPx(SizingTokens.INSET_XL, parent.context)
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
        (divider.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            // Inset to the text: card margin (INSET_XL) + row padding (INSET_XL).
            it.marginStart = LayoutUtils.dpToPx(SizingTokens.INSET_XL * 2f, itemView.context)
            it.height = LayoutUtils.dpToPxFloat(SEPARATOR_HAIRLINE_DP, itemView.context).toInt()
            divider.layoutParams = it
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
     * one inset card instead of a stack of separate pills.
     */
    private fun groupBackground(edge: GroupEdge): GradientDrawable {
        val context = itemView.context
        val radius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_MEDIUM, context)
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