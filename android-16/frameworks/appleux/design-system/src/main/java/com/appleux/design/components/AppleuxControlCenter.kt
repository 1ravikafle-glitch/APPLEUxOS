package com.appleux.design.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.runtime.AppleuxRuntimeConfig

/**
 * iOS 27 Control Center — layout derived from IPSW plist analysis.
 *
 * Module sizes from _CCModuleSizePROTOTYPE:
 * - MediaControlsModule: 2x2 (the only large module — Now Playing)
 * - All other 54 modules: 1x1 (63dp circular tiles)
 * - ConnectivityModule: special cluster (Wi-Fi, BT, Cellular, Airplane)
 * - FocusUIModule: horizontal pill
 *
 * Layout (top to bottom):
 * 1. Top row: [+ add control] left, [power] right
 * 2. Header: compact status (radios + battery, centered)
 * 3. Connectivity cluster (left) + Media card 2x2 (right)
 * 4. Focus pill (left) + Brightness/Volume sliders (right)
 * 5. 4-column circular toggle grid (remaining tiles)
 * 6. Right-edge vertical page dots
 *
 * Total: 55 modules across 7 categories.
 */
class AppleuxControlCenter @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headerView: AppleuxControlCenterHeader
    private val tileContainer: GridLayout
    private val pageDots = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }

    // Connectivity cluster (special — contains Wi-Fi, BT, Cellular, Airplane)
    val connectivityCluster = AppleuxConnectivityCluster(context)

    // Focus pill (horizontal, not circular)
    val focusPill = AppleuxText(context).apply {
        text = "🌙 Focus ◇"
        textSize = 15f
        setTextColor(ColorTokens.label)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 40 * resources.displayMetrics.density
            setColor((110 shl 24) or 0x000000)
            setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), 0x33FFFFFF)
        }
    }

    // Vertical sliders (right side)
    val brightnessSlider = AppleuxVerticalSlider(context).apply {
        label = "Brightness"
        iconRes = com.appleux.design.R.drawable.sf_sun
        layoutParams = LinearLayout.LayoutParams(dp(52), dp(260))
    }
    val volumeSlider = AppleuxVerticalSlider(context).apply {
        label = "Volume"
        iconRes = com.appleux.design.R.drawable.sf_volume
        layoutParams = LinearLayout.LayoutParams(dp(52), dp(260))
    }

    // Media card (2x2 — the only large module per IPSW)
    val mediaCard = AppleuxMediaCard(context)

    fun setDots(count: Int, active: Int) {
        pageDots.removeAllViews()
        val d = resources.displayMetrics.density
        for (i in 0 until count.coerceIn(1, 5)) {
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == active) 0xFFFFFFFF.toInt() else 0x80FFFFFF.toInt())
                }
                val s = ((if (i == active) 8f else 6f) * d).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    topMargin = (4 * d).toInt()
                    bottomMargin = (4 * d).toInt()
                }
            }
            pageDots.addView(dot)
        }
    }

    var onAddControl: (() -> Unit)? = null
    var onPower: (() -> Unit)? = null
    var onFocusEdit: (() -> Unit)? = null

    private val tiles: MutableList<ControlTile> = mutableListOf()
    private val tileViews: MutableList<AppleuxControlTile> = mutableListOf()

    var onBackgroundClick: (() -> Unit)? = null
    var editMode: Boolean = false
        set(v) { field = v; alpha = if (v) 0.97f else 1f }

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = false
        setOnClickListener { onBackgroundClick?.invoke() }

        val glassLevel = try {
            AppleuxRuntimeConfig.readInt(
                context.contentResolver,
                AppleuxRuntimeConfig.KEY_GLASS_INTENSITY,
                AppleuxGlass.DEFAULT_LEVEL
            ).coerceIn(AppleuxGlass.MIN_LEVEL, AppleuxGlass.MAX_LEVEL)
        } catch (e: Exception) { AppleuxGlass.DEFAULT_LEVEL }

        setPadding(dp(16), dp(8), dp(16), dp(16))
        AppleuxDisplayInsets.applyTopInset(context, this, extraTopDp = 4f)

        // Row 1: [+] left, [power] right
        val sysRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val chromeFg = if (ColorTokens.darkMode) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
        val addBtn = AppleuxText(context).apply {
            text = "+"
            textSize = 24f
            setTextColor(chromeFg)
            gravity = Gravity.START
            setPadding(dp(12), dp(6), dp(12), dp(6))
            isClickable = true
            setOnClickListener { onAddControl?.invoke() }
        }
        val powerBtn = AppleuxText(context).apply {
            text = "⏻"
            textSize = 22f
            setTextColor(chromeFg)
            gravity = Gravity.END
            setPadding(dp(12), dp(6), dp(12), dp(6))
            isClickable = true
            setOnClickListener { onPower?.invoke() }
        }
        sysRow.addView(addBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        sysRow.addView(powerBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
            gravity = Gravity.END
        })
        addView(sysRow)

        // Row 2: Header (compact — radios + battery, centered)
        headerView = AppleuxControlCenterHeader(context)
        headerView.setCompact(true)
        addView(headerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Row 3: Connectivity cluster (left) + Media card 2x2 (right)
        val row3 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row3.addView(connectivityCluster, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row3.addView(mediaCard, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(row3)

        // Row 4: Focus pill (left) + Brightness slider (right)
        val row4 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        focusPill.setOnClickListener { onFocusEdit?.invoke() }
        row4.addView(focusPill, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row4.addView(brightnessSlider)
        addView(row4)

        // Row 5: 4-column circular toggle grid
        val gridRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        tileContainer = GridLayout(context).apply {
            columnCount = 4
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, WRAP_CONTENT
            )
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        gridRow.addView(tileContainer)
        setDots(3, 0)
        val midWrap = FrameLayout(context)
        midWrap.addView(gridRow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        midWrap.addView(pageDots, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.END))
        addView(midWrap, LayoutParams(LayoutParams.MATCH_PARENT, WRAP_CONTENT))

        addView(AppleuxHomeIndicator(context), LayoutParams(
            LayoutParams.MATCH_PARENT, WRAP_CONTENT))

        // Liquid Glass background
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (ColorTokens.darkMode)
                LiquidGlassTokens.REGULAR_BG_DARK
            else
                LiquidGlassTokens.REGULAR_BG_LIGHT)
            alpha = maxOf(
                AppleuxGlass.scrimAlphaForLevel(glassLevel),
                235
            )
        }
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                if (view.width <= 0 || view.height <= 0) return
                val r = LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP * resources.displayMetrics.density
                val w = view.width.toFloat()
                val h = view.height.toFloat()
                val p = android.graphics.Path()
                p.addPath(AppleuxSuperellipse.path(w.toInt(), h.toInt(), r))
                p.addRect(0f, h - r, w, h, android.graphics.Path.Direction.CCW)
                outline.setPath(p)
            }
        }
    }

    fun bindHeader(
        activeRadios: List<String>,
        airplaneMode: Boolean,
        charging: Boolean,
        batteryPercent: Int,
        chargeTimeText: String? = null
    ) {
        headerView.bind(activeRadios, airplaneMode, charging, batteryPercent)
    }

    fun pushIsland(slots: AppleuxDynamicIsland.LiveSlots) {}

    fun setBrightness(v: Float) { brightnessSlider.level01 = v }
    fun setVolume(v: Float) { volumeSlider.level01 = v }
    fun bindMedia(title: String?, artist: String?, playing: Boolean) { mediaCard.bind(title, artist, playing) }

    fun addTile(tile: ControlTile) {
        tiles.add(tile)
        val tileView = AppleuxControlTile(context, tile)
        tileView.minimumWidth = 0
        tileView.setOnClickListener { tile.onClick(); tileView.animateToggle() }
        tileView.setOnLongClickListener { onTileLongPress?.invoke(tile.id); true }
        tileViews.add(tileView)
        tileContainer.addView(tileView, GridLayout.LayoutParams().apply {
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1)
            width = GridLayout.LayoutParams.WRAP_CONTENT
            height = GridLayout.LayoutParams.WRAP_CONTENT
        })
    }

    var onTileLongPress: ((String) -> Unit)? = null

    fun removeTile(tileId: String) {
        val i = tiles.indexOfFirst { it.id == tileId }
        if (i >= 0) {
            tiles.removeAt(i)
            tileViews.removeAt(i)
            tileContainer.removeViewAt(i)
        }
    }

    fun setTileActive(tileId: String, active: Boolean) {
        val i = tiles.indexOfFirst { it.id == tileId }
        if (i >= 0) tileViews[i].setActive(active)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    data class ControlTile(
        val id: String,
        val name: String,
        @DrawableRes val iconRes: Int? = null,
        val iconName: String = "",
        val isToggle: Boolean = false,
        val isEnabled: Boolean = true,
        val activeColor: Int = ColorTokens.systemBlue,
        val onClick: () -> Unit
    )
}
