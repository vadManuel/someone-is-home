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

(Filled in as checks finish.)
