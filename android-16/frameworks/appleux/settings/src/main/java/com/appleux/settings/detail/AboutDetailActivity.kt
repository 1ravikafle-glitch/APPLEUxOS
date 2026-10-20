package com.appleux.settings.detail

import android.bluetooth.BluetoothManager
import android.os.Build
import com.appleux.design.components.AppleuxListItem
import com.appleux.settings.R

/**
 * Owned About page — the terminal GT General sub-page.
 *
 * Every value is a real platform read (Build, kernel property, live
 * adapter). Rows with no honest value on this hardware (serial, Wi-Fi
 * MAC without grants) are omitted, never faked.
 */
class AboutDetailActivity : SettingsDetailActivity() {

    override val titleText get() = getString(R.string.about)

    override fun onContentReady() {
        DetailKit.body(this) { col ->
            val (card, list) = DetailKit.listCard(this)
            val rows = ArrayList<AppleuxListItem>()

            // Name: the Bluetooth adapter name is the device-facing name
            // iOS shows; fall back to the model when no adapter answers.
            val adapter = runCatching {
                getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
            }.getOrNull()?.adapter
            val name = runCatching { adapter?.name }.getOrNull()
            rows.add(
                DetailKit.valueRow(
                    this, getString(R.string.device_name),
                    name?.takeIf { it.isNotBlank() } ?: Build.MODEL,
                    true
                )
            )

            rows.add(
                DetailKit.valueRow(
                    this, getString(R.string.software_version),
                    Build.VERSION.RELEASE, true
                )
            )
            rows.add(
                DetailKit.valueRow(
                    this, getString(R.string.build_number),
                    Build.VERSION.INCREMENTAL, true
                )
            )
            rows.add(
                DetailKit.valueRow(
                    this, getString(R.string.model_name),
                    Build.MODEL, true
                )
            )
            rows.add(
                DetailKit.valueRow(
                    this, getString(R.string.model_number),
                    Build.DEVICE, true
                )
            )
            runCatching { System.getProperty("os.version") }
                .getOrNull()?.let { kernel ->
                    rows.add(
                        DetailKit.valueRow(
                            this, getString(R.string.kernel), kernel, true
                        )
                    )
                }
            rows.add(
                DetailKit.valueRow(
                    this, getString(R.string.api_level),
                    "API ${Build.VERSION.SDK_INT}", false
                )
            )
            // Last visible row carries no divider.
            rows.last().setDividerVisible(false)
            rows.forEach { list.addView(it) }
            col.addView(card)
        }
    }
}
