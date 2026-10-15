package com.appleux.systemui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.appleux.design.components.AppleuxControlCenter
import com.appleux.design.components.AppleuxNotificationShade
import com.appleux.design.components.AppleuxStatusBar
import com.appleux.design.runtime.AppleuxRuntimeConfig
import com.appleux.design.theme.AppleuxTheme

/**
 * AppleUX SystemUI service: Control Center + notification shade as
 * system overlays, with every tile wired to a real platform API.
 *
 * Boundaries (platform-enforced, not attempted):
 * - The stock status bar is NOT replaced: drawing over it needs the
 *   STATUS_BAR permission (platform signature). AppleuxStatusBar renders
 *   inside our own surfaces only.
 * - Wi-Fi and Bluetooth are DISCONNECT controls, not radio switches: since
 *   API 29 no normal app may enable or disable either radio, and iOS never
 *   did either ("Because Wi-Fi isn't turned off when you disconnect, AirDrop
 *   still works"). Each tap asks the platform to associate/disassociate and
 *   then re-reads the real state; a refusal demotes the tile to an honestly
 *   labelled Settings shortcut.
 */
class AppleuxSystemUIService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var notificationShade: AppleuxNotificationShade
    private lateinit var controlCenter: AppleuxControlCenter
    private val tileIds = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    private var notificationsShown = false
    private var controlCenterShown = false
    private var overlaysAttached = false

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Services never run applyToActivity: sync the theme explicitly or
        // every semantic color resolves to the dark default (black screens).
        AppleuxTheme.isDarkMode = AppleuxRuntimeConfig.readBoolean(
            contentResolver, AppleuxRuntimeConfig.KEY_DARK_MODE, false
        )
        startAsForeground()
        notificationShade = AppleuxNotificationShade(this)
        controlCenter = AppleuxControlCenter(this).apply { visibility = View.GONE }
        addControlTiles()
        // Live Wi-Fi association state. Polling isWifiEnabled cannot tell
        // "radio on" from "connected", and the tile toggles exactly that.
        observeWifiNetworks()
        // Background taps dismiss (the windows are fullscreen and modal).
        controlCenter.onBackgroundClick = { hideControlCenter() }
        // Swipe up dismisses too: a swipe never fires onClick, so without
        // this an open panel becomes an inescapable dead end (§27/§32).
        com.appleux.design.components.AppleuxPanelDismissHandler.attach(
            controlCenter
        ) { hideControlCenter() }
        notificationShade.setOnClickListener {
            // Only background taps reach here; notification rows consume theirs.
            hideNotificationShade()
        }
        com.appleux.design.components.AppleuxPanelDismissHandler.attach(
            notificationShade
        ) { hideNotificationShade() }
        // Live platform feed (empty until the user grants Notification
        // Access; never demo data in the production path).
        AppleuxNotificationBus.subscribe { data ->
            notificationShade.post {
                notificationShade.setNotifications(
                    data,
                    { _, action -> runCatching { action.intent.send() } },
                    { tapped ->
                        runCatching { tapped.contentIntent?.send() }
                        notificationShade.collapse()
                        notificationShade.postDelayed({
                            notificationShade.visibility = View.GONE
                        }, 350L)
                    }
                )
            }
        }
        notificationShade.setOnDismissListener { data ->
            // iOS only lets dismissible notifications be swiped away;
            // ongoing / FLAG_NO_CLEAR ones stay until they end themselves.
            // The swipe has already taken the row off screen, so republish the
            // live list to put it back instead of silently swallowing the tap.
            if (!data.isClearable) {
                android.util.Log.w(
                    TAG,
                    "dismiss refused: ${data.packageName} is ongoing/NO_CLEAR"
                )
                AppleuxNotificationBus.refresh()
            } else {
                data.key?.let { AppleuxNotificationBus.requestDismiss(it) }
            }
        }
        notificationShade.setOnClearAllListener {
            AppleuxNotificationBus.requestClearAll()
        }
        notificationShade.setNotifications(emptyList())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        attachOverlays()
        // DEBUG receipt: proves cross-component delivery (remove before release).
        runCatching {
            android.widget.Toast.makeText(this, "SVC:${intent?.action}", android.widget.Toast.LENGTH_SHORT).show()
        }
        when (intent?.action) {
            ACTION_TOGGLE_NOTIFICATIONS -> {
                if (notificationsShown) {
                    notificationShade.collapse()
                } else {
                    notificationShade.visibility = View.VISIBLE
                    notificationShade.expand()
                }
                notificationsShown = !notificationsShown
            }
            ACTION_SHOW_NOTIFICATIONS -> {
                notificationShade.visibility = View.VISIBLE
                if (!notificationsShown) notificationShade.expand()
                notificationsShown = true
            }
            ACTION_HIDE_NOTIFICATIONS -> {
                if (notificationsShown) notificationShade.collapse()
                notificationsShown = false
            }
            ACTION_TOGGLE_CONTROL_CENTER -> {
                if (!controlCenterShown) {
                    refreshTileStates()
                    controlCenter.visibility = View.VISIBLE
                } else {
                    hideControlCenter()
                }
                controlCenterShown = !controlCenterShown
            }
            ACTION_SHOW_CONTROL_CENTER -> {
                refreshTileStates()
                controlCenter.visibility = View.VISIBLE
                controlCenterShown = true
            }
            ACTION_HIDE_ALL -> {
                hideNotificationShade()
                hideControlCenter()
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "AppleUX SystemUI",
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        } catch (e: Exception) {}
        val note = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AppleUX SystemUI")
            .setContentText("Control Center and notifications")
            .setSmallIcon(android.R.drawable.presence_online)
            .setOngoing(true)
            .build()
        try {
            startForeground(NOTIF_ID, note)
        } catch (e: Exception) {
            // Foreground start can fail on restricted builds; the service
            // still runs (START_STICKY) with a higher kill priority loss.
        }
    }

    /**
     * Single close path per panel. Guarded so a dismiss gesture racing a
     * toggle intent cannot flip the flag back to "open" (§26: the service is
     * the sole owner of panel state).
     */
    private fun hideNotificationShade() {
        if (notificationShade.visibility != View.GONE) notificationShade.collapse()
        notificationsShown = false
    }

    private fun hideControlCenter() {
        controlCenter.visibility = View.GONE
        controlCenterShown = false
    }

    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        // Without this the window is inset by the display cutout and the top
        // band of the panel leaks the launcher behind it (emulator: 1080x2209
        // instead of 1080x2400). iOS panels own the whole display.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        // Pin to the top edge; the default let the frame float.
        gravity = android.view.Gravity.TOP or android.view.Gravity.START

        // REAL frosted glass. RenderEffect (AppleuxGlass) only blurs a window's
        // OWN drawables — it cannot touch the launcher behind us, which is why
        // the card was see-through with labels colliding. FLAG_BLUR_BEHIND is
        // the platform primitive that blurs what is behind the window, which
        // is exactly the iOS "Liquid Glass" material. API 31+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            blurBehindRadius = GLASS_BLUR_BEHIND_RADIUS_PX
        }
    }

    /**
     * iOS Control Center material blur radius. Blur-behind is in PIXELS, and
     * the panel must look identical on the effective-430dpi POCO M5 and on
     * lower density emulators, so the radius is scaled from dp rather than fixed.
     */
    private val GLASS_BLUR_BEHIND_RADIUS_PX: Int
        get() = (60 * resources.displayMetrics.density).toInt()

    /**
     * Attach state is NOT private: when the overlay appop is missing the
     * panels cannot exist and every toggle is a silent no-op. That failure
     * mode cost a full debugging session (§34), so it is tracked, logged at
     * error level, reported to the user, and re-checked on every start
     * instead of being cached as a failure.
     */
    @Volatile
    var overlayAttached: Boolean = false
        private set

    /** Set once the user has been told, so we nag exactly once per grant. */
    private var missingOverlayReported = false

    private fun attachOverlays() {
        if (overlayAttached) return
        try {
            wm.addView(notificationShade, overlayParams())
            wm.addView(controlCenter, overlayParams())
            overlaysAttached = true
            overlayAttached = true
            // Both panels park off-screen/hidden; they appear only on toggle.
            notificationShade.visibility = View.GONE
            controlCenter.visibility = View.GONE
            controlCenterShown = false
            notificationsShown = false
        } catch (e: Exception) {
            // Not a warning: the whole UI is dead without this grant. Log at
            // error level, keep retrying on the next start, and tell the user
            // once with a route to the grant screen.
            overlayAttached = false
            android.util.Log.e(
                TAG,
                "FATAL: Control Center and Notification Center cannot exist " +
                    "without SYSTEM_ALERT_WINDOW. Every panel gesture will be " +
                    "a no-op until granted. Cause: ${e.message}"
            )
            if (!missingOverlayReported) {
                missingOverlayReported = true
                reportMissingOverlayPermission()
            }
        }
    }

    /** One-shot user-facing notice + a Settings row is wired in-app. */
    private fun reportMissingOverlayPermission() {
        runCatching {
            android.widget.Toast.makeText(
                this,
                "AppleUX needs \"display over other apps\" for Control Center",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---- Real tile actions ----

    private fun addControlTiles() {
        addTile(
            "wifi",
            if (wifiIsSettingsOnly) WIFI_SETTINGS_LABEL else "Wi-Fi",
            "system_wifi",
            toggle = !wifiIsSettingsOnly
        ) { toggleWifi() }
        addTile(
            "bluetooth",
            if (bluetoothIsSettingsOnly) BLUETOOTH_SETTINGS_LABEL else "Bluetooth",
            "system_bluetooth",
            toggle = !bluetoothIsSettingsOnly
        ) { toggleBluetooth() }
        addTile("airplane", "Airplane", "system_airplane", true) { toggleAirplane() }
        addTile("flashlight", "Flashlight", "system_flashlight", true) { toggleTorch() }
        addTile("dnd", "Do Not Disturb", "system_dnd", true) { toggleDnd() }
        addTile("brightness", "Brightness", "system_brightness", false) { cycleBrightness() }
    }

    private fun addTile(id: String, name: String, icon: String, toggle: Boolean, onClick: () -> Unit) {
        val tile = AppleuxControlCenter.ControlTile(
            id = id, name = name, iconName = icon, isToggle = toggle,
            onClick = {
                onClick()
                refreshTileStates()
            }
        )
        controlCenter.addTile(tile)
        tileIds.add(id)
    }

    /**
     * Rebuild the grid in place. Used when a radio tile loses its toggle
     * behaviour: the Control Tile is immutable, so replacing the whole row in
     * its original order is simpler than mutating a rendered tile.
     */
    private fun rebuildTiles() {
        tileIds.forEach { controlCenter.removeTile(it) }
        tileIds.clear()
        addControlTiles()
        refreshTileStates()
    }

    private fun refreshTileStates() {
        setTile("wifi", isWifiConnected())
        setTile("bluetooth", isBluetoothConnected())
        setTile("airplane", isAirplaneOn())
        setTile("dnd", isDndOn())
        setTile("flashlight", torchOn)
        // Brightness is a level, not a switch: the control reads as raised
        // whenever the screen sits anywhere above the bottom of the ladder.
        setTile("brightness", brightnessStep() > 0)
        refreshHeader()
    }

    /**
     * Header data is advisory and must never throw: every platform read is
     * permission-gated on some builds, and a failed read would take the
     * whole Control Center down with it.
     */
    private fun refreshHeader() {
        try {
            val radios = mutableListOf<String>()
            if (isWifiEnabled()) radios.add("Wi-Fi")
            if (bluetoothAdapter()?.isEnabled == true) radios.add("Bluetooth")
            if (isAirplaneOn()) {
                // Airplane mode suppresses radios; show the mode instead.
                radios.clear()
            }
            val (pct, charging) =
                com.appleux.design.components.AppleuxControlCenterHeader
                    .batteryState(this)
            controlCenter.bindHeader(
                activeRadios = radios,
                airplaneMode = isAirplaneOn(),
                charging = charging,
                batteryPercent = pct
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "header refresh: ${e.message}")
        }
    }

    private fun setTile(id: String, active: Boolean) {
        try { controlCenter.setTileActive(id, active) } catch (e: Exception) {}
    }

    // ---- Radios: disconnect, never disable ----
    //
    // iOS 27 iPhone User Guide: "In Control Center, tap Wi-Fi to disconnect;
    // to reconnect, tap it again. Because Wi-Fi isn't turned off when you
    // disconnect, AirDrop still works. To turn off Wi-Fi, go to the Settings
    // app." Since API 29 no normal app may flip the radio, so the faithful
    // Android mapping is: ask the platform to associate/disassociate (radio
    // stays on), then re-read the real state. If the read-back does not move,
    // the platform refused us — the tile then stops being a toggle and says
    // so instead of lying.

    private var wifiIsSettingsOnly = false
    private var bluetoothIsSettingsOnly = false

    private fun wifiManager(): WifiManager? = try {
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    } catch (e: Exception) {
        null
    }

    private fun connectivity(): ConnectivityManager? = try {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    } catch (e: Exception) {
        null
    }

    /** Wi-Fi networks the platform currently reports as up. */
    private val liveWifiNetworks = mutableSetOf<Network>()

    private fun hasWifiTransport(network: Network): Boolean = try {
        connectivity()?.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (e: Exception) {
        false
    }

    private val wifiObserver = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (hasWifiTransport(network)) liveWifiNetworks.add(network)
        }

        override fun onLost(network: Network) {
            liveWifiNetworks.remove(network)
        }
    }

    private fun wifiNetworkRequest(): NetworkRequest =
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

    private fun observeWifiNetworks() {
        try {
            connectivity()?.registerNetworkCallback(wifiNetworkRequest(), wifiObserver)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "wifi observer: ${e.message}")
        }
    }

    /** Radio switched on — NOT the same thing as being connected. */
    private fun isWifiEnabled(): Boolean = try {
        wifiManager()?.isWifiEnabled == true
    } catch (e: Exception) {
        false
    }

    /** Associated with a network: the state an iOS Wi-Fi control reflects. */
    private fun isWifiConnected(): Boolean {
        if (liveWifiNetworks.isNotEmpty()) return true
        // Fallback for the window before the observer has reported anything.
        return try {
            val info = wifiManager()?.connectionInfo
            info != null &&
                (info.networkId > 0 || info.supplicantState == SupplicantState.COMPLETED)
        } catch (e: Exception) {
            false
        }
    }

    private fun toggleWifi() {
        if (wifiIsSettingsOnly) {
            openWifiSettings()
            return
        }
        val cm = connectivity()
        if (cm == null) {
            refuseRadioToggle("wifi", "ConnectivityManager is unavailable")
            return
        }
        val wasConnected = isWifiConnected()
        if (wasConnected) disconnectWifi(cm) else connectWifi(cm)
        afterSettles("wifi", expectConnected = !wasConnected, current = { isWifiConnected() }) {
            refuseRadioToggle("wifi", it)
        }
    }

    /**
     * Disassociate while the radio stays on. Releases the Wi-Fi request this
     * app owns (the supported non-privileged path); with no request held it
     * falls back to the deprecated WifiManager.disconnect(), which pre-29
     * builds and some OEM images still honour.
     */
    private fun disconnectWifi(cm: ConnectivityManager) {
        val callback = wifiCallback
        if (callback != null) {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "wifi unregister: ${e.message}")
            }
            wifiCallback = null
            return
        }
        val legacy = try {
            @Suppress("DEPRECATION")
            wifiManager()?.disconnect() == true
        } catch (e: Exception) {
            android.util.Log.w(TAG, "wifi disconnect() blocked: ${e.message}")
            false
        }
        if (!legacy) android.util.Log.w(TAG, "wifi disconnect() refused by the platform")
    }

    /** Reconnect: become the Wi-Fi requester and let the platform associate. */
    private fun connectWifi(cm: ConnectivityManager) {
        if (wifiCallback != null) return
        if (!isWifiEnabled()) {
            refuseRadioToggle(
                "wifi",
                "the radio is off and an app cannot switch it on since API 29"
            )
            return
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (hasWifiTransport(network)) liveWifiNetworks.add(network)
            }

            override fun onLost(network: Network) {
                liveWifiNetworks.remove(network)
            }

            override fun onUnavailable() {
                // The platform answered: it will not associate for us.
                if (wifiCallback === this) {
                    wifiCallback = null
                    refuseRadioToggle("wifi", "requestNetwork could not associate")
                }
            }
        }
        try {
            cm.requestNetwork(wifiNetworkRequest(), callback)
            wifiCallback = callback
        } catch (e: Exception) {
            android.util.Log.w(TAG, "wifi requestNetwork: ${e.message}")
            refuseRadioToggle("wifi", "requestNetwork was refused")
        }
    }

    private var wifiCallback: ConnectivityManager.NetworkCallback? = null

    private fun openWifiSettings() {
        try {
            startActivity(
                Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {}
    }

    private fun bluetoothAdapter(): BluetoothAdapter? = try {
        getSystemService(BluetoothManager::class.java)?.adapter
    } catch (e: Exception) {
        null
    }

    private fun hasBluetoothConnect(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun isBluetoothProfileActive(adapter: BluetoothAdapter): Boolean = try {
        BT_PROFILES.any { adapter.getProfileConnectionState(it) == BluetoothProfile.STATE_CONNECTED }
    } catch (e: Exception) {
        false
    }

    /** A live profile link: what an iOS Bluetooth control reflects. */
    private fun isBluetoothConnected(): Boolean = try {
        val adapter = bluetoothAdapter()
        adapter != null && adapter.isEnabled && isBluetoothProfileActive(adapter)
    } catch (e: Exception) {
        false
    }

    /**
     * Bluetooth is probed, never assumed.
     *
     * Unlike Wi-Fi there is no non-privileged connect/disconnect to ask for:
     * BluetoothAdapter.enable()/disable() are reserved for the platform, the
     * profile proxies that could drop a link (A2dpProfile, HeadsetProfile) are
     * @SystemApi, and BluetoothDevice.createConnect()/createDisconnect() are
     * not in the SDK surface at all. So the one public call an app still has
     * (enable) is tried, every outcome is verified against the real read-back,
     * and anything the platform refuses demotes the tile to an honestly
     * labelled Settings shortcut rather than leaving a switch that lies.
     */
    private fun toggleBluetooth() {
        if (bluetoothIsSettingsOnly) {
            openBluetoothSettings()
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null) {
            refuseRadioToggle("bluetooth", "no adapter on this device")
            return
        }
        if (!hasBluetoothConnect()) {
            refuseRadioToggle("bluetooth", "BLUETOOTH_CONNECT is not granted")
            return
        }
        if (!adapter.isEnabled) {
            // Reconnect direction: the radio itself has to come up first.
            @Suppress("DEPRECATION")
            val accepted = try {
                adapter.enable()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "bluetooth enable: ${e.message}")
                false
            }
            afterSettles("bluetooth", expectConnected = true, current = { adapter.isEnabled }) {
                refuseRadioToggle(
                    "bluetooth",
                    "an app cannot switch the radio on since API 29 " +
                        "(enable() returned $accepted)"
                )
            }
            return
        }
        // Disconnect direction: there is no public call to drop a profile
        // link, so say so out loud instead of pretending otherwise.
        refuseRadioToggle(
            "bluetooth",
            "no SDK call exists to disconnect a profile from an app"
        )
    }

    private fun openBluetoothSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {}
    }

    /**
     * The platform said no (or the read-back never moved). A control that
     * silently does nothing is worse than an honest shortcut, so the tile is
     * relabelled, the reason is logged, and the next tap opens Settings.
     */
    private fun refuseRadioToggle(control: String, reason: String) {
        val firstTime = if (control == "wifi") {
            val was = wifiIsSettingsOnly
            wifiIsSettingsOnly = true
            !was
        } else {
            val was = bluetoothIsSettingsOnly
            bluetoothIsSettingsOnly = true
            !was
        }
        android.util.Log.w(
            TAG,
            "$control: the platform refused a connect/disconnect request " +
                "($reason). The control stops being a toggle and opens " +
                "$control settings instead."
        )
        if (firstTime) rebuildTiles()
        runCatching {
            android.widget.Toast.makeText(
                this,
                "$control cannot be switched here ($reason). Opening Settings.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Re-reads the radio after the platform had time to act and reports a
     * mismatch. State comes from the real radio, never from what we asked for.
     */
    private fun afterSettles(
        control: String,
        expectConnected: Boolean,
        current: () -> Boolean,
        onRefused: (String) -> Unit
    ) {
        handler.postDelayed({
            val now = try {
                current()
            } catch (e: Exception) {
                false
            }
            if (now != expectConnected) {
                onRefused(
                    "still ${if (now) "connected" else "disconnected"} " +
                        "${RADIO_SETTLE_MS}ms after the request"
                )
            }
        }, RADIO_SETTLE_MS)
    }

    private fun isAirplaneOn(): Boolean = try {
        Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    } catch (e: Exception) {
        false
    }

    private fun toggleAirplane() {
        // Privileged path works for our priv-app on the AppleUX image;
        // everywhere else fall back to the official settings page.
        try {
            Settings.Global.putInt(
                contentResolver, Settings.Global.AIRPLANE_MODE_ON,
                if (isAirplaneOn()) 0 else 1
            )
            sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        } catch (e: SecurityException) {
            try {
                startActivity(
                    Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {}
        }
    }

    private var torchOn = false

    private fun toggleTorch() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { cid ->
                cm.getCameraCharacteristics(cid)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
        } catch (e: Exception) {
            torchOn = false
        }
    }

    private fun isDndOn(): Boolean = try {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (e: Exception) {
        false
    }

    private fun toggleDnd() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            nm.setInterruptionFilter(
                if (isDndOn()) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        } catch (e: Exception) {}
    }

    private fun cycleBrightness() {
        // The platform range is 0-255; a fixed +55 overshot the top, skipped
        // everything under 30 and had nothing to do with "25%". The ladder
        // walks the real range in equal steps and wraps, like iOS.
        if (isAutoBrightness() && !forceManualBrightness()) {
            openBrightnessSettings()
            return
        }
        val next = BRIGHTNESS_STEPS[(brightnessStep() + 1) % BRIGHTNESS_STEPS.size]
        if (!writeBrightness(next)) {
            openBrightnessSettings()
            return
        }
        afterSettles("brightness", expectConnected = true, current = { currentBrightness() == next }) {
            android.util.Log.w(TAG, "brightness read-back never reached $next")
            openBrightnessSettings()
        }
    }

    /** The real platform value, clamped to the 0-255 the platform accepts. */
    private fun currentBrightness(): Int = try {
        Settings.System.getInt(
            contentResolver, Settings.System.SCREEN_BRIGHTNESS, DEFAULT_BRIGHTNESS
        ).coerceIn(0, 255)
    } catch (e: Exception) {
        DEFAULT_BRIGHTNESS
    }

    /** Index of the highest ladder step not above the current brightness. */
    private fun brightnessStep(): Int {
        val cur = currentBrightness()
        var step = 0
        for (i in BRIGHTNESS_STEPS.indices) if (BRIGHTNESS_STEPS[i] <= cur) step = i
        return step
    }

    private fun isAutoBrightness(): Boolean = try {
        Settings.System.getInt(
            contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        ) != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    } catch (e: Exception) {
        false
    }

    /** The sensor owns the screen until this succeeds; needs WRITE_SETTINGS. */
    private fun forceManualBrightness(): Boolean = try {
        Settings.System.putInt(
            contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        true
    } catch (e: Exception) {
        android.util.Log.w(TAG, "brightness mode (needs WRITE_SETTINGS app-op): ${e.message}")
        false
    }

    private fun writeBrightness(value: Int): Boolean = try {
        Settings.System.putInt(
            contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(0, 255)
        )
        true
    } catch (e: Exception) {
        android.util.Log.w(TAG, "brightness write (needs WRITE_SETTINGS app-op): ${e.message}")
        false
    }

    /** Ask for the app-op instead of letting the tile quietly do nothing. */
    private fun openBrightnessSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    android.net.Uri.parse("package:$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (e: Exception) {}
        runCatching {
            startActivity(
                Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onDestroy() {
        try {
            connectivity()?.unregisterNetworkCallback(wifiObserver)
        } catch (e: Exception) {}
        val callback = wifiCallback
        if (callback != null) {
            try {
                connectivity()?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {}
            wifiCallback = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AppleuxSystemUI"
        private const val CHANNEL_ID = "appleux_systemui"
        private const val NOTIF_ID = 0xA001

        /**
         * The platform brightness range is 0-255 and nothing else, so the tile
         * cycles exactly those six values (~20% apart) instead of an offset
         * that could overshoot the top of the scale.
         */
        private val BRIGHTNESS_STEPS = intArrayOf(0, 51, 102, 153, 204, 255)
        private const val DEFAULT_BRIGHTNESS = 128

        /** How long the platform gets to act before the read-back is judged. */
        private const val RADIO_SETTLE_MS = 800L

        /** Labels used once a radio refuses to be toggled by an app. */
        private const val WIFI_SETTINGS_LABEL = "Wi-Fi Settings"
        private const val BLUETOOTH_SETTINGS_LABEL = "Bluetooth Settings"

        private const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"

        /** Profiles whose link state reflects "connected" in Control Center. */
        private val BT_PROFILES = intArrayOf(
            BluetoothProfile.A2DP,
            BluetoothProfile.HEADSET,
            BluetoothProfile.GATT
        )

        const val ACTION_TOGGLE_NOTIFICATIONS = "com.appleux.systemui.TOGGLE_NOTIFICATIONS"
        const val ACTION_SHOW_NOTIFICATIONS = "com.appleux.systemui.SHOW_NOTIFICATIONS"
        const val ACTION_HIDE_NOTIFICATIONS = "com.appleux.systemui.HIDE_NOTIFICATIONS"
        const val ACTION_TOGGLE_CONTROL_CENTER = "com.appleux.systemui.TOGGLE_CONTROL_CENTER"
        const val ACTION_SHOW_CONTROL_CENTER = "com.appleux.systemui.SHOW_CONTROL_CENTER"
        const val ACTION_HIDE_ALL = "com.appleux.systemui.HIDE_ALL"
    }


}
