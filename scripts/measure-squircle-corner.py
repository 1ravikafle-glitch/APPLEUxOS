#!/usr/bin/env python3
"""Measure the corner profile of Apple's iOS 27 icon outline.

Compares the extracted outline against two candidate models:
  * a circular-corner rounded rectangle (radius r)
  * a superellipse |x/a|^n + |y/a|^n = 1

Whichever fits is the shape we must reproduce. A squircle is NOT a rounded
rect: its curvature is continuous (G2) and the corner is fuller, which is
the whole point of the iOS icon shape.
"""
import json
import math
import os
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else (
    "/home/elfakiris/OsProject/design-reference/ios27/official-apple/"
    "squircle-path-1024.json"
)

doc = json.load(open(SRC))
minx, miny, w, h = doc["bbox"]
start = doc["start"]
curves = doc["curves"]

pts = [(start[0], start[1])]
cx, cy = start[0], start[1]
STEPS = 40
for c in curves:
    x1, y1, x2, y2, x3, y3 = c
    for i in range(1, STEPS + 1):
        t = i / STEPS
        m = 1 - t
        pts.append((
            m**3 * cx + 3 * m * m * t * x1 + 3 * m * t * t * x2 + t**3 * x3,
            m**3 * cy + 3 * m * m * t * y1 + 3 * m * t * t * y2 + t**3 * y3,
        ))
    cx, cy = x3, y3

xs = [p[0] for p in pts]
ys = [p[1] for p in pts]
bx0, by0, bx1, by1 = min(xs), min(ys), max(xs), max(ys)
size = max(bx1 - bx0, by1 - by0)
print("outline size: %.2f x %.2f" % (bx1 - bx0, by1 - by0))

# ---- corner span: where the top edge stops being straight ------------------
TOL = 0.75
top = by0 + TOL
top_pts = sorted([(p[0], p[1]) for p in pts if p[1] <= top])
left_edge_x = min(p[0] for p in top_pts)
right_edge_x = max(p[0] for p in top_pts)
corner_span = (right_edge_x - left_edge_x) - (bx1 - bx0)
radius = -corner_span / 2.0
print("straight top run: %.2f  -> corner span each side: %.2f" % (
    right_edge_x - left_edge_x, radius))
print("implied rounded-rect radius: %.3f / %.2f = %.4f of icon size"
      % (radius, size, radius / size))

# ---- fit a superellipse to the top-left corner -----------------------------
# Take the corner region and fit exponent n by least squares on normalised
# coordinates with the fitted "a" (semi-axis).
corner = [p for p in pts if p[0] <= bx0 + radius * 1.6 and p[1] <= by0 + radius * 1.6]
if corner:
    best = None
    for n10 in range(20, 91):
        n = n10 / 10.0
        # with a = size/2 centred in the bbox, score the corner points
        cxm = (bx0 + bx1) / 2.0
        cym = (by0 + by1) / 2.0
        a = size / 2.0
        err = 0.0
        for px, py in corner:
            v = (abs((px - cxm) / a) ** n + abs((py - cym) / a) ** n)
            err += (v - 1.0) ** 2
        err /= len(corner)
        if best is None or err < best[1]:
            best = (n, err)
    print("best-fit superellipse exponent n = %.1f  (rms=%.5f)" % best)
    print("  n=2 is an ellipse/circle, n=5 is the classic squircle")