# Overnight verification, 2026-11-21: the morning report

You asked: *"anything you can do overnight? maybe run through all the aspects of the game verifying that it works as expected?"* Then: *"make reasonable decisions overnight and show me the report in the morning for me to review."*

## The short version

- **Everything is on main and pushed.** Final full check: **3,319 tests, 0 failures, 0 skipped**, the release and Playtest builds included (`./gradlew check` on `5150ffd`, run by me, counted from the result files).
- **The simulators now play whole rounds.** That had been impossible since NEARBY DEVICES became required. The scripted rehearsal passed all 21 of its checks, twice:
  - lights out;
  - a Revoke landed;
  - a report called a meeting;
  - the vote Restrained someone;
  - a Static jam, then an Egress;
  - the Insiders won.

  The 4-seat hunt also played a round to a parity win and started a second round. Simulators prove the flow only, not the radios, camera, torch or haptics.
- **Bugs found and fixed tonight,** each with a test that failed before the fix:
  1. **A real Insider tell:** the Notes app showed Insiders their own line (*i hope no one finds out*).
  2. **Surge didn't block input:** a tap during the bright phase handed work over, and STOP NOW worked in the dark.
  3. **An early tap in Drift** was a free retry, not REJECTED (against D-165).
  4. **Switching a permission off (D-310) had four detection bugs.** One could close the house over a plain "left the app".
  5. **The permission walk stalled after Bluetooth's answer.** This blocked every simulator round, and it may have stalled your phones too.
- **Your bedtime rule is built (D-311).** Switching off anything the game needs is cheating: in the lobby you go to PERMISSIONS; mid-round the next meeting names you with your sentence; anyone's next switch-off closes the house.
- **Tomorrow:** use the **Playtest** build, on **three phones**, plus a fourth for the "joining mid-round" line. The release build still stops at the boot screen. Your checklist (`phone-session-2026-11-19.md`) has new lines for what only phones can show.

## Decisions waiting for you

Ranked by how much they affect a first playtest.

1. **The Terminal and the couch show no counts on a real phone.** The house computes room counts but sends none. Scanning T claims the station and shows an empty map; the couch has no true occupancy; the timelapse is a placeholder. This is the biggest gap. Building it is a feature, not an overnight fix. Should it be next?
2. **Cards that aren't markers.** A card your home never registered still reaches the rules: the player is placed at a card that isn't anywhere, their open work is cancelled, and they see NOTHING FOR YOU HERE, not NOT A MARKER. A meeting card from another home calls a meeting. Fixing it needs a new message from the house.
   - **Suggested:** the scan sends one plain "card read", the house decides, and only that phone hears NOT A MARKER.
   - Should NOT A MARKER also buzz "no"?
3. **Surge makes no sound.** No phone speaker is wired up (D-176). Sound is yours.
4. **Static is seen only at the Terminal.** D-186 says every living player knows. How it's announced is yours.
5. **The house sets no lamp level at a meeting.** "All lamps snap bright at the call, then dim together" relies on each screen's colours (D-168). Watch it tomorrow.
6. **The verdict and NOTHING FOR YOU HERE give no buzz.** D-160 lists a yes and a no buzz. Is the silence intended?
7. **An armed pass at an already-Revoked phone does nothing.** There's no buzz and the tile stays ARMED, while a live target gives COOLING DOWN and a Long buzz. Is that right (D-290), or should the two look the same (GDD)?
8. **Not voting counts as a Skip** (D-075, D-117), but D-144 says an abstention counts as nothing. Which stands?
9. **A false line on page 2.** The Insider's EGRESS tile always says *SHARED WITH THE OTHER INSIDER*. With 3 to 6 players there's only one Insider, so it's false and suggests a partner who isn't there. Hide it when alone, or reword it?
10. **Copy never ratified:**
    - *THE HOST CLOSED THE HOUSE*;
    - the note *somebody was already at the marker* (D-247);
    - *END SESSION*, since "session" is outside the vocabulary;
    - the Residents' ending listing *THE RESIDENTS 4 OF 6* without the Insiders, which treats them as separate groups.
11. **Two Surge gaps:**
    - a finger already holding on page 2 when a Surge lands (arming, or the report circle) still completes;
    - the camera window stays open during a Surge.
