package com.appleux.design.tokens

/**
 * App-icon + home-grid metrics, iOS 27.
 * PROVENANCE: iPhone 11 IPSW DefaultIconState.plist (4 cols x 6 rows, dock 4,
 * Today 8) from /home/elfakiris/ios-ui-analysis/layouts/ + Apple icon
 * template .ai squircle (n~=4.4, 40 Beziers) + rock panel 392.73x875.64dp.
 */
object AppIconTokens {
    const val GRID_COLUMNS_IPHONE = 4
    const val GRID_ROWS_PER_PAGE = 6
    const val ICONS_PER_PAGE = 24
    const val DOCK_SLOTS = 4
    const val TODAY_SLOTS = 8
    /** Superellipse exponent measured from Apple's own icon template. */
    const val SQUIRCLE_N = 4.4f
    const val ICON_SIZE_DP = 60f
    const val ICON_CORNER_CONTINUOUS = true
    const val FOLDER_GRID = 3
    const val FOLDER_MAX_SHOWN = 9
}
