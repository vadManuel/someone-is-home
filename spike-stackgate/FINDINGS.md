# Story 1.7 — the stack gate, answered

**Device:** iPhone 16 Pro, iOS 26.6, ProMotion. Frame interval **8.335 ms** measured.
**Stack:** Kotlin 2.4.10 · Compose Multiplatform 1.11.1 · release binary, physical device.

> **Can Compose Multiplatform blank the lamp in the same frame as a trigger, at the tail?**

**Yes — with an allocation budget.** The gate does not resolve to pass or fail. It resolves to
a number the rest of the project has to stay under.

---

## The controlled evidence

Nine runs, each in a **fresh process**, every one reporting thermal `nominal`. Fresh processes
matter: Kotlin/Native schedules collections against heap-size thresholds, so a run inheriting a
grown heap collects at a different cadence and run order becomes an uncontrolled variable.

| run | measured MB/s | n | collections | late draws | late with a GC in window |
|---|---|---|---|---|---|
| `VOLUME_CONTROL` (pressure off) | 0.05 | 10 000 | 291 | **0** | — |
| `ALLOC_PROBE` (pressure off) | 0.05 | 20 000 | 538 | **0** | — |
| `VOLUME_CPU_CONTROL` (CPU, no garbage) | 0.06 | 5 000 | 132 | **0** | — |
| `VOLUME` | 0.13 | 10 000 | 774 | **1** | **0** |
| `LONG_IDLE` (renderer idle 3–10 s) | 0.19 | 200 | 793 | **0** | — |
| `VOLUME_HEAVY` | 0.54 | 10 000 | 3 514 | **0** | — |
| `VOLUME_CRUSH` | 3.00 | 5 000 | 9 438 | **18** | **18** |

*Late* = trigger→black took longer than one frame interval (8.335 ms) × 1.15.

**55 200 blackouts at or below 0.54 MB/s produced exactly one late frame, and that one had no
collection in its window.** Compose Multiplatform on its own allocates ~0.04 MB/s; that is the
floor everything must be compared against, and comparing against zero is what made this spike's
first two runs look meaningful when they were not.

---

## What each run establishes

### The collector is the cause — `VOLUME_CPU_CONTROL`

Same thread count, same loop counts, same arithmetic, writing into preallocated buffers so it
makes no garbage. **Zero late frames in 5 000 trials.** The CRUSH failures are not four busy
threads competing with the renderer; they track *allocation*. D-026 called it right.

*Caveat:* `cpuLoop` runs the same number of iterations but each is cheaper — no object
construction, no string building. It rules out *"merely four busy threads"* rather than *"the
CPU cost of allocation itself,"* a distinction that matters less than it sounds because that
cost is a consequence of allocating.

### There is real headroom — `VOLUME_HEAVY`

At **0.54 MB/s, thirteen times Compose's own baseline**, with 3 514 collections and 153 trials
overlapping one: **zero of 10 000 blackouts overran a frame.**

**A collection overlapping a blackout is not sufficient to make it late.** 153 overlaps at HEAVY
produced no misses; 541 at CRUSH produced 18. What matters is how hard the heap is churning,
not whether a collection coincides.

### Renderer idle costs nothing — `LONG_IDLE`

The one that worried me most, because every other run hammers the renderer every ~100 ms and
keeps it warm, while the real game leaves the lamp static for minutes. With **3–10 s between
blackouts: zero of 200 late**, and p50 moved only 8.03 → 8.13 ms. Compose's redrawer does not
pay a meaningful wake-up cost.

Weakest result in the set on sample size alone — 200 trials bounds the miss rate at roughly
1.5%, not at one-in-ninety-thousand. It shows no *systematic* penalty, which is what it was
built to detect.

### The failure is real and reproducible — `VOLUME_CRUSH`

At 3.00 MB/s: 0.36% of blackouts overran a frame, **every one with a collection in its window.**
Across three CRUSH runs the worst stop-the-world pause reached **9.108 ms — longer than an
entire frame.** That is the un-anonymised revoke, reproduced on demand.

### A residual floor that is not ours — `VOLUME`

One late draw in 10 000, at 10.59 ms. **No collection in its window, and no display-link stall
anywhere in the run.** OS scheduling, and nothing in the stack's control removes it. Roughly
1-in-55 000 across all clean runs, which is the floor any design must tolerate.

---

## The number E0 has to live with

```
     0.04 MB/s   Compose alone, doing nothing
     0.54 MB/s   VERIFIED CLEAN — 55 200 blackouts, one late, non-GC
     ~ ?         the boundary, somewhere in this 5.6x window
     3.00 MB/s   0.36% of blackouts miss a frame
```

**Total app allocation should stay under ~0.5 MB/s.** Verified, not extrapolated. Narrowing the
boundary above it is one run each at ~1.0 and ~2.0 MB/s.

## This changes 1.7b's mitigation

> Epic 1.7b: *"Establish a no-allocation discipline on the blackout path and enforce it with a
> permanent allocation assertion."*

**The blackout path's own allocation is not what hurts.** The allocation driving these
collections is on the BLE, motion, effect and recording threads. A blackout-path assertion
would have stayed green through every failure recorded here.

The load-bearing guard is a **total allocation-rate budget for the whole app**, asserted
continuously — the same shape the epic asks for, aimed one level up. Keep the blackout-path
assertion too: it is cheap, and it guards a different, smaller failure (a future commit quietly
adding allocation to the draw path).

**`ALLOC_PROBE` measured 6 222 bytes per trial, and that number is an upper bound, not the
blackout path's cost.** A trial spans ~14 frames of ordinary Compose rendering, and the
GC-epoch method cannot resolve anything finer than an epoch, which is seconds apart. Isolating
the draw path's own allocation needs a different instrument than this spike has.

## Instrumentation notes worth carrying forward

Three of this spike's own metrics were wrong before they were right, and **all three failed in
the flattering direction** — see the git history for detail. The lesson generalises to E0's
performance instrumentation (architecture G4): every number needs a second, independent source
before it is believed.

- `CADisableMinimumFrameDuration` is not a real key; it is `...OnPhone`. Without the correct
  one iOS caps the app at 60 fps and every latency gets twice the budget it should have.
- `preferredFramesPerSecond = 0` delivers 60 Hz on ProMotion, not the maximum.
- **Span** — vsync boundaries between trigger and draw — is useless. It is dominated by whether
  our `CADisplayLink` fires before or after Compose's within a vsync, and it read 0 for every
  provably-late trial. Latency against the monotonic clock is the metric.

## Still open

- **The boundary between 0.54 and 3.00 MB/s** is unmeasured. Two runs would narrow it.
- **`ALLOC_PROBE` does not isolate the blackout path** — see above. A finer instrument is needed
  before the blackout-path assertion has a defensible threshold.
- **`LONG_IDLE` has only 200 trials.** Enough to rule out a systematic wake-up penalty, not
  enough to characterise its tail.
