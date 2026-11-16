# The contact radio with one phone

The full device check (`_bmad-output/implementation-artifacts/ble-unit4-device-check.md`) needs three
iPhones. This is the part of it one iPhone and this Mac can answer. Two tools:

- **`tools/one-phone/round.sh`** hosts a round on the phone (seat 0) with two iOS Simulators joined as
  seats 1 and 2. The Simulators are **wire-only filler**: they have no Bluetooth, so nothing about the
  radio is ever read off them. They exist because a round arms at three seats.
- **`tools/ble-peer`** is a Swift CoreBluetooth tool for the Mac. `sniff` shows what the phone puts on
  the air. `advertise` puts a seat's token on the air in the phone's exact shape, so the phone has
  something to hear.

The round seed is fixed at **20261116**, so the Mac can derive any seat's token. At three seats that
seed deals the Insider role to seat 0 (the phone), and the bench's scripted Revoke lands on seat 1.
`core/.../OnePhoneVectorsTest.kt` pins both facts, and `tools/ble-peer/Tests` pins the Swift derivation
to the same values.

## Before you start

1. The phone is plugged into the Mac and unlocked. The Mac and the phone are on the **same Wi-Fi**: the
   Simulators find the phone's home over mDNS.
2. On both, **Date & Time → Set Automatically: on.** Tokens are cut from the wall clock.
3. Bluetooth is on, on both.
4. Build the peer: `(cd tools/ble-peer && swift build -c release)`. Its tests: `swift test`.
5. Run `tools/one-phone/round.sh check`. **PASS:** it prints a team, the phone with `developer mode
   enabled`, the two Simulators (a missing one is created by `start`), and `ble-peer: built`.
6. **One time only, the Simulators' notification prompt.** A Simulator that has never answered it is
   refused a seat, naming NOTIFICATIONS, and no Simulator on this Mac has answered it yet. The first
   `start` prints `ONE-TIME SETUP` and opens the Simulator app: press **Allow** on *"Someone's Home
   Would Like to Send You Notifications"*, then Ctrl-C and run `start` again. `sim-grants.py` copies
   that answer to every Simulator from then on.
7. **Terminal needs Bluetooth.** The first `ble-peer` run may ask *"Terminal would like to use
   Bluetooth"*: allow it. If it says `BLUETOOTH IS NOT ALLOWED FOR THIS TERMINAL`, go to System Settings
   → Privacy & Security → Bluetooth, switch on the terminal app you use, and run it again.

Logs go to `/tmp/someones-home-one-phone/`. **Every `start` keeps its own**, in
`runs/<date-time>/`, and no later run deletes or overwrites them: `phone.log` (everything the phone
prints), `seat-1.log`, `seat-2.log`, `driver.log`, and the build and install logs. `phone.log`,
`seat-1.log`, `seat-2.log` and `driver.log` in the top folder are links to the latest run's, and
`latest/` links to its folder. `start` stays in the foreground and follows the phone's `[radio]`,
`[round] PUSH`, `[house]` and bench lines. **Ctrl-C stops everything**: the app on the phone, both
Simulators and the log writers. `round.sh stop` does the same from another terminal. Neither touches
a log.

**A locked phone.** Since D-224 the host's screen is held on from the moment its lobby opens, so it no
longer locks by itself in the lobby. Locked **by hand** (the side button), it is still suspended: the
house takes itself off the air, and when the phone is unlocked it listens on the same port again, goes
back on the air, and every seat comes back to its own chair by its own token (D-225; section 6 checks
it). Before D-225 the house dropped every seat for good (`Websocket handler failed … ENOTCONN`, then
`WILL NOT ARM — 0 SEAT(S)` under an ARMED screen). `start` still refuses to launch on a locked phone,
says so if the app could not start because it was locked, and while it follows the log it prints
`!!! THE PHONE IS LOCKED` when the phone locks. It can only see a lock on a phone with a passcode.

## 1. Install

```
tools/one-phone/round.sh install
```

**PASS:** `installed.` The team comes from `SOMEONES_HOME_DEVELOPMENT_TEAM` or, failing that, from the
Mac's one Apple Development certificate. The script never guesses one.

- `NO SIGNING IDENTITY` or `SIGNING FAILED`: do what the message says (Xcode → Settings → Accounts, or
  open `iosApp/iosApp.xcodeproj` once and pick the team), then run it again.
- If the app will not open on the phone with *Untrusted Developer*: Settings → General → VPN & Device
  Management → trust the developer.

## 2. Permissions sweep

Open Someone's Home **by hand** from the Home Screen and walk setup in the light. On the permissions
screen, press REQUEST WHAT IS MISSING and allow every prompt (Bluetooth, Camera, Notifications, Local
Network, and any other it asks for). Then check Settings → Apps → Someone's Home.

