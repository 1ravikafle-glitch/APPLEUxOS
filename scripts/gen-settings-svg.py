#!/usr/bin/env python3
"""Generate high-quality SVG settings icons for the iOS 27 HTML mockup.

WHY THIS EXISTS
---------------
The mockup shipped 28 Settings row icons as 64x64 JPEG crops lifted straight
out of an iPhone 11 screenshot. index.html renders them at 30x30 CSS px, and
the hero variant at 50x50. On a 2x display that needs 60-100 physical pixels
and on 3x it needs 90-150 -- all from a 64px source, with JPEG ringing on
small white-on-colour glyphs. They are blurry at every density above 1x.

SVG removes the resolution problem entirely: one vector is correct at 30px,
50px, 120px and any DPR, and it weighs less than the JPEG.

SOURCES
-------
Glyphs come from our own Android VectorDrawables in
appleux/design-system/src/main/res/drawable/, which already follow the il_ic_*
convention (24dp, white stroke) used across the project. Colours come from
index.html itself (line 347: O/B/G/Y/R/P) so the replacements match the
mockup rather than guessing from a different iOS release.

Each generated icon is the real iOS construction: a superellipse-ish rounded
square in the row colour, with a white glyph centred inside it.
"""

import io
import os
import re
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
DRAWABLE = (
    "/home/elfakiris/OsProject/appleux/design-system/src/main/res/drawable"
)
OUT_DIR = "/home/elfakiris/Videos/iosssssss/ios27/assets/settings-svg"

# iOS renders these tiles at ~29pt with a corner radius of about 22.37% of the
# side, which is the superellipse approximation the platform actually uses.
RADIUS_RATIO = 0.2237

# Glyph occupies 62% of the tile, centred, matching SF Symbols inside a
# Settings tile.
GLYPH_SCALE = 0.62

# slug used by index.html -> (vector file, colour)
# Colours: O #ff9500 B #007aff G #34c759 Y #8e8e93 R #ff3b30 P #5856d6
ICONS = {
    "airplane-mode":              ("il_ic_airplane",              "#ff9500"),
    "wi-fi":                      ("sf_wifi",                     "#007aff"),
    "bluetooth":                  ("il_ic_bluetooth",             "#007aff"),
    "cellular":                   ("sf_cellular",                 "#34c759"),
    "personal-hotspot":           ("sf_personal_hotspot",         "#8fd9a4"),
    "battery":                    ("il_ic_battery",               "#34c759"),
    "general":                    ("sf_settings",                 "#8e8e93"),
    "accessibility":              ("il_ic_accessibility",         "#007aff"),
    "appearance":                 ("il_ic_appearance",            "#000000"),
    "camera":                     ("sf_camera",                   "#8e8e93"),
    "control-center":             ("il_ic_controlcenter",         "#8e8e93"),
    "display-brightness":         ("il_ic_brightness",            "#007aff"),
    "home-screen-app-library":    ("sf_app_library",              "#007aff"),
    "search":                     ("il_ic_search",                "#8e8e93"),
    "siri":                       ("il_ic_sparkle",               "#5b3fd1"),
    "standby":                    ("il_ic_appearance",            "#000000"),
    "wallpaper":                  ("il_ic_photos",                "#32ade6"),
    "notifications":              ("sf_bell",                     "#ff3b30"),
    "sounds-haptics":             ("sf_volume",                   "#ff3b30"),
    "focus":                      ("il_ic_moon",                  "#5856d6"),
    "screen-time":                ("il_ic_hourglass",             "#5856d6"),
    "face-id-passcode":           ("il_ic_faceid",                "#34c759"),
    "emergency-sos":              ("il_ic_sos",                   "#ff3b30"),
    "privacy-security":           ("il_ic_shield",                "#007aff"),
    "game-center":                ("il_ic_gamecenter",            "#ee4b6a"),
    "icloud":                     ("il_ic_icloud",                "#4aa8ff"),
    "wallet":                     ("sf_wallet",                   "#f0a86b"),
    "apps":                       ("il_ic_grid",                  "#5856d6"),
}

