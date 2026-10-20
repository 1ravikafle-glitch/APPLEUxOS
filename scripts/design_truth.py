#!/usr/bin/env python3
"""Multi-source design-truth harness (design authority lane, session C).

WHY THIS EXISTS
---------------
Until now the design authority validated surfaces against *screenshot-driven
truth* only: device captures and the operator's ground-truth photos in
``design-reference/iphone11-ios27/``. Pixel evidence is excellent for
**rendered appearance** and useless for **published spec**: a screenshot
cannot tell you whether Title 2 tracking is -0.26pt or +0.26pt, and it
cannot tell you Apple's published touch-target minimum, the exact Dynamic
Type leading for a style, or what a real shipped Control Center glyph
measures.

The master prompt (SS5, SS12) already points at the missing sources: use the
licensed material, prefer the extracted IPSW files, and "Resource can directly
be fetched from official apple source if any gap is needed to fill".

So the truth base is now four independent sources, each with its own
authority scope, and every token in the ROM is checked against the sources
that are *competent* for that kind of fact:

  web-hig       Apple Human Interface Guidelines, fetched as DocC markdown.
                Authority: published spec - type metrics, tracking tables,
                layout/spacing tables, component anatomy, behaviour rules.
  figma-kit     Apple's iOS 27 Figma UI Kit, extracted via the Figma REST API
                (kit 27.0.2). Authority: materials, colour, radii, tokens.
  ipsw-artwork  The iOS 27 IPSW asset harvest in ``extracted/`` (5k+ SVGs,
                Control Center module artwork, raster assets). Authority:
                what Apple actually shipped - real artwork geometry, real
                colours, real module inventory.
  screenshot    Ground-truth photos and device captures. Authority: rendered
                appearance at a given frame - glass, blur, shadow, proportion.

Precedence is *scoped*, not global. A published spec beats a kit for tracking;
the kit beats the spec for glass materials because Apple publishes no numeric
Liquid Glass spec; shipped artwork beats both for "how big is Apple's glyph";
and a screenshot beats all three for "how does the finished surface look".

THE ROM IS NOT A SOURCE
------------------------
``rom`` appears in every comparison as the **subject under test**, never as
evidence. A fact where only ``rom`` has a value is *unverified*, not *right*.

OUTPUT
------
  design-reference/ios27/web/         harvested Apple web truth + provenance
  design-truth/facts.json              the merged, per-fact value store
  design-truth/conflicts.json          machine-readable conflict ledger
  design-truth/CONFLICTS.md            human-readable ledger

Everything except ``web`` is offline and deterministic. ``web`` needs network
and is the only command that mutates the harvest directory.
"""
from __future__ import annotations

import argparse
import collections
import datetime
import hashlib
import json
import pathlib
import re
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
WEB_DIR = ROOT / "design-reference" / "ios27" / "web"
HIG_DIR = WEB_DIR / "hig"
GUIDE_DIR = WEB_DIR / "guide"
TRUTH_DIR = ROOT / "design-truth"
EXTRACTED = ROOT / "extracted"

USER_AGENT = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)

HIG_BASE = "https://developer.apple.com/design/human-interface-guidelines"

# HIG articles that bear on surfaces we own. The full inventory is DISCOVERED
# by crawling Apple's own collection tree (see cmd_web), so this list is only
# the "must have" set - the harvester is not limited to it.
HIG_SLUGS = [
    "layout",
    "typography",
    "color",
    "buttons",
    "toggles",
    "sliders",
    "segmented-controls",
    "steppers",
    "lists-and-tables",
    "text-fields",
    "toolbars",
    "tab-bars",
    "sidebars",
    "search-fields",
    "alerts",
    "action-sheets",
    "sheets",
    "popovers",
    "menus",
    "notifications",
    "status-bars",
    "dark-mode",
    "materials",
    "icons",
    "sf-symbols",
    "gestures",
    "loading",
    "onboarding",
    "playing-audio",
    "feedback",
    "accessibility",
    "motion",
    "app-icons",
    "writing",
    "settings",
    "multitasking",
    "going-full-screen",
]

HIG_TREE_ROOTS = [
    "human-interface-guidelines",
    "components",
    "inputs",
    "foundations",
    "patterns",
]

# The iPhone User Guide is Apple's own behavioural description of the system
# surfaces. It is the web source for *behaviour and inventory*, not for metrics.
GUIDE_SLUGS = [
    "use-and-customize-control-center-iph59095ec58",
    "access-features-from-the-lock-screen-iphcd5c65ccf",
    "change-settings-iph5f4d1b6f4",
    "organize-apps-iph0e1e4e3a3f",
]

# ---------------------------------------------------------------------------
# Source registry: what each source is competent to decide.
# ---------------------------------------------------------------------------
SOURCES = {
    "web-hig": {
        "label": "Apple HIG (DocC markdown, developer.apple.com)",
        "authority": "published-spec",
        "competent_for": [
            "typography",
            "layout",
            "spacing",
            "accessibility",
            "behaviour",
            "component-anatomy",
        ],
        "not_competent_for": ["material", "glass-blur", "rendered-appearance"],
    },
    "figma-kit": {
        "label": "Apple iOS 27 Figma UI Kit (extracted, kit 27.0.2)",
        "authority": "design-kit",
        "competent_for": ["material", "color", "radius", "token"],
        "not_competent_for": ["behaviour", "shipped-artwork"],
    },
    "ipsw-artwork": {
        "label": "iOS 27 IPSW asset harvest (extracted/)",
        "authority": "shipped-artifact",
        "competent_for": ["shipped-artwork", "shipped-inventory", "shipped-color"],
        "not_competent_for": ["typography", "material"],
    },
    "screenshot": {
        "label": "Ground-truth photos + device captures",
        "authority": "rendered-appearance",
        "competent_for": ["rendered-appearance", "proportion", "glass", "shadow"],
        "not_competent_for": ["tracking", "spec-metrics"],
    },
    "gt-photo": {
        "label": "iOS 27 reference photos, pixel-sampled (B)",
        "authority": "rendered-appearance",
        "competent_for": ["rendered-appearance", "composited-colour"],
        "not_competent_for": ["token-values", "typography", "layout"],
    },
    "rom-composite": {
        "label": "Our tokens, composited over the measured backdrop",
        "authority": "subject-under-test",
        "competent_for": [],
        "not_competent_for": ["everything - the ROM is the subject, never evidence"],
    },
    "repo-spec": {
        "label": "Repo design baseline (design/spec/ios27-baseline.md, parsed)",
        "authority": "subject-under-test",
        "competent_for": [],
        "not_competent_for": ["everything - the repo spec is checked, not a source"],
    },
    "rom": {
        "label": "AppleUXOS implementation (subject under test)",
        "authority": "implementation",
        "competent_for": [],
        "not_competent_for": ["everything - rom is never evidence"],
    },
}

# Per-fact-scope precedence. First source that has a value for a scope wins.
SCOPE_PRECEDENCE = {
    "typography": ["web-hig", "figma-kit"],
    # GT photos are the authority for a SHIPPED layout geometry: for a
    # spacing value, the shipped pixels outrank the kit's approximations.
    "spacing": ["web-hig", "figma-kit", "gt-photo"],
    "accessibility": ["web-hig"],
    "material": ["figma-kit"],
    "color": ["figma-kit", "web-hig"],
    "color-intent": ["figma-kit", "web-hig"],
    "radius": ["figma-kit", "web-hig"],
    "shipped-artwork": ["ipsw-artwork"],
    "shipped-inventory": ["ipsw-artwork"],
    "shipped-color": ["ipsw-artwork"],
    "rendered-appearance": ["gt-photo"],
    # A SHIPPED layout geometry: the photo of the shipped thing is the
    # authority. Tolerance 1 unit (below) is the measurement noise of a
    # 2x frame read to the pixel.
    "geometry": ["gt-photo"],
    "contrast": [],
}

# Sources that describe the implementation under test. They are never
# evidence - they are what the check is about.
# "device-read" is an observation of the ROM on a live device (B's read
# pipeline), as opposed to "rom-composite", which is a value composited by
# the harness rather than read off a screen.
SUBJECT_SOURCES = ("rom", "rom-kt", "rom-xml", "repo-spec", "rom-composite",
                   "device-read")

# ROM colour token -> (figma kit group, kit key). Only tokens with a direct
# counterpart in Apple's kit are mapped; anything else stays unverified
# rather than being force-fitted onto a near neighbour.
FIGMA_COLOR_MAP = {
    "label": ("labels", "primary"),
    "secondaryLabel": ("labels", "secondary"),
    "tertiaryLabel": ("labels", "tertiary"),
    "quaternaryLabel": ("labels", "quaternary"),
    "systemBackground": ("backgrounds", "primary"),
    "systemBackgroundSecondary": ("backgrounds", "secondary"),
    "systemBackgroundTertiary": ("backgrounds", "tertiary"),
    "systemGroupedBackground": ("backgroundsGrouped", "primary"),
    "secondarySystemGroupedBackground": ("backgroundsGrouped", "secondary"),
    "tertiarySystemGroupedBackground": ("backgroundsGrouped", "tertiary"),
    "systemFillPrimary": ("fills", "primary"),
    "systemFillSecondary": ("fills", "secondary"),
    "systemFillTertiary": ("fills", "tertiary"),
    "systemFillQuaternary": ("fills", "quaternary"),
    "separator": ("separators", "nonOpaque"),
    "separatorPrimary": ("separators", "opaque"),
    "opaqueSeparator": ("separators", "opaque"),
    "systemBrown": ("accents", "brown"),
    "systemGray2": ("grays", "gray2"),
    "systemGray3": ("grays", "gray3"),
    "systemGray4": ("grays", "gray4"),
    "systemGray5": ("grays", "gray5"),
    "systemGray6": ("grays", "gray6"),
    "systemBlue": ("accents", "blue"),
    "systemGreen": ("accents", "green"),
    "systemRed": ("accents", "red"),
    "systemOrange": ("accents", "orange"),
    "systemYellow": ("accents", "yellow"),
    "systemPurple": ("accents", "purple"),
    "systemPink": ("accents", "pink"),
    "systemTeal": ("accents", "teal"),
    "systemIndigo": ("accents", "indigo"),
    "systemMint": ("accents", "mint"),
    "systemCyan": ("accents", "cyan"),
    "systemGray": ("grays", "gray"),
    # Apple publishes systemGray..systemGray6 - there is no systemGray7. Our
    # systemGray7 holds exactly the kit's gray3 value (#C7C7CC / #48484A), so
    # the VALUE is backed but the NAME is not Apple's. Recorded, not hidden.
    "systemGray7": ("grays", "gray3"),
}


# Tolerance for "these two numbers agree". Tracking is published to 0.01pt, so
# anything larger is a real difference; colours compare exactly.
TOLERANCE = {"typography": 0.005, "spacing": 0.0, "material": 0.0, "color": 0,
              "geometry": 1.0}


