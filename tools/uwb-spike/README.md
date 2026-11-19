# The UWB ranging spike

On 2026-11-18 the owner ruled that UWB (Apple's Nearby Interaction) will replace Bluetooth for
Revoke contact, report and presence. Nothing is rewritten until two real iPhones have answered
what UWB actually gives us. This is that test. It is a **measurement tool only**: it changes no
game screen, light, timing or rule.

- **`run.sh`** builds the Playtest app, installs it on both phones, launches the spike screen on
  both, passes each phone's UWB token to the other, and follows the log.
- **`sample.sh`** turns a window of both phones' logs into numbers: how many readings arrived per
  second, the distance (median, nearest, farthest), how often a direction came with it, and any
  suspension, lock or MARK in that window.
- The spike itself is `app/src/cheats/kotlin/home/someoneshome/app/UwbSpike.kt`. It runs only
  when `run.sh` launches the app. Opened from the Home Screen, the Playtest app is the normal
  playtest app. Release builds do not contain the spike at all.

**The iOS Simulator has no UWB.** Everything below needs the two phones.

## How the two phones find each other

UWB needs each phone to hold the other's *discovery token* before they can range. Each phone
prints its token in its log. `run.sh` reads it from there and copies it onto the other phone, over
the same cable the log comes through (`devicectl device copy to`). The house's network connection
is not used. Carrying the token over it would have needed a new kind of game message, and the
fail-closed redaction rules apply to every message. That is too much game code to change for a
measurement.

## Before the 15 minutes (5 minutes, once)

1. Both phones charged, **unlocked**, and plugged into this Mac. Developer Mode on.
2. Wi-Fi and Bluetooth on, and Airplane Mode off on both. **Leave NameDrop on.** Step 7 tests it.
   NameDrop is in Settings → General → AirDrop → Bringing Devices Together.
3. Open two terminals in the repo:
   - **T1:** `tools/uwb-spike/run.sh start`. The first time, this builds and installs on both
     phones, which takes a few minutes. After that, use `start --no-install`.
     Phone **A** is `vadManuel-Phone`. Phone **B** is the other phone that is plugged in. To name
     them yourself, set `UWB_PHONE_A=…` and `UWB_PHONE_B=…` (part of each phone's name is enough).
   - **T2** is for `tools/uwb-spike/sample.sh "<label>" <seconds>`. It waits that many seconds,
     then prints the numbers for both phones. Every result is also saved in
     `/tmp/someones-home-one-phone/uwb-samples.txt`.
4. iOS asks once on each phone to find and interact with nearby devices. Press **Allow** on both.
5. Wait until T1 shows `phone B read A's token` and `phone A read B's token`. Both screens should
   then show a distance in metres that changes as you move the phones.

The screen shows the distance in large type, the direction (or `DIR NONE`), and the number of
readings per second. Its one button, **MARK**, writes the distance at that moment into the log.
The spike keeps the phone from auto-locking while it runs.

If no distance appears after 30 seconds, T1 will show why. Look for `INVALIDATED code=…` (the name
after the code is Apple's reason) or `NEVER HAPPENED`. Check Settings → Privacy & Security →
Location Services → System Services → **Networking & Wireless**. If it is off, iOS turns UWB off.

## The 15 minutes

Write each step's PASS or FAIL in the margin, and keep T2's output. The pass marks below are this
test's own working marks. They are not rulings; the owner decides what is good enough.

### 1. Capability, on both phones (0:00)

T1 printed one `DEVICE` line and one `CAPABILITY` line per phone when they started. Note:
`model=` (for example, `iPhone14,2` is an iPhone 13 Pro), `precise_distance`, `direction`,
`camera_assistance`, `extended_distance`, and `is_supported` (the older yes/no check).

- **PASS:** both phones say `precise_distance=true`.
- **FAIL:** either says `false`. That phone cannot measure distance with UWB. Stop here.
- **Record:** `direction` and `camera_assistance` for each phone. Step 4 needs them.

### 2. Distance at known gaps (0:01–0:05)

Put B face-up on a table. Hold A still at each gap below, keep the phones on the table, and run
one sample at each. Measure the gap with a ruler.

| Gap | In T2 |
|---|---|
| Touching, back to back | `sample.sh "touching" 15` |
| 15 cm | `sample.sh "15cm" 15` |
| 30 cm (a hand-span) | `sample.sh "30cm" 15` |
| 1 m | `sample.sh "1m" 15` |
| Through a closed door, each phone 50 cm from it | `sample.sh "door" 15` |
| Through a wall, each phone 50 cm from it | `sample.sh "wall" 15` |

- **PASS, order:** the medians grow in order: touching < 15 cm < 30 cm < 1 m.
- **PASS, touch:** touching is clearly apart from 15 cm. The touching window's `p90` is below the
  15 cm window's `p10`.
- **PASS, walls:** the door's and wall's `nearest` are both above the 30 cm median. A phone on the
  other side of a door could never pass for a touch.
- **FAIL:** any of the three fails. Write down which.
- **Record:** each median and its nearest and farthest, and how far each median is from the
  ruler. A steady offset is fine; spread is the problem.

### 3. Two people side by side, B between them (0:05–0:07)

Two people stand shoulder to shoulder. Put B between them at chest height, on a shelf or held by a
third person. Hold A the way a player would: at the chest, screen toward you.

1. The left person holds A at their chest: `sample.sh "left" 15`.
2. The right person holds A at their chest: `sample.sh "right" 15`.
3. The right person reaches out and touches A to B, back to back: `sample.sh "touch-in-a-crowd" 15`.

- **PASS:** the touch's `farthest` is below the `nearest` of both the left and the right windows.
  The house could tell the person touching B from the people standing next to it.
- **Record:** whether left and right differ by more than either one's spread (`p90` minus `p10`).
  If they do, UWB can tell neighbours apart. If not, it can only tell a touch from a non-touch.
- **FAIL:** the touch overlaps either neighbour.

### 4. Direction: on a table, in a pocket, in the dark (0:07–0:09)

Keep B 1 m away, upright, screen toward A. Move A slowly from side to side for the first few
seconds. Direction often needs a little movement before it starts.

1. A face-up on the table, top edge pointing at B: `sample.sh "dir-table" 15`.
2. A in a trouser pocket, its owner facing B: `sample.sh "dir-pocket" 15`.
3. Lights off, A held at the chest, screen toward you: `sample.sh "dir-dark" 15`.

- **PASS:** on the table, `direction: in` is at least half the readings, and the azimuth moves
  the right way when you swing A left and right.
- **Record:** the direction percentage for the pocket and the dark. If it reads 0%, check T1 for
  `CONVERGENCE` lines, which name what Nearby Interaction is still waiting for (for example
  `InsufficientMovement` or `InsufficientLighting`).
- **FAIL:** no direction on the table on a phone that said `direction=true` in step 1.

### 5. Update rate (0:09–0:10)

Both phones still, 1 m apart, nobody moving: `sample.sh "rate" 30`.

- **Record:** both rates (`/s over the window`), the `longest gap` and the `median gap`.
- **PASS (working mark):** at least 10 readings a second, and no gap longer than 500 ms. For
  comparison, the Bluetooth contact radio restarts its scan every 100 ms (D-233). Whether this mark is good enough for
  a Revoke is the owner's decision.

### 6. B locks, goes to the background, and comes back (0:10–0:12)

Keep the phones 1 m apart. Watch T1. It shows `APP …`, `SUSPENDED`, `SUSPENSION-ENDED`, `RUN`,
`REMOVED` and `INVALIDATED` lines from both phones as they happen.

1. Start `sample.sh "lock" 40`. Lock B with the side button, wait 10 s, then unlock it.
2. Start `sample.sh "background" 40`. On B, swipe up to the Home Screen, wait 10 s, then open the
   app again from the app switcher.
3. Start `sample.sh "long-background" 90`. Do the same, but wait 60 s.

- **PASS:** after each return, B's readings come back on their own, with no relaunch. Every window
  shows `first reading after …, N ms later`. Record N.
- **Record, for each:** what B logged (`SUSPENDED`, or `INVALIDATED` and its code), and what A
  logged while B was away (`REMOVED reason=…`, or nothing).
- **FAIL:** B never reads again until the app is restarted, or the token has to be relayed again.
  A new token appears in T1 as `relay: token … → phone A`.

### 7. NameDrop against the UWB reading (0:12–0:15)

NameDrop is the iOS feature that lights both screens when the tops of two unlocked iPhones meet.
In a dark room that is a light the game did not author. This step finds out whether a touch UWB
counts as contact can avoid it.

Put B face-up on the table. A second person watches B with a finger over **MARK**. Start
`sample.sh "namedrop-<orientation>" 30` for each orientation below. Bring A slowly toward B in that
orientation and touch it.

- **The second person presses MARK on B the moment NameDrop's glow starts.** If NameDrop never
  fires, press MARK at the touch.
- In T2's output, the `MARK` line and any `APP RESIGN-ACTIVE` line on either phone show the
  distance (`last reading before it`). That is the UWB reading when NameDrop fired.

| Orientation | Label |
|---|---|
| Top edge to top edge, both face-up (NameDrop's own gesture) | `namedrop-top` |
| Back to back | `namedrop-back` |
| Side edge to side edge | `namedrop-side` |
| A face-down on top of B | `namedrop-stacked` |

- **PASS:** in at least one orientation, NameDrop does **not** fire and UWB still reads within the
  step 2 touching range. That is a touch the game could use without NameDrop lighting the room.
- **Record:** for each orientation, whether NameDrop fired, and the UWB distance when it did.
- **FAIL:** NameDrop fires in every orientation before UWB reads a touch.

### 8. (Optional, 1 minute) How many sessions one phone will hold

A round of eight needs each phone to hold seven UWB sessions, one per other phone. Two phones can
check part of that: A opens extra sessions, and we see whether iOS refuses any.

In T1, press Ctrl-C, then run `tools/uwb-spike/run.sh start --no-install --sessions 8`. Session 1
ranges with B as usual. Sessions 2 to 8 are opened only to see whether iOS allows them.

- **Record:** every `S<n> STARTED` and every `S<n> INVALIDATED` line on A. Pay most attention to
  `ActiveSessionsLimitExceeded`, the error Apple's header gives for "the maximum number of active
  sessions was exceeded". Also check whether session 1 keeps its rate (`sample.sh "8-sessions" 15`
  against the step 5 figure).
- This only tests one phone's limit. Sessions 2 to 8 have no real peer, so this says nothing about
  how well seven live sessions range together.

## Three phones

This answers three questions the two-phone run could not:

1. With two live peers, does each pair keep 6.7 readings a second, or is the rate split?
2. How many sessions, live and idle, does each phone hold before iOS refuses one?
3. Does any session option raise the rate above 6.7 a second?

In a three-phone run every phone ranges with both others, so each phone holds two live sessions.
Each session has its own token, and `run.sh` gives each phone the right two.

### Before you start (5 minutes)

1. All three phones charged, **unlocked**, and plugged into this Mac. Developer Mode on.
2. In T1: `UWB_PHONE_C=<part of the third phone's name> tools/uwb-spike/run.sh start`. The first
   time, this builds and installs on all three. After that, add `--no-install`. Phone B is the
   phone left over once A and C are named; set `UWB_PHONE_B=…` if more than three are plugged in.
3. Press **Allow** on the phone that has never run the spike.
4. Wait until T1 shows six `read …'s token` lines, for example `phone A read C's token`.

Each screen now shows one line per other phone: its letter, the distance, and the readings per
second. T2's `sample.sh` sees the third phone without being told. It prints one block per
direction (`A sees B`, `A sees C`, `B sees A`, and so on), and one line per phone:
`A in all, across 2 peer(s): … /s`.

### 1. Rate with two live peers (30 s)

Put the three phones on a shelf or a table edge in a triangle, about 50 cm apart. Prop them
upright, not lying flat (step 2 of the two-phone run). Nobody moves.

`sample.sh "three-still" 30`

- **Record:** the rate, the `longest gap` and the `median gap` for all six directions, and the
  three `in all` lines.
- **What it means:**
  - Each direction near 6.7/s, and each `in all` near 13/s: the rate holds per pair.
  - Each direction near 3.3/s, and each `in all` near 6.7/s: the phone splits one rate between
    its peers. Then 7 peers would get under 1 reading a second each.
  - Anything else: write down the numbers, and say which phone was slower.

### 2. One phone passes another (15 s)

Leave B and C where they are. Hold A and start `sample.sh "three-pass" 15`. Bring A slowly to
10 cm from B, press **MARK** on A, then take it back to where it was.

- **Record:** `A sees B` and `B sees A`: `nearest`, the `longest gap`, and the `MARK` line. Also
  record the rate for `A sees C` and `B sees C`, to check that the pass did not slow the other
  pairs.
- **Expected:** `nearest` close to 0.10, and no gap much over 150 ms in any direction.

### 3. How many sessions each phone holds (about 1 minute per phone)

Ctrl-C in T1. Probe one phone at a time, with the phones left as they were in step 1:

`UWB_PHONE_C=… tools/uwb-spike/run.sh start --no-install --probe A`

Once the tokens are read, start `sample.sh "probe-A" 50` in T2. Phone A opens one more idle
session every 2 seconds. It stops when iOS refuses one, or when it holds 20. When it stops, A's
screen and T1 show a line like this:

`A: [uwb] LIMIT live=2 idle=5 total=7 model=iPhone17,1 os=iOS-26.6.2 stop=ActiveSessionsLimitExceeded`

Then do the same with `--probe B` and `--probe C`.

- **Record:** the `LIMIT` line from each phone.
  - `total` is how many sessions the phone held when it stopped.
  - `stop=cap-20` means iOS never refused one, so that phone's limit is at least 20.
- **Also record:**
  - any `INVALIDATED` line in T1 that ends in `peer=B` or `peer=C` rather than `peer=-`, which
    means iOS ended a *live* session to make room;
  - whether `A sees B` and `A sees C` kept their step 1 rate in T2 as the idle sessions were
    added.

### 4. The session options, one at a time (15 s each)

Ctrl-C in T1. These use **two phones**: leave `UWB_PHONE_C` unset. Hold A and B 1 m apart,
upright, and keep them still. Each line below is one T1 start and one T2 sample. Ctrl-C T1
between them.

| T1: `tools/uwb-spike/run.sh start --no-install …` | T2: `sample.sh … 15` |
|---|---|
| nothing extra (the baseline) | `"opt-none"` |
| `--camera-assist` | `"opt-camera"` |
| `--extended-distance` | `"opt-extended"` |
| `--delegate-queue` | `"opt-queue"` |

- **Check first:** at start, T1 prints each phone's `CONFIG` line with the option set to `true`.
  Once the token is read, it prints `S1 CONFIGURED …=true`. That is the option as iOS holds it.
- **`--camera-assist`** turns the camera on. The first time, iOS asks for the camera: press
  **Allow**. Keep the lights on and the back cameras uncovered. Also record `direction: in`: the
  16 Pro gave no direction without the camera.
- **`--extended-distance`** only does anything if **both** phones said `extended_distance=true` in
  their `CAPABILITY` line. The 13 Pro says `false`. If the third phone says `true`, use it and the
  16 Pro for this row: `UWB_PHONE_B=<the third phone's name>`.
- **`--delegate-queue`** is not an Apple option for rate. It only moves the readings off the
  phone's busy screen thread, to rule that thread out as the cause.
- **Record:** each row's rate and `longest gap`, against the baseline.
  - **A rate clearly above 6.7/s on any row is the finding:** write down which option.
  - If an option is refused, the `INVALIDATED` line names why. Record that too.

**No option sets the rate.** iOS 26's `NINearbyPeerConfiguration` has exactly two settings,
camera assistance and extended distance, and nothing about rate. The 55 Hz paper (arXiv
2511.03534) measured an iPhone 11 against an iPhone 12 Pro Max and an Apple Watch. So if no row
beats 6.7/s, the rate is set by iOS or the hardware, not by the spike.

## Afterwards

Ctrl-C in T1 (or `tools/uwb-spike/run.sh stop`). Send:

- the run folder, `/tmp/someones-home-one-phone/runs/uwb-<date-time>/`, which holds `phone.log`,
  `phoneB.log` (and `phoneC.log` for a three-phone run) and `uwb-relay.log`;
- `/tmp/someones-home-one-phone/uwb-samples.txt`;
- the PASS/FAIL notes.

To re-read a whole run later: `tools/uwb-spike/sample.sh --file <run>/phone.log <run>/phoneB.log`,
with `<run>/phoneC.log` on the end for a three-phone run.

## What the log lines are

Every line starts with `[uwb]`, so `grep '\[uwb\]'` finds all of them. `t=` is milliseconds on the
phone's own clock, which keeps running and never jumps. Only compare `t=` values from the same
phone.

| Line | Meaning |
|---|---|
| `DEVICE model=… os=…` | The hardware identifier and iOS version. |
| `CAPABILITY …` | What `NISession.deviceCapabilities` (and the older `isSupported`) report. |
| `S1 TOKEN <base64> gen=N` | Session 1's discovery token. `gen` goes up each time a session is replaced. |
| `S1 PEER TOKEN READ` / `UNREADABLE` | The other phone's token arrived from the relay. |
| `S1 RUN why=…` | The session was started with the other phone's token. |
| `S1 STARTED` | iOS confirmed that the session is running. |
| `S1 UPDATE t= d= dir= az= hang= vert=` | One reading: distance in metres, direction as a unit vector, azimuth in degrees (from the vector, and Apple's `horizontalAngle`), and the vertical estimate. `nil` means the reading had none. |
| `S1 REMOVED reason=…` | iOS stopped ranging with the peer: `NINearbyObjectRemovalReasonTimeout` or `…PeerEnded`. The spike runs the session again. |
| `S1 SUSPENDED` / `SUSPENSION-ENDED` | iOS paused the session (background, lock) and later allowed it again. The spike runs it again at the end. |
| `S1 INVALIDATED code=… (Name)` | The session ended for good, with Apple's reason. The spike starts a new one (with a new token, which the relay passes on), except after `UserDidNotAllow`, `UnsupportedPlatform` or `ActiveSessionsLimitExceeded`. |
| `S1 CONVERGENCE status=… reasons=…` | What the direction estimate is still waiting for. |
| `S1 TALLY` | Every 10 s: how many readings arrived. |
| `APP …` | The app lost or regained the foreground: `RESIGN-ACTIVE`, `BACKGROUND`, `FOREGROUND`, `ACTIVE`. `LOCKING` and `UNLOCKED` appear only on a phone with a passcode. |
| `MARK n d=…` | MARK was pressed, and the distance at that moment. With three phones: `d_B=… d_C=…`, one per peer. |
| `CONFIG …` | The session options this launch was given, and whether it probes. |
| `S1 CONFIGURED …` | An option as the session's configuration reports it, after the spike set it. |
| `… peer=B` | Three phones only: the line belongs to the session for phone B. `peer=-` is an idle session. |
| `PEERS me=A peers=B,C` | Three phones only: which phone this is, and which it ranges with. |
| `PROBE START` / `LIMIT …` | The limit probe started, and where it stopped. `LIMIT` gives the live, idle and total sessions, and `stop=` gives the error that refused one, or `cap-20`. |
