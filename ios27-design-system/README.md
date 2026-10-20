# iOS27DesignSystem

SwiftPM package for the Apple-side iOS 27 design tokens and the Liquid Glass
measurement probe.

| Target | Kind | Platform | Contents |
|---|---|---|---|
| `iOS27DesignSystem` | library | iOS 26+ | `iOS27DesignTokens.swift` (primitive layer), `iOS27SemanticTokens.swift` (semantic layer) |
| `GlassProbe` | executable | iOS 26+ | Liquid Glass veil measurement probe |

## Why a package

The tokens are `import SwiftUI` and the probe uses `.glassEffect(_:in:)`.
Neither can be compiled by the Swift toolchain on Linux — SwiftUI does not
exist there — and Xcode 27 is Apple-silicon only, so the build has to happen
on a Mac. This package gives those files a single buildable unit that CI can
compile on a GitHub-hosted Apple silicon runner.

## Building

On a Mac with Xcode 26 or later:

```sh
# Library only, for the iOS Simulator
xcodebuild -scheme iOS27DesignSystem \
  -destination 'generic/platform=iOS Simulator' \
  build

# GlassProbe
xcrun --sdk iphonesimulator swiftc \
  -target arm64-apple-ios26.0-simulator \
  -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -o /tmp/GlassProbe \
  Sources/GlassProbe/main.swift
```

Both are exercised automatically by `.github/workflows/macos-xcode.yml`.

## Hosting note

The authoritative write-up of where these builds run and why — Apple silicon
vs Intel vs virtualisation — is `docs/appleux/XCODE_HOSTING.md`.

## Provenance

`iOS27DesignTokens.swift` and `iOS27SemanticTokens.swift` transcribe Apple's
official June 2026 iOS 27 Figma Community Kit
(`1651309003795292092`) into a three-layer token architecture:
Primitive → Semantic → Component. See `docs/appleux/DESIGN_TRUTH.md` for the
authority of each source.
