package org.ioslike.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design tokens for the iOS-style "glass" look. Real backdrop blur comes from the WINDOW (see ShellService). */
object Glass {
    val fill = Tokens.Colors.glassFill
    val stroke = Tokens.Colors.glassStroke
    val label = Tokens.Colors.white
    val labelDim = Tokens.Colors.labelDim
    val blue = Tokens.Colors.blue
    val green = Tokens.Colors.green
    val orange = Tokens.Colors.orange
    val indigo = Tokens.Colors.indigo
    val corner = Tokens.Radius.panel

    val title = Tokens.Type.title.copy(color = label)
    val bodyBold = Tokens.Type.bodyBold.copy(color = label)
    val body = Tokens.Type.bodyDim.copy(color = labelDim)
    val caption = Tokens.Type.caption.copy(color = label)
}

/** Real liquid glass when a backdrop is provided (LocalBackdrop), translucent fallback otherwise. */
fun Modifier.glass(
    shape: Shape = squircle(Glass.corner),
    tint: Color = Glass.fill,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    if (backdrop == null || GlassConfig.quality == GlassQuality.Low) this.clip(shape).background(tint, shape).border(1.dp, Glass.stroke, shape)
    else this.liquidGlass(backdrop, shape, if (tint == Glass.fill) Tokens.Colors.glassFillBackdrop else tint)
}

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

fun <T> iosSpring() = spring<T>(dampingRatio = Tokens.Motion.springDamping, stiffness = Tokens.Motion.springStiffness)