12. **At lights out, guests check their own permissions.** One who switches something off in the last second, or turns NEARBY DEVICES off in the lobby, enters the round and is named at the first meeting rather than kept out. Keeping them out needs a new lobby message.
13. **The minimum party is 3.** That's your temporary order for testing. D-128 says 5 for release.
14. **Not built yet:**
    - cross-player Subroutine chains (D-146);
    - the work-order mix;
    - the Long two-stage Subroutine;
    - Array Wipe;
    - carry state;
    - the motion budget (no accelerometer code, so "movement cancels" can't fire);
    - isolating the Terminal;
    - the Egress timer, discussion time and Revoke cooldown as lobby settings;
    - a check-in gate timeout.

    Detail is in `rules-audit-2026-11-21.md`.

## Calls I made for you (provisional; each is reversible)

| # | Call | Why |
|---|---|---|
| 1 | A phone reopened mid-round with a permission still off goes back to its seat and is named. It is not held at PERMISSIONS. | You said mid-round switch-offs are named, not sent to PERMISSIONS. The gate applies again once the round is over. |
| 2 | Every way a permission can be reported off makes one report per round. | One Bluetooth switch-off could otherwise close the house by itself. |
| 3 | An Insider's Notes shows exactly the note a Resident would. The Insider-only line is deleted. | It was a visible tell, and no decision allowed it. |
| 4 | On the iOS Simulator only, NEARBY DEVICES reads NOT ON THIS PHONE. | Simulators can't do UWB. Without this no bench can host. Phones are unchanged. |
| 5 | An early Drift tap goes up as an empty entry, which the house already rejects. | The smallest way to make D-165 true, with no new message. |
| 6 | A Surge drops the open Subroutine at the slam, not at the reboot. | So a hold that started before the Surge can't finish during it. |
| 7 | The host's own phone, if it switches something off in the lobby, goes to PERMISSIONS while its house stays open and guests stay seated. | The host *is* the house, so it can't simply leave. |
| 8 | The rehearsal's leak check allows an Insider's own line to arrive only at the reveal, after the round ends. | The reveal is meant to pair each Insider with their line, and it goes to every phone. |
| 9 | The recording format is 46 (from 43 yesterday). | The notice and the close changed shape. Older recordings won't open. |

## Only a phone can tell

These are also in the checklist:
- Does the permission walk carry on by itself after you tap Allow on Bluetooth?
- Does Control Centre's Bluetooth button really turn the radio off? Apple documents it as only disconnecting accessories. Airplane mode does turn it off.
- Which error code iOS gives when Local Network is refused (expected −65570)?
- How long UWB takes to start after an Insider arms beside someone, and whether a brisk walk-past lands.
- Whether iOS shows any indicator while UWB runs.
- The report circle's feel and brightness.
- The meeting ring on a real vibration motor.
- Egress taps on real Wi-Fi.

---

## Detail, in the order it happened

### Merged tonight

| Commit | What |
|---|---|
| `252e062` | **D-312:** Notes is identical for both roles; four render tests now draw their real state; JOIN's short refusals are centred |
| `f1ab92e` | **D-311:** switching off is cheating; five D-310 detection bugs fixed |
| `40d79ce` | **D-313:** early Drift tap; Surge blocks input; four rules that held now have tests |
| `e23d44c` | **GDD sweep:** about 100 stale lines and nine code comments brought in line with later decisions. No code changed. Two disputes flagged in place. |
| `0a75d67` | **Phone checklist:** three phones, the Playtest build, and the overnight lines |
| `5150ffd` | **D-314:** the permission walk survives a late answer, and the simulator benches play again |

Each was checked by me before merging:
- I counted the test results from the result files myself.
- I confirmed the tests ran after the last code edit.
- I looked at every changed screen.
- I scanned for banned words.

D-311 and D-313 were also run together in a full check before pushing: 3,312 tests, 0 failures.

### The five checks

- **The full test suite on `1eb155e`** (today's work before the overnight fixes): 3,269 tests, 0 failures.
- **Review of D-306 to D-310.** D-306 to D-309 are sound. D-310's detection bugs were fixed in D-311.
- **Every screen rendered and looked at.**
  - 433 renders; all 72 screens have a phone-size render.
  - 111 Insider/Resident pairs compared pixel by pixel. The only difference no decision allowed was Notes, now fixed.
  - No screen in the dark uses an unusual colour.
  - No banned or borrowed words anywhere.
  - Your D-307, D-309 and D-310 words appear exactly as approved.
- **The GDD against the code.** Most of what a first playtest touches is built, tested and matches your rulings. The gaps are above.
- **Scripted rounds on simulators.** The first pass was blocked by the permission walk. After D-314:
  - **Rehearsal:** 21/21 checks, twice.
  - **Hunt:** a round to a parity win, then a second round.
  - **End-to-end:** 3 pass, and one opt-in test is skipped by design.

### Smaller things left as they are

- The Egress banner always draws page 1 underneath it, so a report cancelled from under the banner returns to page 1.
- A few GDD lines the audit didn't list are still stale:
  - the win table has no "every Insider Restrained" row;
  - "fifteen minutes" appears twice;
  - the per-Resident load of 7;
  - "will never know it";
  - the word "guest" in the D-311 paragraph.

## Owner's review, 2026-11-21 morning

- **Notes:** "lets just remove the notes app entirely". Done as D-315 (`1ed150a`). Page 1 is FILES, MESSAGES / SETTINGS, CAMERA, with the empty slots kept, as D-199 did.
- **The Terminal and the couch:** build it next. In progress as D-316.
- **Sound:** "we can't control sound on the phone. so lets just exclude this". Surge is light only and the game has no audio. This reverses D-176's "one sound in the game".
- **Check-in gate:** no timeout. "that's how its supposed to be" (D-104 stands).
- **The minimum party:** stays at 3 while testing.
- **To build:**
  - the motion budget, after D-316;
  - lobby settings: Egress timer, discussion, Revoke cooldown. In progress as D-317;
  - cross-player chains;
  - carrying.

  Chains and carrying will be built together with the work-order mix once that is discussed.
- **To discuss:** the work-order mix, the two missing Subroutine types, Static's announcement, Isolate on the Terminal, cards from another home, and the remaining overnight decisions.
- **The design mockup:** update the Claude Design project to remove Notes.
