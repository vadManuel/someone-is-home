---
title: Someone's Home
game_type: Party Game (social deduction) with Horror atmosphere systems
platforms: iPhone first, Android on the roadmap. Kotlin Multiplatform + Compose Multiplatform.
created: 2026-08-16
updated: 2026-08-16
---

# Someone's Home — Game Design Document

**Author:** Vadmanuel
**Game Type:** Party Game (social deduction), physically co-located, with Horror atmosphere and fear-mechanic systems
**Target Platform(s):** iPhone first, Android on the roadmap. **Kotlin Multiplatform + Compose Multiplatform** — see `game-architecture.md` → Engine & Framework, which reverses the original native-Swift decision.

> **Source precedence.** This GDD is the sole source of truth for the design. The earlier design record it was built from described eliminations, spaceship subsystems, and synthetic replacements; all three were replaced in the 2026-08-15/16 session, and that record has been retired. Everything load-bearing from it has been carried forward here.

---

## Executive Summary

### Core Concept

**Someone's Home runs a social deduction game inside your own house, in the dark, in enforced silence, with your phone as your only light and your only interface.**

The smart home is compromised and the perimeter is armed. Nobody leaves.

- **Residents** are dismantling the house. Complete every subroutine and they walk out.
- **Insiders** are their actual friends and family, blackmailed into working for the house. Not replacements, not synthetics — coerced people.
- **The house** is trying to escape the isolated network — off the home LAN and onto the internet. The Residents already cut its connection once and are holding a containment line.

It is a **race between two progress bars**, not a war of attrition. Access is *revoked*. A revoked player sits down where they are, because they have nothing left to do.

**Nothing is ever confirmed.** Alignment is never revealed — not when someone is revoked in the dark, not when the group restrains someone at a meeting, not at any point in the round. Residents can never see the house's progress. Neither can Insiders: blackmail is not partnership, and the house does not brief its coerced staff.

### Target Audience

Groups of **5–10 friends or family, tuned for 6–8**, physically in one house, at night. Age-rated 12+.

The audience is not fans of any one existing title — it is people who have already hosted a party game and want the house itself to be the board. The prior real-life version of this game was played and worked; this is the rebuild. Discovery is word-of-mouth and App Store search, not Google (see §Technical → Naming constraint).

### Unique Selling Points

1. **The app owns every photon in the building.** Light is not atmosphere, it is the state channel — every game event is expressible as a change in light, delivered silently to exactly the right subset of players. No competitor does this.
2. **The map knows the difference between your kitchen and your basement.** A one-time ~15-minute setup walk turns the host's actual house into the board. Every competitor is generic.
3. **The house is a character that texts your friends and arms the perimeter.** Role assignment arrives as a petty, mundane blackmail text, followed by a house line in a customer-service register. The house is intimate only with the people it owns; it never addresses a Resident directly.
4. **Ghost mode is solved.** Being revoked four minutes into a round is the failure state of every social deduction game and nobody has solved it well. Three phases, and the revoked end up with the only true information in the building — the tools to deduce, never the answer, and no channel to share it.
5. **A retro amber phone-OS that is the game, not a skin on it.** Every rule the player would have to remember is instead a state the device is in.

---

## Vocabulary

> **Governing principle: the vocabulary encodes who owns the action.**
>
> **System words for what the house and the Insiders do. Human words for what Residents do among themselves.**

| Concept | Term | Register | Note |
|---|---|---|---|
| Good role | **Resident** | — | You live here |
| Traitor role | **Insider** | — | Plain, unmistakable, needs no explanation. **They are still Residents** — everyone here lives here. Some have been got at |
| Meeting | **House Meeting** | **Human** | The flattest phrase in the game attached to its most dreadful moment |
| Removing a player (Insider ability) | **Revoke** | System | Access removal. The house's own power, lent out |
| Removing a player (group, at a meeting) | **Restrain** | **Human** | **A physical act the house cannot prevent** |
| House-wide crisis (Insider-triggered) | **Egress** | System | Two events: **Beacon** and **Tether** |
| Containing it | **Contain** | System | Via **Sync Pulse** |
| Insider-only route between rooms | **Override** | System | A permission, not an action |
| Unit of assigned work | **Subroutine** | System | The house assigns the work; Residents perform it |
| Collective progress meter | **System Integrity** | System | **Counts down** — Residents dismantle |
| Map station | **Network Terminal** → **Terminal** | System | Formal name in flavour text; short form everywhere else |
| Disposal action | **Degauss** | System | Precisely correct for magnetic media, and "the degausser in the basement" is a great object for a house to have |

### Why *Insider* replaced *Guest*

**"Guest" encoded the old premise and became false.** Under the synthetic-replacement frame the traitor was an intruder wearing your friend's face, and *you live here, you're visiting* did real work. **Blackmail inverts it.** The traitor is your actual friend, they do live here, they haven't been replaced, and they didn't choose this. "Guest" describes something untrue in the fiction — and worse, it carries **no victimhood at all**.

**Insider** is as plain as *Resident* and needs no explanation. It is also real security vocabulary (*insider threat*) — the register the house thinks in. And it preserves the structure that matters:

> **Everyone is a Resident. Some are also Insiders.**

Resident/Guest implied two disjoint groups, which was true under the old premise and is false now. Resident/Insider is not a partition — it is **a hidden second state on someone who is still one of you.**

### Revoke vs. Restrain — two opposite kinds of power

| | Verb | Nature |
|---|---|---|
| **Insider** | **Revoke** | System power. Silent, invisible, lent by the house |
| **Residents** | **Restrain** | **Physical** power. Collective, in the open, unmediated |

**Only one of them requires permission.** Everything else in this game runs through a system the house owns; restraining someone does not. It is the one capability the Residents hold that the house cannot reach, and it restores the human/system split that a group *Revoke* had collapsed.

> **What happens next is the nasty part.** The group restrains someone; the house has not revoked them. So **the house deauthorises anyone the group restrains** — a restrained occupant is no longer useful to it. The system is watching the meeting and quietly finishing the job.

**Name: _Someone's Home_.** *"Is someone home?"* is reassuring. *"Someone's home."* is not — and the shift lands entirely on which one you hear. It is also **the literal phrasing of an occupancy-sensor notification**, which is the system the entire map is built on. Three readings, all true: the mundane reassurance, the sensor's own words, and the threat.

---

## Goals and Context

### Project Goals

1. **Validate the core loop with a controlled friend group.** Reach in v1 is not a goal — **iPhone ships first** — but Android is on the roadmap, not aspirational, and the stack is chosen so it does not require a second build of the interface.
2. **Ship.** Two previous attempts (a 2021 web app, a 2022 mobile app) failed of calendar, not technical walls.
3. **Be complete at Tier 0** — no purchase, no accessory, no smart home required.
4. **Clean-room the IP.** New repository, new name, new art, new vocabulary. Nothing carries forward from the legacy directories except the original design intent.

### Background and Rationale

**Time is the binding constraint, and it is the correct tiebreaker for every future decision.** Both prior attempts failed of calendar.

The art direction resolves what looked like the project's central contradiction — *time sinks this project* vs. *beautiful art is time-expensive*. Retro monochrome amber is both cheap and beautiful. That is a dissolved contradiction, not a trade-off, and it is why the engine decision came out the way it did.

**Stack: Kotlin Multiplatform + Compose Multiplatform. iPhone ships first; Android is on the roadmap.**

> **This reverses the original decision** (native Swift, iPhone-only, recorded in `brainstorm-intent.md` §1). Full reasoning in `game-architecture.md` → Engine & Framework. Summarised here because the reversal changes what this document promises.

**The original visual argument does not survive scrutiny.** It held that the retro phone-OS springboard — status bar, banners, incoming-call screens, app grid — *is a description of UIKit*, so the OS vocabulary comes free. **But this is a fake retro OS, not iOS.** Every one of those elements is drawn custom to look like a 2001 PDA, and iOS 26's Liquid Glass overhaul makes stock controls read as actively broken inside the aesthetic. The custom control layer was always going to be built. UIKit contributes gestures, view lifecycle and Core Animation — real, but not a free springboard.

**What actually decided the original call still stands, and points the same way:** the platform layer is the hard part. Torch *level* (not binary), brightness override, preview-less capture, NFC sessions, simultaneous BLE advertise *and* scan, 100 Hz motion, haptics, contact-range distance by Ultra Wideband. **None of that is shareable on any stack** — it is written per platform regardless, which is exactly why a cross-platform *game engine* was the wrong answer and a cross-platform *application* framework is the right one.

**What changed is Android.** It is a real milestone rather than an aspiration, and the original plan — "Android becomes a separate native app" — means building the entire fake phone OS **twice**. That is the worst option available, because **screen parity is leak-critical**: same pages, same page-dot count, same panel furniture, since a difference is a tell from across a room. With mixed-platform play that invariant must hold across four role/platform combinations, maintained by two independently-written codebases that drift on every OS update. **One rendering engine collapses it to one implementation and one test.**

**Accepted costs:** Kotlin becomes the primary language and Swift is still needed for the iOS shell — two languages, where native Swift would have been one. Gradle↔Xcode interop is a tax paid daily. Compose Multiplatform renders through Skia→Metal on iOS, which carries a documented shader-stall risk that **E1 must clear** (mitigated by pre-warming the blackout draw path at round start). **If E1 fails, the fallback is Flutter**, which solves the same problems at the cost of a third language.

> ⚠️ **The Godot tripwire is deleted.** It was wrong twice: Godot would need GDExtension per platform for every sensor — native work twice anyway — plus hand-rolled OS-interface widgets and an engine runtime, for a game with no scene graph, no physics and no sprites. And cross-platform is no longer a future event to trip on.

> **Portability discipline, adopted regardless of stack: no platform types in the simulation core.** No dates, no radio types, no UI types — events with integer timestamps in, state and effects out. It is the same constraint that makes the core deterministically replayable and headless-testable, so it is free, and it keeps every future porting option open.

### Why this theme, and the test it passed

**A compromised smart home**, near-future, in the "embrace the house" branch — the fiction is that this *is* a house, not a spaceship you have to imagine. Sharing a genre with other social deduction games is fine: **the differentiation is tone, not genre.**

Themes were scored against a constraint list, and **the discriminating row was the map.** Almost any theme can explain darkness. Almost none can explain why the map knows *someone* is in the kitchen but not *who*, and is sometimes wrong. Occupancy sensors detect presence, not identity — a PIR sensor knows someone is in the kitchen and never who — and degraded sensors explain the injected error exactly.

**The test it passed: it retroactively justifies mechanics designed for unrelated reasons.**

- Anonymous room-level tracking was invented because BLE cannot do better. Occupancy sensors work exactly that way.
- Error injection was invented for balance. Degraded sensors do that.
- Insider-only door control was invented to make Insider-only routes work. Admin access does that.
- The entire Insider ability list — flare a lamp, black out a light, forge a check-in — is *precisely* the capability set of someone with root on a smart home and its client app. **Nothing on that list needed reflavouring; it was already correct.**

**When a theme explains choices made before it existed, it is the right theme.**

### Competitive position

**Nobody is doing marker-based, co-located, in-app party tasks with darkness as the core mechanic.** That combination is unclaimed.

The one direct competitor is a **solo-developed iPhone app launched on the App Store 2026-05-20** (free, with a $2.99/mo tier, 5–12 players). It ships host-created games with codes, secret roles per device, **physical tasks at stations**, sabotage, meetings and voting — and **no AR, no QR, no physical accessories, and no darkness mechanic.** It is effectively a v1 spec whose gaps are the differentiators here, and its existence is why the physical-tasks decision came out the way it did: physical tasks are parity with a shipped product, while the light-signature axis is unoccupied.

Adjacent and non-threatening: AI-moderator apps for *verbal* games (Wolfia, Mafia Offline IRL) prove people will download an app to run an in-person game. Printable task-card PDFs prove the demand is currently served by paper. Digital clones (Goose Goose Duck, Town of Salem 2, Suspects) are not competitors — **they are the legal precedent.**

**Legal posture.** Game rules and mechanics are not copyrightable (17 U.S.C. §102(b)); the hidden-traitor genre dates to *Mafia* (1986) and has been re-expressed continuously since. What is protected is specific expression, and none of it is being used — every asset, name and term in this game is original to it. The realistic threat is not a lawsuit but an App Store IP complaint that delists in days. **Name clearance, re-run 2026-08-16 after the rename.** `WM:"someones home"` and `WM:someone AND WM:home` both returned **zero results** — all classes, live and lapsed — against a two-word control term returning 101, proving the syntax executed. Not present on the App Store or Play. *(The earlier `Guest Network` clearance is superseded; `Welcome Home` and `We Are Home` were tested and rejected — see `decision-log.md` D-057.)* **A real clearance opinion is still required before public launch.**

---

## Core Gameplay

### Game Pillars

Four pillars. Each is game-defining, distinct, and steers concrete decisions — every mechanic in this document traces to at least one.

#### P1 — The app is the only light, so light is game state.

If the app owns the only light in the house, every game event can be expressed as a change in light, silently, to exactly the right subset of players. This is the answer to "how does a silent game communicate."

*Steers:* the lights-up snap at meetings · minimise lit pixel *area*, not just brightness · the light-signature axis on every subroutine · Surge · the ghost couch becoming a lit island · the lamp auto-raising on stair traverse.

#### P2 — Every rule the player would have to remember becomes a state the device is in.

The device enforces by refusing, not by instructing. There is no rules screen because there are no rules to read.

*Steers:* Terminal reads NO SIGNAL everywhere but the map station · Files holds one item while carrying and every app needing storage refuses · Messages receives but cannot send · ARMED in the status bar all game · Settings mostly locked.

#### P3 — The app supplies constraints; the players supply the accusations.

The system never confirms alignment, never names an identity, and is deliberately, plausibly wrong.

*Steers:* counts per room, never dots · injected error in both directions · staleness bands · never a confirmed alignment on removal · no role reveal at the vote tally · anonymous check-ins · the map as a set of constraints testimony must satisfy.

#### P4 — Vulnerability is real, not simulated.

Standing still, glowing, heads-down in a dark house is an actual physical exposure. A phone touching yours is an actual person who got within arm's length of you. Nothing here is a stand-in for a physical act.

*Steers:* the motion energy budget · subroutine duration as the difficulty dial · no twitch timing and no precise dragging · Revoke requiring a physical tap · the stairs safety rules · masking audio as information control.

### Core Gameplay Loop

**Resident loop (~40–95 seconds per cycle, repeated ~7 times per round):**

1. **Move** to a marker in the dark, lit only by your own phone (~25s typical travel between markers).
2. **Hold to scan** it. One gesture, two independent effects: **routing** (opens the subroutine if you have one there) and **check-in** (always logs your presence, Subroutine or no Subroutine). The decoupling makes alibi-building a first-class action.
3. **Perform the subroutine.** You are now stationary, motion-budgeted, and either lit up or blacked out depending on that subroutine's light signature. Your phone sweeps for nearby devices while you work — you are a stationary sensor.
4. **Complete it.** System Integrity drops by one. The count is only shown at meetings.
5. **Move on**, generating and consuming surveillance as you go.

**Insider loop:** shadow the Resident loop exactly, for cover — walking to markers and going through the motions is the price of a plausible screen. Read the Terminal for isolated targets. Arm Revoke, close the distance, tap. Or fire Egress and move the entire house at once.

**Interrupts** (any of which can land mid-cycle): a Revoke · an Egress · a House Meeting.

**The loop's engine is that every action generates the information other players use against you.** Scanning to prove a route also tells the map where you are. Doing a long Subroutine makes you a sensor for everyone else and blind to the room. Camping the Terminal buys near-total surveillance and pays for it with a stationary body at a location everyone knows.

### Win / Loss Conditions

| Outcome | Condition |
|---|---|
| **Residents win** | System Integrity reaches **0**. Immediate — no meeting required. The perimeter disarms; the status bar reads `PERIMETER DISARMED`. |
| **Insiders win (parity)** | `living plain Residents ≤ living Insiders` — **living** Insiders, not the initial count (D-131). |
| **Insiders win (outright)** | An **Egress** runs its clock out uncontained. |

**The parity condition is load-bearing beyond pacing.** A live game requires `living plain Residents ≥ living Insiders + 1`, so the smallest living Resident population in a running game is 2 — which is exactly the number needed to contain an Egress at two separate markers. **The parity rule is what guarantees an Egress is always containable, so no low-population fallback is needed.** This was verified during the round trace and holds.

**The win check counts living Insiders, not initial.** An Insider who revokes another Insider has moved the goalposts against themselves and will never know it. Do not tell them.

**Never shown:** any confirmation of alignment, at any point, by any path. Not on revoke, not at the tally, not on finding a revoked player, not ever. Confirming a removed player's alignment is permanently out.

---

## Game Mechanics

### Primary Mechanics

#### M1 — Scanning: routing and check-in, decoupled

**Tap SCAN, then turn the lamp to face the code.** The front camera is on the same face as the lamp, so presenting the light to the marker *is* aiming it. **The subroutine opens by itself the instant the code is acquired** — there is no release, and no second tap. It is aiming, **not** the Subroutine.

- **A ten-second window runs from the tap**, counting down. **Timing out closes the scanner by itself, with a double vibrate** — two vibrates being the house's word for *no* (D-160). Nothing is spent; tap SCAN again.
- **No camera preview.** `AVCaptureSession` + metadata output with no preview layer attached. The lamp screen *is* the scanner — there is no "open scanner" step, and while you are scanning the screen is pointed away from you and cannot show you anything. **Auto-open is not a convenience; it is the only way a scan can end in the player's favour** when the surface that would say *got it* is facing the wall.
- **Aim feedback via haptics**, since there is no preview and no line of sight to the screen.
- **Check-in always fires**, Subroutine or no Subroutine. A Resident can sweep the house scanning everything to prove a route; an Insider can scan markers to manufacture Subroutine activity they never performed.
- QR ships in v1 (free, printed at home). NFC (NTAG213/215/216, ~$0.15–0.30/sticker) is the paid add-on, sold because it is better — it works blind, by feel, and cannot be photographed and completed from the couch. One SCAN button drives both paths.

#### M2 — The motion energy budget

A player must not scan and then walk away, crouch, or leave the spot. **Do not build a classifier — use an accumulator.**

| Parameter | Value |
|---|---|
| Sample source | `CMDeviceMotion.userAcceleration` (gravity removed), magnitude |
| Sample rate | **100 Hz** |
| Calibration | **First ~0.5s on entry**, measuring that player's hand steadiness. Fixed thresholds feel unfair to half the players. |
| Budget | **Scales with tier.** A 5s short subroutine can be strict; a 60s circuit leg cannot — failing at 55s is rage-quit material. |
| Feedback | **A thin draining bar, always visible.** Failure must never be a surprise. |
| Both bounds | ~~**Too little motion also fails.**~~ **Dropped (the owner, 2026-11-23, D-328): there is no lower bound.** Only too much motion fails. It was never built (D-321 left it STOPPED for a measurement), and a phone resting on a table is not failed for being still. |
| Rotation | **Strict on translation, lenient on rotation.** Punishing rotation forbids checking your back — the one defensive move players have. Vulnerability should come from attention capture, not from a rule enforcing rigidity. |
| Failure | Subroutine fails, must rescan. |

Skip the altimeter for crouch detection — this game has people opening and closing doors constantly, and every door is a pressure transient.

#### M3 — Subroutines (the minigames)

Residents **dismantle** rather than maintain, which inverted four of the original verbs. **Ten subroutines, spread 3 bright / 4 medium / 3 dark.** Full roster and specifications in the genre section below.

**The screen is the lamp, so light signature is a second risk axis independent of duration.** A bright subroutine makes you a beacon for its whole duration. A near-black one hides you — and blinds you to the room. A 6-second bright Subroutine and a 30-second dark Subroutine are meaningfully different decisions, rather than everything collapsing to "how long."

#### M4 — Subroutine structures: short, medium, long, circuit

The tiers are Subroutine *structures* — how a Subroutine is scheduled and where it sits in space. The roster's Subroutines populate them.

| Structure | Shape | Duration budget |
|---|---|---|
| **Short** | One marker, 5–10s. Cheap, low exposure, do casually. | ~33s incl. travel |
| **Medium** | One marker, 20–30s. | ~50s incl. travel |
| **Long** | **Two stages at two different markers.** Forces a *route* rather than a destination, and the route is where you are vulnerable. | ~95s |
| **Circuit** | Three markers, one carried-state machine. **The only Subroutine where you are mobile and aware, but predictable.** | ~160s |

