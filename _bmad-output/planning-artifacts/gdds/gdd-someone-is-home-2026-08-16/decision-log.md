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

## Revision 15 — the map, the app, and the first phone

*Decided 2026-08-20, continuing the same day. Everything from D-085 on came out of building
story 0.7 and then putting the ported screens on real hardware for the first time.*

### D-085 · **The 44-shape marker roster lives in `model`, not `ui` — decided**

A shape is a marker's **identity**, not its decoration. The id is what a printed card encodes and
what a scan decodes months later, so the roster is wire data that `ui` happens to draw.

It had to move before map persistence could name a marker at all, and the alternative was a
second roster beside the first — which is exactly the failure D-070 was written about. Two things
decoding to the same marker put a player in the wrong room, and that wrong count lands inside the
injected error the Terminal already carries on purpose.

`MarkerShapes.require(id)` joins `get`. Returning null is right for a *scan* — an unregistered
card is a fact about a piece of paper (D-071) — and wrong where the id is a constant we wrote:
`listOfNotNull` turns a typo into a list one item shorter, and nobody counts a list they did not
write.

### D-086 · **The house map is keyed on the card id. A second card carrying a registered shape is refused — RAISED, NOT SETTLED**

Keyed on the id, per D-069's whole argument: the replacement for a mislaid card is a different
card, so the original found later behind a shelf is simply unregistered rather than reporting a
player into the replacement's room.

**The refusal is a judgement call and is flagged as one.** The shape is the marker's whole name —
`MARKER 07` is gone from every screen — so two registered cards showing one shape give a player
told to go to the diamond two places to stand. Refusing is fail-closed: the host sees it during
the setup walk while a card can still be reprinted.

The alternative — allow it, let the id disambiguate internally — keeps the data honest and pushes
an ambiguity onto people who cannot see ids. **Someone should decide this properly.**

### D-087 · **Map persistence is platform backup plus export, and the stored form IS the export form**

game-architecture.md:419 already decided the mechanism; this records what it actually means.

The map is written where the OS includes it in device backup. **A phone restored from backup keeps
its houses; a bare reinstall on a wiped device with no restore does not, and nothing in the app
can change that.** That is what the export path is for, which is why the stored form being
human-transferable text is load-bearing rather than a convenience — handing a house to another
host is handing them the file.

**A failed save throws.** The first version discarded the write's result, so on a fresh install
every save did nothing and every load came back null — fifteen minutes of walking a dark house,
gone, with no sign until the next evening. A Boolean return would have been dropped the same way.
This is host-side setup rather than a live round, so rule 6 does not apply: a host who cannot save
their house needs telling while the cards are still in their hand.

### D-088 · **Resuming a round replays it before trusting it, and rebuilds from EMPTY — decided**

Story 0.10. A recording is written by a process that then crashed, so *"it parsed"* is a much
weaker claim than *"the rules, run again, produce exactly it"*. Only the second licenses carrying
on. **Failing is the safe direction**: a round that comes back subtly wrong is worse than one that
does not come back, because eight people are standing in a dark house and a resumed round that
disagrees with the one they played hands them a game with no appealable history.

**The state rows are expected OUTPUT, not input.** Rebuilding authority state by parsing one would
create a second way to construct a `GameState` — one that bypasses the rules entirely and could
mint a round no sequence of events could produce.

Recordings also became *readable*: `toText()` shipped with stories 0.3 and 0.4 and nothing could
read it back. A recording that cannot cross a process boundary is not a debugging instrument, it
is a string a live process prints about itself — and the live process is precisely the one that is
gone when eight phones have gone wrong.

### D-089 · **Fixtures are named by predicate, and a partial set is fatal — decided**

Story 0.10d. A moment is *"the first time anyone was revoked"*, not a tick number: a tick number is
a fact about one recording, the predicate is a fact about the game and survives re-recording and a
rules change.

A mark that matches nothing **throws**. A fixture set that quietly comes back short is this
project's recurring failure in a new costume, and a test asserting against a dropped fixture passes
for the one reason that means nothing.

### D-090 · **There is an app root, and what the Simulator may and may not certify — decided**

`:app` is the one module that sees both `:ui` and `:platform`, and stays close to empty: logic
there is logic no boundary check covers. The Swift side is a window and a root view controller.

**The Simulator may certify layout and nothing else.** It has no BLE, no torch, no camera and no
haptics, which is every input this game has. That is the same licence `ui`'s desktop target already
holds, and it does not extend one inch further — the day the shell grows a lamp it stops being
verifiable anywhere but on plural physical devices in a dark room.

### D-091 · **The status row is three zones and the middle is empty on every device — decided**

Not "empty where there is a cutout". **Compose reports that a cutout exists and nothing whatever
about how wide it is**, so any rule keyed on the pill is a guess at Apple's geometry that goes
quietly wrong on the next handset. The middle is simply never used, and the layout owes nothing to
hardware it cannot measure.

The carrier leaves the middle and lives in the left zone. **The zones are deliberately lopsided**:
the right holds a clock and a battery and wants about 16% of the panel, the left holds two glyphs
*and a word*. Splitting them evenly truncated `UNREGISTERED`; widening the left until it fit pushed
its last character *under* the pill, which is the worse of the two failures.

Measured: the carrier has about 60 design units before the pill and `UNREGISTERED` wants 60.3. It
does not fit and no tuning makes it fit. So it ellipsizes — the right string to lose, being the
fallback for a *missing* cause chosen because it "asserts only that you are out", which
`UNREGISTER…` still does. **REVOKED and RESTRAINED fit whole**, which is what D-078 exists to
protect.

### D-092 · **The device's insets are a value, so a notch can be faked — decided**

`LocalPanelInsets` replaces a direct `WindowInsets` read inside the chrome. The reason is not
layering hygiene: **a notch cannot be faked through a platform read**, so without this the screens
could only ever be checked by holding a phone.

With it, every screen renders against simulated insets in `./gradlew check` and fails if anything
lands under the cutout, under the home indicator, or in the deep corners. **With the insets removed
the guard reports 576 faults across all 56 screens** — that was the state of the app before the
first phone, and hand-inspection had found five of them.

The property it tests is **uniformity**, not "differs from the background". Content is variation; a
flat fill is not, whatever colour it is — and a flat fill under the pill is *required* on the lamp
screens, where the amber must reach the edges or the core has emitted light the hardware swallows.

**What it cannot judge:** whether the amber reads right, whether the pixel fonts land on whole
pixels at 3x, whether a screen is beautiful. It checks that nothing is hidden. That is all.

### D-093 · **No pulsating outline around the Dynamic Island — decided against**

Proposed as a way to use the Island band: a ring around the pill, steady while you are in the
round, pulsing red once you are out. Declined on three counts, recorded because it is a natural
idea that will occur again.

**A pulse is a local animation the core never emitted** — rule 5, and D-076's *"a step rather than
a fade"*.

