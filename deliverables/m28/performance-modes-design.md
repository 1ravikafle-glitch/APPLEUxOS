# AppleUX Performance Modes — Design (m28)

**Status:** DESIGN ONLY. Not implemented. Blocks on kernel/driver work
(CPUFreq governor, GPU DVFS, thermal zones) which is not started yet.
The existing `appleux/performance/` Adaptive Performance Controller
(Q100→Q70 quality tiers) is a *separate, already-shipped* system and is
NOT replaced by this.

**Why separate:** the current controller degrades *rendering quality*
(nothing is overclocked, nothing is underclocked). This design manages
*hardware power state*. They compose: Auto mode picks a hardware ceiling,
the quality controller degrades gracefully underneath it.

## 1. Three user-facing modes

| Mode | Behaviour | Helio G99 notes |
|---|---|---|
| **Performance** | Higher CPU/GPU frequency ceilings, stronger short-term boost. Thermal protection retained. | Caps only if kernel exposes them AND stability testing passes. |
| **Normal** (default) | Stock-supported frequency ranges, normal Android scheduling, automatic DVFS + thermal. | This is effectively today's behaviour. |
| **Auto** | Dynamically adapts to load, temperature, battery, screen state. | Adjusts *limits and boost behaviour*, never pins fixed clocks. |

Invariant for ALL modes: kernel DVFS, voltage control and thermal safeguards
stay enabled. Auto never forces fixed CPU/GPU clocks — it adjusts the
*supported frequency limits* and boost aggressiveness, so the governor
retains freedom inside the envelope.

## 2. Battery Saver — an automatic override, not a fourth mode

Battery Saver is a **temporary power-saving override** that can activate on
top of any of the three modes. It is a separate Control Center toggle.

Activation triggers (any):

| Trigger | Behaviour |
|---|---|
| Battery low (configurable, default **20%**) | Activate automatically |
| User enables Battery Saver manually | Activate immediately |
| Screen locked | Enter **idle power-saving state** (see §2.1) |
| Device sleep / suspend | CPU/GPU enter lowest supported power states |
| Charging, or charge rises above recovery threshold | Restore the selected mode |
| Device hot | Reduce performance automatically for thermal safety |

Manual Battery Saver **takes priority** over Auto's decisions while active.

### 2.1 Idle-on-lock is NOT full Battery Saver

Critical distinction: **locking the screen triggers an idle policy, not the
full Battery Saver profile.** Must keep working while idle: calls, mobile
data, notifications, alarms, music playback, background tasks. The system
reduces clocks when idle and suspends hardware only when Android deems it
safe — it must not gate on Battery Saver being enabled.

### 2.2 Auto mode decision logic

```
Monitor: CPU/GPU load · temperature · battery · screen state
   │
   ├─ Battery Saver requested OR battery critically low?
   │     YES → Power saving: lower frequency ceilings, RETAIN essential services
   │     NO  ↓
   └─ Adapt to load: boost for demanding work, downclock when idle
```

## 3. Control Center surface

- Selector: **Performance | Normal | Auto**
- Separate **Battery Saver** toggle (independent of the mode)
- Manual Battery Saver wins; otherwise Auto responds to conditions

## 4. Implementation boundaries (for when kernel work starts)

- **Kernel side (NOT ours yet):** CPUFreq governor limits, GPU DVFS
  ceiling, thermal zone trip points, per-idle-state suspend. These decide
  what a "frequency ceiling" can even be set to on the Helio G99 (MT6789:
  2× Cortex-A76 @2.2 GHz + 6× A55 @2.0 GHz, Mali-G57 MC2).
- **AppleUX side (this ROM):** the policy engine that reads the monitors in
  `appleux/performance/` (PressureMonitor, FrameTimeMonitor), applies the
  mode's ceiling, and decides when Battery Saver override or idle-on-lock
  applies.
- `AppleuxRuntimeConfig` is the persistence/transport layer (signature-guarded
  `AppleuxConfigProvider`) — the mode and Battery Saver toggle are new keys
  there, readable cross-process.

## 5. Non-goals / cautions

- No fixed-clock pinning.
- No behaviour that breaks calls/alarms/background work while idle.
- No claim of measured device performance: this is design until the kernel
  interface exists. Do not quote numbers from it as results.
