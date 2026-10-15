#!/usr/bin/env python3
"""Render Apple's extracted squircle path to a PNG mask and an Android
ShapeDrawable-compatible corner spec.

Two outputs, both verified visually rather than trusted:
  1. squircle-mask-1024.png      - the flattened Apple path at 1024x1024
  2. apple-squircle-mask.xml     - VectorDrawable using the SAME path data,
                                   normalised to a 24x24 viewport, ready to
                                   drop into the AppleUX launcher icon mask.

The vector is taken from Apple's own iOS 27 icon template, so the launcher
stops guessing a "squircle-ish" corner radius.
"""
import json
import os
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else (
    "/home/elfakiris/OsProject/design-reference/ios27/official-apple/"
    "squircle-path-1024.json"
)
OUTDIR = os.path.dirname(SRC)

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow required for the verification render")

doc = json.load(open(SRC))
minx, miny, w, h = doc["bbox"]
start = doc["start"]
lines = doc["lines"]
curves = doc["curves"]

# --- flatten cubics ---------------------------------------------------------
pts = [(start[0], start[1])]
cx, cy = start[0], start[1]
STEPS = 24
for c in curves:
    x1, y1, x2, y2, x3, y3 = c
    for i in range(1, STEPS + 1):
        t = i / STEPS
        mt = 1 - t
        px = (mt**3) * cx + 3 * (mt**2) * t * x1 + 3 * mt * (t**2) * x2 + (t**3) * x3
        py = (mt**3) * cy + 3 * (mt**2) * t * y1 + 3 * mt * (t**2) * y2 + (t**3) * y3
        pts.append((px, py))
    cx, cy = x3, y3

print("flattened points:", len(pts))
xs = [p[0] for p in pts]
ys = [p[1] for p in pts]
bx0, by0, bx1, by1 = min(xs), min(ys), max(xs), max(ys)
print("flattened bbox : %.2f %.2f %.2f %.2f" % (bx0, by0, bx1, by1))
print("size           : %.2f x %.2f" % (bx1 - bx0, by1 - by0))

# --- render 1024 mask -------------------------------------------------------
N = 1024
scale = (N * 0.86) / max(bx1 - bx0, by1 - by0)
img = Image.new("L", (N, N), 0)
dr = ImageDraw.Draw(img)
poly = [
    (
        (p[0] - bx0) * scale + N * 0.07,
        (p[1] - by0) * scale + N * 0.07,
    )
    for p in pts
]
dr.polygon(poly, fill=255)
png = os.path.join(OUTDIR, "squircle-mask-1024.png")
img.save(png)
print("wrote", png)

# --- corner radius implied by Apple geometry -------------------------------
# For the top-left corner: the straight run along the top edge starts at the
# first x that equals the bbox left+entry offset. Measure it geometrically.
top_y = by0
left_x = bx0
# scan the flattened path for where it leaves the top edge horizontally
entry = None
for i, (px, py) in enumerate(pts):
    if abs(py - top_y) < 0.5:
        entry = min(entry, px) if entry is not None else px
corner_span = (bx1 - bx0) - (max(xs) - (min(xs)))  # symmetric sanity check
print("implied corner span (left+right): %.2f of %.2f grid = %.4f"
      % (corner_span, bx1 - bx0, corner_span / (bx1 - bx0)))

# --- VectorDrawable in a 24x24 viewport ------------------------------------
k = 24.0 / max(bx1 - bx0, by1 - by0)
def f(v):
    return round(v * k, 3)

d = "M%s,%s " % (f(start[0] - bx0), f(start[1] - by0))
for c in curves:
    d += "C%s,%s %s,%s %s,%s " % tuple(
        f(c[i] - (bx0 if i % 2 == 0 else by0)) for i in range(6)
    )
d += "z"

xml = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    "<!-- Apple iOS 27 app-icon squircle, extracted from Apple's official\n"
    "     App Icon Template.ai by scripts/extract-apple-squircle.py.\n"
    "     %d cubic segments on a 1024pt grid, normalised to 24x24. -->\n"
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="24dp"\n'
    '    android:height="24dp"\n'
    '    android:viewportWidth="24"\n'
    '    android:viewportHeight="24">\n'
    "  <path\n"
    '      android:fillColor="#FFFFFFFF"\n'
    '      android:pathData="%s" />\n'
    "</vector>\n"
) % (len(curves), d.strip())

vp = os.path.join(OUTDIR, "apple-squircle-mask.xml")
open(vp, "w").write(xml)
print("wrote", vp)
print("viewport 24 pathData length:", len(d))