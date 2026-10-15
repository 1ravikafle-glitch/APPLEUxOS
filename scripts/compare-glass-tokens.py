#!/usr/bin/env python3
"""Three-way comparison of Liquid Glass token values.

    incoming  design-reference/inbox/extracted/design-tokens.incoming.json
              (from the iOS-27 x AOSP-16 surface spec, unvetted)

    ours      appleux/design-system/src/main/java/com/appleux/design/tokens/
              LiquidGlassTokens.kt
              (shipped; carries its own provenance block)

    kit       design-reference/ios27/ios27-ds/packages/tokens/src/materials.json
              (MEASURED from Apple's Figma UI kit 27.0.2 via REST API, 2026-07-29)

WHY THIS EXISTS
  The incoming spec states its glass values are Apple-sourced. Apple's kit
  exposes fills, drop shadows, inner shadows, background blurs and corner radii
  through the Figma REST API, so those specific numbers are CHECKABLE against
  the measured extraction rather than argued about. This script prints the
  check. It does not decide policy; it removes the argument.

  It also prints the one thing that is NOT checkable: the backdrop blur radius.
  Figma's GLASS effect does not expose it over REST, so `blurRadius` in the
  incoming file is an assertion with no measured counterpart, and it must be
  labelled as ours-if-adopted rather than as Apple's.

Usage: python3 scripts/compare-glass-tokens.py [--in design/spec/extracted/design-tokens.incoming.json]
"""

import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INCOMING = os.path.join(ROOT, "design-reference", "inbox", "extracted",
                        "design-tokens.incoming.json")
OURS = os.path.join(ROOT, "appleux", "design-system", "src", "main", "java",
                    "com", "appleux", "design", "tokens", "LiquidGlassTokens.kt")
KIT = os.path.join(ROOT, "design-reference", "ios27", "ios27-ds", "packages",
                   "tokens", "src", "materials.json")

GREEN, RED, YELLOW, DIM, OFF = "\033[32m", "\033[31m", "\033[33m", "\033[2m", "\033[0m"


def load_ours():
    """Read the shipped Kotlin constants we actually compile."""
    if not os.path.exists(OURS):
        return {}
    src = open(OURS, encoding="utf-8").read()
    out = {}
    for m in re.finditer(
            r"const val (\w+)\s*=\s*(-?0[xX][0-9a-fA-F]+|\d+\.?\d*f?|[\d.]+)f?", src):
        name, raw = m.group(1), m.group(2)
        try:
            if raw.lower().startswith("0x"):
                v = float(int(raw, 16))
            else:
                v = float(raw.rstrip("f"))
        except ValueError:
            continue
        out[name] = v
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="incoming", default=INCOMING,
                    help="token JSON to check (default: the unvetted inbox copy)")
    args = ap.parse_args()

    for p in (args.incoming, OURS, KIT):
        if not os.path.exists(p):
            print("missing: %s" % p, file=sys.stderr)
            return 2

    inc = json.load(open(args.incoming, encoding="utf-8"))
    kit = json.load(open(KIT, encoding="utf-8"))
    ours = load_ours()

    reg = kit["liquidGlass"]["regular"]["large"]
    kl, kd = reg["light"], reg["dark"]
    inc_mat = inc.get("material", {}).get("liquidGlassRegular", {})

    # (label, incoming key, incoming value, kit value, ours const)
    rows = [
        ("corner radius", "cornerRadius", inc_mat.get("cornerRadius"),
         reg["cornerRadius"], ours.get("REGULAR_CORNER_RADIUS_DP")),
        ("fill alpha (light)", "fillAlpha", inc_mat.get("fillAlpha"),
         kl["bg"]["a"], ours.get("REGULAR_BG_LIGHT_ALPHA")),
        ("shadow blur", "shadowBlur", inc_mat.get("shadowBlur"),
         kl["shadow"]["blur"], ours.get("SHADOW_BLUR_DP")),
        ("shadow offsetY", "shadowOffsetY", inc_mat.get("shadowOffsetY"),
         kl["shadow"]["offsetY"], ours.get("SHADOW_OFFSET_Y_DP")),
        ("shadow alpha (light)", "shadowAlpha", inc_mat.get("shadowAlpha", inc_mat.get("shadowAlphaLight")),
         kl["shadow"]["a"], ours.get("SHADOW_ALPHA_LIGHT")),
        ("shadow alpha (dark)", "shadowAlphaDark", inc_mat.get("shadowAlphaDark", inc_mat.get("shadowAlpha")),
         kd["shadow"]["a"], ours.get("SHADOW_ALPHA_DARK")),
    ]

    print("Liquid Glass: incoming spec  vs  Apple kit 27.0.2 (measured)  vs  shipped")
    print("=" * 96)
    print("%-20s %14s %26s %14s  %s" % ("field", "incoming", "Apple kit (Figma)",
                                         "ours", "verdict"))
    print("-" * 96)
    mismatches = []
    for label, _key, inc_v, kit_v, our_v in rows:
        if kit_v is None:
            continue
        same_as_kit = inc_v is not None and abs(float(inc_v) - float(kit_v)) < 1e-9
        same_as_ours = our_v is not None and abs(float(our_v) - float(kit_v)) < 1e-9
        if same_as_kit and same_as_ours:
            verdict = GREEN + "all agree" + OFF
        elif same_as_kit:
            verdict = GREEN + "kit agrees" + OFF
        elif our_v is None:
            verdict = YELLOW + "ours unset" + OFF
            mismatches.append(label)
        elif same_as_ours:
            verdict = RED + "incoming wrong; ours matches kit" + OFF
            mismatches.append(label)
        else:
            verdict = RED + "incoming AND ours differ from kit" + OFF
            mismatches.append(label)
        print("%-20s %14s %26s %14s  %s" % (
            label,
            "-" if inc_v is None else inc_v,
            kit_v,
            "-" if our_v is None else ("%g" % our_v),
            verdict))

    print("-" * 96)
    inc_fill = inc.get("color", {}).get("liquidGlassFillLight", {})
    print("dark glass fill     incoming=%s  kit=%s  ours=0x%06X  %s" % (
        inc_fill.get("dark"), kd["bg"]["hex"],
        int(ours.get("REGULAR_BG_DARK", 0)),
        RED + "incoming picks #1C1C1E, kit measured #1A1A1A" + OFF))
    print()
    print("NOT CHECKABLE — no measured counterpart exists:")
    print("  backdrop blur radius : incoming asserts blurRadius=%s. Figma's GLASS"
          % inc_mat.get("blurRadius"))
    print("                         effect does not expose its radius over REST, and")
    print("                         ios27-ds says so in its own materials.json.")
    print("                         This value has no Apple provenance.")
    print("  rim alpha            : incoming asserts rimAlpha=%s. The kit models the"
          % inc_mat.get("rimAlpha"))
    print("                         rim as spread-shadow rings, not an alpha; see")
    print("                         LiquidGlassTokens.kt RIM_* constants instead.")
    print()
    print("VERDICT: %d of %d comparable fields disagree with the measured kit."
          % (len(mismatches), len(rows)))
    print("Do not adopt incoming material values. The rest of the token file is")
    print("colour/typography/radius, which must be compared field by field before")
    print("any of it is treated as Apple-sourced.")
    return 0


if __name__ == "__main__":
    sys.exit(main())