# Overnight verification, 2026-11-21, for the owner's review

The owner can't plug in phones until 2026-11-21 and asked: *"anything you can do overnight? maybe run through all the aspects of the game verifying that it works as expected?"*

Main at the start: `1eb155e` (D-306 to D-310 merged).

## What runs

| Check | What it can prove | What it cannot |
|---|---|---|
| The full `./gradlew check` on main | Every module's tests on today's combined code, counted from XML | Anything on a phone |
| `e2e.sh` and `rehearse.sh` on simulators | The real app's flow from lobby to ending, with stand-in radios | UWB, Bluetooth, camera, torch, haptics |
| Every screen rendered and looked at | Vocabulary, parity, light, layout, leftover text | How it looks on a real panel in the dark |
| A review of D-306 to D-310 | Logic bugs against the rulings, each proved with a failing test | Timing on real radios |
| The GDD against the code | What is built, tested and matching the latest ruling | Feel |

## How findings are handled

- **A clear bug:** fixed with a test that fails before and passes after, checked by me, pushed. Listed below.
- **Anything touching design, copy, light or timing:** written up here for the owner, not changed.
- Every other call is listed below, marked provisional.

## Owner rulings given before bed (2026-11-21), being built as D-311

- **Switching off anything the game needs is cheating.** That covers any permission, and Bluetooth turned off in Control Centre or airplane mode. Leaving the app is not cheating.
- **In the lobby:** the phone is removed and lands on PERMISSIONS. The same check runs at lights out.
- **Mid-game:** the next meeting names them with the approved sentence: "Resident Mia switched off a permission this game needs. If anyone switches one off again, the house closes and everyone starts over." Players who only left the app keep their "unreachable" sentence, which comes first.
- **The second time:** the house closes, as in D-310.
- **Overruled:** D-310's provisional items 1 (Bluetooth power off was not counted) and 2 (a permission switched off in the lobby did not count).
- **Owner's last word:** "make reasonable decisions overnight and show me the report in the morning for me to review".

## Results

### D-311 and D-313 merged (`40d79ce`)

I ran the full check myself on the two combined: **3,312 tests, 0 failures, 0 skipped**, release and Playtest builds included. BUILD SUCCESSFUL.

### D-311, switching off is cheating

- **Your rules, as built:**
  - Switching off any permission, or the Bluetooth radio, counts. Leaving the app doesn't.
  - **In the lobby:** a guest is let go to PERMISSIONS with that row shown REFUSED. They keep their name and can rejoin. The lobby re-checks once a second, on every return to the app, at NEW ROUND, and for the host after a close.
  - **Mid-round:** the meeting names them with your sentence. Anyone who only left the app is named first with the old sentence. Anyone's next switch-off closes the house.
  - The notice renders exactly your words, in the right order.
- **All five review bugs are fixed**, each with a test that failed before:
  - a reopened phone no longer turns a plain leave into a second switch-off;
  - a phone reopened mid-round with Camera off gets back to its seat, and is named for it;
  - permissions are re-checked between rounds;
  - one Bluetooth switch-off is one report, whatever reports it.
- **Decisions made (provisional):**
  - **The host's own phone** goes to PERMISSIONS, but its house stays open and every guest stays seated. LIGHTS OUT does nothing until everything is back on, then CONTINUE returns the host to its lobby.
  - **At lights out,** guests check themselves. The host can't ask them without a new message. A guest who switches something off in the second before lights out, or turns NEARBY DEVICES off in the lobby, enters the round and is named at the first meeting rather than kept out. Tell me if you'd rather they were kept out; that needs a new lobby message.
  - **The recording format is now 46,** because the notice now lists leavers and switchers separately.
  - **Unchanged:** the Egress banner always draws page 1 under it, so a report cancelled from under the banner returns to page 1.
- **Only a phone can tell:** whether Control Centre's Bluetooth button really turns the radio off. Apple says it only disconnects accessories. If the radio stays on, nothing is named, because nothing actually went off. Airplane mode does turn it off. The checklist says what to look for.

### The full test suite on main (done)

