# Someone's Home — Development Epics

**Companion to `gdd.md`.** Fourteen epics. `gdd.md` carries the summary table; this file carries the breakdown.

**Stack: Kotlin Multiplatform + Compose Multiplatform.** Shared Kotlin carries the simulation core *and* the entire interface; `expect`/`actual` carries the platform layer (radio, torch, camera, motion, haptics, NFC, audio) per side. **The interface is never built twice.** See `game-architecture.md` → Engine & Framework.

**E1 now carries a stack gate** (story 1.7). It is not a gate for the *game* — the coordinated lights-up moment is already validated from prior play with smart-home lights — but it is a gate for the *technology*.

**Sequencing principle: build straight through to the full product.** E1 is the first increment, not a go/no-go gate. **The core beat is already validated** — the coordinated lights-up moment was reproduced in prior play with smart-home lights (*"Alexa, turn on all the lights"* at a meeting call) and it worked. There is no open question worth stalling the build for.

**Two questions still get answered, just as instrumentation rather than as a gate:** whether a dim amber screen alone produces the ~1-metre bubble (**the palette is cheap to change if not** — it is four luminance values, not an art pipeline), and whether the target's lamp dying at the instant of contact conceals the attacker. Both are watched in the first real playtest; neither blocks anything.

**Standing constraints that apply to every epic** — violating any of these is a defect regardless of the story's acceptance criteria:

1. **The app never confirms anyone's alignment to anyone**, by any path, at any time.
2. **Every observable effect must be self-inflictable**, and self-targeting shares the ability's cooldown.
3. **No actor-side feedback on firing any ability.** The attacker's device stays inert.
4. **The System Integrity denominator never moves** once the round starts.
5. **Core state signals are never fakeable.** No amendment needed — the deactivation report is verified by physical contact.
6. **Audio modulates only on global events.** Light is private per device; sound is not.
7. **Both roles' screens are structurally identical** — same pages, same page-dot count, same panel furniture.
8. **The system may know things it never shows.** Rendering rules and radio rules are separate; conflating them produced the one finding in the round trace that was simply wrong.
9. **Physical conduct rules are social contract, never enforced in software** — don't run, don't speak, don't dodge, don't conceal your phone. The app checks none of them, exactly as it never checks whether you spoke.

---

## E0 — Foundations *(new — surfaced during architecture)*

**Goal:** the three systems that appear in no other epic and are expensive to retrofit. **Built at the front because everything downstream depends on them being there.**

