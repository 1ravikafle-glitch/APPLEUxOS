#!/usr/bin/env python3
"""Render every path in Apple's iOS 27 icon template as a contact sheet.

Extraction picked "the path with the most curves", which turned out to be a
plain rounded rect, not the squircle. Render all of them side by side and
look, instead of trusting a heuristic.
"""
import json
import os
import re
import sys
import zlib

from PIL import Image, ImageDraw

AI = (
    "/home/elfakiris/OsProject/design-reference/ios27/official-apple/"
    "iOS-27-Icon-Templates-Photoshop-Illustrator/"
    "iOS 27 - Icon Templates - Illustrator/App Icon Template.ai"
)
OUT = (
    "/home/elfakiris/OsProject/design-reference/ios27/official-apple/"
    "icon-template-paths.png"
)
CELL = 160
COLS = 7


def load_paths():
    data = open(AI, "rb").read()
    pat = re.compile(rb"<<(.*?)>>\s*stream\r?\n?(.*?)\r?\n?endstream", re.S)
    content = b""
    for hdr, body in pat.findall(data):
        if b"FlateDecode" in hdr:
            try:
                body = zlib.decompress(body)
            except Exception:
                continue
        content += body
    paths, cur = [], None
    for line in content.decode("latin-1").split("\n"):
        t = line.strip()
        if t.endswith(" m"):
            n = t[:-1].split()
            if len(n) >= 2:
                cur = {"m": [float(n[-2]), float(n[-1])], "l": [], "c": []}
                paths.append(cur)
        elif t.endswith(" l") and cur:
            n = t[:-1].split()
            if len(n) >= 2:
                cur["l"].append([float(n[-2]), float(n[-1])])
        elif t.endswith(" c") and cur:
            n = t[:-1].split()
            if len(n) >= 6:
                cur["c"].append([float(x) for x in n[-6:]])
    return paths


def flatten(p, steps=16):
    pts = [(p["m"][0], p["m"][1])]
    cx, cy = p["m"][0], p["m"][1]
    for c in p["c"]:
        x1, y1, x2, y2, x3, y3 = c
        for i in range(1, steps + 1):
            t = i / steps
            m = 1 - t
            pts.append((
                m**3 * cx + 3 * m * m * t * x1 + 3 * m * t * t * x2 + t**3 * x3,
                m**3 * cy + 3 * m * m * t * y1 + 3 * m * t * t * y2 + t**3 * y3,
            ))
        cx, cy = x3, y3
    pts.extend([(x, y) for x, y in p["l"]])
    return pts


paths = load_paths()
rows = (len(paths) + COLS - 1) // COLS
sheet = Image.new("RGB", (COLS * CELL, rows * CELL), (24, 26, 32))
dr = ImageDraw.Draw(sheet)

for i, p in enumerate(paths):
    pts = flatten(p)
    xs = [q[0] for q in pts]
    ys = [q[1] for q in pts]
    if not xs:
        continue
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    w, h = max(x1 - x0, 1e-6), max(y1 - y0, 1e-6)
    s = (CELL - 26) / max(w, h)
    ox = (i % COLS) * CELL
    oy = (i // COLS) * CELL
    poly = [(ox + 13 + (x - x0) * s, oy + 13 + (y - y0) * s) for x, y in pts]
    if len(poly) >= 3:
        dr.polygon(poly, outline=(0, 200, 255), fill=(40, 90, 130))
    dr.text((ox + 4, oy + CELL - 12),
            "#%d c=%d" % (i, len(p["c"])), fill=(220, 220, 220))

sheet.save(OUT)
print("wrote", OUT, sheet.size, "paths:", len(paths))