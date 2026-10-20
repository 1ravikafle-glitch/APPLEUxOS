# Xcode hosting — where Apple-platform builds run, and why

**Status:** ACTIVE (decision taken 2026-10-20).
**Scope:** every Apple-platform build in this repository — the Swift design
system, the Liquid Glass probe, and rPlayHub.

---

## 1. The constraint that decides everything

> "Xcode 27 will only install and run on Apple silicon Macs."
> — Xcode 27 release notes, *Intel Deprecation* section.

This is not a preference and it has no build-setting override. It means the
**Xcode 27 toolchain and the iOS 27 SDK cannot run on any Intel machine**,
including:

- the project's own build host (Dell Latitude 5420, Intel i5-1145G7);
- a macOS virtual machine on that host (QEMU/KVM with an OpenCore guest);
- a bare-metal "Hackintosh" built on that host;
- any Intel Mac, new or refurbished.

The Intel ceiling, from Apple's own compatibility table:

| Xcode | Runs on | macOS required | iOS SDK | Host arch |
|---|---|---|---|---|
| 27 / 27.1 / 27.2 | **Apple silicon only** | Tahoe 26.6+ | iOS 27.x | arm64 |
| 26.6 | Intel or Apple silicon | Tahoe 26.2+ | iOS 26.5 | x64 or arm64 |
| 26.3 | Intel or Apple silicon | Sequoia 15.6+ | iOS 26.2 | x64 or arm64 |
| 16.4 | Intel or Apple silicon | Sequoia 15.3+ | iOS 18.5 | x64 or arm64 |

A macOS VM on an Intel host is therefore capped at **Xcode 26.3 / iOS 26.2**,
and that is the *good* case. In practice the VM is worse than that:

- **No Metal.** QEMU exposes no Metal-capable GPU, so the iOS Simulator cannot
  boot a device. `simctl list` works; rendering does not. rPlayHub's entire
  purpose is driving the Simulator through `simctl`
  (`design-reference/rPlayHub/app/rPlayHub/SimulatorList.swift`), so in a VM it
  has nothing to attach to.
- **No GPU acceleration at all**, so the macOS desktop and every Xcode index
  pass are software-rendered.
- **RAM.** The build host has 16 GB total, of which ~11 GB is already in use by
  the AOSP/Cuttlefish build. An 8 GB guest means stopping that work. The 49 GB
  swap prevents an OOM but does not prevent thrashing.
- **Licence.** Apple's macOS Software Licence Agreement §2.B permits macOS only
  "on each Apple-branded computer you own or control". A macOS guest on this
  Dell is outside that grant, and the Apple SDKs Agreement is similarly scoped.

## 2. What this repository actually needs from Xcode

| Asset | Platform | Xcode/SDK floor | Why |
|---|---|---|---|
| `ios27-design-system` (`iOS27DesignSystem`) | iOS | iOS 26 SDK, iOS 27 for parity | `Color(uiColor:)` is UIKit-only; targets the iOS 27 kit values |
| `ios27-design-system` (`GlassProbe`) | iOS | iOS 26 SDK | `.glassEffect(_:in:)` — Liquid Glass, iOS 26+ |
| `design-reference/rPlayHub` (`rPlayHub.app`) | macOS | any current Xcode | SwiftUI/AppKit app; drives the iOS Simulator via `simctl` |

Note the first two are **iOS**, not macOS. The third is macOS but needs a
**working Simulator** to be useful, which rules out every virtualised route.

## 3. Decision

**CI on a GitHub-hosted Apple silicon runner, and a real Apple silicon Mac for
interactive work.**

`.github/workflows/macos-xcode.yml` implements the CI half today. It runs on
the `xcode-27` image (macOS 26, arm64, Xcode 27) and compiles the design
system for the iOS Simulator, the GlassProbe against the iOS 26+ SDK, and
rPlayHub.app for macOS.

This repository is **public**, so GitHub-hosted macOS runner minutes are
free and unlimited for it. `macos-26-intel` exists as a runner label but
carries only Xcode 26.x, and `macos-26` (arm64) also carries only Xcode 26.x —
one Xcode major version is supported per macOS image, so reaching Xcode 27
requires the `xcode-27` label.