| # | Story |
|---|---|
| 0.1 | **Simulation core skeleton** — pure, synchronous, deterministic. Ordered timestamped events in; new state plus a list of effects out. **No platform types: no dates, no radio types, no UI types** |
| 0.2 | **Fixed-timestep tick**, decoupled from render. Display-link equivalents are variable-rate and throttle thermally, so they cannot drive a replayable simulation |
| 0.3 | **Session recording** — capture every event and every effect for a whole round |
| 0.4 | **Deterministic replay** — feed a recording back and reproduce the round exactly. Seed all randomness (error injection, unlock delays, node selection) |
| 0.5 | **Client transcript recorder** at the effect boundary — every byte destined for a given client, captured per client |
| 0.6 | **Differential leak harness** — run a seeded round twice with a player's role swapped; diff the two client transcripts. **Catches role-ASYMMETRIC leaks only** |
| 0.6b | **Per-message schema allowlist at the emit boundary** — an explicit list of what each client class may receive. **Independently required, because 0.6 is structurally blind to symmetric leaks**: both its transcripts come from the same redaction code, so a bug that ships Egress progress to *everyone* passes clean. Not "did it differ" but "was it ever permitted" |
| 0.6c | **Radio-level emission test** — sniff what the antenna actually broadcasts, especially when backgrounded. **Sits below the effect boundary, so neither 0.6 nor 0.6b can see it.** iOS moves service UUIDs to the overflow area and drops the local name on background; the rotating anonymity token may break rather than merely weaken |
| 0.7 | **Map persistence** across rounds and across evenings, surviving app reinstall. The setup walk is ~15 minutes of work and must not evaporate |
| 0.8 | **Disconnect / rejoin** — a phone that dies, crashes, or takes a call mid-round. **Bounded exponential backoff; the lamp holds its last authorised state throughout** (an unauthorised lamp change is an unauthored game signal). Resume presents the stored **seat token** — never re-derive identity from the lobby code, or the attribution hole is rebuilt in the one path nobody tests. Includes the event-protocol **ack timeout** (2 s) |
| 0.9 | **Host-failure UX.** Authority is the host device with no migration (v2), so this *will* happen. **Lamps hold their last state** — every light in a dark house going out at once is a safety event, not a bug report. A legible message; nobody left playing a game that stopped existing |
| 0.10b | **Three build variants — release / playtest / debug.** Playtest = recording on, cheats on, debug surfaces compiled out, **permanent visible marker**. Without it the monthly session yields one round instead of four; with a release build you cannot skip a meeting or reset a broken round |
| 0.10d | **Fixtures snapshot out of recordings**, at interesting ticks — first revocation, chain collapse, parity threshold, mid-Egress. **Hand-written State builders encode the tester's imagination**, the same failure as scripted players; recorded states are produced by the actual rules and regenerate when the rules change |
| 0.10e | **Event-triggered performance instrumentation** in the playtest build: **blackout latency** (contact → lamp-dark; the number the anonymity guarantee rests on), scan acquisition, per-device flip skew, dropped motion windows. **Not average FPS** — you care about one frame in ninety thousand, and averages eat it whole |
| 0.10c | **Fuzzing policies for headless simulation** — camping, wandering, idling, ability-spamming, random. **Scripted "reasonable" players only confirm your assumptions at scale**; the absurd ones find crashes and unstaffable chains |
| 0.10 | **Host crash recovery** — relaunch and resume the round from the local recording. Same device, same authority, no election. **Not migration**, and nearly free given 0.3/0.4 |

**Why this is E0 and not E14.** Eight phones, a dark house, enforced silence, randomized fog, and players who lie by design. **You cannot attach a debugger, cannot ask what happened, and cannot reproduce the round.** Every bug in the four catastrophic-blast-radius systems has the form *"someone saw something they shouldn't have."* Built here, everything downstream is debuggable; built late, the intervening months are spent guessing.

**And the test rig is eight iPhones in one dark room** — realistically a monthly event, not a development loop. Headless simulation of the core is what makes the playtest about *feel* rather than *correctness*.

**Acceptance:** a 25-minute 8-player round replays byte-identically from its recording. The differential harness runs a role-swapped pair and reports zero unexplained divergence.

---

## E1 — The lamp

**Goal:** the lamp, the torch, and the synchronized flip. Everything downstream renders on top of this, so it goes first — not as a gate, but because it is the floor.

| # | Story |
|---|---|
| 1.1 | Full-screen amber lamp at four luminance steps, with a brightness dial |
| 1.2 | Native torch-level control behind `expect`/`actual` (iOS: `setTorchModeOn(level:)`, 0.0–1.0) |
| 1.3 | Screen-brightness override and keep-awake |
| 1.4 | A gesture that flips the lamp to full white |
| 1.5 | A button that flips **every connected phone** to full white at once, via **broadcast → ack → commit** on a scheduled timestamp — not a broadcast "go". Ack timeout required |
| 1.6 | Instrumentation: battery draw over 25 minutes with camera, torch and screen live |
| **1.7a** | **STACK GATE, part 1 — shader stalls.** Can Compose Multiplatform (Skia→Metal) blank the lamp in the same frame as a contact event? Verify on a high-frame-rate camera. **Mitigation: pre-warm the blackout draw path invisibly at round start**, so any stall is paid at arming rather than at the moment that decides whether a revoke is anonymous |
| **1.7b** | **STACK GATE, part 2 — GC pauses. This is the worse one.** Kotlin/Native uses a tracing collector, and a shader stall is a *first-run* problem you can pre-warm away while **a GC pause can hit any frame, forever.** Establish a **no-allocation discipline on the blackout path** and enforce it with a **permanent allocation assertion**, not a one-time measurement. **If either gate fails, the fallback is Flutter** — same benefits, cost of a third language |

**Acceptance:**

