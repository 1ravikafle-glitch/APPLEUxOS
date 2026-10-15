# Soong Unblock Log — aosp_rock on Android 16 (bp4a)

Date: 2026-10-14. Route: `lunch aosp_rock-bp4a-eng && m -j2 nothing`.
Prior state: `build-bacon.log` (Oct 13) failed Soong bootstrap with 5 errors.
Each entry below is one fix + one re-run. All verified by the error disappearing
on the next run.

## 1. `export_cflags` is not a Soong property (4 errors)
File: `vendor/lineage/build/soong/Android.bp`
`export_cflags` appears nowhere in `build/soong/cc/*.go` nor in any AOSP bp
file. The lineage `soong_config_module_type` blocks used it; the working
lineage pattern (e.g. `egl_display_array`) uses `cflags`, which propagates via
`cc_defaults` to consumers. Changed `properties: ["export_cflags"]` →
`["cflags"]` and inner `export_cflags:` → `cflags:` in:
gralloc_handle_custom_content_md_reserved_size, gralloc_handle_reserved_size,
gralloc_handle_ubwcp_format, bootloader_message_offset (also dropped the
now-duplicate `cflags` line that edit created in the bootloader block).

## 2. Duplicate `generated_kernel_headers`
`vendor/lineage/.../soong/Android.bp` defines it as `cc_library_headers`;
`device/xiaomi/rock/Android.bp` defined it as `filegroup`. Renamed rock's to
`rock_generated_kernel_headers` (only referenced by PowerOffAlarm/fingerprint,
which are not in this product; they now bind to lineage's).

## 3. Missing release config (Android 16 requires `lunch <product>-<release>-<variant>`)
`aosp_rock.mk` set no release. Added
`PRODUCT_RELEASE_CONFIG_MAPS += $(wildcard device/xiaomi/release/release_config_map.textproto)`
(bp4a matches BUILD_ID BP4A.251205.006). `AndroidProducts.mk` lunch choices →
`aosp_rock-bp4a-{userdebug,eng}`.

## 4. Namespace `hardware/lineage/interfaces` does not exist
Two-part cause: (a) `PRODUCT_SOONG_NAMESPACES` in `device.mk` listed
`hardware/xiaomi` (which imports it) but not the namespace itself → dir pruned;
(b) per `build/soong/android/namespace.go`, namespaces are created ONLY by
`soong_namespace` modules, and `hardware/lineage/interfaces/Android.bp`
contained only `hidl_package_root`. Fixed both: added the namespace to
`PRODUCT_SOONG_NAMESPACES`, added empty `soong_namespace {}` (first module)
to the lineage interfaces bp.

## 5. Undefined `androidx.compose.compiler_compiler-hosted`
`vendor/lineage/kotlin/Android.bp` kotlin_plugin needs -host androidx modules
absent from this selective sync (`prebuilts/sdk` partial). Plugin is
referenced by nothing in tree → commented out with re-enable note.
Proper fix: `repo sync platform/prebuilts/sdk` before apps needing it.

## 6. Undefined `android.hardware.vibrator@1.0`
Upstream Android 16 removed the vibrator@1.0 HIDL library (AIDL-only;
`hardware/interfaces/vibrator/` has aidl/bench only — verified radio/nfc/
sensors/usb/fastboot HIDL still present). The lineage 1.0-lineage service is
in no product (rock uses `vibrator-service.mediatek`) → commented out.

## 7. Undefined `mediatek-*` boot jars
`PRODUCT_BOOT_JARS` in `device.mk` needs proprietary blobs; `rock-vendor.mk`
is intentionally empty (no hardware to extract from yet). IMS stack commented
out with re-enable note — functionally no-op without blobs.

## 8. Soong glob panic on `/overlay-lineage` (WifiResOverlayRock et al.)
`rock-vendor.mk` used `$(DEVICE_PATH)` (BoardConfig-context var, empty in
product makefiles) → absolute path `/overlay-lineage`. Soong's `**` glob on a
missing absolute dir walks parents up to `/`, which matches, then
`removeSrcDirPrefix` panics on `Rel(top, "/")`. `$(call my-dir)` also expands
empty here. Fix: literal `device/xiaomi/rock/overlay-lineage`.
(Mechanism confirmed in `build/blueprint/pathtools/glob.go` + `fs.go`.)

## 9. `libs: ["android.frameworks"]` in all 5 AppleUX bp files
`android.frameworks` is a genrule, not a .jar-producing lib. Removed from
`frameworks/appleux` + 4× `packages/apps/AppleUX*` (emulator build proves only
android.jar + androidx are needed).

## Status
Soong analysis now completes (`[100% 2/2] analyzing ... generating
out/soong/build.aosp_rock.ninja`); `m -j2 nothing` proceeded into the ninja
build phase. AOSP/Cuttlefish still NOT verified — no images built yet.
Emulator APK route remains the verified path (see `runtime-verification-emulator.md`).

## 10. VERDICT: full-tree Soong analysis OOMs on this box (standing blocker)
Two kills, kernel-logged: `Out of memory: Killed process soong_build,
anon-rss ~11-12GB`. No cgroup cap (`memory.max=max`), 49GB swap free — the
failure is allocation-burst pressure + victim selection (soong inherits
oom_score_adj=200), not a hard ceiling. `-j1` and `GOGC=20` do not help:
the ~11GB is live blueprint state for the whole 146GB tree, not parallelism.
Box budget: 15GB total, ~5GB taken by desktop+qemu+opencode.
Consequence: `m` (any target incl. `nothing`) cannot complete here while the
desktop/emulator live alongside it. Paths: (a) hard-serialized run with qemu
+ browser stopped (~13GB free, untested); (b) external/cloud builder for
bacon/device images; (c) trimmed lunch with fewer source roots.
Emulator-APK and prebuilt-Cuttlefish verification routes are unaffected and
remain the working paths — neither needs Soong. ALSO NOTE for the emulator
thread: the `x86_64-appleux` sysdir image (osbuild 4.2G, Oct 13) never
completed a boot in 2+ hrs of trying; if the AVD still won't come up, switch
its `image.sysdir.1` to stock `x86_64` + `adb install` the APKs.