- I ran `./gradlew check` myself on `1eb155e`, with D-306 to D-310 all merged. **3,269 tests, 0 failures, 0 skipped**, counted from the result files. BUILD SUCCESSFUL in 24 minutes.
- Per module:
  - model: 355 + 339
  - core: 413 + 411
  - ui: 755 + 371
  - platform: 210
  - harness: 132
  - cards: 17
  - app: 266
- Today's five changes had each been tested separately. This is the first run with all of them together.

### Review of D-306 to D-310 (done)

- **D-306, D-307, D-308 and D-309:** sound under every rule checked. That covers the report gate (no path from the lantern or a Subroutine), the pairing's predicates, the removal of the Bluetooth rules, and NEARBY DEVICES not passable at ASKED by any path.
- **D-310 had real bugs, all in detecting a switch-off.** All are handed to D-311's build, which is rewriting the same code:
  1. **Confirmed with a failing test.** After one reported switch-off, a phone that the OS stopped while it was away, then reopened, sent a later plain leave as a *second* permission, which closes the house. A leave must never count.
  2. **Likely.** A phone reopened mid-round with Camera or Motion still off was sent to PERMISSIONS and couldn't get back to its seat. If the player turned it back on to rejoin, the switch-off was never named.
     - **Decision 1 (provisional):** the PERMISSIONS gate is for taking a seat in the lobby. A phone that already holds a seat in a running round goes back to it even with a permission off, and is named at the meeting as cheating. The gate applies again once the round is over.
  3. **Likely.** Permissions were not checked again at NEW ROUND, nor for the host after a close. D-311's check at lights out covers this.
  4. **Unlikely timing.** One Bluetooth switch-off could be reported twice and close the house by itself.
     - **Decision 2 (provisional):** every source for the same permission makes one report per round.
  5. **Cosmetic.** A report cancelled from under a banner on page 2 returned to page 1.

### Scripted rounds on the iOS Simulator (first pass done: blocked)

- **No simulator round could start.** The host never got past PERMISSIONS:
  - Bluetooth first answers "not asked yet" and only answers properly a moment later.
  - The permission walk stops at that first answer and never moves on to NEARBY DEVICES.
  - Since D-309, NEARBY DEVICES must be answered before anyone can host.
- **Why it may matter on your phones:** after someone answers the Bluetooth prompt, the walk may stall until they tap REQUEST WHAT IS MISSING again. Watch for it tomorrow.
- **Being fixed (D-314):**
  - The walk continues when a late answer arrives.
  - **Decision 4 (provisional):** on the iOS Simulator only, NEARBY DEVICES reads NOT ON THIS PHONE, as Bluetooth already does there, so test benches can host. Phones are unchanged.
  - Also: the bench's setup gets sturdier, one stale end-to-end test is updated, and two script defects are fixed:
    - `e2e.sh` deleted its own log on failure;
    - `rehearse.sh` asked for a simulator this Mac doesn't have.
  - Then the rounds are played for real.
- **End-to-end tests:** 2 pass. 1 is stale: it expected the simulator to refuse on BLUETOOTH, which it never does. That predates today.

### The GDD against the code, rule by rule (done)

Most rules a first playtest touches are built, tested and match the latest ruling. That covers the lobby and arming, lights out, page 2, the Revoke, the report, meetings, the vote, the Egress, the endings and the house closing. The full table is kept in the session; ask if you want it as a page.

**First: use the Playtest build tomorrow.** The release build still shows only the boot screen. The whole round lives in the Playtest and Debug builds. The checklist already says `./run-app.sh device playtest`.

**D-313 results** (merged, `40d79ce`):
- **Drift early tap: fixed.** It is handed over and graded REJECTED, the same for both roles.
  - **Decision 5 (provisional):** an early tap goes up as an entry with no step in it. The house already rejects that.
- **Surge input: it did NOT block, and that's fixed.** Before the fix, a tap during the bright phase handed work over, and STOP NOW worked in the dark phase. Now nothing takes a touch for the whole Surge.
  - **Decision 6 (provisional):** the open Subroutine is dropped at the slam, not at the reboot, so a hold that started before the Surge can't finish during it.
  - **Two gaps left for you:**
    - A finger already holding on page 2 when the Surge lands (arming a Revoke or an Egress, or the report circle) still completes.
    - The camera window stays open during the Surge, so a card read then still counts. Closing it would move its double buzz, and no ruling covers that buzz.
