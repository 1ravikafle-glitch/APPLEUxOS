# VALIDATION.md -- how to find out whether any of this is true

**Nothing in `patches/` has been built, booted or measured.** This document
describes the measurements required to move a change from
`EXPERIMENTAL / UNVERIFIED` to verified. Until every step below has been run
on real hardware against a real baseline, every patch in this directory
remains a hypothesis.

Target: POCO M5 / Redmi 11 Prime 4G, Helio G99 (MT6765V), 4 GB RAM.

---

## 0. Before you start -- the prerequisites

This checklist is worthless without these three things. All three are
currently absent.

1. **The device.** A physical POCO M5 or Redmi 11 Prime 4G.
2. **A flashing path.** An unlocked bootloader with `fastboot` access, and a
   known-good recovery path. See section 9 before you flash anything.
3. **A working baseline build.** You cannot measure a delta against nothing.
   Build the unmodified `k65v1_64_bsp_defconfig` baseline first, flash it,
   and collect the entire section-3 measurement battery. Only then apply one
   patch at a time.

**Rule for the whole exercise: one patch at a time.** The series is ordered by
ascending risk in `patches/series`. P01-P04 are low risk and independent.
P05 (LTO) is high risk and must be evaluated last and alone, because a vendor
link failure there is otherwise indistinguishable from a real regression.

---

## 1. Recording provenance on every run

Never trust a remembered measurement. Every run gets a directory and a
metadata file.

```bash
mkdir -p ~/kt-runs
RUN=~/kt-runs/$(date +%Y%m%d-%H%M%S)-<baseline|P01|P02|...>
mkdir -p "$RUN"

cat > "$RUN/meta.txt" <<'EOF'
kernel_commit   : 9b1c9a531dd08e68bf778dcf7ed13f811ec1f173   # or your build
kernel_version  : 4.19.191
patch_applied   : NONE | P01 | P02 | P03 | P04 | P05 | P06
defconfig       : k65v1_64_bsp_defconfig + config/fragments/*.config
rom_build       : <your ROM build id>
build_user      : <who>
EOF

uname -a                        | tee "$RUN/uname.txt"
cat /proc/version               | tee -a "$RUN/uname.txt"
zcat /proc/config.gz > "$RUN/kernel.config" 2>/dev/null || \
  cat /boot/config-$(uname -r)  > "$RUN/kernel.config"
```

`kernel.config` matters more than usual here: the fragments are *proposals*.
Confirm what actually got compiled in rather than assuming.

```bash
# Prove each patch's symbol actually landed:
grep -E 'CONFIG_SCHED_AUTOGROUP|CONFIG_NR_CPUS|CONFIG_LTO_THIN' "$RUN/kernel.config"
grep -E 'CONFIG_CPU_FREQ_GOV_(POWERSAVE|CONSERVATIVE)|CONFIG_DEFAULT_IOSCHED' "$RUN/kernel.config"
```

If a symbol you expected to change did not change, stop: the measurement is
of a different kernel than you think it is.

---

## 2. Boot sanity and proof the patch took effect

```bash
# --- P01: I/O scheduler actually switched ---
cat /sys/block/sda/queue/scheduler     # expect [deadline] with brackets
# Find the real eMMC/UFS device first if it is not sda:
ls /sys/block/ | grep -E 'mmcblk|sda'
cat /sys/block/mmcblk0/queue/scheduler

# --- P02: NR_CPUS took effect ---
cat /sys/devices/system/cpu/possible   # expect 0-7
cat /sys/devices/system/cpu/present    # expect 0-7
cat /proc/cpuinfo | grep -c processor  # expect 8

# --- P03: autogroup gone ---
cat /sys/kernel/debug/sched/debug_locks 2>/dev/null | head   # optional
dmesg | grep -i autogroup

# --- P04: governors removed, schedutil intact ---
for p in /sys/devices/system/cpu/cpufreq/policy*; do
    echo "$p: default=$(cat $p/scaling_governor 2>/dev/null) \
avail=[$(cat $p/scaling_available_governors 2>/dev/null)]"
done
# Expect: powersave and conservative absent; performance and schedutil present.
# If 'default' is NOT schedutil, STOP -- P04 changed behaviour. Revert it.

# --- P05: LTO engaged (proof, not assumption) ---
ls -l arch/arm64/boot/Image                      # on the build host
strings /proc/kcore >/dev/null 2>&1             # too slow; instead:
cat "$RUN/kernel.config" | grep -E 'CONFIG_LTO|CONFIG_CFI'
# On device, the real proof is boot success plus a smaller Image.

# --- P06: tracers gone ---
cat "$RUN/kernel.config" | grep MTK_SCHED_TRACERS
dmesg | grep -iE 'sched.*tracer' | head
```

