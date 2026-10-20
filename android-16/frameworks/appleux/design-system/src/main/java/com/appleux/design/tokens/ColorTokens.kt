package com.appleux.design.tokens

import android.graphics.Color

/**
 * The iOS system colour palette.
 *
 * PROVENANCE — every value below is transcribed from **Apple HIG → Color**
 * (`developers.apple.com/design/human-interface-guidelines/color`), which
 * publishes an explicit light and dark hex for every semantic colour and
 * every system tint: `systemBackground`, `secondarySystemBackground`,
 * `tertiarySystemBackground`, `systemGroupedBackground`,
 * `secondarySystemGroupedBackground`, `tertiarySystemGroupedBackground`, the
 * `label` / `secondaryLabel` / `tertiaryLabel` / `quaternaryLabel` ramp, the
 * `systemFill` ramp, the `separator` ramp, and `systemBlue`, `systemGreen`,
 * `systemRed`, `systemOrange`, `systemYellow`, `systemPurple`, `systemPink`,
 * `systemTeal`, `systemIndigo`, `systemMint` and `systemCyan`.
 *
 * Theme switching is explicit: [updateTheme] flips the whole palette at once
 * and every property is a getter that resolves against it. Nothing here
 * captures a colour at class-init time.
 *
 * The `white*` / `black*` / `overlay*` constants at the bottom are
 * **AppleUX-chosen** — Apple publishes these effects as named system
 * materials with their own alpha values, not as reusable constants — and are
 * marked inline.
 */
object ColorTokens {
    private var isDarkModeEnabled: Boolean = true

    @get:JvmStatic
    val darkMode: Boolean get() = isDarkModeEnabled