# Android VectorDrawable attribute -> SVG attribute.
# pathData is handled separately: mapping it here as well would emit a
# duplicate d= attribute, which is a hard XML error.
ATTR_MAP = {
    "fillColor": "fill",
    "fillAlpha": "fill-opacity",
    "strokeColor": "stroke",
    "strokeWidth": "stroke-width",
    "strokeAlpha": "stroke-opacity",
    "strokeLineCap": "stroke-linecap",
    "strokeLineJoin": "stroke-linejoin",
    "strokeMiterlimit": "stroke-miterlimit",
    "fillType": "fill-rule",
}
FILL_RULE = {"nonZero": "nonzero", "evenOdd": "evenodd"}

WHITE = "#ffffff"


def to_svg_colour(raw):
    """Android stores colours as #AARRGGBB. SVG wants #RRGGBB / #RRGGBBAA.

    The alpha byte comes FIRST in Android's form, so #FF000000 is opaque black,
    not transparent black. Dropping the wrong end turns white glyphs black.
    """
    if not raw.startswith("#"):
        return raw
    h = raw[1:]
    if len(h) == 8:
        return "#" + h[2:]
    if len(h) == 4:
        return "#" + h[1:]
    return raw


def esc_attr(value):
    return (value.replace("&", "&amp;").replace("<", "&lt;")
                 .replace(">", "&gt;").replace('"', "&quot;"))


def _path_element(node):
    """Emit one SVG <path>. The glyph is forced white: the rounded tile behind
    it supplies the colour, exactly as iOS draws these rows."""
    stroked = node.get(ANDROID_NS + "strokeColor") is not None
    d = node.get(ANDROID_NS + "pathData")
    if not d:
        return None

    parts = ['d="%s"' % esc_attr(d)]

    if stroked:
        stroke = node.get(ANDROID_NS + "strokeColor")
        alpha = node.get(ANDROID_NS + "strokeAlpha")
        if stroke:
            parts.append('stroke="%s"' % WHITE)
        if alpha:
            parts.append('stroke-opacity="%s"' % alpha)
        width = node.get(ANDROID_NS + "strokeWidth")
        if width:
            parts.append('stroke-width="%s"' % width)
        for a, s in (("strokeLineCap", "stroke-linecap"),
                     ("strokeLineJoin", "stroke-linejoin"),
                     ("strokeMiterlimit", "stroke-miterlimit")):
            raw = node.get(ANDROID_NS + a)
            if raw:
                parts.append('%s="%s"' % (s, esc_attr(raw)))
        parts.append('fill="none"')
    else:
        parts.append('fill="%s"' % WHITE)
        alpha = node.get(ANDROID_NS + "fillAlpha")
        if alpha:
            parts.append('fill-opacity="%s"' % alpha)
        rule = node.get(ANDROID_NS + "fillType")
        if rule:
            parts.append('fill-rule="%s"' % FILL_RULE.get(rule, rule))

    return " ".join(parts)


