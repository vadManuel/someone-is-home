# Replacing Bluetooth with UWB: the plan (2026-11-18)

Inputs:
- the owner's rulings (`owner-decisions-2026-11-17.md`: the UWB section, U1–U5, and the "later" section);
- the spike (`uwb-spike-results-2026-11-18.md`);
- a map of everything Bluetooth does today (summarised under "What Bluetooth does today" below).

Nothing here is built yet.

## What Bluetooth does today

Bluetooth does one thing on the air:
- Each phone advertises a rotating 16-byte HMAC token (D-032, D-212) and scans for the others.
- Once a second, each phone sends the house a fixed 24-slot list of (token, RSSI, window) readings (`SightingUpload`, D-212). Unused slots carry HMAC filler.

The house turns those readings into three things:
- **Revoke contact** (`core/Contacts.kt`, `resolveContacts`): a mutual sighting at RSSI ≥ −40 (D-233) becomes `ContactMade` both ways.
- **Report**: a one-sided sighting of a Revoked seat, seen in 2 of 3 uploads (D-236), becomes `MeetingCalled(RevokeReported)`.
- **The spectator map** (`core/Placements.kt`): a sighting from a phone that is performing at a card, at RSSI ≥ −75, places the seen seat in that room (D-136, D-272).

The radio also reports outages: `CBManagerState` maps to `RadioFaulted(person | device)` (D-187, D-222, D-223).

Nothing sends data phone to phone. The lobby uses Bonjour, not Bluetooth. Only the cheats build constructs the real radio; the release build is the boot screen only.

## What UWB changes, and what it keeps

**Kept: the house decides everything.** Phones measure, the house rules. The upload stays one fixed size, once a second, from every seat, out seats included.

**Changed: a phone must hold each peer's discovery token before it can measure.** Today's anonymous broadcast becomes a broker:
- Each phone opens one Nearby Interaction session per other seat.
- It hands each session's token to the house.
- The house forwards each token to the one phone it is for.

