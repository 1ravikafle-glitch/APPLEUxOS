// swift-tools-version: 6.2
//
// iOS 27 design system — SwiftPM package.
//
// This package exists so the Apple-side Swift assets in this repository have a
// buildable home on a machine that can actually compile them. Xcode is macOS
// only, and Xcode 27 is Apple-silicon only, so this package is built by CI on
// a GitHub-hosted Apple silicon runner (see .github/workflows/macos-xcode.yml)
// and, for interactive work, on a real Apple silicon Mac.
//
// Platform is iOS-only on purpose:
//   * iOS27SemanticTokens.swift uses Color(uiColor:), which is UIKit-only.
//   * Sources/GlassProbe/main.swift uses .glassEffect(_:in:), the Liquid Glass
//     modifier introduced in the iOS 26 SDK.
//
// Both are iOS 26+ APIs, so the package declares .iOS("26.0"). Building it for
// macOS would fail on Color(uiColor:) and would not exercise the iOS SDK that
// the design system actually targets.

import PackageDescription

let package = Package(
    name: "iOS27DesignSystem",
    platforms: [
        .iOS("26.0")
    ],
    products: [
        .library(
            name: "iOS27DesignSystem",
            targets: ["iOS27DesignSystem"]
        )
    ],
    targets: [
        // Design tokens: primitive layer (values) + semantic layer (aliases).
        .target(
            name: "iOS27DesignSystem",
            path: "Sources/iOS27DesignSystem"
        ),
        // Measurement probe for the Liquid Glass veil. Renders known colour
        // bands behind a glass panel so a single screenshot yields the veil
        // alpha and tint (O = a*T + (1-a)*B solved across two bands).
        .executableTarget(
            name: "GlassProbe",
            path: "Sources/GlassProbe"
        )
    ]
)