def _walk(node, inherited, out):
    """Depth-first walk that flattens nested <group> transforms.

    Transform ORDER matters and Android's is the opposite of the intuitive
    one. Android composes a group as
        translate(t) * rotate(r, pivot) * scale(s) * translate(-pivot)
    and points pass through scale first, rotation next, translate last. An
    SVG transform list is read as a matrix product applied left to right, so
    the same matrix must be written translate, rotate, scale. The sf_*
    vectors depend on this: they carry scaleY=-1 with translateY=2048 to flip
    SF Symbols' Y-up geometry into Android's Y-down space.
    """
    for child in node:
        tag = child.tag.split("}")[-1]
        if tag == "path":
            attrs = _path_element(child)
            if attrs:
                if inherited:
                    out.append('    <g transform="%s">\n      <path %s/>\n    </g>'
                               % (inherited, attrs))
                else:
                    out.append("    <path %s/>" % attrs)
        elif tag == "group":
            def num(name, default):
                raw = child.get(ANDROID_NS + name)
                return default if raw is None else float(raw)

            sx = num("scaleX", 1.0)
            sy = num("scaleY", 1.0)
            tx = num("translateX", 0.0)
            ty = num("translateY", 0.0)
            px = num("pivotX", 0.0)
            py = num("pivotY", 0.0)
            rot = num("rotation", 0.0)

            mine = []
            if tx or ty or px or py:
                mine.append("translate(%g %g)" % (tx + px, ty + py))
            if rot:
                mine.append("rotate(%g %g %g)" % (rot, px, py))
            if sx != 1.0 or sy != 1.0:
                mine.append("scale(%g %g)" % (sx, sy))
            if px or py:
                mine.append("translate(%g %g)" % (-px, -py))

            combined = " ".join(mine + ([inherited] if inherited else []))
            _walk(child, combined, out)


def load_glyph(vector_name):
    """Convert an Android VectorDrawable into (paths, viewport_w, viewport_h).

    Paths come back wrapped in any transforms the file needs, expressed in the
    vector's own viewport coordinates; the caller only scales, so no
    coordinate rewriting happens here.
    """
    path = os.path.join(DRAWABLE, vector_name + ".xml")
    if not os.path.isfile(path):
        raise FileNotFoundError(path)

    root = ET.parse(path).getroot()

    def num(name, default):
        raw = root.get(ANDROID_NS + name)
        return default if raw is None else float(raw)

    vw = num("viewportWidth", 24.0)
    vh = num("viewportHeight", 24.0)

    out = []
    _walk(root, "", out)

    if not out:
        raise ValueError("no usable <path> in %s" % path)
    return out, vw, vh


def build_svg(slug):
    vector_name, colour = ICONS[slug]
    paths, vw, vh = load_glyph(vector_name)

    size = 128.0
    r = size * RADIUS_RATIO

    side = size * GLYPH_SCALE
    ox = (size - side) / 2.0
    oy = (size - side) / 2.0
    sx = side / vw
    sy = side / vh

    glyph = "\n".join(
        '  <g transform="translate(%.3f %.3f) scale(%.6f %.6f)">\n'
        '%s\n  </g>' % (ox, oy, sx, sy, "\n".join(paths))
        for paths in [paths]
    )

    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<!-- %s: vector glyph %s on the iOS rounded-square tile.\n'
        '     Replaces a 64x64 JPEG screenshot crop; resolution-independent. -->\n'
        '<svg xmlns="http://www.w3.org/2000/svg" width="%g" height="%g" '
        'viewBox="0 0 %g %g" role="img" aria-label="%s">\n'
        '  <rect width="%g" height="%g" rx="%.3f" fill="%s"/>\n'
        '%s\n'
        '</svg>\n'
        % (slug, vector_name, size, size, size, size, slug.replace("-", " "),
           size, size, r, colour, glyph)
    )


def main():
    if not os.path.isdir(DRAWABLE):
        sys.exit("drawable dir missing: %s" % DRAWABLE)
    os.makedirs(OUT_DIR, exist_ok=True)

    made, failed = [], []
    for slug in sorted(ICONS):
        try:
            svg = build_svg(slug)
        except Exception as exc:  # noqa: BLE001 - report, do not abort
            failed.append((slug, str(exc)))
            continue
        with io.open(os.path.join(OUT_DIR, slug + ".svg"), "w",
                     encoding="utf-8") as fh:
            fh.write(svg)
        made.append(slug)

    print("generated %d/%d" % (len(made), len(ICONS)))
    for slug, err in failed:
        print("  FAILED %-26s %s" % (slug, err))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())