`/sys/devices/system/cpu/possible` is the single most important check in this
section. If P02 is wrong, it is wrong there, loudly.

---

## 3. Memory -- `dumpsys meminfo`

The headline metric for a 4 GB device. Measure it in a **steady state you
can reproduce**, not on a fresh boot.

```bash
# Full system state
adb shell dumpsys meminfo > "$RUN/meminfo-system.txt"

# A specific package, with the process freshly started
adb shell am force-stop com.android.settings
adb shell am start -n com.android.settings/.Settings > /dev/null
sleep 10
adb shell dumpsys meminfo com.android.settings > "$RUN/meminfo-settings.txt"

# The lines that matter:
grep -E 'Total RAM|Free RAM|Used RAM|Lost RAM|TOTAL|GLIBC|Heap|Native Heap|Java Heap'
```

**Reproducible steady state for a memory comparison:**

```bash
# Fixed app set, fixed order, fixed wait. Same every run, baseline and patched.
for p in com.android.settings com.android.contacts com.android.deskclock; do
  adb shell am force-stop $p
  adb shell monkey -p $p -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 15
done
adb shell dumpsys meminfo > "$RUN/meminfo-steadystate.txt"
```

Run it at least 5 times per build and compare **medians**, not single
samples. Memory measurements are noisy; a single reading that looks like a win
is more often noise.

**What would confirm P02:** `/proc/iomem` shows a smaller percpu pool, and
`grep -i percpu /proc/slabinfo` shows fewer/smaller per-CPU slabs.

```bash
adb shell cat /proc/iomem | head -30        | tee "$RUN/iomem.txt"
adb shell cat /proc/slabinfo | grep -i percpu | tee "$RUN/slabinfo-percpu.txt"
# P02 confirmation: per-CPU row count drops to 8 CPUs' worth.
adb shell cat /sys/kernel/debug/sched/schedstats | wc -l   # expect 8-ish rows
```

**Refutation:** if `/proc/iomem` percpu pool does not shrink between baseline
and patched, P02's stated mechanism is wrong and the patch should be dropped.

---

## 4. Frame rendering -- `dumpsys gfxinfo <pkg> framestats`

```bash
adb shell dumpsys gfxinfo com.android.launcher framestats > "$RUN/gfx-launcher.txt"
adb shell dumpsys gfxinfo com.android.systemui framestats > "$RUN/gfx-systemui.txt"
```

Metrics to read:

```bash
grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync' "$RUN/gfx-launcher.txt"
```

**Reproducible workload.** Do not measure the launcher "wherever it happens
to be". Drive an identical interaction every run:

```bash
# 60s of identical scrolling, same app, same route
adb shell am force-stop com.android.settings
adb shell am start -n com.android.settings/.Settings >/dev/null; sleep 5
# then perform THE SAME scripted gesture set on EVERY build,
# e.g. 20 x swipe up, 20 x swipe down, 10 x open-submenu
adb shell dumpsys gfxinfo com.android.settings framestats > "$RUN/gfx.txt"
```

Compare `50th/90th/95th/99th percentile` and `Janky frames`. Median frame
time alone hides tail problems, which is where a big.LITTLE device actually
misbehaves.

**Confirms P03/P06 (scheduler-path removals):** a change in the 95th/99th
percentile or in jank count under the same workload.
**Refutes them:** p95 identical across 5+ runs. Small removals often will not
move this -- if so, say so and drop the patch rather than keeping it on faith.

