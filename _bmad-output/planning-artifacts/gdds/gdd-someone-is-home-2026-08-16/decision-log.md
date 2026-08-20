# Someone's Home GDD — decision log

**Started:** 2026-08-16 · **Author:** Vadmanuel · **State:** ready (v1.9, revised 2026-08-16 — engine reversed rev 4; architecture rev 6–10; **renamed rev 11**)

> **Revision 2 supersedes parts of revision 1.** D-005 (15-minute round) is reversed by **D-011**. F-010 and F-011 are resolved by **D-007** and **D-008**. F-017's "composes" verdict is reversed by **D-012**. Read the revision-2, -3 and -4 blocks at the bottom before acting on anything above it. **Revision 4 reverses the engine decision to Kotlin Multiplatform + Compose Multiplatform.**

Every decision, change, and version transition, in real time.

---

## Inputs read in full (2026-08-16)

| Input | Role |
|---|---|
| `_bmad-output/brainstorming/brainstorm-engine-selection-2026-08-15/brainstorm-intent.md` | Decisions from the 2026-08-15/16 session. Authoritative. |
| `_bmad-output/brainstorming/brainstorm-engine-selection-2026-08-15/design-doc-revisions.md` | Scoped MoSCoW against the design record. Names what is stale. |
| The base design record (1908 lines, since retired) | Reasoning source. **Stale wherever the revisions file says so** — it still described deaths, spaceship subsystems, and synthetic replacements. |
| A legacy-project context dossier (since deleted) | Consulted for the original physical-task list only. Otherwise ignored per instruction. |

**Standing rule for this GDD:** where the design record and `brainstorm-intent.md` disagreed, the brainstorm won. The design record's sections on death, the Capgras frame, and conversion mode are superseded, not merged. Both source documents were retired on 2026-08-17; the GDD is now self-contained.

---

## Discovery

### D-001 · Workspace created
`_bmad-output/planning-artifacts/gdds/gdd-someone-is-home-2026-08-16/`. Files: `gdd.md`, `epics.md`, `decision-log.md`.

### D-002 · Task framing accepted from the user
§18.1 — a single round walked end to end, naming every system as it fires — is an explicit, first-class section of this GDD, not an appendix. Rationale: seventeen sections of systems were designed in isolation and have never been checked for composition; half changed in the last session.

---

## Open at start of session

| # | Item | Source |
|---|---|---|
| O-1 | Physical tasks (basketball, Jenga, nerf, locks, hair braiding) dropped without a decision | revisions § DECIDE; intent §8.4 |
| O-2 | Round length — ~25 min doesn't survive the arithmetic; ~15 min proposed | intent §6; revisions §17.10 |
| O-3 | Hidden-mode balance lever | intent §8.1 |
| O-4 | The trap | intent §8.2 |
| O-5 | Settings interior | intent §8.3 |
| O-6 | Smart home priority within v2 | intent §8.5 |

---

## Decisions taken

### D-003 · Game type: Party Game (primary) + Horror graft — **decided**
Party Game supplies the structural subsections that hold the open decisions: minigame roster, round pacing, session length, local-multiplayer UX, elimination-vs-points. Horror supplies Atmosphere/Tension and Fear Mechanics (visibility & darkness, vulnerability, detection systems) — the home for light-is-game-state, the light ladder, and the light-signature axis, which Party Game has no slot for.

`needs_narrative` **not set.** Horror's guide carries `<narrative-workflow-recommended>`, but the premise is settled and there is no branching story, cast, or lore to design. The narrative surface in this game is five text messages and one end-of-round line.

### D-004 · Physical tasks: screen-only for v1, physical as a v2 tier — **decided** (closes O-1)
Supersedes the design record's "keep the original real-life task categories." The original list is preserved in full in the GDD's v2 backlog section and is not lost — it is deferred.

Four reasons, in order of weight:
1. **Tier 0 (§4.4) requires the game complete with no purchase and no accessory.** Physical tasks need props per household. A game that needs a Jenga set is not complete at Tier 0.
2. **The light-signature axis (§17.2) is load-bearing and has no physical analogue.** The screen *is* the lamp, so a subroutine's brightness is a difficulty dial independent of duration. A nerf gun has no light signature the app can set.
3. **The motion budget (§7.3) needs the phone in hand.** Braiding hair does not leave a hand for the phone, so the game's only anti-cheat and its whole vulnerability model go dark for the task's duration.
4. **The one shipped direct competitor already ships physical tasks at stations (§2).** Physical is therefore parity rather than differentiation; darkness with a light-signature axis is unclaimed.

**Rejected alternative worth recording:** a host-authored physical marker type (free-text instruction written during the setup walk, player self-confirms). Cheap, keeps what made the original memorable, and folds into the custom-task UGC pitch §6 already makes. Rejected for v1 only because it adds a UGC moderation surface (§1.7) before the core loop is validated. **This is the v2 shape** — it is the right way to bring the physical tasks back, and it should not be re-derived.

### D-005 · Round length: ~15 minutes — **decided** (closes O-2)
Supersedes §17.10's ~25-minute assumption. Arithmetic:

- 6 Residents × 6 subroutines at ~15s play + ~25s travel = **~4 min** of task time running in parallel. The old assumption fails by a factor of six.
- Budget for a 15-min (900s) round: 2 House Meetings ≈ 300s (90s discussion + 45s vote + 15s result, ×2), 1–2 Egresses ≈ 90s → **~510s ≈ 8.5 min** of task time to fill.
- **7 subroutines per Resident, weighted 1 circuit / 1 long / 3 medium / 2 short** = 160 + 95 + 150 + 66 ≈ **471s ≈ 7.9 min**. Lands on target.

Time is added as movement and exposure, not busywork — per the revisions' explicit instruction. Shorter rounds also mean more rounds per night, which for a party game is strictly better.

**Knock-on effects, both real:**
- **The random unlock delay (§8.5) must shrink from 20–120s to ~15–60s.** It was tuned for a 25-minute round; at 15 minutes a 3-link chain with two long delays would consume a quarter of the round before its last link unlocks. See F-013.
- **Ghost mode's worst case is fixed by this decision.** A player revoked at minute 3 waits for the first meeting before ghost mode activates (phase 3). At 25 minutes that wait was intolerable; at 15 it is ~3 minutes. Systems composing correctly.

### D-006 · Working mode: Express — **decided**
Full draft from the three inputs at once, with §18.1 (the single-round trace) as a first-class walked section rather than an appendix.

---

## The §18.1 round trace — 17 findings

The single-round walkthrough was written end to end as the explicit task. It produced 17 findings, indexed in `gdd.md` → *A Single Round, End to End* → *Findings index*.

**Two contradictions between already-settled decisions:**

- **F-010 · Surge stairs-suppression is unimplementable.** Two safety mitigations (suppress Surge on stairs; auto-raise the lamp on stair traverse) require the app to know a player is on the stairs. The tracking model — *the system knows only who was near a scanner* — plus the rules that stairs carry no markers and are never counted, guarantee it never can. **Phase-blocking. Escalated to OI-1.** Recommended resolution: on-device CoreMotion stair detection that raises the player's *own* lamp and reports nothing anywhere (no tracking-model violation), with the surviving mitigations as fallback. This contradicts "do not build a classifier" and is the author's call.
- **F-011 · "Deactivated Person Found" is a fakeable core state signal.** A revoked player's device is fully inactive and has stopped advertising, so the app can never verify a find. The trigger is therefore an unconditional button — which is exactly what the *core state signals must never be fakeable* rule cut when it removed False Alert. **Phase-blocking. Escalated to OI-2.** Recommended: amend the rule to distinguish the house's own signals (never fakeable) from a named player's claim (always a claim, framed as one).

**One blocker on a win condition:**

- **F-005 · System Integrity 32 → 0 does not match 7 subroutines × 6 Residents = 42.** Resolved in the GDD as `7 × initial_residents`, frozen at arming, with orphans silently auto-satisfied. The `32` appears to be an artifact of an earlier player count.

**Seven gaps with proposed resolutions**, all carried into the body of the GDD: Egress node designation (F-001) · Isolate on an Egress node (F-002) · haptic buzz-count leak at role reveal (F-003) · no initial cooldowns at arming (F-004) · the Egress widget takeover costing a stale number (F-006) · counts decrementing more sharply than dots faded (F-007) · Egress timer never numbered (F-009) · unlock delay tuned for the old round length (F-013) · carry state across a meeting (F-014).

**Two decided in-trace:** dot interpolation is dead under counts (F-008) · Files must never show the Memory Dump pattern (F-015).

**Three seams checked and cleared, recorded so they are not re-litigated:** post-meeting Terminal blindness is designed behaviour, not a bug (F-012) · the parity rule *guarantees* every Egress is containable, because the minimum living Resident population in a running game is exactly 2 (F-016) · the round-length decision independently fixed ghost mode's worst case (F-017).

**The first sixty seconds** were traced and need nothing — pure task execution with zero information is the only quiet stretch of the round, and its tension is anticipatory.

**The one seam that did not survive: meeting material.** See OI-3 — the first meeting is thin and the second is rich, and a thin first meeting produces a near-random revoke that accelerates toward parity for no informational gain. **Deliberately not pre-solved; it is a numbers question and the numbers do not exist yet.**

---

## Finalization — 2026-08-16

### Decision log audit
All entries D-001 through D-006 are represented in `gdd.md` or `epics.md`. No decision was set aside.

### Input reconciliation
Ran against all three inputs plus the legacy context dossier's task list. Conducted inline rather than via subagents, per session constraints; the parent had read all three sources in full.

**Seven items the structured sections had silently dropped, all restored:**