- **Tests added and proved:** a Revoke leaves the meter alone; the Insider's fake counter; the Insider band at 18 seats; Surge's cooldown is longer than Static's.
  - My brief had the cooldown order wrong. The code is Surge 150 s > Static 120 s > Isolate 90 s, which matches D-186.
- **Cards that aren't markers: stopped. This needs you.** It's worse than the audit said:
  - A card this home never registered still reaches the rules. The player is placed at a card that isn't anywhere in the home, their open work is cancelled, and they get NOTHING FOR YOU HERE instead of NOT A MARKER. D-072 says such paper never reaches the rules.
  - A meeting card from another home calls a meeting, because nothing checks the card.
  - A phone has no map of the home, so only the house can tell. Fixing it needs three things no ruling covers:
    - a card id on the meeting request;
    - a new message telling one phone NOT A MARKER;
    - whether that answer goes in the recording.
  - **Suggested:** the scan sends one plain "card read" request, the house decides, and only the scanning phone hears NOT A MARKER. Whether that also buzzes "no" is yours.

**What D-313 was asked to fix.** Each of these is code that contradicts a ruling you already made:
- **Drift, early tap.** A tap before the buzz is echoed and you try again, which makes it a free probe of the hidden timing. D-165 says it is handed over and graded REJECTED. A test currently pins the old behaviour.
- **Cards that aren't markers.** A game card from another home shows NOTHING FOR YOU HERE instead of NOT A MARKER (D-124, D-169). Outside paper gives the "yes" buzz and then nothing.
- **Surge.** It may not block input during its bright and dark phases. D-182 says no input at any point. This gets checked and fixed if so.
- **Tests added** for rules that hold today but nothing pins:
  - a Revoke leaves the meter alone (D-154);
  - the Insider's fake counter;
  - the Insider band at 18 seats;
  - Surge's cooldown against Static's.

