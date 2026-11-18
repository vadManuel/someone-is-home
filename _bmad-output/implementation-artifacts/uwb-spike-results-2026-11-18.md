# UWB spike: results (2026-11-18)

Two phones, run by the owner with `tools/uwb-spike/` (steps 1–8 of its README).

- **A:** iPhone 16 Pro (iPhone17,1), iOS 26.6.2
- **B:** iPhone 13 Pro (iPhone14,2), iOS 18.7.8

Raw data:
- every window: `/tmp/someones-home-one-phone/uwb-samples.txt`
- the logs: `/tmp/someones-home-one-phone/runs/uwb-20261118-*`

Both are on the test machine only and are not committed. The figures below are copied from them.

Distances are in metres, as the median, with the nearest and farthest reading in the window. Each pair is A's reading then B's.

## Verdict in one paragraph

UWB distance is accurate to a few centimetres, but only when the straight line between the two phones is clear. Fabric doesn't hurt it, and nor do darkness, a door or a wall.

A body directly between the phones makes it read **3 to 5 m too far**. It never reads short, so it can miss a real touch but never invent one.

Direction is not usable:
- the iPhone 16 Pro gives none without its camera;
- the 13 Pro's direction comes and goes, at between 4% and 100% of readings.

One way of touching gives a clear UWB touch without NameDrop: **screen to screen, stacked**. Back to back also reads as a touch without NameDrop, but the readings dropped out for 3.5 s once during that touch.

Readings arrive at **6.7 a second**, one every 150 ms. That is below the spike's 10-a-second working mark.

Locking the phone or sending the app to the background pauses UWB for both phones. It comes back by itself within 0.5 s of the app returning, with no new token needed.

One phone opened 8 sessions without iOS refusing any. Whether 7 *live* peers share the radio is not yet known.

## Step 1: capability

| | precise distance | direction | camera assistance | extended distance |
|---|---|---|---|---|
| A, 16 Pro | yes | **no** | yes | yes |
| B, 13 Pro | yes | yes | yes | no |

Without the camera, the 16 Pro reports no direction, and it gave a direction in 0% of every window. iPhone 14 and later give direction only with camera assistance. The camera cannot help in a dark room with phones held at the chest.

## Step 2: distance at known gaps

| Window | A | B | Notes |
|---|---|---|---|
| touching, stacked back to front | 0.021 (−0.064–0.071) | 0.022 (−0.059–0.070) | |
| 15 cm, flat on a table | 0.308 (−0.024–0.377) | 0.299 (0.008–0.371) | table multipath: discarded |
| 15 cm, held | 0.120 (0.081–0.143) | 0.134 (0.108–0.161) | |
| 30 cm, held | 0.260 (0.228–0.298) | 0.294 (0.255–0.319) | |
| 1 m, held (retake 2) | 0.941 (0.890–0.960) | 0.940 (0.887–0.991) | the first take was dropped and locked |
| door, each phone 50 cm from it | 0.972 (0.879–1.050) | 0.981 (0.884–1.048) | |
| wall, each phone 50 cm from it | 0.981 (0.707–1.265) | 0.997 (0.729–1.410) | |

- **PASS, order:** touching < 15 cm < 30 cm < 1 m.
- **PASS, touch:** touching's p90 (0.058) is below the held 15 cm p10 (0.104 / 0.121).
- **PASS, walls:** the nearest reading through the door (0.879) and the wall (0.707) is well above the 30 cm median.
- **Caution, a finding the README did not ask about:** a door or wall does not lengthen the reading. Two phones 1 m apart through a wall read the same as 1 m apart in the open. UWB measures distance, not rooms.
- **Caution, flat on a table:** with a phone lying flat on a table, the distance jumps by up to 2 m (see also steps 4a and 4b). Held phones are steady.

## Step 3 (substitute): body in the way

The owner was alone, so the side-by-side test with two people was replaced by a test of a body in the way. Two windows were recorded with A at the same spot, about 1 m from B on a shelf:

| Window | A | B | rate |
|---|---|---|---|
| back to B, A against the chest | **4.596** (3.249–5.815) | **4.479** (3.565–5.963) | 5.1–5.7/s, gaps up to 0.74 s |
| facing B, A in front of the chest | 0.882 (0.770–0.974) | 0.870 (0.662–1.003) | 6.7/s |

When a body blocks the direct path, the phones range off a reflection instead, and the reading comes out 3–5 m too long with no warning. No blocked reading came in short.

**Not tested:** two people side by side, which is the README's step 3. It needs a second person.

## Step 4: direction (moved phone: B, 13 Pro)

| Window | distance A / B | B's direction |
|---|---|---|
| a, flat on a table, held still | 1.398 / 1.493 (0.725–2.461) | 45%, −5° to +2° |
| a, flat on a table, swept left and right | 1.024 / 1.036 (0.741–2.730) | 38%. It followed the sweep from 0° to 45° and back on one side. The other side mostly went blank (one reading, −36°). |
| b, front trouser pocket (owner moving) | 1.382 / 1.378 (1.248–1.461) | 98%, 34° |
| b, front trouser pocket, still | 1.065 / 1.057 (0.974–1.117) | **14%**, 53° |
| c, lights off, held at the chest | 0.933 / 0.925 (0.826–1.007) | 100%, −8° |

