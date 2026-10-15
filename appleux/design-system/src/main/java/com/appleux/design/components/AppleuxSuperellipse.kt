package com.appleux.design.components

import android.graphics.Outline
import android.graphics.Path

/**
 * Continuous-curvature ("squircle") corners for glass SURFACES, not icons.
 *
 * WHY — the prototype (IOSLikeROM `theme/LiquidGlass.kt`) drew every glass
 * element with `ContinuousRoundedRectangle(r)` rather than a circular-corner
 * rounded rect, and that is visibly correct: iOS's corner is a superellipse,
 * and a circular corner at the same apparent radius reads as noticeably
 * "tighter" than the real thing. Our icon path already uses Apple's own 40
 * segments, but every glass surface here was still a plain
 * `GradientDrawable.cornerRadius`, so the dock bar, the Control Center sheet
 * and the widget cards were the only visibly non-iOS shapes left on screen.
 *
 * PROVENANCE, precisely:
 *  - [EXPONENT] 4.4 is **measured from Apple's own artefact**: the outline in
 *    `App Icon Template.ai` shipped in
 *    `design-reference/ios27/official-apple/iOS-27-Icon-Templates.dmg`. The
 *    superellipse fit beat a rounded rect on mean absolute pixel difference
 *    (0.0055 vs 0.0088). See that directory's README.
 *  - Applying that exponent to glass panels — rather than only to app icons —
 *    is **AppleUX-chosen**. Apple documents no radius for the dock or the
 *    sheets, and the values that reach this helper come from
 *    [com.appleux.design.tokens.LiquidGlassTokens], which carries its own
 *    separate provenance (transcribed from the iOS 27 Figma kit).
 */
object AppleuxSuperellipse {

    /**
     * Superellipse exponent fitted to Apple's shipped icon outline. n = 2 is
     * an ellipse/circle; larger is squarer. 4.4 is the measured fit.
     */
    const val EXPONENT = 4.4f

    /** Points sampled per corner. 16 is well under a pixel of chord error at
     *  any radius this ROM uses (max 34 dp) and costs nothing per frame,
     *  because the path is built once per layout, not per draw. */
    private const val SAMPLES_PER_CORNER = 16

    /**
     * A rounded-rect path whose corners are quarter superellipses.
     *
     * @param radiusPx corner "radius" expressed the way iOS expresses it: the
     *   distance from the corner to the start of the curve along the edge. For
     *   a superellipse that is the superellipse's own corner box, so it
     *   matches how `cornerRadius` reads on a rounded rect.
     */
    fun path(width: Int, height: Int, radiusPx: Float): Path {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = radiusPx.coerceIn(0f, minOf(w, h) / 2f)
        val p = Path()
        if (r <= 0.5f) {
            p.addRect(0f, 0f, w, h, Path.Direction.CW)
            return p
        }

        // Corner traversal order: top-right, bottom-right, bottom-left,
        // top-left. Each corner is a quarter of |x/a|^n + |y/a|^n = 1
        // centred on the corner, so it is solved parametrically.
        corner(p, w - r, r, r, -90f, 0f)
        corner(p, w - r, h - r, r, 0f, 90f)
        corner(p, r, h - r, r, 90f, 180f)
        corner(p, r, r, r, 180f, 270f)
        p.close()
        return p
    }

    fun outline(width: Int, height: Int, radiusPx: Float, out: Outline) {
        if (width <= 0 || height <= 0) return
        out.setPath(path(width, height, radiusPx))
    }

    /**
     * Appends one quarter corner centred on (cx, cy), sweeping [startDeg] to
     * [endDeg] in the SVG-ish frame used here: 0 deg is the +x axis, angles
     * increase clockwise on screen because y grows downward.
     */
    private fun corner(p: Path, cx: Float, cy: Float, a: Float, startDeg: Float, endDeg: Float) {
        for (i in 0..SAMPLES_PER_CORNER) {
            val t = i.toFloat() / SAMPLES_PER_CORNER
            val deg = startDeg + (endDeg - startDeg) * t
            val rad = Math.toRadians(deg.toDouble())
            val c = Math.cos(rad).toFloat()
            val s = Math.sin(rad).toFloat()
            // Superellipse: |c|^n and |s|^n keep the parametric form exact
            // (cos^n, sin^n), which is why the exponent is applied to the
            // unit-circle parameterisation rather than to the offset.
            val x = cx + a * Math.pow(
                Math.abs(c).toDouble(), (2.0 / EXPONENT.toDouble())
            ).toFloat() * sign(c)
            val y = cy + a * Math.pow(
                Math.abs(s).toDouble(), (2.0 / EXPONENT.toDouble())
            ).toFloat() * sign(s)
            if (i == 0 && p.isEmpty) p.moveTo(x, y) else p.lineTo(x, y)
        }
    }

    private fun sign(v: Float): Float = if (v < 0f) -1f else 1f
}