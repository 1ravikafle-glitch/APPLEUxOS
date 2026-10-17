package com.appleux.design.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.IconTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens

/**
 * The iOS tab bar: a Liquid Glass strip of labelled items.
 *
 * ## ⚠ ROM-STAGE COMPONENT — read before wiring this up
 *
 * **This cannot be installed as the system navigation bar on a stock image.**
 * Replacing or drawing over the platform navigation bar requires
 * `android.permission.STATUS_BAR` plus `INTERNAL_SYSTEM_WINDOW` — both
 * **signature|privileged**, so only a platform-signed build is granted them.
 * `scripts/build-emulator-apks.sh` signs every module with
 * `tools/debug.keystore`, which carries neither, so on the emulator this
 * throws `SecurityException` rather than degrading.
 *
 * Usable today as an in-app tab bar inside any AppleUX surface. Nothing in
 * SystemUI, the Launcher or Settings currently instantiates it; it is here so
 * the ROM has one tab bar rather than three divergent ones.
 *
 * ## What was wrong before
 *
 *  - `NavigationItem.activeIconRes` was never read — a selected tab showed the
 *    same glyph as an unselected one.
 *  - `NavigationItem.label` was never rendered: the tab bar showed bare icons
 *    with no labels at all.
 *  - Unselected items were tinted `ColorTokens.label`, the *primary* label
 *    colour, so an inactive tab was as prominent as the active one.
 *  - The background was a flat opaque `systemBackground` rectangle rather than
 *    the glass material the rest of iOS 27 uses.
 *  - Items had no `contentDescription`, so an accessibility audit fails them.
 */
class AppleuxNavigationBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val itemViews = mutableListOf<NavigationBarItemView>()
    private var activeItem: NavigationBarItemView? = null
    private var onItemSelectedListener: ((NavigationItem) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, context),
            LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context),
            LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, context),
            LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
        )
        applyTheme()
    }

    // ---- Items -------------------------------------------------------------

    fun addItem(item: NavigationItem) {
        val view = NavigationBarItemView(context, item)
        itemViews.add(view)
        addView(view, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        view.setOnClickListener {
            setActiveItem(item.id)
            onItemSelectedListener?.invoke(item)
        }
        if (itemViews.size == 1) setActiveItem(item.id)
    }

    /** Selects by item id. Unknown ids are ignored. */
    fun setActiveItem(id: Int) {
        val target = itemViews.firstOrNull { it.item.id == id } ?: return
        if (target === activeItem) return
        activeItem?.isActive = false
        target.isActive = true
        activeItem = target
    }

    fun getActiveItemId(): Int? = activeItem?.item?.id

    fun setOnItemSelectedListener(listener: ((NavigationItem) -> Unit)?) {
        onItemSelectedListener = listener
    }

    // ---- Appearance --------------------------------------------------------

    /** Re-resolves the glass fill and both label tints from the tokens. */
    fun applyTheme() {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = LayoutUtils.dpToPxFloat(CORNER_RADIUS_DP, context)
            setColor(
                if (ColorTokens.darkMode) BAR_BG_DARK else BAR_BG_LIGHT
            )
        }
        itemViews.forEach { it.applyTheme() }
    }

    /** One tab: glyph above label, tinted by selection state. */
    class NavigationBarItemView @JvmOverloads constructor(
        context: Context,
        val item: NavigationItem,
        attrs: AttributeSet? = null
    ) : LinearLayout(context, attrs) {

        private val icon = ImageView(context)
        private val label = AppleuxText(context)

        var isActive: Boolean = false
            set(value) {
                field = value
                applyTheme()
            }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minimumHeight = LayoutUtils.dpToPx(
                com.appleux.design.tokens.SizingTokens.TOUCH_TARGET_MIN, context
            )

            icon.layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            icon.layoutParams = LayoutParams(
                LayoutUtils.dpToPx(IconTokens.SYSTEM_ICON_SIZE_TAB_BAR, context),
                LayoutUtils.dpToPx(IconTokens.SYSTEM_ICON_SIZE_TAB_BAR, context)
            )

            label.apply {
                applyTypography(TypographyTokens.caption2)
                gravity = Gravity.CENTER
            }

            addView(icon)
            addView(label)

            applyTheme()
        }

        fun applyTheme() {
            // The selected tab uses activeIconRes when the caller supplied one.
            icon.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    if (isActive) (item.activeIconRes ?: item.iconRes) else item.iconRes
                )
            )
            val tint = if (isActive) ColorTokens.accent else ColorTokens.secondaryLabel
            icon.setColorFilter(tint)
            label.setTextColor(tint)
            label.text = item.label
            contentDescription = item.label
            isSelected = isActive
        }
    }

    data class NavigationItem(
        val id: Int,
        val label: String,
        @DrawableRes val iconRes: Int,
        @DrawableRes val activeIconRes: Int? = null
    )

    private companion object {
        /**
         * PROVENANCE: AppleUX-chosen. iOS tab bars sit flush with the screen
         * edge and get their rounding from the device; a floating bar needs an
         * explicit radius, and this is the Liquid Glass Regular radius used
         * elsewhere in the system.
         */
        const val CORNER_RADIUS_DP = 34f

        val BAR_BG_LIGHT = 0xF2F2F2F7.toInt()
        val BAR_BG_DARK = 0xF21C1C1E.toInt()
    }
}