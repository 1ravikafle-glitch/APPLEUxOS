package com.appleux.design.theme

import com.appleux.design.tokens.ColorTokens

object AppleuxTheme {

    var isDarkMode: Boolean = true
        set(value) {
            field = value
            ColorTokens.updateTheme(value)
        }

    data class ColorScheme(
        val light: Int,
        val dark: Int
    ) {
        val resolved: Int
            get() = if (isDarkMode) dark else light
    }

    private val lightColorTokens = LightColorTokens
    private val darkColorTokens = DarkColorTokens

    val background: ColorScheme = ColorScheme(
        light = lightColorTokens.background,
        dark = darkColorTokens.background
    )

    val surfaceContainer: ColorScheme = ColorScheme(
        light = lightColorTokens.surfaceContainer,
        dark = darkColorTokens.surfaceContainer
    )

    val primaryColor: ColorScheme = ColorScheme(
        light = lightColorTokens.primary,
        dark = darkColorTokens.primary
    )

    val onPrimary: ColorScheme = ColorScheme(
        light = lightColorTokens.onPrimary,
        dark = darkColorTokens.onPrimary
    )

    val secondary: ColorScheme = ColorScheme(
        light = lightColorTokens.secondary,
        dark = darkColorTokens.secondary
    )

    val tertiary: ColorScheme = ColorScheme(
        light = lightColorTokens.tertiary,
        dark = darkColorTokens.tertiary
    )

    val overlayPrimary: Int
        get() = ColorTokens.overlayPrimary

    /**
     * Adopts the platform's night mode, then paints the system bars.
     *
     * A caller that owns its own dark-mode source (AppleUX stores the user
     * choice in Settings.System) must set [isDarkMode] afterwards and call
     * [applySystemBars] again, otherwise this overwrites their choice.
     */
    fun applyToActivity(activity: android.app.Activity) {
        isDarkMode = when (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        applySystemBars(activity)
    }

    /** Repaints the status and navigation bars for the current [isDarkMode]. */
    fun applySystemBars(activity: android.app.Activity) {
        val window = activity.window
        val background = if (isDarkMode) DarkColorTokens.background else LightColorTokens.background
        window.statusBarColor = background
        window.navigationBarColor = background
        // A13 (B verdict: Settings title renders under the stock status bar,
        // clock collides with the title). The window paints edge-to-edge but
        // nothing reserved the status-bar band, so content started at y=0.
        // iOS content NEVER renders under the status bar. One edit here
        // covers every owned activity that calls this post-setContentView;
        // the base padding is captured once so re-dispatches (rotation,
        // insets changes) never accumulate it. Same pattern as
        // AppleuxAppChrome in apps-shared.
        val content = activity.findViewById<android.view.View>(android.R.id.content) ?: return
        val base = intArrayOf(content.paddingLeft, content.paddingTop,
            content.paddingRight, content.paddingBottom)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val top = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(base[0], base[1] + top, base[2], base[3])
            insets
        }
        // A17 (rule 13 — the double-bar law): our apps are SYSTEM apps; the
        // chrome is OURS. Hiding the stock status bar removes the second
        // bar B flagged; the inset reservation above keeps content below
        // the cutout band where OUR bar renders. One edit, every owned
        // activity. Best-effort: a controller failure degrades to stock,
        // never crashes.
        runCatching {
            androidx.core.view.WindowInsetsControllerCompat(window, content)
                .hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }
}

object LightColorTokens {
    val background: Int = 0xFFF5F5F7.toInt()
    val surfaceContainer: Int = 0xFFFFFFFF.toInt()
    val primary: Int = 0xFF007AFF.toInt()
    val onPrimary: Int = 0xFFFFFFFF.toInt()
    val secondary: Int = 0xFF8E8E93.toInt()
    val tertiary: Int = 0xFF32D74B.toInt()
    val label: Int = 0xFF000000.toInt()
    val labelSecondary: Int = 0xFF8E8E93.toInt()
    val separator: Int = 0xFFD1D1D6.toInt()
}

object DarkColorTokens {
    val background: Int = 0xFF000000.toInt()
    val surfaceContainer: Int = 0xFF1C1C1E.toInt()
    val primary: Int = 0xFF0A84FF.toInt()
    val onPrimary: Int = 0xFFFFFFFF.toInt()
    val secondary: Int = 0xFF97979E.toInt()
    val tertiary: Int = 0xFF32D74B.toInt()
    val label: Int = 0xFFFFFFFF.toInt()
    val labelSecondary: Int = 0xFF97979E.toInt()
    val separator: Int = 0xFF323234.toInt()
}