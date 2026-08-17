# Story 1.7 — the stack gate, answered

**Device:** iPhone 16 Pro, iOS 26.6, ProMotion. Frame interval **8.335 ms** measured.
**Stack:** Kotlin 2.4.10 · Compose Multiplatform 1.11.1 · release binary, physical device.

> **Can Compose Multiplatform blank the lamp in the same frame as a trigger, at the tail?**

**Yes — with an allocation budget.** The gate does not resolve to pass or fail. It resolves to
a number the rest of the project has to stay under.

---

## The controlled evidence

Four runs, each in a **fresh process**, all reporting thermal `nominal`. Run order and heat are
controlled; earlier runs in `results/` predate the harness and should not be used for
comparison, because they may have shared a process and their order was uncontrolled.

| run | measured MB/s | collections | trials overlapping a collection | late draws | late given overlap |
|---|---|---|---|---|---|
| `VOLUME_CPU_CONTROL` | 0.06 | 132 | 4 (0.1%) | **0** / 5 000 | 0.00% |
| `VOLUME_HEAVY` | 0.54 | 3 514 | 153 (1.5%) | **0** / 10 000 | 0.00% |
| `VOLUME_CRUSH` | 3.00 | 9 438 | 541 (10.8%) | **18** / 5 000 | 3.33% |

*Late* = trigger→black took longer than one whole frame interval × 1.15.

Compose Multiplatform on its own, with the pressure generator off entirely, allocates
**~0.04 MB/s**. That is the floor to compare everything against, and comparing against zero is
what made the first two runs of this spike look meaningful when they were not.

---

## What each run establishes

### The collector is the cause — `VOLUME_CPU_CONTROL`

Same thread count, same loop counts, same arithmetic, writing into preallocated buffers so it
makes no garbage. **Zero late frames in 5 000 trials.**

This refutes the obvious confound: the late frames under CRUSH are not four busy threads
competing with the renderer for CPU. They track *allocation*. Story 1.7b's framing is correct
and D-026 called it right — the GC half is the sharper one.

*Caveat, honestly:* `cpuLoop` runs the same number of iterations as the allocating loops but
each iteration is cheaper, since it neither constructs objects nor builds strings. So it rules
out *"merely having four busy threads"* rather than *"the CPU cost of allocation itself."* That
distinction matters less than it sounds — that cost is a consequence of allocating.

### There is real headroom — `VOLUME_HEAVY`

At **0.54 MB/s, thirteen times Compose's own baseline**, with 3 514 collections and 153 trials
overlapping one: **zero of 10 000 blackouts overran a frame.**

The important detail is that **a collection overlapping a blackout is not sufficient to make it
late.** 153 overlaps at HEAVY produced no misses; 541 overlaps at CRUSH produced 18. What
changes is how hard the heap is churning, not whether a collection happens to land in the
window.

### The failure is real and reproducible — `VOLUME_CRUSH`

At 3.00 MB/s: 0.36% of blackouts overran a frame, and essentially every one had a collection in
its window. Across three CRUSH runs the worst stop-the-world pause reached **9.108 ms —
longer than an entire frame.** That is the un-anonymised revoke, reproduced on demand.

---

## The number E0 has to live with

```
     0.04 MB/s   Compose alone, doing nothing
     0.54 MB/s   verified clean — 10 000 blackouts, 3 514 collections, zero late
   ~ ? MB/s      the boundary, somewhere in this 5.6x window
     3.00 MB/s   0.36% of blackouts miss a frame
```

**Total app allocation should stay under ~0.5 MB/s.** That is verified, not extrapolated. The
boundary above it is unmeasured; narrowing it is one run at each of ~1.0 and ~2.0 MB/s.

## This changes 1.7b's mitigation

> Epic 1.7b: *"Establish a no-allocation discipline on the blackout path and enforce it with a
> permanent allocation assertion."*

**The blackout path's own allocation is not what hurts.** The allocation driving these
collections is on the BLE, motion, effect and recording threads. A blackout-path assertion
would have stayed green through every failure recorded here.

The load-bearing guard is a **total allocation-rate budget for the whole app**, asserted
continuously — the same shape as the permanent assertion the epic asks for, aimed one level up.
Keep the blackout-path assertion as well; it is cheap and it guards a different, smaller
failure, namely a future commit quietly adding allocation to the draw path.

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

## Not yet run

- **`LONG_IDLE`** — the real game's condition, with the renderer idle for seconds between
  blackouts rather than hammered every 100 ms. Everything above keeps the renderer warm.
- **`ALLOC_PROBE`** — sets the threshold for the blackout-path assertion.
