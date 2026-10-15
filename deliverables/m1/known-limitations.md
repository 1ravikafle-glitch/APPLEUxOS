# Phase 1: Known Limitations

## Environment Limitations

1. **No sudo access**: Cannot install system packages via apt. JDK had to be downloaded locally.
2. **42 GB disk space**: Insufficient for full AOSP source (~200+ GB) or full build output. Requires selective sync, shallow clones, and cloud builds.
3. **8 GB RAM**: Below recommended 16+ GB for full AOSP compilation. Must use low parallelism (`-j2`).
4. **No Docker/Podman**: Cannot use containerized builds. Cloud build must be done via remote CI/CD.

## Development Constraints

1. **No physical device**: All testing must initially use Cuttlefish until physical hardware is available. Cuttlefish results must not be presented as physical-hardware results.
2. **Disk-bound**: Cannot perform full `repo sync` of AOSP. Must use selective project sync.
3. **Build time**: Full Android builds may require cloud VM (16+ GB RAM, 200+ GB disk).

## AOSP Access

1. **No local full tree**: Cannot run `make` or `mmm` for arbitrary AOSP targets until source is synced.
2. **Selective sync only**: Can only work with projects that are downloaded. Must manage disk usage carefully.

## Mitigation Strategies

1. Use `repo sync -c --depth=1 --no-tags` for minimal clones
2. Use `repo sync <project>` for selective project downloads
3. Use cloud VMs for full builds when needed
4. Profile and measure everything — no optimizations without data
5. Use Cuttlefish for initial testing; validate on physical hardware later

## Hardware Assumptions (for target device)

- The target 3 GB RAM / 32 GB eMMC device is NOT available for testing yet
- All performance numbers from Cuttlefish must be treated as approximate
- Physical hardware tests deferred to Phase 17
