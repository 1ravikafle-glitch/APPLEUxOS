# AppleUXOS — RISK REGISTER

> **CONFIDENTIAL — INTERNAL PROJECT DOCUMENT.**
> **Status: DRAFT v1 — researched, not yet validated on hardware.**
> Likelihood (L) and Impact (I) are scored 1-5 by judgment [D]; Score = L x I. Bands: **15-25 Critical**, **8-14 High**, **4-7 Medium**, **1-3 Low**. "Residual" is the expected score *after* the mitigation is done, also an estimate. Re-score after Milestone 0 and Milestone 8.
> Evidence tags: **[R]** public source (section 6) · **[U]** reported by the project owner · **[V]** verify on the device/tree · **[D]** design decision/estimate · **[G]** general engineering knowledge.

---

## Part A — POCO M5 (`rock`), Indian variant, 4 GB RAM / 64 GB UFS 2.2

### A.0 Variant facts that drive the risks
| Fact | Tag |
|---|---|
| India model: 4 GB + 64 GB (UFS 2.2); other India config is 6 GB + 128 GB; microSD slot present; stock UI MIUI 13 on Android 12 at launch | [R] |
| India 4G bands: 1, 3, 5, 8, 40, 41; VoLTE supported; dual-SIM; no 5G | [R] |
| India variant is splash resistant, has an 8 MP front camera and a hardware gyro (the global variant differs) | [R] |
| Stock firmware reported on your unit: Android 14, build V816.0.8.0.ULUINXM, treble on, VNDK 31, active slot _b | [U] |
| Stock firmware includes up to 1 GB of "virtual RAM" (swap on storage) | [R] |
| Xiaomi bootloader unlock needs the official Mi Unlock process with a waiting period (over 7 days quoted, longer after several unlocks, per-account limits) and wipes data | [R] |
| Daily use in India is UPI/banking heavy; many such apps depend on Google's Play Integrity, and hardware-backed attestation fails on unlocked/custom builds | [R] |

### A.1 Gating rules (stop conditions) [D]
These are the optimization for the whole register: if any gate fails, work stops until it is fixed.
1. **Never flash the only phone you depend on.** Use a dedicated test unit, or keep a second phone for calls, UPI and banking. (Risk A1, A2)
2. **No flash before the full backup is verified:** stock firmware package for the *same India region and build*, plus a dump of the device-unique partitions (see A12). Test that the backup files open/checksum before proceeding.
3. **No boot-chain experiments on both A/B slots at once.** Flash one slot, keep the other as a known-good fallback where the layout allows. [V]
4. **No overclock, undervolt or charge-limit change in a release image.** (Risks A9, A10; see `PERFORMANCE_AND_POWER_TUNING.md`)
5. **No build is called "daily-usable" until** the phone-critical checklist passes: emergency call, SMS, VoLTE call on your SIM, mobile data, Wi-Fi, Bluetooth audio, camera preview, fingerprint (if present on the unit), charging, 24 h standby with no unexplained drain.

