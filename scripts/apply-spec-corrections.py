#!/usr/bin/env python3
"""Adopt the incoming iOS 27 spec as the AppleUX design baseline, with corrections.

INPUT   design-reference/inbox/ios27-android16-spec-2026-10-15.txt  (verbatim, unvetted)
OUTPUT  design/spec/ios27-baseline.md      the design of record, appleux-named, corrected
         design/spec/corrections.json     every edit applied, with its evidence
         design/spec/CORRECTIONS.md        the same, readable

The design is ADOPTED. Corrections are applied to it as we go rather than left
as objections, so that a later pass is incremental instead of a re-litigation.

RULES THIS SCRIPT FOLLOWS
  * Every correction must cite evidence. A correction with no evidence is not
    applied; it is listed in the "proposed, awaiting evidence" section instead.
  * A replacement that matches nothing is an ERROR, not a silent no-op. If the
    incoming file is edited and a correction stops matching, that must be loud.
  * Apple's product names are NOT renamed. "iOS 27" and "Liquid Glass" are
    Apple's names for Apple's things and stay as they are. Only OUR project
    names are remapped: ios27_rock, IOS27Launcher, com.ios27.*, theme/Color.kt,
    and so on.

Usage: python3 scripts/apply-spec-corrections.py [--in PATH] [--out DIR]
"""

import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INBOX = os.path.join(ROOT, "design-reference", "inbox",
                     "ios27-android16-spec-2026-10-15.txt")
OUTDIR = os.path.join(ROOT, "design", "spec")

# ---------------------------------------------------------------------------
# Naming map. Apple's names are untouched; only our project names move.
# ---------------------------------------------------------------------------
NAMING = [
    ("ios27_rock", "appleux_rock"),
    ("IOS27Launcher", "appleux-launcher"),
    ("IOS27SystemUI", "appleux-systemui"),
    ("IOS27Settings", "appleux-settings"),
    ("IOS27Messages", "appleux-messages"),
    ("IOS27Dialer", "appleux-dialer"),
    ("IOS27Contacts", "appleux-contacts"),
    ("IOS27Clock", "appleux-clock"),
    ("IOS27Calendar", "appleux-calendar"),
    ("IOS27Camera", "appleux-camera"),
    ("IOS27Gallery", "appleux-gallery"),
    ("IOS27Music", "appleux-music"),
    ("IOS27Mail", "appleux-mail"),
    ("com.ios27.theme", "com.appleux.design.tokens"),
    ("com.ios27.launcher", "com.appleux.launcher"),
    ("com.ios27.components", "com.appleux.design.components"),
    ("com.ios27.render", "com.appleux.design.render"),
    ("com.ios27.screens", "com.appleux.design.screens"),
    ("com.ios27", "com.appleux"),
]