**Also measure CPU accounting**, which is where P03/P06's mechanism would show
up before frame times do:

```bash
adb shell cat /proc/stat | grep '^cpu ' > "$RUN/stat-before.txt"
# ... run the fixed workload ...
adb shell cat /proc/stat | grep '^cpu ' > "$RUN/stat-after.txt"
# user + sys delta during the workload = CPU actually burned doing the work
```

---

## 5. Boot time -- `logcat boot_progress`

```bash
adb logcat -b all -c
adb reboot
# wait for boot, then:
adb logcat -b all -d -s boot_progress > "$RUN/boot_progress.txt"
adb shell getprop sys.boot_completed
```

Extract the `BOOT_PROGRESS_` markers and their timestamps:

```bash
grep -oE 'BOOT_PROGRESS_[A-Z_]+.*[0-9]{4,}' "$RUN/boot_progress.txt"
# or millisecond deltas between consecutive markers:
grep -oE '[0-9]{13}' "$RUN/boot_progress.txt" | awk 'NR>1{print $1-p} {p=$1}'
```

**Procedure:** at least **10 cold boots per build**, alternating baseline and
patched to cancel out thermal drift and background noise. Discard the first
boot after flashing (first-boot work differs). Compare **p50 and p95**, not
the mean.

**Confirms P05 (LTO):** smaller `Image` (`ls -l arch/arm64/boot/Image` on the
build host -- this is the cheapest, most certain test and should be run
first), plus any change in boot percentiles. If `Image` does not shrink, LTO
did not engage and no other result is meaningful.

**Also capture, for context:**

```bash
adb shell 'cat /proc/uptime'                       # total boot to now
adb shell dmesg | grep -iE 'Booting|freeing init|Kernel command line'
adb shell dmesg | grep -ciE 'thermal|throttl'     # sanity: throttling still present
```

That last command is a guard. **If throttling behaves differently between
builds, the comparison is void** -- see `REJECTED.md` (A).

---

## 6. Pressure stall information -- `/proc/pressure/{cpu,memory,io}`

Modern, cheap, and hard to argue with. Read `some avg10/60/300`.

```bash
for r in cpu memory io; do
  echo "== $r"; adb shell cat /proc/pressure/$r
done | tee "$RUN/pressure-idle.txt"
```

Three states, all recorded:

1. **Idle** (screen off, nothing running, 10 min settled) -- the battery case.
2. **Interactive** -- during the fixed workload from section 4.
3. **Memory pressure** -- under a controlled allocation load, which is the
   case that matters on 4 GB.

```bash
# memory-pressure state, reproducible
adb shell 'while true; do cat /dev/zero > /dev/shm/kt_fill 2>/dev/null; sleep 0.2; done' &
FILL=$!; sleep 300
for r in cpu memory io; do adb shell cat /proc/pressure/$r; done | tee "$RUN/pressure-memstress.txt"
kill $FILL; adb shell rm -f /dev/shm/kt_fill
```

`io` `some` under the memory-pressure state is the most sensitive single
indicator for P01 (CFQ -> deadline) on this platform, because it captures how
long tasks stall on storage rather than how many IOPS you got.

---

## 7. Thermal readings

Recording these is not optional; a patch that changes behaviour must be shown
not to have changed the thermal picture.

```bash
for z in /sys/class/thermal/thermal_zone*; do
  echo "$(cat $z/type 2>/dev/null): $(cat $z/temp 2>/dev/null)"
done | tee "$RUN/thermal-idle.txt"

# under load
watch -n 2 'for z in /sys/class/thermal/thermal_zone*; do \
  echo "$(cat $z/type): $(cat $z/temp)"; done' > "$RUN/thermal-load.txt" &
sleep 600; kill %1
```

Also capture the actual CPU frequencies, so results can be read honestly:

```bash
for c in 0 1 2 3 4 5 6 7; do
  echo "cpu$c: $(cat /sys/devices/system/cpu/cpu$c/cpufreq/scaling_cur_freq 2>/dev/null)"
done | tee "$RUN/freqs.txt"
```

