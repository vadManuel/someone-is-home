# Stack gate spike — story 1.7

**Throwaway.** Not the six-module structure, not a foundation for anything. It exists to
answer one question and then be deleted.

> **Can Compose Multiplatform (Skia→Metal) blank the lamp in the same frame as a trigger,
> on real hardware, at the tail?**

If it passes, E0 proceeds on Kotlin Multiplatform. If it fails, the fallback is Flutter and
this spike is the cheapest possible way to have found out.

---

## What "same frame" means here, precisely

A trigger lands at some arbitrary time inside a frame interval. The earliest the hardware can
possibly show dark pixels is the next scanout after the renderer picks the change up:

```
   v_k            v_k+1           v_k+2
    |---------------|---------------|
        ^ trigger    ^ Compose draws  ^ pixels visible
```

So the passing shape is **span = 1**: exactly one vsync boundary between the trigger and the
frame that drew black. A span of 2 or more means the renderer missed a frame — which is the
failure this gate is looking for. Wall-clock latency is reported too, but it is a function of
where in the frame interval the trigger happened to land, so **span is the metric and latency
is the sanity check**.

Device is an iPhone 16 Pro: ProMotion, so the frame interval under test is **8.33 ms**, not
16.7. The spike records the measured vsync interval per run rather than assuming it, because
LTPO will drop the panel to a lower rate if you let it.

---

## The two things that make this a real test

Both come from the boxed notes under story 1.7 in `epics.md`. Without them the spike passes
and tells you nothing.

### 1. Manufactured allocation pressure

A GC pause only happens when there is garbage. One amber screen and a tap allocates almost
nothing, never triggers a collection inside the test window, and returns a clean result — and
you would conclude Kotlin/Native GC is a non-issue and be wrong.

`Pressure.kt` generates representative garbage **on other threads** while the blackout fires,
modelled on what the real app will actually do:

| Source | Rate | Shape |
|---|---|---|
| Motion samples | 100 Hz × 8 | small short-lived objects |
| BLE adverts | 30 Hz | `ByteArray(31)` + wrapper + string id |
| Effect objects | 10 Hz | immutable data classes holding lists |
| Recording writes | 10 Hz | ~500-char strings via `StringBuilder` |
| Retained churn | continuous | 2048-slot ring, so objects survive a young cycle and force major GCs |

Levels: `OFF` / `LIGHT` / `REPRESENTATIVE` / `HEAVY`. **`OFF` is the control** — run it, and if
the tail is identical with and without pressure, the pressure generator isn't doing its job and
the GC half of the gate has not actually been tested.

### 2. Two-layer measurement

A 240 fps camera gives ~30 samples in a session. The gate is about one frame in ninety
thousand. You cannot find a p99.9 tail with thirty samples.

- **In-app, for the tail** — thousands of trials, unattended. Reads the tail, never the mean.
- **Camera, for calibration** — 20–30 samples proving the in-app number corresponds to photons
  actually leaving the glass.

Neither alone answers the gate. The camera validates the instrument; the instrument finds the
tail.

---

## What is instrumented

Four timestamps per trial, all on the `CACurrentMediaTime()` base so they are directly
comparable with `CADisplayLink`:

| | |
|---|---|
| `t_trigger` | stamped immediately before the state write that starts the blackout |
| `t_draw` | stamped inside the draw lambda, on the first frame that draws black |
| `t_present` | first vsync strictly after `t_draw` |
| vsync timeline | continuous, from our own `CADisplayLink` |

Plus, continuously:

- **Display-link gap histogram.** Our `CADisplayLink` callback runs on the main run loop, so
  anything that stalls the main thread — a stop-the-world GC pause included — shows up as a
  gap between ticks. This is the main-thread stall detector.
- **GC epochs**, polled off-thread from `kotlin.native.runtime.GC.lastGCInfo` (reading it
  allocates, so it must never be read on the blackout path), with pause start/end and
  heap-before/after so allocation between collections can be derived.

### Known limitation, stated rather than hidden

`t_present` is *inferred* — the first vsync after the draw. If Compose commits late and the
drawable actually lands a frame later, this instrument records the optimistic value. That is
exactly why the camera pass exists, and it is why **span** (derived from the vsync timeline,
which does catch main-thread stalls) is the headline rather than inferred presentation time.

---

## The blackout path, and why it is shaped like this

```kotlin
val lampArgb = mutableIntStateOf(ARGB_AMBER)          // IntState: no boxing on write
Spacer(Modifier.fillMaxSize().drawBehind {
    drawRect(Color(lampArgb.intValue))                // state read in the DRAW lambda
})
```

Reading the state inside `drawBehind` rather than in composition means a change invalidates
**draw only** — composition and layout are skipped entirely. That is the no-allocation
discipline story 1.7b asks for, expressed in Compose terms. `mutableIntStateOf` avoids the
boxing a `MutableState<Color>` would do on every write.

`ALLOC_PROBE` mode measures whether that discipline actually holds, by bracketing N triggers
with forced collections and deriving allocated bytes from the GC epoch deltas. It reports
**bytes per trial** — the number the real project's permanent allocation assertion should be
set against.

---

## Run modes

| Mode | n | Pressure | Spacing | Answers |
|---|---|---|---|---|
| `VOLUME` | 10 000 | REPRESENTATIVE | 80–120 ms | the tail, under load — **the gate** |
| `VOLUME_CONTROL` | 10 000 | OFF | 80–120 ms | control: proves the pressure generator matters |
| `COLD` | 200 | REPRESENTATIVE | 80–120 ms | first-run shader stall, no pre-warm (1.7a) |
| `LONG_IDLE` | 200 | REPRESENTATIVE | 3–10 s | the renderer going idle between trials |
| `CAMERA` | 30 | REPRESENTATIVE | manual tap | calibration against photons |
| `ALLOC_PROBE` | 20 000 | OFF | — | bytes allocated per blackout (1.7b) |

**`LONG_IDLE` is not optional.** In the real game the lamp sits static in amber for minutes and
then must blank. A tight 100 ms loop keeps Compose's redrawer permanently warm, which is not
the condition the gate is actually about.

Trigger phase is randomised with sub-frame resolution. A trigger fired on a fixed period
locks to one phase of the frame clock and samples one slice of the distribution — which would
look clean and mean nothing.

## Trigger is pluggable

`TriggerSource` has two implementations: `TapTriggerSource` (a screen tap, which is the decided
trigger for this spike) and `ScriptedTriggerSource` (needed because nobody taps ten thousand
times). The gate measures *trigger → pixels dark*, and the rendering pipeline does not care what
produced the trigger. A real BLE contact handshake needs two devices and drags radio risk — a
separate question with its own test, story 0.6c — into a rendering test. Kept apart on purpose.

---

## Running it

```bash
./run-spike.sh build     # build the framework + app
./run-spike.sh install   # install to vadManuel-Phone
./run-spike.sh pull      # copy results CSV/JSON off the device
```

Physical device only. **The iOS Simulator cannot answer this question** — it renders through a
different path on host hardware with no ProMotion panel and no Kotlin/Native-on-ARM GC
behaviour.