- **PASS:** every row the app asks for is on.
- Swipe the app away afterwards. `start` relaunches it with its launch switches.
- Set **Auto-Lock to 30 seconds** (Settings → Display & Brightness) and leave it there for every
  round: since D-224 the host is held awake from its lobby on, so Auto-Lock Never is no longer needed,
  and 30 s is what round A's step 4 and section 6 check against. Put Auto-Lock back to your usual
  setting when you are done.

`start` would raise the same prompts itself, but it waits only 15 s for them.

## 3. Round A, by hand: off before arming, then what goes on the air

```
tools/one-phone/round.sh start --no-install --by-hand
```

`--by-hand` leaves the round to you: nothing is scripted on the phone. The Simulators still walk their
work orders and check in at every meeting by themselves. Wait for `seat 2 is seated`. The phone shows
the lobby with three seated. **Do not press LIGHTS OUT yet.**

**Step 0, nothing before arming (D-067).** In a second terminal:

```
tools/ble-peer/.build/release/ble-peer sniff --round-seed 20261116 --seats 3 --seconds 20
```

- **PASS:** no `[radio]` line in the followed log, and the sniffer ends on `HEARD NOTHING ON 5348` and
  `RESULT: NO FAIL`.

**Arm.** Press LIGHTS OUT on the phone.

- **PASS:** in this order, `PUSH … ContactSeedIssued`, `[radio] SCAN ON`, `[radio] WAKE — THE MANAGERS
  ARE BUILT (ARMING)`, `[radio] ADVERTISE xxxxxxxx · WINDOW n · WALL ms`, `[radio] ADVERTISING`. Also
  `WHAT THIS PHONE IS — INSIDER` (seat 0, as the seed deals it).
- **Expected, not a failure:** a `[house] RADIO FAULT — SEAT 1 …` and `SEAT 2 …` naming `DEVICE`. Those
  are the Simulators' radios reporting no Bluetooth. A device fault is named by no notice.
- **FAIL:** `ADVERTISING FAILED`, or an `OUTAGE` line on the phone.

**The sniffer checks.** Hold the phone about 30 cm from the Mac and run:

```
tools/ble-peer/.build/release/ble-peer sniff --round-seed 20261116 --seats 3 --seconds 70
```

Every advertisement prints as one line: time, `RSSI`, the full service UUID list, `NAME`, `MFR`,
`SVCDATA`, `TX`, `CONN`, the `TOKEN`, the Mac's handle for the sender's address (`ADDR`), and which seat
and window the token resolves to.

- **PASS, shape:** the list is exactly `[5348, <token as a UUID>]`, and `NAME no · MFR no · SVCDATA
  no` on every line. The run ends on `RESULT: NO FAIL`. Any `FAIL —` line is a fail: it names what was
  on the air that should not be.
- **PASS, the token is the house's:** every line ends `= SEAT 0 · WINDOW n (NOW)`, and the token's first
  8 characters match the phone's latest `[radio] ADVERTISE` prefix.
- **PASS, rotation:** a `TOKEN CHANGE … AFTER 15.0 S` line every 15 s (14.x to 15.x is fine), and
  `WALL mod 15000` under about 1100 ms on each. That is also step 1's clock check with the Mac standing
  in for the second phone: a large fixed value means one of the two clocks is off.
- **Report, not a pass or fail:** `NOTE` lines (TX power on the air, an unrecognised key), and
  `ADDRESS CHANGE` lines. If `ADDR` stays the same across several `TOKEN CHANGE` lines, anybody
  listening can link one window's token to the next by address. That is unit 6's finding to rule on.