### A.2 Register (sorted by score before mitigation)
| ID | Risk | L | I | Score | Mitigation (optimized for this variant) | Residual | Tag |
|---|---|---|---|---|---|---|---|
| A1 | **UPI/banking/Aadhaar apps fail** on an uncertified, unlocked-bootloader ROM; payment apps often check Google's integrity verdicts and some use hardware-backed device binding, and mAadhaar-type apps may refuse custom ROMs | 5 | 5 | **25** | Treat AppleUXOS as a **secondary/test OS**; keep UPI and banking on a stock phone. Do not use spoofing, fake keyboxes or attestation emulators (fragile, can violate app/Google terms, and contradict the project's security rules). Offer web banking, bank SMS/USSD options for the user to evaluate; document the limitation in release notes. | 10 (accepted, documented) | [R][D] |
| A2 | **Bricking or soft-bricking** the only phone (bad flash, wrong region firmware, interrupted flash) | 3 | 5 | **15** | Gate 1 and 2; exact-region fastboot package for India; stable power and cable; flash steps scripted and rehearsed on the emulator-side image first; keep recovery/fastboot access verified before every flash. | 6 | [D][V] |
| A3 | **Bootloader unlock blocked, delayed or account-restricted** by Xiaomi's process | 4 | 4 | **16** | Start the official unlock waiting period early (it can take over a week and may be extended after several unlocks); use only the official tool; do not use unofficial bypass tools (they may get the account/device banned and are outside this project's policy). Until unlocked, continue emulator/Cuttlefish-only work. | 6 | [R][D] |
| A4 | **Host cannot build** (16 GB RAM vs Google's 64 GB guidance) | 5 | 3 | **15** | Large swap + low `-j` + ccache; module/incremental builds; rent a cloud machine for full builds (record cost and time); GSI route for early UI tests on the phone. | 6 | [R][D] |
| A5 | **Storage pressure on 64 GB** (system image, ART artifacts, licensed assets, user data; fixed dynamic-partition (super) size from the stock layout) | 4 | 3 | **12** | Measure partition sizes in M0; keep system image lean (no unused apps, strip debug); assets stored once and compressed where licensing allows; budget a hard cap for `system+product+system_ext` and fail the build if exceeded; microSD for user media; verify that the stock `super` size can hold the Android 16 image. | 4 | [V][D] |
| A6 | **Janky UI on Mali-G57 MC2 + 4 GB** (blur, translucency, Compose scenes) | 4 | 4 | **16** | Blur off in Tier B and selective in Tier A under a frame-time governor; cached backdrops; flag-gated Compose scene framework only if it passes the memory budget; ship the Efficiency profile as default under battery saver; measure against stock baseline before adopting any effect. | 8 | [R][D] |
| A7 | **Vendor layer frozen at Android 12/14 generation** (VNDK 31): Android 16 framework may need HAL/sepolicy shims; some HALs may not work | 4 | 4 | **16** | Read vendor manifest target FCM in M0 (Android 16 supports FCM from Android 12 through 202504); start from the closest community device tree as *reference* only; test one HAL class at a time (display, audio, RIL, camera) with the phone-critical checklist. | 8 | [R][V] |
| A8 | **Telephony regressions on India networks** (VoLTE/IMS, carrier config for Jio/Airtel/Vi, band support 1/3/5/8/40/41) | 3 | 5 | **15** | Test with your actual SIMs early; keep the vendor RIL/IMS stack untouched; verify carrier config and APN data; emergency-call test is a gate. Do not claim carrier compatibility without tests. | 6 | [R][V] |
| A9 | **Overclock/undervolt instability or heat damage** | 3 | 4 | **12** | Stock default; Efficiency (underclock) first; Experimental profile only on dev builds with watchdog revert, thermal ceilings and stress tests. Indian summer ambient heat makes sustained thermal testing mandatory (test warm). | 4 | [D] |
| A10 | **Battery/charging safety** | 2 | 5 | **10** | Never raise voltage/current/protection limits; read-only monitoring; optional lower charge limit only through a supported vendor interface; low-battery boost cut-off and load-step test; do not leave test builds charging unattended overnight until charging behavior is validated. | 3 | [D] |
| A11 | **Verified Boot off / unlocked state** weakens physical-theft protection | 3 | 3 | **9** | Mandatory screen lock with credential-encrypted storage; unlock wipes data so no stock data is exposed; document the unlocked state to users; re-lock path only if a self-signed verified-boot flow is supported later. | 4 | [R][D] |
| A12 | **Loss of device-unique data (MediaTek NVRAM/IMEI, persist, calibration) ** | 2 | 5 | **10** | Dump and checksum device-unique partitions before any flash and store off-device (exact partition list verified from the device's partition table in M0, commonly including nv/protect/persist/proinfo/modem-related partitions on MediaTek). Never format those partitions. Restore procedure rehearsed once on a spare device if available. | 3 | [G][V] |
| A13 | **Anti-rollback / downgrade traps** in Xiaomi firmware | 2 | 5 | **10** | Check rollback index/anti-rollback state before any older-firmware flash; never flash firmware older than the installed one without confirming it is allowed; record the current build (V816.0.8.0.ULUINXM) as the reference. | 3 | [G][V] |
| A14 | **Camera and sensors** depend on vendor blobs (camera HAL, ISP, gyro in India variant) | 3 | 3 | **9** | Camera is a stretch goal (matrix marks it conditional); verify per-sensor; ship without camera features rather than ship broken ones; document gaps. | 4 | [R][V] |
| A15 | **Kernel unknowns** (version, GKI vs vendor kernel, module signing, scheduler extensions) | 3 | 4 | **12** | Read-only kernel audit (M0); build the kernel from the device's open source drop that matches the vendor image; keep kernel changes minimal and logged; do not use out-of-tree runtime patch modules in releases. | 5 | [V][D] |
| A16 | **Licensed assets leak** (fonts, icons, sounds in a repo/build) | 2 | 5 | **10** | Private repo only; `vendor/appleux/assets/` excluded from public pushes; manifest with agreement references only (no agreement text); builds containing assets never uploaded to public hosts. | 3 | [D] |
| A17 | **Android 16 patch/branch drift** (Android 17 is now AOSP default; source drops in Q2/Q4) | 3 | 3 | **9** | Pin the Android 16 branch; scheduled merge windows; patch ledger. | 4 | [R][D] |
| A18 | **No Google services** break features (sync, push in some apps, Quick Share, Play Store) | 5 | 3 | **15** | State up-front; evaluate open alternatives (open app store, push via persistent connection options) in M6; do not market compatibility that is not tested. | 8 | [R][D] |
| A19 | **Warranty/support** loss after unlocking | 4 | 2 | **8** | Treat the test phone as out of warranty; check Xiaomi policy for the unit before unlocking. | 8 (accepted) | [V] |
| A20 | **Memory tuning backfires** (zRAM/lmkd/cached apps cause stalls or kills) | 3 | 3 | **9** | One change at a time, PSI stall measurements, revert on regression, `TUNING_LOG.md`. | 3 | [D] |
| A21 | **Single-unit tuning** does not generalize | 3 | 3 | **9** | If possible validate on a second POCO M5; otherwise label results "unit-specific". | 5 | [D] |
| A22 | **Developer burnout/scope** (full OS redesign by one team) | 4 | 4 | **16** | Milestone gates; ship M3 (core UI) as a usable daily-driver demo before deeper surfaces; cut scope by coverage-matrix priority (P0 first). | 9 | [D] |

### A.3 Top 5 to attack first
1. **A1** (payments/banking): decide now that the test phone is secondary. This single decision removes the largest user-harm risk.
2. **A2/A12/A13** (brick/NVRAM/rollback): build and verify the backup and restore kit before the first flash.
3. **A3**: start the unlock waiting period early.
4. **A4**: decide cloud vs local builds after the first measured build.
5. **A7/A8**: audit vendor manifest and telephony before any UI work depends on a booting device.

---

## Part B — Other devices (device-agnostic register)

Use this when adding any phone beyond the POCO M5. Create a copy per device (`RISK_REGISTER_<codename>.md`) and fill the "Device facts" checklist first.

### B.0 Device-profile checklist (fill before assessing risk)
| Question | Why it matters |
|---|---|
| SoC vendor and model (MediaTek / Qualcomm / Samsung / Google) | Determines kernel, HAL and tooling availability |
| RAM and storage, storage type | Tier assignment (A/B), image size budget |
| Launch Android version and current vendor/VNDK level | Android 16 supports vendor FCM from Android 12 up; older launches are out of window [R] |
| Treble compliance and launch API level | GSI needs Treble and launch API 28 or higher [R] |
| Bootloader unlockable? Official method? Waiting time? | Hard gate; some OEMs do not allow unlocking |
| A/B or non-A/B, dynamic partitions, super size | Flash procedure and image size limit |
| Kernel source and vendor blobs available? License? | Buildability |
| Community device trees and their maintenance | Reference or base |
| Region/variant differences (bands, camera, sensors) | Telephony/hardware test scope |
| Anti-rollback / secure-boot behavior | Brick risk |
| Display refresh rates, GPU model | Rendering budget |
| Battery and charger design | Thermal/charging limits |

### B.1 Risk register by device class
| ID | Risk | Applies most to | L | I | Score | Mitigation / decision rule |
|---|---|---|---|---|---|---|
| B1 | **Bootloader cannot be unlocked** | Carrier-locked and some OEM devices | 3 | 5 | 15 | Hard gate: if no official unlock, device is out of scope (emulator only). |
| B2 | **No kernel source / vendor blobs** | Locked ecosystems, new SoCs | 3 | 5 | 15 | Out of scope unless sources exist; GSI-only testing is possible on Treble devices but not a full OS. |
| B3 | **Vendor too old or incompatible with the Android 16 framework** | Devices launched before Android 12 | 4 | 4 | 16 | Check FCM level; if outside the supported window, mark unsupported. |
| B4 | **Brick risk differs by SoC** (MediaTek vs Qualcomm recovery modes) | All | 3 | 5 | 15 | Document the device-specific low-level recovery path *before* flashing; confirm tooling support. |
| B5 | **Low-RAM devices (2 GB)**: OOM, stalls, UI too heavy | Entry-level | 4 | 4 | 16 | Tier B config, effects off, memory-budget gate; test with real 2 GB hardware before claiming support. |
| B6 | **Weak GPU / no blur support** | Entry-level GPUs | 4 | 3 | 12 | Blur off by default (device opt-in property); cached backdrops. |
| B7 | **Storage too small** (16/32 GB) | Entry-level | 4 | 4 | 16 | Image size budget fail-gate; may need a reduced app set or unsupported status. |
| B8 | **Payments/banking and DRM fail** (attestation, Widevine level) | All unlocked/uncertified devices | 5 | 4 | 20 | Document; recommend a stock phone for payments; do not spoof. DRM level checked per device. |
| B9 | **Telephony/regional band gaps** | Non-matching regional variants | 3 | 5 | 15 | Per-variant telephony test; list supported variants explicitly. |
| B10 | **Camera/biometric/sensor HAL breakage** | All, worst with proprietary ISPs | 4 | 3 | 12 | Treat as optional features; ship what works, document what does not. |
| B11 | **Thermal and sustained performance differences** | Thin/hot-climate devices | 3 | 3 | 9 | Re-measure sustained curves per device; do not reuse tuning values across devices. |
| B12 | **Overclock/undervolt values do not transfer** | All | 4 | 4 | 16 | Per-unit only; stock default; never in releases. |
| B13 | **Charging/battery protection differences** | All | 2 | 5 | 10 | Same rule everywhere: never raise limits. |
| B14 | **Anti-rollback, secure boot, OEM lock features** | Xiaomi, Samsung (Knox), others | 3 | 5 | 15 | Per-OEM check; some irreversible changes (for example fuses/counters) must be understood before flashing. |
| B15 | **Unmaintained/low-quality community trees** | Niche devices | 3 | 4 | 12 | Audit; prefer actively maintained trees; own the fork. |
| B16 | **Maintenance load multiplies per device** | Multi-device support | 4 | 4 | 16 | Support tiers: one primary device (POCO M5), at most 1-2 secondary; the rest community/unsupported until the primary reaches release. |
| B17 | **Licensed assets distributed to other users/devices** | Any public distribution | 2 | 5 | 10 | Confirm agreement terms on redistribution before sharing images (see `ASSET_MANIFEST.md`). |
| B18 | **Security patch lag** (vendor/kernel frozen) | Older devices | 4 | 3 | 12 | Publish patch level per device; mark end-of-support; keep security-relevant components updated where the vendor layer allows. |

### B.2 Support tiers for devices [D]
| Tier | Meaning | Entry condition |
|---|---|---|
| Primary | Fully tested, release-blocking | POCO M5 India 4/64 passes all gates |
| Secondary | Tested subset, best effort | Unlockable, sources available, Android 12+ vendor, >= 3 GB RAM |
| Experimental | Builds boot, no guarantees | Meets B1-B3 only |
| Unsupported | Not built | Fails B1, B2 or B3 |

## 5. Review cadence
Re-score the register at: end of Milestone 0, first successful emulator boot, first device flash, and before any release. Add new risks as they appear; never delete a risk, mark it Closed with the date and evidence.

## 6. Sources (researched 19 Oct 2026)
- POCO M5 India specs (bands, storage, India vs global differences): https://m.gsmarena.com/xiaomi_poco_m5_(india)-ampp-11869.php
- POCO M5 India configurations and UFS 2.2: https://deccanherald.com/technology/poco-m5-with-mediatek-chipset-launched-in-india-1142456.html
- Stock virtual RAM option listed for POCO M5: https://us.smartprix.com/mobiles/poco_m5_vs_poco_m4_5g-cpd1gf1zyjlx_pd17yrmm70y.php
- Xiaomi bootloader unlock FAQ (risks, waiting times): https://www.mi.com/global/support/faq/details/KA-07238/
- Xiaomi unlock process background: https://droidwin.com/?p=54748
- Play Integrity hardware-backed verdicts and custom ROMs: https://www.androidauthority.com/google-play-integrity-hardware-attestation-3561592/
- Indian user reports on UPI/banking with unlocked bootloaders: https://techenclave.com/t/are-you-using-stock-android-or-a-custom-os/445886 , https://dev.to/pranavpatel/passing-play-integrity-but-failing-upi-why-i-relocked-my-oneplus-6t-d46 , https://gotlou.srht.site/microg-review-2.html
- Android 16 supported vendor FCM levels: https://source.android.com/docs/core/architecture/vintf/fcm
- Android 16 GSI requirements: https://developer.android.com/about/versions/16/gsi-release-notes
- AOSP hardware requirements and branch status: https://source.android.com/setup/download , https://source.android.com/docs/whatsnew/site-updates

---

## Part C — REALIZED RISKS (2026-10-19, all with evidence)

1. **Framework drift 16→17 (REALIZED)**: 16-built SystemUI cannot run on the
   17 stock Cuttlefish guest — named missing method
   `android.tracing.Flags.sysuiLargePerfettoShmemBuffer()`. Mitigation:
   framework-matched guest built from our tree (`aosp_cf_x86_64_only_phone`).
   Upstream `android-17.0.0_r1` exists; our tree pins 16 (BP4A).
2. **Silent file loss via .gitignore (REALIZED, twice)**: `android-16/` is
   ignored; `git add -A` skips NEW files — lost `apps-shared/Android.bp`
   (broke every Soong target) and left SystemUI edits uncommitted.
   Rule: `git add -f` for new tree files.
3. **Nested repo confusion (REALIZED)**: `frameworks/base` has its own git;
   SystemUI edits must be committed there, not the outer repo.
4. **Host build fragility (REALIZED)**: capped builds die to kills (oomd PSI,
   swap caps); systemd-run hides output; env vars don't propagate.
   Proven config documented in BUILD_AND_TEST_STRATEGY.
5. **Locked bootloader on the physical M5 (OPEN)**: flash requires user unlock
   decision (wipes data). Device is MIUI 13 release-keys today.
6. **No measured device numbers (OPEN)**: every perf figure so far is
   emulator-SwiftShader or projected. Do not quote as results.