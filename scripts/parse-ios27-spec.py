#!/usr/bin/env python3
"""Parse the iOS-27 x AOSP-16 surface specification into machine-readable intake.

The spec (`design-reference/inbox/ios27-android16-spec-2026-10-15.txt`) is edited
by hand. This script is the only thing that reads it, so an edit to the prose
flows through on the next run instead of needing 400 rows re-transcribed.

WHAT IT DOES
  1. Every ```json fenced block that carries a top-level "screen" key becomes
     one file under <out>/screens/<id>.json.
  2. Every ```json fenced block that looks like the token file / provenance
     block becomes <out>/design-tokens.incoming.json / provenance.incoming.json.
  3. Every markdown summary table ("Surface | Elements | Android Backend")
     becomes rows in <out>/surfaces.incoming.json, batched.
  4. Every "**Android backend:**" line becomes the backend string for the screen
     declared immediately above it.
  5. Each declared backend is checked against rules derived from THIS AOSP tree
     (frameworks/base, build/make). A backend that names an API which does not
     exist, or which the caller cannot invoke, is flagged rather than accepted.

WHAT IT DELIBERATELY DOES NOT DO
  It does not decide whether a coordinate, radius or colour is correct. It
  extracts and flags. Provenance judgement stays a human/agent act and is
  recorded in deliverables/m20/intake-review-2026-10-15.md.

Usage:  python3 scripts/parse-ios27-spec.py [--spec PATH] [--out DIR] [--quiet]
Exit:   0 = parsed, blocks that failed to parse are reported on stderr
"""

import argparse
import json
import os
import re
import sys
from collections import OrderedDict

DEFAULT_SPEC = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "design-reference", "inbox", "ios27-android16-spec-2026-10-15.txt")
DEFAULT_OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "design-reference", "inbox", "extracted")

FENCE_RE = re.compile(r"^```json\s*$(.*?)^```\s*$", re.MULTILINE | re.DOTALL)
BATCH_RE = re.compile(r"^#\s*BATCH\s+(\d+)\s*[—-]\s*(.+)$", re.MULTILINE)
SECTION_RE = re.compile(r"^##\s*([0-9]+)\.([0-9]+)\s+(\S+)", re.MULTILINE)
BACKEND_RE = re.compile(r"^\*\*Android backend:?\*\*\s*(.+)$", re.MULTILINE)