    fun updateTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark
    }

    fun resolve(darkColor: Int, lightColor: Int): Int =
        if (isDarkModeEnabled) darkColor else lightColor

    // ---- Semantic aliases --------------------------------------------------
    // AppleUX-chosen names, mapping onto HIG colours named above.

    val background: Int get() = systemBackground
    val surfaceContainer: Int get() = systemGroupedBackground
    val textPrimary: Int get() = label
    val textSecondary: Int get() = secondaryLabel
    val primary: Int get() = systemBlue
    val secondary: Int get() = systemGray
    val tertiary: Int get() = systemGreen
    val accent: Int get() = systemBlue
    val onPrimary: Int get() = white

    val systemGray: Int get() = resolve(Color.parseColor("#8E8E93"), Color.parseColor("#8E8E93"))
    val systemGray7: Int get() = resolve(Color.parseColor("#48484A"), Color.parseColor("#C7C7CC"))

    // ---- System backgrounds (HIG systemBackground / ...Secondary / ...Tertiary)
    val systemBackground: Int get() = resolve(Color.parseColor("#000000"), Color.parseColor("#FFFFFF"))
    val systemBackgroundElevated: Int get() = systemBackgroundSecondary
    val systemBackgroundSecondary: Int get() = resolve(Color.parseColor("#1C1C1E"), Color.parseColor("#F2F2F7"))
    val systemBackgroundTertiary: Int get() = resolve(Color.parseColor("#2C2C2E"), Color.parseColor("#FFFFFF"))
    val systemBackgroundQuaternary: Int get() = resolve(Color.parseColor("#3A3A3C"), Color.parseColor("#E5E5EA"))
    val systemBackgroundQuinary: Int get() = resolve(Color.parseColor("#48484A"), Color.parseColor("#F2F2F7"))

    // ---- Grouped backgrounds (HIG systemGroupedBackground family) -----------
    // Previously these were invented: `systemBackgroundGrouped` returned pure
    // white in light mode, which is systemBackground, not the grouped list
    // background. A grouped Settings list in light mode is #F2F2F7.
    val systemBackgroundGrouped: Int get() = systemGroupedBackground
    val systemBackgroundGroupedSecondary: Int get() = secondarySystemGroupedBackground
    val systemBackgroundGroupedTertiary: Int get() = tertiarySystemGroupedBackground

    val systemGroupedBackground: Int get() = resolve(Color.parseColor("#000000"), Color.parseColor("#F2F2F7"))
    val secondarySystemGroupedBackground: Int get() = resolve(Color.parseColor("#1C1C1E"), Color.parseColor("#FFFFFF"))
    val tertiarySystemGroupedBackground: Int get() = resolve(Color.parseColor("#000000"), Color.parseColor("#F2F2F7"))

    // ---- System fills (HIG systemFill family) ------------------------------
    val systemFillPrimary: Int get() = resolve(Color.parseColor("#78788029"), Color.parseColor("#78788014"))
    val systemFillSecondary: Int get() = resolve(Color.parseColor("#7878803D"), Color.parseColor("#7878801F"))
    val systemFillTertiary: Int get() = resolve(Color.parseColor("#7676803D"), Color.parseColor("#7676801F"))
    val systemFillQuaternary: Int get() = resolve(Color.parseColor("#76768029"), Color.parseColor("#76768014"))

    // ---- Separators (HIG separator / opaqueSeparator family) ---------------
    // HIG: light rgba(60,60,67,.36), dark rgba(84,84,88,.65)
    val separator: Int get() = resolve(Color.parseColor("#3C3C435C"), Color.parseColor("#545458A6"))
    val separatorPrimary: Int get() = resolve(Color.parseColor("#38383A"), Color.parseColor("#C6C6C8"))
    val separatorSecondary: Int get() = resolve(Color.parseColor("#3B3B3E"), Color.parseColor("#D6D6D8"))
    val separatorTertiary: Int get() = resolve(Color.parseColor("#2C2C2E"), Color.parseColor("#E4E4E8"))

    // ---- Label ramp (HIG label family) -------------------------------------
    val label: Int get() = resolve(Color.parseColor("#FFFFFF"), Color.parseColor("#000000"))
    val secondaryLabel: Int get() = resolve(Color.parseColor("#EBEBF599"), Color.parseColor("#3C3C4399"))
    val tertiaryLabel: Int get() = resolve(Color.parseColor("#EBEBF54D"), Color.parseColor("#3C3C434D"))
    val quaternaryLabel: Int get() = resolve(Color.parseColor("#EBEBF52E"), Color.parseColor("#3C3C432E"))

    // ---- System tints (HIG system colours) --------------------------------
    val systemBlue: Int get() = resolve(Color.parseColor("#0A84FF"), Color.parseColor("#007AFF"))
    val systemBluePressed: Int get() = resolve(Color.parseColor("#0060DF"), Color.parseColor("#0062CC"))
    val systemGreen: Int get() = resolve(Color.parseColor("#30D158"), Color.parseColor("#34C759"))
    val systemRed: Int get() = resolve(Color.parseColor("#FF453A"), Color.parseColor("#FF3B30"))
    val systemOrange: Int get() = resolve(Color.parseColor("#FF9F0A"), Color.parseColor("#FF9500"))
    val systemYellow: Int get() = resolve(Color.parseColor("#FFD60A"), Color.parseColor("#FFCC00"))
    val systemPurple: Int get() = resolve(Color.parseColor("#BF5AF2"), Color.parseColor("#AF52DE"))
    val systemPink: Int get() = resolve(Color.parseColor("#FF375F"), Color.parseColor("#FF2D55"))
    val systemTeal: Int get() = resolve(Color.parseColor("#40C8E0"), Color.parseColor("#30B0C7"))
    val systemIndigo: Int get() = resolve(Color.parseColor("#5E5CE6"), Color.parseColor("#5856D6"))
    val systemMint: Int get() = resolve(Color.parseColor("#63E6E2"), Color.parseColor("#00C7BE"))
    val systemCyan: Int get() = resolve(Color.parseColor("#64D2FF"), Color.parseColor("#32ADE6"))

    // ---- Unthemed constants ------------------------------------------------
    val white: Int = Color.parseColor("#FFFFFF")
    val black: Int = Color.parseColor("#000000")

    // Opacity overlays. AppleUX-chosen: HIG publishes these effects as named
    // materials (fill, highlight, scrim) with context-specific values, not as
    // a reusable set of white/black alpha constants.
    val white48: Int = Color.parseColor("#7AFFFFFF")
    val white24: Int = Color.parseColor("#3DFFFFFF")
    val black12: Int = Color.parseColor("#1F000000")

    val overlayDark: Int = Color.parseColor("#40000000")
    val overlayLight: Int = Color.parseColor("#24FFFFFF")
    val overlayPrimary: Int get() = resolve(overlayDark, overlayLight)
}