- **Direction:** when it reads, the 13 Pro's direction is real, but it covered between 14% and 100% of readings in two near-identical pocket windows.
- **Pocket:** the fabric did not change the distance.
- **Darkness:** it changed nothing for UWB itself.

## Step 5: update rate

No separate window was needed. Every still window above reads **6.7/s, a median gap of 150 ms, and a longest gap of 150–167 ms**. So does the 90-second window in step 6.

- **FAIL against the 10/s working mark.** The rate looks fixed by iOS.
- **PASS against the 500 ms gap mark** while still.
- For comparison, the Bluetooth contact radio scans every 250 ms.

## Step 6: lock and background (B leaves)

| Window | Away | B's log | A while B was away | First reading after B's `APP ACTIVE` |
|---|---|---|---|---|
| lock | 17.7 s | `SUSPENDED` 0.13 s after background, `SUSPENSION-ENDED`, `STARTED` | silent, no `REMOVED` | 41 ms |
| background | 10.8 s | the same | silent | 8 ms |
| long background (Home Screen) | 62.2 s | the same | silent | 503 ms |

**PASS on all three:** no relaunch and no new token. Each time, the relay passed only the startup tokens.

A side finding: in the first long-background window, B only reached the app switcher or a pulled-down shade. In that state the app goes inactive but never reaches the background. **UWB kept ranging at 6.7/s for the whole 61 s.**

## Step 7: NameDrop

Both phones had NameDrop on. The owner held one phone in each hand.

| Orientation | NameDrop | UWB while touching | Gaps |
|---|---|---|---|
| top edge to top edge, both face-up | **fired** (both phones lost focus about 5 s) | −0.004 / −0.013 | none |
| back to back | no | 0.037 / 0.031 (p10–p90 0.018–0.090) | **one 3.5 s dropout on both phones mid-touch** |
| side edge to side edge | no | 0.094 / 0.080 (0.039–0.171) | none |
| screen to screen, stacked | no | **−0.083 / −0.090** (p10–p90 −0.103 to 0.188, mostly −0.12 to −0.09) | none |

- **Top to top:** UWB read 0 cm about 0.4 s before NameDrop fired, and kept ranging through it. A 0.4 s lead is no use: by then the phones are already touching, and the screens light anyway.
- **Side by side:** it reads 8–9 cm, which overlaps the held 15 cm window (0.081–0.161). UWB cannot tell it from 15 cm apart.
- **PASS:** stacked screen to screen and back to back both read a touch with no NameDrop. Stacked had no dropout.

## Step 8: sessions on one phone

A was restarted with `--sessions 8`. All 8 sessions logged `STARTED` within 0.1 s of each other. None logged `INVALIDATED`, and there was no `ActiveSessionsLimitExceeded`.

Session 1 against B ran at **6.3/s** (longest gap 0.3 s), against 6.7/s with one session. It read 0.245 m (0.225–0.259) at about 25 cm.

**Limit:** sessions 2–8 had no real peer and never ranged. This shows iOS will *hold* 8 sessions. It does not show what 7 *ranging* sessions do to each pair's rate.

## What this means for replacing Bluetooth

What UWB can do:
- **Revoke as a deliberate touch:** strong. A touch reads below about 10 cm, and a held 15 cm gap reads above it. A body can only make a touch read *longer*, so it cannot put a bystander inside the touch. Two Residents standing side by side are told apart by distance alone.
- **Report as a deliberate touch:** the same as Revoke.

What UWB cannot do:
- **Direction:** not on the 16 Pro, nor on any iPhone 14 or later, in the dark. Rules can rely on distance only.
- **Rooms or presence:** a wall does not lengthen the reading, so UWB alone cannot say "same room". Whatever Bluetooth presence did with RSSI floors against walls (D-233/D-236), UWB distance will not do it better through a wall.
- **Contact with phones left in pockets:** a body between the phones reads 3–5 m too far.

## Decisions for the owner

1. **The touch gesture.** Top to top lights the room through NameDrop. The candidates are:
   - **screen to screen, stacked:** cleanest, no dropout;
   - **back to back:** reads as a touch, but dropped out for 3.5 s once.
2. **The update rate.** Is 6.7 readings a second, one every 150 ms, enough for a Revoke? It is below the spike's 10/s mark, but faster than Bluetooth's 250 ms scan.
3. **Presence and rooms.** UWB cannot see walls. Either presence keeps a non-UWB signal, or it becomes distance-only and accepts that a Resident just through a wall reads as close.
4. **Direction is out.** The owner's goal included directional tracking. On the owner's own 16 Pro, it cannot work in the dark.

## Still to test

- Seven live peers on one phone: does each pair keep 6.7/s? This needs 3 or more phones, and the full answer needs 8.
- Two people side by side (the README's step 3). This needs a second person.
- A pass-by: one Resident walking past another. This decides Q5 (report hold).
- Battery over a full round.
- Android: whether iPhone–Android UWB ranging works at all.