- Devices flip within **±150 ms**, verified on 240 fps video — "reads as one event." Delivery latency is irrelevant; the event is scheduled ahead behind a calling screen, so only clock-offset accuracy is under test.
- Battery instrumentation reports cleanly over a full-length round.
- **THE STACK GATE (story 1.7): the lamp blanks in the same frame as a contact event.**

> **⚠️ Two things about how 1.7 must be measured, or it reports a meaningless pass.**
>
> **1. The GC half needs manufactured allocation pressure.** A GC pause only happens when there is garbage to collect. A minimal spike — one amber screen, a tap, a timestamp — allocates almost nothing, never triggers a collection inside the test window, and returns a clean result. **You would conclude Kotlin/Native GC is a non-issue and be wrong**, because the real app allocates constantly: BLE callbacks, 100 Hz motion samples, effect objects, recording writes. The spike must generate representative allocation on other threads while the blackout fires, or 1.7b is a formality rather than a test.
>
> **2. The measurement is two-layered, and the camera is not the primary instrument.** A 240 fps camera gives ~30 samples in a session; the gate is about **one frame in ninety thousand**. You cannot find a p99.9 tail with thirty samples.
> - **In-app instrumentation for volume** — trigger timestamp → frame-presented timestamp, thousands of trials. **Read the tail, never the mean.**
> - **Camera for calibration** — ~20–30 samples proving the in-app number corresponds to photons actually leaving the glass.
>
> The camera validates the instrument; the instrument finds the tail. **Neither alone answers the gate.**

> **Trigger source for the spike: a screen tap, with the trigger kept pluggable.** The gate measures *trigger → pixels dark*, and the rendering pipeline does not care what produced the trigger. Using a real BLE contact handshake needs two devices and drags the radio risk — a separate question with its own test, story 0.6c — into a rendering test. Keep them apart.

**Watched, not gated:** whether a dim amber screen alone produces the ~1-metre bubble. If it does, the Tier 0 promise holds with no accessory. If it does not, the palette moves — four luminance values, cheap to change — or the snoot becomes more load-bearing than planned.

---

## E2 — Session, sync, and arming

**Goal:** the clock infrastructure everything else rides on, plus the round's opening beat.

| # | Story |
|---|---|
| 2.1 | Host creates a session and **owns the server**. A downed device is a game **state**, never a real process death |
| 2.2 | Join by code; each client measures its clock offset from server time at join |
| 2.3 | **Scheduled-timestamp event scheduling** — broadcast "event at T," ~2s ahead; clients schedule locally. One mechanism, three uses: arming, the lights-up snap, Sync Pulse |
| 2.4 | Lobby settings: player count, Insider count, `Known`/`Hidden`, discussion time, voting time, cooldowns, Egress timer, Access pool, meeting cooldown |
| 2.5 | Role assignment at arming. Nothing shown before |
| 2.6 | **Perimeter arming** — all phones flip simultaneously; `ARMED` enters the status bar for the whole round |
| 2.7 | **Role reveal by text message.** Insider: blackmail text → house line in customer-service register → fellow Insiders if `Known`. Resident: flat administrative work order |
| 2.8 | **F-003 — identical message count and haptic pattern across roles.** Buzz count is a tell in a silent room at the exact moment everyone is still clustered. Pad the Resident sequence, or deliver the whole reveal as one batch with a single haptic |
| 2.9 | **Transport — decided.** Embedded **Ktor 3.5.1** server on the host, **mDNS** discovery (NSD / Bonjour behind `expect`/`actual`), **websockets**. All multiplatform Kotlin, so transport is in the shared column. No-wifi fallback is **manual Personal Hotspot**, documented in the play manual — iOS will not let an app toggle it |
| 2.10 | **Warm the iOS Local Network permission during setup, in the light.** First local-network discovery raises a system modal; firing it at arming in a dark room is the same failure as the NFC sheet |
| 2.11b | **Client attribution — session token bound to a SEAT, issued at join.** Intents are attributed **by connection**; a client never names itself, so it cannot claim to be another player. **A lobby code gets you *a* seat, never *that* seat.** Without this, a second websocket asserting it is Marcus is accepted — the only cheat in this game that is remote, undetectable, and requires no physical act |
| 2.11 | **Clock.** Monotonic timebase; NTP-style offset at join taking the **minimum-RTT** sample; **re-sync every 30 s**; **slew, never jump** — a backward jump could skip a scheduled event |