**A phone held as a lamp faces away from its owner** (D-076's own argument), so the ring would tell
the room rather than the player. Worse, a bright ring appearing at a revocation lights up the exact
spot the revoke happened, against the one property the same-frame blackout exists to protect.

**Brighter-when-out inverts D-077**, which dimmed those screens precisely because the status bar
was the brightest thing on a display whose subject is having everything taken away. Red is also the
only hue outside a closed five-step amber ramp.

**The version that would survive**, if an Island indicator is still wanted: a *static* ring in the
panel's own ink, following the perimeter iris's rule — dimmer when out, changing only when the core
emits an effect.



---

## Revision 16 — the first two phones

*Decided 2026-08-20, the same evening, continuing. Story 0.8's transport went from loopback to
two physical phones — an iPhone 16 Pro hosting, an iPhone 13 Pro joining over the house Wi-Fi —
and the whole story held: seated as a stranger, frames attributed, killed mid-connection by
`devicectl`, relaunched with wiped memory, and resumed as THAT seat. The locked round refused a
stranger terminally, with a frame. Everything below is what the hardware taught that the
loopback could not.*

### D-094 · **Resume needs WHERE as well as WHO — decided**

The seat token answers *who this phone is*. Nothing answered *where the house is*. The killed
and relaunched client held its token and dutifully presented it to `127.0.0.1`, because the
host's address had only ever lived in memory — the phone knew exactly who it was and had no idea
where to say so.

**mDNS discovery is the real answer**: a resuming phone should find the host the same way a
joining phone will, and typing an IP in a dark house was never the design. Until it exists, the
last known host address is stored beside the token (`HostAddressStore`, same doctrine: text in,
text out, atomic, failed save throws) and the resume path reads both. This is an interim the
mDNS story deletes, and it is recorded so the deletion is recognised as one.

### D-095 · **Deployment target 26 → 18 — decided**

The second test device is an iPhone 13 Pro on iOS 18.6, and the target was 26 only because the
repo was created against the current SDK — nothing in the app touches an iOS-26-only API.
Lowered to 18 in all three configurations; project-context's stack line updated in place. The
day something genuinely needs a newer API, raising the floor is a decision to take knowingly,
not a default to inherit.

### D-096 · **The seventh silent instrument, and what caught it — reaffirmed with a new instrument**

The cheat's token-save call did not exist: the edit that was meant to add it silently matched
nothing, the import compiled unused, and the build stayed green — D-084's exact failure shape,
one revision later, performed by the same discipline that documented it. The relaunched 13 Pro
came back a stranger.

What caught it was **pulling the app's data container off the phone** (`devicectl device copy
from`) and finding no token file where one was claimed to be: the device's filesystem consulted
as the instrument, not the build output and not the log. The practice generalises and is now
named: **when a persistence claim fails on hardware, read the container, not the code.** The
first pull also mis-derived the container's layout and reported an absence one directory too
deep — the second look, listing the whole tree, is what made the evidence honest.


---

## Revision 17 — the refusal nobody sees

*Decided 2026-08-20, late the same evening, in conversation with Vadmanuel. D-068 left one half
unbuilt — what the phone that sent a refused event actually experiences — and D-080 recorded why
it could not be built then: the refused party was a connection, not a seat, and nothing could
address it. Story 0.8's seat tokens removed that excuse, so the question came due.*

### D-097 · **A refused event is answered with re-assertion, not explanation — decided**

The gate still refuses and still records (D-066 and D-080 unchanged — replay needs the X rows).
What changes is the client's side of it: **there is no refusal message.** The house answers a
refused event by re-pushing that client's currently-authorized view through the emit boundary,
addressed and permitted like any other delivery. The stale phone snaps to the screen it should
have been on. The refusal is invisible; the correction is ordinary.

**This deletes D-068's standing hazard instead of guarding it.** D-068 allowed a reason to be
sent only when the reason was publicly observable, and warned that the identical code reporting a
mid-round refusal — *target already revoked, cooldown running* — would be an alignment leak
written by someone tidying up error handling. Under D-097 that channel never exists: misuse of
"re-send the authorized view" sends the authorized view, which is safe **by construction**
because it passes the same allowlist as everything else. The forbidden sentence has no message
kind that could carry it.

**One mechanism, two triggers.** A phone that reconnects after a crash needs exactly this — its
current authorized view, plus the pending flip the ack protocol owes it (D5: proceed without the
missing client; it flips on reconnect). Reseated and refused-something-stale converge on the same
redrive.

**Why the client needs nothing new:** clients hold no request/response posture. Intents are
fire-and-forget — the only optimism the design permits is input echo, reflecting your own touch —
and the phone renders whatever the house pushes, whenever it arrives. A re-assertion is
indistinguishable from ordinary delivery, and that indistinguishability *is* the refusal staying
invisible. Outcome prediction and optimistic rollback stay forbidden for the recorded reasons:
prediction requires client-side knowledge (the leak surface `ui ↛ core` exists to delete), and a
rollback is retracted light in a dark room — a broadcast that something was refused.

**Deliberately NOT decided here: the payload.** What "the authorized view" concretely contains is
loop work and the loop is under reconsideration. Two constraints are recorded for whoever builds
it: it must be a **snapshot, safe to re-receive redundantly** (the same requirement resume
imposes), and it enters the schema like any kind — a row somebody decides, or it ships to nobody.
Transport-level refusals (`Refused` frames to connections that hold no seat) are unaffected;
there is no view to re-assert for a stranger, and D-068's public-reason logic still governs them.


---

## Revision 18 — the flag comes off D-086

*Decided 2026-08-20, with Vadmanuel, continuing the same session.*

### D-086 · **A second card carrying a registered shape is refused — SETTLED, refusal ratified**

The judgement call is now a decision, and what decided it is the failure class of the
alternative. Allowing the duplicate keeps the *data* honest — the map is keyed on the id either
way, so that benefit was already had — but puts two identical symbols in a dark house, and the
shape is the marker's whole name. A player told to go to the diamond has two places to stand,
and the wrong-room reports that follow are **indistinguishable from the error the Terminal
injects on purpose**: a systematically wrong count hiding inside deliberately-wrong counts. No
player, host or instrument would ever see it. Silent and permanent — the definitional worst
failure this project has.

The refusal, by contrast, lands on the *host*, in the *light*, during setup, with the fix in
hand: forget the stale registration or take one of the other cards — the roster holds 44 shapes
and a house uses a dozen. One moment of setup friction against an undetectable mid-round wrong,
and the friction wins. `RegisterResult.ShapeAlreadyRegistered` stays; its KDoc's
raised-not-settled flag comes off.


---

## Revision 19 — the meter total arrives

*Decided 2026-08-20, with Vadmanuel, same session. F-005's contradiction — flagged three times —
is closed; its genuinely open halves are named and stay open.*

### F-005 (partial) · **`METER_SEGMENTS` stops being a game value — decided**

`ui`'s 32 was an artifact of an earlier player count sitting in a module forbidden from knowing
game values, contradicting the authority's `(seats − insiders) × 7`. The resolution is the
architecture's own: **the meter total is display data that arrives.** `PanelState` carries
`meterSegments`, sent by the authority and frozen at arming exactly like every balance value;
the constant survives only as the fixture default for the ported screens, documented as the
artifact it is, and nothing rendering a live round may read it. A test renders a round whose
total the design never drew and reads it back off the screen — injection-verified: hardcoding
one readout back to the constant fails it.

**Still open, honestly:** the `7` (subroutines per Resident — a balance value playtesting
decides, locked at arming regardless), and the orphaned-subroutine auto-satisfaction that keeps
a frozen denominator winnable after a revocation — loop work, untouched while the loop is
reconsidered. The denominator's *source* is settled; its *number* and its *upkeep rule* are not.


---

## Revision 20 — what the design chat knew

*Decided 2026-08-20, with Vadmanuel, after mining the full 193k-token conversation behind the
claude.ai/design project the screens were ported from. The port tracked the design faithfully;
what follows is the context that existed only in that chat, now ruled on.*

### D-098 · **"Passage" is deleted, everywhere — ratified**

The design removed the passage room type deliberately: the Insider's route between rooms is
Override, and Override must never be drawable on a map. The map knows room and stairs, nothing
else. Ratified into the repo: CLAUDE.md's vocabulary no longer lists Passage, and the stale
identifiers (`ScreenId.PassageWarn`, `confirmPassage`) are renamed for what the screen actually
is — the stairs type-change warning.

### D-099 · **Stairs hold nothing, structurally — decided**

The rule existed in the design's flows and nowhere below them. Now `Room` carries a
[RoomKind], `HouseMap.register` refuses a card offered to stairs with its own distinct result,
and a `Registration` into stairs **cannot be constructed** — the `require` guards every other
path, `HouseMap.of()` included. The stairwell's invisibility to the Terminal is the game's
natural hiding place, and it holds by construction. The storage format is unchanged: a file of
registrations can never contain stairs, because a registration into stairs cannot exist.
Injection-verified: with both guards removed, `tests="3" failures="2"`.

### D-100 · **House is the AI's word; home is the residents' — ratified into the vocabulary**

"Map a new home", never "map a new house". The ported copy already obeyed this; now the
vocabulary says so.

### D-101 · **v1 blackmail is a verbatim template; the LLM is v2 — decided**

The design chat's intent — feed the player's one line through an LLM to write the house's
blackmail text — is deferred to v2. In v1 the house says, in substance, *"I know you said
'<the line>'."* That keeps the lobby's promise exactly as written: the line is seen by the
house only (the host device), deleted when the round ends, and never sent anywhere that could
generate from it.

### D-102 · **The haptic doctrine — ratified from the design chat**

A screen that arrives unasked buzzes; a screen you tapped into does not (a buzz there trains
people to ignore the ones that matter). **The buzz is identical for every player — same
pattern, same duration, including when an Insider's own Revoke lands — or it is an audible tell
in a silent house.** The design's canonical buzzing set is twelve screens; perimeter-armed is
the one that matters most (the round begins in a room about to go dark, and the haptic is the
only cue that is not light). The restrained player's takeover appears at the **halfway mark of
the vote results** so they do not walk away when the countdown ends. The host's registration
scan buzzes, because the phone is against a card with the screen angled away.

### Raised, not settled — carried to discussion

The Insider-count lobby setting (host-set, default UNKNOWN meaning the house draws a balanced
random count, with an enforced minimum) and its interaction with D-081's "the count is public"
premise and revision 19's meter total; the ghost check-in gate; the swipe-dismiss semantics;
the light-signature visibility. Player count is "virtually unlimited" as design intent against
engineering notes sized for 8. The Guest-to-Host study is parked until the game exists.


---

## Revision 21 — the count that hides, and the meter that stopped counting

*Decided 2026-08-20, with Vadmanuel, closing the four questions revision 20 left open.*

### D-103 · **The Insider count can be hidden, because the meter speaks only percent — decided**

The lobby's Insider-count setting is the host's, and its default is **UNKNOWN**: the house draws
the count at arming, locks it, and tells no one until the round ends.

**The band is a balance envelope, and both edges are load-bearing.** The minimum protects the
Insider side — one Insider against fifteen Residents is unwinnable for the one — and the maximum
protects the Resident side, who must still be able to complete `(seats − insiders) × 7` and who
are guaranteed a living population of at least two by the parity rule (F-016). **Both edges clamp
the setting itself, not just the draw**: a host cannot hand-pick a count outside the band any
more than UNKNOWN can land on one. Placeholder edges `max(1, ⌊seats/6⌋)` to `⌈seats/4⌉` — 6
players → 1–2, 8 → 1–2, 12 → 2–3, 16 → 2–4 — and playtest owns the numbers, as it owns the 7.
Player count itself is virtually unlimited by design intent; the engineering posture is
*designed for 6–10, no hard cap built*.

What makes hiding affordable is a display rule that is **unconditional, not a consequence of
UNKNOWN: SystemIntegrity reaches a panel only as a percentage.** The denominator is
`(seats − insiders) × 7`, and any screen printing it — `28/42`, or a bar whose segments can be
counted across a room — hands every reader the Insider count by division. So the living see a
batched percentage at meetings (updates only there, so successive readings do not factor), the
out see a **live** percentage — their privilege is liveness, never the denominator — and the bar
is fixed display resolution (32 cells, a fact about pixels).

**Supersedes in part:** revision 19's surface (the meter total no longer arrives at all;
`METER_SEGMENTS` returns to a pure display constant; the guard becomes `MeterDisclosureTest`,
which reads the panels and fails on any slash-total — injection-verified, `tests="2"
failures="1"` with `28/32` reintroduced) and D-081's premise that the count is public (the
settings row may read UNKNOWN; the harness's count-preserving exchange form is unchanged and
still the acceptance criterion).

### D-104 · **Living AND ghosts gate the discussion clock — decided**

The talk does not start until every living player *and* every out player has checked in at the
meeting area. The accepted risk, named: a ghost whose phone died stalls the gate until 0.8's
reconnect brings them back or the room resolves it socially. Vadmanuel chose presence over
proceed-without here — a meeting that starts while someone is still walking is a worse game
than a meeting that waits.

### D-105 · **There is no "read" — decided**

The concept is deleted. Everything that pops — egress alert, text banner, any banner — is a
*notification*: swipe up dismisses it, and that is the whole gesture vocabulary. Persistence
already lives where it matters (Messages holds every text; the egress widget holds the
countdown), and **house notices** is the one dismissable surface that never persists — the
popup from a phantom app. No unread badges, no NEW tags; the backlog header counts messages.

### D-106 · **The light signature shows everywhere — decided**

How much light a subroutine makes a phone emit is knowledge a player holds *in advance*: on the
work-order list, on the springboard widget, and on the subroutine screen itself. Residents may
plan a dark route. The presentation (the widget line was once removed for how it read, not what
it said) is design-project work, not settled here.

---

## Revision 22 — the room the app is never told about

*Decided 2026-08-21, with Vadmanuel, resolving escalation E2-1 (unit 2, overnight 2026-08-21).*

### D-107 · **Insider-only rooms are a spoken fact, never app data — decided**

D-098 deleted the third map type; what it did not settle was story 4.7, whose editor exclusion
was written entirely in terms of that type and carried a real hazard — *a Terminal behind an
Insider-only door is unrecoverable*. The ruling is that **the map type went and the fact stayed,
but the fact was never the app's to hold.** The host declares Insider-only rooms aloud during the
setup walkthrough, with every player present — *"this bathroom is Insider-only"*, *"here is the
terminal"* — and the app never learns which rooms they are. A room the editor could mark is a
room a Resident's phone could read; keeping it out of the data is a leak surface that never
exists rather than one that is guarded.

**Doors are physical house rules, and the app enforces none of them.** Residents may open the
door of a playable room and may never close one. Insiders may close playable-room doors, and are
the only ones who may open *or* close the door of a declared Insider-only room — that is the
passive Override, unchanged and still untracked.

**The hazard dissolves rather than moving.** The Terminal's room is playable by definition, so a
closed door can inconvenience the Residents but can never put the Terminal out of reach: *"a
Terminal behind an Insider-only door"* is not a state the game can reach. Story 4.7 therefore
stops being an editor exclusion and becomes walkthrough copy — **guide the host, don't gate
them** — and E4 is left with exactly one exclusion the editor enforces, the stairs one, which is
a safety rule.

### D-108 · **The planning artifacts are swept to match D-098 — ratified**

Five sites still described the deleted type as live, one of them defining it as *mandatory*
vocabulary in the document CLAUDE.md points at for architecture — an agent reading it in good
faith would have written the identifier the lint rejects. All are now purged: the grid painter
paragraph and the marker taxonomy in `gdd.md`, stories 4.2 and 4.7 in `epics.md`, and the
architecture's vocabulary list. The sweep found five more of the same word in the same two
documents (the ability table, the doors section, the T−15:00 walkthrough, the E4 scope row,
story 9.13) and took those too — half a document describing a deleted type as live is the same
contradiction, just further down the page. The decision log keeps its own occurrences, because
a record of a deletion has to be able to name what was deleted.

---

## Revision 23 — what the house grades, and what it never hears

*Decided 2026-08-21, with Vadmanuel, ruling on the overnight run's Subroutine escalations (E10-1,
E10-2, E10-3) and closing a gap open since the §18.1 trace (F-014).*

### D-109 · **The house grades every entry for real, for both roles, in identical words — decided**

Closes E10-1. The client does get a verdict, it is a true one, and it is the same verdict machinery
for everybody: the house grades what was handed over on its merits and answers in the same words on
the same schedule regardless of who sent it. **No fake verdicts and no synthetic failure
distribution** — the tempting alternative, rolling an Insider's failures so they look plausible, is
a random number generator standing where the rule should be, and rule 1's answer is simpler: run
the real rule, emit the real shape.

**The only asymmetry is server-side and appears on no screen: an Insider's success never writes to
SystemIntegrity.** That is the whole of it — the fake is real work, graded honestly, counting for
nothing, exactly as `gdd.md:382` has always said, and the difference lives where no player can
stand.

Identical grading is the *safer* answer and not merely the cheaper one. An Insider whose fake
always visibly succeeded would be legible **to an observer standing behind them**, which is the one
reader the device-side guarantee (D-047) never covered. Success looks like success and failure
looks like failure for both roles, so a screen read over a shoulder in the dark tells a watcher
nothing but how well that player did.

**Register, ruled here because copy is a leak surface too.** Success is ominous, house-voiced, and
written as a damage report — the *"THE HOUSE GROWS WEAKER"* family. **Never "the house has it"**:
the house is not on the residents' side and must never be made to sound like it. Failure is
rejection plus an instruction to re-scan in place; candidate copy *"REJECTED · RESCAN THE MARKER"*.
The final wording is a build-time call; the register is not.

### D-110 · **One attempt per scan, and the walk back is the cost — decided**

Closes E10-3, and it ratifies what the unit built rather than reversing it. A handed-over entry is
spent. The Subroutine re-arms only when the player scans the marker again — the house never re-arms
it silently and the phone never re-arms it on a timer, because a screen that becomes ready again on
its own schedule is the phone forming an opinion about an answer.

**The forced re-scan is the design, not a consequence of it.** It costs time, and it keeps the
player standing at the marker where the house asked them to stand — stationary, lit or blind,
visible. That is pillar P4 being charged for rather than worked around.

**And it hands the Insider a decision in public.** An Insider who fails may simply not re-scan;
their success writes nothing either way, so a second attempt buys them nothing but cover. Whether
to pay for cover, on the spot, in the dark, with someone possibly watching, **is intended
social-read material** — the same channel E10-2 worried about, left open here deliberately because
it is a choice a player makes rather than a statistic the house publishes.

**Movement cancels, client-side — recorded as intent.** A player must remain at the marker while
performing, and walking away ends the minigame on their own phone. This needs proximity hardware
that does not exist yet, so it is recorded as design intent and future work rather than as
something the current screens do. It is a device-side rule about the player's own phone and adds no
report.

### D-111 · **The work plane hears nothing; the presence plane hears the window — decided, and amended within the sitting**

Closes E10-2. The two halves were ruled minutes apart, because the spectator map (D-136) forced the
second.

**The work plane is absolute: walking away means the entry is never sent.** STOP NOW, a step away,
somebody in the doorway — the house is told nothing, grades nothing, and holds no partial state
anywhere. There is no half-returned sequence to resume, so the next scan restarts, which is what
the port already does. **The house grades only what arrives**, and abandonment therefore cannot
become the behavioural channel E10-2 named: there is no abandonment count to leak because there is
no abandonment record.

**The presence plane is the amendment.** While a player is performing, their phone reports
*performing at room X*, and stopping ends the report. So the house does know a window opened
and closed — but never the half-finished answer inside it, and nothing at all about correctness.
The distinction is the point: **the house records, never recites.** This data feeds exactly one
consumer, the spectator map's expiry (D-136), and may never reach a notice, a count, or any surface
a living player can read. Anything else rebuilds the leak the work plane just closed.

### F-014 (closed) · **Carry state survives a meeting — decided**

`gdd.md:1022`'s gap, open since the §18.1 trace and raised again by E10-2 as the same shape of
question. It gets the opposite answer to abandonment, and consistently so: **carry state is a fact
about the world, not an unfinished answer.** An Array Wipe disk in hand and a Memory Dump pattern
in memory both persist through a meeting, and the Array Wipe carry flag keeps blocking every other
scan across it.

The two differ for the reason F-014 always mattered twice over: the carry flag is a *constraint the
player is visibly under*, legible in what they can and cannot do, and clearing it at a meeting
would hand everybody a free unlock for the act of walking into a room. A partial entry is nobody's
fact at all.


---

## Revision 24 — the light a Subroutine costs you

*Decided 2026-08-21, with Vadmanuel, resolving E9-1 and E9-2 and extending D-106.*

### D-112 · **The light signature is a fixed property of the Subroutine kind — decided (v1)**

E9-1's first question, answered the cheap way on purpose: **in v1 the light level is fixed per
Subroutine kind.** The client holds the roster, the house sends only *which* Subroutine, and **no
field is added to `PanelState`, no `Effect` gains a member, and the redaction schema gains no row.**
Per-assignment variety — the same Subroutine lit differently for different players — is explicitly
deferred to v2 rather than left ambiguous. It is the wire that costs something, and nothing in the
design needs that wire yet.

E9-1's second question — that *the set of signatures a player can see* is a statement about their
work order — is answered by D-114 rather than by hiding anything: the work order is a menu, and
choosing your own exposure is what the menu is for.

### D-113 · **Every Subroutine has a light level, structured ones included — Array Wipe is BRIGHT**

Closes E9-2 by ratifying the port's reading rather than overturning it. Array Wipe **is** bright:
the work *is* the scanning, three markers over minutes, on a full amber field, and the design
already calls it *"the heaviest map-data generator in the game … the easiest person in the house to
intercept"*. A dark Array Wipe would have made the circuit a concealment route, which is the
opposite of what it is for.

**The general rule matters more than the row:** the ten-Subroutine roster table is not the whole
set, and no Subroutine ships without a rated signature. Memory Dump is rated when it is built.

### D-114 · **A blocked Subroutine is a blocked entry, and the work order is a menu — decided**

Two rulings that only work together.

**Blocked work is visible as a blocked entry.** A Resident whose Subroutine is gated behind another
sees that *something is there* and cannot see what it is — not its name, not its signature — until
it unblocks. A known unknown: not absent, which would shorten the order and make its length a tell,
and not spelled out, which would hand the player a route they have not earned yet.

**The work order is a menu, not a queue.** The player chooses among whatever is currently
actionable, and sequencing emerges from the dependency graph rather than from an imposed order.
That is what makes D-106's always-visible signature a **decision surface**: with two actionable
Subroutines and one of them bright, the player is choosing how visible to be for the next ninety
seconds, in a house where being seen is the entire risk.


---

## Revision 25 — names leave the phone, and the ballot that cannot be taken back

*Decided 2026-08-21, with Vadmanuel, resolving E6-1 and E8-1.*

### D-115 · **Names leave the phone — decided**

Closes E6-1, and reverses the overnight charter's working assumption rather than the unit's
judgement. The host learns names, and clients receive them wherever the interaction needs them.

**The lobby shows names.** The design's lobby always did; the counts-only lobby built overnight
(`LobbyBody.Standing` as three integers) was the charter's caution, correctly taken while this was
undecided, and it now has to widen. **The widening is deliberate and must arrive in a diff that
says so** — that is exactly the property the two-type split was built for (D-048, D-049), and it is
the whole difference between a disclosure and an accident.

**Clients receive names where the interaction needs them**, the vote ballot being the obvious second
site: a house that cannot say who is being Restrained cannot hold a meeting, and the app is
everybody's only light. Names are **round-scoped**, like everything else the desk holds — they
arrive for the round and they are gone when it ends.

What this does *not* open is occupancy. Knowing who is in the game is not knowing who is in a room;
the latter is what the meeting is for, and nothing here touches it.

### D-116 · **Every blackmail line is real; the house only ever uses the Insiders' — corrected**

**Supersedes `gdd.md:1421`'s "every player has a blackmail file and the Resident's is a fake."**
That framing puts the asymmetry in the wrong place. The correction: every player hands over a real
one-line in the lobby, all of them real, and the house simply never uses a Resident's.

**The cover is the typing, not the content.** Six people bent over their phones writing a true line
is six identical acts, and **nobody can be identified as an Insider by having written one** — which
is what the original sentence was reaching for and could not get, because a fake line is a thing
that has to be *generated for somebody*, and generation is where asymmetries hide (rule 1 again,
and D-101's reason for keeping v1's blackmail a verbatim template).

### D-117 · **Live selections, an irrevocable READY, and auto-lock at the buzzer — decided**

Closes E8-1: half of the unit's provisional build is ratified and half is reversed.

**Every selection tap transmits live.** The stream is not an optimisation — it exists so ghosts can
watch the vote happen (D-134), which is most of what makes the out's meeting screen worth looking
at.

**The button is a readiness signal, and it is irrevocable.** It converts the current selection into
the actual vote, and **after it nothing can be changed** — which supersedes *"changeable until the
clock ends"* at `gdd.md:412` and `:1006`. Rename candidate READY, replacing LOCK IN, for symmetry
with the discussion's READY TO VOTE; final copy is a build-time call.

**Three consequences, all ruled:**

- **Auto-lock at the buzzer.** Whatever is selected when the clock ends locks itself. This
  **narrows D-075**: skip-by-silence now applies only to a player who selected nothing at all for
  the whole window, rather than to anyone who did not press a button.
- **Unanimous readiness closes the window early**, symmetric with the discussion's unanimous READY
  TO VOTE — E8-1's second question, answered by the first: if the button is readiness, it behaves
  like the other readiness.
- **`N OF 6 VOTED` counts locked players, not selections** — E8-1's first question. The living see
  the count and never the selections; the ghosts see the live selections and are the only readers
  who do.

The vote window's default is **45 seconds**, host-changeable in lobby settings — already landed in
code (`fcfefd6`).


---

## Revision 26 — two banners dim the house, and nothing else does

*Decided 2026-08-21, with Vadmanuel, resolving E7-1 and correcting the overnight run's reading of
D-105.*

### D-118 · **Exactly two events dim the house — decided**

E7-1 asked who owns the undim and got a larger answer: **the dim is not a notification style, it is
a two-member vocabulary.** Only the Egress and the house's opening text message arrive with the
bright banner and the screen dim. **Every other notification is quiet** — no dim, no brightness
spike, nothing world-observable at all.

This dissolves E7-1's staggered-undim worry instead of arbitrating it. Six lamps coming back up at
six different moments is only a problem if six lamps went down, and reserving the dim for two
events the whole house already knows about — an Egress is audible, and a dim during the opening
message is the round starting — keeps the light vocabulary **as closed as the word vocabulary**. A
light change that means one specific thing is a signal; a light change that happens twenty times a
round is noise, paid for out of the readability the whole game is built on.

**Lock-screen presentation:** the notification lands under the large clock, which is the phone's own
idiom, and for the two heavy events the rest of the screen dims around it.

### D-119 · **The swipe is the acknowledgment, and quiet notifications persist — decided**

The other half of E7-1, and the correction of D-105's overnight reading.

**Heavy banners are dismissable and self-clearing:** swipe up on the in-game banner, swipe **left**
in the lock-screen presentation, and they auto-dismiss after ten seconds regardless. Undim happens
on personal dismissal or at the ten-second expiry, whichever comes first.

**Quiet banners never auto-dismiss.** They sit until swiped, and **the swipe is the
acknowledgment** — D-105 deleted read state, so the gesture is the only evidence that a player has
seen a thing at all, and a quiet banner that cleared itself would leave nothing behind.

**Dim and undim are steps, never fades — both edges, ruled.** Rule 5 stated for the edge nobody had
stated it for: *a ramp nobody authored is a signal nobody authored*, and that is as true coming back
up as going down.

**Storage, which the overnight run read backwards:** quiet notifications **are** stored. The
canonical example is *"your Subroutine has been unblocked"* — a fact about the world that a player
walking back from a marker has to be able to find again. The only ephemeral notification-like
things are **house notices during a meeting**; the Egress persists on the Egress widget and never as
a stored notification. And **no read/unread indicator anywhere, ever** — D-105 stands, the backlog
header counts messages and nothing tags them.


---

## Revision 27 — the circuit moves every round

*Decided 2026-08-21, with Vadmanuel, ratifying E5-1 and then extending it well past what E5-1
asked.*

### D-120 · **The T shape is the terminal; the Array Wipe stations reserve nothing — ratified**

Closes E5-1. `t_shape` is the reservation, exactly as built: the shape is the only field a scan can
read as a *kind* of card, the letter T is what is printed on the paper, and paper cannot be patched.

**The second half of E5-1 is answered no.** Spares, Rack and Disposal get **no reserved shapes** —
they are ordinary registered markers, which is what D-122 makes them.

### D-121 · **The meeting card is the second reserved shape, and a meeting is called by standing at it**

A new reservation, ruled the same sitting. **Meetings are called only by physically scanning the
meeting card. No remote calls, ever.** The one exception is reporting a Revoked player, which works
from anywhere — F-011's unconditional claim button, unchanged.

The rest follows from the card being a place. **The caller's scan is their check-in**, and players
arriving afterwards tap the existing I'M HERE control, so D-104's gate needs nothing new. The
meeting card lives at the meeting area, and ordinary markers may share its room — it reserves a
shape, not a room.

**The reserved set is now two, T and meeting, and the registrable roster is 42.** The roster still
holds all 44 entries, because it is wire data and ids are never renumbered (D-070, D-085): two
shapes are spoken for, none was removed.

Why physical: a remote call is a button that summons the whole house from a chair. Making it a walk
to a known place puts the caller somewhere everyone can see them, at the moment they would most
like to be unseen — the difference between calling a meeting and *being the person who called it*.

### D-122 · **The house draws the stations at arming, every round — decided**

The Array Wipe circuit's three stations — Spares, Rack, Disposal — are drawn **randomly by the
house, per round, at arming, from the ordinary registered markers.** The host designates nothing.

**Station assignment is round state and is never stored with the home**, which is the load-bearing
half. A home that remembered where the Rack was would turn the circuit into a fact players learn
once and keep, and by the second night in the same house there would be no circuit left to run.
**The circuit moves every round.**

### D-123 · **Markers are capacity, not workload; a card is a place, not a container — decided**

Two statements that between them settle how many markers a home needs and what happens when it has
few.

**Capacity.** A registered marker is a *slot the house may use*, not work that must be done. At
arming the house draws the round's active set — stations plus targets, sized to seats and balance —
and **unused markers sit dark**. The active set is re-drawn each round, like the stations.

**A card is a place, not a container.** The payload never changes and carries nothing about work
(D-069); the house resolves `(seat, card)` to *that player's current Subroutine*. So one card can be
a station and two players' anchors at the same time, and **same-player reuse is lawful**: a work
order deeper than the home's marker count simply visits some markers twice, and where that produces
a self-dependency the player meets it as **blocked-by-your-own-work**, discovered as they complete
rather than announced in advance — D-114's blocked entry doing exactly its job.

**Congestion is content.** Two people at one card in the dark is not a scheduling failure; it is the
game putting two people in a room and leaving them to decide what they saw.

### D-124 · **NOTHING FOR YOU HERE — decided**

Scan refusal gets two distinct vocabularies, because it is answering two distinct facts. A
**registered** card the house has nothing for *you* at says **NOTHING FOR YOU HERE** — true,
non-committal, and silent about whether the card is somebody else's anchor. The **unregistered**
alert is reserved for genuinely unregistered paper, where the honest answer is that this card is
not part of the home at all.

Collapsing the two would build a detector: a Resident sweeping cards could separate *registered but
not mine* from *not registered at all* only if the two sound different — and they must sound
different, because the host needs the unregistered case to be loud during setup and the player needs
the other case to be unremarkable during a round.


---

## Revision 28 — guide, don't gate

*Decided 2026-08-21, with Vadmanuel. Doctrine first, then the one gate it leaves standing —
generalising D-107's "guide the host, don't gate them" from a story-level ruling to a project one.*

### D-125 · **Guide, don't gate — and clamp only what players cannot perceive**

**The app never has an opinion about what a home *is*.** It has opinions only about what a round
mechanically requires. A one-room home is lawful. So is a strange footprint, a tiny one, or an
eccentric idea of what counts as a room. The host knows their home; the app does not.

**The corollary is the sorting rule, and it is the general form of D-103's clamp:** *clamp what
players cannot perceive; guide what they can.* The Insider band is clamped — hard, on the setting as
well as on the draw — because a host cannot see the balance consequence and no player can ever check
it. Home size and seat counts are guided and never blocked, because everybody in the hall can see
how many people are standing there and how big the house is. **A clamp on a visible fact is
condescension; a guide on an invisible one is negligence.**

### D-126 · **Fail early, in the light — every gate lives at REVIEW time**

Every check the app does perform happens **while the host is alone, mapping their home, with time
and light** — at REVIEW, never at hosting time with the party standing in the hall. A refusal at
hosting time lands at the worst possible moment on the one person who cannot walk away, and it is
the same argument D-086 settled for the duplicate shape: **put the friction on the host, in the
light, with the fix in hand.**

### D-127 · **The REVIEW HOME gate, and HOSTS UP TO N — decided**

The one gate, in full: a home passes REVIEW with **one terminal, one meeting card, and at least
eight ordinary markers.** The eight is arithmetic rather than taste — the minimum party is five
(D-128) and the Array Wipe circuit takes three stations (D-122), so eight is the smallest home in
which the smallest lawful round can be armed.

**Capacity is guidance and never a gate.** REVIEW computes and shows **HOSTS UP TO N**, where `N =
markers − 3` (the three stations again), and the lobby shows a guidance line when the joined count
exceeds it. **It never blocks** — D-123 is what makes that affordable, since same-card reuse absorbs
the shortage. The honest thing to tell a host with nine markers and ten players is that it will be
crowded, not that it is forbidden.


---

## Revision 29 — the shape of a round

*Decided 2026-08-21, with Vadmanuel. Party size, workload, and the conditions the round ends on —
the first time the win conditions have been written down as a set. Amends D-103.*

### D-103 (amended) · **At five and six seats there is exactly one Insider**

The band's lower reach is replaced by a fixed value. **Seats 5–6 → exactly 1 Insider. From 7 seats
the band resumes**, `1..⌈seats/4⌉`, unchanged.

The arithmetic that forced it: at five or six seats two Insiders reach parity (D-131) after one or
two Revokes — not a hard round for the Residents but a round that can be over before the first
meeting has anything to discuss. **Seven seats is where a one-plain-Resident buffer survives two
Revokes**, so seven is where the second Insider becomes legal. Revision 21's rule that both edges
clamp the *setting* and not only the draw carries over unchanged: a host cannot hand-pick two
Insiders into a five-seat round.

### D-128 · **The minimum party is five seats — decided**

Four plain Residents and one Insider. Below that the vote has nothing to work with: three plain
Residents and an Insider is one wrong Restrain away from parity, and the meeting the whole game is
built around becomes a formality.

### D-129 · **Work-order size is computed from public facts alone — decided**

The minimum Subroutines per Resident is `K = ⌈M ÷ worstCasePlainResidents⌉ + slack`, where
`worstCasePlainResidents = seats − bandMax`. **The shape of that formula is the ruling; the numbers
are not.**

**It is computable from public lobby facts only** — seats, plus the visible setting or the band it
clamps to — and **never from the hidden draw**. Sizing K against the *actual* Insider count would
let work-order length divide out the number D-103 spent a whole revision hiding, which is the same
division the percentage-only meter exists to prevent. The Insider's fake order is drawn from the
same sizing rule, so **order length is role-independent** on both axes.

**Not a home gate.** A home too small to hold `seats × K` distinct markers is fine, because
same-card reuse (D-123) absorbs it, so this is enforced at arming and not at REVIEW. `slack` is the
one new balance knob and playtest owns it.

### D-130 · **SystemIntegrity scales with seats — decided**

The meter total `M` is **proportional to seats**; the coefficient is playtest's. This retires the
last of the fixed-total thinking F-005 kept catching — `7 × residents` was a coefficient with the
scaling already implied, and naming the shape apart from the number is what lets playtest move one
without re-deriving the other. The display rule is untouched: **the meter reaches a panel only as a
percentage** (D-103), so scaling the total leaks nothing.

### D-131 · **The win conditions, and the Egress that outlives its Insiders — decided**

**Insiders win** when remaining plain Residents ≤ remaining Insiders — parity — or when an Egress
succeeds. **Parity implies the vote veto**, and that implication is why parity is the line: at
parity the Insiders can always block a Restrain, so **Restrain is majority-decided** and a Resident
majority is the Residents' only real instrument.

**Residents win** by completing SystemIntegrity, or by Restraining every Insider — **except that a
running Egress outlives its Insiders and must still be stopped.** Restraining the last Insider
during an Egress does not end the round: the house does not stop what it was told to start.

### D-132 · **Every Insider cooldown starts running at round start, at half — decided**

All Insider ability cooldowns begin the round already running, at **half** their normal duration.
The round therefore opens with a guaranteed stretch of peace, and it closes the opening-Revoke
problem structurally rather than by asking players not to. It also replaces F-004's *no initial
cooldowns at arming* gap with a positive rule. The durations themselves are playtest's.

### D-133 · **No meeting is called during an Egress, and a reported Revoke pauses it — decided**

Three rules, and the third is the one that matters. **No meeting can be called during an Egress** —
the meeting card is inert for the duration, because a house on fire is not a house that debates.
**Reporting a Revoked player still triggers a meeting from anywhere** (D-121's one exception,
unchanged). And **that meeting pauses the Egress timer — it never resets it** — resuming when the
meeting ends. A reset would make the report a free Egress cancellation and every Egress would end
the same way; a pause makes it a decision about spending time.


---

## Revision 30 — the couch, and the map that guesses

*Decided 2026-08-21, with Vadmanuel: correcting E1-2, confirming E1-1 and E8-2, and opening the
hardware-dependent system that forced revision 23's presence amendment.*

### D-134 · **A player who is out has a screen, not a blank wait — corrected**

E1-2 asked whether *"a screen with nothing on it, waiting"* is the intended resting state for a
player who is out. **It is not**, and there are two answers depending on which kind of out they are.

**Newly Revoked:** a screen tells them they are Revoked and to sit silently where it happened. When
a meeting is called they get **no phone call** — they get **STAND AND WALK IN**, with a long haptic
(D-135). The ringing call is for the living.

**Previously Revoked, and Restrained players:** couch spectators, with a live view. During a
meeting, the discussion and vote timers plus the **live vote** — ghosts are the only readers who see
selections rather than a count (D-117). During the round, a live map (D-136), live Egress state, and
live Resident progress — live percentage, never a denominator (D-103).

**Two loop requirements confirmed rather than newly decided:**

- **E1-1** — the Restrained takeover is a **house push, per seat, at the halfway mark of the Tally
  countdown**, confirming D-102's line and revision 20's. The unit's reading was right, and the
  client-scheduled alternative is rejected for the reason the unit gave: a table in `ui` that could
  say *"…and if it was you, this other screen"* is the device deciding a game answer.
- **E8-2** — all four meeting transitions (the check-in gate closing, the discussion ending or a
  unanimous READY arriving, the vote window closing, and the ghost walk-in) are **authority pushes
  the loop must carry.** Recorded so nobody later reads the missing edges as an omission.

### D-135 · **The long haptic is a closed set — decided**

D-102 ruled that a screen arriving unasked buzzes and that the buzz is identical for every player.
This closes the remaining question of *which* buzz. **Every event buzzes; the long haptic is
reserved for five:** the Egress, an incoming phone call, STAND AND WALK IN for a newly Revoked
player, the Restrained takeover screen change, and the end of the LIGHTS OUT IN *n* countdown for
everyone still in the game.

The set is closed for the same reason the dim is (D-118): in a silent house a long buzz is
world-observable through a pocket, and a signal that means five specific things is still a signal.
D-102's identical-for-every-player rule governs each of them — the reserved list is about duration,
never about who feels it.

### D-136 · **The spectator map is inference, and a performer's own scan is knowledge — decided**

A new system, hardware-dependent and future work, ruled now because it forced D-111's amendment.

**While a player scans and performs a Subroutine, their phone BLE-scans for nearby phones and places
those players in that room.** The spectator map is built out of those sightings.

**It is knowingly imperfect, and that is the design.** Short range is preferred, and
**adjacent-room bleed is accepted** rather than engineered away: a map the out can read perfectly
turns the couch into an oracle and the living into pieces on it. Misreading each other is this
game's material, and it does not stop being the material when the reader is on the couch.

**The dedup rule only runs one way: a performer's own scan placement is ground truth.** The house
*knows* where a scanning player is, because they scanned a card whose room the house knows, and **no other
phone's BLE sighting may relocate them. Inference never overrides knowledge.** Every other placement
is a sighting and may be overwritten by the next one.

This is what made the presence plane necessary (D-111): the map has to know when a performance
window opened and closed in order to expire placements, and that is a report the work plane refuses
to make. Hence the split — and hence the rule that presence data feeds the map's expiry and nothing
a living player can read.

### Open and deferred, recorded as such

**Playtest owns:** `slack` (D-129), the meter coefficient (D-130), the cooldown durations (D-132),
and the vote-window option steps — 45/60/90/30 are candidates and only the 45 is the design's
(D-117).

**Copy pending at build time:** the READY button (D-117) and the verdict lines (D-109).

**Design-project work, not settled here:** whether the device mockup's vignette joins the scanning
lines; and the HomeDetail/Delete plan-drawing treatment, which awaits the owner's review.


---

## Revision 31 — the four with no game in them

*Decided 2026-08-21, afternoon, with Vadmanuel, specifying the four Subroutines the overnight run's
batch-1 triage marked UNDERSPECIFIED (`_bmad-output/overnight/2026-08-21.md:1445`, `:1447`, `:1448`,
`:1449`). The triage said precisely what was missing for each; these four rulings supply exactly
that and nothing more.*

### D-137 · **Sniff is a magnitude comparison, not a count — decided**

Closes the triage's line on Sniff (`overnight:1447`), which showed that *"the phone buzzes N times;
tap N"* (`gdd.md:568`) is two different Subroutines and that both are wrong: tapping N *times* is
Handshake with the rhythm taken out, and tapping a choice labelled N puts a numeral on screen, which
the bench's own rule calls suspect (`gdd.md:588`). **The GDD's line is superseded.**

**Sniff is a magnitude comparison and nothing else.** The phone buzzes **two haptic groups separated
by a pause**, and the player answers **which group was bigger**. That is pure perception, exactly as
`gdd.md:588` asks — there is no arithmetic for the player to carry because there is no arithmetic.

**The screen is fully dark until the answer.** No numeral, no running count, no rhythm to hold. It
stays the roster's only *short dark* (`gdd.md:580`), which is the cell the design most needs filled:
the quick one a Resident can take without becoming a beacon.

**Its identity against its neighbours is what makes it a tenth Subroutine rather than a variant.**
Handshake is a pattern echoed back; Sniff has no pattern. Short is a pose held; Sniff has no pose.
What is left is one comparison and one answer.

**Equal groups never occur.** The answer must exist and must be unique — a tie is a coin flip the
house would then grade, and D-109 grades entries on their merits or not at all.

**The answer gesture is a presentation choice** — how a player says *the second one* on a black
screen. The builder decides it and flags it, as with every presentation fixture.

**Playtest owns the gap between the two group sizes.** It is the difficulty knob and the only one.

### D-138 · **A Deallocate tap removes a dot, and evening out means levelling down — decided**

Closes `overnight:1448`, which named three candidate games hiding inside *"tap to even them out"*
(`gdd.md:569`) and observed that the choice decides whether the answer is even unique.

**A tap removes one dot from the tapped column.** Evening out means bringing every column **down to
the shortest** — deallocating what was over-allocated. **The verb is the fiction**, and the fiction
was the answer all along.

**So the answer is unique and the work is countable:** the required number of taps is the sum of the
excess over the shortest column. Nothing on screen is a numeral; the columns carry the arithmetic
(`gdd.md:588`).

**Over-taps are the player's to make.** Columns can go below level, the screen only echoes the tap,
and the house rejects a wrong final state on hand-over (D-109, D-110). A column that refused to go
below level would be the phone forming an opinion about the answer, and D-125 is explicit: clamp
only what players cannot perceive. Column heights are the one thing here a player *can* perceive.

**Playtest owns the column count and the dot distribution.**

### D-139 · **The Interrupt sweep bounces, and it never stops — decided**

Closes `overnight:1445`, which found that removing the unauthored motion and the balance number from
*"a slow bar sweeps; tap inside a generous band"* (`gdd.md:566`) leaves a static bar with no moment
to tap. **The GDD's line is superseded in both respects.**

**The sweep bounces.** It ping-pongs along a visible bar carrying a visible band, and the player taps
to catch the sweep inside the band. Ping-pong rather than wrap: there is no dead return stroke to
wait through and no discontinuity at the edge for a player to time against instead of the band.

**The sweep runs forever until tapped or abandoned.** There is no timeout. **Hesitation is taxed by
exposure, not by a clock** — the screen is MEDIUM and lit, the player is standing at the marker where
D-110 keeps them, and every extra pass is another second of being visible to whoever walks in. That
also keeps constraint 4 (`gdd.md:595`): the sweep is slow and the band is generous, so the pressure
lives in the room rather than in a fail window.

**The architecture is the point, and D-140 reuses it.** The house sends the parameters — band
position, speed, phase — when the scan opens the Subroutine; **the client renders the motion
deterministically** from them; **the tap's sweep-position is the entry**; the house grades it. No
authored Effect, no motion on the wire, no new schema row — which is exactly the wall the triage said
this Subroutine was standing at.

**Every re-scan re-draws band position and phase**, so a retry under D-110 is a fresh judgment rather
than a second run at a picture the player has already memorised.

**Playtest owns the speed and the band width.**

### D-140 · **Drift is object permanence against a house-authored NOW — decided**

Closes `overnight:1449`, which named the same unauthored motion as Interrupt plus three things the
design never said: path, occluder layout, and what radius counts as *"where it is now"*
(`gdd.md:570`). The ruling answers the one the triage called *the whole difficulty* — **when *now*
is** — by making it the house's to choose.

**A dot drifts at constant velocity along a straight path, passes behind occluders and hides. At a
house-chosen instant a haptic pulse says *now*, and the player taps where the dot is at that
moment.** The tap position is the entry; the house grades it against the true position within a hit
radius. The pulse is a short one — D-135 reserves the long haptic for five events and this is not
among them.

**The hidden duration is the difficulty, and it is house-authored and invisible.** The player cannot
see how far they will have to carry the dot in their head before being asked, which is the entire
test and the reason the answer cannot be read off the screen.

**Its identity against Interrupt is a clean split:** Interrupt is the player's timing against a
visible rhythm; Drift is the house's timing against the player's mental model. One asks *when*, the
other asks *where*.

**It uses D-139's pattern exactly.** Path, occluder layout and speed are seeded per scan, sent at
scan time, rendered deterministically by the client, and **re-drawn on every re-scan** — so a retry
is again a fresh judgment. Both are single taps, so constraint 5 (`gdd.md:596`) holds too.

**Playtest owns the speed, the hidden duration and the hit radius.**

### The roster is fully specced, and where the GDD is now wrong

**All ten roster Subroutines are specified.** Six are built as of `14d502d` — Replay, Parity Check,
Short, Signal Trace, Jam, Handshake — and **these four are now buildable**, which was the last thing
standing between the roster and a complete set of interactions.

**The roster lines at `gdd.md:563`–`:588` are superseded by this revision where they conflict.**
Specifically:

| line | the superseded phrasing | what replaces it |
|---|---|---|
| `gdd.md:568` | Sniff — *"The phone buzzes N times; tap N"* | D-137: two haptic groups, and the answer is which was bigger |
| `gdd.md:569` | Deallocate — *"tap to even them out"* | D-138: a tap removes one dot, and level is the shortest column |
| `gdd.md:566` | Interrupt — *"A slow bar sweeps; tap inside a generous band"* | D-139: the sweep bounces and never times out, and the band is drawn per scan |
| `gdd.md:570` | Drift — *"tap where it is now"* | D-140: *now* is a house-sent haptic instant, not a moment the player picks |

**What the roster keeps.** Every light signature stands unchanged (D-112, D-113), the tiers stand,
Sniff remains the only *short dark* (`gdd.md:580`), and `gdd.md:588`'s rule survives all four intact
— not one of them shows a numeral.

**Playtest owns**, added to revision 30's list: Sniff's group gap (D-137), Deallocate's column count
and distribution (D-138), Interrupt's speed and band width (D-139), and Drift's speed, hidden
duration and hit radius (D-140). **Presentation pending at build time:** Sniff's answer gesture.


---

## Revision 32 — the two-second hold, and the button that was never there

*Decided 2026-08-21, afternoon and evening, with Vadmanuel. One correction to revision 27, one input
primitive named across five screens, the arming surface ratified against three GDD fossils, and the
loop run's remaining escalations closed — E-L1-1, E-L3-1 and E-L3-3, the last of them taking F-005's
other half with it.*

### D-121 (corrected) · **Reporting a Revoked player is the contact handshake, not a button**

Revision 27's D-121 called the report *"F-011's unconditional claim button, unchanged"* and said it
*"works from anywhere"*. **The first half is wrong, and not cosmetically.** There is no claim
button. The GDD deleted it, and deleted it for precisely the reason it now has to go from this log
too: *"no button means no enabled/disabled state to read, so it cannot be used as a proximity radar
for revoked players"* (`gdd.md:536`). A log entry that reinstates the button reinstates the radar,
because a button that is enabled only next to a Revoked player is a detector held in the hand.

**The report is the contact primitive** (`gdd.md:543`): one short-range device-to-device handshake,
two verbs, resolved entirely by the state of the two phones — **armed Insider + live player →
revocation; any player + Revoked player → report; anything else → nothing at all, silently, with no
feedback on either device.** The same gesture that Revokes reports, and which of the two happened is
never a choice either player makes. It is the state of the phones, not a selection.

**What survives unchanged is the exception itself.** *From anywhere* meant, and still means, **not
at the meeting card**. D-121's rule is that a meeting is called by walking to the card and scanning
it; the report is the one meeting called somewhere else — kneeling in the dark beside the person
being reported. D-133's *"reporting a Revoked player still triggers a meeting from anywhere"* reads
correctly under this correction and needs no amendment of its own.

**The correction tightens D-121 rather than loosening it.** A button would have been pressable from
a chair, which is exactly what D-121 spent its closing paragraph refusing. Contact is still a walk
to a place; the place is just a person rather than a card, and it is the most exposed place in the
house — stationary, both hands occupied, kneeling beside the player you are about to call everyone
in to look at.

### D-141 · **The two-second hold is the app's deliberateness primitive — decided**

The Delete screen's `HOLD TO DELETE` is not a one-off for a destructive settings action. **The
two-second hold is the app's single gesture for *I meant this*, and five controls take it:**

| control | what the hold is paying for |
|---|---|
| **the vote lock** | select a row, then hold; on completion the vote locks **irrevocably** and the control disables, reading **VOTE CAST** |
| **arming a Revoke** | an accidental arm spends a full cooldown (D-009, D-142) and there is no cancel |
| **arming an Egress** | the misfire the GDD calls *"a game-ending misclick [that] **will** happen in the dark"* (`gdd.md:533`) |
| **the host's LIGHTS OUT** | it starts the evening, in front of the whole party, and there is no way back to the lobby |
| **StairsWarn's UNREGISTER AND CONTINUE** | it discards a registration the host climbed the stairs to make |

**One rule generates that list: hold what cannot be taken back.** Each of the five is irreversible
at the moment it completes, and four of the five are pressed in the dark by a thumb that cannot see
what it is over. The hold is not a confirmation dialogue — it asks for no second screen, no second
target, and no reading. It asks for two seconds of continued intent, which is the one thing an
accident cannot supply.

**Reporting a Revoked player takes no hold, and must not.** The deliberateness there is already paid
in the body: crossing a dark house and putting your phone against someone else's is a slower and far
more exposed commitment than any progress bar can represent. A hold stacked on top would ask a
player kneeling in the open to stay there two seconds longer for nothing — and this is the game's
most vulnerable posture, which is what makes the report a decision at all (`gdd.md:536`'s *report it
now, or walk away and come back when it suits you*).

**STOP NOW stays an instant tap, deliberately, and this is the one place friction is refused.** It
is the panic exit from a Subroutine, and D-111 made abandonment free by design: the work plane hears
nothing, no partial state is held, and the next scan restarts. **Friction on the exit would undo
that.** A player who hears someone in the doorway has to be able to be looking at nothing in the
time it takes to lift a thumb, and a two-second bar filling while they wait is both a delay and a
lit rectangle on the screen of somebody trying to stop being interesting. The abandonment the design
priced at zero has to cost zero at the fingertip too.

**The vote's copy follows from the first row.** The pre-lock control **is** the hold — there is no
READY tap on the ballot any more, which retires half of D-117's open rename: READY TO VOTE survives
in the discussion, where it is a readiness signal rather than a commitment, and the ballot's own
button is gone. The locked label **VOTE CAST** stands, as built.

**D-117 is otherwise untouched.** Selections still stream live so the out can watch the vote happen
(D-134), the lock is still irrevocable, `N OF 6 VOTED` still counts locked players rather than
selections, and **the buzzer still auto-locks whatever is selected** — a player who never completes
a hold has their selection locked for them, which is what keeps the hold a *lock* and not a
*submit*. D-075's skip-by-silence still reaches only a player who selected nothing at all.

### D-142 · **Arming is springboard page 2, in place — and a Resident's page 2 does nothing at all**

Two halves: where the ability surface lives, and what it does under the wrong thumb.

**Where — ratified as built.** Arming happens on **page 2 of the springboard, in place.** Nothing
opens, no view is pushed, no panel is summoned, and the Insider never leaves a screen they were
already allowed to be on. A swipe they share with every Resident, and a hold.

**Three GDD paragraphs are pre-D-009 fossils and are formally superseded here** — `gdd.md:533`'s
*"tap in the panel, then a second deliberate confirm"*, `:535`'s *"arm in the panel, then touch your
phone to theirs"*, and `:941`'s *"long-press bottom-left ~400ms → Status panel → read the roster →
arm Revoke"*, together with `:304`'s tier layout described as the top and bottom halves of that
panel. They all describe a Status panel summoned by a corner long-press, with a two-step confirm
inside it — the shape the design had *before* D-009 made contact the only confirmation that exists.
**The second confirm is now the hold** (D-141), and it is the only one. A later GDD sweep will
reword them, as D-108 swept D-098's ten sites; they are listed here so the sweep has its list and so
nobody reads the fossils as a live specification in the meantime.

**What — the tiles are identical at rest.** Same lighting, same furniture, same brightness on both
roles' page 2, which is the parity the port already built and for the reason it built it: a dimmer
page 2 reads across a dark room as *this one has nothing to tap*, and that is the tell the whole
discipline exists to prevent.

**But a Resident's page-2 controls are ENTIRELY DISABLED — no interaction at all, from the first
millisecond.** No press feedback, no hold progress that runs and then declines, no animation, no
delay, nothing. **Capability on page 2 belongs to Insiders alone.**

The distinction is exact and it is the whole ruling: **identical at rest, inert under the thumb.**
A hold that filled for two seconds and then refused would be a self-test — press it, watch it, learn
your own role — and it would be worse than useless besides, because a bar filling in a dark house is
world-observable to whoever is standing behind the shoulder. The tell the parity was built against
is a *resting* tell; the answer to it must not open a *behavioural* one. Rule 1 again: the safe
control is the one with nothing behind it to probe.

⚠️ **A copy consequence for the builder, flagged rather than ruled:** `PanelModel`'s resting
sub-line reads *READY . TAP TO TEST* for Residents, which invites a tap on a control that now does
nothing whatever. That line has to change. What it becomes is a build-time call under the same
parity rule — it must be a true sentence about a page that does not respond, and it must not be
shorter, dimmer or otherwise distinguishable from the Insider's.

**The arming behaviour itself is D-009 reaffirmed, unchanged:** silent and invisible, nothing moving
on any screen, a **45s window**, the **cooldown spent at the moment of arming** rather than at the
landing, and **no cancel**. A botched stalk still costs a full cooldown, which is still the entire
reason the two-step is interesting.

### D-143 · **The band's floor rises at twelve seats — decided**

Closes **E-L3-1**, the overnight run's escalation on the one place revision 29 disagreed with
itself. **The conservative reading stands** — which is also the reading already built.

**The band's minimum is `max(1, ⌊seats ÷ 6⌋)`**, exactly as revision 21 wrote it. The floor is 1
through eleven seats, **2 from twelve, and 3 from eighteen.** D-103's amendment changed the band at
five and six seats and **nowhere else**; *"from 7 seats the band resumes, `1..⌈seats/4⌉`,
unchanged"* meant the band as revision 21 wrote it, and the leading `1` in that shorthand was the
floor's value at the seat counts under discussion, never a replacement for the expression.

The reason is D-103's own and it is the reason the minimum edge exists at all: **always enforce a
relatively balanced game.** One Insider against seventeen Residents is unwinnable for the one, and
that is a balance fact no host can see and no player can ever check — so it is clamped, on the
setting as well as on the draw (revision 21, D-125). A host at eighteen seats cannot hand-pick a
single Insider any more than they can hand-pick five.

Both readings are identical at 6–10 seats, which is the engineering posture, so **nothing built
changes.** The one line and the one test E-L3-1 asked somebody to say are now said.

### D-144 · **Restrain is plurality-decided, Skip is a candidate, ties resolve to Skip — ratified, and D-131 amended**

The GDD's own rule at `gdd.md:413`, ratified rather than re-invented: **most votes is Restrained;
ties resolve to Skip.** Skip sits on the ballot as a candidate like any name, and a tie — between
two names, or between a name and Skip — **Restrains nobody.**

**D-131's phrase *"Restrain is majority-decided"* is amended to read plurality-decided.** The word
was loose and the mechanism was never a majority: with Skip on the ballot and abstention counting as
nothing (D-075, as narrowed by D-117), four votes out of nine Restrain if no other candidate reaches
four.

**The parity veto survives the amendment intact**, which is the only thing the change needed
checking against. D-131 makes parity the Insiders' win *because* at parity they can always block a
Restrain, and that holds under plurality without needing a majority at all: **at parity the Insiders
can always force at least a tie, and a tie resolves to Skip.** Half the room voting as a bloc for
Skip denies every name a plurality over it. The implication D-131 rests on is unchanged; only the
word describing the tally was wrong.

### D-145 · **The out watch everything live, and the correlation they can build is theirs to keep — decided**

Closes **E-L1-1** by accepting what it named rather than narrowing it. **Option (a): the out see the
meter, the map and the vote live, and nothing is taken away.**

The escalation was right about the mechanism, and it is worth writing down plainly so nobody
rediscovers it as a bug. D-111's presence plane opens and closes *performing at room X*; D-109 makes
an Insider's success write nothing to SystemIntegrity; and a couch watching both for a whole round
is watching a correlation whose far end is alignment. **That is real, and it is accepted.**

**Three things make it the ghost's game rather than a leak.** It is inference stacked on inference —
the spectator map is knowingly imperfect by design (D-136), adjacent-room bleed included, and two
players can be performing at once. It accrues slowly, across a round, against a meter that is a
percentage and never a count (D-103). And decisively: **the out can act on none of it.** They cannot
speak, cannot vote (`gdd.md:415` — voting is a communication channel), and cannot signal. The one
thing a player who has worked out an Insider would normally do is exactly the thing being out has
already removed.

**So the slow private suspicion is deliberately theirs.** It is most of what makes the couch worth
sitting on. D-134 gave the out a screen so that being out is a different game rather than no game,
and **this is the game it is** — reading the house from outside it, correctly or otherwise, with no
way to be believed. Closing the correlation would mean taking the meter or the map from the only
readers permitted to have them, trading a real screen for a theoretical leak against a reader who
cannot use it.

### D-146 · **Dependencies are cross-player, a Revoke strands them, and over-provisioning is the whole rescue — decided**

Closes **E-L3-3**, and with it **F-005's other half** — open since the very first review pass, and
the last thing F-005 was still holding.

**Work-order dependencies are cross-player.** That was the original concept and it is the design: a
Subroutine blocked by a *downstream* Subroutine that somebody else holds, so the work order is
something the house does together rather than six private lists that happen to be running in the
same building. **Self-chains came later and for a different reason** — D-123's same-card reuse
produces blocked-by-your-own-work as a by-product on homes with few markers, discovered as the
player completes. Both exist; only one of them is the point.

**A Revoke strands work, and nothing rescues it.** When a Revoked player held somebody else's
blocker, **the orphaned blocks stay blocked. Nothing auto-satisfies, ever, and no per-chain repair
runs at any point in the round.** The house does not quietly finish a dead player's work.

**This supersedes F-005's *"orphaned subroutines are silently auto-satisfied"* wherever it appears**
— `gdd.md:274`'s *collapse gracefully*, `gdd.md:917`, `gdd.md:1031`, story 7.4 at `epics.md:225`,
and the F-005 line at the head of this log.

**What keeps the round winnable is aggregate over-provisioning, and only that.** D-129 already sizes
every Resident's order at `K = ⌈M ÷ worstCasePlainResidents⌉ + slack`: **`K × Residents` of
completable work against a meter of `M`, plus slack**, with the ceiling computed against the worst
case for the Insider count. There is deliberately more work in the house than the meter needs, so
stranded work comes out of a margin that was sized for it. **`slack` is what pays for the
stranding** — which is the load the overnight run put on it when it moved `ORDER_SLACK` off zero,
and the reason it can never go back to zero.

**Why no rescue, stated so it does not get re-invented.** Balance was never the objection — the
2026-08-18 attrition work showed auto-satisfaction is roughly neutral, because the freed work comes
out of the departing player's own allocation. Three other things are:

- **It is a second mechanism doing the first one's job.** Slack already absorbs stranding, globally
  and without knowing why anything stranded. A per-chain repair adds a rule that fires on a
  condition, and every rule that fires on a condition is a thing somebody can read.
- **It is the house moving the meter for a reason no player caused.** The out read that meter live
  (D-134). A percentage that steps on its own, at the moment of a Revoke, is a removal report
  delivered as progress — and D-103's whole argument is that the meter says one thing only.
- **A permanently blocked entry is content.** D-114 already makes blocked work visible as a known
  unknown. An entry that never unblocks is a fact a Resident can carry to the meeting: either the
  player upstream of me is gone, or they are sitting on it. **That ambiguity is the game**, and
  auto-satisfaction would resolve it silently and for free.

### D-147 · **Unanimous READY counts the living only — confirmed**

Confirming the meeting unit's reading rather than deciding something new. **The discussion's
unanimous READY TO VOTE, and the early close it triggers, count living players only** — for the
plainest possible reason: **the couch has no such control.** A ghost's meeting screen carries the
two timers and the live vote (D-134) and no readiness button, so there is nothing of theirs to count
and a gate that waited on them would simply never open.

**This does not soften D-104**, and the two belong next to each other because they look like they
disagree. D-104 gates the *start* of the discussion on every living player and every out player
checking in at the meeting area — that is presence, which a ghost has, because a ghost walks in.
Readiness is an opinion about a conversation, which a ghost cannot hold because a ghost cannot
speak in it. **Presence is everybody's; readiness is the living's.**

### Where the GDD, the epics and this log are now wrong

| site | the superseded phrasing | what replaces it |
|---|---|---|
| `gdd.md:304`, `:533`, `:535`, `:941` | the Status panel summoned by a corner long-press, and *"tap in the panel, then a second deliberate confirm"* | D-142: springboard page 2, in place, nothing opens, and the second confirm is the two-second hold |
| `gdd.md:274`, `:917`, `:1031` | orphaned Subroutines *"silently auto-satisfied"*; chains *"collapse gracefully"* | D-146: nothing auto-satisfies; `K × Residents` plus slack is the entire answer |
| `epics.md:225` (story 7.4) | *"Orphaned subroutines silently auto-satisfied (revoked holder, collapsed chain) so the bar stays winnable"* | D-146: the story loses its subject; the sweep decides whether the row goes or is rewritten to record the ruling |
| `gdd.md:412`'s vote control | the ballot's button (already renamed once by D-117) | D-141: a two-second hold, then **VOTE CAST** |
| this log, D-121 | *"F-011's unconditional claim button, unchanged"* | D-121 (corrected): the contact handshake, `gdd.md:543` |
| this log, D-131 | *"Restrain is majority-decided"* | D-144: plurality-decided |
| this log, F-005 (line 94) | *"with orphans silently auto-satisfied"* | D-146 |

### Open and deferred, added to revision 30's list

**Copy pending at build time:** the ballot's pre-lock hold label (D-141), and the Resident's page-2
resting sub-line, which can no longer say TAP TO TEST (D-142). D-109's verdict lines are still open
from revision 23.

**A GDD and epics sweep is owed** — D-142's four sites and D-146's four, on the D-108 pattern. It is
a wording sweep and it changes no ruling.

**Playtest keeps `slack` (D-129)** and now knows what it is buying: D-146 makes it the only thing
standing between a stranded chain and an unwinnable meter, so it is a balance number with a
correctness job attached.


---

## Revision 33 — the house picks the doors, and the meter still says one thing

*Decided 2026-08-22, late evening, with Vadmanuel. The three findings that had outlived the §18.1
trace are closed — F-001, F-002 and F-003 — the Egress is finished as a system, and the round gets
an end that leads somewhere. One promotion is reversed. Everything here that touches a written site
is swept in the same pass, alongside the sweep revision 32 said was owed.*

### D-148 · **The house picks the two nodes when it fires, and a small home still burns — F-001 ratified**

**F-001's proposed resolution is ratified as written.** The two Egress nodes are **two ordinary
registered markers in non-adjacent rooms, chosen by the house at the moment the Egress fires.**
There is **no setup step**, the host designates nothing, and the pair is different every time.

That is D-122 and D-123's shape applied to the Egress: the host designates the Terminal and nothing
else, adjacency is already free from the grid (story 4.9), and a marker is *capacity the house may
draw on* rather than a thing somebody assigned. A fixed pair would be a fact learned once and kept —
the same failure D-122 refused for the Array Wipe circuit, and for the same reason. **The circuit
moves every round; so do the doors.**

**Small homes degrade, and never fail.** If no non-adjacent pair exists, the house takes **the
farthest available pair** and fires anyway. **An Egress never fails to fire.** A crisis that
silently declined to happen because the geometry was inconvenient would be the worst possible
version of rule 1: the absent effect is the leak, and here the absent effect is the whole event.
Non-adjacency is a *preference with a fallback*, not a precondition — it buys the containment run
its distance, and where the house cannot supply distance it supplies what it has.

Built in **L5** (`7f2c1c1`).

### D-149 · **Beacon and Tether are a label in v1, drawn at fire time — decided**

**Which of the two an Egress is, is drawn randomly by the house when it fires**, alongside the
nodes. In v1 the two are **mechanically identical**: same node selection, same timer, same
containment, same everything. The difference is the name and the flavour text.

**Any "containment beat" difference is deferred**, which retires the last clause of F-001's proposal
(*"Beacon/Tether then differ in flavour text and the containment beat"*). Two fictions that behave
identically cost nothing and give the house two things to say; two fictions that behave *differently*
is a second system, and it has to be designed rather than implied by a pair of nouns. **The label
ships; the mechanism waits.**

### D-150 · **An Egress node is not on Isolate's list at all — F-002 amended and closed**

F-002 found that Isolating an Egress node during an Egress makes containment impossible and wins the
game with no counterplay. **The resolution is exclusion, not immunity: for the duration of an
Egress the two nodes do not appear in Isolate's target list.**

**This supersedes `gdd.md`'s appears-to-succeed proposal**, and the reason is the shared pool. The
old shape had Isolate fire at a node, play its animation, spend from the **Access pool** and do
nothing — obeying *abilities never report failure* by charging a **shared** resource for an action
that could not work. That is not concealment, it is a tax on a teammate's budget, paid in the dark
by somebody who will never learn why the pool was short later.

**And there is nothing left to conceal, because the house already said it.** The Egress widget
**names both nodes to the entire house** the instant the Egress fires — that is what makes
containment a race rather than a search. A target list that omits two rooms everybody was just told
about leaks exactly nothing. The usual argument for fake success is that the absence of an option
is information; here the information is already public, published by the house, to everyone.

**Isolate is unbuilt.** This is a constraint recorded for its builder, not a change to shipped code.

### D-151 · **The Egress cooldown is one clock for the whole house; Revoke stays per-Insider — decided**

**The Egress cooldown is shared house-wide across all Insiders — one pool, one clock.** One Insider
firing puts *every* Insider on cooldown. **The Revoke cooldown remains per-Insider**, untouched.

The panel's own line — *SHARED WITH THE OTHER INSIDER* — is **ratified as built**
(`PanelModel.kt:463`), and it is the reason the split is right. The Egress is a **house event**: it
takes the whole building, it is announced to everybody, and two Insiders firing back-to-back would
be one crisis wearing two hats. The Revoke is a **personal act** — one stalk, one contact, one
person's exposure — and a shared Revoke clock would make one Insider's botched approach silence the
other's, which is a coordination failure the two of them cannot talk about.

**Both start the round at half (D-132), unchanged.** The halving is per-clock: the shared Egress
clock starts half-run, and each Insider's own Revoke clock starts half-run.

The interesting consequence is deliberate: **the Egress is a resource two people who cannot speak
have to spend between them**, and either of them can spend it without asking. That is the same
pressure D-146 puts on the work order, applied to the Insiders' side of the board.

### D-152 · **The meeting card is `u_shape` — ratified**

D-121 reserved a second shape without naming it, and the build named it provisionally. **The name
stands: `u_shape`, the U being a couch seen from above.** It is a letterform like the T rather than
one of the 42 abstract marks, so the two reserved cards read as a pair on a printed sheet and
neither reads as an ordinary marker.

Ratified rather than re-decided, because **paper cannot be patched** — the same reason D-120 gave
for the T. The reserved set is two; the registrable roster is 42; all 44 roster entries stay, since
ids are never renumbered (D-070, D-085). `MarkerShapes.kt`'s provisional note is updated in this
pass.

### D-153 · **No screen ever prints a total — the endings included — decided**

**D-103's percentage rule extends to the ending screens, without exception.** The meter is a
percentage everywhere it is ever shown, and **no screen in this app, at any point, prints an
absolute System Integrity total.**

The endings looked like the safe place for a real number — the round is over, the reveal has
happened, nothing can be acted on. It is not safe, and the reason is that the app is played **two to
three rounds in an evening** (D-157). A denominator printed at the end of round one is a
denominator carried into round two, where it divides out the thing D-103 spent a whole revision
hiding: the total is `7 × initial_residents`, and initial Residents is seats minus Insiders. **One
ending screen would retroactively unhide the Insider count for every round that follows.**

**Two drawn summary rows are therefore defects**: `ScreensOut.kt:515`'s `SYSTEM INTEGRITY 14 / 32`
and `:590`'s `0 / 32`. They become percentages. *(Client work, queued — this revision records the
rule, not the fix.)*

Note that `32` is itself a fossil of the count F-005 corrected, so the rows were wrong twice.

### D-154 · **The meter moves on completions only, and never jumps on a Revoke — decided**

**System Integrity moves when a Subroutine is completed. Nothing else moves it, ever.** In
particular **a Revoke never moves it**, in either direction and by any amount.

This closes the contradiction the 2026-08-18 attrition brainstorm caught between story 7.4 and the
meeting-freeze paragraph at `gdd.md`, and it is D-146 stated from the meter's side rather than the
work order's: if nothing auto-satisfies when a holder is Revoked, there is nothing for the meter to
register at the moment of a Revoke. The two rulings are one rule seen twice.

**Why it has to be said separately anyway:** the out watch the meter live (D-134, D-145). A
percentage that steps on its own — at the exact moment somebody's lamp goes out across the house —
is **a removal report delivered as progress**, readable by the one audience that has nothing else to
do but read it. D-103's argument is that the meter says one thing; a Revoke-driven step would make
it say two, and the second one loudest.

**Story 7.4 is rewritten in this pass** to carry both halves.

### D-155 · **Ghosts are invisible by state, not by place — decided, and story 4.10 superseded**

**A player who is out never appears in occupancy or presence data anywhere in the house.** The
filter is **seat state**, applied by the house, at the source — not geometry, and not a room.

**This supersedes story 4.10's couch dead zone, and the *mapping dead zone* row in the marker
taxonomy. There is no such zone.** The meeting area is **an ordinary mappable room**: it can hold
markers, it is drawn like any other room, and it produces occupancy data about the living exactly as
every other room does.

The spatial version was solving the right problem the wrong way, and it broke in three places at
once. It only hid ghosts **while they sat still** — a ghost walking to the couch, or standing up
during a meeting, re-entered the data. It **cost the host a room**, in a game whose whole board is
the host's actual house. And it made the couch **a hole in the map**, which is itself a signal: the
one room that reports nothing is the room you look at.

**Filtering by state fixes all three and is simpler.** A ghost is invisible on the way to the couch,
on the couch, and anywhere else they wander, permanently, because being out is a property of the
seat rather than of where the seat's owner is standing.

**Deliberate card scans remain exactly what they are.** This ruling governs *presence* — the
ambient, inferred, BLE-sighting layer that feeds the spectator map (D-136). It does not touch what a
living player does by walking to a card and holding the scan button, and it gives ghosts nothing:
a ghost's phone is dark and scans nothing at all.

### D-156 · **One notification, one haptic, identical for everyone — F-003 closed**

F-003 found the reveal leaking through haptics: in `Known` mode a Insider's phone buzzed four times
(blackmail, house line, two partners) and a Resident's buzzed once, **at the exact moment the whole
party is still standing in a cluster**, where a buzz count is both audible and tactile.

**The opening reveal is delivered as one notification, with one haptic, for every player,
identical.** Whatever the house has to say to a given player arrives as **one delivery**. **The
underlying message count never drives the buzz count** — not at the reveal and not anywhere else.

This is D-102's identical-for-every-player rule enforced at the layer the trace found it escaping
from. The fix is not to give Residents padding buzzes, which would be three fake deliveries wearing
the shape of real ones; it is to stop letting *how much the house has to say* be a thing the room
can hear. The content still differs, at whatever length it needs. **The delivery is one, and the
buzz is one.**

### D-157 · **NEW ROUND — the ending screens lead back to the lobby — decided**

**The ending screens gain a host-only NEW ROUND control.** It returns every player to the lobby, and
the evening continues. *"Two to three rounds in an evening"* is the design's own expectation, and
until now the app had no way to have a second one.

**What survives:** the **home** — walked once, ever, and it would be absurd to walk it again at
midnight — the **seats**, and the **lobby settings** the host tuned.

**What is wiped: the one-lines. Every one of them, at the moment the round ends.** That is D-116's
deletion promise, and the promise is what makes people write something true. A one-line that
survived into a second round would be a thing the app kept — and *kept* is exactly the word the
promise exists to make false. Players write a fresh one for a fresh round, which is also better
content.

**Roles are redrawn at the next arming**, never carried. The band and the clamps apply again from
scratch (D-103 amended, D-143), so a second round is a genuinely new draw and last round's Insider
has exactly last round's odds.

**Host-only**, for the same reason LIGHTS OUT is: it starts a round in front of the whole party.
Whether it takes the two-second hold is a build-time call under D-141's rule — it is irreversible at
the moment it completes, so it probably does — but it is pressed in the light with everyone
watching, which is the one place D-141's *pressed in the dark* argument does not apply.

### D-158 · **Degrading Subroutines go back to v2 — the promotion is reversed**

**`gdd.md`'s promotion of degrading Subroutines from parked-for-v2 to load-bearing v1 is reversed.
They are deferred to v2**, and **epics story 6.9 is marked deferred** in this pass.

The promotion's argument was that hiding the Egress progress bar from both roles left Residents with
no way to sense pressure building, making degradation the sole feedback path. **The argument proves
that something must carry that load, not that this must.** Atmosphere already carries it, which is
what `gdd.md` says one paragraph earlier and what it said before the promotion.

**What the promotion actually proposed is a difficulty curve bolted to the win meter** — every
Subroutine in the game changing shape as System Integrity falls, ten interactions each needing a
degraded variant, all of them harder exactly when the Residents are losing, in a game where the
Insider's Subroutines write nothing and therefore degrade for free. **That needs a full design
conversation and a rebalance**, and it is being reversed now rather than discovered mid-build,
because the roster was only just finished (D-137–D-140) and this would have re-opened all ten.

**Nothing in v1 depends on it.** The reversal removes scope; it adds no gap.

### Swept in this pass

Eleven sites, each carrying an existing ruling into the document that still contradicted it. No
ruling is made here that is not made above or in an earlier revision — it is a wording pass.

| site | superseded by | what it now says |
|---|---|---|
| `gdd.md` — *fire an ability: tap in the panel, then a second deliberate confirm* | D-141, D-142 | springboard page 2 in place, select the tile, two-second hold |
| `gdd.md` — *Revoke: arm in the panel* | D-142 | arm on page 2 with the hold; Residents' tiles identical at rest and entirely disabled |
| `gdd.md` — the Insider's loop, *long-press bottom-left ~400ms → Status panel* | D-142 | swipe to page 2, hold a tile, nothing opens |
| `gdd.md` — Array Wipe stations *3, designated* | D-122 | drawn by the house at arming, never stored with the home |
| `epics.md` story 4.5 — *designate the Terminal and the three Array Wipe markers* | D-122 | the Terminal and only the Terminal |
| `epics.md` story 7.4 — *orphaned subroutines silently auto-satisfied* | D-146, D-154 | nothing auto-satisfies; the meter moves on completions only |
| `gdd.md` + `epics.md` story 6.9 — degrading Subroutines *load-bearing v1* | D-158 | deferred to v2, out of v1 scope |
| `epics.md` story 7.13 — unlock delay *15–60 s* | F-013 | 20–120 s, with the round-length coupling stated |
| `gdd.md` — F-002's *appears to succeed* | D-150 | excluded from the target list; no pool spend |
| `gdd.md` — *every player has a blackmail file, and the Resident's is a fake* | D-116 | all the one-lines are real; the house never uses a Resident's |
| `MarkerShapes.kt` — the `u_shape` *provisional ruling* note | D-152 | ratified |

### Where the GDD and the epics are still wrong

Sites this revision creates or inherits, left for the next sweep so that the wording pass and the
ruling pass stay separable:

| site | the superseded phrasing | what replaces it |
|---|---|---|
| `epics.md` story 4.10, and the *Couch / meeting area* row of `gdd.md`'s marker taxonomy | the meeting area as a **mapping dead zone** producing no occupancy data | D-155: no such zone; ghosts are filtered by seat state and the room is ordinary |
| `gdd.md` — the tier layout described as the top and bottom halves of the Status panel | the summoned panel | D-142, the last of its four fossils; the sweep in this pass took the other three |
| `gdd.md` — the *Open the Status panel* and *Adjust the lamp* control rows | a panel summoned by a corner long-press | **flagged, not ruled.** D-142 moved *arming* to page 2 and said nothing about whether a Status panel survives for the lamp dial. It needs a ruling before it can be swept |
| `gdd.md` — F-001's proposal, *Beacon/Tether differ in flavour text and the containment beat* | the containment-beat half | D-149: identical in v1; the beat is deferred |
| `gdd.md` — orphan auto-satisfaction at three further sites, and the F-005 line at the head of this log | *silently auto-satisfied*, chains *collapse gracefully* | D-146, unchanged; three of its four sites are still unswept |
| `ScreensOut.kt` — the `14 / 32` and `0 / 32` summary rows | an absolute meter total on a screen | D-153: percentages. Client work, queued |

### Open and deferred, added to revision 32's list

**Deferred to v2:** degrading Subroutines (D-158), and the Beacon/Tether containment-beat difference
(D-149).

**Copy pending at build time:** the NEW ROUND control's label and whether it takes the hold (D-157),
alongside the ballot's pre-lock hold label and the Resident's page-2 resting sub-line, both still
open from revision 32.

**Needs a ruling before it can be swept:** whether a Status panel survives at all for the lamp dial
now that arming has left it (D-142's silence, flagged above).

**F-001, F-002 and F-003 are closed**, and with them the last of the trace's gaps that were about
what the *house does*. Two remain, both about what a surface *shows*: **F-006** — the Egress widget
takeover costing a stale number rather than a live one — and **F-007** — room counts decrementing
unambiguously where dots merely faded. Neither has a ruling. Both were written before revisions 21
and 30 changed the surfaces they describe, so each wants a re-read against the current map and meter
before it is answered. *(F-009 was resolved in-trace — the Egress timer is a lobby setting, 120s
default. F-013 is a standing dependency rather than a gap, and this pass reconciled its two written
ranges.)*


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
reads the Insider count. Two leak surfaces of three are instrumented.

**Revision 15 added D-085 through D-093** — the map, the app root, and the first phone. E0's
headless work is done: 0.1–0.5, 0.6, 0.6b, 0.7, 0.10, 0.10c's generator and 0.10d are built,
injection-verified and pushed, along with the D-066 gate, F-005's denominator, a recording parser
and resume. **There is now an app that launches on an iPhone.** Putting the ported screens on one
found five layout faults in a single strip of screen, none of them visible on the desktop target
where the screens were reviewed; a guard now renders all 56 against a simulated notch on every
build. **0.6c still remains and still needs hardware — and needs the radio built first, because
nothing broadcasts yet.**

**Revision 16 added D-094 through D-096** — the first two phones. Story 0.8's transport is
device-proven: an iPhone 13 Pro was killed mid-connection and resumed as its own seat against an
iPhone 16 Pro host over real Wi-Fi, and a locked round refused a stranger terminally. Resume
needs WHERE as well as WHO (D-094; mDNS is the real answer, an address store is the interim),
the deployment target is 18 (D-095), and the seventh silent-instrument event was caught by
reading the device's data container rather than the build (D-096).

**Revision 17 added D-097** — the refusal nobody sees. D-068's unbuilt half is settled: a refused
event is answered by re-asserting the client's authorized view, never by a reason-carrying
message — the leak-prone channel is not narrowed but never built. Same mechanism serves resume.
The payload is loop work and stays open; only the mechanism is decided.

**Revision 18 settled D-086** — the refusal is ratified. Two live cards may never share a shape:
the alternative's failure hides inside the Terminal's injected error and would never be seen,
while the refusal lands on the host in the light with 44 shapes to choose from.

**Revision 19 closed F-005's contradiction** — the meter total arrives in `PanelState` from the
authority, frozen at arming; ui's 32 is demoted to a documented fixture default. Open still: the
7 itself (balance) and orphan auto-satisfaction (loop work).

**Revision 20 mined the design chat** — D-098 passage deleted everywhere, D-099 stairs hold
nothing structurally, D-100 house/home in the vocabulary, D-101 v1 blackmail as verbatim
template (LLM is v2), D-102 the haptic doctrine (identical buzz for all, twelve screens, the
halfway-mark restrained reveal). Insider-count setting, check-in gate, swipe semantics and
light-signature visibility are raised, not settled.

**Revision 21 closed revision 20's questions** — D-103 the Insider count can hide behind an
always-percentage meter (rev 19 and D-081 partially superseded), D-104 living and ghosts both
gate the talk, D-105 the read concept is deleted, D-106 the light signature shows everywhere.

**Revision 22 closed escalation E2-1** — D-107 Insider-only rooms are a spoken walkthrough fact
and never app data, doors are physical house rules the app never enforces, and 4.7's hazard
dissolves into walkthrough guidance instead of an editor exclusion; D-108 the ten stale sites in
the GDD, the epics and the architecture are swept to match D-098.

**Revisions 23–30 are one morning's sitting** — the rulings that answered the overnight run of
2026-08-21 — every escalation it left open once revision 22 had taken E2-1 — and a good deal it did
not ask about. **D-109 through D-136**, plus F-014 closed and D-103 amended.

**Revision 23 ruled the Subroutine escalations** — D-109 the house grades every entry for real, for
both roles, in identical words (an Insider's success simply writes nothing to SystemIntegrity);
D-110 one attempt per scan and the walk back is the cost; D-111 the work plane hears nothing while
the presence plane reports the window; F-014 closed — carry state survives a meeting.

**Revision 24 settled the light signature** — D-112 fixed per Subroutine kind in v1, so no field
arrives and no schema row is needed; D-113 Array Wipe is BRIGHT and nothing ships unrated; D-114
blocked work is a visible blocked entry and the work order is a menu.

**Revision 25 opened the name channel and closed the ballot** — D-115 names leave the phone and the
lobby shows them (the overnight counts-only lobby must widen, in a diff that says so); D-116 every
blackmail line is real and only Insiders' are used, superseding `gdd.md:1421`; D-117 selections
stream live, READY is irrevocable, the buzzer auto-locks — superseding `gdd.md:412` and `:1006` and
narrowing D-075.

**Revision 26 closed the light vocabulary** — D-118 exactly two events dim the house, the Egress and
the opening text message, and every other notification is quiet; D-119 the swipe is the
acknowledgment, quiet notifications are stored, and both dim edges are steps.

**Revision 27 ratified the T card and moved the circuit** — D-120 `t_shape` stands and the Array
Wipe stations reserve nothing; D-121 the meeting card is the second reserved shape and a meeting is
called by walking to it (registrable roster 42); D-122 the house draws the stations at arming, every
round; D-123 markers are capacity and a card is a place; D-124 NOTHING FOR YOU HERE.

**Revision 28 stated the doctrine** — D-125 guide, don't gate: clamp what players cannot perceive,
guide what they can; D-126 every gate lives at REVIEW, while the host is alone; D-127 the gate is
one terminal, one meeting card and eight ordinary markers, with HOSTS UP TO N as guidance that never
blocks.

**Revision 29 sized the round** — D-103 amended (5–6 seats carry exactly one Insider; the band
resumes at 7); D-128 five seats minimum; D-129 work-order size computed from public facts alone;
D-130 the meter scales with seats; D-131 the win conditions as a set, parity implying the vote veto
and a running Egress outliving its Insiders; D-132 cooldowns start running at half; D-133 no meeting
during an Egress, and a reported Revoke pauses its timer.

**Revision 30 gave the out a screen** — D-134 the newly Revoked sit and are called in with STAND AND
WALK IN, while the previously out are couch spectators with a live map, live Egress and the live
vote (E1-1 and E8-2 confirmed as loop pushes); D-135 the long haptic is a closed set of five; D-136
the spectator map is BLE inference in which a performer's own scan is ground truth — the system that
forced
D-111's presence plane.

**Revision 31 finished the roster** — the afternoon's rulings on the four Subroutines the overnight
triage marked UNDERSPECIFIED. D-137 Sniff is a magnitude comparison of two haptic groups on a black
screen, never a count; D-138 a Deallocate tap removes a dot and level is the shortest column; D-139
the Interrupt sweep bounces forever until tapped, with the house sending band, speed and phase and
the client rendering them deterministically; D-140 Drift grades a tap against a house-authored
haptic *now*, reusing D-139's pattern. The roster lines at `gdd.md:566`, `:568`, `:569` and `:570`
are superseded. All ten are specced; six built as of `14d502d`, these four now buildable.

**Revision 32 named the hold and closed the loop run** — D-121 **corrected**: the report is the
contact handshake (`gdd.md:543`), never a claim button, and *from anywhere* means only *not at the
meeting card*. D-141 the two-second hold guards the five irreversible controls — the vote lock,
Revoke arming, Egress arming, LIGHTS OUT and UNREGISTER AND CONTINUE — while contact needs no hold
and STOP NOW stays an instant tap. D-142 arming is springboard page 2 in place, tiles identical at
rest and a Resident's entirely inert, superseding `gdd.md:304`, `:533`, `:535` and `:941`. D-143 the
band floor is `max(1, ⌊seats/6⌋)`, so 2 Insiders at twelve seats and 3 at eighteen (E-L3-1 closed).
D-144 Restrain is plurality-decided with Skip a candidate and ties resolving to Skip, amending
D-131's word while its parity veto survives. D-145 the out watch everything live and the correlation
is the ghost's game (E-L1-1 closed). D-146 dependencies are cross-player, a Revoke strands them,
nothing auto-satisfies, and `K × Residents` plus slack is the whole rescue — **F-005 is now fully
closed.** D-147 unanimous READY counts the living only.

**Carried into E0 as a constraint, not a closed item:** total app allocation ≤ ~0.5 MB/s as the design target, with the measured cliff between 1.5 and 3.0 MB/s — roughly 6× margin, so this is a budget rather than a knife edge. Nobody yet knows what the real app allocates with BLE, 100 Hz motion, effects and recording running at once.
Action: create `project-context.md`.