1. **The vocabulary table** (a MUST row in the revisions) — restored as its own section, including the point that **Evict is gone and House Meeting is the last human word standing.**
2. **Why this theme, and the test it passed** — that the theme retroactively justifies mechanics designed for unrelated reasons. Restored to Background.
3. **Competitive position** — the shipped competitor's specifics, which are load-bearing for D-004. Restored to Background.
4. **What chains are *for*** — chains as bait, chains as the genre's only verifiable alibi, and *keep verification social: leave the ledger coarse.*
5. **The Terminal's second-order effects** — squatting as an undesigned free Insider ability with a physical counter, the memory bottleneck, coverage holes, the bodyguard dynamic.
6. **The diegetic justification for silence** — *the house is listening*, and the standing prohibition on ever implementing microphone access.
7. **The two working prototypes** (device mockup, diagnostics bench) as reference artifacts against E3 and E6.

### Discipline pass
Checked for the standard failure modes. Found and fixed:
- **An unnumbered mechanic spec** — Sync Pulse "tap several times" → **4 taps**, indexed as A-11.
- **An unstated rule** — no Insider action ever advances System Integrity, and a marker not on a Insider's fake list opens nothing, *exactly as it would for a Resident with no task there*, so the null case is not a tell.

No placeholders, no unfilled template slots, no engine-implementation leakage beyond platform capability naming in Technical Specifications.

### Open items carried forward
**OI-1** (stairs safety vs. tracking model) and **OI-2** (fakeable meeting trigger) are **phase-blocking** and gate epics E9 and E10 respectively. **OI-3** (first-meeting material) needs a playtest, not a decision. **OI-4** (hidden-mode lever) and **OI-5** (settings interior) are parked deliberately.

Eleven assumptions are indexed, all numbers, all needing playtest. **A-5 (~25s inter-marker travel) is the riskiest, because both the round-length derivation and the Egress timer rest on it.**

### Narrative handoff
`needs_narrative` not set. See D-003.

### State
**Ready.** Next step for a game heading into build is `gds-game-architecture` — but **E1, the prototype gate, does not need it** and should come first.

---

# Revision 2 — 2026-08-16 (review pass)

Six decisions from a review of the §18.1 findings. **Two of my findings did not survive scrutiny; both corrections are recorded with the reasoning error, because the errors are more useful than the findings were.**

### D-007 · OI-1 (Surge stairs-suppression) — **deferred, not solved**
Ships without it. The four detection-free mitigations carry v1: no markers in stairs zones (editor-enforced), no timed routes crossing stairs, the hard no-running rule, the host acknowledgment at tagging. **The deferral expires at public launch, not at v1** — a controlled friend group is a different exposure from an App Store listing.

**Correction to the original finding.** I claimed both halves were blocked by the tracking model. Only one is. Suppressing Surge needs the *server* to know where the *target* is and stays hard. **The lamp auto-raise is entirely device-local** — your own phone, your own accelerometer, your own lamp, reporting nothing — so the tracking model was never its obstacle. Its only obstacle is "do not build a classifier." I over-attributed and made the whole item look worse than it is.

### D-008 · OI-2 / F-011 — **dissolved. The finding was wrong.**
I read "revoked phones become fully invisible" as a **radio** rule when it is a **rendering** rule. Excluding a revoked player from displayed counts is what the design needs; switching their transmitter off was never required by anything. **The system may know things it never shows** — a separation that holds everywhere in this design, and that the trace collapsed in exactly one place.

With the radio up, the deactivation report is verifiable by physical contact, so it was never a fakeable signal. **Both meeting triggers survive and the never-fakeable rule stands unamended.**

Two rules fall out and are now in the GDD:
- **The revoked phone advertises but never scans.** If it kept scanning, a revoked player would be an invisible sensor generating fresh occupancy data for a room nobody is scanning in — and a room with fresh counts and no scanner *implies a revoked player sitting in it*. Inverts the coverage-holes rule.
- **The contact gate is hard-limited to contact range.** At "nearby," BLE penetrates walls and the report becomes a through-wall detector for revoked players, which ends *you can walk past a revoked player and never notice*.

Also decided: **name the caller, never the room**, on both trigger types.

### D-009 · Revoke becomes phone-to-phone contact — **decided**
**The Insider arms in-app (cooldown starts on arm), then touches their phone to the target's phone.** No target confirmation step. **No self-deactivate control exists anywhere in the game.**

Closes four things at once: the probe (nothing to press), the free fake-tap (touching without an armed token does nothing), the proximity bug (contact *is* the gate, so an open token can no longer resolve against someone nobody touched), and attribution becomes exact.

**Not NFC** — iOS exposes no phone-to-phone NFC. Build as a very-tight-RSSI BLE handshake presented as a tap, with UWB optional.

**Accepted cost: the revoked player often sees who did it.** The mitigation is the mechanic — the target's own screen is the only light on the attacker, and it dies at the instant of contact, so certainty is not guaranteed. **E1 must test this in a real dark room** (story 9.19).

**A previously decided rule is reversed deliberately:** a Insider who revokes another Insider now finds out immediately, where before "they will never know it. Don't tell them." Kept because in `Hidden` mode it is the better beat.

**Free consequence:** any "touch phones to prove we're both Residents" trust ritual is a trap, since an armed Insider accepts the offer.

**Limbo dropped.** The 15–30s failed-deactivation state was invented to punish probing a self-deactivate button. No button, no probe, nothing to punish. The *house tried and failed* fiction is unused and available if it finds another home.

### D-010 · Physical conduct rules are a named category — **decided**
Four rules players must remember: **don't run · don't speak · don't dodge · don't conceal your phone.** The last is new and necessary — otherwise clutching your phone to your chest is a free, cost-free immunity to Revoke that gets discovered in round one.

**This is not erosion of pillar P2.** P2 governs *mechanics*, which the device enforces by refusing. These govern *your body in a real house*, which no device can enforce and none should try. **The line is drawn explicitly so future physical rules don't read as design debt.**

### D-011 · Round length: ~25 minutes as an estimate and a ceiling — **decided** (reverses D-005)
**My arithmetic computed a lower bound and I presented it as a prediction.** Task time is not round time. Players shadow people they suspect, sweep for revoked players, camp the Terminal, hesitate, and choose to do nothing.

The round ends when a bar empties, so its length is **behaviour-driven**: a ~8-minute task-time floor, a ~25-minute realistic round. The floor is not reachable under pressure — two Insiders reach parity in ~2 minutes of clean hunting, so pure task-rushing loses. Adjust from live play.

**Knock-ons:**
- **Unlock delay restored to 20–120s.** At ~25 min it is proportionate again. The finding that survives is the *coupling*: F-013 is reclassified from a gap to a dependency — whenever the round estimate moves, this number must be re-derived, and the two live in different sections.
- **A-5 (~25s inter-marker travel) is now less load-bearing but not less important.** Round length no longer depends on it. **The 120s Egress timer still depends on it entirely, and that gates a win condition.**
- **F-017 is reopened.** See D-012.

### D-012 · F-017 reopened → **OI-6** (ghost dead time)
Recorded rather than quietly dropped. I verified F-017 as composing at a 15-minute round; **the ~25-minute decision reverses that.** A player revoked at minute 3 now waits five to ten minutes for ghost mode instead of ~3 — the exact failure state ghost mode was built to solve.

**No clean fix exists.** Ghost mode cannot activate before the living know you are out (the sequencing rule), and phase 1 must stay empty (anything rendered would glow). **Meeting frequency is the only lever.** Instrumented in the metrics table and in E11's acceptance; meeting cooldown is the first thing to try if it runs long.

### D-013 · OI-3 closed — the thin first meeting is by design
Players can vote **Skip**, and the unanimous *Ready to vote* control lets an empty meeting end in seconds. **Choosing to do nothing is a legitimate move**, and a meeting producing no revoke is a valid outcome rather than a wasted phase. No mitigation is being built. **Watch the skip rate at *second* meetings** — that, not the first, is the signal something is wrong.

---

## State after revision 2

**No phase-blocking items remain.** OI-1 deferred with an expiry condition, OI-2 closed, OI-3 closed. **OI-6 (ghost dead time) is newly open and needs playtest, not a decision.** OI-4 (hidden-mode lever) and OI-5 (settings interior) remain parked.

**Eleven assumptions indexed. A-1 (Egress timer, 120s) is the one that gates a win condition, and it rests on A-5, which is measurable in one evening with a stopwatch.**

---

# Revision 3 — 2026-08-16

### D-014 · Egress timer becomes a lobby setting, 120s default — **closes A-1 and downgrades A-5**
**House size varies more than any default can absorb**, and a number measured in one house does not generalise — an apartment and a three-storey house are not the same game. Host-configurable, so a wrong default is a bad first round rather than a broken game.

**Better than a constant: the app should propose the default from the painted grid.** The setup walk already captured cell geometry, so the longest marker-to-marker path is computable for free and the suggestion adapts per house. Host can override. **This turns what was the riskiest number in the design into a derived one, using data already on hand.**

**A-5 (~25s inter-marker travel) is downgraded accordingly.** Round length is behaviour-driven and the Egress timer is host-tuned, so nothing load-bearing rests on it any more.

### D-015 · E1 is a first increment, not a gate — **decided**
**The coordinated lights-up moment is already validated** — reproduced in prior play with smart-home lights (*"Alexa, turn on all the lights"* during a meeting call). It is not an open question, and there is no reason to stall the build on re-answering it.

Build straight through to the full product. The two remaining unknowns become **playtest instrumentation rather than gates**:
- **Does dim amber alone produce the ~1-metre bubble?** The palette is four luminance values, cheap to change, so a negative result is a tweak rather than a re-plan.
- **Does the target's lamp dying at contact conceal the attacker?** **This is the one with consequences** — if it does not, the anonymous revoke is gone and ghost mode's information design needs revisiting. Watch from the first playtest with a live Revoke.

