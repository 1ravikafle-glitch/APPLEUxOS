package com.appleux.design.components

import android.graphics.Path
import com.appleux.design.R

object AppleuxIconRegistry {

    private val paths: Map<String, Path> = buildMap {
        put("system_wifi", createWifiPath())
        put("system_bluetooth", createBluetoothPath())
        put("system_airplane", createAirplanePath())
        put("system_cellular", createCellularPath())
        put("system_battery", createBatteryPath())
        put("system_brightness", createBrightnessPath())
        put("system_volume", createVolumePath())
        put("system_focus", createFocusPath())
        put("system_dnd", createDndPath())
        put("system_flashlight", createFlashlightPath())
        put("system_camera", createCameraPath())
        put("system_calculator", createCalculatorPath())
        put("system_clock", createClockPath())
        put("system_calendar", createCalendarPath())
        put("system_photos", createPhotosPath())
        put("system_maps", createMapsPath())
        put("system_messages", createMessagesPath())
        put("system_phone", createPhonePath())
        put("system_mail", createMailPath())
        put("system_settings", createSettingsPath())
        put("system_search", createSearchPath())
        put("system_share", createSharePath())
        put("system_edit", createEditPath())
        put("system_lock", createLockPath())
        put("system_play", createPlayPath())
        put("system_pause", createPausePath())
        put("system_chevron_up", createChevronUpPath())
        put("system_chevron_down", createChevronDownPath())
        put("system_chevron_left", createChevronLeftPath())
        put("system_chevron_right", createChevronRightPath())
        put("system_close", createClosePath())
        put("system_add", createAddPath())
        put("system_checkmark", createCheckmarkPath())
    }

    fun getPath(name: String): Path? = paths[name]

    fun getIconNames(): List<String> = paths.keys.toList()

    /**
     * Real Apple SF Symbols geometry (wght 400, extracted from the SF Symbols 27
     * fallback font) as VectorDrawables. Preferred over the hand-drawn [paths]
     * below; names absent here fall back to the canvas paths (kept for symbols
     * the fallback font does not ship: bluetooth, sun, battery, airplane…).
     */
    private val vectorDrawables: Map<String, Int> = buildMap {
        put("system_add", R.drawable.sf_add)
        put("system_app_library", R.drawable.sf_app_library)
        put("system_bell", R.drawable.sf_bell)
        put("system_calendar", R.drawable.sf_calendar)
        put("system_camera", R.drawable.sf_camera)
        put("system_car", R.drawable.sf_car)
        put("system_cellular", R.drawable.sf_cellular)
        put("system_checkmark", R.drawable.sf_checkmark)
        put("system_chevron_down", R.drawable.sf_chevron_down)
        put("system_chevron_left", R.drawable.sf_chevron_left)
        put("system_chevron_right", R.drawable.sf_chevron_right)
        put("system_chevron_up", R.drawable.sf_chevron_up)
        put("system_clock", R.drawable.sf_clock)
        put("system_close", R.drawable.sf_close)
        put("system_creditcard", R.drawable.sf_creditcard)
        put("system_drop", R.drawable.sf_drop)
        put("system_flashlight", R.drawable.sf_flashlight)
        put("system_focus", R.drawable.sf_focus)
        put("system_folder", R.drawable.sf_folder)
        put("system_heart", R.drawable.sf_heart)
        put("system_home", R.drawable.sf_home)
        put("system_key", R.drawable.sf_key)
        put("system_location", R.drawable.sf_location)
        put("system_lock", R.drawable.sf_lock)
        put("system_low_power", R.drawable.sf_low_power)
        put("system_mail", R.drawable.sf_mail)
        put("system_maps", R.drawable.sf_maps)
        put("system_messages", R.drawable.sf_messages)
        put("system_mic", R.drawable.sf_mic)
        put("system_next", R.drawable.sf_next)
        put("system_pause", R.drawable.sf_pause)
        put("system_personal_hotspot", R.drawable.sf_personal_hotspot)
        put("system_phone", R.drawable.sf_phone)
        put("system_play", R.drawable.sf_play)
        put("system_power", R.drawable.sf_power)
        put("system_previous", R.drawable.sf_previous)
        put("system_remove", R.drawable.sf_remove)
        put("system_settings", R.drawable.sf_settings)
        put("system_star", R.drawable.sf_star)
        put("system_ticket", R.drawable.sf_ticket)
        put("system_tram", R.drawable.sf_tram)
        put("system_trash", R.drawable.sf_trash)
        put("system_volume", R.drawable.sf_volume)
        put("system_wallet", R.drawable.sf_wallet)
        put("system_wifi", R.drawable.sf_wifi)
        put("system_xmark", R.drawable.sf_xmark)
    }

