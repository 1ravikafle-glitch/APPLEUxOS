# m31 — Physical POCO M5 Connected + Device Tree Audit

**Date:** 2026-10-19
**Milestone:** the physical target device is now reachable via ADB. The
long-standing "No physical device yet" constraint in AGENTS.md is **obsolete**.

---

## 1. Physical device (SAVE THIS — future flashing/testing target)

| Prop | Value |
|---|---|
| **ADB serial** | `nb79nvba65tse6ug` |
| Device (`ro.product.device`) | `rock` |
| Build product (`ro.build.product`) | `missi` |
| Board platform (`ro.board.platform`) | `mt6789` |
| Model (`ro.product.model`) | `22071219CI` |
| Market name | **POCO M5** |
| Brand | POCO |
| **Current ROM** | MIUI — `POCO/rock_p_in/rock:14/UP1A.231005.007/V816.0.8.0.ULUINXM:user/release-keys` (Android 13, MIUI V816) |
| Bootloader state | **release-keys** — locked unless unlocked; flashing our ROM requires `fastboot flashing unlock` |

Usage: `adb -s nb79nvba65tse6ug shell ...`

**Current ROM is stock MIUI with release-keys** — the device is the FLASH
TARGET, not an overlay-test target. Our ROM (test-keys) cannot be pushed to
it as-is; it needs an unlocked bootloader + a full image flash (boot.img/
vendor_boot/super). The Cuttlefish guest remains the overlay-test target
until then.

## 2. Android 17 exists upstream

`refs/tags/android-17.0.0_r1` → commit `5bc9a7ce1cd78dd53613bbfd0ebf506e1e4adb0f`.
Our tree is Android 16 (BP4A); the Cuttlefish stock guest is Android 17
(CP2A.260605.016). **Framework drift between our 16 build and a 17 guest is
the known runtime risk** (all chrome attach points are try/catch-protected).

## 3. Device tree audit (bring-up state — all present, all healthy)

| Component | State |
|---|---|
| `device/xiaomi/rock` | LineageOS-derived BoardConfig + device.mk, A/B, AVB **test-keys** (`testkey_rsa4096`), EROFS vendor, dynamic partitions (super 9126805504, group mtk_dynamic_partitions) |
| `vendor/xiaomi/rock` | **1.1 G** proprietary blobs |
| `kernel/xiaomi/rock` | **301 M** — prebuilt Image.gz, dtb, DTBO, vndr + dlkm module trees (kernel 5.10, header v4, GKI) |
| `device/xiaomi/rock-kernel` | 223 M |
| VINTF manifest | Full HAL set: audio 7.0, BT 1.1, radio 1.2/1.6 (+ mtkradioex 3.0), sensors 2.0, gatekeeper, graphics composer 2.3, keymaster_attestation, Dolby dms, fingerprints + goodix + xiaomi fingerprint extensions, NFC (nxp), displayfeature, mtkpower 1.2 |
| fstab | system/system_ext/vendor/product EROFS+ext4 dual, F2FS userdata w/ metadata-encryption v2+inlinecrypt, MT6789 firmware partitions |
| Screen config | 1080×2400 @440dpi — **matches our measured panel** |
| Dalvik | 16GB-RAM-optimised heap (256m growthlimit / 512m heap) |

Build invariants worth keeping: `BUILD_BROKEN_DUP_RULES`, AVB
`--set_hashtree_disabled_flag` (verity disabled at build), target
`aosp_rock-bp4a-eng`.

Disk headroom: 236 G free (67% used) — a full `m` dist image build fits.

## 4. Implications for the plan

1. **Cuttlefish guest** stays the overlay-test target (our SystemUI.apk is
   already pushed there; screenshot checklist pending).
2. **Physical POCO M5** is the flash target for the real ROM. To use it:
   unlock bootloader (user decision — wipes data), then fastboot-flash our
   boot/vendor_boot/super images when we build them.
3. **Full device image build** is now the next build goal after chrome
   runtime verification: `m` (default target) for `out/target/product/rock/`
   boot.img + super.img. The 45-min analysis + ~1900 targets are already
   compiled — incremental cost is the remaining image assembly.
4. Vendor/kernel trees exist and are complete — no bring-up blockers.
