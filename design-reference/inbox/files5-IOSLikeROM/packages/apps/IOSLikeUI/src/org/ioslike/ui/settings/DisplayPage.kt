package org.ioslike.ui.settings

import android.app.UiModeManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ioslike.ui.theme.noRippleClickable
import org.ioslike.ui.theme.squircle

/** Appearance (light / dark / automatic), brightness, auto-lock. Real system values. */
@Composable
fun DisplayPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ui = remember { ctx.getSystemService(UiModeManager::class.java) }
    val cr = ctx.contentResolver
    var mode by remember { mutableStateOf(ui.nightMode) }
    var bright by remember { mutableStateOf(Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f) }
    var timeout by remember { mutableStateOf(Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)) }
    val auto = mode == UiModeManager.MODE_NIGHT_AUTO
    val dark = mode == UiModeManager.MODE_NIGHT_YES
    fun setMode(m: Int) { ui.nightMode = m; mode = m }
    val steps = listOf(15_000, 30_000, 60_000, 120_000, 300_000)
    fun label(ms: Int) = if (ms < 60_000) "${ms / 1000} Seconds" else "${ms / 60_000} Minute" + if (ms > 60_000) "s" else ""

    SettingsPage("Display & Brightness", onBack, titleStyle = SC.titleLong) {
        Group("Appearance") {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                AppearanceCard("Light", selected = !dark && !auto, darkCard = false) { setMode(UiModeManager.MODE_NIGHT_NO) }
                AppearanceCard("Dark", selected = dark && !auto, darkCard = true) { setMode(UiModeManager.MODE_NIGHT_YES) }
            }
            SettingsRow("Automatic", chevron = false, divider = false, trailing = {
                IOSSwitch(auto) { on -> setMode(if (on) UiModeManager.MODE_NIGHT_AUTO else UiModeManager.MODE_NIGHT_NO) }
            })
        }
        Group("Brightness") {
            BrightnessSlider(bright) {
                bright = it.coerceIn(0.02f, 1f)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (bright * 255).toInt())
            }
        }
        Group {
            SettingsRow("Text Size") { ctx.stock(Settings.ACTION_DISPLAY_SETTINGS) }
            SettingsRow(
                "Auto-Lock", value = label(timeout), divider = false,
            ) {
                val next = steps[(steps.indexOf(timeout) + 1).mod(steps.size)]
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, next); timeout = next
            }
        }
    }
}

@Composable
private fun AppearanceCard(label: String, selected: Boolean, darkCard: Boolean, onClick: () -> Unit) {
    Column(Modifier.noRippleClickable(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(104.dp, 160.dp).clip(squircle(18.dp))
                .background(Brush.linearGradient(if (darkCard) listOf(Color(0xFF2C2C2E), Color(0xFF0B0B0D)) else listOf(Color(0xFFF2F2F7), Color(0xFFD1D1D6)))),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(48.dp).height(8.dp).clip(CircleShape).background(if (darkCard) Color(0x66FFFFFF) else Color(0x66000000)))
                repeat(3) { Box(Modifier.fillMaxWidth().height(22.dp).clip(squircle(8.dp)).background(if (darkCard) Color(0x1FFFFFFF) else Color(0x14000000))) }
            }
        }
        Box(Modifier.padding(top = 10.dp))
        BasicText(label, style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        Box(
            Modifier.padding(top = 8.dp).size(24.dp).clip(CircleShape)
                .then(if (selected) Modifier.background(SC.blue) else Modifier.border(1.5.dp, SC.tertiary, CircleShape)),
        )
    }
}

@Composable
private fun BrightnessSlider(value: Float, onChange: (Float) -> Unit) {
    var w by remember { mutableStateOf(1f) }
    Box(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { onChange(it.x / w) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> onChange(change.position.x / w); change.consume() } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF39393D)))
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(SC.blue))
    }
}
