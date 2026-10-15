package org.ioslike.ui.settings

import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.ioslike.ui.R
import org.ioslike.ui.controlcenter.ControlCenterController

/** Real Wi-Fi state + scan. Joining a network opens the stock Wi-Fi screen (password dialogs are not rebuilt). */
@Composable
fun WifiPage(cc: ControlCenterController, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by cc.state.collectAsState()
    val wifi = remember { ctx.getSystemService(WifiManager::class.java) }
    var nets by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var current by remember { mutableStateOf("") }

    LaunchedEffect(s.wifi) {
        while (s.wifi) {
            @Suppress("DEPRECATION") wifi.startScan()
            nets = try { wifi.scanResults } catch (_: SecurityException) { emptyList() }
            current = try { @Suppress("DEPRECATION") wifi.connectionInfo.ssid.trim('"') } catch (_: Exception) { "" }
            delay(5000)
        }
    }
    val openWifiSettings = { ctx.stock(Settings.ACTION_WIFI_SETTINGS) }
    val list = nets.filter { it.SSID.isNotBlank() && it.SSID != current }.distinctBy { it.SSID }.sortedByDescending { it.level }

    SettingsPage("Wi-Fi", onBack) {
        Group { SettingsRow("Wi-Fi", chevron = false, divider = false, trailing = { IOSSwitch(s.wifi) { cc.toggleWifi() } }) }
        if (s.wifi) {
            if (current.isNotEmpty() && current != "<unknown ssid>") {
                Group("My Networks") {
                    SettingsRow(
                        current, chevron = false, divider = false,
                        leading = { Image(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(SC.blue)) },
                        trailing = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph(R.drawable.ic_lock); Glyph(R.drawable.ic_wifi); Glyph(R.drawable.ic_info, SC.blue)
                        } },
                        onClick = openWifiSettings,
                    )
                }
            }
            if (list.isNotEmpty()) {
                Group("Networks", footer = "Known networks connect automatically. If none are in range, you will be asked before joining a new one.") {
                    list.take(8).forEachIndexed { i, r ->
                        val secured = r.capabilities.contains("WPA") || r.capabilities.contains("WEP") || r.capabilities.contains("SAE")
                        val bars = wifi.calculateSignalLevel(r.level)
                        SettingsRow(r.SSID, chevron = false, divider = i < minOf(list.size, 8) - 1, trailing = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (secured) Glyph(R.drawable.ic_lock)
                            Glyph(R.drawable.ic_wifi, Color.White.copy(alpha = 0.4f + 0.15f * bars))
                            Glyph(R.drawable.ic_info, SC.blue)
                        } }, onClick = openWifiSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun Glyph(icon: Int, tint: Color = Color.White) {
    Image(painterResource(icon), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(tint))
}
