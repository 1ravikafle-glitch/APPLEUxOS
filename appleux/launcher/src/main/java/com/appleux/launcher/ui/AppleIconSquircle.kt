package com.appleux.launcher.ui

import android.graphics.Outline
import android.graphics.Path

/**
 * Apple's iOS 27 app-icon squircle, as shipped.
 *
 * This is NOT a rounded rectangle. The outline is a continuous-curvature
 * superellipse (best-fit exponent n ~= 4.4, measured against Apple's own
 * "App Icon Template.ai" - see design-reference/ios27/official-apple/).
 * A circular-corner rounded rect at the same apparent radius differs by
 * ~38%% more from the real outline, which is visible at icon size.
 *
 * GENERATED FILE - do not hand-edit. Regenerate with:
 *   python3 scripts/generate-apple-squircle-kt.py
 *
 * Coordinates are Apple's, on a 1024x1024 grid, origin normalised
 * to the top-left of the outline.
 */
object AppleIconSquircle {

    /** Apple's icon grid, as published in the icon template. */
    const val GRID = 1024f

    /** Superellipse exponent fitted to Apple's outline (n=2 is a circle). */
    const val EXPONENT = 4.4f

    private val START_X = 1024.000f
    private val START_Y = 373.000f

    /** 40 cubic segments, exactly as authored by Apple. */
    private val CURVES: Array<FloatArray> = arrayOf(
        floatArrayOf(1024.000f, 358.757f, 1024.006f, 344.517f, 1023.916f, 330.274f),
        floatArrayOf(1023.847f, 318.278f, 1023.709f, 306.284f, 1023.384f, 294.291f),
        floatArrayOf(1022.675f, 268.157f, 1021.137f, 241.797f, 1016.492f, 215.953f),
        floatArrayOf(1011.779f, 189.736f, 1004.088f, 165.336f, 991.963f, 141.516f),
        floatArrayOf(980.046f, 118.103f, 964.474f, 96.679f, 945.898f, 78.102f),
        floatArrayOf(927.321f, 59.526f, 905.897f, 43.954f, 882.484f, 32.037f),
        floatArrayOf(858.664f, 19.912f, 834.264f, 12.221f, 808.047f, 7.508f),
        floatArrayOf(782.203f, 2.863f, 755.843f, 1.325f, 729.709f, 0.616f),
        floatArrayOf(717.716f, 0.291f, 705.722f, 0.153f, 693.726f, 0.084f),
        floatArrayOf(679.483f, -0.006f, 665.243f, 0.000f, 651.000f, 0.000f),
        floatArrayOf(358.757f, 0.000f, 344.517f, -0.006f, 330.274f, 0.084f),
        floatArrayOf(318.278f, 0.153f, 306.284f, 0.291f, 294.291f, 0.616f),
        floatArrayOf(268.158f, 1.325f, 241.797f, 2.863f, 215.954f, 7.508f),
        floatArrayOf(189.736f, 12.221f, 165.336f, 19.912f, 141.516f, 32.037f),
        floatArrayOf(118.104f, 43.954f, 96.679f, 59.526f, 78.103f, 78.102f),
        floatArrayOf(59.526f, 96.679f, 43.955f, 118.103f, 32.037f, 141.516f),
        floatArrayOf(19.913f, 165.336f, 12.221f, 189.736f, 7.509f, 215.953f),
        floatArrayOf(2.863f, 241.797f, 1.325f, 268.157f, 0.617f, 294.291f),
        floatArrayOf(0.291f, 306.284f, 0.153f, 318.278f, 0.084f, 330.274f),
        floatArrayOf(-0.006f, 344.517f, 0.000f, 358.757f, 0.000f, 373.000f),
        floatArrayOf(0.000f, 665.243f, -0.006f, 679.483f, 0.084f, 693.726f),
        floatArrayOf(0.153f, 705.722f, 0.291f, 717.716f, 0.617f, 729.709f),
        floatArrayOf(1.325f, 755.842f, 2.863f, 782.203f, 7.509f, 808.046f),
        floatArrayOf(12.221f, 834.264f, 19.913f, 858.664f, 32.037f, 882.484f),
        floatArrayOf(43.955f, 905.896f, 59.526f, 927.321f, 78.103f, 945.897f),
        floatArrayOf(96.679f, 964.474f, 118.104f, 980.045f, 141.516f, 991.963f),
        floatArrayOf(165.336f, 1004.087f, 189.736f, 1011.779f, 215.954f, 1016.491f),
        floatArrayOf(241.797f, 1021.137f, 268.158f, 1022.675f, 294.291f, 1023.384f),
        floatArrayOf(306.284f, 1023.709f, 318.278f, 1023.847f, 330.274f, 1023.916f),
        floatArrayOf(344.517f, 1024.006f, 358.757f, 1024.000f, 373.000f, 1024.000f),
        floatArrayOf(665.243f, 1024.000f, 679.483f, 1024.006f, 693.726f, 1023.916f),
        floatArrayOf(705.722f, 1023.847f, 717.716f, 1023.709f, 729.709f, 1023.384f),
        floatArrayOf(755.843f, 1022.675f, 782.203f, 1021.137f, 808.047f, 1016.491f),
        floatArrayOf(834.264f, 1011.779f, 858.664f, 1004.087f, 882.484f, 991.963f),
        floatArrayOf(905.897f, 980.045f, 927.321f, 964.474f, 945.898f, 945.897f),
        floatArrayOf(964.474f, 927.321f, 980.046f, 905.896f, 991.963f, 882.484f),
        floatArrayOf(1004.088f, 858.664f, 1011.779f, 834.264f, 1016.492f, 808.046f),
        floatArrayOf(1021.137f, 782.203f, 1022.675f, 755.842f, 1023.384f, 729.709f),
        floatArrayOf(1023.709f, 717.716f, 1023.847f, 705.722f, 1023.916f, 693.726f),
        floatArrayOf(1024.006f, 679.483f, 1024.000f, 665.243f, 1024.000f, 651.000f)
    )

    /**
     * Builds the outline for a [w]x[h] icon box. The path is scaled from
     * Apple's 1024pt grid, so it stays exact at every density and every
     * launcher icon size.
     */
    fun outline(w: Int, h: Int, out: Outline) {
        if (w <= 0 || h <= 0) return
        val path = path(w, h)
        out.setPath(path)
    }

    fun path(w: Int, h: Int): Path {
        val s = minOf(w, h).toFloat() / GRID
        val p = Path()
        p.moveTo(START_X * s, START_Y * s)
        for (c in CURVES) {
            p.cubicTo(
                c[0] * s, c[1] * s,
                c[2] * s, c[3] * s,
                c[4] * s, c[5] * s
            )
        }
        p.close()
        return p
    }
}