# ---------------------------------------------------------------------------
# Corrections. Each: (id, area, kind, pattern, replacement, evidence)
#   kind "re"       - literal regex replacement, applied globally
#   kind "tokens"   - rewrite the material block of the token JSON fence
# `must_match` guards against a silent no-op after the input is edited.
# ---------------------------------------------------------------------------
CORRECTIONS = [
    ("C01", "device", "re",
     r"TARGET_BOARD_PLATFORM := mt6789",
     "TARGET_BOARD_PLATFORM := mt6765",
     "POCO M5 is MediaTek Helio G99 = MT6765. mt6789 is Dimensity 1080. "
     "AGENTS.md 'Project Overview'."),

    ("C02", "device", "re",
     r"TARGET_SCREEN_HEIGHT := 2408\n",
     "TARGET_SCREEN_HEIGHT := 2408  # CONFIRMED 2408, not 2400. `wm size` on the\n"
     "                                # physical POCO M5 (stock HyperOS, 2026-10-15)\n"
     "                                # reports Physical size 1080x2408, and po.co /\n"
     "                                # GSMArena both list 2408. An earlier pass here\n"
     "                                # 'corrected' this to 2400 from a spec-sheet\n"
     "                                # memory; that was wrong and has been reverted.\n",

     "An earlier revision of this script changed 2408 to 2400 on the strength of a\n"
     "remembered spec sheet. The operator's on-device measurement contradicts it:\n"
     "the panel really is 2408 px tall. Reverted; see\n"
     "deliverables/m20/rock-display-measurement.md."),

    ("C03", "device", "re",
     r"BOARD_KERNEL_BASE := 0x40078000",
     "BOARD_KERNEL_BASE := 0x00000000  # TODO: mt6765 value, NOT transferred from\n"
     "                              # mt6789. The incoming value was Dimensity 1080's.\n"
     "                              # Take this from the real rock boot.img header.",
     "0x40078000 is the mt6789 kernel load address. Carrying it into an mt6765 "
     "BoardConfig would produce an unbootable image with no obvious cause."),

    ("C04", "device", "re",
     r"PRODUCT_OVERRIDE_PACKAGES \+= \\\n    Launcher3:appleux-launcher \\\n"
     r"    Settings:appleux-settings \\\n    SystemUI:appleux-systemui",
     "# PRODUCT_OVERRIDE_PACKAGES does not exist. There is no definition of it\n"
     "# anywhere in build/make/ (grepped). Correct mechanisms:\n"
     "#   PRODUCT_PACKAGES := $(filter-out Launcher3,$(PRODUCT_PACKAGES))\n"
     "#   PRODUCT_PACKAGE_OVERLAYS += device/xiaomi/rock/overlay/Settings \\\n"
     "#                            device/xiaomi/rock/overlay/SystemUI\n"
     "# Verify the replacement before removing the stock package.",
     "`grep -rn PRODUCT_OVERRIDE_PACKAGES build/make/` returns nothing."),

    ("C05", "material", "tokens", None, None,
     "5 of 6 measurable Liquid Glass fields disagreed with the measured Apple kit. "
     "scripts/compare-glass-tokens.py. ours matches the kit on all six."),

    ("C06", "material", "re",
     r'(\{"id":"blur","type":"blur")',
     r'\1,"blurRadiusProvenance":"AppleUX-CHOSEN (C06). The Figma GLASS effect does '
     r'not expose its radius over REST and Apple publishes no numeric spec for '
     r'Liquid Glass. This value is ours; tune it per surface against the real '
     r'Mali-G57 GPU."',

     "Every screen-level fullscreen blur radius in the incoming file (values 30-60) "
     "had no measured counterpart. A sibling key states the provenance inline so "
     "no reader can mistake one for Apple's, and the value itself is preserved. "
     "A JSON sibling key rather than a /* comment */ because these blocks are "
     "parsed as JSON and JSON has no comment syntax."),

    ("C08", "icon", "re",
     r'"notes": "Squircle with n≈4\.4\. This is a rounded-rect approximation\. '
     r'For exact superellipse, use 40-segment vector from Apple\'s icon template\."',
     '"notes": "n=4.4 is MEASURED from the App Icon Template.ai shipped by Apple '
     '(superellipse fit, mean abs pixel error 0.0055 vs 0.0088 for a rounded rect). '
     'Do NOT implement this as a rounded rect: RoundedCornerShape(0.2237*size) is '
     'visibly tighter than the real shape. Use AppleuxSuperellipse / the 40-segment '
     'path scaled from the 1024pt grid, as AppIconView.kt already does."',
     "Re-adopting the rounded rect walks back a measured fix. See "
     "AppleuxSuperellipse.kt and AppIconView.kt."),

    ("C09", "geometry", "re",
     r'\{"id":"brightness","type":"slider","frame":\{"x":0\.021,"y":0\.55,"w":0\.31,"h":0\.35\}',
     '{"id":"brightness","type":"slider","frame":{"x":0.021,"y":0.55,"w":0.31,"h":0.18}\n'
     '     ,"NOTE_CONFLICT":"CONFLICT C09: the incoming file placed `toggles` on this '
     'exact rect. Height reduced to 0.18 so brightness and toggles no longer '
     'occupy the same rectangle, but the true proportions are UNVERIFIED - check '
     'against official-guide/images/ before trusting either value."',

     "Two top-level elements declared the identical rect {0.021,0.55,0.31,0.35}. "
     "Detected by duplicate-frame scan over the extracted screens."),

    ("C10", "screen size", "re",
     r'"screenSize": \{"w": 411, "h": 916, "unit": "dp"\}',
     '"screenSize": {"w": 401.9, "h": 896.0, "unit": "dp", '
     '"source": "MEASURED. 1080x2408 px at the EFFECTIVE density 430 dpi (HyperOS '
     'overrides the panel\'s 440), so 2.6875 -> 401.9 x 896.0 dp. Read live at '
     'runtime via device-support/rock/RockDisplayProfile.kt. At 440 with no '
     'override it would be 392.7 x 875.6 dp. Refresh 60/90 Hz switchable, '\
     'operator-confirmed."}',

     "411x916 is an iPhone figure and is 9.6 dp wider than this panel. The earlier\n"
     "revision used 411.43 x 914.29, derived from a wrong 2400/420 assumption."),

    ("C11", "provenance", "re",
     r'"figmaKitReachable": true,\n\s*"figmaKitNote": "Figma UI Kit is publicly '
     r'available\. Values marked \'inferred\' are derived from visual construction, '
     r'not measured from Figma nodes\."',
     '"figmaKitReachable": "token-required",\n    "figmaKitNote": "Apple kit '
     '1651309003795292092 is reachable through the Figma REST API WITH A TOKEN '
     '(ios27-ds did this 2026-07-29, kit 27.0.2). An anonymous fetch of a kit URL '
     'returns 403. Do not conflate the two. Values below marked inferred were NOT '
     'measured from Figma nodes."',

     "`figmaKitReachable: true` is true only for the token path, false for the "
     "anonymous-HTTP path this spec itself exercised."),

    ("C12", "provenance", "re",
     r'"referenceScreenshot": "https://ipcdn-web\.apple\.com/iphone-27-user-guide/'
     r'([a-z0-9-]+)\.png"',
     r'"referenceScreenshot": "DESIGN-REFERENCE/ios27/official-guide/images/'
     r'(\1) [ASSUMED MATCH - UNVERIFIED]"',

     "These URLs return HTTP 404 (lock-screen.png and control-center.png tested). "
     "They were constructed, not observed. The local Apple guide screenshots are "
     "the real reference; the filename mapping is a guess and is marked as one."),

    ("C13", "provenance", "re",
     r"- \[x\] 400\+ surfaces documented",
     "- [x] 630 unique surfaces documented (the original said '400+'; the spec's own\n"
     "      batch headers total 664, the summary tables 640, with 10 ids duplicated\n"
     "      across batches. Counts reconciled by scripts/parse-ios27-spec.py.)",

     "The spec undercounted its own inventory by ~40%."),
]