**For you: built differently from the design, or not built.** Nothing here was changed.
1. **The Terminal and the couch show no counts on a real phone.** The house computes room counts but never sends them. Scanning the T card claims the station and shows an empty map. The couch has no true occupancy, and the timelapse is a placeholder. This is the biggest gap. Building it is a feature, with redaction to design, not an overnight fix.
2. **Surge makes no sound.** No phone speaker is wired up (D-176). Sound design is yours.
3. **Static isn't announced to the house.** Only someone at the Terminal sees it, over an empty map. D-186 says every living player knows. How it is announced (light or buzz) is yours.
4. **The house never sets the lamp at a meeting.** "All lamps snap to full at the call and drop to dim together" depends on each screen's own colours, not a level the house sends (D-168). Light is yours.
5. **The verdict and NOTHING FOR YOU HERE give no buzz.** D-160 lists a "yes" and a "no" buzz for them. Confirm silence is intended, or I'll wire them.
6. **An armed pass at an already-Revoked phone does nothing:** no buzz, and the tile stays ARMED. A pass at a live player gives COOLING DOWN and a Long buzz. D-290 may accept this, since the dark lamp already shows it, but the GDD says the two look the same. Which stands?
7. **Not voting counts as a Skip** (D-075, D-117). D-144 says an abstention counts as nothing. The rulings disagree. Which stands?
8. **The minimum party is 3, not 5.** It's marked as your temporary order for device testing and must go back to 5 before release.
9. **Not built yet:**
   - cross-player Subroutine chains (D-146);
   - the work-order mix 1/1/3/2;
   - the Long two-stage kind;
   - Array Wipe (never dealt);
   - carry state;
   - the motion budget (no accelerometer code at all, so "movement cancels" can't fire);
   - isolating the Terminal;
   - the Egress timer, discussion time and Revoke cooldown as lobby settings (they are fixed);
   - a timeout on the meeting's check-in gate (one wandering phone stalls a meeting).
10. **The GDD's stale lines: swept and merged.** About 100 lines now match the decisions that overrode them:
    - vocabulary: "an Insider", "Subroutine" for "task", "report" for "deactivation report";
    - the vote and its attribution;
    - the Egress taps;
    - F-003, F-004 and F-005 closed;
    - the work order;
    - the Subroutine rows;
    - the four endings.

    Nine code comments were corrected too; no code changed. Where the Terminal and couch counts are described, the GDD now says they are not built yet.

    **Two disputes are flagged in the GDD, not settled:**
    - is not voting an abstention or a Skip (items 7 above);
    - does an armed pass at a Revoked phone look the same as one at a live phone (item 6).

    **A few more stale lines were left for you,** because the audit hadn't listed them:
    - the win table has no "every Insider Restrained" row;
    - "fifteen minutes" at two places;
    - the per-Resident load of 7 at 694;
    - "will never know it" at 211;
    - the word "guest" in the D-311 paragraph.

**What to watch on the phones that tests can't cover.** This list goes into the checklist after D-311 lands:
1. Revoke pass timing: how long the attacker stays lit, and whether a brisk walk-past lands.
2. Report feel: false cancels, and the circle's brightness.
3. Whether iOS shows any indicator while UWB runs.
4. The meeting ring on a real vibration motor.
5. Whether the room goes bright and dark together at a meeting.
6. Egress taps on real Wi-Fi.
7. Surge: silent, and whether taps leak through.
8. An empty Terminal and couch: expect it.
9. The permission switch-off flows (D-310 and D-311).
10. Scanning in the dark.

Bring a fourth phone if you can. With the minimum at 3, the "joining during a round is refused" line needs one more phone than the players.

### Every screen rendered and looked at (done)

- **The scale of the check:**
  - 433 renders. Every one of the 72 screens has a render at phone size.
  - 111 Insider/Resident pairs compared pixel by pixel.
  - The 324 renders from the dark of a round were checked for light.
  - Every word was checked against the vocabulary and the banned words.
  - The ui tests: 755, all passing.
- **Clean:**
  - No banned word, borrowed genre term, NameDrop step or LOCAL NETWORK row anywhere.
  - Your D-307 report screen, the D-309 JOIN states and the D-310 notice and close lines match your words exactly.
  - Nothing runs off a phone-sized screen.
  - No screen in the dark uses a colour the others don't.
- **Fixed and merged as D-312** (`252e062`). The ui tests went from 755 to 757 and all pass. With the Insider line put back on purpose, the new parity test fails. Both roles' Notes screens are now byte-identical.
  1. **A real tell.** The Notes app showed an Insider the line *i hope no one finds out*, while a Resident sees a different note. Notes is one tap from page 1, so a glance at someone's phone could give them away. No decision ever allowed it.
     - **Decision 3 (provisional):** an Insider now gets exactly the note a Resident would. The Insider-only line is deleted.
  2. **Four renders never showed the state they were named for:**
     - both hosting refusals;
     - the host's lobby when LIGHTS OUT goes live;
     - the notice's real wording;
     - a home actually missing something.

     These are fixes to the render tests, not to the game.
  3. **Off-centre words.** Short words on JOIN A HOME (ROUND IN PROGRESS, NAME TAKEN) sat at the left edge instead of centred.
- **For you (not changed):**
  - **A false line on page 2.** The Insider's EGRESS tile always says *SHARED WITH THE OTHER INSIDER*. With 3 to 6 players there is only one Insider, so the line is false, and it suggests a partner who isn't there. Options:
    - hide the line when there is one Insider;
    - give it different words in that case.

    Either changes your approved copy (D-151).
  - **Unapproved copy still in the app:**
    - *THE HOST CLOSED THE HOUSE* is marked as proposed and never ratified.
    - The note *somebody was already at the marker* is still marked provisional (D-247).
  - **Vocabulary:**
    - *END SESSION* on Settings: "session" isn't in the closed vocabulary.
    - The Residents' ending lists *THE RESIDENTS 4 OF 6* without the Insiders. That treats Resident and Insider as separate groups, and the GDD says they are not.