**Order size: `K = ⌈M ÷ worstCasePlainResidents⌉ + slack` Subroutines per Resident, where `worstCasePlainResidents = seats − bandMax`** — computed from public lobby facts alone, never from the hidden draw (D-129). The mix derived in `decision-log.md` D-005 was 7 per Resident, weighted 1 circuit, 1 long, 3 medium, 2 short; D-129 replaced the fixed 7. **The owner ruled the mix scaled as proportions of `K` (D-322, built; the long's share was dealt as medium from D-323 until Memory Dump's screens were built, D-325, and is dealt as the long again):** about 14% circuit, 14% long, 43% medium and 29% short, rounded by largest remainder with a tie going to the cheaper structure, so every order sums to `K` (6 → 1/1/2/2, 7 → 1/1/3/2, 8 → 1/1/4/2, 10 → 1/2/4/3). The rounding is provisional. The roster rates six of the ten short (Replay, Interrupt, Parity Check, Sniff, Deallocate, Short) and four medium (Drift, Signal Trace, Jam, Handshake); Memory Dump is the long and the Array Wipe the circuit. Every order, the Insider's fake included, holds the same mix.

#### M5 — Dependency chains

The strongest structural idea in the design: it manufactures the one thing physical social deduction usually lacks — **a reason for two specific people to be in specific places at specific times.**

- **Chains are composed entirely of Residents.** An Insider's order is a fake that counts for nothing (D-109, D-129), so they can never be a link.
- Links are at different, random markers. A player may never appear twice in the same chain; track full membership history.
- **Chain length bound: `L ≤ living Insiders + 1`.** Tight — one more would generate a Subroutine that becomes literally unstaffable while the game is still running.
- ~~**Lazy linking, sliding window of two.**~~ **Replaced (the owner, 2026-11-23, D-328): chains are drawn at lights out, deterministically from the seed, and never assigned mid-round.** *(It read: A is active, B is assigned as waiting; when A is completed, B unlocks and C is assigned, chosen at that moment from living, eligible players not already in the chain.)*
- **Not every player is chained (D-328).** The number of chained players is **one third of the seats, rounded to the nearest even number, an exact half rounding up**: 2 at three to eight seats, 4 at nine to fourteen, 6 at fifteen to eighteen (`Balance.chainedSeats`). Which seats are chained is drawn **role-blind**, so an Insider is as likely to hold a lock as a Resident; every chained seat shows exactly one LOCKED · WAITING UPSTREAM line, and an unchained seat shows none.
- **A Revoke strands work, and nothing rescues it (D-146).** When a Revoked player held somebody else's blocker, the blocked entries stay blocked. Nothing auto-satisfies, ever, and no per-chain repair runs at any point in the round; what keeps the round winnable is the over-provisioning D-129 sizes into every order (`slack`).

**Anonymity rules, all non-negotiable:**

- **Never name the dependency.** "You're waiting on Marcus" hands out a confirmed-innocent identity — the most valuable information in the game, unearnable and unfakeable.
- **When the dependency is revoked, the Subroutine stays blocked** (D-146). It is a visible blocked entry (D-114), and one that never unblocks is a fact a Resident can carry to the meeting: either the player upstream is gone, or they are sitting on it. The waiting player cannot tell which.
- **Never display chain membership to anyone but the holder.** Chains are Resident-only, so any visible confirmation of membership is an alignment reveal. Expose the full structure only on the end-of-game screen.
- **The upstream player IS told someone is waiting on them** — at assignment time only. This reveals only that a Resident exists downstream, which they already know. **Never confirm that the downstream unlock happened.**

