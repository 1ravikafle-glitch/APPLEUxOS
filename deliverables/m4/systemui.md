# Phase 4 — AppleUX SystemUI

## Status

**Source complete. Not built, not run, not verified.**

`appleux/systemui/` has an `Android.bp` and is intended to build inside AOSP.
It is not part of `scripts/build-emulator-apks.sh`, which only drives `aapt2`,
`kotlinc`, `d8` and `apksigner` for the four SDK-buildable modules.

## Why it is not built here

A SystemUI replacement is privileged: it replaces `com.android.systemui` and
needs `Android.bp`, platform-private framework classes, and a custom system
image. None of those exist on this machine:

- `android-16/` has no `build/` (Soong), no `prebuilts/`, no `out/`
- a full sync plus build needs ~200 GB; the project drive has ~31 GB free
- the probed AOSP Cuttlefish CI artifacts return 404

Installing the APK produced from these sources with `adb install` is not a valid
substitute — it would not be the system UI, and a malformed manifest failure
there says nothing about the module.

## What is verified

Nothing in this module. Do not report SystemUI behaviour, latency, or memory
figures as measured. Any such number in a deliverable is projected.