### D-016 · The meeting call rings — **decided, new requirement**
On the same scheduled timestamp as the masking cut and the lights-up snap, **every phone rings.** The ring lands in the silence the cut just created.

**Why it earns its place despite the silence rule.** The prohibition on phone audio exists because a chirping phone localizes its owner. **At a meeting call, localization is already total** — every lamp in the house just went to full white — so the ring adds no information the light did not already broadcast. And it solves something light cannot: **sound goes through walls.** In a two-storey house you often cannot see another lamp, and heads-down in a dark subroutine you may not register your own screen changing.

Specified: **identical ring for both trigger types** (the screen carries the difference; a differentiated ring would tell players in transit something they learn thirty seconds later anyway) · **plays regardless of the hardware silent switch** (audio session category — a standard iOS gotcha, and half the phones staying quiet at a meeting call is a broken party game) · **stops on acknowledgement or when lights-up completes**, never indefinitely, or a slow player is localized by their own phone.

> **Scope guard written into both documents:** the meeting ring is the **sole** permitted phone-emitted sound and is global by construction. **It is not a precedent for audio anywhere else.** Subroutines stay silent or haptic-only; abilities stay silent; nothing that fires for a subset ever makes a sound.

### D-017 · Base design record marked superseded — **done** (retired entirely 2026-08-17)
A prominent header now points at this GDD, with a was/is table covering the twelve places it is actively wrong (deaths, Capgras, the old system-failure event, shoulder tap and target confirmation, phones stopping advertising, dots, Evict, ghost mode open, voting undesigned, red palette, eleven diagnostics, round sizing) and a list of the sections that survive intact and are still worth reading.

**Rationale:** this project has already been bitten once by a stale design document, which is why `design-doc-revisions.md` exists. Two revision rounds have now passed over that file without touching it.

---

## State after revision 3

**No phase-blocking items. No gate.** Build proceeds to full product.

Open: **OI-6** (ghost dead time — playtest, not a decision), **OI-4** (hidden-mode lever), **OI-5** (settings interior). Watched: the amber/Tier-0 question and the contact-conceals-attacker question.

**Next workflow:** `gds-game-architecture`, pointed at the existing device mockup and subroutine bench rather than re-deriving screens.

---

# Revision 4 — 2026-08-16 (architecture pass)

Produced during `gds-game-architecture` Steps 1–3. **One decision reverses the engine choice from the original brainstorm.**

### D-018 · Stack reversed: Kotlin Multiplatform + Compose Multiplatform — **decided**
**Supersedes `brainstorm-intent.md` §1** (native Swift, iPhone-only) and the GDD's engine section.

**Two premises changed.**

**1. The visual rationale did not survive scrutiny.** The original argument was that the retro phone-OS springboard *is a description of UIKit*, so the OS vocabulary comes free. But **this is a fake retro OS, not iOS** — every element is drawn custom to look like a 2001 PDA, and iOS 26's Liquid Glass overhaul makes stock controls read as broken inside the aesthetic. The custom control layer was always going to be built. UIKit contributes gestures, lifecycle and Core Animation, not a springboard.

**2. Android is real, not aspirational.** The original plan — "Android becomes a separate native app" — builds the entire fake phone OS **twice**.

**The requirement that decided it: screen parity is leak-critical.** With mixed-platform play the invariant must hold across four role/platform combinations, maintained by two independently-written codebases drifting on every OS update. One rendering engine collapses that to one implementation and one differential screenshot test. **Divergence becomes impossible rather than merely discouraged.**

**Compose MP over Flutter, and it was close.** Flutter's Impeller is genuinely better engineered for the one hard rendering requirement — AOT shaders, Metal, no first-run jank, Skia retired. Compose MP is still Skia→Metal and *shader compilation stalls* are its most-documented iOS frame-drop source. **Language count decided it:** Flutter needs Dart + Swift + Kotlin (platform channels on both sides); Compose MP needs Kotlin for shared logic, shared UI *and* the whole Android platform layer, with Swift only for a thin iOS shell. For a solo developer on attempt three whose binding constraint is calendar, that is the argument. Secondary: `expect`/`actual` maps onto the functional-core/imperative-shell boundary as a language feature rather than a bridge beside it.

**Shared fraction ~60–70%**, because the UI moves into the shared column. KMP-core-only (~35–45%) was rejected because it does not solve divergence.

**Accepted costs:** two languages instead of one · Gradle↔Xcode interop tax paid daily · the shader-stall risk, gated by story 1.7 and mitigated by pre-warming the blackout draw. **Fallback if 1.7 fails: Flutter.**

**Versions verified 2026-08-16:** Kotlin 2.4.10 · Compose Multiplatform 1.11.0 · Xcode 26.5 / Swift 6.3.2 · iOS 26 SDK mandatory for App Store since 2026-04-28 · deployment target iOS 26.

**The Godot tripwire is deleted.** Godot would need GDExtension per platform for every sensor (native work twice anyway), hand-rolled OS widgets, and an engine runtime for a game with no scene graph, no physics and no sprites.

### D-019 · The ±50 ms simultaneity target was invented, not derived — **relaxed to ±150 ms**
Latency and simultaneity are separate problems, and I conflated them. **Delivery latency is fully solved by scheduling** — the caller waits on a calling screen for 1–2 s, which was always the design. Relative skew depends only on clock-offset accuracy.

Checked every event: perimeter arming is looser than the meeting; the ring across eight phones reads as normal rather than broken when slightly ragged; **Sync Pulse** looked tight but the design already forbids twitch timing, so a generous tap window absorbs device skew. **Nothing in the game needs ±50 ms.**

**Protocol upgraded to broadcast → acknowledge → commit**, with a required **ack timeout** — a client that never acks must not stall the caller. Lands on the disconnect/rejoin system (now E0.8).

**The one genuinely tight requirement is single-device:** the lamp blanks in the same frame as contact. That is the entire mitigation for losing the anonymous revoke, and it is now the stack gate.

### D-020 · Portability discipline — **adopted regardless of stack**
**No platform types in the simulation core.** No dates, no radio types, no UI types. Events with integer timestamps in; state and effects out. It is the same constraint that makes the core deterministically replayable and headless-testable, so it costs nothing and keeps every porting option open.

### D-021 · E0 added — foundations — **decided**
Three systems appeared in no epic: **session recording + deterministic replay**, **the differential leak harness**, and **map persistence + disconnect/rejoin**.

**Why they are E0 and not E14.** A large share of this game's requirements are *negative* — never confirm alignment, never react on the actor's device, screens structurally identical — and **"no leak occurred" has no failing state.** A thousand passing tests are compatible with the app shipping the identity map to every client. **The testable form is differential:** record the complete client transcript, run the same seeded round twice with a role swapped, diff. Anything that differs is a legitimate ability payload or a leak, and there is no third category.

**Determinism was already latent in the design** — *roll error once at capture, never re-roll*, *store the signed jitter offset* — specified for player-facing consistency and never written down as a requirement. Differential testing depends on it.

**And the game is otherwise undebuggable by construction:** eight phones, a dark house, enforced silence, randomized fog, players lying by design. No debugger, no reliable witnesses, no reproduction. **The test rig is eight iPhones in one room — realistically monthly, an event rather than a development loop.** Headless simulation is what keeps the playtest about *feel* instead of *correctness*.

**Also decided: a fixed-timestep tick decoupled from render.** Display-link equivalents are variable-rate and throttle thermally, so they cannot drive a replayable simulation.

### D-022 · Complexity is two axes, not one — **recorded**
The systems table now rates **implementation weight** and **blast radius** separately. Ghost mode is light and catastrophic; the subroutine framework is heavy and harmless; **the fog pipeline is heavy and catastrophic, and is the one to fear.** Architectural effort belongs on the four catastrophic rows.

Also recorded: the genre table rates *party-game* as **low** complexity, which is wrong here and should not be inherited. **Architect it like a networked multiplayer title.**

### D-023 · OI-7 opened — Android's torch, and no framework fixes it
`setTorchModeOn(level:)` is fine-grained on every iPhone in a decade; **Android exposes strength levels only on hardware reporting multiple levels**, binary below that, with PWM already dismissed as unreliable. **Graduated light is the core mechanic**, so the Android build diverges in the most important dimension — not in layout, in *light* — on any stack.

Design decision, not engineering: **degrade gracefully, or declare a minimum torch capability the way the game declares a minimum OS version.** Answer before the Android milestone, not during.

---

## State after revision 4

**Architecture workflow: Steps 1–3 of 9 complete.** `game-architecture.md` holds project context, the two-axis systems table, the architectural spine, and the engine decision.

**Open:** OI-4 (hidden-mode lever) · OI-5 (settings interior) · OI-6 (ghost dead time) · **OI-7 (Android torch)**.
**Gating:** story **1.7** — the stack gate — runs as a standalone spike before E0 is committed to.

---

# Revision 5 — 2026-08-16

### D-024 · OI-7 corrected and closed — the torch was never the core mechanic
**The original framing was wrong.** It claimed graduated light is the core mechanic, so Android's weak torch degrades the most important dimension. **The torch is not in the light ladder at all** — Tier 0 is the dim amber *screen* lamp, and screen brightness override works identically on both platforms.

**Resolved physically:** the Tier 1 printable becomes a **tube with an optional end cap**. Where software cannot dim the LED, paper does. The tube still narrows the beam to ~20–30°, which is what produces "walk past someone and miss them"; the cap attenuates. **Cap the tube, never tape paper across the lens** — 25 continuous minutes puts paper on the warmest part of the phone, and an inch of standoff is safer and optically better.

