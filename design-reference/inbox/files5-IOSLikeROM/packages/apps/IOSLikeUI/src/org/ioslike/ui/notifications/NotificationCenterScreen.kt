package org.ioslike.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.ioslike.ui.theme.GlassScene
import org.ioslike.ui.theme.LocalSafeInsets
import org.ioslike.ui.theme.Tokens
import org.ioslike.ui.theme.squircle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.ioslike.ui.theme.Glass
import org.ioslike.ui.theme.glass
import org.ioslike.ui.theme.iosSpring
import org.ioslike.ui.theme.noRippleClickable
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun NotificationCenterScreen(
    notifs: List<NotifItem>,
    onOpen: (NotifItem) -> Unit,
    onDismiss: (NotifItem) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
    wallpaper: ImageBitmap? = null,
) {
    GlassScene(wallpaper, blur = Tokens.GlassParams.wallpaperBlur.dp, dim = 0.45f) { Box(Modifier.fillMaxSize().noRippleClickable(onClose)) {
        Column(Modifier.padding(horizontal = 22.dp).padding(top = LocalSafeInsets.current.top + Tokens.Space.contentTopGap).noRippleClickable { }) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                BasicText("Notifications", style = Glass.title)
                if (notifs.isNotEmpty()) {
                    BasicText(
                        "Clear", style = Glass.bodyBold,
                        modifier = Modifier.glass(squircle(17.dp)).noRippleClickable(onClearAll)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            if (notifs.isEmpty()) {
                BasicText("No Notifications", style = Glass.body, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                val groups = notifs.groupBy { it.pkg }.values.sortedByDescending { it.first().time }
                val cutoff = System.currentTimeMillis() - 10 * 60_000L
                val recent = groups.filter { it.first().time >= cutoff }
                val earlier = groups.filter { it.first().time < cutoff }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                    if (recent.isNotEmpty()) item(key = "h-now") { SectionLabel("Now") }
                    items(recent, key = { it.first().pkg }) { g -> GroupStack(g, onOpen, onDismiss) }
                    if (earlier.isNotEmpty()) item(key = "h-earlier") { SectionLabel("Earlier today") }
                    items(earlier, key = { it.first().pkg }) { g -> GroupStack(g, onOpen, onDismiss) }
                }
            }
        }
    }
    }
}

@Composable
private fun GroupStack(group: List<NotifItem>, onOpen: (NotifItem) -> Unit, onDismiss: (NotifItem) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (group.size == 1 || expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            group.forEach { SwipeCard(it, onOpen, onDismiss) }
        }
    } else {
        // collapsed stack: top card + two peeking layers
        Box {
            Box(Modifier.padding(horizontal = 24.dp).padding(top = 20.dp).fillMaxWidth().height(44.dp).glass(squircle(26.dp), Color(0x24FFFFFF)))
            Box(Modifier.padding(horizontal = 12.dp).padding(top = 10.dp).fillMaxWidth().height(44.dp).glass(squircle(26.dp), Color(0x30FFFFFF)))
            Column {
                SwipeCard(group.first(), onOpen = { expanded = true }, onDismiss = { g -> group.forEach(onDismiss) })
                BasicText("+${group.size - 1} more", style = Glass.body, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            }
        }
    }
}

@Composable
private fun SwipeCard(item: NotifItem, onOpen: (NotifItem) -> Unit, onDismiss: (NotifItem) -> Unit) {
    var dx by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    val shown by animateFloatAsState(dx, if (dragging) snap() else iosSpring(), label = "swipe")
    NotifCard(
        item,
        Modifier
            .offset { IntOffset(shown.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (shown / (threshold * 3f)).coerceIn(0f, 1f) }
            .pointerInput(item.key) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; if (dx > threshold) onDismiss(item) else dx = 0f },
                    onDragCancel = { dragging = false; dx = 0f },
                ) { change, amount -> dx = (dx + amount).coerceAtLeast(0f); change.consume() }
            }
            .noRippleClickable { onOpen(item) },
    )
}

@Composable
fun NotifCard(item: NotifItem, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().glass(squircle(26.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item.icon?.let {
            Image(it.asImageBitmap(), null, Modifier.size(40.dp).clip(squircle(10.dp)))
        } ?: Box(Modifier.size(40.dp).clip(squircle(10.dp)).background(Color(0x40FFFFFF)))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                BasicText(item.title, style = Glass.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                BasicText(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.time)), style = Glass.caption.copy(color = Glass.labelDim))
            }
            if (item.text.isNotEmpty()) BasicText(item.text, style = Glass.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** iOS 27-style banner: arrives by sliding in from the LEFT edge, auto-hides after a few seconds. */
@Composable
fun HeadsUpHost(arrived: SharedFlow<NotifItem>, onOpen: (NotifItem) -> Unit) {
    var shown by remember { mutableStateOf<NotifItem?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        arrived.collectLatest { shown = it; visible = true; delay(4500); visible = false }
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(iosSpring()) { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
    ) {
        shown?.let { n ->
            NotifCard(n, Modifier.width(346.dp).padding(top = 8.dp).noRippleClickable { visible = false; onOpen(n) })
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(text, style = Glass.bodyBold.copy(color = Glass.labelDim), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}
