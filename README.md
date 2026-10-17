# OsProject - iOS 27 Resources for Custom Android ROM

**Purpose:** Personal / educational use only  
**Source IPSW:** iPhone12,1_27.2_24B5099f_Restore.ipsw  
**Target Platform:** Custom Android ROM (AOSP-based)  
**Date:** 2026-10-17

---

## Scope

This project extracts and adapts visual resources from a real iOS 27 IPSW for use in a custom Android ROM. The user assumes all responsibility for any legal consequences.

## Source Resources (iOS 27)

| Resource | Location | Count |
|----------|----------|-------|
| App icons | `/home/elfakiris/ios-ui-analysis/app-icons/` | 75+ PNGs |
| Symbol fonts | `/home/elfakiris/ios-ui-analysis/fonts/Core/` | 6 fonts |
| Asset catalogs (.car) | `/home/elfakiris/ios-ui-analysis/all-asset-catalogs/` | 1,042 files |
| Framework assets | `/home/elfakiris/ios-ui-analysis/framework-assets/` | 100+ .car |
| CC images | `/home/elfakiris/ios-ui-analysis/cc-images/` | Module icons |
| New app images | `/home/elfakiris/ios-ui-analysis/new-app-images/` | 3 apps |

## Extraction Tools

| Tool | Use |
|------|-----|
| carfile-go | Pure Go, cross-platform .car extraction |
| carutil (Rust) | Cross-platform assetutil clone |
| car-parser-bom | Rust, supports PNG/JPEG/WEBP/PDF/SVG export |

## Output Structure

```
OsProject/
  extracted/          # Raw extracted PNGs from .car files
  fonts/              # Symbol fonts (converted)
  icons/              # App icons (Android-ready)
  symbols/            # SF Symbols as vector drawables
  overlays/           # RRO overlay APKs
  themes/             # Substratum theme assets
  docs/               # Documentation
```

## Target Android Integration Methods

1. **Icon Pack APK** — launcher-level, no root
2. **Substratum Theme** — system-wide, root
3. **RRO Overlay** — ROM-level, baked in
4. **Custom Launcher** — full iOS-like experience

---

*This is a personal/educational project. User accepts all legal responsibility.*
