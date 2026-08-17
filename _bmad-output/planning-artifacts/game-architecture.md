---
title: 'Game Architecture'
project: "Someone's Home"
date: '2026-08-16'
author: 'Vadmanuel'
version: '1.0'
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8, 9]
status: 'complete'
engine: 'Kotlin Multiplatform + Compose Multiplatform 1.11.1'
platform: 'iOS 26+ (iPhone first), Android on roadmap'

# Source Documents
gdd: '_bmad-output/planning-artifacts/gdds/gdd-someone-is-home-2026-08-16/gdd.md'
epics: '_bmad-output/planning-artifacts/gdds/gdd-someone-is-home-2026-08-16/epics.md'
decision_log: '_bmad-output/planning-artifacts/gdds/gdd-someone-is-home-2026-08-16/decision-log.md'
brief: null
narrative: null
---

## Executive Summary

**Someone's Home** — a co-located social deduction game played in the host's own house, in the dark, in enforced silence, with each player's phone as their only light and only interface.

**Stack:** Kotlin Multiplatform + Compose Multiplatform. iPhone first, Android on the roadmap, **one shared UI and one shared simulation core** — the interface is never built twice.

**The three decisions everything else follows from:**

1. **An event-sourced pure core with strict server authority.** Clients send Intents and render Effects; they hold no game logic and no game state. **Forced by the anonymity rule, not chosen for elegance — a client cannot leak what it was never sent.**
2. **Determinism is a requirement, not a nicety.** Fixed-timestep simulation, seeded randomness, no platform types in the core, ordered collections. It is what makes deterministic replay possible, and **replay is the only thing that makes this game debuggable** — eight phones, a dark house, silence, randomized fog, and players lying by design.
3. **A large share of requirements are *negative*, and negative requirements need three independent tests.** Role-asymmetric leaks (differential harness), symmetric leaks (per-message schema allowlist), and below-the-effect-boundary leaks (radio sniffing). **A green build on any one says nothing about the other two.**

**Structure:** six modules, where every boundary enforces something load-bearing — `core` cannot see coroutines or datetime; `ui` cannot see `core`.

**Patterns:** four novel, fifteen consistency rules, **nine of them compiler-, type- or build-enforced.**

**Status:** validated PASS. **The one gate before implementation — story 1.7 — was executed on hardware 2026-08-17 and passed.** Compose Multiplatform blanks the lamp inside one 8.335 ms frame; the Flutter fallback is not taken. It passed **with a budget**: total app allocation must stay under ~0.5 MB/s, above which the collector starts pushing the blackout past its frame. See D-062/D-063 and `spike-stackgate/FINDINGS.md`.

---

# Game Architecture

## Document Status

This architecture document is being created through the GDS Architecture Workflow.

**Steps Completed:** 9 of 9 — **COMPLETE**

**Engine note:** the initialization note recorded native Swift, iPhone-only. **That decision was reversed in Step 3** — see Engine & Framework. The stack is now Kotlin Multiplatform + Compose Multiplatform. The workflow's shipped engine knowledge fragments (Godot, Unity, Unreal, Phaser, Roblox) do not apply and are not consulted. Steps covering rendering pipelines, physics engines, and scene graphs are expected to be largely inapplicable and will be marked as such rather than answered speculatively.

---

## Project Context

### Game Overview

**Someone's Home** — a co-located social deduction game run inside the host's actual house, in the dark, in enforced silence, with each player's phone as their only light and only interface. Residents dismantle the house; Insiders are blackmailed friends helping it escape the LAN. A race between two hidden progress bars. Nobody dies — access is *revoked*.

### Technical Scope

**Platform:** iPhone first, **Android on the roadmap**. Kotlin Multiplatform + Compose Multiplatform (see Engine & Framework — this reverses the original native-Swift decision). No game engine.
**Genre:** Party game (social deduction) with horror atmosphere systems.
**Players:** 5–10, tuned for 6–8, all physically in one house.
**Round:** ~25 minutes, estimate and ceiling — a round ends when a bar empties.

> **Engine fragments do not apply.** The workflow ships knowledge for Godot, Unity, Unreal, Phaser and Roblox. This project uses none of them: the interface is a diegetic retro phone OS with no scene graph, no physics, and no sprites. Rendering pipelines, physics, and scene-graph decisions are largely inapplicable and are marked as such rather than answered speculatively.

### Complexity Assessment

**Overall: HIGH.**

The genre table rates *party-game* as **low** complexity. **That rating is wrong for this project** and should not be inherited. It assumes minigames and couch multiplayer. This project has a distributed clock, a dual-role BLE radio layer, a deliberately-falsified information pipeline, and a hard requirement that clients never receive data the server holds. **Architect it like a networked multiplayer title, not a party game.**

### Core Systems — two axes, not one

**Complexity alone is the wrong measure.** Implementation weight and blast radius sort differently, and the systems to fear are the ones that score high on both. Ghost mode is small code and catastrophic if wrong; the subroutine framework is large code and harmless if wrong.

| System | Weight | Blast radius | GDD ref |
|---|---|---|---|
| **Occupancy & fog pipeline** — counts, staleness bands, stored jitter, injected error, timelapse | **HIGH** | **CATASTROPHIC** | M11 · E8 |
| **Scheduled-timestamp scheduler** — per-client clock offset, local scheduling, broadcast→ack→commit | **HIGH** | **CATASTROPHIC** | Tech Specs · E2 |
| **Ghost mode** — three phases, privileged data delivered only to the couch | LOW | **CATASTROPHIC** | M12 · E11 |
| **Ability system** — two private cooldowns + one shared pool, self-targeting, no actor feedback | MEDIUM | **CATASTROPHIC** | M6 · E9 |
| **Session & authority** — host-owned server, downed-device-as-*state* | **HIGH** | HIGH | Local MP UX · E2 |
| **Radio layer** — simultaneous advertise + scan, RSSI banding, contact handshake | **HIGH** | HIGH | Fear Mechanics · E5, E9 |
| **Task scheduling & chains** — lazy linking, sliding window, randomized unlock, System Integrity | **HIGH** | HIGH | M5 · E7 |
| **Meeting state machine** — phases, voting, ties→Skip, attribution | MEDIUM | HIGH | M10 · E10 |
| **Motion budget** — CoreMotion 100 Hz accumulator, per-player calibration, dual bounds | MEDIUM | MEDIUM | M2 · E5 |
| **Grid painter / map authoring** — adjacency from cells, editor exclusions | MEDIUM | MEDIUM | Level Design · E4 |
| **Lamp & light control** — 4 luminance steps, torch, brightness override, instant blackout | MEDIUM | MEDIUM | P1 · E1 |
| **Scanning** — preview-less capture, QR now, NFC later | MEDIUM | LOW | M1 · E5 |
| **Subroutine framework** — 10 subroutines + fakes + degradation | **HIGH** | LOW | Genre · E6 |
| **Audio** — masking generation, host playout, ring through the silent switch | LOW | LOW | Audio · E13 |

**The four CATASTROPHIC rows are where architectural effort belongs.** Getting a minigame wrong costs a Tuesday; leaking the real Egress progress to a living client ends the game.

### Systems missing from the GDD entirely

Surfaced during context analysis. **None of these had a home in any of the thirteen epics — all three are now E0.**

| Missing system | Why it matters |
|---|---|
| **Map persistence across sessions** | The setup walk is ~15 minutes and survives between rounds *and between evenings*. Where does it live? What happens when the host reinstalls and fifteen minutes of house-walking evaporates? |
| **Mid-round disconnect and rejoin** | The GDD handles *backgrounding* (log it as a game event). It does not handle a phone dying, crashing, or taking a call. At 8 players over 25 minutes this is routine, not exceptional. **The event protocol's ack timeout lands here directly** |
| **Observability / session replay** | See below — this is the big one |

### The architectural spine

**1. A large share of this game's requirements are *negative*, and negative requirements are not testable as assertions.**

The app must never confirm alignment. The attacker's device must never react. Both roles' screens must be structurally identical. The denominator must never move. Clients must never receive the identity→position mapping. Targeting a revoked player must be indistinguishable from targeting a live one.

