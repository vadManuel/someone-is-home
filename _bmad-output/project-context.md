---
project_name: "Someone's Home"
user_name: 'Vadmanuel'
date: '2026-08-16'
sections_completed: ['stack', 'rules', 'gotchas', 'escalation']
size_budget: 1200
---

# Project Context — Someone's Home

**Read before implementing anything.** These are the rules that idiomatic code breaks.

> **This file has a hard budget of ~1,200 words.** Every addition must displace something, or become compiler-enforced instead. A file nobody finishes reading is worse than no file, because everyone still believes the rules are being read.

## What this game is — one paragraph, because the rules won't make sense otherwise

A social deduction game played in a real dark house. The app is every player's only light. Its central promise is that **it never confirms anyone's alignment to anyone** — so most requirements are *negative*, and a bug is usually a **leak**, not a crash. Second promise: **every round is deterministically replayable**, because eight phones in a dark house cannot be debugged any other way.

## Stack

Kotlin **2.4.10** · Compose Multiplatform **1.11.1** (needs Kotlin 2.2+; native targets 2.3.10+) · Ktor **3.5.1** · Xcode/Swift **26.5** (iOS shell only) · iOS SDK 26, deployment target 26.

## Modules

`model` (data + redaction schema) · `core` (pure rules) · `platform` (expect/actual) · `ui` (Compose, the whole phone OS) · `harness` (recording, replay, leak tests) · app roots.

Gradle already enforces: **`core` sees no coroutines, no datetime, no platform. `ui` sees no `core`.** If an import fails, that is the architecture, not a misconfiguration.

## Rules idiomatic code breaks

### 1. Never early-return on invalid. The absent effect IS the leak.

```kotlin
if (target.isRevoked) return Reduction(state, emptyList())   // ❌ a revoke-detector

val effects = listOf(Effect.AbilityFired(actor, cooldownStarted = true))  // ✅ same shape
val next = state.spendCooldown(actor).let { if (target.isRevoked) it else it.applyTo(target) }
```
Applies to: targeting revoked players, a Resident tapping an ability, chains unlocking by completion vs revocation, fake subroutines. **Never branch on success in a client-visible path.** *(Guarantee is device-side only — Surge is world-observable and that's accepted.)*

### 2. The schema FAILS CLOSED.

New `Effect` type with no schema entry → **ships to nobody**. Never default-permit. The failure mode must be *"my thing didn't appear"* (noticed in 30 seconds), never *"my thing appeared to everyone"* (noticed never).

### 3. Redact by constructing a narrower type. Never by nulling fields.

A nulled field still exists and someone makes it non-null later for an unrelated reason. **The type must *be* the field list.** Client-facing types must be *physically incapable* of carrying ground truth — `Observation` (authority, has `trueCount`) vs `ObservationView` (client, cannot). **A comment saying "authority only" is not a boundary.**

**Client-facing types carry a marker, and only marked types may be wire-`@Serializable`.** Unmarked-but-serializable is a lint failure.

### 4. Determinism — three ways to break it by accident

```kotlin
Uuid.random()                     // ❌ replay produces different IDs
state.nextId()                    // ✅ seeded, recorded

players.toSet().forEach {}        // ❌ hash order varies, effects diverge
players.sortedBy { it.seat }      // ✅ ordered collections only in core

fun render(o) = o.count + noise() // ❌ re-rolls; counts flicker between bands
                                  // ✅ error rolled ONCE at capture, stored
```

### 5. The lamp is a pure function of state

No local animation, tween, or easing the core didn't emit. **Light is game state** — a fade nobody authored is a signal nobody authored. **No error path may touch the lamp**: a screen that blanks because something threw is indistinguishable from a revocation.

### 6. Errors are silent to the *player*, loud to the *authority*

No dialogs, toasts, or unexpected screen state — an error surfacing as an Insider fires an ability is an alignment tell delivered by the crash handler. **But report the failure as an event**: a dead radio makes a living player invisible on the map, which nobody including them would detect. The house announces it at the next meeting.

### 7. Nothing allocates or logs on the blackout path

The lamp must die in the **same frame** as phone contact — the entire mitigation for losing the anonymous revoke. **Kotlin/Native GC pause there doesn't drop a frame, it un-anonymises a revoke.** The path is explicitly marked; there is a permanent allocation assertion. Don't work around it.

### 8. Every subroutine ships with its fake, in the same change

Real UI, real progress, real completion animation, **writes nothing**. Ten subroutines with three forgotten fakes breaks screen parity, and nobody finds out until someone glances at an Insider's phone in the dark. **Not a backlog item.**

### 9. Vocabulary in code, not just in strings

`Resident` / `Insider` are the only role words. `Revoke` = the Insider ability. `Restrain` = the group's action at a meeting. **Revoke and Restrain are not synonyms and must never be collapsed** — one is system power lent by the house, the other is a physical act the house cannot prevent. `Subroutine` = the unit of assigned work. `SystemIntegrity` = the collective progress meter. `Egress` = the Insider-triggered house crisis. `Override` = the Insider-only route between rooms.

**That is the complete vocabulary — any identifier outside it is wrong.** The lint's word list carries the mechanical detail; lint covers `model`, `core`, `ui`.

### 10. Events are facts; Intents are requests

`PlayerRevoked` (past, happened) vs `ArmRevoke` (imperative, may be refused). Event sourcing only works on facts — name them interchangeably and you'll store a request in the log and replay something that never occurred.

## Gotchas

- **The iOS Simulator cannot run this game.** No BLE, torch, camera, or haptics — which is every input. **Never propose simulator verification.** Physical devices, plural.
- **Input echo is not game logic.** `ui` can't import `core`, but lighting the dot you just tapped reflects *your own input*. Pattern arrives as an Effect, taps return as an Intent, the server verifies. **No round-trip to draw a pressed button.**
- **One exception to server authority: the motion budget** accumulates client-side (100 Hz can't round-trip). **Don't add a second.**
- **Never re-derive identity from the lobby code on reconnect.** Resume presents the stored **seat token**. A lobby code gets you *a* seat, never *that* seat.
- **Physical rules are not device rules.** Don't run / don't speak / don't dodge / don't conceal your phone are social contract. **Never enforce them in software** — and never add mic access, for any reason.
- **Balance values lock at arming** and stamp into the recording. No mid-round edits.

## Three leak surfaces, three independent tests

| Layer | Test |
|---|---|
| Role-asymmetric | Differential harness — seeded round, role swapped, diff transcripts |
| Symmetric (everyone gets what nobody should) | Per-message schema allowlist |
| Below the effect boundary (radio emits what the app never sent) | On-device radio sniffing |

**A green build on one says nothing about the other two.** Touching redaction, effects, or the radio means checking which applies.

## When you're unsure — stop and ask

Every other project absorbs a wrong guess as a bug. **Here a wrong guess is silent and permanent:** it ships, it works, nobody notices, and someone loses a game for reasons that never surface.

**Ask, don't guess, about:** what a client may receive · what an ability reveals to whom · what the lamp does · whether something needs a schema entry.

**Fuller answers:** `_bmad-output/planning-artifacts/game-architecture.md` (patterns, boundaries, decisions) · `.../gdds/gdd-someone-is-home-2026-08-16/gdd.md` (design intent) · `.../epics.md` (stories) · `.../decision-log.md` (why anything is the way it is, including what was reversed).

**The vocabulary is closed — use those words and no synonyms**, in code, docs, comments, commit messages and filenames alike. If you need a term the list doesn't carry, raise it rather than inpassage use one. A `pre-commit` hook checks, but the rule holds without it.