**Accepted:** on binary-torch hardware the app can switch the torch but not ramp it. Fine, because **every light event in the game is a screen event.** Small footnote: Tier 0 stays complete everywhere because the torch is optional, but a player who wants the torch on that hardware needs Tier 1.

### D-025 · Transport reopened — MultipeerConnectivity is dead
iOS-only, and the stack reversal disqualified it. It was the local-mesh fallback for basements and bad wifi.

**Replacement is likely better:** an embedded server on the host, mDNS discovery, websockets over the local network — **all multiplatform Kotlin, so transport moves into the shared column** instead of being written twice. **Remaining hole:** no-infrastructure environments. Step 4 fork is host-as-hotspot vs. low-bandwidth game state over the BLE layer already running for occupancy.

### D-026 · Kotlin/Native GC identified as the sharper half of the stack gate
Story 1.7 is now two questions, and the second is worse. **A shader stall is a first-run problem you can pre-warm away; a GC pause can hit any frame, forever** — and the frame that matters is the lamp blackout, where a pause does not drop a frame, it **un-anonymises a revoke.**

**Fix: a no-allocation discipline on the blackout path, enforced by a permanent allocation assertion**, not a one-time measurement. A discipline any future commit can break needs a test that stays.

### D-027 · The play manual added as a deliverable (stories 4.12, 4.13)
*"Tutorial integration is none, by design"* is true of **mechanics** — every game rule is a device state, so the app teaches by refusing. **It is false of the physical rules**, which no device can enforce and the host must say aloud.

The manual holds: the four physical conduct rules · snoot folding and when to add the cap · host setup guidance · the photosensitivity notice · how to explain the contact tap in ten seconds. **One short artifact, and the only thing a player reads** — everything else in this design refuses to explain itself on purpose.

### D-028 · A review discipline, recorded
**Three findings today came from treating two adjacent concepts as one, and two were wrong in the alarming direction:** rendering vs. radio (the finding dissolved) · latency vs. device skew (the number was invented and 4× too tight) · screen lamp vs. torch (a design emergency that was a footnote).

**The design document is dense enough that everything is adjacent to everything.** When a claim takes the form *"X requires Y,"* check whether it is actually *X adjacent to Y*. **A single-round trace catches these automatically** — it is how the first fourteen findings were produced.

---

## State after revision 5

Architecture Steps 1–3 complete. **Open:** OI-4 (hidden-mode lever) · OI-5 (settings interior) · OI-6 (ghost dead time). **OI-7 closed.**
**Gating:** stories **1.7a/1.7b** — the stack gate — run as a standalone spike before E0 is committed to.
**Next:** Step 4, opening on the transport fork.

---

# Revision 6 — 2026-08-16 (architecture Step 4)

### D-029 · Transport — embedded Ktor server, mDNS, websockets — **decided**
Ktor 3.5.1 on the host; peers over websockets; mDNS discovery is the one platform piece behind `expect`/`actual`. **All multiplatform Kotlin, so transport joins the shared column** — a direct dividend of the stack reversal, replacing MultipeerConnectivity which died with it. No-wifi fallback is **manual Personal Hotspot**, documented in the play manual, since iOS will not let an app toggle it.

**Gotcha caught:** first local-network discovery raises the **iOS Local Network permission modal**. At arming, in a dark room, on top of the retro interface, that is the same failure as the NFC system sheet. **Warm it during setup, in the light.**

### D-030 · Authority — host device, no migration — **decided, risks accepted**
Migration deferred to v2. Accepted: single point of failure (the *downed device is a game state* rule covers **player** devices; host process death is a separate, now-accepted failure) · thermal throttling on a device also rendering, scanning, and sensing · untestable below the full eight-phone rig.

**The gap was never migration — it was the failure UX, and that ships in v1 (story 0.9).** "No migration" describes the system and says nothing about seven silenced people in a dark house whose phones just lost the server. **Lamps hold their last state** — every light going out at once is a safety event, not a bug report — plus a legible message, and nobody left playing a game that stopped existing.

**Cheap partial (story 0.10): host crash recovery from the local recording.** Same device, same authority, no election. Not migration; nearly free given E0.

### D-031 · State, concurrency, clock, persistence
**Event-sourced pure core, strict server authority, thin clients** — forced by the anonymity rule, not chosen for elegance: a client cannot leak what it was never sent. **Single confinement dispatcher**; five I/O domains funnel into one ordered queue. **Fixed-timestep tick from a coroutine loop**, never the frame clock. **Clock:** monotonic, min-RTT offset sampling, 30 s re-sync, **slew never jump**, ack timeout 2 s, recordings stamped by tick index not wall clock. **Persistence:** kotlinx.serialization, versioned JSON maps with an export path, JSONL recordings.

### D-032 · BLE identity — ephemeral rotating tokens — **new requirement**
**A stable advertised identifier defeats the entire map design.** Any client hearing it can track that individual all round, making *counts, never identities* decorative — and **server-side anonymization cannot help, because the leak is in the radio, below the server.**

Rotating ephemeral token, **15 s**, advertised payload and nothing else, resolvable only by the authority. Shorter rotation is free server-side, so the interval is bounded by radio overhead rather than usefulness.

**⚠️ Backgrounding may break this rather than weaken it.** iOS moves service UUIDs to the overflow advertising area and drops the local name on background. The existing *"backgrounding degrades advertising"* note was written when the payload meant *"I exist"*; it now carries the whole anonymity scheme. Worst case a phone becomes identifiable by something nobody designed — silent, house-wide, permanent for the round. **Requires a radio-level test: sniff what the antenna emits, not what the code asked it to emit.**

### D-033 · The differential harness has a blind spot — **correction**
**The Step 2 claim that "anything that differs is a legitimate ability payload or a leak, and there is no third category" was wrong.**

Because redaction lives in the core (D6), **both transcripts in a differential run are produced by the same redaction code.** A *symmetric* bug — shipping real Egress progress to everybody — yields identical transcripts and a clean diff. The harness reports no leak and is technically correct: there was no role-asymmetric difference. There was just a leak.

**Three leak surfaces, three independent tests, none covering another:**

| Layer | Leak shape | Test |
|---|---|---|
| Role-asymmetric | One role gets what the other does not | Differential harness (0.6) |
| Symmetric | Everyone gets what nobody should | **Per-message schema allowlist (0.6b)** |
| Below the effect boundary | The radio emits what the app never sent | **Radio-level sniffing (0.6c)** |

---

## State after revision 6

**Architecture Steps 1–4 of 9 complete.** Open: OI-4, OI-5, OI-6. Gating: stories 1.7a/1.7b.

**Unanswered and recorded:** every item in this architecture is load-bearing and nothing is cuttable — which is what a project that dies of calendar looks like from the inside. Revisit at the first honest schedule checkpoint.

---

# Revision 7 — 2026-08-16 (architecture Step 5)

### D-034 · Errors are silent to the *player*, loud to the *authority* — **refined**
The original rule was "silent on clients, always," which is right as a leak rule and dangerous as a robustness rule. **A dead BLE stack makes a living player invisible on the occupancy map** — a substantial advantage, delivered silently, that nobody including them can detect. Clients report failures as events; the authority knows.

**Unchanged:** no dialogs, no toasts, no unexpected screen state, and **the lamp never changes as a side effect of an error** — light is game state, so an error that dims or blanks the screen broadcasts a false game signal to the whole room.

### D-035 · The house notice — **new mechanic** (closes the silent-degradation gap)
**The house reports device degradation to everyone, at the meeting**, in its administrative register: *"NOTICE: Occupant Marcus was unreachable 21:04–21:07. Occupancy data for this interval is incomplete."* Radio failure and backgrounding trigger it identically.

**Delivered at meetings, not in real time** — that is where aggregate evidence already arrives, it keeps the house from chattering mid-round (preserving *the house speaks last*), and it lands the correction where people are arguing from bad data.

**Needs no anti-exploit.** Backgrounding deliberately to manufacture a connectivity excuse backfires — being named off-network means you generated no check-ins either. *"The system could not see you for three minutes"* is an accusation, not an alibi. **Self-punishing.**

**It removes the host as the single point of social responsibility**, and it makes the map's deliberate unreliability diegetic in writing.

### D-036 · A crashed client looks exactly like a revocation — **recorded as unfixable**
Its lamp goes dark, in a dark house, which is precisely what revocation looks like — to that player and to anyone watching. They cannot speak; sitting down tells the room something false; standing there with a dead phone reads as revoked anyway. **Mitigation is speed only** — relaunch fast, rejoin from the recording. **Written down as unfixable so nobody spends a week on it.**

### D-037 · Balance values lock at arming and are stamped into the recording
Runtime tunability is required because **the test rig is eight iPhones in one dark room, realistically monthly** — a rebuild-and-redeploy per tuning change yields one data point per session instead of five. **But locked at arming**, or whoever runs the server can shorten their own Revoke cooldown mid-round while holding an armed token. Stamping is required for replay determinism regardless.

### D-038 · Three build variants, and fuzzing over modelling
**Release / playtest / debug.** Playtest = recording on, cheats on, debug surfaces compiled out, permanently marked. On release you cannot skip a meeting or reset a round that broke in minute two — on the one night a month you have eight people in a house. On debug you are playtesting a build that is not the game. **Debug surfaces are compiled out, never runtime-flagged.**

**Headless simulation must fuzz, not model.** A scripted Resident that walks efficiently to the nearest marker will run ten thousand rounds and never discover that beelining gets you revoked, because the simulated Insiders play the way you imagined too. **That is testing a mental model at scale and calling it evidence.** Include camping, wandering, idling, spamming, and random policies.

### D-039 · The host-holds-authority-state concern — **raised and retracted**
Claimed as the game's strongest cheat vector; **the claim was wrong.** On a release build with an unmodified device the app container is not user-browsable on either platform. The host's app shows exactly what their role warrants. **The error was conflating *data present* with *data accessible*.**

