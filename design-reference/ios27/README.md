# iOS 27 design reference (extracted on Linux, 2026-10-14)

Source: IOSinspiration/SF-Symbols-27.dmg -> SFSymbols.pkg (xar) -> Payload~ (cpio)
App: SF Symbols 27, Xcode 27.0 toolchain. Icon Composer 27.2 (build 142.1).

Contents:
- sf27-catalog.json : 9524 symbols, version-year map, year->release map,
  32 categories, per-category counts, full new-in-2026 (iOS 27.0) name list.
- sf27-new-in-2026.txt : 340 symbol names new in iOS 27.
- *.plist/*.json/*.strings : raw Apple metadata (provenance).

Also extracted (NOT stored here, see /tmp/opencode/ios-insp):
- SFSymbolsFallback.otf (41MB) : full symbol font, reference-only.
  Do NOT ship in APKs: SF Symbols license restricts use to Apple platforms.
  Use names/weights/geometry as spec; draw our own vectors.
- 26 badge.*.svg templates : valid SVG, usable as geometry reference.
- IPSW 27.2 (24B5099f, iPhone12,1): root FS is .aea encrypted (no public keys
  for beta); 3 unencrypted cryptex/ramdisk APFS volumes extracted but contain
  only Safari daemons + restore ramdisk - no SpringBoard UI assets.
- Xcode_26.6_Universal.xip : Apple-signed xar+pbzx, needs macOS to expand.
