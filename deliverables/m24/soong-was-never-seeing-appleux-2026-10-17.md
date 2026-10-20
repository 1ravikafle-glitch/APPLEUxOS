# m24 — Soong could never see a single AppleUX module, 2026-10-17

Priority order: performance > stability > iOS 27 design. This is a **stability**
entry, and it is the most important one in the project, because it invalidates
several earlier "it builds" claims.

## The finding

`out/.module_paths/Android.bp.list` is the list Soong scans for `Android.bp`
files. It contains **14,149 module paths and zero AppleUX entries.**

Every AppleUX module was invisible to the build.

## Root cause

Soong's module finder reads each directory entry's `d_type` straight out of
`getdents` and never descends into a symlinked directory:

```
build/soong/finder/fs/readdir.go:210   case DT_LNK: mode = os.ModeSymlink
build/soong/finder/fs/fs.go:89          func (d *dirEntryInfo) IsDir() bool
                                           { return d.mode.IsDir() }
```

The layout was:

```
appleux/design-system/                 real directory
android-16/frameworks/appleux -> ../../appleux/design-system    symlink
```

`d_type` for `frameworks/appleux` is `DT_LNK`, so `IsDir()` is false and the
walker never enters it. No `Android.bp` is found, no module is defined, and any
reference to `appleux-design-system` by name fails to resolve.

`scripts/check-single-source.sh` had been asserting this broken layout as
*correct* — it required every module to be a symlink into the tree, "because
a copy is how we ended up with a 123-line SystemUI stub". The guard was
protecting against the wrong failure while guaranteeing this one.

## What that invalidates

| Claim | Status |
|---|---|
| "All five SDK-buildable modules compile … `out/emulator/apks/*.apk`" | **True, but only of the SDK route.** The SDK route drives `aapt2`/`kotlinc`/`d8` directly and never consults Soong, so it proved nothing about the Soong graph. |
| "AOSP/Soong route: whole-tree analysis passes … `m -j1 nothing`" | **True but hollow.** `m nothing` runs configuration only. It never resolves a module. |
| "The AppleUX modules are built through `m -j1 AppleUXLauncher AppleUXSettings AppleUXSystemUI`" | **Never ran successfully.** `AppleUXLauncher` depends on `appleux-design-system`, which did not exist as a module. |
| "AppleUX system-core packaging lives in `appleux.mk`" | The makefile was fine. Nothing it named resolved. |

`m -j1 AppleUXLauncher` would have failed with
`depends on undefined module "appleux-design-system"` — the same class of error
this session's first graph check produced for `appleux-systemui-chrome`.

## The fix

Canonical sources are **real directories inside the tree**:

```
android-16/frameworks/appleux/design-system/
android-16/frameworks/appleux/performance/
android-16/frameworks/appleux/launcher/
android-16/frameworks/appleux/settings/
android-16/frameworks/appleux/sounds/
android-16/frameworks/appleux/systemui/chrome/
android-16/hardware/xiaomi-appleux/rock/
```

and `appleux/<module>` / `device-support/rock` are symlinks **back** into those
directories. One copy of every byte, every pre-existing path still resolves,
and Soong can find them.

`check-single-source.sh` was rewritten to assert the new invariant in both
directions: the tree copies must be real directories, and the historical paths
must resolve to them. Section 5 lost its `com.appleux.systemui` privileged
assertion because that module no longer exists.

`.gitignore` needed negations. `android-16/` is ignored wholesale, so without
them the tracked `appleux/*` paths became symlinks pointing at untracked files:
**the bytes would have existed on disk and in no commit at all.** Verified
after staging — 1,793 real source files, 0 untracked.

`android-16/device/xiaomi/rock` is itself a git repository, so the RRO work is
versioned there. Staged there separately.

## Two more latent breakages the same check exposed

Both had been sitting in files Soong had never parsed.

### `android.bp` syntax error in the sounds module

```
frameworks/appleux/sounds/Android.bp:5:32: expected ":", found String
    default_applicable_licenses": ["external_appleux_license"],
```

A stray quote after the property name. The module had therefore never been
parsed; it existed only to be un-buildable. Nothing referenced it, so nothing
failed until the whole tree was analysed. Rewritten with a real license
(`Android-Apache-2.0`) and as an `android_library` — `SoundRegistry.kt` resolves
ids to raw resources, so code + res is right and an `android_app` would need an
`<application>` tag PackageManager refuses for a priv-app.

### `kotlin: { enabled: true }` does not exist in this tree

Seven files declared it. It is not a property on `android_library` or
`android_app` in AOSP 16 — `grep -rln "kotlin: {" --include=Android.bp` across
the entire tree returns **only those seven AppleUX files**. Soong picks Kotlin
up from `.kt` entries in `srcs`; AOSP's own mixed modules (e.g.
`SystemUI-application`) list `src/**/*.kt` and set `kotlincflags`.

Removed from all seven, each with a comment saying why.

## How this was found, and why that matters for the method

`m -j1 nothing` — graph analysis only, compiles nothing. It
resolves every module name, every `static_libs` edge and every `resource_dirs`
entry in the tree, and fails in ~40 seconds instead of after a multi-hour
compile.

It found four separate defects in four fast iterations:

```
pass 1  42s   "SystemUI-application" depends on undefined module "appleux-systemui-chrome"
pass 2   4s   frameworks/appleux/sounds/Android.bp:5:32: expected ":", found String
pass 3  10s   unrecognized property "kotlin.enabled"  x7
pass 4  ...   analysis proceeding
```

**Run this after every build-file change.** A full `m` is hours; this is
seconds, and it is the only thing in the project that can see the Soong graph
at all.

## Also fixed: the SDK route was missing a dependency

`appleux-design-system` declares `static_libs: ["appleux-device-support-rock"]`,
so `device-support/rock/src/main/java` is a hard requirement of the Kotlin
compile. It was **absent from the SDK route's source list** and only appeared to
work because a stale `RockDisplayProfile.class` was lying around in `out/gen`.

It surfaced the moment the move stopped the stale artifact from masking it:
`AppleuxDisplayInsets.kt` imports `com.appleux.device.rock.RockDisplayProfile`
and could not compile without it. `ROCK_SRC` is now added to every module that
compiles `DS_SRC`.

## Verified

```
bash scripts/build-emulator-apks.sh
  ==> design-system ==> performance ==> launcher ==> settings
  BUILD: clean, 4 APKs

bash scripts/check-single-source.sh
  single-source-tree checks passed

m -j1 nothing
  blueprint bootstrap OK; whole-tree analysis running (see §Not verified)
```

## Not verified

`m SystemUI` and `m AppleUXLauncher` have **still** not completed. Graph
analysis was running when this was written. Until it finishes, the claim is
narrow and precise: *the modules are now visible to Soong*, not *the modules
build*.


## Correction, 2026-10-17

The graph-only command printed above is **wrong** and fails:

```
error: ninja: unknown target 'out/soong/build.ninja'
```

This tree has no `build.ninja`; every file is suffixed with the product name
(`build.aosp_rock.ninja`, `combined-aosp_rock.ninja`). Use `m -j1 nothing`, or
drive ninja with `-f out/combined-aosp_rock.ninja`. Fixed in AGENTS.md.