Three narrow rules survive: crash diagnostics must not carry game state · recordings must not be user-exportable mid-round · playtest and debug builds genuinely do expose it, which is why the variant split and visible marker matter.

### D-040 · A recurring error class, now four instances — **review discipline reinforced**
| Conflated | Actually |
|---|---|
| Revoked phones "become invisible" | **Rendering** rule read as **radio** rule |
| The flip needs ±50 ms | **Delivery latency** conflated with **device skew** |
| Graduated light is the core mechanic | **Screen lamp** conflated with **torch** |
| The host can read the authority state | **Data present** conflated with **data accessible** |

**Three of four were wrong in the alarming direction.** When a claim takes the form *"X requires Y,"* check whether it is actually *X adjacent to Y*.

---

## State after revision 7

**Architecture Steps 1–5 of 9 complete.** Open: OI-4, OI-5, OI-6. Gating: stories 1.7a/1.7b.

---

# Revision 8 — 2026-08-16 (architecture Step 6)

### D-041 · Module structure — six modules, not twelve
**Modules exist only where they enforce a load-bearing edge**; everything else is packages. Gradle configuration time is paid on every incremental build, all day, for months, by one person whose binding constraint is calendar — a module that enforces nothing is a tax with no return.

`model` · `core` · `platform` · `ui` · `harness` · app roots. `protocol` folded into `model`; `transport` into `platform`; everything under `core/rules/` was packages wearing module costumes.

**The three edges that justify modules at all:** `core` cannot import coroutines (purity and synchrony survive future contributors) · `core` cannot import datetime (D-020 enforced by Gradle rather than memory) · **`ui` cannot import `core`** (thin clients become a fact rather than a policy).

**Rejected alternative worth recording:** one module plus architecture tests (Konsist-style). Faster builds, but a test can be deleted or skipped where a missing dependency edge cannot. The six-module split is the middle.

### D-042 · Input echo is not game logic — **clarification that saves the `ui ↛ core` edge**
The naive reading of "thin clients" implies a network round-trip to draw a pressed button. It does not. **A subroutine's pattern arrives as an Effect; the UI displays it, captures taps, and echoes them locally** — lighting the dot you just touched reflects your own input rather than simulating anything. The sequence returns as an Intent and the server verifies it against the pattern it generated. No duplication, no round-trip for feedback.

### D-043 · One documented exception to strict server authority — the motion budget
**100 Hz cannot round-trip**, and the draining bar must fail immediately and visibly, so the client computes its own budget and decides its own failure. **The only place in the game where a client adjudicates anything**, documented so exceptions do not breed.

Low-value to cheat: a Insider gains nothing (their completions never advance System Integrity) and a Resident cheating it is *visibly moving during a task* — a physical tell in a room full of people. The honesty framework already carries heavier obligations.

### D-044 · The redaction contract moves to `model/schema/` — one source of truth
It had been filed under `harness`, making **the specification of the entire redaction contract a test artifact** while the redaction code lived in `core`. Two hand-maintained statements of the same truth drift, and when they drift the test keeps passing because it asserts against its own copy. **One declaration, read by the runtime redaction and the harness alike.**

### D-045 · Two structural corrections
**The tick driver moves to the app roots.** `core/sim/` holds tick *semantics* — `advance(state, ticks)`, pure and testable — but the coroutine loop that drives it cannot live in the module forbidden from importing coroutines. As first drawn, the structure contradicted its own boundary table.

**Story 0.6c (radio emission sniffing) cannot live in `harness`**, which is forbidden from touching platform. It becomes an instrumented on-device test beside `platform/radio/`. **The three leak surfaces now live in two homes, so all three must appear in the same CI stage listing** even though they run differently — otherwise a split model quietly loses a layer.

### D-046 · The play manual and snoot printable are shipped assets, not docs
The light ladder specifies an **in-app PDF**. Filed under `docs/` they would never ship. Source in `docs/`, built artifact in `ui/assets/`.

---

## State after revision 8

**Architecture Steps 1–6 of 9 complete.** Open: OI-4, OI-5, OI-6. Gating: stories 1.7a/1.7b.

---

# Revision 9 — 2026-08-16 (architecture Step 7)

### D-047 · "Appears to succeed" is a **device-side** guarantee — **scope corrected**
The rule promised more than physics allows. **Surge is world-observable** — a lamp flares and a phone emits sound. Surge a target you can see, watch nothing happen, and you have learned they are revoked without touching the panel.

**Accepted rather than engineered around**, because it mostly self-mitigates: a Insider usually cannot see their target, so "no flare" is ambiguous between *revoked* and *elsewhere*. The leak opens only in line of sight, against someone already suspected. **Worded precisely so nobody tries to build the impossible version.**

### D-048 · Client types must be physically incapable of carrying ground truth
The fog observation struct held `trueCount` behind a comment reading *"authority only."* It lived in `model`, which `ui` imports, and it was `@Serializable`. **A comment is not a boundary** — someone serializes the whole struct in a hurry and the truth ships. Not malice; a Tuesday.

**Split: `Observation` (authority) and `ObservationView` (client).** Make-illegal-states-unrepresentable, applied where it actually matters.

### D-049 · Redaction projects by narrower type, never by nulling fields
A boolean allowlist guards *which effect types* a viewer may receive and says nothing about *which fields inside them*. An effect could be permitted while carrying a forbidden field, and the CI schema check would pass it because the type was on the list.

**Construct a different, narrower type per audience.** The allowlist becomes field-complete for free, **because the type *is* the field list.** Nulled fields are worse than useless — the field persists, and someone makes it non-null later for an unrelated reason.

Also fixed: **viewers are classified, never identified.** The schema keys on viewer class, never on player identity — a rule written per-player is a rule that can be wrong for exactly one person.

### D-050 · Vocabulary lint scoped to `model`, `core`, `ui`
`task` appears in every coroutine API and `kill` in process management. Run the lint over `platform` and it gets suppressed forty times a week and then switched off. The three game-facing modules are the ones emitting user-visible strings anyway.

### D-051 · **`project-context.md` has never existed** — and every skill this session has been looking for it
Fifteen consistency rules in a nine-thousand-word architecture document is **a constitution with no courthouse.** An agent implementing three short subroutines will not read all of it to find rule seven.

Every skill run in this session resolved the `**/project-context.md` persistent-facts glob to nothing. **That file is where the compiler-unenforceable rules belong** — short, always loaded, tripped over rather than remembered.

**Action: run `gds-generate-project-context` immediately after this workflow**, seeded with the review-enforced half of the consistency table.

---

## State after revision 9

**Architecture Steps 1–7 of 9 complete.** Open: OI-4, OI-5, OI-6. Gating: stories 1.7a/1.7b. **New action: create `project-context.md`.**

---

# Revision 10 — 2026-08-16 (architecture Step 8: validation)

### D-052 · Client attribution — the one real security hole (G1)
The checklist's authentication item looked N/A and was not. **Nothing specified how the authority knows an Intent came from the player it claims to** — a second websocket asserting it was Marcus would have been accepted.

**Unlike every other cheat here, this one is remote, undetectable, and requires no physical act** — the honesty framework cannot reach it because it does not happen in the room.

**Session token bound to a *seat*, issued at join. Intents attributed by connection; a client never names itself.** A lobby code gets you *a* seat, never *that* seat.

**⚠️ Reconnect is where this is actually tested.** Re-deriving identity from the lobby code on resume — the path of least resistance — rebuilds the whole hole in the one code path nobody exercises.

### D-053 · Four smaller validation gaps closed
**G2:** `core/rules/endgame/` added — win checks, reveal ordering, chain disclosure had no home.
**G3:** reconnect policy — bounded exponential backoff, **lamp holds its last authorised state**, resume by stored seat token.
**G4:** performance instrumentation is **event-triggered, not continuous**. Average FPS is the wrong measurement when you care about **one frame in ninety thousand**; measure blackout latency every time it fires, plus scan acquisition, flip skew and dropped motion windows. Accumulates evidence across every round played instead of needing a profiling session with eight phones.
**G5:** **test fixtures are snapshot from recordings**, not hand-written. A hand-built `State` encodes the tester's idea of a mid-round state — the identical failure caught earlier with scripted players.

### D-054 · Version compatibility verified, and the rule recorded
**Compose Multiplatform 1.11.x requires Kotlin 2.2+; native targets require 2.3.10+.** On Kotlin 2.4.10 this clears, but only just. **Pin CMP 1.11.1.** The rule matters more than the numbers — **re-verify this pairing on every upgrade**.

### D-055 · Architectural coverage is not design completeness
14/14 systems have a named home. **That says nothing about whether their numbers exist** — the fog pipeline has a home, four patterns and a chokepoint, and its band thresholds, error rate and jitter magnitude are all still open (A-7, A-8). **Thirteen indexed assumptions remain and every one needs playtest.**

### D-056 · The scope amber gets a trigger and a cut list — **decided**
**An amber with no trigger is a decoration, not a deferral.** The objection has gone unanswered through five party rounds and this project has died of calendar twice.

**Trigger — milestone, not date: when E0–E5 are complete, stop and review the remainder against how long that actually took.** The point of writing it down is granting permission to act on it.

**Cut list, ordered by pain, decided now while nothing is under pressure:**

| Order | Cut | Cost |
|---|---|---|
| 1 | NFC | Already an add-on; the game is complete on QR |
| 2 | Timelapse playback | Keep live counts. Large chunk of E8; the Terminal still works |
| 3 | Degrading subroutines | Loses the only signal the house is winning. Painful, not fatal |
| 4 | Ten subroutines → six | Keep 2 bright / 2 medium / 2 dark so the light-signature axis survives |
| 5 | Spoof | The GDD already flags it as possibly never used |
| 6 | Snoot printable | Tier 0 is complete without it |

