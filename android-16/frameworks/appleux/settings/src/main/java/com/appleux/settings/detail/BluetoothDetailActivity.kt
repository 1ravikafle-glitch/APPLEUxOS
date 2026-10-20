package com.appleux.settings.detail

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.os.Looper
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.components.AppleuxToggle
import com.appleux.settings.R

/**
 * Owned Bluetooth page — GT: bluetooth-dark / bluetooth-dark-2.
 *
 * Layout per ground truth: the Bluetooth on/off row, then MY DEVICES
 * with the paired set. All state comes from the live adapter; a device
 * with no radio reports "Not Available" rather than pretending.
 */
class BluetoothDetailActivity : SettingsDetailActivity() {

    private val main = Handler(Looper.getMainLooper())
    private var column: android.widget.LinearLayout? = null

    private val adapter: BluetoothAdapter?
        get() = runCatching {
            getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        }.getOrNull()?.adapter

    override val titleText get() = getString(R.string.bluetooth)

    override fun onContentReady() {
        DetailKit.body(this) { col ->
            column = col
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        // Bonded set and adapter state can change while we are backgrounded.
        render()
    }

    private fun render() {
        val col = column ?: return
        main.post {
            col.removeAllViews()
            val current = adapter

            // ---- On/Off group ----
            val (card, list) = DetailKit.listCard(this)
            if (current == null) {
                list.addView(
                    AppleuxListItem(this).apply {
                        setTitle(getString(R.string.bluetooth))
                        setSubtitle(getString(R.string.bluetooth_unavailable))
                        setDividerVisible(false)
                    }
                )
                col.addView(card)
                return@post
            }
            val enabled = runCatching { current.isEnabled }.getOrDefault(false)
            list.addView(
                AppleuxListItem(this).apply {
                    setTitle(getString(R.string.bluetooth))
                    setDividerVisible(false)
                    setAccessoryView(
                        AppleuxToggle(this@BluetoothDetailActivity).apply {
                            setChecked(enabled, animate = false)
                            setOnCheckedChangeListener { on ->
                                runCatching {
                                    if (on) current.enable() else current.disable()
                                }
                                renderSoon()
                            }
                        }
                    )
                }
            )
            col.addView(card)

            // ---- MY DEVICES (GT section) ----
            val bonded: Set<BluetoothDevice> = runCatching {
                current.bondedDevices
            }.getOrDefault(emptySet())
            col.addView(DetailKit.sectionLabel(this, getString(R.string.my_devices)))
            val (devCard, devList) = DetailKit.listCard(this)
            if (bonded.isEmpty()) {
                devList.addView(
                    AppleuxListItem(this).apply {
                        setTitle(getString(R.string.devices_appear_here))
                        setDividerVisible(false)
                    }
                )
            } else {
                val names = bonded
                    .mapNotNull { d -> runCatching { d.name }.getOrNull() }
                    .sorted()
                    .ifEmpty { listOf(getString(R.string.devices_appear_here)) }
                names.forEachIndexed { i, n ->
                    devList.addView(
                        AppleuxListItem(this).apply {
                            setTitle(n)
                            setDividerVisible(i != names.size - 1)
                        }
                    )
                }
            }
            col.addView(devCard)
        }
    }

    private fun renderSoon() = main.post { render() }
}