    fun getDrawableRes(name: String): Int? = vectorDrawables[name]

    private fun createWifiPath(): Path = Path().apply {
        // Wi-Fi symbol: 3 arcs (signal waves)
        val size = 24f
        val cx = size / 2
        val cy = size / 2
        val radius1 = 10f
        val radius2 = 7.5f
        val radius3 = 5f
        val strokeWidth = 1.5f

        arcTo(0f, 0f, size, size, 180f - 30f, 60f, false)
    }

    private fun createBluetoothPath(): Path = Path().apply {
        moveTo(12f, 4f)
        lineTo(8f, 12f)
        lineTo(12f, 20f)
        lineTo(8f, 20f)
        close()
        moveTo(16f, 4f)
        lineTo(20f, 12f)
        lineTo(16f, 20f)
        close()
    }

    private fun createAirplanePath(): Path = Path().apply {
        moveTo(12f, 4f)
        lineTo(18f, 14f)
        lineTo(6f, 10f)
        lineTo(18f, 10f)
        lineTo(6f, 14f)
        lineTo(12f, 4f)
        close()
    }

    private fun createCellularPath(): Path = Path().apply {
        moveTo(12f, 4f)
        lineTo(12f, 20f)
        lineTo(10f, 20f)
        lineTo(10f, 4f)
        close()
    }

    private fun createBatteryPath(): Path = Path().apply {
        moveTo(6f, 6f)
        lineTo(18f, 6f)
        lineTo(18f, 18f)
        lineTo(6f, 18f)
        close()
        moveTo(18f, 8f)
        lineTo(20f, 8f)
        lineTo(20f, 16f)
        lineTo(18f, 16f)
        close()
    }

    private fun createBrightnessPath(): Path = Path().apply {
        // Sun with rays
        val cx = 12f
        val cy = 12f
        val r = 6f
        moveTo(cx, cy - r)
        lineTo(cx, cy - r - 2)
        moveTo(cx, cy + r)
        lineTo(cx, cy + r + 2)
        moveTo(cx - r, cy)
        lineTo(cx - r - 2, cy)
        moveTo(cx + r, cy)
        lineTo(cx + r + 2, cy)
        moveTo(cx - 4f, cy - 4f)
        lineTo(cx - 5.5f, cy - 5.5f)
        moveTo(cx + 4f, cy + 4f)
        lineTo(cx + 5.5f, cy + 5.5f)
        moveTo(cx - 4f, cy + 4f)
        lineTo(cx - 5.5f, cy + 5.5f)
        moveTo(cx + 4f, cy - 4f)
        lineTo(cx + 5.5f, cy - 5.5f)
        addCircle(cx, cy, r, Path.Direction.CW)
    }

    private fun createVolumePath(): Path = Path().apply {
        moveTo(8f, 20f)
        lineTo(4f, 12f)
        lineTo(8f, 4f)
        lineTo(10f, 4f)
        lineTo(14f, 12f)
        lineTo(10f, 20f)
        close()
    }

    private fun createFocusPath(): Path = Path().apply {
        // Moon/star symbol for Focus/Do Not Disturb
        val cx = 12f
        val cy = 12f
        val outerR = 8f
        val innerR = 6f
        addCircle(cx, cy, outerR, Path.Direction.CW)
        val innerRect = android.graphics.RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        addOval(innerRect, Path.Direction.CCW)
    }

    private fun createDndPath(): Path = createFocusPath()

    private fun createFlashlightPath(): Path = Path().apply {
        moveTo(12f, 4f)
        lineTo(16f, 16f)
        lineTo(8f, 16f)
        close()
    }

    private fun createCameraPath(): Path = Path().apply {
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        lineTo(20f, 18f)
        lineTo(4f, 18f)
        close()
        addCircle(12f, 12f, 3f, Path.Direction.CW)
    }

    private fun createCalculatorPath(): Path = Path().apply {
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        lineTo(20f, 18f)
        lineTo(4f, 18f)
        close()
    }

