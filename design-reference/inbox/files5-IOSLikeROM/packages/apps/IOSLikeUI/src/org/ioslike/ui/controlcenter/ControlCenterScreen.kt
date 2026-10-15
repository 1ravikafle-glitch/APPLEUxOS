package org.ioslike.ui.controlcenter

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.ioslike.ui.R
import org.ioslike.ui.theme.Glass
import org.ioslike.ui.theme.GlassScene
import org.ioslike.ui.theme.LocalSafeInsets
import org.ioslike.ui.theme.Tokens
import org.ioslike.ui.theme.glass
import org.ioslike.ui.theme.noRippleClickable
import org.ioslike.ui.theme.pressable
import org.ioslike.ui.theme.squircle

/**
 * iOS-style grid on a 4-column unit grid:
 *   [Connectivity 2x2][Media 2x2]
 *   [Rotation|Cast / Focus 2x1][Brightness 1x2][Volume 1x2]
 *   [Quick Share 2x1][Hotspot 2x1]
 *   [Torch][Timer][Calculator][Camera]
 */
@Composable
fun ControlCenterScreen(cc: ControlCenterController, media: MediaRepo, wallpaper: ImageBitmap?, onClose: () -> Unit) {
    val s by cc.state.collectAsState()
    val m by media.state.collectAsState()
    val available = cc.nearbyBackend.available

    GlassScene(wallpaper, blur = Tokens.GlassParams.wallpaperBlur.dp, dim = 0.35f) {
        Box(Modifier.fillMaxSize().noRippleClickable(onClose)) {
            BoxWithConstraints(
                Modifier.align(Alignment.TopCenter).padding(top = LocalSafeInsets.current.top + Tokens.Space.contentTopGap, start = 16.dp, end = 16.dp).noRippleClickable { },
            ) {
                val gap = Tokens.Space.gap
                val u = (maxWidth - gap * 3) / 4
                val w2 = u * 2 + gap
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Connectivity(s, cc, Modifier.size(w2, w2))
                        MediaTile(m, media, Modifier.size(w2, w2))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Column(Modifier.width(w2), verticalArrangement = Arrangement.spacedBy(gap)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                SquareTile(R.drawable.ic_rotation, "Rotation lock", s.rotationLock, Glass.orange, Modifier.size(u), cc::toggleRotationLock)
                                SquareTile(R.drawable.ic_cast, "Screen mirroring", false, Glass.blue, Modifier.size(u)) { cc.openCast(); onClose() }
                            }
                            WideTile(R.drawable.ic_dnd, "Focus", if (s.dnd) "On" else "Off", s.dnd, Glass.indigo, Modifier.size(w2, u), cc::toggleDnd)
                        }
                        VSlider(s.brightness, R.drawable.ic_brightness, Modifier.size(u, w2), cc::setBrightness)
                        VSlider(s.volume, R.drawable.ic_volume, Modifier.size(u, w2), cc::setVolume)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        WideTile(
                            R.drawable.ic_share, "Quick Share",
                            if (available) "Send & receive" else "Bluetooth",
                            false, Glass.blue, Modifier.size(w2, u),
                        ) { cc.toggleNearby(); onClose() }
                        WideTile(R.drawable.ic_hotspot, "Hotspot", "Settings", false, Glass.green, Modifier.size(w2, u)) { cc.openHotspot(); onClose() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        SquareTile(R.drawable.ic_flashlight, "Flashlight", s.flashlight, Glass.orange, Modifier.size(u), cc::toggleFlashlight)
                        SquareTile(R.drawable.ic_timer, "Timer", false, Glass.orange, Modifier.size(u)) { cc.openTimer(); onClose() }
                        SquareTile(R.drawable.ic_calculator, "Calculator", false, Glass.orange, Modifier.size(u)) { cc.openCalculator(); onClose() }
                        SquareTile(R.drawable.ic_camera, "Camera", false, Glass.orange, Modifier.size(u)) { cc.openCamera(); onClose() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Glyph(icon: Int, tint: Color = Color.White, size: Dp = 28.dp) {
    Image(painterResource(icon), null, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

@Composable
private fun Connectivity(s: CcState, cc: ControlCenterController, modifier: Modifier) {
    Column(modifier.glass().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Round(R.drawable.ic_airplane, "Airplane mode", s.airplane, Glass.orange, cc::toggleAirplane)
            Round(R.drawable.ic_cellular, "Mobile data", s.mobileData, Glass.green, cc::toggleMobileData)
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Round(R.drawable.ic_wifi, "Wi-Fi", s.wifi, Glass.blue, cc::toggleWifi)
            Round(R.drawable.ic_bluetooth, "Bluetooth", s.bluetooth, Glass.blue, cc::toggleBluetooth)
        }
    }
}

@Composable
private fun Round(icon: Int, label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(Tokens.Size.round).pressable(onClick)
            .glass(CircleShape, if (on) color.copy(alpha = 0.92f) else Glass.fill)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Glyph(icon) }
}

@Composable
private fun SquareTile(icon: Int, label: String, on: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.pressable(onClick)
            .glass(squircle(Tokens.Radius.tile), if (on) color.copy(alpha = 0.92f) else Glass.fill)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Glyph(icon) }
}

@Composable
private fun WideTile(icon: Int, title: String, subtitle: String, on: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.pressable(onClick).glass(squircle(Tokens.Radius.tile), if (on) color.copy(alpha = 0.92f) else Glass.fill).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Glyph(icon)
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Glass.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(subtitle, style = Glass.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MediaTile(m: MediaState?, media: MediaRepo, modifier: Modifier) {
    Box(modifier.glass().padding(14.dp)) {
        if (m == null) {
            BasicText("Not Playing", style = Glass.body, modifier = Modifier.align(Alignment.Center))
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val art = m.art
                    if (art != null) Image(art.asImageBitmap(), null, Modifier.size(44.dp).clip(squircle(10.dp)))
                    else Box(Modifier.size(44.dp).clip(squircle(10.dp)).background(Color(0x40FFFFFF)))
                    Column(Modifier.weight(1f)) {
                        BasicText(m.title, style = Glass.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(m.artist, style = Glass.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                    MediaButton(R.drawable.ic_prev, "Previous", media::previous)
                    MediaButton(if (m.playing) R.drawable.ic_pause else R.drawable.ic_play, if (m.playing) "Pause" else "Play", media::playPause)
                    MediaButton(R.drawable.ic_next, "Next", media::next)
                }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: Int, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).pressable(onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Glyph(icon, size = 26.dp)
    }
}

@Composable
private fun VSlider(value: Float, icon: Int, modifier: Modifier, onChange: (Float) -> Unit) {
    var h by remember { mutableStateOf(1f) }
    val shape = squircle(Tokens.Radius.slider)
    val fill by animateFloatAsState(value.coerceIn(0f, 1f), spring(stiffness = 600f), label = "slider")
    Box(
        modifier.glass(shape)
            .onSizeChanged { h = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { onChange(1f - it.y / h) } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragStart = { onChange(1f - it.y / h) }) { change, _ ->
                    onChange(1f - change.position.y / h); change.consume()
                }
            },
    ) {
        Box(Modifier.matchParentSize().clip(shape)) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fill).background(Color.White.copy(alpha = 0.92f)))
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) { Glyph(icon, Color(0xFF3A3A3C), 26.dp) }
    }
}
