# Contact radio, unit 4: device check

The CoreBluetooth radio (D-218) is built and compiled but has not yet run on a phone. This check is
the first run. It answers four questions: do two phones hear each other, does a Revoke land, does
a report land, and do the phones agree on the window. Every answer comes from the phones' stdout.

## What you need

- **Three iPhones.** A round arms at three seats or more (`Balance.MINIMUM_SEATS`), so two phones
  alone cannot reach a Revoke. Every phone carries the radio, and the host phone plays a seat too.
  Phone A hosts. The examples below call the Insider **I**, its target **T**, and the third phone
  **R**, a Resident. Which phone is the Insider is only known once the round arms.
- All three on the same Wi-Fi, with Bluetooth on.
- On each phone, **leave Auto-Lock as it normally is** (Settings → Display & Brightness → Auto-Lock,
  30 seconds or 1 minute is best for step 6). Since D-221 the app keeps the screen awake from arming
  until the round ends or the phone leaves. A phone whose side button is pressed, or that leaves the
  app, is still suspended within seconds, and its radio and socket go quiet. With no background
  modes this is expected: it is not a fault.
- On each phone, **Settings → General → Date & Time → Set Automatically: on.** The window is cut
  from the wall clock.
- On each phone, grant Bluetooth at the Perms screen, in the light, before taking a seat.

## Install

Plug all three phones into the Mac, then run:

```
SOMEONES_HOME_DEVELOPMENT_TEAM=<your Team ID> ./run-app.sh device playtest
```

This builds the Playtest configuration once per phone, installs it on each, and checks that it is
still running. To work on one phone only, add `APP_DEVICE_NAME=<part of its name>`.

## Logs

`run-app.sh` does not attach a console. Open one terminal per phone and relaunch the app with
stdout attached:

```
xcrun devicectl list devices                     # the CoreDevice identifier of each phone
xcrun devicectl device process launch --console --terminate-existing \
    --device <identifier> home.someoneshome.app 2>&1 | tee phone-A.log
```

The lines that matter are `[radio] …` (this unit), `[round] PUSH n — …` (what the house told this
phone) and `[house] …` (the host only). To filter them:
`grep -E '\[radio\]|\[round\] PUSH|\[house\] (REFUSED|RADIO FAULT)' phone-A.log`.

## 0. Off before arming (D-067)

Walk all three phones through the lobby, but do not press LIGHTS OUT yet.

- **Pass:** no `[radio]` line on any phone.

Press LIGHTS OUT. Each phone should then log, in this order:
`PUSH … ContactSeedIssued`, `[radio] SCAN ON`, `[radio] WAKE — THE MANAGERS ARE BUILT (ARMING)`,
`[radio] ADVERTISE xxxxxxxx · WINDOW n · WALL ms`, `[radio] ADVERTISING`.

- **Fail:** `ADVERTISING FAILED — …`, or an `OUTAGE` line.

The role line `WHAT THIS PHONE IS — INSIDER` tells you which phone is I.

## 1. The two clocks agree on the window

Every 15 s each phone logs a new `ADVERTISE … · WINDOW n · WALL ms`.

- **Pass:**
  - All phones name the same `WINDOW n` in the same 15 s.
  - For each line, `WALL mod 15000` is under about 1100. That means the token turned within one
    beat of the boundary.
  - The phones' `WALL` readings for the same window are within about a second of each other.
- **Fail:** the phones are a window apart, or `WALL mod 15000` sits near a fixed large value. Either
  means a phone's clock is off. Check Date & Time.

## 2. Mutual sighting

Hold two phones about 30 cm apart for 10 s, then put them back to back.

- **Pass:**
  - Each phone logs `[radio] HEARD 1 — yyyyyyyy -nn` once a second, where `yyyyyyyy` is the
    **other** phone's latest `ADVERTISE` prefix (or the one before it, around a window edge). That
    match is the proof that what is on the air is the token.
  - RSSI is around -50 to -70 at 30 cm and rises when the phones touch.
  - Write down the strongest touching value for each phone pair. That number is unit 5's input.
- **Scan rate:** the `[radio] TALLY 10 S — CALLBACKS c …` line should show roughly 20 to 40
  callbacks per phone heard. That is at most 4 a second, because the scan restarts every 250 ms.
  - **If CALLBACKS stays at 1 per phone after the first report**, iOS is not resetting its duplicate
    filter on a restart. Report this. The fix is one constant (`ContactAdvertisement`).
- **Touch:** a touch between two living phones with no Revoke armed logs `PUSH … A TOUCH, ANSWERED`
  on both phones.
  - **If touching never gives -35 or stronger**, the resolver's gate (D-213) never opens. Note the
    best RSSI you saw: that is unit 5's finding, not a radio fault.

