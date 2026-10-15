package org.ioslike.ui.controlcenter

import android.app.NotificationManager
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/** AirDrop is Apple's protocol. Plug in whatever "nearby share" your ROM ships (e.g. your own, or GMS Quick Share). */
interface NearbyShareBackend {
    val available: Boolean get() = true
    fun isEnabled(): Boolean
    fun setEnabled(on: Boolean)
    /** Open the share/receive UI (what tapping the Control Center tile does). */
    fun openUi() {}
}

object NoNearbyShare : NearbyShareBackend {
    private var on = false
    override fun isEnabled() = on
    override fun setEnabled(on: Boolean) { this.on = on } // TODO: wire to a real implementation
}

data class CcState(
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val airplane: Boolean = false,
    val mobileData: Boolean = false,
    val nearby: Boolean = false,
    val dnd: Boolean = false,
    val rotationLock: Boolean = false,
    val flashlight: Boolean = false,
    val darkMode: Boolean = false,
    val brightness: Float = 0.5f,
    val volume: Float = 0.5f,
)

/** Reads and writes real system state. Needs the platform signature + the permissions in the manifest. */
@Suppress("DEPRECATION")
class ControlCenterController(
    val ctx: Context,
    val nearbyBackend: NearbyShareBackend = NoNearbyShare,
) {
    private val _state = MutableStateFlow(CcState())
    val state: StateFlow<CcState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val cr = ctx.contentResolver
    private val wifi = ctx.getSystemService(WifiManager::class.java)
    private val bt: BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val tm = ctx.getSystemService(TelephonyManager::class.java)
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val ui = ctx.getSystemService(UiModeManager::class.java)
    private val camera = ctx.getSystemService(CameraManager::class.java)
    private val torchId: String? = camera.cameraIdList.firstOrNull {
        val c = camera.getCameraCharacteristics(it)
        c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refresh()
    }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == torchId) _state.update { it.copy(flashlight = enabled) }
        }
    }

    fun start() {
        val f = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        ctx.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        listOf(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
        ).forEach { cr.registerContentObserver(it, false, observer) }
        camera.registerTorchCallback(torchCb, main)
        refresh()
    }

    fun stop() {
        ctx.unregisterReceiver(receiver)
        cr.unregisterContentObserver(observer)
        camera.unregisterTorchCallback(torchCb)
    }

    fun refresh() {
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        _state.update {
            it.copy(
                wifi = wifi.isWifiEnabled,
                bluetooth = bt?.isEnabled == true,
                airplane = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
                mobileData = tm.isDataEnabled,
                nearby = nearbyBackend.isEnabled(),
                dnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
                rotationLock = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1) == 0,
                darkMode = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES,
                brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f,
                volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol,
            )
        }
    }

    // ---- actions (each ends with refresh or an optimistic update) ----
    fun toggleWifi() { wifi.setWifiEnabled(!wifi.isWifiEnabled); refresh() }

    fun toggleBluetooth() {
        bt?.let { if (it.isEnabled) it.disable() else it.enable() }
        refresh()
    }

    fun toggleAirplane() { cm.setAirplaneMode(!_state.value.airplane) }

    fun toggleMobileData() { tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER, !tm.isDataEnabled); refresh() }

    fun toggleNearby() {
        nearbyBackend.openUi(); refresh()
    }

    fun toggleDnd() {
        nm.setInterruptionFilter(
            if (_state.value.dnd) NotificationManager.INTERRUPTION_FILTER_ALL
            else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )
    }

    fun toggleRotationLock() {
        Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (_state.value.rotationLock) 1 else 0)
    }

    fun toggleFlashlight() {
        val id = torchId ?: return
        try { camera.setTorchMode(id, !_state.value.flashlight) } catch (_: CameraAccessException) {}
    }

    fun toggleDarkMode() {
        val next = !_state.value.darkMode
        ui.setNightModeActivated(next)
        _state.update { it.copy(darkMode = next) }
    }

    fun setBrightness(f: Float) {
        val v = (f.coerceIn(0.02f, 1f) * 255).roundToInt()
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v)
        _state.update { it.copy(brightness = v / 255f) }
    }

    fun setVolume(f: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f.coerceIn(0f, 1f) * max).roundToInt(), 0)
        _state.update { it.copy(volume = f.coerceIn(0f, 1f)) }
    }
}