So the house still keeps seat identities from the phones (D-032's *resolvable only by the house*):
- Each session is addressed by a **handle** the house deals: random, per pair, and per round.
- The phone never learns which seat a handle is. It uploads (handle, distance), and the house maps handles back to seats.
- Without this, a modified phone could draw a live radar of every named person in the house.

**Changed: distance replaces RSSI.**
- The phone keeps, per handle, the **nearest distance it read in the last second** and the number of readings.
- Keeping the nearest is what makes U1's flyby work. A pass inside 10 cm is usually caught by one or two readings at 150 ms, and the 1-second upload must not average it away.

**Changed: a Revoked phone keeps ranging.**
- Today a Revoked phone advertises and never scans (D-008).
- UWB ranging is a two-way exchange: if the Revoked phone closed its sessions, nobody could measure the distance to it, and the report would be impossible.
- Its sessions stay open. Its uploads stay full-size, as every out phone's already are (D-217), and the house reads only the Resident's side of a report.

## The units

Each unit is its own commit, with tests, a broken-guard proof and, where a screen changes, a looked-at PNG.

### 1. The launch gate (needs no phones to build; one phone to see it)
- `platform`: `expect fun uwbSupported(): Boolean`. The iOS side is `NISession.deviceCapabilities.supportsPreciseDistanceMeasurement`, and false wherever Nearby Interaction is missing.
- `ui`: the approved screen: **"THIS PHONE CAN'T PLAY" / "Someone's Home needs Ultra Wideband: iPhone 11 or later, not iPhone SE."** It is shown before anything else and has no way forward.
- `Info.plist`: `NSNearbyInteractionUsageDescription` moves into the source plist for every build. Today a build phase injects it into Debug and Playtest only. Its wording goes to the owner.

### 2. The wire (model)
- **`TokenOffer`, phone to house, carried in the 1-second upload** like the request slot (D-263, D-275):
  - It holds this phone's session tokens, each padded to a fixed size.
  - Archived tokens measured 312 bytes (iPhone 13 Pro, iOS 18) and 345 bytes (iPhone 16 Pro, iOS 26), so the pad is 384 bytes, checked at runtime. An oversized token is a device fault, never a short frame.
  - A phone with no new token sends filler of the same size. The slot is in every upload, from every seat.
- **`TokenBundle`, house to phone, one fixed-size message per phone** at arming, and again whenever any peer's token changes:
  - It holds (handle, peer token) for every other seat, padded to the 17 others of an 18-seat round (D-143).
  - Every phone gets one at the same moment, the same size, whatever changed.
- **`DistanceUpload`** replaces `SightingUpload`'s body:
  - It has 17 fixed slots of (handle, nearest distance in centimetres, readings this second). Unused slots are keyed filler, as today.
  - The cadence stays at `CADENCE_MILLIS`.
- The recording format bumps for any event that changes shape: `Event.Sensed` and the sightings it carries.

### 3. The house's rules (core)
- **Revoke contact:** an armed Insider's phone and another phone, both reporting a nearest distance at or under `Balance.REVOKE_REACH` = **10 cm** (U1) within `MUTUAL_TICKS` of each other.
  - Both sides are required, as both RSSI sides are today, so one modified phone cannot claim a touch alone.
  - Both phones read the same two-way exchange, so a real flyby shows on both.
  - Stays the same: the arm window (D-280), the answer frames (D-216, D-219) and the debounce.
- **Report (Q5, ruled 2026-11-18):** a phone reading a Revoked seat within `REPORT_REACH` = **30 cm**, held for **2 s**.
  - The phone does not know who is Revoked, so the house drives the circle:
    1. The first upload in range starts a pending report, and the house sends that one phone a fixed-size `ReportPending`. The phone fills a circle over 2 s.
    2. If the phone is still in range at 2 s, the meeting is called and the phone shows the call.
    3. If it leaves range first, the house sends `ReportCancelled`.
  - Both frames depend on being near a body, never on the receiver's role.
  - The 5 s debounce between two phones becomes a named grace: the Insider who did the Revoke can't report their own victim during it, and can afterwards (option a).
- **The spectator map:** a phone performing at a card places a seat in that room when it reads it within `ROOM_REACH`. This is provisional; it starts at 1.5 m.
  - U3 accepts that a wall doesn't lengthen the reading. The map's deliberate error (D-272) stays.
- **Outages:** Nearby Interaction has no power states, so faults come from sessions instead:
  - `UserDidNotAllow` is the person's fault;
  - `ResourceUsageTimeout`, `UnsupportedPlatform`, `ActiveSessionsLimitExceeded` and invalidation are the device's;
  - a suspension (lock or background) is already a foreground loss (D-223).
  - Each fault keeps the one fixed `RadioFaulted` body.

### 4. The radio (platform, iOS)
- **`NearbyRadio`**, replacing `ContactRadio` and `SwitchboardRadio`:
  - one `NISession` per handle, opened when the `TokenBundle` arrives;
  - re-run when a suspension ends, which the spike measured as recovering within 0.5 s with no new token;
  - when a session is invalidated, it gets a new session and a new token, offered in the next upload;
  - a fixed table of the nearest reading per handle, drained once a second.
- The spike's `UwbPeerSession` is the starting point. It already has the `==` delegate fix (cff978e).

### 5. The bench (harness and cheats tests)
- `SimulatedAir`'s distance-to-RSSI curve becomes a distance model built from the spike:
  - about ±3 cm of noise with a clear line;
  - a body in the way reads 3–5 m long, and never short;
  - a reading every 150 ms;
  - a pause, then a recovery within 0.5 s, on lock or background.
- The roughly 13 benches that go through it are retuned to it.
- The resolver's differential tests are rebuilt on distances.

### 6. Bluetooth comes out
- Removed:
  - `BluetoothRadio` and the CoreBluetooth wrapper;
  - `NSBluetoothAlwaysUsageDescription`;
  - the Perms walk's BLUETOOTH row, which gets a Nearby Interaction row in its slot (see "Questions for the owner");
  - `tools/ble-peer`;
  - the token HMAC pipeline, where handles replace it.
- **The NameDrop setup step (D-248) also comes out:** the owner rejected it, and the stacked or flyby reach makes it unnecessary.
- Last, after units 1–5 are green on phones.

### 7. On phones (needs the owner)
- **Seven, and ideally seventeen, live peers on one phone.** The spike showed that iOS will *hold* 8 sessions, not that 7 ranging sessions keep 6.7/s each. This decides the largest round. It needs at least 3 phones; a full answer needs 8.
- **Bluetooth switched off:** does Nearby Interaction still range? It should, because peer sessions don't use Bluetooth, but this is unverified.
- **The permission prompt:** where iOS asks "find nearby devices" in the setup walk (D-161: every permission is asked at setup).
- **A round's battery and heat** with 7 or more sessions open.
- **A flyby inside 10 cm,** done the way a player would, plus a top-to-top flyby against NameDrop.

## Questions for the owner

1. ~~Q5, the report~~: ruled on 2026-11-18. It is 30 cm held 2 s, with a filling circle, and the Insider can report their own victim after a 5 s grace.
2. **The permission row's wording:** the Nearby Interaction prompt text, and the setup walk's line for it.
3. **Round size,** if the live-peer test shows that iOS can't range with 17 phones at once.

## Order

Units 1 and 2 can start now. Unit 3 depends on 2. Unit 4 depends on 2, and on a phone to see it. Unit 5 goes with 3. Unit 6 comes last. Unit 7 runs whenever the owner has phones.

## Risk found 2026-11-18: how many peers one iPhone can range at once

A web search (Apple docs, Apple Developer Forums, WWDC, papers) found no published limit.

What Apple says:
- Docs for `activeSessionsLimitExceeded`: *"The maximum number of concurrent Nearby Interaction sessions varies across device type and platform."*
- Staff on the forums: *"dynamic and may be limited by available system resources"* (forums thread 652180, Jun 2020), and *"not limitless … may vary depending on the devices and iOS versions"* (thread 700295, Feb 2022).

The only figures are developer reports from 2021 (thread 692392):
- iPhone 11: a limit of 2 sessions.
- iPhone 12: four phones with 3 sessions each worked; a fifth phone failed.
- Nothing is published for iPhone 13–17, the U2 chip, or iOS 16 and later.

No one has published whether the rate per pair drops as peers are added. One paper (arXiv 2511.03534) reports about 55 Hz with one peer, against our 6.7 Hz. The cause of the difference is unknown and should be checked: a setting we could change would matter a great deal.

Consequences:
- An 18-seat round needs 17 sessions per phone, which is probably above the limit.
- The spike's 8 sessions on the 16 Pro had no live peers, and iOS may count only sessions that are actually ranging.
- A flyby (U1) needs the pair to be ranging at the moment of the pass.

Proposed mitigation, **not ruled**:
- Bluetooth, already built, picks roughly which peers are near, which it can do for any number of phones.
- UWB ranges the nearest few precisely.
- Bluetooth would then decide nothing in the game, only which peers to measure. This is the way Apple itself pairs the two.
- **The owner must rule on it**, since U5 and the "replace Bluetooth for everything" ruling are both in play.

**A device test comes before unit 4:** with 3 or more iPhones, open sessions one at a time until iOS refuses, and measure the rate at 1, 2, 4, 8 and 16 peers.

Units 2, 3 and 5 can be built without knowing the limit.
