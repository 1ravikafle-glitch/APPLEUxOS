package org.ioslike.ui.theme

import android.app.ActivityManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle

/**
 * Real "liquid glass" via the open-source Kyant0/AndroidLiquidGlass `backdrop` library:
 * backdrop blur + vibrancy + lens refraction (AGSL, API 33+) + highlight rim, with continuous (squircle) corners.
 * Two layers: the BACKDROP (wallpaper / snapshot, marked layerBackdrop) and glass elements drawn on top of it.
 */
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/** iOS-style continuous-curvature corners (not plain circular arcs). */
fun squircle(r: Dp): Shape = ContinuousRoundedRectangle(r)

/**
 * Quality tiers so the glass runs on weak GPUs (e.g. Mali-G57 MC2 on Helio G99, 4 GB RAM).
 *  High   - blur + vibrancy + lens refraction
 *  Medium - blur + vibrancy, NO lens (the expensive part)
 *  Low    - no backdrop sampling at all: translucent tint + rim (see glass())
 * Auto-detected from RAM in ShellApp; override with GlassConfig.quality = ... after testing on the phone.
 */
enum class GlassQuality { High, Medium, Low }

object GlassConfig {
    @Volatile var quality: GlassQuality = GlassQuality.Medium
    fun autoDetect(ctx: Context) {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
        quality = when {
            am.isLowRamDevice || gb < 3.2 -> GlassQuality.Low
            gb < 7.0 -> GlassQuality.Medium      // a 4 GB phone reports roughly 3.5-3.8 GB
            else -> GlassQuality.High
        }
    }
}

fun Modifier.liquidGlass(backdrop: Backdrop, shape: Shape, tint: Color): Modifier = when (GlassConfig.quality) {
    GlassQuality.High -> drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(Tokens.GlassParams.highBlur.dp.toPx())
            lens(refractionHeight = Tokens.GlassParams.highLensHeight.dp.toPx(), refractionAmount = Tokens.GlassParams.highLensAmount.dp.toPx())
        },
        onDrawSurface = { drawRect(tint) },
    )
    GlassQuality.Medium -> drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(Tokens.GlassParams.mediumBlur.dp.toPx())
        },
        onDrawSurface = { drawRect(tint) },
    )
    GlassQuality.Low -> this.background(tint, shape)
}

/** Cheap "blur": shrink the wallpaper ~12x and let bilinear filtering stretch it back. Costs almost nothing per frame. */
private fun cheapBlur(src: ImageBitmap): ImageBitmap {
    val b = src.asAndroidBitmap()
    return Bitmap.createScaledBitmap(b, (b.width / Tokens.GlassParams.cheapBlurDivisor.toInt()).coerceAtLeast(1), (b.height / Tokens.GlassParams.cheapBlurDivisor.toInt()).coerceAtLeast(1), true).asImageBitmap()
}

/** Draws the backdrop (wallpaper, optionally blurred + dimmed) and provides it to every glass() inside. */
@Composable
fun GlassScene(
    wallpaper: ImageBitmap?,
    blur: Dp = 0.dp,
    dim: Float = 0.2f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val soft = remember(wallpaper) { if (blur > 0.dp) wallpaper?.let { cheapBlur(it) } else null }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            if (wallpaper != null) {
                if (blur > 0.dp && GlassConfig.quality != GlassQuality.High) {
                    Image(soft!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
                } else {
                    Image(
                        wallpaper, null,
                        Modifier.fillMaxSize().then(if (blur > 0.dp) Modifier.blur(blur) else Modifier),
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF5B6CFF), Color(0xFFFF7AB8), Color(0xFF2FD5C7)))))
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) { content() }
    }
}

/** iOS-like press feedback: springy scale-down + haptic. Put it BEFORE glass() so the glass scales too. */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) Tokens.Motion.pressScale else 1f, spring(dampingRatio = Tokens.Motion.pressDamping, stiffness = Tokens.Motion.pressStiffness), label = "press")
    val haptic = LocalHapticFeedback.current
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
        }
}

/** Current system wallpaper scaled/cropped to the screen, used as the glass backdrop. */
object WallpaperSource {
    fun load(ctx: Context): ImageBitmap? {
        return try {
            val dm = ctx.resources.displayMetrics
            val d = WallpaperManager.getInstance(ctx).peekDrawable() ?: return null
            val sw = dm.widthPixels
            val sh = dm.heightPixels
            val bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val iw = d.intrinsicWidth.coerceAtLeast(1)
            val ih = d.intrinsicHeight.coerceAtLeast(1)
            val scale = maxOf(sw.toFloat() / iw, sh.toFloat() / ih)
            val w = (iw * scale).toInt()
            val h = (ih * scale).toInt()
            d.setBounds((sw - w) / 2, (sh - h) / 2, (sw + w) / 2, (sh + h) / 2)
            d.draw(Canvas(bmp))
            bmp.asImageBitmap()
        } catch (_: Exception) { null }
    }
}