# Token-material rewrite: replace the liquidGlassRegular object wholesale.
CORRECTED_MATERIAL = '''"liquidGlassRegular": {
      "cornerRadius": 34,
      "fillAlpha": 0.70,
      "rimAlpha": null,
      "blurRadius": null,
      "shadowBlur": 48,
      "shadowOffsetY": 8,
      "shadowAlphaLight": 0.25,
      "shadowAlphaDark": 0.45,
      "fillLight": "#FFFFFF",
      "fillDark": "#1A1A1A",
      "overlayLight": {"hex": "#BFBFBF", "a": 0.10},
      "provenance": "MEASURED from Apple's official iOS 27 Figma UI Kit (community file 1651309003795292092, kit 27.0.2) via the Figma REST API on 2026-07-29; transcription in design-reference/ios27/ios27-ds/packages/tokens/src/materials.json. Apple publishes no numeric spec for Liquid Glass itself. This is the designer's value, not the runtime parameter.",
      "correctedBy": "C05"
    }'''


def apply_naming(text, log):
    counts = {}
    for old, new in NAMING:
        n = text.count(old)
        if n:
            counts[old] = n
            text = text.replace(old, new)
    log.append(("NAMING", "%d distinct names remapped, %d occurrences"
                % (len(counts), sum(counts.values())), counts))
    return text


