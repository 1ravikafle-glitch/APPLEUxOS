package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.IconTokens
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.tokens.MotionTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.SizingTokens
import com.appleux.design.motion.AppleuxMotionEngine
import com.appleux.design.runtime.AppleuxRuntimeConfig

class AppleuxControlCenter @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val motionEngine: AppleuxMotionEngine = AppleuxMotionEngine.getInstance(context)
    private val tileContainer: GridLayout
    private val headerView: AppleuxControlCenterHeader

    private val tiles: MutableList<ControlTile> = mutableListOf()
    private val tileViews: MutableList<AppleuxControlTile> = mutableListOf()

    /** Taps on empty panel area (outside tiles) dismiss the panel. */
    var onBackgroundClick: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        // Fullscreen modal panel: background taps dismiss (children consume
        // their own taps first). Without this the window eats every touch.
        isClickable = true
        isFocusable = false
        setOnClickListener { onBackgroundClick?.invoke() }
        // Dim scrim behind a floating card (iOS Control Center), not a
        // fullscreen opaque panel. Alpha tracks the glass intensity level.
        val glassLevel = try {
            AppleuxRuntimeConfig.readInt(
                context.contentResolver,
                AppleuxRuntimeConfig.KEY_GLASS_INTENSITY,
                AppleuxGlass.DEFAULT_LEVEL
            ).coerceIn(AppleuxGlass.MIN_LEVEL, AppleuxGlass.MAX_LEVEL)
        } catch (e: Exception) {
            AppleuxGlass.DEFAULT_LEVEL
        }
        // No flat scrim here any more: the panel background IS the glass fill
        // (set in init), and a second scrim layer double-darkened the sheet.
        setPadding(
            SpacingTokens.PADDING_LARGE, SpacingTokens.PADDING_LARGE,
            SpacingTokens.PADDING_LARGE, SpacingTokens.PADDING_LARGE
        )

        headerView = AppleuxControlCenterHeader(context)
        addView(headerView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        tileContainer = GridLayout(context).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(SpacingTokens.PADDING_MEDIUM, SpacingTokens.PADDING_MEDIUM, SpacingTokens.PADDING_MEDIUM, SpacingTokens.PADDING_LARGE)
        }
        // iOS 27 Control Center is a FULL-SCREEN Liquid Glass sheet (see
        // design-reference/ios27/official-guide/images/7511c37e-*.png), not a
        // small floating card: the wallpaper/app shows through the whole
        // surface and only the individual controls are rounded. The previous
        // single centered card was the single biggest structural mismatch.
        //
        // Real frosting comes from FLAG_BLUR_BEHIND on the overlay window
        // (AppleuxSystemUIService); a RenderEffect here would only blur this
        // view's own fill and never what is behind it.
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            // Sheet with rounded TOP corners only, like an iOS presented sheet.
            cornerRadii = floatArrayOf(
                LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP * resources.displayMetrics.density,
                LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP * resources.displayMetrics.density,
                0f, 0f, 0f, 0f, 0f, 0f
            )
            setColor(
                if (ColorTokens.darkMode) LiquidGlassTokens.REGULAR_BG_DARK
                else LiquidGlassTokens.REGULAR_BG_LIGHT
            )
            this.alpha = AppleuxGlass.scrimAlphaForLevel(glassLevel)
        }
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                if (view.width <= 0 || view.height <= 0) return
                val r = LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP *
                    view.resources.displayMetrics.density
                // Outline only offers a symmetric setRoundRect, so the top-rounded
                // sheet shape goes through a Path.
                val w = view.width.toFloat()
                val h = view.height.toFloat()
                val p = android.graphics.Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, r)
                    quadTo(0f, 0f, r, 0f)
                    lineTo(w - r, 0f)
                    quadTo(w, 0f, w, r)
                    lineTo(w, h)
                    close()
                }
                outline.setPath(p)
            }
        }
        addView(
            tileContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    /**
     * Refreshes the header block. The service owns the receivers, so the
     * panel never registers its own and cannot leak.
     */
    fun bindHeader(
        activeRadios: List<String>,
        airplaneMode: Boolean,
        charging: Boolean,
        batteryPercent: Int
    ) {
        headerView.bind(activeRadios, airplaneMode, charging, batteryPercent)
    }

    fun addTile(tile: ControlTile) {
        tiles.add(tile)
        val tileView = AppleuxControlTile(context, tile)
        // iOS tiles are fixed generous cells. GridLayout's UNDEFINED-spec
        // negotiation squeezes WRAP children (truncated labels) and honoring
        // minimumWidth backfires, so size cells explicitly.
        val cell = (104 * resources.displayMetrics.density).toInt()
        tileView.minimumWidth = 0
        tileView.setOnClickListener {
            tile.onClick()
            tileView.animateToggle()
        }
        tileViews.add(tileView)
        val tileLayoutParams = GridLayout.LayoutParams().apply {
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1)
            width = cell
            height = cell
        }
        tileContainer.addView(tileView, tileLayoutParams)
    }

    fun removeTile(tileId: String) {
        val index = tiles.indexOfFirst { it.id == tileId }
        if (index >= 0) {
            tiles.removeAt(index)
            tileViews.removeAt(index)
            tileContainer.removeViewAt(index)
        }
    }

    /** Syncs a tile's visual state to a real platform state. */
    fun setTileActive(tileId: String, active: Boolean) {
        val index = tiles.indexOfFirst { it.id == tileId }
        if (index >= 0) tileViews[index].setActive(active)
    }

    data class ControlTile(
        val id: String,
        val name: String,
        @DrawableRes val iconRes: Int? = null,
        val iconName: String = "",
        val isToggle: Boolean = false,
        val isEnabled: Boolean = true,
        val onClick: () -> Unit
    )

    data class ControlTileState(
        val isActive: Boolean = false,
        val isEnabled: Boolean = true,
        val label: String = ""
    )
}