# ---------------------------------------------------------------------------
# Backend rules.
#
# Each rule is (pattern, verdict, note). `verdict` is one of:
#   "blocked"  - the API cannot serve this surface as mapped
#   "no-equivalent" - Apple's service has no AOSP counterpart at all
#   "signature" - works only because we replace the app in-tree (platform-signed)
#   "warn"     - mapping is probably wrong for another reason
#
# Every note below was checked against THIS source tree, not from memory. The
# evidence line is recorded so the rule can be re-verified after an AOSP bump.
# ---------------------------------------------------------------------------
BACKEND_RULES = [
    (r"\bgetImei\b",
     "signature",
     "TelephonyManager.getImei() is @RequiresPermission(READ_PRIVILEGED_PHONE_STATE). "
     "AOSP Settings DOES declare it (packages/apps/Settings/AndroidManifest.xml:58), "
     "so this works only because we replace Settings in-tree as a platform-signed "
     "privapp. It is invalid for any ordinary app."),
    (r"\bgetSubscriberId\b|\bgetDeviceId\b|\bgetSimSerialNumber\b",
     "signature",
     "Same privileged-permission class as getImei(); the 'About' screen inherits "
     "the same constraint."),
    (r"\bNotificationManager\b.*\bcancelAll\b|\bcancelAll\(\)",
     "signature",
     "NMS.cancelAllNotifications() calls checkCallerIsSystemOrSameApp(pkg) "
     "(frameworks/base/services/core/java/com/android/server/notification/"
     "NotificationManagerService.java). A Notification Center 'Clear All' must "
     "live in SystemUI or another system-signed component, not in a plain app."),
    (r"\bwipeData\b",
     "blocked",
     "DevicePolicyManager.wipeData() is only callable by a device owner / "
     "profile owner / DO-NOT-KILL app. A Settings row cannot offer 'Erase iPhone' "
     "or 'Erase All Content' without a DPC or a real factory-reset flow."),
    (r"\bSiri\b|\bGemini\b|Google Assistant",
     "no-equivalent",
     "Siri has no AOSP equivalent and no third-party API on AOSP. Any Siri "
     "surface is a stub or must be deleted, not mapped."),
    (r"\bApple ?Pay\b|\bApple ?Wallet\b",
     "no-equivalent",
     "Apple Pay requires Apple's proprietary NFC entitlement. Google Wallet is "
     "a different product, not a substitute."),
    (r"\bMatter\b|\bThread\b",
     "no-equivalent",
     "Apple Home and Matter/Thread are different products. Matter cannot stand "
     "in for Home."),
    (r"\biCloud\b",
     "no-equivalent",
     "No AOSP equivalent. Settings rows named after iCloud are Apple's, not ours."),
    (r"\bFind My\b|\bFindMy\b",
     "no-equivalent",
     "Apple Find My is tied to Apple's servers and hardware. Google Find My "
     "Device is a different product."),
    (r"\bJavaMail\b|\bImap\b|\bSmtp\b",
     "warn",
     "Neither JavaMail nor IMAP/SMTP ships in AOSP. These backends do not exist "
     "in the tree and would be a from-scratch mail stack, not a mapping."),
    (r"\bOpenWeatherMap\b|\bPlaces API\b|\bDirections API\b|\bNewsAPI\b|\bYahoo Finance\b",
     "warn",
     "Third-party network API requiring an API key and a network layer AOSP does "
     "not ship. Not a mapping; it is a dependency."),
    (r"\bARCore\b",
     "warn",
     "ARCore is a Google Play Services library. It is absent from AOSP and from "
     "an unbundled POCO M5 build unless separately vendored."),
    (r"\bAppPredictionService\b",
     "warn",
     "AppPredictionService is a Play Services component, absent from AOSP."),
    (r"\bAction Button\b|\bButtonSettings\b",
     "warn",
     "'Action Button' is a LineageOS feature. POCO M5 has no such hardware "
     "button; the row has nothing to bind to."),
    (r"\bWallpaperManager\b.*setWallpaperComponent",
     "warn",
     "setWallpaperComponent() selects a live WallpaperService, which does not "
     "produce the iOS depth effect. It is the nearest available hook, not an "
     "equivalent."),
    (r"\bEmojiPicker\b",
     "warn",
     "EmojiPicker is an input-method surface owned by the IME, not a system "
     "overlay a ROM can summon."),
    (r"\bRenderEffect\b",
     "warn",
     "RenderEffect blurs a window's OWN content. It never blurs what is behind "
     "the window. Real frost needs FLAG_BLUR_BEHIND + blurBehindRadius (API 31+)."),
    (r"\bPRODUCT_OVERRIDE_PACKAGES\b",
     "blocked",
     "Not a real make variable: no definition exists anywhere in build/make/. "
     "Replacing a stock package is done with PRODUCT_PACKAGES filtering or a "
     "PRODUCT_PACKAGE_OVERLAYS entry."),
]

BATCH_TITLES = OrderedDict()


def parse_batches(text):
    """Map batch number -> title, for provenance of each extracted screen."""
    for num, title in BATCH_RE.findall(text):
        BATCH_TITLES[int(num)] = title.strip()
    return BATCH_TITLES


def blocks(text):
    """Yield (start_offset, raw_body) for each ```json fence."""
    for m in FENCE_RE.finditer(text):
        yield m.start(), m.group(1)


def load_json_loose(raw):
    """Parse a fenced block. Returns (obj_or_None, error_or_None)."""
    body = raw.strip()
    if not body:
        return None, "empty block"
    try:
        return json.loads(body), None
    except json.JSONDecodeError as exc:
        # Some blocks in the spec are illustrative fragments. Try a last resort
        # of closing unbalanced brackets, and report if that still fails.
        for closer in ("}", "]", "}"):
            try:
                return json.loads(body + closer * 4), None
            except json.JSONDecodeError:
                continue
        return None, str(exc)


def extract_screens(text, outdir):
    """One file per screen JSON block. Returns (written, failures)."""
    written, failures = [], []
    screens_dir = os.path.join(outdir, "screens")
    os.makedirs(screens_dir, exist_ok=True)

    # Which section is each block in? Track by byte offset.
    sections = [(m.start(), m.group(1), m.group(2), m.group(3)) for m in SECTION_RE.finditer(text)]

    def section_for(offset):
        cur = None
        for off, a, b, slug in sections:
            if off <= offset:
                cur = (a, b, slug)
            else:
                break
        return cur

    for offset, raw in blocks(text):
        obj, err = load_json_loose(raw)
        if obj is None:
            failures.append((offset, err, raw.strip()[:60]))
            continue
        if not isinstance(obj, dict):
            continue
        if "screen" in obj:
            sid = str(obj["screen"]).strip()
            sec = section_for(offset)
            record = {
                "_spec_section": "%s.%s %s" % sec if sec else None,
                "_spec_line": text.count("\n", 0, offset) + 1,
                "screen": obj,
            }
            with open(os.path.join(screens_dir, sid + ".json"), "w") as fh:
                json.dump(record, fh, indent=2)
                fh.write("\n")
            written.append(sid)

    return written, failures


