#!/usr/bin/env python3
"""Validate the iOS 27 icon outline: Apple vs rounded-rect vs superellipse.

Draws the three at large scale, cropped to the top-left corner so the
difference between a circular corner and a continuous-curvature squircle is
actually visible. Prints a pixel-difference score so the match is measured,
not eyeballed.
"""
import json
import math

from PIL import Image, ImageDraw

SRC = ("/home/elfakiris/OsProject/design-reference/ios27/official-apple/"
       "squircle-path-1024.json")
N = 1024
doc = json.load(open(SRC))

# ---- Apple's real outline (flattened) --------------------------------------
pts = [(doc["start"][0], doc["start"][1])]
cx, cy = doc["start"][0], doc["start"][1]
for c in doc["curves"]:
    x1, y1, x2, y2, x3, y3 = c
    for i in range(1, 49):
        t = i / 48.0
        m = 1 - t
        pts.append((
            m**3 * cx + 3 * m * m * t * x1 + 3 * m * t * t * x2 + t**3 * x3,
            m**3 * cy + 3 * m * m * t * y1 + 3 * m * t * t * y2 + t**3 * y3,
        ))
    cx, cy = x3, y3

xs = [p[0] for p in pts]
ys = [p[1] for p in pts]
bx0, by0, bx1, by1 = min(xs), min(ys), max(xs), max(ys)
apple = [((p[0] - bx0) / (bx1 - bx0), (p[1] - by0) / (by1 - by0)) for p in pts]

# ---- models in the same normalised space -----------------------------------
RADIUS = 0.2836  # measured above


def rounded_rect(n=512):
    r = RADIUS
    out = []
    # top edge L->R then corners; param by arc
    for i in range(n + 1):
        t = i / n
        out.append((r + t * (1 - 2 * r), 0.0))
    for i in range(1, n + 1):
        a = -math.pi / 2 + (math.pi / 2) * (i / n)
        out.append((1 - r + r * math.cos(a) * -1 if False else 1 - r - r * math.sin(a) * 0, 0))
    # build properly: 4 corners
    out = []
    corners = [
        (1 - r, r, -math.pi / 2, 0.0),
        (1 - r, 1 - r, 0.0, math.pi / 2),
        (r, 1 - r, math.pi / 2, math.pi),
        (r, r, math.pi, 3 * math.pi / 2),
    ]
    for ccx, ccy, a0, a1 in corners:
        for i in range(n + 1):
            a = a0 + (a1 - a0) * (i / n)
            out.append((ccx + r * math.cos(a), ccy + r * math.sin(a)))
    return out


def superellipse(n_exp=4.4, n=512):
    out = []
    for i in range(n * 4):
        a = 2 * math.pi * i / (n * 4)
        ca, sa = math.cos(a), math.sin(a)
        x = math.copysign(abs(ca) ** (2.0 / n_exp), ca)
        y = math.copysign(abs(sa) ** (2.0 / n_exp), sa)
        out.append((0.5 + 0.5 * x, 0.5 + 0.5 * y))
    return out


rr = rounded_rect()
se = superellipse()


def render(poly, size):
    img = Image.new("L", (size, size), 0)
    ImageDraw.Draw(img).polygon(
        [(x * size, y * size) for x, y in poly], fill=255
    )
    return img


a_img = render(apple, N)
r_img = render(rr, N)
s_img = render(se, N)


def diff(a, b):
    pa, pb = a.load(), b.load()
    tot = 0
    for y in range(0, N, 2):
        for x in range(0, N, 2):
            tot += abs(pa[x, y] - pb[x, y])
    return tot / ((N / 2) ** 2 * 255)


print("mean abs pixel difference vs Apple's outline (0 = identical):")
print("  rounded rect r=0.2836 : %.4f" % diff(a_img, r_img))
print("  superellipse n=4.4    : %.4f" % diff(a_img, s_img))

# Contact sheet: full icon row + zoomed top-left corner row.
Z = 420
sheet = Image.new("RGB", (N * 3 + 40, N + Z + 60), (18, 20, 26))
d = ImageDraw.Draw(sheet)
labels = ["APPLE iOS 27", "ROUNDED RECT", "SUPERELLIPSE n=4.4"]
for i, (im, lab) in enumerate(
    zip([a_img, r_img, s_img], labels)
):
    rgb = Image.merge("RGB", (im, im, im))
    sheet.paste(rgb, (i * (N + 20), 30))
    d.text((i * (N + 20) + 10, 10), lab, fill=(240, 240, 240))
    crop = im.crop((0, 0, 340, 340)).resize((Z, Z), Image.NEAREST)
    sheet.paste(Image.merge("RGB", (crop, crop, crop)), (i * (Z + 20), N + 50))
sheet.save(
    "/home/elfakiris/OsProject/design-reference/ios27/official-apple/"
    "squircle-validation.png"
)
print("wrote squircle-validation.png")