def now_iso() -> str:
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write_json(path: pathlib.Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def read_json(path: pathlib.Path):
    return json.loads(path.read_text(encoding="utf-8"))


# ---------------------------------------------------------------------------
# 1. WEB TRUTH
# ---------------------------------------------------------------------------
def fetch(url: str, timeout: int = 30) -> tuple[int, bytes, str]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read(), resp.headers.get("Content-Type", "")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read(), ""
    except Exception as exc:  # network down, DNS, TLS - record, never crash
        return 0, f"ERROR {exc}".encode("utf-8"), ""


HIG_LINK_RE = re.compile(r"\]\(/design/Human-Interface-Guidelines/([a-z0-9\-]+)\)")


def discover_hig_slugs() -> list[str]:
    """Crawl Apple's own HIG collection tree for the complete article list.

    A hand-written slug list is a guess about Apple's information architecture.
    The collection pages are the authority, so read them.
    """
    found: set[str] = set()
    frontier = list(HIG_TREE_ROOTS)
    seen: set[str] = set()
    while frontier and len(seen) < 200:
        slug = frontier.pop(0)
        if slug in seen:
            continue
        seen.add(slug)
        url = f"{HIG_BASE}/{slug}.md"
        status, body, _ = fetch(url)
        if status != 200:
            continue
        text = body.decode("utf-8", errors="replace")
        for child in HIG_LINK_RE.findall(text):
            if child not in found:
                found.add(child)
                frontier.append(child)
    return sorted(found | set(HIG_SLUGS))


def cmd_web(args) -> int:
    HIG_DIR.mkdir(parents=True, exist_ok=True)
    GUIDE_DIR.mkdir(parents=True, exist_ok=True)
    manifest = {
        "generated": now_iso(),
        "hig": {},
        "guide": {},
    }

    slugs = HIG_SLUGS if args.core_only else discover_hig_slugs()
    if not args.core_only:
        print(f"discovered {len(slugs)} HIG articles from Apple's collection tree")

    for slug in slugs:
        url = f"{HIG_BASE}/{slug}.md"
        status, body, ctype = fetch(url)
        entry = {
            "url": url,
            "http_status": status,
            "bytes": len(body),
            "sha256": sha256_bytes(body),
        }
        if status == 200:
            text = body.decode("utf-8", errors="replace")
            # DocC markdown carries a JSON header with the canonical title.
            title = ""
            m = re.search(r'"title"\s*:\s*"([^"]+)"', text[:600])
            if m:
                title = m.group(1)
            entry["title"] = title
            entry["stored"] = f"hig/{slug}.md"
            (HIG_DIR / f"{slug}.md").write_text(text, encoding="utf-8")
        else:
            entry["title"] = ""
            entry["stored"] = None
            entry["error"] = body[:200].decode("utf-8", errors="replace")
        manifest["hig"][slug] = entry
        print(f"HIG  {slug:28s} {status}  {entry['bytes']:>7d} B")

    for slug in GUIDE_SLUGS:
        url = f"https://support.apple.com/en-us/guide/iphone/{slug}/27/ios/27"
        status, body, ctype = fetch(url)
        entry = {
            "url": url,
            "http_status": status,
            "bytes": len(body),
            "sha256": sha256_bytes(body),
            "stored": None,
        }
        if status == 200:
            text = body.decode("utf-8", errors="replace")
            (GUIDE_DIR / f"{slug}.html").write_text(text, encoding="utf-8")
            entry["stored"] = f"guide/{slug}.html"
        manifest["guide"][slug] = entry
        print(f"GUIDE {slug:28s} {status}  {entry['bytes']:>7d} B")

    write_json(WEB_DIR / "provenance.json", manifest)
    ok = sum(1 for e in manifest["hig"].values() if e["http_status"] == 200)
    print(f"\nHIG {ok}/{len(slugs)} reachable -> {WEB_DIR}")
    return 0


# ---------------------------------------------------------------------------
# 2. WEB FACT PARSING
# ---------------------------------------------------------------------------
def parse_markdown_tables(text: str) -> list[dict]:
    """Return every pipe table in a markdown document with its heading path."""
    lines = text.split("\n")
    tables: list[dict] = []
    stack: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        hm = re.match(r"^(#{1,6})\s+(.*)$", line)
        if hm:
            level = len(hm.group(1))
            stack = stack[: level - 1]
            stack.append(hm.group(2).strip())
            i += 1
            continue
        if line.strip().startswith("|") and i + 1 < len(lines) and re.match(r"^\s*\|[\s:\-|]+\|\s*$", lines[i + 1]):
            header = [c.strip() for c in line.strip().strip("|").split("|")]
            rows = []
            j = i + 2
            while j < len(lines) and lines[j].strip().startswith("|"):
                rows.append([c.strip() for c in lines[j].strip().strip("|").split("|")])
                j += 1
            tables.append(
                {
                    "heading_path": " > ".join(stack),
                    "headers": header,
                    "rows": rows,
                    "line": i + 1,
                }
            )
            i = j
            continue
        i += 1
    return tables


def find_table(tables: list[dict], heading_contains: str, header_contains: str) -> dict | None:
    for t in tables:
        if heading_contains.lower() in t["heading_path"].lower():
            if any(header_contains.lower() in h.lower() for h in t["headers"]):
                return t
    return None


def facts_from_web() -> dict:
    """Parse the harvested HIG markdown into structured design facts."""
    facts: dict[str, dict] = {}

    def put(fact: str, scope: str, value, unit: str, evidence: str):
        facts[fact] = {
            "scope": scope,
            "value": value,
            "unit": unit,
            "evidence": evidence,
            "source": "web-hig",
        }

    type_path = HIG_DIR / "typography.md"
    if not type_path.exists():
        return facts
    text = type_path.read_text(encoding="utf-8")
    tables = parse_markdown_tables(text)

    # --- iOS/iPadOS Dynamic Type sizes, "Large (default)" = our base ramp ---
    size_tbl = find_table(tables, "Large (default)", "Size (points)")
    if size_tbl:
        for row in size_tbl["rows"]:
            if len(row) < 4:
                continue
            style = row[0].strip()
            size = _num(row[2])
            lead = _num(row[3])
            if size is None:
                continue
            key = _style_key(style)
            put(f"typography.{key}.size", "typography", size, "pt",
                f"web/hig/typography.md:{size_tbl['line']} (iOS Large default)")
            if lead is not None:
                put(f"typography.{key}.lineHeight", "typography", lead, "pt",
                    f"web/hig/typography.md:{size_tbl['line']} (iOS Large default)")

    # --- iOS/iPadOS/visionOS tracking values, SF Pro ---
    track_tbl = find_table(tables, "SF Pro", "Tracking (points)")
    tracking_by_size: dict[float, float] = {}
    if track_tbl:
        for row in track_tbl["rows"]:
            if len(row) < 3:
                continue
            size = _num(row[0])
            pts = _num(row[2])
            if size is None or pts is None:
                continue
            tracking_by_size[size] = pts
            put(f"typography.trackingBySize.{size:g}", "typography", pts, "pt",
                f"web/hig/typography.md:{track_tbl['line']} (SF Pro)")

    # The tracking table is indexed by POINT SIZE, not by style name. Resolve
    # each style through the size table so the published spec lands on the
    # same fact key the style-level sources use.
    for key, style_size in _STYLE_POINT_SIZES.items():
        if style_size in tracking_by_size:
            put(f"typography.{key}.tracking", "typography", tracking_by_size[style_size], "pt",
                f"web/hig/typography.md:{track_tbl['line']} (SF Pro, {style_size:g}pt row)")

    return facts


# The base iOS type ramp (Large content size). Keyed by the token's own name
# so it lines up with TypographyTokens.kt.
_STYLE_POINT_SIZES = {
    "largeTitle": 34, "title1": 28, "title2": 22, "title3": 20,
    "headline": 17, "body": 17, "callout": 16, "subheadline": 15,
    "footnote": 13, "caption1": 12, "caption2": 11,
}


def _num(text: str):
    if text is None:
        return None
    t = text.strip().replace("−", "-").replace("–", "-")
    m = re.search(r"[-+]?\d+(?:\.\d+)?", t)
    return float(m.group(0)) if m else None


def _style_key(style: str) -> str:
    s = style.strip().lower().replace(" ", "")
    return {
        "largetitle": "largeTitle",
        "title1": "title1",
        "title2": "title2",
        "title3": "title3",
        "headline": "headline",
        "body": "body",
        "callout": "callout",
        "subhead": "subheadline",
        "subheadline": "subheadline",
        "footnote": "footnote",
        "caption1": "caption1",
        "caption2": "caption2",
    }.get(s, s)


# ---------------------------------------------------------------------------
# 3. IPSW SHIPPED-ARTIFACT FACTS
# ---------------------------------------------------------------------------
VIEWBOX_RE = re.compile(r'viewBox="([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)"')
PNG_DIMS_RE = re.compile(r"_(\d+)x(\d+)\.png$")


def facts_from_ipsw() -> dict:
    """Facts about what Apple actually shipped: artwork geometry and inventory."""
    facts: dict[str, dict] = {}
    if not EXTRACTED.exists():
        return facts

    viewboxes: list[float] = []
    bundle_count = 0
    for d in sorted(EXTRACTED.iterdir()):
        if not d.is_dir():
            continue
        bundle_count += 1
        for svg in d.glob("*.svg"):
            try:
                head = svg.read_bytes()[:400]
            except OSError:
                continue
            m = VIEWBOX_RE.search(head.decode("utf-8", errors="replace"))
            if m:
                w, h = float(m.group(3)), float(m.group(4))
                if 0 < w < 2000 and 0 < h < 2000:
                    viewboxes.append(max(w, h))
            elif svg.name.endswith(".svg"):
                pass

    if viewboxes:
        viewboxes.sort()
        facts["shipped-artwork.svgViewBoxMaxDimension.median"] = {
            "scope": "shipped-artwork",
            "value": round(viewboxes[len(viewboxes) // 2], 4),
            "unit": "pt",
            "evidence": f"extracted/: {len(viewboxes)} shipped SVG viewBoxes",
            "source": "ipsw-artwork",
        }
        facts["shipped-artwork.svgCount"] = {
            "scope": "shipped-artwork",
            "value": len(viewboxes),
            "unit": "files",
            "evidence": "extracted/: shipped vector artwork with a readable viewBox",
            "source": "ipsw-artwork",
        }
        facts["shipped-artwork.bundleCount"] = {
            "scope": "shipped-inventory",
            "value": bundle_count,
            "unit": "bundles",
            "evidence": "extracted/: asset bundles harvested from the iOS 27 IPSW",
            "source": "ipsw-artwork",
        }

    # Raster assets carry their pixel size in the file name - Apple's own
    # statement of the asset's intended point size.
    raster: dict[str, int] = {}
    for d in sorted(EXTRACTED.iterdir()):
        if not d.is_dir():
            continue
        for png in d.glob("*.png"):
            m = PNG_DIMS_RE.search(png.name)
            if m:
                key = f"{m.group(1)}x{m.group(2)}"
                raster[key] = raster.get(key, 0) + 1
    for size, count in sorted(raster.items(), key=lambda kv: -kv[1])[:40]:
        facts[f"shipped-artwork.raster.{size}"] = {
            "scope": "shipped-artwork",
            "value": count,
            "unit": "files",
            "evidence": f"extracted/: raster assets at {size}px",
            "source": "ipsw-artwork",
        }
    return facts


# ---------------------------------------------------------------------------
# 4. FIGMA FACTS
# ---------------------------------------------------------------------------
def facts_from_figma() -> dict:
    """Normalise the Figma-kit extractions into the same fact space."""
    facts: dict[str, dict] = {}

    def put(fact, scope, value, unit, evidence):
        facts[fact] = {"scope": scope, "value": value, "unit": unit,
                       "evidence": evidence, "source": "figma-kit"}

    typo = ROOT / "design-reference/ios27/ios27-ds/packages/tokens/src/typography.json"
    if typo.exists():
        data = read_json(typo)
        ref = data.get("_reference", {})
        ev = f"ios27-ds tokens/src/typography.json (kit {ref.get('kitVersion')}, measured {ref.get('measured')})"
        for name, spec in (data.get("styles") or {}).items():
            key = _style_key(name)
            if "fontSize" in spec:
                put(f"typography.{key}.size", "typography", spec["fontSize"], "pt", ev)
            if "lineHeight" in spec:
                put(f"typography.{key}.lineHeight", "typography", spec["lineHeight"], "pt", ev)
            if "letterSpacing" in spec:
                put(f"typography.{key}.tracking", "typography", spec["letterSpacing"], "pt", ev)

    # Colours are produced by facts_from_figma_colors, which knows the kit's
    # hex+alpha shape and the ROM-token name mapping.

    mats = ROOT / "design-reference/ios27/ios27-ds/packages/tokens/src/materials.json"
    if mats.exists():
        data = read_json(mats)
        ev = "ios27-ds tokens/src/materials.json (Apple iOS 27 Figma UI Kit 27.0.2)"
        for name, spec in _iter_material_entries(data):
            if isinstance(spec, dict):
                for key in ("cornerRadius", "shadowBlur", "shadowOffsetY"):
                    if isinstance(spec.get(key), (int, float)):
                        put(f"material.{name}.{key}", "material", spec[key],
                            "pt" if "corner" in key else "px", ev)

    return facts


def facts_from_kit_glass() -> list:
    """The iOS 27 kit's Liquid Glass fills, overlay and shadow, as colour
    facts under the SAME ids the design spec uses, so the ROM's glass values
    are checked against the one source that publishes them instead of
    sitting unverified.

    The kit's own disclaimer travels with the evidence: these numbers
    APPROXIMATE the native material for web rendering - Apple publishes no
    numeric values for the material itself. That is exactly why the kit IS
    the authority for the material scope (DESIGN_TRUTH.md), and it is the
    first authority for colour too, so the comparison is legitimate.
    """
    facts: list = []
    mats = ROOT / "design-reference/ios27/ios27-ds/packages/tokens/src/materials.json"
    if not mats.exists():
        return facts
    data = read_json(mats)
    lgk = data.get("liquidGlass", {}) if isinstance(data, dict) else {}
    regular = lgk.get("regular", {}) if isinstance(lgk, dict) else {}
    variant = regular.get("large") or regular.get("medium") or {}
    if not isinstance(variant, dict):
        return facts
    ev = ("ios27-ds tokens/src/materials.json -> liquidGlass.regular (Apple iOS 27 Figma UI Kit 27.0.2). "
          "The kit's own disclaimer: these numeric values APPROXIMATE the native material for web/CSS "
          "rendering; Apple publishes no numeric values for the Liquid Glass material - which is why the "
          "kit is the authority for this scope (DESIGN_TRUTH.md).")

    def argb(hexv, alpha):
        h = hexv.lstrip("#")
        r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
        a = int(round(alpha * 255))
        return "%02X%02X%02X%02X" % (a, r, g, b)

    for mode in ("light", "dark"):
        v = variant.get(mode)
        if not isinstance(v, dict):
            continue
        bg = v.get("bg")
        if isinstance(bg, dict) and bg.get("hex") and bg.get("a") is not None:
            facts.append((f"color.liquidGlassFillLight.{mode}", {
                "scope": "color", "value": argb(bg["hex"], bg["a"]), "unit": "argb",
                "evidence": f"{ev} Fill {mode}: {bg['hex']} @ {bg['a']}.",
                "source": "figma-kit",
            }))
        ov = v.get("overlay") or v.get("overlay2")
        if isinstance(ov, dict) and ov.get("hex") and ov.get("a") is not None:
            facts.append((f"color.liquidGlassOverlayLight.{mode}", {
                "scope": "color", "value": argb(ov["hex"], ov["a"]), "unit": "argb",
                "evidence": f"{ev} Overlay {mode}: {ov['hex']} @ {ov['a']}.",
                "source": "figma-kit",
            }))
        sh = v.get("shadow")
        if isinstance(sh, dict) and sh.get("a") is not None:
            # The shadow is carried in the same ARGB form as the spec's other
            # shadow value (black at the published alpha), so the comparison
            # is like for like rather than a float against an ARGB string.
            facts.append((f"color.liquidGlassShadow.{mode}", {
                "scope": "color", "value": argb(sh.get("hex") or "#000000", sh["a"]), "unit": "argb",
                "evidence": f"{ev} Shadow {mode}: {sh.get('hex')} @ {sh['a']} (blur {sh.get('blur')}, offsetY {sh.get('offsetY')}).",
                "source": "figma-kit",
            }))
    return facts


def _iter_colour_entries(data):
    if isinstance(data, dict):
        for key, value in data.items():
            if key.startswith("_"):
                continue
            if isinstance(value, dict) and ("light" in value or "dark" in value):
                yield key, value
            elif isinstance(value, dict):
                for sub, subval in value.items():
                    if isinstance(subval, dict) and ("light" in subval or "dark" in subval):
                        yield f"{key}.{sub}", subval


def _iter_material_entries(data):
    if isinstance(data, dict):
        for key, value in data.items():
            if key.startswith("_"):
                continue
            if isinstance(value, dict):
                yield key, value


# ---------------------------------------------------------------------------
# COLOUR: the byte-order trap
# ---------------------------------------------------------------------------
# Android reads a colour literal as #AARRGGBB. Apple publishes translucent
# colours the way CSS does, as #RRGGBBAA. A value copied from the HIG into
# Color.parseColor() or into colors.xml without rotating the bytes is
# therefore decoded as: alpha <- the R byte, B <- the alpha byte.
#
# The two readings are modelled separately so the ledger can tell them apart:
#   rom-kt / rom-xml  = what Android actually renders (the defect)
#   rom-intent        = what the author meant (author intent, usually right)
#
# A fact where intent matches the kit but the effective value does not is an
# ENCODING defect. A fact where intent itself disagrees with the kit is a
# VALUE defect. Those need different fixes and different owners.

ARGB_RE = re.compile(r"^#?([0-9A-Fa-f]{6})([0-9A-Fa-f]{2})?$")


def rotate_argb(value) -> str | None:
    """Swap the alpha byte of an 8-digit AARRGGBB value with its trailing
    byte, i.e. read the same literal as CSS #RRGGBBAA. Returns None when the
    value is not an 8-digit hex colour."""
    if not isinstance(value, str):
        return None
    v = value.strip().lstrip("#")
    if len(v) != 8:
        return None
    try:
        int(v, 16)
    except ValueError:
        return None
    return (v[6:8] + v[0:6]).upper()


def _level_delta(a: str, b: str):
    """Largest per-channel difference between two 6-digit hex colours."""
    va, vb = a.strip().lstrip("#").upper(), b.strip().lstrip("#").upper()
    if len(va) != 6 or len(vb) != 6:
        return None
    try:
        ca = [int(va[i:i + 2], 16) for i in (0, 2, 4)]
        cb = [int(vb[i:i + 2], 16) for i in (0, 2, 4)]
    except ValueError:
        return None
    return max(abs(x - y) for x, y in zip(ca, cb))


def compare_argb(a: str, b: str) -> tuple:
    """Compare two AARRGGBB colours with a 1/255 per-channel tolerance.

    Apple publishes alpha as a decimal fraction (0.3); 0.3 * 255 is 76.5, so
    a one-step difference in the alpha byte is a rounding artefact, not a
    colour defect."""
    va, vb = a.strip().lstrip("#").upper(), b.strip().lstrip("#").upper()
    if len(va) != 8 or len(vb) != 8:
        return (0.0, va != vb)
    try:
        ca = [int(va[i:i + 2], 16) for i in (0, 2, 4, 6)]
        cb = [int(vb[i:i + 2], 16) for i in (0, 2, 4, 6)]
    except ValueError:
        return (0.0, va != vb)
    delta = max(abs(x - y) for x, y in zip(ca, cb))
    return (float(delta), delta > 1)


def android_parse_color(hex_text: str) -> str:
    """Exactly what android.graphics.Color.parseColor does (AOSP, 9-char case
    takes the literal as #AARRGGBB with no rearrangement)."""
    h = hex_text.strip().lstrip("#")
    if len(h) == 6:
        return ("FF" + h).upper()
    if len(h) == 8:
        return h.upper()  # already AARRGGBB by AOSP's reading
    return h.upper()


def author_intent(hex_text: str) -> str:
    """What the author meant: 8-digit literals are written CSS-style
    #RRGGBBAA, so the bytes are rotated into AARRGGBB."""
    h = hex_text.strip().lstrip("#")
    if len(h) == 8:
        return (h[6:8] + h[0:6]).upper()
    return android_parse_color(h)


def hex_to_argb(value: str, alpha: float | None) -> str:
    h = value.strip().lstrip("#").upper()
    if len(h) == 6 and alpha is not None:
        a = int(round(alpha * 255))
        return f"{a:02X}{h}"
    if len(h) == 8:
        return h
    if len(h) == 6:
        return "FF" + h
    return h


def facts_from_hig_colors() -> dict:
    """Tint + gray RGB values published in the HIG colour article.

    Apple renders each swatch as an image whose alt text carries the exact
    RGB triple, e.g. "R-0,G-136,B-255" - so the published palette is
    machine-readable straight off Apple's own page.
    """
    facts: dict[str, dict] = {}
    path = HIG_DIR / "color.md"
    if not path.exists():
        return facts
    text = path.read_text(encoding="utf-8")
    tables = parse_markdown_tables(text)

    name_by_index = {
        "red": "systemRed", "orange": "systemOrange", "yellow": "systemYellow",
        "green": "systemGreen", "mint": "systemMint", "teal": "systemTeal",
        "cyan": "systemCyan", "blue": "systemBlue", "indigo": "systemIndigo",
        "purple": "systemPurple", "pink": "systemPink",
    }
    for table in tables:
        headers = [h.lower() for h in table["headers"]]
        if not any("default (light)" in h for h in headers):
            continue
        for row in table["rows"]:
            if len(row) < 3:
                continue
            key = row[0].strip().lower()
            rom_name = name_by_index.get(key)
            if rom_name is None:
                continue
            for idx, mode in ((1, "light"), (2, "dark")):
                if idx >= len(row):
                    continue
                m = re.search(r"R-(\d+),\s*G-(\d+),\s*B-(\d+)", row[idx])
                if not m:
                    continue
                r, g, b = (int(m.group(i)) for i in (1, 2, 3))
                facts[f"color.{rom_name}.{mode}"] = {
                    "scope": "color",
                    "value": f"{r:02X}{g:02X}{b:02X}FF",
                    "unit": "argb",
                    "evidence": f"web/hig/color.md:{table['line']} (tint table, alt-text RGB)",
                    "source": "web-hig",
                }
    return facts


def facts_from_figma_colors() -> dict:
    """Kit colours, hex + alpha recombined into AARRGGBB."""
    facts: dict[str, dict] = {}
    path = ROOT / "design-reference/ios27/ios27-ds/packages/tokens/src/colors.json"
    if not path.exists():
        return facts
    data = read_json(path)
    ev = "ios27-ds tokens/src/colors.json (Apple iOS 27 Figma UI Kit 27.0.2)"

    def pick(group, key, mode):
        grp = data.get(group)
        if not isinstance(grp, dict):
            return None
        val = grp.get(key)
        if not isinstance(val, dict):
            return None
        mode_val = val.get(mode)
        if mode_val is None:
            return None
        if isinstance(mode_val, dict):
            return hex_to_argb(mode_val.get("hex", ""), mode_val.get("a"))
        return hex_to_argb(str(mode_val), None)

    for rom_name, (group, key) in FIGMA_COLOR_MAP.items():
        for mode in ("light", "dark"):
            value = pick(group, key, mode)
            if value:
                facts[f"color.{rom_name}.{mode}"] = {
                    "scope": "color",
                    "value": value,
                    "unit": "argb",
                    "evidence": f"{ev} [{group}.{key}.{mode}]",
                    "source": "figma-kit",
                }
    return facts


COLOR_TOKEN_RE = re.compile(
    r"val\s+(?P<name>\w+)\s*:\s*Int\s+get\(\)\s*=\s*resolve\(\s*"
    r'Color\.parseColor\("#(?P<dark>[0-9A-Fa-f]{6,8})"\)\s*,\s*'
    r'Color\.parseColor\("#(?P<light>[0-9A-Fa-f]{6,8})"\)'
)
XML_COLOR_RE = re.compile(r'<color\s+name="(?P<name>[\w.]+)"\s*>(?P<hex>#[0-9A-Fa-f]{6,8})</color>')


def facts_from_rom_colors() -> list:
    """The ROM palette, decoded twice: as Android renders it and as the
    author wrote it. Returns (fact, meta) pairs - one fact can carry several
    values from different slots."""
    facts: list = []
    kt = TOKEN_DIR / "ColorTokens.kt"
    if kt.exists():
        for m in COLOR_TOKEN_RE.finditer(kt.read_text(encoding="utf-8")):
            name, dark, light = m.group("name"), m.group("dark"), m.group("light")
            for mode, raw in (("light", light), ("dark", dark)):
                facts.append((f"color.{name}.{mode}", {
                    "scope": "color",
                    "value": android_parse_color(raw),
                    "unit": "argb",
                    "evidence": f"ColorTokens.kt {name}.{mode} (#{raw} parsed by Color.parseColor = what Android renders)",
                    "source": "rom-kt",
                }))

    for mode, rel in (("light", "values/colors.xml"), ("dark", "values-night/colors.xml")):
        path = TOKEN_DIR / ".."/".."/".."/".."/"res"/rel
        path = (TOKEN_DIR.parent.parent / "res" / rel).resolve()
        if not path.exists():
            continue
        for m in XML_COLOR_RE.finditer(path.read_text(encoding="utf-8")):
            name, raw = m.group("name"), m.group("hex")
            facts.append((f"color.xml.{name}.{mode}", {
                "scope": "color",
                "value": android_parse_color(raw),
                "unit": "argb",
                "evidence": f"{rel} {name} ({raw} as Android reads it)",
                "source": "rom-xml",
            }))
    return facts


# ---------------------------------------------------------------------------
# REPO SPEC — the design spec this repo carries, checked against the web
# ---------------------------------------------------------------------------
# design/spec/ios27-baseline.md is transcribed by hand and then parsed into
# design/spec/extracted/. It claimed HIG provenance and drifted from Apple's
# published table. It is now a checked SUBJECT: if the baseline is edited
# again and drifts from the live HIG, the ledger says so.

def facts_from_repo_spec() -> list:
    facts: list = []
    path = ROOT / "design/spec/extracted/design-tokens.incoming.json"
    if not path.exists():
        return facts
    data = read_json(path)
    ev = "design/spec/extracted/design-tokens.incoming.json (parsed from design/spec/ios27-baseline.md)"

    for name, spec in (data.get("typography") or {}).items():
        key = _style_key(name)
        if not isinstance(spec, dict):
            continue
        for field, out in (("size", "size"), ("lineHeight", "lineHeight"), ("tracking", "tracking")):
            if isinstance(spec.get(field), (int, float)):
                facts.append((f"typography.{key}.{out}", {
                    "scope": "typography",
                    "value": float(spec[field]),
                    "unit": "pt",
                    "evidence": f"{ev} typography.{name}.{field}",
                    "source": "repo-spec",
                }))

    for name, spec in (data.get("color") or {}).items():
        if not isinstance(spec, dict):
            continue
        for mode in ("light", "dark"):
            value = spec.get(mode)
            if isinstance(value, str) and value.startswith("#"):
                facts.append((f"color.{name}.{mode}", {
                    "scope": "color",
                    "value": hex_to_argb(value, spec.get("darkAlpha", spec.get("alpha"))
                                          if mode == "dark" else spec.get("alpha")),
                    "unit": "argb",
                    "evidence": f"{ev} color.{name}.{mode}",
                    "source": "repo-spec",
                }))
    return facts


# ---------------------------------------------------------------------------
# RENDERED APPEARANCE — the ground-truth photo measurements
# ---------------------------------------------------------------------------
# A published token says what colour the system uses. A photograph says what
# iOS 27 actually PAINTS. For a translucent token those are different
# numbers: the token is composited over the backdrop before it reaches the
# screen. So the comparison has to composite too, otherwise the ledger would
# compare a published hex against a photographed composite and call the
# difference a defect.
#
# design-truth/gt-measurements.json holds B's pixel samples from the iOS 27
# reference photos, each with the ROM token that should produce it and the
# backdrop it was sampled on.

def composite_over(fg_argb: str, backdrop: str) -> str:
    """Composite an AARRGGBB colour over an opaque RGB backdrop."""
    fg = fg_argb.strip().lstrip("#").upper()
    bg = backdrop.strip().lstrip("#").upper()
    if len(fg) != 8 or len(bg) < 6:
        return fg
    fa, fr, fg_, fb = (int(fg[i:i + 2], 16) for i in (0, 2, 4, 6))
    br, bg_, bb = (int(bg[i:i + 2], 16) for i in (0, 2, 4))
    a = fa / 255.0
    out = [round(f * a + b * (1 - a)) for f, b in ((fr, br), (fg_, bg_), (fb, bb))]
    return "".join(f"{c:02X}" for c in out)


def facts_from_hig_typography_legibility() -> dict:
    """HIG -> Typography, "Ensuring legibility": default and minimum text
    size per platform."""
    facts: dict[str, dict] = {}
    path = HIG_DIR / "typography.md"
    if not path.exists():
        return facts
    for line in path.read_text(encoding="utf-8").split("\n"):
        m = re.match(r"\|(iOS, iPadOS|macOS|tvOS|visionOS|watchOS)\s*\|([\d.]+) pt\s*\|([\d.]+) pt\s*\|", line)
        if m:
            platform = m.group(1).split(",")[0].strip()
            facts[f"accessibility.{platform}.defaultTextSize"] = {
                "scope": "accessibility", "value": float(m.group(2)), "unit": "pt",
                "evidence": "web/hig/typography.md: default legible text size",
                "source": "web-hig",
            }
            facts[f"accessibility.{platform}.minimumTextSize"] = {
                "scope": "accessibility", "value": float(m.group(3)), "unit": "pt",
                "evidence": "web/hig/typography.md: minimum legible text size",
                "source": "web-hig",
            }
    return facts


def facts_from_hig_accessibility() -> dict:
    """The HIG's published control-size and spacing guidance.

    This is what backs a 44 dp touch target: HIG -> Accessibility publishes
    a DEFAULT control size per platform (44x44 pt on iOS/iPadOS) and a
    MINIMUM (28x28 pt), plus the padding guidance for bezel and bezel-less
    controls. Those are numbers, and the ledger should carry them rather than
    leaving them as an assertion in a comment.
    """
    facts: dict[str, dict] = {}
    path = HIG_DIR / "accessibility.md"
    if not path.exists():
        return facts
    text = path.read_text(encoding="utf-8")
    for line in text.split("\n"):
        m = re.match(r"\|(iOS, iPadOS|macOS|tvOS|visionOS|watchOS)\s*\|([\d.]+)x([\d.]+) pt\s*\|([\d.]+)x([\d.]+) pt\s*\|", line)
        if m:
            platform = m.group(1).split(",")[0].strip()
            facts[f"accessibility.{platform}.defaultControlSize"] = {
                "scope": "accessibility",
                "value": float(m.group(2)),
                "unit": "pt",
                "evidence": f"web/hig/accessibility.md: '{platform}' default control size",
                "source": "web-hig",
            }
            facts[f"accessibility.{platform}.minimumControlSize"] = {
                "scope": "accessibility",
                "value": float(m.group(4)),
                "unit": "pt",
                "evidence": f"web/hig/accessibility.md: '{platform}' minimum control size",
                "source": "web-hig",
            }
    for label, value in (("paddingAroundBezeled", 12.0), ("paddingAroundBezelLess", 24.0)):
        if f"about {int(value)} points" in text:
            facts[f"accessibility.{label}"] = {
                "scope": "accessibility",
                "value": value,
                "unit": "pt",
                "evidence": "web/hig/accessibility.md: 'about 12 points of padding around elements that include a bezel. For elements without a bezel, about 24 points of padding works well around the element's visible edges.'",
                "source": "web-hig",
            }
    return facts


def facts_from_type_legibility() -> list:
    """The type ramp against the HIG's published legible text sizes.

    HIG -> Typography, "Ensuring legibility": iOS/iPadOS default size
    17 pt, minimum size 11 pt. Our ramp's smallest style is caption2 at
    11 pt - exactly Apple's minimum, and the body style is exactly Apple's
    default.
    """
    out: list = []
    typo = facts_from_rom()
    # the fact key is typography.<style>.size -> the style is the middle part
    sizes = {k.split(".")[-2]: float(v["value"]) for k, v in typo.items()
             if k.endswith(".size") and v.get("value") is not None}
    if sizes:
        out.append(("accessibility.iOS.defaultTextSize", {
            "scope": "accessibility",
            "value": sizes.get("body"),
            "unit": "pt",
            "evidence": "TypographyTokens.body - the HIG's default legible text size for iOS",
            "source": "rom",
        }))
        out.append(("accessibility.iOS.minimumTextSize", {
            "scope": "accessibility",
            "value": min(sizes.values()),
            "unit": "pt",
            "evidence": ("the smallest style in the ramp ("
                         + ", ".join(sorted(k for k, v in sizes.items() if v == min(sizes.values())))
                         + f", {min(sizes.values())} pt) against the HIG's 11 pt minimum"),
            "source": "rom",
        }))
    return out


def _relative_luminance(rgb: str) -> float:
    def chan(c: int) -> float:
        c = c / 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (int(rgb[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * chan(r) + 0.7152 * chan(g) + 0.0722 * chan(b)


def contrast_ratio(fg: str, bg: str) -> float:
    """WCAG contrast ratio between two opaque sRGB hex colours."""
    l1, l2 = _relative_luminance(fg.upper()), _relative_luminance(bg.upper())
    hi, lo = max(l1, l2), min(l1, l2)
    return round((hi + 0.05) / (lo + 0.05), 3)


# The label/background pairs the ROM actually renders, with the type style each
# label is used at. The HIG publishes the minimum contrast per size and weight
# (accessibility.md: up to 17 pt -> 4.5:1; 18 pt+ -> 3:1; bold -> 3:1).
CONTRAST_PAIRS = [
    ("label.onCard", "label", "systemBackground", "body"),
    ("secondaryLabel.onCard", "secondaryLabel", "systemBackground", "body"),
    ("secondaryLabel.onPage", "secondaryLabel", "systemGroupedBackground", "body"),
    ("tertiaryLabel.onCard", "tertiaryLabel", "systemBackground", "footnote"),
    ("quaternaryLabel.onCard", "quaternaryLabel", "systemBackground", "footnote"),
    ("label.onPage", "label", "systemGroupedBackground", "largeTitle"),
    ("label.onAlert", "label", "systemBackgroundSecondary", "body"),
]


def facts_from_contrast() -> list:
    """Contrast of every label/background pair we render, against the HIG's
    published minimum for the size it is used at.

    NOTE: Apple's own system palette puts its secondary label at ~3.4:1 on a
    white card - below the HIG's 4.5:1 content guideline. That is Apple's
    choice, and our job is fidelity, not compliance with a guideline Apple
    does not apply to its own system labels. Recording the ratios prevents a
    later pass from "fixing" a faithful colour and moving us AWAY from Apple.
    """
    out: list = []
    colors = {}
    for fact, meta in facts_from_rom_colors():
        if fact.startswith("color."):
            colors[fact.split(".")[1] + "." + fact.split(".")[2]] = meta["value"]
    for key, fg_token, bg_token, style in CONTRAST_PAIRS:
        fg = colors.get(fg_token + ".light")
        bg = colors.get(bg_token + ".light")
        if not fg or not bg:
            continue
        if len(fg) == 8 and fg[:2] != "FF":
            fg = composite_over(fg, bg)
        elif len(fg) == 8:
            fg = fg[2:]
        if len(bg) == 8:
            bg = bg[2:]
        ratio = contrast_ratio(fg, bg)
        size = facts_from_rom().get(f"typography.{style}.size")
        size_pt = float(size["value"]) if size else 0.0
        required = 3.0 if size_pt >= 18 else 4.5
        out.append((f"contrast.{key}", {
            "scope": "contrast",
            "value": ratio,
            "unit": ":1",
            "evidence": (f"ColorTokens.{fg_token} over ColorTokens.{bg_token} at "
                          f"{size_pt:g} pt; HIG minimum for that size is {required}:1 - "
                          + ("meets it" if ratio >= required else
                             "BELOW it, because this is Apple's own palette value and "
                             "fidelity is the target")),
            "source": "rom",
        }))
        out.append((f"contrastRequirement.{key}", {
            "scope": "contrast",
            "value": required,
            "unit": ":1",
            "evidence": f"HIG -> Accessibility minimum contrast for {size_pt:g} pt text",
            "source": "web-hig",
        }))
    return out


def facts_from_chrome_control_size() -> list:
    """The back button's size, checked against the HIG's published default
    control size."""
    path = (ROOT / "android-16/frameworks/appleux/design-system/src/main/java"
            "/com/appleux/design/tokens/DetailChromeTokens.kt")
    if not path.exists():
        return []
    m = re.search(r"const val BACK_BUTTON_SIZE_DP: Float = ([\d.]+)f",
                  path.read_text(encoding="utf-8"))
    if not m:
        return []
    return [("accessibility.iOS.defaultControlSize", {
        "scope": "accessibility",
        "value": float(m.group(1)),
        "unit": "pt",
        "evidence": ("DetailChromeTokens.BACK_BUTTON_SIZE_DP and "
                      "SizingTokens.CONTROL_SIZE_XL (the button's MEDIUM step) - "
                      "our two 44 dp controls, checked against the HIG's "
                      "published iOS default control size. The ladder's other "
                      "steps (40 / 48 dp) are AppleUX-chosen."),
        "source": "rom",
    })]


# ---------------------------------------------------------------------------
# CONTRAST — the HIG publishes minimum contrast ratios, so every canonical
# label/background pair can be checked instead of eyeballed.
# ---------------------------------------------------------------------------
# HIG -> Accessibility, "Increase contrast": minimum contrast ratio
#   text up to 17 pt, any weight ....... 4.5:1
#   text 18 pt and larger .............. 3:1
#   any text, bold ..................... 3:1
# and: "check the minimum contrast in both light and dark appearances."


def _srgb_to_linear(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def relative_luminance(argb: str) -> float:
    h = argb.strip().lstrip("#").upper()
    if len(h) == 8:
        h = h[2:]
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return (0.2126 * _srgb_to_linear(r) + 0.7152 * _srgb_to_linear(g)
            + 0.0722 * _srgb_to_linear(b))


def contrast_ratio(fg: str, bg: str) -> float:
    l1, l2 = relative_luminance(fg), relative_luminance(bg)
    if l1 < l2:
        l1, l2 = l2, l1
    return (l1 + 0.05) / (l2 + 0.05)


# The canonical pairings the HIG's own colour table implies: a foreground
# colour against the surface it is used on. The label ramp is what the system
# draws on top of the background and the grouped background; the alert, the
# notification banner and the Control Center chrome are fixed surfaces.
CONTRAST_PAIRS = [
    ("label", "systemBackground", "body", 17.0, False),
    ("label", "systemGroupedBackground", "body", 17.0, False),
    ("secondaryLabel", "systemBackground", "body", 17.0, False),
    ("secondaryLabel", "systemGroupedBackground", "body", 17.0, False),
    ("tertiaryLabel", "systemBackground", "footnote", 13.0, False),
    ("quaternaryLabel", "systemBackground", "footnote", 13.0, False),
    ("label", "systemSurface", "body", 17.0, False),
]


def facts_from_contrast() -> list:
    """Every canonical pair's contrast ratio, against the HIG's published
    floor for that text size."""
    out: list = []
    colors = facts_from_figma_colors()
    rom = {f: m for f, m in facts_from_rom_colors()}
    for fg_name, bg_name, style, size, bold in CONTRAST_PAIRS:
        for mode in ("light", "dark"):
            fg_fact = f"color.{fg_name}.{mode}"
            bg_fact = f"color.{bg_name}.{mode}"
            fg = colors.get(fg_fact) or rom.get(fg_fact)
            bg = colors.get(bg_fact) or rom.get(bg_fact)
            if not fg or not bg:
                continue
            fgv, bgv = fg["value"], bg["value"]
            if len(fgv) == 8 and fgv[:2] != "FF":
                fgv = composite_over(fgv, bgv[2:] if len(bgv) == 8 else bgv)
            elif len(fgv) == 8:
                fgv = fgv[2:]
            if len(bgv) == 8:
                bgv = bgv[2:]
            ratio = contrast_ratio(fgv, bgv)
            floor = 3.0 if (size >= 18.0 or bold) else 4.5
            out.append((f"contrast.{fg_name}.on.{bg_name}.{mode}", {
                "scope": "contrast",
                "value": round(ratio, 2),
                "unit": ":1",
                "evidence": (f"ColorTokens.{fg_name} on {bg_name} ({mode}), "
                             f"{size:g} pt{' bold' if bold else ''} - HIG floor {floor}:1 "
                             f"({'meets' if ratio >= floor else 'BELOW'})"),
                "source": "rom",
            }))
    return out


def facts_from_gt_geometry() -> dict:
    """Geometry measured off the iOS 27 ground-truth frames.

    A photo is a competent authority for a SHIPPED LAYOUT GEOMETRY - the
    band inset of the dock, the pitch of a column - in a way it is not for
    a published spec value. Each measurement may carry several sources: the
    GT reading (authority), the ROM's own layout derivation, and a live
    device read of the ROM.
    """
    path = TRUTH_DIR / "gt-geometry.json"
    if not path.exists():
        return {}
    data = read_json(path)
    out: dict = {}
    for fact, m in (data.get("measurements") or {}).items():
        out[fact] = {
            "scope": m["scope"],
            "unit": m["unit"],
            "values": m["values"],
            "evidence": m.get("evidence") or {},
        }
    return out


def facts_from_gt_photos() -> dict:
    facts: dict[str, dict] = {}
    path = TRUTH_DIR / "gt-measurements.json"
    if not path.exists():
        return facts
    data = read_json(path)
    for key, m in (data.get("measurements") or {}).items():
        facts[f"rendered.{key}"] = {
            "scope": "rendered-appearance",
            "value": m["hex"].lstrip("#").upper(),
            "unit": "hex",
            "evidence": f"{data.get('measured_by')} | {m.get('source')}",
            "source": "gt-photo",
            "accepted_delta": int(m.get("accepted_delta", 0) or 0),
        }
    return facts


def facts_from_rom_rendered() -> list:
    """What our tokens PAINT, composited over the same backdrop the photo
    was sampled on - the apples-to-apples comparison with the GT photo."""
    out: list = []
    path = TRUTH_DIR / "gt-measurements.json"
    if not path.exists():
        return out
    data = read_json(path)
    rom_colors = {fact: meta for fact, meta in facts_from_rom_colors()}
    accepted = {
        k: int((m or {}).get("accepted_delta", 0) or 0)
        for k, m in (data.get("measurements") or {}).items()
    }
    for key, m in (data.get("measurements") or {}).items():
        token = m.get("rom_token")
        backdrop = m.get("backdrop") or data.get("backdrop")
        mode = m.get("mode", "light")
        fact_name = f"color.{token}.{mode}"
        entry = rom_colors.get(fact_name)
        if entry is None:
            continue
        value = entry["value"]
        if backdrop and len(value) == 8 and value[:2] != "FF":
            value = composite_over(value, backdrop)
        elif len(value) == 8:
            value = value[2:]
        out.append((f"rendered.{key}", {
            "scope": "rendered-appearance",
            "value": value,
            "unit": "hex",
            "evidence": f"ColorTokens.{token} ({mode}) composited over {backdrop or 'opaque'}",
            "source": "rom-composite",
            "accepted_delta": accepted.get(key, 0),
        }))
    return out


# ---------------------------------------------------------------------------
# DESIGN INVARIANTS — rules that must hold regardless of any source
# ---------------------------------------------------------------------------
# Master Prompt §12b.2: "Every measurable principle ... stays tokenized. No
# literals in components." A frozen hex literal outside the token layer is
# how the Settings surface kept rendering the pre-iOS-26 palette: 18 of them
# lived in AppleuxTheme.kt and no token change could reach the surface that
# drew them. This invariant makes that class of defect visible.

# A colour literal can be written two ways in Kotlin: 0xAARRGGBB.toInt(), or
# built from an alpha byte with a shift - (0xE6 shl 24) or 0x3C3C43. Both are
# frozen copies of a token value, so both are caught.
LITERAL_RE = re.compile(r"0x[0-9A-Fa-f]{8}\s*\.toInt\(\)|0x[0-9A-Fa-f]{2}\s+shl\s+24")


def _all_spec_colour_facts() -> list:
    """(fact, meta) pairs for every colour the design spec declares."""
    path = ROOT / "design/spec/extracted/design-tokens.incoming.json"
    if not path.exists():
        return []
    data = read_json(path)
    out = []
    for name, spec in (data.get("color") or {}).items():
        if not isinstance(spec, dict):
            continue
        for mode in ("light", "dark"):
            if isinstance(spec.get(mode), str):
                out.append((f"color.{name}.{mode}", spec))
    return out


def facts_from_chrome_tokens() -> list:
    """The detail-page chrome tokens, so the GT chrome samples have a ROM
    counterpart to be compared against."""
    out: list = []
    path = (ROOT / "android-16/frameworks/appleux/design-system/src/main/java"
            "/com/appleux/design/tokens/DetailChromeTokens.kt")
    if not path.exists():
        return out
    text = path.read_text(encoding="utf-8")
    consts = dict(re.findall(r"const val (\w+): Int = 0x([0-9A-Fa-f]{8})", text))
    for name, key in (("BACK_BUTTON_FILL_LIGHT", "chrome.discFill.light"),
                      ("BACK_BUTTON_FILL_DARK", "chrome.discFill.dark"),
                      ("BACK_BUTTON_RIM_LIGHT", "chrome.discRim.light"),
                      ("BACK_BUTTON_RIM_DARK", "chrome.discRim.dark")):
        if name in consts:
            rgb = consts[name].upper()[2:]  # the GT samples are 6-hex RGB
            out.append((f"rendered.{key}", {
                "scope": "rendered-appearance",
                "value": rgb,
                "unit": "hex",
                "evidence": f"DetailChromeTokens.{name}",
                "source": "rom",
            }))
    # The chevron is painted in ColorTokens.label (the primary label).
    ct = (ROOT / "android-16/frameworks/appleux/design-system/src/main/java"
          "/com/appleux/design/tokens/ColorTokens.kt")
    if ct.exists():
        m = re.search(r'val label: Int get\(\) = resolve\(Color\.parseColor\("#([0-9A-Fa-f]{6})"\), '
                      r'Color\.parseColor\("#([0-9A-Fa-f]{6})"\)\)', ct.read_text(encoding="utf-8"))
        if m:
            dark, light = m.group(1).upper(), m.group(2).upper()
            out.append(("rendered.chrome.chevron.light", {
                "scope": "rendered-appearance", "value": light, "unit": "hex",
                "evidence": "ColorTokens.label (light)", "source": "rom"}))
            out.append(("rendered.chrome.chevron.dark", {
                "scope": "rendered-appearance", "value": dark, "unit": "hex",
                "evidence": "ColorTokens.label (dark)", "source": "rom"}))
    return out


def facts_from_invariants() -> list:
    out: list = []
    root = ROOT / "android-16/frameworks/appleux"
    if not root.exists():
        return out

    # --- invariant 1: no colour literals outside the token layer -----------
    offenders = []
    for path in root.rglob("*.kt"):
        parts = path.parts
        if "tokens" in parts:
            continue  # the token layer is where literals belong
        if "build" in parts or "out" in parts:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for m in LITERAL_RE.finditer(text):
            offenders.append(f"{path.relative_to(ROOT)}: {m.group(0)}")
    out.append(("invariant.noColorLiteralsOutsideTokens", {
        "scope": "invariant",
        "value": len(set(offenders)),
        "unit": "literals",
        "evidence": ("none found" if not offenders
                     else "; ".join(sorted(set(offenders))[:20])),
        "source": "invariant",
    }))

    # --- invariant 2: every dimen matches the token it mirrors -----------
    # The XML layer cannot hold its own copy of a token value. Tracking is
    # an em FRACTION (tracking / point-size); a type size is the point
    # size; a spacing is the SpacingTokens step. Each dimen is checked
    # against the token it claims to mirror, so the XML layer cannot drift
    # from the token layer.
    typo = facts_from_rom()
    dims = {}
    dimen_file = root / "design-system/src/main/res/values/dimens.xml"
    if dimen_file.exists():
        # The family is one of a KNOWN set, so the name is split on the
        # family's own boundary. A greedy `(\w+)_(\w+)` split silently
        # turned "appleux_space_insets_lg" into the family "space_insets",
        # which matched no branch below - so every spacing dimen was
        # skipped and the invariant was vacuously true.
        for m in re.finditer(
            r'<dimen name="appleux_(tracking|text|radius|space)_(\w+)">(-?[\d.]+)(?:sp|dp)</dimen>',
            dimen_file.read_text(encoding="utf-8"),
        ):
            dims[(m.group(1), m.group(2).lower())] = float(m.group(3))
    bad = []
    for (family, name), value in sorted(dims.items()):
        if family == "tracking":
            key = {"body": "body", "footnote": "footnote", "subheadline": "subheadline",
                   "callout": "callout", "title1": "title1", "title2": "title2",
                   "title3": "title3", "largetitle": "largeTitle",
                   "caption2": "caption2"}.get(name)
            if key is None:
                continue  # e.g. widget_numeral, an AppleUX-chosen value
            fact = typo.get(f"typography.{key}.tracking")
            size_fact = typo.get(f"typography.{key}.size")
            if not fact or not size_fact:
                continue
            expected = round(float(fact["value"]) / float(size_fact["value"]), 4)
            if abs(expected - value) > 0.00005:
                bad.append(f"tracking.{name}: dimen {value} but token says {expected}")
        elif family == "text":
            fact = typo.get(f"typography.{name}.size")
            if not fact:
                continue
            if abs(float(fact["value"]) - value) > 0.001:
                bad.append(f"text.{name}: dimen {value} but token says {fact['value']}")
        elif family == "radius":
            # medium mirrors SizingTokens.CORNER_RADIUS_MEDIUM, glass mirrors
            # LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP; the rest are
            # AppleUX-chosen shapes with no published counterpart.
            expected = {"medium": 12.0, "glass": 34.0}.get(name)
            if expected is None:
                continue
            if abs(expected - value) > 0.001:
                bad.append(f"radius.{name}: dimen {value} but token says {expected}")
        elif family == "space":
            if name == "grid":
                expected = 8.0
            elif name == "dock_band_inset":
                # mirrors LauncherTokens.DOCK_BAND_INSET_H_DP (measured:
                # the GT glass's 1 px rim starts 11.5 pt from the screen edge)
                expected = 12.0
            elif name == "dock_bar_padding_h":
                # measured from the GT frame: the outer gap from the band's
                # rim to the first icon is 21.25 pt = 20.2 dp
                expected = 20.0
            elif name == "dock_bar_padding_v":
                # mirrors LauncherTokens.DOCK_BAR_PADDING_V_DP (measured:
                # the GT's dock glass is 105 pt tall = 99.6 dp on our panel)
                expected = 20.0
            elif name.startswith("insets_"):
                step = name.split("_")[-1]
                order = ["none", "xs", "sm", "md", "lg", "xl", "xxl", "xxxl",
                         "xxxl2", "xxxl3", "xxxl4", "xxxl5", "xxxl6", "xxxl7",
                         "xxxl8", "xxxl9", "xxxl10", "xxxl11", "xxxl12", "xxxl13",
                         "xxxl14", "xxxl15", "xxxl16", "xxxl17", "xxxl18",
                         "xxxl19", "xxxl20"]
                if step not in order:
                    continue
                # The ladder is NOT a uniform 2 dp step: none=0, xs=2,
                # sm=4, then md=8 and every step after it by 4 (the
                # 6 dp and 10 dp steps do not exist).
                idx = order.index(step)
                expected = float(idx * 2) if idx < 3 else float(8 + (idx - 3) * 4)
            else:
                continue
            if abs(expected - value) > 0.001:
                bad.append(f"space.{name}: dimen {value} but token says {expected}")
    out.append(("invariant.dimensMatchTokens", {
        "scope": "invariant",
        "value": len(bad),
        "unit": "mismatches",
        "evidence": ("all " + str(len(dims)) + " dimens match the tokens they mirror"
                     if not bad else "; ".join(bad)),
        "source": "invariant",
    }))

    # --- invariant 3: the spec and the implementation agree on the token set
    # The baseline (design/spec/ios27-baseline.md) is parsed into
    # design/spec/extracted/. If it declares a colour the ROM does not
    # implement, the design spec and the code have drifted apart, and
    # nothing else in the ledger would notice.
    spec_colors = {fact for fact, _meta in _all_spec_colour_facts()}
    rom_colors = {f for f, _ in facts_from_rom_colors()}
    # The Liquid Glass material lives in its own token object
    # (LiquidGlassTokens), not in ColorTokens.
    glass = ROOT / "android-16/frameworks/appleux/design-system/src/main/java/com/appleux/design/tokens/LiquidGlassTokens.kt"
    if glass.exists():
        gt = glass.read_text(encoding="utf-8")
        for name, const in (("liquidGlassFillLight", "REGULAR_BG_"),
                            ("liquidGlassOverlayLight", "REGULAR_OVERLAY_LIGHT"),
                            ("liquidGlassShadow", "SHADOW_ALPHA_")):
            if const in gt:
                rom_colors.add(f"color.{name}.light")
                rom_colors.add(f"color.{name}.dark")
    missing = sorted(c for c in spec_colors if c not in rom_colors)
    out.append(("invariant.specColoursImplemented", {
        "scope": "invariant",
        "value": len(missing),
        "unit": "tokens",
        "evidence": ("every colour the spec declares is implemented"
                     if not missing
                     else "declared in design/spec but absent from ColorTokens: "
                          + ", ".join(missing)),
        "source": "invariant",
    }))

    # --- invariant 4: the design spec must not cite the pre-iOS-26 palette
    # The spec is the design contract. When the palette moved to the iOS
    # 26/27 unified tints, 89 references inside the spec kept the old
    # values - a silent drift that no token-level check would see, because
    # the spec's surface descriptions are not token values.
    spec_file = ROOT / "design/spec/ios27-baseline.md"
    if spec_file.exists():
        spec_text = spec_file.read_text(encoding="utf-8")
        stale = {
            "#FF3B30": "#FF383C", "#FF453A": "#FF4245", "#FF9500": "#FF8D28",
            "#FF9F0A": "#FF9230", "#FFD60A": "#FFD600", "#007AFF": "#0088FF",
            "#0A84FF": "#0091FF", "#30B0C7": "#00C3D0", "#40C8E0": "#00D2E0",
            "#5AC8FA": "#00C3D0", "#64D2FF": "#00D2E0", "#5856D6": "#6155F5",
            "#5E5CE6": "#6D7CFF", "#AF52DE": "#CB30E0", "#BF5AF2": "#DB34F2",
            "#32ADE6": "#00C0E8", "#63E6E2": "#00DAC3",
        }
        found = sorted({f"{k} -> {v}" for k, v in stale.items() if k in spec_text})
        out.append(("invariant.noPreIOS26PaletteInSpec", {
            "scope": "invariant",
            "value": len(found),
            "unit": "tints",
            "evidence": ("the spec cites only the iOS 26/27 unified palette"
                         if not found else "; ".join(found)),
            "source": "invariant",
        }))

    # --- invariant 5: no raw type size that mirrors the type ramp --------
    # A layout that wrote android:textSize="17sp" froze a copy of the type
    # ramp, and a ramp change could not reach it - the same defect class as
    # the letterSpacing literals, one level up. The widget numerals
    # (34/44/52 sp) and the debug-page sizes (14/18/24 sp) are not ramp
    # styles and are allowed to stay literal.
    ramp_sizes = ("12sp", "13sp", "15sp", "17sp", "20sp", "28sp", "34sp")
    # Documented exceptions: the WIDGET NUMERAL family - a big time display -
    # is not a type-ramp style, even where its size collides with one (the
    # battery widget's 34 sp numeral is the largeTitle size, but it is a
    # clock, not a title). Kept explicit here so the exception is visible in
    # the tool and not only in a document.
    type_size_exceptions = {
        "android-16/frameworks/appleux/launcher/src/main/res/layout/widget_battery.xml": ("34sp",),
        "android-16/frameworks/appleux/launcher/src/main/res/layout/widget_calendar.xml": ("52sp",),
        "android-16/frameworks/appleux/launcher/src/main/res/layout/widget_clock.xml": ("44sp",),
    }
    raw_sizes = []
    for path in root.rglob("*.xml"):
        if "build" in path.parts or "out" in path.parts:
            continue
        if path.parent.name == "values" or path.name.endswith("dimens.xml"):
            continue  # the dimen DEFINITIONS are where a size belongs
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        rel = str(path.relative_to(ROOT))
        allowed = type_size_exceptions.get(rel, ())
        for m in re.finditer(r'android:textSize="([\d.]+sp)"', text):
            if m.group(1) in ramp_sizes and m.group(1) not in allowed:
                raw_sizes.append(f"{rel}: {m.group(1)}")
    out.append(("invariant.noRawRampTypeSizes", {
        "scope": "invariant",
        "value": len(raw_sizes),
        "unit": "layouts",
        "evidence": ("every ramp type size points at a dimen"
                     if not raw_sizes else "; ".join(sorted(set(raw_sizes))[:20])),
        "source": "invariant",
    }))

    # --- invariant 6: every recorded tokenisation still preserves its value
    # A tokenisation is only behaviour-preserving if the token's CURRENT value
    # equals the literal's ORIGINAL value. design-truth/applies.json records
    # each one; if a token is changed later, the "value unchanged, source
    # changed" claim stops being true and this says so.
    applies_file = TRUTH_DIR / "applies.json"
    if applies_file.exists():
        ct = (root / "design-system/src/main/java/com/appleux/design/tokens/ColorTokens.kt")
        if ct.exists():
            ctext = ct.read_text(encoding="utf-8")
            broken = []
            for entry in read_json(applies_file).get("entries", []):
                token = entry.get("token")
                expected = str(entry.get("value", "")).upper()
                m = re.search(rf"val {re.escape(token)}: Int(?: = Color\.parseColor\(\"#(\w+)\"\))?"
                              rf"(?: get\(\) = resolve\((.*?)\))?", ctext, re.S)
                if not m:
                    broken.append(f"{token}: token not found")
                    continue
                actual = m.group(1)
                if actual:
                    actual = actual.upper()
                    if actual != expected.upper():
                        broken.append(f"{token}: now {actual}, was {expected}")
                else:
                    pair = [p.upper() for p in re.findall(r"#(\w{6,8})", m.group(2) or "")]
                    if pair:
                        missing = [p for p in pair if p not in expected.upper()]
                        if missing:
                            broken.append(f"{token}: now {pair}, was {expected}")
            out.append(("invariant.tokenisationsPreserveTheirValues", {
                "scope": "invariant",
                "value": len(broken),
                "unit": "tokens",
                "evidence": ("every recorded tokenisation still produces the value it replaced"
                             if not broken else "; ".join(broken)),
                "source": "invariant",
            }))

    # --- invariant 7: no duplicate declaration in the token layer -------
    # A build failed with "conflicting declarations ... ColorTokens.kt:235
    # and :248" because two sessions added the same token to the same
    # object. Duplicates are scoped per object, not per file: two objects
    # may each declare a `shadowColor`.
    token_dir = root / "design-system/src/main/java/com/appleux/design/tokens"
    if token_dir.exists():
        dupes = []
        for path in sorted(token_dir.glob("*.kt")):
            text = path.read_text(encoding="utf-8", errors="replace")
            for m in re.finditer(r"^object\s+(\w+)\s*\{(.*?)^\}", text, re.S | re.M):
                obj, body = m.group(1), m.group(2)
                # Only DIRECT members of the object: exactly four spaces of
                # indentation. A constructor parameter or a local val deeper
                # in a nested class or function is not a duplicate member.
                names = re.findall(r"^    (?:const )?val (\w+)", body, re.M)
                for name, count in collections.Counter(names).items():
                    if count > 1:
                        dupes.append(f"{path.name}:{obj}.{name} x{count}")
            # file-level members (no object wrapper) count too
            top = re.findall(r"^(?:const )?val (\w+)", text, re.M)
            for name, count in collections.Counter(top).items():
                if count > 1:
                    dupes.append(f"{path.name}:{name} x{count}")
        out.append(("invariant.noDuplicateTokenDeclarations", {
            "scope": "invariant",
            "value": len(dupes),
            "unit": "declarations",
            "evidence": ("no object declares the same member twice" if not dupes
                         else "; ".join(dupes)),
            "source": "invariant",
        }))

    # --- invariant 8: every token the DOCS name must exist ---------------
    # The design specs are the design authority's own documents, and a
    # spec that names a token which does not exist is worse than no spec:
    # it reads as settled. This caught four wrong references in
    # settings-menu.md on 2026-10-20 (ColorTokens.surface, a non-existent
    # RadiusTokens.md, INSETS_LG quoted as 24dp instead of 12dp, and the
    # glass radius quoted as the card radius).
    token_files = {
        "ColorTokens": "ColorTokens.kt", "SpacingTokens": "SpacingTokens.kt",
        "TypographyTokens": "TypographyTokens.kt", "LiquidGlassTokens": "LiquidGlassTokens.kt",
        "DetailChromeTokens": "DetailChromeTokens.kt", "SizingTokens": "SizingTokens.kt",
        "MotionTokens": "MotionTokens.kt", "ElevationTokens": "ElevationTokens.kt",
        "NotchTokens": "NotchTokens.kt", "IconTokens": "IconTokens.kt",
        "LockScreenTokens": "LockScreenTokens.kt", "AppleuxBrandTokens": "AppleuxBrandTokens.kt",
        "AppIconTokens": "AppIconTokens.kt",
    }
    members = {}
    for obj, fname in token_files.items():
        f = root / "design-system/src/main/java/com/appleux/design/tokens" / fname
        if f.exists():
            members[obj] = set(re.findall(r"^\s*(?:const )?val (\w+)", f.read_text(encoding="utf-8"), re.M))
    docs_dir = ROOT / "docs/appleux"
    bad_refs = []
    if members and docs_dir.exists():
        # Only the SPEC documents are linted. The chat log and the historical
        # logs quote file names and code fragments (`ColorTokens.kt:235`), which
        # are not token references.
        spec_docs = list((docs_dir / "design-skeletons").glob("*.md")) + [
            docs_dir / "DESIGN_SYSTEM.md", docs_dir / "DESIGN_TRUTH.md",
        ]
        ref_re = re.compile(r"\b(" + "|".join(sorted(members)) + r")\.([A-Za-z_]\w*)")
        for doc in spec_docs:
            if not doc.exists():
                continue
            try:
                text = doc.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            for obj, member in ref_re.findall(text):
                if member == "kt":
                    continue  # a file name, not a member
                names = members[obj]
                if member in names:
                    continue
                # An abbreviation (`LiquidGlassTokens.REGULAR`) names a family.
                if any(n.startswith(member) for n in names):
                    continue
                bad_refs.append(f"{doc.relative_to(ROOT)}: {obj}.{member}")
    out.append(("invariant.docTokenReferencesResolve", {
        "scope": "invariant",
        "value": len(bad_refs),
        "unit": "references",
        "evidence": ("every token the docs name exists" if not bad_refs
                     else "; ".join(sorted(set(bad_refs))[:15])),
        "source": "invariant",
    }))

    # --- invariant 9: no raw corner radius in a drawable -------------------
    # A drawable that wrote android:radius="12dp" froze a copy of
    # SizingTokens.CORNER_RADIUS_MEDIUM. Every radius now names a dimen, and
    # each dimen is checked against the token it mirrors.
    raw_radii = []
    for drawable in root.rglob("res/drawable*/*.xml"):
        try:
            dtext = drawable.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for m in re.finditer(r'android:radius="(-?[\d.]+dp)"', dtext):
            raw_radii.append(f"{drawable.relative_to(ROOT)}: {m.group(1)}")
    out.append(("invariant.noRawRadiusLiterals", {
        "scope": "invariant",
        "value": len(raw_radii),
        "unit": "drawables",
        "evidence": ("every corner radius names a dimen" if not raw_radii
                     else "; ".join(sorted(set(raw_radii))[:15])),
        "source": "invariant",
    }))

    # --- invariant 10: every hex a SPEC quotes must be reachable ----------
    # The doc-lint checks that the specs name real tokens; this checks the
    # VALUES they quote. A spec may legitimately quote: a token value, a
    # composite of a translucent token over its backdrop, a measured GT
    # colour, or - explicitly - a superseded value shown for comparison.
    reachable = set()
    for ctext in (root / "design-system/src/main/java/com/appleux/design/tokens").glob("*.kt"):
        ctext_body = ctext.read_text(encoding="utf-8", errors="replace")
        # a token value can be written as "#RRGGBB[AA]" or as 0xRRGGBB[AA]
        for m in (list(re.finditer(r'"#(\w{6,8})"', ctext_body))
                  + list(re.finditer(r'0x(\w{6,8})\b', ctext_body))):
            v = m.group(1).upper()
            reachable.add(v)
            if len(v) == 8:
                reachable.add(v[2:])
    # composites of a translucent token over the two common backdrops
    for back in ("FFFFFF", "F2F2F7"):
        for v in list(reachable):
            if len(v) == 8 and v[:2] != "FF":
                reachable.add(composite_over(v, back))
    # measured ground-truth colours
    gt_file = TRUTH_DIR / "gt-measurements.json"
    if gt_file.exists():
        for m in read_json(gt_file).get("measurements", {}).values():
            reachable.add(m["hex"].lstrip("#").upper())
    # deliberately quoted values that are neither: superseded values shown for
    # comparison, and wallpaper probes. Each with its reason.
    for hexv, reason in (
        ("D1D1D5", "the superseded separator value, quoted in the checklist as the 'before' reading"),
        ("D1D3E7", "the superseded secondary-label composite, quoted in the checklist as the 'before' reading"),
        ("E1B26D", "a wallpaper probe (the dock band), measured on the device - not a token"),
        ("E1C19A", "a wallpaper probe (the wallpaper), measured on the device - not a token"),
    ):
        reachable.add(hexv)
    unreachable = []
    for doc in (ROOT / "docs/appleux/design-skeletons").glob("*.md"):
        try:
            dtext = doc.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for m in re.finditer(r"#([0-9A-Fa-f]{6})\b", dtext):
            if m.group(1).upper() not in reachable:
                unreachable.append(f"{doc.name}: #{m.group(1).upper()}")
    out.append(("invariant.specHexesAreReachable", {
        "scope": "invariant",
        "value": len(unreachable),
        "unit": "values",
        "evidence": ("every hex a spec quotes is a token value, a composite, a "
                     "measured colour, or an explicitly superseded value"
                     if not unreachable else "; ".join(sorted(set(unreachable))[:15])),
        "source": "invariant",
    }))

    # --- invariant 11: every @dimen/@color a layout names must exist -------
    # B hit this in a build: the settings layouts referenced spacing dimens
    # that only existed in the design system's SOURCE, while the built res
    # archive was stale. The source was right and the build was stale - but
    # a missing resource is exactly what breaks a build, so it is checked
    # here first.
    defined = set()
    for values in root.rglob("res/values*/*.xml"):
        try:
            vtext = values.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for m in re.finditer(r'<(dimen|color|bool|integer|string|array|integer-array|string-array|plurals|style)\s+name="([\w.]+)"', vtext):
            defined.add(m.group(2))
    for values in root.rglob("res/values*/*.xml"):
        pass
    # drawables and other file-based resources
    for res in root.rglob("res"):
        if not res.is_dir():
            continue
        for f in res.iterdir():
            if f.is_file():
                defined.add(f.name.split(".")[0])
    missing_refs = []
    ref_re = re.compile(r'"@(dimen|color)/([\w.]+)"')
    for layout in root.rglob("res/layout*/*.xml"):
        try:
            ltext = layout.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for kind, name in ref_re.findall(ltext):
            if name not in defined:
                missing_refs.append(f"{layout.relative_to(ROOT)}: @{kind}/{name}")
    out.append(("invariant.layoutResourceReferencesResolve", {
        "scope": "invariant",
        "value": len(missing_refs),
        "unit": "references",
        "evidence": ("every @dimen/@color a layout names exists in the source tree"
                     if not missing_refs else "; ".join(sorted(set(missing_refs))[:15])),
        "source": "invariant",
    }))

    # --- invariant: a launcher's token and the dimen that mirrors it agree ---
    # The dock's band inset, inner padding and corner exponent each exist
    # twice: as a Kotlin token (the design system's statement) and as a
    # resource (what the XML ships). The dimens check above only sees the
    # resource, so a token edited on its own would drift from it silently -
    # and the launcher is where the band is built. This compares the two.
    dock_token_pairs = [
        ("DOCK_BAND_INSET_H_DP", "appleux_space_dock_band_inset", 12.0),
        ("DOCK_BAND_MARGIN_B_DP", "appleux_space_dock_band_margin_b", 12.0),
        ("DOCK_BAR_PADDING_V_DP", "appleux_space_dock_bar_padding_v", 20.0),
        ("DOCK_BAR_PADDING_H_DP", "appleux_space_dock_bar_padding_h", 20.0),
    ]
    launcher_tokens = ROOT / "android-16/frameworks/appleux/launcher/src/main/java/com/appleux/launcher/LauncherTokens.kt"
    token_mismatch = []
    if launcher_tokens.exists():
        tok_text = launcher_tokens.read_text(encoding="utf-8", errors="replace")
        dimen_text = dimen_file.read_text(encoding="utf-8", errors="replace")
        for token_name, dimen_name, expect in dock_token_pairs:
            tm = re.search(r"const val " + token_name + r": Float = ([0-9.]+)f", tok_text)
            dm = re.search(r'<dimen name="' + dimen_name + r'">([0-9.]+)dp</dimen>', dimen_text)
            if tm is None:
                token_mismatch.append(f"{token_name} is not declared in LauncherTokens.kt")
                continue
            if dm is None:
                token_mismatch.append(f"@{dimen_name} is not declared")
                continue
            tv, dv = float(tm.group(1)), float(dm.group(1))
            if abs(tv - dv) > 0.01 or abs(tv - expect) > 0.01:
                token_mismatch.append(
                    f"{token_name}={tv}f vs @{dimen_name}={dv}dp (both should be {expect})")
    # --- invariant: no built artifact is older than the source it was built from ---
    # A read-back is only evidence if the thing on the device contains the
    # change being measured. This caught a real trap: the launcher APK was
    # six minutes older than DockView.kt, so a device read would have
    # reported the OLD 12 dp gap and been filed as a FAIL against a fix
    # that was in fact correct and not yet built.
    apk_modules = {
        "appleux-launcher": ("launcher", "design-system"),
        "appleux-settings": ("settings", "design-system"),
        "appleux-design-system": ("design-system",),
        "appleux-browser": ("apps-shared", "design-system"),
        "appleux-camera": ("apps-shared", "design-system"),
        "appleux-calendar": ("apps-shared", "design-system"),
        "appleux-contacts": ("apps-shared", "design-system"),
        "appleux-files": ("apps-shared", "design-system"),
        "appleux-messages": ("apps-shared", "design-system"),
        "appleux-video": ("apps-shared", "design-system"),
    }
    stale = []
    apks_dir = ROOT / "out/emulator/apks"
    if apks_dir.is_dir():
        fw = ROOT / "android-16/frameworks/appleux"
        for apk in sorted(apks_dir.glob("*.apk")):
            mods = apk_modules.get(apk.stem)
            if not mods:
                continue
            newest = 0.0
            newest_file = ""
            for mod in mods:
                base = fw / mod
                if not base.is_dir():
                    continue
                for src in base.rglob("*"):
                    if src.suffix in (".kt", ".xml") and src.is_file():
                        m = src.stat().st_mtime
                        if m > newest:
                            newest, newest_file = m, str(src.relative_to(ROOT))
            if newest and apk.stat().st_mtime < newest:
                stale.append(
                    f"{apk.name} is older than {newest_file} - a device read of it "
                    f"would not contain the current source")
    out.append(("invariant.builtArtifactsAreCurrent", {
        "scope": "invariant",
        "value": len(stale),
        "unit": "stale artifacts",
        "evidence": ("every built APK is at least as new as the sources it was built from"
                     if not stale else "; ".join(stale[:8])),
        "source": "invariant",
    }))

    out.append(("invariant.layoutTokenDimensAgree", {
        "scope": "invariant",
        "value": len(token_mismatch),
        "unit": "mismatches",
        "evidence": ("each launcher token and the dimen that mirrors it carry the same value"
                     if not token_mismatch else "; ".join(token_mismatch)),
        "source": "invariant",
    }))

    # --- invariant: the dock's gap is derived from the SLOT count ----------
    # The dock is a fixed FOUR-cell grid, and that is Apple's own
    # provenance (SB_DefaultIconState~iphone.plist `buttonBar`: phone,
    # safari, messages, music) - four slots, persisted by DockPreferences.
    # The ICON COUNT is user data: it drops to 3 the moment a slot is
    # empty, and it changed when an app was uninstalled. A gap derived
    # from the item count would therefore move the band whenever the user
    # edits the dock, which is the class of defect the token layer exists
    # to remove. The derivation must read DOCK_SLOTS.
    dock_view = ROOT / ("android-16/frameworks/appleux/launcher/src/main/java/"
                        "com/appleux/launcher/ui/DockView.kt")
    dock_rules = []
    if dock_view.exists():
        dv = dock_view.read_text(encoding="utf-8", errors="replace")
        if "DOCK_SLOTS" not in dv:
            dock_rules.append("DockView.kt does not derive the dock gap from DOCK_SLOTS")
        if re.search(r"adapter\s*\??\.\s*itemCount|adapter\.itemCount", dv):
            dock_rules.append("DockView.kt derives the dock gap from the ITEM count, "
                              "not the SLOT count")
    if launcher_tokens.exists():
        lt = launcher_tokens.read_text(encoding="utf-8", errors="replace")
        sm = re.search(r"const val DOCK_SLOTS: Int = (\d+)", lt)
        if sm and int(sm.group(1)) != 4:
            dock_rules.append(f"DOCK_SLOTS is {sm.group(1)}, expected 4 "
                              "(Apple's dock carries four slots)")
        cm = re.search(r"const val DOCK_CORNER_EXPONENT: Float = ([0-9.]+)f", lt)
        if cm and float(cm.group(1)) > 2.4:
            dock_rules.append(f"DOCK_CORNER_EXPONENT is {cm.group(1)}: the GT's dock "
                              "corner is ROUND (n about 2.0-2.4 over the full arc); "
                              "n = 4.4 is the app-icon shape")
    # --- invariant: the transparency slider's presets match the kit ------
    # iOS 27 replaced iOS 26's binary Reduce Transparency toggle with a
    # continuous, system-wide slider that scales every glass surface's
    # opacity and blur. The five preset classes and the derivation are
    # published by Apple's kit (materials.json > transparency); our token
    # layer carries them. A preset that drifts from the kit is a silent
    # appearance change, so it is checked here.
    kit_mats = ROOT / "design-reference/ios27/ios27-ds/packages/tokens/src/materials.json"
    glass_tok = ROOT / ("android-16/frameworks/appleux/design-system/src/main/java/"
                        "com/appleux/design/tokens/GlassTransparencyTokens.kt")
    glass_rules = []
    if kit_mats.exists() and glass_tok.exists():
        try:
            km = json.loads(kit_mats.read_text(encoding="utf-8"))
        except Exception:
            km = {}
        tr = km.get("transparency", {})
        presets = tr.get("presets", {})
        gt = glass_tok.read_text(encoding="utf-8", errors="replace")

        def _tok_float(name):
            m = re.search(r"const val %s\s*=\s*(-?[0-9.]+)f" % name, gt)
            return float(m.group(1)) if m else None

        pairs = [
            ("fullyTinted", "PRESET_FULLY_TINTED_VALUE", "PRESET_FULLY_TINTED_OPACITY_SCALE",
             "PRESET_FULLY_TINTED_BLUR_SCALE"),
            ("tinted", "PRESET_TINTED_VALUE", "PRESET_TINTED_OPACITY_SCALE", "PRESET_TINTED_BLUR_SCALE"),
            ("default", "PRESET_DEFAULT_VALUE", "PRESET_DEFAULT_OPACITY_SCALE", "PRESET_DEFAULT_BLUR_SCALE"),
            ("clear", "PRESET_CLEAR_VALUE", "PRESET_CLEAR_OPACITY_SCALE", "PRESET_CLEAR_BLUR_SCALE"),
            ("ultraClear", "PRESET_ULTRA_CLEAR_VALUE", "PRESET_ULTRA_CLEAR_OPACITY_SCALE",
             "PRESET_ULTRA_CLEAR_BLUR_SCALE"),
        ]
        for pname, vname, oname, bname in pairs:
            p = presets.get(pname)
            if not p:
                glass_rules.append("the kit publishes no transparency preset '%s'" % pname)
                continue
            for key, name in (("value", vname), ("opacityScale", oname), ("blurScale", bname)):
                ours = _tok_float(name)
                theirs = p.get(key)
                if ours is None:
                    glass_rules.append("%s is missing from GlassTransparencyTokens.kt" % name)
                elif theirs is not None and abs(ours - float(theirs)) > 1e-6:
                    glass_rules.append("%s is %s but the kit says %s" % (name, ours, theirs))
        derive = tr.get("_derive", {})
        for key, base_name, slope_name in (("opacityScale", "OPACITY_SCALE_BASE", "OPACITY_SCALE_SLOPE"),
                                           ("blurScale", "BLUR_SCALE_BASE", "BLUR_SCALE_SLOPE")):
            d = derive.get(key, {})
            for dkey, name in (("base", base_name), ("slope", slope_name)):
                ours = _tok_float(name)
                theirs = d.get(dkey)
                if ours is None:
                    glass_rules.append("%s is missing from GlassTransparencyTokens.kt" % name)
                elif theirs is not None and abs(ours - float(theirs)) > 1e-6:
                    glass_rules.append("%s is %s but the kit says %s" % (name, ours, theirs))
    # --- invariant: the increased-contrast values match the HIG -----------
    # The HIG publishes an "Increased contrast (light)" and "Increased
    # contrast (dark)" column for every palette colour and every gray step,
    # with the exact RGB in the swatch alt-text. Our token object carries
    # them verbatim; this compares every one of them against the harvested
    # page, so a transcription slip cannot survive.
    hig_color = ROOT / "design-reference/ios27/web/hig/color.md"
    ic_tok = ROOT / ("android-16/frameworks/appleux/design-system/src/main/java/"
                     "com/appleux/design/tokens/IncreaseContrastTokens.kt")
    ic_rules = []
    if hig_color.exists() and ic_tok.exists():
        htext = hig_color.read_text(encoding="utf-8", errors="replace")
        itok = ic_tok.read_text(encoding="utf-8", errors="replace")
        base_names = {
            "Red": "systemRed", "Orange": "systemOrange", "Yellow": "systemYellow",
            "Green": "systemGreen", "Mint": "systemMint", "Teal": "systemTeal",
            "Cyan": "systemCyan", "Blue": "systemBlue", "Indigo": "systemIndigo",
            "Purple": "systemPurple", "Pink": "systemPink", "Brown": "systemBrown",
            "Gray": "systemGray1", "Gray(2)": "systemGray2", "Gray(3)": "systemGray3",
            "Gray(4)": "systemGray4", "Gray(5)": "systemGray5", "Gray(6)": "systemGray6",
        }

        def _rgb_of(cell):
            m = re.search(r"R-(\d+),G-(\d+),B-(\d+)", cell)
            return tuple(int(g) for g in m.groups()) if m else None

        def _our_hex(name):
            m = re.search(r"%s[^=]*=\s*Color\.parseColor\(\"#([0-9A-Fa-f]{8})\"\)" % name,
                          itok)
            return m.group(1).upper() if m else None

        seen = 0
        for line in htext.split("\n"):
            if not line.startswith("|") or "![R-" not in line:
                continue
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) < 6:
                continue
            key = cells[0].replace(" ", "")
            base = base_names.get(key)
            if base is None:
                continue
            for cell, suffix in ((cells[4], "IncreasedLight"), (cells[5], "IncreasedDark")):
                rgb = _rgb_of(cell)
                if rgb is None:
                    continue
                seen += 1
                name = base + suffix
                ours = _our_hex(name)
                if ours is None:
                    ic_rules.append("%s is missing from IncreaseContrastTokens.kt" % name)
                    continue
                theirs = "FF%02X%02X%02X" % rgb
                if ours != theirs:
                    ic_rules.append("%s is #%s but the HIG says #%s" % (name, ours, theirs))
        if seen == 0:
            ic_rules.append("no increased-contrast column was found in the harvested HIG page")
    out.append(("invariant.increaseContrastValuesMatchHig", {
        "scope": "invariant",
        "value": len(ic_rules),
        "unit": "violations",
        "evidence": ("every increased-contrast palette and gray value matches the HIG's "
                     "published column"
                     if not ic_rules else "; ".join(ic_rules)),
        "source": "invariant",
    }))

    out.append(("invariant.glassTransparencyPresetsMatchKit", {
        "scope": "invariant",
        "value": len(glass_rules),
        "unit": "violations",
        "evidence": ("the five transparency presets and the slider's derivation match "
                     "the iOS 27 kit's materials.json"
                     if not glass_rules else "; ".join(glass_rules)),
        "source": "invariant",
    }))

    out.append(("invariant.dockGapIsSlotDerived", {
        "scope": "invariant",
        "value": len(dock_rules),
        "unit": "violations",
        "evidence": ("the dock gap is derived from DOCK_SLOTS (4), the slot grid is "
                     "fixed, and the dock corner stays round"
                     if not dock_rules else "; ".join(dock_rules)),
        "source": "invariant",
    }))

    # --- invariant 10: no raw letterSpacing literal in any layout/style ----
    raw = []
    for path in root.rglob("*.xml"):
        if "build" in path.parts or "out" in path.parts:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for m in re.finditer(r'android:letterSpacing="(-?[\d.]+)"', text):
            raw.append(f"{path.relative_to(ROOT)}: {m.group(1)}")
        for m in re.finditer(r'<item name="android:letterSpacing">(-?[\d.]+)</item>', text):
            raw.append(f"{path.relative_to(ROOT)}: {m.group(1)}")
    out.append(("invariant.noRawLetterSpacingLiterals", {
        "scope": "invariant",
        "value": len(raw),
        "unit": "literals",
        "evidence": ("none - every letterSpacing points at a tracking dimen"
                     if not raw else "; ".join(sorted(set(raw))[:20])),
        "source": "invariant",
    }))
    return out


# ---------------------------------------------------------------------------
# 5. ROM FACTS (the subject under test)
# ---------------------------------------------------------------------------
TOKEN_DIR = (
    ROOT / "android-16/frameworks/appleux/design-system/src/main/java/com/appleux/design/tokens"
)


def facts_from_rom() -> dict:
    facts: dict[str, dict] = {}

    def put(fact, scope, value, unit, evidence):
        facts[fact] = {"scope": scope, "value": value, "unit": unit,
                       "evidence": evidence, "source": "rom"}

    typo = TOKEN_DIR / "TypographyTokens.kt"
    if typo.exists():
        text = typo.read_text(encoding="utf-8")
        for m in re.finditer(
            r'val\s+(\w+)\s*:\s*FontSpec\s*=\s*FontSpec\((.*?)\n\s*\)',
            text,
            re.S,
        ):
            name, body = m.group(1), m.group(2)
            size = _num(_kw(body, "size"))
            lh = _num(_kw(body, "lineHeight"))
            tr = _num(_kw(body, "tracking"))
            ev = f"design-system/.../tokens/TypographyTokens.kt ({name})"
            key = _style_key(name)
            if size is not None:
                put(f"typography.{key}.size", "typography", size, "pt", ev)
            if lh is not None:
                put(f"typography.{key}.lineHeight", "typography", lh, "pt", ev)
            if tr is not None:
                put(f"typography.{key}.tracking", "typography", tr, "pt", ev)

    color = TOKEN_DIR / "ColorTokens.kt"
    if color.exists():
        # Colours are handled by facts_from_rom_colors, which decodes the
        # literal twice (as Android renders it, and as the author wrote it).
        pass

    return facts


def _kw(body: str, key: str):
    m = re.search(rf"{key}\s*=\s*(-?[\d.]+)f?", body)
    return m.group(1) if m else None


# ---------------------------------------------------------------------------
# 6. CROSS-CHECK
# ---------------------------------------------------------------------------
def merge_facts() -> dict:
    store: dict[str, dict] = {}
    for source, loader in (
        ("web-hig", facts_from_web),
        ("figma-kit", facts_from_figma),
        ("ipsw-artwork", facts_from_ipsw),
        ("rom", facts_from_rom),
    ):
        for fact, meta in loader().items():
            entry = store.setdefault(
                fact,
                {"scope": meta["scope"], "unit": meta["unit"], "values": {}, "evidence": {}},
            )
            entry["values"][source] = meta["value"]
            entry["evidence"][source] = meta["evidence"]

    # Colour facts come from their own loaders so the intent/effective split
    # and the XML palette are represented.
    for loader in (facts_from_hig_colors, facts_from_figma_colors, facts_from_kit_glass,
                   facts_from_rom_colors,
                   facts_from_repo_spec, facts_from_gt_photos, facts_from_gt_geometry,
                   facts_from_rom_rendered,
                   facts_from_chrome_tokens, facts_from_hig_accessibility,
                   facts_from_hig_typography_legibility, facts_from_type_legibility,
                   facts_from_chrome_control_size, facts_from_contrast,
                   facts_from_invariants):
        produced = loader()
        items = produced.items() if isinstance(produced, dict) else produced
        for fact, meta in items:
            entry = store.setdefault(
                fact,
                {"scope": meta["scope"], "unit": meta["unit"], "values": {}, "evidence": {}},
            )
            if "values" in meta:
                # A meta may carry several sources for one fact (a GT
                # measurement plus the ROM's own reading of the same fact).
                for src, val in meta["values"].items():
                    entry["values"][src] = val
                    ev = (meta.get("evidence") or {}).get(src)
                    if ev:
                        entry["evidence"][src] = ev
            else:
                entry["values"][meta["source"]] = meta["value"]
                entry["evidence"][meta["source"]] = meta["evidence"]
            if "accepted_delta" in meta:
                entry.setdefault("accepted_delta", {})[meta["source"]] = meta["accepted_delta"]
    return store


# Why a value has no competent source. A value without a source is
# unverified - but "unverified" should say WHY, so the ledger is a work list
# rather than a shrug.
UNVERIFIED_REASONS = {
    "liquidglassfill": (
        "Apple publishes NO numeric values for the Liquid Glass material - the "
        "iOS 27 kit says so in its own words ('Numeric values ... APPROXIMATE "
        "the native material for web/CSS rendering. Apple publishes no "
        "numeric values for the Liquid Glass material.'). Our values are "
        "AppleUX-chosen derivations from that approximation."
    ),
    "liquidglassrim": (
        "Apple publishes no numeric values for the Liquid Glass material; the "
        "rim is part of that material. AppleUX-chosen derivation."
    ),
    "liquidglassoverlay": (
        "Apple publishes no numeric values for the Liquid Glass material - the "
        "iOS 27 kit says so in its own words. The overlay is part of that "
        "material, so the measured value (#bfbfbf @ 0.10 light, #1a1a1a @ 0.9 "
        "dark) is an AppleUX-chosen derivation from the kit's approximation, "
        "not a published number."
    ),
    "liquidglassshadow": (
        "Apple publishes no numeric values for the Liquid Glass material; the "
        "shadow is part of that material. AppleUX-chosen derivation."
    ),
    "baselinebandwidth": (
        "The measurement is of the ROM's Oct-19 BASELINE build, not the current "
        "tree, so it cannot be compared against the GT as if it were ours. The "
        "current build's dock band has to be read on the device."
    ),
    "backbuttonfill": (
        "MEASURED from the iOS 27 GT frames (general-light-top-hero / "
        "bluetooth-dark): the back button is a soft neutral disc, #F3F3F7 in "
        "light and #1F1F1F in dark. It is a measurement, not a published "
        "value, so no HIG or kit counterpart exists to compare it against."
    ),
    "backbuttonrim": (
        "MEASURED from the iOS 27 GT frames: the disc's lifted edge, light "
        "(247,247,252) = #F7F7FC and dark (32,32,32) = #202020. It is a "
        "measurement, not a published value, so no HIG or kit counterpart "
        "exists to compare it against."
    ),
    "separatorsecondary": (
        "Apple publishes only `separator` and `opaqueSeparator`; the extra "
        "rungs are AppleUX-chosen."
    ),
    "separatortertiary": (
        "Apple publishes only `separator` and `opaqueSeparator`; the extra "
        "rungs are AppleUX-chosen."
    ),
    "systembackgroundquaternary": (
        "The iOS 27 kit publishes no quaternary background level ('No "
        "quaternary level exists' in the kit's own note)."
    ),
    "systembackgroundquinary": (
        "The iOS 27 kit publishes no quinary background level."
    ),
    "systembluepressed": (
        "No pressed tint is published in the HIG colour table or the kit."
    ),
    "emphasized": (
        "Inherited, not measured: HIG publishes ONE SF Pro tracking table and "
        "the kit exposes one letter-spacing per style with separate weight "
        "names, so both sources use the regular table for the emphasised "
        "weight."
    ),
    "systemsurface": (
        "Apple publishes no name for the flat fallback surface - iOS 27 draws "
        "these as glass - so the pair is AppleUX-chosen. It is composed from "
        "the kit's grouped background (light #f2f2f7) and secondary "
        "background (dark #1c1c1e), which are the two kit values behind it."
    ),
    "chromeforeground": (
        "Bar-relative foreground, not a page-relative one. Apple publishes no "
        "token for it: the light bar is frosted white so its glyphs are the "
        "dark surface colour, and the dark bar is black so its glyphs are the "
        "primary label. AppleUX-chosen."
    ),
    "white60": (
        "An alpha step of white (0.60). Apple publishes no numeric value for "
        "it; it is inlined in the App Library widget text, so it is named "
        "here. AppleUX-chosen, same family as white48 / white50 / white70."
    ),
    "black60": (
        "An alpha step of black (0.60) - the App Library tile's drop shadow. "
        "Apple publishes no numeric value for it; it is inlined, so it is "
        "named here. AppleUX-chosen, same family as black12."
    ),
    "systemsurface95": (
        "The flat surface at 95 % opacity - the navigation bar's fill. Apple "
        "publishes no numeric value for a flat fallback of a Liquid Glass "
        "bar; it is derived from the flat surface, so it is AppleUX-chosen."
    ),
    "white50": (
        "An alpha step of white (0.502). Apple publishes no numeric value "
        "for it; it is inlined in two components, so it is named here. "
        "AppleUX-chosen, same family as white48 / white24."
    ),
    "white70": (
        "An alpha step of white (0.702). Apple publishes no numeric value for "
        "it; it is inlined in the notification surface, so it is named here. "
        "AppleUX-chosen, same family as white48 / white24."
    ),
    "contrast": (
        "A COMPUTED RATIO, not a token value. The HIG publishes the FLOOR "
        "(4.5:1 for text up to 17 pt, 3:1 for 18 pt and larger or bold) and "
        "the ratio is stated in each row's evidence. Apple's own de-emphasised "
        "label steps sit below the general floor in light appearance - the "
        "system mitigates that with the Increase Contrast setting, which the "
        "HIG requires: 'If your app doesn't provide this minimum contrast by "
        "default, ensure it at least provides a higher contrast color scheme "
        "when the system setting Increase Contrast is turned on.'"
    ),
    "contrast": (
        "A computed ratio, not a token value: the HIG's floor for a given text "
        "size is stated in the evidence. Apple's own de-emphasised label steps "
        "sit below that floor by design - the HIG's prescribed mitigation is "
        "the system's Increase Contrast setting, which our ROM does not "
        "implement (filed). Recording the ratios so a faithful colour is never "
        "'fixed' away from Apple."
    ),
    "trackingsize": (
        "The published tracking table is indexed by point size; a per-size "
        "fact has no style-level counterpart to compare against."
    ),
}


def unverified_reason(fact: str) -> str:
    low = fact.lower()
    for key, reason in UNVERIFIED_REASONS.items():
        if key in low:
            return reason
    return ""


def adjudicate(store: dict) -> dict:
    """Compare every fact against the sources competent for its scope.

    A fact can carry several subject values (the Kotlin token, the XML token,
    the author's intent). Each subject is compared against the authority
    values and reported separately, so a defect in the *rendered* value and a
    defect in the *authored* value never get conflated.
    """
    conflicts = []
    agree = []
    unverified = []
    for fact, entry in sorted(store.items()):
        scope = entry["scope"]
        values = entry["values"]
        order = SCOPE_PRECEDENCE.get(scope, [])
        authority = [(s, values[s]) for s in order if s in values]
        subjects = [(s, values[s]) for s in SUBJECT_SOURCES if s in values]
        if not authority:
            if subjects:
                unverified.append(
                    {
                        "fact": fact,
                        "scope": scope,
                        "unit": entry["unit"],
                        "value": subjects[0][1],
                        "subjects": {s: v for s, v in subjects},
                        "evidence": entry["evidence"].get(subjects[0][0]),
                        "reason": unverified_reason(fact),
                    }
                )
            continue
        base_source, base_value = authority[0]
        tol = TOLERANCE.get(scope, 0.0)
        # When a fact carries an "intent" slot, a mismatch in the effective
        # value whose intent slot AGREES with the authority is an ENCODING
        # defect (the colour is right, the bytes are written in the wrong
        # order for the consumer). A mismatch in the intent slot itself is a
        # VALUE defect (the colour itself is wrong).
        for subject_source, subject_value in subjects:
            delta = None
            mismatch = False
            if scope == "rendered-appearance" and isinstance(base_value, str) and isinstance(subject_value, str):
                delta = _level_delta(base_value, subject_value)
                # The tolerance belongs to the MEASUREMENT, so it may be
                # recorded against either side of the pair.
                deltas = entry.get("accepted_delta", {})
                allowed = deltas.get(subject_source)
                if allowed is None:
                    allowed = deltas.get(base_source, 0)
                mismatch = delta is not None and delta > allowed
            elif scope == "color" and isinstance(base_value, str) and isinstance(subject_value, str):
                delta, mismatch = compare_argb(base_value, subject_value)
            elif isinstance(base_value, (int, float)) and isinstance(subject_value, (int, float)):
                delta = abs(float(base_value) - float(subject_value))
                mismatch = delta > tol
            else:
                mismatch = base_value != subject_value
            if mismatch:
                defect = "value"
                # An ENCODING defect is one where the bytes are in the wrong
                # order for the consumer: rotating the alpha byte back into
                # its place yields the authority value exactly.
                rotated = rotate_argb(subject_value)
                if rotated and rotated == base_value:
                    defect = "encoding"
                conflicts.append(
                    {
                        "fact": fact,
                        "scope": scope,
                        "unit": entry["unit"],
                        "verdict": "conflict",
                        "defect": defect,
                        "subject": subject_source,
                        "subject_value": subject_value,
                        "winner": base_source,
                        "winner_value": base_value,
                        "delta": None if delta is None else round(delta, 6),
                        "tolerance": tol,
                        "values": values,
                        "evidence": entry["evidence"],
                    }
                )
            else:
                agree.append(
                    {
                        "fact": fact,
                        "scope": scope,
                        "unit": entry["unit"],
                        "value": base_value,
                        "subject": subject_source,
                        "sources": [s for s, _ in authority],
                        "delta": None if delta is None else round(delta, 6),
                    }
                )
    return {"conflicts": conflicts, "agreements": agree, "unverified": unverified}


def cmd_cross(args) -> int:
    store = merge_facts()
    verdict = adjudicate(store)

    # A missing harvest must never degrade silently: the ledger would look
    # clean simply because one of the sources was absent.
    degraded = []
    present = {s for entry in store.values() for s in entry.get("values", {})}
    if "web-hig" not in present:
        degraded.append(
            "web-hig: no Apple web harvest found - run "
            "`python3 scripts/design_truth.py web` (network). Every typography "
            "and tint check below is missing its primary authority."
        )
    if "figma-kit" not in present:
        degraded.append("figma-kit: kit extraction not found (design-reference/ios27/ios27-ds)")
    if "ipsw-artwork" not in present:
        degraded.append("ipsw-artwork: extracted/ not found")

    invariants = [
        {"fact": f, "value": store[f]["values"].get("invariant"),
         "evidence": store[f]["evidence"].get("invariant")}
        for f in store if f.startswith("invariant.")
    ]
    payload = {
        "generated": now_iso(),
        "invariants": invariants,
        "sources": SOURCES,
        "scope_precedence": SCOPE_PRECEDENCE,
        "degraded_sources": degraded,
        "summary": {
            "facts": len(store),
            "agreements": len(verdict["agreements"]),
            "conflicts": len(verdict["conflicts"]),
            "unverified_rom_facts": len(verdict["unverified"]),
        },
        **verdict,
    }
    write_json(TRUTH_DIR / "facts.json", store)
    write_json(TRUTH_DIR / "conflicts.json", payload)
    render_report(payload)
    print(
        f"facts={len(store)} agree={len(verdict['agreements'])} "
        f"conflict={len(verdict['conflicts'])} unverified={len(verdict['unverified'])}"
    )
    for note in degraded:
        print(f"  DEGRADED - {note}")
    for inv in payload["invariants"]:
        mark = "OK " if inv["value"] == 0 else "FAIL"
        print(f"  INVARIANT {mark} {inv['fact']} = {inv['value']}")
        if inv["value"]:
            print(f"           {inv['evidence']}")
    from collections import Counter
    kinds = Counter(c.get("defect", "value") for c in verdict["conflicts"])
    print("  by defect class:", dict(kinds))
    for c in verdict["conflicts"]:
        print(
            f"  {c.get('defect', 'value').upper():8s} {c['fact']} [{c['subject']}] "
            f"authority={c['winner']}={c['winner_value']} subject={c['subject_value']}"
        )
    return 0


# ---------------------------------------------------------------------------
# 7. REPORT
# ---------------------------------------------------------------------------
def render_report(payload: dict) -> None:
    s = payload["summary"]
    lines = [
        "# Design-truth conflict ledger",
        "",
        f"Generated {payload['generated']} by `scripts/design_truth.py`.",
        "",
    ]
    for note in payload.get("degraded_sources", []):
        lines += [f"> **DEGRADED - {note}**", ""]
    lines += [
        f"**{s['facts']} facts · {s['agreements']} agreeing · {s['conflicts']} conflicting "
        f"· {s['unverified_rom_facts']} ROM values with no source.**",
        "",
        "## Sources",
        "",
        "| Source | Authority | Decides |",
        "|---|---|---|",
    ]
    for key, meta in SOURCES.items():
        lines.append(f"| `{key}` | {meta['authority']} | {', '.join(meta['competent_for']) or '—'} |")
    lines += [
        "",
        "## Scope precedence",
        "",
        "| Scope | Order |",
        "|---|---|",
    ]
    for scope, order in SCOPE_PRECEDENCE.items():
        lines.append(f"| {scope} | {' > '.join(order)} |")
    lines += ["", "## Conflicts", ""]
    if not payload["conflicts"]:
        lines.append("None. Every fact with two or more competent sources agrees.")
    else:
        lines += [
            "| Fact | Scope | Winner | Value | ROM | Δ | Evidence |",
            "|---|---|---|---|---|---|---|",
        ]
        for c in payload["conflicts"]:
            ev = c["evidence"].get(c["winner"], "")
            lines.append(
                f"| `{c['fact']}` | {c['scope']} | {c['winner']} | {c['winner_value']} | "
                f"{c['values'].get('rom', '—')} | {c.get('delta', '—')} | {ev} |"
            )
    lines += ["", "## ROM values with no competent source", ""]
    unv = payload["unverified"]
    if not unv:
        lines.append("None — every ROM value is backed by at least one source.")
    else:
        lines += ["| Fact | Scope | ROM value | Why there is no source |", "|---|---|---|---|---|"]
        for u in unv[:80]:
            lines.append(
                f"| `{u['fact']}` | {u['scope']} | {u['value']} | {u.get('reason') or u['evidence']} |"
            )
        if len(unv) > 80:
            lines.append(f"| … {len(unv) - 80} more | | | |")
    (TRUTH_DIR / "CONFLICTS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def cmd_report(args) -> int:
    conflicts = TRUTH_DIR / "conflicts.json"
    if not conflicts.exists():
        print("no conflicts.json - run `design_truth.py cross` first", file=sys.stderr)
        return 1
    render_report(read_json(conflicts))
    print(f"rendered {TRUTH_DIR / 'CONFLICTS.md'}")
    return 0


def cmd_facts(args) -> int:
    store = merge_facts()
    write_json(TRUTH_DIR / "facts.json", store)
    print(f"{len(store)} facts -> {TRUTH_DIR / 'facts.json'}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("web", help="harvest Apple web truth (network)").set_defaults(func=cmd_web)
    sub.add_parser("facts", help="merge all sources into facts.json").set_defaults(func=cmd_facts)
    sub.add_parser("cross", help="cross-check and write the conflict ledger").set_defaults(func=cmd_cross)
    sub.add_parser("report", help="re-render CONFLICTS.md from conflicts.json").set_defaults(func=cmd_report)
    args = ap.parse_args()
    if args.cmd == "web":
        args.core_only = "--core-only" in sys.argv
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
