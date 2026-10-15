# Phase 1: Storage Report

## Current Usage

```
Filesystem      Size  Used Avail Use% Mounted on
/dev/nvme0n1p3  137G  106G   31G  78% /media/elfakiris/ElfakIris1
```

## Project Disk Usage

```
Directory                    Size
tools/                       ~350 MB  (JDK 21 + repo)
android-sdk/                 ~150 MB  (cmdline tools)
cuttlefish/source/           ~70 MB   (git repo)
android-16/                   19 MB   (.repo init only)
appleux/                     ~10 KB  (design system skeleton)
deliverables/                ~5 KB
---------------------------------------------
Total                        ~590 MB
```

## Storage Budget Analysis

### Available: 31 GB (after SDK, emulator, and dependency closure)

### AOSP Source Estimate
- Full AOSP tree (shallow clone, `--depth 1`): ~120-150 GB
- Full AOSP tree (full history): ~250+ GB
- Selective sync (core + SystemUI + Launcher + Settings only): ~30-50 GB
- Selective sync with `--depth 1`: ~15-25 GB

### Build Output Estimate
- Out/target/ (full build): ~100+ GB
- Incremental build output: ~5-10 GB
- Cuttlefish images: ~2-5 GB

### Recommended Strategy

1. **Phase 2 (Audit)**: Download only AOSP manifest + selectively inspect projects. Target: ~2 GB.
2. **Phase 3-9 (Development)**: Download selective projects with `--depth 1`. Target: ~15-25 GB for source.
3. **Phase 10-20 (Builds)**: Use remote/cloud build infrastructure. Local builds with `-j2` and `tmpfs` swap.
4. **Alternative**: Use a second external drive or cloud VM for full builds.

### Storage Allocation Plan

```
Budget: 42 GB available

├── AOSP source (selective, shallow)    ~20 GB
├── Build outputs (incremental)          ~10 GB
├── Cuttlefish                           ~2 GB
├── SDK + tools                          ~2 GB
├── AppleUX source                       ~2 GB
├── Downloads / references               ~2 GB
├── Buffers / caches                     ~4 GB
= Total                              ~42 GB
```

### Recommendations

- Use `repo sync -c --no-tags --no-clone-bundle` to minimize download
- Use `repo sync -c --depth=1` for shallow clones
- Use `repo sync -- -c` for minimal branch checkout
- Enable `git config --global pack.threads 1` to limit parallel packing
- Use separate build directory on tmpfs or zram-backed swap for compilation
- Consider cloud builds (GitHub Actions, Google Cloud Build) for full compilation
