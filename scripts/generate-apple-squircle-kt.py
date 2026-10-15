#!/usr/bin/env python3
"""Emit the Apple iOS 27 squircle as Kotlin source for the launcher.

Source of truth is Apple's own App Icon Template.ai, extracted by
scripts/extract-apple-squircle.py into squircle-path-1024.json. This
generator converts that into a buildable Path so the launcher clips icons
with Apple's real outline instead of a guessed rounded-rect radius.

Regenerate with:
    python3 scripts/generate-apple-squircle-kt.py
"""
import json
import os

SRC = ("/home/elfakiris/OsProject/design-reference/ios27/official-apple/"
       "squircle-path-1024.json")
OUT = ("/home/elfakiris/OsProject/appleux/launcher/src/main/java/"
       "com/appleux/launcher/ui/AppleIconSquircle.kt")

doc = json.load(open(SRC))
minx, miny, w, h = doc["bbox"]
start = doc["start"]
curves = doc["curves"]
GRID = float(doc["grid"])

lines = []
for i, c in enumerate(curves):
    sep = "," if i < len(curves) - 1 else ""
    lines.append("        floatArrayOf(%s)%s" % (
        ", ".join("%.3ff" % (v - (minx if k % 2 == 0 else miny))
                  for k, v in enumerate(c)),
        sep))

kt = '''package com.appleux.launcher.ui

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
 * Coordinates are Apple's, on a {grid:.0f}x{grid:.0f} grid, origin normalised
 * to the top-left of the outline.
 */
object AppleIconSquircle {{

    /** Apple's icon grid, as published in the icon template. */
    const val GRID = {grid:.0f}f

    /** Superellipse exponent fitted to Apple's outline (n=2 is a circle). */
    const val EXPONENT = 4.4f

    private val START_X = {sx:.3f}f
    private val START_Y = {sy:.3f}f

    /** 40 cubic segments, exactly as authored by Apple. */
    private val CURVES: Array<FloatArray> = arrayOf(
{curves}
    )

    /**
     * Builds the outline for a [w]x[h] icon box. The path is scaled from
     * Apple's {grid:.0f}pt grid, so it stays exact at every density and every
     * launcher icon size.
     */
    fun outline(w: Int, h: Int, out: Outline) {{
        if (w <= 0 || h <= 0) return
        val path = path(w, h)
        out.setPath(path)
    }}

    fun path(w: Int, h: Int): Path {{
        val s = minOf(w, h).toFloat() / GRID
        val p = Path()
        p.moveTo(START_X * s, START_Y * s)
        for (c in CURVES) {{
            p.cubicTo(
                c[0] * s, c[1] * s,
                c[2] * s, c[3] * s,
                c[4] * s, c[5] * s
            )
        }}
        p.close()
        return p
    }}
}}
'''.format(
    grid=GRID,
    sx=start[0] - minx,
    sy=start[1] - miny,
    curves="\n".join(lines),
)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w").write(kt)
print("wrote", OUT)
print("curves:", len(curves), "| start: %.3f, %.3f" % (start[0] - minx, start[1] - miny))