package org.ioslike.ui.lockscreen

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.ioslike.ui.R
import org.ioslike.ui.notifications.NotifCard
import org.ioslike.ui.notifications.NotifItem
import org.ioslike.ui.theme.Glass
import org.ioslike.ui.theme.GlassScene
import org.ioslike.ui.theme.LocalSafeInsets
import org.ioslike.ui.theme.Tokens
import org.ioslike.ui.theme.glass
import org.ioslike.ui.theme.iosSpring
import org.ioslike.ui.theme.noRippleClickable
import org.ioslike.ui.theme.pressable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class LockCallbacks(
    val onUnlockGesture: () -> Unit,   // swipe-up finished: show PasscodeScreen / bouncer / dismiss keyguard
    val onCamera: () -> Unit,
    val onFlashlight: () -> Unit,
)

/** Lock screen on real glass: the wallpaper is the backdrop, cards and buttons refract and blur it. */
@Composable
fun LockScreen(notifs: List<NotifItem>, torchOn: Boolean, wallpaper: ImageBitmap?, cb: LockCallbacks) {
    GlassScene(wallpaper, blur = 0.dp, dim = 0.12f) { LockContent(notifs, torchOn, cb) }
}

@Composable
private fun LockContent(notifs: List<NotifItem>, torchOn: Boolean, cb: LockCallbacks) {
    val now by produceState(Date()) { while (true) { value = Date(); delay(1000L) } }
    val locale = Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val time = SimpleDateFormat(if (is24) "H:mm" else "h:mm", locale).format(now)
    val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale).format(now)

    var dy by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 160.dp.toPx() }
    val shown by animateFloatAsState(dy, if (dragging) snap() else iosSpring(), label = "unlock")

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; if (dy < -threshold) cb.onUnlockGesture(); dy = 0f },
                    onDragCancel = { dragging = false; dy = 0f },
                ) { change, amount -> dy = (dy + amount).coerceAtMost(0f); change.consume() }
            }
            .graphicsLayer { translationY = shown * 0.5f; alpha = 1f - (-shown / (threshold * 2f)).coerceIn(0f, 0.6f) },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = LocalSafeInsets.current.top + Tokens.Space.lockTopGap), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_lock), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color.White))
            Spacer(Modifier.height(10.dp))
            BasicText(date, style = Tokens.Type.date.copy(color = Glass.labelDim))
            BasicText(time, style = Tokens.Type.clock.copy(color = Color.White, textAlign = TextAlign.Center))
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notifs.take(3).forEach { NotifCard(it) }
                if (notifs.size > 3) BasicText("+${notifs.size - 3} more", style = Glass.body, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 44.dp).padding(bottom = LocalSafeInsets.current.bottom + Tokens.Space.lockButtonsBottom),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundButton(R.drawable.ic_flashlight, torchOn, cb.onFlashlight)
            RoundButton(R.drawable.ic_camera, false, cb.onCamera)
        }
        BasicText("Swipe up to unlock", style = Glass.body, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalSafeInsets.current.bottom + Tokens.Space.lockHintBottom))
    }
}

@Composable
private fun RoundButton(icon: Int, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).pressable(onClick).glass(CircleShape, if (on) Color(0xE6FFFFFF) else Glass.fill),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(if (on) Color(0xFF1C1C1E) else Color.White))
    }
}

/** Passcode entry on glass. onSubmit must verify the credential and return true on success. */
@Composable
fun PasscodeScreen(wallpaper: ImageBitmap?, length: Int = 6, onSubmit: (String) -> Boolean, onCancel: () -> Unit) {
    GlassScene(wallpaper, blur = 30.dp, dim = 0.35f) {
        var code by remember { mutableStateOf("") }
        var errorTick by remember { mutableStateOf(0) }
        val shake = remember { Animatable(0f) }
        LaunchedEffect(errorTick) {
            if (errorTick > 0) {
                repeat(3) { shake.animateTo(16f, tween(40)); shake.animateTo(-16f, tween(40)) }
                shake.animateTo(0f, tween(40))
            }
        }
        fun press(d: String) {
            if (code.length >= length) return
            code += d
            if (code.length == length) { if (!onSubmit(code)) { code = ""; errorTick++ } }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = LocalSafeInsets.current.top + Tokens.Space.passTopGap), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("Enter Passcode", style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(length) { i ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < code.length) Color.White else Color(0x59FFFFFF)))
                }
            }
            Spacer(Modifier.height(40.dp))
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { row.forEach { d -> Key(d) { press(d) } } }
                Spacer(Modifier.height(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                    BasicText("Cancel", style = Glass.bodyBold, modifier = Modifier.noRippleClickable(onCancel))
                }
                Key("0") { press("0") }
                Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                    BasicText("Delete", style = Glass.bodyBold, modifier = Modifier.noRippleClickable { code = code.dropLast(1) })
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    val letters = mapOf("2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ")
    Box(Modifier.size(78.dp).pressable(onClick).glass(CircleShape), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(label, style = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium))
            letters[label]?.let { BasicText(it, style = TextStyle(color = Glass.labelDim, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)) }
        }
    }
}