**Acceptance:** two phones in different rooms flip within ±50 ms. A Resident and a Insider standing side by side receive indistinguishable haptic activity at arming.

---

## E3 — The device shell

**Goal:** the diegetic phone OS. **Every rule the player would have to remember becomes a state the device is in.**

| # | Story |
|---|---|
| 3.1 | Springboard: **two pages, role-identical**, same icons, same positions, **same page-dot count** |
| 3.2 | Status bar with `ARMED`, and the System Integrity widget |
| 3.3 | **Terminal** — reads `NO SIGNAL` everywhere but the map station |
| 3.4 | **Files** — empty; holds exactly one item while carrying; every app needing storage refuses |
| 3.5 | **Notes** — accepts typing, never saves |
| 3.6 | **Messages** — receives, cannot send |
| 3.7 | **Settings** — mostly locked. Lamp control and *About* have content; **End Session** for the host |
| 3.8 | **Status panel** — long-press bottom-left ~400 ms. Identical layout for both roles; bottom row is abilities for Insiders and inert readouts for Residents |
| 3.9 | **Second-confirm on every ability fire.** Opening the panel is cheap; firing must not be |
| 3.10 | Incoming-call screen component (used by both meeting triggers), with the caller named |
| 3.12 | **The ring.** Full-volume ringtone on the call screen, **identical for both trigger types**, playing **regardless of the hardware silent switch** (audio session category — standard iOS gotcha). Stops on acknowledgement or when lights-up completes; **never rings indefinitely**, or a slow player is localized by their own phone |
| 3.11 | Ambient lamp flicker as permanent system behaviour — **not an ability** |

**Acceptance:** a shoulder-surf at close range cannot distinguish a Insider's device from a Resident's in any state, including the Status panel.

---

## E4 — The house

**Goal:** the host's actual house becomes the board, in one ~15-minute walk, once ever.

