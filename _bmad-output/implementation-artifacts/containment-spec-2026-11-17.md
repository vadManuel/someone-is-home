# Someone's Home: Egress containment build spec

Read-only research, written 2026-11-17 against main `f914022`. Nothing was edited, built or committed.
U1 (uplink cover, WIP `384f8cb` in worktree `agent-a64289e2b1632afbf`) is assumed landed: every round request rides a `RequestSlot` inside the fixed-cadence padded upload, with exactly-once ids.

Short paths: **GDD** = `_bmad-output/planning-artifacts/gdds/gdd-someone-is-home-2026-08-16/gdd.md`, **DL** = `decision-log.md` and **EP** = `epics.md` in the same folder, **ARCH** = `_bmad-output/planning-artifacts/game-architecture.md`, **PACK** = `_bmad-output/implementation-artifacts/owner-decisions-2026-11-17.md`. Code paths are relative to the repo root; `…/` stands for `src/commonMain/kotlin/home/someoneshome/<module>/`.

**Word clash to avoid in new code.** U1 calls each fixed-cadence upload a "beat". The Sync Pulse also has beats. New identifiers for containment should say `pulse`, not `beat`, so the two never share a name.

---

## 0. The problem in three lines

1. **No control.** No screen draws a containment control. `EgressWidget` only reports ON or OFF THE BEAT (`ui/…/ScreensWork.kt:900-930`). Nothing in `Flow` sends `IntentMessage.ReturnSyncPulse`.
2. **No clock.** The house grades taps against `egress.firedAt` in absolute house ticks (`core/…/Egress.kt:185-231`). No phone knows the house tick, and `EgressOpened` carries only a duration (`remaining`). The real client never runs `ClockSync`.
3. **So every Egress fired on real phones wins for the Insiders.** The bench shows the same: its taps are unanchored and always miss (`app/src/cheats/…/RoundCheat.kt:579-589`, D-232 at DL:5758).

---

## 1. What the docs specify

### 1.1 Who can contain, and where

- GDD:369: *"Containment | **Two people at two separate markers**, simultaneously"*
- GDD:372: *"Participants | **Unlimited.** Holders at one marker pair with holders at the other; extras form concurrent pairs; first success contains"*
- GDD:1014: *"Residents converge. Two reach separate nodes and hold; … Extras arriving form **concurrent pairs**; the first success contains it. A stalling Insider at one node **removes a partner, not the attempt**"*. This says an Insider at a node can take part, and the design intends it.
- GDD:209 and :1078 (F-016): *"the smallest living Resident population in a running game is 2 — which is exactly the number needed to contain an Egress"*.
- DL:2417 (D-148): the nodes are *"two ordinary registered markers in non-adjacent rooms, chosen by the house at the moment the Egress fires"*. If no non-adjacent pair exists, the farthest pair is used: *"An Egress never fails to fire."*
- DL:2761 (D-162): *"**Scanning the node's marker opens containment.** … **The fix is that there is nothing to tap** — you scan, the same as you do at every other card in the building"*. In context, "nothing to tap" means there is no separate "I'm here" button, because the scan is the check-in. The Sync Pulse taps (A-11) still apply.
- DL:1895 (D-131): *"Restraining the last Insider during an Egress does not end the round: the house does not stop what it was told to start."*
- Out players: nothing gives them a containment role. GDD:545 and :1052 give the couch *"the real Egress progress"* and nothing else.

### 1.2 How: the Sync Pulse

- GDD:371: *"Containment action | **Sync Pulse** — both phones pulse haptically in unison off a server-scheduled timestamp; both players tap on the beat **4 times** [ASSUMPTION: A-11]"*
- GDD:373: *"Failed attempt | 2–3s lockout — makes stalling meaningfully more expensive and stops spam-retry from being optimal"*
- GDD:1564 (A-11): *"Sync Pulse: 4 taps on the beat | Too few and containment is trivial; too many and it cannot be completed inside the Egress timer"*
- DL:2851-2852 (D-167): *"Sync Pulse beat | **1.5 s** | four taps on the beat (A-11) is therefore ~6 s of tapping"* and *"Sync Pulse tap window | **400 ms** | the tolerance around each beat"*.
- DL:3242 (D-171 §6): *"**400 ms** around each Sync Pulse beat. **The 400 ms is wide on purpose**, and *no twitch timing* is constraint 4"*.
- DL:2444 (D-149): any Beacon/Tether difference in the containment beat is deferred. **Both types use the same pulse.**

### 1.3 The clock-offset mechanism, precisely

- GDD:919: *"Every phone flips **simultaneously**, off a server-scheduled timestamp ~2s in the future rather than a broadcast "go." Each client measured its clock offset at join and schedules the flip locally … **This is the same mechanism that drives the lights-up snap and Sync Pulse — one piece of infrastructure, three uses.**"*
- GDD:1301-1305: *"**Do not broadcast "go."** Broadcast **"the event begins at timestamp T,"** ~2s in the future. Every client measures its offset from server time at join and schedules the flip locally … **One mechanism, three uses:** the lights-up snap, perimeter arming, and Sync Pulse."*
- GDD:1291 and DL:281 (D-019): skew is set by clock-offset accuracy, not delivery. *"**Sync Pulse** looked tight but the design already forbids twitch timing, so a generous tap window absorbs device skew."*
- ARCH:393-403 (D5 · Clock), the governing numbers:

