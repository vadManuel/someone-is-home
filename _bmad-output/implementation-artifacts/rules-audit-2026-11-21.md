# Rules against code, 2026-11-21 (overnight audit at 1eb155e)

This is a read-only audit: every rule in the GDD and the decision log, checked against the code and tests. The morning summary is in `overnight-decisions-2026-11-21.md`. This file keeps the detail that the follow-up work needs. "Tested" means a test with that name exists; this audit ran nothing.

## Code that contradicts a ruling

1. **The Terminal and the couch show no counts on a phone.** `PanelModel.kt:1445-1451` leaves `terminalCounts`, `timelapseCounts` and `trueCounts` null on a housed phone, so nothing is drawn. The house builds placements and captures counts (`Placements.kt`, D-272) but sends nothing to any phone. The timelapse is a fixture (`ScreensWork.kt:768`). **Owner.**
2. **Surge makes no sound.** `Flow.kt:2855` drops `sound`. `deviceSpeaker()` has no caller, and every actual is `SilentSpeaker` (D-176, GDD 313/419). **Owner.**
3. **Static isn't announced house-wide.** It is drawn only in `TerminalView` (`ScreensWork.kt:805`), and `StaticOpened` has no haptic (`HouseBuzzer.kt:462`). D-186 says every living player knows. **Owner.**
4. **Drift: an early tap is echoed rather than graded.** `ScreensSubroutines.kt:839`; D-165. **Fixed in D-313.**
5. **The minimum party is 3, not 5** (`Balance.kt:59`): the owner's temporary order for device testing. It must go back to 5 before release.
6. **An armed pass at an already-Revoked phone does nothing.** `Ranging.kt:255`, `Contacts.kt:525`: no `ContactMade`, no buzz, the tile stays ARMED. D-290 describes `ArmedTouch.Spent`. GDD 1375 says the two cases look the same. **Owner.**
7. **Not voting counts as a Skip** (`Meeting.kt:241-258`, `Rules.kt:1816`; D-075, D-117), while D-144 says an abstention counts as nothing. **Owner.**
8. **Cards that aren't markers.** `Cheats.kt:889`: NOT A MARKER can't be reached in a round, and outside paper gets a "yes" buzz then nothing (D-124, D-169). **Fixed in D-313.**
9. **No lamp level is set at a meeting.** `Effect.LampSet` is sent only at arming (`Rules.kt:391`); D-168 and GDD 430/457/1041/1061. **Owner.**
10. **The verdict and NOTHING FOR YOU HERE are silent** (`HouseBuzzer.kt:419, 435`), while D-160 lists a "yes" and a "no" buzz. **Owner to confirm.**
11. **Surge might accept input** during its bright and dark phases (`Flow.kt:1467`, `:1502`); D-182. **Checked and fixed in D-313.**

## Not built
- Cross-player chains, the 20-120 s unlock delay and the upstream notice (D-146). Only self-chains exist (`Arming.kt:212-216`).
- The work-order mix 1/1/3/2: kinds are drawn flat (`Arming.kt:224`).
- The Long two-stage kind (Memory Dump).
- Array Wipe, never dealt (D-164).
- Carry state (D-114, F-014).
- The stations drawn at arming are unused.
- The M2 motion budget: no accelerometer code. `KitBudgetRow` is a static label, so D-203's motion release and D-110's "movement cancels" can't fire.
- Isolating the Terminal (GDD 315; `Rules.kt:875`).
- Lobby settings: the Egress timer (constant 120 s, `Balance.kt:178`, literal at `ScreensHost.kt:1259`), DISCUSSION and REVOKE COOLDOWN are fixed text (D-037 partial).
- A timeout on the check-in gate (`HouseClock.kt`; D-104 accepts the risk).

## Built but untested
- D-154: a Revoke leaves the meter alone. **D-313 adds a test.**
- The Insider's fake counter. **D-313.**
- The Terminal lock released when a connection drops (`LobbyOverTransport.kt:1003`).
- D-190: the Isolate list re-sent on reconnect.
- D-143 at 18 seats. **D-313.**
- D-037: balance locked at arming.
- The talk 90 s and vote 45 s values.
- Surge's cooldown against Static's. **D-313.**
- D-291 over sockets.
- Out seats on the ballot, and a Restrained Revoked seat getting `RestrainedTakeover`.
- Revoke arming accepted during a meeting (only a forged frame can do it).