**Step 4 (the device check's step 6), the screen stays awake (D-221).** Put the phone down untouched
for a minute (twice Auto-Lock).

- **PASS:** it never dims or locks, and its brightness does not move. (Since D-224 the host does not dim
  in its lobby either; section 6 checks that. A guest's lobby still dims on its usual schedule, which
  needs a second phone to see.)

**Step 5 (the device check's step 7), Bluetooth switched off is named (D-222, D-223).** Open Settings →
Bluetooth on the phone, switch it off, wait 5 s, switch it on, and go back to the app.

- **PASS:** the phone logs one `[radio] OUTAGE — …` line for each manager (`SCANNER OFF` and
  `ADVERTISER OFF`), **exactly one** `[radio] FAULT — THE RADIO WENT DOWN (…) · PERSON · THE HOUSE IS
  TOLD ONCE`, and on the way back `ADVERTISING` with **no** `ADVERTISING FAILED`. The host (the same
  phone) logs exactly one `[house] RADIO FAULT — SEAT 0 · PERSON · NAMED AT THE NEXT MEETING`. Nothing
  on the screen changes.
- Going to Settings puts the host in the background, so the house pauses while you are there and the
  Simulators may reconnect. That is this rig's limit, not the radio's: with two phones the switch-off
  would happen on a phone that is not hosting.
- **Then call a meeting** at the hosted home's meeting card with the phone's camera. The Simulators
  check in by themselves; check the phone in.
  - The card must belong to the home the phone hosts. By default that is `ONE PHONE`, a seeded home
    with no printed cards. To host a home you mapped on this phone, with its printed cards, start the
    round with `ONE_PHONE_HOME="<that home's name>" tools/one-phone/round.sh start …`; a stored home
    is hosted as it is and never overwritten.
  - With no printed card, do this step in round B instead: switch Bluetooth off and on right after
    LIGHTS OUT. The bench's own meeting (about 15 s in) then carries the notice. **PASS:** when the gate closes, the phone logs `[round] PUSH … NOTICE
  — SEAT 0 UNREACHABLE` just before the `PHASE Discussion` push. At the meeting after that, with nothing
  switched off between, it logs `NOTICE — NOBODY NAMED` and the notices screen shows only its band.
- **FAIL:** no FAULT line, one that says `DEVICE`, more than one per switch-off, `ADVERTISING FAILED`,
  or a missing notice. To switch off a second time, wait until the phone has been back for a few
  seconds: a switch-off within two seconds of coming back is counted as the same one.

Stop with Ctrl-C. To check the end of step 4, press LEAVE on the phone first and put it down: **PASS:**
it dims and locks on its usual schedule.

## 4. Round B, scripted: the Mac advertises as the Revoked seat

The house drops a sighting nobody returns, so a touch between the phone and the Mac is never a contact.
A **report** needs one side only, so this is the one result the phone and the Mac can produce together:
the bench Revokes seat 1 (a Simulator), the Mac advertises seat 1's tokens, the phone hears them, and
the house resolves the phone's sighting to seat 1 and rules on it.

First find the Mac's strongest spot. The house only counts **-35 dBm or stronger** (D-213):

```
tools/ble-peer/.build/release/ble-peer sniff --round-seed 20261116 --seats 3 --quiet
```

That needs a phone that is advertising, so do it at the end of round A, or right after LIGHTS OUT in
round B. `--quiet` prints the median and best RSSI every 2 s. Move the phone over the Mac (around the
hinge and the keyboard) and remember where the reading is best.

Then, in one terminal:

```
tools/one-phone/round.sh start --no-install
```

Add `--auto-lights-out` to have the bench press LIGHTS OUT itself once three are seated; then start
`advertise` first. Either way the bench on every seat waits for LIGHTS OUT for as long as the room
takes: it prints `STILL WAITING FOR THE LIGHTS TO GO OUT — 60S …` every minute, and gives up only if
the lights have gone out and no work order arrives within 60 s.

and in a second terminal, before you press LIGHTS OUT:

```
tools/ble-peer/.build/release/ble-peer advertise --round-seed 20261116 --seat 1
```

It prints `ADVERTISE xxxxxxxx · WINDOW n · WALL ms` every 15 s, the phone's own line.

Press LIGHTS OUT and **hold the phone at the strongest spot from then on.** The bench walks one shift
(about 15 s), Revokes seat 1, and a few seconds later reports it from seat 2 itself.

- **PASS, the bench plays:** after `[house] === THE LIGHTS GO OUT`, `[round-bench] THE ORDER ARRIVED`
  and `=== ACT 2, A SHIFT ===`. **FAIL:** any `NO WORK ORDER ARRIVED` line.

- **PASS, the phone hears the Mac:** the phone logs `[radio] HEARD 1 — yyyyyyyy -nn`, where `yyyyyyyy` is
  the Mac's latest `ADVERTISE` prefix (or the one before it, at a window edge).
- `[round-bench] THE REVOKE LANDED ON SEAT 1`. If it names another seat, the dealing was not the one
  pinned: stop, and re-run `advertise` with that seat.
- **PASS, the house resolved the phone's sighting,** one of:
  - `[round] PUSH … MEETING CALLED BY SEAT 0 · RevokeReported…`: the phone's report beat the bench's.
    The strongest result.
  - or, once the bench has reported it (`SEAT 2 FOUND A REVOKED PLAYER`), `[house] REFUSED —
    AlreadyReported · MeetingCalled (THE RADIO'S, NOT ANSWERED)` while you hold the phone there, about
    once every 5 s. Nothing but the phone's own upload can produce it after the bench's report, so it
    shows the house mapped the Mac's token to seat 1.
- **If HEARD shows the Mac's prefix but neither line appears:** look at the RSSI in the HEARD lines. If
  it never reaches -35, the gate held. That is unit 5's finding (the Mac is not a phone), not a radio
  fault. Write down the best value.

Stop with Ctrl-C in both terminals.

## 5. A rough RSSI table

With the phone armed and advertising (round A or B), run `ble-peer sniff --round-seed 20261116 --seats 3
--quiet` and hold the phone for about 10 s at each distance. Write down the MEDIAN and BEST:

| | Mac hears phone (sniff) | Phone hears Mac (`HEARD`, while `advertise` runs) |
|---|---|---|
| touching the strongest spot | | |
| 30 cm | | |
| 1 m | | |
| through a wall | | |

**PASS** is only that the numbers fall with distance. This is a Mac and a phone; it does not set the
contact gate.

## 6. The host locks in the lobby, and the room comes back (H1, H2: D-224, D-225, D-226)

The bug this checks: on 2026-11-16 the host locked in the lobby, every seat dropped for good, and
LIGHTS OUT logged `WILL NOT ARM — 0 SEAT(S)` under a screen that said ARMED. Auto-Lock at 30 s (step 2).

```
tools/one-phone/round.sh start --no-install --by-hand
```

Wait for `seat 2 is seated`. Hand the phone's line over if the lobby asks for it, and wait until the
lobby shows three seated and `3 of 3 handed theirs over`, with LIGHTS OUT live. Note the port in the
phone's `[lobby] HOSTING 'ONE PHONE' ON :<port>` line. **Do not press LIGHTS OUT yet.**

1. **Awake in the lobby (H1).** Put the phone down untouched for a minute (twice Auto-Lock).
   - **PASS:** it never dims or locks, and its brightness does not move.
2. **Lock it by hand.** Press the side button. Wait 20 s.
   - **PASS:** the launcher prints `!!! THE PHONE IS LOCKED`, and the phone's log has `[lobby] THIS PHONE
     LEFT THE FOREGROUND — THE HOUSE IS OFF THE AIR UNTIL IT IS BACK` (it may only reach the log at the
     unlock). `seat-1.log` and `seat-2.log` show their sockets dropping.
3. **Unlock it** and come back to the app.
   - **PASS, in order:** `[lobby] BACK IN THE FOREGROUND — LISTENING AGAIN ON :<the same port>`, then
     `[lobby] BACK ON THE AIR AS 'ONE PHONE'`. While the seats come back the lobby may show `NEEDS 3
     RESIDENTS` under a defunct LIGHTS OUT: that is the guard, not a fault. Within about 15 s the lobby
     shows three seated again, the same three names (their order may change), and `3 of 3 handed
     theirs over` with **nobody re-typing a line**.
   - **FAIL:** a different port; `COULD NOT REOPEN`; any `JOINING FRESH` or `A CONNECTION WAS REFUSED`
     line (a phone came back as a stranger); a fourth seat; the count stuck below 3.
   - **Report, not a fail:** `NOT BACK ON THE AIR — …`. Seated phones come back by address without the
     advertisement; only a phone that has not joined yet would miss it.
4. **LIGHTS OUT.** Hold it.
   - **PASS:** `[house] ARMED — 3 SEAT(S) IN THE ROUND`, the screen ARMED, and the Simulators' work
     orders arrive (`[round-bench] THE ORDER ARRIVED` in their logs).
   - **FAIL:** `WILL NOT ARM`, or ARMED on the screen with no `[house] ARMED —` line.
5. **Hosting ends, and the screen sleeps.** Run the section again, and at step 4 press LEAVE instead of
   LIGHTS OUT, then put the phone down.
   - **PASS:** it dims and locks on its usual schedule.

Put Auto-Lock back to your usual setting when you are done.

## What only more phones can verify

- **A real Revoke.** It needs mutual sighting, and the Mac cannot upload what it hears. The device
  check's steps 2 and 3 (a touch answered on both phones, the target's scan going off) need two phones.
- **Phone-to-phone RSSI** and therefore the -35 gate itself (unit 5). Mac numbers do not transfer.
- **Two phones agreeing on the window** (the device check's step 1). Round A only compares the phone to
  the Mac.
- **Sniffing a full room** (unit 6, three or more phones): whether an Insider's phone and a Resident's
  look the same on the air, and the address linkage across phones.
- **Contact-to-dark timing** on 240 fps video (unit 7) and **allocation with seven peers** (unit 8).
- **Bluetooth switched off on a phone that is not hosting** (step 5 without the host pausing).

## Report back

For each of step 0, arming, the sniffer's shape, token and rotation checks, steps 4 and 5, round B and
section 6: pass or fail. Also send any `FAIL`, `NOTE`, `OUTAGE` or `REFUSED` line, the RSSI table, and the run
folders under `/tmp/someones-home-one-phone/runs/` (one per `start`; `latest/` is the newest).