| Parameter | Value |
|---|---|
| Local timebase | **Monotonic**, never wall clock |
| Offset estimation | NTP-style round trips at join; **take the minimum-RTT sample** |
| Re-sync cadence | **Every 30 s** |
| Correction | **Slew, never jump** |
| Scheduled events | broadcast → ack → commit; `T = last_ack + 500 ms` |
| Recording timestamps | **Simulation tick index, not wall clock** |

- EP:105 (2.2): *"each client measures its clock offset from server time at join"*. EP:106 (2.3): *"One mechanism, three uses: arming, the lights-up snap, Sync Pulse"*. EP:388 calls it *"load-bearing … cannot be bolted on"*.

### 1.4 What each phone sees and hears

| Who | Docs say | Where |
|---|---|---|
| Every phone, living and out, at fire | A **dismissable** alert, with the house dimming (one of D-118's two dims) and the long haptic (D-135) | GDD:374, :1012; DL:1686, :1954 |
| Living | *"The System Integrity widget becomes the Egress countdown, and names the two nodes"* | GDD:375, :735, :1012 |
| Out (couch) | *"Both bars … the real Egress progress, which no living player of either role can see"*; the widget is empty with no Egress (D-178) | GDD:545, :1052; DL:3368 |
| The two (or more) at nodes | *"both phones pulse haptically in unison off a server-scheduled timestamp"*, then four taps on the beat | GDD:371, :1014 |
| Actor | *"No actor-side feedback on firing any ability"* | GDD:416-417 |
| On containment | *"**Contained.** The widget reverts. Nobody learns anything about anybody."* | GDD:1016 |
| On failure | The Insiders win outright | GDD:207, :312, :1092; DL:1895 |
| Sound | *"Swell during an Egress"* (v1 has no game audio); *"Only ever modulate on global events"* | GDD:1244, :1262 |

### 1.5 The lamp

The docs say nothing about the lamp during containment. The standing rules apply: the lamp is a pure function of state, with no local animation (project-context rule 5). Only two events dim the house, and the Egress opening is one of them (D-118).

### 1.6 Success and failure

- Success: the first pair at opposite nodes that both hit four beats contains it (GDD:372). Containing does not refund the shared cooldown; the code already tests this (`EgressTest`:335).
- Failure: the timer runs out (`EgressExpired`). It is a lobby setting with a 120 s default (GDD:1157, DL:216 D-014). A meeting called by a report pauses it and never resets it (D-133, DL:1913).
- Playtest target, GDD:1381: *"Contained ≥70% of the time at full population; **>0% and <50%** at 3 living Residents."*

### 1.7 Where the docs are silent

1. **What the phone shows at a node.** No screen is specified. GDD:1014 says "hold"; D-162 says scan; A-11 says tap. There is no copy, layout or exit path.
2. **What a node scan does to work on that card.** A node is an ordinary active marker (D-148), so it may hold a Subroutine (or an Insider's fake) for the scanner. D-162 says the scan "opens containment" and does not say what happens to the work.
3. **Which phones pulse.** "Both phones" (GDD:371). Nothing says whether phones not at a node pulse too.
4. **Whether the four taps must be consecutive**, and what "simultaneously" (GDD:369) and "tap on the same beat" (GDD:1014) mean when two players start at different moments.
5. **What a phone shows during the 2-3 s lockout.** Nothing.
6. **A phone with no clock fix**, for example one that has just reconnected.
7. **The lamp during containment** (see 1.5).
8. **An Egress while the house is off the air** (host locked). PACK Q4 already raises this.

### 1.8 Where the docs contradict each other

| Topic | One side | Other side | What governs |
|---|---|---|---|
| The containment act | GDD:1014, :372 *"reach separate nodes and **hold**"*, *"Holders"* | GDD:371 and A-11 (four taps), D-162 (scan) | D-162 plus A-11: scan to arrive, tap four times on the pulse |
| What counts as simultaneous | GDD:1014 *"they tap on the **same beat**"* | GDD:369 *"simultaneously"*; the code pairs by arrival within 6 s (`Balance.SYNC_PULSE_PAIR_WINDOW`) | Unruled (O3) |
| Offset measured when | GDD:919, :1303, EP:105 *"at join"* | ARCH D5 *"Every 30 s, slew never jump"*; ARCH:147 flags drift | D5 (`ClockSync.kt` implements it) |
| Recording timestamps | ARCH D5 *"Simulation tick index"* | Tap times have to be on a shared wall timeline to be gradable | D5 governs event `at`. Tap times are recorded payload (see C2). |
| Isolate at a node | EP:285 (9.7) *"firing Isolate at one still appears to succeed"* | D-150 (DL:2450): the nodes are excluded from the list, with no fake success | D-150; the code follows it (`Rules.kt:813-815`, `:974`) |
| Living Egress progress | GDD:545, :1052 (hidden from the living) | GDD:375, :735, :1012 (the countdown) | PACK Q3 (open) |
| Egress cooldown (nearby, not containment) | D-167 (DL:2850) *"120 s default"* | `Balance.EGRESS_COOLDOWN` = 180 s (`model/…/Balance.kt:162-172`) | Flag only |

---

## 2. What exists in code

### 2.1 Core rules: built and tested at the event level

| What | Where | State |
|---|---|---|
| Node and type draw (farthest pair, seeded) | `core/…/Egress.kt:65-97` | Built |
| Pulse schedule: `beatNear`, `beatAt` (house-wide, from `firedAt`, ignores pauses) | `core/…/Egress.kt:185-201` | Built, **in ticks** |
| `onTheBeat`: exactly 4 taps, each within the window, no repeated beat | `core/…/Egress.kt:218-231` | Built. **Taps need not be consecutive.** |
| `Egress` state: nodes, deadline, pause, offers, lockouts; `partnerFor` uses **arrival tick** within the pair window | `model/…/Egress.kt:70-223` (`partnerFor` :156-161) | Built |
| `egressFired`: one `AbilityFired` to the actor, `EgressOpened` per seat, Isolate offers and blanks | `core/…/Rules.kt:1812-1879` | Built |
| `pulseReturned`: presence from the last scan, lockout, pairing, one-shape `SyncPulseAnswered`, `EgressContained` plus Isolate re-offers | `core/…/Rules.kt:1910-1962` | Built |
| `egressExpired` leads to `WinRoute.EgressUncontained` | `core/…/Rules.kt:1977-1980`, `:182` | Built |
| Pause and resume across a meeting; offers are dropped on resume | `Rules.kt:1346-1348`, `:1704-1706`; `model/…/Egress.kt:188-209` | Built |
| Admission: pulse and expiry need a running, unpaused Egress | `core/…/Admission.kt:193-194`, `:327-329` | Built |
| Intent to event | `core/…/Intents.kt:69` | Built |
| Presence written by every scan | `core/…/Rules.kt:507`, `:562` | Built |
| Balance: `SYNC_PULSE_BEATS`=4, `LEAD`=2 s, `INTERVAL`=1.5 s, `WINDOW`=0.4 s, `LOCKOUT`=3 s, `PAIR_WINDOW`=6 s (all in ticks at 20 Hz) | `model/…/Balance.kt:306-370` | Built |
| Tests | `core/src/commonTest/…/EgressTest.kt`:465 (same node), :486 (two nodes contain), :509 (no attribution), :552 (held), :573 (lockout, same shape), :623 (four on one beat), :645 (window), :875 (stale offer) | Built, in ticks |

### 2.2 Events, effects, wire, schema

| What | Where | Notes |
|---|---|---|
| `Event.EgressFired(at, actor, type, nodes)` | `model/…/Event.kt:395` | **No time anchor** |
| `Event.SyncPulseReturned(at, actor, taps: List<Long>)` | `model/…/Event.kt:425-430` | Taps are meant to be "ticks". No phone has them. |
| `Intent.ReturnSyncPulse` / `IntentMessage.ReturnSyncPulse(taps)` | `model/…/Intent.kt:112`; `model/…/protocol/IntentWire.kt:214-218`, `:298` | On the wire. **Nothing in `ui` or `app` production code sends it.** |
| `Effect.EgressOpened(seat, type, nodes: List<Destination>, remaining, haptic)` | `model/…/Effect.kt:910` | Destinations carry `marker` (card id), shape and room |
| `Effect.EgressHeld`, `SyncPulseAnswered(seat, held)`, `EgressContained`, `EgressSucceeded` | `model/…/Effect.kt:957`, `:983`, `:998`, `:1017` | |
| `RoundMessage.EgressOpened/Held/SyncPulseAnswered/Contained/Succeeded` | `model/…/protocol/RoundWire.kt:664-708`; projection `:1105-1111` | **`SyncPulseAnswered(held: Boolean)` is 4 or 5 characters wide**: its frame size changes with the value (the leak D-258 fixed for `AbilityFired`) |
| Schema rows: `EGRESS_OPENED`, `EGRESS_HELD`, `EGRESS_CONTAINED` to `LIVING + OUTSIDE`; `SYNC_PULSE_ANSWERED` to `LIVING`; `EGRESS_SUCCEEDED` has no row on purpose | `model/…/EmitSchema.kt:544-583`; addressing `:768-777` | Fail closed, as required |
| D-258's audit lists `SyncPulseAnswered(held)` as a per-act size tell ("S2"), not a role tell | DL:7035 | Still open |

### 2.3 Clock offset: the parts exist, nothing connects them

| What | Where | State |
|---|---|---|
| `TransportFrame.TimeProbe(probe)` / `TimeMark(probe, hostMillis)` | `model/…/protocol/TransportFrame.kt:126-145` | Built (protocol 2) |
| The host answers probes below the app with its monotonic millis | `platform/…/transport/TransportHost.kt:285-293`; host clock `LobbyOverTransport.kt:537-539` (`monotonicNanos()/1e6`) | **Built and running** |
| `ClockSync` (minimum RTT, 30 s resync, slew 50 ms/s, `hostNowMillis`) | `platform/…/transport/ClockSync.kt:45-128`; `ClockSyncTest` | Built, unit-tested |
| The real client, `TransportClient` | `platform/…/transport/TransportClient.kt:38-120`; built at `LobbyOverTransport.kt:1270-1279` | **Sends no probes and ignores `TimeMark`** |
| Only user of `ClockSync` | `app/src/cheats/…/TransportCheat.kt:90`, `:181`, `:220-225` (the PING rig) | Rig only |
| The house tick clock, which drifts from wall time (V4: +1 tick per arrival; D-230 skip-forward; the bench's `skewNanos`) | `app/…/TickDriver.kt:72-105`; `app/src/cheats/…/Cheats.kt:543`; `RoundCheat.kt:118`, `:912` | This is why beats must **not** be scheduled on house ticks |
| `RoundAuthority.fire` makes the draw at `now()` (a tick) | `app/…/RoundAuthority.kt:467-487` | No host-millis anchor |

### 2.4 UI

| What | Where | State |
|---|---|---|
| `EgressOpened` stores type, **rooms only** (`it.room`), countdown and public clock, then raises the dismissable banner | `ui/…/Flow.kt:2909-2926` | Card ids are dropped, so the phone cannot recognise a node scan |
| `EgressHeld`, `SyncPulseAnswered` (copied into `beatHeld`), `EgressContained` | `ui/…/Flow.kt:2939-2966` | Built |
| The scan answer: `null` goes to `ScanBad`, otherwise `ScanCaught` | `ui/…/Flow.kt:2534-2540`; `scanning(marker)` keeps `scannedAt` at `:2195-2197` | No node branch |
| The Egress widget: countdown, bar, nodes, YOUR BEAT line | `ui/…/ScreensWork.kt:869-930`; `PanelModel.kt:253-312`, `:1734-1737`, `:1793` | Built. **No tap control.** |
| `PanelState.beatHeld` | `ui/…/PanelModel.kt:301-312` | Built, read-only |
| Haptic for `SyncPulseAnswered`: none | `model/…/HapticScript.kt:435` | Correct as is |
| A `ui` seam for a house-time clock | none; `wallClock: () -> WallTime?` (`Flow.kt:1188`) is the pattern to copy | Missing |

### 2.5 The bench and tools

| What | Where | State |
|---|---|---|
| `SOMEONES_HOME_ROUND_NODE` picks which node a bench seat walks to | `RoundCheat.kt:185-192`, `:1015`; `tools/sim-hunt/hunt.sh:151-152` (`--nodes`) | Built |
| `reachNodeAndPulse`: scans through `BeginSubroutine`, then sends **unanchored** taps `LEAD + b·INTERVAL` | `RoundCheat.kt:579-589` | Always misses (D-232). It also bypasses the screen, because there is no control to press. |
| Header table and the "cannot contain" note | `RoundCheat.kt:37`, `:51-53` | To rewrite |
| A loopback test that four phones fire and open an Egress | `app/src/cheatsTest/…/AbilityWiringTest.kt` | No containment case |

### 2.6 What is half-built

- **Core containment logic is complete but cannot be reached.** It grades on a timeline no phone has.
- **The wire request exists (`ReturnSyncPulse`) but nothing sends it.** The wire answer exists (`SyncPulseAnswered`) and the phone draws it, but its size varies with the value.
- **The clock-offset machinery is built at both ends (the host answers, `ClockSync` estimates) but is not connected on the real client.**
- **The UI has the widget and the answer line, but no control, no screen and no node detection.**
- A small core gap: `partnerFor` does not check that the partner is still living. A partner Revoked inside the 6 s window can still pair (`model/…/Egress.kt:156-161`).

---

## 3. Build plan

Order: **C1 → C2 → C3 → C4 → C5**, then C6 on phones. C1 and C2 can run in parallel in separate worktrees (`platform`/`app` vs `model`/`core`). C3 depends on C2's wire field. C4 depends on C1 and C3.
**PACK Q4 (an Egress pauses while the house is off the air) should land with or before C5.** Without it, an Insider host still wins every Egress by locking the host phone, containment or not.

Every unit follows the standing checks: stage explicit paths; run `./gradlew check --continue` (tests **and** lints, including `vocabularyLint`); read the test XML, not the exit code; prove each guard by breaking it; render any screen to PNG.

---

### C1. A measured house clock on every phone (platform, app)

**Changes**
1. `platform/…/transport/TransportClient.kt`: give it a `ClockSync`. In the pump's coroutine scope, launch a ticker every 250 ms that sends `TimeProbe(id)` for each id from `clockSync.dueProbes(nowMillis())`. In the read loop, consume `TimeMark` with `clockSync.onMark(probe, hostMillis, nowMillis())` before calling `onFrame`. Reset the in-flight round on a new connection; keep the fix, since the host clock did not change. Expose `fun hostNowMillis(): Long? = clockSync.hostNowMillis(nowMillis())`.
   - **Probes stay transport frames. They do not go into U1's request slot.** Batching them into the cadence would add up to a second to RTT and wreck the estimator. Every phone sends the same 5 probes every 30 s, so the frames are symmetric across roles.
2. `app/…/LobbyOverTransport.kt`: expose `houseNow: () -> Long?` from the live client. It returns null before seating and before the first fix. The host's own seat goes through the same loopback client, so it uses the same path.
3. App root: hand `houseNow` to `ui` the way `WallTime` is handed (D-243). `ui` cannot see `platform`.
4. Cheats only: `SOMEONES_HOME_CLOCK_SKEW_MS`, a constant added to this phone's client `nowMillis`. It proves the offset is measured, not assumed. Simulators on one Mac share a monotonic base, so without it the sim-hunt would pass by accident.
5. Log, console only (rule 6): `[clock] FIX rtt=… offset=…` at each round's finish.

**Leak and parity**
- Each phone sends `TimeProbe` frames on its own 30 s phase from connect, independent of role. `TimeMark` goes only to the prober. Frame widths grow with the probe counter and the host's uptime digits, which are the same for every phone.
- No `Effect`, so no schema row. `TimeProbe` and `TimeMark` are already `@ClientFacing`.
- R12 risk: `TransportHost.kt:292` writes `TimeMark` directly with `ws.send` while the per-connection writer coroutine also sends. Confirm Ktor's outgoing channel is safe for concurrent senders (it is a channel), or route the mark through the writer at the head of the queue. Either way, add a test.

**Tests**
- `platform` `TransportLoopbackTest`: a real host, a client whose `nowMillis` is skewed by +3 000 ms and another by −2 000 ms. Within 1 s of seating, `|hostNowMillis() − host clock| ≤ 20 ms` on both. **Prove by injection**: drop the `onMark` call and the test must fail.
- `platform`: over 65 s of virtual time, two clients send the same number of probe frames (11, at seconds 0, 30 and 60).
- The existing `ClockSyncTest` stays green.

---

### C2. The pulse on a shared timeline, and grading the four taps on it (model, core, app edge, harness)

**Design.** The house's ticks drift from wall time (V4, D-230's skip, the bench's skew). The phones' haptics run on wall time. So **the pulse schedule is anchored in host-monotonic milliseconds, and taps are reported on that same timeline.** Event `at` stays a tick (D5). The anchor and the taps are recorded payload, so a replay grades identically (rule 4).

**Changes**
1. `model/…/Balance.kt`: add `SYNC_PULSE_LEAD_MILLIS`, `SYNC_PULSE_INTERVAL_MILLIS` and `SYNC_PULSE_WINDOW_MILLIS`, derived from the tick constants as `x * 1000 / TICKS_PER_SECOND` so there is still one source of truth. Keep `syncPulseIsGradeable()` and add a millis twin.
2. `model/…/Event.kt:395`: `EgressFired` gains `pulseFrom: Long`, the host-monotonic millis of pulse 0, computed as `anchor + SYNC_PULSE_LEAD_MILLIS`.
   `core/…/Egress.kt:65`: `egressFor(…, anchorMillis: Long)` takes the anchor as a parameter. Core never reads a clock.
   `app/…/RoundAuthority.kt:476`: passes the **same** `nowMillis` function `TransportHost` answers `TimeMark` with (`monotonicNanos()/1e6`, `LobbyOverTransport.kt:539`). It must **not** pass the `TickDriver`'s skewed `clockNanos` (`Cheats.kt:543`). Inject it as a constructor seam: `hostMillis: () -> Long`.
3. `model/…/Egress.kt`: `Egress` gains `pulseFrom`. `Event.SyncPulseReturned.taps` and `IntentMessage.ReturnSyncPulse.taps` are redefined as **host-timeline millis** (KDoc changes, no type change).
4. `core/…/Egress.kt`: rewrite `beatNear`, `beatAt` and `onTheBeat` against `pulseFrom` in millis (integer arithmetic). **Subject to O3:** require four **consecutive** pulse indices, and return the run's first index. `onTheBeat` keeps its early returns. It is an internal grader, and the client-visible shape is fixed by `pulseReturned` (rule 1 holds).
5. `model/…/Egress.kt`: `PulseOffer` gains `firstPulse: Long`. **Subject to O3:** `partnerFor` pairs on the pulse grid. The partner must be another seat, at the other node, **still living**, and its run must overlap this one (`|a.firstPulse − b.firstPulse| < SYNC_PULSE_BEATS`). `holding` drops offers whose run cannot overlap the new one. Pairing then no longer depends on upload arrival (U1 adds up to about 1 s). Lockouts stay on arrival `at`, which errs lenient.
6. `model/…/Effect.kt:910` and `model/…/protocol/RoundWire.kt:664`: `EgressOpened` gains `pulseFrom: Long`. Projection at `RoundWire.kt:1105`. The value is the same for every seat.
7. `model/…/protocol/RoundWire.kt:691`: `SyncPulseAnswered(held: Boolean)` becomes one fixed-width digit, `held: Int` serialised as `1`/`0`. This copies D-258's `AbilityFired` fix (DL:6993-6999), keeping the call-site accessor. Anything else does not decode.
8. **Subject to O1**, `core/…/Rules.kt` `markerScanned` (around :530): while an Egress runs **unpaused**, a scan of one of its nodes opens nothing for **any** seat (`entry = null`). The scan still places the seat there and still answers `ScanAnswered(null)` plus `PresenceChanged`, the same shape as an Isolated card at `:534-545`. This branch reads public state (the nodes were named to every phone), never the role.
9. Every place that uses the `Egress` constructor, and `harness` `Recording`, `RecordingText` and `Transcript`: bump the recording format **28 → 29**.

**Leak and parity**
- `EgressOpened` grows by one number, the same on every phone. Its length depends on the host's uptime digits, never the seat. Re-run `IsolateFrameSizeTest`'s Egress leg: every phone except the actor should get the same `[326+k, 594]`.
- `SyncPulseAnswered` reaches only the seat that tapped, of either role, and it becomes **one width**. The remaining tell, an extra downward frame to whoever tapped, is a tell about an act and a position, not a role (D-258's S2 class). See O5.
- No new `Effect` kind, so no new schema row. `EmitSchemaTest` must show the rows unchanged. **The fail-closed check:** delete `SYNC_PULSE_ANSWERED`'s row in a scratch run and confirm the loopback test in C5 fails with "my thing didn't appear".
- The O1 rule is symmetric. An Insider's fake at a node goes dark exactly as a Resident's real work does. This is the same argument `markerScanned` already makes for Isolate (`Rules.kt:539-541`).

**Tests**
- `core` `EgressTest`: port every containment case to millis taps. New cases:
  - (a) four taps within ±400 ms of four **consecutive** pulses pass; a gap fails (O3);
  - (b) two runs at opposite nodes that share one pulse contain; runs with no shared pulse do not, **whatever their arrival ticks**;
  - (c) a pause does not move the pulse grid;
  - (d) a partner Revoked after offering cannot pair;
  - (e) O1: a node card with a real Subroutine (and an Insider's fake) opens nothing during a running Egress, and opens normally before the fire and after containment; the effects list has the same shape in every case.
- `harness` differential: a seeded round with an Egress and containment, with seats 1 and 2 swapping roles, where an Insider is one of the containing pair in one run and a Resident is in the other. The transcripts must be identical apart from the actor's `AbilityFired`.
- `harness` `ReplayTest`/`EgressFixtureTest`: a recording-29 round replays to the same containment.
- `model`: `RoundWireTest` round-trip of `EgressOpened` with `pulseFrom`. `SyncPulseAnsweredWireSizeTest` (the `IsolateWireSizeTest` pattern): both values have equal length at sequences 1 to 10⁶, and a `2` does not decode.

---

### C3. The containment screen and its control (ui)

**Changes**
1. `PanelState` (`ui/…/PanelModel.kt`) gains:
   - `egressCards: List<MarkerId>?`, from `message.nodes.map { it.marker }`, stored beside `egressNodes` at `Flow.kt:2916`;
   - `pulseFrom: Long?`, from `EgressOpened.pulseFrom`;
   - `pulseTaps: Int`, input echo from 0 to 4;
   - `pulseWait: Countdown?`, the uniform wait after OFF THE BEAT (O4).

   All of these are cleared by `betweenRounds()` (D-259) and by `EgressContained`.
2. `Flow` constructor seam: `houseNow: () -> Long? = { null }`, the same pattern as `wallClock` (`Flow.kt:1188`).
3. Routing, `Flow.kt:2534`: on `ScanAnswered(null)`, if `egressRunning && !egressHeld && scannedAt ∈ egressCards`, push `ScreenId.Contain`; otherwise push `ScanBad` as now. The phone moves only on the house's answer and on public facts.
   Leaving `Contain`: the player's own back gesture, or pushes the house sends:
   - `EgressContained` goes to `Home`, where the widget has already reverted;
   - `MeetingCalling` / `MeetingOpened` take over as today;
   - `RoundEnded` takes over as today.
4. Control: `tapPulse()` on `PanelActions`. Each tap stamps `houseNow() ?: localMonotonic` and increments `pulseTaps`.
   - **No fix**: stamp locally and log once. The house grades the taps and they miss. Nothing on screen says why (rule 6).
   - On the 4th tap: `send(IntentMessage.ReturnSyncPulse(taps))`, which U1 puts in the next upload's slot. Then reset `pulseTaps` to 0.
   - Further taps while a send is waiting for its answer are ignored.
   - On `SyncPulseAnswered(0)`: start `pulseWait` at `Balance.wholeSeconds(SYNC_PULSE_LOCKOUT)` on **every** OFF THE BEAT. Taps are inert while it runs. The rule reads the answer only, never why it was false, so it cannot become a node detector (`Flow.kt:2946-2955`).
5. `ScreensWork.kt`: a new `ContainScreen`, following "screens say little" (headline plus the one action). Provisional copy for O4:
   - the legend `EGRESS . <TYPE>`;
   - the node's room (the one this phone scanned);
   - the countdown, using the widget's `egressLeftText`;
   - one large tap field reading `TAP WITH THE PULSE`;
   - four pips (input echo);
   - the existing `YOUR BEAT · ON/OFF THE BEAT` line once answered;
   - `WAIT` while `pulseWait` runs.

   **No visual pulse.** The beat is felt, not drawn (rule 5). The lamp level is whatever the lamp state already says.
6. `ScreenGraph`/`Flow` edges, `RoutingTest` and `Screen.kt` registration. Add `Contain` to whatever set the hold and tap gestures are tested against.

**Leak and parity**
- Whether a phone is on `Contain` depends only on the holder scanning a publicly named card during a public Egress. The screen, tap behaviour, wait, copy and pixels are identical for both roles.
- The pips are input echo: no round trip, and nothing the house sends draws them.
- Lamp: no change. `LightSignatureTest` must show `Contain` with the same lamp output as `Home` in the same state.

**Tests**
- `FlowTest`/`RoutingTest`:
  - a node scan during an Egress leads to `Contain`;
  - before the Egress, or while it is paused, it leads to `ScanBad`;
  - a non-node scan during an Egress leads to `ScanBad`;
  - `EgressContained` from `Contain` leads to `Home`;
  - a meeting call from `Contain` follows the meeting route.
- Tap test (`desktopTest`, `HoldInputTest` pattern): four taps on a fake `houseNow` send exactly one `ReturnSyncPulse` with those four stamps. A fifth tap starts nothing until the answer arrives. OFF THE BEAT starts the wait, and taps during it send nothing.
- Render harness: PNGs of `Contain` in its four states (fresh, 2 pips, ON THE BEAT, WAIT). **Look at them.** A parity render must show the Insider's and the Resident's `Contain` pixel-identical. `HousedContentTest` must find no fixtures on a housed `Contain`.
- `ScreenGraphTest` and `vocabularyLint`.

---

### C4. The felt pulse (app, model haptics)

**Changes**
1. `model/…/HapticScript.kt`: `PULSE`, the same short buzz Sniff's groups use (D-140, `HapticScript.kt:60`). Name it once.
2. `app/…/PulseBuzzer.kt` (new; `HouseBuzzer`'s neighbour): while `screen == Contain && egressRunning && !egressHeld && pulseFrom != null && houseNow() != null`, compute the next pulse index `n` and its local time `pulseFrom + n·INTERVAL − offset`, wait until then, and play `PULSE`. Re-read the offset each pulse, since it slews. Stop the moment any of those conditions goes false.
   - Use one coroutine with no allocation per pulse. This counts against the 0.5 MB/s budget (rule 7).
   - **Subject to O2:** only the `Contain` screen pulses.

**Leak and parity**
- Haptics are private to the hand. Which phones pulse is decided by the holder's scan, not by role. The pattern and timing are identical for both roles.
- The pulse is not sound, so GDD:1262's *"only modulate on global events"* does not apply. Note for the device test: a phone buzzing on a hard surface can be heard. Phones are held in hand (house rule), so accept that.

**Tests**
- A `PulseBuzzerTest` with a fake clock:
  - pulses fire at `pulseFrom + n·1500 − offset` within one scheduler step;
  - none fire off `Contain`, while held, or after containment;
  - the sequence is identical for an Insider-seated and a Resident-seated panel;
  - **break it**: ignore the offset, and the test catches the skew.
- Allocation: run the scheduler for 120 s in the existing allocation probe and confirm about zero per pulse.

---

### C5. The bench, the loopback wiring test and the sim-hunt (app/cheats, tools)

**Changes**
1. `RoundCheat.reachNodeAndPulse` (`:579-589`) is rewritten to act like a person:
   - scan the node card through the real scan path;
   - **wait until `flow.state.screen == ScreenId.Contain`**, and log it if the phone never gets there;
   - read the pulse grid the phone holds (`pulseFrom` and the `houseNow` seam);
   - press `flow.actions().tapPulse()` four times at the next four pulses, plus seeded jitter of ±150 ms;
   - on OFF THE BEAT, wait for the screen's `WAIT` to clear and try again.
   No minted events and no direct `client.act`. Remove the "cannot contain" note (`:51-53`) and update the header table (`:37`).
2. Bench switches:
   - `SOMEONES_HOME_ROUND_PULSE=miss`: this seat taps 700 ms off the grid, to exercise the lockout and OFF THE BEAT on screen;
   - `SOMEONES_HOME_CLOCK_SKEW_MS` (from C1), set per seat by `hunt.sh` (`--skew`).
3. `tools/sim-hunt/hunt.sh`:
   - `--nodes` already alternates `ROUND_NODE`. Add `--skew`, which gives seats +2 500 ms and −1 800 ms.
   - Expected ledger lines: `CONTAIN` (the screen reached), `RETURNING THE SYNC PULSE`, `ON THE BEAT`, `[house] EGRESS CONTAINED`. The round must go on to a meeting or ending.
   - Grep the ledger for `EgressUncontained` in runs where both node seats were alive.

**Tests**
- `app/src/cheatsTest/…/ContainmentWiringTest.kt` (new; the `AbilityWiringTest` pattern): five phones over loopback with a real `TransportHost`, real `TransportClient`s with **skewed clocks** (+3 s, −2 s, 0, +1 s, −0.5 s), the real desk, real authority, real `EffectWire`, and U1's slot uplink. The Insider holds EGRESS through `flow.actions()`. Cases:
  - (a) Two Residents scan the two node cards through the scan flow, reach `Contain`, and tap on their own phones' pulse grid. Every phone gets `EgressContained`, every widget reverts, and `egressesContained == 1` everywhere.
  - (b) The same pair at the **same** node does not contain.
  - (c) One taps off the grid: OFF THE BEAT, the wait, a retry on the grid, contained.
  - (d) An **Insider** at one node and a Resident at the other contain, with the same frames as (a).
  - (e) Role-swapped rerun of (a): per-seat frame **counts and lengths** are identical to the original, apart from the actor's answer (which U1 covers) and the tappers' `SyncPulseAnswered`, which must be the same width for both values.
  - (f) **Break it**: send `pulseFrom = 0` and (a) must fail by expiry. Skew a client without `ClockSync` and (a) must fail.
- After merge, the orchestrator runs `:app:cheatsTest`, `:core:allTests`, `:ui:desktopTest` and `./gradlew check --continue` unfiltered and reads the XML. Then `hunt.sh --seats 5 --nodes --skew`, looking at the screenshots of both node seats on `Contain`.

---

### C6. Two phones (owner, physical devices; the Simulator cannot run haptics)

1. Two iPhones in one room. Film both screens and hands at 240 fps on one node scan each: the `PULSE` buzzes must line up within ±150 ms (GDD:1291).
2. Two people at two nodes in different rooms: contain three times out of three by feel. Then one person taps deliberately late: OFF THE BEAT, WAIT, retry.
3. Lock the host phone mid-Egress for 20 s. Expected behaviour depends on PACK Q4.
4. The `[clock] FIX` lines on both phones: RTT and offset are plausible (RTT under 30 ms on home Wi-Fi).

---

## 4. Owner decisions needed before building

Only what the docs genuinely leave open. The arm window (PACK Q1/Q2a), Egress progress for the living (Q3) and the host lock (Q4) are already in the pack. **Q4 must be ruled before C5 lands**, or containment can still be beaten by locking the host.

| # | Question | Recommendation | Why |
|---|---|---|---|
| **O1** | A node card that also holds work for the scanner (a Subroutine, or an Insider's fake): what does a scan open while the Egress runs? | **Containment only. No work opens at either node, for any seat, while the Egress runs unpaused.** Work there opens normally before the fire, after containment, and during a meeting pause. | D-162 says the scan opens containment. Two routes from one scan would be ambiguous in the dark. The rule reads only public facts, and it treats both roles identically. |
| **O2** | Which phones feel the pulse? | **Only phones on the containment screen** (people who scanned a node). | GDD:371 says "both phones". House-wide buzzing every 1.5 s for two minutes would be noise in every hand and add nothing. Position, not role, decides it. |
| **O3** | What counts as "four taps on the beat" and "simultaneously"? The code allows any four distinct beats, and pairs by upload arrival within 6 s. | **Four consecutive pulses, each within ±400 ms. A pair is two runs at the two nodes that share at least one pulse.** | This matches GDD:1014's "same beat" and :369's "simultaneously" without forcing two silent people to start on the same beat. It is measured on the house's pulse grid, so U1's upload delay cannot split a real pair. |
| **O4** | What the containment screen says and does | `EGRESS . <TYPE>` · the room · the countdown · **TAP WITH THE PULSE** · 4 pips · `ON/OFF THE BEAT` · **WAIT for 3 s after every OFF THE BEAT**. No visual pulse. Copy is provisional. | GDD and the mockups say nothing. "Screens say little." A uniform wait after any OFF THE BEAT shows the lockout without revealing why the beat failed. The pulse is felt, not drawn (rule 5). |
| **O5** | Accept that whoever taps at a node gets one extra downward frame (`SyncPulseAnswered`), now fixed width? | **Accept for v1.** It reveals an act at a node, which people in the room can already see; it does not reveal role (D-258's S2 class). Later, fold answers into U1's per-cadence `UploadHeard` frame. | It is a leak acceptance, so it is the owner's call (project-context: ask about what a client may receive). |

Calls this spec makes without the owner, flagged for the decision log:
- **Pulse times are host-monotonic milliseconds**, recorded as payload. Event `at` stays a tick (ARCH D5). The reason is in C2.
- **Clock probes stay as transport frames**, outside U1's request slot.
- **A Revoked partner's held offer no longer pairs.** This fixes a gap; the GDD's "two people at two separate markers" implies two living people.
- **SyncPulseAnswered becomes a digit**, the same as D-258.
