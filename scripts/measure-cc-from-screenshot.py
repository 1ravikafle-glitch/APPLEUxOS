#!/usr/bin/env python3
"""Measure the iOS 27 Control Center from Apple's own screenshot.

Source: design-reference/ios27/official-guide/images/7511c37e-*.png, which
carries Apple's alt text "Control Center open on the iPhone screen, with icons
on the right for viewing other groups of controls."

This is screenshot-driven design, not eyeballing: detect the circular controls
by their rim highlight, fit each blob's bounding box, and report the geometry
normalised to the screen. The numbers come out of Apple's pixels, so the
Android implementation can copy them instead of inventing them.
"""
import json
import os
from collections import deque

from PIL import Image

SRC = ("/home/elfakiris/OsProject/design-reference/ios27/official-guide/"
       "images/7511c37e-6788-4a26-ba9a-e01b54a20934.png")
OUT = ("/home/elfakiris/OsProject/design-reference/ios27/official-guide/"
       "control-center-geometry.json")

im = Image.open(SRC).convert("RGB")
W, H = im.size
px = im.load()

# --- 1. isolate the phone screen (drop the annotation gutter) --------------
mid = H // 2
cols = [x for x in range(W) if sum(px[x, mid]) / 3 > 55]
X0, X1 = cols[0], cols[-1]
SW = X1 - X0
print("screen: x %d..%d  width=%d  image=%dx%d" % (X0, X1, SW, W, H))

# --- 2. find circular controls ----------------------------------------------
# Controls sit in the upper ~70% of the screen. A control is a compact region
# whose pixels differ strongly from the blurred background around them.
# Detection: local luminance variance is high inside a control, low on glass.
LUM = [[sum(px[x, y]) / 3.0 for x in range(X0, X1 + 1)] for y in range(H)]
SWm = len(LUM[0])


def lum(x, y):
    if 0 <= y < H and 0 <= x < SWm:
        return LUM[y][x]
    return 0.0


def local_var(cx, cy, r):
    s = s2 = n = 0
    for y in range(max(0, cy - r), min(H, cy + r + 1), 2):
        for x in range(max(0, cx - r), min(SWm, cx + r + 1), 2):
            v = LUM[y][x]
            s += v
            s2 += v * v
            n += 1
    if n < 4:
        return 0.0
    m = s / n
    return s2 / n - m * m


# Coarse grid over SATURATION. The active controls in Apple's shot are
# strongly tinted (blue Wi-Fi, green lock, red calculator, blue camera), which
# is a far more reliable signal than luminance variance against a blurred
# glass background - variance thresholds simply do not separate there.
CELL = 8
colsN, rowsN = SWm // CELL, H // CELL
mask = [[False] * colsN for _ in range(rowsN)]


def sat(x, y):
    r, g, b = px[X0 + x, y]
    mx, mn = max(r, g, b), min(r, g, b)
    if mx == 0:
        return 0.0
    return (mx - mn) / float(mx)


SAT_MIN = 0.42
for gy in range(rowsN):
    for gx in range(colsN):
        cx, cy = gx * CELL + CELL // 2, gy * CELL + CELL // 2
        hits = 0
        total = 0
        for yy in range(cy - CELL // 2, cy + CELL // 2 + 1):
            for xx in range(cx - CELL // 2, cx + CELL // 2 + 1):
                if 0 <= xx < SWm and 0 <= yy < H:
                    total += 1
                    if sat(xx, yy) >= SAT_MIN:
                        hits += 1
        mask[gy][gx] = total > 0 and (hits / float(total)) >= 0.5

seen = [[False] * colsN for _ in range(rowsN)]
blobs = []
for gy in range(rowsN):
    for gx in range(colsN):
        if not mask[gy][gx] or seen[gy][gx]:
            continue
        q = deque([(gy, gx)])
        seen[gy][gx] = True
        pts = []
        while q:
            y, x = q.popleft()
            pts.append((x, y))
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < rowsN and 0 <= nx < colsN \
                        and mask[ny][nx] and not seen[ny][nx]:
                    seen[ny][nx] = True
                    q.append((ny, nx))
        if len(pts) < 40:
            continue
        x0 = min(p[0] for p in pts) * CELL
        x1 = (max(p[0] for p in pts) + 1) * CELL
        y0 = min(p[1] for p in pts) * CELL
        y1 = (max(p[1] for p in pts) + 1) * CELL
        bw, bh = x1 - x0, y1 - y0
        fill = len(pts) * CELL * CELL / float(bw * bh)
        # Keep only control-sized, reasonably solid blobs (grid cells of a
        # circular button fill ~pi/4; allow generous slack for the rim).
        if bw < 40 or bh < 40 or bw > 420 or bh > 620:
            continue
        if fill < 0.45:
            continue
        blobs.append({
            "x": round(x0 / SW, 4), "y": round(y0 / H, 4),
            "w": round(bw / SW, 4), "h": round(bh / H, 4),
            "w_px": bw, "h_px": bh,
            "fill": round(fill, 3),
            "centre_x": round(((x0 + x1) / 2.0) / SW, 4),
            "centre_y": round(((y0 + y1) / 2.0) / H, 4),
        })

blobs.sort(key=lambda b: (b["y"], b["x"]))
print("\ndetected %d control-sized regions (normalised to the screen):\n" % len(blobs))
print("  centre_x  centre_y   w      h     fill")
for b in blobs:
    print("   %.3f    %.3f   %.3f  %.3f   %.2f" %
          (b["centre_x"], b["centre_y"], b["w"], b["h"], b["fill"]))

json.dump({"source": os.path.basename(SRC), "screen_px": SW,
           "screen_aspect": round(H / float(SW), 3),
           "controls": blobs}, open(OUT, "w"), indent=1)
print("\nwrote", OUT)