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
- **More rulings (morning):**
  - **Static:** a quiet banner MONITORING JAMMED on every living phone (no dim, no buzz).
  - **Isolating the Terminal:** not now.
  - **The work-order mix:** scale the 1 : 1 : 3 : 2 (circuit : long : medium : short) proportions with K.
  - **Foreign cards:** built as proposed, NOT A MARKER with a two-pulse 'no' buzz (D-318).
  - **Verdict buzz:** ACCEPTED gives the short 'yes'; REJECTED and NOTHING FOR YOU HERE give a two-pulse 'no'.
  - **Armed pass at a Revoked phone:** keep "nothing happens"; correct the GDD.
  - **Not voting:** counts as a Skip (D-075, D-117 stand; D-144's abstention line is superseded).
  - **Page 2's SHARED WITH THE OTHER INSIDER:** hidden when the round has one Insider.
- **Built this morning:**
  - D-316: the Terminal and couch map;
  - D-317: the three lobby settings, with each value as its only choice until you rule ranges;
  - D-318: foreign cards.

  An integration agent is combining them (the recording format becomes 48).
- **Open from D-316:**
  - The timelapse has five unruled points: whose cooldown it is, what a frame shows and the sample rate, the button while cooling, the count-up against rule 5, and whether a meeting counts as an early cancel.
  - The couch on a home with two or more storeys shows only the first.
  - The Terminal shows only the station's own storey.
  - The plan now goes to every phone at arming (provisional; it amends D-186 and D-271).
- **Open from D-317:** ranges and steps for the three settings, and whether the map proposes the Egress timer.
- **Merged:** D-316, D-317 and D-318, integrated as `ead4393`. Recording format 48; 3,389 tests, 0 failures; rehearsal 21/21.
- **Rulings on the map and lobby:**
  - **Lobby ranges:** DISCUSSION 60/90/120 s, EGRESS TIMER 90/120/150 s, REVOKE COOLDOWN 45/60/90 s.
  - **Floors:** the GROUND/UPPER strip is tappable on the Terminal and the couch (one Terminal for the whole home).
  - **The plan:** goes to every phone at arming (D-316 kept).
- **Rulings on the timelapse:**
  - the cooldown belongs to the station;
  - frames are bare counts, every 2 s;
  - while unavailable, a dimmed button reading `TIMELAPSE . READY IN m:ss`;
  - a meeting clears it.
- **Building now:**
  - D-319: floors, timelapse, Static banner;
  - D-320: verdict buzzes, the page-2 line, lobby ranges, GDD fixes, removing sound;
  - D-322: chains, carrying, Memory Dump, Array Wipe, the scaled mix.
- **Next:** the motion budget (after D-319), then the design mockup.
- **Open, unexplained:** the rehearsal's line-leak check once saw an Insider's own line arrive just before ROUND ENDED. It passed on a rerun, and it predates today. To investigate.

## Owner's rulings, 2026-11-22

- **Copy:**
  - THE HOST CLOSED THE HOUSE is approved;
  - END SESSION becomes LEAVE THIS HOUSE;
  - the Residents' ending drops "THE RESIDENTS · 4 OF 6" and keeps only the names.
- **Surge:** cancels any hold in progress and closes the camera at the slam.
- **Insiders are told the Insider count:** "we can share any info with player phones". Page 2's SHARED WITH THE OTHER INSIDER line shows only when there are two or more.
- **Memory Dump:** draft its screens for review. It is not dealt until they are approved.
- **Chain notice:** no separate notice. The LOCKED · WAITING UPSTREAM row says it, and "you're up" announces the unlock.
- **Chains are Residents only:** "insiders don't have real tasks... an insider cannot be included in the chain". Insiders' fake orders show fake locked entries that unlock on a plausible delay, so the screens stay identical (D-324, after integration).
- **Memory Dump: approved as drafted** (draft branch `worktree-agent-aa216d461ba48ec5f`, commit `abac9b9`).
  - **Pattern:** 4 of Replay's dots in a 2×2 grid, played twice in Replay's rhythm (about 9 s), then the target's glyph and "TO <ROOM>".
  - **Entry and verdict:** entry is Replay's tap input. A mismatch shows "REJECTED . BACK TO <SOURCE ROOM>".
  - **Light:** MEDIUM.
  - **Briefings:** "DOTS WILL FLASH IN ORDER. / CARRY THE ORDER IN YOUR HEAD." and "THE DOTS WILL NOT FLASH. / TAP THEM BACK FROM MEMORY."
  - **My calls (provisional):** the pattern is held only once BEGIN starts the playback (NOT THIS ONE holds nothing); the house tells the phone the pattern is held, so the order row reads TO <ROOM>; the length is fixed at 4.
- **Build queue:**
  - after the integration: D-324, chains among Residents only, with fake locked entries for Insiders;
  - D-325: Memory Dump's screens, switched on;
  - D-321: the motion budget;
  - then the design mockup.
- **Merged:** D-319, D-320, D-322 and D-323 (`b891101`). Recording 50; 3,522 tests, 0 failures; rehearsal 21/21.
- **More rulings (2026-11-22):**
  - **MONITORING JAMMED:** stays over the current screen.
  - **Leaving the Terminal mid-playback:** an early cancel.
  - **"you're up":** gets the same buzz as a house text.
  - **Lights out:** keep the guests' self-check.
  - **Notifications permission:** removed ("we don't use it").
  - **NEARBY DEVICES:** tested on every return to the app in the lobby.
  - **The once-a-second lobby check:** dropped in favour of the callbacks iOS gives.
- **Building:**
  - D-321: the motion budget;
  - D-324 and D-325: chains among Residents only, and Memory Dump from the approved draft, switched on;
  - D-326: permissions.
- **Note for the owner:** the meeting's System Integrity percentage depends on which seats did real work. That is by design: only plain Residents' work moves the meter (M8, D-154), and the meter is batched at meetings to blur it (D-153). The overnight differential test only passed before by a fixture coincidence. Say if you want coarser steps.
- **More rulings (2026-11-22):**
  - **Array Wipe:** briefed at Spares with "CARRY ONE DISK AT A TIME. / NOTHING ELSE OPENS UNTIL IT IS DELIVERED." The rest stays as built: it lands on page 1 after each station, and a finished circuit just strikes through.
  - **The two-pulse "no":** the same everywhere.
  - **The meeting percentage:** stays exact.
  - **The Release build:** not yet.

## Night of 2026-11-22

- **The design mockup (Claude Design project) is updated:** the Notes tile and screen are gone, MESSAGES moves up a slot, and the gallery entry and the "RESEED BACKLOG AND NOTES" label are cleaned. Previewed before publishing (version 18). Generator changes are in `f308821`.
- **Merged tonight, as one integration (`ceca1ea`):**
  - **D-321:** the motion budget.
  - **D-324:** chains among Residents only, with fake holds for Insiders and the "you're up" buzz.
  - **D-325:** Memory Dump from the approved draft, now dealt, plus the Array Wipe briefing.
  - **D-326:** Notifications removed, NEARBY DEVICES probed on a return to the lobby, the poll replaced by events.
- **Proof:**
  - recording format 52;
  - 3,618 tests in the default check, 4,113 across the three builds, 0 failures;
  - buildSrc 35;
  - the rehearsal 21/21;
  - e2e 3/3;
  - the iPhone Playtest build succeeds.
- **The design mockup, republished as version 19:** Notes removed, and PERMISSIONS shows the app's four rows (it still had LOCAL NETWORK and no NEARBY DEVICES).
- **For you:**
  1. **Lazy linking (GDD M5) is not built:** five questions are open (D-324). Chains stay two players long and are fixed at lights out.
  2. **The motion budget's numbers are guesses to tune on phones:** about 2 s of walking away on a short Subroutine. The "too little motion fails" rule needs a stillness measurement on both phones (D-321).
  3. **The motion budget meets Memory Dump:** walking to the target counts as motion, so the screen closes to the work order on the way. The pattern stays held and the row still reads TO <ROOM>. Is that close OK, or should the TO <ROOM> screen be exempt?
  4. **The NEARBY DEVICES probe on return:** if LIGHTS OUT lands within about 2 s of a guest's return, the probe can overlap the round's first UWB sessions (D-326).
- **Overnight review of today's changes (D-316 to D-326).** Most of it is sound: the timelapse, chains, carries, Memory Dump grading, NOT A MARKER, the lobby settings, Surge, the resets between rounds, the Insider count and determinism. Two confirmed bugs and three likely ones are being fixed as D-327:
  1. **Confirmed:** the "you're up" banner walked a busy player off their Subroutine (losing the attempt) and off the Terminal (freeing the station and charging the timelapse). It also closed a briefing or the camera.
     - **Decision 1 (provisional):** quiet house texts stand over the current screen, as MONITORING JAMMED does.
  2. **Confirmed:** a narrow race let a chain unlock during a meeting. The rules now refuse it during a meeting as well.
  3. **Likely:** the phone's own vibration could spend the motion budget.
     - **Decision 2 (provisional):** motion isn't charged while this phone is buzzing, plus a short tail.
  4. **Likely:** a reconnecting phone could forget the disk it carries. To be checked, and fixed if real.
  5. **Likely:** the lobby's NEARBY DEVICES check could let go a seat that was just armed. A late answer now goes to the round.
- **D-327 merged** (`f8f67cf`). 3,666 tests in the default check, 4,175 across the three builds, 0 failures; rehearsal 21/21.
  1. **"you're up" and other quiet texts now stand over the current screen.** On a Subroutine, the Terminal, the caught scan or the camera, nothing is lost and nothing is sent. Pages 1 and 2 behave as before.
  2. **A chain release is refused during a meeting** and minted after it.
  3. **The phone's own buzz no longer spends the motion budget**, with a 150 ms tail (provisional).
  4. **The disk on reconnect was not a real bug:** the house replays the round to a returning phone. A test now pins it.
  5. **A seat that has just been armed is kept** if a late lobby check answers.
     - **Still open for you:** if the probe answers before the phone has heard any arming push, it can't know. Closing that gap needs a small protocol change. Even then, the house keeps the seat and the player rejoins by token.
     - I also changed one D-326 trigger: a later round's push now counts as armed, not as a lobby check. Please confirm.

### Morning summary, 2026-11-23

- **Main:** `f8f67cf`, all pushed. Every module passes, as do the rehearsal and e2e, and the iPhone Playtest build compiles.
- **Built since yesterday morning:**
  - **The map:** the Terminal and couch with live counts and floor switching, and the timelapse.
  - **Lobby:** settings with your ranges; NOT A MARKER for foreign cards.
  - **Feedback:** verdict buzzes, MONITORING JAMMED, no sound at all.
  - **The work order:** mixed by structure, with chains among Residents, carrying, Array Wipe, Memory Dump.
  - **Movement and permissions:** the motion budget; four permissions, without Notifications.
  - **Fixes:** this night's review fixes.
- **Questions for you:**
  - lazy linking (5 open points, D-324);
  - the motion numbers, tuned on phones, and the stillness rule (D-321);
  - whether walking during Memory Dump should close its screen;
  - the lobby probe gap and the changed D-326 trigger (D-327).
- **Ready for the phone test:** `phone-session-2026-11-19.md` has a line for each new piece. Use the Playtest build on three phones, plus a fourth for the mid-round join.

## Owner's rulings, 2026-11-23

- **Chains:** not everyone is chained. A third of the players, rounded to the nearest even number, with exact halves rounding up (5-8 players give 2, 9-13 give 4, 18 gives 6). They are drawn at lights out with no lazy linking. Insiders get fake locks at the same odds. A Revoked or Restrained upstream player strands the line, as before.
- **The motion budget:** the "too still fails" rule is dropped.
- **PROCEED:**
  - on every Subroutine, the bottom STOP NOW becomes PROCEED once the house has answered (accepted or rejected);
  - Memory Dump's TO <ROOM> screen uses PROCEED too;
  - PROCEED replaces CONTINUE on PERMISSIONS and PERIMETER ARMED.
- **Memory Dump:** after the pattern, TO <ROOM> shows. PROCEED or walking away goes to the work order. While the pattern is carried, other cards are refused (the meeting card and the Terminal still work).
- **Wrong card while carrying** (pattern or disk): "CARRYING . GO TO <ROOM>".
- **Building:** D-328.
- **Lobby permission check:** the changed D-326 trigger (a later round's push counts as armed) stays. For the gap just before lights out, the owner chose: "report at the next meeting instead of kicking out a player". A lobby let-go now asks the house first. If the round is already armed, the seat is kept and the switch-off is named at the first meeting. Build as D-329 after D-328.
- **D-328 merged** (`60c5caa`). 3,685 tests in the default check, 4,195 across the three builds, 0 failures; rehearsal 21/21; e2e green; recording format 53.
  - **Chains:** chained players come in pairs, a third of the seats rounded to the nearest even number, drawn role-blind. A pair that contains an Insider gets two fake holds, timed like a real unlock.
  - **PROCEED** replaces STOP NOW after a verdict, and replaces CONTINUE on PERMISSIONS and PERIMETER ARMED. The only CONTINUE left in the app is the stairs warning's two-second hold, UNREGISTER AND CONTINUE. Should that become PROCEED too?
  - **Memory Dump's TO <ROOM> screen** has PROCEED.
  - **A held pattern refuses every other card, the source included.** A player who forgets the pattern can't fetch a fresh one until they hand over (wrong) at the target. Is that what you want? Allowing a re-scan of the source to restart is a small change.
  - **Wrong card while carrying:** "CARRYING . GO TO <ROOM>".
  - **The padlock** on the status strip shows for a disk but not for a held pattern. Nothing rules it.
- **Building:** D-329, the lobby let-go asks the house first; if the round is armed, the switch-off is reported at the meeting.
- **Owner's rulings on D-328's follow-ups:**
  - re-scanning the source restarts a forgotten pattern;
  - UNREGISTER AND CONTINUE becomes UNREGISTER AND PROCEED;
  - the padlock shows for a held pattern too.

  Building as D-330.
- **Merged:**
  - **D-330** (`69a0e34`): source re-scan restarts the pattern, UNREGISTER AND PROCEED, the padlock for a held pattern.
  - **D-329** (`d0ca2ec`): a lobby let-go asks the house first. An armed seat is kept and named at the first meeting. The phone waits up to 5 s (provisional) and never leaves without the house's answer.
  - Recording format 54. 3,710 tests in the default check, 4,227 across the three builds, 0 failures; rehearsal 21/21.
- **D-329's remaining narrow gap:** a guest whose socket drops and reconnects between being released and hanging up can be put back on the desk.
- **Design mockup:** version 20 (PROCEED on PERIMETER ARMED, UNREGISTER AND PROCEED).
- **All of the owner's questions are answered.** Nothing is open.
