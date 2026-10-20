# AppleUX — deployability audit, 2026-10-17

Scope: how far is AppleUX from being a deployable, functioning custom ROM on
POCO M5 (Rock, mt6789)? Every claim below is backed by a command that was run
during this audit. Nothing is inferred from documentation.

## Verdict

**Not deployable. Nothing has ever booted.** The blocker is not the UI design
work — it is that the entire boot/flash path has never been attempted, and the
system-core integration is currently wired to the wrong copy of the source.

Workstream estimates:

| Workstream | State | Estimate to done |
|---|---|---|
| AppleUX UI source (Kotlin) | Written, builds on the SDK path only | ~30% left |
| System-core (Soong) integration | Wired to a stale stub copy | ~70% left |
| Boot chain (boot/vendor_boot/system/super/vbmeta) | **Never started** | 100% left |
| Bootloader unlock + fastboot flash | **Never started** | 100% left |
| Device bring-up / verification | **Never started** | 100% left |
| Automated tests | **0 tests exist** | 100% left |

Roughly: the ROM is maybe 15–20% of the way to "deployable", and almost all of
that 15–20% is UI source code that has never been compiled by the real build
system or shown on a device.

## Finding 1 (critical): two divergent AppleUX trees, Soong points at the wrong one

There are two copies of the AppleUX sources and they are wildly different.

| Module | `appleux/` (real) | `android-16/` tree (stale) |
|---|---|---|
| design-system | 66 .kt / **9217** lines / 50 components | 33 .kt / **3967** lines / 23 components |
| launcher | 30 .kt / **5445** lines | 8 .kt / **721** lines |
| settings | 12 .kt / **1785** lines | 9 .kt / **615** lines |
| systemui | 5 .kt / **1359** lines | 2 .kt / **164** lines |
| performance | 8 .kt / **1955** lines | 8 .kt / **1005** lines |

Concretely:

- `appleux/systemui/.../AppleuxSystemUIService.kt` is **1050 lines** — the real
  Control Center host, alongside `AppleuxControlTileService.kt`,
  `AppleuxShadeTileService.kt` and `AppleuxNotificationListener.kt`.
- The tree copy is **123 lines with 6 empty `onClick = { /* ... */ }`
  handlers**. It is a stub.
- `SettingsListActivity.kt` defines **39 settings rows** in `appleux/`, and
  **11** in the tree copy.

`scripts/build-emulator-apks.sh` builds from `appleux/` — the real sources.
Soong builds from `android-16/frameworks/appleux` + `android-16/packages/apps/
AppleUX*` — the stubs. So the system-core work done today would, if it compiled,
ship an **empty Control Center and a third of the Settings catalog**.

This is the single most important thing to fix, and it must be fixed before
any further system-core work.

## Finding 2: no bootable artifact exists

    $ ls android-16/out/target/product/rock/
    all_modules.txt  build_fingerprint-aosp_rock.txt
    build_thumbprint-aosp_rock.txt  clean_steps.mk
    previous_build_config.mk  previous_use_partial_compile.txt

Six bookkeeping files. No `system.img`, no `boot.img`, no `vendor_boot.img`, no
`super.img`, no `dtbo.img`, no `vbmeta.img`, no `payload.bin`, no `system.img`.

Present and usable as prebuilts:
- kernel: `thirdparty/kernel_xiaomi_rock/` — vendor `Image.gz`, `dtb`, `dtbo.img`
- vendor blobs: `thirdparty/vendor_xiaomi_rock/` — 973 MB, prebuilt

Absent entirely:
- **Bootloader.** No `lk`/MBL/ELF build, no `.mbn`, no unlock capability.
  Without this the POCO cannot be flashed even with a finished system image.

`m -j1 nothing` passing is not progress toward this. It validates product
config; it emits no images.

## Finding 3: Cuttlefish proves nothing about AppleUX

`cuttlefish/dl/` holds `aosp_cf_x86_64_only_phone-img-16373615.zip` (1.16 GB)
and `cvd-host_package.tar.gz` (899 MB). These are **stock AOSP** prebuilt
images. Booting them would demonstrate that a stock emulator works. It would
not demonstrate a single line of AppleUX code runs.