    private fun createClockPath(): Path = Path().apply {
        addCircle(12f, 12f, 8f, Path.Direction.CW)
        moveTo(12f, 12f)
        lineTo(12f, 8f)
        moveTo(12f, 12f)
        lineTo(16f, 12f)
    }

    private fun createCalendarPath(): Path = Path().apply {
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        lineTo(20f, 18f)
        lineTo(4f, 18f)
        close()
    }

    private fun createPhotosPath(): Path = Path().apply {
        addCircle(12f, 12f, 8f, Path.Direction.CW)
    }

    private fun createMapsPath(): Path = Path().apply {
        addCircle(12f, 12f, 8f, Path.Direction.CW)
    }

    private fun createMessagesPath(): Path = Path().apply {
        moveTo(6f, 6f)
        lineTo(18f, 6f)
        lineTo(18f, 18f)
        lineTo(6f, 18f)
        close()
    }

    private fun createPhonePath(): Path = Path().apply {
        moveTo(12f, 6f)
        lineTo(16f, 12f)
        lineTo(8f, 12f)
        close()
    }

    private fun createMailPath(): Path = Path().apply {
        moveTo(4f, 8f)
        lineTo(12f, 14f)
        lineTo(20f, 8f)
        lineTo(20f, 18f)
        lineTo(4f, 18f)
        close()
    }

    private fun createSettingsPath(): Path = Path().apply {
        addCircle(12f, 12f, 9f, Path.Direction.CW)
        addCircle(12f, 12f, 4.5f, Path.Direction.CCW)
    }

    private fun createSearchPath(): Path = Path().apply {
        addCircle(12f, 12f, 6f, Path.Direction.CW)
        moveTo(16f, 16f)
        lineTo(20f, 20f)
    }

    private fun createSharePath(): Path = Path().apply {
        moveTo(12f, 6f)
        lineTo(18f, 14f)
        lineTo(6f, 14f)
        close()
        moveTo(6f, 20f)
        lineTo(18f, 20f)
    }

    private fun createEditPath(): Path = Path().apply {
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        lineTo(20f, 8f)
        lineTo(4f, 8f)
        close()
        moveTo(8f, 10f)
        lineTo(16f, 10f)
        lineTo(16f, 18f)
        lineTo(8f, 18f)
        close()
    }

    private fun createLockPath(): Path = Path().apply {
        moveTo(8f, 12f)
        lineTo(16f, 12f)
        lineTo(16f, 16f)
        lineTo(8f, 16f)
        close()
        moveTo(12f, 8f)
        lineTo(12f, 12f)
        lineTo(10f, 12f)
        lineTo(10f, 8f)
        arcTo(8f, 8f, 16f, 12f, 180f, 180f, false)
    }

    private fun createPlayPath(): Path = Path().apply {
        moveTo(10f, 6f)
        lineTo(18f, 12f)
        lineTo(10f, 18f)
        close()
    }

    private fun createPausePath(): Path = Path().apply {
        moveTo(6f, 6f)
        lineTo(10f, 6f)
        lineTo(10f, 18f)
        lineTo(6f, 18f)
        close()
        moveTo(14f, 6f)
        lineTo(18f, 6f)
        lineTo(18f, 18f)
        lineTo(14f, 18f)
        close()
    }

    private fun createChevronUpPath(): Path = Path().apply {
        moveTo(6f, 16f)
        lineTo(12f, 8f)
        lineTo(18f, 16f)
        close()
    }

    private fun createChevronDownPath(): Path = Path().apply {
        moveTo(6f, 8f)
        lineTo(12f, 16f)
        lineTo(18f, 8f)
        close()
    }

    private fun createChevronLeftPath(): Path = Path().apply {
        moveTo(16f, 6f)
        lineTo(8f, 12f)
        lineTo(16f, 18f)
        close()
    }

    private fun createChevronRightPath(): Path = Path().apply {
        moveTo(8f, 6f)
        lineTo(16f, 12f)
        lineTo(8f, 18f)
        close()
    }

    private fun createClosePath(): Path = Path().apply {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }

    private fun createAddPath(): Path = Path().apply {
        moveTo(12f, 6f)
        lineTo(12f, 18f)
        moveTo(6f, 12f)
        lineTo(18f, 12f)
    }

    private fun createCheckmarkPath(): Path = Path().apply {
        moveTo(8f, 14f)
        lineTo(11f, 17f)
        lineTo(16f, 10f)
    }
}