**Nothing on this list is a leak harness, replay, or core purity.** It pre-decides what makes the project **big**, never what makes it **correct** — because deciding under pressure is how the wrong half gets cut.

---

## State after revision 10

**Architecture Steps 1–8 of 9 complete. Overall validation: PASS.**
Open: OI-4, OI-5, OI-6. Gating: stories 1.7a/1.7b. Action: create `project-context.md`.

---

# Revision 11 — 2026-08-16 (renaming pass)

Three renames, driven by one observation: **the vocabulary still encoded the premise the game used to have.**

### D-057 · Game renamed: **Guest Network → Someone's Home**
*Guest Network* meant *something is on your network that isn't family*. Under blackmail it **is** family — so the name advertised a premise the game no longer has.

**Cleared 2026-08-16**, same methodology as the original, controls included:

| Query | Result |
|---|---|
| A known-populated two-word control term | **101** — matches the original run exactly ✓ |
| `WM:"someones home"` | **0** |
| `WM:someone AND WM:home` | **0** — all classes, live and dead |
| App Store / Play | Not present |

**Two candidates were tested and rejected first:**
- **Welcome Home** — **knocked out.** Serial `87674080`, **LIVE/REGISTERED, Class 009, "Computer game programs; Computer game software"**, King Show Games Inc. Identical wordmark, exact class of goods.
- **We Are Home** — clear in our classes (no live mark in 009/041/028), **but** the live `WELCOME HOME` Class 009 registration made confusing similarity a real question. Dropped rather than gambled on.

> **⚠️ A methodology finding worth keeping.** The USPTO UI **silently returns "No results found" when a URL-borne query fails to populate the search box.** The first *Welcome Home* attempt read as a clean zero and was a false negative — the real answer was 241. **Never trust a zero without confirming the query is visible in the box and a control query returns its expected count.** This is exactly why the original search ran controls.

**Why the name works:** *"Is someone home?"* is reassuring; *"Someone's home."* is not, and the shift lands entirely on which one you hear. It is also **the literal phrasing of an occupancy-sensor notification** — the system the whole map is built on. Three readings, all true.

### D-058 · Role renamed: **Guest → Insider**
"Guest" encoded the *synthetic replacement* premise: an intruder wearing your friend's face, where *you live here, you're visiting* did real work. **Blackmail inverts it** — the traitor is your actual friend, does live here, wasn't replaced, and didn't choose this. The word was factually untrue in the fiction and **carried no victimhood at all**.

**Insider** is as plain as *Resident*, needs no explanation, and is real security vocabulary (*insider threat*) — the register the house thinks in.

**And it fixes a structural error:** Resident/Guest implied two disjoint groups. **Resident/Insider is not a partition — everyone is a Resident; some are also Insiders.** A hidden second state on someone who is still one of you, which is both accurate under blackmail and a sharper horror.

