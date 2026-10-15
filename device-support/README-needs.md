# What is needed for a real POCO M5 (rock) implementation

Status: **this directory does not exist yet.** `AGENTS.md` lists
`device-support/` in the project tree, but it was never created. Nothing in
`appleux/` is device-specific today.

## Why this is not optional

Every visual number we have was taken either from Apple's own screenshots
(390pt-class logical screen, 3x) or from the 1080x2400 emulator at 420 dpi.
The target is different in ways that change layout:

| Property | Reference used | POCO M5 (rock) | Consequence |
|---|---|---|---|
| Logical width | 390-447 pt | ~411 dp at 420 dpi | tighter than iPhone, not equal to either |
| Refresh | 60 Hz (assumed) | 90 Hz panel on some variants | frame budget 11.1 ms, not 16.7 |
| GPU | SwiftShader (emulator) | Mali-G57 MC2 | blur cost differs completely |
| RAM | 15 GB host | **4 GB device, 3 GB budget** | measured emulator numbers are meaningless here |
| Panel | — | 1080x2400, notch/punch-hole | cutout inset is real, not emulated |

The blur-behind cost we have never measured is the single biggest unknown, and
it is a GPU question, so it cannot be answered on SwiftShader at all.

## What I need from you

### 1. Hardware access (highest value — unblocks everything else)

Without a device we cannot produce a single real performance number, and the
kernel track stays permanently unverified. Either:

- **the physical POCO M5**, USB-debuggable, or
- **cloud device access**, or
- confirm you want us to continue emulator-only and accept that no perf claim
  is ever made.

I am not asking for this to measure vanity numbers — it is the difference
between the kernel patches being hypotheses and being evidence.

### 2. Device facts I cannot derive

- Exact `ro.product.device` / build fingerprint of your unit
- Panel refresh rate (`dumpsys display | grep -i fps`) and whether it is 60 or 90
- RAM: is it the 4/64 or 6/128 variant? The 3 GB budget assumes 4 GB
- Whether the panel has a punch-hole or notch, and its exact inset
- Stock MIUI/HyperOS build on it, if any

### 3. Design specifics only you can decide

- **Wallpaper**: use your harvested iOS 18 set, or ship iOS 27 originals? The
  harvested set is iOS 18, and stock iOS 27 wallpapers are not something I can
  obtain. Your call.
- **Dock contents default**: currently first four apps alphabetically. Should
  be Phone / Messages / Safari-equivalent / Camera?
- **Grid density**: 4x6 as now, or 5x7 like newer iPhones? 4x6 fits a 1080x2400
  panel but leaves large gaps; 5x7 is denser and more iOS-accurate.

## What I will build regardless (no input needed)

- `device-support/rock/` with density, refresh-rate and cutout-aware tokens
- A device profile read at runtime so one APK adapts rather than forking
- Frame budget derived from the real refresh rate instead of assuming 60 Hz
- Cutout inset applied to every panel (the emulator's 191px inset was real)

## What is explicitly NOT needed

You do not need to supply Apple assets, Figma files or legal guidance. We have
53 official Apple screenshots and Apple's own icon vector banked, which is more
accurate ground truth than the Figma kit would have been. I will not be
asking for those again.