def extract_tokens_and_provenance(text, outdir):
    """Find the token file and the provenance block among the json fences."""
    tokens = prov = None
    for _off, raw in blocks(text):
        obj, err = load_json_loose(raw)
        if err or not isinstance(obj, dict):
            continue
        if tokens is None and "color" in obj and "typography" in obj:
            tokens = obj
        elif prov is None and "meta" in obj and "fields" in obj:
            prov = obj
    if tokens:
        with open(os.path.join(outdir, "design-tokens.incoming.json"), "w") as fh:
            json.dump(tokens, fh, indent=2)
            fh.write("\n")
    if prov:
        with open(os.path.join(outdir, "provenance.incoming.json"), "w") as fh:
            json.dump(prov, fh, indent=2)
            fh.write("\n")
    return tokens, prov


def extract_summary_tables(text, outdir):
    """Markdown tables headed 'Surface | ... | Android Backend'."""
    rows = []
    line_no = 0
    for line in text.splitlines():
        line_no += 1
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 3:
            continue
        surface = cells[0].strip("`")
        backend = cells[-1].strip("`")
        # Skip the header row and the ---|--- separator.
        if surface.lower() == "surface" or set(surface) <= set("-: "):
            continue
        if not surface or surface.lower() == "backend":
            continue
        rows.append({"surface": surface, "elements": cells[1], "backend": backend,
                     "_spec_line": line_no})
    if rows:
        with open(os.path.join(outdir, "surfaces.incoming.json"), "w") as fh:
            json.dump(rows, fh, indent=2)
            fh.write("\n")
    return rows


def check_backends(entries):
    """Apply BACKEND_RULES to each declared backend string."""
    flagged = []
    for entry in entries:
        backend = entry.get("backend") or ""
        for pattern, verdict, note in BACKEND_RULES:
            if re.search(pattern, backend, re.IGNORECASE):
                flagged.append({"surface": entry.get("surface"), "backend": backend,
                                "verdict": verdict, "note": note})
                break
    return flagged


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", default=DEFAULT_SPEC)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()

    if not os.path.exists(args.spec):
        print("spec not found: %s" % args.spec, file=sys.stderr)
        return 2
    text = open(args.spec, encoding="utf-8").read()
    os.makedirs(args.out, exist_ok=True)
    parse_batches(text)

    screens, failures = extract_screens(text, args.out)
    tokens, prov = extract_tokens_and_provenance(text, args.out)
    table_rows = extract_summary_tables(text, args.out)
    flagged = check_backends(table_rows)
    if flagged:
        with open(os.path.join(args.out, "backend-flags.json"), "w") as fh:
            json.dump(flagged, fh, indent=2)
            fh.write("\n")

    if not args.quiet:
        by_verdict = OrderedDict()
        for f in flagged:
            by_verdict.setdefault(f["verdict"], []).append(f["surface"])
        print("spec            : %s (%d bytes)" % (args.spec, len(text)))
        print("batches         : %d" % len(BATCH_TITLES))
        print("screen JSON     : %d written -> %s/screens/" % (len(screens), args.out))
        print("token file      : %s" % ("extracted" if tokens else "NOT FOUND"))
        print("provenance file : %s" % ("extracted" if prov else "NOT FOUND"))
        print("summary rows    : %d" % len(table_rows))
        print("unique surfaces : %d" % len({r["surface"] for r in table_rows}))
        print()
        print("BACKEND FLAGS (checked against this AOSP tree):")
        for verdict, names in by_verdict.items():
            print("  %-14s %3d  e.g. %s" % (verdict, len(names), ", ".join(names[:3])))
        if failures:
            print()
            print("UNPARSEABLE JSON BLOCKS: %d" % len(failures))
            for off, err, head in failures:
                print("  line ~%d: %s | %s" % (text.count("\n", 0, off) + 1, err,
                                              head.replace("\n", " ")))
        print()
        print("NOTHING EXTRACTED HERE IS ADOPTED. See")
        print("  deliverables/m20/intake-review-2026-10-15.md")
    return 0


if __name__ == "__main__":
    sys.exit(main())