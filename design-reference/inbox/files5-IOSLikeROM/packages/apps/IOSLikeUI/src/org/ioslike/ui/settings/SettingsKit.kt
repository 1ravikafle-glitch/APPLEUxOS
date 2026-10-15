package org.ioslike.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.ioslike.ui.R
import org.ioslike.ui.theme.Glass
import org.ioslike.ui.theme.LocalBackdrop
import org.ioslike.ui.theme.glass
import org.ioslike.ui.theme.pressable
import org.ioslike.ui.theme.LocalSafeInsets
import org.ioslike.ui.theme.Tokens
import org.ioslike.ui.theme.squircle

/** iOS-style Settings kit: opaque grouped lists (like Apple's), glass only for the nav bar, back button and search. */
object SC {
    val bg = Tokens.Colors.bg; val cell = Tokens.Colors.cell; val cellPressed = Tokens.Colors.cellPressed; val sep = Tokens.Colors.separator
    val secondary = Tokens.Colors.secondary; val tertiary = Tokens.Colors.tertiary
    val blue = Tokens.Colors.blue; val green = Tokens.Colors.green; val orange = Tokens.Colors.orange; val red = Tokens.Colors.red
    val pink = Tokens.Colors.pink; val indigo = Tokens.Colors.indigo; val gray = Tokens.Colors.gray; val cyan = Tokens.Colors.cyan
    val title = Tokens.Type.largeTitle.copy(color = Color.White)
    val titleLong = Tokens.Type.largeTitleLong.copy(color = Color.White)
    val rowText = Tokens.Type.row.copy(color = Color.White)
    val rowValue = Tokens.Type.row.copy(color = secondary)
    val small = Tokens.Type.small.copy(color = secondary)
}

/** Large title that collapses into a glass nav bar while scrolling; optional glass back button. */
@Composable
fun SettingsPage(title: String, onBack: (() -> Unit)?, titleStyle: TextStyle = SC.title, content: @Composable ColumnScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(SC.bg)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = LocalSafeInsets.current.top + Tokens.Space.contentTopGap)) {
                if (onBack != null) Spacer(Modifier.height(52.dp))
                BasicText(
                    title, style = titleStyle,
                    modifier = Modifier.padding(horizontal = 20.dp).graphicsLayer { alpha = 1f - (scroll.value / 60f).coerceIn(0f, 1f) },
                )
                Spacer(Modifier.height(14.dp))
                content()
                Spacer(Modifier.height(56.dp + LocalSafeInsets.current.bottom))
            }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.fillMaxWidth().height(LocalSafeInsets.current.top + Tokens.Space.navBarExtra).graphicsLayer { alpha = (scroll.value / 60f).coerceIn(0f, 1f) }.glass(RectangleShape, Color(0x80000000)))
            Box(Modifier.fillMaxWidth().padding(top = LocalSafeInsets.current.top + Tokens.Space.navTopGap, start = 16.dp, end = 16.dp).height(44.dp)) {
                if (onBack != null) {
                    Box(Modifier.align(Alignment.CenterStart).size(44.dp).pressable(onBack).glass(CircleShape), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_chevron_l), "Back", Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.White))
                    }
                }
                BasicText(
                    title, style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = (scroll.value / 60f).coerceIn(0f, 1f) },
                )
            }
        }
    }
}

@Composable
fun SettingsSearch(modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().height(46.dp).glass(CircleShape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(painterResource(R.drawable.ic_search), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(SC.secondary))
        BasicText("Search", style = SC.rowValue)
    }
}

/** Inset grouped list: rounded container, optional header above and footer below. */
@Composable
fun Group(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 22.dp)) {
        header?.let { BasicText(it.uppercase(), style = SC.small, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) }
        Column(Modifier.fillMaxWidth().clip(squircle(26.dp)).background(SC.cell)) { content() }
        footer?.let { BasicText(it, style = SC.small, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }
    }
}

@Composable
fun IconTile(icon: Int, tint: Color) {
    Box(
        Modifier.size(30.dp).clip(squircle(7.dp)).background(Brush.linearGradient(listOf(lerp(tint, Color.White, 0.22f), tint))),
        contentAlignment = Alignment.Center,
    ) { Image(painterResource(icon), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color.White)) }
}

@Composable
fun SettingsRow(
    title: String,
    icon: Int? = null,
    tint: Color = SC.gray,
    value: String? = null,
    chevron: Boolean = true,
    divider: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier.background(if (pressed && onClick != null) SC.cellPressed else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = null, onClick = onClick) else Modifier),
    ) {
        Row(Modifier.fillMaxWidth().height(Tokens.Space.rowHeight).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
            if (icon != null) { IconTile(icon, tint); Spacer(Modifier.width(14.dp)) }
            BasicText(title, style = SC.rowText, modifier = Modifier.weight(1f), maxLines = 1)
            if (value != null) BasicText(value, style = SC.rowValue, maxLines = 1)
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
            if (chevron) {
                Spacer(Modifier.width(8.dp))
                Image(painterResource(R.drawable.ic_chevron_r), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(SC.tertiary))
            }
        }
        if (divider) Box(Modifier.align(Alignment.BottomStart).padding(start = if (icon != null) Tokens.Space.dividerIconIndent else Tokens.Space.dividerIndent).fillMaxWidth().height(0.5.dp).background(SC.sep))
    }
}

/** 51x31 switch with a springy knob, like the system one. */
@Composable
fun IOSSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "knob")
    val bg by animateColorAsState(if (checked) SC.green else Color(0xFF39393D), label = "track")
    Box(
        Modifier.size(51.dp, 31.dp).clip(CircleShape).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) },
    ) {
        Box(Modifier.offset(x, 2.dp).size(27.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
    }
}