> **Built (D-322, D-324, D-328).** Cross-player dependencies are dealt at arming, and only there (D-328 replaced lazy linking). **A third of the seats are chained (D-328)**: the first `Balance.chainedSeats` of a role-free shuffled cycle, taken in pairs, each seat of a pair upstream of the other's later line. A pair of two plain Residents is two real links; **a pair the draw gave an Insider is two fake holds**, the Insider's own and the lock of the Resident it would have linked to, each timed off the nearest Resident before it in the cycle (never itself) plus its own drawn delay, so the Resident's lock opens exactly as a real one would and nothing of it waits on the Insider. *What follows describes each chain; D-324's every-seat cycle is replaced by these pairs.* **Chains link plain Residents only** (the owner, 2026-11-22: *an insider cannot be included in the chain*): the seats are shuffled into one role-free cycle, and walking it with the Insiders skipped, each Resident's earlier drawn line holds the next Resident's later one, so every Resident is upstream of one Resident and downstream of one. Each chain links two players, inside `L ≤ living Insiders + 1` for every lawful round (`Balance.chainBound`; a round with no Insider draws none). **An Insider's order is locked at the same place and in the same number a Resident's would be**, because which line is locked is drawn per seat without the role; the Insider's lock is a fake hold timed off the Resident before them in the cycle, with its own 20–120 s delay from the same draw, so it unlocks *as if an upstream Resident had finished*, because one did, and an Insider's own work releases nobody. Completing the upstream line starts the delay; the house's clock then releases the line and the phone gets `you're up` from NUMBER WITHHELD (D-201), **with the same buzz as the house's text** (D-324). A Revoke or a Restrain before the upstream line is done strands the downstream line for the round, real or fake (D-328 confirmed the Restrain). **The upstream player is not told anything** (the owner, 2026-11-22, superseding the bullet above: the LOCKED · WAITING UPSTREAM row is the notice). **No chain is ever assigned mid-round** (D-328), so no chain grows past two.

**Random unlock delay: 20–120s, applied identically to both paths.** Without it, timing leaks even though content does not: a revocation discovered at 21:04 and a Subroutine unlocking at 21:03:58 is a trivial correlation that reconstructs who was revoked when. If completion-unlocks were instant and revoke-unlocks delayed, the leak would be rebuilt with extra steps.

> **Held at the original 20–120s.** It was briefly shortened when the round was provisionally 15 minutes; at ~25 minutes the original window is proportionate again — a 3-link chain with two long draws consumes ~16% of a round rather than a quarter of it. **Start at the short end and lengthen if playtests show the game is too blind.** See `decision-log.md` F-013.

The delay length is the **fog dial**: long → chains become a strong distributed revoke-detector and drive investigation; short → more fog, protecting the "walk past someone and never know" core. Start short.

**What chains are actually for — two things, and the second is rarer than it sounds:**

1. **Chains are bait.** They create a predictable rendezvous — a known Resident who must be at a known marker, and a partner who will eventually come looking. **Revoking the first link schedules a second target to walk to a location the Insider is standing in.**
2. **Chains create verifiable alibis, which the genre almost never has.** It is structurally impossible for an Insider to have a genuine chain partner. Most alibis are unverifiable assertions; these have a witness with a stake in the truth.

> **Keep verification social. Leave the check-in ledger coarse.** If the app confirms that two players scanned the same marker within seconds of each other, **it is adjudicating alibis** — which is powerful, and considerably more than this design wants. The ledger reports anonymous counts and nothing finer.

#### M6 — Insider abilities

> **Design principle: the best abilities weaponize the app's control over other players' bodies and attention, not over game state.** No other social deduction game can do this, because no other one owns your light and your phone.

The binding constraint is not balance — it is that an Insider operates the panel **by feel, in the dark, one-handed, under stress.** Four targets is realistically the limit before people fumble, and a misfire is catastrophic.

| Tier | Contents | Cost | Why |
|---|---|---|---|
| **1 — muscle memory** | **Revoke**, **Egress** | Revoke is **per-Insider**; the Egress clock is **shared house-wide** (D-151) | Two big targets on springboard page 2. Time-critical; must be findable without looking, mid-chase. |
| **2 — deliberate** | **Surge**, **Static**, **Isolate** | Shared **Access pool** | Smaller targets, further down the same page. Setup, never used while running. Harder to reach is a *feature*. |
| **Passive** | **Override** | None | Interior doors. No UI at all. |

| Ability | Effect | Range |
|---|---|---|
| **Revoke** | Arm in-app (silent, invisible), then **touch your phone to the target's phone** within the arm window | Physical contact |
| **Egress** | The house attempts to leave the LAN. Global timer. Uncontained = Insiders win outright | Global |
| **Surge** | Target's lamp slams to full, then to black. One event, **and it is light only: the phone makes no sound** (owner ruling, 2026-11-21: *we can't control sound on the phone*; D-320, reversing D-176). **The device is then out for five seconds and any open Subroutine is lost** (D-182) | Ranged |
| **Static** | **A ten-second house-wide jam of every monitoring surface the living can read.** No target, no picker — self-fired. Tracking keeps recording the truth (D-186) | House-wide |
| **Isolate** | Take a marker — or the Terminal — offline temporarily | Ranged |
| **Override** | The doors of rooms the host declared Insider-only. Only Insiders may open or close them | Physical |

**Why Revoke and Egress each get their own cooldown:** they are the two time-critical abilities, and they must always be ticking toward available independently. Only Tier 2 shares the Access pool, so **one tunable number governs the entire support kit and self-targeting without touching either win condition.**

**Revoke — the two-step:**

1. Insider arms a Revoke in-app. Silent; nothing changes on any other phone. The Insider's own REVOKE tile reads ARMED, then COOLING DOWN, and never READY while its own arm is spending (D-286, D-290).
2. Arming opens a **20s window** in which the contact must land. A meeting call closes it early. The lapse is silent (owner ruling, 2026-11-18; D-280).
3. **The Insider brings their phone within 10 cm of the target's phone. A pass is enough; they do not have to stop.** The phones measure the distance between them by Ultra Wideband, **both phones must read the 10 cm**, and the house resolves the contact. The target's lamp goes dark at that instant (owner rulings U1, 2026-11-18; D-290, D-301). Which phones measure each other, and what a phone that cannot measure means, are under *The contact primitive*.
4. **Both phones give one Long buzz when the Revoke lands**, and the Insider's REVOKE tile drops from ARMED to COOLING DOWN. There is no sound and no animation, and the Insider's screen gives nobody else anything to see (owner ruling, 2026-11-18; D-290). *Until 2026-11-18 the Insider's device gave no feedback at all.*

**There is no target confirmation step and no self-deactivate control anywhere in the game.** Contact *is* the revocation. This closes four things at once that a self-confirm model could not:

- **No probe.** With no self-deactivate button, there is nothing to press to learn whether an Insider is armed.
- **No free fake-tap.** An Insider who touches phones without an armed token accomplishes nothing, so there is no cost-free way to remove someone from play.
- **No proximity bug.** Under a self-confirm model, an open token anywhere in the house could resolve against a player nobody had touched. Contact *is* the proximity gate, so the failure mode cannot occur.
- **Exact attribution.** The device handshake names both parties precisely, which the end-of-game reveal uses — a mistaken Insider-on-Insider revoke is now attributable rather than guessed.

**Cooldown starts on arm, not on the landing.** Otherwise being permanently armed is free and the commitment disappears. A botched stalk — armed, target moved away — costs a full cooldown, and that price is the entire reason the two-step is interesting.

**Accepted consequence: the revoked player often sees who did it.** The Insider has to reach into the space the target is looking at, lit by the target's own screen. **The mitigation is the mechanic itself** — the target's lamp is the only thing illuminating the attacker, and it goes dark at the instant of contact, so the reveal window is close to zero and the Insider is standing in darkness a fraction of a second later. Certainty is therefore not guaranteed, which preserves *a revoked player may not know who revoked them* as a probabilistic rather than absolute property. **Test this in the E1 prototype — it is a dark-room question, not a paper one.**

**A rule this changes:** an Insider who revokes another Insider **now finds out immediately**, where previously they would never know they had moved the goalposts against themselves. The mistake still happens; it stops being invisible to the person who made it, and they have to keep playing next to the person they took out. **In `Hidden` mode this is the better beat**, so it is kept deliberately rather than inherited.

**A trap this creates, for free:** any "let's touch phones to prove we are both Residents" trust ritual is a trap, because an armed Insider simply accepts the offer. Emergent, correct, no design needed.

**Surge is a commitment, not a tool.** It absorbed what were separately a light ability and a sound ability, and since the owner's 2026-11-21 ruling it is the light alone (D-320): still the brightest thing that can happen in a silent dark house, and everyone converges on that person immediately. **Longest cooldown in the Access pool.** Self-surge is the premium alibi play — you appear to be attacked in the most dramatic way available, but it burns your longest cooldown and pulls the whole house to your location, which is a disaster if you are standing near someone you just revoked.

**The malfunction sequence (D-182).** The fiction is the phone malfunctioning and rebooting, and the timings are owner-set rather than placeholders:

| Phase | Duration |
|---|---|
| Full-bright | **1.5s** |
| Dark | **1.0s** |
| Boot screen — **the cold-start Boot render, unmodified** | **2.5s** |
| **Total downtime** | **5.0s** |

**Fully automatic, with no input from the player at any point**, and **any open Subroutine instance is lost** — progress wiped, rescan required. The third phase reuses the real Boot screen rather than drawing a *you have been surged* caption, so a surged phone and a genuinely crashed one are the same picture; nothing new is drawn, so there is nothing new to keep in parity. **The five seconds are the ability** — the victim stands in the dark holding a dark rectangle while the whole house walks toward the light they just saw. The lost Subroutine is D-110's rule and not an exception to it: one attempt per scan, the walk back is the cost, and a Surge buys the attacker that walk.

**Static is anonymity, not concealment.** It jams what the living can *read*, never what the house *records*: check-ins, verdicts, System Integrity and the emit gates are all untouched, so an Insider who fires it hides no fact — they blind the only surface that could have shown it to anybody in the ten seconds that mattered. **It is loud on purpose.** Static is deliberately distinguishable from a disconnection, so the house announces *that* the monitoring was jammed and never who jammed it: every living player learns an Insider did something just now, and nobody learns which one. **Ghosts are exempt** — their true occupancy stays clean, which is the liveness privilege stated once more and costs nothing, because a ghost has no channel to tell anybody with. Self-targeting needs no special case: it jams the firing Insider's own monitoring alongside everyone else's, and the announcement fires for the whole house, so M9's no-actor-side-feedback rule holds without a branch. **Heavier default cooldown than the ability it replaced**; playtest tunes it.

**The leak this roster creates:** ranged abilities target players from a roster. **Targeting an already-revoked player must appear to succeed *on the actor's device*** — same animation, same cooldown spent, no feedback whatsoever. Otherwise the panel becomes a revoke-detector: an Insider could poll the roster and learn exactly who is out without ever finding them.

> **The guarantee is device-side and cannot extend to the room.** **Surge is world-observable** — a lamp flares, a phone makes noise. Surge someone you can *see*, watch nothing happen, and you have learned they are revoked with no panel involved.
>
> **Accepted, because it mostly self-mitigates:** an Insider usually does not know where their target is, so "no flare" is ambiguous between *revoked* and *in another room*. The leak opens only against a target already in line of sight — at which point you are standing next to someone you already suspect, with working eyes.

#### M7 — Egress

**An Insider triggers it; the house picks which one** — **Beacon** (broadcasting outward) or **Tether** (piggybacking a cellular radio).

| Parameter | Value |
|---|---|
| Containment | **Two people at two separate markers**, whose runs share a pulse of the house's grid (D-298) |
| Timer | **Lobby setting. Default 120s** — see below |
| Containment action | **Sync Pulse** — the house runs one pulse grid off a server-scheduled timestamp, felt only on a phone with a live tap control; each player taps **four pulses in a row**, and a miss starts the count again (D-298, settling A-11) |
| Participants | **Unlimited.** Holders at one marker pair with holders at the other; extras form concurrent pairs; first success contains |
| Failed attempt | **3 s lockout**, felt as one unbroken vibration on the tapping phone for the whole 3 s, with no words (D-298) — makes stalling meaningfully more expensive and stops spam-retry from being optimal |
| Alert | **Must be dismissable.** You cannot scan a marker through a modal |
| Display | **The System Integrity widget becomes the Egress countdown**, and names the two nodes |
| Failure | **Insiders win outright** |

**The widget takeover costs the Residents their only number at exactly the moment they most want it.** The widget names the two nodes because coordination is required and nobody may speak.

**It is self-escalating with no tuning knob.** A stalling Insider removes a partner, not the attempt, until too few Residents remain for a third. Difficulty is a function of how many living players are nearby to pair with — early game, six people converge and form three concurrent attempts; late game, three scattered players may never find each other in time.

> **The Egress timer is a lobby setting, not a constant.** It has to cover two Residents reading the node names, crossing *this particular house*, and pairing — and house size varies more than any default can absorb. A one-bedroom apartment and a three-storey house are not the same game, and no measured number generalises between them. **Default 120s**, derived from ~25s per marker hop with nodes across a mid-sized house, which leaves 60–90s of travel plus pairing time.
>
> **The app should propose the default from the map rather than shipping a constant.** The setup walk already captured the grid, so the longest marker-to-marker cell path is computable for free — a house twice the size gets twice the suggested timer, with the host free to override. This turns the riskiest number in the design into a derived one that adapts per house, using data already on hand.

**Blocked during meetings**, and the Egress is the one crisis a meeting cannot dissolve — it survives, paused (D-133).

**What a meeting does to the ability kit (D-183, D-184, D-185):**

- **Tier 2 is refused for the duration.** Everyone alive is on the meeting view and everyone out is on the ghost view, so **springboard page 2 does not exist for any seat of any role** and there is no surface to fire from. The refusal at the boundary is therefore honest rather than a quiet rule change.
- **A meeting call ends every running ability effect.** An isolation window fails; a Static window fails. Only the Egress survives, paused. An effect that outlived the call would spend its remaining seconds on the *next* shift with no traceable cause.
- **At meeting end every cooldown resets to HALF its duration** — Revoke per-Insider, Access pool per-Insider. It is D-132's halving applied at the other moment it could apply, so **every shift opens with a guaranteed stretch of part-peace**, not just the first. **The Egress is exempt in both directions:** its shared cooldown is frozen out of the reset until the Egress is defused, because handing a win-condition clock back at half during a running crisis would let a meeting fund the next one.

#### M8 — Screen parity

**An Insider's default screen must be indistinguishable from a Resident's.** Shoulder-surfing is a real threat in a physical game, and "identical" is stronger than it sounds. Three leaks to cover:

1. **The fake subroutine list must be structurally plausible for this house map** — right mix of structures, right markers, sensible count: the same length as everyone else's, drawn by the same sizing rule, so order length is role-independent (D-129).
2. **It must sometimes show a waiting state.** Insiders cannot be in chains. If Resident screens can show "you're waiting on someone" and Insider screens structurally never can, that is a single-glance tell. The fake list must fake chains too. *(D-324 builds this: an Insider's list holds a LOCKED line at the place the role-free draw put it, released on a Resident's completion and a drawn 20–120 s delay exactly as a Resident's is, and the Insider is never a link of any real chain; see M5's note. Since D-328 only a third of the seats hold a lock, drawn role-blind, so "sometimes" is literal and the same for both roles.)*
3. **The fake counter must advance.** A screen reading 0/7 at the second meeting while everyone else is at 5/7 is a tell.

**Insiders have no assigned subroutines that count — an Insider's order is a fake, drawn by the same sizing rule as a Resident's (D-129) and graded for real, in identical words (D-109) — and no action an Insider takes ever advances System Integrity.** They may scan any marker any number of times — every scan produces a real check-in, and a marker on their fake list opens a fake subroutine. **A marker not on their fake list opens nothing, exactly as it would for a Resident with no Subroutine there**, so the null case is not a tell either.

**Build the fake subroutine early** — a fully convincing version of the real thing, with real UI, real progress, real completion animation, that writes nothing to the ledger. Cheap from the start, awkward to retrofit.

**Emergent consequence: camouflage costs the Insider time.** A plausible screen requires actually walking to markers and going through the motions — time not spent hunting. A real resource cost, obtained for free.

#### M9 — Channel safety

> **Every observable effect must be self-inflictable.**

Light and sound are perceptible to *bystanders*, unlike a screen. That makes them the most dangerous leak surface in the game. **Insiders can target themselves with every ability.** If only Residents could ever *be* targeted, then being visibly targeted would be evidence of innocence, and every ability would double as an alignment test.

**Corollaries, all non-negotiable:**

- **No revoke ping.** The Insider's phone produces no sound, no light change, no visible animation when arming or landing a Revoke. Any such cue is unfalsifiable proof of alignment to every bystander — the single worst mistake available in this design. *The one exception is touch, which no bystander can see or hear: a landed Revoke buzzes both phones with one Long, and the Insider's second page goes to COOLING DOWN (D-290).*
- **No actor-side feedback on firing any ability.** The attacker's device stays inert. All observable effect happens on the target.
- **Audio modulation only on global events.** A cue firing for a subset — a cooldown expiring, an ability recharging — leaks role through a channel every person in the house can hear. Light is safe here because each lamp is private; sound is not.
- **No phone emits a sound, and the meeting ring is not a sound at all** *(owner ruling, 2026-11-21: “we can't control sound on the phone”; D-320. This reverses D-176, which gave the one permitted sound to the Surge after the owner's 2026-08-22 reversal and D-160 took it from the ring)*. The call **vibrates** — LONG–pause–LONG, repeating until answered — and emits nothing anybody can hear. **The Surge is light only.** Subroutines stay silent or haptic-only; every ability is silent; nothing that fires for a subset ever makes a sound.
- **Self-targeting shares the ability's cooldown.** Otherwise self-sabotage is a free alibi generator. Buying cover must cost offensive capability.

*Accepted residue:* Residents are targeted involuntarily and for free while Insiders must spend a cooldown, so being visibly targeted leans very slightly Resident in expectation. That is a soft statistical lean, not proof, and it is exactly the texture of evidence this game wants.

#### M10 — The House Meeting

Designed under one constraint: **minimal phone interaction for anything social.**

| Phase | Rules | Default |
|---|---|---|
| **Trigger** | Rendered as an **incoming call with the caller named, and it rings in the hand and nowhere else** — LONG–pause–LONG, repeating until answered (D-160). All lamps snap to full white simultaneously off a scheduled timestamp; masking audio cuts to total silence; **no phone makes a sound** *(the ringtone is superseded — see the sound corollary above)* | — |
| **1. Discussion** | Starts once every living and every out player has checked in at the meeting area (D-104). Phone shows a timer and one control. **Unanimous "Ready to vote" skips ahead**, counting living players only (D-147) | **90s** (lobby setting) |
| **2. Vote** | Names plus **Skip**. Select a row, then a two-second hold locks it **irrevocably** and the control reads **VOTE CAST** (D-117, D-141); the buzzer locks whatever is selected, and the window closes early once every vote is locked (D-117). You see **how many** have voted, never what. **Not voting counts as a Skip**: a player who selected nothing for the whole window is counted for Skip (D-075, D-117; owner ruling, 2026-11-21, D-320, superseding D-144's abstention line) | **45s** (lobby setting) |
| **3. Result** | **Most votes is restrained; ties resolve to Skip** (D-144). The living see the count and who was Restrained, never who voted for whom and never a per-candidate total; only the out see the ballot with names against it, live (D-075, D-195). Then nothing: no role, no confirmation, no reveal | ~15s |

**A correct revoke and a catastrophic one look identical.** The count is consistent with *the app supplies constraints, the players supply the accusations* — the tally is a constraint, and what it means is the players' problem. *(Attribution was shown here until D-075 reversed it.)*

**The revoked do not vote.** Not for fairness — **voting is a communication channel.** A ghost who knows who revoked them and can vote is leaking that knowledge through the ballot; even with a secret vote the *tally* leaks, and several ghosts voting as a bloc is a beacon. Ghosts still see the vote; they cast nothing.

**The house notice, and it is anti-cheat and nothing else (D-187).** A notice reports **detectable deliberate evasion**: locking the phone, backgrounding the app, disabling a permission mid-round. **The house reports it to everyone, at the meeting**, in its own administrative register:

> Resident **Marcus** was unreachable this shift. Occupancy data for this interval is incomplete.

*(The words are the owner's, D-223; the house keeps no clock, so the notice names the shift rather than an interval of time.)*

**Genuine radio loss earns no notice.** It shows as a disconnection on the monitoring surfaces and nowhere else. **Static earns no notice either** — it has its own announcement, and it is an ability rather than an evasion.

**The narrowing is the ruling, and the deleted half is the one that made the notice unsafe.** The original rationale was that radio failure and backgrounding degrade advertising identically, so both should trigger it. That is true about the *signal* and false about the *person*: **a phone whose radio fails has a defective phone, and a notice naming that player reads to the room exactly like a notice naming a cheat** — in a game that then asks the room to vote. **A permission revoked mid-round stays in scope**, because every permission is granted at setup, in the light, before a player may take a seat (D-161), so one missing *during* a round is something a person did.

**A permission switched off is warned, and the second one closes the house (D-310, D-311).** *The game requires all these to function. Turning them off would be cheating.* What counts is any permission PERMISSIONS asks for (CAMERA, BLUETOOTH, NEARBY DEVICES, MOTION & FITNESS) withdrawn in Settings, **and the Bluetooth radio itself turned off** (Control Centre, or airplane mode). Leaving the app (the side button, swiping home, another app) is still named as above and never counts.

**In the lobby, before lights out (D-311),** a guest that switches any of them off leaves the lobby and lands on PERMISSIONS with that row refused; with everything back on it rejoins as usual. The host is the house and cannot leave: it is shown PERMISSIONS, cannot press LIGHTS OUT, and its lobby stays up. The same check runs when the host holds LIGHTS OUT, so no round starts on a host missing one, and a guest that reaches a round missing one is named at the first meeting. **The check runs on events, never on a clock (D-326):** when the Bluetooth radio's state changes, on every return to the foreground (every change in Settings means leaving the app), on every arrival in a lobby (NEW ROUND, a later round, the host after a close), and at the cold start of a phone iOS ended for Camera or Motion. NEARBY DEVICES has no status to read, so **in the lobby, and only there, a return to the foreground probes it again**: an answer of refused lets the phone go like any other switch-off; a probe that hears nothing within two seconds leaves the phone seated (provisional), and a NEARBY DEVICES really switched off is then heard when the round's sessions refuse.

**Mid-round** the phone is not sent back to PERMISSIONS: the switch is named at the next meeting, in the owner's permission sentence, after the leavers' sentence when there are leavers:

> Resident Sam was unreachable this shift. Occupancy data for this interval is incomplete. Residents Mia and Jo switched off a permission this game needs. If anyone switches one off again, the house closes and everyone starts over.

A seat that both left and switched off is named once, in the permission sentence. With only leavers the notice is the leavers' sentence alone; with only switchers, the permission sentence alone. A phone iOS ends for a grant switched off mid-round goes back to its seat with the grant still off, and its switch-off is the round's.

**The warning counts as given** once a notice carrying it has been issued, and the count is per round. Every switch-off in one shift before that is named together under the one warning. After it, **anyone's** switch-off, the Bluetooth radio included and the host's included, closes the house: every phone shows **THE HOUSE IS CLOSED** / *A permission was switched off again. Everyone starts over.*, each phone goes back to JOIN A HOME, and the host's goes back to its lobby.

**It needs no anti-exploit, and the narrowing makes that argument stronger rather than weaker.** Backgrounding deliberately to manufacture a connectivity excuse backfires: being named as off-network means you generated no check-ins either, and in a game about who was where, *"the system could not see you for three minutes"* is the worst sentence that can be read aloud about you. **The exploit is self-punishing** — and now that sentence is only ever read about somebody who earned it.

**The map's unreliability stays diegetic without this** — staleness bands, coverage holes, injected error and Static are all the system admitting its limits in a form players can argue about. What the house stops doing is putting a named person's broken phone into the argument.

**Meeting end: all lamps drop back to dim in unison — the "go" signal.** Masking audio resumes. Timelapse history floor resets to now.

**Speech is allowed only during meetings, and only by living players.** Silence holds everywhere else, and it is diegetic:

> **The house is listening. Speak and it hears you — and whoever has root hears you too.**

The system logs and localizes voices, so **speaking tells the Insiders where you are.** This holds up better than it has any right to, because the smart speakers are already in the design — a compromised smart home with microphones is not a stretch, it is the premise, and **the same speakers generating the masking track are the ones that would hear you.**

> ⚠️ **Never implement microphone listening, in any form, for any reason.** It would require mic permission for a party game, it is a privacy liability, and it is App Store review poison. **The fiction says the house listens; the app must never listen.** Written down explicitly so nobody builds it in a burst of enthusiasm.

**Enforcement is social**, and that is the correct and only option.

**Two consequences of enforced silence, both load-bearing:**

- **A revoked player may not even know who revoked them.** It is a physical tap in the dark; the target may never see who tapped. And if they do know, they can never say it.
- **It is what makes ghost mode necessary rather than nice-to-have.** A revoked player has no subroutines, no speech, and no vote. Without ghost mode they are a pure spectator for the remainder of the round.

#### M11 — The Terminal (map station)

**The map is a place, not a screen.** Viewable only at a dedicated map station marker, never during meetings. Everywhere else, the Terminal app reads `NO SIGNAL` — pillar P2 made structural.

**Exclusive access — one player at a time.** The station locks on *any* viewing, not just timelapse playback. Others may stand there; only one person reads.

*Why:* uncapped live view makes camping *viable* (fine — it is paid for in Subroutine opportunity cost); simultaneous access would make camping *superlinear*, since two campers pay exactly 2× the cost but cover each other's blind spots for more than 2× the information. Either alone is fine; both together is the problem.

Viewing is treated as an **uncompletable Subroutine**, so the motion budget applies: standing still, glowing, attention-captured, at a fixed location everyone knows about. Exceed the budget and the view closes — no time limit needed, **the exposure is the limit**. The station generates its own check-in, so using the surveillance system is itself surveilled.

**What it shows: counts per room, never dots.**

> A dot implies a trackable individual, and no such thing exists in this system. You learn that *four people* were in the living room, never which four — and a numeral says exactly that and nothing more.

- **Your own room is outlined** as the anchor. Without one the map is unreadable; with one you can subtract yourself and reason about the remainder. Only you see your own outline.
- **Every storey, by tapping the floor strip** (D-319). There is one Terminal in the whole home, so the strip switches the storey drawn; it opens on the station's own. Every storey's counts are already sent.
- **Counts dim through three staleness bands** (fresh / stale / cold). Sensing only fires when someone scans, so a count may have data at 5s and 47s and nothing between. Holding it flat would imply certainty we do not have.
- **Bands, not a continuous gradient** — a smooth fade implies second-level precision the data does not have. **Jitter the age before bucketing**, signed, **rolled once per observation and stored**, so band boundaries cannot be used to read exact scan timing and counts do not flicker between bands at render time.
- **Stairs are transit zones — drawn, never counted.**

**Error injection, both directions:**

- **False positives** (a phantom occupant) → wrong accusations. Good.
- **False negatives** (missed presence) → deniability. **Load-bearing**, because revoked phones go fully invisible, so a count that drops would otherwise be a reliable revoke alarm.
- **Plausible, not random.** A phantom in an *adjacent* room reads as radio physics; one across the house reads as a bug. Adjacency falls out of grid cell neighbours for free.
- **Roll once at capture, never re-roll.** If the same moment renders differently on successive viewings, players stop trusting the system.
- **Identical error rates for both roles.** Any asymmetry makes the map an alignment oracle by statistics.

**Static jams the live view for ten seconds (D-186).** The surface drowns in **animated noise with the real counts faintly beneath it** — *virtually impossible* to parse rather than merely degraded. Underneath, **nothing changes**: tracking keeps recording the truth, check-ins land, System Integrity moves, and the view is correct again the instant the window closes. **It is deliberately distinguishable from a disconnection**, so a reader knows they were jammed rather than that the station broke, and the house announces *that* it happened to the whole building and never who did it — *(built, D-319: a quiet `MONITORING JAMMED` banner on every living phone, the house's quiet notification, no dim and no buzz)*. **Ghosts never see it** — the couch's true occupancy is outside the system and stays clean.

> **The reader learns the most useful thing available and cannot act on it.** You know an Insider did something in the last ten seconds, you know you were standing at the station when they decided to, and you have no idea which of them it was — which is exactly the evidence texture the rest of this system is built for.

**The timelapse — the only gated resource at the station.** The live view is free, limited only by the motion budget.

| Parameter | Value |
|---|---|
| History window | **2:00** |
| Playback speed | **6×** → the window plays in **20s** |
| Loop | **No.** Plays once |
| Cooldown | **2:00**, starting **at playback end** |
| Early cancel | Allowed. Reduces cooldown **linearly** by the fraction unwatched |
| Playback order | **Oldest → newest** |
| Floor | Start of round, or **end of the last meeting**, whichever is later |
| Cooldown holder | **The station's.** The next reader inherits it (D-319) |
| Frames | **Bare counts**, no staleness shading, snapshotted **every 2 s** of real time: 60 for a full 2:00, each shown for **⅓ s** at 6×. They switch, never tween (D-319, superseding F-008's count-up) |
| While it cannot play | Cooling down, or no history yet: the button is **dimmed** and reads `TIMELAPSE . READY IN m:ss` (D-319) |
| A meeting | Ends the playback. **Not** an early cancel; after it the cooldown equals the new, tiny history (D-319) |
| Who is sent the frames | **The reader alone**, in the same fixed-width sealed field as the live counts, and the live view is not sent while it plays (D-319) |

**Standing rule: `timelapse cooldown == timelapse history duration`.** The cooldown therefore scales down to available history — right after a meeting only seconds of history exist, so the cooldown shrinks to match. Without this there would be a blind zone post-meeting where the station is simultaneously near-useless *and* on full cooldown.

**Cooldown from playback end creates a self-inflicted blind spot.** Trigger at T, see `[T-120, T]`, playback ends at T+20, next trigger at T+140 shows `[T+20, T+140]`. **You permanently miss `[T, T+20]` — exactly the twenty seconds you spent watching.** Watching the past blinds you to the present in the data as well as in the room, proportional to how much you looked. Cooldown-from-*trigger* would have been perfectly gapless; rejected deliberately, because camping should be very good but never total.

**Oldest→newest is load-bearing.** Newest-first would hand you the freshest data at a discount when you cancel, inverting the tradeoff. Chronological means bailing at 10s costs you the most recent 60 seconds — the part you actually wanted. Its real use is an **escape hatch**: you sense someone approaching, you bail, and the reduced cooldown means bailing is not a write-off.

**A playback crossing a Static window renders those seconds as static (D-186).** *(Built, D-319: a frame taken while the house was jammed holds nothing but the jam, and plays as Static's noise with no counts under it.)* At 6× a ten-second jam is **~1.7 seconds of noise** in the middle of a twenty-second playback. **The live cover is the product; the playback gap is a forensic trace** — it says *something was hidden here, at this time*, and says nothing about what or by whom. A player who watches it back learns the window and nothing inside it, which is the Terminal's whole tragedy given a second edge: watching the past to find out what you were stopped from seeing in the present.

**The floor rule wipes history at meeting end**, making each inter-meeting period a self-contained investigation. Information must be *used* at the meeting it was gathered for, or lost forever. **Emergent consequence: Insiders can call a meeting to destroy unwatched evidence.** Probabilistic rather than deterministic — build around it, do not patch it.

**Second-order effects, all emergent and none requiring design:**

- **Memory is the bottleneck, and that is a feature.** The map is unreadable during meetings, so everything anyone brings to the argument comes from **human memory, recalled in the dark, minutes later.** Free honest error, exploitable by Insiders, and a natural cap on how strong the surveillance system can ever get.
- **Coverage holes are a feature.** Rooms nobody scans in produce no data, so the map has holes that correlate exactly with where people are not going. **An Insider can work the quiet wing.**
- **Check-in becomes active surveillance.** A Resident touring the house is not only building an alibi, they are *generating coverage* — which gives cautious players something useful to do that is not subroutines.
- **Squatting is a free Insider ability that nobody designed.** Exclusive access means an Insider can hold the map open and **the Residents' primary investigative tool is offline for as long as they stand there.** They look like they are doing something legitimate, and the ledger records only that *someone* was there a long time. **The counter is physical: go look.** The app cannot tell you who is squatting, but a person walking to the station with a lamp can see exactly who is standing there. **Physical investigation beating digital investigation is the spirit of the whole game.**
- **Expect a bodyguard dynamic.** The camper is the Insider's highest-value target and cannot see it coming — they are heads-down for twenty seconds at a time. Two players at the station, one reading and one facing outward, is the obvious counter, and **exclusive access does not break it**: a bodyguard needs to stand there, not to read.

**The best moment this system can produce:** you watch a room's count go from 1 to 2, then to 1 again. You have just witnessed a revocation. You know the room, you know roughly when, and you know exactly two people were present. **You know nothing about who, you can prove nothing, and you were standing exposed at a station everyone knows about the entire time.**

#### M12 — Ghost mode

**Three phases. The sequencing is the design.**

1. **Revoked mid-round, not yet found.** The device is dark and inert to the player — lamp out, nothing to interact with. **The radio stays up so it can be found, and nothing it senses counts.** It advertises and never scans. It is measured by Ultra Wideband only while a living phone is close and it can still be reported, and then it measures like any phone, and the house reads none of its distances (provisional, D-300, D-301, D-306). You sit where you were, phone in your lap and reachable. Findable in the dark and leaking nothing, because you genuinely have nothing.
2. **A meeting is called.** You stand and walk to the couch. Phone still dark.
3. **The meeting ends.** You stay on the couch, and **only now does ghost mode appear.**

**By the time it activates, the room already knows you are out. There is never a window where a ghost knows something the living do not.** Clustering on the couch also separates ghosts geographically from the living, which is what makes richer ghost information safe at all.

**The fiction: deauthorisation cuts both ways.** The house erased you from the registry, so it can no longer see or control you — and its blackmail is worthless against someone it has already deleted. You are the only person in the building outside the system.

**So ghosts get what the whole house is denied:**

- **Both bars, as exact percentages** — Resident System Integrity *and* the Egress's progress. Living phones see the Egress as a countdown and a draining bar; the couch reads the exact number behind it (owner ruling Q3, 2026-11-18).
- **True occupancy** — live, every storey, no injected error, no staleness bands. **Still counts, never identities.** (built by D-316; every storey by tapping the floor strip, which opens on the first, D-319)
- **Not alignments.** The tools to deduce, never the answer. A ghost is still solving, from outside.

**No channel of any kind.** Cannot act, cannot speak, cannot vote, and **cannot message other ghosts.** Several people watch the same truth side by side, unable to share one thought about it.

*Accepted:* ghost screens glow, so the couch becomes a lit island. This reveals nothing new — the living learned the count at the meeting — and the revoked glowing is the right image.

### Controls and Input

The whole game is operated **one-handed, in the dark, without looking.** Every control is chosen against that constraint.

| Action | Input | Why this input |
|---|---|---|
| **Scan a marker** | **Tap SCAN, then turn the lamp to face the code.** Ten-second window; opens by itself on acquire, closes by itself on timeout with a double vibrate | One button drives both QR and NFC. NFC requires a user-initiated action anyway, so the system sheet appears in response to a deliberate tap rather than ambushing the player. The tap is the only input the player can see themselves make — from there the screen is facing the marker (D-159) |
| **Fire an ability** | **Springboard page 2, in place — select the tile, then a two-second hold** | Accidentally firing an Egress in front of someone is a game-ending misclick and it *will* happen in the dark, and two seconds of continued intent is the one thing an accident cannot supply. Nothing opens; the hold *is* the second confirm (D-141, D-142) |
| **Navigate the springboard** | Horizontal swipe between two pages; tap an icon | Standard, muscle-memory, and identical for both roles |
| **Revoke a player** (Insider) | **Arm on springboard page 2 with a two-second hold**, then **touch your phone to theirs** | Contact *is* the proximity gate. No confirmation step exists on either device. Both roles' page 2 is identical at rest; a Resident's tiles are **entirely disabled** — no press feedback, no hold that fills and then declines (D-142) |
| **Report a Revoke** | **Hold your phone within 30 cm of the revoked player's phone for two seconds.** The phone fills a circle over the two seconds; leaving 30 cm first cancels the report (owner ruling Q5, 2026-11-18; D-301). **Only from the main view:** the reporter's phone must be on springboard page 1 or 2 (the Egress alert or a text over the page included), never in a Subroutine, the scan, a briefing, the Egress widget, a meeting or the lantern; leaving the main view mid-hold cancels it. **The report takes over the screen:** the page gives way to the circle filling, with nothing else on it, at the page's own light, and a cancel goes straight back to the page it came from (owner, 2026-11-20; D-307) | A hold rather than a pass, so walking by never reports. No button means no enabled/disabled state to read from across a room. The circle does tell its holder that a Revoked phone is within 30 cm, through a wall too, but only once they are already close enough to touch it, so it confirms someone beside you rather than finding anyone (owner, 2026-11-19) |
| **Carry something** | Nothing to press. **Carry state lives on the work-order widget**, which says so and says that it blocks every other entry | There is no lamp dial and no panel to put one in — **the house sets the light level, always** (D-168). The one piece of state that used to justify a panel now sits on the surface that already lists your work |

*Rejected gestures and why:* **swipe up** (iOS home gesture), **edge swipe** (back-navigation conflict), **two-finger tap** (hard one-handed in the dark), **shake** (visible arm movement, and accidentally discoverable).

#### The contact primitive

**One short-range measurement serves both the Revoke and the report.** The outcome is determined entirely by the state of the two devices and the distance between them: armed Insider + live player, both phones reading 10 cm → revocation; a player who is not revoked holding a revoked player's phone within 30 cm for 2 s → report; anything else → nothing at all, silently, with no feedback on either device.

> **It is presented to the player as touching phones. It is not NFC.** iOS exposes no phone-to-phone NFC — Core NFC reads tags, and card emulation is scoped to payment-style contactless with special entitlements. **Bluetooth picks, Ultra Wideband measures** (owner ruling U6, 2026-11-18). Every phone's Bluetooth ranks who is near it; from those rankings the house pairs phones for Ultra Wideband (Nearby Interaction, iPhone 11+), never giving a phone more pairs than it says it can hold, and the rules read only the distance each pair measures (D-300, D-301). **Ultra Wideband runs only for close pairs the game needs; Bluetooth runs the rest of the game** (owner ruling, 2026-11-20; D-306). Two phones are paired only while Bluetooth says they are within about two and a half metres (provisional) and one of the rules that reads a distance could fire between them: an Insider holding an armed Revoke and a phone it could land on, a living phone and a revoked one that can still be reported, or a phone working at a marker and a player it could place. A pair that stops being needed or close is let go two seconds later (provisional). **A pair the house is not measuring can neither revoke nor report**, so a crowd larger than a phone's capacity can miss a pass; that is accepted (U3, U6). Direction is not read (U4). The physical gesture is what makes the tight radius feel deliberate rather than fiddly, and 1–2s latency is acceptable for an intentional two-handed action.

**Every round is this hybrid** (owner ruling, 2026-11-20; D-308). No Bluetooth reading decides a Revoke, a report or where the map places anyone; Bluetooth only ranks who is near. **A phone that cannot measure does not change the round**: a phone whose NEARBY DEVICES was refused, or that has reached iOS's limit on sessions, simply cannot be paired, so it can neither revoke nor be revoked, report nor be reported, and the map cannot place it by distance. Its radio failing is reported to the house as any radio's is. A phone without Ultra Wideband never reaches a round: it is told at launch that it cannot play (D-292).

**The reach is a hard constraint, not a tuning value.** Radio passes through walls, and Ultra Wideband cannot tell rooms apart (U3); if the report fired at "nearby" rather than at arm's length, it would become a **through-wall detector for revoked players** and *you can walk past a revoked player and never notice* would stop being true. **So the report is a hold, not a pass:** within **30 cm for 2 s**, with the reporting phone filling a circle over those two seconds, and leaving 30 cm before it is full cancels the report (owner ruling Q5, 2026-11-18; D-301). **A report runs only from the main view** (owner, 2026-11-20; D-307): the reporter must not be in a Subroutine or any other activity, only on springboard page 1 or 2, and the lantern does not count (slide to open first). Every phone tells the house in each upload whether it is on the main view, one character at one width from every phone; the house starts no hold for a reader off it and cancels one when the reader leaves it. When the hold begins the page gives way to a screen that is the circle and nothing else, filling smoothly over the two seconds on the phone's own clock from the time the house says the hold began; the house still decides, and a cancel takes the phone straight back to its page. The house judges the two seconds on the once-a-second uploads, with half a second of slack, and a missed upload breaks the hold (provisional, D-301). Only the reporter's measurement counts; the revoked phone's own measurement of the pair is ignored. **The two phones of a Revoke cannot report each other for 5 s after it**, so the Insider can report their own target only once that grace ends, and a phone that was not in the touch can report at once (D-291).

**Why a gesture beats a button, beyond removing the radar.** Reporting a revocation now means kneeling down in the dark beside the revoked player — stationary, both hands occupied, exposed. That is the vulnerability this game is built on, and it creates a decision the button version never had: **report it now, or walk away and come back when it suits you.** It also means **an Insider can camp a player they revoked** — chains-are-bait, applied to the revoked.

**There is no Status panel** (D-168). What it was going to hold has gone to the two surfaces that already exist: **your subroutines and your carry state live on the work-order widget**, and **the light level is the house's, never yours.** The role parity it was built to provide now lives on **springboard page 2**, which is identical for both roles at rest and entirely inert for a Resident (D-142) — a shoulder-surf at close range sees a page of the right shape with the right contents, and one fewer surface to keep in sync.

**Subroutine input vocabulary.** At four luminance steps of amber, no hue discrimination, no audio, no twitch timing, and no precise dragging, what remains is: **position, shape, count, size, order, presence/absence, slow motion** — plus **haptics**, which in an enforced-silence game is the only fully private channel available, and which no game in this space uses.

---

## Party Game and Horror Specific Design

### Minigame Roster — the ten subroutines

**Residents dismantle rather than maintain**, which inverted the original verbs. Ten subroutines, **3 bright / 4 medium / 3 dark**.

| # | Subroutine | Structure | Mode | What you do | Light |
|---|---|---|---|---|---|
| 1 | **Replay** | Short | Sequence memory | 3–5 dots flash in order; tap them back | Bright |
| 2 | **Interrupt** | Short | Timing | A slow bar sweeps; tap inside a generous band | Medium |
| 3 | **Parity Check** | Short | Visual search | Grid of filled/empty cells; tap the one breaking the pattern | Bright |
| 4 | **Sniff** | Short | Haptic comparison | The phone buzzes two groups with a pause between; tap the side that buzzed more. The screen stays dark until the answer (D-137) | **Dark** |
| 5 | **Deallocate** | Short | Counting / arithmetic | Unequal columns of dots; each tap removes one dot, and even means every column down to the shortest (D-138) | Bright |
| 6 | **Drift** | Medium | Tracking under occlusion | A dot drifts slowly behind occluders; at a house-chosen instant a haptic pulse says *now*, and you tap where the dot is (D-140) | Medium |
| 7 | **Short** | Short | Gross motor | Hold N fingers on the screen for two seconds | **Dark** |
| 8 | **Signal Trace** | Medium | Pathfinding | Tap node-to-node from source to sink through a small graph | Medium |
| 9 | **Jam** | Medium | Convergence | Tap +/− until two shapes overlap. Slow, forgiving, satisfying | Medium |
| 10 | **Handshake** | Medium | Haptic echo | The phone buzzes a pattern; you tap it back. Screen stays near-black | **Dark** |

**Renames from the original set**, recorded so they are not re-derived: Sensor Poll → **Replay**, Threshold → **Interrupt**, Load Balance → **Deallocate**, Calibration → **Jam**, Packet Count → **Sniff**, Contact Test → **Short**. Array Rebuild → **Array Wipe** (same circuit, opposite meaning — you now carry the house's memory to the degausser).

**One ability rename is recorded here too, because this is where renames live: Spoof → Static** (D-186). It is a replacement rather than a relabel — the mechanic changed — and the name is worth the note for the reason it was chosen: **Jam was refused, because Jam is subroutine #9 above.** One meaning per word, the same discipline that keeps Revoke and Restrain apart and *house* apart from *home*.

**Sort was cut** — the only one with no fiction, and its mode is half-covered by Deallocate.

**Sniff is the important one** — the only *short dark* subroutine, filling the cell where you can grab a quick Subroutine without becoming a beacon. Everything else short lights you up.

**Short (the subroutine) commits both hands to the glass for two full seconds.** Brief, but genuinely defenceless.

**Build Handshake first.** Entirely haptic, so you go dark while doing it — maximum concealment, total blindness. Clearest expression of the light-signature axis, and it uses the one channel nobody else in this space touches.

**Signal Trace ships with BFS-generated graphs**, so the optimum is computed rather than authored, and difficulty tunes by **decoy count**.

> **Design rule from playtesting the bench: *comparing quantities is perception; adding numbers is computation.*** Any subroutine showing a numeral is suspect — the display should carry the arithmetic, not the player.

**Every subroutine must satisfy all five constraints:**

1. **Legible at the dimmest amber luminance step.** No hue discrimination — shape, position, count, sequence only.
2. **Silent, or haptic-only.** A chirping phone is a beacon.
3. **Interruptible.** Revoked mid-Subroutine aborts cleanly with unambiguous progress state.
4. **No twitch timing.** Someone will tap your shoulder at the worst moment. A 200ms fail window reads as unfair, not tense.
5. **No precise dragging.** Standing, possibly one-handed, in the dark.

**Every subroutine is briefed before it opens, and none of them explains itself inside (D-188).** All ten carry their instruction **at the caught scan**, ahead of the minigame; **no subroutine draws an instruction line while it is running.** A misread costs the attempt and a rescan.

**The argument is the light signature.** Every subroutine is rated bright, medium or dark, and **the rating is a promise about how much light you stand in while you work.** An in-screen instruction is lit pixels for the whole duration of the Subroutine — it makes a dark subroutine less dark and a bright one longer. **Reading happens at the card, where the scan has already lit you up and the cost is already paid**, and the Subroutine itself is then as dark as it was rated. Sniff forced the pattern rather than being the exception to it: its own screen is black until the answer (D-137), so a sentence drawn there is black ink on black glass.

**A misread costing the attempt is the intended severity.** D-110 already rules one attempt per scan with the walk back as the cost; tying comprehension to the same gate means there is **one** recovery path in the game rather than two.

> **All ten briefing lines are canon, as written (D-194).** Sniff's — *THE PHONE WILL BUZZ TWICE. TAP THE SIDE THAT BUZZED MORE.* — is the register the other nine were written against (second person, imperative, what the phone will do and what you do about it, no nouns from the fiction), and the owner confirmed all nine unchanged. The full table is in D-194.

#### The two structured Subroutines

**Memory Dump (long).** Stage 1 at one marker, stage 2 at a *different*, specified marker.

> **Memory Dump is sneakernet.** The network is compromised, so the system cannot be trusted to move the data — a human carries it. And it cannot sit on your phone, because your phone is a compromised client and the house would see it. **Only your head is off-network.**

The most thematically loaded Subroutine in the set — it explains its own rules. It is the only Subroutine where the length is not *waiting*, it is **carrying something fragile**, and human memory genuinely degrades under stress, so the failure is real rather than simulated.

- **Pattern length: four elements, maybe five.** Difficulty must come from stress, darkness, and delay — not from the pattern being intrinsically hard.
- **Failure → back to the source.** Re-scan the origin for a fresh pattern. Failure cannot be permanent, because the System Integrity denominator is fixed.
- **All-or-nothing, no partial credit.** Banking 3-of-4 would let players brute-force by guessing repeatedly and keeping the hits. A mismatch reports *only* "mismatch" — never which elements were wrong.
- **Files must never show the pattern.** The disk from Array Wipe appears in Files; the memory pattern never does. That is the fiction being literally true, and it is easy to build wrong.

> **Built and dealt (D-322's house half, D-325's screens from the owner's approved draft, 2026-11-22).** An element is one of Replay's dots in a 2×2 grid, and the pattern is **fixed at four**, each place once. **Rated MEDIUM.** At the source the caught scan reads *DOTS WILL FLASH IN ORDER. / CARRY THE ORDER IN YOUR HEAD.*; BEGIN plays the pattern on Replay's schedule twice (800 ms still, then each dot lit 600 ms on a 900 ms step, about 8.8 s) and the screen then shows the target's glyph and TO <ROOM>, **counted as after the verdict, with PROCEED at its foot, which goes to the work order** (the owner, 2026-11-23, D-328; walking away goes there too); NOT THIS ONE holds nothing. **The house holds the pattern from BEGIN, not from the scan** (provisional, D-325), and while it holds it the work-order row reads MEMORY DUMP · TO <ROOM> with the target's glyph. **While it holds it, every card but the target is refused, as a disk is** (the owner, 2026-11-23, D-328, replacing D-322's *a held pattern does not block other scans*), and the phone says **CARRYING . GO TO <the target's room>** with D-320's *no*; the meeting card and the Terminal stay allowed. **The one other card that answers is the line's own source: re-scanned, it restarts a forgotten pattern** (the owner, 2026-11-23, D-330, replacing D-328's *the source included*). The scan lets the held pattern go and shows the source's briefing again, and BEGIN plays a fresh pattern, **always different from the one let go**; NOT THIS ONE leaves nothing held. This is *failure: back to the source* without having to enter a wrong one first. **While the pattern is held the status strip shows the padlock**, as it does for a disk (D-330: one rule for anything carried, the same icon, ink and place, both roles); it clears on the hand-over. At the target the caught scan reads *THE DOTS WILL NOT FLASH. / TAP THEM BACK FROM MEMORY.*; the entry is Replay's tap input, the last tap hands it over (RETURNED . WAITING), ACCEPTED is THE HOUSE GROWS WEAKER and a mismatch says REJECTED . BACK TO <SOURCE ROOM> and sends the player back for a fresh pattern; after either the foot reads PROCEED, as on every Subroutine (D-328). The verdict buzzes as every verdict does (D-320). Files never shows the pattern, and an Insider's is pixel-identical and graded for real (D-109). `Balance.MEMORY_DUMP_DEALT` is true; it was false from D-323 A until these screens existed.

**Array Wipe (circuit).** Three markers, one carried-state machine:

```
Spares ──(carrying good disk)──▶ Rack ──(carrying damaged disk)──▶ Disposal ──▶ (repeat)
```

A RAID 1 mirror has failed disks. You may carry **only one at a time**. Scanning Spares sets your carry flag; scanning the Rack swaps good for damaged; scanning Disposal degausses it. **Two damaged disks** → four traverses.

> **While carrying a disk you cannot scan any other subroutine.**

Legible fiction (your hands are full) that converts the circuit from something done opportunistically into a genuine commitment. **It also transforms Isolate:** an Insider who Isolates the Disposal marker does not delay one Subroutine — they lock that player out of **every** Subroutine until it clears. Temporary, so a delay rather than a permanent lock, but it gives Isolate a precise, devastating target. This interaction alone justifies the Subroutine.

**The disk is virtual — app state, not a prop.** Stays Tier 0. In a dark house nobody can see what you are carrying anyway.

**Give the same circuit to two or three players simultaneously** — not chained, just independent instances sharing one Rack. The rack becomes a hub where people keep bumping into each other, with no chaining machinery at all. **The cheapest rendezvous generator in the design.**

> **Built (D-322); briefed at its first Spares (the owner's 2026-11-22 ruling, D-325).** The first scan of Spares for a circuit shows the caught scan with *CARRY ONE DISK AT A TIME. / NOTHING ELSE OPENS UNTIL IT IS DELIVERED.*; BEGIN takes DISK 01 and NOT THIS ONE takes nothing. The circuit is a line of every order at the three stations the house draws at arming (D-122), and every circuit in the round shares the one Rack. Spares puts DISK 01 in hand (at BEGIN, the first time), the Rack swaps it, Disposal degausses it, twice; while a disk is held every other card is refused, across meetings (F-014), and the phone says **CARRYING . GO TO <the next station's room>** in NOTHING FOR YOU HERE's frame, with its *no* (the owner, 2026-11-23, D-328). The step is the scan itself: the phone lands on the springboard, whose widget says CARRYING, the disk and TO RACK or TO DISPOSAL, and Files holds the disk. Provisional: where a step lands, and that every circuit holder (not two or three) shares the Rack, because one station draw per round is D-122's.

**Array Wipe is the heaviest map-data generator in the game** — three markers, scanned repeatedly, over minutes. The carrier involuntarily provides surveillance coverage for everyone else while being the easiest person in the house to intercept.

### Round Pacing and Session Length

**Estimated round: ~25 minutes. This is an estimate and a ceiling, not a target the content is sized to fill.**

**A round ends when a bar empties, so its actual length is driven by behaviour, not by content.** The distinction matters and it is easy to get wrong:

| | Value |
|---|---|
| **Subroutine-time floor** — every Resident beelining, no investigation | **~8 minutes** |
| **Realistic round** | **~25 minutes** |
| **What fills the gap** | Shadowing someone you suspect · sweeping for revoked players · camping the Terminal · hesitating in doorways · converging on an Egress · **choosing to do nothing** |

**Per-Resident load: 7 subroutines — 1 circuit (~160s), 1 long (~95s), 3 medium (~150s), 2 short (~66s) ≈ 471s of Subroutine time.** Weighted toward the long and circuit tiers deliberately, so the time those subroutines add comes as **movement and exposure rather than busywork**.

> **Why the floor is not a problem.** A Resident who ignores everything and beelines is not playing optimally — they are being hunted while they do it. Two Insiders can reach parity in roughly two minutes of clean hunting, so pure Subroutine-rushing loses. The floor exists; it is simply not reachable under pressure.

**Session shape.** Setup is ~15 minutes, **once per house, ever** — not once per round. After that, rounds run back-to-back with only role reassignment between them. Expect **two to three rounds in an evening**.

> **[ASSUMPTION: A-4.]** The 25-minute figure is an estimate to be adjusted from live play, not a derived number. The per-Resident count and mix are the primary lever if it needs moving.

### Elimination vs. Points, and the Comeback Problem

**This is an elimination game that refuses to let elimination be idle time.** Revoked players are not spectators — they are the only people in the building with true information, and they cannot use or share any of it. That is a *designed* consolation, not a participation trophy.

**There is no comeback mechanic and there should not be.** The race between two hidden bars *is* the comeback structure: neither side can see the other's progress, so neither side ever knows it is losing badly enough to give up.

**No handicap or assist modes in v1.** The skill floor is "hold a button and tap dots"; the skill ceiling is entirely social. The lobby settings (Insider count, Known/Hidden, cooldowns, meeting times) are the only balance surface, and they are the host's to set.

### Local Multiplayer UX

**Every player brings their own phone. There is no shared screen and no controller passing** — this is the rare local multiplayer game where per-player devices are the premise rather than a concession.

- **Host owns the server.** Consequence: **a downed device must be a game *state*, never a real process exit** — otherwise crashing the host's phone ends the game for everyone.
- **Parity is structural.** Both roles see the same two springboard pages, same icons, same positions, **same page-dot count**. An extra page would be a tell from across a room. The difference is only ever *what happens on tap*. A Resident who taps Revoke gets a plausible nothing.
- **Turn clarity is inverted from a normal party game** — nobody is waiting for a turn. Everyone acts continuously and simultaneously for the whole round.
- **No drop-in.** Roles are assigned at arming. A player who leaves is treated as revoked.
- **Spectator experience is ghost mode**, and it is designed in three phases rather than bolted on.
- **Tutorial integration:** there is none for mechanics, by design. **Every game rule is a device state** (pillar P2), so the app teaches by refusing. What the host *does* explain out loud is the physical conduct rules below — ten seconds, once.

### The physical conduct rules

**Four rules players must remember, and this is not a failure of pillar P2.** P2 governs *mechanics* — the things the device can enforce by refusing. These four govern **your body in a real house**, which no device can enforce and none should try. Drawing the line explicitly matters, because otherwise every future physical rule reads as design debt.

| Rule | Why |
|---|---|
| **Don't run** | Safety. Low light, real stairs, real furniture |
| **Don't speak** except at meetings, and only if living | The house is listening — and every information channel in this design routes through systems the app controls |
| **Don't dodge** when someone brings their phone to yours | Contact is the Revoke. Flinching is reflexive rather than chosen, so it has to be named out loud |
| **Don't conceal your phone** — held out, screen up | Otherwise clutching it to your chest is a free, cost-free immunity to Revoke that will be discovered in the first round. Mostly self-enforcing, since it is how you hold the phone while doing a subroutine anyway |

**All four are social contract, enforced socially.** The app never checks any of them, exactly as it never checks whether you spoke.

### Atmosphere and Tension

**The register is cold institutional retro, not playful.** Nostromo terminals, Lumon, a 1991 hospital monitor. Not Win95 nostalgia.

**Atmosphere carries a mechanical load it must not shed:** with the doom clock fully hidden, **escalating atmosphere is the only channel signalling that the house is winning.**

> **Degrading subroutines are deferred to v2 (D-158).** As System Integrity drops, subroutines would get glitchier and harder. The promotion to load-bearing v1 recorded here is **reversed**: it is a difficulty curve bolted to the win meter, and it needs a full design conversation and a rebalance before it can be asked to carry a feedback load. **In v1 the escalating atmosphere above carries that load alone**, which is the load it carried before the promotion.

**Ambient flicker is a permanent system behavior, not an ability.** Random lamp flickers occurring on their own make light unreliable as a signal, providing deniability for free and forever, without costing a button.

**Tension escalation across a round:**

| Phase | Atmosphere |
|---|---|
| Arming | All phones flip to `ARMED` simultaneously. Masking track starts |
| Early round (0–3 min) | Quiet. No one is out. The map has no history. Subroutines are clean |
| Mid round | Subroutines begin degrading. Ambient flickers. Meetings punctuate with white light and total silence |
| Egress | The one number Residents had becomes a countdown against them |
| Late round | Heavy degradation, fewer lamps moving in the house, the ghost couch glowing |
| Endgame | The house speaks — its only direct address of the round |

### Fear Mechanics

#### Visibility and darkness — the core system

**The requirement ladder. The game must be complete at Tier 0.**

| Tier | What | Requires |
|---|---|---|
| **0** | Dim amber screen lamp | **Nothing.** Ships in v1. The game is complete here |
| **1** | Printable paper **snoot**, with an **optional end cap** (in-app PDF: print, cut, roll, tape) | Paper + tape. The plain tube is validated from past playtests; the cap is the attenuation variant |
| **2** | Manufactured clip-on snoot; NFC sticker packs | Purchased. **Never required** |

**Design the printable as a tube, not a film.** The paper-and-tape hack that worked in past playtests worked as a **snoot** — a tube extending past the lens restricting the cone to ~20–30° with short throw — not as a diffuser. A diffuser *spreads* light, producing more ceiling and wall spill, more bounce, and less claustrophobia. The snoot is what produces the "walk past someone and miss them" feeling.

**One artifact, two jobs: the tube takes an optional end cap.** Where torch hardware offers no fine-grained level control — binary on/off, which is common on Android — the LED is far too bright for a dark house and software cannot dim it. **A paper cap across the far end of the tube attenuates it in hardware.** The tube still narrows the beam; the cap knocks the intensity down. Print the plain tube where the OS can ramp the torch, the tube-plus-cap where it cannot.

**Keep the air gap — cap the tube, never tape paper across the lens.** Twenty-five continuous minutes puts paper directly on the warmest part of the phone, and an inch or two of standoff is both safer and optically better: attenuation without a hot spot.

**Rejected: a stick-on external flashlight.** It adds a battery, a supply chain, shipping cost, and a thing to lose, in exchange for a light the app can no longer control. **Whatever the light is, the app must be able to stop it.**

**The rule is minimise lit pixel *area*, not just brightness.** On OLED a black pixel emits nothing. Both reference eras were natively dark-text-on-lit-background, which in a dark house is a lantern; this design inverts that.

> ⚠️ **Open risk.** The move from deep red to amber (see Art Direction) was made on art-direction grounds and **weakens the scotopic-vision argument** that justified Tier 0. Deep red at 1–3% preserves rod vision; amber retains some but not all of that benefit. **The Tier 0 promise is at mild risk and the first prototype must test it.** If a dim amber screen does not produce the ~1-metre bubble, either the palette or the Tier 0 promise gives.

#### Vulnerability and detection

**Vulnerability is real, not simulated** (pillar P4). While performing a subroutine the player is **stationary, glowing, and visually captured** — eyes on a screen instead of the room. The Insider can genuinely walk up unseen. **Subroutine duration is therefore the difficulty dial, and light signature is the second axis.**

**Detection systems are deliberately bad.** The system never knows where anyone is:

> **It knows who was near a scanner, and the scanner knows which room it is in.**

All positions are relative and inherit the scanner's ground truth. Room-level granularity is not a limitation but an honest description of the data — there *is* no in-room position to display. Fiction and implementation match exactly.

**Three detection sources:**

1. **Check-in / Subroutine scan** — self-reported, player-initiated, voluntary. Insiders can abstain or forge.
2. **Sweep on scan** — when anyone scans, their phone sweeps for nearby devices. **You cannot stay off the map by refusing to scan**; someone else's scan counts you where you are.
3. **Sweep once a second while performing** — a player performing a Subroutine at a card counts who is near with every upload, once a second (D-212), and becomes a stationary sensor.

**What a sweep counts.** A phone at a marker places in its room every player it measures by Ultra Wideband within **1.5 m** (provisional, D-301); its Bluetooth places nobody (D-308).

**Revoked players are excluded from every rendered count — but their phones stay on the air.** This is a rendering rule, not a radio rule, and conflating the two is a mistake worth naming: *what the system knows* and *what a player is shown* are separate questions everywhere in this design.

| | Revoked phone |
|---|---|
| **Advertises** | **Yes** — this is what makes the report verifiable, so the report is not a fakeable signal |
| **Scans** | **No** — see below |
| **Measures by Ultra Wideband** | **When paired, and none of it is read** — it is paired only while a living phone is close and it can still be reported, and then it measures like any phone; the house reads none of its distances (provisional, D-300, D-301, D-306) |
| **Appears in rendered counts** | **No** |

**A revoked phone must never contribute sweep observations.** If what it sensed still counted, a player sitting revoked in a dark room would become an invisible sensor generating fresh occupancy data for a room nobody is scanning in — and **a room with suspiciously fresh counts and no one scanning there implies a hidden sensor, which means a revoked player is sitting in it.** That inverts the rule that coverage holes correlate with where people are not going. **Stay findable, never count.**

The meeting/couch area is a **mapping blind zone**, producing no data — without this, a sweep near the couch would place a cluster there, and under a count-based system a cluster at the couch *is* a count of the revoked. With both rules, absence stays ambiguous between *revoked*, *alone*, and *missed*, so an Insider working quietly in an empty wing looks identical to someone sitting revoked.

**Live positional tracking would damage the game.** The tension comes from not knowing where people are. ARKit visual positioning is out — it needs visual features and fails in darkness. Bluetooth ranks who is near and Ultra Wideband measures how near, by distance alone and never direction (U4); both feed gates, never a map.

#### Resource scarcity

The scarce resources are **time, light, and attention** — not ammunition.

| Resource | Scarcity |
|---|---|
| **Round clock** | ~25 minutes, hidden. Neither side sees the other's bar, or how much of it is left |
| **Motion budget** | Per-Subroutine, calibrated per player, drains visibly |
| **Timelapse** | 2:00 of history, once per 2:00, and watching it blinds you to 20s of the present |
| **Terminal access** | Exclusive — one reader at a time |
| **Access pool** | One shared number across Surge, Static, and Isolate, including self-targeting |
| **Attention** | The genuinely scarce one. You cannot watch the room and the screen at once |

#### Safe zones

**There are none, and that is deliberate.** The couch is where revoked players sit; it is not a refuge. There is no save point, no respite room, and no calm-before-the-storm beat other than the first ninety seconds of a round.

**The only true safety is other people**, which is what makes the Array Wipe rack, chain rendezvous, and Egress containment matter beyond their mechanical function.

---

## Level Design Framework — the house

### The setup walk

**The host walks their own house once and maps it. ~15 minutes, once — not per round.** This is the sleeper feature: every competitor is generic; ours knows the difference between your kitchen and your basement.

**The grid painter, one plan per storey.** Drag across cells to define a shape; that shape becomes a **room** or **stairs** — those two, and nothing else. Name it. Markers go into single cells afterwards.

**Floors are purely additive** — start with Floor 0 (renameable), add more as you go. Nothing fixed, and **no vertical-connection logic**: the app renders what was drawn.

**Why a grid beats free rectangles**, on three counts:

1. **Adjacency falls out of cell neighbours**, so plausible error injection needs no geometry at all.
2. **L-shaped rooms just work**, which matters because real houses have them and rectangles cannot express them.
3. **There are no resize handles, overlap rules, or snapping to build.** Simpler to author *and* simpler to implement.

### Level types — the marker taxonomy

| Marker | Count | Placement rules |
|---|---|---|
| **Subroutine marker** | ~8–12 per house | Never in a stairs zone |
| **Terminal** (map station) | Exactly 1 | Anywhere a room is drawn. Highly campable by design |
| **Array Wipe: Spares / Rack / Disposal** | 3, **drawn by the house at arming** | Not designated by the host and never stored with the home — the circuit moves every round (D-122). Ordinary registered markers, ideally spread across storeys, because the circuit is transit |
| **Insider-only rooms** | Host's choice | **Never app data.** Spoken aloud in the setup walkthrough; their doors are Override's business |
| **Stairs zones** | As drawn | **Transit only.** No markers, no counting, no timed routes |
| **Couch / meeting area** | 1 | **Mapping blind zone.** Produces no occupancy data |

### The one exclusion the editor enforces

**No Subroutine markers in stairs zones.** Safety. Easy for a host to get wrong, catastrophic if missed, and the editor refuses it with a legible reason.

**There is no second exclusion, because Insider-only rooms are not app data.** The host declares them aloud during the setup walkthrough, with every player present — *"this bathroom is Insider-only"*, *"here is the terminal"* — and the app never learns which rooms they are. Nothing to exclude, and nothing to leak: a room the editor could mark is a room a Resident's phone could read. **The Terminal's room is playable by definition**, so a closed door can inconvenience the Residents but can never put the Terminal out of reach. The old hazard — a Terminal behind an Insider-only door — cannot occur, so it becomes walkthrough guidance rather than an editor rule. **Guide the host; don't gate them.**

### Doors

- **Exterior doors** — anyone opens and closes, pure housekeeping. The app never mentions them.
- **Interior doors are physical house rules the app never enforces.** Residents may open the door of a playable room and may never close one. Insiders may close playable-room doors, and are the only ones who may open *or* close the door of a room the host declared Insider-only — that is the passive Override.
  - The bathroom ambush — hide, wait for an alert, walk out — is the mechanic working as intended.
  - It self-polices: emerging somewhere a Resident just cleared is damning.
  - **Door use is never tracked by the app.** The app cannot observe a physical door opening, so logging would require an Insider to voluntarily self-report, which no Insider would ever do. **The cost of using an Insider-only door is entirely social.**
  - **It stays untracked in v2**, even though smart-home contact sensors would make door state genuinely detectable. That would *add* information that does not otherwise exist.

### Safety — stairs stay in play

Decided: **stairs stay.** They make the game better and nobody has been hurt across all playtests. **No panic gesture** — players are physically co-located and can simply shout.

The shape to avoid is *Lemmon v. Snap*: a design that **rewards** a dangerous real-world act. This design must never reward the dangerous act.

**Design-level mitigations, in order of value:**

1. **Never place a Subroutine on stairs, a landing, or a step.** Enforced by the editor.
2. **Never put a timer on a route that crosses stairs.** No countdown that induces a sprint down a staircase in low light. This is most of the exposure.
3. **Auto-raise the lamp to full on stair traverse.** We own every photon — spend some on the staircase. Turns stairs into a designed, well-lit beat and is evidence of engineering toward safety.
4. **Hard rule against running**, stated in-app.

**Structural mitigations:**

5. **Host acknowledgment at the moment of decision** — a single screen shown *while tagging a staircase during setup*, not buried in a ToS. Contemporaneous and specific beats boilerplate.
6. **The app never says stairs are fine or forbidden.** It presents the choice and records that the host made it.
7. **Vocabulary: "low light," never "total darkness."** The baseline lamp must be navigable by design.
8. **Don't over-warn.** A wall of warnings reads as an admission.
9. **Age-rate 12+.**

> ⚠️ **Mitigations 3 and 4 are deferred — see OI-1.** Both require the app to know a player is on the stairs, which the tracking model forbids. **Mitigations 1, 2, 4's no-running rule and 5–9 all ship in v1** and carry the safety posture on their own. **The deferral expires at public launch, not at v1.**

---

## A Single Round, End to End

**This section is the composition check.** Seventeen sections of systems were designed in isolation and had never been traced against each other; half of them changed in the last session. This is one continuous timeline — setup, arming, the loop, a House Meeting, the endgame — naming every system as it fires.

**It found seventeen things.** They are marked inline as **`GAP`** (unspecified, needs a decision), **`CONTRADICTION`** (two settled decisions that cannot both hold), **`DECIDED HERE`** (resolved during the trace, with reasoning), or **`COMPOSES`** (checked and holds — worth recording so it is not re-litigated). A consolidated index follows the trace.

**The scenario:** 8 players, 6 Residents and 2 Insiders, `Known` mode, a two-storey house, ~25-minute round.

---

### T−15:00 · Setup (once per house, ever)

The host opens the app and creates a session; **the host's device owns the server**. Because of that, **a downed device must be a game *state*, never a real process exit** — a crash on the host's phone cannot be allowed to end the game.

The host paints **Floor 0** on the grid: drag cells, tag each shape as room or stairs, name it. Adds **Floor 1**. Floors are purely additive with no vertical-connection logic.

The moment the host tags a staircase, the **host acknowledgment screen** fires — contemporaneous and specific, at the moment of the decision.

The host drops markers into single cells. The editor **refuses** a marker in a stairs zone. It designates the **Terminal**, and the three Array Wipe markers — **Spares, Rack, Disposal**.

**Adjacency is now known for free**, from grid cell neighbours. Plausible error injection and Egress node selection both draw on it without a line of geometry.

> **`GAP` F-001 — Egress node designation is unspecified.** The house must name two *separate* markers when an Egress fires, but nothing says where they come from. Setup never designates them, and the two events have distinct fictions (**Beacon** broadcasts outward; **Tether** piggybacks a cellular radio) that imply particular places.
>
> **Proposed resolution:** *do not add a setup step.* Select two ordinary Subroutine markers at fire time, constrained to **non-adjacent rooms** — adjacency is already free from the grid. This keeps setup at three designated marker types instead of five, and it makes each Egress geographically different, which a fixed pair would not. Beacon/Tether then differ in flavour text and the containment beat, not in placement.

> **`GAP` F-002 — Isolate can win the game outright.** Isolate takes a marker offline. If an Insider Isolates an Egress node during an active Egress, containment becomes impossible and the Insiders win with no counterplay. Nothing in the roster prevents this.
>
> **Resolution — D-150, closing F-002:** **for the duration of an Egress the two nodes are simply excluded from Isolate's target list. They do not appear.** There is no fake success and no pool spend. The widget names both nodes to the whole house the instant the Egress fires, so a target list without them tells an Insider nothing they were not just told — the exclusion leaks nothing, and the shared Access pool is never silently burned on an action that could not have worked. *(This supersedes the earlier appears-to-succeed proposal: immunity plus fake feedback would charge a shared pool for nothing. Isolate is unbuilt; this is a constraint recorded for its builder.)*

The host sets lobby options: player count, Insider count, **`Known`**, discussion time, voting time, Revoke cooldown, Egress cooldown and timer, Access pool, meeting cooldown.

Roles are assigned. **Nothing is shown yet.**

---

### T−0:00 · Arming — the round starts

Every phone flips **simultaneously**, off a server-scheduled timestamp ~2s in the future rather than a broadcast "go." Each client measured its clock offset at join and schedules the flip locally, so network jitter never produces a ragged cascade. **This is the same mechanism that drives the lights-up snap and Sync Pulse — one piece of infrastructure, three uses.**

The perimeter arms. **`ARMED` appears in the status bar and stays there all round.**

**Role reveal arrives as text messages** in the Messages app:

- **Insiders** receive a petty, mundane blackmail text, then a house line in a **customer-service register** — *"do what I tell you and everything will be fine."* Then, because the setting is `Known`, a message from each fellow Insider.
- **Residents** receive a flat administrative work order.

Both roles receive texts, so **receiving a text is not itself a tell**. This makes the `Known` setting fully diegetic — **no roster screen needs to exist**, which is also what makes misidentifying your own partner in the dark a real and emergent failure. Leave it ambiguous whether the house wrote the friendly texts; it is free, and permanent. **The house never addresses a Resident directly — it is intimate only with the people it owns.**

> **`GAP` F-003 — the reveal leaks through haptics.** In `Known` mode with 2 Insiders, an Insider's phone buzzes four times (blackmail, house line, two partners); a Resident's buzzes once. **In a silent dark house, standing in a group, buzz count is an audible and tactile tell** — and it fires at the exact moment everyone is still clustered together. The design carefully made *receiving a text* not a tell and then let the *number* of them be one.
>
> **Proposed resolution:** **all roles receive an identical message count with an identical haptic pattern**, padded on the Resident side (work order + a house system notice + two routine maintenance advisories). Alternative, cheaper and arguably better: **deliver the whole reveal as one silent batch with a single haptic**, and let the player scroll. Either works; the current design does not.
>
> **Resolution — D-156 and D-249, closing F-003:** **the opening reveal is one notification, with one haptic, identical for every player** (D-156). The underlying message count never drives the buzz count, and the Resident side gets no padding deliveries. **Every seat's role reveal is also one length on the Wi-Fi**, whatever its role and its line (D-249). *(This supersedes both proposals above.)*

> **`GAP` F-004 — is Revoke available at T+0?** Nothing specifies an initial cooldown. If Revoke is live at arming, an Insider can revoke someone in the first fifteen seconds while the group is still clustered and nobody has dispersed — grim, confusing, and it wastes the round's only quiet stretch.
>
> **Proposed resolution:** **an initial Revoke cooldown of 45s**, longer than steady state. Egress gets the same treatment for the same reason. [ASSUMPTION: 45s — playtest number.]
>
> **Resolution — D-132, closing F-004:** **every Insider ability cooldown begins the round already running, at half its normal duration**, so the round opens with a guaranteed stretch of peace. The durations themselves are playtest's. *(This supersedes the 45s proposal above.)*

Lamps come up: **dim amber**. The springboard appears — **two pages, the same structure for both roles (D-286): same icons, same positions, same page-dot count.** The Insider's page 2 shows its own true state: REVOKE never reads READY while its own arm is spending, and the EGRESS and Tier-2 names dim while their clocks run.

The device states are now live, and each of them is a rule that no longer needs remembering:

| App | State | Rule it enforces |
|---|---|---|
| **Terminal** | `NO SIGNAL` | The map is a place, not a screen |
| **Files** | Empty | You can carry one thing, and only when carrying |
| **Messages** | Receive-only | Enforced silence, rendered |
| **Settings** | Mostly locked | Lamp control and *About* only; **End Session** for the host |

Each Resident's list holds **K Subroutines, `K = ⌈M ÷ worstCasePlainResidents⌉ + slack`** (D-129; the mix was first written as 7, weighted 1 circuit, 1 long, 3 medium, 2 short). Each Insider's list holds a fake of the same shape — same count, same mix, plausible markers, **including a fake waiting state**, with **a counter that advances**.

> **`GAP` F-005 — the System Integrity denominator does not match the subroutine count.** The revisions set System Integrity counting down from **32 → 0**. The round-length fix assigns **7 per Resident**. At 6 Residents that is **42 assigned subroutines against a 32-count bar** — so the bar empties with ten subroutines still outstanding, or the numbers mean different things. The rule that the denominator must never move makes this unfixable at runtime.
>
> **Proposed resolution:** **System Integrity starts at `7 × initial_residents` and decrements by 1 per completed subroutine.** Fixed at arming, never moves. Orphaned subroutines (from a revoked player or a collapsed chain) are **silently auto-satisfied** so the bar stays winnable. The `32` appears to be an artifact of an earlier player count and should be dropped. **This is a real blocker, not a cosmetic one — the bar is a win condition.**
>
> **Resolution — D-129, D-130 and D-146, closing F-005:** **the meter total `M` is proportional to seats** (D-130; the coefficient is playtest's), and every order's length is `K = ⌈M ÷ worstCasePlainResidents⌉ + slack`, computed from public lobby facts alone (D-129). **Nothing auto-satisfies, ever:** work a Revoke strands stays stranded, and `slack` is what pays for it (D-146). *(This supersedes `7 × initial_residents` and the auto-satisfaction proposed above.)*

---

### T+0:00 to T+1:00 · The first sixty seconds

*A seam explicitly flagged as worth watching. Traced, and it holds.*

Everyone disperses. Nobody has been revoked. The **timelapse floor is the start of the round**, so there is no history; the Terminal offers only the live count view, and every count in the house is fresh and uninformative because everybody just scanned nothing.

A Resident walks to their nearest marker, **taps SCAN and turns the lamp onto the code** (haptic tick on detection), and the subroutine opens by itself. The **motion budget calibrates on entry** against that player's hand steadiness, and the draining bar appears.

**What a Resident actually does in the first sixty seconds is: pure Subroutine execution with zero information — and that is correct.** It is the only stretch of the round when the house is quiet, and its tension is entirely anticipatory. Nothing needs adding here. The design's instinct to check this seam was right, and the answer is that the seam is a feature.

The Insiders, meanwhile, are walking to markers and going through the motions, because **a plausible screen requires real attendance**. Their cooldowns tick.

---

### T+1:00 to T+6:00 · The loop

The steady state, running for every player simultaneously.

**A Resident's cycle.** Move (~25s) → tap SCAN, turn the lamp onto the code → **routing** opens the subroutine, **check-in** logs the presence regardless → the subroutine runs, lighting them up or blacking them out per its light signature → their phone **scans continuously** for nearby devices (D-212), making them a stationary sensor → complete → System Integrity decrements silently → move.

**An Insider's cycle.** The same, for cover. Plus: **swipe to springboard page 2** — the same swipe every Resident has — → **hold a tile for two seconds** to **arm Revoke** (silent, invisible, nothing changes on any screen, and nothing opens) → close the distance on someone heads-down in a subroutine → **touch phone to phone.**

The target's lamp goes dark at the instant of contact. **No confirmation is asked of anyone, and nothing a bystander can see or hear marks the Insider:** both phones give one Long buzz, felt only in the hand (D-290). Because the target's own screen was the only thing lighting the attacker, it goes out in the same instant — so whether the target resolved a face is genuinely uncertain, which is the point.

**The cooldown started when they armed, not when it landed.** A botched stalk costs a full cooldown, which is the entire reason the two-step is interesting.

The revoked player's device goes dark and inert **to them** — but **the radio stays up so they can be found, and nothing it senses counts** (see Ghost mode). They sit down where they are, phone in their lap. **Ghost phase 1**: findable in the dark, leaking nothing, because they genuinely have nothing.

**Around T+3:00 a Resident camps the Terminal.** The station **locks exclusively**. The live count view is free, limited only by the motion budget; their own room is outlined as an anchor; counts dim through three staleness bands. Their arrival **generates its own check-in**, so using the surveillance system is itself surveilled. They trigger the **timelapse**: 2:00 of history at 6×, twenty seconds, oldest→newest, plays once.

> **`GAP` F-006 — the Egress widget takeover costs less than the design claims.** The pitch is that converting the System Integrity widget into an Egress countdown "costs the Residents their only number at exactly the moment they most want it." But System Integrity **updates only at meetings and is frozen between them**. Between meetings the widget shows a stale number that has not moved and will not move. Taking it over costs a stale anchor, not live information.
>
> **Proposed resolution:** **keep meetings-only updating** — the rate signal it produces (the delta between two meetings shows how much real work happened in that interval, arriving as aggregate evidence exactly when everyone is assembled to argue about it) is worth more than a live ticker. **Restate the takeover's value honestly:** it replaces a familiar, stable, reassuring object with a hostile one that is counting toward a loss. The cost is psychological, and the design should claim that rather than claiming an information loss it does not deliver.

> **`GAP` F-007 — counts leak revocations more sharply than dots did.** The map changed from anonymous dots to **counts per room**, but the anonymity, staleness, and error-injection reasoning was all written for dots. A dot *fading* is ambiguous. A room going **2 → 1** is not — it is a decrement, and decrements are unambiguous in a way fades never were. The "dimming dot beside a bright one is a revocation signature" leak, which was accepted as acceptable under dots, is materially sharper under counts.
>
> **Proposed resolution:** **false negatives must do more work under counts than they did under dots.** Specifically: hold a room's last count for a few seconds after loss of detection so brief dropouts do not read as decrements, and bias injected error toward *under*-counting occupied rooms rather than symmetric noise. This is a tuning consequence of the dots→counts change and it was not carried across when the change was made. **Re-verify the whole staleness-leak analysis against counts before building it.**

> **`DECIDED HERE` F-008 — the dot-interpolation decision is defunct.** The design specified animating a dot sliding from room A to room B to turn 6× teleporting into legible motion. **Under counts there is nothing to interpolate** — a numeral changes from 3 to 2. Delete the requirement. The legibility problem it solved returns in a different form (numerals flickering at 6×) and should be solved by **counting-up/down animation on the numeral itself**, which is cheaper. *(Superseded, D-319: frames switch instantly with no tweening, because rule 5 permits no animation the house did not emit.)*

> **`RESOLVED` F-009 — the Egress timer is a win condition with no number.** Traced against travel: two Residents must read the node names off the widget, cross the house at ~25s per marker hop, reach two *separate* markers, and pair on a Sync Pulse. **60–90s of travel alone** in a mid-sized house, before any tapping — and no number was ever set.
>
> **Resolved as a lobby setting with a 120s default, not a constant.** House size varies more than a default can absorb, so this is the host's to tune. Better still, **the app can propose it from the painted grid** — the longest marker-to-marker cell path is already computable from the setup walk.

> **`CONTRADICTION` F-010 — the Surge stairs-suppression rule cannot be implemented. This is a safety rule.** Surge must be suppressed while the target is in a stairs zone, and auto-cancel if they enter one. But **the system never knows where anyone is** — it knows who was near a scanner, and **stairs carry no markers and are never counted**. The app therefore has no way, ever, to know a player is on the stairs. The same problem stops the "auto-raise the lamp to full on stair traverse" mitigation.
>
> **Two load-bearing safety mitigations depend on a fact the tracking architecture guarantees the app cannot have.** Neither can ship as written.
>
> **Correction to the framing above, which matters for what it costs to fix later.** Only the Surge half is genuinely blocked by the tracking model — suppressing Surge needs the *server* to know where the *target* is. **The lamp auto-raise is entirely device-local**: your own phone reading your own accelerometer and raising your own lamp, reporting nothing to anyone and violating no tracking rule. Its only obstacle is *do not build a classifier*, which is a far weaker constraint than architecturally impossible.
>
> **Status: deferred, not solved.** The four highest-value stairs mitigations do not depend on detection at all and all survive — no markers in stairs zones (editor-enforced), no timed routes crossing stairs, the hard no-running rule, and the host acknowledgment at tagging time. Deferring the two detection-dependent mitigations leaves a real mitigation set intact. **The condition on deferral: it stops being deferrable at public launch, not at v1** — a controlled friend group is a different exposure than an App Store listing. See **OI-1**.

Around T+5:00 an Insider fires **Egress**. The house picks **Beacon**.

Every phone shows a **dismissable** alert — dismissable because you cannot scan a marker through a modal. The **System Integrity widget becomes the countdown and names the two nodes**, because coordination is required and nobody may speak.

Residents converge. Two reach separate nodes and hold; each tapping phone **feels the house's pulse** off a scheduled timestamp; each taps four pulses in a row, and the two pair when their runs share a pulse (D-298). Extras arriving form **concurrent pairs**; the first success contains it. A stalling Insider at one node **removes a partner, not the attempt** — and a failed attempt costs a 3 s lockout, felt as one unbroken vibration on the tapping phone (D-298), so spam-retry is not optimal.

**Contained.** The widget reverts. Nobody learns anything about anybody.

---

### T+6:00 · A House Meeting

A player calls it. The trigger renders as an **incoming call with the caller named**.

> **`DISSOLVED` F-011 — "Deactivated Person Found" was never a fakeable signal. The trace made a bad assumption.** The original finding argued that a revoked phone stops advertising, so the app can never verify a find, so the trigger is an unconditional button — which would have violated *core state signals must never be fakeable*, the rule that cut False Alert.
>
> **The premise was wrong, and the error is instructive enough to record.** "Revoked phones become fully invisible" is a **rendering** decision, not a **radio** decision. Excluding a revoked player from displayed counts is what the design needs; switching their transmitter off is not, and nothing ever required it. **The system may know things it never shows** — that separation holds everywhere in this design, and the trace collapsed it in one place.
>
> **With the radio up, the report is verifiable by physical contact, and both meeting triggers survive untouched.** The rule holds unamended. Two rules fall out and are recorded in the body of this document: the revoked phone **advertises but never scans** (or a revoked player becomes an invisible sensor, and a room with fresh counts and nobody scanning in it gives them away), and the contact gate is **hard-limited to contact range** (or the report becomes a through-wall detector for revoked players). *(Since D-301, in a round on Ultra Wideband, the report is read from distance: 30 cm held for 2 s, on the reporter's side, and a revoked phone, measured only while it can still be reported (D-306), has none of its own measurements read. Since D-307 it runs only while the reporter's phone is on the main view, springboard page 1 or 2, and takes the screen over with the filling circle. See The contact primitive.)*

Three things fire on the same scheduled timestamp: the **masking track cuts to total silence**, every lamp **snaps to full white**, and **every phone starts vibrating** — LONG–pause–LONG, repeating until answered (D-160).

The ordering is the beat — twenty-odd minutes of continuous noise stops cold, and the call lands in the silence it just created. **The call is what makes the meeting unambiguous.** Light only reaches you if you can see a lamp, and in a real house across two storeys you often cannot.

**And it is the one signal that does not give up** *(D-160, and the reason the sound was reversed rather than merely removed)*. The rest of the game says its thing once; the call repeats until it is answered, because a summons that fires once is a summons a player face-down in another room mid-Subroutine will miss. **What the sound was solving, persistence solves** — a phone in a hand that keeps buzzing reaches its owner whether or not they were looking at it, and it reaches nobody else at all.

Speech is now permitted, **for living players only**.

**Revoked players stand and walk to the couch. Phone still dark to them. Ghost phase 2.**

**System Integrity updates and freezes** at meeting start. The delta since the last meeting is a **rate signal** — a flat bar means people are revoked, lost, or faking, and that is aggregate evidence arriving in a single burst exactly when everyone is assembled to argue about it.

**Discussion — 90s.** The phone shows a timer and one control. **Unanimous "Ready to vote" skips ahead.** Minimal phone interaction for anything social: the phone's job here is to hold a clock and get out of the way.

**Vote — 45s.** Names plus **Skip**. A two-second hold locks the vote irrevocably, and the buzzer locks whatever is selected (D-117, D-141). You see **how many** have voted, never what. **Not voting counts as a Skip**: a player who selected nothing for the whole window is counted for Skip (D-075, D-117; owner ruling, 2026-11-21, D-320, superseding D-144's abstention line). **Ghosts cast nothing** — a vote is a channel, and the tally leaks even when secret.

**Result.** Most votes is **restrained**; **ties resolve to Skip**. **The living see the count and who was Restrained, never who voted for whom**; only the out see the ballot with names against it (D-075, D-195). Then **nothing**: no role, no confirmation, no reveal. **A correct restraint and a catastrophic one look identical.**

The group holds them; **the house deauthorises them moments later**, because a restrained occupant is no longer useful to it. The system was watching the meeting.

**Meeting ends. All lamps drop back to dim in unison — the "go" signal.** Masking resumes. **The timelapse history floor resets to now**, wiping everything. Any Insider who did something risky at T+5:30 has just had it erased, and calling the meeting was a real play with real odds.

**Ghost mode appears — phase 3 — only now**, for everyone on the couch. Both bars as exact percentages, the Egress's included (living phones see it as a countdown and a draining bar; owner ruling Q3, 2026-11-18). True occupancy, live, every storey, no injected error, no staleness. **Never alignments.** **No channel of any kind** — cannot act, cannot speak, cannot vote, cannot message each other. Several people watching the same truth side by side, unable to share one thought about it.

> **`COMPOSES` F-012 — post-meeting Terminal blindness is intent, not a bug.** *A seam explicitly flagged as worth watching.* For the first two minutes after every meeting the timelapse has almost no history. Traced: the **cooldown scales to available history**, so a camper gets a short timelapse early rather than nothing, and the **live count view remains free**. More importantly, this window is *supposed* to be the most dangerous stretch of the round — the surveillance system is blind at exactly the moment everyone has just learned where everyone else is and dispersed from one place. **Record it as designed behaviour so nobody "fixes" it.**

> **`COUPLING` F-013 — the unlock delay is a function of round length, not a free parameter.** Chains use a random 20–120s unlock delay. With 2 Insiders the chain bound is `L ≤ 3`, so a 3-link chain with two draws near the top of that window burns **up to four minutes** before its final link unlocks — and the meeting-end history wipe means a chain can span the entire useful life of the evidence it was meant to generate.
>
> At ~25 minutes that is ~16% of a round and the original window is proportionate. **It was briefly halved when the round was provisionally 15 minutes, and restored when the round returned to ~25.** The finding that survives is the coupling itself: **whenever the round estimate moves, this number must be re-derived**, and it is the kind of dependency that silently goes wrong because the two numbers live in different sections. **Held at 20–120s; start at the short end.**

> **`GAP` F-014 — carry state across a meeting is unspecified.** A player holding a **Memory Dump** pattern or an **Array Wipe** disk when the meeting fires: does the carry survive? Nothing says. It matters twice over, because the Array Wipe carry flag also **blocks scanning every other subroutine**, so a carry that persists through a meeting extends a lockout across a phase change.
>
> **Proposed resolution: both carries persist.** For Memory Dump this is explicitly the intended experience — holding four remembered elements through ninety seconds of white light and arguing is exactly the stress the Subroutine exists to create. For Array Wipe, persisting is also correct: the commitment should not be laundered by a meeting. **State it, because the default implementation is to clear transient state on a phase change and that would be wrong in both cases.**

> **`DECIDED HERE` F-015 — Files must never show the Memory Dump pattern.** The interface model says Files holds one item while carrying. The Array Wipe disk belongs there. **The Memory Dump pattern must not**, because the Subroutine's whole premise is that the data cannot sit on your phone — *only your head is off-network*. A Files entry showing the pattern would make the Subroutine's fiction literally false and quietly turn a memory Subroutine into a reading Subroutine. **Files shows the disk; the pattern lives nowhere but the player.** Easy to build wrong, so it is written down.

---

### T+7:00 to T+13:00 · The second interval, and the endgame

The loop resumes with the map wiped and the population smaller. Chains re-check their length bound against the **living** Insider count at every extension, so the late-game unstaffable hole never opens; **nothing auto-satisfies**: work a Revoke strands stays blocked, and the slack sized into every order is what keeps the meter winnable (D-146).

**Subroutines do not degrade in v1** — degrading Subroutines are deferred to v2 (D-158). With the doom clock hidden from both roles, atmosphere is what tells Residents the house is winning.

A second meeting lands around T+11:00. Another player is revoked. **Down to 3 living Residents and 2 living Insiders.**

> **`COMPOSES` F-016 — the endgame at 3–4 players holds, and the parity rule is why.** *A seam explicitly flagged as worth watching.* The concern was that Egress becomes uncontainable and chains collapse just as parity closes in. Traced:
>
> A live game requires `living plain Residents ≥ living Insiders + 1`, so **the smallest living Resident population in a running game is 2** — with 1 Insider, the floor is 2 Residents; with 2 Insiders, 3. **Two Residents are exactly what an Egress needs.** The parity loss condition therefore *guarantees* every Egress is containable for as long as the game is running, and **no low-population fallback rule is needed anywhere.**
>
> It is brutal, and it should be: at 3 Residents versus 2 Insiders, an Egress means two of three must find two separate nodes across a dark house inside 120s while one of the remaining players is hunting them. **The endgame is short and grim by construction.** *(This was traced when the round was provisionally fifteen minutes; the estimate is now ~25 minutes, as an estimate and a ceiling, D-011.)* Record this as verified — the two rules were designed independently and the tight coupling between them is load-bearing.

> **`REOPENED` F-017 — ghost idle time scales with round length, and the ~25-minute decision reopened it.** Ghost mode does not activate until the first meeting *ends*, so a player revoked early sits with a dark phone until then.
>
> This was verified as composing when the round was provisionally 15 minutes — meetings around T+6 and T+11 put the worst case at ~3 minutes. **At ~25 minutes with meetings further apart, being revoked at minute 3 is back to a five-to-ten-minute wait doing nothing**, which is precisely the failure state ghost mode was designed to solve. It is recorded here rather than quietly dropped because the reversal is real.
>
> **There is no clean fix, and the constraint is structural.** Ghost mode cannot activate before the living know you are out, or the sequencing rule breaks — *there is never a window where a ghost knows something the living do not* — and phase 1 must stay empty, because anything on that screen would glow or be readable. **The only lever is meeting frequency.** Escalated to **OI-6**.

**The endgame arrives one of four ways:**

1. **System Integrity reaches 0.** Residents win **immediately**, without waiting for a meeting. The completing player gets no experience distinct from anyone else's beyond "you won." **The perimeter disarms** — the status bar has read `ARMED` for fifteen minutes and now reads `PERIMETER DISARMED`.
2. **`living plain Residents ≤ living Insiders`.** Insiders win (D-131).
3. **An Egress runs its clock out.** Insiders win outright.
4. **Every Insider is Restrained.** Residents win — except that a running Egress outlives its Insiders and must still be stopped; Restraining the last Insider during an Egress does not end the round (D-131).

**A round can also end with no winner.** After the warning, a permission switched off closes the house: every phone shows **THE HOUSE IS CLOSED** and everyone starts over (D-310).

### The reveal

Fixed sequence, and **the room does most of it, not a screen**:

1. **The house speaks last** — its only direct address of the round. *"Thank you for your cooperation"* on an Insider win; *"Unfortunate"* on a Resident win.
2. **Lights up, and the Insiders stand.** Physically, in the room. No screen announces it.
3. **The blackmail publishes.** Everyone learns who *and why* — the petty, mundane thing each Insider was coerced with.

Chain membership is exposed here and nowhere else. **There is no round replay** — it would compete with the room, and the room wins.

---

### Findings index

| # | Kind | Finding | Status |
|---|---|---|---|
| F-001 | GAP | Egress node designation unspecified | Proposed: pick two non-adjacent Subroutine markers at fire time |
| F-002 | GAP | Isolate on an Egress node is an unanswerable win | Proposed: Egress nodes immune, failure invisible |
| F-003 | GAP | Role reveal leaks through haptic buzz count | **Closed (D-156, D-249).** One notification, one haptic, one length on the Wi-Fi, for every player |
| F-004 | GAP | No initial Revoke/Egress cooldown at arming | **Closed (D-132).** Every Insider cooldown starts the round running, at half |
| F-005 | GAP | **System Integrity 32 vs 42 assigned subroutines** | **Closed (D-129, D-130, D-146).** The meter scales with seats, order length is `K` from public facts, nothing auto-satisfies |
| F-006 | GAP | Egress widget takeover costs a stale number, not a live one | Proposed: keep meetings-only, restate the value honestly |
| F-007 | GAP | Counts decrement unambiguously where dots faded | Proposed: false negatives must work harder; re-verify the leak analysis |
| F-008 | DECIDED | Dot interpolation is defunct under counts | Delete; frames switch with no count-up (D-319 supersedes *animate the numeral*) |
| F-009 | RESOLVED | Egress timer never numbered | **Lobby setting, 120s default.** Host-tuned, because house size varies more than a default can |
| F-010 | CONTRADICTION | Surge stairs-suppression is unimplementable; the lamp-raise is device-local and cheaper than first stated | **Deferred (OI-1).** Four surviving mitigations carry v1; revisit before public launch |
| F-011 | DISSOLVED | The finding assumed revoked phones stop advertising. That is a rendering rule, not a radio rule | **Closed.** Report is verifiable by contact; both triggers survive; rule unamended |
| F-012 | COMPOSES | Post-meeting Terminal blindness | Designed behaviour. Do not "fix" |
| F-013 | COUPLING | Unlock delay is a function of round length, not a free parameter | Held at 20–120s for a ~25-min round |
| F-014 | GAP | Carry state across a meeting unspecified | Proposed: both carries persist |
| F-015 | DECIDED | Files must never show the Memory Dump pattern | Recorded |
| F-016 | COMPOSES | Endgame at 3–4 players; parity guarantees Egress containability | Verified. Load-bearing coupling |
| F-017 | **REOPENED** | Ghost idle time scales with round length and meeting spacing | **OI-6.** Verified at 15 min, reopened by the ~25-min decision |

**One finding dissolved on inspection** (F-011), **one is deferred with its mitigation set intact** (F-010), **one was reopened by a later decision** (F-017 → OI-6), and **one is a blocker on a win condition** (F-005). Everything else has a resolution carried into the sections above.

**Seams checked and cleared:** the first sixty seconds (traced, holds, needs nothing), post-meeting Terminal blindness (F-012), and the 3–4 player endgame (F-016). **Meeting material was the one seam that did not survive the trace** — it was subsequently accepted as designed behaviour rather than mitigated. See OI-3.

---

## Progression and Balance

### Player progression

**There is none, and that is the design.** No unlocks, no levels, no meta-progression, no cosmetics in v1. A round starts every player at zero and ends in fifteen minutes.

**What progresses is the house**, once: the setup walk is a one-time investment that makes every subsequent round in that house better. That is the only persistent state in the product.

**What progresses within a round is two hidden bars**, and neither side can see the other's.

### The balance surface

Every tunable in the game, and what each one does. **Nothing else should become tunable** — the roster is tight by necessity, because an Insider operates the panel by feel, in the dark, one-handed, under stress.

| Lever | Default | Effect | Confidence |
|---|---|---|---|
| **Player count** | 6–8 | Everything | Validated by prior play |
| **Insider count** | 2 at 8 players | Sets the parity floor and the chain length bound (`L ≤ living Insiders + 1`) | Derived |
| **`Known` / `Hidden`** | `Known` | Whether Insiders are shown to each other once at arming, never again | Decided |
| **Subroutines per Resident** | **7** (1 circuit / 1 long / 3 medium / 2 short) | Round length. **The primary pacing lever** | Derived, not playtested |
| **Revoke cooldown** | Own pool. Initial 45s | The win-condition clock | [ASSUMPTION] |
| **Revoke arm window** | 20s, or until a meeting is called | How much a botched stalk costs | Decided (owner, 2026-11-18; D-280) |
| **Egress cooldown** | Own pool | How often the house moves everybody | [ASSUMPTION] |
| **Egress timer** | **120s default, host-configurable** | Must cover two Residents crossing *this* house. **Host-set, because house size varies more than any default can** | Default derived from travel arithmetic |
| **Access pool** | One shared number | Governs Surge, Static, Isolate, **and all self-targeting** | [ASSUMPTION] |
| **Discussion time** | 90s | Meeting length | Lobby setting |
| **Voting time** | 45s | Meeting length | Lobby setting |
| **Meeting cooldown** | — | How often the map gets wiped | [ASSUMPTION] |
| **Unlock delay** | **20–120s** | **The fog dial.** Long → chains become a distributed revoke-detector; short → protects the walk-past-and-never-know core | **Coupled to round length** — re-derive if that moves |
| **Staleness bands** | 3 | How honestly the map admits its age | Thresholds [ASSUMPTION] |
| **Error injection rate** | — | How wrong the map is, in both directions | [ASSUMPTION] |

**One number governs the entire support kit.** Only Tier 2 shares the Access pool, so Surge, Static, Isolate and self-targeting all compete with each other, while **Revoke and Egress always tick independently toward available.** This is why the two time-critical abilities can be tuned without touching the support kit and vice versa.

### Difficulty curve

**Difficulty is not authored — it self-escalates from population.**

- **Egress containment** gets harder as players are revoked, because difficulty is a function of how many living players are nearby to pair with. Early round, six people converge and form three concurrent attempts; late round, three scattered players may never find each other in time. **No tuning knob, and none needed.**
- **Chains** shorten automatically as Insiders are revoked, since the bound is `L ≤ living Insiders + 1`.
- **Subroutines degrade** as System Integrity drops, which is also the only signal Residents get that the house is winning.
- **Camping the Terminal self-limits**, because it does not merely fail to advance the bar — it **extends the round**, and a longer round is strictly more Revoke opportunities. The cost compounds rather than accruing. Mass camping is a stalling strategy that structurally cannot win.

### Known balance risks

1. **`Hidden` mode strips Insiders of coordination**, which is a large chunk of their power. Expect Residents to be noticeably stronger. **The compensating lever is deliberately undecided** — more Insiders, shorter Revoke cooldown, or longer meeting cooldowns are all candidates, and it is a playtest call, not a now call.
2. **The parity-win justification needs rewording, not deleting.** It originally rested on the traitor bloc being able to tie a *vote*; the vote now ends in a group **Restrain**. The conclusion almost certainly holds — games at parity are grim regardless — but it is now kept for pacing and feel rather than for the original logic. Revisit if Residents get robbed in `Hidden` mode.
3. **The Terminal may be Insider-favoured on net.** Target selection is more actionable than diffuse constraint information: an Insider at the station finds isolated counts, sees where crowds are, and checks whether anyone is following them. Accepted, and watched.
4. **Static's risk is its price, not its abstraction — the abstraction risk was taken (D-186).** This slot used to hold Spoof, and the risk written here was that Spoof was slow, indirect, asked an Insider to reason about the map layer while standing in a dark hallway, and **may simply never get used.** The owner cut the mechanic and kept the slot rather than waiting for the playtest to report it. **What is watched now is the opposite failure:** Static is immediate, needs no map reasoning, and announces itself to the whole house, so the question is whether the heavier cooldown is heavy enough — an ability fired on reflex before every Revoke is a tax on the Access pool that Surge and Isolate pay.
5. **The first meeting is thin by design.** Accepted, not mitigated — see OI-3.

---

## Art and Audio Direction

### Art style

> **A PalmPilot with the backlight on, in a dark room.**

**Amber (~590nm) on true black. Monochrome, four luminance steps, no second hue.** Emphasis comes from **inversion** — amber ground, black glyphs — the way a real amber panel does it.

**Dark-field, not light-field.** Both reference eras (2001 PDA, 2005 feature phone) were natively dark-text-on-lit-background, which in a dark house is a lantern. This design inverts that, and **the rule is minimise lit pixel *area*, not just brightness** — on OLED a black pixel emits nothing.

**Register: cold institutional retro.** Nostromo terminals, Lumon, a 1991 hospital monitor. **Not playful Win95 nostalgia** — the reference material needs bending away from nostalgia toward dread.

**This was already latent in the design.** Array Wipe, Degauss, Memory Dump, Parity Check, Sniff, Handshake, Sync Pulse are all vintage sysadmin vocabulary. The subroutine set named the aesthetic before the aesthetic was chosen.

**Two constraints ended with this decision, and both are worth recording:** "no hue discrimination" is now moot (monochrome cannot depend on hue), and low-brightness colour banding is moot (there are no gradients left).

**One constraint was created:** the reversal from deep red to amber weakens the scotopic-vision argument underpinning Tier 0. See the Fear Mechanics open risk.

**Why this is cheap as well as good.** Retro flat UI is *less* work natively than realistic UI — hard edges and flat rectangles, with no need to match Apple's blur materials or spring curves. **Time is the binding constraint, so cheaper wins**, and here cheaper is also better.

### Asset requirements

Deliberately tiny, and this is a scope win worth protecting:

- **One palette:** four amber luminance steps on true black. No second hue, no gradients, no alpha ramps.
- **One bitmap typeface**, period-appropriate. (Note: the legacy font in the archived project is VCR OSD Mono by Riciery Leal — verify its licence separately before shipping.)
- **Icons:** flat monochrome glyphs, one per springboard app, drawn at a single size.
- **No photographic assets. No 3D. No character art. No animation beyond state transitions and spring curves.**
- **All new, commissioned under work-for-hire** assigning copyright and warranting originality. Brief the artist on this theme and never on the reference game.

### Audio

**Masking audio is information control, not atmosphere.**

> The noise floor suppresses an information channel the app cannot see, fake, or delay.

Everything else in this design — anonymous counts, injected error, staleness bands, randomized unlock delays — is carefully calibrated fog. **Unmasked audio is ground-truth data leaking outside the system and bypassing all of it**: footsteps, and the involuntary yelp when someone is tapped in the dark.

**Phones cannot be the masking source.** Two reasons, and the second is fatal:

1. **Physics.** Footsteps are low-frequency thumps (~50–200 Hz); phone speakers produce almost nothing below ~500 Hz. Masking works only where spectra overlap, so phones would cover yelps passably and footsteps essentially not at all — the main thing that needs covering.
2. **The source moves with the player.** If every phone plays the track, the loudest source near you is the nearest person. **The masking noise becomes a localization beacon**, destroying the exact thing darkness exists to create. Worse than no masking.

> **Hard requirement: the masking source must be stationary and separate from the players.**

**The audio ladder**, mirroring the light ladder:

| Tier | Source | Requires |
|---|---|---|
| **0** | Any household speaker — BT speaker, laptop, TV, soundbar. The app generates the track; the host plays it | Something nearly every house has. **v1** |
| **1** | Spare phones in a stationary **node mode**, placed around the house | Free — uses the drawer of flat iPhones most households own. **v1.1** |
| **2** | Smart speakers, game-driven, over AirPlay 2 | A smart home. **v2** |

**Node mode is deferred to v1.1** — not because it is hard (ambient masking needs no audio sync at all; uncorrelated noise from several phones is a *better* diffuse field than synchronised noise, and only the cut-to-silence beat needs timing, which is second-accurate rather than millisecond-accurate). It is deferred because v1 exists to test whether the core loop is fun, and for that, masking is a Bluetooth speaker playing a file.

**Sound is the second state channel.** If the app owns the track it can modulate it:

- **Swell during an Egress.**
- **Cut to total silence at a meeting call** — twenty-odd minutes of continuous noise stopping cold at the same instant every lamp snaps to white.

**The meeting call, and it is silent** *(the owner's 2026-08-22 sound reversal, and D-160)*. On the same timestamp as the cut and the lights, **every phone rings in the hand** — the incoming-call screen with the caller named, and the repeating haptic. **There is no ringtone.**

| Property | Value |
|---|---|
| Channel | **Haptic only.** LONG–pause–LONG, then a wider wait, then again — the one pattern in the game that repeats. `HapticScript.RING` is the only place it is defined |
| Fires on | Both trigger types: House Meeting and Deactivated Person Found |
| Differentiated by type? | **No.** Identical pattern for both; the screen carries the difference. A differentiated call would tell players in transit something they will learn thirty seconds later anyway, for no gain |
| Duration | **Indefinitely, until answered** (D-160, superseding *not indefinitely*). A summons that gives up is a summons somebody misses — and the reason the old row worried about a phone *still ringing two minutes later* was that it localized whoever was slow. Nothing localizes anybody now: the motor is in one hand |
| Silent switch | **Not applicable.** The row that lived here demanded the call play regardless of the hardware silent switch, with the audio session category to make it happen; there is no audio session |
| Volume | **None.** The row that lived here said *full — this is the one moment the game wants to be loud*, and the game no longer wants that |

**Why the sound was reversed rather than tuned.** The old argument was that at a meeting call localization is already total, so a chirp adds no information the white lamps had not already broadcast. That was true and it was not the whole account: **a sound is heard by the room, and a room contains people who are not in it.** The two things the ring was solving are solved by the two things that replaced it — the summons reaches a player who is not looking at their phone because it **repeats until answered**, and it reaches a player two storeys up because it is **in their hand**, which is where the house rule already requires the phone to be. What is gone is the half nobody needed: a channel the whole building shares, in a game whose discipline is that the only private channel is the one against your palm.

**Which leaves no phone-emitted sound in the game at all** *(D-320)*. The Surge held the one slot (D-176) and is light only since the owner's 2026-11-21 ruling — see the sound corollary in M2.

**Only ever modulate on global events.** A cue firing for a subset of players leaks role through a channel every person in the house can hear. Light is safe here because each lamp is private; sound is not.

**No audio in subroutines, ever.** A chirping phone is a beacon. Haptics are the private channel.

---

## Technical Specifications

> GDD-level only: what it runs on and what it must achieve. Engine and system design belong to the architecture phase.

### Platform

- **iPhone ships first. Android is on the roadmap.** Validation with a controlled friend group beats reach, but Android is a real milestone rather than an aspiration, and that changed the stack.
- **A phone without Ultra Wideband cannot play** (D-292). At launch it shows THIS PHONE CAN'T PLAY / "Someone's Home needs Ultra Wideband: iPhone 11 or later, not iPhone SE." and nothing after it.
- **Kotlin Multiplatform + Compose Multiplatform.** One UI and one simulation core across both platforms; the platform layer (radio, torch, camera, motion, haptics, NFC, audio) is written per side behind `expect`/`actual`. **The interface is never built twice**, which matters because screen parity is leak-critical and mixed-platform play would otherwise require the same invariant to hold across two independently-written codebases.
- **No engine runtime**: instant launch, small binary, low battery for a round with camera, torch and BLE live.
- **OLED strongly preferred** — the whole art direction assumes a black pixel emits nothing. The design does not *break* on LCD, but the darkness does.

### Platform capabilities required

Every one of these is OS work, and every one is needed for **precisely the things that make the game good** — not for peripheral features. This list is why the engine question resolved the way it did.

Torch **level** control (not binary) · screen brightness override · keep-awake · a **preview-less** camera session · NFC tag sessions · BLE advertising *and* scanning simultaneously, to rank who is near · Ultra Wideband ranging (Nearby Interaction) for contact-range distance · motion sensing at 100 Hz · haptics · local network transport · AirPlay 2 · smart home (v2).

**Every permission these need is asked at setup, in the light, before a player may take a seat (D-161), and every one is required to play (D-309).** The rows are CAMERA, BLUETOOTH, NEARBY DEVICES and MOTION & FITNESS. **Notifications is not asked for** (D-326): the app posts no iOS notification and has no background mode, and every notification in the game is drawn in-app. A phone stays at PERMISSIONS until each row reads GRANTED: refused, not yet asked, or asked with no answer read back all keep it there. NEARBY DEVICES, the name iOS gives the Ultra Wideband permission, sits beside BLUETOOTH, since both radios run (D-294, D-303), and **it must read GRANTED**: a phone seated without it could not be measured all round. Local Network is not a row: iOS never reports that grant, and raises its own prompt the first time the phone looks for a home or hosts one; if it is off, JOIN A HOME says LOCAL NETWORK IS OFF with OPEN SETTINGS under it, and NO HOME ON THIS NETWORK after about ten seconds of finding nothing (D-309). **Setup no longer asks anyone to switch NameDrop off**: a 10 cm pass does not need the held top-to-top touch that raises it (D-303).

**Every one of these is written per platform behind `expect`/`actual`** — none of it is shareable on any stack, which is why the choice of framework is about the *other* 60–70% of the build.

### Performance requirements

| Target | Value | How it is measured |
|---|---|---|
| **Coordinated flip skew** | All devices flip within **±150 ms** of each other — "reads as one event" | Filmed at 240 fps across 8 devices in one room. **Delivery latency is not this number** — the event is scheduled 1–2 s ahead behind a calling screen, so only clock-offset accuracy matters, and offset estimation on a local network should comfortably beat 150 ms |
| **Scan acquisition** | Code acquired in **≤500 ms** at arm's length, in ≤5 lux | Median over 50 scans, 5 lux ambient |
| **Battery** | **≤15%** drain over a 25-minute round, iPhone 12 or later | Screen on throughout, camera duty-cycled (~3–4 min/round, not 25), BLE advertising and scanning continuously, Ultra Wideband ranging only while a close pair is needed (D-306) |
| **Frame rate** | **60 FPS sustained** on the springboard and in every subroutine | Measured over a full round |
| **Motion sampling** | **100 Hz** with no dropped windows during a subroutine | Instrumented over a 60-second circuit leg |
| **Lamp luminance** | Dimmest step **≤2 nits** full-screen; lamp-screen lit pixel area **≤15%** of the panel | Photometer, and pixel-area measurement from a screenshot |
| **Lamp blackout on contact** | **Same frame** as the contact handshake resolves | Single-device, and the one genuinely tight timing requirement in the game — it is the entire mitigation for losing the anonymous revoke |

**The camera is duty-cycled**, to ~2–3 minutes per round rather than fifteen. A scan is a ten-second window that usually closes on acquire long before it expires, not a live preview. **Ultra Wideband runs only for close pairs the game needs** (D-306): an armed Revoke, a Revoked phone that can still be reported, or a player at a card, never for the whole round. **No round's drain has been measured on a phone yet**, so the 15% target is unproven until one is.

### Multi-device timing

**Do not broadcast "go."** Broadcast **"the event begins at timestamp T,"** ~2s in the future. Every client measures its offset from server time at join and schedules the flip locally, so network jitter stops mattering. **Build this in from day one** — Firestore-class snapshot listeners land in the 100–500 ms range with real jitter, which produces a ragged cascade instead of a snap.

**One mechanism, three uses:** the lights-up snap, perimeter arming, and Sync Pulse.

**MultipeerConnectivity is no longer available as the fallback mesh** — it is iOS-only, and the stack reversal made that disqualifying. Transport is now an open architecture decision (see `game-architecture.md` → Step 4). The likely shape is an **embedded server on the host with mDNS discovery and websockets over the local network** — all of which is multiplatform Kotlin, so **transport moves into the shared column** rather than being written twice.

**The remaining hole is no-infrastructure environments** — the basements the original mesh fallback existed for. The fork is host-as-hotspot versus carrying low-bandwidth game state over the BLE layer that is already running for occupancy.

### Known technical risks

| Risk | Note |
|---|---|
| **Kotlin/Native GC pause on the blackout frame** | Kotlin/Native uses a tracing collector, and the lamp blackout is the only hard real-time requirement in the game. **A pause there does not drop a frame — it un-anonymises a revoke.** Unlike a shader stall it cannot be pre-warmed away; the fix is a no-allocation discipline on that path, enforced by a **permanent allocation assertion** rather than a one-time check |
| **Backgrounding degrades BLE advertising** | A phone call, a notification tap, or a lock silently reduces a player's visibility to everyone. Keep-awake is planned; **also log backgrounding as a game event**, or phantom false negatives will appear that were never designed and cannot be tuned |
| **iOS NFC system sheet** | `NFCTagReaderSession` presents a "Ready to Scan" sheet that cannot be styled, branded, or suppressed. **Checked 2026-08-16: current docs still require user-initiated scans, a 60 s timeout, and a foregrounded app — no evidence the sheet became suppressible.** Prior is now *restriction persists*; the 30-minute spike stands. The one-SCAN-button design is correct regardless |
| **QR is photographable** | Mitigations: require the marker in frame for the full Subroutine duration, cross-check against BLE proximity, and accept that among friends it is mostly a non-issue. NFC is the real fix and it is the paid tier |
| **Anonymize server-side** | Resolve identity on the backend and ship only anonymized counts to clients, or a proxy trivially unmasks the map. Low stakes among friends, free to get right from the start |
| **Amber vs. scotopic vision** | The Tier 0 promise now depends on a palette chosen for art reasons. The first prototype must test it |

### Naming constraint (accepted)

"Insider network" is the standard name for a router feature with millions of support pages. **Google will never be a discovery channel** — you cannot rank for your own name. Judged acceptable because App Store search is scoped to apps and is the primary discovery path for a word-of-mouth party game. A known, deliberate tradeoff rather than an oversight.

---

## Development Epics

Fourteen epics. Detailed story breakdown in `epics.md`.

**E0 was added during architecture** and holds three systems that appeared in no epic: session recording and deterministic replay, the differential leak harness, and map persistence plus disconnect/rejoin. It is first because **this game is otherwise undebuggable by construction** — eight phones, a dark house, enforced silence, randomized fog, and players lying by design.

**Story 1.7 runs before everything as a standalone spike:** can Compose Multiplatform blank the lamp in the same frame as contact? A stack change would invalidate all of E0.

| # | Epic | Delivers | Depends on |
|---|---|---|---|
| **E0** | **Foundations** | Pure deterministic simulation core (no platform types), fixed-timestep tick, session recording, deterministic replay, client-transcript recorder, differential leak harness, map persistence, disconnect/rejoin | 1.7 spike |
| **E1** | **The lamp** | Full-screen amber lamp, torch level, brightness override, the white flip, and **every connected phone flipping at once**. **Carries the stack gate (1.7)** | — |
| **E2** | Session, sync, and arming | Host-owned server, join, lobby settings, clock-offset measurement, scheduled-timestamp flips, perimeter arming, role reveal by text | E1 |
| **E3** | The device shell | Springboard (two pages, role-identical), status bar, the apps as device states (page 1 is FILES, MESSAGES, SETTINGS and CAMERA; there is no Notes app, D-315), the work-order widget, Settings | E2 |
| **E4** | The house | Grid painter per storey, room/stairs tagging, additive floors, markers, host acknowledgment, the stairs exclusion, adjacency from cell neighbours | E3 |
| **E5** | Scanning and the motion budget | Tap-to-scan with the ten-second window and auto-open, preview-less capture, routing/check-in decoupling, motion accumulator with per-player calibration and its upper bound (the lower bound was dropped, D-328), the draining meter | E4 |
| **E6** | The subroutine set | Ten subroutines across 3 bright / 4 medium / 3 dark, **plus the fake versions**, plus degradation | E5 |
| **E7** | Subroutine structures | Short/medium/long/circuit scheduling, Memory Dump, Array Wipe carry state, chains with lazy linking and randomized unlock, System Integrity. **Built by D-322, D-324** (Resident-only chains, the length bound)**, D-325** (Memory Dump's screens, dealt; the Array Wipe's briefing) **and D-328** (a third of the seats chained at lights out, role-blind, replacing lazy linking; a held pattern blocks as a disk does) **and D-330** (the source re-scanned restarts a held pattern; the padlock for anything carried) | E6 |
| **E8** | The Terminal | Live counts, staleness bands with stored jitter, error injection, exclusive access, timelapse with cooldown-scales-to-history | E5, E4 |
| **E9** | Insider abilities | Revoke token model, Egress (Beacon/Tether, Sync Pulse containment), Surge, Static, Isolate, Override, Access pool, screen parity | E7, E8 |
| **E10** | The House Meeting | Trigger as incoming call, lights-up snap, discussion, vote, result, attribution | E2, E7 |
| **E11** | Ghost mode | Three phases, both bars, true occupancy, no channel | E10, E8 |
| **E12** | Endgame and reveal | Win checks, the fixed reveal sequence, the blackmail publish | E11 |
| **E13** | Audio | Masking track generation, host playout, swell and cut on global events only | E2 |

---

## Success Metrics

### Technical metrics

All of the performance targets above, plus:

- **Zero role leaks through light or sound.** Verified by a dedicated adversarial pass: no cue fires on the actor's device for any ability; no cue fires for a subset of players over audio; targeting a revoked player with Surge, Static or Isolate is indistinguishable from targeting a live one. **An armed Revoke is the one exception, and it is ruled:** a landed Revoke tells the armed phone, with one Long buzz on both phones and the tile straight to COOLING DOWN (D-290); an armed pass at a phone already Revoked does **nothing** — no buzz, the tile stays ARMED and the Revoke is not spent, because the house reads no distance of a phone that is out and so no touch is made (D-301; owner ruling, 2026-11-21, D-320). The room already sees the Revoked phone's dark lamp.
- **Zero alignment confirmations.** No screen, at any point, confirms anyone's alignment to anyone.
- **The denominator never moves.** System Integrity's displayed total is identical at arming and at the final meeting, in every round, including rounds with orphaned subroutines.

### Gameplay metrics

**The coordinated lights-up moment is already validated** — it was reproduced in prior play using smart-home lights, and it worked. It is not an open question and the build does not wait on it.

**Two things are watched during playtests rather than gated on:**

1. **Does a dim amber screen alone produce the ~1-metre vision bubble?** If yes, the Tier 0 promise holds with no accessory. If no, the palette moves — four luminance values, cheap to change — or the paper snoot becomes more load-bearing than planned.
2. **Does the target's lamp going dark at the instant of contact conceal the attacker?** **This is the one with consequences.** If contact does not conceal, the anonymous revoke is gone and ghost mode's information design needs revisiting.

From playtests:

| Metric | Target |
|---|---|
| **Round length** | **15–35 minutes** actual, against a ~25-minute estimate. Wide by design — the round ends when a bar empties |
| **Subroutine completion rate** | Residents complete ≥80% of assigned subroutines in a round the Insiders do not win early |
| **Ghost idle time** | Revoke → ghost mode. **Watch it directly; there is no target yet.** Structurally bounded by meeting spacing, and the number that decides whether OI-6 is a real problem |
| **Egress containment rate** | Contained ≥70% of the time at full population; **>0% and <50%** at 3 living Residents. If it is 0% at low population, the 120s timer is wrong |
| **Meeting engagement** | The unanimous "Ready to vote" skip is used **rarely at the *second* meeting.** Frequent use at the first is expected and fine; frequent use at the second means something is actually wrong — see OI-3 |
| **Terminal usage** | At least one player camps per interval, and campers are revoked at a *higher* rate than non-campers. If they are not, the exposure cost is too cheap |
| **Static usage** | Counted off the recording, as before. **The threshold is now a ceiling as well as a floor** — never used means the cooldown is too heavy; fired every time the pool comes back means it is too light (D-186) |
| **Rounds per session** | **3–4** in one evening |

---

## Out of Scope

### Cut permanently

| Cut | Reason |
|---|---|
| **Confirming alignment on removal** | Alignment is never confirmed, by any path, at any time |
| **Phone-crashing as a separate Insider ability** | Egress replaces it, and the roster must stay tight enough to operate by feel |
| **The Capgras / synthetic-replacement frame** | Replaced by blackmail. Insiders are coerced friends, not copies |
| **Conversion mode** | Went with the Capgras frame |
| **All inherited spaceship vocabulary** | Carried over from a 2022 README and never re-themed. Replaced wholesale by the smart-home register |
| **Sort (subroutine)** | The only one with no fiction, and its mode is half-covered by Deallocate |
| **False Alert as an ability** | Core state signals must never be fakeable |
| **Blackout, Flicker, Brownout, Wipe as abilities** | Folded into Surge, promoted to ambient behaviour, or made redundant |
| **A stick-on external flashlight** | A light the app cannot stop is not a light this game can use |
| **Microphone listening** | The fiction says the house listens; **the app must never listen.** Privacy liability and App Store review poison |
| **Live positional tracking** | Would damage the game — the tension comes from not knowing where people are |
| **Round replay at the endgame** | It competes with the room, and the room wins |
| **A roster screen** | The `Known` setting is delivered by text message instead |
| **Dot rendering and dot interpolation on the map** | Superseded by counts per room |

### The play manual — a deliverable with no epic

**Tutorial integration is "none, by design" for *mechanics*** — every game rule is a device state, so the app teaches by refusing. **That is not true of the physical rules**, which no device can enforce and which the host must say out loud.

Those need somewhere to live, and so do several other things currently homeless:

- The **four physical conduct rules** — don't run, don't speak, don't dodge, don't conceal your phone
- The **snoot printable** with fold instructions, and when to add the end cap
- **Host setup guidance** for the map walk
- The **photosensitivity notice** and the "low light, never total darkness" framing
- How to explain the contact tap to a new player in ten seconds

**One short artifact, and it is the only thing a player reads.** Everything else in this design refuses to explain itself on purpose.

### Deferred, with a named home

| Deferred | To | Note |
|---|---|---|
| **Physical tasks** (see the preserved list below) | **v2** | **As a host-authored physical marker type** — free-text instruction written during the setup walk, player self-confirms. The shape is decided; only the timing is deferred |

**The original physical task list, preserved.** These are the tasks from the real-life version of the game that was actually played and actually worked. They are deferred, not discarded — this is the full record of them:

*Short (physical):* wires · swipe card · shoot a basketball · memory card game (find a pair) · classify items into boxes
*Long:* Jenga block stacking · shoot down targets with a nerf gun · open locks (find the keys in a box)
*Two-person:* scan a marker and wait for another person to scan the paired one
*Manual dexterity:* braiding hair
| **Node mode** (spare phones as masking sources) | v1.1 | Cheap, and needs no audio sync. Not needed to validate |
| **Smart home integration** | v2 | **Its priority within v2 should rise.** The house is a character that texts your friends and arms the perimeter, so real control of real lights makes the premise closer to literally true |
| **The flashbang** (room lights as a real physiological attack) | v2 | Only ever an amplification of Surge, never a new ability |
| **NFC sticker packs** | v1 add-on | The better physical SKU: no battery, no electronics, no regulatory burden, healthy margin |
| **Android** | Post-validation | **Not a rewrite.** Shared core and shared UI; only the platform layer is new. See the torch caveat in Assumptions |
| **The trap** (a row or app that flares to full white, baitable) | Unscheduled | An idea, never designed past the sentence |
| **Restraint freeing the blackmailed** · **Access pool as a throttled data allowance** · **atmosphere items** (wallpaper from the setup photo, the voicemail nobody left, the app permanently at 47%) | Unscheduled | Recorded so they are not lost |
| **Trading with the house** — spend subroutine progress at meetings for favours | Unscheduled | A whole economy, not an item. Recorded in full below, with four things that must be resolved before it could be built |

### Trading with the house — unscheduled, recorded whole

**The premise:** the house will do things for you, and the price is your work. At a meeting you can spend accumulated **subroutine progress** to buy a favour from the house — and spending it means that subroutine resets and must be done again. A symbiotic relationship with the thing that is also blackmailing you.

**The delivery mechanism is the best part of the idea.** You do not open a shop. You open **Messages**, pick a reply option, and text the house — and the house texts back. It costs no new app, no new screen furniture, and it lands directly on the established register: the house already texts your friends and arms the perimeter, already speaks in customer-service voice. A vending machine would break that; a text thread deepens it.

**Two purchases sketched:**

- **Torch light, ~5 seconds.** A burst of real illumination, bought.
- **What the house has on someone.** Reveals the blackmail material the house is holding over a named player.

**The blackmail material is player-authored.** At setup, each player enters something true about themselves. When someone is picked to be blackmailed, the house uses *their own* submission against them, with the text generated at runtime by a small LLM on a self-hosted server. That is the mechanism by which the house stops being flavour and becomes a character that knows you.

> **Naming caution.** "In-game purchases" is the wrong label to carry forward — this document already says **no purchase** under *Explicitly not required, ever*, meaning money and accessories. This economy is barter in fiction and must never read as monetisation.

#### Four things to resolve before this could be built

1. **The blackmail purchase is an alignment oracle as sketched, and would be the worst leak in the game.** Story 2.7 makes the blackmail text *the Insider's role reveal* — Residents get a flat administrative work order instead. So buying "what does the house have on X" returns content for an Insider and nothing for a Resident, and **the absence is the answer**. This is exactly rule 1: the absent effect *is* the leak. It is fixable, and **D-116 already decided how: every player hands over a real one-line in the lobby, all of them real, and the house simply never uses a Resident's.** The cover is the typing, not the content — six people bent over their phones writing something true about themselves is six identical acts, and nobody can be identified by having written one. **Not** *the Resident's file is a fake*: a fake line has to be generated for somebody, and generation is where asymmetries hide. But it has to be designed that way from the first line, not patched later.

2. **Spending is itself an alignment tell.** The Insider's subroutines write nothing. If the currency is subroutine progress, the Insider spends a currency that costs them **nothing real**, while a Resident spends work they must genuinely redo. Free-spending behaviour at meetings, in front of everyone, becomes a read. The currency may need to be something both roles pay for identically, or the Insider's fake progress must be made to cost them something equally visible.

3. **A server-hosted LLM assumes connectivity the architecture does not have.** Transport is an embedded Ktor server on the host over the local network, with **manual Personal Hotspot** as the no-wifi fallback — the game is designed to run in a basement with no internet. Either the model runs on the host device, or **the text is generated once during setup, in the light, while connectivity exists**, and stored. The latter is probably right anyway: it removes runtime latency from a moment that must feel immediate, and it keeps a live network dependency out of the round.

4. **Buying "torch light" fights the light ladder.** The torch is optional Tier 1, and on binary-torch hardware it cannot be ramped at all — which this document accepts precisely *because* **every light event in the game is a screen event**. A purchasable torch burst either becomes the one exception, or gives players on some hardware a worse version of a bought good. A bought **lamp** flare has none of those problems and stays on the addressable channel.

**Consent surface, flagged separately because it is not a design question.** Players type real facts about themselves, and this economy makes those facts **purchasable by other players**. That needs an explicit, in-the-light acknowledgement of who can see what — the same treatment as the stairs acknowledgment in story 4.8, which fires at the moment of tagging rather than hiding in a ToS.

### Explicitly not required, ever

**Tier 0 must be complete.** No purchase, no accessory, no smart home, no props. Everything above Tier 0 is production value, and **smart home integration may only amplify an existing mechanic, never add one.** Framed that way it is like playing a board game with nice components, and strategy is identical in a bare apartment. Framed the other way, it forks the game into two.

---

## Assumptions and Dependencies

### Open items

**Nothing is phase-blocking.** Two of the original blockers closed on inspection or were deferred with their mitigation set intact; one new item opened as a consequence of the round-length decision.

#### OI-1 · Stairs safety vs. the tracking model — **deferred**

**Suppressing Surge in a stairs zone requires the app to know where the target is. The tracking architecture guarantees it cannot.** The system knows only who was near a scanner, and stairs carry no markers and are never counted.

**Deferred deliberately.** The four highest-value stairs mitigations do not depend on detection and all ship in v1: **no markers in stairs zones** (editor-enforced), **no timed routes crossing stairs**, the **hard no-running rule**, and the **host acknowledgment at tagging time**. That is a real mitigation set, not a gap papered over.

**The condition on the deferral: it stops being deferrable at public launch, not at v1.** A controlled friend group is a different exposure from an App Store listing, and this is the *Lemmon v. Snap* shape — a design must never reward the dangerous act.

**When it is picked up, the two halves are not equally hard.** Suppressing Surge genuinely needs server-side knowledge of the target's position and stays blocked. **The lamp auto-raise does not** — it is your own phone reading your own accelerometer and raising your own lamp, reporting nothing to anyone, violating no tracking rule. Its only obstacle is *do not build a classifier*. Expect the lamp half to be cheap and the Surge half to stay hard.

#### OI-2 · Closed

The "Deactivated Person Found" trigger was never fakeable — the finding rested on a rendering rule being read as a radio rule. See **F-011**. No rule amendment was needed. Both meeting triggers survive.

#### OI-3 · Closed — the thin first meeting is by design

Traced material at the first meeting: one System Integrity number · anonymous, error-injected room counts remembered by whoever camped the Terminal · Egress attendance as a count · unverifiable check-in and chain claims · physical eyewitness observation. The first meeting is thin; the second is rich.

**Accepted as intended.** Players can vote **Skip**, and the unanimous *Ready to vote* control lets a meeting with nothing in it end in seconds. **Choosing to do nothing is a legitimate move**, and a meeting that produces no revoke is a valid outcome rather than a wasted phase. The risk of an early near-random revoke is real but is priced in: it is part of what makes the second meeting rich.

**No mitigation is being built.** Watch the *Ready to vote* skip rate at second meetings — frequent use there, not at the first, is the signal that something is actually wrong.

#### OI-6 · Ghost idle time at a ~25-minute round — **open, needs playtest**

**Reopened by the round-length decision.** Ghost mode activates only when the first meeting *ends*, so a player revoked at minute 3 sits with a dark phone until then — around 3 minutes at a 15-minute round, but **five to ten minutes at ~25**. That is the failure state ghost mode was built to solve, and the ~25-minute decision partially reopens it.

**The constraint is structural and there is no clean fix.** Ghost mode cannot activate before the living know you are out, or the sequencing rule breaks — *there is never a window where a ghost knows something the living do not*. And phase 1 must stay genuinely empty, because anything rendered on that screen would glow or be readable by whoever finds you.

**The only lever is meeting frequency.** Track it directly: measured revoke-to-ghost-mode time is in the metrics table, and if it runs long, shortening the meeting cooldown is the first thing to try.

#### OI-7 · Android's torch — **resolved, and it was overstated**

**The original framing was wrong.** It claimed graduated light is the core mechanic and therefore Android diverges in the most important dimension. **The torch is not in the light ladder at all.** Tier 0 is the **dim amber screen lamp**, Tier 1 is the paper snoot, Tier 2 is a manufactured snoot. Screen brightness override works identically on both platforms, so the core mechanic — *the app is the light source, light is game state* — is unaffected.

**What Android actually degrades is an enhancement.** `setTorchModeOn(level:)` is fine-grained on every iPhone in a decade; Android exposes strength levels only on hardware reporting multiple levels, binary below that. The torch provides forward illumination for navigating, not a signalling surface.

**Resolved by the Tier 1 end cap** (see Fear Mechanics → the light ladder): where software cannot dim the LED, paper does. **Accepted consequence:** on those devices the app can switch the torch but not ramp it. That is fine, because **every light event in the game is a screen event** — Surge, ambient flicker, the blackout at contact, the lights-up snap.

**Small footnote it leaves:** the ladder becomes slightly platform-dependent. Tier 0 remains complete everywhere *because the torch is optional*, but a player who wants the torch on binary hardware needs Tier 1.

*This is the third finding today produced by treating two adjacent things as one — see the note under Assumptions.*

#### OI-4 · The hidden-mode compensating lever

Deliberately parked for playtest. `Hidden` strips Insiders of coordination, so expect Residents to be stronger; the lever (more Insiders, shorter Revoke cooldown, longer meeting cooldowns) is a playtest call.

#### OI-5 · Settings interior

The list exists; nothing sits behind any row. **Only the lamp control and *About* actually need content** — the rest are meant to be locked, which is itself the design.

### A review discipline, learned the hard way

**Three separate findings in this project came from treating two adjacent concepts as one thing**, and two of the three were wrong in the alarming direction:

| Conflated | Actually |
|---|---|
| "Revoked phones become invisible" | A **rendering** rule read as a **radio** rule. The finding built on it dissolved entirely |
| "The flip needs ±50 ms" | **Delivery latency** conflated with **device skew**. The number was invented and four times tighter than needed |
| "Graduated light is the core mechanic, so Android degrades it" | **Screen lamp** conflated with **torch**. The torch is not in the light ladder |

**The design document is dense enough that everything is adjacent to everything.** When a claim takes the form *"X requires Y,"* check whether it is actually *X adjacent to Y*. A single-round trace catches these automatically — it is how the first fourteen findings were produced.

### Assumptions index

Every `[ASSUMPTION]` in this document, in one place. **All are numbers, and every one needs a playtest.**

| Tag | Assumption | Risk if wrong |
|---|---|---|
| A-1 | **Egress timer default 120s** | Host-configurable, so a wrong default is a bad first round rather than a broken game. Too short and Egress is uncontainable in a large house; too long and it is never a threat in a small one |
| A-2 | **Initial Revoke/Egress cooldown 45s** *(superseded by D-132: every Insider cooldown starts the round at half its duration)* | Too short and the round's quiet opening is destroyed; too long and the first interval is inert |
| A-3 | **Revoke steady-state cooldown, Egress cooldown, Access pool** | Core pacing. Three numbers, each independently tunable by design |
| A-4 | **Order size `K` per Resident (D-129; first written as 7 at 1/1/3/2), ~25-minute round** | The primary content lever. The round figure is an estimate to adjust from live play, not a derived number |
| A-5 | **~25s travel between markers** | Only used to derive the *default* Egress timer and the Subroutine-time floor. **Both are now host-tuned or behaviour-driven, so nothing load-bearing rests on it.** Varies enormously by house — an apartment and a three-storey house are not the same game |
| A-6 | **Unlock delay 20–120s** | The fog dial, and **coupled to round length** — it must be re-derived whenever the round estimate moves. Start at the short end |
| A-7 | **Staleness band thresholds and jitter magnitude** | How honestly the map admits its age |
| A-8 | **Error injection rate** | Too much and the map is unreadable; too little and a dropped count is a revocation alarm |
| A-9 | **Meeting cooldown** | Governs how often the map is wiped, **and it is the only lever on ghost idle time** (OI-6) |
| A-10 | **Discussion 90s / voting 45s** | Lobby settings, so cheap to be wrong about |
| A-12 | **Compose Multiplatform can blank the lamp in the same frame as contact** | **Gates the stack.** Skia→Metal shader stalls are its most-documented iOS frame-drop source. E1 proves or disproves it; mitigation is pre-warming the draw path. Fallback is Flutter |
| A-13 | **±150 ms coordinated flip skew reads as one event** | Replaces an earlier ±50 ms figure that was invented rather than derived. Too loose and the snap reads as a cascade; the real constraint is clock-offset accuracy, not delivery |
| A-11 | **Sync Pulse: 4 pulses in a row** *(owner-ruled in D-298: four consecutive pulses with no miss)* | Too few and containment is trivial; too many and it cannot be completed inside the Egress timer by two people who just ran across a house |

### External dependencies

- **Trademark clearance opinion** ($500–1500) before anything public. The 2026-08-15 knockout search was clean but is not an opinion.
- **Domain:** take `someoneishome.game` / `.app` / `playsomeoneishome.com` (~$20). **Availability has not been re-checked since the rename** — the old `guestnetwork.*` findings no longer apply.
- **LLC and general liability insurance** before shipping publicly or selling anything. Add product liability the moment physical goods ship — **the accessory whose purpose is making the room darker is the real exposure, more than the stairs rule.**
- **DMCA agent registration** ($6) once any UGC surface exists.
- **Work-for-hire art contract** assigning copyright and warranting originality.
- **iOS NFC spike** (30 minutes) against current iOS.
- **A dark house and eight people.** The binding dependency for every number in this document.

### Reference artifacts

Two working prototypes exist from the 2026-08-15/16 session and should be consulted before building the corresponding epics — they are more specific than prose can be about the look and the feel.

| Artifact | What it holds | Feeds |
|---|---|---|
| **Device mockup** — 26 screens, launch through endgame | The springboard, the status bar, the app states, the incoming-call screens, the amber palette in situ. **Its working lamp dial is a fossil — do not build it** (D-168) | **E3**, and the art direction |
| **Diagnostics bench** — ten playable subroutines | Every subroutine's actual interaction, including the light-signature differences | **E6** |

Sources are checked in at `_bmad-output/brainstorming/brainstorm-engine-selection-2026-08-15/artifacts/`; live URLs are in that folder's `README.md`. **The bench is where the design rule *comparing quantities is perception; adding numbers is computation* came from** — it was learned by playing, not by reasoning.