**Interpretation rule:** if the patched build's thermal or frequency profile
differs materially from baseline, a performance difference is **not
attributable to the patch** -- it is attributable to a different thermal
condition. Fix the methodology (same ambient, same cooldown, same charge
level, same wait) before drawing any conclusion. Do not correct it by
removing throttling.

---

## 8. Storage I/O, and battery/idle drain

### 8a. Storage throughput and CPU cost -- P01

On-device `dd` (available everywhere, low precision):

```bash
adb shell 'sync; dd if=/dev/zero of=/data/local/tmp/kt_test bs=4k count=65536 conv=fdatasync 2>&1 | tail -1'
adb shell 'dd if=/data/local/tmp/kt_test of=/dev/null bs=4k 2>&1 | tail -1'
adb shell rm -f /data/local/tmp/kt_test
```

`dd` is too coarse to judge a scheduler change. Use **fio** if you can push it
(`fio` static arm64 binary via `adb push`), 4k randread + randwrite, and
record **both** IOPS and the CPU time consumed:

```bash
adb shell 'cat /proc/stat | grep ^cpu' > "$RUN/stat-io-before.txt"
adb shell 'fio --name=kr --filename=/data/local/tmp/kt --rw=randread \
  --bs=4k --size=1G --iodepth=32 --runtime=60 --time_based --group_reporting' \
  > "$RUN/fio-randread.txt"
adb shell 'cat /proc/stat | grep ^cpu' > "$RUN/stat-io-after.txt"
```

**The decision rule for P01 is the CPU delta, not the IOPS.** P01's rationale
claims deadline does less per-dispatch bookkeeping than CFQ. If IOPS is
unchanged and CPU time is unchanged, the claim is refuted and the patch goes.
If IOPS is unchanged but CPU time drops, it worked as described.

### 8b. Battery / idle drain -- P04 and generally

This is the slowest and least controlled measurement in the entire exercise.
Treat any single night as noise.

**Conditions -- all of these must be identical between runs:**

- Same ROM build, same kernel build slot.
- Battery **health** recorded beforehand (`adb shell dumpsys battery`).
- Screen brightness fixed (use `adb shell settings put system screen_brightness 100`).
- Same network state: for an idle test, **airplane mode ON**. Cell/Wi-Fi
  idle-state radio activity will swamp anything the kernel does.
- Same charging state, same ambient temperature, same time of night.
- Reboot and let settle 30 min before starting the measurement window.
- Nothing else touching the device. No sync, no backup, no updates.

**Procedure -- 3 nights per build, alternating builds to cancel drift:**

```bash
# 1. record start
adb shell dumpsys battery > "$RUN/battery-start.txt"

# 2. 8h idle soak in airplane mode, screen off
adb shell 'settings put global airplane_mode_on 1; svc wifi disable; svc data disable'
sleep 28800

# 3. record end
adb shell dumpsys battery > "$RUN/battery-end.txt"

# 4. also capture drain attributed to the kernel's own idle path:
adb shell 'cat /proc/interrupts | grep -iE "irq [0-9]+:.*(cpuidle|deep|psc)"' \
  | tee "$RUN/idle-irqs.txt"
adb shell cat /proc/stat | grep '^cpu ' | tee "$RUN/stat-idle.txt"
```

**Compare:** `mAh` consumed over the identical 8h window. Accept a
difference only if it repeats across all 3 nights and exceeds the
night-to-night spread of the baseline. **A 1-2% overnight difference is inside
the noise floor of this measurement on a 4 GB phone and must be reported as
"no detectable change".** Claiming a battery win on that basis is a false
positive.

P04's own honest framing (in its header) is that it mostly removes an
unintended escape hatch rather than saving measurable power. If the drain is
flat, that is the expected result and is not a failure -- but it should be
reported as "no detectable change", not as "no measurable difference".

---

## 9. Reverting -- do this before you flash

**Establish the escape hatch first.** Do not begin flashing until you have
confirmed you can get back. A device that cannot be recovered is a lost
device, and none of the patches here are worth that.