The first meaningful runtime test is: build our own Cuttlefish image with the
AppleUX modules as product packages, boot it, screenshot.

## Finding 4: no verification evidence exists

- `grep -rn "@Test" appleux` → **0**
- No screenshots, no `gfxinfo` captures, no metrics JSON anywhere in the project
- `adb devices` → empty; no emulator AVD currently exists
- AGENTS.md states the modules are "verified on the API 36 emulator" and quotes
  launcher/settings jank numbers, but the artifacts backing those claims are not
  present. Treat them as unverified until re-captured.

The frame numbers in AGENTS.md are additionally SwiftShader software-render
numbers with no GPU, so they say nothing about the 4 GB target even when they
were captured.

## Finding 5: the system-core copies have no version control

- `appleux/` → **is** a git repo
- `android-16/frameworks/appleux`, `android-16/packages/apps/AppleUX*` →
  **not** git repos, and I removed them from `.repo/project.list` today

So the tree copies have no `git diff`, no history, and no recovery. If
concurrent work overwrites one, it is gone. They are also redundant duplicates
of `appleux/`.

The correct fix is to delete the tree copies and make Soong consume `appleux/`
directly, not to sync the two by hand forever.

## What was genuinely fixed today

Worth recording, because these were real and are now behind us:

1. `m -j1 nothing` passes. The long-standing "~11 GB OOM" was `qemu-system`
   holding 3.6 GB, not Soong.
2. `appleux.mk` listed `AppleUXPerformance`, which is not a module — the real
   one is `androidux-library` shaped `appleux-performance-lib`. `m nothing`
   cannot catch this.
3. The priv-app allowlist existed but was never copied to
   `/system/etc/permissions`, so every signature|privileged grant was dead.
4. `AppleUXSystemUI` was not `privileged: true`, so it installed to
   `/system/app` and STATUS_BAR / WRITE_SECURE_SETTINGS were denied regardless.
5. AndroidX is absent from this manifest. Added `external/androidx-prebuilts`
   (48 modules). Note Android 16 has no `prebuilt_jar`; AARs go through
   `android_library_import` with plural `aars`.
6. `repo sync` was aborting on four separate faults and had never synced
   anything: malformed `rock.xml`, an `appleux.xml` pointing at a nonexistent
   GitHub remote, a `manifests` repo pinned to a nonexistent ref, and 11 stale
   entries in `project.list`. It now finishes successfully.

The AndroidX wiring was validated by Soong accepting the module definitions —
the APKs themselves have **not** been produced yet.

## Ordered path to deployable

**Step 1 — kill the duplicate trees (blocking everything else).**
Delete `android-16/frameworks/appleux` and `android-16/packages/apps/AppleUX*`.
Point the Soong modules at `OsProject/appleux/*` via `soong_namespace` or a
symlink. Until then no system-core work means anything. Put `appleux/` under git
first, and give the tree copies a git repo before deleting so there is a diff to
review.

**Step 2 — make Soong actually produce the APKs.**
`scripts/soong-capped.sh AppleUXLauncher AppleUXSettings AppleUXSystemUI`.
This has never completed. Expect the real sources to surface compile errors the
stub copies never had.

**Step 3 — first runtime proof.**
Build a Cuttlefish image with the AppleUX modules in PRODUCT_PACKAGES, boot it,
confirm HOME / SETTINGS / SystemUI binding resolve to our packages, screenshot
Settings and Control Center against `design-reference/iphone11-ios27/`.

**Step 4 — the actual ROM.**
`system.img` + `vendor_boot.img` + `dtbo` + `vbmeta` + signed `payload.bin`.
This is the first time any image gets produced.

**Step 5 — device.**
Bootloader build and unlock, backup, flash, then the frame-time / radio / audio
verification that is currently split emulator-vs-device.

## Honest bottom line

The design work and the Kotlin UI are real and substantial — 50 design-system
components, a 1050-line Control Center host, a 39-row Settings catalog, a
performance controller. That is genuine progress and it is not nothing.

But none of it has been compiled by the build system that will ship it, none of
it has run, and the ROM plumbing that would make it deployable has not been
started. The gap is not polish. It is the whole distance between "libraries that
compile" and "a phone that boots".