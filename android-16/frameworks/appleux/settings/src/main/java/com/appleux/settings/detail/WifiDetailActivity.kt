package com.appleux.settings.detail

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.ScrollView
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem
import com.appleux.settings.R

/**
 * Owned Wi-Fi detail page: live network list and a real on/off toggle.
 *
 * This replaces a row that fired `ACTION_WIFI_SETTINGS`, which handed the user
 * to stock Settings — the behaviour our no-stock doctrine forbids. Everything
 * here is read through the platform APIs the app already holds
 * (`ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` / `ACCESS_NETWORK_STATE`), so the
 * page reports what the radio is actually doing.
 */
class WifiDetailActivity : SettingsDetailActivity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var card: AppleuxCard
    private lateinit var list: android.widget.LinearLayout

    private val wifi: WifiManager? by lazy {
        runCatching {
            applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        }.getOrNull()
    }

    /** Re-reads on this cadence; scan results arrive asynchronously. */
    private val refresh = object : Runnable {
        override fun run() {
            render()
            main.postDelayed(this, REFRESH_MS)
        }
    }

    override val titleText: String get() = getString(R.string.wifi)

    override fun onContentReady() {
        list = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        card = AppleuxCard(this).apply {
            applyLiquidGlassRim()
            addView(list)
        }
        content.addView(
            ScrollView(this).apply { addView(card) },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onResume() {
        super.onResume()
        observeWifi()
        main.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(refresh)
        unregisterCallback?.invoke()
        unregisterCallback = null
    }

    private var unregisterCallback: (() -> Unit)? = null

    private fun observeWifi() {
        val cm = runCatching {
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }.getOrNull() ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                renderSoon()
            }

            override fun onLost(network: Network) {
                renderSoon()
            }
        }
        runCatching { cm.registerNetworkCallback(request, callback) }.onSuccess {
            unregisterCallback = { runCatching { cm.unregisterNetworkCallback(callback) } }
        }
    }

    private fun renderSoon() = main.post { render() }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()

        val manager = wifi
        val enabled = manager?.isWifiEnabled == true
        val ssid = currentSsid()

        list.addView(
            AppleuxListItem(this).apply {
                setTitle(getString(R.string.wifi))
                setSubtitle(
                    when {
                        !enabled -> getString(R.string.wifi_off)
                        ssid.isNullOrEmpty() -> getString(R.string.wifi_not_connected)
                        else -> ssid
                    }
                )
                setDividerVisible(true)
                setOnClickListener { toggleWifi() }
            }
        )

        if (enabled) {
            for (result in scanResults()) {
                list.addView(
                    AppleuxListItem(this).apply {
                        setTitle(result)
                        setSubtitle(null)
                        setDividerVisible(true)
                    }
                )
            }
        }
    }

    private fun currentSsid(): String? = runCatching {
        val info = (wifi?.connectionInfo as? WifiInfo)?.ssid ?: return@runCatching null
        info.trim('"').takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun scanResults(): List<String> = runCatching {
        manager()?.scanResults.orEmpty()
            .mapNotNull { it.SSID?.takeIf { s -> s.isNotBlank() } }
            .distinct()
            .sorted()
            .take(MAX_RESULTS)
    }.getOrDefault(emptyList())

    private fun manager(): WifiManager? = wifi

    private fun toggleWifi() {
        val manager = wifi ?: return
        // CHANGE_WIFI_STATE is a normal permission for a system app; a refusal
        // is reported by the platform rather than thrown at the user.
        runCatching {
            manager.isWifiEnabled = !manager.isWifiEnabled
        }.onFailure { render() }
    }

    private companion object {
        const val REFRESH_MS = 4000L
        const val MAX_RESULTS = 12
    }
}