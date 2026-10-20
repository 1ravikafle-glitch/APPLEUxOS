# Rejected changes -- and why the "obvious" tuning advice does not apply here

Companion to `notes/baseline-evidence.md` section 5. This file is a record of
things that were considered and deliberately left out. It exists so the
patch set can be audited for *omissions* as well as additions, and so
nobody re-adds one of these later without reading why it was refused.

Two different kinds of rejection appear here:

**(A) Forbidden by the project charter.** These would produce misleading
benchmarks or a worse device. They are excluded on principle, regardless of
whether the source suggests they "work".

**(B) Rejected on evidence.** The kernel source for this exact tree, at this
exact commit, says the change would be a no-op, or is already the case, or
is not safely determinable without hardware.

---

## (A) Forbidden by the charter -- do not add these, ever

**Thermal throttling changes.** No trip-point edits, no governor swaps, no
DT thermal overrides, no raising of throttle thresholds -- not even "just for
a benchmark run".

Why: throttling is a thermal-safety control, not a performance defect. A
kernel measured with throttling weakened produces numbers that do not
describe the device in a hand. The Helio G99 sustains load long enough for a
sustained-throughput test to reach steady-state thermal behaviour; a
throttle-free benchmark therefore reports a peak that the device can hold for
seconds, not minutes. It also transfers real heat into the user's battery and
chassis, and on repeated runs can force *more* aggressive throttling later,
producing exactly the inconsistent numbers that make kernel work untrustworthy.

If a measurement appears anomalous, the correct response is to report it as
an anomaly. The correct response is never to remove the throttle to make the
graph look better.

**Lowmemorykiller / lmkd / ADJ weakening.** No disabling, no raising of
kill thresholds, no extending of the "critical" kill floor, no
`minfree` softening.

Why: on a 4 GB device with a large Android userspace, aggressive reclaim and
process killing is what keeps the device alive at all. Softening it to make a
memory number look better converts a smooth degradation into an OOM kill or a
LMK-driven app restart experienced by an actual user. Memory numbers taken
with lmk relaxed are not comparable to numbers taken with it active, and they
describe a device that does not exist.

**Forcing maximum clocks.** No performance governor, no cpufreq pinning to
the top bin, no disabling of CPU DVFS, no `scaling_governor=performance`, no
locking the little cores high.

Why: a clock-pinned benchmark measures a part with no battery, no skin
temperature and no thermal ceiling. Every number taken that way is wrong
about the device, and the divergence grows with load duration -- exactly where
a kernel patch's effect would be most visible, so it is also the measurement
most likely to manufacture a false win.

The correct practice for any frequency-related measurement is to *report* the
frequency distribution alongside the result, not to pin it.

---

## (B) Rejected on source evidence

Each of these is a change a generic kernel-tuning guide would recommend.
On this tree at commit `9b1c9a53`, the source says otherwise. Full greps and
file:line references are in `notes/baseline-evidence.md` section 5.

| Change | Why not |
|---|---|
| Enable KSM | Not selected by anything here; Android app processes share little page content, so KSM spends scan CPU for negligible reclaim. |
| Enable Transparent HugePages | Already resolves to `n` (no `default` in `mm/Kconfig`, absent from both defconfigs). THP compaction stalls cause latency spikes on mobile. |
| Enable ZSWAP | `default n`, and ZRAM (`CONFIG_ZRAM=y`) already compresses the anon pages it would sit in front of. Second tier, pure CPU cost. |
| zram writeback | `default n` by design; would add eMMC writes and wear. P04 pins it off rather than enabling it. |
| `CONFIG_CMA_SIZE_MBYTES=...` | **Symbol does not exist in this tree** (`grep -c CMA_SIZE_MBYTES mm/Kconfig` = 0). A tuning guide's CMA patch is a silent no-op here; CMA is sized via DTS/bootargs. |
| BFQ | BLK-MQ-only in this Kconfig, and MSDC's use of blk-mq could not be confirmed. See the gate command in P01. |
| Kyber | Same gate. Worth re-evaluating only if MSDC blk-mq is confirmed. |
| mq-deadline | Same gate. |
| Strip ftrace / kprobes | `CONFIG_KPROBES=y`; atrace and perfetto depend on these. Stripping breaks system tracing for a small size win. |
| 16 KB page size | A platform-level porting change affecting userland and all vendor modules, not a config fragment. Must not be attempted blind. |
| NUMA | Not enabled; MT6765 is a single memory-controller SoC, there is no second node. |
| Strip debug options (KASAN, DEBUG_VM, SLUB_DEBUG, PROVE_LOCKING, ...) | Already absent/off in this baseline. Nothing to strip. Listed so it is clear they were checked, not overlooked. |
| Replace/disable MTK-Oplus dynamic readahead | `CONFIG_OPLUS_DYNAMIC_READAHEAD=y` is an existing adaptive mechanism; second-guessing it from a config fragment is unverifiable here. |
| Anything touching `vendor/oplus/.../healthinfo/` | MTK/Oplus instrumentation wired in via the `block/blk` symlink. Out of scope, not assessable without hardware. |

---

## The honest summary of what is left

Six patches, of which:

* **P01, P02, P04** rest on baseline facts that are corroborated in two
  independent MT6765 defconfigs and are structural (a scheduler default, a
  CPU count, a set of compiled-in governors). These are the most defensible.
* **P03** is a small, low-risk removal of a desktop feature Android does not
  use.
* **P06** is gated on a grep and may legitimately be dropped.
* **P05** is expected to be painful to build and may need to be dropped for
  vendor-module link failures.

That distribution is the correct one. A patch set that claims uniform
confidence across six changes on a device nobody has booted is not being
honest with itself. The value here is the reasoning and the measurement
plan; the patches are hypotheses that get to be wrong.
