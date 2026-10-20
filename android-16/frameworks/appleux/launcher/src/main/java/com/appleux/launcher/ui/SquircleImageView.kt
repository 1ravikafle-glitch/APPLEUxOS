package com.appleux.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView

/**
 * iOS tile renderer. Source icons are circular bitmaps (GMS round legacy
 * icons) or full-bleed adaptive layers, so no clip can produce a squircle:
 * instead the icon is composited in software — full-bleed squircle tile +
 * centered glyph — which is deterministic on every GPU and density.
 */
class SquircleImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : ImageView(context, attrs, defStyle) {

    private var tile: Bitmap? = null
    private var tileW = 0
    private var tileH = 0
    private var source: Drawable? = null

    override fun setImageDrawable(d: Drawable?) {
        source = d
        packArt = null
        calendarDay = null
        calendarWeekday = null
        tile = null
        super.setImageDrawable(null)
        requestLayout()
        invalidate()
    }

    private var packArt: Drawable? = null

    /**
     * Vendored iOS art: already squircle, drawn full-bleed (clipped for
     * safety). Cleared by [setImageDrawable]; the two modes never mix.
     */
    fun setPackArt(d: Drawable?) {
        packArt = d
        calendarDay = null
        calendarWeekday = null
        source = null
        tile = null
        super.setImageDrawable(null)
        requestLayout()
        invalidate()
    }

    var calendarDay: String? = null
    var calendarWeekday: String? = null

    /**
     * iOS calendar tile: blank pack art + live weekday (red) + day number.
     * The Glass calendar art ships empty exactly so the date draws live.
     */
    fun setCalendarArt(d: Drawable?, weekday: String?, day: String?) {
        // setPackArt clears the date fields, so assign AFTER it.
        setPackArt(d)
        calendarWeekday = weekday
        calendarDay = day
        requestLayout()
        invalidate()
    }

    /** 0 = none, 1 = YouTube (red + play), 2 = Safety (teal + shield). */
    var glyphKind: Int = 0

    fun setGlyphTile(kind: Int) {
        glyphKind = kind
        packArt = null
        source = null
        tile = null
        super.setImageDrawable(null)
        requestLayout()
        invalidate()
    }

    private fun drawGlyphTile(c: Canvas, w: Int, h: Int, kind: Int) {
        val s = w.coerceAtMost(h).toFloat()
        val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (kind == 1) android.graphics.Color.RED else 0xFF30B0C7.toInt()
        }
        c.drawPath(AppleIconSquircle.path(w, h), bg)
        val fg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        if (kind == 1) {
            val tri = android.graphics.Path().apply {
                moveTo(w * 0.40f, h * 0.32f)
                lineTo(w * 0.40f, h * 0.68f)
                lineTo(w * 0.68f, h * 0.50f)
                close()
            }
            c.drawPath(tri, fg)
        } else {
            val sh = android.graphics.Path().apply {
                moveTo(w * 0.50f, h * 0.18f)
                lineTo(w * 0.78f, h * 0.30f)
                lineTo(w * 0.78f, h * 0.52f)
                cubicTo(w * 0.78f, h * 0.70f, w * 0.64f, h * 0.80f, w * 0.50f, h * 0.86f)
                cubicTo(w * 0.36f, h * 0.80f, w * 0.22f, h * 0.70f, w * 0.22f, h * 0.52f)
                lineTo(w * 0.22f, h * 0.30f)
                close()
            }
            fg.style = android.graphics.Paint.Style.STROKE
            fg.strokeWidth = s * 0.07f
            fg.strokeJoin = android.graphics.Paint.Join.ROUND
            c.drawPath(sh, fg)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (glyphKind != 0 && width > 0 && height > 0) {
            val out = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            drawGlyphTile(c, width, height, glyphKind)
            canvas.drawBitmap(out, 0f, 0f, null)
            out.recycle()
            return
        }
        val art = packArt
        if (art != null) {
            if (width > 0 && height > 0) {
                val save = canvas.save()
                canvas.clipPath(AppleIconSquircle.path(width, height))
                art.setBounds(0, 0, width, height)
                art.draw(canvas)
                val day = calendarDay
                if (day != null) {
                    val s = width / 60f
                    calendarWeekday?.let {
                        val wp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.RED
                            textSize = 11f * s
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        canvas.drawText(it, width / 2f, height * 0.30f, wp)
                    }
                    val dp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.BLACK
                        textSize = 30f * s
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    canvas.drawText(day, width / 2f, height * 0.68f, dp)
                }
                canvas.restoreToCount(save)
            }
            return
        }
        if (tile == null || tileW != width || tileH != height) renderTile()
        val b = tile
        if (b != null) canvas.drawBitmap(b, 0f, 0f, null)
        else super.onDraw(canvas)
    }

    private fun renderTile() {
        val w = width; val h = height
        tileW = w; tileH = h
        val src = source
        if (w <= 0 || h <= 0 || src == null) {
            tile = null
            return
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        // iOS icons are full-bleed artwork: an adaptive icon renders ALL
        // layers edge-to-edge inside the squircle (color preserved). Legacy
        // round art gets a white tile + inset glyph instead.
        val adaptive = src as? AdaptiveIconDrawable
        if (adaptive != null) {
            // Clip stack: squircle path as the clip, layers drawn full-bleed.
            c.save()
            c.clipPath(AppleIconSquircle.path(w, h))
            adaptive.setBounds(0, 0, w, h)
            adaptive.draw(c)
            c.restore()
        } else {
            val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawPath(AppleIconSquircle.path(w, h), base)
            val insetX = (w * 0.16f).toInt()
            val insetY = (h * 0.16f).toInt()
            src.setBounds(insetX, insetY, w - insetX, h - insetY)
            val layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val lc = Canvas(layer)
            src.draw(lc)
            c.drawBitmap(layer, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
            layer.recycle()
        }
        tile = out
    }
}