| # | Story |
|---|---|
| 4.1 | **Grid painter**, one plan per storey. Drag cells to define a shape |
| 4.2 | Tag a shape as **room / passage / stairs**; name it |
| 4.3 | **Additive floors** — Floor 0 first, renameable, add more. **No vertical-connection logic** |
| 4.4 | Drop markers into single cells |
| 4.5 | Designate the **Terminal** and the three Array Wipe markers (**Spares, Rack, Disposal**) |
| 4.6 | **Editor exclusion:** no marker in a stairs zone |
| 4.7 | **Editor exclusion:** no marker and no Terminal in a passage-tagged room. **A Terminal behind a Insider-only door is unrecoverable** |
| 4.8 | **Host acknowledgment screen, fired at the moment a staircase is tagged** — not in a ToS |
| 4.9 | **Adjacency derived from cell neighbours.** Consumed by error injection and Egress node selection; no geometry code |
| 4.10 | Couch / meeting area designation — a **mapping dead zone** |
| 4.11 | QR marker PDF generation for printing at home |
| 4.12 | **Snoot printable** — tube with fold instructions, plus the **optional end cap** for hardware without fine-grained torch control. Cap the tube; never tape paper across the lens (thermal + optical) |
| 4.13 | **The play manual** — the only thing a player reads. The four physical conduct rules (don't run / speak / dodge / conceal your phone), snoot folding, host setup guidance, the photosensitivity notice, and how to explain the contact tap in ten seconds. **Everything else in this design refuses to explain itself on purpose; this is the exception** |

**Acceptance:** an L-shaped room is expressible. Adjacency is queryable without geometry. The editor refuses both exclusions with a legible reason.

---

## E5 — Scanning and the motion budget

**Goal:** the single most-repeated interaction in the game, and the anti-cheat that makes standing still mean something.

| # | Story |
|---|---|
| 5.1 | **Hold-to-scan**, ~0.5 s acquire, haptic tick on detection, release |
| 5.2 | **Preview-less capture session.** The lamp screen *is* the scanner — no "open scanner" step exists |
| 5.3 | **Routing** — the marker ID selects which subroutine opens, if the player has one there |
| 5.4 | **Check-in** — always logs, task or no task. Decoupled from routing |
| 5.5 | **Motion accumulator** at 100 Hz on gravity-removed acceleration magnitude |
| 5.6 | **Per-player calibration** over the first ~0.5 s on entry |
| 5.7 | **Both bounds** — too much *and* too little motion fail. A phone on a table has a noise floor below hand-held tremor |
| 5.8 | **Strict on translation, lenient on rotation.** Let players turn and check their back |
| 5.9 | **Budget scales with tier.** Strict for a 5 s short; forgiving for a 60 s circuit leg |
| 5.10 | **The draining meter, always visible.** Failure must never be a surprise |
| 5.11 | Sweep for nearby devices on every scan, and every 5 s during a motion-restricted task |
| 5.12 | NFC path behind the same hold button (after the iOS spike) |
| 5.13b | **BLE identity — ephemeral rotating tokens, 15 s, authority-resolvable only.** A stable advertised ID lets any client track an individual all round, which makes *counts, never identities* decorative. The leak is in the radio, below the server, so server-side anonymization cannot fix it. Advertise the token and nothing else — no role, no name, no stable ID |
| 5.13 | **The contact primitive** — one very-tight-RSSI device-to-device handshake, presented to the player as touching phones. Serves both the Revoke and the deactivation report; outcome determined purely by the two devices' states. **Not NFC — iOS exposes no phone-to-phone NFC.** UWB optional as a precision upgrade |
| 5.14 | **Gate the handshake at contact range, as a hard constraint.** At "nearby" it becomes a through-wall detector for revoked players and *you can walk past a body and never notice* stops being true |

**Acceptance:** setting the phone down fails. Turning to look behind you does not. Two players report the meter felt fair without being told it was calibrated.

---

## E6 — The subroutine set

**Goal:** ten subroutines, each a distinct cognitive mode, each a distinct point on the light-signature axis.

| # | Story |
|---|---|
| 6.1 | **Handshake** (medium, dark, haptic echo) — **build this first.** Maximum concealment, total blindness, the clearest expression of the light-signature axis |
| 6.2 | **Sniff** (short, dark, haptic counting) — the only *short dark* subroutine, and structurally the most important cell in the grid |
| 6.3 | **Short** (short, dark, gross motor) — both hands on the glass for two seconds |
| 6.4 | **Replay**, **Parity Check**, **Deallocate** (short, bright) |
| 6.5 | **Interrupt** (short, medium light) |
| 6.6 | **Drift**, **Jam** (medium) |
| 6.7 | **Signal Trace** (medium) with **BFS-generated graphs**; difficulty tunes by decoy count |
| 6.8 | **Fake versions of every subroutine** — real UI, real progress, real completion animation, writes nothing to the ledger. **Cheap now, awkward to retrofit** |
| 6.9 | **Degrading subroutines** — glitchier and harder as System Integrity drops. **Load-bearing v1, not v2**: with the doom clock hidden from both roles, this is the only signal that the house is winning |

**Every subroutine must pass all five constraints:** legible at the dimmest amber step · silent or haptic-only · interruptible with unambiguous progress state · no twitch timing · no precise dragging.

**And the bench rule:** *comparing quantities is perception; adding numbers is computation.* Any subroutine showing a numeral is suspect.

---

## E7 — Task structures

**Goal:** scheduling, carrying, chaining, and the bar.

| # | Story |
|---|---|
| 7.1 | Assignment: **7 per Resident — 1 circuit, 1 long, 3 medium, 2 short** |
| 7.2 | **System Integrity** starts at **`7 × initial_residents`**, decrements by 1 per completion. **F-005 — this replaces the 32 → 0 figure, which does not match the assigned count** |
| 7.3 | **Denominator frozen at arming.** Recompute internally; never move the displayed total |
| 7.4 | **Orphaned subroutines silently auto-satisfied** (revoked holder, collapsed chain) so the bar stays winnable |
| 7.5 | Updated **only at meetings**, frozen at meeting start. **Exception:** reaching 0 wins immediately |
| 7.6 | **Memory Dump** — stage 1 and stage 2 at *different* markers; 4–5 elements; all-or-nothing; mismatch reports only "mismatch"; failure returns to source for a fresh pattern |
| 7.7 | **F-015 — Files must never show the Memory Dump pattern.** Only your head is off-network |
| 7.8 | **Array Wipe** — Spares → Rack → Disposal, one carry at a time, two damaged disks, four traverses |
| 7.9 | **While carrying, no other subroutine can be scanned.** This is what makes Isolate on Disposal devastating |
| 7.10 | **F-014 — both carries persist across a House Meeting.** Default implementations clear transient state on a phase change; that is wrong here, twice |
| 7.11 | **Chains:** Resident-only, `L ≤ living_guests + 1`, re-checked at every extension |
| 7.12 | **Lazy linking, sliding window of two.** Next link chosen at resolution time from living eligible players |
| 7.13 | **Randomized unlock delay 15–60 s, identical distribution on both paths** (completed *and* revoked). **F-013 — revised down from 20–120 s for the 15-minute round** |
| 7.14 | **Upstream notification at assignment only.** Never confirm the downstream unlock happened |
| 7.15 | **Graceful collapse** when a chain cannot be staffed |
| 7.16 | Chain seeding — each link's output seeds the next, so a chain reads as a pipeline rather than three numbered errands |

**Acceptance:** a chain whose holder is revoked behaves identically, from the waiting player's side, to one whose holder completed. Verified by inspection of both code paths and by a blind playtest.

---

## E8 — The Terminal

**Goal:** the surveillance system, and everything that makes it honest about being bad.

| # | Story |
|---|---|
| 8.1 | Terminal readable **only at the map station**; `NO SIGNAL` elsewhere |
| 8.2 | **Exclusive access — one reader at a time.** Others may stand there |
| 8.3 | Viewing is an **uncompletable task** — the motion budget applies, and the exposure *is* the limit |
| 8.4 | The station **generates its own check-in** |
| 8.5 | **Live counts per room, never dots.** Your own room outlined as the anchor, visible only to you |
| 8.6 | **Three staleness bands.** Jitter the age before bucketing — **signed, rolled once per observation, stored** |
| 8.7 | **Error injection both directions.** Plausible (adjacent rooms only), rolled once at capture, **identical rates for both roles** |
| 8.8 | **F-007 — false negatives must work harder under counts than under dots.** Hold a room's last count briefly after loss of detection; bias error toward under-counting. **Re-verify the staleness-leak analysis against counts before building** |
| 8.9 | **Timelapse:** 2:00 history, 6×, 20 s playback, plays once, oldest→newest |
| 8.10 | **Cooldown 2:00 from playback end**, creating the self-inflicted blind spot. Early cancel reduces it linearly |
| 8.11 | **Cooldown scales to available history** — right after a meeting, both shrink together |
| 8.12 | **Floor = start of round or end of last meeting**, whichever is later |
| 8.13 | **F-008 — no dot interpolation.** It is dead under counts. Animate the numeral's count-up/down instead |
| 8.14 | Stairs drawn as transit zones — **never counted** |
| 8.15 | Couch area produces no data |
| 8.16 | **Anonymize server-side.** Resolve identity on the backend; ship only counts |
| 8.17 | **Log backgrounding as a game event**, or undesigned phantom false negatives will appear |

---

## E9 — Insider abilities

**Goal:** the roster, operated by feel, in the dark, one-handed, under stress.

| # | Story |
|---|---|
| 9.1 | **Revoke — arm plus contact.** Arm silently (nothing changes on any screen), 45 s window, then **touch phone to phone**. No confirmation step on either device, and **no self-deactivate control exists anywhere in the game** |
| 9.2 | **Cooldown starts on arm, not on the landing** |
| 9.3 | **F-004 — initial cooldown of 45 s at arming** for Revoke and Egress |
| 9.4 | Revoked state: device dark and inert **to the player**, lamp out — but **the radio stays up: advertises, never scans.** Excluded from rendered counts, which is a rendering rule, not a radio rule |
| 9.5 | **Egress** — house picks Beacon or Tether; **host-configurable timer, 120 s default**; **dismissable alert** |
| 9.5b | **Suggest the Egress timer from the painted grid.** The longest marker-to-marker cell path is already computable from the setup walk, so the default adapts per house instead of shipping a constant. Host can always override |
| 9.6 | **F-001 — node selection: two ordinary task markers in non-adjacent rooms, chosen at fire time.** No new setup step |
| 9.7 | **F-002 — Egress nodes are immune to Isolate for the duration**, and firing Isolate at one still appears to succeed |
| 9.8 | **Sync Pulse containment** — both phones pulse in unison off a scheduled timestamp; tap on the beat. Unlimited participants, concurrent pairs, first success contains, 2–3 s failed-attempt lockout |
| 9.9 | **The System Integrity widget becomes the countdown and names both nodes** |
| 9.10 | **Surge** — lamp to full, then to black, **and** the target's phone emits sound. One event. **Longest cooldown in the Access pool** |
| 9.11 | **Spoof** — false check-in in a room you are not in |
| 9.12 | **Isolate** — take a marker or the Terminal offline temporarily |
| 9.13 | **Override** — passive; passage-tagged interior doors. **No UI at all**, and never tracked |
| 9.14 | **Access pool** shared by Tier 2 only. Revoke and Egress each have their own |
| 9.15 | **Self-targeting on every ability, drawing from the same cooldown** |
| 9.16 | **Targeting an already-revoked player appears to succeed** — same animation, same cooldown spent, no feedback. Otherwise the panel is a revoke-detector |
| 9.17 | **Screen parity:** fake list structurally plausible for this map, **sometimes showing a waiting state**, with **a counter that advances** |
| 9.18 | **OI-1 — stairs suppression for Surge is deferred.** Ships without it; the four detection-free stairs mitigations carry v1. **Revisit before public launch, not before v1** |
| 9.19 | **The target's lamp must die in the same frame as contact.** It is the only thing lighting the attacker, so the blackout latency *is* the anonymity of the revoke. Verify in a real dark room |

---

## E10 — The House Meeting

**Goal:** the game's dramatic centrepiece, with the phone doing as little as possible.

| # | Story |
|---|---|
| 10.1 | Trigger rendered as an **incoming call with the caller named** |
| 10.2 | **Both triggers ship unchanged.** The deactivation report is verified by physical contact, so it is not a fakeable signal and no rule amendment is needed. **Name the caller, never the room** |
| 10.3 | **All lamps to full white simultaneously**, off a scheduled timestamp |
| 10.4 | **Masking audio cuts to dead silence**, and **every phone rings**, on that same timestamp. The ring lands in the silence the cut just created |
| 10.5 | System Integrity updates and **freezes** at meeting start |
| 10.6 | **Discussion, 90 s.** Timer and one control. **Unanimous "Ready to vote" skips ahead.** Living players only |
| 10.7 | **Vote, 45 s.** Names plus Skip, changeable until the clock ends. **Count of votes cast shown, never their content.** Not voting is an abstention |
| 10.8 | **Ghosts see the vote and cast nothing** |
| 10.9 | **Result:** most votes **restrained**, **ties resolve to Skip**, **attribution shown**, then nothing — no role, no confirmation, no reveal |
| 10.9b | **The house deauthorises anyone the group restrains**, moments after the tally. The group holds them; the system finishes the job. **Restrain is a physical act the house cannot prevent — the deauthorisation is the house's response to it, not its cause** |
| 10.10 | **Meeting end: all lamps drop to dim in unison** — the "go" signal. Masking resumes |
| 10.11 | **Timelapse floor resets to now** |
| 10.14 | **The house notice.** Any device unreachable or backgrounded for a meaningful interval is reported to **everyone, at the meeting**, in the house's administrative register. Radio failure and backgrounding trigger it identically. **Needs no anti-exploit — being named off-network is an accusation, not an alibi** |
| 10.12 | Egress blocked during meetings; Revoke cooldown pauses or resets at meeting end |
| 10.13 | Revoked players prompted to stand and walk to the couch (ghost phase 2), **phone still dead** |

---

## E11 — Ghost mode

**Goal:** solve the failure state of every social deduction game. **The sequencing is the design.**

| # | Story |
|---|---|
| 11.1 | **Phase 1** — revoked, device dark and inert to the player, radio advertising and never scanning, sit where you are with the phone in your lap and reachable |
| 11.2 | **Phase 2** — a meeting is called: stand, walk to the couch, phone still dead |
| 11.3 | **Phase 3** — the meeting *ends*: ghost mode appears. **Never before.** There must never be a window where a ghost knows something the living do not |
| 11.4 | **Both bars** — System Integrity *and* the real Egress progress, which no living player of either role can see |
| 11.5 | **True occupancy** — live, every storey, no injected error, no staleness bands. **Counts, never identities** |
| 11.6 | **Never alignments.** The tools to deduce, never the answer |
| 11.7 | **No channel of any kind** — cannot act, speak, vote, or message other ghosts |

**Acceptance:** two ghosts side by side have no mechanism to communicate anything. **Instrument ghost dead time (revoke → phase 3) and report it every playtest** — it is the number that decides OI-6, and meeting cooldown is the only lever on it.

---

## E12 — Endgame and reveal

| # | Story |
|---|---|
| 12.1 | **Residents win** — System Integrity reaches 0, immediately, no meeting. The completing player gets no distinct experience beyond "you won" |
| 12.2 | **`PERIMETER DISARMED`** replaces `ARMED` in the status bar |
| 12.3 | **Insiders win (parity)** — `residents_alive ≤ guests_alive`, counting **living** Insiders. A Insider who revoked a Insider is never told |
| 12.4 | **Insiders win (outright)** — an Egress runs its clock out |
| 12.5 | **Reveal, fixed order:** the house speaks last (*"Thank you for your cooperation"* / *"Unfortunate"*) → **lights up and the Insiders stand** (the room does this, not a screen) → **the blackmail publishes**, so everyone learns who *and why* |
| 12.6 | Chain membership exposed here and nowhere else |
| 12.7 | **No round replay.** It competes with the room |

---

## E13 — Audio

| # | Story |
|---|---|
| 13.1 | Masking track generation — low-frequency content that actually overlaps footsteps (~50–200 Hz) |
| 13.2 | Host playout to any household speaker over AirPlay / Bluetooth (**Tier 0**) |
| 13.3 | **Swell during an Egress** |
| 13.4 | **Cut to dead silence at a meeting call**, on the same scheduled timestamp as the lights |
| 13.5 | **Global events only.** No cue may fire for a subset of players. **The meeting ring is the sole permitted phone-emitted sound and is global by construction — it is not a precedent for audio anywhere else** |
| 13.6 | **No audio in any subroutine, ever** |

**Never build:** microphone access, in any form, for any reason. The fiction says the house listens; the app must never listen.

---

## Build order

**Story 1.7 first, as a standalone spike.** One screen, one question: does the lamp blank in the same frame as contact on Compose Multiplatform? **A stack change would invalidate every line of E0**, so this runs before anything is committed to.

**Then E0 → E1 → E2 → E3 → E4 → E5 → E6 → E7** is the critical path to a playable round; **E8, E9, E10** can start once E5 and E7 land; **E11, E12, E13** close it out.

**Things that must be built earlier than their epic number suggests, because they are expensive or impossible to retrofit:**

- **All of E0.** Recording, replay and the differential harness are the only way this game is debuggable at all.
- **The fake subroutines (6.8)** — build alongside the real ones, never after.
- **Scheduled-timestamp event scheduling (2.3)** — load-bearing for four separate features (arming, lights-up, Sync Pulse, the ring) and cannot be bolted on.
- **Server-side anonymization (8.16)** — free at the start, invasive later.
- **The no-platform-types discipline in the core (0.1)** — it is what makes replay, headless testing, and any future port possible. One slip and all three degrade.

**No epic is blocked on a decision.** OI-1 is deferred with its mitigation set intact and OI-2 closed on inspection.

**Two things to watch rather than gate on**, both cheap to observe once there is a playable round: whether a dim amber lamp alone produces the ~1-metre bubble (E1), and whether the target's lamp dying at the instant of contact conceals the attacker (story 9.19). **The second is the one with real consequences** — if contact does not conceal, the anonymous revoke is gone and ghost mode's information design needs revisiting. Worth watching from the first playtest that includes a live Revoke.
