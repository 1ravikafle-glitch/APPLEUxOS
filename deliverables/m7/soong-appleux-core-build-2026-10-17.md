# AppleUX — system-core Soong unblock log (2026-10-17)

Goal: build `AppleUXLauncher`, `AppleUXSettings`, `AppleUXSystemUI` through
Soong inside the AOSP 16 tree, as real system-core packages. `m nothing` was
already passing but proves nothing about these three — it never builds them.

## Blocker 0 (cleared): RAM

The standing ~11-12GB anon-RSS OOM was a loaded host, not Soong. `qemu-system`
was holding 3.6GB. Killed it → 11Gi available → `m -j1 nothing` succeeded in
48:21. Lesson: free the host before blaming the build.

## Blocker 1 (cleared): bogus PRODUCT_PACKAGES entry

`device/xiaomi/rock/appleux.mk` listed `AppleUXPerformance`, but the module is
`appleux-performance-lib` — an `android_library`, not installable. `m nothing`
does not validate PRODUCT_PACKAGES, so this would only have failed at install
time, after a very long build.

`appleux-performance-lib` and `appleux-design-system` reach the image via
`static_libs` in each app's Android.bp, never via PRODUCT_PACKAGES.

## Blocker 2 (cleared): priv-app allowlist was never copied

`device/xiaomi/rock/permissions/privapp-permissions-appleux.xml` existed but
nothing referenced it. Without `PRODUCT_COPY_FILES` into
`/system/etc/permissions`, every signature|privileged grant is denied at
runtime even when the APK is in priv-app. Added the PRODUCT_COPY_FILES line.

## Blocker 3 (cleared): SystemUI was not priv-app

`AppleUXSystemUI` had `privileged: true` missing entirely, so it installed to
`/system/app` and its STATUS_BAR / WRITE_SECURE_SETTINGS grants were inert.
Added `privileged: true`.

Also: the allowlist granted permissions the manifest never declared (a grant
for an undeclared permission is dead). Manifests now declare exactly what the
allowlist grants — launcher gained CAPTURE_AUDIO_OUTPUT + BIND_APPWIDGET,
SystemUI gained EXPAND_STATUS_BAR/STATUS_BAR/WRITE_SECURE_SETTINGS/
PACKAGE_USAGE_STATS/CHANGE_WIFI_STATE/BLUETOOTH_CONNECT/BLUETOOTH_SCAN.
Removed a dangling `android:sharedUserLabel` with no `sharedUserId`.

## Blocker 4 (cleared): AndroidX absent from the tree

Root cause of the real failure. `grep androidx .repo/manifests/default.xml` →
nothing. This tree has no `external/androidx`, so Soong could not resolve
`androidx.appcompat_appcompat`, `androidx.recyclerview_recyclerview`,
`androidx.core_core-ktx`, or `androidx.dynamicanimation_dynamicanimation` for
any AppleUX module.

Fix: `external/androidx-prebuilts/` — 48 modules built from the AARs the SDK
route already uses in `OsProject/thirdparty/aar` (vendored into the tree so the
Soong build has no external path dependency). Exposed as one aggregate module
`androidx_runtime`.

### Android 16 module-type gotchas (cost two failed soong bootstraps)

- There is no `prebuilt_jar` in this tree. Plain jars → `java_import` + `jars`.
- AARs → `android_library_import` with a **plural** `aars` property, not
  `android_import` with singular `aar`. `android_import` is not registered at
  all here and fails at bootstrap with "unrecognized module type".

App `Android.bp` files now declare `static_libs: ["androidx_runtime", ...]`
instead of the five nonexistent androidx module names. `androidx.preference` was
declared by AppleUXSettings but never imported by any source file — dropped.

## Blocker 5 (cleared): rock.xml was not well-formed XML

`.repo/local_manifests/rock.xml` failed to parse, which made **every** `repo`
command die with `error parsing manifest ... not well-formed (invalid token):
line 14, column 37`. Two separate illegal constructs, both inside one comment:

1. Arrows written as the literal sequence less-than + hyphen. Less-than always
   opens a tag, even inside a comment.
2. `git clone --depth=1 --single-branch` — `--` is illegal inside an XML comment
   ("Double hyphen within comment"). Fixing only the arrows was not enough.

Both are now spelled out in full and the command dropped. No semantic change to
the manifest — it still only declares three remotes and deliberately keeps the
rock/hardware projects OUT of repo management so `repo sync` cannot reset the
manually-placed bring-up work. Verified those six paths appear in no manifest
project entry, so a full sync will not touch them.

## Blocker 6 (cleared): appleux.xml aborted the ENTIRE sync

`repo sync` failed with five identical errors:

    fatal: 'frameworks/appleux' does not appear to be a git repository
    GitCommandError: 'fetch ...' on frameworks/appleux failed