**"No leak occurred" has no failing state.** A thousand passing tests are fully compatible with the app quietly shipping the identity map to every client. Channel enumeration helps but is incomplete by nature — the round trace found two leaks (F-003, F-011's neighbours) by a human reading prose, not by testing.

> **The testable form is differential, not assertional.** Record the **complete client transcript** — every byte a given client receives across a round. Then run the same seeded round twice with a player's role swapped, and diff the two transcripts.
>
> **⚠️ Corrected in Step 4 (D6): this catches *role-asymmetric* leaks only and is structurally blind to *symmetric* ones** — both transcripts come from the same redaction code. A second, independent per-message schema check is required. See *Three leak surfaces, three independent tests*.

**2. Determinism is already latent in the design and was never written down as a requirement.**

*Roll error once at capture, never re-roll.* *Store the signed jitter offset per observation.* Both were specified for player-facing consistency — counts must not flicker between staleness bands on re-render. **They are seed determinism by another name**, and differential testing depends on them. Make it explicit before someone "optimises" a re-roll back in.

**3. The game is undebuggable by construction unless replay is built early.**

Eight phones. A dark house. Enforced silence. Twenty-five minutes. Randomized fog. Players who are lying as a design requirement. **You cannot attach a debugger to eight devices in a dark room, cannot ask the players what happened, and cannot reproduce the round.** Every bug in the highest-blast-radius systems has the form *"someone saw something they shouldn't have."*

**Deterministic session recording and replay is the same machinery as the differential test harness** — one system, two consumers. Built at E2 it makes everything downstream debuggable; built at E11 the intervening months are spent guessing.

**4. Knowledge, radio, and rendering are three independent axes.**

A revoked phone **advertises** (radio on), **does not scan** (sensor off), and **is not rendered** (display off). Collapsing any two produced the one finding in the round trace that was simply wrong — a rendering rule read as a radio rule. **The domain model must keep these separate by construction, not by convention.**

### Novel concepts — no standard patterns exist

1. **Light as the primary state channel**, with the app owning every photon in the building.
2. **A deliberately-wrong information pipeline.** Most systems fight for accuracy; this one must be *reproducibly* inaccurate, at rates provably identical across roles.
3. **Physical device contact as a game verb**, resolving to two different outcomes purely by device state.
4. **Privileged spectator data** that must reach ghosts and provably never reach the living.

### Technical Risks

| Risk | Note |
|---|---|
| **"Host owns the server" is a sentence, not an architecture** | Transport undecided. MultipeerConnectivity is flaky past ~8 peers and 8 is the ceiling. A local server means discovery and NAT on someone's guest wifi. A real backend is rejected because the game must work in a basement. **Local-first with an optional relay is the likely shape — a Step 4 decision** |
| **Thermal, not just battery** | The host device runs BLE advertise + scan, CoreMotion at 100 Hz, a camera session, a 60 fps render, haptics, *and* serves seven peers, for 25 minutes. **Sustained load throttles, and the ±50 ms snap is the first casualty.** The ≤15% battery figure is a hypothesis and the host is where it is weakest |
| **Host is a single point of failure** | And the design forbids the obvious fix — a downed device must be a game *state*, never a process death |
| **Backgrounding degrades BLE advertising** | A call, a notification tap, or a lock silently reduces a player's visibility to everyone. Must be logged as a game event or phantom false negatives appear that were never designed and cannot be tuned |
| **Clock drift** | Offset is measured at join. Nothing specifies re-sync cadence over a 25-minute round |
| **iOS NFC system sheet** | Unverified. 30-minute spike outstanding |
| **QR is photographable** | Mitigations exist; NFC is the real fix and it is the paid tier |
| **Amber vs. scotopic vision** | The Tier 0 promise rests on a palette chosen for art reasons. Watched in playtest; the palette is four luminance values and cheap to change |
| **Contact-conceals-attacker** | Unverified. If contact does not conceal, the anonymous revoke is gone and ghost mode's information design needs revisiting |

### Scope tension — recorded unresolved

**Fourteen systems, four with catastrophic blast radius, fourteen epics — against a design document whose single most load-bearing line is that *time is the binding constraint, and two previous attempts died of calendar, not technical walls.***

The assessment above is accurate, and its accuracy is itself the risk. A minimal version exists — lamp, scan, subroutines, revoke, meeting — but it deletes the Terminal, which is the deduction layer, and without deduction the meetings have nothing in them.

**This is recorded rather than resolved.** The decision to build through to the full product is already made (`decision-log.md` D-015). The tension does not go away because a decision was taken, and it should be revisited at the first honest schedule checkpoint.


## Engine & Framework

> **⚠️ This section REVERSES the engine decision recorded in `brainstorm-intent.md` §1 and the GDD's Goals and Context.** That decision was native Swift, iPhone-only. The reversal and its trigger are documented below rather than quietly applied.

### Selected Stack

**Kotlin Multiplatform + Compose Multiplatform.** No game engine.

| | Version (verified 2026-08-16) |
|---|---|
| **Kotlin** | 2.4.10 stable (2.4.0 released June 2026) |
| **Compose Multiplatform** | **1.11.1** — iOS stable since 1.8.0 (May 2025). **Requires Kotlin 2.2+; native targets require 2.3.10+** |
| **Xcode / Swift** | 26.5 / Swift 6.3.2 — for the iOS shell only |
| **iOS SDK** | 26 (mandatory for App Store since 28 April 2026). **Deployment target iOS 26** |
| **Android** | Target TBD at Android milestone |

### Why this reverses the original decision

**The original decision was sound given its inputs and wrong given the real ones.** Two premises changed:

**1. The visual rationale does not survive scrutiny.** The original argument was that the retro phone-OS springboard — status bar, notification banners, incoming-call screens, app grid — *is a description of UIKit*, so you get the OS vocabulary free. **But the game is a fake retro OS, not iOS.** Every one of those elements is built custom to look like a 2001 PDA, and iOS 26's Liquid Glass overhaul makes stock controls read as actively broken inside the aesthetic. **You were always going to draw your own controls.** UIKit contributes gesture recognition, view lifecycle, and Core Animation — real, but not the free springboard the decision was sold on.

**2. Android is on the roadmap, not aspirational.** The original decision explicitly accepted "Android becomes a separate native app against a shared backend." That is the worst available option: the entire fake phone OS gets built **twice**, and divergence is the default outcome rather than an accident.

### The requirement that decided it

> **Screen parity is leak-critical, and mixed-platform play multiplies it.**

*Both roles' screens must be structurally identical* — same pages, same page-dot count, same panel furniture — because a difference is a tell from across a room. With iOS and Android players in the same round, that invariant must hold across **four** combinations, not two. Two independently-written UI codebases means two implementations of a leak-critical invariant, drifting on every OS update.

**One rendering engine collapses it back to two combinations, one implementation, and one differential screenshot test.** Compose Multiplatform draws its own pixels via Skia→Metal on iOS and Skia→Vulkan on Android, so parity is structural rather than maintained.

### Why Compose Multiplatform over Flutter

Genuinely close. Flutter's **Impeller** is better engineered for this project's single hardest rendering requirement — AOT-compiled shaders, Metal-backed, no first-run jank, ~50% faster rasterization, and Skia fully retired. Compose Multiplatform is still Skia→Metal, and *Metal shader compilation stalls* are its most-documented source of iOS frame drops.

**Two things reduce that risk to something worth accepting:**

1. **This is the lightest UI imaginable.** Compose MP's documented frame-drop sources are heavy lists, complex animations, and elaborate Canvas work. This game renders flat amber rectangles at four luminance steps, bitmap text, no gradients, no images, no photographic assets. A solid-colour fill is not a novel shader.
2. **A standard mitigation exists** — pre-warm the blackout draw path invisibly at round start, so any stall is paid at arming rather than at the moment that decides whether a revoke is anonymous.

**What decided it was language count.** Flutter needs Dart for the app *plus* Swift and Kotlin for every platform channel — BLE, torch, camera, motion, haptics, NFC, audio, on both sides. Three languages, and Dart transfers nowhere. Compose MP needs **Kotlin** for shared logic, shared UI, *and* the entire Android platform layer (Kotlin being Android's native language), with **Swift only for the thin iOS shell**. Two languages, one doing most of the work. For a solo developer whose stated binding constraint is calendar, on attempt three, that is the argument.

Secondary: `expect`/`actual` is a first-class language feature that maps exactly onto the functional-core / imperative-shell boundary, where Flutter's platform channels sit *beside* that boundary as a message-passing bridge. And KMP compiles to native with direct interop — no serialization hop between shared code and platform code, which matters for BLE callback volume.

### What is shared vs. written per platform

`expect`/`actual` is the seam. The **contract** is declared once in common code; the **implementation** is native on each side.

```kotlin
// commonMain — declared once
expect class TorchController { fun setLevel(level: Float) }
// iosMain    → AVCaptureDevice.setTorchModeOn(level:)
// androidMain → CameraManager.turnOnTorchWithStrengthLevel()
```

| Written **once** (common Kotlin) | Written **per platform** (`actual`) |
|---|---|
| Game state machine, win conditions | BLE advertise + scan, RSSI banding, contact handshake |
| Chain scheduling, lazy linking, unlock delays | **Torch level control** |
| Fog pipeline — counts, staleness bands, jitter, error injection | Screen brightness override, keep-awake |
| System Integrity, cooldown pools | Camera session (preview-less) |
| Meeting state machine, voting, tally | Motion sensors at 100 Hz |
| Ghost-mode phases and per-role data filtering | Haptics |
| Subroutine *logic* and *rendering* | NFC |
| Clock-offset maths, event scheduling | Audio playout / AirPlay |
| **The entire phone-OS interface** — springboard, status bar, apps, Status panel, grid painter, Terminal, meeting screens, all ten subroutine screens | Permissions, background modes, app lifecycle |

**Shared fraction: roughly 60–70%**, because the UI — the single largest chunk of this build — moves into the shared column. A KMP-core-only variant would land around 35–45% and would *not* solve divergence, which is why it was rejected.

### What the stack does NOT provide — this is the architecture

| Category | Status |
|---|---|
| Rendering, physics, scene graph, sprites | **N/A.** No simulation, no scene graph, no sprites exist in this game |
| UI layout, gestures, animation | Compose Multiplatform |
| Audio | AVFoundation / Android audio, behind `expect`/`actual` |
| Build | Gradle + Xcode (interop tax paid daily) |
| **Replication** | **Build it.** |
| **Fixed-timestep simulation tick** | **Build it.** `CADisplayLink`-equivalents are variable-rate and throttle thermally, so they cannot drive a replayable simulation |
| **Serialization / persistence** | **Build it.** |

**Those three rows are the architecture**, and they map onto the three highest-blast-radius systems from Step 2. An engine's replication model would have fought the anonymization requirement rather than helped it.

### Timing requirements — corrected

**The previously-stated ±50 ms simultaneity target was invented, not derived, and is relaxed.**

*Latency* and *simultaneity* are separate problems. Delivery latency is fully solved by scheduling — the caller waits on a calling screen for 1–2 seconds, which is already the design. Relative skew between devices depends only on clock-offset accuracy, not delivery.

| Requirement | Target |
|---|---|
| **Coordinated flip skew** (lights-up, arming, ring) | **±150 ms** — "reads as one event." Offset estimation over a local network should comfortably beat this |
| **Sync Pulse tap window** | Generous by design — twitch timing is forbidden, and a generous window absorbs device skew |
| **Lamp blackout on contact** | **Same frame.** Single-device, and the one genuinely tight requirement |

**Event protocol: broadcast → acknowledge → commit.** Broadcast *"event at T,"* collect acks, then commit. **Ack timeout is required** — a client that never acks (backgrounded, dead, phone call) must not stall the caller; proceed without them and let that client flip on reconnect. This lands on the disconnect/rejoin system flagged missing in Step 2.

### The technology gate

**Build E1 (the lamp) in Compose Multiplatform first.** It is already scoped as one screen — torch, brightness, the flip — and it answers the only real objection to the stack:

> **Can Skia→Metal kill the lamp in the same frame as phone contact?**

That is the entire mitigation for losing the anonymous revoke. Testable with one screen and a high-frame-rate camera. **If it passes, everything downstream is shared. If it fails, one screen is lost and the fallback is Flutter** — which solves the same problems at the cost of a third language.

E1 is no longer a gate for the *game* (the coordinated lights-up moment is already validated from prior play with smart-home lights). It is now a gate for the *stack*.

### Portability discipline — adopt regardless

> **No platform types in the simulation core.** No `Date`, no BLE types, no UI types. Events with integer timestamps in; state and effects out.

This is the same constraint that makes the core deterministically replayable and headless-testable in milliseconds. Free, and it keeps every future porting option open.

### The tripwire is retired

The original tripwire — *"if cross-platform day-one becomes non-negotiable, the answer is Godot"* — is **deleted**. It was wrong twice over: Godot would require GDExtension per platform for every sensor (native work twice anyway), hand-rolled OS-interface widgets, and an engine runtime for a game with no scene graph, no physics and no sprites. And cross-platform is no longer a future event to trip on.

**No starter template.** None fits this shape.

### Development Environment

| Tool | Purpose |
|---|---|
| **Apple's native Xcode MCP** (Xcode 26.3+) | Build system, Simulator, Previews, debugger for the iOS shell |
| **Context7** | Current Kotlin, Compose Multiplatform and Apple documentation rather than training-data recall |
| **Gradle** | Primary build; the daily interop tax with Xcode is an accepted cost |

`XcodeBuildMCP` (Sentry, ~82 tools) noted as a fallback if first-party tooling proves thin.

### Open platform question created by this decision

**Android's torch is worse, and no framework fixes it.** `setTorchModeOn(level:)` is fine-grained on every iPhone in a decade; Android exposes strength levels only on hardware reporting multiple levels, with binary on/off and unreliable PWM below that. **Graduated light is the core mechanic.**

This is a design decision, not an engineering one: **does the lamp degrade gracefully on weak hardware, or does the game declare a minimum torch capability the way it declares a minimum OS version?** Answer it before the Android milestone, not during. Recorded as **OI-7**.


## Architectural Decisions

**The shipped decision catalog does not apply.** Its networking options are all engine netcode (Unity Netcode, Photon, Mirror, Unreal replication, Roblox RemoteEvents), its UI options are all engine-specific, and its asset-pipeline category is moot — this game streams nothing. Decisions below are derived from the architecture.

### Decision Summary

| # | Category | Decision | Version | Status |
|---|---|---|---|---|
| D1 | Transport | Embedded Ktor server on host + mDNS + websockets | Ktor **3.5.1** (Jun 2026) | Decided |
| D2 | Authority | **Host device, no migration.** Migration deferred to v2 | — | Decided, risks accepted |
| D3 | State model | Event-sourced pure core; strict server authority; thin clients | — | Forced by constraints |
| D4 | Concurrency | Single confinement dispatcher for the core; I/O funnels into one ordered queue | — | Forced by D3 |
| D5 | Clock | Monotonic local + offset to authority; slew, never jump | — | Numbers set below |
| D6 | Redaction | **In the core.** Effects are per-client by construction | — | Decided |
| D7 | BLE identity | **Ephemeral rotating tokens**, authority-resolvable only | — | New requirement |
| D8 | Persistence | kotlinx.serialization; versioned JSON maps, JSONL recordings | — | Decided |

### D1 · Transport

Host runs an **embedded Ktor server**; peers connect over **websockets** on the local network. Discovery via **mDNS** — the one platform-specific piece, behind `expect`/`actual` (NSD on Android, Bonjour on iOS).

**All of it is multiplatform Kotlin, so transport joins the shared column** rather than being written twice — a direct dividend of the stack reversal, replacing MultipeerConnectivity, which died with it.

**No-wifi fallback is manual and documented, not automated.** The host enables Personal Hotspot and everyone joins; iOS will not let an app toggle that. It goes in the play manual. Justified because the game already requires a host, a dark house, eight friends and a speaker for the masking track — *"everyone on the same wifi"* is a small marginal ask. BLE-as-transport remains available as a later addition rather than v1 scope.

> **⚠️ The iOS Local Network permission prompt must be warmed during setup, not at arming.** First use of local-network discovery raises a system modal — in the middle of the screen, in the platform's own visual language, on top of a carefully built retro interface. **Firing it at game start, in a dark room, is the same failure as the NFC system sheet.** Trigger discovery deliberately during the setup walk, in the light, before anyone is playing.

### D2 · Authority — host device, no migration

**Truth lives on the host's device. It also plays.** Migration deferred to v2.

**Risks accepted, stated plainly so v2 inherits them:**

- **Single point of failure.** The rule that *a downed device is a game state, never a real process death* covers **player** devices. The host's process dying is a different failure, and is now an accepted one.
- **Thermal.** The host runs BLE advertise + scan, 100 Hz motion, a camera session, a 60 fps render *and* serves seven peers for 25 minutes. Sustained load throttles, and throttling degrades exactly the timing that matters most. **Mitigations: keep simulation work off the render thread, and reduce the host's own scan duty cycle** — it can afford to be a worse sensor than everyone else.
- **It is untestable below full scale.** Host throttling only appears with the complete eight-phone rig, which is a monthly event.

> **The gap was never migration — it is the failure UX, and that must ship in v1.**
>
> "No migration" describes what the system does. It says nothing about what **seven people in a dark house, forbidden from speaking, holding phones that just lost their websocket** actually experience.
>
> - **Lamps hold their last state.** Every light in the house going out simultaneously is a safety event, not a bug report.
> - **A legible message appears** — the round has ended, and why.
> - **Nobody is left playing a game that stopped existing.**
>
> Cheap to build. Without it, the accepted risk is materially worse than the risk that was accepted.

> **A cheap partial that is not migration and should ship in v1:** E0 already records every event, so **a host that crashes can relaunch and resume the round from its own local recording.** Same device, same authority, no election, no consensus — crash recovery, not migration. Most of the value for a fraction of the work, on infrastructure that already exists.

### D3 · State model — event-sourced, strictly server-authoritative

The template offers Singleton / State Machine / ECS / Redux. **The answer is Redux-shaped, and that is not a stylistic preference** — event sourcing is what makes deterministic replay possible, and replay is the only thing that makes this game debuggable.

```
(State, List<Event>) → (State, List<Effect>)
```

Pure, synchronous, deterministic, **no platform types**. Clients are thin: they send **intents** and render **effects**. No game logic client-side, ever.

**Forced by the anonymity rule, not chosen for elegance.** A client cannot leak data it was never sent. Client-side prediction, shared state, and any form of authoritative client are excluded by construction.

### D4 · Concurrency

Core confined to a **single dispatcher**. The five I/O domains — BLE, motion, camera, network, UI — run on their own dispatchers and funnel into **one ordered event queue**. Swift 6's strict-concurrency problem became a Kotlin coroutines problem and got easier: the core touches nothing concurrent.

**Fixed-timestep tick driven by a coroutine loop, not Compose's frame clock.** Frame clocks are variable-rate and throttle thermally, so they cannot drive a replayable simulation.

**No allocation on the blackout path** — the Kotlin/Native GC constraint from story 1.7b, enforced by a permanent assertion.

### D5 · Clock

| Parameter | Value |
|---|---|
| Local timebase | **Monotonic**, never wall clock |
| Offset estimation | NTP-style round trips at join; **take the minimum-RTT sample**, not the mean |
| Re-sync cadence | **Every 30 s** |
| Correction | **Slew, never jump.** A backward jump could skip an already-scheduled event |
| Scheduled events | broadcast → ack → commit. `T = last_ack + 500 ms` |
| Ack timeout | **2 s.** Proceed without the missing client; it flips on reconnect |
| Recording timestamps | **Simulation tick index, not wall clock** — determinism requires it |

### D6 · Redaction lives in the core

**The core emits per-client effects by construction.** Who can see what *is* game logic — ghost mode, role, room, staleness — so it belongs where the logic is. The shell never holds data it should not transmit.

Rejected: core emits global effects, shell filters per client. That would put the single most leak-critical decision in the layer that is neither deterministic nor headlessly testable.

**The transcript recorder sits at the emit boundary** — one chokepoint, every client-bound byte.

> **⚠️ This creates a blind spot in the differential harness, and it is not optional to fix.**
>
> The harness runs a seeded round twice with a role swapped and diffs the client transcripts. **But both transcripts are produced by the same redaction code.** A *symmetric* bug — the core cheerfully shipping real Egress progress to everybody — yields two identical transcripts and a clean diff. **The harness reports no leak, and it is technically correct: there was no role-asymmetric difference. There was just a leak.**
>
> **The earlier claim that "anything that differs is a legitimate ability payload or a leak, and there is no third category" was wrong.** The harness catches *asymmetric* leaks and is structurally blind to symmetric ones.
>
> **Required second test, independent of the first: a per-message schema check at the emit boundary.** An explicit allowlist of what each client class may receive — ghosts may receive both bars; the living may not receive Egress progress at all. Not *"did it differ"* but *"was it ever permitted."* **Two tests, two failure modes, neither covering the other.**

### D7 · BLE identity — ephemeral rotating tokens

**A stable advertised identifier defeats the entire map design.** Any client that hears it can track that individual all round, making *counts, never identities* decorative. Server-side anonymization cannot help — the leak is in the radio, below the server.

| Parameter | Value |
|---|---|
| Advertised payload | **A rotating ephemeral token. Nothing else** — no role, no name, no stable ID |
| Rotation interval | **15 s** |
| Resolution | **Authority only.** Clients report *"saw token `4f2a` at RSSI −67"* |
| Contact handshake | Token exchange at contact range, resolved server-side |

Shorter rotation costs nothing server-side — the authority stitches tokens to identities regardless — so the interval is bounded by radio overhead, not usefulness. Same shape as exposure-notification protocols.

> **⚠️ Backgrounding may break this, not merely weaken it.** iOS moves service UUIDs into the overflow advertising area and drops the local name when an app backgrounds. **The existing "backgrounding degrades advertising" note was written when the payload meant *"I exist."* It now carries the entire anonymity scheme.**
>
> Worst case: a backgrounded phone stops being resolvable by its token and becomes identifiable by something nobody designed — a stable, silent, house-wide tell for the rest of the round, defeating exactly what D7 exists to prevent. **It would never be noticed, because the failure is invisible and the game keeps running.**
>
> **Required: a radio-level test.** Background a phone and sniff what the antenna actually emits — not what the code asked it to emit. Those are different claims and only one of them is verifiable. **This sits below the effect boundary, so neither the differential harness nor the schema check can see it.**

### D8 · Persistence & serialization

**kotlinx.serialization** throughout. Maps as **versioned JSON** — small, diffable, and human-readable when something goes wrong at 11pm in a dark house. Recordings as **JSONL**, append-only, trivially truncatable and diffable, with a binary format available later if size bites. Settings as key-value.

**Map survival across reinstall matters** — the setup walk is ~15 minutes of work. Platform backup plus an explicit export/share path, so a house can be handed to another host.

### Three leak surfaces, three independent tests

The decisions above produced a layered picture that is worth stating once, because **no single test covers more than one layer**:

| Layer | Leak shape | Caught by |
|---|---|---|
| **Role-asymmetric** | One role receives something the other does not | **Differential harness** — seeded round, role swapped, diff transcripts |
| **Symmetric** | Everyone receives something nobody should | **Per-message schema allowlist** at the emit boundary |
| **Below the effect boundary** | The radio emits something the app never sent | **Radio-level sniffing**, especially when backgrounded |

A green build on any one of these says nothing about the other two.


## Cross-cutting Concerns

These apply to **every** system. Violating one is a defect regardless of a story's acceptance criteria.

### Error Handling

**Strategy: Result objects in the core, errors-as-events in the shell. Nothing throws across the boundary.**

| Layer | Rule |
|---|---|
| **Core** | Never throws. A malformed or impossible event is **dropped and recorded**, never crashes the round |
| **Shell** | I/O failure is not an exception to propagate — it is **a game event fed into the core** (`BleUnavailable`, `TransportLost`, `CameraDenied`) |
| **Boundary** | Exceptions never cross it in either direction |

```kotlin
// Core: total function, no throw
fun reduce(state: State, event: Event): Reduction =
    when (validate(state, event)) {
        is Invalid -> Reduction(state, effects = emptyList(), dropped = event)
        is Valid   -> applyEvent(state, event)
    }

// Shell: I/O failure becomes an event, not an exception
scanner.onFailure { queue.offer(Event.BleUnavailable(deviceId, tick)) }
```

**Two rules specific to this game, and both are leak rules rather than robustness rules:**

> **1. Errors are silent to the *player*, loud to the *authority*.** No dialogs, no toasts, no error banners, no unexpected screen state — an error surfacing on a Insider's device as they fire an ability is an alignment tell delivered by the crash handler.
>
> **But silence toward the player must not mean silence toward the system.** A dead BLE stack makes a living player **invisible on the occupancy map** — a substantial advantage, delivered silently, that nobody including them can detect. The client reports the failure as an event; the authority knows; see *the house notice* below.

> **2. The lamp never changes as a side effect of an error.** Light is game state. A screen that dims, flashes, or blanks because something threw is a **false game signal** broadcast to everyone in the room — indistinguishable from a Surge or a revocation. The lamp changes only when the core says so.

**Critical vs. recoverable:** recoverable failures (BLE dropout, scan failure, websocket blip) become events and the round continues. The one genuinely critical failure is **host process death** — story 0.9: lamps hold their last state, a legible message, nobody left playing a game that stopped existing.

> **⚠️ One failure is unfixable, and is recorded as unfixable so nobody spends a week on it.** When a *client* crashes, its lamp goes dark — **which is exactly what a revocation looks like**, to that player and to anyone watching. They cannot speak. Sitting down tells the room something false; standing in the dark with a dead phone reads as revoked anyway. **Mitigation is speed only:** relaunch fast, rejoin from the recording, lamp returns. In the seconds before that, the game has lied to everyone, and no rule prevents it.

### The house notice — reporting degradation diegetically

**When a device is unreachable or backgrounded for a meaningful interval, the house says so — to everyone, at the next meeting.**

> **NOTICE:** Occupant **Marcus** was unreachable 21:04–21:07. Occupancy data for this interval is incomplete.

| Property | Value |
|---|---|
| **Trigger** | Radio failure **or** backgrounding — same rule, one notice. Both degrade advertising identically |
| **Delivery** | **At the meeting**, batched with the other aggregate information. Not the moment it happens |
| **Audience** | Everyone, identically. Role-independent, so it leaks nothing |
| **Register** | Administrative, unhelpful, faintly bureaucratic — the house explaining its own limitations badly |

**Why meetings and not real time:** meetings are already where aggregate evidence arrives in a burst — the System Integrity delta lands there for the same reason. It keeps the house from chattering mid-round, which would dilute *the house speaks last* at the endgame, and it puts the correction exactly where people are arguing from bad data.

**Why it needs no anti-exploit.** The obvious attack is a Insider backgrounding their phone during a revoke to manufacture a connectivity excuse. **It backfires: being named as off-network means you generated no check-ins either.** In a game about who was where, *"the system could not see you for three minutes"* is the worst sentence that can be read aloud about you. **The exploit is self-punishing.**

**And it makes the map's unreliability diegetic in writing.** The map is deliberately wrong — injected error, staleness bands, false negatives. Now the house *admits it*, in its own voice, in a form players can argue about.

**It also removes the host as the single point of social responsibility** for explaining a weird round afterwards.

### Logging

**Format: structured, one record per line, keyed on the simulation tick — with wall clock alongside.** Tick is the primary key because it correlates with the recording; wall clock is retained because playtests get filmed and video needs correlating.

```kotlin
log.info("egress.contained", tick = 4172, node = "beacon", pairs = 2)
// → {"lvl":"INFO","evt":"egress.contained","tick":4172,"wall":"21:06:14.221","node":"beacon","pairs":2}
```

| Level | Use |
|---|---|
| **ERROR** | Something broke and the round is degraded |
| **WARN** | Handled but unexpected — a dropped event, an ack timeout, a backgrounding |
| **INFO** | Round milestones — arming, meetings, Egress, endgame |
| **DEBUG** | Per-event tracing. Off in release |
| **TRACE** | Radio and sensor firehose. Development only |

**Constraints:**

- **Never log on the blackout path.** Logging allocates, and story 1.7b's no-allocation discipline governs the one frame that decides whether a revoke is anonymous.
- **The host's log contains everything. Never ship it to a client** for diagnostics — that is a leak channel with a friendly name.
- **`WARN` on every backgrounding event.** It is required by the existing design, it feeds the house notice, and it is the trigger condition for the D7 token risk.
- **Crash diagnostics must never carry game state.** A platform-captured crash report from the host's device is the one path by which authority memory could surface somewhere readable.

### Configuration

| Tier | Storage | Changeable |
|---|---|---|
| **Constants** | Compile-time | Never |
| **Balance values** | **Host-editable between rounds; locked at arming; stamped into the recording** | Between rounds only |
| **Player settings** | Local key-value | Anytime |
| **Platform capability** | Detected at launch (e.g. torch level support) | Never |

> **Balance values must be runtime-tunable, because the test rig is eight iPhones in one dark room, realistically monthly.** Thirteen indexed assumptions need playtest numbers — Egress timer, Revoke cooldown, Access pool, unlock delay, staleness bands, error rates, meeting cooldown, Sync Pulse taps. **If changing one requires a rebuild and redeploy to eight devices, you get one tuning change per session instead of five.**

> **⚠️ Locked at arming, and stamped into the recording.** Without the lock, whoever runs the server can shorten their own Revoke cooldown mid-round while holding an armed token. Stamping is required for replay determinism regardless — the same wall, again.

### Event System

Decided in D3, restated as rules:

- **Typed, never stringly-typed.** A sealed hierarchy, so the compiler enforces exhaustive handling — a silently-unhandled event is indistinguishable from a game rule.
- **Synchronous in the core.** Async processing would destroy determinism, and determinism is what makes replay and both leak harnesses possible.
- **Naming: `subject.verbPast`** — `player.revoked`, `egress.contained`, `meeting.tallied`. **Events are facts that already happened; intents are requests that might not be honoured.** Never name an event as an imperative: `revokePlayer` describes an intent, `player.revoked` describes history, and event sourcing only works on the latter.
- **Event history and replay is E0.** It is not a debug feature, it is the event system.

### Debug & Development Tools

**In-game debug overlays are unusable here.** Any pixel drawn is light, light is game state, and a debug HUD is a beacon. **Debugging happens out-of-band.**

| Tool | What it is |
|---|---|
| **Replay viewer** | The primary tool. Load a recording, scrub the round, view **every client's transcript side by side**, in the light, afterwards. This replaces the debugger you cannot attach |
| **Headless simulation** | Run N scripted players against the core with no devices. Ten thousand rounds before dinner |
| **Cheat commands** | Force a role, force an Egress, jump to a meeting, set System Integrity |
| **Leak harnesses** | Differential (0.6), schema allowlist (0.6b), radio sniffing (0.6c) — CI, not manual |

> **⚠️ Scripted players encode your assumptions and will confirm them at scale.** A simulated Resident that walks efficiently to the nearest marker will run ten thousand rounds and never discover that beelining gets you revoked — because the simulated Insiders are also playing the way you imagined. That is **testing a mental model at enormous scale and calling it evidence.**
>
> **Fuzz, do not model.** Include policies that camp pointlessly, wander, idle, stand still for four minutes, spam abilities, and act at random. **The absurd players find the crashes and the unstaffable chains; the reasonable ones only confirm what you already believe.**

**Three build variants, not two:**

| Variant | Recording | Cheats | Debug surfaces | Marked |
|---|---|---|---|---|
| **Release** | On | **Off** | **Compiled out** | No |
| **Playtest** | On | **On** | Compiled out | **Yes — permanent status-bar marker** |
| **Debug** | On | On | On | **Yes** |

**Why playtest exists as its own variant:** on release you have no cheat commands on the one night a month you have eight people in a house — no skipping to a meeting, no forcing an Egress, no resetting a round that broke in minute two. **You get one round instead of four.** On debug you are playtesting a build that is not the game.

**Debug surfaces are compiled out of release, never runtime-flagged** — a runtime flag is a leak waiting for a misconfiguration. **Non-release builds must be visibly, unmistakably marked**, or somebody plays a real round with cheats live and finds out afterwards.

**Recordings must not be exportable through any user-facing path during a round.** The map has an export path so a house can be handed to another host; the recording contains everything and must not share it.

### On the host holding authority state — assessed and not a problem

Raised and **retracted**, recorded so it is not re-raised: the authority runs in the host's app process while the host plays, so complete truth — roles, real positions, real Egress progress — lives on a playing participant's device.

**This is not an access path.** On a release build with an unmodified device, the app container is not user-browsable on either platform. The host's app shows them exactly what their role warrants, and they have no more access to the authority's state than any player has to their own app's memory. **The error was conflating *data present* with *data accessible*.**

What survives is narrow and already covered above: crash diagnostics must not carry game state · recordings must not be user-exportable mid-round · **playtest and debug builds genuinely do expose it**, which is why the variant split and the visible marker matter.

*(If the trust assumption ever weakens — a stranger hosting, competitive play — the structural fix is a dedicated authority device, which the design already half-assumes via node mode.)*


## Project Structure

### Organization Pattern

**Domain packages inside a small number of modules, where every module boundary enforces something load-bearing.**

The rules from Steps 3–5 — a pure core with no platform types, thin clients with no game logic, redaction at one chokepoint — are otherwise *conventions*, and the failure mode for every one of them is a silent leak. **A module edge turns a convention into a compile error.**

**But only where it earns its keep.** Gradle configuration time is paid on every incremental build, every day, for months, by one person whose binding constraint is calendar. A module that enforces nothing is a tax with no return. **Six modules, not twelve** — everything else is packages.

### Directory Structure

```
someone-is-home/
├── model/                    # Pure data. No behaviour.
│   ├── Event.kt              #   sealed: facts that happened
│   ├── Intent.kt             #   sealed: requests that may be refused
│   ├── Effect.kt             #   sealed: what a client is told
│   ├── State.kt
│   ├── protocol/             #   wire format (was its own module)
│   └── schema/               #   ← THE REDACTION CONTRACT. Declarative.
│
├── core/                     # Pure rules. THE SIMULATION.
│   ├── rules/
│   │   ├── tasks/            #   chains, lazy linking, System Integrity
│   │   ├── abilities/        #   cooldown pools, Revoke, Egress, Tier 2
│   │   ├── meeting/          #   phases, voting, tally, ties→Skip
│   │   ├── fog/              #   counts, staleness, error injection, timelapse
│   │   ├── ghost/            #   three phases, privileged data
│   │   └── redaction/        #   per-client filtering ← reads model/schema
│   ├── sim/                  #   tick SEMANTICS only: advance(state, ticks)
│   └── Reduce.kt             #   (State, Event) → (State, List<Effect>)
│
├── platform/                 # expect/actual. No game logic.
│   ├── radio/                #   BLE advertise + scan, tokens, contact
│   ├── light/                #   torch level, brightness, keep-awake
│   ├── capture/              #   preview-less camera, QR, NFC
│   ├── motion/               #   100 Hz source AND accumulator (see exception)
│   ├── haptics/
│   ├── audio/                #   masking playout, the ring
│   └── transport/            #   Ktor server + client, mDNS (was its own module)
│
├── ui/                       # Compose Multiplatform. The entire phone OS.
│   ├── theme/                #   amber palette, 4 luminance steps, bitmap type
│   ├── shell/                #   springboard, status bar, Status panel
│   ├── apps/                 #   Terminal, Files, Notes, Messages, Settings
│   ├── subroutines/          #   the ten, plus their fakes
│   ├── meeting/              #   incoming call, discussion, vote, result
│   ├── setup/                #   grid painter, marker placement
│   ├── lamp/                 #   the lamp surface, the blackout path
│   └── assets/               #   snoot printable, play manual — SHIPPED, not docs
│
├── harness/                  # E0. Never ships. Headless.
│   ├── recording/ · replay/
│   ├── differential/         #   0.6  role-swap transcript diff
│   ├── schemacheck/          #   0.6b asserts against model/schema
│   └── fuzz/                 #   0.10c chaotic policies
│
├── androidApp/               # entry point, actuals, THE TICK DRIVER
├── iosApp/                   # Swift shell, actuals, THE TICK DRIVER. As thin as possible.
└── tools/replay-viewer/      # Desktop Compose. The debugger you cannot attach.
```

### Architectural Boundaries

| Module | May depend on | **May NOT depend on** | Enforces |
|---|---|---|---|
| **model** | stdlib, kotlinx-serialization | Everything else | Data stays data |
| **core** | `model` | **coroutines · datetime · Ktor · Compose · platform** | Purity, synchrony, determinism, no platform types |
| **platform** | `model` | `core` · `ui` | `expect`/`actual` holds no game rules |
| **ui** | `model`, `platform` | **`core`** | Thin clients — the UI *cannot* contain game logic |
| **harness** | `core`, `model` | `ui` · `platform` | Tests run headless, no devices |
| **app roots** | everything | — | Composition roots only |

**Three edges are the reason modules exist at all:**

> **`core` cannot import coroutines.** Pure and synchronous by design; removing the dependency makes "someone will eventually make this async" impossible rather than discouraged.

> **`core` cannot import a datetime library.** Time enters as tick indices, nothing else. D-020's portability discipline, enforced by Gradle rather than by memory.

> **`ui` cannot import `core`.** The UI renders `Effect`s and emits `Intent`s, both from `model`. It cannot see the rules, so it cannot duplicate, cache, or predict them.

> **⚠️ The tick driver lives in the app roots, not in `core`.** `core/sim/` holds the *semantics* of advancing — `advance(state, ticks)`, pure and testable. **The coroutine loop that drives it cannot live in the module forbidden from importing coroutines.** Drawn otherwise, the structure contradicts its own boundary table on the first read.

### Input echo is not game logic

**The `ui ↛ core` edge does not mean a network round-trip to draw a pressed button.**

A subroutine's pattern arrives as an **Effect**. The UI displays it, captures taps, and **echoes them locally** — lighting the dot you just touched is reflecting your own input, not simulating anything. The tap sequence returns as an **Intent**, and the server verifies it against the pattern it generated.

**No logic duplication, no round-trip for feedback, boundary intact.** Stated explicitly because the naive reading of "thin clients" leads people to round-trip their own button presses.

### The one exception to strict server authority

> **The motion budget accumulates on the client.**

100 Hz sampling cannot round-trip, and the draining bar must fail immediately and visibly. So the client computes its own budget and decides its own failure. **This is the only place in the game where a client adjudicates anything, and it is documented so exceptions do not breed.**

**Cheating it is low-value:** a Insider gains nothing, since Insider completions never advance System Integrity; a Resident cheating it is *visibly moving while doing a task*, which is a physical tell in a room full of people. The honesty framework already carries heavier obligations — *don't dodge*, *don't conceal your phone*, *don't tap someone you haven't armed on*.

### System → Location mapping

| System | Location |
|---|---|
| Tick semantics · seeded RNG | `core/sim/` (**driver** in app roots) |
| Fog pipeline — counts, staleness, injected error, timelapse | `core/rules/fog/` |
| Chains, lazy linking, System Integrity | `core/rules/tasks/` |
| Cooldown pools, Revoke, Egress, Tier 2 | `core/rules/abilities/` |
| Meeting phases, voting, tally | `core/rules/meeting/` |
| Ghost three-phase gating | `core/rules/ghost/` |
| **Per-client redaction** | `core/rules/redaction/`, reading `model/schema/` |
| **Redaction contract** | `model/schema/` — one declaration, read by runtime *and* harness |
| Clock offset, scheduling, ack/commit | `platform/transport/` |
| BLE tokens, rotation, contact handshake | `platform/radio/` |
| Motion accumulator | `platform/motion/` — **the documented exception** |
| Torch, brightness, blackout | `platform/light/` + `ui/lamp/` |
| The ten subroutines + fakes | `ui/subroutines/` (verification in `core`) |
| Grid painter | `ui/setup/` |
| Win conditions, reveal sequence, chain disclosure | `core/rules/endgame/` |
| Recording, replay, differential, schema check, fuzzing | `harness/` |
| **Radio emission test (0.6c)** | **Instrumented on-device test beside `platform/radio/` — NOT `harness`** |
| Play manual, snoot printable | `ui/assets/` — **shipped**, source in `docs/` |

> **⚠️ The three leak surfaces now live in two homes.** Differential (0.6) and schema (0.6b) are headless and belong in `harness`. **Radio sniffing (0.6c) is a device test and cannot live in a module forbidden from touching platform.** The risk is that a split model quietly loses a layer from CI — **all three must appear in the same CI stage listing even though they run differently.**

### Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Classes, objects, Composables | PascalCase | `FogPipeline`, `StatusPanel` |
| Functions, properties | camelCase | `reduce`, `motionBudgetRemaining` |
| Constants | `const val` UPPER_SNAKE | `MAX_CHAIN_LENGTH` |
| **Event** types | PascalCase **past tense** | `PlayerRevoked`, `EgressContained` |
| Event wire keys | `subject.verbPast` | `player.revoked` |
| **Intent** types | PascalCase **imperative** | `ArmRevoke`, `CastVote` |
| Files | Match primary declaration | `FogPipeline.kt` |
| `expect`/`actual` pairs | Identical names both sides | `TorchController` |

> **Event/Intent naming is not cosmetic.** `ArmRevoke` is a request that may be refused; `PlayerRevoked` is a fact that already happened. **Event sourcing only works on facts** — a codebase that names them interchangeably will eventually store a request in the log and replay something that never occurred.

**Game vocabulary is mandatory in code, not only in UI strings.**

`Resident` / `Insider` are the only role words — everyone is a Resident, some are *also* Insiders. `Revoke` — the Insider ability. `Restrain` — the group's action at a meeting. **Revoke and Restrain are not synonyms and must never be collapsed:** one is system power lent by the house, the other is a physical act the house cannot prevent. `Subroutine` — the unit of assigned work. `SystemIntegrity` — the collective progress meter. `Egress` — the Insider-triggered house crisis. `Override` — the Insider-only route between rooms. `Passage` — a map-editor shape tag, alongside `room` and `stairs`.

**That list is exhaustive** — any identifier outside it is wrong. The lint's word list carries the mechanical detail.

The vocabulary encodes **who owns the action** — system words for the house and Insiders, human words for what Residents do among themselves. Code that drifts to `killPlayer()` surfaces as "kill" in a UI string eventually, and the register the whole game rests on dissolves quietly.

### Assets

```
ui/theme/
├── palette.kt        # 4 amber luminance steps on true black. No second hue.
├── typography.kt     # one bitmap face
└── icons/            # flat monochrome glyphs, one size
ui/assets/
├── snoot.pdf         # tube + optional end cap — printable in-app
└── play-manual/      # shipped, not documentation
```

**No art pipeline. No 3D, sprites, textures, photographic assets, or animation beyond state transitions.** Everything is drawn from primitives — the cheapest asset story a game can have, and a direct consequence of the art direction.


## Implementation Patterns

The template's categories mostly do not apply — there are no entities, prefabs, object pools, behaviour trees, or AI. **The lens that matters here is: what would two competent implementers decide differently, silently, in a way that leaks?**

### Novel Pattern 1 — The Indistinguishable Outcome

**The signature pattern of this codebase, and the one every implementer will get wrong by default**, because early-return-on-invalid is idiomatic everywhere else. **Here, the absence of an effect is itself an observable.**

```kotlin
// ❌ WRONG. The missing effect is a revoke-detector: poll the roster,
//    learn who is out, never find a revoked player.
if (target.isRevoked) return Reduction(state, effects = emptyList())

// ✅ RIGHT. Identical effect, identical cooldown spent, no feedback either way.
//    Only the internal state branches.
val effects = listOf(Effect.AbilityFired(actor, cooldownStarted = true))
val next = state.spendCooldown(actor).let { s ->
    if (target.isRevoked) s else s.applyTo(target)
}
return Reduction(next, effects)
```

**Rule: never branch on success in the client-visible path.** Branch on state, emit the same shape.

> **⚠️ Scope correction: this guarantees indistinguishability *on the actor's device*, not in the room.** Surge is **world-observable** — a lamp flares and a phone makes noise. Surge someone you can see, watch nothing happen, and you have learned they are revoked with no panel involved.
>
> **Mostly self-mitigating and accepted:** a Insider usually does not know where their target is, so "no flare" is ambiguous between *revoked* and *in another room*. The leak opens only when Surging someone already in line of sight — at which point you are standing next to a person you already suspect, with eyes.
>
> **Worded precisely because the unqualified version promises something the physics of a room cannot deliver**, and someone will eventually try to engineer it.

### Novel Pattern 2 — Sealed Observation, and two types not one

Deliberate error must be **reproducible**, or replay breaks and players stop trusting a map that renders differently on second viewing.

```kotlin
// Authority-side. Never crosses the wire.
data class Observation(
    val room: RoomId, val tick: Int,
    val trueCount: Int,
    val reportedCount: Int,   // error applied ONCE, here
    val ageJitter: Int        // signed, rolled ONCE, here
)

// Client-side. PHYSICALLY INCAPABLE of carrying ground truth.
@Serializable
data class ObservationView(
    val room: RoomId, val tick: Int,
    val reportedCount: Int,
    val ageJitter: Int
)
```

> **⚠️ Two types, not one type with a comment.** A single `Observation` carrying `trueCount` — annotated *"authority only, never leaves the core"* — lives in `model`, which `ui` imports, and is `@Serializable`. **A comment is not a boundary.** Someone serializes the whole struct in a hurry and the ground truth ships. It will not be malice, it will be a Tuesday.

```kotlin
// Capture: roll once, from the seeded stream, keyed on observation identity
fun seal(o: RawObservation, rng: SeededRng) = Observation(
    room = o.room, tick = o.tick, trueCount = o.count,
    reportedCount = injectError(o.count, rng.next(o.id)),
    ageJitter = rng.signed(o.id)
)

// ❌ WRONG — re-rolls on render; counts flicker between staleness bands
fun render(o: ObservationView) = o.reportedCount + randomError()

// ✅ RIGHT — rendering is a pure function of the sealed view
fun render(o: ObservationView, now: Int) = Band.of(now - o.tick + o.ageJitter) to o.reportedCount
```

**Rules:** roll once at capture · store the roll · **no RNG at render time, ever** · identical error rates for both roles, because asymmetry makes the map an alignment oracle by statistics.

### Novel Pattern 3 — The Redaction Gate

**One place, schema-driven, and it projects by *constructing a narrower type*.**

```kotlin
// core/rules/redaction/Redact.kt — the ONLY place this happens
fun redact(effect: Effect, viewer: Viewer): List<Effect> =
    Schema.projectionFor(effect::class, viewer.classOf())   // model/schema
        ?.invoke(effect)
        ?.let(::listOf) ?: emptyList()

sealed interface Viewer {
    data class Living(val role: Role) : Viewer
    data object Ghost : Viewer
}
```

> **⚠️ Never project by nulling fields.** A boolean allowlist guards *which effect types* a viewer may receive and says nothing about *which fields inside them* — so an effect can be permitted and still carry something the viewer must not see, and the CI schema check passes it because the type was on the list.
>
> **Construct a different, narrower type per audience.** Then the allowlist is field-complete for free, **because the type *is* the field list.** Nulled fields are worse than useless: the field still exists, and someone will make it non-null later for an unrelated reason.

**Effects are addressed, never broadcast.** The core never emits "everyone gets X."

**Viewers are classified, never identified.** The schema keys on *viewer class* — `Living(Resident)`, `Living(Insider)`, `Ghost` — never on player identity. A rule written per-player is a rule that can be wrong for exactly one person.

**`model/schema/` is the single source of truth**, read by this gate at runtime and by harness story 0.6b in CI.

### Novel Pattern 4 — Light Is a Pure Function of State

```kotlin
// ❌ WRONG — local animation the core didn't authorise is an unauthored game signal
LaunchedEffect(revoked) { lamp.animateTo(0f, tween(400)) }

// ✅ RIGHT — the lamp renders what the core said, nothing more
val level = effects.latestOf<Effect.LampLevel>()?.value ?: LampLevel.Dim
LampSurface(level)
```

**No local animation, tween, or easing the core did not emit.** Light is game state; a fade nobody authored is a signal nobody authored. And per Step 5: **no error path may touch the lamp.**

### Standard Patterns

**Communication — explicit constructor wiring at the composition root.** No service locator, no DI framework, no ambient singletons.

> **Global mutable state is banned outright**, not discouraged. It defeats determinism, and determinism is what makes replay and both leak harnesses work.

**"Entity creation" → deterministic ID allocation.** There are no entities; the analogous trap is worse.

```kotlin
val id = Uuid.random()   // ❌ replay produces different IDs; every recording is worthless
val id = state.nextId()  // ✅ monotonic, part of State, recorded, reproducible
```

*(BLE rotation tokens draw from the same seeded stream — deterministic for replay, unpredictable to clients, because the seed lives only on the authority. **Never derive a token from anything a client knows**, such as a player index.)*

**Collection iteration order is load-bearing.**

```kotlin
players.toSet().forEach { emit(it) }          // ❌ hash order varies; effects diverge on replay
players.sortedBy { it.seat }.forEach { emit(it) }  // ✅ ordered collections only, in core
```

A classic determinism killer — invisible until a replay diverges by one effect and nobody can say why.

**State transitions — sealed hierarchies, exhaustive `when`, no `else`.** An `else` swallows a new state silently, and a silently-unhandled state is indistinguishable from a game rule. Kotlin enforces exhaustiveness on sealed types, so this is compiler-backed rather than review-backed.

**Time access — tick indices only.** Enforced by the module boundary, stated here so nobody threads a wall clock through as a parameter to get around it.

### Consistency Rules

| Rule | Enforcement |
|---|---|
| No branch on success in client-visible paths | Review + differential harness (0.6) |
| Effects addressed, never broadcast | Type system — `Effect` carries an audience |
| **Client types cannot express ground truth** | **Type system — separate authority/view types** |
| **Project by narrower type, never by nulling** | **Type system + schema check (0.6b)** |
| Error and jitter rolled once at capture | Review + replay divergence |
| No RNG at render time | Review; `render` takes no RNG parameter |
| Ordered collections only in `core` | Lint + replay divergence |
| IDs from the seeded sequence | Lint banning `Uuid.random` in `core` |
| No global mutable state | Module boundaries + review |
| No `else` in core state machines | **Compiler** — sealed exhaustiveness |
| No wall clock in `core` | **Gradle** — no datetime dependency |
| No coroutines in `core` | **Gradle** — no coroutines dependency |
| No game logic in `ui` | **Gradle** — `ui` cannot see `core` |
| Game vocabulary in code | Lint over **`model`, `core`, `ui` only** — allows the vocabulary above and rejects everything else, from a word list held in the lint config |
| No allocation on the blackout path | Permanent allocation assertion (1.7b) |

> **The vocabulary lint is scoped to the three game-facing modules.** `task` appears in every coroutine API and `kill` in process management — run it over `platform` and you will suppress it forty times a week and then switch it off. `model`, `core` and `ui` are the modules that produce user-visible strings anyway.

**Nine of fifteen rules are compiler-, type- or build-enforced.** The remainder survive on review and are exactly what the differential and schema harnesses exist to catch.

### ⚠️ These rules need a courthouse

**Fifteen consistency rules living in a nine-thousand-word architecture document is a constitution nobody opens at the moment it matters.** An agent implementing three short subroutines will not read all of this to discover rule seven.

**`project-context.md` has never existed in this repository**, and every skill run this session has resolved its persistent-facts glob to nothing. **That file is the courthouse** — the short, always-loaded surface where the non-negotiable rules live and an implementer trips over them without having to remember to look.

**Recommended immediately after this workflow:** run `gds-generate-project-context` and seed it with the compiler-unenforceable half of the table above.


## Architecture Validation

**Validated 2026-08-16 against the workflow checklist.** Five gaps found and resolved below.

| Check | Result | Notes |
|---|---|---|
| Decision compatibility | **PASS** | Version chain verified, not assumed |
| GDD coverage | **PASS** | 14/14 systems located after G2 |
| Pattern completeness | **PASS** | Lifecycle/retry added as G3 |
| Epic mapping | **PASS** | 14/14 epics mapped after G2 |
| Document completeness | **PASS** | No placeholders |
| **Not overengineered** | **AMBER** | See *The scope amber* below — now with a trigger |

### Version compatibility — verified

**Compose Multiplatform 1.11.x requires Kotlin language/API version 2.2+, and native targets require Kotlin 2.3.10+.** On **Kotlin 2.4.10** this clears, but only just.

**Pin CMP at 1.11.1** (1.11.0 was the May 2026 release; 1.11.1 supersedes it).

> **The rule matters more than the numbers: CMP tracks Kotlin closely and native targets are the constrained ones. Re-verify this pairing on every upgrade, not once.**

### G1 · Client attribution — the one real hole

The checklist's authentication item looked N/A and was not. Transport is websockets on a local network, and **nothing specified how the authority knows an Intent came from the player it claims to.** A second connection asserting it was Marcus would have been accepted.

**Unlike every other cheat in this design — tapping without arming, dodging, concealing a phone — this one is remote, undetectable, and requires no physical act anyone could witness.** The honesty framework cannot reach it, because it does not happen in the room.

**Resolution:**

- A **session token issued at join and bound to a *seat***, not merely to a connection.
- **Intents are attributed by connection. A client never names itself**, so it cannot claim to be someone else.
- **A lobby code gets you *a* seat. It never gets you *that* seat.**

> **⚠️ Reconnect is where this is actually tested, and it is the path most likely to be built loosely.** If a resuming client re-derives identity from the lobby code — the path of least resistance at 1am — **the entire hole is rebuilt in the one code path nobody exercises.** The client stores its token; the server refuses a resume presenting the wrong one. See G3.

### G2 · `core/rules/endgame/` — added

Win-condition checks and the reveal sequence had no module home. Now: the three win checks, the fixed reveal ordering (house speaks → lights up, Insiders stand → blackmail publishes), and chain-membership disclosure.

### G3 · Reconnect and retry

Story 0.8 covered disconnect and rejoin as *state* but never specified the client's retry behaviour.

- **Bounded exponential backoff.**
- **The lamp holds its last authorised state throughout** — consistent with story 0.9's host-failure rule, and required because a lamp changing without authorisation is an unauthored game signal.
- **The resume presents the stored seat token** (see G1).

### G4 · Performance instrumentation — event-triggered, not continuous

> **Average frame rate is the wrong measurement here.** You do not care about sustained 60 FPS; you care about **one frame in ninety thousand** — the blackout — and an average eats it whole. Continuous sampling also costs performance to collect.

**Measure the moments, every time they fire, in the playtest build:**

| Measurement | Instrument |
|---|---|
| **Blackout latency** | Contact timestamp → lamp-dark timestamp. **The number the entire anonymity guarantee rests on** |
| **Scan acquisition** | Hold-press → code acquired |
| **Coordinated flip skew** | Scheduled T → actual flip, per device |
| **Motion sampling** | Dropped-window count per subroutine |

Cheap, and it **accumulates evidence across every round ever played** instead of requiring a separate profiling session with eight phones in a dark room.

### G5 · Test data comes from recordings, not from imagination

`core` tests are pure function tests over `(State, Event) → Reduction` — no mocks, no fakes, because the module boundary guarantees there is nothing to mock.

> **⚠️ The harder question is what the test data is.** A valid mid-round eight-player `State` is enormous — rooms, markers, occupancy history, cooldowns, chains, ballots, tick counters. **A hand-written builder encodes the tester's idea of a mid-round state**, which is the identical failure already caught with scripted players: fixtures you invent confirm the game you imagine.
>
> **Snapshot fixtures out of real recordings** at interesting ticks — first revocation, chain collapse, parity threshold, mid-Egress. Real states produced by the actual rules, and **they regenerate when the rules change instead of drifting silently.**

### Coverage report — and what it does not say

| | Count |
|---|---|
| Systems with a named architectural home | **14 / 14** |
| Epics mapped to locations | **14 / 14** |
| Architectural decisions | **8 numbered + 5 forced-by-constraint** |
| Novel patterns, each with WRONG/RIGHT examples | **4** |
| Consistency rules | **15**, of which **9** are compiler/type/build-enforced |
| Leak surfaces with independent tests | **3** |

> **⚠️ Architectural coverage is not design completeness, and this table measures only the first.** Fourteen of fourteen systems know *where they live*. That says nothing about whether their numbers exist — the fog pipeline has a home, four patterns and a chokepoint, and **its staleness band thresholds, error injection rate and jitter magnitude are all still open assumptions (A-7, A-8).** Thirteen indexed assumptions remain unresolved and every one needs playtest.

### The scope amber

**Honestly assessed, this architecture is heavy for a party game:** fourteen systems, six modules, three leak harnesses, deterministic replay, a fixed-timestep simulation, rotating radio tokens. **The naive reading of "not overengineered for actual requirements" fails.**

The defence is that the leak-critical requirements were derived from the design rather than invented — a game whose premise is *the app never confirms anyone's alignment* genuinely needs the machinery that proves it doesn't. **But every item being individually justified is exactly what a project that dies of calendar looks like from the inside**, and this project has died of calendar twice.

**An amber with no trigger is a decoration, not a deferral.** So:

**The trigger — milestone, not date:** **when E0–E5 are complete, stop and review the remainder against how long that actually took.** You will know by then. The point of writing it down is granting permission to act on it.

**The cut list, ordered by pain, decided now while nothing is under pressure:**

| Order | Cut | Cost |
|---|---|---|
| 1 | **NFC** | Already an add-on; QR is the free tier and the game is complete on it |
| 2 | **Timelapse playback** | Keep the live count view. A large chunk of E8; the Terminal still works |
| 3 | **Degrading subroutines** | Loses the only signal that the house is winning. Painful, not fatal |
| 4 | **Ten subroutines → six** | Keeps 2 bright / 2 medium / 2 dark, so the light-signature axis survives |
| 5 | **Spoof** | The GDD already flags it as possibly never used |
| 6 | **Snoot printable** | Tier 0 is complete without it |

> **Nothing on this list is a leak harness, replay, or core purity.** The cut list is pre-deciding what makes the project **big**, never what makes it **correct**. Deciding under pressure is how the wrong half gets cut.

**Overall status: PASS**, with the scope amber carrying a trigger and a plan rather than a note.



## Development Environment

### Prerequisites

| Requirement | Version / Note |
|---|---|
| **JDK** | 17+ (21 recommended) |
| **Kotlin** | 2.4.10 |
| **Compose Multiplatform** | 1.11.1 — **requires Kotlin 2.2+; native targets require 2.3.10+** |
| **Ktor** | 3.5.1 |
| **Xcode** | 26.5 — **iOS 26 SDK is mandatory for App Store submission since 2026-04-28** |
| **Android Studio** | With the Kotlin Multiplatform plugin |
| **Gradle** | Wrapper, committed |

> **⚠️ The iOS Simulator cannot run this game.** No Bluetooth, no torch, no camera, no haptics — which is **every input this game has**. Essentially all meaningful development requires **physical devices**, and any multi-device behaviour requires several. Budget for this: it changes the inner loop from "run in simulator" to "deploy to hardware," on every iteration.

### Project Initialization

**No starter template applies.** Generate the KMP skeleton from the official Kotlin Multiplatform wizard (`kmp.jetbrains.com`) or Android Studio's KMP plugin, then restructure to the six-module layout in *Project Structure*. A generic app template contributes nothing beyond the Gradle scaffold.

### AI Tooling

| Tool | Purpose |
|---|---|
| **Apple's native Xcode MCP** (Xcode 26.3+) | Build system, Simulator, Previews, debugger for the iOS shell |
| **Context7** | Current Kotlin, Compose Multiplatform, Ktor and Apple documentation rather than training-data recall |

`XcodeBuildMCP` (Sentry, ~82 tools) is the fallback if first-party tooling proves thin.

> **The most important tooling in this project is not an MCP server.** It is the **replay viewer** (`tools/replay-viewer/`) and the **headless simulator** (`harness/`). They are the debugger you cannot attach and the playtest you cannot schedule.

### First Steps

1. **Run story 1.7 as a standalone spike.** One screen: does Compose Multiplatform blank the lamp in the same frame as a contact event, on real hardware? **A stack change would invalidate all of E0**, so nothing else starts until this answers.
2. **Create `project-context.md`** — the compiler-unenforceable half of the consistency rules, in the file every agent loads. Fifteen rules in an architecture document is a constitution with no courthouse.
3. **Build E0.** Recording, replay, the three leak harnesses, map persistence, disconnect/rejoin. Everything downstream is debuggable or it is not.
4. **Then E1 → E7** to a playable round.
5. **At E5 complete: stop and run the scope review** against the cut list in *Architecture Validation*.

### Non-code work outstanding

Buy `someoneishome.game` / `.app` (~$20, availability not yet checked post-rename) · trademark clearance opinion before anything public ($500–1500) · 30-minute iOS NFC spike · LLC and general liability insurance before selling anything physical · DMCA agent registration once any UGC surface exists · work-for-hire art contract.

---

## Document Status: COMPLETE

**All 9 workflow steps complete.** Validated 2026-08-16, overall PASS.

**Carried forward, unresolved by design:**

| Item | Nature |
|---|---|
| **OI-4** Hidden-mode compensating lever | Playtest call |
| **OI-5** Settings interior | Mostly locked rows; only lamp control and *About* need content |
| **OI-6** Ghost dead time at ~25 minutes | Playtest; meeting frequency is the only lever |
| **13 indexed assumptions** | Every one needs a number from a real round |
| **The scope amber** | Has a trigger (E0–E5 complete) and a cut list. **Resolves at a checkpoint, not in a document** |

**Gating before implementation:** ~~story 1.7a/1.7b~~ — **CLEARED 2026-08-17.** Both halves pass. 1.7a's pre-warm mitigation proved unnecessary and should not be built; 1.7b's mitigation was aimed one layer too low and is replaced by a whole-app allocation budget (story 1.7c, D-063).
