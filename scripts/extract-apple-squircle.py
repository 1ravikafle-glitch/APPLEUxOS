#!/usr/bin/env python3
"""Extract Apple's official iOS 27 app-icon squircle path.

Source of truth: the "App Icon Template.ai" that Apple ships in
iOS-27-Icon-Templates-Photoshop-Illustrator.dmg (linked from
developer.apple.com/design/resources/). The .ai file is a PDF; the squircle
is emitted as a single path built from cubic Bezier segments on a 1024pt
grid.

Emits design-reference/ios27/official-apple/squircle-path-1024.json plus a
printout of the geometry, so the AppleUX icon mask can be generated from
Apple's real numbers instead of a guessed corner radius.
"""
import json
import os
import re
import sys

AI = sys.argv[1] if len(sys.argv) > 1 else (
    "/home/elfakiris/OsProject/design-reference/ios27/official-apple/"
    "iOS-27-Icon-Templates-Photoshop-Illustrator/"
    "iOS 27 - Icon Templates - Illustrator/App Icon Template.ai"
)
OUT = sys.argv[2] if len(sys.argv) > 2 else (
    "/home/elfakiris/OsProject/design-reference/ios27/official-apple/"
    "squircle-path-1024.json"
)

import zlib

data = open(AI, "rb").read()
# The .ai uses bare \r separators, so do not anchor on \r\n.
pat = re.compile(rb"<<(.*?)>>\s*stream\r?\n?(.*?)\r?\n?endstream", re.S)
content = b""
for hdr, body in pat.findall(data):
    if b"FlateDecode" in hdr:
        try:
            body = zlib.decompress(body)
        except Exception:
            continue
    content += body

text = content.decode("latin-1")

paths, cur = [], None
for line in text.split("\n"):
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


def bbox(p):
    pts = [p["m"]] + p["l"] + [c[4:6] for c in p["c"]]
    xs = [q[0] for q in pts]
    ys = [q[1] for q in pts]
    return max(xs) - min(xs), max(ys) - min(ys), min(xs), min(ys)


# The squircle is the path with the most curve segments: Apple's icon mask.
squircle = max(paths, key=lambda p: len(p["c"]))
w, h, minx, miny = bbox(squircle)

print("paths in file      :", len(paths))
print("squircle segments  :", len(squircle["c"]))
print("squircle bbox      : %.3f x %.3f at (%.3f, %.3f)" % (w, h, minx, miny))
print("grid               : 1024 x 1024")
print("normalized radius  : %.5f of grid" % (min(w, h) / 1024.0))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
json.dump(
    {
        "source": "Apple iOS 27 App Icon Template.ai (devimages-cdn.apple.com)",
        "grid": 1024,
        "bbox": [minx, miny, w, h],
        "start": squircle["m"],
        "lines": squircle["l"],
        "curves": squircle["c"],
    },
    open(OUT, "w"),
    indent=1,
)
print("wrote", OUT)