*Rejected: **Proxy*** (acting-on-behalf-of is exactly right, but too jargon-adjacent), ***Accessory*** (legal accomplice + HomeKit device is a superb double meaning, but reads oddly as a role label), ***Staff*** (best fictional relationship — the house's coerced employees — but a mass noun with no clean singular).

### D-059 · Group action: **group Revoke → Restrain**
Reverses a change inherited from `design-doc-revisions.md` §15.7, which replaced *Evict* with a group *Revoke* and justified it as the Residents being forced to speak in the house's vocabulary. **That was too subtle for what it cost.** When an Insider revokes they wield the house's power and it lands; when six people vote, they were borrowing a word for something they do not control.

> **Restrain is a physical act the house cannot prevent.**

| | Verb | Nature |
|---|---|---|
| **Insider** | **Revoke** | System power. Silent, invisible, lent by the house |
| **Residents** | **Restrain** | **Physical** power. Collective, in the open, unmediated |

**Only one requires permission.** Everything else in this game routes through a system the house owns. This does not — it is the one capability the Residents hold that the house cannot reach, and it restores the human/system split.

**Mechanical resolution (story 10.9b):** the group restrains someone, but the *house* has not revoked them — so **the house deauthorises anyone the group restrains**, because a restrained occupant is no longer useful to it. **The system is watching the meeting and quietly finishing the job.** The deauthorisation is the house's *response* to the restraint, never its cause.

### D-060 · Propagation
`gdd.md`, `epics.md`, `game-architecture.md`, `decision-log.md` updated. Architecture naming rules now cover the role words as well as the ability words, and record that **Revoke and Restrain are not synonyms and must never be collapsed.** Repo root in the structure tree is `someone-is-home/`.

**The rules now state the vocabulary positively, 2026-08-17.** They used to be written as a list of what not to say. They now name only what the vocabulary *is* and declare the list exhaustive, which is both shorter and easier to check; the lint's word list carries the mechanical detail, where it belongs.

### D-061 · Register brought in line across the spec — 2026-08-17

**Pillar: access is revoked; nobody dies.** The design settled that early, but roughly forty sentences across the GDD, epics, architecture, decision log, project context and one Kotlin comment were drafted before it and still used the register of the retired design record. They now read in the game's own terms — **revoke**, **revoked player**, **target**, **revocation**.

**No design changed.** Every rule those sentences state is the rule it stated before; only the register moved.

**Ordinary English is left alone.** A lamp or a phone can still stop working, hardware and processes can still fail, and a player still has a physical presence in a real house — that last one is precisely what pillar P2 governs. The distinction is between the game's vocabulary and the language everyone speaks; only the former is regulated.

**Why it matters beyond tidiness.** These documents are read as the spec when Revoke gets implemented. Prose teaches the codebase what to call things, and the vocabulary rule exists because naming drift surfaces in a UI string eventually.

**A map-editor shape tag is now `passage`**, alongside `room` and `stairs` — the ability that governs it was already **Override**, and the tag had never been brought into line.

**Paths and identifiers, renamed 2026-08-17.** The deferred half of this decision is now done: the repo root, the output folder `gdd-someone-is-home-2026-08-16/`, and `project_name` across all six `_bmad` config files.

**The convention is one form, not two.** **The apostrophe in *Someone's Home* cannot appear in a directory or repository name**, so every path and identifier uses **`someone-is-home`** — repo, directories, config values — and *Someone's Home* is used in every piece of user-facing text and prose. An intermediate `SomeoneIsHome` spelling was applied earlier the same day and replaced within hours: two path spellings for one project is exactly the drift this log exists to prevent. The pre-rename name survives here only where it records the rename itself (D-057).

---

## State after revision 11

**Name:** *Someone's Home* · **Roles:** Resident / Insider · **Verbs:** Revoke (Insider) / Restrain (group).
Architecture Steps 1–9 complete. Open: OI-4, OI-5, OI-6. Gating: stories 1.7a/1.7b. Action: create `project-context.md`.

---

# Revision 12 — 2026-08-17 (story 1.7 executed on hardware)

### D-062 · **The stack gate passes. Kotlin Multiplatform is confirmed; the Flutter fallback is not taken.**

Nine runs on an iPhone 16 Pro, release binary, each in a fresh process, all thermally nominal. Evidence in `spike-stackgate/FINDINGS.md`; raw data is deliberately untracked.

**1.7a — shader stalls — is a non-issue and the mitigation should not be built.** The first blackout after a cold launch drew in 3.3–5.6 ms, inside one 8.335 ms frame, and 200 trials with pre-warm disabled produced no late frame. **Pre-warming the blackout draw path at round start is now scope that can be deleted rather than written.** Renderer-idle was cleared in the same pass: 3–10 s between blackouts — the real game's condition, where the lamp sits static for minutes — moved p50 by 0.1 ms.

**1.7b — GC pauses — is real, and resolves to a threshold rather than a verdict.** Late blackouts track allocation, confirmed against a control that ran the same threads and loop counts with preallocated buffers and produced zero late frames in 5 000 trials. So it is the collector, not thread contention.

| | |
|---|---|
| Compose alone, idle | ~0.04 MB/s |
| **Verified clean** | **0.54 MB/s** — 55 200 blackouts, **one** late frame |
| Fails | 3.00 MB/s — 0.36% miss a frame, every one with a collection in its window |

**The single late frame in the clean set had no collection in its window and no main-thread stall** — OS scheduling, ~1 in 55 000, and no engine choice removes it. That is the floor the design tolerates regardless of stack, and it is worth knowing that the alternative would not have been better.

**A collection landing inside a blackout is not sufficient to make it late** — 153 overlaps at 0.54 MB/s produced no misses, 541 at 3.00 MB/s produced 18. What matters is how hard the heap is churning, not coincidence.

### D-063 · **1.7b's mitigation was aimed at the wrong layer — corrected (story 1.7c)**

The epic specified *a no-allocation discipline on the blackout path, enforced by a permanent allocation assertion.* **That assertion would have stayed green through every failure actually observed.** The allocation driving these collections is on the BLE, motion, effect and recording threads; the blackout path's own allocation never mattered.

**The load-bearing guard is a total allocation-rate budget for the whole app — ~0.5 MB/s, asserted continuously.** Same shape as the original, one level up. The blackout-path assertion is kept because it is cheap and guards a smaller, different failure — a future commit quietly adding allocation to the draw path — but it is not the guard that matters, **and it still has no measured threshold.** `ALLOC_PROBE` returned 6 222 bytes per trial, which is an upper bound covering ~14 frames of ordinary rendering, not the draw path's own cost; the GC-epoch method cannot resolve finer than an epoch.

**This was a good prediction that was one layer off.** D-026 correctly identified the GC as the sharper half and correctly reasoned that a pause can hit any frame forever. The error was assuming the fix belonged where the symptom appears.

### D-064 · **A measurement discipline, learned expensively**

**Four instrument bugs in this spike, and every one produced a plausible pass in the flattering direction.** A wrong Info.plist key that silently capped the app at 60 fps and doubled the apparent frame budget; a display link sampling at half the rate it was measuring; a stall baseline seeded from one sample that flagged 1 022 ordinary frames; and a headline metric — vsync span — that read 0 for every provably-late trial and reported PASS on a FAIL.

**None was caught by the instrument that was wrong.** Each surfaced only when a second, independent number disagreed with it. **This is now the rule for E0's performance instrumentation (G4): no performance number is believed until something that does not share its mechanism agrees.**

It also validates the boxed warnings written into story 1.7 before the spike ran. The first build did report a clean pass while its pressure generator contributed 14% on top of the app's own allocation floor — pressure ON and OFF were statistically indistinguishable, which is precisely the meaningless pass the note predicted. **The control run is what caught it, and the control was only there because the note demanded one.**

### D-065 · **The allocation cliff is higher than the first pass suggested — bisected**

Two further 10 000-trial runs. **The boundary is not sharp and it is not just above 0.54 MB/s.**

| allocation | late | n | rate | 95% upper |
|---|---|---|---|---|
| 0.54 MB/s | 0 | 10 000 | 0.000% | 0.030% |
| 0.99 MB/s | 3 | 10 000 | 0.030% | 0.070% |
| 1.46 MB/s | 0 | 10 000 | 0.000% | 0.030% |
| **3.00 MB/s** | **18** | 5 000 | **0.360%** | 0.534% |

0.99 and 1.46 overlap — three late frames versus zero in ten thousand is not a difference. Combined across 0.54–1.46 MB/s the rate is **0.010%**, the same order as the OS-scheduling floor from D-062; at 3.00 MB/s it is **0.360%**, a **36×** jump. **The cliff sits between 1.5 and 3.0 MB/s and remains unlocated.**

**The design target stays ~0.5 MB/s, but the useful finding is that E0 is not on a knife edge** — roughly 3× margin to anything measurable and 6× to the cliff. Allocation needs a budget, not paranoia, and that distinction changes how much E0 should contort to avoid per-event state copies.

**A confound, recorded rather than buried.** The two bisection runs have a different trigger-phase distribution from every other run — draw-latency mode at 6.05 ms against 8.03 ms — so their triggers landed with about 2 ms more slack before the one-frame threshold. Cause unknown; the pressure workers run on background threads and should not move main-queue timing directly. **It makes the two zero-late results mildly flattering.** It does not threaten the conclusion: MID_A produced GC-linked late frames *despite* the extra slack, and a 36× elevation is far too large to be a phase artifact. **Anyone re-running this should expect the phase distribution to move and should not read a shifted median as a regression.**

---

## Revision 13 — the marker system, and what the house is allowed to say

*Decided 2026-08-19 in conversation, alongside the device-design port. Everything here was a
live question at the start of the day.*

### D-066 · **Events arriving before the round is armed are refused above the rules — decided**

`GameState.armed` had two writers and no readers: the reducer processed every event identically
whether or not a round existed. Demonstrated rather than argued — feeding `ContactMade`,
`SubroutineCompleted` and `MeetingCalled` to `GameState.EMPTY` emitted `AbilityFired`,
`SubroutineProgressed` and `MeetingOpened` respectively.

**The obvious fix is banned.** `if (!state.armed) return Reduction(state, emptyList())` is rule
1's forbidden shape — the absent effect is the signal.

**Resolution: an admission gate above `reduce`.** The authority refuses non-`RoundArmed` events
before the rules see them, and records the refusal. `reduce` stays total and never learns the
flag exists, so the branch is provably outside every client-visible path.

**Severity, stated honestly: this is a replay bug, not a leak.** Arming *constructs* a fresh
state, so pre-arm effects reach clients while the state that would explain them is discarded —
the recording's effect rows and state rows disagree about a round that had not begun, and the
recording is the only debugging instrument this game has. The reachable paths are narrow
(D-067 removes the widest one) but the fuzzer of story 0.10c will hit it on day one.

### D-067 · **Nothing in-game runs in the lobby, including the contact radio — decided**

`ContactMade` is reported by the radio rather than requested by a button, which made it the one
pre-arm event a player could cause by standing near someone. It cannot: the radio does not run
before arming. This is what makes D-066's gate sufficient rather than partial.

### D-068 · **A client may be told it was refused only when the reason is public — decided**

The gate reports its refusal to the client. That is safe **only** because round-state — not armed
yet, already ended — is publicly observable: everyone can see whether the lights are on.

**This reasoning does not survive generalisation, and the generalisation will look like good
consistency.** The moment the same path reports a mid-round refusal — you are revoked, that
target is already revoked, your cooldown is running — the identical code becomes an alignment
leak, written by someone tidying up error handling.

A second safe category emerged: **events no honest client can emit.** Self-targeting is
impossible because contact is phone-to-phone; voting for a revoked player is impossible because
the name is not tappable. Both may be rejected outright.

### D-069 · **Markers are anonymous printed cards carrying an id and a shape — decided**

The host prints a generic sheet and registers each card by selecting a room and scanning. No card
is meaningful until registered.

**The shape is the marker's name.** The app never shows the id; hosts and players both navigate by
the shape, because a shape resolves faster than two digits by torchlight and does not need to be
the right way up. `MARKER 07` is gone from every screen.

**The id exists because paper is lost.** A shape alone was briefly considered sufficient and is
not: a host who mislays a card and prints a replacement creates two physical cards decoding to
the same marker. The old one, found later behind a shelf, would report a player as standing in
whichever room the *new* one was registered to — and that corrupts the Terminal's per-room counts,
which the design deliberately fills with injected error. **The bug would hide inside noise the
design added on purpose, and would be undetectable in play.**

Payload is `version + shape + id`, nine characters, which fits **QR Version 1 at error-correction
level H** — 21×21 modules, the smallest symbol that exists, strongest correction. Capacity
verified empirically at 10 characters. Micro QR was considered and rejected: one finder pattern
instead of three and much weaker correction, in a room where blur is the operating condition.
**Buy scan margin with card size, not with symbol version.**

### D-070 · **The 44-shape roster is the measured one, not the drawn one — decided**

The device design ships a 24-shape fixture. The `shape-encoder` project's 44 are used instead,
because that set was chosen by measuring pairwise confusability at small sizes rather than by
eye. The fixture reintroduces `pentagon` and `hexagon`, both of which that measurement had cut
for reading as "circle with corners"; a test asserts they stay cut.

**THIS SIDE UP is printed on every card**, and it is what earns the full 44 — the tightest pairs
in the data are `semicircle_up`/`semicircle_down` and `arrow_up`/`arrow_down`, which are rotations
of each other and safe only when orientation is fixed.

### D-071 · **Residents are fenced to their assigned markers; Insiders open any registered one**

Recorded with its cost. **The scan's response is role-dependent**, and the readable difference is
behavioural rather than on-screen: scan-and-stay against scan-and-walk-away, where walking away
identifies a Resident. Narrow — Residents rarely scan markers they know are not theirs — and
accepted deliberately. Markers may be assigned to more than one Resident, which is what stops a
Resident's own list from making them a detector for their own markers.

**Every game-side refusal produces one message.** Not assigned to you and blocked upstream are
indistinguishable, and the wording is about the player rather than the marker: *"Nothing of yours
opens here"*, never "already completed" or "belongs to another resident", each of which is a
small statement about someone who is not in the room.

**One refusal is allowed to be specific.** An unregistered card is a fact about a piece of paper,
so it says so. Both refusal screens are drawn by one function so they stay pixel-identical apart
from their text — one of them is Resident-only, and differing icons would let an onlooker tell
which was which from across a room.

### D-072 · **The house announces only what no player could have observed — decided**

Nothing reports an unregistered card to anyone. A count at the end of the round or a notice at
the next meeting was proposed and **rejected**: either turns the app into an arbiter of a
player's claim, letting the room verify testimony. Unreported, it becomes material — a Resident
who mentions it gives up their own position to be useful; an Insider can claim it to explain
standing at a marker doing nothing; neither can be checked.

This clarifies why the house *does* announce a dead radio at the next meeting. A radio failure is
invisible to everyone including the player it happened to, so without the announcement a phantom
appears that nobody designed. **Everything a player saw is theirs to report, or to lie about.**

### D-073 · **Randomness is fresh per match and recorded — decided**

Marker assignment and chain-unlock delays are genuinely random, not fixed. Seeded does not mean
identical: every match draws a fresh seed and `Event.RoundArmed` already carries it. Two valid
shapes — derive from the round seed, or sample at the edge and write the value onto the event, the
way a timestamp already arrives. **The only broken version is drawing inside the rules without
recording**, which costs E0's byte-identical replay.

### D-074 · **A chain unlock is delayed identically whether it was freed by completion or by a
revocation — decided**

Otherwise the delay itself is the tell, and "completion is known immediately, revocation needs
settling" is a natural-sounding implementation that would introduce it silently. Same
distribution, same wording, and the batched alternative — unblocking at the start of the next
round after a meeting — is stronger still because a batch has no timing signal at all.

### D-075 · **The vote does not publish attribution; not voting counts as a Skip — decided**

The living see counts. Only a player outside the system sees who cast what, and sees it live,
which is what makes being out an information privilege rather than a preview. **This reverses
`brainstorm-intent.md` §6's "attribution is shown"** — that note is now stale.

Not voting counts as a Skip rather than as an abstention. Combined with ties already resolving to
Skip, the whole weight of inaction sits behind restraining nobody.

### D-076 · **The notification dim is a lamp change, and every banner must go to everyone**

A banner dims the whole panel behind it. That is not styling: **a phone held as a lamp faces away
from its owner**, so they cannot read a banner but can see the room's light level drop. The buzz
says something arrived; the dim confirms it with the screen pointed at a wall.

Two consequences. The dim must arrive as an **authored effect** and must be a step rather than a
fade — a ramp nobody authored is a signal nobody authored (rule 5). And **no notification may be
addressed to fewer than everyone**: a dimming lamp is world-observable, so a per-player banner is
a beacon pointing at whoever received it.

### D-077 · **Concealment screens dim their chrome too — decided**

The revoked screen said LAMP ALLOCATION WITHDRAWN while its status bar was the brightest thing on
the display. Revoked, handshake, disconnect and the outside-the-system screens now dim the whole
surface. The perimeter iris follows the panel's ink rather than being pinned to full intensity —
a small lit ring carries across a dark room when text does not.


### D-078 · **The status bar is present on every screen — decided**

Not most screens. Every one, including the two where the player has just been removed from the
round. It is how anyone confirms the perimeter is still armed and what the time is, and a device
that stopped saying so would be the app abandoning a player at the exact moment it took
everything else away.

**One variation, and it is not an absence.** The lantern and the scan fill the panel with amber,
so the shared bar — amber ink on black — would be invisible on them. Those two draw the same row
themselves, inverted.

**The design's fixture contradicts this on `revoked` and `restrained`**, which are pinned to all
four edges and paint solid black over a bar the fixture has already computed. That is why D-075's
carrier work — REVOKED against RESTRAINED, following the cause — would never have been visible on
the two screens whose entire subject is that distinction. The port does not inherit it, and a
test asserts no screen can end up without a status row.


---

## Revision 14 — the emit boundary, and what a green harness is worth

*Decided 2026-08-20 while building E0 stories 0.5, 0.6 and 0.6b. Everything here came out of
making the leak machinery real; none of it was visible from the design side.*

### D-079 · **The client taxonomy has two axes — role AND round-state — decided**

The emit allowlist is keyed on `ClientClass = Role x RoundState`, eight classes. Not on role.

**Keyed on role alone it would ship the two things being out is made of.** A player outside the
system sees the real progress bars and true occupancy, *which no living player of either role may
see* (gdd.md:1014). Two rows prove it and both already existed as decisions:

- **The live SystemIntegrity decrement.** The count is shown to living players only at meetings,
  batched and then frozen (gdd.md:192, :1002). A continuous decrement is a rate signal nobody
  living is entitled to.
- **The vote attribution list** (D-075). The living see counts; only a player outside the system
  sees who cast what.

Keyed on role, the entry serving an out Resident serves a living Resident. **This was verified by
building the wrong version:** an allowlist keyed on role alone was injected, and it granted the
live progress count to `Resident/PreArm`, `Resident/Live` and `Resident/Ended` alongside the two
out classes.

**The pre-arm classes appear in no row at all.** D-067 — nothing in-game runs in the lobby.

**A consequence, accepted: one kind, one payload, one permission set.** A message shape is
permitted to a class in full or not at all. If two classes need different content that is two
message kinds, each with its own row and its own narrower client-facing type. Per-class narrowing
of a single kind is redaction by nulling fields under another name (rule 3).

**Living players therefore currently receive no progress and no vote outcome at all**, because the
batched meeting-time messages do not exist yet. That is the fail-closed direction and the correct
one to be wrong in.

### D-080 · **The gate refuses with a distinct return type, and the refusal is recorded — decided**

D-066 settled *that* pre-arm events are refused above the rules. This settles **where the refusal
lives**, which was left open and is the part with teeth.

`admit(state, event)` returns `Admitted(reduction)` or `Refused(reason)`. **A refusal carries no
reduction at all.** Returning `Reduction(state, emptyList())` would be rule 1's forbidden shape
wearing a different name — the absent effect is the signal — so refusing has to be a different
*kind* of answer, not a quieter one.

**The recording gains an `X` row type**, compared by replay alongside effects and state. Without
it a refused event and an event that never happened are the same recording, and the gate would
have fixed one disagreement by creating another. Recording format 3 -> 4.

**`reduce` never learns the flag exists**, which is what makes the branch *provably* outside every
client-visible path rather than reviewed as being outside one. A standing test asserts `reduce`
still processes a pre-arm event when called directly, so a migration back inside the rules fails
loudly.

**D-068's client-visible refusal is NOT built, and the reason is structural.** The emit boundary
addresses seats filtered to `state.seats`; pre-arm there are no seats, because the refused party
is a *connection* that has not become a seat. It needs the connection identity arriving with story
0.8's seat token. Building a placeholder would mean inventing that concept early.

### D-081 · **The Insider count is public, so the differential harness runs on the exchange form**

Not a new decision — a *consequence* nobody had traced. The lobby settings, Insider count among
them, are the host's to set and visible to everyone before the round (gdd.md:655, :875).

It matters because F-005's denominator is `7 x initial_residents`, which reads the Insider count.
**So swapping one player's role changes a balance value, and the two runs of the differential
harness are no longer the same round.** Every out player's progress line differs, at every seat,
and the harness reports it as unexplained divergence.

That divergence is a **world change, not a tell** — the count was public before anyone moved. But
the harness cannot know that, and it is right to report it.

**Resolution: the acceptance criterion moves to the count-preserving exchange form** — two seats
trade roles, so the count is identical in both runs and only *which* seats hold the role varies.
The invariant it now checks is the sharper one: **with the Insider count fixed, no effect depends
on which seats hold the role.**

**The one-seat toggle is kept, and its divergence is asserted as expected**, with the reason named
in the test. Deleting it would hide the coupling; leaving it unexplained would look like a
weakened harness. Anyone reading that test and suspecting it was loosened to make something pass
should read this entry first.

### D-082 · **F-005's denominator counts Residents. The other half is still open**

`event.seats.size * 7` counted Insiders into a bar only Residents can lower — 56 against 42
completable at 8 players. **Insiders have no assigned subroutines and no action an Insider takes
ever advances the meter** (gdd.md:382), so Residents could not reach zero and a win condition was
unreachable. Now `(seats - insiders) * 7`, the operand F-005's proposed resolution names.

**The 7 is still a placeholder and F-005 is still open.** Its other half — orphaned subroutines
from a revoked player or a collapsed chain being silently auto-satisfied so the fixed denominator
stays winnable — is **not built**. The bar is reachable in arithmetic and not yet in play.

### D-083 · **Three leak surfaces means three instruments, and the blind spots are now measured**

project-context has always said a green build on one says nothing about the other two. That is now
a number rather than an argument.

A symmetric leak was injected — the live true progress count widened from the out classes to
everyone in the house:

| instrument | result |
|---|---|
| 0.6 differential harness | `tests="7" failures="0"` — **saw nothing** |
| 0.6b schema allowlist | `tests="11" failures="2"` |

Both of the differential harness's runs come out of the same redaction code, so the two
transcripts agree and the diff is clean. **0.6b is independently required, not a second opinion.**

**Two further blind spots, recorded now rather than discovered later:**

- **Leaks carried by the lamp are invisible to the differential harness.** A lamp message is
  addressed to one phone, so a per-role luminance difference shows up only at the swapped seat and
  is filed as expected. In the house it is a tell visible across a dark room. **The harness reads
  the wire; the lamp leaks through the air.**
- **A restrained player is still classified as living.** `RoundState.Out` means revoked *or*
  restrained, and nothing in `GameState` stores a restrained player — so such a seat keeps every
  living-class permission while `ui` already renders it the outside-the-system screen. Rule 9
  forbids borrowing the revoked list for it: one is system power lent by the house, the other a
  physical act it cannot prevent. **Closing it needs a second list and a second clause.**

### D-084 · **A guard is proven by injecting the bug it exists for, before it is trusted — reaffirmed**

Not new, but it earned its place three times in one day and the practice is now explicit: **write
the guard test first and watch it fail.**

- A pre/post classification bug in the per-client recorder was **not caught** by nine tests that
  all passed. The tenth, written specifically for it, caught it.
- A source edit during the review fixes **silently matched nothing**. The build stayed green. Only
  a test written beforehand caught it.
- A code review of the three finished stories returned **ten findings, all valid**, six of them
  provable — including a state row that had stopped rendering a field that decides what every
  client may receive.

**Confirm from test-result XML, never from a build's exit code**, and put the failure text in the
commit message.


---

## State after revision 12

**Name:** *Someone's Home* · **Roles:** Resident / Insider · **Verbs:** Revoke (Insider) / Restrain (group).
Architecture Steps 1–9 complete. Open: OI-4, OI-5, OI-6. **Gating: CLEARED — 1.7a/1.7b both pass; E0 proceeds on Kotlin Multiplatform.**
**Revision 13 added D-066 through D-078** — the marker system, the pre-arm gate, and the rule that
the house announces only what no player could have observed. The device design is ported to
Compose across 55 screens; 4 of 55 differ by role, and all four are intended.

**Revision 14 added D-079 through D-084** — the emit boundary. The client taxonomy has two axes,
the admission gate's refusal is a distinct return type and is recorded, and the differential
harness now runs on the count-preserving exchange form because the SystemIntegrity denominator
reads the Insider count. **E0 stories 0.5, 0.6 and 0.6b are built, injection-verified and pushed;
0.6c remains, and it needs hardware.** Two leak surfaces of three are instrumented.

**Carried into E0 as a constraint, not a closed item:** total app allocation ≤ ~0.5 MB/s as the design target, with the measured cliff between 1.5 and 3.0 MB/s — roughly 6× margin, so this is a budget rather than a knife edge. Nobody yet knows what the real app allocates with BLE, 100 Hz motion, effects and recording running at once.
Action: create `project-context.md`.
