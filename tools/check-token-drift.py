#!/usr/bin/env python3
"""
Check that the XML palette and ColorTokens.kt agree.

Why this exists
---------------
There are two ways an AppleUX colour is expressed:

  * Kotlin drawing code reads com.appleux.design.tokens.ColorTokens
  * themes / layouts / drawables read @color/* from
    design-system/src/main/res/values{,-night}/colors.xml

Both are transcribed from Apple HIG -> Color. They were not kept in step: the
XML layer used to live in four per-app files under four different names, and
systemui/values/colors.xml declared `label` as #FFFFFF in the DEFAULT (light)
qualifier. Nothing caught it, because nothing compared them.

This compares the two representations entry by entry and exits non-zero on any
mismatch, so a colour changed in one place and forgotten in the other fails
CI-style instead of shipping a white-on-white status bar.

Mappings
--------
ColorTokens property      XML resource
  systemBackground            system_background
  systemBackgroundSecondary   system_background_elevated
  systemBackgroundTertiary    system_background_secondary
  systemBackgroundQuaternary  system_background_tertiary
  systemBackgroundQuinary     system_background_quaternary
  systemGroupedBackground   system_grouped_background
  secondarySystemGroupedBackground  secondary_system_grouped_background
  tertiarySystemGroupedBackground   tertiary_system_grouped_background
  label                     label
  secondaryLabel            secondary_label
  tertiaryLabel             tertiary_label
  quaternaryLabel           quaternary_label
  separator                 separator
  separatorPrimary          separator_primary
  systemFillTertiary        fill_tertiary
  systemFillQuaternary      fill_quaternary
  systemBlue .. systemCyan  system_blue .. system_cyan
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TOKENS = ROOT / "appleux/design-system/src/main/java/com/appleux/design/tokens/ColorTokens.kt"
DAY = ROOT / "appleux/design-system/src/main/res/values/colors.xml"
NIGHT = ROOT / "appleux/design-system/src/main/res/values-night/colors.xml"

# Kotlin property -> XML colour name
MAP = {
    "systemBackground": "system_background",
    "systemBackgroundSecondary": "system_background_elevated",
    "systemBackgroundTertiary": "system_background_secondary",
    "systemBackgroundQuaternary": "system_background_tertiary",
    "systemBackgroundQuinary": "system_background_quaternary",
    "systemGroupedBackground": "system_grouped_background",
    "secondarySystemGroupedBackground": "secondary_system_grouped_background",
    "tertiarySystemGroupedBackground": "tertiary_system_grouped_background",
    "label": "label",
    "secondaryLabel": "secondary_label",
    "tertiaryLabel": "tertiary_label",
    "quaternaryLabel": "quaternary_label",
    "separator": "separator",
    "separatorPrimary": "separator_primary",
    "systemFillTertiary": "fill_tertiary",
    "systemFillQuaternary": "fill_quaternary",
    "systemBlue": "system_blue",
    "systemBluePressed": "system_blue_pressed",
    "systemGreen": "system_green",
    "systemRed": "system_red",
    "systemOrange": "system_orange",
    "systemYellow": "system_yellow",
    "systemPurple": "system_purple",
    "systemPink": "system_pink",
    "systemTeal": "system_teal",
    "systemIndigo": "system_indigo",
    "systemMint": "system_mint",
    "systemCyan": "system_cyan",
}

# `val x: Int get() = resolve(Color.parseColor("#DARK"), Color.parseColor("#LIGHT"))`
RESOLVE = re.compile(
    r"val\s+" + r"(?P<name>\w+)" + r"\s*:\s*Int\s+get\(\)\s*=\s*resolve\(\s*"
    r'Color\.parseColor\("#(?P<dark>[0-9A-Fa-f]{6,8})"\)\s*,\s*'
    r'Color\.parseColor\("#(?P<light>[0-9A-Fa-f]{6,8})"\)\s*\)',
)
XML_COLOR = re.compile(r'<color\s+name="(?P<name>[\w.]+)"\s*>(?P<hex>#[0-9A-Fa-f]{6,8})</color>')


def kotlin_palette(text):
    """property -> (light, dark), only for direct resolve(dark, light) pairs."""
    out = {}
    for m in RESOLVE.finditer(text):
        out[m.group("name")] = (m.group("light").upper(), m.group("dark").upper())
    return out


def xml_palette(path):
    out = {}
    for m in XML_COLOR.finditer(path.read_text()):
        out[m.group("name")] = m.group("hex").lstrip("#").upper()
    return out


def main():
    kt = kotlin_palette(TOKENS.read_text())
    day = xml_palette(DAY)
    night = xml_palette(NIGHT)

    drift, missing = [], []
    for prop, xml_name in sorted(MAP.items()):
        if prop not in kt:
            missing.append(f"ColorTokens.kt has no resolve() pair for '{prop}' "
                           f"(-> @color/{xml_name}) - nothing to compare")
            continue
        k_light, k_dark = kt[prop]
        for label, table, want in (("light", day, k_light), ("dark", night, k_dark)):
            got = table.get(xml_name)
            if got is None:
                missing.append(f"@color/{xml_name} ({label}) not declared")
            elif got.upper() != want.upper():
                drift.append(f"  {prop:32s} @color/{xml_name:38s} {label:5s} "
                             f"ColorTokens=#{want}  XML=#{got}")

    for m in missing:
        print("  MISSING " + m)
    if drift:
        print("  DRIFT:")
        for d in drift:
            print(d)

    print(f"  compared {len(MAP)} tokens across light and dark")
    if drift:
        print("  FAIL: the XML palette and ColorTokens.kt disagree.")
        return 1
    if missing:
        print("  WARN: some tokens could not be compared.")
        return 0
    print("  OK: XML palette matches ColorTokens.kt in both appearances.")
    return 0


if __name__ == "__main__":
    sys.exit(main())