For interactive Xcode — a simulator you can click, SwiftUI previews, the
Instruments profiler — the options, in order of preference:

1. **A used Apple silicon Mac mini.** M2 with 16 GB is the sweet spot; an M1
   with 8 GB is enough to compile but tight once a simulator runtime is
   installed. This is the only option that gives Xcode 27, the iOS 27 SDK, a
   Metal-backed Simulator and a usable Xcode GUI, and it has no recurring
   cost. It doubles as a build server for the AOSP side over SSH.
2. **A rented Mac mini** (MacStadium, MacInCloud, VmMac) over SSH + VNC, when
   interactive time is occasional. Note that AWS EC2 Mac instances carry a
   24-hour minimum allocation because of Apple's licensing terms, which makes
   them a pipeline tool rather than an interactive one.

## 4. Rejected options and why

| Option | Verdict | Reason |
|---|---|---|
| macOS VM (QEMU/KVM) on the build host | Rejected | Intel-capped at Xcode 26.3; no Metal, so no Simulator; 16 GB RAM shared with the AOSP build; outside Apple's macOS licence |
| Bare-metal Hackintosh on the build host | Rejected | CPU is Tiger Lake (11th gen), one generation past the guide's Ice Lake ceiling; Iris Xe GT2 is listed in the GPU Buyers Guide as "Highest Supported OS: None"; Intel SST audio has no macOS driver; AX201 CNVio2 Wi-Fi needs a Broadcom swap. Even a perfect Hackintosh would still be Intel, so still no Xcode 27 |
| Linux Swift toolchain (swift.org) | Rejected | SwiftUI, AppKit, AVFoundation, VideoToolbox and IOSurface do not exist on Linux — the sources do not compile there at all |
| Darling (macOS compatibility layer on Linux) | Rejected | Cannot host the Xcode toolchain or the iOS SDK |
| An Intel Mac | Rejected | Caps at Xcode 26.x; no iOS 27 SDK |

## 5. Repository changes made with this decision

| Change | Why |
|---|---|
| `ios27-design-system/Package.swift` | The Swift sources had no buildable home — they were plain files that no tool could compile, and they were untracked. Now a SwiftPM package with a library target and an executable target |
| `ios27-design-system/Sources/` | The two token files moved from the package root into `Sources/iOS27DesignSystem/`; the GlassProbe moved from the gitignored `design-reference/ios27-design-system/tools/` into `Sources/GlassProbe/` so it is versioned |
| `.github/workflows/macos-xcode.yml` | The Apple silicon build |
| `.gitmodules` | **Bug fix.** The index carried 22 gitlinks (mode 160000) but `.gitmodules` was missing, so every submodule path failed to resolve on a fresh clone (`fatal: no submodule mapping found in .gitmodules`). URLs were recovered from each working tree's `origin` remote and every pinned SHA was confirmed to equal that tree's HEAD |

## 6. Known limits of the CI route

- GitHub-hosted macOS runners have no persistent state between jobs. Caches and
  build products do not survive; use artifacts (the workflow uploads
  `rPlayHub.app`) or a real Mac for anything stateful.
- Simulator *runs* on a hosted runner are possible but the runner has no
  display, so the output has to be captured headlessly. rPlayHub's simulator
  streaming path is not exercised by CI today.
- The `xcode-27` image is a preview image and is not covered by the GitHub
  Actions SLA. It is pinned deliberately: it is the only image that carries
  Xcode 27. When it reaches GA the label is expected to stay `xcode-27`.
- The workflow does not sign anything. `rPlayHub.app` is built with signing
  disabled, so it cannot be distributed or notarised from CI as-is.

## 7. Related

- `docs/appleux/DESIGN_TRUTH.md` — which source is authoritative for which
  token value.
- `docs/appleux/DESIGN_SYSTEM.md` — the Android-side implementation of the
  same token architecture, which *is* buildable on this host.
- `design-reference/rPlayHub/BUILD.md` — the two-product build of rPlayHub
  (`cdhost` daemon plus `rPlayHub.app`).