## 3. A Revoke lands

On phone I, arm Revoke from its abilities. Then hold I against T, back to back, for 2 to 3 s.

- **Pass:**
  - I logs `PUSH … ABILITY FIRED · COOLDOWN STARTED=true`.
  - T logs `PUSH … THE HOUSE WITHDREW MY LAMP`, then `[radio] SCAN OFF — ADVERTISING ONLY FROM HERE`.
  - From then on, T logs **no** `HEARD` lines, and T's `TALLY` reads `SCANNING NO · ADVERTISING YES`.
  - T keeps logging a new `ADVERTISE` every window (D-008: advertises, never scans).
- **Fail:** the host logs `[house] REFUSED — …`. The reason names which gate refused it.

## 4. A report lands

Hold R, a living phone, against T, the Revoked phone, for 2 to 3 s. Only R needs to hear T, because
T no longer scans.

- **Pass:**
  - R logs `HEARD` with T's current prefix.
  - Every phone logs `PUSH … MEETING CALLED BY SEAT <R's seat> · RevokeReported`.
- Contacts are ignored while a meeting runs (R5). Touching during the meeting does nothing, and that
  is correct.

## 5. Optional: an outage stays off the screen (rule 6)

Pull down Control Center on any phone and turn on Airplane Mode. Wi-Fi drops too, unless you have
told iOS to keep it on in Airplane Mode.

- **Pass:**
  - The phone logs `[radio] OUTAGE — SCANNER OFF` (and `ADVERTISER OFF`).
  - Nothing on the screen changes: no dialog, no flicker, no lamp change.
  - Turn Airplane Mode off: `ADVERTISING` returns, and `HEARD` resumes, without anyone touching the
    app.
- Since D-222 the house is told once per outage. Step 7 checks that.

## 6. The screen stays awake for the round, and only for the round (D-221)

Set one phone's Auto-Lock to 30 seconds before the lobby.

- **Before arming:** leave that phone untouched on the lobby screen. **Pass:** it dims and locks on
  its usual schedule. Unlock it.
- **After LIGHTS OUT:** put it down untouched for at least twice its Auto-Lock time. **Pass:** the
  screen never dims and never locks, and its brightness does not change at any point. The only light
  changes are the game's own (the opening text's dim, D-118).
- **After the round ends** (or after LEAVE on that phone): put it down again. **Pass:** it dims and
  locks on its usual schedule.
- **Fail:** the phone dims or locks mid-round; or it stays awake in the lobby before arming or after
  the round; or its brightness moves when the round arms or ends.

## 7. A radio that goes down is announced at the next meeting (D-222)

During a round, on one living phone (call it **F**), open **Settings → Bluetooth** and switch it
off. (The Control Center toggle may only disconnect accessories and not power the radio down; use
Settings.) Wait 5 s, switch it back on, and return to the app.

- **Pass, on F:**
  - `[radio] OUTAGE — …` lines, and **exactly one** `[radio] FAULT — THE RADIO WENT DOWN … THE HOUSE
    IS TOLD ONCE`, however many OUTAGE lines there are.
  - Nothing on F's screen changes: no dialog, no flicker, no lamp change, no buzz.
- **Pass, on the host:** exactly one `[house] RADIO FAULT — SEAT <F's seat> · THE HOUSE ANNOUNCES IT
  AT THE NEXT MEETING`. Nothing reaches any phone at that moment: no `[round] PUSH` line on any phone
  that is about F.
- Then call a meeting at the meeting card and check everybody in. **Pass:** when the gate closes,
  **every** phone, living and out, logs `[round] PUSH … RADIO NOTICE — SEAT <F's seat>`, just before
  the `PHASE Discussion` push that opens the notices.
- At the meeting after that, with no radio switched off in between, every phone logs `RADIO NOTICE —
  NO RADIO WENT DOWN`. That empty notice at every meeting is correct (rule 1).
- Do it once more with F being the Insider if you can: the lines must be identical, bar the seat.
- **What the screen says about it is not decided yet** (D-222). The notices screen still shows its
  old fixture words. That is expected and not a failure.
- **Fail:** no FAULT line; more than one FAULT line, or more than one host line, for one switch-off;
  a phone missing the notice; or anything on F's screen moving.

## Report back

For each step, 0 to 7, send pass or fail. Also send:
- the strongest touching RSSI for each phone pair;
- the CALLBACKS figure from one `TALLY` line;
- any line containing `FAILED`, `OUTAGE` or `REFUSED`.

Keep the logs: unit 6 (sniffing) and unit 8 (allocation) will want them.
