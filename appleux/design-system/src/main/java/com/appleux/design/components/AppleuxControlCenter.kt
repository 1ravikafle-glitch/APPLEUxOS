package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.motion.AppleuxMotionEngine
import com.appleux.design.runtime.AppleuxRuntimeConfig

/**
 * iOS 27 Control Center — fullscreen Liquid Glass sheet.
 *
 * Layout (guide 7511c37e, Figma Regular 34dp / #FFF@0.70 + rim):
 * status bar / island / compact header / connectivity-cluster + focus row /
 * media card / brightness+volume vertical sliders + circular toggle grid /
 * right-edge page dots / bottom + and power.
 *
 * Backwards compatible: addTile/removeTile/setTileActive/bindHeader/pushIsland
 * preserved for AppleuxSystemUIService.
 */
class AppleuxControlCenter @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val motionEngine: AppleuxMotionEngine = AppleuxMotionEngine.getInstance(context)
    private val tileContainer: GridLayout
    private val headerView: AppleuxControlCenterHeader

    val connectivityCluster = AppleuxConnectivityCluster(context)
    val brightnessSlider = AppleuxVerticalSlider(context)
    val volumeSlider = AppleuxVerticalSlider(context)
    val mediaCard = AppleuxMediaCard(context)
    private val pageDots = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }

    /** iOS right-edge page column. Count/active update in place. */
    fun setDots(count: Int, active: Int) {
        pageDots.removeAllViews()
        val d = resources.displayMetrics.density
        for (i in 0 until count.coerceIn(1, 5)) {
            val dot = View(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (i == active) android.graphics.Color.WHITE else 0x80FFFFFF.toInt())
                }
                val s = ((if (i == active) 8f else 6f) * d).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    topMargin = (3 * d).toInt(); bottomMargin = (3 * d).toInt()
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
        setPadding(dp(12), dp(6), dp(12), dp(12))
        AppleuxDisplayInsets.applyTopInset(context, this, extraTopDp = 4f)

        // iOS CC shows NO clock and NO island: one compact summary line
        // (radios + battery). The date/time stack was the triple-clock bug.
        headerView = AppleuxControlCenterHeader(context)
        headerView.setCompact(true)
        addView(headerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // iOS top row: + (add control) left, power right — nothing at bottom.
        val sysRow = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        // Adaptive chrome: dark on light glass (iPhone shows dark +/power).
        val chromeFg = if (ColorTokens.darkMode) android.graphics.Color.WHITE
        else 0xFF1C1C1E.toInt()
        val addBtn = AppleuxText(context).apply {
            text = "+"; textSize = 22f; setTextColor(chromeFg)
            gravity = Gravity.START; setPadding(dp(8), dp(4), dp(8), dp(4))
            isClickable = true; setOnClickListener { onAddControl?.invoke() }
        }
        val powerBtn = AppleuxText(context).apply {
            text = "\u23FB"; textSize = 20f; setTextColor(chromeFg)
            gravity = Gravity.END; setPadding(dp(8), dp(4), dp(8), dp(4))
            isClickable = true; setOnClickListener { onPower?.invoke() }
        }
        sysRow.addView(addBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sysRow.addView(powerBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            gravity = Gravity.END
        })
        addView(sysRow)

        // Row 1 (iOS: connectivity 2x2 left + Now Playing 2x2 right, equal
        // halves of the same row — not stacked).
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(connectivityCluster, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topRow.addView(mediaCard, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(topRow)

        // Row 2 (iOS: controls left, tall brightness+volume sliders pinned to
        // the right edge spanning the middle rows).
        val midRow = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        // Focus pill, half width left (iOS 2x1). Pill glass, moon glyph is
        // drawn by the text prefix until a dedicated focus tile ships.
        val focusHalf = LinearLayout(context).apply { orientation = HORIZONTAL }
        val focusPill = AppleuxText(context).apply {
            text = "\u263E Focus"; textSize = 15f
            setTextColor(ColorTokens.label); gravity = Gravity.CENTER
            isClickable = true; setOnClickListener { onFocusEdit?.invoke() }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 40 * resources.displayMetrics.density
                setColor((110 shl 24) or 0x000000)
                setStroke(
                    resources.displayMetrics.density.toInt().coerceAtLeast(1),
                    0x33FFFFFF
                )
            }
        }
        focusHalf.addView(focusPill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        focusHalf.addView(android.view.View(context), LinearLayout.LayoutParams(0, 1, 1f))
        midRow.addView(focusHalf, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sliderCol = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER
        }
        brightnessSlider.label = "Brightness"
        brightnessSlider.iconRes = com.appleux.design.R.drawable.sf_sun
        volumeSlider.label = "Volume"
        volumeSlider.iconRes = com.appleux.design.R.drawable.sf_volume
        for (s in listOf(brightnessSlider, volumeSlider)) {
            s.layoutParams = LinearLayout.LayoutParams(dp(48), dp(240)).apply {
                marginEnd = dp(8)
            }
            sliderCol.addView(s)
        }
        midRow.addView(sliderCol, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(midRow)

        // Toggle grid, full width.
        val gridRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        tileContainer = GridLayout(context).apply {
            columnCount = 4
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        gridRow.addView(tileContainer)
        // Right-edge page dots (iOS has control pages).
        setDots(3, 0)
        val midWrap = FrameLayout(context)
        midWrap.addView(gridRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        midWrap.addView(pageDots, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END))
        addView(midWrap, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(AppleuxHomeIndicator(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(if (ColorTokens.darkMode) LiquidGlassTokens.REGULAR_BG_DARK else LiquidGlassTokens.REGULAR_BG_LIGHT)
            // Emulator/SwiftShader has no blur-behind: lift the fill floor so
            // the sheet reads as a panel, not as floating glyphs. On-device
            // blur carries the material and this extra alpha is harmless.
            this.alpha = maxOf(
                AppleuxGlass.scrimAlphaForLevel(glassLevel),
                CC_FILL_FLOOR_ALPHA
            )
        }
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                if (view.width <= 0 || view.height <= 0) return
                val r = LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP * view.resources.displayMetrics.density
                val w = view.width.toFloat(); val h = view.height.toFloat()
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
        connectivityCluster.setStates(
            wifi = activeRadios.contains("Wi-Fi"),
            bt = activeRadios.contains("Bluetooth"),
            airplane = airplaneMode,
            cellular = activeRadios.contains("Cellular")
        )
    }

    /** Kept for the service call path; the island lives on the shade now, not here. */
    fun pushIsland(slots: AppleuxDynamicIsland.LiveSlots) = Unit

    fun setBrightness(v: Float) { brightnessSlider.level01 = v }
    fun setVolume(v: Float) { volumeSlider.level01 = v }
    fun bindMedia(title: String?, artist: String?, playing: Boolean) { mediaCard.bind(title, artist, playing) }

    fun addTile(tile: ControlTile) {
        tiles.add(tile)
        val tileView = AppleuxControlTile(context, tile)
        tileView.minimumWidth = 0
        tileView.setOnClickListener { tile.onClick(); tileView.animateToggle() }
        // Long-press opens secondary panel (iOS behaviour) — sheet anchored by service.
        tileView.setOnLongClickListener { onTileLongPress?.invoke(tile.id); true }
        tileViews.add(tileView)
        // WRAP_CONTENT: the tile self-sizes to CONTROL_DIAMETER_DP (63dp).
        // A fixed 48dp cell clipped every circle by 15dp.
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
        if (i >= 0) { tiles.removeAt(i); tileViews.removeAt(i); tileContainer.removeViewAt(i) }
    }

    fun setTileActive(tileId: String, active: Boolean) {
        val i = tiles.indexOfFirst { it.id == tileId }
        if (i >= 0) tileViews[i].setActive(active)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        /** Minimum sheet alpha: legible panel even with zero behind-blur. */
        const val CC_FILL_FLOOR_ALPHA = 235
    }

    data class ControlTile(
        val id: String, val name: String,
        @DrawableRes val iconRes: Int? = null,
        val iconName: String = "",
        val isToggle: Boolean = false,
        val isEnabled: Boolean = true,
        /**
         * Active fill. iOS colors controls individually (airplane orange,
         * cellular green, wifi/bt blue, flashlight white...); default blue
         * preserves every existing call site that does not pass one.
         */
        val activeColor: Int = ColorTokens.systemBlue,
        val onClick: () -> Unit
    )
    data class ControlTileState(val isActive: Boolean = false, val isEnabled: Boolean = true, val label: String = "")
}
