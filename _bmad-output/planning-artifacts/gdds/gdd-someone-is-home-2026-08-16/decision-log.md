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
| The base design record (1908 lines, since retired) | Reasoning source. **Stale wherever the revisions file says so** — it still described ends, spaceship subsystems, and synthetic replacements. |
| A legacy-project context dossier (since deleted) | Consulted for the original physical-task list only. Otherwise ignored per instruction. |

**Standing rule for this GDD:** where the design record and `brainstorm-intent.md` disagreed, the brainstorm won. The design record's sections on end, the Capgras frame, and conversion mode are superseded, not merged. Both source documents were retired on 2026-08-17; the GDD is now self-contained.

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

**Two decided in-trace:** dot interpolation is defunct under counts (F-008) · Files must never show the Memory Dump pattern (F-015).

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

**Accepted cost: the revoked player often sees who did it.** The mitigation is the mechanic — the target's own screen is the only light on the attacker, and it goes dark at the instant of contact, so certainty is not guaranteed. **E1 must test this in a real dark room** (story 9.19).

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

### D-012 · F-017 reopened → **OI-6** (ghost idle time)
Recorded rather than quietly dropped. I verified F-017 as composing at a 15-minute round; **the ~25-minute decision reverses that.** A player revoked at minute 3 now waits five to ten minutes for ghost mode instead of ~3 — the exact failure state ghost mode was built to solve.

**No clean fix exists.** Ghost mode cannot activate before the living know you are out (the sequencing rule), and phase 1 must stay empty (anything rendered would glow). **Meeting frequency is the only lever.** Instrumented in the metrics table and in E11's acceptance; meeting cooldown is the first thing to try if it runs long.

### D-013 · OI-3 closed — the thin first meeting is by design
Players can vote **Skip**, and the unanimous *Ready to vote* control lets an empty meeting end in seconds. **Choosing to do nothing is a legitimate move**, and a meeting producing no revoke is a valid outcome rather than a wasted phase. No mitigation is being built. **Watch the skip rate at *second* meetings** — that, not the first, is the signal something is wrong.

---

## State after revision 2

**No phase-blocking items remain.** OI-1 deferred with an expiry condition, OI-2 closed, OI-3 closed. **OI-6 (ghost idle time) is newly open and needs playtest, not a decision.** OI-4 (hidden-mode lever) and OI-5 (settings interior) remain parked.

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
- **Does the target's lamp going dark at contact conceal the attacker?** **This is the one with consequences** — if it does not, the anonymous revoke is gone and ghost mode's information design needs revisiting. Watch from the first playtest with a live Revoke.

### D-016 · The meeting call rings — **decided, new requirement**
On the same scheduled timestamp as the masking cut and the lights-up snap, **every phone rings.** The ring lands in the silence the cut just created.

**Why it earns its place despite the silence rule.** The prohibition on phone audio exists because a chirping phone localizes its owner. **At a meeting call, localization is already total** — every lamp in the house just went to full white — so the ring adds no information the light did not already broadcast. And it solves something light cannot: **sound goes through walls.** In a two-storey house you often cannot see another lamp, and heads-down in a dark subroutine you may not register your own screen changing.

Specified: **identical ring for both trigger types** (the screen carries the difference; a differentiated ring would tell players in transit something they learn thirty seconds later anyway) · **plays regardless of the hardware silent switch** (audio session category — a standard iOS gotcha, and half the phones staying quiet at a meeting call is a broken party game) · **stops on acknowledgement or when lights-up completes**, never indefinitely, or a slow player is localized by their own phone.

> **Scope guard written into both documents:** the meeting ring is the **sole** permitted phone-emitted sound and is global by construction. **It is not a precedent for audio anywhere else.** Subroutines stay silent or haptic-only; abilities stay silent; nothing that fires for a subset ever makes a sound.

### D-017 · Base design record marked superseded — **done** (retired entirely 2026-08-17)
A prominent header now points at this GDD, with a was/is table covering the twelve places it is actively wrong (ends, Capgras, the old system-failure event, shoulder tap and target confirmation, phones stopping advertising, dots, Evict, ghost mode open, voting undesigned, red palette, eleven diagnostics, round sizing) and a list of the sections that survive intact and are still worth reading.

**Rationale:** this project has already been bitten once by a stale design document, which is why `design-doc-revisions.md` exists. Two revision rounds have now passed over that file without touching it.

---

## State after revision 3

**No phase-blocking items. No gate.** Build proceeds to full product.

Open: **OI-6** (ghost idle time — playtest, not a decision), **OI-4** (hidden-mode lever), **OI-5** (settings interior). Watched: the amber/Tier-0 question and the contact-conceals-attacker question.

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

**Open:** OI-4 (hidden-mode lever) · OI-5 (settings interior) · OI-6 (ghost idle time) · **OI-7 (Android torch)**.
**Gating:** story **1.7** — the stack gate — runs as a standalone spike before E0 is committed to.

---

# Revision 5 — 2026-08-16

### D-024 · OI-7 corrected and closed — the torch was never the core mechanic
**The original framing was wrong.** It claimed graduated light is the core mechanic, so Android's weak torch degrades the most important dimension. **The torch is not in the light ladder at all** — Tier 0 is the dim amber *screen* lamp, and screen brightness override works identically on both platforms.

**Resolved physically:** the Tier 1 printable becomes a **tube with an optional end cap**. Where software cannot dim the LED, paper does. The tube still narrows the beam to ~20–30°, which is what produces "walk past someone and miss them"; the cap attenuates. **Cap the tube, never tape paper across the lens** — 25 continuous minutes puts paper on the warmest part of the phone, and an inch of standoff is safer and optically better.

**Accepted:** on binary-torch hardware the app can switch the torch but not ramp it. Fine, because **every light event in the game is a screen event.** Small footnote: Tier 0 stays complete everywhere because the torch is optional, but a player who wants the torch on that hardware needs Tier 1.

### D-025 · Transport reopened — MultipeerConnectivity is defunct
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

Architecture Steps 1–3 complete. **Open:** OI-4 (hidden-mode lever) · OI-5 (settings interior) · OI-6 (ghost idle time). **OI-7 closed.**
**Gating:** stories **1.7a/1.7b** — the stack gate — run as a standalone spike before E0 is committed to.
**Next:** Step 4, opening on the transport fork.

---

# Revision 6 — 2026-08-16 (architecture Step 4)

### D-029 · Transport — embedded Ktor server, mDNS, websockets — **decided**
Ktor 3.5.1 on the host; peers over websockets; mDNS discovery is the one platform piece behind `expect`/`actual`. **All multiplatform Kotlin, so transport joins the shared column** — a direct dividend of the stack reversal, replacing MultipeerConnectivity which failed with it. No-wifi fallback is **manual Personal Hotspot**, documented in the play manual, since iOS will not let an app toggle it.

**Gotcha caught:** first local-network discovery raises the **iOS Local Network permission modal**. At arming, in a dark room, on top of the retro interface, that is the same failure as the NFC system sheet. **Warm it during setup, in the light.**

### D-030 · Authority — host device, no migration — **decided, risks accepted**
Migration deferred to v2. Accepted: single point of failure (the *downed device is a game state* rule covers **player** devices; host process exit is a separate, now-accepted failure) · thermal throttling on a device also rendering, scanning, and sensing · untestable below the full eight-phone rig.

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

**Unanswered and recorded:** every item in this architecture is load-bearing and nothing is cuttable — which is what a project that fails of calendar looks like from the inside. Revisit at the first honest schedule checkpoint.

---

# Revision 7 — 2026-08-16 (architecture Step 5)

### D-034 · Errors are silent to the *player*, loud to the *authority* — **refined**
The original rule was "silent on clients, always," which is right as a leak rule and dangerous as a robustness rule. **A failed BLE stack makes a living player invisible on the occupancy map** — a substantial advantage, delivered silently, that nobody including them can detect. Clients report failures as events; the authority knows.

**Unchanged:** no dialogs, no toasts, no unexpected screen state, and **the lamp never changes as a side effect of an error** — light is game state, so an error that dims or blanks the screen broadcasts a false game signal to the whole room.

### D-035 · The house notice — **new mechanic** (closes the silent-degradation gap)
**The house reports device degradation to everyone, at the meeting**, in its administrative register: *"NOTICE: Occupant Marcus was unreachable 21:04–21:07. Occupancy data for this interval is incomplete."* Radio failure and backgrounding trigger it identically.

**Delivered at meetings, not in real time** — that is where aggregate evidence already arrives, it keeps the house from chattering mid-round (preserving *the house speaks last*), and it lands the correction where people are arguing from bad data.

**Needs no anti-exploit.** Backgrounding deliberately to manufacture a connectivity excuse backfires — being named off-network means you generated no check-ins either. *"The system could not see you for three minutes"* is an accusation, not an alibi. **Self-punishing.**

**It removes the host as the single point of social responsibility**, and it makes the map's deliberate unreliability diegetic in writing.

### D-036 · A crashed client looks exactly like a revocation — **recorded as unfixable**
Its lamp goes dark, in a dark house, which is precisely what revocation looks like — to that player and to anyone watching. They cannot speak; sitting down tells the room something false; standing there with a flat phone reads as revoked anyway. **Mitigation is speed only** — relaunch fast, rejoin from the recording. **Written down as unfixable so nobody spends a week on it.**

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
`task` appears in every coroutine API and process management borrows other banned words. Run the lint over `platform` and it gets suppressed forty times a week and then switched off. The three game-facing modules are the ones emitting user-visible strings anyway.

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
**An amber with no trigger is a decoration, not a deferral.** The objection has gone unanswered through five party rounds and this project has failed of calendar twice.

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
| `WM:someone AND WM:home` | **0** — all classes, live and lapsed |
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

**Pillar: access is revoked; nobody is eliminated.** The design settled that early, but roughly forty sentences across the GDD, epics, architecture, decision log, project context and one Kotlin comment were drafted before it and still used the register of the retired design record. They now read in the game's own terms — **revoke**, **revoked player**, **target**, **revocation**.

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

This clarifies why the house *does* announce a failed radio at the next meeting. A radio failure is
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

*Retired by D-204 (2026-11-13): the strip moved under the Island, so nothing sits beside the pill.*

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
and the whole story held: seated as a stranger, frames attributed, stopped mid-connection by
`devicectl`, relaunched with wiped memory, and resumed as THAT seat. The locked round refused a
stranger terminally, with a frame. Everything below is what the hardware taught that the
loopback could not.*

### D-094 · **Resume needs WHERE as well as WHO — decided**

The seat token answers *who this phone is*. Nothing answered *where the house is*. The stopped
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
meeting area. The accepted risk, named: a ghost whose phone failed stalls the gate until 0.8's
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
to catch the sweep inside the band. Ping-pong rather than wrap: there is no defunct return stroke to
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
runs at any point in the round.** The house does not quietly finish a defunct player's work.

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

**This supersedes story 4.10's couch blind zone, and the *mapping blind zone* row in the marker
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
| `epics.md` story 4.10, and the *Couch / meeting area* row of `gdd.md`'s marker taxonomy | the meeting area as a **mapping blind zone** producing no occupancy data | D-155: no such zone; ghosts are filtered by seat state and the room is ordinary |
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

## Revision 34 — the scan that closes itself, and the panel that is gone

*Decided 2026-08-22, daytime, with Vadmanuel. A working session rather than a trace: the scan
gesture is corrected at the root, the haptic alphabet grows a word for **no**, a whole surface is
deleted, and the round gains a unit of time it has been describing without naming. Six escalations
close — E-D2-1, E-D2-2, E-D2-4, E-L5-6, E-S2-3 and the harness question L6 raised. The written
sites each ruling touches are swept in the same pass.*

### D-159 · **The scan is a tap, a turn, and a door that opens by itself — corrected**

**Press-and-hold-then-release is superseded.** The scan is: **tap SCAN → turn the phone so the lamp
faces the code → the subroutine opens on acquire, by itself.** A **ten-second window** counts down
from the tap; **timing out closes the scanner, with a double vibrate** (D-160). Nothing is spent by
a timeout — tap again.

**The reason is geometry, and it was always there.** The front camera sits on the same face as the
lamp, so the light that lets a phone read a printed code in a dark house is on the side pointed *at
the marker* — which means **the player cannot see their own screen while scanning.** A hold-and-
release gesture asks the player to decide when the read succeeded, on evidence that is facing a wall
three inches from their hand. Auto-open is not a convenience: **it is the only way a scan can end in
the player's favour**, because the surface that would say *got it* is the surface doing the reading.

The **ten-second window is the ported one and was always right** — the design's final number, and
the KDoc that had it at seven was corrected on 2026-08-19 (`3319bfe`). What changes here is only
what the window is *counting down to*: it now bounds an unattended read rather than a held button.

Superseded sites, swept below: `gdd.md`'s M1 mechanic, the *Scan a marker* control row, the
first-sixty-seconds trace, the Resident's cycle, the battery note, the NFC-sheet risk row, the E5
summary, and epics stories 5.1, 5.2 and 5.12.

### D-160 · **One vibrate is yes, two is no, and a call rings until answered — decided**

The haptic alphabet had one word — *something arrived* (D-102) — and a reserved set of five long
buzzes (D-135). **It gains two more, by owner decision:**

- **ONE vibrate = yes.** Confirm, accept, acquired, graded.
- **TWO vibrates = no.** Refuse, reject, expired, declined.
- **A phone call vibrates LONG–pause–LONG, repeating indefinitely until answered.**

**D-135 is amended, not replaced.** Its set was five single long buzzes; the incoming call leaves
that set and becomes **the repeating pattern** instead. The other four are unchanged. A call is the
one event in the game the house *insists* on — a meeting has been called and the round stops until
everybody has it — and a signal that fires once and gives up is a signal a player in another room,
mid-subroutine, with the phone face-down on a shelf, will miss. **Everything else in the game says a
thing once. The call is the exception because it is not a notification; it is a summons.**

This is the vocabulary D-159 needs: a scan that times out with nobody looking at the screen has to
say so through the one channel that reaches a player whose eyes are elsewhere, and *no* now has a
word.

**D-102's identical-for-every-player rule governs all of it**, unchanged. One vibrate means yes on
an Insider's phone and on a Resident's, at the same length, for the same reasons.

### D-161 · **No seat until the device is ready — the warming rule covers every permission**

**A player cannot join a lobby until device setup is complete**, and complete means **the whole
Perms set, camera included.** The gate is at setup, in the light, before anybody sits down.

E2's story 2.10 already warmed Local Network for exactly this reason. **The generalisation is the
ruling:** the argument was never about mDNS, it was that *iOS asks once, ever, on whichever screen
first needs the thing* — and in this game the screen that first needs the camera is a player alone
in a dark corridor, where a system modal is **both a light nobody authored (rule 5) and a tell.**
Warming one permission and leaving three to ambush the round is the same bug with a smaller blast
radius.

**Making it a gate rather than a prompt is the other half.** A warmed permission that was declined
leaves a phone that looks like it is working and is not — E-D2-1's whole finding: *Don't Allow*
leaves the scan screen lit, instructive, counting down and registering nothing. **A refusal at setup
is recoverable in ninety seconds; the same refusal at arming costs the round.** So the lobby will
not seat a device that cannot do the job, and the host is never handed a party that will fail in the
dark.

**A permission that flips mid-round is a different event and gets a different answer:** it surfaces
as a **house notice** at the next meeting, in the administrative register `gdd.md` already uses for
a device that went unreachable. Same shape, same words, same reason — silent degradation is a real
advantage, and *the system could not see you for three minutes* is the worst sentence that can be
read aloud about you.

**E-D2-1 and E-D2-2 are closed.** E-D3-4 — an uninstall silently resetting the Local Network grant —
is this ruling met in the wild, and it arrived in the one place it is survivable: on setup, in the
light.

### D-162 · **A node is a marker, so containment opens by scanning it — E-L5-6 ratified**

**Scanning the node's marker opens containment.** This is ratified as the obvious reading
rather than decided as new policy: **a node *is* a marker** — D-148 says an Egress node is two
ordinary registered markers the house picks at fire time — and everything in this house that a
marker offers, it offers to a scan.

E-L5-6 was right that nothing in the GDD said so, and right that the symptom was bad: a player who
walks to the correct room and taps is told `held=false` with no reason. **The fix is that there is
nothing to tap** — you scan, the same as you do at every other card in the building, and the reason
you are at the card is D-136: a scan is the only way the house can know where anybody actually is.
Containment is a race across a house between two people, and it ends the way every other errand in
this game ends, by putting a phone in front of a piece of paper.

### D-163 · **One vote clock, one number: 45 seconds — decided**

**The couch vote clock is 45 seconds**, the same as the living's. The 60 was **a drawing artifact**,
and `Countdown.kt` has been documenting its own provenance since it was written: the ghost's 60 was
kept because it reproduced a mockup frame — `VOTING ENDS IN 0:24` over twelve lit segments of
thirty — and moving it to 45 redraws that picture.

**Redraw the picture.** D-145 gave the out a live vote precisely so they watch the same event the
living are in; two clocks means the couch's bar empties at a different rate from the bar in the
hallway, for no reason a player could ever be told, in a game where the couch and the living room
are twelve feet apart and can see each other. **One clock, one number, host-changeable in lobby
settings (D-117).**

`Countdown.kt`'s `GhostMeeting` entry is a **code site owed a sweep**, listed below. It is deliberate
today and stops being deliberate now.

### D-164 · **The Array Wipe circuit is a line on the work order — decided**

**The assigned mix's *1 circuit* IS the Array Wipe circuit**, and it is **a real work-order entry**
like every other. It is not a house event, not a side quest, and not something a Resident discovers
by walking into it.

The consequence is a wire consequence and it belongs to **W1**: **the order format must be able to
carry a circuit** — three markers and one carried state (D-123's *a card is a place*), where an
ordinary entry carries one. An order line that can only name a single marker cannot express the
entry the design has been costing at ~160s in the per-Resident load since the mix was written.

**This is why the widening is authorized rather than argued** (D-171). The circuit was never a
missing feature; it was a feature the format could not say.

### D-165 · **An early Drift tap spends the attempt and fails — decided, superseding E-S2-3**

**A tap before the buzz is handed over, graded REJECTED, and the attempt is gone.** To retry you
walk back and re-scan.

S2 took the dark-room reading — a control that swallows a tap in silence is indistinguishable from a
flat phone — and echoed the early tap instead of sending it. **The reading was reasonable and the
conclusion is the opposite one**, because the game already has an answer to *did my tap land*, and
it is not silence and it is not an echo: **it is a grade.** D-109 grades every entry for real, for
both roles, in identical words. An early tap is an entry. It is a wrong one. **The house says so.**

**D-110's rule does the rest:** one attempt per scan, and the walk back is the cost. An early tap
that costs nothing is a free probe of the house's `now` — tap, learn you were early, tap again — and
D-140 built Drift specifically as object permanence *against* a house-authored NOW. A mechanic whose
entire content is *when* cannot let you ask *when is it* for free.

**And it is honest about the thing that matters.** The player is told they were early, at the moment
they were early, by the same grader that tells them they were on time. Nothing about the timing
model is hidden; only the retry is expensive.

### D-166 · **The role-swap diff stops at the first ending — the harness narrowing is accepted**

**L6's narrowing is accepted:** the differential harness diffs the two client transcripts **up to the
first round-ending in either run**, and stops there.

**When the round ends is not a guarded secret.** It is the loudest public fact in the game — every
lamp in the house comes up, every phone rings or goes to an ending screen, and D-153 already rules
that the ending screens print no total anybody could arithmetic backwards. A harness that keeps
diffing past that point is comparing two runs that are no longer the same round, and its
disagreements are about *which ending happened*, which is exactly the thing the role swap was
supposed to change.

**The guarantee that matters is untouched.** The role-swap diff was never the whole instrument —
the architecture is explicit that a symmetric leak yields two identical transcripts and a clean
diff, which is why the **per-message schema check at the emit boundary** exists as an independent
second test. **That one runs to the end of both runs**, unnarrowed. Two tests, two failure modes,
neither covering the other, and only one of them was narrowed.

### D-167 · **Four numbers ratified, two reclassified — decided**

Ratified as the design's, not as a builder's guess:

| number | value | note |
|---|---|---|
| **simulation tick** | **20 Hz** | the authority's step rate |
| **Egress cooldown** | **120 s default, host-changeable** | one clock for the whole house (D-151); the lobby already owns the Egress *timer* as a setting (F-009), and the cooldown joins it |
| **Sync Pulse beat** | **1.5 s** | four taps on the beat (A-11) is therefore ~6 s of tapping after the run across the house |
| **Sync Pulse tap window** | **400 ms** | the tolerance around each beat |

**Two are reclassified from invented to derived**, which changes nothing about their values and
everything about whether they are open questions:

- **The ~2 s scheduling lead was already the design's** (`gdd.md:1261`), written down with its
  argument — broadcast *the event begins at T*, never *go* — and used in three places. It is not a
  number somebody picked while building.
- **The printed id's length is D-069's payload arithmetic.** D-069 ruled that an id exists so a
  stale card is recognisable, and the length falls out of the payload rather than out of taste.

**The distinction is worth the paragraph.** A number in the *invented* column is a standing question
that somebody has to answer before ship; a number in the *derived* column already has its answer
written above it. Two entries were in the wrong column and were going to be re-litigated.

### D-168 · **The Status panel is deleted — decided**

**There is no Status panel. The lamp dial does not exist as a concept.** No corner long-press, no
summoned surface, no dial.

Revision 33 flagged this and refused to guess: D-142 moved *arming* to springboard page 2 and said
nothing about whether a panel survived for the lamp. **It does not, because the lamp was the last
thing in it and the lamp is not the player's.** The house sets the level — that is what the whole
light-signature system is (D-112, D-113): a subroutine lights you up or blacks you out according to
what the house decided it costs, and a dial that let a player override that would be a dimmer switch
on the game's central tension.

**What the panel held goes to surfaces that already exist.** Abilities left for page 2 (D-142).
**Carry state moves to the work-order widget**, which is where it belongs: carrying blocks every
other entry, so the state and the list it blocks are now one surface, and **the widget says so on
its face** rather than making the player hold the rule in their head in a dark room. The role parity
argument survives intact on page 2, which is identical at rest for both roles.

**Four fossils swept:** `gdd.md`'s *Open the Status panel* and *Adjust the lamp* control rows, the
*identical for both roles* paragraph, and **the tier table's *top and bottom half***, which was
describing the panel's geometry two hundred lines before anybody noticed. Also `gdd.md`'s E3 and
reference-artifact rows, `epics.md` story 3.8, story 3.9's *opening the panel is cheap*, and E3's
acceptance line.

### D-169 · **Card ids are unique across prints, and a mislaid card is simply unregistered — settled**

E-D2-4 and D-086 are both ratified, and together they close the card-identity question:

- **Ids are unique across prints.** A reprint never reissues an id. The **printed four-of-run-tag,
  three-of-card split stands as the implementation** of that uniqueness — adopted from the build
  rather than replaced, because it is correct and because **paper cannot be patched** (D-120).
- **Registration dedupes on shape.** Two live cards may never share a shape — D-086, ratified in
  revision 18, and the refusal lands on the host in the light with 42 registrable shapes to choose
  from.
- **A scan validates the id against the map.** A mislaid card's id is **no longer registered**, so it
  is refused as *unregistered* — which is the whole point of D-069's id and needs no separate
  staleness concept. There is no *this card is old* state; there is only *this house does not know
  this card*.

The three fit together only because ids are never renumbered (D-070, D-085): the id is a permanent
name, the shape is what the house resolves by, and the map is what makes either mean anything.

### D-170 · **SHIFT — the work interval between meetings — new vocabulary**

**`Round` keeps its code meaning, unchanged: lobby to victory, one word.** The vocabulary gains a
second unit of time beneath it:

> **A SHIFT is the work interval between meetings. The first shift runs from lights-out to the first
> meeting. A meeting ends the shift it interrupts.**

The game has been describing this interval since it was designed and has had no name for it, which
is why sentences about it kept reaching for *round* and coming out wrong. With the word, the
derived phrasings say themselves:

- **cooldowns run during shifts and pause during meetings** (D-133's *no meeting during an Egress*
  is the same clock seen from the other side);
- **the living's meter freezes for the length of a shift**;
- **carry state persists across shifts** — F-014, closed in revision 23, is exactly this sentence
  and could not be written before.

**Added to the enforced vocabulary** in `project-context.md`, `game-architecture.md`'s list and
`CLAUDE.md`. It needs no lint entry: the lint holds rejected stems, and `shift` is a word being
*allowed*, not one being replaced.

### D-171 · **W1 is authorized — six wire widenings**

Six widenings, authorized as a set:

| # | widening | why |
|---|---|---|
| 1 | **order line carries marker + room** | an entry the player can walk to has to name where |
| 2 | **the meter to clients** | revision 19's `PanelState` total, reaching the surfaces that draw it |
| 3 | **vote window into arming** | one clock, one number (D-163), issued where every other round setting is |
| 4 | **seat → name for the reveal** | D-115 put names on the wire; the reveal is where they are read |
| 5 | **marker → room map** | the spectator map (D-136) and containment (D-162) both resolve through it |
| 6 | **the circuit in the order format** | D-164 — the assigned *1 circuit* has to be expressible |

**Each is a widening of an existing shape, not a new `Effect`**, and every one of them lands in the
redaction schema in the same change that adds it — **the schema fails closed, so a field with no
entry ships to nobody** and the widening would be silently inert. That is rule 2 doing its job, and
it is the reason six of these can be authorized at once rather than one at a time.

### Swept in this pass

Sixteen sites. No ruling is made here that is not made above.

| site | superseded by | what it now says |
|---|---|---|
| `gdd.md` — M1, *press and hold to scan … release, subroutine opens* | D-159 | tap SCAN, turn the lamp onto the code, opens on acquire; ten-second window; double vibrate on timeout |
| `gdd.md` — *one hold-button drives both paths* | D-159 | one SCAN button |
| `gdd.md` — *Scan a marker* control row | D-159 | the tap, the turn, the window, and why the tap is the only input the player sees themselves make |
| `gdd.md` — the first-sixty-seconds trace, *holds to scan* | D-159 | taps SCAN and turns the lamp onto the code |
| `gdd.md` — a Resident's cycle, *hold to scan* | D-159 | same |
| `gdd.md` — battery, *the scan is a ~0.5s acquire* | D-159 | a ten-second window that usually closes on acquire |
| `gdd.md` — NFC risk row, *the one-hold-button design* | D-159 | the one-SCAN-button design |
| `gdd.md` — E5 summary row, *hold-to-scan* | D-159 | tap-to-scan with the window and auto-open |
| `gdd.md` — *Open the Status panel* and *Adjust the lamp* control rows | D-168 | deleted; replaced by a carry-state row that says there is no dial |
| `gdd.md` — *the Status panel is identical for both roles* | D-168, D-142 | there is no panel; parity lives on page 2 |
| `gdd.md` — tier table, *two big targets, top and bottom half* | D-168, D-151 | page 2, and the Egress clock named as house-wide |
| `gdd.md` — E3 row and the device-mockup reference row | D-168 | the work-order widget; the mockup's dial marked a fossil |
| `gdd.md` — the house-notice register | D-161 | mid-round permission flips land here, in the same words |
| `epics.md` story 2.10 — *warm the Local Network permission* | D-161 | every permission, and the lobby gates on it |
| `epics.md` stories 3.8, 3.9, and E3's acceptance | D-168, D-141 | the work-order widget; the two-second hold; page 2 at rest |
| `epics.md` stories 5.1, 5.2, 5.12 | D-159 | the tap, the window, the auto-open, the SCAN button |
| `epics.md` — acceptance *±50 ms* | D-019 | ±150 ms |
| `epics.md` story 7.15 — *graceful collapse when a chain cannot be staffed* | D-146 | **deleted.** D-146 rules the mechanism out by name: nothing auto-satisfies, a Revoke strands a chain, and over-provisioning is the whole rescue |

**Closed as correct-on-purpose, with no edit:** the standing question about **the empty lower halves
of several subroutine layouts.** They are not an oversight. `gdd.md`'s **minimise lit pixel *area*,
not just brightness** rule makes unlit screen the default state of every surface in this game, and a
layout that fills its lower half to look finished is a lantern in a dark house. **A line in the log
is the whole ruling; the layouts are already right.**

### Where the code and the epics are still wrong

| site | the superseded phrasing | what replaces it |
|---|---|---|
| `Countdown.kt` — `ScreenId.GhostMeeting to 60`, and the KDoc explaining why it is deliberate | the ghost's separate 60 s vote clock | D-163: 45, the same as the living's. Client work, queued — and the KDoc's *left alone deliberately* has to go with it |
| everything carried into this revision from revision 33's list | — | unchanged and still owed: `ScreensOut.kt`'s `14 / 32` rows (D-153), `epics.md` story 4.10 and the marker-taxonomy blind zone row (D-155), and three further orphan auto-satisfaction sites (D-146) |

### Open — awaiting a ruling

The four escalations the evening device unit raised on 2026-08-21, recorded here so they are queued
rather than remembered:

> **Two of these four are now ruled — see revision 35 below: E-D3-1 by D-172, E-D3-2 by D-173.**
> They are left standing here as they were raised, because what they said about the tree on the
> night is the reason the rulings say what they say.

- **E-D3-1 · A stored seat token is refused in silence.** A phone that played a round presents its
  token to a host that never issued one; the resume is refused, correctly (D-087), and **the lobby
  says nothing to anybody** — not the player, not the host. `LobbyOverTransport.onPhase` acts on
  `Seated` and on nothing else, so `Dismissed` reaches no log, no screen and no host. **Rule 6
  exactly inverted.** It will happen on the second evening of every household that plays twice. The
  refusal is right; the silence is the bug — and **what a refused client is told is a decision about
  what a client receives**, which is why it is a ruling and not a one-line fix.
- **E-D3-2 · Two homes with one name: the advertisement fails outright and the host is not told.**
  Bonjour returns `-72001` (collision), the second house is **never published at all**, and its host
  sits in a one-seat lobby with no reason given. The nearby list already dedupes by address and port
  and deliberately **not** by name, with the reason at the call site — so the screen is built for a
  duplicate name the radio cannot publish. Two halves want rulings: whether the advertisement
  carries a disambiguator (Bonjour will auto-rename if asked), and **what the host's screen says
  when its own house could not go on the air.**
- **E-D3-3 · There is no effect push, so five seats armed one phone.** `LIGHTS OUT` passed the gate
  on the house's own counts and then `ARMED THIS PHONE ONLY`. `FlowModel.lightsOut` is presentation
  only and says so; `armingFor` has no production caller. **There is no host→client effect push for
  any effect** — L2 and L3 left the same seam — so the arming draws and the opening message cannot
  reach a second seat at any party size. Recorded as the state of the tree rather than as a
  shortfall of that run.
- **E-D3-4 · An uninstall resets Local Network, and mDNS then fails with no symptom whatsoever.**
  Recorded rather than open: it is **D-161 met in the wild**, and it arrived on setup, in the light,
  which is the one place it is survivable. Two taps from a person cleared it and the advertisement
  went out the moment they happened. The warning is now evidenced rather than predicted.

---

## Revision 35 — what a phone does when the house says no

**Two rulings, both about a refusal that reached nobody**, and they go in opposite directions on
purpose. The seat token is handled where the player never sees it; the advertisement is handed to
the host in the light. The difference is not taste: **one of them is something a person can act on
and the other is not.**

### D-172 · **A stale seat token is dropped and the phone joins fresh — E-D3-1 closed, seamless**

A phone that played a round stores its seat token and presents it to the next house it meets. A
house that never issued that token refuses it, and **the refusal stays right** — D-087's property is
that a resume asks for *that* seat, and a house that honoured a token it never minted would be
handing out somebody else's chair. What was wrong was everything after the refusal: the session went
terminal, `LobbyOverTransport` acted on `Seated` and nothing else, and the result was a lobby that
never filled, forever, on both phones and with no reason given anywhere. **It happens on the second
evening of every household that plays twice.**

**Ruled: the client drops the stale token and joins fresh, by itself, and the player is shown
nothing.** No message, no refusal, no state the eye can catch — the lobby fills the way it does for
a phone that had never played. The reasoning is the sorting rule D-125 already gives: *clamp what
players cannot perceive, guide what they can.* A seat token is not a thing a player has, knows about,
or can do anything about. Telling them their token was refused is telling them a fact with no action
attached to it, in a dark room, in a game whose whole discipline is that the screen says only what
it must.

**Silent to the player is not silent to the authority.** Both ends record it: the house logs the
refused connection with its reason, and the phone logs that it was refused and that it started over.
**The token itself is never printed at either end** — the reason is the record, the token is the
secret, and a leak detector that writes down what it caught is the shape D3 already refused once.

Three things this ruling does *not* do:

- **It does not weaken `ClientSession`.** That class still presents its token or nothing, and still
  goes terminal on a refusal. The starting-over is a decision made one layer above it by the thing
  that owns the lobby, which is where a policy about what this app does belongs.
- **It does not retry.** Only `UnknownToken` starts over. A locked round and a full house refuse a
  stranger for the same reason they refused the token, so a phone that started over on those would
  be knocking at a door it has been told is shut, forever.
- **It happens once.** The second attempt is a Hello, and a Hello cannot be refused for holding an
  unknown token — but the fuse is written down rather than argued from somebody else's protocol.

### D-173 · **A home that could not go on the air refuses HOST WITH THIS HOME — E-D3-2 closed, loudly**

Bonjour returned `-72001` on the night: a second home called THE BUNGALOW was **never published at
all**, and the phone that ran it sat in a one-seat lobby while every other phone searched for a home
that was not there.

**Ruled: the refusal reaches the host, at hosting time, on the screen they are standing on.** HOST
WITH THIS HOME becomes a refusable control — the shape SAVE HOME and HAND IT OVER already have — and
the lobby opens only when the home is really on the air. The two failures are named apart because
the host acts on them differently:

| what happened | what the host reads |
|---|---|
| the name is already on the air here | `ANOTHER HOME HERE IS ALREADY CALLED THE BUNGALOW` |
| the advertisement was refused for anything else | `THIS HOME DID NOT GO ON THE AIR` |

**This is the opposite call to D-172 and for the reason D-172 is what it is.** A duplicate home name
is the most actionable refusal in the app: the fix is a rename, the host is holding the phone in a
lit room with the party in front of them, and the RENAME row is on the same screen as the refusal.
Rule 6 is about a round in a dark house; **hosting happens in the light, before there is a round**,
and D-126 already puts every gate where the host is alone with the setup.

**No disambiguator.** Bonjour will auto-rename on request, and the second half of E-D3-2 asked
whether it should. It should not: a house that quietly became `THE BUNGALOW (2)` would be a home
whose name on five other phones is not the name its host typed, in a game where *tap the row with
your house's name on it* is the entire joining ceremony. The nearby list still dedupes by address
and port and never by name (two households may legitimately both be THE BUNGALOW) — what changes is
that the *second* one is now told it never went up, instead of finding out from an empty lobby.

**The bug is legible in D3's own log, one line apart, and that is the evidence this ruling turns
on.** In `host.log` from 2026-08-21, `[lobby] ADVERTISED AS 'THE BUNGALOW'` arrives **three lines
after** `[lobby-bench] SEATED — 1 JOINED`. The host was standing in its own lobby before the home was
on the air — so the window in which a refusal could still have been acted on had already closed, on
every run, whether or not the advertisement was going to fail. Under this ruling the order is
reversed: the restored evening of 2026-08-22 logs `ADVERTISED AS` **before** the host's own seat, and
that ordering is the ruling in one line of evidence.

**A refused home also takes the server back down.** A bound port under a home nobody can find is not
a house, and leaving it up would make the next attempt — under a better name — fail for a second,
unrelated reason.

### ⚠️ Built under D-173 but **not ruled**: the ten seconds

**The owner ruled two outcomes and the OS has three.** With the Local Network grant gone, iOS
publishes nothing and reports nothing — no error, no prompt, no callback of any kind (E-D3-4). A
build that waited on the two ruled callbacks would have made HOST WITH THIS HOME a control that does
nothing when pressed, which is a worse failure than the one D-173 fixes and would have shipped in the
name of fixing it. So an unanswered advertisement is refused after **ten seconds**, and exactly one
of three outcomes always reaches the host.

**Nobody ruled ten.** It is an engineering choice made to keep the ruling honest, not a decision
handed down, and it is the one number on this page with no authority behind it. Bonjour answers in
well under a second on a working stack — D3's logs have the publish landing between two lobby lines
— so it is a bound on a callback that never comes rather than a timeout on a slow network, and no
rule reads it and it stamps into no recording. **It wants the owner's eye**, on two questions: is ten
seconds the right wait for a host standing in a lit room with a party in front of them, and is
`THIS HOME DID NOT GO ON THE AIR` the right thing to say to somebody whose real problem is a
permission they can still grant.

**E-D3-3 stays open.** There is still no host→client effect push, and nothing here changes that.

---

## Revision 36 — the phone that is not an iPhone

*Ruled 2026-08-23, with Vadmanuel. One entry, and it is about a platform rather than a mechanic:
what this design has been quietly assuming about the hardware in every player's hand, and what
happens to each assumption when one of those hands is holding something else. **No rule of the game
changes here.** What changes is the list of things a phone has to be able to do before it is allowed
to play.*

### D-174 · **Android is a calibration problem, not a redesign — and v1 may ship iPhone-only**

**Ruled: rather than support Android broadly, the project may limit v1 to iPhone until specific
Android phones have been researched and individually whitelisted.** The whitelist is an allowlist of
*named models*, gated at setup. Broad Android support is not a v1 target, and **the Android milestone
is conditional on that research rather than scheduled ahead of it.**

**The word carrying the ruling is *calibration*.** Not one of the six risks below asks the game to be
a different game on Android, and the stack reversal is why: **one rendering engine, so screen parity
is structural rather than maintained**, and **a server-authoritative core, so every rule that decides
anything runs on the host in common Kotlin and cannot diverge.** What is left is the thin `:platform`
`actual` — and **every risk on this page lives inside one of them.** That is the good news and it is
the reason this ruling is a *may* and not a *never*: **nothing has to be redesigned; six things have
to be measured on named hardware.**

#### 1 · Haptics is a gameplay channel, and the Android motor zoo is not one instrument

The design's own sentence is that haptics is **"the only fully private channel available, and no game
in this space uses it"** (`gdd.md:551`). It is load-bearing in four places at once: **Sniff's two
counted groups** (D-137), **Handshake's rhythm**, **the Sync Pulse beat** at 1.5 s inside a 400 ms
window (D-167), and now **one vibrate yes, two vibrates no, and a call that rings until answered**
(D-160). Two of the roster's three *dark* Subroutines are haptic-only, and **Sniff is the only
*short dark*** — the cell the design most needs filled (`gdd.md:580`). On a motor that cannot render
two groups as distinguishably unequal, or cannot start and stop cleanly enough for a rhythm to be
echoed back, **those two cells empty and the light-signature axis loses its cheap end.**

**The worse one is only visible across devices, and D-102 is where it hides.** D-102 rules the buzz
**identical for every player** — same pattern, same duration, an Insider's own Revoke included —
because anything else is an audible tell in a silent house. **That rule silently assumes identical
hardware.** A Taptic Engine and a rotary motor do not sound alike and do not feel alike, and in a
house under enforced silence **a distinguishable motor turns the buzz into device identification**:
the pattern still says nothing about *what* happened, and the timbre now says *whose phone it
happened on*. D-102 was written to stop a buzz from naming an event. **Mixed hardware lets it name a
person.** A private channel is only private if the hardware is.

#### 2 · The contact handshake is a distance measurement, and RSSI is not a distance

`gdd.md:547` is unusually blunt for this document: **the radius is a hard constraint, not a tuning
value.** D-008 stopped the through-wall detector by name and D-009 built the contact gate in its
place. **Per-chipset transmit-power calibration and antenna placement mean a threshold tuned
iPhone↔iPhone is a different physical distance iPhone↔Android** — and asymmetric between the two
directions, since each side reads the other's radio.

**Both failure modes are bad and they point opposite ways.** Too tight and **Revoke stops landing,
silently** — the contact primitive's whole design is that a non-outcome produces *"nothing at all,
silently, with no feedback on either device"* (`gdd.md:543`), so a mis-tuned gate is
indistinguishable from an Insider who never tried, on both phones, forever. Too loose and **the
deactivation report is a through-wall detector for Revoked players again**, which ends *you can walk
past a Revoked player and never notice* — the sentence D-008 exists to protect. **This is the one
risk of the six where calibration means a number per model *pair*, not a number per model.**

#### 3 · The dark screens assume OLED, and that assumption is load-bearing

**"On OLED a black pixel emits nothing"** (`gdd.md:722`) is not an art note. It is the entire reason
*minimise lit pixel **area**, not just brightness* means anything, and it is what makes the dimmest
step's **≤2-nit full-screen floor** (`gdd.md:1254`) reachable at all. On an LCD a black screen is a
backlight behind a closed shutter — **it glows**, and the floor may simply be unreachable.

**The concrete failure is a Revoked player's phone on the floor.** A flat phone that emits nothing is
how a Revoked player stops being a light source in a dark house; on a lit panel they are **a soft
lamp visible across a dark room**, and the map of who is out draws itself for anybody standing in the
doorway. The same panel makes every *dark* Subroutine — Handshake's near-black screen most of all — a
beacon, which deletes concealment from the axis it is half of.

**This is the real Android light question, and it is not the one this log asked.** OI-7 asked about
the torch and **D-024 closed it correctly**: the torch is not in the light ladder, and *every light
event in the game is a screen event*. **Precisely because every light event is a screen event, the
panel — not the LED — is where Android can actually take the light ladder away.** D-024 stands; it
answered a smaller question than the one that was underneath it.

#### 4 · Discovery is the front door, and NSD is the flakier half of it

mDNS is the discovery mechanism (D-025), and revision 35 has just spent an entire ruling on what a
host reads when a home **could not go on the air** (D-173). Android's NSD carries a long-documented
history of registration and resolution flakiness, and OEM handling of Wi-Fi multicast varies by
device — so the failure D-173 rules on is **more likely** on the hardware this ruling is about, not
less.

**The front door is the one place a failure costs the evening rather than the round**, which is also
why it is the most survivable of the six: **D-173 already puts every discovery refusal in front of
the host, at hosting time, in a lit room, with RENAME on the same screen** — and the ten-second bound
built under it (ruled by nobody, and flagged as such) already assumes a stack that may answer with
nothing at all.

#### 5 · OEM process attackers versus a round-long lifetime

A round is ~25 minutes of continuous BLE advertising *and* scanning, a duty-cycled camera session,
motion at 100 Hz and a screen that must not sleep. **Aggressive OEM battery managers freeze or stop
processes on exactly that profile.**

**The posture absorbs most of it, and by accident rather than by foresight.** The phone *is* the
lamp, and the house rule is **don't conceal your phone — held out, screen up** (`gdd.md:677`), so the
app is foregrounded, screen-on, in a hand, for the whole shift. **That is the least killable state an
Android app can be in.**

**The residual risk is the pocketed phone**, and the design has exactly one class of player who
pockets it: **a Revoked player walking to the couch.** An app frozen there stops advertising, and a
Revoke that was performed reads to the house as a Revoke that never was. **It degrades loudly rather
than silently**, because D-161's mid-round register already covers it: a device that went unreachable
surfaces as a **house notice** at the next meeting, in the administrative words the house already
uses.

#### 6 · Vibrator scheduling jitter versus the coordinated flip

**Largely absorbed before it was raised.** The coordinated flip is scheduled, never commanded —
*the event begins at T*, broadcast ~2 s ahead, a lead D-167 reclassified as the design's own rather
than a builder's guess — so what remains is local scheduling accuracy against **±150 ms** for a flip
that reads as one event and **400 ms** around each Sync Pulse beat. **The 400 ms is wide on purpose**,
and *no twitch timing* is constraint 4 on every Subroutine in the roster. **Recorded as the smallest
of the six**, and recorded anyway, because a motor that is late is a different bug from a motor that
is wrong.

#### The structural mitigations — and why this ruling is a *may*

- **The server-authoritative core keeps all game logic safe.** No rule, no grade, no cooldown and no
  win condition ports. The redaction schema, the emit boundary and the three leak harnesses are
  common Kotlin and never see a platform type (the portability discipline: no `Date`, no BLE types,
  no UI types in the core).
- **Every risk above lives in a thin `:platform` `actual`** — haptics, BLE, panel, discovery,
  lifecycle. Six seams, each already declared once in common code, each already written twice.
- **The capability gate already has a home.** D-161 put a gate at device setup, in the light, before
  anybody sits down, and ruled that a lobby will not seat a device that cannot do the job. **A
  CAPABILITY gate belongs beside that permission gate** — amplitude-controlled vibrator, simultaneous
  BLE advertise and scan, panel type — refusing **ninety seconds from recoverable rather than at the
  cost of a round**, which is D-161's own arithmetic applied to hardware instead of consent.

**Why a whitelist rather than a capability floor.** Because two of the six cannot be asked about.
**A phone will not report its motor's timbre and will not report that its RSSI reads two decibels
hot** — those are measurements, not queries, and a gate can only read what the OS will answer.
**A researched allowlist of named models is a capability query that has already been run by a
person, on the hardware, once**, and the setup gate then reads a model name. The capability gate
covers what the OS *will* answer; the whitelist covers the rest.

### Where this lands in writing

| site | what it now says |
|---|---|
| `game-architecture.md` — *Open platform question created by this decision* | corrected: OI-7 is **closed** (D-024), and the section now says why the torch was the smaller question |
| `game-architecture.md` — new subsection, *Android is a calibration problem* | the six risks, the structural mitigations, and the whitelist |
| `game-architecture.md` — stack table, the **Android** row | the milestone is conditional (D-174), not merely undated |
| `game-architecture.md` — Technical Risks table | one row, pointing at the subsection |
| `game-architecture.md` — *Carried forward, unresolved by design* | Android scope carried as an open item with a research trigger |

**One citation corrected in passing:** the through-wall detector was stopped by **D-008**, and the
contact gate built by **D-009**. D-069 is the marker-card ruling and has nothing to do with the
contact radius.

---

## Revision 37 — the rulings the build was already obeying

*Recorded 2026-08-23. **Four of the six take no new decision.** They had reached the code — argued
on types, cited in KDoc, held by tests — and had never reached this log, which is the one place a
person goes to ask *why is it like that*. A ruling that lives only in the source is a ruling the
next reader re-litigates, and two of those four had already been re-derived once.*

*The last two are the ones revision 35 flagged and left open, now answered by the owner: **the ten
seconds are ratified** and **a refusal names the cause a person can fix**, not only the symptom.
The second changes a screen, and its wording is the builder's draft rather than the owner's words —
flagged as such below and on the constants themselves.*

### D-175 · **Tier 2 targets: a list, a list, and the map — recorded**

**Surge picks a person from a LIST. Isolate picks a marker from a LIST. Spoof picks a room from the
MAP.** *The glow of reading it is the deliberate price.*

**The price is the ruling, not a consequence of it.** Tier 1 arms in place on a page the player is
already standing on (D-142), because the Revoke and the Egress have no target to pick. Tier 2 makes
you stop, open something, and **read** — in a dark house, holding the only light in it. That is why
`gdd.md:306` says Tier 2 is never used while running and being harder to reach is a feature, and it
is why the pickers may not be improved: **nothing on any of the three screens may be quicker than
reading.** No search, no recent-targets shortcut, no repeat-last-target, and no tabs on Spoof's
floors. Every one of those buys back exactly what is being paid.

**The map is the third answer and it is the one with a cost.** A phone cannot draw a plan it was
never sent, so the home's plan rides the arming event and reaches a living Insider — the largest
single disclosure in the emit table, one class wide, and it must never be widened by one. The
alternative was worse in the direction this project cares about: a plan delivered per-floor or on
the fire that needs it would make *which pieces arrived* a second channel.

**Two consequences the build had to take and this log should hold.** The lists are **built for every
seat and narrowed by the allowlist**, never filtered by role — a role branch in the rules makes the
arming burst itself role-dependent and retires the differential harness for every future change as
well as the one that introduced it. And **a tap fires with no second confirm**: the deliberateness
was paid at the tile by two seconds of a finger (D-141), the design has one confirmation primitive
rather than two, and a row that reported what it did would be `gdd.md:344`'s revoke-detector rebuilt
as a list.

### D-176 · **The one sound in the game is the Surge, and the call is silent — recorded, and it is a reversal**

`gdd.md:400` reserved the single permitted phone-emitted sound for **the meeting ring**. That slot
now belongs to **Surge**, and the call makes no sound at all: it **vibrates**, LONG–pause–LONG,
repeating until answered (D-160).

**Surge's licence is that it is self-inflictable** (`gdd.md:391`, `:393`). An Insider can Surge
themselves at the cost of the pool's longest cooldown, so being audibly attacked is not evidence of
innocence — which is the same shape the ring's licence had (*it fires for everyone*) and the reason
the slot could move rather than simply close. `gdd.md:399` is untouched: **nothing that fires for a
subset ever makes a sound**, and this is not a precedent for a second member. The `Sound` type has
one, every `when` over it is exhaustive, and a second is a compile error until somebody has written
down which subset hears it and why that is not a role oracle.

**The ring's argument was true and was not the whole account.** It earned its place because at a
meeting call localization is already total — every lamp in the house has just gone to full white, so
a sound adds nothing the light had not already broadcast. What that reasoning leaves out is that
**a sound is heard by the room, and a room contains people who are not in it.** The two things the
ring was for are met by the two things that replaced it: the summons reaches a player who is not
looking at their phone because it **repeats until answered**, and it reaches a player two storeys up
because it is **in their hand**, which is where the house rule already requires the phone to be.
What is gone is the half nobody needed — a channel the whole building shares, in a game whose
discipline is that the only private channel is the one against your palm.

**Three written rows fall with it**, swept on 2026-08-23 and listed below: *not indefinitely* (the
old duration row worried about a phone still ringing two minutes later localizing whoever was slow —
nothing localizes anybody now), *must play regardless of the hardware silent switch*, and *volume:
full — this is the one moment the game wants to be loud.*

**No code path ever emitted ring audio**, which is worth recording because it is the reason this is a
sweep rather than a repair: `Effect.MeetingOpened` has only ever carried a `Haptic`, `Sound` has only
ever had `Surge` in it, and `platform`'s `Speaker` has no production call site at all. The documents
were the only place the ringtone still existed.

### D-177 · **NEW ROUND is the label — confirmed**

D-157 gave the ending screens a host-only control back to the lobby and left its **label** as copy
pending at build time. The word is **NEW ROUND**, confirmed as written.

**It takes no two-second hold**, which was the other half D-157 left open and is recorded here as the
build's reading standing unchallenged rather than as a second ruling. D-141's argument is about a
control **pressed in the dark**, where a thumb finds a tile by feel and a mis-tap cannot be taken
back. This is pressed in the light, in front of the whole party, on the one screen in the game where
nobody is hiding their display.

### D-178 · **The couch's Egress widget is empty until there is an Egress — decided**

**It shows `EGRESS . <type>` and the ghost's live percentage while an Egress runs. Otherwise it is
dim and empty: no percentage, no type, and not the word.**

The couch is the one audience allowed to watch the real Egress number (`gdd.md:1014`) and it watches
from **one screen for the whole round** — which is exactly why this widget, alone among the things on
that screen, has to be able to say nothing. The living need no equivalent: their Egress is a screen
the house pushes them to, so the screen *is* the state and cannot be wrong about whether a crisis is
running. A couch reading `68%` from lights-out in a house where nothing has happened is the app
lying to the one person it has promised the truth to.

**`TRUE` disappears, and it was two grammars in one shape.** The literal read TRUE as an
*adjective* — the **true** Egress number, matching TRUE OCCUPANCY at the foot of the same screen —
while the living's widget one screen over draws `"EGRESS . ${type}"`, where the second half **is**
the type: `EGRESS . BEACON`. Two meanings, one shape, one screen apart, in front of the reader with
the most time to notice. The type is the reading the shape already had everywhere else.

**The frame stays at its full height either way**, so the plan below it does not walk up the screen
when an Egress fires — a layout that moved would be a second, silent announcement of the crisis,
arriving before the widget had said anything. And **empty is not zero**: nothing draws a bar with no
lit segments here, because a drained bar is a statement about an Egress nearly contained and *no
Egress at all* is not a value on that scale.

**This is F-006 seen from the other side and does not close it.** F-006 is about the living's
takeover costing a stale number rather than a live one; this is about the couch's widget claiming a
number when there is nothing to number. Both are still open on the same missing sender — the Egress
has a carrier (`Effect.EgressHeld`) but not the duration it is a fraction of.

### D-179 · **The ten seconds are ratified — revision 35's ⚠️ closed**

**An unanswered advertisement is refused after ten seconds, and the owner has adopted the number.**

It was built rather than ruled, and revision 35 said so at length: it is *the one number on that
page with no authority behind it*. The reason it existed is the reason it is now kept — **the owner
ruled two outcomes and the OS has three.** With the Local Network grant gone iOS publishes nothing
and reports nothing: no error, no prompt, no callback of any kind (E-D3-4). A build that waited only
on the two ruled callbacks would have made HOST WITH THIS HOME a control that does nothing when
pressed, which is a worse failure than the one D-173 fixes and would have shipped in the name of
fixing it.

**What the ten seconds bound is a callback that never comes, not a slow network.** Bonjour answers
in well under a second on a working stack — D3's own logs have the publish landing between two lobby
lines — so this is a fuse on silence. **No rule reads it and it stamps into no recording**, which is
what makes ratifying it cheap: nothing downstream has to be re-derived because a number changed
status from *invented* to *adopted*.

The flag comes off. The value does not change.

### D-180 · **A refusal names the fixable cause, not only the symptom — decided**

**Where a host is refused and the thing that would fix it is not on the screen they are standing on,
the refusal says what that thing is.** `THIS HOME DID NOT GO ON THE AIR` keeps its place and gains a
second line naming the **Local Network** grant.

**This is the gap D-173 left, and it is a real one.** That ruling's argument was that a duplicate
home name is the most actionable refusal in the app — the fix is a rename, the host is holding the
phone in a lit room, and the RENAME row is on the same screen as the refusal. **The other refusal has
none of that.** Its fix is two taps away in the OS, on a screen this app does not own, and nothing in
*this home did not go on the air* points at it. A host reading that sentence learns they have a
problem and not one thing about solving it — which is the same shape as the silence D-173 replaced,
one step further in.

**The line points rather than asserts, and that is the honest form rather than a hedge.**
`CouldNotGoOnTheAir` covers both a refusal the OS gave a reason for and ten seconds of silence it
gave nothing for (D-179), so **this phone genuinely cannot know which happened.** A line stating the
permission *was* the cause would be the app asserting a fact it does not have, on a screen somebody
acts on. *Check* is the strongest verb the evidence carries, and it is enough: two taps from a person
cleared exactly this on the night it was first seen (E-D3-4), and the whole reason that arrival was
survivable is that it landed on setup, in the light.

**Rule 6 is not in tension with this.** *Errors are silent to the player, loud to the authority* is
about a round in a dark house; **hosting happens in the light, before there is a round**, and D-126
already puts every gate where the host is alone with the setup. This is the same call D-173 made,
applied to the refusal D-173 could not act on.

> ### ⚠️ THE WORDING IS THE BUILDER'S DRAFT
>
> The owner ruled the **shape** — symptom, then the cause a person can act on — and did not dictate
> the words. `CHECK LOCAL NETWORK PERMISSION` is drafted within the register, under the project's
> standing copy rule, and **awaits the owner's eye**. Two constants rather than one string, so that
> the ruled half and the drafted half can be read, tested and rewritten apart:
> `LobbyModel.NOT_ON_THE_AIR` and `LobbyModel.NOT_ON_THE_AIR_CAUSE`.
>
> **LOCAL NETWORK is the app's own name for the grant** — the setup walk lists it under that
> heading — so the host is sent to a row they have already read once rather than to a term this
> screen invented.

### Swept in this pass

| site | superseded by | what it now says |
|---|---|---|
| `gdd.md:400` — *the meeting ring is the one permitted phone-emitted sound* | D-176 | the Surge is, and the call is haptic-only |
| `gdd.md:411` — M10's trigger row, *and it rings … every phone rings* | D-176, D-160 | it rings in the hand and nowhere else; no phone makes a sound |
| `gdd.md:993` — *every phone rings* | D-176, D-160 | every phone starts vibrating, repeating until answered |
| `gdd.md:995` — *sound reaches everyone, through walls* | D-176 | the call is what makes the meeting unambiguous, and it is the one signal that does not give up |
| `gdd.md:1208-1217` — *an actual ringtone*, and the four-row property table | D-176, D-160 | haptic only; indefinitely until answered; no audio session; no volume |
| `Countdown.kt` — `ScreenId.GhostMeeting to 60` and its *left alone deliberately* KDoc | D-163 | both ballot rows read `Balance.VOTE_WINDOW_SECONDS`; the design's `0:24` now sits over sixteen segments of thirty |
| `ScreensWork.kt` — `NOT YOURS` | D-124 | `NOTHING FOR YOU HERE` (E-L4-2 closed) |
| `ScreensOut.kt` — `EGRESS . TRUE` and its always-populated widget | D-178 | the type, and only while one runs |
| `ScreensOut.kt` — the sign-off card in amber on the Residents' ending | — | the bone palette, like every other element on that screen |
| `Chrome.kt` — *REVOKED and RESTRAINED fit whole with room to spare* | measurement | the carrier draws exactly ten characters and RESTRAINED is ten; there is no margin |
| `Lobby.kt` — `THIS HOME DID NOT GO ON THE AIR` as the whole refusal | D-180 | the same line, plus the grant a host can still give — **wording drafted, not ruled** |
| revision 35's ⚠️ *Built under D-173 but not ruled: the ten seconds* | D-179 | ratified; the value is unchanged and the flag comes off |

### Still open — and one of them is now smaller

- **E-P1-4 is smaller by one.** The buzz had no route from the wire to the motor: `HouseBuzzer`
  answered for `Effect`, a client only ever holds a `RoundMessage`, and the class therefore had no
  production caller at all. It answers for both now, and the two answers are compared against each
  other across every kind in the game rather than trusted to agree. **The fifteen screen routes are
  untouched** — which screen a `MeetingOpened` opens is still a decision and not a wiring gap.
- **Two copy drafts are open, and they are the only things left standing from this revision.**
  D-180's cause line (`CHECK LOCAL NETWORK PERMISSION`) and Sniff's briefing are both the builder's
  words inside a ruled shape, and both want the owner's morning eye. Neither is a decision waiting
  to be made — the decisions are D-180 and E-S1-1's placement; these are the sentences under them.
- **Sniff's briefing copy is a draft**, flagged on `Subroutine.briefing`. E-S1-1 is closed as a
  *placement* — the instruction is read at the card, because Sniff's own screen is black until the
  answer (D-137) and a sentence drawn there is black ink on black glass. The wording is the build's
  and wants a person's eye. Whether the other nine should be briefed at the card as well is a copy
  call nobody has made; they are deliberately null.

---

## Revision 38 — the jam the whole house can see, and the shift that opens half-charged

*Ruled 2026-08-23, with Vadmanuel. **Ten entries, and nine of them close something that was written
down as owed.** The two Tier-2 units left eighteen escalations between them, the push units left
five, and the rehearsal left the largest finding this project has had. What follows takes the ones a
build was right to refuse — the ones where guessing would have been a design decision taken by
whoever happened to be typing.*

*The tenth is not a closure. **Spoof is gone**, replaced rather than tuned, and the replacement runs
in the opposite direction: the ability whose whole risk was that nobody would notice it is now the
one thing in the game the house announces out loud. Five escalations, one enum row, one screen and
the largest disclosure in the emit table all fall out with it.*

### D-181 · **The routing table is ratified — E-P3-1 closed**

**The per-kind routing the owner walked through with unit R1 is confirmed as ruled, kind by kind.**
The table itself lives in that unit's worklog section (`_bmad-output/overnight/2026-08-21.md`,
*Routing — Unit R1*) and is deliberately **not** restated here: it is forty-odd rows of *which
screen a kind opens*, it will be edited as screens are built, and a second copy in this log would be
the copy that goes stale while looking authoritative.

**What this log holds instead is that the table is a ruling and not a wiring convenience.** E-P3-1
was the finding that four phones stood on the lobby screen through a complete, correct round — a
house running a whole game no player would see any of — and unit P1 had refused to close it by
choosing, on the grounds that *which* screen a `MeetingOpened` opens is a design question. **That
refusal was correct and is why this entry exists.** A build that had guessed would have produced the
same working app and no record that anybody decided anything, and the next person to move a screen
would have had nothing to weigh their change against.

**E-P3-2 falls to the same table.** The check-in gate could not close whenever anybody was outside
the system, because `StandAndWalkIn` was one of the routeless kinds and no screen offered that seat
a control. It is not a missing rule — the wire member existed and the rules accepted it — it was a
kind with nowhere to go, which is E-P1-4 seen from a phone. **A routed kind is a control a player
can reach; that is the whole repair.**

### D-182 · **A Surge is the phone malfunctioning for five seconds, and it eats the work in your hands — T1·6 closed**

**Owner-set values, not placeholders:**

| | |
|---|---|
| Full-bright | **1.5s** |
| Dark | **1.0s** |
| Boot screen | **2.5s** — the cold-start Boot render, unmodified |
| Total downtime | **5.0s** |
| Player input | **None.** Fully automatic, start to finish |
| An open Subroutine instance | **LOST.** Progress wiped, rescan required |

**The fiction is the phone rebooting, and that is why the third phase is the Boot screen rather than
a Surge screen.** A bespoke *you have been surged* render would be a caption on the event, readable
by the person it happened to and by anyone glancing over their shoulder — and this game already has
a screen that means *this device just came back from nothing*. Reusing it costs one asset and buys
the ambiguity: a surged phone and a phone that genuinely crashed are the same picture. **Nothing new
is drawn, so there is nothing new to keep in parity.**

**Five seconds is a long time in a dark house, and the length is the ability.** `gdd.md:342` calls
the Surge a commitment rather than a tool — the loudest, brightest thing that can happen — and the
half that was never sized was how long the victim stays out of the game. A one-second flare is a
startle; five seconds is a person standing in the dark holding a dark rectangle while everybody in
the building walks toward the light they just saw. T1 flagged this as unruled and noted that `core`
has no clock to say it with, which remains true: **these are the platform's numbers and they belong
on the platform's side of the line**, minted from one effect the rules already emit.

**The lost Subroutine instance is the interrupt question, answered against the softer reading.**
`gdd.md:594`'s *interruptible — aborts cleanly with unambiguous progress state* is satisfied: the
state is unambiguous, and it is **gone**. Preserving progress across a Surge would have made the
attack survivable by standing still, and it would have needed a resume path that is a second way
into a Subroutine — **the one thing D-110 spent a ruling closing.** One attempt per scan, and the
walk back is the cost; a Surge simply buys the attacker that walk. Nothing about this is a new
rule, which is the argument for it: **the Surge does not need a special case, it needs to not have
one.**

### D-183 · **Tier 2 is refused during a meeting, and the refusal is honest — T1·7 and T2·8 closed**

**A Tier-2 fire is refused for the duration of a meeting.** Both units left this admissible and both
were right to: T1's reasoning was that the pool pauses so nothing turns on the gate, and that adding
a refusal would be inventing a rule while refusing *quietly* inside the rules would be rule 1's
forbidden shape.

**What settles it is that there is no surface to fire from.** Everyone alive is on the meeting view;
everyone out is on the ghost view. **Springboard page 2 does not exist during a meeting for any seat
of any role**, so no honest client can produce the intent, and the refusal at the admission boundary
is therefore not a client-visible path at all — it is the boundary doing what it is for, against a
forged client. Rule 1 is about an effect a player should have seen and did not. **Nobody can see an
effect on a screen nobody is standing on.**

That is a stronger position than *the pool pauses, so it does not matter*. A pause makes the fire
pointless; it does not make it impossible, and *pointless but permitted* is exactly the shape that
gets found by whoever builds the next client and wonders why the tile is missing. **Now the answer
is the same on both sides of the wire: there is no tile, and there is no admission.**

### D-184 · **A meeting hands every cooldown back at half — `gdd.md:373` chosen at last**

**At meeting end, every cooldown resets to HALF its duration.** Not paused, not resumed, not
cleared: **halved and restarted.** Per-Insider on the Revoke, per-Insider on the Access pool.

`gdd.md:373` has read *"Revoke cooldown pauses or resets at meeting end"* since the design was
written and has never chosen between them; T1 built neither, asserted in `TierTwoTest` that the
Revoke row does **not** move, and wrote that the build which decides has to come and change a line
saying what it is changing. **This is that ruling, and it picks a third thing neither word named.**

**It is D-132's arithmetic, reused rather than invented.** Every Insider clock opens the round at
half, so that arming is not immediately followed by a Revoke and **every shift begins with a
guaranteed stretch of peace whose length is known.** A meeting ends a shift and starts the next one
(D-170), so the shift after a meeting gets the same guarantee the first shift got. One rule, stated
once, applying at both moments it could apply. **A reset to zero would make a meeting a full
rearmament and turn calling one into an Insider's move; a plain pause would make the meeting a
loophole in the other direction, banking a cooldown that was about to expire.** Halving is the only
answer that gives the same shape to both.

**The Egress is the exception, and it is exempt in both directions.** The crisis survives a meeting
— paused, per D-133 — and **its shared cooldown is frozen out of the reset until the Egress is
defused.** Handing the Egress clock back at half while an Egress is still running would let a
meeting called *during* a crisis fund the next one, which is the single most dangerous thing a reset
rule can do in a game where an uncontained Egress ends the round outright. The Egress is a win
condition; every other clock on this list is a noise.

### D-185 · **A meeting call ends every running ability effect — T1·9 and T2·9 closed**

**Calling a meeting terminates every ability effect currently running.** An isolation window fails.
Any monitoring effect fails. **Only the Egress survives it**, paused per D-133.

Both units flagged the opposite behaviour and neither defended it: T1's item 9 was *an isolation
does not pause across a meeting, so a meeting can eat one whole*, and T2's item 9 was the same
sentence one plane over. Both called it *the simpler reading of "offline for N seconds", recorded
rather than defended.* **The reading is now that the simpler thing is not to survive at all.**

**The moment is the call, not the end.** A meeting call snaps every lamp in the house to full white
and puts every living player in one room — which is the moment an isolated marker stops being an
attack and becomes a piece of stale machinery ticking in an empty hallway. An effect whose whole
function is to shape movement through a dark house has nothing left to do once nobody is moving
through it, and one that outlived the call would land its remaining seconds on the *next* shift,
bought at the price of the shift it was fired in. **Nobody would be able to tell that from a fresh
fire, which is the real objection: an effect that crosses a meeting is an effect with no traceable
cause.**

**The Egress survives because it is not an effect on anybody.** It is the crisis the round can end
on, it is already ruled to pause (D-133), and a meeting that could dissolve one would make calling a
meeting the cheapest possible containment — a Restrain that defuses the Egress by accident.

### D-186 · **STATIC — Spoof is replaced, not tuned. T2·1 through T2·5 close, and the picker fails with it**

**Spoof is gone.** The ability in its slot is **Static**: a **10-second house-wide jam of every
monitoring surface the living can read.**

| | |
|---|---|
| Target | **None.** No picker, no list, no plan |
| Fired from | Springboard page 2, self-fired in place, under the two-second hold (D-141, D-142) |
| Duration | **10s** |
| Scope | **House-wide** |
| Tracking | **Untouched.** The house records the truth throughout |
| The living's live view | **Animated noise**, with the real data faintly beneath it — *virtually impossible* to parse |
| A Timelapse crossing the window | Renders those seconds as static — **~1.7s of noise at 6×** |
| Ghosts | **Exempt.** Their true occupancy stays clean |
| Cooldown | **Heavier than old Spoof's.** Constant raised; playtest tunes it |
| Announcement | **The house shows that it was jammed. It never shows who** |

**The redesign answers `gdd.md:1145` rather than deferring it.** That balance risk says Spoof may be
too abstract for the dark — slow, indirect, asking an Insider to reason about the map layer while
standing in a hallway — and `gdd.md:1347` made the watching a number: *fired at least once per three
rounds; if it is never used, cut it.* **The owner has cut the mechanic and kept the slot.** What
replaced it needs no map reasoning at all: you fire it because you are about to do something, and it
costs you nothing to understand.

**It is loud and anonymous by design, and those two words are the whole ability.** Static is
deliberately **distinguishable from disconnection** — a jammed Terminal does not read as a broken
one — so the house visibly announces **that** the monitoring was jammed, in the same administrative
register it announces everything else, and names nobody. That is this project's evidence texture
exactly: every living player now knows an Insider did something in the last ten seconds and has no
idea which one, and the reader at the station knows precisely what they were prevented from seeing.
**Spoof's failure was that its consequence was invisible to everybody including the person who fired
it** — T2 wrote *looking like it did nothing is the ability working*, which is true and is also the
reason nobody would ever have reached for it. Static inverts that at no cost to anonymity.

**The Timelapse gap is a forensic trace and is meant to be one.** The live cover is the product; ten
seconds compressed to under two at 6× is a scar in the playback that says *something was hidden
here, at this time*, without saying what or by whom. A player who watches it back learns the window
and nothing inside it. **Watching the past to find out what you were stopped from seeing in the
present is the Terminal's whole tragedy, and Static gives it a second edge.**

**Ghosts are exempt because ghosts are outside the system.** M12's fiction is that the house erased
them from the registry and can neither see nor control them; a jam is the house's own monitoring
degrading, and a ghost's true occupancy is not the house's monitoring. **It is the liveness
privilege stated once more** — ghosts already get both bars, true counts, no injected error and no
staleness bands, and D-134's clustering is what makes that safe. A ghost with a clean map during a
jam cannot tell anybody, and that is why it costs nothing.

**The name was chosen against Jam, and the reason is a rule rather than a preference.** **Jam is
roster Subroutine #9** (`gdd.md:573`) — tap +/− until two shapes overlap. **One meaning per word**,
which is the same discipline that keeps Revoke and Restrain apart, and *house* apart from *home*.
The vocabulary is closed and it is now closed around **Static**; **Spoof leaves it.** The lint's word
list carries the mechanical half and is the build's to swap, in a diff that says so.

#### The consequences, which are larger than the ability

**D-175's third answer falls.** Revision 37 recorded the targeting model as *Surge picks a person
from a LIST, Isolate picks a marker from a LIST, Spoof picks a room from the MAP* — **the map answer
is superseded and Static has no target at all.** The first two stand unchanged, and so does the
argument under them: the glow of reading is the deliberate price, and nothing on either surviving
picker may be quicker than reading.

**The home plan returns to crossing to NO client, and T2·4 is rolled back rather than closed.** That
escalation flagged the plan reaching a living Insider as *the largest disclosure in the emit table*,
one class wide, never to be widened by one — a row that existed only because a phone cannot draw a
plan it was never sent. **Static has no plan to draw.** The row goes, and `Destination`'s own warning
about the whole map reaching every client returns to being a warning about a thing the app does not
do. **This is the single largest reduction in the emit table since the table was written, and it
came from deleting a screen.**

**The phantom machinery is superseded outright** — the effect that shipped to nobody, the duration
constant, and the phantom arm of the placement read. With it go three escalations that were only
ever questions about a phantom: **T2·1**, whether a scan is knowledge forever (the most consequential
guess in that unit, and the one most likely to have made Spoof useless in play); **T2·2**, how long a
phantom lasts; **T2·3**, a room the plan does not hold being representable. **They dissolve rather
than being answered**, which is worth writing down so nobody hunts for the rulings later. **T2·5**
— a phantom's recipients being nobody — dissolves the same way.

**D-136 survives untouched and must not be swept with the rest.** *Inference never overrides
knowledge* is a rule about the spectator map, which is still unbuilt and still needs it; what falls
is only the phantom's arm of it. The structural separation T2 built — that a lie **cannot** be
expressed in the same collection as a scan — was the right shape and the reasoning on it should be
kept for whoever builds that map.

**Static is self-targeting by shape, exactly as Spoof was**, so `gdd.md:393`'s *every observable
effect must be self-inflictable* is satisfied without a special case: it jams the Insider's own
monitoring alongside everybody else's, and an Insider standing at the Terminal when their own Static
lands sees the same noise a Resident does. **`gdd.md:398`'s no-actor-side-feedback rule is satisfied
by the same fact** — the announcement fires for the whole house, so it is a global event, and the
firing phone is told nothing a bystander's phone is not.

### D-187 · **A house notice is an anti-cheat report and nothing else — `gdd.md:419-427` narrowed**

**Notices exist solely to report detectable deliberate evasion**: locking the phone, backgrounding
the app, disabling a permission mid-round. **Genuine radio loss earns no notice.** It shows as
disconnection on the monitoring surfaces and nowhere else. **Static earns no notice either** — it has
its own announcement, and it is not evasion.

**This deletes the radio-failure half of the original rationale, which was the half that made the
notice unsafe.** `gdd.md:424` argued that radio failure and backgrounding degrade advertising
identically so both should trigger it, and that silent degradation is a real advantage worth
naming. The first clause is true about the *signal* and false about the *person*: **a phone whose
radio fails has a defective phone, and a notice naming that player reads to the room exactly like a
notice naming a cheat.** `gdd.md:426` says so out loud without noticing — *"the system could not see
you for three minutes" is the worst sentence that can be read aloud about you.* That is the correct
consequence for somebody who backgrounded the app on purpose and a straightforwardly unjust one for
somebody whose hardware failed, in a game that then asks the room to vote.

**The anti-exploit argument survives the narrowing and gets stronger.** Backgrounding to manufacture
an excuse still backfires, still generates no check-ins, and is now the *only* thing a notice can
report — so the sentence means what it says instead of covering two cases the room cannot tell
apart. **A permission revoked mid-round stays in scope** and stays for D-161's reason: every
permission is granted at setup, in the light, before a seat is taken, so one missing during a round
is something a person did.

**What is lost is the diegetic flourish**, and it is worth naming the loss. `gdd.md:428` liked the
notice for making the map's unreliability *diegetic in writing* — the system admitting its own
limitations badly, in a form players could argue about. **The house still admits it**, through
staleness bands, coverage holes, injected error and now Static; what it stops doing is putting a
named person's broken phone into the argument.

### D-188 · **Every Subroutine is briefed before it opens, and none of them explains itself inside**

**All ten Subroutines carry their instruction BEFORE the minigame — at the caught scan — and none
carries one inside it.** A misread costs the attempt and a rescan.

**This inverts the pattern, rather than extending the exception.** E-S1-1 was closed as a *placement*
for Sniff alone: Sniff's own screen is black until the answer (D-137), so a sentence drawn there is
black ink on black glass, and the instruction had to move to the card. The other nine were left
deliberately null with the note that whether they should all be briefed at the card was a copy call
nobody had made. **The call is made and it goes the other way from the way it was framed:** Sniff is
not the exception that needs a briefing, it is the one that made the right pattern visible.

**The argument is the light signature.** Every Subroutine is rated bright, medium or dark (D-112,
D-113) and **the rating is a promise about how much light you are standing in while you work.** An
in-screen instruction line is lit pixels for the whole duration of the task on a screen whose entire
design constraint is how little it may glow — it makes a dark Subroutine less dark and a bright one
longer. **Reading happens at the card, where the scan has already lit you up and the cost is already
paid**, and then the task itself is as dark as it was rated to be. This is the same accounting D-175
made about the Tier-2 pickers, in the opposite direction: there, reading is a price worth charging;
here, it is a price already charged, and charging it twice is the mistake.

**A misread costing the attempt is the intended severity, not an accepted consequence.** D-110
already rules one attempt per scan with the walk back as the cost. Tying comprehension to the same
gate means a player who skims in the dark pays what a player who fumbles pays, and there is exactly
one recovery path in the game rather than two.

> ### ⚠️ NINE LINES ARE OWED, AND THEY ARE COPY
>
> The nine in-screen instruction lines are to be **stripped**, and **nine briefing lines drafted in
> Sniff's register.** A build unit is queued for it. **Sniff's drafted line stands** — *THE PHONE WILL
> BUZZ TWICE. TAP THE SIDE THAT BUZZED MORE.* — and is the register the other nine are written
> against: second person, imperative, what the phone will do and what you do about it, no nouns from
> the fiction. It remains the builder's words inside a ruled shape and still wants the owner's eye.

### D-189 · **The per-seat replay outbox stays uncapped — E-P1-3 closed**

**No cap.** The outbox is round-bounded and wiped at round end, and that is the whole of its bound.

Unit P1 deliberately declined to add one and said why: **a bounded outbox answers a re-assert
partially, and a silent partial is the failure class this project ranks worst.** The re-assert is
D-097's mechanism — a refused event is answered by re-asserting the client's authorized view, never
by a reason-carrying message — and a view re-asserted from a truncated log is a phone confidently
holding a house that has moved on, with nothing on it saying so. **A cap converts an unbounded
memory cost into an unbounded correctness cost**, which is a worse trade in a game debugged by
replaying recordings.

**The number does not want a ruling, which is why this one is short.** At this game's message rate
the outbox is tens of kilobytes a seat against a whole-app allocation target of ~0.5 MB/s, and
`roundEnded()` already frees it. **P1 wrote that this wants a ruling and not a constant, and it was
right; the ruling is that the constant stays absent.**

### D-190 · **A reconnecting phone is re-sent the Isolate target list — T2·6 closed**

**A phone that reconnects mid-round is re-sent the Isolate targets.** The plan half of that
escalation is **moot**: the plan no longer crosses to any client (D-186), so there is nothing to
re-send and the Spoof tile that would have opened an empty plan does not exist.

T2 recorded this as *T1's existing gap, inherited rather than created, and now twice as visible.*
Half of it was solved by deleting an ability. **The other half is real and is not a special case:**
the list is arming-time state that reaches exactly one class, so a seat that missed the arming burst
has an ability it cannot aim — a tile that opens an empty column, which is indistinguishable from a
round armed with no home. **The re-send is the same posture the outbox has**, one message rather
than a new mechanism.

---

### Swept in this pass

| site | superseded by | what it now says |
|---|---|---|
| `gdd.md:306` — Tier 2 is *Surge, Spoof, Isolate* | D-186 | Surge, **Static**, Isolate |
| `gdd.md:313` — the Surge row, *lamp to full then to black* | D-182 | the five-second sequence, and the Subroutine it costs |
| `gdd.md:314` — the Spoof row, *false check-in … phantom occupant* | D-186 | **Static** — a ten-second house-wide jam of the living's monitoring |
| `gdd.md:342` — *Surge is a commitment*, with no duration | D-182 | the same argument, with the five seconds under it |
| `gdd.md:372-373` — *Revoke cooldown pauses or resets at meeting end* | D-183, D-184, D-185 | every cooldown returns at half; Tier 2 is refused during; running effects end at the call |
| `gdd.md:419-427` — the notice's radio-failure rationale | D-187 | anti-cheat only; genuine radio loss reads as disconnection and earns no notice |
| `gdd.md:447-472` — the Terminal's live view | D-186 | plus what a jammed live view looks like, and that ghosts never see one |
| `gdd.md:474-495` — the Timelapse spec | D-186 | plus the static gap a playback crossing a jam renders |
| `gdd.md:576` — the rename history | D-186 | plus the ability rename, and why **Jam** was refused as a name |
| `gdd.md:766`, `:1121`, `:1129` — the Access pool's three rows | D-186 | Static in Spoof's place; the pool is otherwise unchanged |
| `gdd.md:1145` — balance risk 4, *Spoof may be too abstract for the dark* | D-186 | the risk was taken and the mechanic was replaced; what is watched now is the price |
| `gdd.md:1310`, `epics.md:285` — E9's contents and story 9.11 | D-186 | Static, self-fired, no picker |
| `gdd.md:1347` — *Spoof usage*, fired once per three rounds | D-186 | **Static usage**, on the same counting method and a new question |
| `epics.md:306` — story 10.4, *every phone rings* | D-176, D-160 | haptic only — **carried over**; revision 37's sweep reached `gdd.md` and not `epics.md` |
| `epics.md:316` — story 10.12, the *pauses or resets* clause | D-183, D-184, D-185 | the three meeting rules, stated apart |
| `epics.md:315` — story 10.14, the house notice | D-187 | deliberate evasion only |

### Owed to the build, and not by this pass

- **`TierTwoTest`'s assertion that the Revoke row does not move across a meeting** was written so
  that the build which decides has to come and change a line saying what it is changing. **D-184 is
  that decision** and the line is now owed a change. Docs do not touch it.
- **The lint's word list** carries `Spoof` and does not carry `Static`. The swap is code and belongs
  in a diff that says what it is doing.
- **Nine briefing lines and nine deletions** (D-188), plus the queued unit that writes them.
- **The emit table's largest row comes out** (D-186). A disclosure that shrinks deserves the same
  ceremony as one that grows, and the schema fails closed either way.

### Still open

- **Two copy drafts remain, and a third joins them.** D-180's `CHECK LOCAL NETWORK PERMISSION`, and
  Sniff's briefing line — both the builder's words inside a ruled shape — are now joined by **the
  nine briefings D-188 orders.** None is a decision waiting to be made.
- **The Terminal's noise is specced in words and not in pixels.** *Animated, with the real data
  faintly beneath, virtually impossible to parse* is the ruling; how faint, how fast, and what it
  costs to draw against a ~0.5 MB/s allocation target are the build's, and the first two are a
  dark-room question rather than a paper one.
- **`egressFired` still lies on `cooldownStarted`.** T1's flag, T2's flag, and now revision 38's:
  three units have declined to change it under a freeze, and D-184 gives the Egress clock a second
  reason to be got right.
- **F-006 is untouched.** The Egress has a carrier and not the duration it is a fraction of.

---

## Revision 39 — the phone that knows it called, and the house that asks who is firing

*Written by the routing day's closing sweep, which built nothing and measured everything. **Three
entries, and they are not all the same kind of thing** — the first is the owner's, chosen from two
options put to them while unit C3 was running; the second and third are the build applying doctrine
already in this log to two holes a rehearsal found by playing. That difference is written at each
entry rather than smoothed over, because a reader deciding whether they may move one of these needs
to know whose it is.*

*Revision 38 was committed at 16:08 while unit R1 was still running, so **D-181 through D-190 are
already recorded and are not restated here.** What follows is only what the day ruled after that
commit.*

### D-191 · **The calling phone is told it is calling, and the message carries no field — E-R1-1 closed, OPTION 1**

**The owner's ruling, 2026-08-23**, on an escalation R1 raised and refused to guess: the routing
table asks for *the calling seat → `Calling`*, `Effect.MeetingOpened` carries `caller: Seat`, and a
phone in this game **does not know its own seat**, so the comparison that would pick the caller out
of five ringing phones has one operand. Two shapes were put up — a per-seat message, or teaching a
phone its seat — and **the per-seat message was chosen.**

**The second option was the expensive one and that is the argument.** *Teaching a phone its own
seat* sounds like plumbing and is not: a seat index on a client is a comparison operand for
everything else that carries a seat, and every future disclosure decision would then have to be made
against a client that can ask *is this me?* about any of them. The per-seat message answers exactly
one question and can answer no others.

**`Effect.MeetingCalling(seat)` on the authority's side, and `RoundMessage.MeetingCalling` with no
field at all on the wire** — `RestrainedTakeover`'s existing arrangement, reused rather than
invented. Two meetings called by two different people put **byte-identical bytes** on two different
sockets; the class is `LIVING` and it is `deliveries` that narrows it to the one seat, so the row
grants nothing wider even if somebody widens it later.

**Three consequences, each decided rather than fallen into:**

- **No haptic.** The ring is `MeetingOpened`'s and the caller receives it like everybody else. One
  phone in a silent house buzzing differently from the other five is the caller named by hardware,
  at the one moment the design has already made public by making them walk to the card. **D-135's
  five stay five.**
- **Emitted after the ring**, because the ring is what carries the caller's name onto the panel; a
  caller pushed to `Calling` first would arrive holding a header the next message was still on its
  way to fill.
- **The recording says the seat and the wire does not.** This is the one place the two artifacts
  deliberately disagree: a client may not be told which seat it is, and a recording that could not
  say which seat called the meeting could not replay one.

### D-192 · **The house asks who is firing — E-R2-1 closed, and it is doctrine applied rather than a new rule**

**Not an owner ruling. This is D-066 and rule 1 applied to a hole Rehearsal 2 found by playing**,
and it is recorded here because it added a permanent branch to the admission gate and a member to an
enum whose KDoc forbids members of that shape.

**The finding first, because it was observed and not reasoned to.** Across one rehearsal round five
seats pressed the Egress tile 39 times between them and **the house made 39 draws.** One of them was
a plain Resident who had been Revoked mid-shift and was sitting on the couch. `roleOf` and `isOut`
appear in `Rules.kt` exactly three times — the scan, the entry grading and the Sync Pulse — and **no
ability handler in the game read either one.** A round could be decided by somebody who was not in
it.

**The gate is `admit`'s and not the handler's, which is the whole of D-066's argument.** In the
handler it would be `if (!insider) return Reduction(state, emptyList())` — rule 1's forbidden shape,
in the file rule 1 is about. At the gate the refusal **costs nothing** (no cooldown, no pool, no
draw) and is **recorded**, and an early return is never either.

**`RefusalReason.AbilityNotTheirs` is admitted against that enum's own entry condition, and the
exception is argued at the member rather than waved past.** The condition was D-068's — *only
reasons a player standing in the dark could have worked out without their phone* — and **D-097
deleted the channel it was written to protect**: a refused event is answered by re-pushing that
seat's own authorized view, and no effect and no wire kind can carry a reason down at all. What is
left is a reason the authority and the recording read and nobody else. The entry condition is
**unchanged for every reason that can still meet a player.**

**`ContactMade` is deliberately not gated.** `factOf` rules it *reported, never requested*, and a
contact revokes only from a seat holding an armed Revoke — which now nobody but a living Insider can
be. Gating a report above the rules would put an invisible drop in the recording, which is the exact
thing D-066 records refusals to prevent.

### D-193 · **Every phone is told what it is — E-R2-2 closed, on the arming push**

**Also doctrine applied rather than owner-ruled**, and the same rehearsal found it: every ledger
line of every seat of both rounds read `ROLE RESIDENT`, including the seat holding the round's only
Insider. `PanelState.role` was written by the cheat picker and by nothing else in the app, so **in a
real evening no Insider could have fired any ability at all** — everything under it worked and
nobody would ever have seen it.

**`Effect.RoleRevealed(seat, role)`, once per seat, for every seat, as a run of the arming burst.**
Four things about its shape are decisions:

- **Permitted to both living classes, never to one.** A row of `Insider/Live` would deliver the
  reveal to Insiders and silence to everybody else, **and the silence would be the answer** — rule
  1's forbidden shape written into the allowlist, where it would have looked like a tightening.
  What stops seat 5 learning seat 3's role is the addressing, which the allowlist cannot see.
- **A `Boolean` on the wire.** `Role` carries no `@ClientFacing` and no `@Serializable` and **must
  never acquire one** — `redactionLint`'s own KDoc records the day that guard default-permitted the
  most alignment-revealing type in the game. Rule 3's *construct a narrower type*, applied at the
  transport.
- **Silent**, and that is F-003 (`gdd.md:930`): the reveal leaking through the *number* of buzzes,
  at the one moment of the evening when nobody has dispersed.
- **No screen moves.** The reveal is text on a thread the player opens and the opening banner is
  what says *now*. A navigation here would be one phone doing something a Resident's phone does not,
  in the light, with the party still standing together.

**Known mode is not widened.** `gdd.md:923` gives an Insider a message from each fellow Insider,
riding the opening text thread diegetically. This carries **self-knowledge only**, and there is
nowhere on the member to put anything else.

### What this revision does NOT close

**Nine escalations from Rehearsal 2 and four from R1 and C3 are open and unruled**, and R2F looked
at every one of them under the instruction *implement only if the worklog shows it is already
ruled*. **None of them qualified**, and two look ruled until the file is opened:

- **E-R2-3** — where a living player is pushed when the house catches fire is a **routing decision
  this log rules nowhere**; D-118 and D-076 rule the dim and the buzz, not a navigation. Its second
  half, the two containment markers never reaching a phone, is the same escalation and was left with
  it: a phone holding two markers on a panel nobody is looking at closes nothing on its own.
- **E-R2-6** — the couch's live ballot cites D-117 and is still blocked one layer down. The wire
  member carries **seats** and `PanelState.ballots` wants **names**, so the open question is not the
  routing but **where the resolution happens**, and that is a protocol decision.

**E-R2-9 / E-R1-2 is a disclosure question and belongs to whoever rules it**: the drawn tally screen
shows per-candidate counts to everybody, `Effect.MeetingResult` carries only `restrained`, and on a
real round the living's tally is therefore a header with nothing under it. Either the effect is a
field short or the screen shows the living more than it should.

### Still open — carried forward from revision 38, plus what the day added

- **Twelve copy strings now wait on the owner, not three.** D-180's `CHECK LOCAL NETWORK
  PERMISSION`, Sniff's briefing line, and **the nine briefings D-188 ordered are now drafted** and
  listed in full in the routing day's worklog. None is a decision waiting to be made; all are the
  builder's words inside a ruled shape.
- **`CurrentSubroutine.instruction` has no carrier and is always null in play**, and whether the
  flavour line wants one or belongs on the client's roster beside the label is **a copy call nobody
  has made.** New with C3.
- **`Balance.STATIC_COOLDOWN` is 120s and the figure is the build's.** D-186 ruled the direction and
  named no number; what is built is an ordering — Surge 150 > Static 120 > Isolate 90.
- **`GameState.accessPausedAt` is unused machinery** — D-183 and D-184 between them made it
  unobservable. Kept and flagged rather than deleted, because removing state is a recording-shape
  decision.
- **`egressFired`'s `cooldownStarted` lie was fixed by R1.** Three revisions carried it; it is off
  this list.
- **The Terminal's noise is now pixels** — D-186's *animated, real data faintly beneath* is built,
  about one cell in six transparent and moving every step. How faint and how fast remain a dark-room
  question rather than a paper one.
- **F-006 is untouched.** The Egress has a carrier and not the duration it is a fraction of, and
  E-R2-4 and E-R2-7 are the same missing carrier arriving from two more directions.

---

## Revision 40 — the count the living get, and the sentence that had nothing behind it

*The owner's final Q&A batch, ruled 2026-08-24. **Five entries and they are all the owner's**, which
is unusual for this log and worth saying at the top: revision 39 mixed one ruling with two pieces of
doctrine the build applied itself, and none of that is here. Every entry below answers a question
that was put to the owner and had been sitting open — three of them since the routing day, one since
C3, one since D-184 made it defunct. **Two are subtractions**, which is the shape a batch of answers
takes when the honest answer to *what should this carry?* is *nothing*.*

### D-194 · **The nine briefings are canon, as written**

The last of D-188's business. D-188 ruled that all ten Subroutines are briefed at the card and none
of them explains itself inside; **Sniff's line already existed** and the other nine were drafted by
the build in Sniff's register — second person, imperative, what the phone will do and then what you
do about it, two lines, no nouns from the fiction — and carried a flag through two revisions.

**The owner read all nine and confirmed them unchanged.** They are the game's words now:

| Subroutine | the sentence a player reads at the card |
|---|---|
| REPLAY | DOTS WILL FLASH IN ORDER. / TAP THEM BACK IN THAT ORDER. |
| INTERRUPT | A SWEEP WILL RUN BACK AND FORTH. / TAP WHILE IT IS INSIDE THE BAND. |
| PARITY CHECK | A GRID WILL FILL IN A PATTERN. / TAP THE ONE BLOCK THAT BREAKS IT. |
| SNIFF | THE PHONE WILL BUZZ TWICE. / TAP THE SIDE THAT BUZZED MORE. |
| DEALLOCATE | THE COLUMNS WILL STAND UNEVEN. / TAP THEM DOWN TO THE SHORTEST. |
| DRIFT | A DOT WILL MOVE, THEN HIDE. / WHEN THE PHONE BUZZES, TAP WHERE IT IS. |
| SHORT | THE PHONE WILL ASK FOR FINGERS. / HOLD THAT MANY UNTIL IT RETURNS. |
| SIGNAL TRACE | TWO ENDS WILL BE MARKED. / TAP NODE TO NODE FROM ONE TO THE OTHER. |
| JAM | TWO RINGS WILL SIT APART. / TAP UNTIL THEY SIT ON ONE ANOTHER. |
| HANDSHAKE | THE PHONE WILL BUZZ A RHYTHM. / TAP IT BACK BY FEEL. |

**What changes in the build is the flags and nothing else** — the ⚠️ on `Subroutine.briefing` and in
`BriefingTest` come off. A confirmed line and a drafted line render identically, which is exactly why
the flag had to be cleared explicitly: nothing in a green build would ever have said these were still
waiting on somebody. `CHECK LOCAL NETWORK PERMISSION` is confirmed on the same terms at D-197.

### D-195 · **The living's result is COUNT-ONLY — E-R2-9 / E-R1-2 closed**

**The escalation was a leak in the drawn direction and had been for as long as the screen existed.**
The result screen carried three rows with names and counts against them — MARCUS 3, SKIP 2, PRIYA 1 —
**on every phone in every round, from a fixture**, because `Effect.MeetingResult` carried `restrained`
alone and there was nothing on a living phone's wire that could fill them. Both readings were put up:
either the effect was a field short, or the drawn screen showed the living more than it should.

**The owner ruled the second.** A living seat sees the participation count and who was Restrained —
`6 OF 6 VOTED . MARCUS WAS RESTRAINED` — and **never a per-candidate total.**

**The argument is what the two disclosures survive as.** A count is a fact about the room that the
room watched happen, and it is gone the moment the meeting is. A per-candidate tally is *the shape of
the room's thinking, published to the people still in it*: who drew suspicion and how much, in a form
that outlives the meeting in six people's memories and is argued from at the next one. D-075 already
reversed the GDD's *attribution shown*; this is the same reversal one step finer.

**Ghosts are unaffected and keep strictly more**, which is what makes the asymmetry an actual
privilege rather than a preview: the couch reads the ballot itself, with names against it, live
(D-117, D-134), and can act on none of it.

**Three consequences, each decided rather than fallen into:**

- **`Effect.MeetingResult` gains `voted` and `living`, and the pair discloses nothing.** That is
  provable rather than argued: it is byte-identical to the `VoteProgressed` emitted in the same
  reduction one effect earlier — same numerator, same denominator, same permission. The screen was
  computing those two numbers off a fixture; it reads them off the message now.
- **Read AFTER the auto-lock.** Every ballot is banked at the buzzer including the silences D-117
  counts as Skips, so the denominator is every living seat and the numerator is all of them. A build
  that read the moment *before* the buzzer would report the people who never pressed as having stayed
  out of a vote they were counted in.
- **The counted rows are deleted, not hidden.** `MeetingTally` no longer has a field for them, so
  rule 3 does the work instead of a screen keeping a rule: the living's result type is *physically
  incapable* of carrying a candidate's total. `Effect.MeetingResolved` is untouched on the wire and
  its attribution now **completes the couch's ballot** — the same list the couch has been watching
  fill, finished, including the seats whose silence never produced a selection message of their own.

### D-196 · **The flavour slot fails, and it is a v2 revisit rather than a rejection**

`CurrentSubroutine.instruction` held a fake-technical sentence naming what one instance of a kind
asked for — *RESYNC THE KEEPALIVE* under HANDSHAKE on the caught scan. **Nothing on the wire ever
carried it.** So in a real round it was either absent, or it was the port's own words printed over
whatever Subroutine the house had actually opened; C3 recorded it as *a copy call nobody has made*.

**The owner deleted the slot** — field, fixture and render slot in one change, rather than
commissioning a carrier for it.

**What makes the deletion cheap is D-188 landing first.** What a player has to *do* is
`Subroutine.briefing`, which is total over the roster and cannot be absent; the flavour line was a
second, larger sentence above the one that is true, competing with it on the last lit screen before
the work. Two sentences where one is authoritative is worse than one.

**Noted for v2 and not thrown away.** The register — the house talking about itself in its own
technical voice — is worth having and would want a carrier, a line per instance and ten more strings.
It is not worth those before the game has been played.

### D-197 · **The hosting refusal gains a control that opens the OS settings page**

D-180 ruled that this refusal name the fixable cause rather than only the symptom, and the build drew
`THIS HOME DID NOT GO ON THE AIR` with `CHECK LOCAL NETWORK PERMISSION` under it. **Both lines are
confirmed as written**, and the ruling adds the half that was missing: **the fix is not on that screen
and cannot be.** A host who read the cause line still had to find their way around iOS Settings with
the party standing in front of them.

**OPEN SETTINGS, under the sentence it acts on**, going to this app's own page via
`UIApplicationOpenSettingsURLString` — the only page an app may open. The host grants, comes back, and
presses HOST WITH THIS HOME again.

**Four things about it are decisions:**

- **It is host setup, in a lit room, before any round exists.** That is the whole licence for a
  control that leaves the app; nothing on this seam may ever be reachable once a round is armed.
- **It opens a page and cannot read one.** No `isGranted`, no callback, no result. Whether the grant
  was given is answered by advertising again — a seam that could report the permission would be a
  second source of truth about a fact the radio already answers, and the two would disagree on the
  phone where it mattered.
- **The other refusal is not offered it.** A duplicate home name is fixed by RENAME, one row up on
  the same screen; a control offering to open the OS settings for it would send a host looking for a
  permission that was never the problem.
- **It is drawn off the cause line rather than off a flag beside it**, so the control and the
  sentence cannot come apart in either direction — a host told to check a permission with nothing to
  press, or a control on a refusal it does nothing about.

### D-198 · **`GameState.accessPausedAt` is deleted — unused machinery, confirmed defunct**

Three revisions carried it as *unused machinery, kept and flagged rather than deleted, because removing
state is a recording-shape decision*. **The owner confirmed the pool no longer pauses — it resets at
meetings — and the state is gone.**

**Two earlier rulings had already closed it from both ends.** D-183 refuses every Tier-2 fire at the
gate for the whole of a meeting, so **nothing can ask whether the pool is ready while it would be
paused**; D-184 resets every per-Insider clock at lights out, so the deadline a resume would have
moved is overwritten a moment later. Between them the mark could not change any answer.

**The reason to delete rather than leave it is not tidiness.** State that cannot change an answer is
state a later reader will one day believe — and this one had a KDoc arguing for it, a transcript
column carrying it, and a test asserting it, all of which read as a live rule to anybody who had not
followed D-183 and D-184.

**The recording format bumps 16 → 17**, per the format's own condition, and it is the second bump
that is mostly a subtraction: the state row loses `accessPaused=` and `MeetingResult` gains the
participation pair from D-195. Nothing about a round changes — the pool behaves exactly as version
16's did, because the mark was already unobservable. What is gone is the record of it.

### Still open — carried forward, minus what this batch closed

- **Four of the twelve copy strings that waited on the owner are settled here** (D-194's nine plus
  D-197's cause line, counted as revision 39 counted them). **Sniff's line and the Terminal's noise
  are unchanged**, and no copy string in the build is flagged as awaiting an eye any more.
- **`CurrentSubroutine.instruction` is off this list**, deleted at D-196 with a v2 revisit against it.
- **`GameState.accessPausedAt` is off this list**, deleted at D-198.
- **`Balance.STATIC_COOLDOWN` is 120s and the figure is still the build's.** D-186 ruled the
  direction and named no number; what is built is an ordering — Surge 150 > Static 120 > Isolate 90.
- **The Terminal's noise is still pixels.** How faint and how fast remain a dark-room question rather
  than a paper one.
- **F-006 is untouched, and it is now the largest thing open.** The Egress has a carrier and not the
  duration it is a fraction of; **E-R2-4 and E-R2-7 are the same missing carrier arriving from two
  more directions**, E1 escalated the shape of it rather than guessing, and the closing sweep's
  reading stands: one field turns three escalations into screens a player reads, and it is the
  shortest path to a fourth rehearsal being worth running.
- **The couch's ballot denominator is still short.** The rows are the house's; the list holds the
  seats the house has shown rather than every living seat, so a phone that has never touched a row
  has no line to be STILL DECIDING on. D-195's completion of the ballot at the result narrows the
  window rather than closing it — what is still wanted is the living seats at ballot-open.

---

## Revision 41 — the map you walk to, and three places the build had drifted

*Ruled by the owner 2026-11-12, during the redesign of the device mockup for legibility. Comparing
every mockup screen against the shipped Kotlin screens turned up two kinds of finding: a tile that
had no reason to exist, and three places where the build had quietly narrowed or contradicted a
standing ruling. Both entries are the owner's.*

### D-199 · **The Terminal tile is deleted. The map opens by scanning the T card — decided**

**The tile only ever said NO SIGNAL.** M11 makes the map a place, not a screen, and the springboard
tile was how `gdd.md` made that rule a device state: tap it anywhere but the station and it refuses.
**It is deleted on the argument that deleted PHONE (D-121):** a tile that refuses teaches the rule by
punishing somebody for pressing it in the dark, it costs a grid slot and a tap, and it says nothing
the host does not already say aloud during setup, *here is the terminal*.

**The deletion exposed the real gap: nothing said how the map opens at the station.** The T card is
printed paper. The phone cannot sense that it is standing next to it, and the shipped NO SIGNAL
screen reached the live map through a RETRY that succeeded unconditionally, which was a fixture
standing in for a mechanism nobody had ruled.

**The mechanism is the scan.** The map opens when the SCAN button is pointed at the T card, which
is how every other place in the house is entered: a Subroutine at its marker, a meeting at the
meeting card (D-121). **The scan is the station's check-in** (M11, *the station generates its own
check-in*), so the reader is recorded at a known place exactly as the rule intends, and **it is the
claim on the exclusive lock**. A scan of the T card while someone else is reading is refused on the
scan path, beside NOTHING FOR YOU HERE and NOT A MARKER: `ONE READER AT A TIME` / *The terminal
already has a reader. Others may stand here, but only one person reads.* That refusal is new copy
and awaits the owner's eye. The live view and the timelapse both carry the motion budget row,
because reading is an uncompletable task.

**The slot stays empty**, as PHONE's did, so the grid keeps its shape for both roles. Page 1 is now
FILES, NOTES, MESSAGES, an empty slot, then SETTINGS, CAMERA and two empty slots.

| site | what it says now | what replaces it |
|---|---|---|
| `gdd.md:171` steer | *Terminal reads NO SIGNAL everywhere but the map station* | the map opens by scanning the T card |
| `gdd.md:470` | *Everywhere else, the Terminal app reads NO SIGNAL* | there is no Terminal app. The station is reached by scanning |
| `gdd.md:944` | the Terminal row of the device-state table | deleted |
| `epics.md` stories 3.3 and 8.1 | *NO SIGNAL elsewhere* | the scan opens the map, and a second reader is refused |
| `ScreensSpringboard.kt` | the TERMINAL tile | deleted, slot empty |
| `ScreenId.TermNo`, `Flow.kt:184` | NO SIGNAL and its unconditional RETRY | deleted. A T-card acquisition pushes `TermLive` |

### D-200 · **D-077, D-093 and the house thread stand against the build — decided**

Three places where the shipped code had drifted from a ruling. **The ruling wins in each.**

- **The reconnecting screen dims.** D-077 names it among the concealment screens. The build had
  narrowed that to the revoked screen alone, arguing that a reconnecting phone needs to be noticed.
  The narrowing was never ruled, and a reconnecting phone lit brighter than its neighbours is exactly
  the flare D-077 exists to prevent.
- **The scan ring does not pulse.** `ringPulse()` animates the ring on the full amber field, which is
  a local animation the core never emitted (D-093) on the one screen that is already a lamp.
  **Widened by the owner 2026-11-13: the incoming-call screens go steady too.** CALLING and
  INCOMING pulsed by the same function for the same non-reason, on every phone the house rings at
  once. `ringPulse()` is deleted, so nothing is left to call.
- **The house is one conversation.** Messages lists one row per sender, and the build drew the house
  twice, at 21:02 and 21:01, which reads as two different numbers. It is one row now, showing its
  newest text and opening a thread that holds all four, the 21:01 line *Do not attempt the exterior
  doors.* included. D-105's reason for drawing both rows identically (dimming the older one would
  read as a read mark) does not arise with one row. The header still counts messages, not rows.

**Also confirmed, not changed:** the notice copy *Resident MARCUS was unreachable 21:04–21:07* stays.
D-187 narrows when a notice fires, to deliberate evasion only, and says nothing against the
sentence, which is `gdd.md:440`'s own example. And **D-168's carry state is still unbuilt**: the
mockup now draws it on the work-order widget (CARRYING / DISK 01 / TO RACK / NOTHING ELSE OPENS
UNTIL IT IS DELIVERED, opening Files), and that copy also awaits the owner's eye.

### D-201 · **The unlock text is a player's voice. The sign-off is the house's — decided**

Two senders had been drawn on the phone, HOUSE and NUMBER WITHHELD, and neither document said why.

**NUMBER WITHHELD is a player.** The text that tells a Resident their next Subroutine is open
reads as another player telling them they can go: **`you're up`**, lower case, in the register of
every other personal text on the phone. **The number is withheld because the dependency is never
named** (`gdd.md:278`): a sender's name would hand out a confirmed-innocent identity. **And the
words never say why**, because the unlock is identical whether the upstream player finished or was
revoked (`gdd.md:279`), and a text that said *done* would be a lie on one path and a tell on the
other. The random unlock delay (`gdd.md:284`) is what keeps its timestamp from saying it instead.

**The house signs off as HOUSE.** *Thank you for your cooperation.* on the Insiders' ending and
*Unfortunate.* on the Residents' ending both come from HOUSE, the same sender as the thread. There
is one house and it has one name.

| site | what it says now | what replaces it |
|---|---|---|
| the quiet notification and the lantern card (`Notifications.kt`) | `NUMBER WITHHELD` / *A subroutine is available to you.* | `NUMBER WITHHELD` / *you're up* |
| both endings' sign-off card (`PanelModel.kt:1445`) | `NUMBER WITHHELD` | `HOUSE` |

---

## Revision 42 — the panel, as the mockup draws it

*Ruled by the owner 2026-11-12, at the close of the mockup redesign. The redesign changed how every
screen is drawn, and none of it reached this log: the palette, the faces, the canvas and the layout
rules lived only in `tools/design-mockup/kit.py`, while `Design.kt` still documents the system they
replace. One entry, because it is one system.*

### D-202 · **The panel is five amber steps, two faces and a 360 canvas — decided**

**The old ramp could not be read.** `Design.kt` set body text in `#8A5C12`, which is 3.6:1 on
black, and drew its hairlines and edges in `#46300B` and `#241806`, at 1.7:1 and 1.2:1: lines that
existed in the code and barely reached the eye. The redesign was for legibility, and legibility
starts with the ramp.

**The dark ramp**, on true black:

| value | on `#000` | carries |
|---|---|---|
| `#FFC759` | 13.6:1 | ink: headings, live values, the one solid block |
| `#E39A22` | 8.9:1 | secondary ink, and the whole ink of a medium screen |
| `#C08A2E` | 6.9:1 | dim text, and the ceiling of a concealment screen (D-077) |
| `#8A5C12` | 3.6:1 | lines: button edges, inactive borders. **Never text** |
| `#5A3D0E` | 2.1:1 | hairlines: list rules, spent segments. **Never text** |
| `#150E04` | — | the fill of the room you are standing in, and the dark stripe of the stairs hatch |

**Text uses the top three only**, and all three clear 4.5:1 on black and still clear 6.3:1 on the
`#150E04` fill. **`#46300B` and `#241806` are retired, and so is `#0B0703`** (`Amber.Well`), which the
mockup never draws; if the port finds a screen that still needs it, that screen comes back here
rather than keeping the value quietly. Bone, slate and caution are unchanged, value for value: they
belong to screens played with the house lights on, and nothing about them was failing.

**One text is below the top three, and it is named so it stays the only one:** the ACCESS REVOKED
headline on the revoked screen, `#8A5C12` at 56px (3.6:1, which clears the 3:1 that large text
needs). It recedes on purpose, under its own instruction line in `#C08A2E`: the screen is a
concealment screen, and what the revoked player needs to read is what to do, not what happened.

**Two faces, split by reading rather than by kind.** **VT323 carries everything a player reads:**
headings, numbers, buttons and reading text, at 22px and up. **Two smaller sizes, and only these:**
a caption runs 18–20px (the hold note, a setup hint, a widget line such as OF 7 ASSIGNED), and a
map's room labels and chips run 17px, because a 10 × 12 plan at 360 has no room for 22. **Silkscreen is for 12px labels only**, the status strip and the small
heading over a section, chrome learned once and then not read. This reverses `Design.kt`'s split,
which gave Silkscreen every label and VT323 only quantities, and put most of the screen in the face
that is hardest to read small.

**The canvas is 360 units wide**, up from 300, and **every tap target is 56 or taller**: a thumb in
the dark finds a target by its size, never by seeing it. **The springboard's page bar is not a
target.** Paging is the horizontal swipe, and the two-segment bar only says which page is showing;
the mockup's 28px tappable bar becomes a plain indicator. Five setup controls drawn under 56 rise to
it, in the mockup and the port alike: the name chips (40), and the marker-remove chips, the
terminal's remove control and YOUR ONE LINE (52).

**Five layout rules**, all of them the light rules applied to layout:

- **Hairline lists, not boxed rows.** A list is rows divided by `#5A3D0E` rules. A box around every
  row multiplies lit edges for no information.
- **One solid block per shift screen.** The single filled amber area is the action. Everything else
  is line and glyph, because lit area is what carries across a dark room (`gdd.md:1195`).
- **No glow, gradients or local animation** (rule 5, D-093). The stairs hatch is two flat values in
  a hard stripe, which is a pattern, not a gradient.
- **Screens say little.** A headline and the one next action. The buzz and the light change have
  already said that something happened.
- **Variants of one screen share one layout**, and only the words change: register marker and its
  two refusals, the two remove screens, the scan refusals, the two incoming calls.

| site | what it says now | what replaces it |
|---|---|---|
| `Design.kt` `Amber` | Bright, Mid, Dim `#8A5C12` as body text, Faint `#46300B`, Edge `#241806`, Well `#0B0703` | the ramp above. `#8A5C12` becomes a line value; Faint, Edge and Well go |
| `Design.kt` `PanelType` | Silkscreen carries every label, VT323 anything read as a quantity | VT323 carries what is read, Silkscreen is 12px labels |
| `Design.kt` `DESIGN_WIDTH` | 300 | 360 |
| `gdd.md:1193`, `gdd.md:1211` | four amber luminance steps · one bitmap typeface | the ramp above · two faces, split as above |
| `gdd.md:621` | legible at the dimmest amber luminance step | legible in `#C08A2E`, the dimmest value text may use |
| `game-architecture.md:756` | `palette.kt`: 4 amber luminance steps | the ramp above |
| `tools/design-mockup` `s_play.py` `pagebar`, `s_setup.py:84`, `:123`, `:124`, `:248` | a 28px tappable page bar · setup targets at 40 and 52 | an indicator, not a button · 56 |

---

## Revision 43 — when the station comes free

*Ruled by the owner 2026-11-12, while building D-199. D-199 made the scan of the T card the claim
on the station's lock and said nothing about when the claim ends; `gdd.md:472` says only that the
station locks on any viewing. Building the lock needed the other half.*

### D-203 · **The Terminal's lock comes free four ways — decided**

**The reader leaves the view.** BACK, the dock, STOP THE PLAYBACK onward to the springboard, or the
motion budget closing it: any move off the live view and the timelapse ends the reading. Moving
between those two does not. The phone reports it with the actor and nothing else, as STOP NOW does,
because a reason would be a record of how somebody left the station.

**A meeting is called.** The map is never read at a meeting (`gdd.md:470`), and D-185 already ends
every running effect at the call. A lock that outlived the call would hand the next shift's station
to whoever was reading when it rang. No scan claims it while the meeting runs.

**The reader is put outside the system**, revoked or restrained. A seat that is out holds nothing,
whichever way it went out.

**The reader's connection drops.** A phone that is gone cannot say it stopped reading, so the house
says it on that seat's behalf, the moment the connection closes. Otherwise a flat phone would keep
the station for the rest of the round.

**Nothing announces any of the four.** The next scan of the T card finds the station free; a push
saying so would tell somebody who had been reading.

| site | what it says now | what replaces it |
|---|---|---|
| `GameState.reader`, `readerHolding()` | (new) | the lock; an out seat holds nothing |
| `Event.TerminalLeft`, `Intent.LeaveTerminal`, `IntentMessage.LeaveTerminal` | (new) | the reader leaving, by the phone or by the house for a dropped connection |
| `Rules.kt` `meetingCalled` | clears the jam and the isolations | also frees the station |
| `FlowModel.arrive` | — | leaving the station's two screens sends the leave |
| `LobbyOverTransport` `onLost` | releases the seat on the desk | also asks the rules to free the station |

---

## Revision 44 — the status strip moves under the Island

*Ruled by the owner 2026-11-13, porting the chrome in D-202's restyle. The mockup draws the strip
below a 36px camera band and fills the row; D-091 kept it in the Island's band with the middle
third empty. Both could not stand.*

### D-204 · **The status strip sits under the Island, laid out as the mockup draws it — decided**

**Measured, the mockup's strip cannot share the Island's band.** At 360 its right group (the ARMED
chip, the padlock on Files, the clock) starts 180 units into the 320-unit row, 198 without the
padlock; an iPhone 16's Island covers roughly units 102 to 218 of it. Kept in the band, the chip
runs 20 to 40 units under the pill, which is the fault D-091 was written to stop.

**So the strip moves down instead of shrinking.** It is drawn 34 tall directly under the top safe
area, 20 in from each side, with a `#5A3D0E` hairline beneath it, and the band beside the Island is
left as ground. A screen gives up 34 units of height for it: about 661 for content on an iPhone
16, against the 690 the mockup's screens were drawn for.

**D-091 is retired, not amended.** The empty middle existed because the row lived beside the pill.
Nothing sits beside the pill now, so the row is the mockup's: signal, carrier, then the chips and
the clock at the right. Its concession goes with it: the carrier slot is 169 units, and
UNREGISTERED, which D-091 cut to `UNREGISTER…`, is drawn whole.

**What the strip carries, from the mockup:**

- three signal bars, spent in play (`#5A3D0E`), lit on the bone screens;
- the carrier in Silkscreen 12, blank until a player is out, then REVOKED or RESTRAINED (D-078);
- the perimeter as a **worded chip**, ARMED in ink or DISARMED drained, rather than a bare iris;
- a padlock on Files and while carrying, the two places nothing else opens;
- the clock, VT323 22.

**The reception glyph and the battery go.** Neither said anything in the game, and both were lit
edges on the one row that is on screen all evening. On a concealment screen (D-077) the strip's ink
is `#C08A2E`, like everything else there.

**The lantern and the scan keep drawing their own**, inverted: the same row in black on amber,
without the clock, which the lantern draws large.

| site | what it says now | what replaces it |
|---|---|---|
| `Chrome.kt` `StatusBar`, `PanelFrame` | a 17-unit row in the Island's band, three zones | a 34-unit strip under the safe area, laid out as the mockup |
| `ScreensWork.kt` `InvertedStatusRow`, `ScreensArming.kt` `LockScreen` | two copies of an inverted row with a battery | one inverted strip |
| `PanelVals` `signalOn`, `signalOff`, `receptionInk`, `lockChip` | the old ramp; a reception mark; the lock on Files and the lantern | the strip's values in D-202's ramp; no reception mark; the lock on Files and while carrying |
| `StatusCarrierTest` | UNREGISTERED cut at ten characters | every carrier drawn whole |
| D-091 | the middle is empty on every device | retired |

---

## Revision 45 — what the 360 canvas left open

*Ruled by the owner 2026-11-13, when Step 3 of the mockup port finished at `b9c9e0b`. Moving the
canvas from 300 to 360 units kept every restyled screen pixel for pixel, and left six values that
were either written in the old units or that D-202 had sent back to the owner. Each was settled on
the recommendation put to the owner.*

### D-205 · **The smallest control is 44pt on the narrowest phone — decided**

`TAP_TARGET` was 36 units, written for the 300 canvas, where it cleared Apple's 44pt on the
narrowest phone in range (375 points across). On the 360 canvas the same 36 units is about 37.5pt.
**It rises to 44pt there: `44 × 360 / 375`, 42.24 units**, computed in the code from the canvas and
the phone width rather than typed as a number. Every wider phone makes it larger.

It is a floor, not a size. D-202 draws the kit's own targets at 56 and they are unchanged; what
the floor catches is a control whose drawing would leave it shorter. `MeetingInputTest` measures
the meeting's controls against **the constant itself**, so the floor the controls are built to and
the floor they are measured against cannot drift apart. That test had held 43.2 units, which is the
old 36 carried across unchanged (45pt); it now holds 44pt exactly, measured in whole pixels as
layout rounds them. No render moved.

| site | what it says now | what replaces it |
|---|---|---|
| `Controls.kt` `TAP_TARGET` | 36 units, about 37.5pt | `44 × 360 / 375` units, 44pt on a 375-point phone |
| `MeetingInputTest` `FLOOR` | 43.2, typed | `TAP_TARGET`, compared in whole pixels |
| Deallocate's column strip | 40 units, the block's width, about 41.7pt | `TAP_TARGET` with the block centred; strips sit `14 − (TAP_TARGET − 40)` apart, so no render moved (owner, 2026-11-13) |

### D-206 · **The cheat screens are drawn on the 360 canvas — decided**

The screen picker, the variant marker, the shutter chip and the haptic, camera and transport
benches were never restyled and were still written in 300-canvas units, so the flip drew them about
a sixth smaller. **Every length and type size in them is its old value × 1.2**, which puts them
back at the size they had; borders stay one unit, a hairline on either canvas. They are debug
surfaces, compiled out of release, and nothing about them is restyled beyond the scale.

| site | what it says now | what replaces it |
|---|---|---|
| `app/src/cheats` `Cheats.kt`, `HapticCheat.kt`, `ScanCheat.kt`, `TransportCheat.kt` | 300-canvas sizes | the same sizes × 1.2 |

### D-207 · **The glass keeps its 3.6 pitch — decided**

The mockup draws the scan lines at 3px on its 360 canvas. The port draws them at 3.6, the 3 old
units carried across unchanged in size. **The 3.6 stands.** Taking the mockup's 3 moves the glass on
every screen, and `ScanLinesTest`'s lit-ground check then reads the springboard's meter as text.
No code changes.

### D-208 · **The jam veil keeps its exact floor, under its own name — decided**

The veil's dimmest lit level was `Amber.Edge`, `#241806`, one of the values D-202 retired from the
panel's ramp. **It is not an ink there, it is a light level**: the dimmest step of what a jammed
surface throws into a dark room, and rule 5 makes light game state. So **it keeps its exact value**,
moved out of `Amber` into `JAM_VEIL_FLOOR` in `Static.kt`, where nothing else draws with it. The
veil was rendered before and after at four frames and the PNGs are byte-identical; a test pins the
value, so a later tidy toward the ramp has to come back through this log.

| site | what it says now | what replaces it |
|---|---|---|
| `Static.kt` `ink` | `lerp(Amber.Edge, …)` | `lerp(JAM_VEIL_FLOOR, …)`, the same `#241806` |
| `StaticRenderTest` | (new) | the floor is pinned to `#241806` |

### D-209 · **The live terminal's counts are Ink, Mid and Dim — decided**

TermLive's counts dim through three staleness bands. The port drew HALL and STUDY in the retired
`#46300B` and `#241806`, and LIVING in `#8A5C12`; the mockup draws HALL and STUDY in `#8A5C12`, which
D-202 says is never text. **Fresh, stale and cold are `#FFC759`, `#E39A22` and `#C08A2E`**, the three
values text may use: KITCHEN and GARAGE fresh, LIVING stale, HALL and STUDY cold. The bands still
read as a step down, and every count now clears 4.5:1.

With D-208 that leaves `Amber.Faint` and `Amber.Edge` with no reader, and **both are deleted**, as
D-202 said they would be. The rest of `Amber`'s old dark ramp still has readers and stays.

| site | what it says now | what replaces it |
|---|---|---|
| `PanelModel.kt` `Plan.terminalCounts` | Bright, `Amber.Dim`, Faint, Edge | `Ramp.Ink`, `Ramp.Mid`, `Ramp.Dim` |
| `Design.kt` `Amber.Faint`, `Amber.Edge` | kept for two readers | deleted |

### D-210 · **The Interrupt and Drift player marks stay `#FFC759` — decided**

D-202 makes `#E39A22` the whole ink of a medium screen, and Interrupt and Drift are medium screens.
Their player marks (the caught sweep and the tapped place) are drawn in `#FFC759`, and they stay
there: what the player did is brighter than what the house drew, the grammar every Subroutine
screen uses, and the tests pin it. **This is the one exception to a medium screen's single ink.**
No code changes.

---

## Revision 46 — the route from either end

*Ruled by the owner 2026-11-13, with revision 45. The house graded a Signal Trace route only from
the source, so the port drew the two ends differently; the mockup draws them alike, and D-194's
briefing already says TAP NODE TO NODE FROM ONE TO THE OTHER.*

### D-211 · **Signal Trace accepts the route traced from either end — decided**

**The house grades the route and its reverse as the same answer.** A player who starts at the far
end and walks the shortest route back has done the work, and the screen gives them no way to know
which end the house had called the start. Nothing else loosens: a wrong route is wrong either way
round, a route missing an end is refused, and the right route at the wrong card is still not an
answer (D-110).

**It is decided in `core`, where the house grades**, as a second equality in `grades` and nothing
more. It is a pure function of the question and the entry, so a replay grades every route as it
was graded (rule 4). The reverse of the unique shortest route is itself unique, so `traceSeeds`
deals the same seeds it did.

**The fake is unchanged and stays identical** (rule 8). An Insider's entry goes through the same
`grades`, so the same route, either way round, gets the same verdict on both phones. `returned`
still builds its effects once, outside every branch (rule 1): a reversed route that lands emits
exactly what the forward one does, and one that does not land emits exactly what any refusal does.
Nothing on the phone asks which end a route started from, so no client path branches on it.

**Then the ends are drawn alike**, as the mockup draws them: both filled in the screen's ink, and
the sink's inner square goes. A screen that still marked a start would be asking a question the
house no longer grades.

| site | what it says now | what replaces it |
|---|---|---|
| `core` `Subroutines.kt` `grades` | Signal Trace graded by equality with the route from the source | equality with that route or its reverse |
| `ScreensSubroutines.kt` `SignalTraceScreen` | the source filled, the sink a square inside a square | both ends filled, alike |
| `gdd.md:599` | tap node-to-node from source to sink | superseded: from one end to the other, either end first |
| `SubroutineDrawTest`, `SubroutineVerdictTest` | (new) | forward and reversed routes grade alike; the Insider's fake grades them as the Resident's work does |

## Revision 47 — the radio's key and what a phone says it heard

*Ruled by the owner 2026-11-13, scoping the contact handshake that lets a Revoke and a Revoked
report land on real phones. Until now `ContactMade` and `RevokeReported` were minted only by the
bench. This is unit 1 of that build: the tokens and the upload. The house's resolver, the wiring
and the radio itself are later units.*

### D-212 · **Each phone gets a seed at arming, advertises an HMAC token, and uploads at one size — decided**

Five rulings, all from the scoping recommendations:

1. **Each phone gets a seed at arming.** The seed rides a schema row of its own, `ContactSeedIssued`,
   addressed to its seat and no other phone. The row names both living classes and nothing else,
   and like every row it fails closed: with the row deleted, the seed reaches nobody.
2. **Tokens are an HMAC of the seed and the 15-second window index** (D-032/D7's rotating token,
   now given its function). The phone and the house compute one function from the same two inputs.
   The house can derive every seat's seed, so it can turn any token back into a seat. Nobody else
   can link one window's token to the next.
3. **Every live phone scans continuously and uploads sightings on one cadence, in one message shape,
   padded to one size, whatever its role or armed state.** A phone that heard nothing sends exactly
   as many bytes as one that heard everybody. An observer of the Wi-Fi must not be able to tell
   armed from unarmed, or Insider from Resident.
4. **Revoked phones advertise but never scan.** This is D-008's rule, restated for the radio that
   now exists.
5. **No armed bit, role or seat ever goes on the radio.** The token's inputs are the seed and the
   window and nothing else. Whether a contact was a Revoke is the house's conclusion, drawn from
   the two phones' state, never something a phone announces (D-121 corrected).

**The seed is drawn from the round's seed and the seat index, through an HMAC and not through
`Draw`.** `Draw` is a linear generator, so a phone handed raw outputs of it could run it backwards
to the round's seed, and the Insiders are drawn from that seed. The HMAC is one-way. It is seeded
and recorded (rule 4), because the round's seed is already on `RoundArmed`, so a replay issues
every phone the same seed and the house holds nothing new. The seed never reads a role, so a
role-swapped round puts byte-identical seeds on every wire.

**The upload is a report of fact, not a request** (rule 10). `IntentMessage`'s header already
names the contact as deliberately absent from it: the seat it would name is one only the house can
resolve. So the phone reports what it heard, as tokens, signal strengths and windows, and the
contact is the house's conclusion. It travels as `RoundBody.Sighted`, a body member of its own,
like `Fire`. **It carries no sequence number**: a counter's digits grow, and a field that grew
with what a phone had done is one a reader of the Wi-Fi could measure. It has 24 fixed-width slots.
Empty slots get filler keyed off the seed, which resolves to no seat, and all slots are sorted so
the filler sits nowhere in particular.

**D-136 stands as written.** It says which sightings the spectator map is built from, which is
those made while a player performs, and that is the house's filter. It does not say when the radio
runs. Continuous scanning feeds it the same sightings and more.

**Open, and raised rather than decided:**

- **The transport is plain `ws`.** A reader on the Wi-Fi can read every frame, not only measure
  it, including `ArmRevoke` on the way up and `RoleRevealed` on the way down. Ruling 3's observer is
  defeated above this unit, so padding meets the ruling on size and cannot meet it on content.
- **`StaticOpened.seed` can be run back to the round's seed.** The round's seed times an odd
  constant, plus the tick times another, is invertible modulo 2⁶⁴ by anybody who guesses the tick.
  A modded phone could then redraw the Insiders and, now, every seat's contact seed. This predates
  this unit.
- **What a Revoked phone uploads.** Ruling 3 says every *live* phone uploads, and ruling 4 says a
  Revoked one never scans. If it stops uploading, the Wi-Fi sees who is out. If it keeps uploading
  heard-nothing messages on the cadence, it does not. That is for unit 3 to wire, and the message
  allows either.
- **The token's length (16 bytes) and the cadence (1 s)** are provisional for the radio (unit 4)
  and for the contact-to-dark measurement (unit 7).

| site | what it says now | what replaces it |
|---|---|---|
| `model` `Contact.kt`, `Hmac.kt` | (new) | `ContactSeed`, `ContactWindow`, `ContactToken`, `Sighting`; `ContactTokens.of` and `seatOf`; HMAC-SHA-256 in common Kotlin |
| `model` `Effect.ContactSeedIssued`, `EmitSchema` | (new) | one per seat, addressed to that seat; row: both living classes |
| `model/protocol` `RoundMessage.ContactSeedIssued`, `RoundBody.Sighted`, `SightingUpload` | (new) | the seed with the seat removed; the fixed-size upload and its packing |
| `core` `contactSeedFor`, `armed` | (new) | the seed run in the arming burst, after the role reveal |
| `harness` `Recording.HEADER` | `recording/18` | `recording/19`: one effect a version 18 build cannot name |
| `app`, `ui` consumers | (new) | named no-ops: silent, never on `PanelState`, and the house drops uploads until unit 2 |
| `ContactTokensTest`, `SightingUploadTest`, `ContactSeedTest`, `ContactDifferentialTest`, `EmitSchemaTest` | (new) | RFC 4231 and outside vectors; rotation; inversion; one seed per seat and its own, fails closed; seeds and uploads unmoved by a role exchange; one frame length for every phone |

## Revision 48 — the house's contact resolver

*Unit 2 of the contact handshake. The owner's 2026-11-13 rulings 2–4 (D-212's scoping) are
applied here; the numbers they left open are chosen, and two of them are placeholders. Nothing is
wired yet: the arrival queue and the bench's stand-ins are unit 3's.*

### D-213 · **The house turns sightings into contacts, mints every pair, and leaves the outcome to the rules — decided, two tolerances provisional**

**Where it lives.** `core` `Contacts.kt`, `resolveContacts`, above the rules beside `routeScan`
and for its reason: deciding what kind of fact an arrival is needs knowledge `reduce` never holds,
here the token book. It is pure. The tick and the house's window are arguments, and what it
remembers between arrivals is a value (`ContactMemory`), so a replay of the same arrivals rebuilds
the same events and the same memory.

**What it mints.** A mutual sighting mints `ContactMade` in both directions, for every pair,
whoever they are. A one-sided sighting of a Revoked seat mints `MeetingCalled` with
`RevokeReported`, which is exactly what the bench's stand-in mints. One side is enough because a
Revoked phone advertises and never scans (D-008). Where several apply, every one is minted, sorted
by the seat that acts or calls and then the seat it names, with a contact before a report between
the same two, and the rules decide. In a huddle that means an armed Insider's contact reaches the
lowest seat they touched. A second report in one arrival is refused by the gate and recorded, like
any second meeting.

**It never reads a role or an armed state, and the field list is the guarantee.** The only state
it sees is `ContactHouse`: the round's seed, its seats, and who is Revoked. That last one is read
because the report *is* that fact meeting a phone. `ContactResolverDifferentialTest` runs one set
of arrivals against role exchanges, a role toggle and arms, and requires identical events and
memory on every seat.

| tolerance | value | standing |
|---|---|---|
| RSSI floor | **−35 dBm**, at or above is contact | ⚠️ **placeholder until unit 5.** D-008 and `gdd.md` make the radius a hard constraint: unit 5 may tighten it towards contact, per model pair if it must, and never loosen it towards *nearby* |
| RSSI ceiling | 0 dBm or more is not a reading | decided. CoreBluetooth reports 127 for *no RSSI*; ungated, it would pass as the strongest contact in the house, from anywhere |
| windows | the house's and the one before it | decided. A token from two windows back is stale; one from the window ahead is refused, so an advertiser whose clock runs ahead past an edge is lost for its skew and heard by the next upload |
| mutual | both halves arrive within **2 s** | ⚠️ **placeholder.** Measured on arrival ticks: one 1 s upload interval each side, plus jitter. Unit 7 sets it, together with the cadence |
| debounce | one contact per unordered pair per **5 s**, whatever was minted | ruled (D-212's scoping, ruling 4) |
| duplicates | same seat, same slot list, within two windows | derived. Filler is keyed off the upload's counter, so honest uploads never match. After two windows every token in a repeat is stale anyway |

**The token book replaces a `seatOf` call per slot.** `seatOf` derives every seat's token to answer
one, so per slot it costs two HMACs per seat. Across 24 slots, 18 seats and every phone once a
second, on a host that is itself a phone, that breaks rule 7's budget. The book is built by
`ContactTokens.of` once per window, in seat order, keeping the first match, and a test pins it to
`seatOf`.

**Open, and raised for unit 3 rather than decided** (the rules and the schema are unchanged):

1. **A touch by an unarmed Insider puts a message on their socket.** `Rules.contact` emits
   `AbilityFired` to the actor on every contact (rule 1), and the row ships it to a living Insider
   whether armed or not. Until now only the bench minted contacts, always from an armed Insider.
   Once unit 3 wires the resolver, every touch sends one frame to an Insider's socket and none to
   a Resident's. Nothing shows on screen and nothing buzzes, but the frame's presence follows the
   role, on a transport D-212 already records as readable. **This needs a ruling before unit 3
   wires.**
2. **An armed Revoke outlives its holder going out.** Only a contact clears `cooldownArmed`, and
   `Rules.contact` does not ask whether the actor is still living. An Insider who armed and was
   then Restrained or Revoked can still land a Revoke if their phone uploads a mutual sighting.
3. **A report's caller is never checked for being living**, not here and not at the gate. That is
   harmless while out phones never scan. It stops being harmless if the pending question of what a
   Revoked phone uploads is answered with sightings.
4. **A refused event the resolver minted must not trigger D-097's re-push.** No phone asked for it.
5. **An Insider who stays beside the player they Revoked for longer than the debounce reports
   them**, which calls a meeting under the Insider's own name. That is the gesture working as
   designed (D-121 corrected: one gesture, outcome by state). Noted for the playtest.
6. **A contact during a meeting is admitted**, so a Revoke armed before the call can land in the
   meeting room. This predates the resolver.

| site | what it says now | what replaces it |
|---|---|---|
| `core` `Contacts.kt` | (new) | `resolveContacts`, `ContactGate`, `ContactMemory`, `ContactBook`, `ContactHouse` |
| `ContactResolverTest`, `ContactResolverDifferentialTest` | (new) | the gate on both sides; mutual and one-sided; window edges; debounce; duplicates; filler and strangers; a huddle of three; overlaps in seat order; replay; output unmoved by role or arm |

## Revision 49 — nothing a phone holds leads back to the round's seed

*Ruled by the owner 2026-11-15 (R6 of the contact handshake's second set): clients get a one-way
derived seed. It is fixed before unit 3, because unit 3 puts contact seeds on every phone and the
leak below hands a modded phone all of them.*

### D-214 · **Every value the house derives from the round's seed goes through an HMAC under its own label — decided**

**The leak, confirmed.** `StaticOpened.seed` was `roundSeed * 0x5DEECE66D + tick * 0x9E3779B9`
modulo 2⁶⁴, and it reaches every living phone. The first constant is odd, so it has an inverse. A
phone that guesses the firing tick computes `(jamSeed - tick * 0x9E3779B9) * inverse` and checks
the answer against its own contact seed (D-212), which is exact. A thousand guesses take
milliseconds. With the round's seed it reruns `insidersFor` and knows who the Insiders are, and it
reruns `contactSeedFor` and holds every seat's radio key. `RoundSeedSecrecyTest` runs this attack.
Against the old derivation it recovered the round at the true tick and redrew the Insiders.

**The same flaw, one step removed.** Every `Draw` stream started at `roundSeed * constant + domain`,
so every stream in the round was an affine function of one secret. Its outputs reach phones: a
seat's work order (stations, active set, anchors, kinds), each opened Subroutine's question, the
Insiders' Isolate picker (the active set), and each Egress's nodes and type. These outputs are
truncated and reduced, so recovering the seed from them takes a search rather than a division. A
Drift's 512-way draw exposes nine bits of the state at a time, and the low bits of an LCG modulo 2⁶⁴
evolve without the high ones, so the search is on the order of 2⁴² rather than 2⁶⁴. That attack was
not run. It is harder than the jam's seed but not out of reach, and the fix is the same.

**The ruling, applied.**

1. `oneWaySeed(roundSeed, purpose, index)` is HMAC-SHA-256 keyed by the round's seed (eight
   big-endian bytes, as `contactSeedFor` already keys it) over the purpose's label and the index as
   eight bytes. Its output is the digest's first eight bytes. `Hmac.kt` from D-212 is reused.
2. **Each purpose has a label of its own**: `insider-count`, `insider-seats`, `stations`, `active`,
   `anchors`, `kinds`, `instance`, `egress-nodes` under `someone-is-home/draw/…/1`, and
   `someone-is-home/static-noise/1`. No label is a prefix of another. Indices: seat for anchors and
   kinds, entity for instances, tick for Egress and Static. These replace the old strides.
3. **`Draw` has no constructor that takes a raw state.** It takes the round's seed, a purpose and
   an index, and starts from `oneWaySeed`. The generator after that is unchanged. A new stream gets
   the HMAC whether or not its author knew why.
4. **`StaticOpened.seed` is `oneWaySeed(roundSeed, StaticNoise, tick)`.** The message, its shape,
   its audience, its timing and the lamp are unchanged. Only the value is new, and the client
   draws noise from it exactly as before.
5. `contactSeedFor` keeps its own label and its four-byte index (D-212), so the pinned token
   vectors stand.

**What moves.** Every round re-draws: for a given arming row, the Insiders, stations, work orders,
questions, Egress nodes and noise all differ from what version 19 drew. No message changed shape and
no client receives anything new. **Recording format 20.**

**Why none of the three instruments caught it.** The differential harness holds the seed fixed and
exchanges roles, so a value that encodes the seed is byte-identical in both runs. That is the
property the harness certifies, and here it is the leak. The harness also rewrites the Insider list
without touching the seed, so in its swapped run the seed no longer even predicts the Insiders. The
schema allowlist checks which kind reaches which phone, never what a value can be solved for. Radio
sniffing sees the radio. **This is a fourth class: a symmetric value that is a function of the
secret.** It is guarded at the derivation. `RoundSeedSecrecyTest` asserts that no purpose's stream
origin, and no jam seed as the rules emit it, is affine in the round's seed (any `a·s + b` satisfies
`f(x+d) − f(x) = f(y+d) − f(y)`). It asserts that the labels are distinct and prefix-free, and it
runs the attack above.

**Allocation.** One HMAC per `Draw`: per seat twice at arming, once per scan, and once per Egress
or Static. That is a few hundred bytes per event, on the host, off the blackout path (rule 7).

**Open, and raised rather than decided:**

- **Where the round's seed comes from.** The only source found, `RoundCheat.seed()`, falls back to
  `Random.nextLong()`, which is not a cryptographic source. An HMAC hides a 64-bit key only as well
  as the key is unpredictable. The release build's seed should come from the platform's secure
  random. Not changed here.

| site | what it says now | what replaces it |
|---|---|---|
| `core` `Arming.kt` `Draw` | `seed * GOLDEN + domain`, numeric domains and strides | `SeedPurpose`, `oneWaySeed`; `Draw(roundSeed, purpose, index)` starts from the HMAC |
| `core` `Rules.kt` `staticSeed` | `seed * 0x5DEECE66D + tick * 0x9E3779B9` | `oneWaySeed(seed, StaticNoise, tick)` |
| `core` `Scanning.kt`, `Egress.kt` | `seed + id * stride`, `seed + tick * stride` | `Draw(seed, Instance, id)`, `Draw(seed, EgressNodes, tick)` |
| `harness` `Recording.HEADER` | `recording/19` | `recording/20`: every round re-draws |
| `RoundSeedSecrecyTest` | (new) | the jam-seed attack; no derived seed affine in the round's seed; labels distinct and prefix-free; the jam still replays |
| `HostArmingTest` Egress test | fired from seat 0, which the old seed drew as the Insider | fired from the seat the seed drew |
| `HandOverLoopTest.playOneCard` | tapped eight times and pressed SUBMIT whatever the screen was | stops where the screen goes inert. The re-draw dealt a Handshake first, and the bench filed five hand-overs that no phone could file |

## Revision 50 — the round's seed comes from the secure source

*Step 0 of unit 3 of the contact handshake, closing D-214's open item. Every derivation in D-214 is
one-way, so the round's seed is now the whole of the secrecy, and it was drawn from a generator
anybody can run.*

### D-215 · **A round's seed and a seat's token are drawn from the platform's secure source — decided**

**Where a round's seed originates, all of it.** A search of every module for a seed being made
found one live source and three that are not live:

| origin | what it is | what it draws from now |
|---|---|---|
| `RoundCheat.seed()` (cheats; debug and playtest) | the seed every evening armed by a thumb gets, and the bench's when no switch names one | `drawRoundSeed()`. It was `kotlin.random.Random.nextLong()` |
| the bench's `SOMEONES_HOME_ROUND_SEED` switch | a rehearsal naming its own evening, so it can be repeated | unchanged. A named seed is meant to be known |
| `harness` `Fuzz` | the fuzzer's generated rounds, from its own seeded stream | unchanged. A test input, never an evening |
| tests' fixed seeds | pinned rounds | unchanged |

The release variant arms nothing yet (its root shows Boot), so it has no seed source of its own.
When it gains one, `drawRoundSeed()` is the only function in `app` that draws one.

**The ruling, applied.**

1. `platform` gains `secureRandomBytes(count)`, an `expect` with an iOS `actual` over
   `SecRandomCopyBytes(kSecRandomDefault, …)`, and `secureRandomLong()`, eight of those bytes
   big-endian. **It throws rather than falling back.** A secure source quietly replaced by a weaker
   one is the failure nobody sees, in the one value whose quality cannot be seen. The draw happens
   before a round exists, on the host's phone, in a lit room, so a LIGHTS OUT that does nothing and
   says why on the authority's log is the loud direction (rule 6).
2. `app` gains `drawRoundSeed()`, and `RoundCheat.seed()` falls back to it.
3. **Seat tokens move too.** They are not seeds, but a token a modded phone could guess is a resume
   as somebody else's seat, and that seat's pushes, its role among them. Both mints, the lobby's and
   the transport bench's, are now `mintSeatToken()`: sixteen secure bytes as 32 hex characters.
4. **It is still recorded.** The value drawn is the value `RoundArmed` carries, so a recording replays
   the evening exactly. Nothing about derivation changes, and no recording format moves.
5. **Guarded by `seedSourceCordon`**, a source rule over every source set of `app` and `platform`
   that compiles into a phone, cheats included. It fails on any use of `kotlin.random`. A rule over
   the whole module rather than one function, for the arming cordon's reason: a guard that names one
   call site is satisfied by the call one line up. `SecureRandomTest` covers what a test can: the
   source answers, never repeats in a thousand draws, and moves all 64 bits of a seed.

**There is no JVM or desktop actual.** `platform` has iOS targets only, and nothing on a JVM arms a
round. Adding a JVM target to `platform` for this one function would need a JVM actual for every
`expect` in the module. When `platform` gains a JVM target, the actual is `java.security.SecureRandom`.

**Open, and raised rather than decided:**

- **The host's log prints the seed.** `RoundAuthority.arm` writes `ARMING — SEED n`, and the debug
  and playtest roots send it to the device console. From the seed, `insidersFor` redraws the Insiders
  and `contactSeedFor` every radio key. The arming note's own comment already says a file naming the
  Insiders gives the game away, and this line is that file one function call removed. It is also, today,
  the only place an evening's seed is kept, because no build writes a recording yet (`RECORDING_ON`
  has no consumer). It is left as it is: removing it loses replay, keeping it keeps the leak, and the
  choice is the owner's.

| site | what it says now | what replaces it |
|---|---|---|
| `platform` `SecureRandom.kt`, `SecureRandom.ios.kt` | (new) | `secureRandomBytes`, `secureRandomLong`; `SecRandomCopyBytes`, throwing on failure |
| `app` `RoundSeed.kt` | (new) | `drawRoundSeed()`, `mintSeatToken()` |
| `RoundCheat.seed()` | `?: Random.nextLong()` | `?: drawRoundSeed()` |
| `LobbyHouse.start`, `TransportCheat.startHost` | `SeatToken(Random.nextLong()… + Random.nextLong()…)` | `mintSeatToken()` |
| `buildSrc` `SeedSourceRules`, `SeedSourceCordonTask`; root `build.gradle.kts` | (new) | `seedSourceCordon` on `app` and `platform`, under `check` |
| `SecureRandomTest`, `SeedSourceRulesTest` | (new) | the source answers, never repeats, moves every bit; the rule catches the old fallback and leaves the secure draw alone |

## Revision 51 — every touch is answered at one size

*R1 of the owner's 2026-11-15 rulings, closing D-213's open item 1. Unit 3 of the contact handshake
wires the resolver to the live house; before it does, a touch must stop putting a frame on a socket
whose presence follows the role.*

### D-216 · **Every touch puts one frame of one size on each phone that touched; only an armed Insider's is `AbilityFired` — decided**

**What it was.** `Rules.contact` answered every actor with `AbilityFired`, and that row ships to
living Insiders only. With the resolver minting both halves of every touch, an unarmed Insider's
touch would have put a frame on their socket and a Resident's would have put nothing on theirs.

**The ruling, applied.**

1. **A new effect, `ContactAnswered(seat)`,** addressed to the actor. Its row is both living classes,
   written out as a two-class set, and like every row it fails closed: a kind spelled otherwise
   reaches nobody. Not the out classes: a Revoked phone never scans (D-008), so it is never the actor
   of a contact, and a seat that went out between the two halves of one touch is denied on the
   round-state axis, which carries no role.
2. **`Rules.contact` answers each actor exactly once.** An actor holding an armed Revoke is told
   `AbilityFired(cooldownStarted = true)`, because it really was spent. Every other actor, an unarmed
   Insider and every Resident, is told `ContactAnswered`. The resolver mints both halves of every
   touch (D-213), so each phone that touched is the actor of exactly one event and gets exactly one
   frame. `RevokeLanded` is still built on every contact and narrowed by its row, unchanged.
3. **The two are one length on the wire.** `RoundMessage.ContactAnswered` carries `pad`, always the
   nine-character constant `PAD`, so that once each is pushed, encoded and carried, it and
   `AbilityFired(true)` are the same number of bytes. `ContactAnswerSizeTest` pins this through the
   real `RoundWire` and `TransportWire` at sequence numbers from 1 to a million.
4. **It moves nothing.** No screen, no lamp, no buzz: `Flow` and `HouseBuzzer` name it as a no-op.

**Measured on sockets.** `TouchFrameSizeTest` stands up a real host, five real clients over
loopback and the real `EffectWire`, and puts both halves of three touches through `admit`. Each
toucher's socket received one frame per touch: **120 bytes** for an unarmed Insider, its Resident
partner, two Residents, and an armed Insider. The bystanders received nothing.

**Rule 1 still holds, and this is why.** The branch is on the actor's own armed state, which their
phone already knows because they held the tile down. Nothing reads the target: an armed touch on a
live player, a Revoked one, another Insider or oneself is answered identically, and so is an
unarmed one. One consequence, stated: a Revoke armed into a running clock arms nothing, so the touch
after it is answered `ContactAnswered`. That tells the actor about their own clock and nothing else,
which is what a Tier-2 fire into a running pool already tells them (`cooldownStarted = false`,
T1·8 / T2·7). `AbilityRulesTest` and `OpeningCooldownTest` are amended to say so.

**Recording format 21**: one effect a version 20 build cannot name, and every unarmed touch's
effect transcript changes.

**Open, and raised rather than decided** (each changes what a client receives beyond R1):

- **The Revoked target's frame.** When an armed touch lands, the target is sent `RevokeLanded`, which
  is 95 bytes against the 120 every other touch frame is, and in some seat orders it also receives
  its own `ContactAnswered` first. On a readable or merely measurable Wi-Fi this marks the phone that
  was just Revoked. Padding `RevokeLanded`, or sending it in place of that seat's answer, is a change
  to what a client receives.
- **The push sequence number grows per seat.** `RoundBody.Push.seq` is in every frame, and its
  digits grow with how many messages that seat has been sent. Insiders are sent kinds Residents are
  not (`IsolateTargetsOffered` at arming and on every Egress), so over a round an Insider's sequence
  can cross a power of ten before a Resident's, and every frame after is a byte longer.
- **Frame counts by role exist elsewhere.** `IsolateTargetsOffered` reaches Insiders only, so the
  arming burst already has one more frame on an Insider's socket than on a Resident's. R7's
  encryption hides content, not counts.

| site | what it says now | what replaces it |
|---|---|---|
| `model` `Effect.ContactAnswered`, `EmitSchema` | (new) | addressed to the actor; row: both living classes |
| `model/protocol` `RoundMessage.ContactAnswered` | (new) | the seat removed, `pad = PAD`, `AbilityFired(true)`'s length on the wire |
| `core` `Rules.contact` | `AbilityFired(actor, true)` to every actor | `AbilityFired` to an armed actor, `ContactAnswered` to every other |
| `ui` `Flow`, `app` `HouseBuzzer`, `harness` `Transcript` | (new) | a named no-op, silent, one transcript row |
| `harness` `Recording.HEADER` | `recording/20` | `recording/21` |
| `ContactAnswerSizeTest`, `TouchFrameSizeTest`, `RulesTest`, `AbilityRulesTest`, `ArmingTest` | (new, amended) | one length at every sequence; one frame per toucher on real sockets; the armed and unarmed answers |
| `RecordingTest` (`ReplayTest.kt`) *order changes the state but never the visible effect stream* | the two streams equal line for line | the actor's one answer compared as one answer, every other line equal |

## Revision 52 — the resolver is wired, and the bench touches phones

*Unit 3 of the contact handshake, with the owner's 2026-11-15 rulings R2–R5 and R8. The resolver
(D-213) is joined to the house's arrival queue, phones upload on the cadence, and the bench's two
minted stand-ins are replaced by a simulated radio.*

### D-217 · **Uploads reach the resolver on the arrival queue; out phones upload filler; disarm, living reporters, no contacts at meetings, no answer to a refused contact — decided**

**The wiring.**

1. **Host.** `LobbyHouse.onFrame` puts `RoundBody.Sighted` on the same ordered queue as every request
   (`Arrival.Sighted`); its one consumer calls the new `onSighted` seam, which the app root fills with
   `RoundAuthority.sighted`. That unpacks the upload, runs `resolveContacts` with the house's memory,
   the next tick and the house's window, and admits every event it mints in the order minted.
   Memory resets at arming.
2. **The window is cut from the wall clock on both ends.** `platform` gains `wallClockMillis()`
   (`CLOCK_REALTIME` on iOS). The monotonic clock starts at each phone's own boot, so window indices
   cut from it would differ by hours between phones. The wall clock is network-kept, and the
   resolver already accepts the window before the house's (D-213). Nothing in the rules reads it.
3. **Phone.** `platform` gains `ContactRadio` (advertise, scan, drain, silence) and `NoRadio`. Unit 4's
   iOS radio implements the interface. `app` gains `ContactUploader`, fed every push the inbox applies.
   It takes the seed from `ContactSeedIssued`, goes out on `RevokeLanded` or `RestrainedTakeover`, and
   stops on `RoundEnded`. Each beat it rotates the advertised token when the window turns and sends one
   `SightingUpload.pack` up the phone's own socket. `RoundOverTransport` requires a radio, for the
   buzzer's reason, and runs the cadence (`upload()`). Every phone today holds `NoRadio`, so it
   already uploads filler on the cadence.
4. **R8: an out phone keeps uploading on the same cadence, at the same size, with nothing heard.** It
   stops scanning and keeps advertising (D-008), and drains and drops what its radio held, so a
   reading taken just before the push cannot ride out later.

**The bench.** `RoundCheat.theRevoke` no longer mints `ContactMade` or `MeetingCalled(RevokeReported)`.
`SimulatedAir` (in cheats) holds positions. RSSI is −30 dBm at 5 cm, falling 20 dB a decade and
floored at −100, so contact under the −35 floor is about 9 cm. `SimulatedRadio` implements
`ContactRadio` over it, and `ContactAct` walks one phone to another, holds them together for two
beats, and walks back. **Across processes there is no shared air**, so in the rehearsal `HouseAir`
runs a `ContactUploader` per seat on the host, with that seat's own seed, and puts the uploads into
`RoundAuthority.sighted`. That stands in for the hop up each socket and for nothing below it. The
in-process tests carry every upload over a real socket.

**R2: being Restrained or Revoked disarms an armed Revoke.** In `Rules.contact` the target leaves
`cooldownArmed` when it is Revoked, and in `takeover` the Restrained seat leaves it. Nothing is
emitted either way.

**R3: only living players can report.** The gate refuses `MeetingCalled(RevokeReported)` from an out
caller, with a new reason, `ReporterOut`. This is admissible for `AbilityNotTheirs`'s reason: no honest
phone produces it, because an out phone does not scan and uploads nothing heard. It sits above the
meeting checks so that a forgery is recorded as one.

**R4: a refused contact is answered with nothing.** `RoundAuthority.sighted` admits through its own
door, `concluded`. A refusal is recorded in `refusals` and in `contactRefusals`, and is noted. Nothing
goes down any socket, D-097's re-assertion included: D-097 answers a phone that asked for something
stale, and nobody asked for this. D-097's re-push on a refused request is still unbuilt. When it is
built in `heard`, it cannot reach a contact.

**R5: contacts are ignored during meetings.** The decision log was checked first and says nothing
otherwise: D-121 and D-133 govern where a report may be made, D-183 refuses Tier 2 at a meeting,
D-185 ends running effects at the call, and D-213 item 6 only recorded the behaviour. So the gate
refuses `ContactMade` while a meeting runs (`MeetingAlreadyRunning`). The refusal applies to every
seat, every role and both halves of a touch, in the D-183 shape, and it is recorded. A report at a
meeting was already refused as a second meeting.

**Recording format 22**: a new refusal reason, and contacts at meetings and from disarmed seats now
reduce differently.

**Open, and raised rather than decided:**

- **An already-reported Revoked player can be reported again.** After the meeting, a couch phone
  still advertises (D-008). A living phone held against it reports it, and the rules call a new
  meeting. The resolver reads `revoked`, not `newlyRevoked`. Which one it should read is a design
  question.
- **The rehearsal's radio hop is stood in for** (above). Two-phone verification of the upward hop is
  unit 4's.
- **A Revoked phone's uploads are filler only, but its advertising continues on the cadence.** That
  is D-008 as written. Unit 6's sniffing should confirm that the advertisement looks the same before
  and after.

| site | what it says now | what replaces it |
|---|---|---|
| `platform` `ContactRadio.kt`, `WallClock.ios.kt` | (new) | `ContactRadio`, `RadioReading`, `NoRadio`, `wallClockMillis` |
| `app` `ContactUploader` | (new) | seed, out and ended off the pushes; one upload a beat, filler when out (R8) |
| `app` `RoundOverTransport` | no radio | a required radio, `beat()`, `upload()`, the uploader fed every applied push |
| `app` `LobbyHouse` / `LobbyOverTransport` | `RoundBody.Sighted -> Unit` | `Arrival.Sighted` on the ordered queue, `onSighted` seam |
| `app` `RoundAuthority.sighted`, `contactRefusals` | (new) | resolver, memory, window; refused contacts recorded, never answered (R4) |
| `app` cheats `SimulatedAir`, `SimulatedRadio`, `ContactAct`, `HouseAir` | (new) | the fake radio; the act; the rehearsal's stand-in hop |
| `RoundCheat.theRevoke` | minted `ContactMade` and `RevokeReported` | the act through the resolver |
| `core` `Rules.contact`, `takeover` | an armed Revoke outlived its holder | disarmed on Revoke and on Restraint (R2) |
| `core` `admit`; `model` `RefusalReason.ReporterOut` | no living check; contacts admitted at meetings | R3 and R5 refusals |
| `harness` `Recording.HEADER` | `recording/21` | `recording/22` |
| `ContactRadioBenchTest`, `ContactRulingsTest`, `ContactUploaderTest` | (new) | a round to an ending through the radio; R2, R3, R5; R4 on sockets; R8 |
| `WallClockTest` | (new) | the window clock is the epoch in milliseconds, not the time since boot |
| `ContactResolverTest` (a pair hearing a Revoked seat) | one refusal, the second meeting | two: the touch's other half (R5) and the second meeting |
| `ContactResolverDifferentialTest` (the rules diverge) | the huddle's armed Revoke lands though a report in the same arrival sorted first | shown on a huddle that hears no Revoked seat; with one, the report's meeting refuses the touch (R5) |

## Revision 53 — the radio is CoreBluetooth

*Unit 4 of the contact handshake. Every phone held `NoRadio` until now; this puts the real radio
behind `ContactRadio` (D-217). Written and compiled for a device; nothing here has run on a phone
yet. That is the owner's device check (`_bmad-output/implementation-artifacts/ble-unit4-device-check.md`).*

### D-218 · **The radio advertises a 16-bit service UUID and the token, turns its scan over every 250 ms into a fixed ring, and reports an outage to nobody yet — built, three items raised**

**The shape.** `platform` `BluetoothRadio.kt` holds every decision, in common code: `SwitchboardRadio`
(what was asked, what each manager's power allows, and the hardware put wherever both agree),
`SightingRing` (the coalescing buffer) and `RadioHardware` (the five-call seam CoreBluetooth sits
behind). `BluetoothRadio.ios.kt` is the managers, their delegates and one serial queue. Both managers
call back on that queue and the app's calls are put on it, so the switchboard is single-threaded;
`drain` is the one synchronous hop, once a beat. Nothing runs on the main thread. `Cheats.kt` holds
`bluetoothContactRadio` where it held `NoRadio`, logging to stdout only, never to the rig's on-screen
log: a radio line on a screen is a screen that changed for a reason the house did not author.

**What goes on the air: two service UUIDs and nothing else.** No local name, so iOS sends none. One
UUID is the game's, identical on every phone; the other is the window's token, carried whole as a
128-bit UUID. **The game's UUID is 16 bits because nothing else fits.** iOS gives a foreground app 28
bytes and accepts only service UUIDs and a local name. A 128-bit list costs 2 + 16n bytes, so a 128-bit
game UUID beside a 128-bit token (34) does not fit, and iOS moves what does not fit to its overflow
area, where only a scanner asking for that exact UUID finds it, and no scanner can ask for a token it
does not know. A 16-bit list costs 4, so the pair fits in 22. The scanner filters on the game's UUID,
which also keeps every other Bluetooth device in the house from waking the callback (rule 7).

**Rotation** is the uploader's (D-217): the first beat after a window boundary calls `advertise`, which
stops the old advertisement and starts the new. So the air turns within one beat (1 s) of the boundary,
and the resolver's previous-window allowance (D-213) covers the lag. `ContactRadioRotationTest` reads it
off a clock it turns by hand.

**Off before arming (D-067).** The managers are built at the first `advertise` or `scan(true)`, which the
uploader makes only once `ContactSeedIssued` arrives. Before that nothing exists to put anything on the
air, and no Bluetooth prompt can be raised in the lobby. `silence` at `RoundEnded` takes both off.

**Revoked phones advertise and never scan (D-008, story 9.4).** Scanning follows the last `scan` call
and nothing else: no reset, power cycle or scan-cycle tick starts it again, and a reading in flight
when `scan(false)` lands is dropped on arrival, so it cannot ride out in an upload (R8's reason).

**Allocation (rule 7).** Every CoreBluetooth callback into Kotlin/Native allocates before our code
runs: the bridge wraps the manager, the peripheral, the advertisement dictionary, the RSSI number,
the UUID list, two UUIDs and the token's data, about eight objects. **Estimate: 300 bytes a callback,
at most 500.** Our code adds nothing: the dictionary key is read once, the token's bytes go into a
buffer allocated once, and the ring is two fixed arrays.
- **The scan rate, chosen: duplicates filtered, scan stopped and restarted every 250 ms**
  (`ContactAdvertisement.SCAN_CYCLE_MILLIS`). iOS reports each phone once per scan, so at most four
  reports a second per phone heard, whatever iOS's own advertising rate. The scan is never off for longer
  than a restart, so living phones still scan continuously (D-212 ruling 3).
- **At 7 peers: at most 28 callbacks a second, about 8.4 KB/s (14 KB/s at 500 B).** The drain adds about
  1 KB/s (up to 14 readings a beat at a window edge, each a reading, a token and a 32-character string),
  and the device log's HEARD line about 0.2 KB/s. **About 10 to 16 KB/s, 2 to 3 per cent of the
  0.5 MB/s budget.** With duplicates allowed and no cycle, the rate would be iOS's: if that is 30
  reports a second per phone, 63 to 105 KB/s.
- The ring (`SightingRing`) is the upload's 24 slots: one per token, the strongest reading kept. Full,
  the weakest gives way to a stronger newcomer. **iOS's 127 is no reading**: kept only when nothing
  better was heard for that token, so it never displaces a real one. Left to `pack`, which keeps the
  highest number per token, 127 would win and the house would reject it, losing the contact.
- **`AllocationBudget` is not wired to the radio's queue** (TODO in `SwitchboardRadio.tally`). Its
  counter counts the whole process, only moves when a collection runs, and allocates a `GCInfo` on
  every read. Sampled from the radio's queue it would cost more than the radio and still not say what
  the radio spent. The radio logs its callback count every 10 s instead; unit 8 measures bytes per
  callback on a phone and multiplies.
- Not this unit's, noticed: `SightingUpload.pack` runs up to 24 HMACs and builds its slots as strings
  every second on every phone. Unit 8 should measure it beside the radio.

**Errors: silent to the player; not yet loud to the house (rule 6).** Both managers are built with
the system power alert off, which is a dialog iOS would otherwise lay over the lamp. A manager going
Off, Unauthorized, Resetting or Unsupported is logged and reported to `SwitchboardRadio`'s `outage`,
and the lamp, screens and haptics never hear of it. On power returning, the radio takes up what was
asked of it without being asked again. **No event exists to carry an outage to the house.** The
architecture names `BleUnavailable` in its error table, but it was never built; nothing in `Event`,
`RoundBody` or `RoundMessage` carries one. D-187 has since ruled that a radio that failed earns no
notice and a permission revoked mid-round does. So what goes up, and what the house does with it, is
raised rather than invented. `outage` is the one place it would go.

**Info.plist: unchanged.** `NSBluetoothAlwaysUsageDescription` is already there in the house's voice,
written for the permission prompt (*"The house senses which phones are near each other in the rooms.
No connection leaves this house."*), and it is accurate for this radio. **No background modes were
added**; that is the owner's call. What their absence costs: when a phone locks or leaves the app,
iOS suspends it within seconds and its radio goes quiet. It stops advertising and scanning, so it
cannot Revoke or be Revoked, report or be reported, and its socket goes quiet too. With the modes,
the app would keep running but iOS would move both service UUIDs to the overflow area. No scanner
could read the token, so a backgrounded phone still could not be resolved, and D-032's worry (a
backgrounded phone becoming identifiable by something nobody designed) would apply in full. Unit 6's
sniffing is where that gets settled.

**Raised, not decided:**

1. **The 16-bit service UUID (`5348`) is a placeholder.** 16-bit UUIDs belong to the Bluetooth SIG,
   and this one is unassigned rather than ours: fine in a living room, not on the App Store. The ways
   out both change what the radio emits: a SIG member UUID, or a 128-bit game UUID beside a token cut
   to fit (which moves the truncation into `ContactTokens.of`, as `ContactToken`'s KDoc foresaw).
2. **The outage event** (above): what a phone sends when its radio is down, whether a permission
   revoked mid-round is told apart from a radio that failed (D-187), and what the house says.
3. **Auto-Lock.** The app never disables the idle timer, so a phone left alone dims and then locks
   during a round, which suspends the radio and the socket. Disabling it is a lamp decision as well
   as a radio one: the dim before the lock is a fade nobody authored (rule 5). The device check sets
   Auto-Lock to Never by hand.

Also open: the iOS Bluetooth address rotates on its own schedule, not ours, so it can link one window's
token to the next. That is unit 6's sniff. If iOS does not reset its duplicate filter on a scan
restart, a device shows each phone reported once and never again; the fallback is one line (duplicates
on, no cycle), at the cost above.

| site | what it says now | what replaces it |
|---|---|---|
| `platform` `BluetoothRadio.kt` | (new) | `ContactAdvertisement`, `RadioHardware`, `SightingRing`, `SwitchboardRadio`, `expect bluetoothContactRadio` |
| `platform` `BluetoothRadio.ios.kt` | (new) | CoreBluetooth managers on one queue, the hot-path callback, the scan-cycle timer |
| `app` cheats `Cheats.kt` | `NoRadio` | `bluetoothContactRadio`, stdout only |
| `platform` `ContactRadio.kt`, `RoundClient`, `HouseAir` KDoc | "until unit 4" | the radio that exists |
| `SightingRingTest`, `SwitchboardRadioTest`, `ContactRadioRotationTest` | (new) | coalescing and 127; pre-arm off, one advertisement, the Revoked no-scan switch through resets, outages, silence; rotation within one beat of every boundary |

## Revision 54 — the Revoked target's frame, the host log, and the repeat report

*The owner's 2026-11-15 rulings S1, S3 and S4, on the items D-216 and D-217 left open. S2, fixed-size
frames for every message, is folded into the transport encryption (R7) and is not here.*

### D-219 · **The Revoked target's frame is padded to the touch's size, the host log prints no seed, and a Revoked player is reported once — decided, two items raised**

**S1: `RevokeLanded` is padded to the touch's frame size.** `RoundMessage.RevokeLanded` was an empty
object, 95 bytes carried against the 120 of `AbilityFired(true)` and `ContactAnswered`. On a Wi-Fi that
can be read or merely measured, the phone whose touch came back short was the phone that had just been
Revoked. It now carries `pad`, always the twelve-character constant `PAD`, by `ContactAnswered`'s
arrangement (D-216). Nothing reads the pad; the screen, the lamp and the haptics are unchanged.
`ContactAnswerSizeTest` pins it to `AbilityFired(true)`'s length at every sequence from 1 to a million,
and `TouchFrameSizeTest`, on real sockets, now measures the target too: **120 bytes for every toucher,
the Revoked target included.**

**S3: the host log prints no seed.** `RoundAuthority.arm` logged `ARMING — SEED <seed>`, and every
secret in the round is an HMAC under that value (D-214): the Insider draw and every contact seed, so
every token on the air. The line keeps its counts and loses the seed. Every log and print path in
`app` (`commonMain` and `cheats`) and `platform` was read for seed, token and key material. That was
the only one. Two lines that come close, kept:

- `BluetoothRadio.ios.kt` logs the first eight hex characters of the advertised token on each phone's
  own console. The token is on the air in the clear by design (D-212), and an HMAC output gives up
  neither its seed nor its key.
- The transport bench (`TransportCheat`) logs a carried body on the client. It is a cheat-only rig on
  its own port that carries pings, not a round.

The guard is `ContactRadioBenchTest.theHostLogCarriesNoSeedTokenOrKeyThroughARound`. It arms through
production's `armAtLightsOut` with a seed from the secure source, plays the round to an ending through
the fake radio on real sockets, with real seat tokens from `mintSeatToken`, and searches every line
the house, the arming and the act wrote. It looks for the seed in decimal (signed and unsigned), in hex,
and in base64 of both byte orders (standard and URL-safe, padded or not). It looks for each seat's
contact seed and each seat token in hex and base64. Matching ignores case. It also checks its own
detector against the removed line, so a detector that sees nothing cannot pass.

**S4: a second report of an already-reported Revoked player is refused.** A couch phone keeps
advertising (D-008), so after its meeting any living phone held against it called another. `GameState`
gains `reported`, written by a `RevokeReported` meeting call and never cleared inside a round. `admit`
refuses a report of a seat on it with a new reason, `AlreadyReported`. It sits beside R3's
`ReporterOut` and above the meeting checks, so a repeat during its own meeting is recorded as a repeat.
It clears `RefusalReason`'s entry condition the ordinary way: the meeting rang every phone with the
reported seat's name on it (D-121). Rule 1 holds: the refusal is recorded (`refusals`,
`contactRefusals`), and because it is the radio's it is answered with nothing (R4). On the sockets, no
phone's frame count moves.

**Recording format 23**: one refusal reason a version 22 build cannot name, and a repeat report now
reduces differently.

**Raised, not decided** (each changes what a client receives beyond padding):

1. **In one seat order the Revoked target gets two frames.** The resolver mints both halves of a touch
   in seat order (D-213). When the target sits below the Insider, the target's own half is admitted
   first. It is still living and unarmed, so it is answered `ContactAnswered`, and then the Insider's
   half sends `RevokeLanded`. That is two frames to the target where every other toucher gets one,
   both 120 bytes since S1. `TouchFrameSizeTest` asserts it as it is, so a fix shows up as one. The
   fixes all change what a client receives: mint the armed half first, drop the target's answer, or
   send `RevokeLanded` in its place.
2. **"Reported" is read literally.** A seat Revoked before a meeting called from the meeting card is
   not on `reported` afterwards, though the room saw it walk in (D-134), so it can still be reported
   once. Refusing that too would mean reading `newlyRevoked` instead (D-217's open item), which is a
   design question. `ContactRulingsTest` pins the literal reading.

| site | what it says now | what replaces it |
|---|---|---|
| `model/protocol` `RoundMessage.RevokeLanded` | empty object, 95 bytes carried | `pad = PAD` (12 characters), 120 bytes; `RevokeLanded.Padded` |
| `app` `RoundAuthority.arm` | `ARMING — SEED <seed> · …` | `ARMING — <counts>` |
| `model` `GameState.reported`, `wasReported`; `RefusalReason.AlreadyReported` | (new) | the seats a meeting has reported; the S4 refusal |
| `core` `Rules.meetingCalled`, `admit` | a couch phone could be reported at every touch | recorded on the call; a repeat refused, answered with nothing |
| `harness` `Recording.HEADER` | `recording/22` | `recording/23` |
| `ContactAnswerSizeTest`, `TouchFrameSizeTest` | the target's frame "outside R1" | the target at the touch's length; the lower-seated target's two frames, asserted as they are |
| `ContactRadioBenchTest`, `HostLogSecrets` | (new) | the host log through a round, every encoding; a repeat report on sockets |
| `ContactRulingsTest` | R2, R3, R5 | and S4: refused after its meeting, a fresh seat still reportable, a card meeting reports nobody |
| `ContactResolverTest` (a pair hearing a Revoked seat) | the second report refused `MeetingAlreadyRunning` | refused `AlreadyReported`: S4 sits above the meeting checks |

## Revision 55 — one frame per touch in either seat order, and the screen kept awake

*The owner's 2026-11-15 rulings T1 and T4, on the items D-219 and D-218 left open. T3 (UUID `5348`
stays for the playtest; a permanent ID goes on the pre-release list with R7 and S2) needs no code.*

### D-220 · **The first half of a touch answers the whole touch, so a Revoked target is sent `RevokeLanded` in place of its answer — decided**

**What it was.** Each half of a touch answered its own actor, and the armed half also sent the
target `RevokeLanded`. The resolver mints the halves in seat order (D-213), so when the target sat
below its Insider the target's own half came first: still living and unarmed, it was answered
`ContactAnswered`, and then the Insider's half sent `RevokeLanded`. Two frames to the one phone whose
count should least differ (D-219 raised item 1).

**The ruling, applied.**

1. **The first half reduced answers both phones and lands any Revoke either holds.** The actor's
   armed Revoke is read first, so two armed Insiders resolve as they did when the lower seat's half
   came first. A Revoke the target holds lands on the actor in the same half. Every armed phone that
   touched spends its Revoke, landed or not, and R2's disarm falls out of the same line.
2. **The second half is recognised and changes nothing.** `GameState.touches` holds the touches
   answered at the tick being reduced, as unordered pairs; a contact at a later tick drops them. Both
   halves are always minted at one tick, so whether a half is the second is a fact about the arrival,
   never a role or an arm. It emits nothing in every case.
3. **Each phone is sent two effects and the boundary keeps one.** Its answer (`AbilityFired` if it was
   armed, `ContactAnswered` otherwise; living rows) and `RevokeLanded` (out rows). A phone the touch
   Revoked is out and gets the Revoke landing; every other phone is living and gets its answer. The
   stream is the same four effects whether the Revoke landed, hit a seat already out, was never armed
   or was aimed at the actor, so `AbsentEffectIsTheLeakTest` still compares equal line for line, and
   a self-touch keeps an other-touch's shape (`gdd.md:393`). A phone never hears itself, so a
   self-touch is forged-only and its second answer lands on the forger alone.
4. **The answer reads one thing, the phone's own armed state.** Nothing reads the other phone's.

**Measured on sockets.** `TouchFrameSizeTest` now makes the touches in both seat orders, unarmed and
armed, and asserts every socket's frame count per touch: **1 for each phone that touched, 0 for each
bystander, in all four cases**, every frame 120 bytes. The lower-seated target is sent exactly
`RevokeLanded`; the higher-seated Insider exactly `AbilityFired`. `TouchAnsweredOnceTest` (core)
asserts the same deliveries off the rules, and a huddle of three around an armed Insider: each phone
in two touches is delivered two effects.

**One consequence, stated.** A Revoke held by the higher seat now lands in the lower seat's half. In
`ContactResolverDifferentialTest`'s huddle, seat 2's report of the couch seat 6 sorts after its own
half of the touch with the armed seat 5. Until T1 the Revoke waited for seat 5's half, sorted after
the report's meeting, and R5 refused it. Now it lands first, seat 2 is out, and R3 refuses its report.
That is what already happened when the Insider sat below seat 2, so the outcome no longer depends on
which of the two sits higher. In huddles generally a phone is also no longer short a frame for a
touch it made after going out mid-arrival.

**Recording format 24**: no row changed shape, and every touch reduces to a different transcript.

| site | what it says now | what replaces it |
|---|---|---|
| `model` `GameState.touches`, `Touch` | (new) | the touches answered at the current tick, unordered pairs |
| `core` `Rules.contact` | each half answers its actor; the armed half sends the target `RevokeLanded` | the first half answers both phones and lands either Revoke; the second changes nothing |
| `harness` `Recording.HEADER` | `recording/23` | `recording/24` |
| `TouchFrameSizeTest` | the lower-seated target's two frames, asserted as they were | one frame per socket per touch, both seat orders, armed and unarmed |
| `TouchAnsweredOnceTest` | (new) | deliveries per seat: both orders, unarmed, a huddle, the same pair at a later tick |
| `RulesTest`, `ArmingTest`, `ReplayTest` | two effects per contact | four, the pair once for each phone |
| `ContactResolverDifferentialTest` (the report in the huddle) | R5 refuses the armed touch | the Revoke lands in the first half; R3 refuses the report |

### D-221 · **The screen is kept awake from arming until the round ends or the player leaves, and nothing else changes — decided**

**The ruling, applied.** `platform` `ScreenWake` over a one-property `IdleTimer`; on iOS
`UIApplication.idleTimerDisabled`, written on the main thread. `RoundOverTransport` takes one,
required, for the radio's reason (D-217). It holds at `ContactSeedIssued`, which is arming for every
seated phone whatever its role (`keepsTheScreenAwake`, one named question), and releases at
`RoundEnded` and when the round half is told the phone has hung up. `RoundSeam` gains `left()`,
reached from `LobbyOverTransport.hangUp`, which every exit shares (leave, close the house, the house
closed). Release puts back what hold found, so a phone whose owner had already disabled the timer is
left that way; both calls are idempotent, so a resume's re-asserted seed is harmless.

**No background modes** were added. A phone whose side button is pressed, or that leaves the app,
still suspends; that is a person's act and D-187's notice is about it.

**Checked against D-077, D-093 and D-118: it changes no light level.** The property stops Auto-Lock
and the OS's dim before it. It does not touch `UIScreen.brightness`, draws nothing and animates
nothing, so the lamp is still a pure function of state (rule 5). D-077's concealment screens dim
their own chrome, which is app-drawn and untouched. D-093's Island ring is not built and this adds
none. D-118's two dims are the app's, the Egress and the opening text, and are untouched. What the
idle timer used to do was dim a phone nobody had touched for a while, on the OS's schedule: a fade
nobody in the game authored, and a third light change the closed vocabulary never had. Holding the
timer removes that one and adds nothing.

**Found on the way: a phone that left mid-round kept its radio on.** `leave` hung up the socket and
told the round half nothing, so the radio advertised and scanned on; with the screen held awake it
would have kept the screen on too. `left()` now silences both.

| site | what it says now | what replaces it |
|---|---|---|
| `platform` `ScreenWake.kt`, `ScreenWake.ios.kt` | (new) | `IdleTimer`, `ScreenWake` (hold, release, restore), `NoIdleTimer`, `deviceIdleTimer` |
| `app` `RoundOverTransport` | no idle timer | a required `ScreenWake`; held at `ContactSeedIssued`, released at `RoundEnded` and `roundEnded()` |
| `app` `RoundSeam.left`, `LobbyOverTransport.hangUp`, `RoundClient` | a hang-up reached no round | the round half silences the radio and releases the screen |
| `app` cheats `Cheats.kt` | (none) | `ScreenWake(deviceIdleTimer())` handed to the round |
| `ScreenWakeTest`, `ScreenAwakeTest`, `RoundEndedPushTest` | (new) | restore and idempotence; held through the funnel from arming to the end and to a hang-up; a leave reaches the round half |

## Revision 56 — a radio that goes down is announced at the next meeting

*The owner's 2026-11-15 ruling T2, on D-218's raised item 2. The mechanism is built; what a phone
draws from the notice is proposed below and waits on the owner.*

### D-222 · **A phone reports its radio going down in one fixed body, the house records it, and announces it to every phone at the next meeting — built; the copy is the owner's**

> ⚠️ **Superseded in part by D-223 (2026-11-15).** The announcement below broke D-187: it named
> every outage, resets included. Since D-223 the house names only a radio switched off or its
> permission withdrawn, in the house notice D-187 already covers; a reset is recorded for the
> authority and named nowhere; a meeting where nobody evaded draws nothing; the body carries a cause;
> `RadioFaultsAnnounced` and its schema row are gone, folded into `NoticeIssued`. The upward report,
> the debounce and the arrival queue stand. The proposed copy A and B and the *data is complete*
> line below are withdrawn.

**What goes up.** `RoundBody.RadioFaulted`, a `data object`: `{"type":"radioFaulted"}`, the same
bytes from every phone whatever its role, whether it is out, and whether the radio was switched
off, had its permission withdrawn or reset. No kind, no time, no seat (the seat is the
connection's). `ContactUploaderTest` pins the string.

**When it goes up: once per fault onset.** `SwitchboardRadio` calls the radio *down* while either
manager is Off, Unauthorized, Resetting or Unsupported, and the change from up to down is one onset,
however many callbacks follow: a Bluetooth switch-off reaches both managers, a reset passes through
Resetting and Off. `faultBegan()` hands the onset over once; the uploader sends it on the next beat.
Both managers On is up again, so a second switch-off is a second onset. Only a round's radio reports
(before the first advertise there is nobody to tell), and a radio still down when the next round
arms is a fault of that round. On iOS `faultBegan` is a second synchronous hop onto the radio's
queue, beside `drain`, once a beat, and allocates nothing. Every phone reports alike, out or not.

**What the house does.** `LobbyHouse.onFrame` puts it on the ordered arrival queue
(`Arrival.RadioFaulted`), `RoundAuthority.radioFaulted` logs `RADIO FAULT — SEAT n` (loud to the
authority) and admits `Event.RadioFaulted(at, seat)`, a fact (rule 10). The rule records the seat on
`GameState.radioFaulted` (the round's seats, each once, in seat order) and **emits nothing, in every
case, to anybody** (rule 6: the player's screen, lamp and motor never hear of it). A report before
arming or after the round is refused at the gate and recorded.

**The announcement reuses the meeting's notices moment.** The house already has one place it
announces: the notices screen (`ScreenId.Notice`, D-035, D-105), which every phone reaches when the
check-in gate closes and `MeetingPhaseOpened(Discussion)` moves it off the assembly screen. Its words
are still the design's fixture (*Resident MARCUS was unreachable 21:04–21:07*), and nothing has fed
it until now. At that same transition the rules now emit `Effect.RadioFaultsAnnounced(seats)` just
ahead of the phase push, then empty the list.

- **Schema row `RADIO_FAULTS_ANNOUNCED` to `LIVING + OUTSIDE`**, exactly the phase push's audience:
  the living and the out are both standing at the meeting. It fails closed like every row: deleted,
  the notice reaches nobody; narrowed to Residents, the phones it missed would be the Insiders.
- **Sent at every meeting, empty when no radio went down** (rule 1), so its arrival is the gate
  closing, which the room already knows, and only its content says anything. The content is the
  same on every phone.
- **It names seats and no role.** Every phone reports a fault identically, so being named says which
  radio went down, never who holds it. `RadioFaultTest` shows an Insider's fault and a Resident's
  produce the same stream on every phone, bar the seat named.
- `ui` `Flow` names `RadioFaultsAnnounced` as a no-op and `HouseBuzzer` keeps it silent (the gate's
  own push buzzes). **Nothing on any screen changes yet**: that is the copy question below.

**Raised with the owner: D-187.** D-187 narrowed house notices to deliberate evasion and said
*genuine radio loss earns no notice*, because a notice naming a player whose hardware failed reads
to the room like a notice naming a cheat. T2 announces all three faults. Switching Bluetooth off and
withdrawing the permission are a person's act (D-187's own scope); a reset is not. The body carries
no kind, so if Reset should not be announced, the radio would have to stop reporting it rather than
the house filtering it.

**Proposed copy, in the house's administrative register, for the owner to choose** (not built):

- **A, named, the D-035 sentence:** *"Resident MARCUS's radio went dark. Occupancy data since the
  last meeting is incomplete."* With two: *"Residents MARCUS and ELLIOT …: radios went dark."*
- **B, unnamed, D-187's caution kept:** *"A radio in this house went dark. Occupancy data since the
  last meeting is incomplete."* With two: *"2 radios in this house went dark."* If B, the effect
  narrows to a count, because a client-facing type should not carry names it never shows (rule 3).
- **And for a meeting with no fault:** either *"Occupancy data since the last meeting is complete."*
  or the notices screen is skipped. Skipping it would make its presence the news, so the first is
  recommended.

**Found on the way:** the state row (`Transcript.render`) had left out S4's `reported` list; it
now carries `reported`, `touches` (D-220) and `radioFaulted`.

**Recording format 25**: one event and one effect a version 24 build cannot name, every meeting's
transcript changes, and the state row gains three columns.

| site | what it says now | what replaces it |
|---|---|---|
| `model` `Event.RadioFaulted`, `GameState.radioFaulted` | (new) | the fact; the seats awaiting the notice |
| `model` `Effect.RadioFaultsAnnounced`, `EmitSchema` | (new) | every seat; row `LIVING + OUTSIDE` |
| `model/protocol` `RoundBody.RadioFaulted`, `RoundMessage.RadioFaultsAnnounced` | (new) | the fixed upward body; the seats, projected |
| `core` `Rules` `radioFaulted`, `checkedIn`; `Admission` | (new) | recorded silently; announced and emptied at the gate |
| `platform` `ContactRadio.faultBegan`, `SwitchboardRadio`, `BluetoothRadio.ios.kt` | outages logged, the house not told | one onset per outage, collected each beat |
| `app` `ContactUploader`, `RoundOverTransport`, `LobbyHouse`/`LobbyOverTransport`, `RoundAuthority.radioFaulted`, cheats `Cheats.kt`, `RoundClient` | (new) | up on the beat; the queue; the house's log; the console's `RADIO NOTICE` line |
| `ui` `Flow`, `app` `HouseBuzzer` | (new) | a named no-op, silent, until the copy is ruled |
| `harness` `Transcript`, `RecordingText`, `Recording.HEADER` | `recording/24` | the rows; `recording/25` |
| `RadioFaultTest`, `SwitchboardRadioTest`, `ContactUploaderTest`, `ContactRadioBenchTest` | (new) | silent report, every class told, empty notice, role-blind; one onset per outage; the fixed body; on sockets |

## Revision 57 — the house notice names evasion, and nothing else

*The owner's 2026-11-15 ruling on D-222's raised D-187 conflict. Built, except the words for a named
player, which wait on the owner.*

### D-223 · **A radio switched off or its permission withdrawn is named in the house notice; a reset is recorded and named nowhere; a meeting with no evasion draws nothing — built**

**D-187 governs and D-222 is brought under it.** A house notice reports detectable deliberate
evasion and nothing else. Of the radio's outages, two are a person's act and two are not:

- **Named:** Bluetooth switched off (`poweredOff`) or its permission withdrawn (`unauthorized`)
  during a round. Every grant is given at setup, in the light (D-161), so one missing mid-round is
  something a person did.
- **Not named:** `resetting` and `unsupported`. Recorded for the authority, in the house's log and
  the round's recording, and shown as disconnection on the monitoring surfaces and nowhere else,
  which a radio that is down does by itself.

**The phone says which, and nothing more.** `RoundBody.RadioFaulted` carries `cause`, `person` or
`device`, two serial names of six letters, so the one body is the same length either way and from
every phone (`ContactUploaderTest` pins both strings). `SwitchboardRadio` decides the cause once, at
the onset (`causeOf`): Off or Unauthorized is a person's, Resetting or Unsupported the phone's own,
and **the phone's own failure wins** a disagreement between the managers. The cause holds for the
outage, so a reset that passes through Off is still a reset. ⚠️ Limit, reasoned and not observed: a
manager whose reset callbacks arrive after the other has already come back up starts a second onset,
which would read as a person's if it opened on Off. A reset cannot be induced on demand at the
device check.

**The rules draw the line in one function** (`namedByTheNotice`). A person's outage puts the seat on
`GameState.unreachable`; a device's is admitted, so it is recorded, and moves nothing. Neither emits
anything to anybody when it arrives, so the two are not told apart then (rule 1). The house's log
says which: `RADIO FAULT — SEAT n · PERSON · NAMED AT THE NEXT MEETING`, or `· DEVICE · RECORDED ·
NAMED BY NO NOTICE (D-187)`.

**One notice, not a radio notice beside it.** The house had one notice path, the notices screen
(`ScreenId.Notice`, D-035) with D-035's sentence as its fixture, and no effect fed it: lock and
backgrounding, D-187's other two, are not yet detectable. D-222's `RadioFaultsAnnounced` was the
first feed and was named for the radio. It is now **`Effect.NoticeIssued(unreachable)`**, schema row
**`NOTICE_ISSUED`**, same audience (`LIVING + OUTSIDE`), same moment (the check-in gate closing,
ahead of the phase push), and `GameState.unreachable` is the one list every evasion will write.
`RADIO_FAULTS_ANNOUNCED` is gone. The schema still fails closed: the `when` is exhaustive and an
effect with no row ships to nobody.

**A meeting with no evasion draws nothing.** The empty notice is still sent at every meeting, so the
frame count does not say whether anybody evaded, and every phone gets the same bytes. The notices
screen draws its band and no box and no line: no *data is complete* sentence. A housed phone is never
shown the fixture (it named MARCUS at every live meeting until now). A phone with no house still
draws it, for the render harness.

**The copy, raised and not built.** The existing notice sentence, *Resident MARCUS was unreachable
21:04–21:07. Occupancy data for this interval is incomplete.*, already covers a permission revoked
mid-round (D-161: *same shape, same words*), but it cannot be reused unchanged: it carries a clock
interval, and the house keeps no clock. Every time on every screen is a fixture, and `Tick` is a
sequence, not a time. Building one (whose clock, which zone, when an interval ends for a radio still
off at the meeting) is a design question, not copy. **Proposed, in its voice and shape, one line:**

> *Resident MARCUS was unreachable this shift. Occupancy data for this interval is incomplete.*

**Ruled by the owner, 2026-11-15: the proposed line, as written.** It is built in `noticeBody`, in `ui`
`PanelModel.kt`. Several named in one meeting read as one sentence: *Residents ROSE and DANI were
unreachable this shift. …* `HouseNoticeTest` pins the words.

**Also kept:** a radio still switched off when a round arms is reported at that round's first
advertise. It is off during the round, and a player who switches Bluetooth off in the lobby to be
unseen all round is the evasion D-187 is about. Nothing is reported or named before arming: the
radio has no token, the uploader has no seed, and the gate refuses a report from the lobby.

**`project-context.md` rule 6 amended** to match: it said a failed radio *is announced at the next
meeting*. A failed radio is recorded; only evasion is announced.

**Recording format 26**: `RadioFaulted` carries `cause`, the notice effect and the state column are
renamed.

| site | what it says now | what replaces it |
|---|---|---|
| `model` `RadioCause` | (new) | `Person` / `Device`, serial names of one length |
| `model` `Event.RadioFaulted`, `RoundBody.RadioFaulted` | no kind | `cause` |
| `model` `Effect.RadioFaultsAnnounced`, `RoundMessage.RadioFaultsAnnounced`, `RADIO_FAULTS_ANNOUNCED` | every outage, named | `NoticeIssued(unreachable)`, `NOTICE_ISSUED`: evasion only |
| `model` `GameState.radioFaulted` | every outage | `unreachable`: a person's outage only |
| `core` `Rules` `radioFaulted`, `namedByTheNotice` | every outage listed | the line, in one function |
| `platform` `ContactRadio.faultBegan`, `SwitchboardRadio`, `causeOf`, `BluetoothRadio.ios.kt` | `Boolean` | the onset's `RadioCause` |
| `app` `ContactUploader`, `RoundOverTransport`, `LobbyHouse`, `RoundAuthority.radioFaulted`, `HouseBuzzer`, cheats | no cause | the cause up, through the queue, into the log |
| `ui` `Flow`, `PanelState.notice`, `PanelVals.notice`, `noticeBody`, `NoticeScreen` | a no-op; the fixture at every live meeting | the house's notice; nothing for an empty one |
| `harness` `Transcript`, `RecordingText`, `Recording.HEADER` | `recording/25` | `cause`, `NoticeIssued`, `unreachable`; `recording/26` |
| `RadioFaultTest`, `SwitchboardRadioTest`, `ContactUploaderTest`, `ContactRadioBenchTest`, `HouseNoticeTest` | announce everything | a reset unnamed, a switch-off named, nothing before arming, nothing drawn when empty, one length |
| `ble-unit4-device-check.md` step 7 | the radio notice | the house notice, and a reset unnamed |

## Revision 58 — the host's phone, locked in its lobby

*The owner's 2026-11-16 rulings H1 and H2, on the bug the one-phone session found: when the host's
phone auto-locked in the lobby every seat's socket dropped (`ENOTCONN`), the advertisement was refused
(`NSNetServicesErrorCode -72000`), the house woke with nobody seated and nothing that would seat them,
and LIGHTS OUT then logged `WILL NOT ARM — 0 SEAT(S), 0 LINE(S) IN` under a screen that said ARMED
(`/tmp/someones-home-one-phone/phone-roundB1.log`). On a game night, a host who sets the phone down in
the lobby silently loses the room.*

**Root cause, three faults in a row.** (1) Nothing held the host's screen in the lobby: D-221 holds it
from arming. (2) iOS suspends an app that leaves the foreground, and a suspended app keeps neither its
listening socket nor its Bonjour service; nothing in the house ever listened or advertised again. The
clients already rejoin by token on a bounded backoff (G3, story 0.8), but there was no listener to
rejoin. (3) `FlowModel.lightsOut` walked the host's screen to ARMED on the press and told the house
after, so a refusal still showed ARMED; and the host's button was drawn from the last standing its own
socket received, a READY one from before the lock.

### D-224 · **The host's screen is kept awake from the moment its lobby opens until hosting ends — decided**

**H1, extending D-221.** The host holds the idle timer off when its home is on the air and it is walked
into its lobby, and the hold ends when hosting does: CLOSE THE HOUSE, LEAVE, or the round ending.
NEW ROUND opens the host's lobby again, so it holds again. **Non-host phones keep D-221 as it is**:
nothing in a guest's lobby touches the idle timer.

**One idle timer, one `ScreenWake`, two holds.** The app root builds one and lends it to the lobby link
and the round half. Two wakes over one property would each remember the other's `true` as the owner's
setting. The two holds are one state with three values: a round's hold takes over the lobby's with no
instant where the timer is back on; a round's release ends both (hosting ends with the round); a round's
release with a lobby held does nothing, because the house's `RoundEnded` broadcast reaches the host's own
round half after NEW ROUND has already re-held; a hosting release leaves a round's hold to the round.

**Checked against D-077, D-093 and D-118: it changes no light level, only Auto-Lock.** It is D-221's
property, `UIApplication.idleTimerDisabled`, held earlier. It does not touch `UIScreen.brightness`,
draws nothing and animates nothing. D-077's concealment chrome, D-093's absent Island ring and D-118's
two dims are untouched. What it removes, in the host's lobby, is the OS's own dim before the lock.

### D-225 · **A host that leaves the foreground takes the house off the air and puts it back on the same port with the same ledger; every seat returns to its own chair by its own token — decided**

**H2.** A lock, the side button or a swipe home is still possible by hand, and still suspends the app (no
background modes, T4). So the house now handles the two moments itself, through a `platform` seam,
`Foreground` (iOS: `UIApplicationDidEnterBackground` and `WillEnterForeground` on the main queue):

- **Leaving:** the advertisement comes off the air and the server stops, while the app still has the
  seconds iOS gives it. The connections close cleanly, so each phone learns at once and starts the
  reconnect it already has. **The ledger, the desk and the round survive**; each lost connection reaches
  the desk and the round's queue exactly as any lost connection does (`onLost`, unchanged).
- **Returning:** the same port is bound again on the same `TransportHost`, whose ledger minted every
  token in the room, and the home goes back on the air under the same name. A client holds the address
  and port it joined on and its token, and resumes by presenting the token (D-052's gotcha, G3, D-094).
  **Identity on reconnect is the stored seat token and nothing else**: a token the ledger minted returns
  *that* seat, one it never minted is refused (`UnknownToken`, then D-172's silent fresh join), no token
  is a stranger who gets a free seat and never a held one, and after arming not even that. D-097's
  re-push rides the re-seating unchanged (`RoundSeam.seated`).
- A Bonjour refusal on the return is logged and the house stays up: seated phones return by address and
  need no advertisement. A port that cannot be bound after ten tries is said in so many words, because
  it strands the room.

**The line rides back up with the name.** The desk drops a seat's line with its connection
(`LobbyDesk.left`), because a person leaving is also only a closed connection. The name has always been
re-sent on every seating; without the line the room came back with every phone saying HANDED OVER over
a count of 0 OF 3, and nobody would think to re-type. The link now keeps the handed line in memory,
beside the name, until the round ends, the phone leaves or the house closes (the screen's own `OneLine`
copy lives exactly as long), and re-sends it by the one body that always carried it. **Nothing a client
receives changes**: the line goes up only, never down, and the desk's leave semantics are unchanged.

**The address must be reused, and a refused bind must be catchable.** The server closed the
connections, so they sit in TIME_WAIT on its port; it is now bound with address reuse. And Ktor's engine
binds on a coroutine of its own, where a refusal is an **uncaught** exception that ends a Kotlin/Native
process: found by breaking address reuse in `HostReopenTest`, which failed with `EADDRINUSE` instead of
failing. On a phone that is the house crashing at the moment it recovers. A named port is now bound
once by the caller, with the engine's options, before the engine is given it.

**Found on the way: CLOSE THE HOUSE never stopped the server.** `LobbyHouse.stop` read the server field
inside a launched coroutine after nulling it, so on the main thread's dispatcher the stop saw null. The
server is now read into a local first.

**Mid-round, nothing about the round changes, and nothing names the host.** The same two moments apply
to a host backgrounded mid-round, because they are transport, not rules. What follows is all existing
behaviour: each dropped seat gets D-203's `LeaveTerminal`, pushes to a seat that is away are recorded as
unreachable and collected on its `Gap` (D-097, D-190), the seats resume under the locked ledger, and the
tick driver measures the gap on its return and abandons a backlog too large to chase, out loud (E-P3-3).
**D-187 calls locking or backgrounding mid-round deliberate evasion, but D-223 recorded that neither is
yet detectable, and the host is the house**, so no notice names anyone for it. With D-224 and D-221 the
host's screen is held awake through the lobby and the round, so a host can only be backgrounded by
their own hand. Whether a host doing that should be named is not decided here: raised for the owner.
*(Decided 2026-11-16: named like anyone else — D-229, D-230.)*

### D-226 · **LIGHTS OUT walks the host to ARMED only on the house's yes, and a host whose own link is down is shown an empty room — decided**

**The walk is handed to the arming.** `FlowModel.lightsOut` hands its walk to the seam, and
`armAtLightsOut` calls it after the house armed and on nothing else. A refused press leaves the host on
its lobby. With no house behind the model (renders, the desktop preview) the default walks at once, as
it always did.

**The host's standing is not kept past its own link.** While the host's own socket is down, its lobby is
shown the empty standing LEAVE already shows, so LIGHTS OUT is drawn defunct under NEEDS 3 RESIDENTS, the
existing words, until the house sends a real one. The host only: a guest draws no button from it, and
its list blinking empty on each reconnect would change what a guest is shown for nothing. So LIGHTS OUT
is refused below `MINIMUM_SEATS` connected three times over: the button is defunct, the house refuses
(`readyToArm` counts connected seats), and the screen does not move.

| site | what it says now | what replaces it |
|---|---|---|
| `platform` `ScreenWake` | one hold, the round's | `holdWhileHosting`, `releaseHosting`; the round's hold takes over and ends both |
| `platform` `Foreground`, `Foreground.ios.kt` | (new) | `deviceForeground()`: background and foreground, on the main queue |
| `platform` `TransportHost.start` | port 0 only, once | startable again after `stop`, address reuse, a named port tried before the engine binds it |
| `app` `LobbyHouse` | `HostAdvertiser` direct; `stop` never stopped the server | `Advertising` seam; `backgrounded`, `foregrounded`, `reopen`; the server read before the launch |
| `app` `LobbyOverTransport` | no idle timer; the line sent once | a required `ScreenWake`, held at lobby open and NEW ROUND, released at leave and close; the line re-sent on every seating; the host's standing emptied while its link is down |
| `app` `armAtLightsOut` | the screen walked before it was asked | `armed`, called on a yes |
| `ui` `FlowModel.lightsOut`, `onLightsOut` | `push(Armed)`, then report | report and hand over the walk |
| `app` cheats `Cheats.kt` | a wake built inside the round client | one wake for lobby and round; the foreground watched; `armed` handed to the arming |
| `ScreenWakeTest`, `HostReopenTest`, `HostForegroundTest`, `HostArmingTest`, `LightsOutSeamTest` | (new cases) | the two holds; same chair by token across a reopen, wrong and missing tokens; suspend and resume with three phones on loopback; a refused press stays on the lobby |
| `tools/one-phone/README.md` | Auto-Lock Never for round B | section 6: lock the host by hand in the lobby, unlock, the room comes back, LIGHTS OUT works |

### D-227 · **The lobby's desk is changed only on the house's own thread, so a seat that resumes keeps its own name — built**

**Found on the device rerun after D-225** (`runs/20261116-132911`): the host locked in the lobby, both
filler seats resumed by token at the same instant (they had dropped at the same instant, so their
backoffs matched), both sent their names, and the lobby read `[PHONE, SIMA, UNNAMED]` where it had read
`SIMB`, with all three lines in. The roster at arming carried two names for three seats. Seat 1 kept its
name only because it won the race.

**Not the ledger, not the resume, not the line re-send.** The ledger seated each token in its own chair,
and the client re-sent the name before the line on every seating, as D-225 says. The fault was below
both: `TransportHost` calls `onFrame`, `onSeated` and `onLost` on Ktor's connection threads, one per
socket and several at once (its KDoc said so), and `LobbyHouse` wrote the desk (a set and two plain
maps) straight from them. Two writes into one map from two threads is a lost write or an entry filed
under the wrong key. On loopback the race rarely shows; a device on Wi-Fi lost it once.

**The ruling.** Every change a phone makes to the desk (a seating, a loss, a name, a line) is handed to
the house's own scope, which is the main thread the host's own controls already reach the desk on, and
the standing is built there too. A change the desk refuses is logged for the authority and never shown.
`TransportHost` reports a loss under its lock, so a seat's loss always reaches the caller before a later
resume of the same seat. It takes that lock uncancellably: a server stopping cancels every connection at
once, and a cancelled connection that had to wait for the lock was thrown out before reporting its loss,
leaving a seat on the desk whose phone had gone (seen in this work's own tests, not on the device). The standing still lists names in arrival order (`LobbyBody.Standing`); the
roster is drawn seat by seat, and a name is only ever filed under the seat whose socket sent it.

| site | what it says now | what replaces it |
|---|---|---|
| `app` `LobbyHouse` | the desk written on connection threads | `atTheDesk`: every desk change on the house's scope, refusals logged |
| `platform` `TransportHost` | `onLost` after the lock, and skipped when a cancelled connection waited for it | `onLost` under the lock, never cancelled |
| `HostForegroundTest` | (new cases) | six guests through five locks keep their names; a seat resuming out of order keeps its own; the desk is changed only on the house's thread |

### D-228 · **The one-phone rig plays four seats by default, under seed 20261150 — built**

At three seats (one Insider) the bench's Revoke left one plain Resident against one Insider, which is
parity (D-131): the round ended at the Revoke, the report was refused as `RoundAlreadyEnded`, and the
report path and the meeting notice could not be checked on the device. `round.sh` now runs a third
Simulator filler seat (`--seats 3|4`, default 4), and one Revoke leaves two against one.

The seed moves with it. 20261116 dealt seat 0 the Insider role at three seats but seat 2 at four, where
the bench's Revoke (the first living plain Resident other than the Insider) would have landed on the
phone and stopped its scan. 20261150 is the first seed from 20261116 up that deals seat 0 at three and
at four, so the Revoke lands on seat 1, a Simulator, and the Mac advertises as seat 1. Pinned in
`OnePhoneVectorsTest` (with the round going on after that Revoke) and in `tools/ble-peer`'s tests.

`round.sh start --no-phone` now plays the Mac half end to end: the seats must keep looking without
giving up, then a fourth Simulator hosts in the phone's place and the round is played to the end of its
first meeting. The bench gains `SOMEONES_HOME_LOBBY_SEATS`, so an automatic LIGHTS OUT waits for the
whole party rather than pressing at the three seats that make the button live.

## Revision 59 — leaving the foreground mid-round is named, the host's own seat included

*The owner's 2026-11-16 ruling on the item D-225 raised: build D-187's lock and background detection
for every phone, and treat the host like anyone else. Built; one gap raised.*

### D-229 · **A phone that leaves the foreground mid-round tells the house once, when it is back, in the body a person's radio outage already sends — built**

**What detects it.** D-225's `Foreground` seam (`UIApplicationDidEnterBackground` and
`WillEnterForeground`) was watched by the host's house alone. `LobbyOverTransport.backgrounded` and
`foregrounded` now reach every phone's round half first (`RoundSeam.leftTheForeground`,
`backInTheForeground`), the host's and every guest's, and only then the house. In
`ContactUploader` a leave is an onset if this phone holds the round's seed, and the return owes the
house one report; the next beat pays it. A lock, the side button and a swipe home all post the same
notification. Pulling down Control Centre or a notification only resigns active, suspends nothing, and
is not watched (D-225).

**What goes up: `RoundBody.RadioFaulted` with `RadioCause.Person`, unchanged.** With no background
modes (D-221) a phone that leaves the foreground is suspended, and its radio stops advertising with
it: a leave IS a radio taken down by its owner's hand, which is exactly what D-223's `person` cause
says. So the `person` path is reused whole rather than paralleled: the same one body of one length
from every phone (rule 1), no new event, effect, schema row or wire member (rule 2 is untouched
because nothing new ships), no recording format change (still `recording/26`), the same gate, the same
`GameState.unreachable`, the same `NoticeIssued` at the check-in gate, and D-223's line as ruled:
*Resident X was unreachable this shift. Occupancy data for this interval is incomplete.*, several
names one sentence. The house cannot tell a lock from a switch-off and does not need to; the phone's
own log says which (`THIS PHONE LEFT THE FOREGROUND IN A ROUND`, `BACK IN THE FOREGROUND — THE NEXT
BEAT TELLS THE HOUSE, ONCE · PERSON`). Nothing on any screen, lamp or motor hears of it (rules 5, 6).

**Once per onset.** Leaving and returning any number of times between two beats is one report. The
radio's own onset on the beat a phone comes back is folded into it, as a person's: leaving the app
was a person's act whatever the radio also did, and one beat never sends two bodies. A reset with
nobody leaving is still the device's and still named by nobody (D-223).

**Told on the return, not at the leave.** At the leave the host's house is stopping its server
(D-225), so a report sent then would race it and the host would be the one phone whose report is lost.
On the return every phone is alike: the report goes into its socket's outbox, which holds frames
while the connection is coming back and sends them once the seat is taken again, so the host's own
report reaches its reopened house over loopback like anybody's. ⚠️ Limit, reasoned and not observed:
`TransportClient` is at-most-once, so a report handed to a guest's socket that failed while the phone
was suspended, and that the phone has not yet noticed, can be lost with it.

**Nothing is owed** before arming (no seed; a leave in the lobby is not carried into the round), after
the round (the seed is gone, and a report that crosses the end is refused by the gate and recorded,
D-223), across a round's end, or by a phone that relaunched into its seat: a crash is not a person's
act (D-036), and a fresh process holds no onset.

**Permissions withdrawn mid-round (D-161).** Every grant is changed in Settings, and going there is
leaving the foreground, so the leave reports it whenever the app survives the change: Notifications
and Local Network (reasoned: iOS does not end an app for those). Bluetooth switched off or its
permission withdrawn is D-223's own. ⚠️ **Not covered, raised:** iOS ends an app whose Camera or Motion
grant (and, reasoned, its Bluetooth permission) is withdrawn in Settings. The onset is held in memory
and fails with the process, and the relaunch resumes by token owing nothing, by the crash rule above.
Recommendation: keep the onset beside the seat token at the leave (`SeatStore`), and report it at the
resumed seat's first beat; a crash in the foreground writes no marker, so D-036 still holds. And a
guest whose Local Network grant is withdrawn cannot reach the house at all, so its report never
arrives; the house sees a seat that dropped, which D-187 already treats as disconnection.

### D-230 · **The host is named like anyone else, and the round goes on by the house's clock while the host is away — decided, nothing about the round changed**

**The owner's ruling, applied.** D-225 left open whether a host that leaves the foreground mid-round
should be named. It is: the host's round half reports its own seat by D-229's path, up its own
loopback socket into its own house, and the next meeting names seat 0 to every phone exactly as it
would name a guest. No branch anywhere asks whether the reporter is the host.

**What the round does while the host is away, from what is already decided and built (unchanged):**

- **The house is off the air** (D-225): its server stops, every seat's connection drops, each seat gets
  D-203's `LeaveTerminal`, pushes to a seat that is away are recorded and caught up by its `Gap` on
  return (D-097, D-190), and the seats come back to their own chairs by their own tokens.
- **The clock keeps the wall's time.** `tickHouse` reads `CLOCK_MONOTONIC`, which on Darwin goes on
  counting while the phone sleeps. On the return it measures the whole gap, abandons the backlog past
  two seconds, and skips simulation time forward to the wall (`FixedTimestep`: *a ninety-second
  discussion has to be ninety seconds*), saying `THE HOUSE CLOCK SKIPPED n STEP(S)` in its log. Every
  deadline that fell due inside the gap (the talk, the ballot, the tally, an Egress expiring, the
  cooldowns) fires at the first step after the return. So a host away for long enough can come back
  to a meeting moved on or an Egress run out. **That is the existing rule and it is not changed here.**
- **The other phones** keep the screen they had; what they send while the house is away waits in
  their socket's outbox and goes up when they are seated again, or is lost with a failing socket
  (at-most-once); nothing is minted for them in the meantime because nothing is listening.

| site | what it says now | what replaces it |
|---|---|---|
| `app` `ContactUploader` | the radio's onset only | `leftTheForeground`, `backInTheForeground`: one person's report per leave, in a round, on the next beat; the radio's onset folded in |
| `app` `RoundOverTransport` | (none) | the two calls, logged on this phone only |
| `app` `RoundSeam`, `LobbyOverTransport.backgrounded`/`foregrounded` | the house only, the host only | every phone's round half first, then the house |
| `app` cheats `DeferredRoundSeam`, `RoundClient`, `Cheats.kt` | (none) | forwarded to the round half |
| `model` KDoc on `GameState.unreachable`, `Effect.NoticeIssued`, `Event.RadioFaulted`, `RoundBody.RadioFaulted` | lock and background *not yet detectable* | written by the same `person` body (no type, field or format change) |
| `ContactUploaderTest` | (new cases) | once per onset, only on the return; nothing before arming, after the end, across it, or on a relaunch; a reset alone stays the device's; one body a beat |
| `EvasionNoticeTest` | (new) | over loopback with the real link and authority: a guest named once; the host names its own seat across its house's reopening; a leave in the lobby named by nobody |
| `tools/one-phone/README.md` | (none) | section 7: lock the host mid-round, unlock, and the act-3 meeting's notice names seat 0 |

## Revision 60 — page 2's two holds reach the house, and the bench mints no player's act

*2026-11-16. Found on two real iPhones: the Insider held REVOKE, the tile said ARMED · TOUCH THEIR
PHONE, and every touch was answered as an ordinary `ContactAnswered`. Nothing in `app` or `ui` sent
`IntentMessage.ArmRevoke`. Every earlier "the Revoke lands" result came from the bench minting
`RevokeArmed` (`RoundCheat.theRevoke`) or a test putting `Intent.ArmRevoke` to the house directly.*

### D-231 · **The REVOKE and EGRESS holds send their requests; the tile echoes ARMED and nothing more; the Egress alert is the house's push on every phone — built**

**The audit** (every action the UI offers in a round, traced from the control to the house on the
live path in `CheatRoot`): the meeting's four controls, the scans (a Subroutine card and the meeting
card), BEGIN, the hand-over, STOP NOW, leaving the live view, SURGE, ISOLATE and STATIC were wired. The
contact and the report go up as sightings on the cadence (D-217). **Two were not:** the REVOKE hold
(`FlowModel.stepRevoke` stepped the tile and sent nothing) and the EGRESS hold (`armEgress` walked the
phone to the alert and sent nothing; only the bench, calling `RoundClient.fireEgress`, could start an
Egress). **One has no control at all:** containment (`ReturnSyncPulse`); see D-232.

1. **REVOKE.** `FlowModel.armRevoke` (renamed from `stepRevoke`) sends `IntentMessage.ArmRevoke` up the
   existing `send` seam on every hold, the same way whatever the outcome (rule 1). The house admits
   `Intent.ArmRevoke` as `RevokeArmed`; arming stays silent in both outcomes (D-009, D-142, D-216), so
   there is no answer for the tile to follow. **ARMED is input echo**, which is D-216's own reading
   (*their phone already knows because they held the tile down*), set unconditionally. The tile no
   longer steps to COOLING DOWN and READY on later holds: a second hold is a second request.
2. **EGRESS.** `FlowModel` gains a `fireEgress` seam (the Egress is `RoundBody.Fire`, not an
   `IntentMessage`), which `CheatRoot` points at `RoundClient.fireEgress`. The hold sends it and the
   screen stays on page 2. **The alert reaches the actor as it reaches everybody, by `EgressOpened`**,
   so `ScreenId.Banner` moves to `Flow.housePushed` and leaves `Flow.viaActions[Page2]`. That removes
   the game's only role-asymmetric edge. A press into a running clock is answered as every fire into
   a running clock is (T1·8): `AbilityFired(cooldownStarted = false)` to the actor alone, which moves
   nothing.

**Open, raised with the owner rather than decided** (each changes what a client receives or draws):

- **When the REVOKE tile stops saying ARMED.** No house message can drive it. The arm is silent by
  design, a refused arm (a clock still running, including the opening half) still echoes ARMED, and
  the tile changing at contact on `AbilityFired` would be actor-side feedback at the instant the
  target's light goes dark (`gdd.md:398`). Today it says ARMED from the first hold until the round ends,
  and READY · HOLD TO ARM before it, whatever the clock. Recommendation: send every seat its own two
  ability clocks in one fixed-shape row (Residents' included, as `openingCooldowns` already draws
  them), at arming and at each meeting's end, so the tile draws READY or COOLING DOWN as a pure
  function of the house's deadline and drops ARMED when the clock it was armed under restarts.
- **The 45 s window** D-009 and D-142 name is not built: an armed Revoke lasts until a contact, being
  out, or being Restrained (R2).

### D-232 · **The bench presses the Insider's tiles on the panel and mints no player's act — built; containment has no control — raised**

**Removed from `RoundCheat`:** the minted `RevokeArmed` (act 3), the minted `SyncPulseReturned`
beats that contained every rehearsal's Egress (act 5), and the answer-key `MarkerScanned` and
`SubroutineReturned` that banked the meter (act 6). **Now:** a phone whose own screen says Insider
holds REVOKE (from its work order to the first meeting), EGRESS and STATIC through `flow.actions()`,
the lambdas the tiles hold, and nothing else presses them; act 3 waits for the house to hold the
Insider's arm before the touch. The contact, the report and the hop between processes are still
`SimulatedAir` and `HouseAir` (D-217).

**What the bench now shows, because it is true of the phones:** an Egress is not contained (no screen
draws a containment control, and nothing on the wire carries the house's clock that `onTheBeat`
grades taps against), and the meter moves only by the phones' own entries, which the bench cannot
answer. A rehearsal should therefore end by its Egress running out; that has not been run on
Simulators in this change. **Raised:** containment needs a control
and a clock anchor, both a change to what a client draws and receives.

| site | what it says now | what replaces it |
|---|---|---|
| `ui` `FlowModel.stepRevoke` | steps the tile, sends nothing | `armRevoke`: sends `ArmRevoke`, echoes ARMED |
| `ui` `FlowModel.armEgress` | `go(Banner)`, sends nothing | calls the new `fireEgress` seam, moves nothing |
| `ui` `Flow.housePushed`, `Flow.viaActions` | `Banner` walked to from `Page2` | `Banner` pushed only |
| `ui` `PanelActions.stepRevoke` | (renamed) | `armRevoke` |
| `app` cheats `Cheats.kt` | (none) | `fireEgress = { press.value?.fireEgress() }` |
| `app` cheats `RoundCheat` | four player-act mints; every seat pressing `RoundClient` directly | none; the Insider's panel pressing |
| `AbilityWiringTest` (new) | (none) | four phones over loopback with the real link, desk and authority: the REVOKE action arms the house and the touch lands; an early arm is sent the same and arms nothing; the EGRESS action opens on every phone by the push, an early one answered to the actor alone |
| `HoldInputTest`, `FlowTest` | the hold changes the tile; the Egress hold reaches `Banner` | the hold sends `ArmRevoke` / fires, and no page-2 hold moves the screen |

## Revision 61 — the contact gate opens at −40, and the scan turns over ten times a second

*Ruled by the owner 2026-11-16, after a two-phone touch test: two iPhones, quick realistic touches,
the radio's own `HEARD` lines read on each phone.*

### D-233 · **The contact gate is −40 dBm and the scan restarts every 100 ms — decided (amends D-008's "only tighten" for this value; replaces D-213's floor and D-218's scan rate)**

**What two phones read** (2026-11-16, phone to phone, the readings the uploader keeps):

| distance | RSSI |
|---|---|
| 1 m | median −57 |
| 30 cm | median −43, range −41 to −45 |
| touching, loose | −37 |
| touching, firm | −27 |

**What the gate let through.** Touches came quicker than the 5 s pair debounce at first, which
confounds the count, so the second run spaced them about 6 s apart.

| gate | touches registered | readings at or above the gate |
|---|---|---|
| −35 (D-213) | about 33 to 50 per cent (10 of the owner's 20 to 30) | 14, against 66 at −36 to −45 |
| −40 (a build on one phone, never committed) | about 65 to 80 per cent (10 of 12 to 16) | 16, against 14 at −41 to −46 |

1. **The floor is −40 dBm** (`ContactGate.RSSI_FLOOR`). A loose touch reads −37 and opens it; 30 cm
   reads −41 at its strongest and does not. **This loosens D-008's radius, which said the gate may
   only ever tighten, by the owner's ruling and for this value only.** At −35 half or more of real
   touches never landed, and a Revoke that silently never lands is D-008's other failure. The margin
   at 30 cm is one dB, so the next two-phone session reruns the hit rate **and a 30 cm pass-by
   false-positive check**; any further move is its own ruling. The ceiling is unchanged: 0 dBm and
   above, CoreBluetooth's 127 included, is no reading.
2. **The scan restarts every 100 ms** (`ContactAdvertisement.SCAN_CYCLE_MILLIS`, was 250). iOS
   reports each phone once per scan with duplicates filtered, so a phone heard is reported up to ten
   times a second instead of about four, and a quick touch is more likely caught at its strongest.
   The ring still keeps the strongest reading per token, and the uploader drains it once a second.
   **The counts of cycles are now derived from the cycle**, so what they stand for does not move:
   the tally every 10 s (`TALLY_CYCLES` 40 → 100), the fault settle and the start patience 2 s each
   (8 → 20). The iOS timer's leeway is a tenth of the cycle (25 → 10 ms), so it cannot slip to
   eight restarts a second.

**Allocation at 7 peers (rule 7), D-218's estimate redone at the new rate:**

| | rate | bytes | KB/s |
|---|---|---|---|
| scan callbacks | 7 peers × 10/s = 70/s | 300 (at most 500) | 21 (35) |
| scan restarts (stop, start, timer) | 10/s | under 100 | under 1 |
| drain (once a beat, up to 14 readings) | 1/s | about 1 000 | about 1 |
| device log `HEARD` line | 1/s | about 200 | 0.2 |
| **total** | | | **about 23 (37)** |

**About 23 KB/s, 37 KB/s at the worst per-callback cost: 4.6 to 7.4 per cent of the 0.5 MB/s
budget**, against D-218's 2 to 3 per cent. Not tight. The bytes per callback are still an estimate;
unit 8 measures them on a phone, and the `TALLY` line's callback count is the rate to multiply.

**Noticed, not changed:** the upload's filler RSSI spans −100 to −40 (`SightingUpload`), so filler
can now sit at the floor. Filler tokens resolve to nobody, so it opens nothing (`ContactResolverTest`
already feeds it at −20); it only means a real reading at −40 is no longer outside filler's range.

| site | what it says now | what replaces it |
|---|---|---|
| `core` `ContactGate.RSSI_FLOOR` | −35, placeholder (D-213) | −40, ruled |
| `platform` `ContactAdvertisement.SCAN_CYCLE_MILLIS` | 250 | 100 |
| `platform` `TALLY_CYCLES`, `FAULT_SETTLE_CYCLES`, `START_PATIENCE_CYCLES` | 40, 8, 8 | 100, 20, 20, derived from the cycle |
| `platform` iOS `LEEWAY_NANOS` | 25 ms | a tenth of the cycle, 10 ms |
| `ContactResolverTest` | pins −35 | pins −40, citing this entry; a new test pins the gate in the measured readings (−27, −37, −40 land; −41, −43, −45, −57 do not) |
| `SwitchboardRadioTest` | (none) | a new test pins 100 ms, ten restarts in ten cycles, and the 10 s / 2 s / 2 s durations |
| `app` cheats `SimulatedAir`, `tools/one-phone/README.md`, `ble-unit4-device-check.md` | −35, 250 ms | −40, 100 ms |

## Revision 62 — the owner's list from two real iPhones: ARMED on every phone, the line above the keyboard, no Perms screen when nothing is missing, and two quieter holds

*2026-11-16. Found on real iPhones by the owner. Two bugs and four improvements; one is answered
with a recommendation rather than a change.*

### D-234 · **The house's opening push walks every seated phone to PERIMETER ARMED, the host's included, and the host no longer walks itself — built (amends D-226)**

**Found:** after LIGHTS OUT the host's phone said PERIMETER ARMED and the guests' did not. **Cause:**
D-226 handed the walk to `armAtLightsOut`, which called it on the host alone, on the house's yes.
Nothing the house sent walked anybody else: `RoundMessage.OpeningMessage`, which every seat is sent
once at arming, raised the opening banner (`Notify`), so a guest went from the lobby straight to the
springboard. The host, already on ARMED (a screen the house drives), dropped that banner. Two
phones, two different first frames of the round.

**The ruling.** `OpeningMessage` arriving on a phone that stands in the lobby or on its one-line
screen (`Flow.seatedBeforeArming`) pushes ARMED; anywhere else it is the banner, as before (a phone
the house is catching up after a gap). `armAtLightsOut` loses its `armed` parameter and `CheatRoot`
calls no walk: the host's own seat is sent the same push over its loopback socket, so every phone
reaches ARMED by one path, in the same frame, whatever its role (rule 1). D-226's other half
stands: a refused press sends nothing and moves nothing. With no house behind `FlowModel` (renders,
the desktop preview) the seam's default still walks at once. No wire member, effect, schema row or
recording format changes: the message already reached every seat.

**Raised, not decided:** the opening banner (D-118/D-119's heavy notification) is now drawn at
arming on no phone. The host never saw it before this either, and the role reveal is unchanged on
its thread in Messages. If the banner should follow ARMED, it would arrive on a tap (CONTINUE),
which D-102 says does not buzz and D-119 says dims the house at one moment for everybody; that is
the owner's to rule.

| site | what it says now | what replaces it |
|---|---|---|
| `ui` `FlowModel.houseSaid` | `OpeningMessage` → the banner | `roundOpened`: ARMED from the lobby or the one-line screen, the banner elsewhere |
| `ui` `Flow` | (none) | `seatedBeforeArming` = `Lobby`, `Secret` |
| `app` `armAtLightsOut` | `armed()` on a yes | no walk; the push is the walk |
| `app` cheats `CheatRoot` | `armed = armed` | the seam's walk not called |
| `ArmedForEveryPhoneTest` (new) | (none) | three phones on loopback with the real link, desk and authority: LIGHTS OUT puts ARMED on every guest (one of them on the one-line screen) and on the host, by the push; a refused press moves nobody and sends no opening |
| `HostArmingTest` | the host walked on `armed` | the press walks nothing; the host's own push walks it; a refusal sends no opening |

### D-235 · **The line stays above the keyboard, the Perms screen is skipped when the OS grants everything, and the lobby's holds carry no helper text — built**

1. **The keyboard** (bug). The owner could not see the one line they were typing. `PanelInsets`
   gains `keyboard`, read from `WindowInsets.ime`; `PanelFrame` ends the content at the taller of
   the home bar and the keyboard, on every screen with a field. On the one-line screen that left
   about 470 of a 13 Pro's 780 units, and the field, the one weighted block, was squeezed to
   nothing (rendered), so **while the keyboard is up the caption and the two promise rows step
   aside**: the header, the headline, the field and HAND IT OVER stay, and the line can be handed
   over without closing the keyboard. The promises are read before typing begins and are back when
   the keyboard goes down. The iOS view controller's own nudge toward a focused field is switched
   off (`OnFocusBehavior.DoNothing`), so one rule moves the screen and the layout a test renders is
   the layout the phone draws. ⚠️ Rendered and tested with the keyboard's inset passed in by hand
   (336 pt on a 13 Pro); not yet seen on a phone.
2. **The Perms screen is skipped** when, at the moment the self-test falls through, the OS answers
   every row (`PermsModel.missingNow()`, the fresh read the lobby's gate asks). D-161 is unchanged:
   the gate is still at setup, in the light; any row missing, or not answered yet, shows the screen;
   the lobby still refuses a seat on the OS's own answer. Asked when the timer fires, not when the
   self-test is drawn, because Notifications answers through a callback that lands after launch.
3. **LIGHTS OUT** loses HOLD THE BUTTON FOR TWO SECONDS and KEEP HOLDING: the bar under the thumb says
   both. The inert button's reason (NEEDS n RESIDENTS, WAITING FOR EVERYONE'S LINE) stays, because it
   answers *why will it not start* (D-226), and its line is held empty once the gate opens so the
   block under the host's thumb does not move.
4. **CLOSE THE HOUSE / LEAVE THIS HOUSE** lose their notes, and a guest's LEAVE THIS LOBBY is now
   LEAVE THIS HOUSE, the owner's words.
5. **The PLAYTEST chip stays.** It opens the cheat picker: a jump to any screen, the role switch,
   and the transport rig's log, the haptic bench and the camera bench, which nothing else reaches.
   `e2e.sh`'s human-mock taps it (`variant-marker`) to get past the permissions walk the Simulator
   cannot complete, and the playtest variant's definition is a permanent visible marker. The
   one-phone and two-phone tooling does not use it. It is compiled out of release already.

| site | what it says now | what replaces it |
|---|---|---|
| `ui` `PanelInsets`, `platformInsets` | top, bottom, side | `keyboard`, from `WindowInsets.ime` |
| `ui` `PanelFrame` | content ends at the home bar | at the taller of the home bar and the keyboard |
| `ui` `SecretScreen` | caption and promises always | not while the keyboard is up |
| `app` `Entry.ios.kt` | the default focus behaviour | `OnFocusBehavior.DoNothing` |
| `ui` `FlowModel.fallThrough`, `FlowHost` | Boot always to Perms | to Join when nothing is missing |
| `ui` `LightsOut`, `LobbyExit` | notes under both; LEAVE THIS LOBBY | the inert button's reason only; LEAVE THIS HOUSE |
| `KeyboardLayoutTest`, `PermsSkipTest` (new); `LobbyInputTest` | (none); KEEP HOLDING read as the bar | the field and HAND IT OVER above a 310-unit keyboard; skipped only when every row is open, row by row; the bar read off its own progress; the two holds' words |

## Revision 63 — a report is heard twice, and a phone running ahead is still mapped

*2026-11-16, overnight run, from findings R5 and R8 of the read-only review of `67f3313..HEAD`. A
provisional call made without the owner: **owner to confirm.** Desktop tests only; nothing here has
been on a phone.*

### D-236 · **A report needs two readings at contact range in two of the reporter's three latest uploads; a contact is unchanged; the token book holds the window ahead — built, owner to confirm (amends D-213's report rule and its windows)**

**R5, what was wrong.** D-233's −40 gate, D-233's 100 ms scan and D-218's strongest-per-second ring
were never measured together. A report needed one side and **one** reading at or above the floor.
Measured phone to phone (D-233): 30 cm reads −41 to −45, median −43, so the gate's margin at 30 cm is
one dB. Those readings were the ring's at a 250 ms scan, the strongest of about four callbacks a
second; at 100 ms it keeps the strongest of about ten, which can only move the kept reading up, and
that has not been measured. So one stray second at −40 could call a meeting from a pass-by, and an
Insider still near the player they Revoked after the 5 s debounce could report their own target on
one stray reading.

1. **A report needs `ContactGate.REPORT_READINGS` = 2 readings** at contact range, of the same
   Revoked seat, by the same reporter, from two of its uploads, **within `REPORT_SPAN_TICKS` =
   2.5 s** of arrival time: two upload intervals from the first reading to the last, plus half of
   one for delivery jitter. That is *two of the reporter's three latest uploads*. A second delivery of
   one upload is not a second reading (the duplicate guard drops it first), and two phones' single
   readings do not add up.
2. **What it buys**, worked from the measurement, because the rate at 100 ms is the unmeasured part:
   if *p* is the chance that one second's kept reading at 30 cm reaches −40,

   | *p* | 2 s pass-by, one reading | 2 s pass-by, two of three | 10 s standing at 30 cm, one reading | 10 s, two of three |
   |---|---|---|---|---|
   | 0.05 | 10 % | 0.3 % | 40 % | 4 % |
   | 0.1 | 19 % | 1 % | 65 % | 13 % |
   | 0.2 | 36 % | 4 % | 89 % | 40 % |

   Exact for independent seconds (the readings are not quite independent; a phone held still at the
   edge of range is the worst case). A pass-by falls by a factor of ten to twenty-five. Standing at
   30 cm for long enough still reports at a high *p*: that is the gate's question, not this one, and
   the two-phone 30 cm pass-by check D-233 asked for is still owed, now with this rule in.
3. **What it costs.** A report now takes the reporter's phone held at contact range for about two
   seconds (at least one upload interval, at most 2.5 s), where one qualifying second did before.
   A Revoked phone does not move, so a finder can hold it; a brush past it no longer reports.
   **Owner to confirm**, and if any copy says *touch* to report, *hold* is now the truer word.
4. **The lingering Insider** (D-213, open item 5). A stray reading near the target after the
   debounce no longer reports. An Insider who **keeps their phone at contact range** with their
   target past the debounce still reports them, under their own name, as D-213 ruled by design.
5. **A contact is unchanged: one reading from each phone within 2 s.** A contact already needs two
   readings from two independent radios, the same order of false positive the report now has
   (about *p*² at 30 cm). Asking two of three of each side would make it about *p*⁴ and would undo
   D-233: quick moving touches gave about one qualifying reading per phone per second, and the owner
   moved the gate to −40 to catch exactly those. `a contact still needs only one reading from each
   phone` pins it.
6. **Role-blind, and the field list is still the guarantee.** The count is one question asked of
   every reporter; `ContactHouse` is unchanged (seed, seats, who is Revoked), so no role or arm can
   reach it. A new differential case has an Insider, a Resident and the other Insider each hear the
   Revoked seat once, again later once, then twice: no report from a single reading, three from the
   second readings, and identical output under all six role exchanges and arms.

**R8, what was wrong.** The token book held the house's window and the one behind (D-213 refused the
one ahead). A phone whose clock runs ahead of the host's moves to its next token that much before
the house's window does, at every edge, so for that lead four times a minute every touch with it was
lost. The two-phone sessions measured the clocks within 0.4 to 0.5 s of each other, so the loss was
small but real, and a phone further off loses more.

7. **The book holds the window ahead too: `WINDOWS_AHEAD` = 1.** It is built once per window, the
   house's window first, then the one behind, then the one ahead, first match kept, and pinned to
   `ContactTokens.seatOf` asked in that order. **Bounded:** two ahead is refused (no honest clock is
   a whole window fast, and a token from further ahead can only come from a seed), two behind is
   refused as before, so a token resolves for exactly three windows and then never again.
8. **Rule 7.** The book is reused between edges and allocates nothing in steady state; at an edge it
   derives three tokens per seat instead of two (54 HMACs every 15 s at 18 seats). **The duplicate
   memory is derived from the same bound, so it grows from two windows to three (30 s to 45 s)**:
   otherwise a second delivery carrying a next-window token could be taken as fresh in its third
   window. To pay for that, the received list is now built once per arrival (filtered into one
   list, then appended to) rather than filtered and then copied again. Estimated at 8 bytes a
   reference and one upload a second per phone: 8 seats about 23 KB/s, was about 31; 18 seats about
   117 KB/s, was about 156. **Raised, not fixed:** at 18 seats that list is still about a quarter of
   the 0.5 MB/s budget on the host. A ring keyed by seat would make it near zero; it is the
   resolver's largest allocation and unit 8 should measure it.

| site | what it says now | what replaces it |
|---|---|---|
| `core` `resolveContacts` | a report on one reading; `heard` keeps the latest reading per pair for 2 s | a report on `REPORT_READINGS` readings within `REPORT_SPAN_TICKS`; `heard` keeps every reading per upload for the longer of the two spans |
| `core` `ContactGate` | `WINDOWS_BACK`; `DUPLICATE_TICKS` two windows | `WINDOWS_AHEAD` = 1, `UPLOAD_TICKS`, `REPORT_READINGS` = 2, `REPORT_SPAN_TICKS` = 2.5 s; `DUPLICATE_TICKS` three windows |
| `core` `ContactBook` | the house's window and the one behind | and the one ahead |
| `ContactResolverTest` | one reading reports; the window ahead refused | one reading never reports, two within 2.5 s do and 2.55 s do not, hear-miss-hear does, a 30 cm pass with stray −40s does not, two phones do not add up, a duplicate is not a second reading, a contact still needs one a side; one window ahead mapped, two ahead not, its duplicate remembered for three windows |
| `ContactResolverDifferentialTest` | single-reading reports | reports confirmed by a second reading; a new case, one reading against two, under every variant |
| `tools/one-phone/README.md` | a report on one reading | two in about 2.5 s; what a single −40 line means |

## Revision 64 — why the two-phone Revoke did not land, and three things a person's phone did by itself

*2026-11-16. Two iPhones (`bfdc404`, run `20261116-214533`): A hosted and was the only Insider (seat
0), held REVOKE (the tile said ARMED) and touched B. The house answered three touches, all as plain
`ContactAnswered`. The house logged nothing about the arm.*

### D-237 · **The house logs what became of every ability request, round lines leave the TRANSPORT screen, and a person's phone no longer votes by itself — built; the seat in the log line is provisional, owner to confirm**

**The diagnosis.** The opening Revoke clock is `Balance.REVOKE_COOLDOWN / 2` = 600 ticks = **30 s of
house time** from arming (D-132; the tick driver starts at the ARMED screen). Phone A's one
`PRESSED — ArmRevoke` sits about 27.5 s after arming (±1 s, placed between radio lines one second
apart and anchored on `ADVERTISE … WALL` stamps; B's first advertisement puts arming at about
`1794883765.4`). So the arm came **about 2.5 s inside the opening half-cooldown** and `revokeArmed` kept
the state unchanged and said nothing (Rules.kt, D-132). The arm is a single request, not a standing
one, so the three touches at about 45, 60 and 72 s were unarmed touches. Inferred from timing, not
observed: the house printed nothing. The other candidate is ruled out on loopback: the host's own
press reaching its own house is now a test, and it passes. **The pre-existing `AbilityWiringTest`
already had the host as the Insider** (seed 20261150 draws seat 0); the hop it never covered was a
guest's, and it now covers both. The bench hides this: `RoundCheat.pressing` re-holds REVOKE every
second, so a refused arm is followed by one that lands. The tile still echoes ARMED on a refused arm
(D-231's open item, unchanged here).

1. **The house says what became of every ability request** (rule 6), to the device console only:
   `REVOKE ARM — SEAT n · ARMED`, `· COOLING DOWN (READY IN Ns)` (`· ALREADY ARMED` when it was), or
   `· REFUSED <reason>`; `EGRESS FIRE`, `SURGE FIRE`, `ISOLATE FIRE` and `STATIC FIRE` say `FIRED`, `COOLING
   DOWN`, `NOT FIRED` or `REFUSED`. Read off the gate's answer and the state, after the push; no
   effect or frame changes. ⚠️ **Provisional, owner to confirm: the seat.** Only an Insider holds an
   ability, so a seat in the line names an Insider in a file, which the arming note has always
   refused. `RoundAuthority` names no seat by default (`A SEAT`); `CheatRoot` names it only when the
   launch set `SOMEONES_HOME_ROUND_SEED`, where the Mac already holds the seed and `insidersFor` redraws
   every Insider from it.
2. **Every `[house]`, `[round]`, `[round-bench]`, `[screen]` and `[house-clock]` line goes to the
   console only** (`ConsoleLines`). They also went to the transport rig's log, which the TRANSPORT view
   draws, two taps from the PLAYTEST chip on every playtest phone: a player's own `WHAT THIS PHONE IS —
   INSIDER`, `PRESSED — ArmRevoke`, `ABILITY FIRED` and the screen ledger's `ROLE INSIDER` were on their
   own screen, and the house's lines on the host's. The rig keeps the lobby's transport lines.
3. **The bench's vote is cast only on a bench seat.** `RoundCheat.react` is wired to every phone's
   pushes, and it answered every ballot with `SelectVote(caller)`, so a phone launched from the
   springboard voted by itself the instant the vote opened. It now needs `SOMEONES_HOME_ROUND`, the
   switch that runs the rest of the bench.

**The READMEs** (`tools/one-phone/README.md`, `ble-unit4-device-check.md`) now say: wait 35 s after
LIGHTS OUT, and after each meeting, before holding REVOKE (a meeting resets the clock to half, D-184);
an arm that lands restarts it at the full 60 s.

| site | what it says now | what replaces it |
|---|---|---|
| `app` `RoundAuthority` | ability requests unlogged | `abilityLog`, `namesSeats` (`AbilityLog.kt`) |
| `app` cheats `CheatRoot` | round lines to stdout and the rig | `ConsoleLines`: console only; the lobby's to both |
| `app` cheats `RoundCheat.react` | votes on every phone | votes only when `armed` |
| `AbilityWiringTest` | one Revoke test, the host as Insider | a guest's and the host's (an early arm refused and logged, a touch that does nothing, then an arm that lands); nothing about the round on any phone's rig |
| `AutoVoteGateTest`, `AbilityLogTest` (new) | (none) | no vote without the switch, a bench seat still votes; no seat named by default |

## Revision 65 — every Subroutine draws the question the house dealt

*2026-11-16, overnight run, from the fake-data audit (the Subroutine screens' fixtures). The fix is a
bug fix; the Replay flash's timing is a provisional call made without the owner: **owner to
confirm.** Desktop and loopback tests only; nothing here has been on a phone.*

### D-238 · **Every Subroutine screen draws the question the house dealt at the scan, and the bench's fixture only where no house is attached; Replay's dots flash — built; the flash's timing is the owner's to confirm**

**The bug.** The house drew a fresh question per scan (D-139, D-140), sent it on
`ScanAnswered` as `SubroutineInstance.parameters`, and graded the entry against it. `ui` never read
the parameters: all ten screens drew `SubroutineModel`'s companion fixtures, so every phone in a
round drew the same sample puzzle, and the house graded each answer against a question the player
had not been shown. Replay never flashed at all, and its four fixture dots took four taps from a
player the house had dealt three or five. **Grading was wrong**, measured before the fix by playing
every card on every seat (seeds 20260822, 7, 19, 41, 101) and answering correctly *from what the
screen drew*:

| Subroutine | graded right / answered right off the screen |
|---|---|
| Replay | 0 / 25 |
| Parity Check | 0 / 12 |
| Deallocate | 0 / 11 |
| Signal Trace | 0 / 17 |
| Jam | 1 / 23 |
| Handshake | 5 / 22 (right only when the house happened to deal the fixture's five beats) |
| Short | 8 / 22 (right only when the house dealt the fixture's three fingers) |
| Interrupt | 12 / 23 (where the fixture's band happened to overlap the dealt one) |
| Drift | 17 / 23 (likewise) |
| Sniff | 22 / 22: its question is buzzed, and `HouseBuzzer` already played the house's instance |

**What changed.**
1. **The model holds the question.** `SubroutineModel` gains one slot per drawn question (`pattern`,
   `grid`, `hold`, `wiring`, `columns`, beside the existing `sweep`, `path`, `rings`) and `deal(kind,
   parameters)`, which reads them through the same `model` readers `core` draws and grades with. The
   two sequences and Deallocate take their size from the question (`SequenceEntry.length`,
   `ColumnEntry.columns` are now set by the deal). Every screen reads its slot.
2. **BEGIN deals.** `CurrentSubroutine` carries the scan's parameters from the arrival to BEGIN, so
   the kind and the question come off one row. **On a housed phone, BEGIN with no dealt question
   opens nothing**; on a phone with no house the fixtures stand, and the render harness still draws
   the design.
3. **A question the client cannot draw opens nothing.** The read happens at the arrival (the
   boundary `MalformedSubroutineParameters` always named), and the phone stays on the scan. The house
   cannot deal one (`SubroutineDrawTest`); this is the second refusal.
4. **Replay's dots flash in the house's order**, once, from the screen opening: a still 0.8 s, then
   each dot lit 0.6 s on a 0.9 s step. This is D-139's arrangement one Subroutine along (the client
   renders the question deterministically from the parameters and the screen's own clock; no Effect,
   no schema row), and it replaces the KDoc's earlier claim that the flash had to be an Effect. Taps
   land throughout. ⚠️ **The timing is a placeholder for the owner to confirm**, like the speeds
   playtest owns.

**What did not change.** The wire: `SubroutineInstance` was already the narrow client type with its
schema row, and it still carries the question and never the answer, so a client receives nothing it
did not before. The rules, the grading and the fake are untouched: the Insider's fake is dealt by
the same draw, drawn by the same screen and graded identically, and its right answer writes nothing.
**Handshake has no wrong answer a screen can give**: the entry is one ordinal per tap and the timing
is not graded (the gap `HandshakeRhythm`'s KDoc already names), so with the beat count now right
every completed Handshake is accepted.

| site | what it said | what it says now |
|---|---|---|
| `ui` `SubroutineModel` | screens read the companion's fixtures | `deal` writes the house's question into the slots the screens read |
| `ui` `FlowModel.beginSubroutine`, `currentFrom` | the instance's parameters dropped at the join | carried on `CurrentSubroutine`, read at the arrival, dealt at BEGIN; housed and undealt opens nothing |
| `ui` `ReplayScreen` | four dots, no flash | the dealt dots, flashed in the dealt order |
| `HousedSubroutineQuestionTest` (`ui`, new) | (none) | all ten: the model holds the dealt question; the pixels differ from the fixture's and the Insider's equal the Resident's; housed and undealt opens nothing; a malformed question opens nothing; the flash order |
| `SubroutineQuestionWiringTest` (`app`, new) | (none) | five phones over real loopback sockets, every card of three seeded rounds: the phone decodes the house's parameters and holds them; answered right from what the phone holds it is graded right, wrong graded wrong, for all ten for a Resident and for the Insider's fake; only a Resident's right answer moves System Integrity |

## Revision 66 — the REVOKE tile waits for a clock every phone can see

*2026-11-16, overnight run, review finding R1 (CRITICAL). The fix is a bug fix; drawing the cooldown
from public times is a provisional call made without the owner: **owner to confirm.** When ARMED
comes back down is NOT decided and is raised below with a recommendation. Desktop and loopback tests
only; nothing here has been on a phone.*

### D-239 · **Page 2's clocks are drawn from LIGHTS OUT, a meeting's end and an Egress opening, the same on every phone; the REVOKE hold says ARMED only once the public clock has run out — built, provisional, owner to confirm; when ARMED ends — raised**

**The bug** (R1, and the likeliest cause of D-237's failed two-phone Revoke). `Rules.revokeArmed` is
a silent no-op while the seat's clock runs: 30 s after LIGHTS OUT (D-132), 30 s after every meeting's
end (D-184), 60 s after an arm that took. `FlowModel.armRevoke` set ARMED on every hold and nothing
cleared it, so a hold 2.5 s early said ARMED over an unarmed house and the touch did nothing.
`AbilityWiringTest` asserted the ARMED echo on a refused arm as correct, and the bench re-held REVOKE
every second, so a refused hold was always followed by one that took.

**What changed.**
1. **Three public clocks on every phone** (`ui` `PublicCooldown`, `PanelState.revokeClock`,
   `accessClock`, `egressClock`). Each restarts on a push every seat gets: `OpeningMessage` (all three
   at half), `MeetingEnded` (all three at half, except the Egress's while an Egress runs, D-184's
   freeze) and `EgressOpened` (the house-wide Egress clock at full). `FlowHost` counts them a second at
   a time from the push, the Static window's arrangement. The lengths are `Balance`'s, now named once
   (`REVOKE_OPENING`, `ACCESS_OPENING`, `EGRESS_OPENING`, which `core` writes the seats' clocks from
   too) and converted by `Balance.wholeSeconds`, plus **one second of pad so the phone never says READY
   before the house**: the host's tick driver starts a quarter-second poll after arming. The pad errs
   late on purpose: a hold inside it arms the house without the echo, and the next hold says ARMED over
   an arm that is really there; erring early is ARMED over nothing, which is the bug.
2. **The REVOKE hold sends `ArmRevoke` every time, the same way (rule 1), and says ARMED only when the
   public clock has run out.** Before that the tile reads COOLING DOWN with its bar filling. The branch
   reads public time only: not the role, not an answer (there is none), not the request.
3. **The bars are real.** The mockup's fixed 0.38 / 0.64 / 0.82 are now the three clocks' fractions;
   the fixtures remain only for a phone no house has armed, so the render sweep still draws the design.
   The Access bar draws the pool's public half only: a Tier-2 fire restarts the actor's own pool, which
   no bar draws (the same question as below, one tier down).
4. **An arm survives a meeting on the tile as it does in the house** (D-184 resets the clock, not the
   arm), and a new LIGHTS OUT clears an earlier round's ARMED.
5. **The bench holds REVOKE once**, when its own tile reads READY, as a person does.

**Parity.** Before anybody holds anything, an Insider's page 2 and a Resident's draw the same bars,
inks, fills, borders and state at every public moment; only D-142's words differ (REVOKE / POWER, the
two resting lines). After an Insider's hold the tile says ARMED, an asymmetry D-231 already built.

**Raised, not decided: when ARMED comes back down.** Today it never does (unchanged). The provisional
direction was *on `AbilityFired`, with a post-fire cooldown*, and that needs a ruling for three
reasons: (a) `gdd.md:322`, `:325` and M9 (`gdd.md:416-417`) say the Insider's phone shows *no light
change, no visible animation when arming or landing a Revoke* and *no feedback whatsoever*, and D-231
already raised that a tile changing at contact is that feedback; (b) `RoundMessage.AbilityFired` does
not say which ability, and Surge, Isolate and Static send it too; (c) a private 60 s cooldown on the
Insider's tile, with every Resident's tile at READY, is a resting tell no ruling (D-009, D-142) covers.
The options:

| | ARMED ends | cost |
|---|---|---|
| A | on `AbilityFired(true)`, then the seat's own cooldown drawn | feedback at contact (M9); a private clock on one role's tile; needs the ability named on the wire |
| B | at the next meeting's end, silently; the tile rejoins the public clock exactly, since the house's clock is then the public half | a spent Revoke reads ARMED until the meeting |
| C | 45 s after the hold, on the phone's own clock, with the house disarming at the same moment (the window D-142 and `gdd.md:323` name); the echo then waits until 60 s after the hold, drawn as the public clock | the window is built in `core` too; a hold between 45 and 60 s fills and says nothing |

**Recommendation: C**, because it is the design's own window, it never changes the tile at contact,
and it keeps every drawn bar public. It needs the owner's word on two things the docs do not say: what
the 45 s window does across a meeting (D-185 ends *running ability effects*; whether an armed Revoke
is one is unruled, and today a meeting keeps the arm), and whether the window expiring is silent.

**Not covered.** A phone that joins mid-round, or misses `OpeningMessage`, has no clock and echoes
ARMED as before. `FlowHost` counts only while the panel is composed (the cheat picker or a
backgrounded app pauses it), which errs late. Nothing here has run on a phone, and the simulator
rehearsal with the bench's single hold has not been run.

| site | what it said | what it says now |
|---|---|---|
| `model` `Balance` | `REVOKE_COOLDOWN / 2` etc. written in `core` | `REVOKE_OPENING`, `ACCESS_OPENING`, `EGRESS_OPENING`, `wholeSeconds` |
| `core` `Rules` | `/ 2` at arming and meeting end | the named halves (same values) |
| `ui` `FlowModel.armRevoke` | ARMED on every hold | ARMED only once `revokeClock` has run out; the request on every hold |
| `ui` `FlowModel.houseSaid` | clocks drawn nowhere | `OpeningMessage`, `MeetingEnded`, `EgressOpened` restart them; `secondPassed` counts |
| `ui` `PanelVals` | bars 0.38 / 0.64 / 0.82 | the clocks' fractions; the tile drawn from `revokeShown` |
| `app` cheats `RoundCheat.pressing` | REVOKE re-held every second | held once, when the tile reads READY |
| `AbilityWiringTest` | a refused arm echoes ARMED | an early hold sends the same request and shows no ARMED; a hold after the public clock shows ARMED and the touch lands, for a guest and the host |
| `PublicCooldownTest` (`ui`, new) | (none) | the two roles' page 2 equal at every public moment; the early hold sends and does not echo; the lengths are `Balance`'s plus the pad |

## Revision 67 — three review findings: the bench's seed on a person's press, the arrival queue's order, and the out named for evasion

*2026-11-16. Findings R4, R6 and R7 of the read-only review of `67f3313..b35bba3`.*

### D-240 · **A person's LIGHTS OUT never reads the bench's seed; the seed switch is honoured only beside a bench switch — built**

**The bug.** `CheatRoot` answered the host's press with `RoundCheat.seed()`, which read
`SOMEONES_HOME_ROUND_SEED` whenever it was set. `round.sh` sets it by default (20261150), and
`tools/one-phone/README.md` says that seed deals the Insider role to seat 0. `armingCordon` scans
`app/src/commonMain` only, and the press is answered in `app/src/cheats`, so the KDoc there that said
*no launch switch is read on this path* was false. The house's ability lines (D-237) also named seats
whenever the seed switch alone was set.

1. **`RoundCheat.benchSeed` is the one read of the seed switch.** It returns the named seed only on a
   bench launch, one that also holds `SOMEONES_HOME_ROUND` (the scripted phone and every Simulator) or
   `SOMEONES_HOME_ROUND_BY_HAND` (new, below). On any other launch it **does not read the seed switch
   at all**. `seed()` is `benchSeed() ?: drawRoundSeed()`. The press is `lightsOutPressed`
   (`Cheats.kt`), pulled out of the composable so a test can hand it the launch environment, and
   `namesSeats` asks the same `benchSeed()`, so a round drawn for real never has its Insider's seat in
   a log line.
2. ⚠️ **`--by-hand` is still a bench launch.** It used to drop `SOMEONES_HOME_ROUND` and keep the seed,
   and the README's rounds A and D need the seed (`ble-peer sniff --round-seed 20261150`, `= SEAT 0`).
   So `round.sh start --by-hand` now sets `SOMEONES_HOME_ROUND_BY_HAND=1`, which honours the seed and
   runs no script. **For the owner to weigh:** a two-phone session whose host is launched with
   `--by-hand` still deals the README's Insider to that host. A phone opened from the Home Screen
   carries no switch, so it always draws a secure seed.
3. **`benchSeedCordon`** (new, under `check`): in `app/src/cheats` the seed switch may be named only by
   its own declaration and inside `fun benchSeed`, whose body must name `ROUND_SWITCH` before it names
   the seed.

| site | what it said | what it says now |
|---|---|---|
| `app` cheats `RoundCheat.seed` | the seed switch, on any launch | `benchSeed() ?: drawRoundSeed()`; `benchSeed` reads it only beside a bench switch |
| `app` cheats `CheatRoot` `onLightsOut`, `namesSeats` | `RoundCheat.seed()`; the seed switch alone | `lightsOutPressed`; `benchSeed() != null` |
| `tools/one-phone/round.sh` `--by-hand` | no `ROUND`, the seed | `ROUND_BY_HAND`, the seed |
| `build.gradle.kts`, `buildSrc` | `armingCordon` over production only | and `benchSeedCordon` over `src/cheats` (`BenchSeedRulesTest`) |
| `LightsOutSeedTest` (new) | (none) | a press with the seed alone, or nothing, never asks for the seed switch and arms another seed; `ROUND` and `ROUND_BY_HAND` arm the named one |

### D-241 · **The arrival queue awaits the rules, and the house takes one reduction at a time — built**

**The bug.** `LobbyHouse.attend` is the one consumer of every arrival, and `CheatRoot`'s four doors
were `scope.launch { rules.value?.sighted(…) }`: a coroutine per arrival, so the queue took the next
arrival while the last was still pushing. `RoundAuthority.sighted` pushes between the two halves of a
touch, and a second touch reduced in that gap replaced `GameState.touches` (it keeps the current
tick's only), so the first touch's second half was taken for a fresh touch and **both of its phones
were answered twice** (T1, D-220), with two reductions' effects interleaved on the wire. Measured with
the fix removed: eight phones in seven rounds of four simultaneous touches were answered 8 to 10
times each for 7 touches.

1. **The four seams are `suspend`, and `attend` awaits them.** `HouseDoors` is the doors, built by
   `CheatRoot` and by every test that stands up a host, so a test cannot pass on doors the app lacks.
2. **`RoundAuthority` takes one reduction at a time** (a lock held by `arm`, `heard`, `mint`,
   `sighted`, `radioFaulted` and `fireEgress`), because the queue is not its only caller: the tick
   driver mints from its own coroutine and the bench from its own. `push` is handed its reduction's
   own state; it read the property per effect, so a late effect was classified against a newer state.
3. ⚠️ **Reasoned, not observed, on the real send.** With the fix removed and nothing else changed, the
   loopback test **passed**: in that run no send suspended, so nothing interleaved. The test's `emit`
   yields before it writes, as a real send does when a socket's buffer is full or the host's lock is
   held; with that yield it fails without the fix. Whether phones ever hit the window is unmeasured;
   the fix removes it either way.
4. **Cost:** an arrival waits for the previous one's pushes. At one upload a second per phone that is
   well inside a second; not measured on a phone.

| site | what it said | what it says now |
|---|---|---|
| `app` `LobbyHouse` / `LobbyOverTransport` | four plain seams | four `suspend` seams, awaited by `attend` |
| `app` cheats `CheatRoot` | a launch per arrival | `HouseDoors` (new, `app/commonMain`) |
| `app` `RoundAuthority` | any caller reduces any time; `push` read `state` per effect | one reduction at a time; `push(effects, after)` |
| `AbilityWiringTest`, `ArmedForEveryPhoneTest`, `SubroutineQuestionWiringTest`, `EvasionNoticeTest` | copied the launching doors | `HouseDoors` |
| `ArrivalOrderTest` (new) | (none) | eight phones over loopback, every pair touching once in seven rounds of four at once: one answer per toucher per touch, no effect sent against a newer state, each phone's stream equal to the house's deliveries in order; and concurrent callers on the authority alone |

### D-242 · **A seat that is out is never named for evasion; its phone still sends the same report — built, provisional, owner to confirm**

**The bug.** `ContactUploader.leftTheForeground` checks only that the phone holds a seed, and
`onTheNotice` (`core` `Rules.kt`) took any seated seat. So a Revoked or Restrained player who locked
their dark phone was named at the next meeting. D-187 is about living players hiding from the house.

**⚠️ The provisional call (owner to confirm): the house neither lists nor names a seat that is out.**
`onTheNotice` refuses a seat that is out, and the check-in gate's `NoticeIssued` leaves out any seat
that is out by then, so a seat listed while living and Revoked before the meeting is not named either.
**The phone is unchanged, and that is rule 1:** an out phone that leaves the foreground still sends
`RadioFaulted(Person)` on its return, the same bytes a living phone sends. A phone that stayed quiet
because it is out would put one frame fewer on the Wi-Fi, and the missing frame would say who is out.
The report is admitted and recorded, and moves nothing.

Open for the owner: (a) whether a seat that evaded while living and is out by the meeting should still
be named (built: it is not); (b) reasoned, not checked against play: the notice arrives at the
meeting, where the out are already known to the room (D-134's STAND AND WALK IN), so leaving them off
tells nobody anything. No event, effect or wire member changes, so the recording format does not; a
recording in which an out seat reported now replays to a shorter notice.

| site | what it said | what it says now |
|---|---|---|
| `core` `onTheNotice` | any seated seat | any seated seat that is not out |
| `core` the check-in gate's `NoticeIssued` | every listed seat | every listed seat not out by the gate |
| `app` `ContactUploader` | (unchanged) | KDoc: why an out phone still reports |
| `RadioFaultTest` | (none) | an out seat's report is admitted, reaches nobody, lists nothing; listed while living and out by the gate is not named, a living one still is |
| `EvasionNoticeTest` | three seats | and four: a Revoked guest and a living guest both lock; the same body from both, only the living one listed and named |

## Revision 68 — the times are the phone's and the countdowns are the house's

*2026-11-16. The owner, on two real iPhones: "is the clock fake?? can we stop having fake things in
the app". Every time and every countdown a housed phone drew was the mockup's.*

### D-243 · **A housed phone draws its own wall clock and the times house messages landed; the meeting's and the Egress's countdowns run from the house's pushes — built, provisional, owner to confirm**

**What was fake on a housed phone.** The strip's clock (`20:41`, `21:07`, `21:31`, `21:38`), the
lantern's `21:04` and `SATURDAY 16 AUGUST`, the house thread's `20:58`–`21:02` and the stored text's
`NOW`, the endings' `21:38` (and `BEACON` whatever the house ran), and every countdown: nothing wrote
`PanelState.secondsLeft`, so the discussion stood at `1:04`, the ballot at `0:38`, the tally at
`LIGHTS OUT IN 9` and the couch at `0:24`; the Egress widget stood at `1:42` over 22 of 32, with
`EgressHeld.remaining` on the wire and unread (E-R2-7). The lock screen also carried the design's two
standing texts at `21:01` and `21:03`.

**What draws them now, on a phone with a house attached** (a phone with none keeps every drawn value,
so the render harness still compares to the mockup):

1. **The phone's own wall clock** (`platform` `localWallTime`, `localtime_r`; handed to `ui` by the
   app root as `WallTime`, because `ui` cannot see `platform`). The strip, the lantern's time and its
   date. Read once a second by `FlowHost` while housed; the panel changes when the minute does. Rule 4
   is untouched: `core` never sees it and nothing in the round reads it.
2. **The moment each house message landed, on that clock.** `OpeningMessage` stamps the thread's two
   opening rows and the role's line; each `MessageDelivered` is stamped on arrival, on the lock screen
   and as a new row of the house thread; `RoundEnded` stamps the endings (the line and the sign-off).
   The Insiders' ending prints the Egress type the house ran. **The design's `21:01` text is not in a
   housed thread, and the two standing stand-ins leave a housed lock screen** when the house attaches:
   no house sent them (removed by value, so nothing the house sent is touched).
3. **The meeting's clocks run from the push that opens the phase.** `MeetingPhaseOpened` starts
   `secondsLeft` at the window the house runs that phase for (`Balance.DISCUSSION_SECONDS`, the ballot
   it sent, `Balance.TALLY_SECONDS`: the constants `HouseClock` closes each phase on), `MeetingEnded`
   clears it, and the gate has none (D-104).
4. **The Egress countdown is the house's `remaining`.** `EgressOpened.remaining` starts it and is the
   length the bar is a fraction of; each `EgressHeld.remaining` resets it, and HELD stops it (D-133);
   `EgressContained` clears it. Ticks become seconds by `Balance.wholeSeconds`, which rounds up.

**One second hand, D-239's.** `FlowModel.secondPassed` now also counts these two down, keyed the same
way in `FlowHost`, so a push restarts a fresh second and a partial second is dropped: the phone reads
late by the push's travel, never early. **Nothing moves when a clock reaches zero**: no screen, no
light, no request (rule 5). The house moves every phone by a push on its own clock, as before.

**⚠️ Provisional (owner to confirm).** This is E-R2-7's second answer, *send an anchor and let the
phone count*, which `secondsLeft`'s old KDoc refused (*six phones counting down differently*). D-239
took the same road for page 2; this extends it to the meeting and the Egress. The phones agree to
within a push's travel, not exactly. The clock is twenty-four hour, the design's form, whatever the
phone's own setting.

**Raised, not decided: the Egress bar on a living phone.** `gdd.md:545` and `:1052` give the couch
*the real Egress progress that no living player of either role can see*, while `:375`, `:735` and
`:1012` make the living's widget *the Egress countdown*. The mockup draws the living's widget with a
countdown and a bar; both are now real, and since the timer's length is known, the countdown already
says what the bar does. The couch's percentage and the living's bar are the same fraction. If the
owner reads `:545` as hiding the bar from the living, the bar comes off their widget and the number
stays.

**Not covered.** A phone that relaunches mid-round is caught up by D-097's replay of every push, at
once, so its clocks restart at the replay, late (page 2's by up to their whole length), and its
replayed texts carry the replay's time. Fixing either needs the house's time on the wire, a time base
the build does not have (E-R2-4); not done. `FlowHost` counts only while the panel is composed (the
cheat picker or a backgrounded app pauses it, which errs late). Nothing here has run on a phone.

| site | what it said | what it says now |
|---|---|---|
| `platform` `localWallTime` | (none) | the phone's clock in its own zone; `LocalWallTimeTest` |
| `app` `deviceWallTime`, cheats `CheatRoot` | (none) | handed to `FlowModel(wallClock = …)` |
| `ui` `WallTime`, `HouseText`; `PanelState.wallTime`, `openedAt`, `houseTexts`, `endedAt`, `egressLeft` | (none) | the clock, the arrival stamps, the Egress countdown |
| `ui` `PanelVals.clock`, `lockClock`, `lockDate`, `houseThread`, `inbox`, `endedAt`, `egressEndedLine`, `egressLit`, `egressLeftText`, `egressPercent`, `countdown` | the mockup's times and moments | the phone's clock, the arrivals, the house's countdowns; the drawn values only with no house; a dash where a housed phone has no reading |
| `ui` `FlowModel.houseSaid` | `secondsLeft` and `remaining` unread | the phase push and both Egress pushes write them; the opening, a text and the ending are stamped |
| `ui` `FlowModel.secondPassed`, `FlowHost` | page 2's three clocks | and the phase's and the Egress's; a wall-clock read each second while housed |
| `ui` `FlowModel.houseAttached` | the flag only | and the stand-in standing texts leave |
| `RealClocksTest` (new), `EffectPushTest` | (none) | housed phones draw the injected clock and the arrivals, unhoused ones the design's; the clocks follow the pushes in process, under `FlowHost`, and over a real socket |

## Revision 69 — no socket write under the house's lock, every report kept until the house has it, and D-227's guard proven

*2026-11-16. Finding R12 of the review of `67f3313..b35bba3` (raised in D-241), the two limits D-229
left open, and the guard `1d16974` could not break.*

### D-244 · **A push queues on its seat's own connection and returns; nothing the house holds a lock for waits on a socket — built**

**The bug (R12).** D-241's lock (`RoundAuthority`'s one reduction at a time) is held across the
pushes, and a push ended at `TransportHost.send`, which was the socket write itself (`ws.send`).
`EffectWire.emit` held its own lock across it too. A guest whose socket stops draining (suspended,
out of range, half-defunct) fills Ktor's buffers, `ws.send` suspends, and every other phone's frames,
every other door into the rules and the tick driver wait behind it: the lamp's same-frame promise
(rule 7) and the evasion return path, both held by the slowest phone in the house. Measured before the
fix: with one guest's write held back, LIGHTS OUT never returned (`StalledSocketTest`).

1. **Each seated connection has its own ordered outbound queue.** `TransportHost.send` encodes, puts
   the frame on that seat's queue and returns; the connection's own writer takes them off in order
   and does the socket write. The reduction lock and the effect wire's lock are held for an enqueue,
   never for network I/O. One reducer, effects in reduction order, and each seat's frames in the
   order the house made them (one FIFO per connection) are unchanged.
2. **`Seated` is the first frame on a new connection's queue**, put there before the connection is
   findable, so no frame for a seat can reach its phone ahead of its seat (it could before, by a
   race).
3. **A seat whose queue holds `OUTBOUND_FRAMES` (512) is not reading and is dropped** like any lost
   connection; `send` throws, the same signal as no connection (`onUnreachable`), and the phone comes
   back by its token and asks for what it missed (D-097). A writer whose socket fails drops its link
   the same way. Clock probes are still answered directly on the read loop (rtt, D5).

**Cost:** a channel per connection and one coroutine; nothing on the blackout path.

| site | what it said | what it says now |
|---|---|---|
| `platform` `TransportHost.send` | the socket write | enqueue on the seat's own queue; drop the link when full |
| `platform` `TransportHost` connection | one read loop; `Seated` written after the link was findable | a writer per connection; `Seated` first on the queue |
| `platform` `EffectWire`, `app` `RoundAuthority` KDoc | the push suspends on a socket | no network I/O under either lock |
| `StalledSocketTest` (new, app) | (none) | four phones over loopback, one guest's write held: arming and a meeting reach the other three within 2 s (27 ms measured), every stream in the house's order, the held guest's too once it drains |
| `OutboundQueueTest` (new, platform) | (none) | a held seat delays no other seat's frames, which arrive in order; a full queue drops the seat |

### D-245 · **Every radio report is kept until the house answers it: written beside the seat token at a leave, sent again on every resume, and heard once per onset — built**

**The two limits D-229 raised.** (a) iOS ends an app whose Camera or Motion grant is withdrawn in
Settings, and going to Settings is a leave: the report owed for it lived in memory and failed with the
process, and the relaunch resumed its seat owing nothing. (b) `TransportClient` is at-most-once: a
report handed to a guest's socket that failed while the phone was suspended was lost with it.

1. **The body names its onset.** `RoundBody.RadioFaulted(cause, onset)`: `onset` is 16 lowercase hex
   characters, drawn fresh from the secure source for each onset by the phone. Random and fixed-width,
   never a counter or a time, so the body is still one length from every phone and says nothing about
   how often or when (rule 1, D-223). A body with any other width is not read.
2. **The phone keeps every report until the house answers it.** `ContactUploader` holds the reports
   it owes, oldest first (at most 8). A report goes up one a beat, and goes up again on every new
   connection until `RoundBody.FaultHeard(onset)` comes back. Once per onset is unchanged: flicks
   between two beats are one report, and the radio's onset on that beat is folded into it.
3. **The house answers every report and hears each onset once.** `LobbyHouse.attend` puts the report
   to the rules (awaited, D-241) and then sends `FaultHeard` to that seat, **whatever the rules made
   of it** (named, recorded only, refused, out or not: rule 1). `RoundAuthority` keeps each seat's
   onsets for the round and puts a repeat to nothing, logging `HEARD AGAIN`; the recording still holds
   one `RadioFaulted` per onset, so the format does not change (still `recording/26`). `FaultHeard` is
   transport bookkeeping like `Gap`: not a push, no sequence, no schema row, no screen.
4. **A report owed at a leave is written at the leave.** `SeatStore` gains the owed reports
   (`owedReports`, `keepOwedReports`, `forgetOwedReports`); `DeviceSeat` writes them to
   `owed-reports.txt` in Documents beside `seat-token.txt` (`platform` `saveOwedReports`, atomic,
   throws when it did not write). The text is a one-way fingerprint of the round's contact seed (its
   token for a window no phone is in) and one line per report. When a relaunched process hears its
   round's seed again on its resume, it reads back the reports kept for that round and owes them; a
   report kept for another round is forgotten. Answered, the report leaves the file; the round's end
   or a hang-up empties it. **A crash in the foreground writes nothing, so D-036 holds**: only a leave
   writes.

**What this does not cover.** A guest whose Local Network grant is withdrawn cannot reach the house
at all (D-229). The bench rig (`RoundClient`) is the one phone root, and `CheatRoot` passes
`DeviceSeat`; every other construction keeps reports in memory. Nothing here has run on a phone: the
relaunch is a test's process ending (the phone's coroutines cancelled, a fresh phone over the same
store), not iOS ending one.

| site | what it said | what it says now |
|---|---|---|
| `model` `RoundBody.RadioFaulted` | `cause` | `cause`, `onset` (16 hex) |
| `model` `RoundBody.FaultHeard` | (none) | host to the reporting seat: the onset it has |
| `platform` `saveOwedReports`, `loadOwedReports`, `clearOwedReports` | (none) | `owed-reports.txt` beside the token; `OwedReportsStoreTest` |
| `app` `SeatStore`, `DeviceSeat`, `MemorySeat` | the token | and the owed reports |
| `app` `ContactUploader` | one `owed` flag in memory | the reports owed, kept, re-sent per connection, cleared by the answer |
| `app` `RoundOverTransport` | | `FaultHeard` to the uploader; a seating re-sends |
| `app` `LobbyHouse`, `HouseDoors`, `RoundAuthority.radioFaulted` | the cause | the body; the answer after the rules; one onset once |
| `app` cheats `RoundClient`, `CheatRoot` | | the store, `DeviceSeat` on the phone |
| `ContactUploaderTest` | | re-sent per connection until answered; two onsets two names; kept at the leave and read back by a fresh uploader; a foreground crash keeps nothing; one length |
| `EvasionNoticeTest` | | over loopback: a report into a socket that failed while suspended reaches the house once; a lost answer is heard once; a report owed when the app is ended reaches the house from the relaunch and the file is cleared; a foreground crash and relaunch names nobody; every report answered, out or not |

### D-246 · **D-227's loss ordering is proven by breaking it — built (a test, no behaviour change)**

`1d16974` reported a seat's loss under `TransportHost`'s lock, so a loss is always reported before a
later resume of the same seat, and took that lock uncancellably, so a stopping server reports every
loss. Neither could be made to fail by reverting it: on loopback each window is microseconds wide.
`LossOrderTest` holds the window open on purpose, with an `onLost` that takes 400 ms (a thread
descheduled at the wrong moment, or a slow desk):

1. **Loss before resume.** A phone drops; while the loss is still being reported it resumes by its
   token. The caller must hear `lost` before `seated`. With the loss reported after the lock (the code
   before `1d16974`): *Expected <[lost 0, seated 0]>, actual <[seated 0, lost 0]>*.
2. **Every loss reported.** Four phones seated; the first loss is slow, so the other three are waiting
   for the lock when the stopping server cancels them. Without `NonCancellable`: *a stopping server
   reported 1 of 4 losses: [0]*.

## Revision 70 — a housed phone draws the house's people and counts, or nothing

*2026-11-17. The owner's "can we stop having fake things in the app", the rest of it: after D-243
took the times, the design's people, counts and sample texts were still on housed phones.*

### D-247 · **What a housed phone draws is the house's, or nothing: the Insider's own line, the seat count, the meeting's counts, the endings, the notes, the caller and the banners — built; the inbox and the note's words provisional, owner to confirm**

**What was still fake on a housed phone, and what draws it now** (a phone with no house keeps every
drawn value, so the render harness still compares to the mockup):

1. **The Insider's blackmail text** said *You still have Priya's spare key* to every Insider in every
   round. D-101 makes it a verbatim template over the line the player handed over, and nothing carried
   the line back. **`RoleRevealed` now carries `line`**: an Insider's own one line, read off the
   house's desk at arming (`GameState.lineOf`), and blank on a Resident's (D-116: the house never uses
   a Resident's line, so it is never sent again). It rides the existing seat-only `ROLE_REVEALED` row
   rather than a kind of its own, because the blackmail text *is* the Insider's role reveal (story
   2.7): one message per seat at the same point in the burst, so the burst's shape is unchanged and
   no phone learns anything it did not type. The thread reads *I know you said "…". Follow my
   instructions tonight and nobody else needs to hear about it.* `PanelState.heldOver` holds it as a
   `HeldLine`, whose `toString` prints a length (`OneLine`'s rule), and drops it when the round ends.
   **Both `RoleRevealed`s print a length too.** `RoundAuthorityTest` held that *no effect carries a
   line while the round runs*, by sweeping every pushed effect's `toString`; that rule is now
   *exactly one does, the Insider's own reveal, and it prints none*, and the test checks both halves.
   `Transcript` renders the seat and the role and never the line, and a replay rebuilds blank lines,
   so live and replayed transcripts stay byte-identical: **no recording version bump.** Checked
   against D-116 and D-101: the line reaches only the phone that typed it.
2. **PERIMETER ARMED's `6 RESIDENTS`** is the round's seat count (`PanelState.seats`, the roster's
   length). Everyone is a Resident.
3. **The meeting counts.** `MeetingCounts` held the design's 6/6/4/4/3/4 and nothing reset them, and
   `answered` is pushed by nothing, so every housed caller's screen said `4 OF 6 ANSWERED`. The counts
   are nullable now and **reset to unsaid** when a house attaches (unless a house already counted, so a
   reconnect keeps a meeting in progress) and on each `MeetingOpened` and `StandAndWalkIn`, which
   arrive before the gate's first count. **`answered` stays unsaid:** the house hears a ring answered
   only as a check-in, which is a different fact, so the caller's screen draws the headline alone. The
   discussion's ready bar is now drawn against the living, like its caption (D-147).
4. **The Residents' ending** drew `4 OF 6`, ELLIOT, PRIYA, MARCUS, ROSE and `EGRESSES CONTAINED 2`. It
   now lists the roster less the seats `InsidersRevealed` named (by seat, blanks left out); counts the
   `EgressContained` pushes since LIGHTS OUT; and **drops the standing count**, because the house sends
   no list of who is still standing. The chips now name every Resident, out or not.
5. **Notes.** Every Resident read *why was rose in the garage* (`noteSeed` is never set). ⚠️ A housed
   Resident's note is now *somebody was already at the marker*: no name, no room, no count.
   Provisional words. Naming a real player instead would put the same accusation on every
   Resident's phone, which is the app making a read the room is meant to make.
6. **Reconnecting's `ATTEMPT 3 . 8S`**: nothing counts attempts, so a housed phone draws nothing there.
7. **The ring's fallback caller** (PRIYA, ELLIOT) when the house named nobody: a housed phone draws no
   name.
8. **⚠️ The inbox backlog (MUM, DELIVERY, FLAT CHAT, VOICEMAIL, BANK, UNKNOWN, PRIYA and their times)
   is gone from housed phones. Provisional, owner to confirm.** It was meant as a per-player disguise,
   but `inboxSeed` is never set, so every phone showed the same list, and D-156 refuses fake
   deliveries. A housed inbox is the house's one row, identical on every phone, so density says
   nothing.
9. **Found by the sweep: every house text arrived under the design's banner.** `MessageDelivered`
   lands on `Quiet`, whose static arrival is the design's `you're up` from NUMBER WITHHELD at `21:03`,
   so the house's words never showed on the banner. On a housed phone `Quiet` now draws the house's
   newest text at the time it landed, and swiping it removes that text from the lock screen
   (`Notifications.houseText`, one value for both). A text that landed with no clock is stamped `-`
   rather than `NOW`, D-243's reason.
10. **The evasion notice's `21:04–21:07`** was already reachable only unhoused (D-223); the sweep
    confirms it.

**The sweep.** `HousedContentTest.noHousedScreenDrawsAFixture` renders every `ScreenId` in both roles on
a housed phone fed a round, a meeting and an ending, and fails on any of the design's names, senders,
counts and times. Found nothing else.

**Not done, and reported rather than guessed.** FILES' `1 ITEM . DISK 01 . DESTINATION . RACK`, and the
Terminal, Timelapse and Ghost3 counts, are unit 4d. The survivors are not on the wire; listing them
would need `RoundEnded` to carry them. Nothing here has run on a phone.

| site | what it said | what it says now |
|---|---|---|
| `model` `Effect.RoleRevealed`, `RoundMessage.RoleRevealed` | the role | and `line`: an Insider's own, blank for a Resident |
| `core` arming | the role | and an Insider's own line off the desk |
| `ui` `PanelState.heldOver`, `egressesContained`, `residents`; `HeldLine` | (none) | the quote, the contained count, the Residents |
| `ui` `PanelVals.houseLine`, `inbox`, `noteLine`, `callerOr`, `accountedFor`, `residentNames`, `residentsStanding`, `egressesContained`, `reconnectLine`, `arrival` | the design's | the house's, or nothing, on a housed phone |
| `ui` `MeetingCounts`, `MeetingModel` | Int fixtures, never reset | nullable; reset on attach and per meeting; nothing drawn while unsaid |
| `ui` `FlowModel` | `dismissNotification` swiped the static arrival | swipes the banner the panel drew |
| `app` cheats `ScreenLedger` | `GATE 4/6` | `-` for an unsaid count |
| `RoundAuthorityTest` | no pushed effect carries a line | none prints one; only the Insiders' own reveals carry one |
| `HousedContentTest` (new), `HousedContentWiringTest` (new) | (none) | one guard per item, the sweep, and five phones over a real loopback socket |

## Revision 71 — NameDrop is switched off at setup, by the player's own hand

*2026-11-17, overnight run, unit 6, from the owner's two real iPhones on 2026-11-16. A provisional
call made without the owner: **owner to confirm the copy and the re-entry point.** Desktop and
simulator unit tests only; nothing here has been on a phone.*

### D-248 · **A setup step asks the player to switch NameDrop off, once per install, after the Perms screen and before a seat — built, provisional, owner to confirm**

**What happened.** iOS NameDrop (two unlocked iPhones held close offer to share contacts) came up
twice in about 40 touches between two phones. Its animation **lights both screens**. In this game
phones meet on purpose in the dark, so that is light the house never sent: rule 5's spirit, arriving
from the OS. D-077, D-093 and D-118 are unchanged; this is a setup step, not a light rule.

**What the app cannot do.** iOS has no API that switches NameDrop off or reads whether it is on. The
one sanctioned settings link, `UIApplication.openSettingsURLString`, opens the app's own page, which
has no AirDrop switch; the deeper links (`App-Prefs:`, `prefs:root=`) are private, change between
iOS versions and are refused at App Store review. **So there is no OPEN SETTINGS button**: the screen
writes the path out, one row a step, and the player says they did it.

1. **The step.** A new setup screen, `NameDrop`, light-field (bone, like every setup screen),
   reached in the light before a seat (D-161's place). Proposed copy, **flagged for the owner**:
   header `NAMEDROP`; *iPhones brought together light up to share contacts. Turn that off now, while
   the lights are on.*; the path as four rows, `1 SETTINGS`, `2 GENERAL`, `3 AIRDROP`,
   `4 BRINGING DEVICES TOGETHER [OFF]`; a caption *This phone cannot see that switch.*; and the one
   action, `DONE`.
2. **Where it sits.** After the Perms screen, before JOIN A HOME (every seat, host or guest, is taken
   through JOIN). Both ways past PERMISSIONS ask one question, `NameDropModel.pastPerms`: CONTINUE,
   and D-235's skip when the OS already grants everything. So **the skip cannot walk a fresh install
   past the step**, and a confirmed install is never stopped at it. It is not a gate: the lobby does
   not refuse a seat over it, because nothing can check it and a self-attested gate would be a tap.
3. **Once per install.** DONE writes `namedrop-off.txt` in Documents (the store beside the resident
   name; a failed write is dropped, so the worst case is being asked once more). It cannot have
   changed in any way the phone could see, so it is never asked again on its own.
4. **Again only on request.** The PERMISSIONS screen gains a `NAMEDROP` row (`TURN OFF` filled until
   confirmed, `OFF` in outline after) that opens the step, and JOIN A HOME's back arrow now always
   reaches PERMISSIONS: since D-235 a set-up phone arrives at JOIN with nothing behind it. The
   in-round SETTINGS screen was **not** used: it is dark, inert by design and identical on every
   phone, and a bone setup screen opened from it would be a lit screen mid-round.
5. **Docs.** `tools/one-phone/README.md` and `ble-unit4-device-check.md`: switch NameDrop off on
   every phone before a two-phone test.

| site | before | after |
|---|---|---|
| `ui` `ScreenId`, `PanelVals.PRE` | Boot, Perms, Join | Boot, Perms, NameDrop, Join (a setup screen) |
| `ui` `FlowModel.fallThrough` | skip to Join | skip to `nameDrop.pastPerms` |
| `ui` `PermsScreen` | CONTINUE to Join | CONTINUE to `pastPerms`; a NAMEDROP row |
| `ui` `JoinScreen` | back arrow only with a trail | back arrow always, to PERMISSIONS |
| `ui` `NameDrop.kt` (new) | — | `NameDropStore`, `NameDropModel`, `LocalNameDrop` |
| `platform` `NameDropStore` (new), `app` `NameDropDocument` (new), `Cheats.kt` | — | the install's file, wired at the app root |
| `NameDropStepTest` (new), `NameDropStoreTest` (new), `PermsSkipTest` | — | the guards below |

**Guards, each proven by breaking it** (`NameDropStepTest`, `PermsSkipTest`):
- The skip to Join by name (the code before this entry): *a fresh install skipped the NameDrop step on
  its way to a seat expected:<NameDrop> but was:<Join>*.
- CONTINUE to Join by name: *CONTINUE walked a fresh install past the NameDrop step*.
- DONE without the write: *DONE was not written to the install's file*.
- `pastPerms` always the step: *a returning install that had confirmed NameDrop was stopped at the
  step again*, and PermsSkipTest's *a fully granted phone was shown the Perms screen
  expected:<Join> but was:<NameDrop>*.
- The skip removed: PermsSkipTest *expected:<Join> but was:<Perms>*.
- JOIN's back arrow without its fallback: no `back-chevron` on a skipped phone's join screen.

**Not verified:** on a phone (no installs in this unit), whether the 4-row path matches every iOS
version's wording (it is iOS 17's), and an iCloud restore to a new phone, which carries the file
with it although the new phone's switch may be on.

## Revision 72 — the reveal is one size, the notice lets a housed phone go, and the slow test was the clock

*2026-11-17, overnight run, unit F1. Two findings by the orchestrator after D-247 (a red ui test on
main, and `RoleRevealed`'s size following the role), and one by the simulator hunt (H2). Desktop and
simulator unit tests only; nothing here has been on a phone.*

### D-249 · **Every seat's `RoleRevealed` is one length on the Wi-Fi, whatever its role and its line — built**

**What leaked.** Every seat is sent exactly one `RoleRevealed`, at the same place in the arming burst,
so its size is a reading of the role for anybody who can measure the Wi-Fi. That breaks the
symmetric-frame promise that R1, S1 and T1 keep for a touch (D-216, D-219, D-220). As D-247 shipped it,
a Resident's was `"insider":false` with the blank line dropped (the JSON leaves a default out) and an
Insider's was `"insider":true,"line":"…"`. Measured over loopback, carried and encoded: **Resident 113
bytes; Insider 112 with a blank line, 127 with one character, 246 with 120, 163 with a line JSON
escapes, 366 with a line past the limit.** Even before D-247, `true` and `false` differ by one byte, so
the Insider's was the short frame.

**Now.** The wire member is `RoleRevealed(role: Int, lineHex: String)`. `role` is one digit (1 Insider,
0 Resident). `lineHex` is the line's UTF-8 bytes in lowercase hex, zero-padded to `LINE_BYTES` =
`LobbyBody.Handover.LIMIT × 3` = 360 bytes, so it is always 720 characters. A Resident's is all
padding. A longer line is cut at a character. Hex rather than the text, because JSON escapes some
characters and not others. Both widths are checked when a message is built, so one of another width
does not decode. The call sites keep `RoleRevealed(insider, line)` and read `.insider` and `.line`
(decoded). **Every reveal is now 843 bytes**, for both roles and all six lines. The cost is about 730
bytes per seat, once a round.

**The line's limit moved to the model** (`LobbyBody.Handover.LIMIT`, 120, as typed). The house now
bounds what it keeps to it (`LobbyDesk.handedOver`, as a name is bounded), and the padding reads it.
The lobby screen's `OneLine.LIMIT` points at it.

**Guards, each proven by breaking it.** `RoleRevealedFrameSizeTest` (app; a real `TransportHost`, five
real clients over loopback, the rules' own arming, `TouchFrameSizeTest`'s method) arms once per line:
blank, one character, 120 characters, 120 three-byte characters, a line JSON escapes, and one past the
limit. Every Resident hands over a line too. With D-247's shape put back: *a reveal's frame length
follows the role or the line: {blank={Resident 0=113, Insider=112, …}, one character={…Insider=127…},
…}*. `RoleRevealedSizeTest` (model) checks the same at sequence numbers 1 to 4 321, the round trip,
and that a reveal of the wrong width does not decode.

**The audit: every seat-only `RoundMessage` whose content depends on the role** (file:line in
`model/.../protocol/RoundWire.kt`):

| message | why it is role-dependent | what a reader of the Wi-Fi sees | done |
|---|---|---|---|
| `RoleRevealed` :291 | it is the role | the size (above) | **fixed** |
| `IsolateTargetsOffered` :137 | its row is `Insider/Live` only (`EmitSchema.kt:244`), so only Insiders' sockets get it: at arming (`Rules.kt:410`), when an Egress starts (`:1858`) and when one is contained (`:1945`) | **one frame more** on every Insider's socket at each of those moments | ⚠️ **reported, not fixed.** A frame count, not a size. Closing it means a frame of the same length to every Resident, which is a wire and allowlist change. **Owner to decide.** |
| `AbilityFired(cooldownStarted)` :79 | Insider-only row (`EmitSchema.kt:194`); `true` and `false` differ by a byte | whether a Surge, Static, Isolate or Egress request *took* (`Rules.kt:746, 827, 891, 1829`). The frame exists only after an Insider's own request, so the reader already knows the socket is an Insider's | reported. The touch path sends only `true`, padded against `ContactAnswered` (D-216) |
| `HouseSignedOff` :646 | `Insider/Ended` only (`EmitSchema.kt:604`) | one frame more per Insider | sent after `InsidersRevealed` has told every phone; accepted |
| `MessageDelivered` :186 | per seat, free text | would vary | nothing in `core` constructs it today |

The other seat-only kinds carry nothing a role changes: `WorkOrderIssued`, `ScanAnswered` and
`SubroutineGraded` (an Insider's fake work is dealt and graded by the same rules, rule 8),
`TerminalAnswered`, `SyncPulseAnswered`, `VoteHeld`, `ContactSeedIssued`, `LampSet`, `Surged`,
`OpeningMessage`, `RosterIssued`, `RoundEnded`, `EgressOpened`, `StandAndWalkIn`,
`RestrainedTakeover`, `MeetingCalling`, and `ContactAnswered` and `RevokeLanded` (both padded). Their
Booleans and nullables vary with what happened, not with who it happened to.

### D-250 · **A housed phone leaves the house notice: its nine seconds run once the house has opened the talk, and the ballot's push takes it off — built, provisional, owner to confirm**

**What the hunt found (H2).** In the simulator round, at 5 and 6 seats, every living seat sat on HOUSE
MEETING · NOTICES from the gate's close to the next LIGHTS OUT: never the discussion, the ballot or
the tally. The fix for the owner's fictional meeting (`HousedPhoneTest`) had switched the notice's
nine seconds (`Flow.autoAdvance`) off on a housed phone. `Flow.housePushes` had no row for the notice,
and with NOBODY NAMED there is no card to swipe.

**Now.**
1. **`Flow.ownOncePhaseOpened`**: the notice's nine seconds run on a housed phone once the house has
   opened the talk (`PanelState.meetingPhase == Discussion`). That is the push that walked the phone
   onto the notice. A housed phone with no phase open still stays put, so the owner's evening stays
   fixed. `Flow.runsItsOwnTimer` is the one question `FlowHost` asks.
2. **A `housePushes` row, `Notice → Vote` on `MeetingPhaseOpened`**, for a talk that ends while the
   phone is still on the notice (every hand went up). The next phase push a phone on the notice can
   hear is the ballot's.

⚠️ **Provisional.** The nine seconds is presentation (*shown once at the top of the meeting, then
gone*), and the talk's clock started at the gate either way. The alternative is to skip the notice
on a housed phone. **Owner to confirm.**

**Guards, each proven by breaking it.** `NoticeWalkWiringTest` (app; five phones over a real loopback
socket, the real `RoundAuthority`) walks Notice → Discussion → Vote → Tally → Home on the house's
pushes: with nobody named, with a seat named (a `RadioCause.Person` fault), and with the talk closed
while the phones are still on the notice. Those phones have no `FlowHost`, so the nine seconds are
fired by the calls `FlowHost` makes. With the map entry removed: *nobody named: ALBA had no way off the
notice (H2)*. With the row removed: *never happened: closed early: every phone stood on the ballot*.
`HousedPhoneTest.aHousedPhoneLeavesTheNoticeOnceTheHouseHasOpenedTheTalk` runs the real `FlowHost` on
the frame clock. With the entry removed: *the house opened the talk and the phone stayed on the notice
(H2) expected:<Discussion> but was:<Notice>*.

### D-251 · **The red `HousedPhoneTest` was the test clock, not a ticker; the two that flaked under load now bound a hang, not a speed — built (tests only)**

**What was red.** `everyMeetingScreenParksAHousedPhoneUntilTheHouseSpeaks` failed with *the test body
did not run to completion* after one minute, then passed on a quiet machine. **No commit broke it.**
Its time, filtered, per commit: `a6b0164` 25.1 s, `765adf3` 28.2 s, `0cde8e0` 25.5 s, `74e2df8` 32.6 s
(39.5 s inside the full suite). Under three agents' builds it crossed 60 s.

**Why it took that long.** It advanced eight screens 30 s each, and the compose test clock steps every
16 ms frame. Measured: 3.1 s of real time per 30 s with `FlowHost` attached, and 0.1 s with an empty
composition, **with no state write, recomposition or draw in any of those frames**. The D-239/D-243
ticker was not running (`ticking` was false on every screen, because no clock was pushed), and the
wall-clock loop ends at once with no clock. So the cost is the test clock's own per-frame work over a
large tree.

**Now.** `HousedPhoneTest` advances each parked screen just past the longest stand-in that could fire:
the longest `autoAdvance` row on a meeting screen or `HOUSE_STAND_IN_MS`, plus a second (10 s). The
test takes 8.3 s, and no stand-in can fire later than that.

**The two that flaked once under load bound a hang, not a speed.** `OutboundQueueTest` asserted that
another seat's 40 loopback frames arrive within 2 s. The held seat is released only when the test
ends, so a send coupled to it waits for ever and fails at any bound: 20 s now. `HostForegroundTest`
waited 20 s per condition and 5 s per push, and every failure it guards is a seat that never comes
back: 60 s and 30 s now.

## Revision 73 — what the simulator hunt found

*2026-11-17. `tools/sim-hunt/hunt.sh` played whole rounds on four, five and six Simulators (wire-only
seats: no BLE, torch, camera or haptics) and photographed every seat at every beat. Five of its
findings are fixed here; the rest are queued elsewhere.*

### D-252 · **NEW ROUND ends the round once and the second round arms; a ghost's vote is refused at the gate; the lobby probe and Signal Trace stop crashing — built**

1. **NEW ROUND looped (H1).** The host's NEW ROUND broadcasts `LobbyBody.RoundEnded`. The host's own
   phone hears it over loopback like every seat, and `CheatRoot` answers it with `FlowModel.newRound`,
   the same call the button makes, which reaches `LobbyOverTransport.roundEnded` and broadcast the end
   again. The five- and six-seat hunts logged 586 `THE HOUSE ENDED THE ROUND` per seat, one seat 5932
   frames in seconds, names flipping to UNNAMED as each pass wiped the desk, and a host that failed.
   **Now the link answers the house's push without ending anything**: while it is handing
   `RoundEnded` to the panel, `roundEnded()` keeps only its local half (the line goes). **The name goes
   back up once, from the push**, on every phone, the host's included; it was sent from
   `roundEnded()`, which raced the desk's wipe on a guest.
2. **The second round never armed.** `armAtLightsOut` refused on `house.armed`, which stays true on a
   finished round, so after NEW ROUND the host's LIGHTS OUT said the house was already playing. It now
   refuses only a round that is armed and not ended; arming constructs a fresh round (`Rules.armed`).
3. **A ghost's vote reached the couch (H8).** The ballot never held it, but `voteSelected` answered a
   seat with no ballot with `VoteHeld` and `VoteSelectionShown`, and live selections go to every seat
   outside the system: the couch drew *SEAT 0 SELECTED SKIP* and VOTES CAST 5 OF 5 over a ballot of
   four. **A selection or READY from an out seat is refused at the admission gate**,
   `RefusalReason.VoterOut`, recorded and answered with nothing. Admissible for `ReporterOut`'s reason:
   the couch has no ballot to tap (D-134), so no honest phone produces it; rule 1 is about an effect a
   player should have seen, and nobody's screen offers this one. Recording format 27.
4. **The lobby probe crashed the host (H5).** `LineWatch.verdict` iterated the list `saw` appended to
   from another thread: `ConcurrentModificationException`. It now keeps a count per kind under one
   lock, which also stops it holding one entry per frame for the life of the process.
5. **Signal Trace crashed the 4-seat dry run at seed 20261150 (H3).** The bench tapped nodes 0 to 7;
   D-238's graphs can hold 7, and the canvas read `wiring.nodes[7]`. A tap on a node the house did not
   deal now does nothing (`SubroutineModel.tap`), and the bench taps only the nodes dealt.

**The bench changed with it.** NEW ROUND is pressed on the host only (the guests' presses were
thumbs nobody has, and they hid whether the push alone returns them), and with
`SOMEONES_HOME_LOBBY_AGAIN` (set by `hunt.sh`) every seat hands its line over again after NEW ROUND
and the host presses LIGHTS OUT again.

**Found by the re-run and not fixed here (`ui`):** after NEW ROUND a phone carries the last round's
panel into the next one, so the second round's ARMED screen still read OUT REVOKED, OUT RESTRAINED,
METER 96% and last round's work. `FlowModel.newRound` resets the line and the screen and nothing else.

| site | what it did | what it does |
|---|---|---|
| `app` `LobbyOverTransport` | the push's answer ended the round again; the name went up from `roundEnded()` | the answer ends nothing; the name goes up once from the push |
| `app` `armAtLightsOut` | refused a finished round's house | refuses only a running round |
| `core` `admit`, `model` `RefusalReason.VoterOut`, `harness` `recording/27` | answered a ghost's vote to the couch | refused at the gate, recorded |
| `harness` test fixture `round()` | every seat voted, the couch included | the living vote, so the clean round still refuses nothing (the fuzzer still hands ghosts ballots, and they are refused) |
| `app` cheats `LineWatch` | a list read and written on two threads | counts under a lock |
| `ui` `SubroutineModel.tap`, `app` cheats `RoundCheat.entryTaps` | a route could hold a node not dealt | it cannot; the bench taps the dealt nodes |
| `app` cheats `CheatRoot`, `LobbyCheat`, `tools/sim-hunt/hunt.sh` | NEW ROUND on every seat; one round | NEW ROUND on the host; a second round with `LOBBY_AGAIN` |
| `NewRoundLoopTest` (new) | | five phones on loopback wired as `CheatRoot` wires them: the round to its end, NEW ROUND, one `RoundEnded` per phone, the same five names, the second round arms |
| `MeetingTest`, `LineWatchTest`, `SignalTraceBoundsTest` (new) | | a ghost's selection and READY refused with nothing emitted; a verdict read while 20 000 frames land; a tap on node 7 of a 7-node graph |

### D-253 · **A guest whose house has been unreachable for fifteen seconds is shown the disconnect screen, and walked back when its seat is taken again — built, the fifteen seconds provisional, owner to confirm**

**H4.** When the host's app failed the guests stayed where they were, on their last screen or on a
lobby that still listed the host. `DisconnectScreen` (RECONNECTING, STAY WHERE YOU ARE, the lamp
held) existed and nothing walked to it.

- **When.** A guest's link marks the moment it went (`ClientSession.Phase.Rejoining`); a seating
  clears the mark. The app root is told, once, at the first **refused reconnect** made
  `HOUSE_GONE_AFTER_MILLIS` (15 s) or more after the mark. Never by a timer alone: the reconnect backs
  off to 8 s between tries (0.5, 1, 2, 4, 8 s, so tries at about 0.5, 1.5, 3.5, 7.5 and 15.5 s), and a
  timer fired on a host that had come back and was only waiting for the next try. That was found by
  the test: with a timer, a lock the host came back from inside the window still walked the guests.
  So in practice the screen changes at about 15.5 s, when the fifth try is refused.
- **Never on the host.** Its own link drops when its phone locks, and the house it would miss is in its
  hand.
- **Nothing about the reconnect changes.** The seat token keeps being presented on the same backoff,
  so a host that relaunches (crash recovery) or comes back into range gets every seat back in its own
  chair, and the guest is walked back to the screen it was on (`HouseGone`), before the house re-pushes
  what it missed (D-097).
- **CLOSE THE HOUSE is unchanged:** `HouseClosed` still walks to the join screen with its line.

**Not covered.** The disconnect screen has no way out, so a guest whose host never returns must
leave the app by hand (owner to rule whether it gets a LEAVE). A guest leaving a station for it sends
`LeaveTerminal` when the link returns, so it is back on the station's screen without its reading. A
host phone that fails silently on real Wi-Fi may hold a guest's socket open until TCP gives up, so the
fifteen seconds start later than the end, and a reconnect that hangs rather than being refused
delays the decision to its end; the Simulators refuse at once. Not run on a phone.

| site | what it said | what it says now |
|---|---|---|
| `app` `LobbyOverTransport` | (nothing) | `onHouseLost` after the window, `onHouseBack` at the seating that answers it; `HOUSE_GONE_AFTER_MILLIS` |
| `app` `HouseGone` (new), cheats `CheatRoot` | (nothing) | the walk to `Disconnect` and back |
| `HouseGoneTest` (new) | | a host that fails: every guest on the disconnect screen after the window and not before; a lock inside the window walks nobody; a long lock walks them there and back |

### D-254 · **The house's opening text lands over PERIMETER ARMED, on every phone, in the frame ARMED does: D-118's banner and dim restored — built (amends D-234)**

**What D-234 broke.** D-234 made `OpeningMessage` walk a seated phone to plain ARMED, and ARMED is a
screen the house drives (`Flow.houseDriving`), so `arrived()` dropped the banner. The opening text,
one of D-118's only two heavy notifications, was drawn at arming on **no phone at all**: one of the two
events allowed to dim the house had stopped dimming it. Light is the owner's, and D-234 changed it as a
side effect. The owner's pack (Q6) asked for it back.

**The order, from the docs.** D-118 ties the dim to the opening text's *arrival* (*a dim during the
opening message is the round starting*), and D-119 has every phone dim and undim on the same ten
seconds. D-234 itself noted that a banner after ARMED would arrive on a CONTINUE tap, at a different
moment on every phone. So the banner lands **over** ARMED, in the same frame, from the same push. The
docs imply this rather than state it; flagged for the owner.

**Now.** `ScreenId.ArmedNotify` is PERIMETER ARMED with the opening banner over it, the
`LockNotify` pattern: `Notifications.arrivals` maps it to the opening text over `Armed`, so it dims
(`Notifications.dims`) exactly as `Notify` does. `roundOpened` pushes it from the lobby and the
one-line screen; anywhere else the message is the springboard banner, as before. It leaves by the
swipe (to ARMED) or D-119's ten seconds (`Flow.autoAdvance`, and `Flow.ownTimers` so a housed phone
runs them); CONTINUE under it still goes to the springboard. In `BUZZING`, `housePushed` and
`AMBER_BLOCKS` beside `Notify`. **No wire, effect, schema, buzz or lamp change**: the message, its one
haptic and the dim rule are D-118's and D-234's; only the screen it lands on moved. `Flow.armedOn`
(ARMED or ARMED under the banner) is what the bench's script and the host's tick driver now wait on
(`app` cheats `Cheats.kt`): waiting on plain ARMED alone would start the house's clock ten seconds
late, or never if the host took CONTINUE from under the banner.

**Guards, each proven by breaking it.** `ArmedBannerTest` (ui): four housed phones, Resident and
Insider, from the lobby and the one-line screen, take the arming burst; every one is on `ArmedNotify`,
draws the opening text and PERIMETER ARMED, dims, and draws **the same frame to the pixel**; the swipe
and the ten seconds leave ARMED. With `roundOpened` put back to D-234's `push(Armed)`: *the opening push
did not land the banner over ARMED: Resident in the lobby expected:<ArmedNotify> but was:<Armed>*.
`ArmedForEveryPhoneTest` (app, three phones over real loopback) now asserts the banner and the dim on
the host and both guests.

### D-255 · **Any real phone shows its own clock, housed or not; the render harness is the phone handed no clock — built (H9, extends D-243)**

**Found (H9).** The join screen, the lobby and every setup screen showed the design's `20:41` until a
house attached. A player sees it on first launch. **Nothing told the render harness apart from a real
phone with no house yet**: both were `houseAttached = false`, and D-243 read the clock only while
housed.

**Now, explicitly.** `PanelVals.renderHarness` = no house attached **and** no device clock. The app
root already hands the phone's clock down (`wallClock`, D-243); `FlowModel` now reads it at
construction and `FlowHost` every second **housed or not**. The strip's clock, the lantern's time and
its date are the device's whenever a clock exists; a dash on a housed phone with none; the design's
times in the harness alone. No wire change.

**Guard, proven by breaking it.** `HousedContentTest.aPhoneWithNoHouseYetDrawsItsOwnClock`: a phone
with a clock and no house, on Join, Perms, NameDrop, Maps, Lobby and Secret, draws its own time and no
`20:41`; the lantern too. With the pre-D-255 rule put back: *a phone with no house drew a drawn time on
Join expected:<2[3:46]> but was:<2[0:41]>*. `anUnhousedPhoneStillDrawsTheDesign` holds the harness's
`20:41`.

### D-256 · **The caught scan puts the room on its own line, and two lines of mixed case match the capitals around them — built (H10)**

1. **The caught scan's row** (`ScanCaughtScreen`). `<ROOM> . SUBROUTINE n OF 8` with the light mark
   beside it was one line, which fitted the design's GARAGE and nothing a host types: on the
   Simulators SCULLERY pushed the mark off the panel and FRONT ROOM lost it (`r5/montage/01-ARMED.png`).
   The room is now its own centred line, over `SUBROUTINE n OF 8` and the mark; a long room wraps under
   itself. The count gives way before the mark does.
2. **Case.** The lobby's `n of 5 handed theirs over` and the integrity widget's `Frozen until next house
   meeting` are now capitals, like every line around them. The setup screens' sentence-case captions
   (*No homes yet…*, *Keep scanning…*, the NameDrop step) stand among sentence case and are unchanged.

**Guard, proven by breaking it.** `CaughtRowTest` renders the caught scan on a phone-sized canvas with
SCULLERY, FRONT ROOM, MASTER BEDROOM and DOWNSTAIRS BATHROOM at SUBROUTINE 10 OF 10: the mark is on the
panel and not squeezed, the room and the count drawn whole. With the one-line row put back: *SCULLERY
squeezed the light mark to nothing: DpRect(left=0.0.dp, top=0.0.dp, right=0.0.dp, bottom=0.0.dp)*.

### D-257 · **A housed phone draws no bungalow and no counts on the couch or the Terminal, and FILES holds its own carry — built; the real plan and occupancy are STOPPED for the owner (unit 4d, H6)**

**What was fixture.** `Plan` is the design's fixed bungalow (KITCHEN, LIVING, HALL, STAIRS, STUDY,
GARAGE, on GROUND and UPPER) with drawn counts. On a housed phone the couch (`Ghost3`) drew
`KITCHEN 2 / LIVING 3 / HALL 0 / STUDY 1 / GARAGE 1` in a home with none of those rooms, and the
Terminal's live view and playback drew the same plan with their own counts and `-1:12`. FILES drew
`1 ITEM . DISK 01 . RACK` on every phone, carrying or not.

**What the house sends, checked.** **No phone is sent the home's plan.** The lobby carries counts,
names and the setting (`LobbyBody.Standing`); the round carries destinations (marker, shape, room
name) on work orders and Egress nodes, never geometry. D-186 says so outright: *the home plan returns
to crossing to NO client*; reaching a living Insider was *the largest disclosure in the emit table*.
**No message carries an occupancy count**: the couch's true occupancy is D-136's spectator map
(*unbuilt, hardware-dependent and frozen*: BLE sightings, `Placement.Inferred` has no producer), and
the Terminal's counts are `ObservationView`s with staleness and injected error that nothing produces.

**STOPPED, owner to decide.** (1) Sending the plan to the Terminal's reader (a living seat, M11) is
what D-186 rolled back: a living-seat disclosure. (2) Sending it to out seats only is allowed by the
GDD (*true occupancy, every storey*, `gdd.md:546`), but the plan is not in `GameState` (the arming
event carries destinations, not geometry), so it is a recording-format and core change, not a narrow
message. (3) True counts need the spectator map; a count from scans alone (`Placement.Known`) would be
a new design for a frozen system. None is built here.

**Now.** `PanelVals.terminalCounts`, `timelapseCounts` and `trueCounts` are the bungalow in the
harness and null on a housed phone; null draws `NoPlan`, the frame and a dash (`NO_READING`), with no
floor strip. The playback's offset is a dash and its bar empty. `PanelVals.filed` is `state.carrying`
(nothing writes it yet, E7), the port's carry in the harness alone: FILES says `0 ITEMS` with nothing
in it, or `1 ITEM` with the carry's item and destination; the fixture's `DEGRADED` went with it.
The same on every phone, either role. No wire change.

**Guards, each proven by breaking it.** `HousedContentTest`: the sweep's fixture list gains the
bungalow's rooms, floors, `-1:12`, `RACK` (every screen of the round; the setup editor starts from the
bungalow on purpose and is exempt), `1 ITEM` and `DISK 01`. With the couch put back to `Plan.trueCounts`:
*Ghost3 (Resident): 'GROUND' in "GROUND" …*; `noHousedPlanDrawsTheBungalow`: *Ghost3 drew the design's
bungalow on a housed phone: [… GROUND, UPPER, KITCHEN, LIVING, HALL, STAIRS, STUDY, GARAGE, 2, 3, 0, 1,
1]*. `filesFollowsTheCarry` (a carry of SPARE 03 to DISPOSAL, and none); with FILES put back to the
fixture: *an empty FILES did not say so: [… 1 ITEM, DISK 01, IN HAND | DESTINATION . RACK …]*.

**H7 (the meeting counts) needed no code.** D-247 already made them nullable and reset them at
every meeting. The `r5` hunt that found them started a minute after `74e2df8` landed; which build it
ran is not recorded. On this build the counts are the house's or nothing (below).

**On the Simulators (D-254 to D-257 together).** `tools/sim-hunt/hunt.sh --seats 5 --seed 20260822`
on this build, played to the ending: every seat, the host and the Insider included, went LOBBY →
ARMEDNOTIFY within a quarter of a second and drew the same banner over PERIMETER ARMED; the caught
scan's room and count fit on all five; the caller's ring drew no count; the revoked seat's ledger read
`READY -/- · VOTED -/-` at every beat where `r5` read `3/6` and `4/6`; and its couch drew the empty
frame. The Playtest app launched with no house drew the Simulator's own time on its first setup
screen. **Not seen on a phone**, and FILES, the Terminal and the playback are not on the hunt's route
(rendered by the harness only).

## Revision 74 — every living phone gets one Isolate frame of one size, and an ability's answer is one size

*2026-11-17, overnight run, unit F4. The two items D-249's audit reported, and the audit of every
`RoundMessage` that unit started. The principle is the owner's (R1, S1, T1; D-216, D-219, D-220,
D-249): nobody watching the Wi-Fi may tell roles apart by frame count, size or timing. Fixed-size
frames for every message wait for the encryption work (S2). Desktop and simulator unit tests only;
nothing here has been on a phone.*

### D-258 · **A Resident is sent a blank of the Isolate offer's size wherever an Insider is sent the offer, and `AbilityFired` carries a digit — built**

**What leaked.** `IsolateTargetsOffered` reaches `Insider/Live` only, and it is sent at arming, when an
Egress starts and when one is contained. At each of those moments every living Insider's socket
carried one frame more than every living Resident's. Measured over loopback at arming, five seats,
Insiders 1 and 4: **Insiders 7 frames, Residents 6.** Separately, `AbilityFired(cooldownStarted)`
serialised `true` and `false`, so the answer to a Surge, Static, Isolate or Egress request was
**120 bytes when it started a cooldown and 121 when it fell into a running one** (carried, at
sequence 1): whether the request took was readable off its size.

**Now, the Isolate frame.**

1. **A new effect and kind, `IsolateTargetsBlanked(seat, targets)`.** `Rules.isolateOffers` builds it
   beside each seat's offer, seat by seat, at all three moments, so the rules still read no role and
   the house writes the frames in seat order whatever the roles are. Its row is `Resident/Live` and
   nothing else, written out as a one-class set. The offer's row is unchanged. So each living seat is
   sent exactly one of the two, at the same place in the burst, and the out are sent neither.
2. **The targets never cross on the blank.** The effect holds the offer's list only so the
   projection can read how wide the offer's frame is; `RoundMessage.IsolateTargetsBlanked` carries
   `paddingHex`, zeros and nothing else, and a blank with anything in it does not construct or
   decode. A Resident's phone has no list to read, and `Flow` and `HouseBuzzer` name it as a no-op.
   A new kind rather than the offer widened: widened, the row that served a Resident would hand them
   the geography, and their phone would hold something it could read as targets.
3. **One length by construction.** The offer's list now travels as `targetsHex`, its JSON's UTF-8
   bytes in lowercase hex, because each layer of the wire escapes some characters and not others.
   The two serial names are both 21 characters and the two field names both 10, and the blank is
   zeros of the offer's hex width, so the frames are the same length at every moment. The call sites
   keep `IsolateTargetsOffered(targets)` and read `.targets`. The width follows the home and the
   moment, never the role.

**Now, the answer.** The wire member is `AbilityFired(cooldown: Int)`, serialised under the old name
`cooldownStarted` as one digit (1 started, 0 not); the call sites keep
`AbilityFired(cooldownStarted = …)` and read `.cooldownStarted`. Any other value does not decode.
`ContactAnswered.PAD` drops from 9 to 6 characters and `RevokeLanded.PAD` from 12 to 9, so every
touch frame stays one size: **every touch frame is now 117 bytes**, and both answers are 117.

**Measured after, over loopback** (`IsolateFrameSizeTest`): at arming every seat gets 7 frames, and
each seat's lengths are identical with seats 1 and 2 exchanging roles (`[106, 1798, 118, 133, 843,
146, 866]` for seat 1 either way). The Isolate frame is **866 bytes on all five sockets**. At an
Egress, every phone but the actor's gets `[326, 594]`, the other Insider's included; the actor gets
its own 117-byte answer first. Model-level, offer and blank are equal at every sequence from 1 to a
million for lists with no cards (128 bytes), escaped rooms (384), multibyte rooms (458) and forty
cards (5186).

**Guards, each proven by breaking it.**

- `IsolateFrameSizeTest` (app; a real `TransportHost`, five real clients, the real `EffectWire`, the
  rules' own arming and admission; a home whose room names escape and take several bytes). Arms
  twice with roles exchanged, starts an Egress, fires again into its cooldown.
- `EgressTest."every living seat gets one Isolate frame of one length at each of the three moments"`
  (core; arming, Egress start and containment, through the allowlist and the real encoders, with a
  Revoked seat).
- `IsolateWireSizeTest` (model; offer and blank, and `true` and `false`, at sequences 1 to a million;
  the rows; one of the pair per living seat; a blank that carries anything does not decode).
- `EffectPushTest`'s two probes now also search for a text's UTF-8 hex, because a sweep for plain
  text reads straight past `lineHex` (D-249) and `targetsHex`. Its geography fixture now includes the
  blank, which does reach the Resident there and must not carry the conservatory.

**Recording format 28**: one effect a version 27 build cannot name.

**The audit: every `RoundMessage`, by who it goes to and what its size depends on** (rows from
`EmitSchema.ALLOWED`; core reads a role in three places only, the admission gate, the reveal and the
meter's banking, so no other effect's content depends on one):

| message | sent to | size varies with | verdict |
|---|---|---|---|
| `IsolateTargetsOffered` / `IsolateTargetsBlanked` | Insider/Live; Resident/Live | the home and the moment | **fixed here** |
| `AbilityFired` | the Insider actor | nothing now | **fixed here** (the digit). Its *count* answers the actor's own upward `Act` or `Fire`, which only an Insider sends: an upward leak a downward frame cannot cover; S2 |
| `RoleRevealed` | every living seat | nothing | fixed, D-249 |
| `ContactAnswered`, `RevokeLanded` | toucher; Revoked target | nothing | padded, D-216/D-219, re-padded here |
| `HouseSignedOff` | Insider/Ended | the winner's text | **accepted**: built after `InsidersRevealed` in the same burst (`Rules.endingEffects`), when every phone already shows who the Insiders were |
| `MessageDelivered` | living + out, per seat | free text | **unsent**: nothing in `core` constructs it. Needs a fixed width before it is |
| `SubroutineGraded(accepted)`, `TerminalAnswered(reading)`, `SyncPulseAnswered(held)`, `VoteHeld(selection?, locked)`, `ScanAnswered(opened?)`, `MeetingCalling` | the one seat that acted, either role | what that seat did | not role: the same rules for both roles (D-109, rule 8). Each is a size tell of the seat's own act, for S2 |
| `WorkOrderIssued`, `ContactSeedIssued`, `OpeningMessage`, `RosterIssued`, `LampSet`, `Surged`, `EgressOpened`, `StandAndWalkIn`, `RestrainedTakeover` | per seat, both roles | the seat's draw, or nothing | not role: proven for the arming burst by the exchange in `IsolateFrameSizeTest` |
| `StaticOpened`, `SystemIntegrityBatched`, `MeetingOpened`, `CheckInProgressed`, `MeetingPhaseOpened`, `NoticeIssued`, `ReadyProgressed`, `VoteProgressed`, `MeetingResult`, `MeetingEnded`, `EgressHeld`, `EgressContained`, `RoundEnded`, `InsidersRevealed` | every seat of a class pair | nothing per seat | byte-identical on every phone |
| `SubroutineProgressed`, `SystemIntegrityLive`, `VoteSelectionShown`, `MeetingResolved` | the out, both roles | nothing per seat | not role |
| `PresenceChanged`, `EgressSucceeded` | nobody (no row) | | unsent |

**Raised, not decided:** `RoundBody.Push.seq` still grows with what a seat has been sent (D-216's
open item). An Egress actor's own answer puts its counter one ahead, which in the test crosses 9 to
10, so its next frame is a byte longer than every other phone's. The Isolate fix removes the
per-role drift at arming and at every Egress; the remaining drift follows a seat's own acts, and
only fixed-size frames (S2) close it.

| site | what it says now | what replaces it |
|---|---|---|
| `model` `Effect.IsolateTargetsBlanked`, `EmitSchema` | (new) | built beside every offer; row `Resident/Live` only; addressed to its own seat |
| `model/protocol` `RoundMessage.IsolateTargetsOffered` | `targets: List<Destination>` | `targetsHex`, read back as `.targets` |
| `model/protocol` `RoundMessage.IsolateTargetsBlanked` | (new) | `paddingHex`, zeros of the offer's width |
| `model/protocol` `RoundMessage.AbilityFired` | `cooldownStarted: Boolean` | `cooldown: Int`, one digit, under the same serial name |
| `model/protocol` `ContactAnswered.PAD`, `RevokeLanded.PAD` | 9 and 12 characters, 120 bytes | 6 and 9, 117 bytes |
| `core` `Rules.isolateOffers` | an offer per seat | an offer and a blank per seat, seat by seat |
| `ui` `Flow`, `app` `HouseBuzzer`, `harness` `Transcript` | (new) | a named no-op, silent, one transcript row |
| `harness` `Recording.HEADER` | `recording/27` | `recording/28` |
| tests | `StaticTest` seven runs; `EgressTest` containment kinds; `RoundWireTest`, `HouseBuzzTest` fixtures | eight runs; the blank named; one more effect in each sweep |

## Revision 75 — a round ends on the phone too, the disconnect screen has a way out, and the 4-seat bench reaches its ending

*2026-11-17, overnight run, unit F5: the three findings the F2 re-run left (D-252, D-253). Desktop,
iOS-Simulator unit tests and a five-seat simulator hunt; nothing here has been on a phone.*

### D-259 · **NEW ROUND, CLOSE THE HOUSE, a closed house and LEAVE forget the round on every phone and keep the evening; the bench plays a second round and says ENDING when a round ends at a meeting — built**

**What was wrong.** `FlowModel.newRound` walked the screen to the lobby and wiped the line, and
copied every other field across, so the second round's PERIMETER ARMED drew the first round's OUT
REVOKED / OUT RESTRAINED, METER 96% and work order. `Rules.armed` constructs the next round in the
house; nothing constructed the phone's.

**The boundary, field by field.** `PanelState.betweenRounds()` builds a fresh panel and names what it
keeps, so a field added later is reset unless somebody says it belongs to the evening (a lobby field
wrongly reset is seen at once; a round field wrongly kept is a leak nobody sees).

| | reset at the boundary | kept |
|---|---|---|
| `PanelState` (44 fields) | `role`, `revoke`, `outBy`, `verdict`, `secondsLeft`, the six Egress fields, `beatHeld`, `current`, `scannedAt`, `revealed`, `signOff`, `order`, `ballots`, `tally`, `caller`, `meetingPhase`, `voteWindowSeconds`, `integrityPercent`, `seats`, `roster`, `notice`, `isolateTargets`, `staticJam`, the three page-2 clocks (D-239), `carrying`, `openedAt`, `houseTexts`, `endedAt` (D-243), `heldOver`, `egressesContained`, `residents` (D-247): 37 | `screen` (the caller moves it), `houseAttached`, `markersOn`, `torch`, `wallTime`, `inboxSeed`, `noteSeed`: 7 |
| beside the panel (`ui`) | the couch's ballot; the meeting's echo, counts (UNSAID) and, housed, its roster names; the Subroutine in hand and its card; every house text of the round still under the clock (by value, so an unhoused bench's fixtures stay) | the lobby (name, home, standing; the line goes as before, D-116), saved homes, editor, perms, NameDrop |
| not touched, on purpose | **the lamp** (the house's, rule 5: the next arming's `LampSet` sets it) and **a Surge in flight** (it runs itself out in 5 s; clearing it mid-sequence would leave its slam painted). The dealt question slots are overwritten by the next BEGIN before any screen draws them | |
| `app` | already reset by the push and checked now: `RoundClient`'s order, card and kinds; the inbox's sequence; the seed, the token window, the away mark and the owed reports, in memory and beside the token (D-245); the radio silenced; the wake released. **New:** `ContactUploader.out` and the upload count, which only the next round's seed cleared, so a Revoked phone said it was out of the round all through the lobby | the seat token and session, the name (re-sent once, D-252), the house's desk and ledger |

Called by `newRound` (the host's button and every phone's answer to the house's push; the second
call finds nothing), `houseClosed`, `terminateHouse` and `leaveLobby`.

**The 4-seat bench (seed 20261150) never reached ENDING: the script, not the game.** From
`/tmp/someones-home-hunt/f2-r4/seat-0.log`: one Insider (seat 0) Revoked seat 1, seat 2 found it and
called the meeting, the two living guests voted for the caller and the host abstained, so seat 2 was
restrained; one Insider against one Resident is parity (`gdd.md:206`), and the house logged `THE ROUND
ENDED — Insiders BY Parity` at the tally. Correct. But a round that ends keeps its last meeting, and
the bench's meeting loop waited for `meeting == null` for up to 180 s. NEW ROUND (14 s after the
reveal) and the second arming replaced the state, the loop let go 22 s after the tally (`LIGHTS-OUT-
AGAIN` at +89 s), `house.ended` was then the second round's, and the script played the Static and
Egress acts against round two (`NO JAM WAS FIRED FROM ANY PHONE`; every thumb had stopped with round
one) and never said ENDING, which `hunt.sh` waits for. **Now** the meeting loop stops on `house.ended`
and the ending is beaten at once.

**And the bench plays the second round.** With `SOMEONES_HOME_LOBBY_AGAIN` (set by `hunt.sh`) the
second round armed and nobody played it. `CheatRoot` now loops the bench once per round; the bench's
per-round flags are cleared at the round's `RoundEnded` (never at the next opening: the work order
lands before `OpeningMessage`), and the ending's own at the start of each `play`. A second round's
beats are named `AGAIN-<beat>`.

| site | before | now |
|---|---|---|
| `ui` `PanelState.betweenRounds` (new), `FlowModel.roundForgotten` (new) | `newRound` copied the round across | the four exits call it |
| `app` `ContactUploader.roundEnded` | `out` and the upload count survived the round | reset with the seed |
| `app` cheats `RoundCheat`, `CheatRoot`; `tools/sim-hunt/hunt.sh` | waited on a finished round's meeting; one round per launch | stops on the end; one pass per round |
| `RoundBoundaryTest` (new, `app`) | | five phones on loopback: a Revoke, a Restrain and a Revoke to the Insiders' parity, NEW ROUND, then every phone's whole panel equals a never-played one plus the kept fields, the meeting, card, lock screen, round client, inbox, uploader and radio are fresh, the name, token, home and hosting kept; the second ARMED is nobody's out |
| `RoundForgottenTest` (new, `ui`) | | a filled panel through each of the four exits |

### D-260 · **The disconnect screen gets LEAVE THIS HOUSE: the join screen, the round forgotten, the seat token kept — built, provisional, owner to confirm**

D-253 left a guest whose host never returns on RECONNECTING with no way out but stopping the app. The
mockup draws no control on `sDisconnect` and no entry rules one, so this is a call: **the lobby's own
way out**, LEAVE THIS HOUSE (the owner's words for a guest leaving, D-235), a two-second hold
(D-141), at the foot of the screen under STAY WHERE YOU ARE, on the concealment palette (D-077). It
calls `FlowModel.leaveLobby`: the link hangs up and stops trying, the round is forgotten (D-259), the
phone walks to the join screen. **The seat token and session stay**, so picking the home again when
the host is back presents the same token and gets the same chair; a guest that never pressed it is
still walked back by the host's return (D-253). Same screen on every phone, either role.

| site | before | now |
|---|---|---|
| `ui` `DisconnectScreen`, `Flow.viaActions` | no control | LEAVE THIS HOUSE → Join |
| `HouseGoneTest` (+1) | | ROSE leaves from RECONNECTING, DANI stays; the host returns: DANI walked back to her lobby in her own chair, ROSE left on the join screen and unseated; ROSE picks the home again and is seated in her own chair on her own token |
| `RoundForgottenTest` | | the hold walks to Join with the round forgotten; most of a hold leaves nothing |

**Guards, each proven by breaking it.** `newRound` without the reset (the bug as found):
`RoundBoundaryTest` *the host (ALBA) carried round one into the lobby: [outBy, revealed, order,
ballots, tally, meetingPhase, voteWindowSeconds, seats, roster, notice, revokeClock, accessClock,
egressClock, openedAt, endedAt, egressesContained, residents]*, and `RoundForgottenTest` *NEW ROUND
carried the round across*. `betweenRounds` keeping `outBy`: all four exits in `RoundForgottenTest`
fail. `out` not reset: *the host (ALBA)'s radio still says it is out of the round*. The hold removed:
*could not find any node that satisfies … 'LEAVE THIS HOUSE'* (both hold tests). Leaving forgets the
token: *leaving the disconnect screen threw the seat token away. Expected <29aaf…>, actual <null>*.
Leaving drops the session: *ROSE came back as a stranger rather than on her own token*.

**On the Simulators** (wire-only seats; no BLE, torch, camera or haptics). `hunt.sh --seats 5 --seed
20261150 --tail 150` (`/tmp/someones-home-hunt/f5-r5`): round one to ENDING (Insiders, Egress
uncontained) with seat 0 Revoked and seat 1 Restrained; NEW ROUND; every seat's ledger on the lobby
read `OUT - · WORK - · ORDER - · METER - · SEATS -`; the second round's ARMEDNOTIFY read `OUT -` on
both seats that had been out (where `f2-r5` read OUT REVOKED); the second round was played through its
meeting (`AGAIN-MEETING-RINGING` … `AGAIN-TALLY`) to its own ENDING, and a third armed. One `THE HOUSE
ENDED THE ROUND` per seat per round, no crash. `--seats 4` at the same seed (`f5-r4`), the run that
never said ENDING: `ENDING` at +76 s after `THE ROUND ENDED AT THE MEETING — Insiders by Parity`, then
the second round to its own ENDING. **Not run on a phone.**

**Found, not fixed (`ui`).** The Insiders' ending says THE HOUSE REACHED THE INTERNET and EGRESS
COMPLETE whatever the route, so a parity win (the 4-seat run) is drawn as an Egress win
(`ScreensOut.kt:458`).


## Revision 76 — each ending says how the round was won

### D-261 · **Each ending names the route the house sent: the meter and the Egress keep the design's words, parity and Restraining every Insider get new ones — built; the two new lines provisional, owner to confirm**

D-260's finding: the Insiders' ending drew THE HOUSE REACHED THE INTERNET and EGRESS COMPLETE on
every Insider win, so the 4-seat hunt's parity win at the tally (seed 20261150, `f5-r4`) was drawn
as an Egress. The route was already on the wire (`RoundEnded.by`, D-131's four, all public) and the
phone threw it away. It is now kept on the panel (`PanelState.endedBy`; set by `RoundEnded`, cleared
at LIGHTS OUT and by D-259's reset) and the banner's line is `PanelVals.endingLine(by)`, the one
place all four live. The same on every phone, either role: the route is one public fact.

| route | ending | line | EGRESS COMPLETE row | source |
|---|---|---|---|---|
| `SystemIntegrityCleared` | Residents | PERIMETER DISARMED . YOU CAN LEAVE | n/a | `gdd.md:205`, the mockup |
| `InsidersRestrained` | Residents | INSIDERS RESTRAINED . YOU CAN LEAVE | n/a | **provisional** |
| `Parity` | Insiders | TOO FEW RESIDENTS LEFT | not drawn | **provisional** |
| `EgressUncontained` | Insiders | THE HOUSE REACHED THE INTERNET | drawn | the mockup, `gdd.md:29` |

**No document gives parity or the Restrain route any words**, so these are the shortest that say
what the whole room watched happen, in the banner's register. A parity win completed no Egress, so
its ending drops that row rather than printing one that did not happen. The Restrain line is the
mockup line's length so it stays on one line. The status bar still reads DISARMED on both Residents'
routes, which `Flow.endings` already said ("the perimeter disarms").

A housed phone told no route draws no line and no Egress row (D-247's rule); the unhoused render
harness keeps the mockup's two endings.

**Guards, each proven by breaking it.** `EndingRouteTest` feeds each route to a housed phone in both
roles and checks the screen it walks to draws that route's line, no other route's, and EGRESS
COMPLETE only for an Egress. The banner hard-coded back to the Insiders' old line with the row
always drawn (the bug as found): *Parity (Resident) did not draw 'TOO FEW RESIDENTS LEFT': [… INSIDERS
WIN, THE HOUSE REACHED THE INTERNET, …, EGRESS COMPLETE, …]* and *the parity ending is not drawn*.
The Residents' banner hard-coded to the meter's line: *InsidersRestrained (Resident) did not draw
'INSIDERS RESTRAINED . YOU CAN LEAVE'*. `RoundForgottenTest` now carries a route into NEW ROUND
and the other exits. Each of the eight endings and the harness's two were rendered at 600×1300 and
looked at. **Not on a phone.**

Also fixed: `RoundForgottenTest` held `STAY ON TASK`, which `:ui:vocabularyLint` refuses.
## Revision 77 — the actor's Access bar follows the house's answer to its own fire

### D-262 · **A Tier-2 fire's answer restarts the actor's own Access bar from that ability's weight; page 2 stays the same on both roles until a fire — built, provisional, owner to confirm**

**The bug** (review #2's V3). D-239 drew the Access bar from the public clocks only and left the
house's `AbilityFired` unread (`Flow.kt`, *a branch that grew a field here would be the first
actor-side feedback in the game*). After a Surge, Static or Isolate the bar read full while the
house's pool ran 150, 120 or 90 s, and a hold in that time fired into a running pool, was answered
`AbilityFired(false)`, and did nothing: D-237's silent no-op on REVOKE, one tier down.

**What changed (`ui` only).**
1. Each Tier-2 fire this phone sends records that ability's weight (`Balance.SURGE_COOLDOWN`,
   `STATIC_COOLDOWN`, `ISOLATE_COOLDOWN`, the ones `core` spends the pool by); an Egress fire clears
   it. The house's next `AbilityFired` reads it once: if the pool was spent (`cooldownStarted`), the
   bar restarts as `PublicCooldown.fired(weight)`, counted from the answer's arrival plus D-239's 1 s
   pad, so it errs late. A fire into a running pool restarts nothing. An answer with no fire in flight
   (an armed Revoke's contact) moves nothing.
2. A meeting's end still puts the bar back on the public half (D-184), and drops any answer still
   owed.
3. **REVOKE's ARMED is untouched**, so D-239's options A/B/C stay open.

**Why this is not the actor-side feedback the GDD forbids.** `gdd.md:322`/`:325` (*nothing changes on
screen*, *no feedback whatsoever*) are Revoke's, and are about the arm and the contact. For the ranged
abilities `gdd.md:357` asks for *same animation, **same cooldown spent**, no feedback whatsoever* on the
actor's device. So the spent cooldown is drawn there by design. What stays forbidden is any sign of
what happened to the target. `core` never branches on the target (rule 1), so a Surge at somebody
already Revoked restarts this bar exactly as any other Surge does. M9 (`gdd.md:417`) keeps the
device free of light, sound and animation, and this adds none: the bar is a hairline already on page
2. The answer goes to the actor alone (the schema's `AbilityFired` row). It answers the actor's own
two-second hold about a second later, so it is input echo plus the house's answer to the one phone
that asked. It is not news at a contact, which is what keeps D-239's Revoke question separate.
`AbilityFired` still does not name the ability; the phone knows which fire it is by its own last
request.

**Parity.** Before a fire, an Insider's page 2 and a Resident's are the same at every public moment
(D-142, D-239), now checked in pixels too. **After a fire the actor's bar differs**, and that is
D-239's raised point (c) accepted for this tier: someone reading an Insider's page 2 next to anyone
else's, inside the pool's 90 to 150 s, sees a bar the others do not. This is ARMED's asymmetry
(D-231) one tier down. After a Static it is the one thing that tells the jammer's phone from a
bystander's. **Owner to confirm**, against the alternative: keep the bar public and let a hold into
the running pool keep failing silently.

**Limit, flagged.** An armed Revoke's contact is answered with the same `AbilityFired(true)`. One that
lands inside a Tier-2 fire's round trip (well under the two-second hold) is read as that fire's answer.
The bar then restarts a round trip early, or, if the fire fell into a running pool, restarts when it
should not have (late, the safe direction). An answer that named the ability would close this; that
is a wire change, left for after U1.

**Guards, each proven by breaking it.**
- `PublicCooldownTest.aSurgeRestartsTheActorsOwnAccessBarAndAFireIntoItShowsNotReady`. The answer
  dropped again (`AbilityFired -> Unit`, the bug as found) fails *the Surge's answer did not restart
  the pool from the Surge's weight expected:<PublicCooldown(seconds=151, elapsed=0)> but
  was:<PublicCooldown(seconds=46, elapsed=46)>*. `cooldownStarted` ignored fails *a fire into the
  running pool moved the bar expected:<0.06622516> but was:<0.0>*.
- `PublicCooldownTest.anAnswerToNoTier2FireMovesNothing`. Every answer read as a Surge fails *a
  contact's answer moved the panel*.
- `AccessBarParityTest` (desktop, new) renders page 2 for both roles at LIGHTS OUT, 15 s in, the
  Access half run out, 20 s after the Insider's Surge, and a meeting's end. Only the texts whose
  strings differ by role (D-142's words) are masked; every other pixel must match, except after the
  Surge, where the actor's page must differ. Breaking each:
  - the Insider's Access clock started 3 s ahead at LIGHTS OUT fails *page 2 differed by role at
    LIGHTS OUT, 120 pixels, first at (21,409)*, and `PublicCooldownTest`'s value check fails too;
  - the pool bar drawn in the Insider's ink only, a shade that no value check sees, fails *page 2
    differed by role at 15 s in, 618 pixels*;
  - the answer dropped fails *after the Surge the actor's page 2 still drew the pool as ready*.
- Rendered and looked at (`ui/build/screens-access/`): before the fire both roles' pool bars are
  full; 20 s after the Surge the Insider's is about an eighth full and the Resident's is full.

**Not on a phone.**

## Revision 78 — every request rides the upload, and every living phone is sent the answer's frame

*2026-11-17, overnight run, unit U1. The two leaks the orchestrator found after D-258. The principle
is the owner's (R1, R8; D-212 ruling 5, D-216, D-249, D-258): every living phone sends and receives
the same number of frames, of the same size, at the same cadence, whatever its role. Desktop,
loopback and simulator only; nothing here has been on a phone.*

### D-263 · **Every request a player makes in a round rides inside the fixed-cadence upload, retried under one id until the house answers; every living phone but the actor is sent a blank of the actor's answer — built, provisional, owner to confirm (including the cost)**

> **Amended by D-275:** the slot carries every waiting request that fits, in order, under one id,
> rather than one a beat (items 1, 2 and 4 below), as URL-safe base64 of 384 bytes in the same 532
> characters rather than 256 bytes of hex. The upload is still 1 808 bytes and nothing on the wire
> moved.

**What leaked.** (a) **Up.** `ArmRevoke`, the Egress tile (`RoundBody.Fire`), Surge, Isolate and
Static each went up as a frame of its own at the moment it was pressed. Residents never send them, so
anybody measuring the Wi-Fi saw which phones put a frame on the air between their beats, and those
were the Insiders. Encryption (R7) hides a frame's contents, not its existence, size or timing.
(b) **Down.** When an ability was answered the actor alone was sent `AbilityFired`, one frame more
than every other phone at that moment (D-258's audit: the Egress actor's `+1`).

**⚠️ The provisional call (owner to confirm): no request travels as its own frame once a round is
armed.** It waits for the next beat and rides inside the upload every phone already sends once a
second at one size (D-212). **The cost is up to one beat (`SightingUpload.CADENCE_MILLIS`, 1 s) of
extra latency per request, and one more beat for every lost upload.** The cadence is not raised.

**Up: the request slot.**

1. **`RoundBody.Sighted` gains `ask`**, one `RequestSlot` of 532 characters: a 16-hex request id,
   four hex characters of length, and the request's JSON (`Asked.Act(intent)` or `Asked.Fire`) as
   UTF-8 hex padded with zeros to 256 bytes. Hex, because no layer escapes it, so the width is the
   length. No default on the field, so an upload cannot be built without one. **Every upload frame
   is 1 808 bytes for every phone, whatever its slot holds** (it was 1 263 before the slot), measured
   on the encoded frame for every member of `IntentMessage`, the Egress tile and an empty slot.
2. **The phone** (`SlotRequests`, in `RoundOverTransport`). In a round, `act` and `fireEgress` queue
   the request; each beat the uploader fills the slot with the oldest one the house has not answered,
   under its own random id, or an empty slot under a fresh random id. One a beat, in the order made,
   so a lock never overtakes its selection (D-117). A `SelectVote` not yet sent is replaced by the
   next one, so a finger moving down the ballot is heard where it is now rather than a row a beat. A
   request whose JSON is over 256 bytes is said in the phone's log and not sent (rule 6); the largest
   today is far under. The queue empties when a round begins or ends.
3. **The house** (`LobbyHouse.attend`, one consumer, D-241 order unchanged). It reads the slot
   **first**, then judges the sightings, so an arm and a touch in one beat are put to the rules in
   the order they were made. `IntentDesk.slotted` attributes the request to the connection's seat
   (the slot has nowhere to name one) and puts each id to the rules once; it keeps the last 64 ids per
   seat **across connections**, because a request is resent on a new socket under the same id. Then
   it answers **every** upload with `RoundBody.UploadHeard(id)`, whatever the slot held and whatever
   the rules made of it (rule 1): one frame of one length a beat to every phone in the round.
4. **Exactly once.** A request stays at the head of the queue, resent every beat under its id, until
   `UploadHeard` names it. A lost upload, a closed socket or a lost answer all end the same way, and the
   desk hears the id once.
5. **Before arming nothing changes.** A request made while the phone holds no seed (the lobby) still
   goes up as its own `RoundBody.Act` or `Fire`: no request means anything to the rules before arming,
   and every phone is in that state at the same moments. The lobby's own bodies are untouched. The
   house still reads a standalone `Act`/`Fire` at any time (a phone in a round sends neither).
6. **Out seats** keep uploading on the same cadence at the same size (R8, unchanged), with the same
   slot.
7. **Not moved, and why.** `RoundBody.Gap` (a reconnect, or an out-of-order push) and
   `RoundBody.RadioFaulted` (D-245) still travel alone. Neither depends on a role: a radio fault or a
   phone leaving the foreground happens to any phone, and D-245's report has its own exactly-once
   machinery that a slot would have to share. **Raised:** each still marks *that phone* to a sniffer
   (it faulted, or it reconnected), not its role.

**Down: the blank.**

8. **A new effect and kind, `AbilityBlanked(seat)`.** `Rules` builds the actor's `AbilityFired` and
   one `AbilityBlanked` for every other seat, seat by seat, at every Surge, Isolate, Static and Egress,
   taking or falling into a running clock (`answeredAndBlanked`). No role is read: the only question
   asked of a seat is whether it is the actor's. Its row is both living classes and nothing else, so
   every living phone is sent exactly one frame and the out none. `RoundMessage.AbilityBlanked` carries
   one padding digit; the serial name is two characters longer than `AbilityFired` and the field two
   shorter than `cooldownStarted`, so both are **117 bytes** at every sequence. `Flow` and
   `HouseBuzzer` name it as a no-op. A touch is unchanged: R1 already answered both touchers.
9. **Measured at an Egress's start, over loopback, five phones:** every living phone, the actor
   included, was sent `[116, 307, 891]` bytes (this home's draw) net of the sequence number's digits (the answer or the
   blank, `EgressOpened`, the Isolate offer or blank). Before, the actor was sent one frame more.

**The cost, measured** (`UplinkFrameTest`, five phones over loopback, presses one after another):
24 requests from press to the house's hearing it, **min 5 ms, mean 695 ms, max 1 009 ms**; the Surge
whose upload was lost, **2 008 ms**. Checked against what is time-critical:
- **The lamp going dark on `RevokeLanded`** is downward and follows the sightings, which were already on
  the cadence. Unchanged. The arm now reaches the house up to a beat after the hold ends; the hold is
  two seconds (D-141) and the touch comes after it, so an Insider who touches within a second of
  releasing the tile may touch unarmed. **For the owner.**
- **D-141 holds:** every hold is on the phone and the request leaves when it ends.
- **Meetings:** a check-in, READY or lock pressed in the last second of its window may now land after
  it closes. The vote window is 45 s.
- **Containment:** Sync Pulse taps are graded on the times the phone recorded, not on arrival; a pair
  must hand over within six seconds of each other (`SYNC_PULSE_PAIR_WINDOW`), and each now arrives up
  to a second late, independently. A return in an Egress's last second may miss it.
- Subroutine entries are graded on what was entered, not on arrival.

**Guards, each proven by breaking it.**

- `UplinkFrameTest` (app cheats; five real phones, `FlowModel` actions, `RoundClient`,
  `LobbyOverTransport`, `RoundAuthority`, a tap on each socket). A guest Insider arms, Surges with its
  upload lost, everybody calls, checks in, readies and votes a Resident out with one lock's answer
  lost, and the Insider fires the Egress. Every round frame every phone sent is an upload of one length
  (1 808 bytes); in every four-second window the phones' counts differ by at most the one beat a
  window edge allows; every upload is answered by one frame of one length; at the Egress every living
  phone gets the same frames; the Surge, every check-in and every lock are heard exactly once.
- `SlotRequestsTest` (order, resend under one id, the selection replaced, too long, cleared).
- `RequestSlotTest`, `AbilityBlankWireSizeTest`, `SightingUploadTest` (model: one width for every
  request; 117 bytes at sequences 1 to a million; the row; a slot that is not one reads as nothing).
- `EgressTest."every living seat gets one answer frame of one length whenever an ability is answered"`
  (core: Surge, Isolate, Static, Egress and an early Egress by each Insider, with a Revoked seat).
- `IsolateFrameSizeTest` and `AbilityWiringTest` now expect the blank where they expected nothing.
- `tools/sim-hunt/hunt.sh --seats 5` (wire-only Simulator seats, seed 20261150): the bench's round
  played to ENDING (the Egress uncontained) and a second round armed; the guest Insider's arm,
  Static and Egress reached the house through the slot, and no seat logged a slot it could not read.

**Recording format 29**: one effect a version 28 build cannot name.

**Raised, not decided:**

- **Timing still links a request to its asker.** The answer's burst lands a few milliseconds after
  the actor's upload arrives, and each phone's beat has its own phase, so a sniffer correlating "upload
  from phone X, then a frame on every phone" can still pick out X. On the host's own phone the request
  rides loopback, so the burst follows no guest's upload at all, which points at the host. The fix is
  for the house to put slotted requests to the rules on **its own beat** rather than at arrival, at up
  to one more second of latency (two in all). Not built: it doubles the cost the owner approved.
- `Gap` and `RadioFaulted` still go alone (item 7).
- The slot's 256 bytes and the 1 s cadence are provisional, as D-212's cadence always was.

| site | what it says now | what replaces it |
|---|---|---|
| `model/protocol` `RequestSlot`, `Asked` | (new) | the fixed-width slot; a control or the Egress tile |
| `model/protocol` `RoundBody.Sighted` | `slots` | `slots`, `ask` |
| `model/protocol` `RoundBody.UploadHeard` | (none) | host to the uploading seat, every upload: the slot's id |
| `model/protocol` `SightingUpload.pack` | four arguments | and the slot, an all-zero empty one by default |
| `model` `Effect.AbilityBlanked`, `EmitSchema`, `RoundMessage.AbilityBlanked` | (new) | every seat but the actor; row: both living classes; one padding digit, 117 bytes |
| `core` `Rules` Surge, Isolate, Static, Egress | `AbilityFired` to the actor | `answeredAndBlanked`: the actor's answer and every other seat's blank, seat by seat |
| `platform` `IntentDesk.slotted` | (new) | attributed by the connection, each id put once, 64 kept per seat across connections |
| `app` `SlotRequests`, `ContactUploader.ask` | (new) | the phone's queue; the slot read once a beat |
| `app` `RoundOverTransport.act`, `fireEgress` | an `Act` or `Fire` frame of its own | in a round, the next beat's slot; before arming, as before |
| `app` `LobbyHouse.attend` | sightings only | the slot, then the sightings, then `UploadHeard` |
| `ui` `Flow`, `app` `HouseBuzzer`, `harness` `Transcript` | (new) | a named no-op, silent, one transcript row |
| `harness` `Recording.HEADER` | `recording/28` | `recording/29` |

### D-264 · **Every phone measures the house's clock on its one socket, on one schedule whatever its role — built**

**What was missing.** The host has answered `TimeProbe` with its monotonic milliseconds since
protocol 2, and `ClockSync` (ARCH D5: minimum-RTT sample, a round every 30 s, slew never jump) was
built and unit-tested, but the real client sent no probes and dropped every `TimeMark`. Only the PING
rig used the estimator. So no phone knew the house's time, and the Sync Pulse could not be put at one
moment in two hands.

**Now.**

1. **`TransportClient` probes on every seated connection.** A coroutine beside the pump asks its own
   `ClockSync` every 250 ms what is due and writes those `TimeProbe`s straight onto the socket: five at
   seating, five every 30 s after. Each `TimeMark` is stamped on arrival and fed to the estimator
   **before and instead of** `onFrame`; nothing above the transport sees a mark. A new connection
   abandons the round in flight (its marks will never come) and probes at once; **the fix is kept**,
   because the house's clock did not change with the socket.
2. **The estimate is published as one immutable value** (`ClockFix`: base, target, slew start, RTT),
   so `hostNowMillis()` can be read from any thread without touching the single-threaded estimator.
   Never backward (the slew is unchanged).
3. **`LobbyOverTransport.houseNow()`** exposes it: null before seating and before the first fix. The
   host's own phone reaches its house over loopback like every guest, so it measures it the same way.
   **No screen reads it yet** (C3, C4 will).
4. **`monotonicMillis()`** (platform) names the house's clock once: what the host answers probes with,
   and what the pulse is anchored on (D-265).
5. **Bench and log.** `SOMEONES_HOME_CLOCK_SKEW_MS` adds a constant to a cheats build's client clock
   (Simulators on one Mac share one monotonic base, so an unmeasured offset would read right by
   accident). Each new estimate is logged `[clock] FIX rtt=… offset=… applied=…`, console only
   (rule 6). The PING rig now reads the client's own estimate instead of running a second one, whose
   probe numbers would have collided with the client's.

**Probes stay transport frames, outside D-263's request slot** (the spec's call): waiting for the
next beat would add up to a second of round trip, and round trip is the offset's noise floor.

**Leak and parity.** The schedule reads nothing but the connection's own clock; `TransportClient`
knows no seat and no role. Every phone sends the same five probes at the same offsets from its own
seating, and each mark goes only to the phone that asked. Their widths follow the probe counter and
the host's uptime digits, the same for every phone. No `Effect`, so no schema row; `TimeProbe` and
`TimeMark` were already `@ClientFacing`.

**R12's concern (the spec's).** `TransportHost` writes a `TimeMark` directly from the read loop while
the connection's writer sends pushes; Ktor's outgoing side is a channel, so whole frames interleave.
Now tested rather than argued (below). `TransportHost` itself is unchanged.

**Guards, each proven by breaking it.**

- `ClockProbeLoopbackTest` (platform; a real `TransportHost`, real clients over loopback):
  - two phones whose clocks are **3 s ahead and 2 s behind** each read the house's clock to within
    20 ms within a second of seating (measured: **0 ms off both**); no mark reaches the client's app
    and no probe the host's. *Broken by dropping the `onMark` call*: `phone 0 (skew 3000) had no fix a
    second after seating`, and all three tests fail. *Broken by reading the phone's own clock instead
    of the offset*: `phone 0 (its clock 3000 ms off) reads the house's clock 3000 ms wrong`.
  - three phones over 72 s of their own clocks (run ten times fast) send **15, 15, 15** probes: three
    bursts each. *Broken by a phone that doubles some bursts*: `Expected [15, 15, 15], actual [25, 25,
    15]`.
  - a push a millisecond for 1.5 s while the phone re-probes: all 1 103 pushes arrive whole and in
    order beside 30 probes' marks.
- `PulseFrameTest` (app; D-265): six phones with clocks off by +3 s, −2 s, 0, +1 s, −0.5 s and
  +0.25 s each put pulse 0 within **1 ms** of the house's moment, and every phone, Insider or Resident,
  sent **5** probes. *Broken by a zero offset*: `seat 0 (clock 3000 ms off) would feel pulse 0 -3000
  ms from the house's moment`.
- `TransportLoopbackTest` now checks the client's own probes instead of a hand-sent one; `ClockSyncTest`
  unchanged and green.

| site | what it says now | what replaces it |
|---|---|---|
| `platform` `TransportClient` | sends no probes; marks reach `onFrame` | probes on `ClockSync`'s schedule; marks to the estimator only; `hostNowMillis()`, `clockFix()`, `probesSent`, `onFix` |
| `platform` `ClockSync` | three private offset fields | `ClockFix`, one immutable value; `reconnected()` |
| `platform` `monotonicMillis` | (none) | the house's clock in ms |
| `app` `LobbyOverTransport` | client clock fixed | `clientMillis`, `onClockFix`, `houseNow()` |
| `app` cheats `TransportCheat`, `Cheats`, `ConsoleLines` | the rig's own estimator | the client's; `CLOCK_SKEW_SWITCH`; `[clock]` console lines |

### D-265 · **The Sync Pulse is scheduled and graded in the host's milliseconds, and every phone is told the grid — built (recording 30)**

**What was wrong.** The house graded taps against `firedAt + LEAD + n · INTERVAL` **in house
ticks**, and `EgressOpened` carried only a countdown. No phone knows the house's tick, and the ticks do
not keep wall time (one extra per arrival, D-230's skip-forward, the bench's skew), so a tap could not
be put on the grid by anybody: every Egress on real phones was an Insider win.

**Now** (the spec's own call, *pulse times are host-monotonic milliseconds, recorded as payload*):

1. **`Balance`**: `SYNC_PULSE_LEAD_MILLIS` (2 000), `SYNC_PULSE_INTERVAL_MILLIS` (1 500) and
   `SYNC_PULSE_WINDOW_MILLIS` (400), derived from the tick constants so there is one number of each;
   `syncPulseIsGradeableInMillis()` beside `syncPulseIsGradeable()`.
2. **`Event.EgressFired.pulseFrom`**: the host's millisecond of pulse 0. `egressFor` takes
   `anchorMillis` and sets `pulseFrom = anchor + LEAD_MILLIS`; **core reads no clock**.
   `RoundAuthority` hands it its new `hostMillis` seam, which is `monotonicMillis` — the clock
   `TransportHost` answers probes with — and **never the tick driver's**. The event's `at` stays a
   tick (ARCH D5). The anchor is a recorded input, so a replay grades the same taps the same way.
3. **`Egress.pulseFrom`**; **a pause does not move it** (D-133 stops the countdown, not the grid).
4. **`SyncPulseReturned.taps` and `ReturnSyncPulse.taps` are the host's milliseconds** as the phone
   measured them (KDoc only; the type is unchanged).
5. **`pulseNear`, `pulseAt`, `onThePulse`** (were `beatNear`, `beatAt`, `onTheBeat`; the spec asks new
   containment names to say *pulse*, since U1's uploads are "beats") grade on `pulseFrom` in integer
   milliseconds. **The rule is unchanged**: any four distinct pulses, each within ±400 ms; pairing
   still by arrival within `SYNC_PULSE_PAIR_WINDOW`. Consecutive pulses and pairing on the grid are
   **O3, the owner's**, and are not built. Nor is the Revoked-partner fix, which the spec puts in the
   same O3 change.
6. **`EgressOpened.pulseFrom`** (effect and wire): the same number for every seat.

**Leak and parity.** `EgressOpened` grows by `,"pulseFrom":N` on every phone alike; its width
follows the host's uptime digits, never the seat. No new kind here (D-266 adds one).

**Guards, each proven by breaking it.**

- `EgressTest` (core), every containment case ported to millisecond taps, plus:
  - *the pulse grid is anchored on the host clock and named to every seat*;
  - *taps are graded on the host clock whatever tick the house is at*: the same taps contain for an
    Egress fired at tick 60 and at tick 60 000, arriving 0, 7 or 90 ticks late, and taps on the tick
    grid miss;
  - *a pause does not move the pulse grid*.
  - **Broken by grading against the fire tick** (`firedAt + LEAD`): 10 of 30 fail, among them `two
    people at two nodes did not contain it`. The test anchor is set half a pulse off the grid that
    break would build, after a first anchor happened to sit exactly on the 400 ms window edge and let
    most cases pass under the break.
- `RoundAuthorityTest.theEgressPulseIsAnchoredOnTheHostClockAndNotTheTicks` (app cheats): the host
  clock fixed, the ticks jumped 5 000 s. **Broken by anchoring on the tick**: `the grid is not the
  host clock plus the lead. Expected <7779123>, actual <92000>`.
- `PulseFrameTest` (app; a real `TransportHost` on `monotonicMillis`, six real clients with skewed
  clocks, the real `EffectWire`, the rules' own arming and admission, run twice with seats 1 and 2
  exchanging roles): every phone, the Revoked one included, decodes the same `pulseFrom` from
  `EgressOpened`, and through its own measured offset puts pulse 0 within 1 ms of the house's moment.
  **Broken by a grid per seat**: `the phones were told different grids`.
- `SyncPulseAnsweredWireSizeTest` (model): `EgressOpened.pulseFrom` survives the wire and the frame is
  one length for every seat.
- `RecordingTextTest`: an Egress with its anchor survives its own recording; the fixture round's Egress
  is fired, paused, resumed and contained on the millisecond grid (`EgressFixtureTest`), and the
  differential harness replays it with roles exchanged.

**Recording format 30**: `EgressFired` carries `pulseFrom` (a row without one is fatal), taps are
milliseconds, `EgressOpened` carries `pulseFrom`, the state row's `egress` gains `pulse=`, and one
effect a version 29 build cannot name (D-266).

**Not changed, and why.** `RoundCheat.reachNodeAndPulse` still sends unanchored taps and still always
misses (D-232): rewriting it to tap on the grid through a screen is C5, which waits for the screen.

| site | what it says now | what replaces it |
|---|---|---|
| `model` `Balance` | tick timings only | and their millisecond twins |
| `model` `Event.EgressFired`, `Egress`, `Effect.EgressOpened`, `RoundMessage.EgressOpened` | no anchor | `pulseFrom`, host ms |
| `core` `egressFor` | no clock | `anchorMillis`, handed in |
| `core` `beatNear`, `beatAt`, `onTheBeat` | ticks off `firedAt` | `pulseNear`, `pulseAt`, `onThePulse`, ms off `pulseFrom` |
| `app` `RoundAuthority` | the tick only | `hostMillis` seam, `monotonicMillis` |
| `harness` `Recording.HEADER` | `recording/29` | `recording/30` |

### D-266 · **A Sync Pulse answer is one digit wide, and every other living phone is sent a blank of it — built, provisional, owner to confirm (it closes the spec's O5 rather than accepting it)**

**What leaked.** `RoundMessage.SyncPulseAnswered(held: Boolean)` serialised `true` or `false`, so the
answer to a return at a node was **one byte longer when the beat was missed** (114 and 115 bytes at
sequence 1, computed from the frame now measured at 111): whether a pair was forming was readable off
the Wi-Fi (D-258's audit, "S2"). And the answer reached the tapper alone, so the one living phone that
got a frame at that moment was the one standing at a node with its taps in. The request itself already
rides the fixed-cadence upload (D-263), so the downward frame was the whole tell.

**Now, the width** (D-258's `AbilityFired` fix, copied): the wire member is `answer: Int`, serialised
under the old name `held` as one digit (1 held, 0 not); call sites keep `SyncPulseAnswered(held = …)`
and read `.held`; any other value does not construct or decode.

**Now, the blank** (D-263's downward pattern, as the brief asked): a new effect and kind
`SyncPulseBlanked(seat)`. `pulseReturned` builds the tapper's answer and one blank for every other
seat, seat by seat, at every return, held, missed, locked out, from nowhere near a node, or the one
that contains. No role is read. Its row is both living classes, written out as `ABILITY_BLANKED`'s is,
the same classes as the answer's, so every living phone is sent exactly one of the two and the out
neither. `RoundMessage.SyncPulseBlanked` carries one zero digit under a name one character shorter and a
field one longer, so **answer and blank are 111 bytes at sequence 1, held or not**. `Flow` and
`HouseBuzzer` name it as a no-op; `Transcript` gives it a row.

**⚠️ For the owner.** The spec's O5 recommended *accepting* the extra frame for v1. This builds the
blank instead, at a cost of one ~110-byte frame per living phone per return (a return is four taps,
so at most one every six seconds per person at a node). Reverting it is deleting the blank's
construction in `pulseReturned`; the width fix stands either way.

**Guards, each proven by breaking it.**

- `SyncPulseAnsweredWireSizeTest` (model): held, not held and the blank are one length at sequences 1
  to a million; anything but one digit does not decode; the blank's row is the answer's; one frame per
  living seat for an Insider's and a Resident's return, held or not, and none for the out. **Broken by
  a two-digit "not held"**: `at seq 1 a held beat's answer is not a missed one's length`, and `held:10`
  decoded.
- `EgressTest."every living seat gets one pulse frame of one length whenever a return is answered"`
  (core; through the gate, the allowlist and the real encoders, seat 3 Revoked): a Resident's held and
  missed beats, a return from no node, an Insider's held and missed beats, and an Insider and a Resident
  containing. **Broken by answering the tapper only**: `a Resident's held beat: the pulse frames reached
  another set of seats: {0=[111]}`.
- `PulseFrameTest` (app, over sockets, roles exchanged): at a held beat, a miss and containment, every
  living phone is sent the same frames, byte for byte net of the sequence number's digits (110 bytes for
  the pulse frame on all of them), and each seat's frames are the same whichever role it holds; the
  Revoked phone gets no pulse frame. **The fail-closed check**: with the blank's schema row deleted,
  `A HELD BEAT: seat 1 got []` — the blank shipped to nobody.

| site | what it says now | what replaces it |
|---|---|---|
| `model/protocol` `RoundMessage.SyncPulseAnswered` | `held: Boolean` | `answer: Int`, one digit, under the name `held` |
| `model` `Effect.SyncPulseBlanked`, `EmitSchema`, `RoundMessage.SyncPulseBlanked` | (new) | every seat but the tapper; row: both living classes; one zero digit, 111 bytes |
| `core` `pulseReturned` | the answer, to the tapper | the answer and every other seat's blank, seat by seat |
| `ui` `Flow`, `app` `HouseBuzzer`, `harness` `Transcript` | (new) | a named no-op, silent, one transcript row |

**Two completeness guards caught the new kind on the first full run, as they exist to:**
`EmitSchemaTest."every Egress kind is permitted to somebody"` (`an Egress kind exists that this test
does not name`) and `HouseBuzzTest` (`the emit schema knows a kind this file's fixtures have never
built. Expected <[]>, actual <[SyncPulseBlanked]>`). Both now name it.

**Checked for this revision** (`./gradlew check --continue`, every test XML read): model 294 (JVM and
iOS Simulator), core 313, harness 123, platform 203, app 192, ui desktop 651, ui iOS Simulator 312,
0 failures; both variant sweeps (release, playtest), `vocabularyLint` in every module,
`redactionLint`, the cordons and the boundary checks clean. The unsigned Playtest device build
compiles. **Not on a phone; no screen, haptic or bench uses the clock or the grid yet.**

### D-267 · **Every push carries the round it belongs to, and the standing names the round the house is on: a phone that was off the air when a round ended ends its own the moment it is seated again — built**

**What broke** (V1 of the second review). `RoundBody.Push.seq` starts again at 1 every round and
`LobbyBody.RoundEnded` is sent once, to the phones connected at that moment, and never replayed. A
guest whose network went during round one (locked, out of range) came back after NEW ROUND still in
round one: its inbox held round one's sequence, so round two's opening pushes, numbered from 1, were
dropped as duplicates (no role, no seed, no roster); its radio stayed on round one's seed; a report it
owed from round one (D-245) went up into round two; and **at its seating it sent the line typed for
round one back up, which the desk filed as round two's** (`LobbyOverTransport` re-sends the line on
every seating, H2/D-225), so the house could arm round two with a line its owner wrote for round one,
with that phone still on round one's screen. `MissedRoundEndTest` reproduced it over loopback: with
the fix disabled the guest stays on round one's HOME screen.

**The fix, two markers, one decision each:**

1. **`LobbyBody.Standing.round`**: the house's round of the evening, from 1, one more at every
   NEW ROUND (`LobbyHouse.round`, written with the desk). The standing is the one body every seating
   is sent, so a phone reads it before anything else of the new connection. A number, not a flag:
   read twice it says the same thing, which is the objection `RoundEnded`'s note raises against a flag.
   **A phone that reads a higher number than it last heard missed the round's end, and ends its own
   there** (`houseEndedTheRound`, the same steps `RoundEnded` takes: the round half, the typed line,
   `FlowModel.newRound` under D-252's guard, the name back up). **The line a seating owes the house
   now goes up on the first standing after the seating, not at the seating**, so it is sent only if
   it was typed for the round the house is on.
2. **`RoundBody.Push.round`**, the same number, on every push, the same for every seat at a moment
   (no size differs by seat). `RoundInbox` judges the round before the sequence: an older round's
   push, or one of a round this phone ended, is stale; a later round's starts it from nothing. **A
   phone still in an earlier round when a later round's push arrives ends its round there first**
   (`RoundOverTransport`: its own reset, then `FlowModel.houseBeganAnotherRound`, which is
   `newRound`'s panel reset and walk to the lobby **without telling the link**, so a host's phone can
   never re-end a round over loopback, D-252). Over loopback the standing gets there first, so this is
   the second defence; it is proven on its own (`RoundMarkerTest`).
3. **`RoundBody.Gap.round`**: the round the phone's sequence is in. A house on another round
   replays from the start of its own.
4. **Leaving is not ending** (`RoundInbox.left`, `RoundOverTransport.left`): a phone that walked out
   (LEAVE, D-260's exit) holds nothing and no round is closed to it, so coming back to the same round
   by its token it is told that round again from the start (D-097).
5. **Owed reports** were already scoped to a round by the seed's fingerprint on a relaunch (D-245);
   in a living process the boundary above now drops them, so none crosses into the next round.

**Guards, each proven by breaking it.**

- `MissedRoundEndTest` (app cheats; five phones over loopback as `CheatRoot` builds them; one guest
  reaches the house through `Unplug`, a loopback relay the test unplugs, so the house really loses
  the socket and the guest's reconnects are really refused). The guest locks mid-round one (it owes
  the leave), its network goes, round one is played to the Insiders' ending and NEW ROUND pressed
  without it; its network comes back. It is walked to the lobby with a panel equal to a fresh
  lobby's, owes nothing, uploads nothing, and the desk counts no line from it; round two arms with
  its new line, it holds its role, seed and roster, and round two's first notice names nobody.
  *Injected (the standing check disabled):* `never happened: the guest was walked to the lobby on
  Home`.
- `RoundMarkerTest.aLaterRoundsPushEndsTheRoundThePhoneIsStillIn` (a real `RoundOverTransport` and
  `FlowModel`; fifty round-one pushes held and a leave owed, then round two from 1). *Injected (the
  inbox ignores the round):* `never happened: round two's opening was applied`. *Injected (the turn
  in `RoundOverTransport` removed):* `round one's line is still typed`.
- `RoundInboxTest.theRoundOnThePushDecidesBeforeTheSequence`,
  `EffectWireTest.everyPushCarriesTheRoundAndAnAskFromAnotherRoundStartsAtTheBeginning` (platform).
- `tools/sim-hunt/hunt.sh --seats 5 --tail 180 --relaunch-guest 2` (wire-only Simulator seats, seed
  20261150; `/tmp/someones-home-hunt/t1-r5`). Round one to ENDING (Insiders by an uncontained
  Egress), seat 2 stopped there; round two armed with four seats and played to its ENDING (Insiders by
  parity); seat 2 relaunched at AGAIN-ARMED, resumed its seat by its token, sat in the lobby with a
  clean panel, was pushed nothing of round two, and was named by no notice. No seat logged a missed
  frame or a crash; each seat's radio reported once per round it was in (seat 2 once).

**Not built, raised:** a phone relaunched (or seated) while a round it is not in is running sits in
the lobby, which counts it ("5 JOINED") beside a round of four. A mid-round arrival has no screen of
its own yet.

**No recording change**: no event or state row moved.

| site | what it says now | what replaces it |
|---|---|---|
| `model/protocol` `RoundBody.Push`, `RoundBody.Gap` | `seq`, `message`; `held` | and `round` (0 unmarked) |
| `model/protocol` `LobbyBody.Standing` | counts, names, setting | and `round` |
| `platform` `EffectWire` | unmarked pushes | every push marked; `roundEnded(next)`; a `Gap` from another round replays from 1 |
| `platform` `RoundInbox` | the sequence only | the round first; `turns`; `left` |
| `app` `LobbyHouse.roundEnded`, `publish` | | the round counted, carried on the standing and the wire |
| `app` `LobbyOverTransport` client | the line re-sent at every seating | the standing's round checked first; the line on that standing |
| `app` `RoundOverTransport`, `RoundClient` | | a later round's push ends this phone's round first; `left` |
| `ui` `FlowModel.houseBeganAnotherRound` | (new) | `newRound` without the link |
| `app` cheats `LobbyCheat` | | `SOMEONES_HOME_LOBBY_SEATS_AGAIN`, the party for later rounds |
| `tools/sim-hunt/hunt.sh` | | `--relaunch-guest K` |

### D-268 · **A seat has one replay, paced by the phone's socket and outside the live queue's cap, and a phone asks once per point — built**

**What broke** (V2). Every `Gap` was answered by `EffectWire.reassert` writing a full copy of the
stream after it, under the wire's lock, onto the seat's one live queue, which holds
`OUTBOUND_FRAMES` (512) before the house drops the link (D-244). And a phone asked again on **every**
push after a hole. So a phone behind a hole during a burst was queued a copy per push, the queue
overflowed, the link dropped, the phone came back and asked, and it went round again; and a phone
relaunched (holding nothing) into a seat with more than 511 pushes could never be caught up at all.
Measured with the old host code under `ReplayPacingTest`: the relaunched phone was dropped and
reseated 58 times in 30 s and held 174 of 1 107; the lagging phone held 124 of 707 after dozens of asks.

**The fix.**

1. **One replay per seat** (`EffectWire.reassert`). An ask while a replay runs moves it to the point
   asked (a new connection's) instead of starting a second copy. Each frame is read under the lock
   and written outside it. **While a seat is being replayed, a new push for it is filed and not
   written live**; the replay writes it in order, and the seat goes back to live writes at the end of
   its stream, found under the lock, so the phone sees one stream and no hole opens behind the replay.
2. **Paced, and outside the cap** (`TransportHost.resend`). A replayed frame is handed to the seat's
   own writer through a rendezvous channel: it returns when the writer has taken the frame, so a
   replay goes at the pace the phone reads it, never sits in the live queue and never counts against
   its 512. The writer prefers the live queue when both hold a frame (only lobby bodies and upload
   answers can be there for a seat being replayed). A replayed frame that waits more than
   `REPLAY_STALL_MILLIS` (10 s) drops the link, as a full live queue does.
3. **The phone asks once per point** (`RoundOverTransport`): a hole at the point it last asked from on
   this connection asks nothing. A new connection asks once. **Safety net:** a hole still open after
   `GAP_PATIENCE_BEATS` (5) beats with nothing applied asks again, so an answer that failed with a
   socket cannot leave a phone holding for the rest of the round. No gap, no extra frame: an idle
   phone sends no `Gap`.

**Guards, each proven by breaking it.**

- `ReplayPacingTest` (app; four phones over loopback, the real server and effect wire).
  `aRelaunchedPhoneAThousandPushesBehindIsCaughtUpOnce`: a guest's process ends, 1 100 pushes go on
  its stream, the app is opened again over the same token behind a 1 ms-a-frame socket; it holds all
  1 107 from one ask on one connection, each sent once. `aLaggingPhoneUnderAVoteBurstIsSentEachPushOnce`:
  a 2 ms-a-frame guest's link drops, 400 pushes are filed while it is away, and 300 `VoteProgressed`
  go to every seat during its replay; it needed 700, was sent 700, asked once, and was not dropped.
  *Injected (the old `reassert`):* `never happened within 30000 ms: the relaunched phone holds its
  whole stream held 174 of 1107 · 58 seating(s) · 58 ask(s)` and `... MARCUS 124/707 · lagging asks
  [7, 20, 21, 21, 21, ...`.
- `RoundMarkerTest.aHoleIsAskedAboutOncePerPoint`. *Injected (ask on every hole):* `the phone asked
  more than once for one hole. Expected <[Gap(held=1, round=3)]>, actual <[Gap(held=1, round=3),
  Gap(held=1, round=3), ...`.
- `EffectWireTest.aSeatHasOneReplayAndPushesMadeDuringItArriveAfterIt` (platform: a second ask
  merges, a push made mid-replay reaches every other seat at once and this seat after the replay).

| site | what it says now | what replaces it |
|---|---|---|
| `platform` `EffectWire.reassert` | a full copy per ask, under the lock, on the live queue | one replay per seat, merged, paced, a new push held behind it |
| `platform` `TransportHost` | one queue per link | and `again`, a rendezvous channel; `resend`; `REPLAY_STALL_MILLIS` |
| `app` `RoundOverTransport` | a `Gap` on every hole | once per point per connection; again after 5 beats with a hole open |
| `app` `LobbyHouse` | `reassert(seat, held)` | `reassert(seat, held, round)`, `resend` wired |

### D-269 · **The phones' clocks count to a stamped deadline, and the house's clock is the driver's alone — built**

**What broke** (V4). (a) **Phone.** `FlowHost` counted every clock a second at a time in a
`LaunchedEffect` keyed on the clocks' own values, so every second and every push relaunched it: a push
that restarted one clock threw away the part-second every other clock had run, and each wait ran a
little long. An Insider's own `AbilityFired` (D-262) restarts its Access bar and no other phone's, so
**the Insider's Revoke bar fell behind a Resident's at every fire: page 2 differing by role** (D-142,
D-239). Measured under `ClockParityTest` with the old ticker: after four fires answered mid-second the
Insider read 11 s where the Resident read 13 s. (b) **House.** Every arrival (request, upload, radio
fault, the Egress tile) was stamped `tick++`, on top of the driver's twenty a second. At eighteen seats
the uploads alone are eighteen a second, so the house's clock ran nearly twice as fast as the room's:
cooldowns ended early, D-236's 2.5 s contact window was about 1.3 s, and an Egress was stamped on this
clock and judged on the driver's.

**The fix.**

1. **Phone** (`FlowModel.clocksRead`, `clocksStamped`; `FlowHost`). Each clock is stamped, at the push
   that (re)started it, with the phone's monotonic time (`monotonicMillis`, handed down by the app
   root as the wall clock is), and a reading is the whole seconds since then. Found by comparing with
   what was last seen, so no push's branch stamps anything itself. `FlowHost` runs one loop for the
   life of the model, waking at the next whole second of whichever running clock turns over first
   (at most 250 ms when none runs). Nothing accumulates, and D-239 parity holds: two phones sent the
   same pushes differ by when the pushes reached them, however often either redraws. A model handed no
   clock (renders, tests) keeps its own, moved by `secondPassed`, with the same arithmetic.
2. **House** (`RoundAuthority.stamp`). An arrival is stamped with the tick the driver last set and does
   not move it. Arrivals in one 50 ms step share a tick, which the rules accept (both halves of a touch
   already did), and the recording orders them by its rows. Rule 4: `core` reads no clock, the stamp is
   the driver's and rides the recorded event. `now()` (benches and tests minting their own events) is
   unchanged.

**Guards, each proven by breaking it.**

- `ClockParityTest` (ui desktop; two real `FlowModel`s each in a real `FlowHost` in one composition,
  the composition's clock as the monotonic clock). An Insider and a Resident sent the same pushes; the
  Insider's four fires answered half way through a second. **Worst gap 0 s** at every reading; and a
  clock runs out within 200 ms of its seconds. *Injected (the old keyed ticker):* `the Insider's
  Revoke clock and the Resident's read different seconds: [... fire 4: 11s/12s, after fire 4:
  11s/13s ...` and `the Revoke clock was still running after its 31s`.
- `HouseTimeTest` (app cheats; the real authority armed with eighteen seats; ten driver seconds, every
  seat uploading on its seed each second, half pressing something refused, one radio fault).
  200 ticks in ten seconds. *Injected (`tick++` back):* `second 1: the arrivals moved the house's
  clock past the driver's. Expected <20>, actual <47>.`

| site | what it says now | what replaces it |
|---|---|---|
| `ui` `FlowModel` | `secondPassed` counts | `monotonicMillis`; `clocksRead`, `untilNextSecond`; `secondPassed` for a model with no clock |
| `ui` `PublicCooldown.at` | (new) | a clock read at a whole second |
| `ui` `FlowHost` | `LaunchedEffect(clocks)` | one loop keyed on the model |
| `app` cheats `CheatRoot` | | the monotonic clock handed down |
| `app` `RoundAuthority` | `Tick(tick++)` per arrival | `stamp()`, the driver's tick |

## Revision 79 — every phone measures the house's clock, and the Sync Pulse is scheduled on it

*2026-11-17, overnight run, unit C12: the decision-free half of the containment spec
(`_bmad-output/implementation-artifacts/containment-spec-2026-11-17.md`, C1, C2 and the
`SyncPulseAnswered` width). C3 (the screen), C4 (the felt pulse) and C5 (the bench through the screen)
wait for the owner's O1-O5 and the pack's Q4. **No screen reads any of this yet, so no Egress can be
contained on real phones yet either.** Desktop, loopback and the iOS-Simulator unit-test target only;
nothing here has been on a phone.*

## Revision 80 — the home editor opens on nothing on a real phone

*2026-11-17, overnight run, unit O1 of the occupancy spec
(`_bmad-output/implementation-artifacts/occupancy-spec-2026-11-17.md`). `ui` only. Desktop and
simulator unit tests only; nothing here has been on a phone.*

### D-270 · **A real phone's home editor opens on one empty storey, no rooms and no cards; the design's bungalow is the render harness's alone — built**

**What was fixture.** `FlowModel.editor`, `Screen`'s default editor and `LocalEditor` all started
from `HomeEditorModel.bungalow()`: the design's two storeys, nine rooms, two stairs, nine markers, the T card in
HALL and the meeting card in LIVING. MAP A NEW HOME already cleared it (`startNewHome`), and EDIT THE
PLAN loads the saved home, but **any route into the editor that did neither** (the playtest picker,
EDIT THE PLAN with no home open) showed a host rooms and cards their house does not have. The only
claim it was "on purpose" was a test comment (`HousedContentTest`, D-257's exemption).

**The docs.** gdd:822 and EP 4.3: *"start with Floor 0 (renameable), add more as you go. Nothing
fixed"*; gdd:895: *"The host paints **Floor 0**"*; D-125: *"The app never has an opinion about what a
home is."* No entry asks for a template.

**Now.** One rule, in one function: `HomeEditorModel.opening(renderHarness)` gives the bungalow to the
render harness and `HomeEditorModel.empty()` (the plan `startNewHome` builds: one storey called
`GROUND`, no rooms, no cards, no name) to anything else. The harness is D-255's, asked of a state by
the new `PanelVals.isRenderHarness(state)` (no house **and** no device clock), which `renderHarness`
now calls, so the clock and the editor cannot disagree about which phone is the harness.
`FlowModel`'s `editor` parameter is nullable (*choose for me*) and the property is chosen after
`init` has read the clock; `Screen`'s default uses the same rule; `LocalEditor`'s default, which has no
state to ask, is `empty()`, failing closed. The app always hands a clock (`deviceWallTime`), so every
real phone opens empty; every render and test that hands none still draws the mockup.

**Saved homes are unchanged.** EDIT THE PLAN and RENAME load the open home whole (`load`: plan, cards,
terminal, meeting card, name). The one-phone rig's `ONE PHONE` home is seeded through
`SavedHomesModel.save` (`LobbyCheat.seedTheHome`) and never touches the editor, so the rig is
unaffected.

**An empty home is not saved, and SAVE HOME is not where that is decided.** `saveHome` refuses only
a blank name, a name another home holds, or a failed write; it has no rule about rooms (D-125). The
editor's only way to SAVE HOME is REVIEW HOME, which goes to SAVE HOME only when the home passes
D-127's one gate (a terminal, a meeting card, eight ordinary markers) and otherwise to the screen that
names what is missing (D-126: *every gate lives at REVIEW*). An empty plan has no room to hold a card,
so it is short all three: REVIEW HOME . 3 THINGS MISSING, then THIS HOME NEEDS 3 MORE THINGS, and the
file is untouched. Rendered and looked at (300×650): the empty editor reads `HOME 1`, `0 ROOMS . 0
MARKERS`, one `GROUND` block and an empty grid; FLOORS reads `1 IN THIS HOME`.

**Not changed, flagged.** The storey is named `GROUND`, the GDD says *Floor 0*; both are renameable.
The spec recommends keeping `GROUND` and sweeping gdd:822/895; neither is done here. The spec's other
suggestion, dropping `HousedContentTest`'s setup-screen exemption, does not work: on those screens
KITCHEN, LIVING and GARAGE are the room panel's preset chips, STAIRS its type control and GROUND the
empty storey's name. The exemption stays, its comment corrected, and `EditorOpeningTest` holds the
editor instead.

**Guards (`EditorOpeningTest`), each proven by breaking it.**

- `aNewHomeOnARealPhoneOpensEmpty`: a phone handed a clock opens empty before any route, after EDIT
  THE PLAN with nothing open, after MAP A NEW HOME, and after MAP A NEW HOME following a saved home;
  drawn through the model's editor and through `Screen`'s default, it counts `0 ROOMS . 0 MARKERS` and
  draws no bungalow room. **Broken by `opening` always returning the bungalow**: *a fresh real phone:
  not one storey expected:<1> but was:<2>*. **Broken by `Screen`'s default alone put back to the
  bungalow**: *Screen's default did not count an empty storey: [, 23:46, THE BUNGALOW, 5 ROOMS . 5
  MARKERS, GROUND, UPPER, KITCHEN, …]*.
- `theHarnessStillDrawsTheMockup`: a model handed no clock holds the bungalow, and both routes draw
  THE BUNGALOW and `5 ROOMS . 5 MARKERS`. **Broken by `opening` always returning empty**: *the harness
  lost its bungalow expected:<[THE BUNGALOW]> but was:<[]>*.
- `aSavedHomeRoundTrips`: a home read off the file opens in a real phone's editor as saved, and SAVE
  HOME writes back exactly that. **Broken by `load` keeping the previous cards**: *EDIT THE PLAN did
  not open the home as saved expected:<SavedHome(SOMEWHERE KEPT, 2fl, 9rm, 9mk, terminal=HALL,
  meeting=LIVING)> but was:<…>*.
- `anEmptyHomeGoesToReviewAndIsNotSaved` (through the real screens): MAP A NEW HOME, then REVIEW HOME
  . 3 THINGS MISSING, lands on ReviewNeeds and the file stays empty. **Broken by REVIEW HOME always
  going to SAVE HOME**: *an empty home went on to SAVE HOME expected:<ReviewNeeds> but was:<SaveName>*.

| site | what it says now | what replaces it |
|---|---|---|
| `ui` `HomeEditorModel` | `bungalow()` only | `empty()`, `opening(renderHarness)`; `startNewHome` builds `empty()`'s plan |
| `ui` `FlowModel.editor` | `bungalow()` default | `opening(isRenderHarness(state))`, chosen after `init` reads the clock |
| `ui` `Screen` `editor`, `LocalEditor` | `bungalow()` | `opening(…)`; `empty()` |
| `ui` `PanelVals` | `renderHarness` inline | `isRenderHarness(state)`, shared |

**Checked for this revision** (every test XML read, on top of D-267 to D-269): ui desktop 657, ui
iOS Simulator 312, app 198 (debug), the release sweep 127 and the playtest sweep 198, 0 failures;
`:ui:vocabularyLint` clean. **Not on a phone.**


## Revision 81 — the house holds the plan again and builds the spectator map's input; no phone is sent anything new

*2026-11-17, overnight run, unit O24: the decision-free core of the occupancy spec
(`_bmad-output/implementation-artifacts/occupancy-spec-2026-11-17.md`, O2 and O4, with O6's capture).
O3, O5, O6's view and wire, and O7 wait on the owner's Q1-Q5. **Nothing here reaches any client.** Every
number in it is provisional and named for playtest (Q5). Checked in core, harness and the real
`RoundAuthority` on the iOS-Simulator unit-test target and the JVM; nothing here has been on a phone, and
no RSSI value has been measured for a room.*

### D-271 · **The home's plan is a recorded round input again, as a narrow `HomePlanView` with no card in it, and it crosses to no client — built (recording 31)**

**What was missing** (the spec's §0). The host holds the plan for the round (`RoundAuthority.home`) and the
rules had no copy, so nothing they decide could know which rooms touch which. D-186 took the plan off the
arming event with the Spoof picker.

**What is built.**

1. **`HomePlanView`** (`model`): storeys in plan order, each a name, a bounding box and one cell per square
   (a room's index or unpainted); rooms in plan order as a name and a kind. **No card, terminal, meeting
   card, station, active marker or seat**: rule 3, the type is the field list. Cells rather than strokes, as
   `4cefbf5` chose. Built only by `HousePlan.toView()`; `adjacency` is `Floor.neighboursOf`'s rule read off
   the cells (four-neighbourhood, one storey), computed once per round.
2. **It is NOT `@Serializable` and NOT `@ClientFacing`.** Whether the plan may reach any phone is the
   owner's Q1 (D-186 and EP:292 against M11/M12). No `RoundMessage` carries it and no `Effect` names it, so
   the emit schema has nothing to permit; the unit that answers Q1 adds the marker, the message and the row
   together. **D-186's sentence stands: the plan crosses to no client.**
3. **`Event.RoundArmed.plan` and `.terminalRoom`**, held as `GameState.plan` and `GameState.terminalRoom`.
   The T card's room is the house's own copy, so a seat placed at the station is placed in a room. D-199's
   *the station's room is the host's to say aloud* stands for every phone: no effect carries it.
   `RoundAuthority.armWith` passes `hosted.plan.toView()` and `hosted.map.terminal?.room?.name`.
4. **Nothing in the rules decides from the plan but the spectator map's error** (D-272), which asks one
   question of it: *do these two rooms touch*.
5. **Recording 31.** `RoundArmed` gains `rooms=`, `storeys=` and `terminalRoom=` (`none` or `some:` and the
   name, so a room called NONE cannot vanish); the parser defaults nothing and refuses a plan the model
   refuses.

### D-272 · **The house builds D-136's placements from the uploads it already gets, and captures each room's count with its error rolled once; nothing is sent — built, every tunable provisional, owner to confirm (Q5)**

**What was missing.** `RoundAuthority.sight` handed each upload to the contact resolver alone.
`Placement.Inferred` and `Observation` had no producer. And **`GameState.placementOf` returned `Known` off
the presence row's card whether or not the window was open** (the spec's finding): it could not tell a seat
mid-Subroutine from one that walked away ten minutes ago, and it placed every seat at the last card it read
for the rest of the round.

**What is built.**

1. **`resolvePlacements`** (`core`, beside `resolveContacts`, one unpack and the resolver's own token book).
   An upload mints **`Event.Sensed(at, sensor, room, heard)`** when its sender is a **sensor**: living,
   placed at a card this shift, and performing (a window open or a Subroutine armed, D-136) or within the
   **3 s sweep on scan** of the scan or close that placed it (`gdd.md:776`). `room` is where the house
   already knows the sensor stands; `heard` is every living seat whose token it carried at **−75 dBm or
   stronger**, in seat order, possibly none. Nothing is sensed at a meeting (and a `Sensed` that arrives
   during one is refused at the gate, R5's shape), before arming, after the end, from an out seat (D-155;
   R8's filler is the first denial), or at a card the house's map cannot put in a room. Only an upload the
   resolver took as new senses (`ContactResolution.taken`), so a transport's repeat is one capture. After
   the contacts, so a seat a Revoke just put out senses nothing.
2. **The rules (`sensed`)** write one `Placement.Inferred(seat, room, at)` per heard living seat into
   **`GameState.sightings`, a different collection from the presence plane** (D-136, D-186's kept shape),
   newest wins; and capture one **`Observation`** of the sensor's room. **It emits nothing on any path.**
3. **`placementOf(seat, at)`**, as D-136 reads: an out seat or any seat during a meeting is unplaced; **work
   in progress (window open or Subroutine armed) is Known and nothing relocates it**; a check-in is Known for
   **10 s**; otherwise the newest of the closed Known and the latest sighting, each for **45 s**, knowledge
   on a tie; then unplaced (a coverage hole). Nothing from before the shift counts: `shiftStartedAt` is the
   arming or the last meeting's end, where the sightings and observations are emptied (`gdd.md:1050`,
   `:1070`). `Presence` gains `since`, the last tick the house knew the seat stood at its card.
4. **`Observation`** is reshaped for capture (1.8 #5 and #6 of the spec, authority side only): `id`, `room`
   as a plan room name, `seenAt`, `trueCount` (the living seats `placementOf` puts there), and
   **`reportedCount` and `ageJitter`, rolled once at capture under `SeedPurpose.Occupancy` keyed on the
   capture's own number, never on a seat or a role, and stored** (rule 4, `gdd.md:484`, `:492`). The error:
   **15 % a missed occupant**, only in a room with somebody in it (F-007's lean to under-counting);
   otherwise **5 % a phantom**, only where a room sharing an edge on the plan has somebody in it
   (*plausible, not random*, `gdd.md:491`). **±5 s** jitter. Bands are `stalenessAt(now)`: fresh under
   15 s, stale under 45 s, cold after, a pure function of the stored capture. `ObservationView` is
   unchanged and no message carries it; its shape is O6's.
5. **The unreliability is the design** (D-136; the occupancy spec's O4): bleed at the floor, holes where
   nobody performs, expiry, and the error. None of it is to be tuned away.
6. **Not built, read as specified in the spec but left out:** the Terminal reader as a sensor (the spec's
   1.7 #6; the 3 s sweep covers the check-in only), F-007's hold beyond the 45 s expiry, observation
   history for the timelapse, and a check-in that captures without an upload.

**What reaches a phone: nothing new.** `Sensed` has no effect, so no schema row was added or asked for; the
plan and the map are held in the authority's state and its recording only.

**Allocation** (rule 7), estimated by counting objects, **not measured**: an upload from a seat that is not
a sensor adds about 40 B (the resolver's `taken` list); a sensor's upload adds about 4 KB, of which about
1.5 KB is the capture's one HMAC and the rest the `Sensed`, two `GameState` copies, the sightings and
observations lists and one `placementOf` per seat. **Every living phone a sensor at once: about 32 KB/s at
8 seats and 72 KB/s at 18**, 6 % and 14 % of the 0.5 MB/s budget; half performing, half that. A Kotlin/Native
measurement through `allocatedBytesSinceStart` read 32-78 B per sensor upload, which cannot be right for
code that runs an HMAC, so it is discarded as the under-read that counter's own KDoc warns of.

**Guards, each proven by breaking it** (the injected failure quoted).

- `HomePlanViewFieldsTest` (model JVM, reflection): the three classes' fields exactly, and no serializer.
  *Injected `val terminal: MarkerId?` on the view:* `expected:<[storeys: …, rooms: …]> but was:<[…]>`.
- `HomePlanViewTest`: an L-shaped room is one room; stairs stay stairs; adjacency equals
  `Floor.neighboursOf` on every room.
- `PlacementTest` (core, the real pipeline: a packed upload, the resolver's book, the gate):
  - a performer hears seat 4 at −60: `Inferred` in its room, observed with both counted, no effect;
  - *injected (a newer sighting beats an open window):* `scan, then heard, at 111: an inference relocated a
    performer. Expected … Known, actual … Inferred`;
  - *injected (`placementOf` without the work-in-progress arm):* `a seat ten minutes into an open
    Subroutine was not placed at it. Expected <Known(…)>, actual <null>`;
  - *injected (out seats heard):* `an out seat was heard into a room. Expected <[Seat(4)]>, actual <[Seat(3),
    Seat(4), Seat(5)]>`;
  - *injected (sensing at a meeting):* `a Sensed was minted at a meeting`; *(no wipe at meeting end):* `the
    sightings outlived the meeting`;
  - *injected (every held observation re-captured on each capture):* `the first capture was re-captured by
    the second. Expected <1>, actual <2>`;
  - *injected (the error reads a role):* `insiders [Seat(0)]: the error moved with a role`;
  - over 3 000 captures: an occupied room missed 469 (15.6 %), a phantom beside it 122 (4.1 %), an
    isolated empty room never wrong.
- `OccupancyRecordingTest` (harness): the plan and every `Sensed` round-trip the text form and replay
  Identical; a row missing `rooms`, `storeys`, `terminalRoom` or `heard`, or a cell naming a room the plan
  lacks, is fatal; **every seat's per-client transcript (`recordPerClient`, the allowlist's own deliveries)
  is byte-identical with and without the plan and the map**, and so is the effect stream; a role exchange
  (seats 1 and 3) gives the same diff with the map as without, nothing unexplained, and the same map.
  *Injected (`Sensed` emits a `LampSet` to the sensor):* `seat 0 was sent something new`. *Injected
  (`Sensed` emits a `PresenceChanged`, which has no row):* every per-client transcript still identical —
  the schema failed closed — and `the authority produced an effect for the plan or the map`.
- `PlacementWiringTest` (app, the real `RoundAuthority` in the bench's home): the hosted plan is the rules'
  plan; a performer's upload places what it heard and **pushes nothing**; a repeat is not captured again; a
  non-sensor's upload observes nothing. *Injected (the `taken` check off):* `a transport's repeat was
  captured again. Expected <2>, actual <3>`. *Injected (the authority never senses):* `seat 3 was not placed
  by seat 0's upload`.

| site | what it says now | what replaces it |
|---|---|---|
| `model` `HomePlanView`, `StoreyView`, `RoomView`, `HousePlan.toView` | (new) | the narrow plan; not serialisable |
| `model` `Event.RoundArmed` | no plan | `plan`, `terminalRoom` |
| `model` `Event.Sensed` | (new) | sensor, room, heard |
| `model` `GameState` | `placementOf(seat)` | `placementOf(seat, at)`, `placedRoomOf`, `roomOfCard`; `plan`, `terminalRoom`, `shiftStartedAt`, `sightings`, `observations`, `nextObservation` |
| `model` `Presence` | seat, card, open | and `since` |
| `model` `Placement.Inferred` | seat, room | and `at`; `PlacementTiming` (10 s, 45 s) |
| `model` `Observation` | room as a card, a precomputed staleness | id, plan room, seenAt, true, reported, ageJitter; `stalenessAt(now)`; `toView(now)` |
| `core` `Placements.kt` | (new) | `SensingGate`, `resolvePlacements`, `sensed`, `capture` |
| `core` `SeedPurpose` | | `Occupancy` |
| `core` `ContactResolution` | memory, events | and `taken` |
| `core` `Admission` | | `Sensed` at a meeting refused |
| `core` `Rules` lights out | | a new shift; sightings and observations emptied |
| `app` `RoundAuthority` | the plan stays on the lobby; uploads to contacts only | the plan and station room at arming; each new upload then to `resolvePlacements` |
| `harness` `Transcript`, `RecordingText`, `Recording.HEADER` | `recording/30` | `recording/31`; `Sensed` rows; the state row's map |

**Checked for this revision** (`:model:check :core:check :harness:check :app:check`, both variant sweeps, `:ui:compileKotlinDesktop`, `:ui:vocabularyLint`; every test XML read): model 300 JVM and 298 iOS Simulator, core 323, harness 127, app 199, 0 failures; `vocabularyLint`, `redactionLint`, the cordons and the boundary checks clean. The `ui` tests were not run: `ui` reads none of this. **Not on a phone; no RSSI is calibrated for a room; nothing is drawn.**

### D-273 · **A phone seated while a round it is not in runs is shown the round's count and ROUND IN PROGRESS, and NEW ROUND seats it like everybody — built (provisional copy, owner to confirm)**

**What broke** (raised in D-267; `hunt.sh --relaunch-guest 2`, `/tmp/someones-home-hunt/t1-r5b`). A
guest stopped at round one's ending and relaunched after round two armed with four is seated by its
token (D-225: a token returns *that* seat at any time). The desk counted it, and the standing is the
desk's, so its lobby read **5 JOINED** over five names (its own among them) beside a round of four,
asked it for a line the next NEW ROUND deletes, and the bench typed one: `5 OF 5 HANDED THEIRS OVER`.
The round itself was right: the seat was pushed nothing, and the arming had read the desk once.

**The docs are silent.** D-097 (re-assertion), D-225/226 (the token, the host's empty room), D-252,
D-259 and D-267 say how a phone *in* a round comes back, and D-267 raised this case without ruling;
neither the GDD nor the epics name a mid-round arrival. So this is the minimal honest state, flagged:
**it is not in this round, and the count is the round's.**

**Now.**

1. **The house knows the round's seats** (`LobbyHouse.playing`): taken from the state the round's
   first push is emitted with (its seats and their names, before the ending empties them), held to
   NEW ROUND (`roundEnded`) or the house's stop. From the emit rather than the arming call so nothing
   in `RoundAuthority` changes; an ended round's late push cannot set it.
2. **While it is set, the standing is the round's** (`publish`): `joined`, `linesIn` and `names` are
   the round's seats as armed (every one handed a line over, the arming gate), so a seat that dropped
   and came back is still counted and a seat outside it never is. **Each phone is told whether it is
   one of them**: `LobbyBody.Standing.waitingForNextRound`, true only on a phone seated outside the
   running round. It answers one question about the phone it is sent to and names no seat (D-191's
   shape); who is in a round is public, the room watched the lights go out. It differs in length
   between an in-round phone and a waiting one, which says only that.
3. **The waiting phone's lobby** (`RoundUnderWay`, `ui`): the home's name with `4 IN THIS ROUND`, the
   round's names, **ROUND IN PROGRESS**, **YOU JOIN THE NEXT ONE**, and LEAVE THIS HOUSE. No settings
   and no line row: neither is its to act on, and a line typed now would be deleted at NEW ROUND.
   *Copy provisional.* Screens say little: the headline and the one way out.
4. **NEW ROUND seats it like everybody**: the house clears the round's seats before it publishes, so
   the next standing is the desk's again; the phone already gets `RoundEnded` and the standing as any
   seated phone does. The bench waits out the flag before typing a line (`awaitTheNextRound`), as a
   person would, having no line row to type into.

**Guards, each proven by breaking it.**

- `RoundUnderWayTest` (app cheats; five phones over loopback as `CheatRoot` builds them): round one
  to its ending with five, a guest stopped there (its socket closes), NEW ROUND, round two armed with
  four, the guest relaunched (a new process, the same stored token) mid-round two: it is told it waits,
  counts 4, lists the round's four names without its own, stands on its lobby and is pushed no role or
  roster; the host is told 4 and not told to wait; round two ends and the guest still waits (an ending
  is not NEW ROUND); NEW ROUND seats it with 5, and round three arms with five, the guest holding its
  role and a roster of five. *Injected (the standing ignores the round's seats, the old behaviour):*
  `never happened: the relaunched guest was told a round is under way without it Standing(joined=5,
  linesIn=4, names=[ALBA, BRUNO, CYRUS, DEVI, ENZO], insiders=1, round=2, waitingForNextRound=false)`.
  *Injected (the round's seats kept across NEW ROUND):* `never happened: every line reached the house
  Standing(joined=5, linesIn=5, names=[ALBA, BRUNO, CYRUS, DEVI, ENZO], insiders=1, round=2,
  waitingForNextRound=false)`.
- `RoundUnderWayScreenTest` (ui desktop): the flag draws `4 IN THIS ROUND`, ROUND IN PROGRESS, the
  four names and LEAVE THIS HOUSE, and no JOINED, YOUR ONE LINE or INSIDERS; the same standing
  without it draws the ordinary lobby. *Injected (the waiting branch removed):* `the round's count is
  not on the waiting phone`.
- `tools/sim-hunt/hunt.sh --seats 5 --tail 180 --relaunch-guest 2` (wire-only Simulator seats, seed
  20261150; `/tmp/someones-home-hunt/d271-r5`). Seat 2 stopped at round one's ENDING, relaunched at
  AGAIN-ARMED: `SEATED — 4 IN THIS ROUND · ROUND IN PROGRESS · [KESTREL, OSPREY, HERON, FINCH]`,
  waited through round two typing nothing, then `THE NEXT ROUND'S LOBBY — 5 JOINED`, handed its line
  over, and was armed into and played round three (ARMED, its Subroutines). Looked at:
  `shots/20-AGAIN-MEETING-RINGING--seat2.png` reads HUNT HOME · 4 IN THIS ROUND, the four names,
  ROUND IN PROGRESS, YOU JOIN THE NEXT ONE, LEAVE THIS HOUSE; `shots/28-AGAIN-ARMED--seat2.png` is
  round three's ARMED Subroutine screen. (t1-r5b's shot 20 was the ordinary lobby with 5 JOINED.)

**Two tests read the standing as a count of connections mid-round** (`MissedRoundEndTest`,
`ReplayPacingTest`, waiting for the house to lose a guest). Mid-round it is the round's count now, by
design, so they read the desk (`armingInputs().seats`) instead.

| site | what it says now | what replaces it |
|---|---|---|
| `model/protocol` `LobbyBody.Standing` | counts, names, setting, round | and `waitingForNextRound` |
| `app` `LobbyHouse` | the standing is the desk's, one body to every phone | `playing` from the round's first push to NEW ROUND; the round's counts, and the flag per phone |
| `ui` `LobbyModel.waitingForNextRound`, `RoundUnderWay` | the ordinary lobby | the round's count, ROUND IN PROGRESS, LEAVE THIS HOUSE |
| `app` cheats `LobbyCheat` | a line typed at the seating | `awaitTheNextRound`; the waiting header in its standing line |

**Checked for this revision** (every test XML read, on top of D-271/D-272): app 200 (debug), the
release sweep 127 and the playtest sweep 200, ui desktop 659, ui iOS Simulator 312, 0 failures; every
`vocabularyLint`, `armingCordon`, `benchSeedCordon`, `seedSourceCordon` and `redactionLint` clean.
**Not on a phone.**

### D-274 · **The roster is indexed by seat across a hole; the out are told the meter at every meeting; a phone waiting on an ended round says WAITING FOR THE HOST — built (recording 32)**

**What broke** (`hunt.sh`, six seats, seat 2 relaunched and sat out round two;
`/tmp/someones-home-hunt/r6-final/a6/sheets`). Round two armed with seats 0, 1, 3, 4 and 5.

1. **Names shifted past the hole.** `Rules.armed` built the roster by mapping the seats it was armed
   with, so with seat 2 absent entry 2 was seat 3's name — and every phone reads the roster *by seat
   index* (`nameOf`, the ballot's row → seat, the Surge row → seat). The couch named seats 1, 3, 4, 5
   OSPREY, FINCH, PLOVER, UNNAMED, and an older run restrained HERON while every screen said FINCH.
   D-115's own comment said a blank holds the gap; the code did not.
2. **The out phone's ending drew SYSTEM INTEGRITY as no reading** beside every living phone's 100%.
   The out are sent the meter when it moves (`SystemIntegrityLive`), and the batch at a meeting is the
   living's; a seat Revoked before the meter ever moved was told neither, ever.
3. **The relaunched guest said ROUND IN PROGRESS over the ending.** D-273 keeps it waiting through the
   ending (an ending is not NEW ROUND), and nothing told it the round had ended.

**Now.**

1. **`RosterIssued.roster` is `List<String?>`, one slot per seat index up to the highest seat, null
   where no seat sits** (a blank stays *a seat that gave no name*). Still one list, byte-identical to
   every phone, so the frame is role-independent; that seat 2 is empty is no more than the room saw at
   LIGHTS OUT. The phone turns null into a blank on the way in, and **a blank is not a row**: the ballot
   and the Surge picker skip it, keeping the index, which is the seat. `PanelState.seats` is the
   non-null count, never the length. The transcript's `RosterIssued|seats=` counts non-nulls, the same
   number as before for every round armed `0..n-1`.
2. **Every meeting call emits `SystemIntegrityLive` beside the batch, with the batch's percentage.**
   Its row is unchanged (the out classes only), it is silent (`HouseBuzzer`), unconditional like the
   batch, and it reaches nobody when nobody is out; the out are being walked in at that instant and
   are entitled to the number all round (D-134). It makes true what `SYSTEM_INTEGRITY_BATCHED`'s row
   already said — *the out have had the same number all along*. One more effect per meeting, so
   **recording 32**.
3. **`LobbyBody.Standing.roundOver`**, set on the waiting phone's body only, from the round's first
   ended push to NEW ROUND (`LobbyHouse.playingOver`). The waiting screen then draws **WAITING FOR THE
   HOST** — the lobby's own caption for a guest waiting on the host — in place of ROUND IN PROGRESS;
   YOU JOIN THE NEXT ONE and LEAVE THIS HOUSE stay. No new copy. Still provisional with D-273's.

**Not done: the couch's VOTES CAST** (the same sheet: `VOTES CAST 4 OF 4` on the couch while every
living phone read `0 OF 4 VOTED`). The couch counts the selections it is shown, and every selection is
an un-cast pick — by design (D-117, D-134: the couch sees selections). It is not fed anything the house
did not hold. Counting *cast* ballots needs the locked count, which reaches the living only
(`VOTE_PROGRESSED to LIVING`); widening that row, or relabelling the couch's count, is the owner's.

**Guards, each run red against the old code first.**

- `RosterBySeatTest` (core): seats 0, 1, 3, 4. *Old code:* `seat 3's name is not at index 3:
  [NAME-0, NAME-1, NAME-3, NAME-4]`.
- `MeterPushTest` — *calling a meeting hands the out the meter too*. *Old code:* `the out seat was
  never told the meter, so its ending reads no number. Expected <1>, actual <0>`.
- `RosterGapTest` (ui desktop): the ballot and the Surge picker draw no row for the hole, and DANI's
  rows send seat 3. *Old code:* `the ballot drew a row for the seat that is not in the round
  expected:<0> but was:<1>`; `found '1' node that satisfies: (TestTag = 'target-row-2')`.
- `RoundUnderWayScreenTest` and `RoundUnderWayTest`: the waiting phone over an ended round.
  *Injected (the ending not published):* `never happened: the waiting guest was told round two is
  over Standing(joined=4, …, waitingForNextRound=true, roundOver=false)`. *Injected (the screen ignores
  the flag):* `the waiting phone says a round is in progress over its ending expected:<0> but was:<1>`.
- `RosterGapRender` (ui desktop, a viewer): `build/screens/gap-*.png`, looked at — the ballot and the
  Surge picker list five names with no blank row, the couch names seats 1, 3, 4, 5 OSPREY, HERON,
  FINCH, PLOVER, the couch's ending reads 100%, the waiting phone reads WAITING FOR THE HOST on one line.

**Checked** (every test XML read): model 298 (iOS Simulator) and 300 (JVM), core 326, harness 127, app
200, ui desktop 663 (+1 viewer), 0 failures; `vocabularyLint`, `redactionLint`, `armingCordon`,
`benchSeedCordon` and `seedSourceCordon` clean. **Not on a phone.**

### D-275 · **A beat's request slot carries every waiting request that fits, under one id, in the order made; the slot stays 532 characters and the upload 1 808 bytes — built, provisional, owner to confirm**

**What was slow** (D-263's cost, measured; `hunt.sh --seats 6`, `/tmp/someones-home-hunt/r6-final/b6-base*`,
`[slot-measure]` lines). D-263 drained a phone's requests **one a beat**, and every Subroutine entry,
BEGIN and STOP NOW is a request of its own. A burst of them queued up to 25 deep: `HandOverEntry`
p50 ≈ 9 s and max 24 s from press to the house's hearing it, BEGIN p50 ≈ 2.6 s and max 5 s, Static
≈ 3 s. Check-in, READY, the vote and the arm stayed within a beat. Nothing was lost; a Subroutine
that takes nine to twenty-four seconds to register is not playable.

**Options weighed.**

- **Coalescing entries** (one `HandOverEntry` carrying the entries so far): refused. Each
  `HandOverEntry` is a separate fact, `Event.SubroutineReturned`, graded once against the work the
  card opened (D-109); merging them changes what the rules are handed and what a recording holds,
  which is game behaviour, not latency. And it would fix one kind while BEGIN, STOP NOW and Static
  kept queueing behind it.
- **Packing the slot** (taken): as many queued requests as fit go up in one beat, inside the width
  the slot already has. Nothing about the wire's count, size or cadence moves.

**Now.**

1. **The slot holds a list** (`RequestSlot`). `id` · `length` · `payload` as before, the payload now
   the JSON of a **list** of `Asked`, in the order made; an empty slot is an empty payload. **The width
   is unchanged at 532 characters, so every upload is still 1 808 bytes**, measured on the encoded
   frame for lists of 0 to every request that fits.
2. **URL-safe base64 instead of hex for the payload**, for the reason D-263 chose hex: no layer of the
   wire escapes a letter, a digit, `-` or `_`, so the width is still the length. The same 512
   characters now carry **384 bytes** where hex carried 256, which is what lets four typical entries
   (≈ 89 bytes each) share a beat rather than two. The id and the length stay hex. A slot whose
   payload is outside the alphabet, or whose padding is not zeros, reads as nothing.
3. **The phone** (`SlotRequests`). Each beat with no slot out, the oldest requests waiting are taken
   **in order, for as long as the next still fits**, under one fresh id; the first that does not fit
   stops the list, so nothing is ever sent ahead of a request made before it (D-117: a lock never
   overtakes its selection). **A slot out keeps its id and exactly its list** on every resend until
   `UploadHeard` names it; a request made meanwhile waits for the next slot, because one added to a
   list the house may already have put would be dropped with the resend. A `SelectVote` not yet in
   a slot is still replaced by the next. A request too long for a slot **on its own** (384 bytes of
   JSON, less the list's brackets) is said in the phone's log and not queued (rule 6).
4. **The house** (`IntentDesk.slotted`, `LobbyHouse.attend`). The id is the dedup key, as before, and
   now names the list: the first time a seat's id is heard **every request under it is put to the
   rules, one at a time, in the slot's order, each awaited** (D-241 unchanged); every later time it is
   one `Duplicate` and nothing under it is put again. Then the sightings, then one `UploadHeard`.
   64 ids kept per seat across connections, unchanged.

**What an observer on the wire can count — checked, nothing moved.**

- **Up:** one upload a beat per phone, 1 808 bytes, whatever the slot holds — none, one, or as many
  as fit; the Insider's Static among four entries and a Resident's five entries included
  (`RequestSlotTest`, `UplinkFrameTest`).
- **Down, the answer:** one `UploadHeard` per upload, one length, unchanged.
- **Down, the rules' answers:** each request makes what it made before, to the seats it made it for;
  only *when* moves, earlier. An ability is still answered by `AbilityFired` to the actor and
  `AbilityBlanked` to every other living seat (D-263 item 8), so a Static that shares its beat with
  entries is one frame of one length on every living phone (`UplinkFrameTest` 3b). Entries are
  answered to the actor alone exactly as before, and an Insider's fake is graded identically, so two
  entries answered in one beat say nothing a Resident's two would not. A beat can now carry two
  abilities, and every living phone is sent two frames for it rather than one in each of two beats;
  every phone sees the same, and the count over the round is the same.
- **Timing:** D-263's raised item (the answer's burst follows the actor's upload) is unchanged, not
  widened: the burst still follows one upload, now sooner.

**Measured** (`hunt.sh`, wire-only Simulator seats, seed 20261150; press to `UploadHeard`, from
`[slot-measure]` lines a measurement build logged and this commit does not carry; the "one a beat"
column at three seats is the same build with the packing switched off by a launch value, so the
wire and everything else are identical):

| | one a beat | as many as fit |
|---|---|---|
| 3 seats, `HandOverEntry` | n 56 · p50 7.1 s · max 17.2 s | n 31 · p50 0.98 s · max 2.2 s |
| 3 seats, BEGIN | n 36 · p50 1.9 s · max 4.2 s | n 36 · p50 0.59 s · max 1.0 s |
| 3 seats, all | 114 asked, **21 never heard** before the round ended; p50 4.2 s · max 17.2 s · depth 18 | 68 asked, 68 heard; p50 0.61 s · max 2.2 s · depth 11 |
| 6 seats, `HandOverEntry` | (b6-base, b6-base2, D-263's code) p50 8.8 / 9.0 s · max 21.8 / 24.3 s | n 45 · p50 0.98 s · max 2.9 s |
| 6 seats, BEGIN · Static · Egress | p50 2.6 s · 2.8 s · 1.3 s; max 5.0 · 3.3 · 2.3 s | p50 0.53 · 1.0 · 1.0 s; max 1.0 · 1.0 · 1.0 s |
| 6 seats, check-in, READY, select, lock | within a beat | within a beat, unchanged |
| 6 seats, all | 145 asked, p50 4.6 s · max 21.8 s · depth 22 | 161 asked, 161 heard; p50 0.75 s · max 2.9 s · depth 9 |

The six-seat round reached ENDING on the same beats (+110 s and +111 s). A bench whose entries are
answered sooner hands over fewer of them and begins more work, which is why the counts differ. No
seat logged a slot it could not read or a request too long for one. **Not on a phone.**

**Guards, each proven by breaking it.**

- `SlotRequestsTest` (app): ten entries handed over at once are all heard, in order, once, within
  three beats; a request made while a slot is out waits and the resend is unchanged; a request that
  does not fit waits and nothing behind it goes first; the lock rides with its selection.
  *Injected (one a beat, D-263's drain):* `ten entries took 10 beats`, and three more failures.
  *Injected (the slot read by filter, so a smaller request behind one that does not fit goes first):*
  `two that do not fit together went together, or the small one jumped`.
- `UplinkFrameTest` 3b (app cheats; five real phones over loopback): the Insider presses Static and
  four entries, a Resident five entries, at once. All ten reach the house in **1 710 and 1 721 ms** (two runs) and are each
  heard once; every upload of the round is still 1 808 bytes on every phone, in every four-second
  window the phones' counts differ by at most one, every upload is answered by one frame of one
  length; every entry is graded once on either role, the grades one length; and the Static's frames
  are the same on every living phone (`AbilityFired` or `AbilityBlanked`, then `StaticOpened`: 128
  and 151 bytes net of the sequence digits). *Injected (one a beat):* `five requests from each of two
  phones took 4730 ms to reach the house`.
- `RequestSlotTest` (model): every prefix of a long run of requests that fits is one width and one
  1 808-byte upload beside an empty slot, and reads back in order; `fits` agrees with what `of`
  packs at every size around the edge; a payload outside the alphabet or with dirty padding reads as
  nothing. *Injected (the payload not padded):* `the upload's length says how many were asked: {0=1299,
  1=1358, ... 7=1808}`.

**Raised, not decided:** the work orders a seat is answered with differ in length by the seat's draw
(2 129 and 2 137 bytes in the test above), as D-258's table already records; batching does not touch
it.

| site | what it says now | what replaces it |
|---|---|---|
| `model/protocol` `RequestSlot` | one `Asked` in 256 bytes of hex | a list of `Asked` in 384 bytes of URL-safe base64, 532 characters still; `bytesOf`, `fits`, `fitsAlone` |
| `platform` `IntentDesk.slotted` | one landing per id | every request under a new id, in order; one `Duplicate` for an id heard before |
| `app` `SlotRequests` | the head, one a beat | every waiting request that fits, in order, under one id, frozen until answered |
| `app` `LobbyHouse.attend` | one request from the slot | each request in the slot, in order, each awaited |

**No recording change**: the slot is not recorded; the intents it carries are, as before.

**Checked for this revision** (every test XML read, on top of D-274): app 203 (debug) and the release
sweep 130, core 326, harness 127, model 300 (iOS Simulator) and 302 (JVM), platform 206, 0 failures;
every `vocabularyLint`, `armingCordon`, `benchSeedCordon`, `seedSourceCordon`, `redactionLint` and
`boundaryCheck` clean. **Not on a phone.**

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
device-proven: an iPhone 13 Pro was stopped mid-connection and resumed as its own seat against an
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
