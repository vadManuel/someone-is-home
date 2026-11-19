# Someone's Home: decisions waiting for the owner (2026-11-17)

Main is at `a0cb0aa`. This pack is read-only: nothing was edited or built while writing it.
Paths are shortened: **GDD** = `_bmad-output/planning-artifacts/gdds/gdd-someone-is-home-2026-08-16/gdd.md`, **DL** = `decision-log.md` and **EP** = `epics.md` in the same folder.
Code paths are relative to the repo root.

**Leak test used throughout:** does any phone's screen, frame count, frame size, light or buzz differ by role? If so, an observer can learn who is an Insider.

## The calls in one table

| # | Question | Recommendation |
|---|---|---|
| 1 | When ARMED ends | **C:** 45 s after the hold, or earlier when a meeting is called. Silent. |
| 2 | Arm window and containment | The window ends silently when a meeting is called. Build containment on GDD:919's clock-offset mechanism. |
| 3 | Egress progress for the living | **Keep both real,** and reword GDD:545/:1052. |
| 4 | Host lock during an Egress | **Pause the Egress while the house is off the air** (D-133's rule). |
| 5 | Report needs about a 2 s hold | **Keep it provisionally.** Confirm after the pass-by test. Change "touch" to "hold" in the report copy. |
| 6 | Seven provisional calls | Confirm six. **D-234: bring the opening banner back** (it is a D-118 light event). |
| 7 | Handshake timing ungraded | **Ship with count-only grading for the first playtests**, then grade the rhythm's shape. |
| 8 | Two-phone checks | The 30-minute script is at the end. |

---

## 1. When the REVOKE tile leaves ARMED (D-239, `765adf3`)

**What the docs say**
- GDD:322: *"Insider arms a Revoke in-app. Silent and invisible; nothing changes on screen for anyone."*
- GDD:323: *"Arming opens a **45s window**."*
- GDD:325: *"The Insider's device gives **no feedback whatsoever**."*
- M9, GDD:416-417: *"No revoke ping… no visible animation when arming or landing"* and *"No actor-side feedback on firing any ability."*
- EP:278 (story 9.1): *"Arm silently (nothing changes on any screen), 45 s window."*
- DL:2258 (D-142): *"nothing moving on any screen, a 45s window, the cooldown spent at the moment of arming… no cancel."*
- **Contradictions:**
  - D-216/D-231 (DL:5733-5735) built ARMED as "input echo". That already strains GDD:322's "nothing changes on screen", and D-239 itself calls ARMED an asymmetry (DL:6176-6177).
  - GDD:1154 and EP:280 say the opening cooldown is **45 s**. D-132 (DL:1906) and the code say **half**, which is 30 s.

**What the code does now**
- `ui/.../Flow.kt:1371-1374` `armRevoke()` sends `ArmRevoke` on every hold. It sets ARMED only when `revokeClock` is not running.
- `ui/.../PublicCooldown.kt:72-77` `revokeShown`: once ARMED is set, the tile shows it until the round ends.
- `core/.../Rules.kt:631-641` `revokeArmed`: a hold while the cooldown runs changes nothing, silently. Otherwise the seat is added to `cooldownArmed` and its clock restarts at 60 s.
- Nothing expires the arm. It clears only on a contact (`Rules.kt:1052`) or when the seat is lost (`Rules.kt:1673`).
- `Flow.kt:2182`: `AbilityFired` does nothing on screen.

**Options**

| | ARMED ends | Play | Leaks | Lamp/light |
|---|---|---|---|---|
| A | On `AbilityFired(true)`, then the seat's own 60 s cooldown is drawn | The Insider gets confirmation at contact | **Breaks M9.** The tile changes at the moment the target's lamp goes dark. A private cooldown on one role's tile, while every Resident's reads READY, is a resting tell. `AbilityFired` would also need to name the ability. | None on the lamp, but the screen changes at contact. |
| B | At the next meeting's end, silently | A spent Revoke still reads ARMED until the meeting. The Insider may hold and touch again for nothing. | None: the tile rejoins the public clock at a moment every phone sees. | None |
| **C** | **45 s after the hold**, on the phone's own clock. The house disarms at the same moment. From 45 s to 60 s the tile shows the public clock, and a hold in that span fills and echoes nothing. | Matches the GDD's own 45 s window, so a botched stalk is now bounded. Needs a change in `core`. | None, if the expiry sends nothing to anyone (rule 1). The tile never changes at contact. | None |
| D (not in D-239) | No echo at all; the tile only ever shows the public clock | The Insider cannot tell whether a hold took (READY already means the house will accept it) | The cleanest option: no role asymmetry on page 2 at all | None |

**Recommendation:** C, with the meeting rule from question 2. It is the design's own window, it is silent at contact, and every bar it draws is public.

---

## 2. The 45 s arm window, and containment (D-232)

**What the docs say: the arm window**
- GDD:323, GDD:1155 (*"Revoke arm window | 45s | Decided"*), EP:278 and D-142 (DL:2258) all name the window.
- **The docs are silent on two points:**
  - **Meetings:** whether an armed Revoke survives a meeting. D-185 (DL:3611) says *"Calling a meeting terminates every ability effect currently running… Only the Egress survives it."* No document says whether an arm counts as an "effect". D-184 (DL:3585) resets the *clocks* to half at meeting end.
  - **Expiry:** whether expiry is silent. GDD:418 implies it must be at least for sound: *"A cue firing for a subset — a cooldown expiring… leaks role."*

**What the docs say: containment**
- GDD:371 and EP:286 (story 9.8): *"both phones pulse haptically in unison off a server-scheduled timestamp; both players tap on the beat 4 times"*, with a 2-3 s lockout after a failed attempt.
- GDD:919 and GDD:1300-1305 specify how: *"Each client measured its clock offset at join… **One mechanism, three uses:** the lights-up snap, perimeter arming, and Sync Pulse."* GDD:1340 assigns clock-offset measurement to epic E2.

**What the code does now**
- Arm window: none. The arm lasts until a contact or the seat being lost (Q1 sites). The arm also survives a meeting (`PublicCooldown.kt:66-67`, D-239 item 4).
- Containment:
  - No screen has a tap control. The widget (`ui/.../ScreensWork.kt:860-880`) only shows ON or OFF THE BEAT.
  - `core/.../Egress.kt:218` `onTheBeat` grades taps against `egress.firedAt` in absolute house ticks, but no house time is sent to phones. `EgressOpened.remaining` is a duration.
  - The bench's taps carry no anchor and always miss (`app/src/cheats/.../RoundCheat.kt:526-542`).
- **Result: no Egress can be contained on real phones today.** Every Egress the Insiders fire wins for them.

**Options: the window**

| | Meeting and expiry | Play | Leaks | Light |
|---|---|---|---|---|
| **a** | **A meeting call ends the arm (D-185). Expiry is silent to everyone; the Insider's tile goes back to the public clock.** | The Insider re-arms after the half cooldown that follows the meeting. A Revoke never lands on a later shift with no traceable cause. | None: ARMED clears at a moment every phone sees (`MeetingCalled`/`MeetingEnded`) or at 45 s on the Insider's own phone. | None |
| b | The window pauses during the meeting and resumes after | The Insider keeps an arm through a meeting | Fine for leaks, but it contradicts D-185's reasoning | None |
| c | The window keeps running through the meeting | It nearly always expires during the meeting (90 + 45 + 15 s), so this is option a without the clarity | None | None |

**Options: containment**

| | How | Play | Leaks | Light |
|---|---|---|---|---|
| A | Add a tap control after the node scan. The Egress push carries "the first beat in N ms". | Simplest. The phones agree to within the push's travel time. | The same frame on every phone. The answer stays one shape (`SyncPulseAnswered`). An Insider at a node can tap too, which the design intends. | Haptic. The screen stays dark. |
| **B** | **GDD:919's clock offset measured at join**, then absolute beat times. Same tap control as A. | The design's mechanism. It also fixes the D-243 relaunch times (E-R2-4) and the "snap" flips. | Same as A | Same as A |
| C | The house sends a "buzz now" frame per beat to the seats at a node and grades taps on arrival | No clock on the phone at all. Wi-Fi jitter of 10-100 ms against a 400 ms window (`Balance.SYNC_PULSE_WINDOW`). | Only seats at nodes get frames. That reveals position, not role. | Same as A |

**Recommendation:** use window option **a** (a meeting call ends the arm, and expiry is silent) and containment option **B**. Containment is currently a guaranteed Insider win, so schedule it before the first real playtest.

---

## 3. Egress progress for living players (D-243, `0cde8e0`)

**What the docs say: they contradict each other**
- GDD:545: the couch gets *"Both bars — Resident System Integrity *and* the real Egress progress, which no living player of either role can see."* GDD:1052 says the same.
- Against that:
  - GDD:375: *"The System Integrity widget becomes the Egress countdown."*
  - GDD:735: *"The one number Residents had becomes a countdown against them."*
  - GDD:1012 says the same.
- GDD:33: *"Residents can never see the house's progress."*
- No document says what "real" Egress progress is if it is not the countdown. Since the timer's length is known, a countdown already tells the living what fraction remains.

**What the code does now**
- The living widget shows the countdown text and a draining bar: `ui/.../ScreensWork.kt:866-873`, from `PanelModel.kt:1665` (`egressLit`) and `:1668` (`egressLeftText`).
- The couch shows the percentage: `PanelModel.kt:1885`, used at `ScreensOut.kt:314-315`.
- All of these are the same fraction, taken from `EgressOpened.remaining` and `EgressHeld.remaining`.

**Options**

| | Living see | Play | Leaks | Light |
|---|---|---|---|---|
| **A** | **Countdown and bar (as built)**. Reword GDD:545/:1052 so the couch's privilege is the percentage and true occupancy. | The bar repeats the number, so nothing is lost | None: identical on every phone | No change |
| B | Countdown only; remove the bar (D-243's offer, DL:6366) | Cosmetic. The number already says what the bar says. | None | Slightly fewer lit segments on living phones, the same for every role |
| C | Countdown only, and the couch gets something new as "progress", such as containment attempts or held beats | Gives the couch real extra information; it is new design | Watch it: attempt counts must never show which seats tapped | No change for the living |

**Recommendation:** A, and fix the GDD wording. A countdown that was deliberately wrong would make the win-condition timer unfair (F-009, GDD:998).

---

## 4. The D-230 host-lock exploit

**What the docs say**
- D-230 (DL:5692-5698): while the host is away the clock keeps wall time. *"Every deadline that fell due inside the gap… an Egress expiring… fires at the first step after the return… **That is the existing rule and it is not changed here.**"*
- D-133 (DL:1913): a meeting *"pauses the Egress timer — it never resets it."*
- EP:43 (story 0.9): *"Authority is the host device with no migration (v2), so this will happen."*
- **The docs are silent** on an Egress while the house is off the air.

**What the code does now**
- The host's lock stops the server: `app/.../LobbyOverTransport.kt:743-751`, from `4d2cb2b`/D-225.
- `app/.../TickDriver.kt:72-105` drops the backlog and skips forward to wall time.
- `app/.../HouseClock.kt:130-132` mints `EgressExpired` on the first step after the return. The Insiders win (`Rules.kt:182`).
- While the house is off the air nobody can report or contain, and the evasion notice that would name the host never arrives because the round is already over.
- **This also hands Resident hosts' accidental locks to the Insiders.**

**Options**

| | Rule | Play | Leaks | Light |
|---|---|---|---|---|
| **A** | **The Egress pauses while the house is off the air**, and the return sends `EgressHeld(remaining)` | An Insider host gains nothing by locking. A Resident host's accidental lock costs nothing. The host is still named at the next meeting (D-230). | None: the rule does not look at roles | None. Phones count down locally during the gap (D-243), then jump back up on the return, the same on every phone. |
| B | Freeze every round clock while off the air | The most consistent. Reverses D-230's "a ninety-second discussion has to be ninety seconds". | None | None |
| C | Keep it; make "the host never locks or leaves" a conduct rule (like D-010) | No code change. Relies on honesty, and accidents still decide rounds. | None in the system, but the exploit stays open to an Insider host | None |

**Recommendation:** A. It applies D-133's existing pause rule to the host's absence, and it removes both the exploit and the accident.

---

## 5. A report now takes about 2 s held (D-236, `523c460`)

**What the docs say**
- GDD:563: *"Report a deactivation | **Touch your phone to the revoked player's phone** | Same gesture, different outcome by state."*
- GDD:574 and EP:188 (story 5.14): if the gate fires at "nearby", the report becomes *"a through-wall detector for revoked players"*.
- **The docs are silent** on how long a contact lasts. D-236 (DL:5968-5971) admits the gesture is now a hold: *"if any copy says touch to report, hold is now the truer word."*

**What the code does now**
- `core/.../Contacts.kt:223` `REPORT_READINGS = 2`.
- `Contacts.kt:231` `REPORT_SPAN_TICKS` = 2.5 s.
- `Contacts.kt:167` the floor is -40.
- A contact (and so a Revoke) still needs only one reading from each phone.

**Options**

| | Rule | Play | Leaks | Light |
|---|---|---|---|---|
| **A** | **2 of the last 3 uploads within 2.5 s (as built)** | The finder holds the phone to a dark phone for about 2 s. A brush past no longer calls a meeting. The pass-by false-positive rate falls 10-25× (DL:5958-5962). | None: the same question for every reporter (role-blind) | The finder's lamp rests on the dark phone 2 s longer, then the meeting turns every lamp white anyway |
| B | Go back to one reading | An instant report, but a 30 cm pass-by can report (p = 10-36 % per 2 s pass at p = 0.05-0.2) | It brings back the GDD:574 through-wall/pass-by detector risk | None |
| C | Wait for the two-phone pass-by measurement, then pick between A and B by the measured p | Decided on evidence | Same as A or B | Same as A or B |

**Recommendation:** keep A now (it is already C in practice), confirm it after test step 9 below, and change any report copy from "touch" to "hold".

---

## 6. The provisional calls

| Call | Docs | Code | Options | Recommendation |
|---|---|---|---|---|
| **D-242: an out seat is never named for evasion** | D-187 says evasion is about the living hiding from the house. EP:322 says the notice names *"locking the phone, backgrounding… mid-round"*. **The docs are silent on out seats.** | `core/.../Rules.kt:1130` (`onTheNotice`), `:1444` (the gate filters out out seats), `326f3ed`. The out phone still sends the same bytes (rule 1). | (a) Confirm. (b) Name a seat that evaded while living even if it is out by the meeting. (c) Name every seat. None of these leaks a role, because the room already knows who is out (D-134). | **Confirm (a).** |
| **D-247: the inbox backlog is removed, and Notes have no names** | D-156 (DL:2563, :2574) refuses fake deliveries. `inboxSeed` was never set, so every phone was identical. | `ui/.../PanelModel.kt:1043-1050` (no backlog when housed), `:2176` `HOUSED_NOTE = "somebody was already at the marker"`, `74e2df8`. | (a) Confirm. (b) Seed a backlog per player (it disguises, but it is fake deliveries, against D-156). (c) Name a real seat in Notes (the app making the room's read). | **Confirm (a).** ⚠️ Separate from this: the orchestrator found that `RoleRevealed`'s size varies by role (the `line` field). Unit F1 is fixing it and has **not landed** at `a0cb0aa`. |
| **D-248: the NameDrop copy and flow, and JOIN's back arrow** | D-161 puts setup in the light. **The docs say nothing about NameDrop.** | `ui/.../NameDrop.kt:73` (`pastPerms`), `ScreensColdStart.kt:225`, `:308`, `:319` (the path rows), `:367` (JOIN's back arrow is always shown and goes to Perms), `a0cb0aa`. | (a) Confirm the copy and the flow. (b) Change the copy. (c) Also add a lobby reminder. | **Confirm (a).** Check during step 2 below that the four path rows match your phones' iOS wording. |
| **D-237: the house log names seats only when ROUND_SEED is set** | GDD:33 says the role is never confirmed, and the arming note refuses to name an Insider in a file. | `app/.../RoundAuthority.kt:106` `namesSeats = false` by default; `CheatRoot` sets it only when `benchSeed() != null`. `b35bba3`/`765adf3`. | (a) Confirm. (b) Never name a seat (always `A SEAT`). (c) Always name seats (console only). | **Confirm (a).** The Mac already knows the seed in a seeded round, and real rounds say `A SEAT`. |
| **D-240: `round.sh --by-hand` still gets the README's Insider seat** | DL:6235-6240 | `app/src/cheats/.../RoundCheat.kt:1003` `benchSeed`; `tools/one-phone/round.sh:260` (`ROUND_BY_HAND`); `326f3ed`. | (a) Confirm: seat 0 is always the Insider, and `ble-peer` can derive tokens. (b) Draw a secure seed with `--by-hand`, which is realistic but makes the Insider unknown and `ble-peer` useless. (c) Add a `--secure-seed` flag. | **Confirm (a).** The two-phone script depends on it. |
| **D-238: Replay flash timing 0.8 / 0.6 / 0.9 s** | GDD:592 says *"3–5 dots flash in order"*, with Bright light. GDD:624 says *"No twitch timing."* **The docs give no timing.** | `ui/.../ScreensSubroutines.kt:860-862`, `20409e3`. One pass lasts 3.2 s (3 dots) to 5.0 s (5 dots). | (a) Confirm. (b) Slower, about 1.0/0.8/1.2, for arm's length in the dark (a longer bright light signature). (c) Faster. | **Confirm provisionally** and feel it on a phone. |
| **D-234: the opening banner no longer shows at arming** | D-118 (DL:1686): *"Exactly two events dim the house"*, and one of them is the opening text. D-119: a swipe acknowledges it. | `ui/.../Flow.kt:2906-2907` `roundOpened`: a phone in the lobby goes to ARMED; the banner shows only elsewhere. `c5ab6ed`. | (a) Accept: no opening banner or dim; the role arrives in the Messages thread only. **This quietly removes one of D-118's two light events.** (b) After ARMED, the house pushes the opening banner to every phone at one moment, which brings back D-118's dim. (c) Show the banner on a CONTINUE tap (D-102: no buzz), at different moments per phone. | **(b)**. It keeps D-118's closed light vocabulary and is the same on every phone. Light is the owner's call. |

---

## 7. Handshake tap timing is not graded (a known gap, D-238)

**What the docs say**
- GDD:601: *"The phone buzzes a pattern; you tap it back. Screen stays near-black."* EP:200 says build it first.
- GDD:624: *"No twitch timing… A 200ms fail window reads as unfair."*
- **The docs are silent** on how strictly the rhythm is graded.

**What the code does now**
- `model/.../SubroutineQuestion.kt:928-942`: the KDoc says *"a rhythm returned at any tempo passes"*.
- `core/.../Subroutines.kt:205`: the expected answer is the tap ordinals 0..n-1.
- `Subroutines.kt:237`: graded by equality.
- So any completed Handshake passes, for a Resident and for the Insider's fake alike.

**Options**

| | Grading | Play | Leaks | Light |
|---|---|---|---|---|
| **A** | **Count only (as built)** | The Subroutine is "tap N times". Easy, so System Integrity rises faster. | None: the Insider's fake is graded the same | Dark, unchanged |
| B | Grade the shape: which gaps are long and which are short (ordinal), from tap times sent in the entry | A real rhythm with a generous tolerance and no twitch timing. Needs a change to the entry's wire shape. | The entry must be the same length for every role. Grading stays the same for the fake. | Dark, unchanged |
| C | Grade each gap within ±40 % of the dealt gap | Closest to "by feel". Risks breaking GDD:624. | Same as B | Same as B |

**Recommendation:** ship A for the first playtests and log it. Do B if players report Handshake as trivially easy.

---

## 8. What only two phones can verify, and a 30-minute script

### What is not yet verified on a phone

| # | Check | Decision | Script step |
|---|---|---|---|
| 1 | The Revoke lands, and the cooldown is respected | D-239, D-237 | 5, 8 |
| 2 | 30 cm pass-by false positives at -40, 100 ms and 2-of-3 | D-233, D-236 | 7, 9 |
| 3 | Contact hit-rate retest | D-233 | 6 |
| 4 | The line stays above the keyboard | D-235 | 3 |
| 5 | ARMED on guests | D-234 | 4 |
| 6 | Clocks and countdowns | D-239, D-243 | 4, 5, 10c |
| 7 | Bluetooth off on a phone that is not the host | D-222/223 | 10a |
| 8 | An evasion report survives the app being ended and relaunched | D-245 | 10b |
| (extra) | NameDrop step, once per install | D-248 | 2, 10 |

### Before the second phone arrives (phone A only, about 10 min)
1. `tools/one-phone/round.sh check`. **PASS:** a team, the phone with `developer mode enabled`, the Simulators, and `ble-peer: built`.
2. `tools/one-phone/round.sh install`. **PASS:** `installed.`
3. On A:
   - NameDrop off (Settings → General → AirDrop → Bringing Devices Together).
   - Date & Time: Set Automatically.
   - Bluetooth on.
   - Auto-Lock 30 s.
4. Open three terminals:
   - T1 for `round.sh`.
   - T2 for B's console.
   - T3 for `grep -c`.
5. Note B's CoreDevice id once B is plugged in: `xcrun devicectl list devices`.

**Roles:** A hosts (seat 0) and is the Insider under seed 20261150. Two Simulators fill seats 1 and 2. B joins by hand as seat 3, so the round has 4 seats, the same deal `OnePhoneVectorsTest` pins.

### The 30 minutes, in priority order

Before you start, know where to cut:
- **If behind at 0:12:** skip step 6.
- **If behind at 0:15:** skip step 7 and go straight to step 8.
- Steps **8, 9 and 10 matter most.**

**0. (0:00–0:05) Install on B.** Plug B in and run `APP_DEVICE_NAME=<part of B's name> ./run-app.sh device playtest`. On B, set Date & Time automatic and Bluetooth on. **Leave NameDrop as it is:** step 2 tests the screen that asks for it.

**1. (0:05) Start the round.**
- T1: `tools/one-phone/round.sh start --no-install --by-hand --seats 3`. Wait for `seat 2 is seated`.
- T2: `xcrun devicectl device process launch --console --terminate-existing --device <B> home.someoneshome.app 2>&1 | tee /tmp/someones-home-one-phone/latest/phoneB.log`

**2. (0:06) NameDrop step (D-248).** On B, follow setup.
- **PASS:**
  - NAMEDROP appears after PERMISSIONS (or after boot, when everything is already granted).
  - The four path rows match B's Settings exactly. Switch it off, then press DONE, which goes to JOIN A HOME.
  - JOIN's back arrow goes to PERMISSIONS, which shows a NAMEDROP row reading OFF.
- **FAIL:** JOIN is reached without the step, or the path wording differs from Settings (write down B's wording).

**3. (0:08) Keyboard (D-235).** B joins the home and types its one line.
- **PASS:**
  - The field and HAND IT OVER stay visible above the keyboard the whole time.
  - The caption and promise rows step aside.
  - The line can be handed over without closing the keyboard.
  - A's lobby reads 4 seated and `4 of 4 handed theirs over`.

**4. (0:10) LIGHTS OUT on A. Note the time.**
- **PASS, ARMED on the guest (D-234):** B shows PERIMETER ARMED in the same second as A.
- **PASS, seat count (D-247):** PERIMETER ARMED reads `4 RESIDENTS`.
- **PASS, role:** A's log has `WHAT THIS PHONE IS — INSIDER`.
- **PASS, clocks (D-243):** both status clocks show each phone's real time in 24 h, never `20:41`/`21:07`.
- **FAIL:** B stays on the lobby or the springboard, or the screens arrive more than about 1 s apart.

**5. (0:10–0:11) Page-2 parity and an early hold (D-239).** Both phones swipe to page 2 and are held side by side (take a photo).
- **PASS, parity:** both tiles read COOLING DOWN with bars filling at the same rate. Only the words differ.
- **The early hold:** at about 10 s, A holds REVOKE.
  - **PASS:** A's tile stays COOLING DOWN, with no ARMED.
  - **PASS:** the host console logs `[house] REVOKE ARM — SEAT 0 · COOLING DOWN (READY IN ~20S)`.
- **The early touch:** touch A to B back to back for 2 s.
  - **PASS:** both log `PUSH … A TOUCH, ANSWERED`, and B stays lit.
- **READY:** at about 31 s after LIGHTS OUT, both tiles turn READY within about 1 s of each other. A reads `READY . HOLD TO ARM`; B reads `READY . ALL NOMINAL`.

**6. (0:11–0:13) Hit-rate retest, nobody armed (D-233).** Make 10 quick, real back-to-back touches, about 6 s apart (the pair debounce is 5 s; count aloud).
- Run `grep -c "A TOUCH, ANSWERED" …/phoneB.log` before and after.
- **Record n/10.** The last measurement was 65-80 % with a 250 ms scan, so expect at least 8/10.
- Also record one `[radio] TALLY` CALLBACKS figure. Expect about 50-100 per peer per 10 s.

**7. (0:13–0:15) Contact pass-by at 30 cm (D-233).** Put B on a table. Carry A past it at about 30 cm (a hand-span) at walking pace, 10 times, about 6 s apart. Then hold A still at 30 cm for 10 s.
- **PASS:** zero new `A TOUCH, ANSWERED` from the passes.
- **Record:** the count from the 10 s stand, and the best `HEARD` RSSI. Any contact from a pass would have been a Revoke if A had been armed.

**8. (0:15–0:18) The Revoke (top priority).** With the tile at READY, A holds REVOKE.
- **PASS, the arm:** A's tile reads `ARMED . TOUCH THEIR PHONE`, and the console logs `REVOKE ARM — SEAT 0 · ARMED`.
- Touch A to B back to back for 2-3 s.
- **PASS, on A:** A logs `PUSH … ABILITY FIRED · COOLDOWN STARTED=true`. A's screen and light do not change; its tile still says ARMED, which is the known open item from question 1.
- **PASS, on B:**
  - B logs `THE HOUSE WITHDREW MY LAMP`, then `[radio] SCAN OFF — ADVERTISING ONLY FROM HERE`.
  - B goes dark at the touch.
  - From then on B has no `HEARD` lines, and its `TALLY` reads `SCANNING NO · ADVERTISING YES`.
- If the touch did not register, the arm is still held, so touch again.
- **FAIL:** `REFUSED`, or a plain `A TOUCH, ANSWERED` after an `ARMED` console line.

**9. (0:18–0:21) Report pass-by and the 2 s hold (D-236).** Wait 10 s after the Revoke.
- **Brush:** touch A to B and pull it away in under 1 s, once.
- **Pass-by:** carry A past B at 30 cm, 5 times.
- **PASS:** neither the brush nor the passes produce a `MEETING CALLED` line.
- **The hold:** hold A on B, touching, for 3 s.
- **PASS:** `PUSH … MEETING CALLED BY SEAT 0 · RevokeReported` arrives on both phones within about 3 s. (An Insider reporting their own target is allowed, D-236 item 4.)
- **Record:** how long the hold took.
- Press Ctrl-C in T1 and T2.

**10. (0:21–0:30) Second round, scripted: B's evasions are named.**
- T1: `tools/one-phone/round.sh start --no-install --seats 3` (without `--auto-lights-out`).
- T2: relaunch B's console to a new file, `…/phoneB-r2.log`.
- B joins. **PASS (D-248, once per install):** no NAMEDROP step this time.
- When 4 are seated, press LIGHTS OUT on A.
- Both steps below happen during act 2. You have at least 31 s before the bench's Revoke on seat 1.

**10a. Bluetooth off on B (not the host).** On B, go to Settings → Bluetooth: off, wait 5 s, on, then back to the app.
- **PASS, on B:**
  - Exactly one `[radio] FAULT — … · PERSON · THE HOUSE IS TOLD ONCE`.
  - `ADVERTISING` returns.
  - Nothing on B's screen moves.
- **PASS, on the host:** `RADIO FAULT — SEAT 3 · PERSON · NAMED AT THE NEXT MEETING`. There may be one line or two, because going to Settings also counts as leaving the app, unless both land on the same beat.

**10b. The app ended and relaunched (D-245).**
- On B: swipe up to Home, wait 3 s, open the app switcher and swipe Someone's Home away. Wait 5 s.
- T2: `xcrun devicectl device process launch --console --device <B> home.someoneshome.app 2>&1 | tee …/phoneB-relaunch.log`
- **PASS:**
  - B logs `1 REPORT(S) OWED FROM BEFORE THIS PROCESS — THE NEXT BEATS TELL THE HOUSE (D-245)`.
  - B returns to seat 3 by its token, with no `JOINING FRESH` and no fifth seat.
  - The host logs one `RADIO FAULT — SEAT 3 · PERSON …` for this leave. `HEARD AGAIN` on a re-send is fine.
- If B lands on JOIN instead of its seat, write it down. That is a finding, not your error.

**10c. The bench's act-3 meeting.** The Revoke lands on Simulator seat 1, and seat 2 reports. Check B in when it rings.
- **PASS, the notice:** every phone logs `[round] PUSH … NOTICE — SEAT 3 UNREACHABLE` before `PHASE Discussion`. No notice names seat 0, 1 or 2.
- **PASS, countdowns (D-243):** with A and B side by side (photo), both discussion countdowns start at about 1:30 and stay within 1 s of each other.

**Afterwards:**
- Put Auto-Lock back to your usual setting.
- Send the run folders under `/tmp/someones-home-one-phone/runs/`, the `phoneB*.log` files, and the numbers from steps 6, 7 and 9.

**Caveats (not run while writing this):**
- Step 10 mixes the scripted bench with a person's phone in seat 3. If the gate waits on B, check B in promptly. If the bench stalls, treat 10a/10b as passing on their log lines alone.
- Without a printed meeting card, step 10c is the only meeting where B is still living. After step 8, D-242 means B is never named.

## 9. Added 2026-11-18, after the regression runs and review #3

Fixed and pushed since this pack was written (all with tests that fail on the old code, full check green, not yet on a phone):
- **Names shifted after a seat sat out** (D-274): in a round armed without one seat, every later name moved up one, and a vote for "FINCH" restrained someone else. The roster is now indexed by seat.
- **An unnamed seat could not be voted for** (D-277), and **two people who typed the same name** sent every vote for the second to the first (D-278).
- **Subroutine entries took 9 s on average, up to 24 s,** to reach the house, because one request rode each 1 s upload. Several now ride together in the same fixed slot (D-275); entries arrive in about 1 s. **Owner to confirm** with D-263.
- **Allocation** (D-276): host 1.98 → 1.48 MB/s and guest 0.84 → 0.66 MB/s at 6 seats, nothing on the wire moved. Still above rule 7's 0.5 MB/s; see 9e.

**9a. Out players' vote count.** The out phone shows "VOTES CAST 4 OF 4" while the voters read "0 OF 4 VOTED": it counts the live selections it is shown (D-117/D-134) and calls them cast. Options: **(a, recommended)** also send the out the locked count (`VOTE_PROGRESSED`) and draw it against the living count, which also mends the short denominator flagged in `Flow.kt`; (b) keep the wire and relabel the line (new copy); (c) hide the count until the vote resolves.

**9b. The ending's System Integrity when no meeting happened.** At 3 seats a round can end by parity at the first Revoke, before any meeting, and every phone's ending shows SYSTEM INTEGRITY "–", because the meter is only told at meetings. Should the ending always carry the final meter? (Recommended: yes, one value to every phone with the ending.)

**9c. The waiting phone is lit during a dark round (LIGHT).** A phone that joins while a round runs sits on the bright paper-coloured lobby screen ("ROUND IN PROGRESS / YOU JOIN THE NEXT ONE", then "WAITING FOR THE HOST" over the ending) for the whole round. In a dark house it is probably the brightest thing in the room. The light rules are yours.

**9d. Repeated names in the lobby (copy).** The desk accepts two people typing the same name. Votes now reach the right seat either way (D-278), but the room sees two identical rows. Refuse the second ("NAME TAKEN"?), mark it ("SAM 2"?), or leave it?

**9e. Rule 7 on the host.** What remains is mostly outside the game code: Ktor over kotlinx-io allocates a fresh 8 KB segment for most frames (~450 KB/s on the host at 6 seats, growing with seats), and every state change recomposes the screen (~270 KB/s). Both need a structural change (pooled transport buffers, or narrowing recomposition near the light rules). The 0.5 MB/s figure and the stack-gate cliff (1.5–3.0 MB/s) were themselves read with the in-app counter now shown to be wrong (D-276), so the first step is re-measuring the cliff on a phone with the validated method.

**9f. The Egress cooldown can lapse while its Egress is paused** (review #4, D-279). The cooldown (180 s) runs from the moment an Egress is fired, and a meeting pauses the Egress's 120 s timer but not the cooldown. Example: fired at 0 s, so the next is ready at 180 s; a meeting runs 10–190 s; the Egress resumes and is contained at 250 s; a second Egress can fire at 251 s. The code's own comment says the cooldown "outlives the timer by construction", and D-184 says a meeting must not fund the next Egress, but here the meeting does. Options: **(a, recommended)** push the cooldown back by however long the Egress was paused, so it always outlives its own Egress; (b) keep the behaviour and correct the comment. This is a timing value, so it is yours.

## Owner rulings, 2026-11-18 (gone through one by one)

- **Q1 + Q2 window: an armed Revoke lasts 20 s, or until a meeting is called, whichever comes first.** Expiry is silent: nothing is sent and the Insider's tile goes back to the public clock. This overrides the 45 s window in GDD:323/:1155 and D-142. (Option C, with 20 s in place of 45 s.)
- **Q2 containment: option B, with the clock offset refreshed every second** on the uploads every phone already sends, not measured once at join. The owner asked whether a one-time sync would drift over a round; the paper estimate was 0.05–0.1 s over 30 min against the 0.4 s window, so the offset is kept current. Every phone does the same thing. The house sends absolute beat times. Needed before the first real playtest.
- **Q3: option A.** Living phones keep the countdown and draining bar as built. Reword GDD:545/:1052 so the couch's privilege is the percentage plus true occupancy, not a hidden "real" progress.
- **Q4: option A.** The Egress pauses while the house is off the air (the host locked or left), as a meeting pauses it (D-133). On return every phone gets `EgressHeld(remaining)`. The host is still named at the next meeting (D-230).
- **Q5: decide after the two-phone pass-by test** (test step 9). The 2 s hold (2 of 3 readings in 2.5 s) stays as built until then; no ruling yet.
- **Q6 D-242: confirmed, with one exception: the host.** An out player who goes dark is not named, but an out *host* who locks or leaves is, because the whole house goes off the air with them. The owner also asked that the notice name the player, not the seat. The screen already does ("Resident MARCUS was unreachable this shift…", `noticeBody`); "SEAT 3 UNREACHABLE" is only the phone's log line. Found while checking: a seat with a blank name would read "Resident  was unreachable"; it should draw UNNAMED as the ballot does (D-277).
- **Q6 D-247: confirmed.** No filler backlog and no names in the housed notes. The owner asked whether Messages should open straight into the house thread, since it is the only sender. **Ruling: keep the one-row list** as a privacy step, because the thread holds the role line and a glance at a phone opening Messages must catch only the identical HOUSE row.
- **Q6 D-248: the NameDrop setup step is rejected.** The owner will not ask players to switch NameDrop off. The NFC-session idea was ruled out because iOS's scanning panel lights the screen. **New direction from the owner: replace Bluetooth with UWB (Nearby Interaction) for everything** — contact, report, presence — for precise distance and direction, so a Revoke reaches only the player intended even beside another, and contact can be at a distance short of the top-to-top hold that triggers NameDrop. Needs a two-phone UWB spike first; see the overnight note for the open questions.
- **UWB phones only; no Bluetooth fallback.** A phone without UWB must be told at launch that it cannot play because its hardware is insufficient. Detect it with Nearby Interaction's capability check (`NISession.deviceCapabilities.supportsPreciseDistanceMeasurement`). The screen's wording is to be drafted for the owner.
- **Q6 D-237: confirmed.** The house log names seats only in seeded (bench) rounds and writes "A SEAT" otherwise.
- **Q6 D-240: confirmed.** `round.sh --by-hand` keeps the README's Insider (seat 0).
- **Q6 D-238: kept provisionally** (0.8 s lead, 0.6 s lit, a step every 0.9 s). The owner will judge it by feel on a phone.
- **Q6 D-234: option (c).** PERIMETER ARMED gains a CONTINUE tap. The tap raises the opening banner with its dim (D-118/D-119) on that phone, at whatever moment its player taps, and does not buzz (D-102). The owner chose this over one simultaneous dim, knowing the room sees separate dims.
- **Q7: option C.** Handshake grades each gap between taps to within ±40 % of the dealt gap. The entry must carry tap timings at the same length for every role, and the Insider's fake is graded identically.
- **9a: remove the vote count from the couch entirely.** The owner: the couch already sees who is choosing what, so "VOTES CAST 4 OF 4" adds nothing. The couch's vote screen loses the count line. Nothing new goes on the wire.
- **9b: option (a).** The ending always carries the final System Integrity, one value to every phone.
- **9c: a round in progress cannot be joined.** The owner's rule, which replaces the lit-waiting-screen question. A phone that was not seated at LIGHTS OUT is refused at JOIN while a round runs, instead of being seated to wait for the next one. Phones seated at LIGHTS OUT that drop and relaunch still reconnect, since that is a rejoin rather than a new join. The refusal line on JOIN is to be drafted for the owner (today's copy is "ROUND IN PROGRESS / YOU JOIN THE NEXT ONE").
- **9d: option (a).** The lobby refuses a name already taken, case-insensitively, and the player types another. The "NAME TAKEN" wording is to be shown to the owner.
- **9e: option (a).** Re-measure allocation and the stack-gate cliff on a phone with the validated method, after the UWB change settles the traffic. No restructure until then.
- **9f: option (a).** When a paused Egress resumes, `egressReadyAt` is pushed back by the length of the pause, so the cooldown always outlives its Egress (D-184's intent).
- **D-275: confirmed.** Several requests share one fixed request slot per upload (with D-263).

### Build work these rulings create
1. Revoke arm window: 20 s or a meeting call, silent (core + the ARMED tile).
2. Containment: a clock offset refreshed every second on the uploads, absolute beat times, and a tap control after the node scan.
3. Egress pauses while the house is off the air.
4. The evasion notice also names an out host. A blank name is drawn UNNAMED in the notice.
5. CONTINUE on PERIMETER ARMED raises the opening banner and dim on that phone.
6. Handshake grades each gap within ±40 %, with tap timings carried at a fixed length.
7. The couch's vote count is removed.
8. The ending carries the final System Integrity.
9. JOIN refuses a new phone while a round runs (rejoin still works), with wording to approve.
10. The lobby refuses a taken name, with wording to approve.
11. The Egress cooldown is pushed back by the pause.
12. UWB replaces Bluetooth: the spike first, then a plan. Unsupported phones are refused at launch, with wording to approve.
13. GDD wording: GDD:545/:1052 (couch's Egress view), GDD:323/:1155 (arm window 20 s).
- Still open: Q5 (report hold), decided after the two-phone pass-by test, which UWB now changes.
14. UWB rulings U1–U4 (below): the Revoke is a 10 cm flyby bubble, distance only, and no work around walls or bodies.

## Owner rulings on the UWB spike, 2026-11-18

Results: `uwb-spike-results-2026-11-18.md`.

- **U1, the Revoke reach: a 10 cm bubble around the phone, and a flyby counts.** A Revoke fires when the two phones read within 10 cm. They do not have to stay touching. The owner prefers a flyby to a held touch.
  - Facts the build must respect:
    - Readings arrive every 150 ms. A hand crossing a 20 cm-wide bubble at 1 m/s gives one or two readings inside it, and a faster pass can give none.
    - The reading wobbles by about ±3 cm, so the bubble's edge sits between about 7 and 13 cm.
    - Side by side reads 8–9 cm, which is inside the bubble.
    - A top-to-top flyby was not tested against NameDrop. Only a held top-to-top touch fired it.
  - Under U3, these misses and edge cases are part of the game, not something to engineer away.
- **U2: 6.7 readings a second is enough.**
- **U3: the walls are part of the game.** UWB cannot tell rooms apart, and a body in the way reads long. The owner's original intent stands: the technology's uncertainty is part of the game, not something to work around.
- **U4: direction is dropped.** Rules use distance only.
- **U5: iPhone first.** The owner has no Android phone, so Android stays on the roadmap and nothing is built for it now.
  - Cross-platform UWB is possible on paper: Android 16's Ranging module ranges with an iPhone through Apple's Nearby Interaction Accessory Protocol.
  - It is unproven for a full mesh, because an Android responder serves one initiator at a time.
  - The one thing to keep: the house brokers each pair's UWB setup over the existing connection rather than through an iOS-only channel, so an Android path can be added later.

## Owner rulings, 2026-11-18, later

- **D-283 out host: record the host's seat.** The house notes which seat the host took at LIGHTS OUT, and uses it only so the unreachable notice can name an out host. Everyone in the room already knows who the host is.
- **D-281 return buzz: keep it.** One short buzz on every phone when the house comes back on the air during an Egress.
- **Page 2 parity means the same structure, not the same content.** The owner's words: the second page should be *structurally* the same for Insiders and Residents, but different in text and function. Two changes follow:
  - The Insider's page never says READY when the Insider's own ability is not ready.
  - The Insider's tiles dim while an ability the Insider used is cooling down.

  This narrows D-142's *not shorter, dimmer or otherwise distinguishable* to the page's layout, frames and wire. A Resident's page is unchanged. It answers the 20–60 s arm gap: the Insider sees COOLING DOWN, dimmed, instead of READY.
- **Wording approved:**
  - **No UWB**, at launch: "THIS PHONE CAN'T PLAY" / "Someone's Home needs Ultra Wideband: iPhone 11 or later, not iPhone SE."
  - **JOIN during a round:** keep today's "ROUND IN PROGRESS" / "YOU JOIN THE NEXT ONE".
  - **A taken name in the lobby:** "NAME TAKEN", with no second line.

## Owner rulings, 2026-11-18, on D-286's questions and Q5

- **A landed Revoke takes ARMED down at once, and both phones buzz Long.**
  - At the hit, the Insider's tile goes straight from ARMED to COOLING DOWN, instead of holding ARMED until 20 s from the arm.
  - The Revoked phone and the Insider's phone both give the **Long** buzz.
  - Today neither phone buzzes when a Revoke lands.
- **The dimming of the Tier-2 names stays subtle.** It keeps the existing `Ramp.Dim`, with no new brightness level.
- **Q5, the report: within 30 cm, held for 2 s.**
  - The reporting phone shows a circle that fills over the 2 s, and then the meeting's call.
  - Leaving 30 cm before the circle is full cancels the report.
  - This replaces Bluetooth's RSSI ≥ −40 dBm in 2 of 3 uploads (D-236).
- **The Insider who did the Revoke may report their own victim (option a).**
  - The house's 5 s debounce between the same two phones (`ContactGate.DEBOUNCE_TICKS`) becomes a deliberate rule: a grace after a Revoke during which neither phone of the pair can report the other.
  - After the grace, anyone can report, the Insider included, and their circle warns them first.
  - A neighbour already within 30 cm of the victim can report at once. That is accepted under U3.
- **D-287, the door at the ending: confirmed.** Once the ending screen shows, the round is over, so a new phone may join again and waits on WAITING FOR THE HOST until NEW ROUND.
- **The Nearby Interaction wording is approved:**
  - **`NSNearbyInteractionUsageDescription`** (iOS's one-time prompt): "Someone's Home measures the distance between phones in the room. It is how the game sees who is close to whom."
  - **The setup walk's permission row** that replaces BLUETOOTH reads **NEARBY DEVICES**.
- **D-292, the simulator answers "UWB supported":** proposed so the desktop tools keep working. It applies only to the simulator source set, and the owner did not object.