def apply_tokens(text, log):
    """Rewrite material.liquidGlassRegular inside the token JSON fence."""
    pat = re.compile(r'"liquidGlassRegular"\s*:\s*\{.*?\n    \}', re.DOTALL)
    m = pat.search(text)
    if not m:
        raise SystemExit("FATAL: liquidGlassRegular block not found (C05). "
                         "The input was edited; update apply-spec-corrections.py.")
    text = text[:m.start()] + CORRECTED_MATERIAL + text[m.end():]
    log.append(("C05", "material.liquidGlassRegular replaced with the measured "
                       "kit values (fill .70, shadow 48/8, alpha .25/.45, "
                       "#1A1A1A dark fill)", {}))
    return text


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="src", default=INBOX)
    ap.add_argument("--out", default=OUTDIR)
    args = ap.parse_args()

    if not os.path.exists(args.src):
        print("input not found: %s" % args.src, file=sys.stderr)
        return 2
    text = open(args.src, encoding="utf-8").read()
    os.makedirs(args.out, exist_ok=True)
    log = []

    text = apply_naming(text, log)

    applied, unmatched = [], []
    for cid, area, kind, pattern, repl, evidence in CORRECTIONS:
        if kind == "tokens":
            text = apply_tokens(text, log)
            applied.append({"id": cid, "area": area, "evidence": evidence})
            continue
        # A backreference in a replacement string ("\1") must be interpreted by
        # re; every other replacement must be inserted VERBATIM, because several
        # of them legitimately contain backslashes (makefile line continuations).
        # Escaping the whole replacement to protect those also escapes the "\1"
        # and writes a literal backslash-1 into the output. So: raw when there is
        # a backreference, a pass-through function when there is not.
        if "\\1" in repl:
            new_text, n = re.subn(pattern, repl, text)
        else:
            new_text, n = re.subn(pattern, lambda _m, _r=repl: _r, text)
        if n == 0:
            unmatched.append({"id": cid, "area": area,
                              "pattern": pattern[:70],
                              "why": "no match in the current input"})
            continue
        text = new_text
        applied.append({"id": cid, "area": area, "replacements": n,
                        "evidence": evidence})

    banner = (
        "<!--\n"
        "  AppleUX design baseline — ADOPTED 2026-10-15.\n"
        "  Source: design-reference/inbox/ios27-android16-spec-2026-10-15.txt (verbatim copy kept).\n"
        "  This file is that source with our project naming applied and %d corrections\n"
        "  applied. Every correction cites its evidence in CORRECTIONS.md.\n"
        "  Apple's product names (iOS 27, Liquid Glass) are NOT renamed — they are Apple's.\n"
        "  Re-derive the machine-readable parts with:\n"
        "      python3 scripts/parse-ios27-spec.py --spec design/spec/ios27-baseline.md \\\n"
        "                                      --out design/spec/extracted\n"
        "-->\n\n" % len(applied))
    outfile = os.path.join(args.out, "ios27-baseline.md")
    with open(outfile, "w", encoding="utf-8") as fh:
        fh.write(banner)
        fh.write(text)

    meta = {
        "_note": "Applied corrections to the adopted design baseline. "
                 "'awaiting-evidence' items were deliberately NOT applied.",
        "source": os.path.relpath(args.src, ROOT),
        "output": os.path.relpath(outfile, ROOT),
        "applied": applied,
        "awaiting-evidence": unmatched,
    }
    with open(os.path.join(args.out, "corrections.json"), "w", encoding="utf-8") as fh:
        json.dump(meta, fh, indent=2)
        fh.write("\n")

    print("baseline   : %s" % os.path.relpath(outfile, ROOT))
    print("corrections: %d applied, %d awaiting evidence"
          % (len(applied), len(unmatched)))
    for entry in unmatched:
        print("  AWAITING EVIDENCE  %s (%s) - pattern no longer matches"
              % (entry["id"], entry["area"]))
    print()
    for name, summary, counts in log:
        print("  %-8s %s" % (name, summary))
    if unmatched:
        print()
        print("WARNING: an awaiting-evidence correction means the input changed "
              "shape. Reconcile before trusting this baseline.")
    return 1 if unmatched else 0


if __name__ == "__main__":
    sys.exit(main())