`.repo/local_manifests/appleux.xml` declared five `<project>` entries against a
`github.com/appleux` remote that has never existed. None of those five
directories is a git repository, and none ever was — they are manually placed
sources. One unreachable project entry fails the whole sync, so **no other
project was being synced either**. The manifest is now remotes-only, matching
how `rock.xml` already handles the manually placed rock/hardware trees.

Soong finds these directories by directory scan, so nothing about the build
depends on them being repo projects.

## Blocker 7 (cleared): manifests repo pointed at itself

    error: vendor/xiaomi/rock: Cannot remove project: uncommitted changes are present.
    fatal: couldn't find remote ref refs/heads/android-16.0.0_r4

Two separate faults:

1. `.repo/manifests` had `remote.origin.url` set to its own path, and
   `branch.default.merge` = `refs/heads/android-16.0.0_r4`, a ref that existed
   only as `refs/remotes/origin/...`. repo fetches `refs/heads/*` from origin,
   so the fetch failed and aborted the sync before any project was touched.
   Fixed locally by creating the missing branch at the same commit
   (`git branch android-16.0.0_r4 default`). Origin is that same directory, so
   the fetch now resolves. Manifest content unchanged.

2. `--prune` and the stale tracking entries. `repo` kept 11 paths in
   `.repo/project.list` that `rock.xml` / `appleux.xml` deliberately exclude:

       device/xiaomi/rock             hardware/lineage/interfaces
       device/xiaomi/rock-kernel      hardware/mediatek
       frameworks/appleux             hardware/xiaomi
       packages/apps/AppleUXLauncher  vendor/lineage
       packages/apps/AppleUXSearch
       packages/apps/AppleUXSettings
       packages/apps/AppleUXSystemUI

   For each one repo tried to drop it from tracking, hit uncommitted changes,
   and aborted the sync. `--prune` is exactly the wrong flag for a tree that
   keeps hand-modified projects out of repo management; it buys nothing on a
   full sync and risks deleting bring-up state.

   Fixed by removing those 11 lines from `.repo/project.list`. That file is
   repo bookkeeping only and does **not** touch working trees. Verified after
   the edit: all 11 directories still present with their contents.

## Getting a reliable project/path comparison

`repo list` does not emit a simple `name : path` pair, so parsing it with
`awk -F' : '` produced garbage and listed ~700 legitimate projects (art,
bionic, build/make) as stale. The trustworthy comparison is to pull the path
attributes straight out of the manifest XML:

    grep -ho 'path="[^"]*"' .repo/manifests/default.xml .repo/local_manifests/*.xml

1013 manifest paths vs 1011 tracked — a difference of exactly 11.

## Full sync

`repo list` = 1018 projects. Disk is not the constraint (296 GB free on
`/dev/nvme0n1p4`); RAM is. Full sync started with `repo sync -c -j6
--no-clone-bundle --optimized-fetch`. `-j6` because repo sync is IO/network
bound, not RAM bound.

## RAM capping — the actual lever

`soong_build` is a Go **1.24** binary. Measured peak RSS during whole-tree
analysis: **9.8 GB** on a 15 GB box. That is live heap, so `-j1` does not help
— it just makes the same allocation slower. Go 1.19+ exposes `GOMEMLIMIT`,
which makes the runtime trade CPU for memory and collect harder instead of
growing the heap. That is the knob, and it was never tried before.

`scripts/soong-capped.sh` wraps it: `GOMEMLIMIT=8GiB`, `GOGC=50`, `SOONG_JOBS=1`,
`NINJA_ARGS=-j1`, `KOTLIN_BUILD_WORKERS=1`, plus a 15 s sampler that records
peak combined RSS so memory regressions are visible instead of silent.

Swap is 49 GB with 48 GB free, so running serialized-and-slow is the right
trade: correctness-gated build, not throughput-gated.

## Verification status

- `m -j1 nothing` — EXIT 0. Config + Soong analysis + Kati only.
- `xmllint` — manifests + allowlist OK.
- `aapt2 compile` — both overlay res dirs OK.
- `m -j1 AppleUXLauncher AppleUXSettings AppleUXSystemUI` — **first run** in
  progress. This is the first attempt that actually compiles these sources
  through Soong; the SDK route (`scripts/build-emulator-apks.sh`) is a
  separate aapt2/kotlinc/d8 path and proves nothing about the tree.

## Still unproven

- A system image. Nothing here has booted.
- On-device or emulator pixels of the Soong-built APKs.
- Stock Settings/Launcher3/SystemUI are still in-tree by design (charter:
  verify replacement, then remove). They are excluded by product membership,
  not by `rm`, because deleting source breaks the Soong dependency graph.