### 9a. Before anything: pull the current boot image

```bash
adb reboot bootloader
fastboot devices                       # confirm the device is visible
fastboot getvar current-slot
fastboot fetch boot boot_backup.img     # the partition, from THIS device
fastboot getvar all > fastboot-vars-before.txt   # record unlocked state

# also keep the stock ROM boot.img from the official fastboot package for
# your exact model/region -- do not rely on this adb pull alone
```

Keep `boot_backup.img` and the official stock `boot.img` somewhere that is
not the device. Reverting is the difference between a failed experiment and
a bricked phone.

### 9b. Reverting with fastboot (simplest, recommended)

```bash
adb reboot bootloader
fastboot flash boot boot_backup.img      # your own previously-working build
# or
fastboot flash boot stock_boot.img      # the official ROM image
fastboot reboot
```

On an A/B device also handle the inactive slot:

```bash
fastboot --set-active=a   # or _b, whichever boot_backup came from
```

### 9c. Reverting with Magiskboot (for a Magisk-patched boot image)

```bash
# 1. unpack the working image
magiskboot unpack boot_backup.img

# 2. repack it untouched -- this is the point: undoing a Kconfig change does
#    not require touching the ramdisk, you are restoring a whole known-good
#    kernel+ramdisk image
magiskboot repack boot_backup.img stock_boot.img

# 3. flash the repacked image
fastboot flash boot stock_boot.img
fastboot reboot
```

If you used **Kona / KernelSU / AnyKernel3** rather than Magisk for the patch,
revert with the same tool that applied it -- and keep the original boot image
from 9a regardless. `magiskboot unpack/repack` with no modifications between
the two is effectively a no-op round trip, which is exactly what you want.

### 9d. If the device will not boot at all

```bash
adb reboot bootloader        # or: hold vol+ and vol- while powering on
fastboot flash boot boot_backup.img
fastboot reboot
```

If fastboot itself will not engage, that is a hardware-unlock or
edl-mode problem and no kernel patch is going to fix it. Escalate before
touching the device again.

---

## 10. Reporting: what a result is allowed to claim

Filling this in is what converts a patch from UNVERIFIED to verified, and it
is also the fastest way to catch a measurement that went wrong.

| Field | Value |
|---|---|
| Patch ID | P0x |
| Built? | yes / **no** |
| Booted? | yes / **no** |
| Baseline commit | 9b1c9a531dd08e68bf778dcf7ed13f811ec1f173 |
| Patched commit | |
| Symbol confirmed in /proc/config.gz? | yes / no |
| Metric | |
| Baseline (median, n=) | |
| Patched (median, n=) | |
| Delta | |
| Spread / noise floor | |
| Thermal profile identical? | **must be yes, or the result is void** |
| Verdict | confirmed / refuted / **inconclusive** |

Three rules:

1. **"Inconclusive" is a valid result.** Report it. A change that cannot be
   distinguished from noise has not been shown to help, and keeping it in a
   patch set on the strength of a plausible rationale alone is how untested
   tuning accumulates over time.
2. **No number enters any document without a baseline, a sample count and a
   noise floor.** A bare "12% faster" is not a result.
3. **Never fix a surprising measurement by removing throttling, weakening
   lmk, or pinning clocks.** If a patched build is slower, that is a finding,
   and section 7 tells you whether the device was even in a comparable state.

---

## 11. Minimum bar to call this set "verified"

Not all six patches need to pass. The honest outcomes are all acceptable:

- **Some confirmed, some refuted, some inconclusive** -> publish that. This
  is the realistic best case.
- **All inconclusive** -> the correct conclusion is that these changes are
  below the noise floor of the available measurements on a 4 GB device, and
  they should not ship. That is a real finding, not a failure.
- **P05 fails to build** -> drop it, ship the rest. Do not patch around the
  vendor module.

What is *not* an acceptable outcome is shipping any of this as "optimised"
or "performance-improved" on the strength of the written rationale alone.
The rationale is a hypothesis. These commands are what test it.