## Stale GDD lines (a later decision wins; for the GDD sweep)
- **Vocabulary:** `guests_alive` / `living_guests` (206, 209, 273, 1089, 1102); "A Insider" (211 and elsewhere); "deactivation report" (576, 793); "task" throughout (190, 254, ...).
- **Meetings and the vote:**
  - 431: "living players only" (D-104).
  - 432, 1055: changeable until the clock ends, and abstention (D-117, D-141; see item 7).
  - 433, 435, 1057: attribution shown (D-075, D-195: the living see a count, only the out see attribution).
  - 441: the "Occupant Marcus 21:04–21:07" example (D-223).
  - 1041: "every phone starts vibrating" (it rings in the hand).
  - 1059: "deauthorises them moments later".
  - 1100-1104: the endings omit Restraining every Insider (D-131) and THE HOUSE IS CLOSED (D-310).
- **Revoke and Egress:**
  - 322: "nothing changes on screen" (D-286, D-290).
  - 338: "finds out immediately".
  - 369, 371, 373, 1025, A-11 at 1578: "simultaneously", "tap on the beat 4 times", "2-3s lockout" (D-298: four in a row, a shared pulse, 3 s of vibration).
- **Opening findings:**
  - 941-943, 1123: F-003 still marked open (closed by D-156, D-249).
  - 945-947, 1124: F-004's 45 s (D-132: 30 s).
  - 949: "identical" page 2 (D-286: the same structure).
- **Work order:**
  - 963-965, 1125: F-005 `7 × initial_residents` and auto-satisfy (D-130: seats × 5; D-146: nothing auto-satisfies).
  - 265, 398, 960: 7 per Resident, 1/1/3/2 (D-129).
  - 271, 402: "Insiders have no assigned subroutines" (D-109, D-129).
  - 274-275, 280, 1081: unlock on revoke, collapse gracefully (D-146).
- **Subroutines:**
  - 603-605: Sniff, Deallocate and Drift as first written (D-137, D-138, D-140).
  - 641: "nine briefing lines are owed" (D-194).
  - 1083: "Subroutines visibly degrading" (D-158).
- **Other:**
  - 1091: "fifteen minutes and not twenty-five".
  - 987: "sweeps every 5s".
  - 501, 552, 1063: real counts on the Terminal and the couch (not built; item 1).
- **Code comments:**
  - `Rules.kt:2135` and `Balance.kt:385`: "2-3 s".
  - `Rules.kt:2292`: Surge halves "unruled".
  - `model/Egress.kt:142`: "no Isolate, no Access pool".
  - `WorkOrder.kt:13`: "six are built".
  - `ScreensWork.kt:630`: "reachable only by a Resident".
  - `ScreensHost.kt:1310-1328`: the D-273 branch, unreachable since D-287.
  - `iosApp/iosApp/Info.plist:95`: "replaces Bluetooth".

## Gaps in the phone checklist
- Missing: Surge, Static, Isolate, the Access pool, the meeting ring, the check-in gate, the vote hold, the tally, the endings.
- Line 50 (a third phone refused during a round) needs a fourth phone while the minimum is 3.
- **Ten things tests can't cover:**
  1. Revoke pass timing (arm to first distance in the host log).
  2. Report feel: false cancels, the circle's brightness, nothing from a wall, a Subroutine, the scan, the widget or the lantern.
  3. iOS indicators while a Nearby Interaction session runs.
  4. The meeting ring on a real vibration motor.
  5. The room going bright and dark together at a meeting.
  6. Egress taps on real Wi-Fi: the lockout, only the tapper feeling the pulse, the camera reading a card under the dim alert.
  7. Surge: silent, and whether taps leak through.
  8. An empty Terminal and couch.
  9. The permission switch-off flows.
  10. Scanning in the dark: the verdict's silence, foreign QR codes, an early Drift tap, an armed pass at a Revoked phone.
