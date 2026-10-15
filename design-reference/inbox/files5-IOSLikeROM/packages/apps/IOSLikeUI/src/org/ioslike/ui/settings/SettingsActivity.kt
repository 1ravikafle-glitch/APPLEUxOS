package org.ioslike.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ioslike.ui.R
import org.ioslike.ui.controlcenter.ControlCenterController
import androidx.compose.runtime.CompositionLocalProvider
import org.ioslike.ui.theme.LocalSafeInsets
import org.ioslike.ui.theme.iosSpring
import org.ioslike.ui.theme.readSafeInsets

enum class Page { Home, Wifi, Display }

/** iOS-style Settings front end. Pages that are not rebuilt yet open the stock Settings screen. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        setContent { CompositionLocalProvider(LocalSafeInsets provides readSafeInsets()) { SettingsApp() } }
    }
}

/** Opens a stock Settings screen. setPackage avoids looping back into this app. */
fun Context.stock(action: String) {
    try { startActivity(Intent(action).setPackage("com.android.settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS).setPackage("com.android.settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
}

@Composable
private fun SettingsApp() {
    val ctx = LocalContext.current
    val cc = remember { ControlCenterController(ctx).also { it.start() } }
    DisposableEffect(Unit) { onDispose { cc.stop() } }
    var sub by remember { mutableStateOf<Page?>(null) }
    var last by remember { mutableStateOf(Page.Wifi) }
    if (sub != null) last = sub!!
    BackHandler(enabled = sub != null) { sub = null }

    Box(Modifier.fillMaxSize().background(SC.bg)) {
        SettingsHome(cc) { sub = it }
        AnimatedVisibility(
            visible = sub != null,
            enter = slideInHorizontally(iosSpring()) { it },
            exit = slideOutHorizontally(iosSpring()) { it },
        ) {
            when (last) {
                Page.Wifi -> WifiPage(cc) { sub = null }
                Page.Display -> DisplayPage { sub = null }
                else -> Unit
            }
        }
    }
}

@Composable
private fun SettingsHome(cc: ControlCenterController, navigate: (Page) -> Unit) {
    val ctx = LocalContext.current
    val s by cc.state.collectAsState()
    SettingsPage("Settings", onBack = null) {
        SettingsSearch()
        Column(Modifier.padding(top = 18.dp)) {
            Group {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(60.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8E9AAF), Color(0xFF5B6270)))),
                        contentAlignment = Alignment.Center,
                    ) { Image(androidx.compose.ui.res.painterResource(R.drawable.ic_person), null, Modifier.size(30.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)) }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        BasicText("Your Account", style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
                        BasicText("Sign-in, backup & services", style = SC.small.copy(fontSize = 14.sp))
                    }
                }
            }
            Group {
                SettingsRow("Airplane Mode", R.drawable.ic_airplane, SC.orange, chevron = false, trailing = { IOSSwitch(s.airplane) { cc.toggleAirplane() } })
                SettingsRow("Wi-Fi", R.drawable.ic_wifi, SC.blue, value = if (s.wifi) "On" else "Off") { navigate(Page.Wifi) }
                SettingsRow("Bluetooth", R.drawable.ic_bluetooth, SC.blue, value = if (s.bluetooth) "On" else "Off") { ctx.stock(Settings.ACTION_BLUETOOTH_SETTINGS) }
                SettingsRow("Mobile Service", R.drawable.ic_cellular, SC.green) { ctx.stock(Settings.ACTION_NETWORK_OPERATOR_SETTINGS) }
                SettingsRow("Hotspot", R.drawable.ic_hotspot, SC.green, value = "Off", divider = false) { ctx.stock("android.settings.TETHER_SETTINGS") }
            }
            Group {
                SettingsRow("Notifications", R.drawable.ic_bell, SC.red) { ctx.stock("android.settings.ALL_APPS_NOTIFICATION_SETTINGS") }
                SettingsRow("Sounds & Haptics", R.drawable.ic_volume, SC.pink) { ctx.stock(Settings.ACTION_SOUND_SETTINGS) }
                SettingsRow("Focus", R.drawable.ic_dnd, SC.indigo) { ctx.stock("android.settings.ZEN_MODE_SETTINGS") }
                SettingsRow("Digital Wellbeing", R.drawable.ic_timer, SC.indigo, divider = false) { ctx.stock(Settings.ACTION_SETTINGS) }
            }
            Group {
                SettingsRow("General", R.drawable.ic_settings, SC.gray) { ctx.stock(Settings.ACTION_DEVICE_INFO_SETTINGS) }
                SettingsRow("Control Center", R.drawable.ic_sliders, SC.gray) { ctx.stock(Settings.ACTION_SETTINGS) }
                SettingsRow("Display & Brightness", R.drawable.ic_brightness, SC.blue) { navigate(Page.Display) }
                SettingsRow("Home Screen & App Library", R.drawable.ic_grid, SC.blue) { ctx.stock(Settings.ACTION_HOME_SETTINGS) }
                SettingsRow("Accessibility", R.drawable.ic_person, SC.blue) { ctx.stock(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                SettingsRow("Wallpaper", R.drawable.ic_photos, SC.cyan) { ctx.startActivity(Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                SettingsRow("Assistant", R.drawable.ic_sparkle, SC.gray) { ctx.stock(Settings.ACTION_VOICE_INPUT_SETTINGS) }
                SettingsRow("Privacy & Security", R.drawable.ic_shield, SC.blue) { ctx.stock(Settings.ACTION_PRIVACY_SETTINGS) }
                SettingsRow("Battery", R.drawable.ic_battery, SC.green, divider = false) { ctx.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}
