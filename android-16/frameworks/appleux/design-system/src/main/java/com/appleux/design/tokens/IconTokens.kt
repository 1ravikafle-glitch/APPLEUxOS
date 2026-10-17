package com.appleux.design.tokens

/**
 * Icon sizing and the SF-Symbol name registry.
 *
 * PROVENANCE — **AppleUX-chosen throughout.** Apple publishes no numeric spec
 * for SF Symbols; the symbols themselves are licensed for use on Apple
 * platforms only and cannot be redistributed in an APK, which is why this
 * module ships hand-built `sf_*` vectors instead.
 *
 * [SYSTEM_ICONS] maps iOS-style semantic names ("personal_hotspot",
 * "screen_mirroring") onto those local vectors. It is a lookup table, not a
 * claim about Apple's asset names — the right-hand column is ours.
 */
object IconTokens {
    const val SIZE_TINY: Float = 12f
    const val SIZE_SMALL: Float = 16f
    const val SIZE_MEDIUM: Float = 22f
    const val SIZE_LARGE: Float = 28f
    const val SIZE_EXTRA_LARGE: Float = 32f
    const val SIZE_STATUS_BAR: Float = 14f

    const val STROKE_WEIGHT_LIGHT: Float = 1.5f
    const val STROKE_WEIGHT_REGULAR: Float = 2.0f
    const val STROKE_WEIGHT_MEDIUM: Float = 2.5f
    const val STROKE_WEIGHT_BOLD: Float = 3.0f

    const val CORNER_RADIUS_ICON: Float = 0f
    const val CORNER_RADIUS_SYSTEM: Float = 4f

    val ICON_ASPECT_RATIO: Float = 1f

    val SYSTEM_ICON_SIZE_STATUS_BAR: Float = 12f
    val SYSTEM_ICON_SIZE_TOOLBAR: Float = 22f
    val SYSTEM_ICON_SIZE_TAB_BAR: Float = 25f
    val SYSTEM_ICON_SIZE_HOME_SCREEN: Float = 60f

    object States {
        const val NORMAL: Int = 0
        const val PRESSED: Int = 1
        const val ACTIVE: Int = 2
        const val DISABLED: Int = 3
        const val SELECTED: Int = 4
        const val TRANSITIONING: Int = 5
    }

    val SYSTEM_ICONS: Map<String, String> = mapOf(
        "wifi" to "system_wifi",
        "bluetooth" to "system_bluetooth",
        "airplane" to "system_airplane",
        "cellular" to "system_cellular",
        "battery" to "system_battery",
        "battery_half" to "system_battery_half",
        "battery_low" to "system_battery_low",
        "brightness" to "system_brightness",
        "volume" to "system_volume",
        "volume_low" to "system_volume_low",
        "volume_high" to "system_volume_high",
        "focus" to "system_focus",
        "dnd" to "system_dnd",
        "flashlight" to "system_flashlight",
        "camera" to "system_camera",
        "calculator" to "system_calculator",
        "clock" to "system_clock",
        "calendar" to "system_calendar",
        "photos" to "system_photos",
        "maps" to "system_maps",
        "messages" to "system_messages",
        "phone" to "system_phone",
        "mail" to "system_mail",
        "settings" to "system_settings",
        "search" to "system_search",
        "share" to "system_share",
        "copy" to "system_copy",
        "paste" to "system_paste",
        "edit" to "system_edit",
        "lock" to "system_lock",
        "unlock" to "system_unlock",
        "play" to "system_play",
        "pause" to "system_pause",
        "next" to "system_next",
        "previous" to "system_previous",
        "power" to "system_power",
        "reboot" to "system_reboot",
        "airdrop" to "system_airdrop",
        "personal_hotspot" to "system_hotspot",
        "screen_mirroring" to "system_screen_mirroring",
        "accessibility" to "system_accessibility",
        "low_power" to "system_low_power",
        "location" to "system_location",
        "nfc" to "system_nfc",
        "wallet" to "system_wallet",
        "home" to "system_home",
        "app_library" to "system_app_library",
        "dock" to "system_dock",
        "widget" to "system_widget",
        "chevron_up" to "system_chevron_up",
        "chevron_down" to "system_chevron_down",
        "chevron_left" to "system_chevron_left",
        "chevron_right" to "system_chevron_right",
        "close" to "system_close",
        "add" to "system_add",
        "remove" to "system_remove",
        "checkmark" to "system_checkmark",
        "xmark" to "system_xmark",
    )
}