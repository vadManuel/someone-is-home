# Someone's Home: the occupancy map and the home plan on phones — build spec

*Read-only research, 2026-11-17, at `cd575db`. Nothing here is built. Unit 9 of the overnight
queue. Paths are relative to the repo root. `gdd` = `.../gdds/gdd-someone-is-home-2026-08-16/gdd.md`,
`DL` = the decision log beside it, `EP` = `epics.md` beside it.*

## 0. The problem in four lines

- `c02f094` (D-257) took the fixture bungalow and its counts off every housed Terminal, playback
  and couch. They now draw an empty frame with a dash. That is honest, and it is also an empty game
  layer: M11 and M12 have nothing to show.
- **No client has a plan to draw.** D-186 rolled the plan back to crossing to no client.
- **No message carries occupancy.** `Observation`/`ObservationView` have no producer, and the D-136
  spectator map has no producer either (`Placement.Inferred` is never constructed).
- **The house already holds everything it needs.** The host has the plan (`RoundAuthority.home`),
  knows where every performer is (`GameState.presence`), and gets one sighting upload a second from
  every living phone (D-212). D-212 itself says these uploads are the spectator map's input. What
  is missing is the pipeline from those inputs to narrow client types.

---

## 1. What the docs specify

### 1.1 The Terminal: who reads it, and when (living, either role)

- **A place, not a screen.** gdd:470: *"Viewable only at a dedicated map station marker, never
  during meetings."* D-199 (DL:4199): *"The map opens when the SCAN button is pointed at the T card
  … The scan is the station's check-in … and it is the claim on the exclusive lock."*
- **One reader.** gdd:472: *"The station locks on *any* viewing … Others may stand there; only one
  person reads."* D-203 (DL:4351-4370) lists four ways the lock comes free (the reader leaves, a
  meeting is called, the reader goes out, the connection drops) and says *"Nothing announces any of
  the four."*
- **Either role, same terms.** EmitSchema's `TERMINAL_ANSWERED` row is both living classes
  (`EmitSchema.kt:384-392`). gdd:526 counts an Insider squatting the station as part of the game.
- **Uncompletable task.** gdd:476: *"the motion budget applies … The station generates its own
  check-in, so using the surveillance system is itself surveilled."*

### 1.2 What the Terminal shows, with what error and staleness

- gdd:478: *"**What it shows: counts per room, never dots.**"*
- gdd:482: *"**Your own room is outlined** as the anchor … Only you see your own outline."*
- gdd:483: *"**Counts dim through three staleness bands** (fresh / stale / cold). Sensing only fires
  when someone scans, so a count may have data at 5s and 47s and nothing between."*
- gdd:484: *"**Jitter the age before bucketing**, signed, **rolled once per observation and stored**,
  so band boundaries cannot be used to read exact scan timing and counts do not flicker between
  bands at render time."*
- gdd:485: *"**Stairs are transit zones — drawn, never counted.**"*
- gdd:487-493, error injection: *"False positives (a phantom occupant) … False negatives (missed
  presence) → deniability. **Load-bearing**, because revoked phones go fully invisible … **Plausible,
  not random.** A phantom in an *adjacent* room reads as radio physics … **Roll once at capture,
  never re-roll.** … **Identical error rates for both roles.**"*
- gdd:524: *"**Coverage holes are a feature.** Rooms nobody scans in produce no data."*
- gdd:992-994 (F-007), carried into EP:256 (story 8.8): *"hold a room's last count for a few seconds
  after loss of detection … bias injected error toward *under*-counting occupied rooms … **Re-verify
  the whole staleness-leak analysis against counts before building it.**"*
- D-209 (DL:4494): bands are drawn as *"Fresh, stale and cold are `#FFC759`, `#E39A22` and
  `#C08A2E`"*.
- `Plan.timelapseCounts` KDoc (`ui/.../PanelModel.kt:2426-2433`): *"Every room carries a number,
  always. Zero and no-reading are the same fact."* This is code, not a ruling, but it agrees with
  gdd:524.
- **Static** (D-186, DL:3644; gdd:495): the living see *"Animated noise, with the real data faintly
  beneath it"* for 10 s; *"Tracking: Untouched."* Ghosts are exempt.
- gdd:1319 / EP:268 (8.16): *"Anonymize server-side. Resolve identity on the backend; ship only
  counts."*

### 1.3 How the house senses (the input to both maps)

- gdd:769: *"It knows who was near a scanner, and the scanner knows which room it is in."*
- gdd:773-777, the three sources: *"Check-in / task scan … Sweep on scan — when anyone scans, their
  phone sweeps for nearby devices … Sweep every 5s during a motion-restricted task — a player doing
  a long task becomes a stationary sensor."*
- gdd:779-787: *"Revoked players are excluded from every rendered count — but their phones stay on
  the air … **A revoked phone must never contribute sweep observations.**"*
- D-136 (DL:1971-1982): *"While a player scans and performs a Subroutine, their phone BLE-scans for
  nearby phones and places those players in that room … **adjacent-room bleed is accepted** … a
  performer's own scan placement is ground truth … no other phone's BLE sighting may relocate them.
  Inference never overrides knowledge. Every other placement is a sighting and may be overwritten
  by the next one."*
- D-111 (DL:1549): presence data *"feeds exactly one consumer, the spectator map's expiry (D-136),
  and may never reach a notice, a count, or any surface a living player can read."*
- D-155 (DL:2540): *"A player who is out never appears in occupancy or presence data anywhere in the
  house. The filter is **seat state**, applied by the house, at the source."*
- D-212 (DL:4596-4598): *"D-136 stands as written. It says which sightings the spectator map is
  built from, which is those made while a player performs, and that is the house's filter. It does
  not say when the radio runs. Continuous scanning feeds it the same sightings and more."*

### 1.4 The couch (the out): true occupancy, when, for whom

- gdd:543-547: *"**True occupancy** — live, every storey, no injected error, no staleness bands.
  **Still counts, never identities.** … **Not alignments.**"* Repeated at gdd:1052 and EP:341 (11.5).
- gdd:537-539: ghost mode appears only after the meeting ends; *"There is never a window where a
  ghost knows something the living do not."* EP:339 (11.3): *"**Never before.**"*
- D-134 (DL:1939-1942): the couch gets *"a live map (D-136), live Egress state, and live Resident
  progress"*.
- D-145 (DL:2302-2323): *"the out see the meter, the map and the vote live, and nothing is taken
  away"*. The correlation they can build is accepted because *"the out can act on none of it."*
- D-079 (DL:899-901): true occupancy is something *"no living player of either role may see"*; the
  allowlist is keyed on role and round-state.
- D-186 (DL:3646, 3673-3678): ghosts are exempt from Static; *"ghosts already get both bars, true
  counts, no injected error and no staleness bands."*
- D-178 (DL:3386): the couch's plan sits under an Egress widget whose frame keeps its height.

### 1.5 Timelapse

- gdd:499-517, the table: history 2:00, 6× (20 s), plays once, cooldown 2:00 from playback end,
  early cancel cuts the cooldown linearly by the fraction unwatched, oldest→newest, floor = start of
  round or end of the last meeting (that is, the start of the current shift). gdd:511: *"`timelapse
  cooldown == timelapse history duration`"*. EP:257-262 (8.9-8.12) match.
- gdd:517 / D-186: a playback crossing a Static window renders those seconds as static, about 1.7 s
  at 6×.
- gdd:1050 / EP:321 (10.11): *"The timelapse history floor resets to now"* at meeting end. gdd:1070:
  *"The loop resumes with the map wiped."*
- gdd:996 / EP:261 (8.13), F-008: no dot interpolation; *"Animate the numeral's count-up/down
  instead."*
- game-architecture.md:1037 lists Timelapse as cut #2 if the schedule slips (*"Keep the live count
  view"*).

### 1.6 Whether the plan may reach a client

- **D-175 (DL:3309-3313)**, the original crossing: *"A phone cannot draw a plan it was never sent, so
  the home's plan rides the arming event and reaches a living Insider — the largest single
  disclosure in the emit table, one class wide, and it must never be widened by one."* The plan then
  was rooms, names, stairs and cells with no markers (`git show 4cefbf5`, `HomePlanView`).
- **D-186 (DL:3694-3700)**, the rollback: *"**The home plan returns to crossing to NO client** … a
  row that existed only because a phone cannot draw a plan it was never sent. **Static has no plan
  to draw.** The row goes, and `Destination`'s own warning about the whole map reaching every
  client returns to being a warning about a thing the app does not do."*
- **EP:292 (9.11c)**: *"**No client is ever sent the home's plan**."*
- **`Destination.kt:27-29`**: *"What is NOT authorised is the whole map to every client. A client
  receives the destinations of its own order's lines and nothing else."* Here "map" means
  card→room (the answer key to work orders), not room geometry.
- **D-122 (DL:1766)** and D-123: the stations and active markers are round state, never stored with
  the home, so the circuit cannot be learned.
- **gdd:845**: *"a room the editor could mark is a room a Resident's phone could read"*, which is why
  Insider-only rooms are never app data.
- **D-186's reason, precisely:** the plan was harmful as an **Insider-only row** (one class wide, so
  role-asymmetric) and as the largest single item in the table. The text never says the geometry
  itself was a secret. Static's arrival removed the only screen that needed it.

### 1.7 Where the docs are silent

1. **How the Terminal and the couch draw a room without being sent a plan.** M11 needs an outlined
   room on a drawn plan (gdd:482). M12 needs *every storey* (gdd:546). D-186 sends the plan
   nowhere. No ruling covers this (see 1.8 #1).
2. **When a placement expires.** D-136 says the map *"has to know when a performance window opened
   and closed in order to expire placements"* and gives no duration. Nor does it say how long a
   check-in scan that opens no window (the T card, NOTHING FOR YOU HERE) counts as knowledge.
3. **The room-presence RSSI threshold.** The −40 floor (D-233) is for contact. Room placement needs a
   looser one; nothing names it. gdd:791: *"BLE RSSI is a proximity gate, never a map."*
4. **Staleness thresholds, jitter size, error rate, F-007 hold time.** All `[ASSUMPTION]`
   (gdd:1163-1164, A-7, A-8 at gdd:1558-1559). Playtest owns them.
5. **Storeys.** The ghost sees *every storey*. The Terminal's storey coverage is never stated. The
   shipped `FloorStrip` is *"a reading, not a control"* (`ScreensWork.kt:826-840`) hard-coded to
   GROUND/UPPER, and D-175 bans floor tabs on a picker.
6. **Whether the Terminal reader is a sensor.** Reading is *"treated as an uncompletable task"*
   (gdd:476), and a motion-restricted task makes you a sensor (gdd:777). The code places the reader
   with `open = false` (`Rules.kt:506`), so as built the reader is not a sensor.
7. **The timelapse cooldown's owner** (per station or per reader). gdd:499 calls it *"the only gated
   resource at the station"*. I read that as per station.
8. **Whether the ghost has an outlined room.** gdd:482 gives the outline to the Terminal reader.
   Ghosts are outside the system and unplaced (D-155). I read that as no outline.

### 1.8 Where the docs contradict each other

1. **The plan.** D-186 and EP:292 say no client gets it. M11 (gdd:478-485) and M12 (gdd:546) draw
   it on a living reader's phone and on every ghost's. **These cannot both hold. Owner Q1.**
2. **"True" occupancy.** gdd:546/1052 say *true … no injected error, no staleness*. D-136, which is
   later, says the spectator map *"is inference"*, *"knowingly imperfect"*, with *"adjacent-room bleed
   … accepted rather than engineered away: a map the out can read perfectly turns the couch into an
   oracle"*. The house cannot know true positions in any case (gdd:767-769). **Reconciled reading:**
   the ghost gets the house's own placements (D-136's inference, with bleed and holes), with **no
   injected error and no staleness bands**. "True" means *not deliberately falsified*. **Owner Q2
   (confirm).**
3. **The couch blind zone.** gdd:789, gdd:839, EP:159 (4.10) and EP:265 (8.15) keep a mapping defunct
   zone. D-155 supersedes them: filter by seat state, and the meeting room is ordinary. The GDD and
   epics rows were never swept. **D-155 wins.** Just a sweep, no question.
4. **F-008's numeral animation** (gdd:996, EP:261) against rule 5 and D-202 (DL:4326: *"No glow,
   gradients or local animation"*). **Owner Q4.**
5. **The `ObservationView` shape.** game-architecture.md:812-833 carries `tick` + `ageJitter` and has
   the client bucket the age. The code (`Observation.kt:42-46`) carries a precomputed `staleness:
   Int` and no time. They disagree on where bucketing happens. Resolved in unit O6 below; no question.
6. **`Observation.room` is a `MarkerId`** (`Observation.kt:13`), but an observation is about a room,
   and rooms are keyed by name (`HousePlan.paint` refuses a duplicate name across storeys,
   `HousePlan.kt:140-149`). Fixed in O6.

---

## 2. What exists in code (at `cd575db`)

### 2.1 The authority/client split, and its lack of a producer

| | where | state |
|---|---|---|
| `Observation` (has `trueCount`) | `model/.../Observation.kt:12-23` | authority type. **No constructor call anywhere.** `room: MarkerId` (wrong key, see 1.8 #6) |
| `ObservationView` | `Observation.kt:40-49` | `@ClientFacing @Serializable`, `internal` constructor, not a `data class` (no public `copy`), so it cannot carry `trueCount`. **No `RoundMessage` carries it.** Fields: `room: String`, `reportedCount`, `staleness` |
| `Observation.toView()` | `Observation.kt:52-53` | the only bridge. Unused |
| `Placement.Known` / `Inferred` | `Placement.kt:27-60` | D-136's two grades as two types. `Inferred` has **no constructor caller** (kept on purpose after D-186) |
| `GameState.placementOf` | `GameState.kt:553-554` | returns `Known` off `presence.at` **regardless of whether the window is open**. "Nothing in `core` calls this today" |
| `Presence` / `GameState.presence` | `Presence.kt:37-54`, `GameState.kt:177` | the presence plane, one row per seat that has scanned. Recorded, replayed, reaches nobody |
| `Effect.PresenceChanged` | `Effect.kt:441`, `EmitSchema.kt:394-410` (no row), `:736` (own seat only) | two independent denials |

### 2.2 The home, and what crosses at arming

| | where | state |
|---|---|---|
| `HousePlan` (floors, rooms as unions of `CellRect` strokes, `RoomKind.Room/Stairs`) | `HousePlan.kt:179-294` | **not `@Serializable`**. KDoc: *"reaches a client only as whatever narrower view a screen needs, constructed for the purpose"* (`:159-165`). Adjacency is `Floor.neighboursOf` (`:111-120`). `LIMIT = 256` (`:255`) |
| `HouseMap` (card→room, terminal, meeting) | `HouseMap.kt:121-356` | `destinations` (`:171`) |
| `SavedHome(plan, map)` | `SavedHome.kt:29-98` | what the host stores |
| `RoundAuthority.home` | `app/.../RoundAuthority.kt:168` | **the host holds the whole plan for the round**, set at `armWith` (`:279`). Its one reader is the Egress draw (`:479-485`, `egressFor(..., here.map, here.plan, ...)`) |
| `Event.RoundArmed.markers: List<Destination>` | `Event.kt:49-67` | the only geography on the arming event. **No plan** |
| `RoundArmed.terminal` / `GameState.terminal` | `RoundAuthority.kt:276-278`, `GameState.kt:286` | the T card **id only**. *"The room the host put it in is the host's to say aloud."* The T card's room is not in `GameState` |
| `GameState.destinations` | `GameState.kt:151-167` | card, glyph, room for ordinary markers. *"Nothing derives anything from it."* A client gets only its own work order's rows (`WorkOrder.asLines`) |
| prior art | `git show 4cefbf5` (added `HomePlanView` + `HomePlanOffered`, Insider-only) and `7713c56` (removed them) | cells rather than strokes; rode `RoundArmed.plan`, passed through `reduce` without being stored; four client plan types |

### 2.3 The radio side: the inputs already exist

| | where | state |
|---|---|---|
| `SightingUpload` | `model/.../protocol/SightingUpload.kt` | 24 fixed slots (token, RSSI, window) every 1 s from every living phone; out phones send keyed filler (R8, D-217). Unpacked at `:107-124` |
| `RoundAuthority.sighted` / `sight` | `RoundAuthority.kt:399-412` | unpacks and calls `resolveContacts` **only**. Nothing else reads a sighting |
| `ContactGate.RSSI_FLOOR = -40` | `core/.../Contacts.kt:167` | the contact gate. Not a room gate |
| arrival queue | `app/.../LobbyOverTransport.kt:830-878, 931` | `Arrival.Sighted` → `onSighted` |
| iOS radio | `platform/src/iosMain/.../BluetoothRadio.ios.kt:71` | exists (unit 4); RSSI not yet calibrated on phones (unit 5) |
| house clock on phones | `app/.../LobbyOverTransport.kt:1240` `houseNow()` | C1 / D-264: every phone measures the host's clock |

### 2.4 Rules touching the station

- `Rules.stationScanned` (`core/.../Rules.kt:499-511`): places the scanner at the T card with
  `open = false`, takes the lock if free, emits `TerminalAnswered(seat, reading)` and
  `PresenceChanged`. **No occupancy is computed or sent.**
- `GameState.reader` / `readerHolding()` (`GameState.kt:302-308`), freed four ways (D-203).
- `GameState.staticUntil` (`GameState.kt:202`): the current jam only, **no history of past jam
  windows** (the timelapse will need one).
- `SeedPurpose` (`core/.../Arming.kt:260-...`): HMAC-per-label draw streams (D-214). No occupancy
  label.

### 2.5 UI

| | where | state |
|---|---|---|
| `PanelVals.terminalCounts` / `timelapseCounts` / `trueCounts` | `ui/.../PanelModel.kt:1356-1362` | bungalow in the render harness, **null on a housed phone** (D-257) |
| `timelapseAt` / `timelapsePlayed` | `PanelModel.kt:1369-1372` | `-1:12`/0.44 in the harness, dash/0 housed |
| `object Plan` (fixture bungalow, 10×12, `HERE = "GARAGE"`) | `PanelModel.kt:2383-2454` | the only plan any in-round screen can draw |
| `KitLivePlan(counts)` | `ui/.../PlanViews.kt:232-289` | **reads `Plan.rooms`, `Plan.COLS/ROWS` and `Plan.HERE` directly**: it cannot draw any other home. Rooms are `PlanRect`s (single rectangles), so an L-shaped room cannot be drawn |
| `TerminalView` / `NoPlan` | `ui/.../ScreensWork.kt:790-823` | plan or dash; `StaticNoise` overlays the plan (`:804`) |
| `FloorStrip` | `ScreensWork.kt:830-840` | hard-coded `GROUND` / `UPPER` |
| `Ghost3Screen` | `ui/.../ScreensOut.kt:228-248` | meters, Egress widget, plan or dash |
| `Flow` on `TerminalAnswered` | `ui/.../Flow.kt:2541-2542` | pushes `TermLive` or `TermBusy` |
| guards | `ui/src/desktopTest/.../HousedContentTest.kt:244-265, 315-357` | the bungalow's words fail any housed round screen. **The setup editor is exempt** (*"starts from the bungalow on purpose"*, `:246-247`; no D-entry rules this) |

### 2.6 The editor's starting plan

- `FlowModel.editor = HomeEditorModel.bungalow()` (`ui/.../Flow.kt:998`), and the app root
  (`app/src/cheats/.../Cheats.kt:257`) passes no editor, **so a live phone's editor starts as the
  bungalow**. `LocalEditor` (`HomeEditor.kt:891-892`) and `Screen.kt:33` default to it as well.
- Both live entry points replace it before the editor shows: MAP A NEW HOME → `startNewHome`
  (`HomeEditor.kt:563-574`: `HousePlan.EMPTY.withFloor(GROUND)`, empty map) and EDIT THE PLAN /
  RENAME → `editor.load(saved)` (`Flow.kt:1853-1861`, `ScreensHost.kt:126-128, 1124-1131`). So
  today the bungalow is latent: a default reachable by any future route into `ScreenId.Editor`
  that skips those two calls.
- `bungalow()` (`HomeEditor.kt:674-760`) builds its ground floor from `Plan.rooms`, so the editor
  fixture and the in-round fixture are one source.

---

## 3. Build plan

Eight units, in dependency order. **O1, O2, O4 and O5 do not wait on Q1–Q3.** O3 and every unit
that draws a plan wait on **Q1**. O8 needs phones.

Rules for every unit, from the overnight run's verification rules:

- Read the test XML, never an exit code.
- Prove every guard by breaking it (verify by injection).
- Render every changed screen to PNG (the UI render harness). Compiling proves nothing.
- Run `./gradlew check --continue`, which covers lints including `vocabularyLint`.
- Check every new wire field for a **role-dependent size**: a Boolean or an optional string
  serialises to different lengths (D-249).
- Bump the recording format for every new event or field (it is at 30).

**Common symmetry rule for every new `RoundMessage` here** (D-216, D-249, D-258, D-263): *nobody
watching the Wi-Fi may tell roles apart by frame count, size or timing.*

- Each new message has **one fixed width for a given home**: counts as two hex digits, times as
  fixed hex, rooms by index, never a JSON Int or an optional field.
- Each one either goes to every seat identically, or its audience is chosen by a **public** fact:
  round-state (D-068), or *this phone is the reader*. Never by role.
- Error rolls are keyed on the observation's id under a new HMAC `SeedPurpose`, **never** on a seat
  or a role, so a role-swapped round produces a byte-identical occupancy transcript.

### O1. The editor starts from an empty home (ui only; decision-free)

**Changes.**
- `FlowModel.editor` defaults to a new `HomeEditorModel.empty()`, the same state as `startNewHome`:
  one storey, no rooms, no cards. `startNewHome` calls it.
- `LocalEditor` and `Screen.kt:33` default to `empty()` as well. The render harness and tests that
  want the bungalow ask for `bungalow()` by name.
- Drop the editor exemption from `HousedContentTest` (`:246-247`). Instead, add a test that a fresh
  `FlowModel`'s editor holds no rooms.

**Why decision-free.** gdd:822 and EP:152 (4.3): *"start with Floor 0 (renameable), add more as you
go. Nothing fixed"*. D-125: *"The app never has an opinion about what a home is."* No D-entry rules
the bungalow in; the only claim it is "on purpose" is a test comment.

**Leak/parity.** None; this is host setup in the light.

**Tests.**
- `HomeEditorTest`: `FlowModel().editor.plan.rooms` is empty and has exactly one floor.
- Render `Editor` from a fresh `FlowModel` to PNG: an empty grid.
- Injection: put `bungalow()` back as the default and confirm the new test fails.

**Small open point, not a question:** the storey is named `GROUND` (`HomeEditor.kt:763`), while the
GDD says *Floor 0*. Both are renameable, so I recommend keeping `GROUND` (a host's word) and
sweeping gdd:822/895.

### O2. The plan as a recorded input the rules hold and derive nothing from (model, core, harness)

**Changes.**
- A new `@ClientFacing @Serializable` **`HomePlanView`**, constructed only in `model` from a
  `HousePlan` (an `internal` constructor plus `HousePlan.toView()`, the `ObservationView`
  discipline):
  - storeys in plan order, each with a name and a bounding box;
  - one row-major cell string per storey (room index in base-36, `.` for unpainted);
  - rooms in plan order as (name, `Room`/`Stairs`).
  - **No cards, no terminal, no meeting card, no stations, no active markers, no seats.** The type
    *is* the field list (rule 3): a card cannot be expressed in it.
  - Cells rather than strokes, as 4cefbf5 chose: strokes are the host's gestures.
  - Bound each storey to the editor's viewport (10×12 today, since panning is unbuilt). A plan
    outside it is refused at REVIEW (D-126), never at arming.
- `Event.RoundArmed.plan: HomePlanView` (the parser defaults nothing, per the `markers` KDoc) and
  `GameState.plan`. The KDoc says what `destinations` says: a lookup table that **nothing in the
  rules decides from** except O6's adjacency for plausible error (gdd:491, gdd:826).
- `GameState.terminalRoom: String?` taken off the arming event, so that the reader's placement
  resolves to a room. O4 needs it. It never reaches a client except as O6's `here`, to the reader.
- `RoundAuthority.armWith` passes `hosted.plan.toView()` and the terminal's room.
- Recording **31**; `RecordingText` and `Transcript` rows.

**Schema.** No effect yet, so it ships to nobody.

**Leak/parity.** The plan reaches no phone in this unit. Role never touches it.

**Tests.**
- Round-trip through `RecordingText` and `ReplayTest`. The parser throws on a missing plan.
- `HomePlanViewTest`: an L-shaped room survives as one room. A stairs room stays stairs. The view
  has no member that can hold a `MarkerId` (assert on the constructor's parameter list).
- `DifferentialTest` stays green (the arming transcript is identical on a role swap).
- Injection: add a terminal field and confirm the field-list test fails.

### O3. The plan to phones, and drawing any home (model, core, app, ui) — **waits on Q1**

**Changes, assuming Q1 = (a), every seat at arming:**
- `Effect.PlanIssued(seat)`, emitted **once per seat in seat order** in the arming burst, after
  `RosterIssued`. Addressed to its own seat (`RosterIssued`'s arrangement, `EmitSchema.kt:~770`).
- `RoundMessage.PlanIssued(plan: HomePlanView)`.
- **Schema row `PLAN_ISSUED to (LIVING + OUTSIDE)`**, written out as a four-class set (the
  `ABILITY_FIRED` habit). Fail-closed: no row means a frame and a dash, as today.
- Reconnect: the plan is **re-sent on reassert**. T2·6 recorded that the old plan was not, and V2's
  512-push cap would drop a push from arming. Re-send it from `GameState.plan` when a seat resumes,
  or keep it outside the cap.
- UI:
  - `PanelState.plan: HomePlanView?`, written by `Flow` on `PlanIssued`.
  - `KitLivePlan(plan, counts, here)` draws from the view: per-cell edges where the neighbour
    differs, which handles L-shapes; labels at each room's first cell (the editor's rule,
    `PlanViews.kt:344-360`); stairs hatched.
  - `FloorStrip(plan)` lists the plan's storeys (or is replaced, per Q3).
  - `object Plan` becomes a harness-only `HomePlanView` built from the bungalow, so the harness and
    the housed path use one drawing code.
  - `NoPlan` stays the fail-closed fallback for `plan == null`.

**Under Q1 = (b) or (c)** the effect moves to `TerminalAnswered(reading = true)` and/or the start of
ghost phase 3. The audience is then the reader (either role, a public fact) and/or phase-3 ghosts
(round-state), and the reconnect re-send keys on the same facts.

**Leak/parity.**
- The content is a pure function of the home. It is **byte-identical on every seat**, so the
  arming burst gains exactly one frame of one length per seat (bar the push sequence's digits,
  D-216's raised item). There is no role branch.
- It discloses room shapes, names and storeys, which every player sees by walking the house. It
  discloses **no** card→room fact (`Destination.kt:27-29`'s actual concern) and no station (D-122).
- Insider-only rooms are never app data (gdd:845), so they cannot leak.
- **Plain `ws`:** a sniffer reads the plan, which is the same disclosure. R7 is unchanged.

**Frame size.** About 120 characters per storey of cells, plus names. Say ≤ 1 KB for a two-storey
home. Once per seat per round, plus re-sends.

**Tests (real path).**
- `EmitSchemaTest`: the row is exactly those four classes.
- `PlanFrameSizeTest` (app, `TouchFrameSizeTest`'s method): a real `TransportHost`, five real
  clients over loopback, the real arming. Every seat's `PlanIssued` frame is the same length and the
  same bytes, Insider seats 1 and 4 against a role-swapped run.
- A reconnect test: a phone relaunched after more than 511 pushes still draws the plan.
- `HousedContentWiringTest`: a housed Terminal and couch draw the **hosted** home's room names, and
  none of the bungalow's.
- Render PNGs of an L-shaped two-storey home on `TermLive` and `Ghost3`.
- Injection: delete the schema row and confirm the frame and dash appear and the wiring test fails.
  Put `Plan.rooms` back into `KitLivePlan` and confirm the bungalow sweep fails.

### O4. Placements: D-136's spectator map, house-side only (core, app edge, harness; decision-free with provisional tunables)

**Changes.**
- A new pure core function, `resolvePlacements(upload, seat, state)`, called from
  `RoundAuthority.sight` beside `resolveContacts` (one unpack). It produces events only when
  **the uploading seat is a sensor**:
  - a living seat with presence open (performing: gdd:777's 5 s sweep, D-136);
  - or a seat within `SCAN_SWEEP_MILLIS` of a scan (gdd:776's sweep on scan; D-212 says
    continuous scanning feeds "the same sightings and more");
  - plus the Terminal reader, if the GDD reading in 1.7 #6 stands.
- For each heard token that resolves to a **living** seat (D-155) at RSSI ≥ `ROOM_RSSI_FLOOR`
  (provisional), it mints **`Event.Sensed(at, sensor, room, heard: List<Seat>)`**. The room is the
  sensor's Known card resolved through `destinations` or `terminalRoom`. Sensed is a recorded fact,
  like `ContactMade` (D-213).
- `GameState.sightings: List<Placement.Inferred>` with a tick, one per seat, newest wins. This is
  a **different collection** from `presence`, as `Placement.kt:51-57` demands.
- `placementOf(seat, at)` is rewritten as D-136 reads:
  - a **Known placement whose window is open** (or is within `KNOWN_HOLD` of a check-in scan) wins
    outright, and no sighting relocates it;
  - otherwise the newest of {closed Known, Inferred} wins, until `PLACEMENT_EXPIRY`;
  - after that the seat is unplaced (a coverage hole).
  - **Out seats are never placed**, as a seat-state filter at the top (D-155).
- Sensing is ignored while a meeting runs (D-217 R5's shape: nobody is out on the floor, and
  presence is frozen). Every sighting placement is cleared when a meeting ends (gdd:1050, gdd:1070:
  *the map wiped*).
- An out phone is never a sensor: it drains and drops (R8), and the house filters it by state as
  the second denial (gdd:787).
- Recording **32**.

**Schema.** `Sensed` produces no effect. Nothing reaches a phone in this unit.

**Leak/parity.**
- Nothing crosses.
- Sensing reads no role. An Insider's fake performance opens presence exactly as a real one does
  (rule 8; `Rules.kt:577`), so Insiders are sensors exactly as often.
- **Unreliable reads are kept on purpose:** bleed into an adjacent room at the threshold, holes
  where nobody performs, staleness by expiry. Do not tune them away (D-136; the
  `unreliable-reads-are-the-point` memory).

**Tests.**
- `PlacementTest` (core):
  - a performer at the kitchen card hears seat 4 at −60, so seat 4 is Inferred in the kitchen;
  - a sighting never moves a seat whose window is open (D-136, both firing orders);
  - a Revoked or Restrained seat is never placed, even when heard at −30;
  - an out seat's upload places nobody;
  - nothing is placed at a meeting, and everything is cleared at meeting end;
  - expiry works.
- `ContactResolverDifferentialTest`'s method: a role swap gives identical `Sensed` streams.
- App: an upload through `LobbyHouse.attend` to `RoundAuthority.sighted` lands a `Sensed` in the
  recording, and contacts are unchanged (`TouchFrameSizeTest` stays green).
- Injection: write a sighting into `presence` instead and confirm the D-136 test fails.

**Tunables, all provisional and owned by playtest:**

| name | start value |
|---|---|
| `ROOM_RSSI_FLOOR` | −75 dBm |
| `SCAN_SWEEP_MILLIS` | 3 s |
| `KNOWN_HOLD` for a windowless check-in | 10 s |
| `PLACEMENT_EXPIRY` | 45 s |

### O5. The couch's true occupancy (core, model, app, ui) — the plan drawing waits on Q1 (and Q3 for storeys)

**Changes.**
- At every reduction that changes `placementOf` for any room (`Sensed`, scan, performance end,
  Revoke, Restrain, expiry ticks the authority drives), core emits **`Effect.TrueCounts(seat)`**
  once per **phase-3 ghost**. A phase-3 ghost is a seat that is out, not in `newlyRevoked`, with no
  meeting running.
- The content is per-room counts in `GameState.plan` order. Stairs are drawn and never counted
  (gdd:485). Out seats are excluded (D-155).
- Throttle to at most one emission per beat (1 s), so the frame rate is bounded and every ghost is
  sent the same frames.
- `RoundMessage.TrueCounts(countsHex)`: two hex digits per room, a fixed width for the home.
- **A new type, not `ObservationView`:** `TrueOccupancyView` (`@ClientFacing`, counts only, never a
  seat) belongs to this message alone. Being a distinct kind is what lets the schema keep it off
  living phones.
- **Schema row `TRUE_COUNTS to OUTSIDE`**, two classes written out.
- Addressing (`EmitSchema.audienceOf`) excludes `newlyRevoked` and emits nothing during a meeting.
  That is a second, independent denial for EP:339 (*never before phase 3*).
- Static does **not** touch it: there is no jam row for the out, and the ghost map stays clean
  (D-186).
- UI: `PanelVals.trueCounts` comes from `PanelState.trueCounts`. Counts in full ink, no bands, no
  outline (1.7 #8). `Ghost3Screen` draws `KitLivePlan(plan, counts, here = null)`.

**Leak/parity (rule 3).**
- `TrueCounts` reaches only out seats. No living class appears in its row, and the living's
  Terminal type (O6) cannot hold its numbers.
- The count drop a ghost sees at a Revoke is D-145's accepted correlation: the out can act on none
  of it.
- An Insider ghost and a Resident ghost are sent identical bytes at identical moments.
- Living versus out frame counts differ, but round-state is public (D-068).
- **On plain `ws` a living player with a sniffer could read these frames.** That is R7's, already
  true of `SystemIntegrityLive`, and must be stated in the decision entry.

**Tests (real path).**
- `GhostOccupancyWiringTest` (app; five phones over loopback, the real rules, `SimulatedAir`
  positions):
  - one seat is Revoked; **before the meeting closes it receives no `TrueCounts`** (sniff every
    frame on its socket);
  - after the meeting it receives counts matching `SimulatedAir` (a performer in the kitchen with
    two others heard at −60 gives 3; the Revoked seat is never counted);
  - **no living socket ever carries the kind**;
  - an Insider ghost and a Resident ghost get equal-length, equal-content frames.
- `EmitSchemaTest`: the row is exactly `OUTSIDE`.
- `StaticTest`: a ghost's counts are unchanged across a jam.
- Render `Ghost3` with counts on a two-storey L-shaped home.
- Injection: widen the row to `LIVING` and confirm the wiring test fails; drop the `newlyRevoked`
  filter and confirm the "before" assertion fails.

### O6. The Terminal's live view (model, core, app, ui) — the drawing waits on Q1

**Changes.**
- **`Observation` corrected** (1.8 #5 and #6):
  - `room: String` (a plan room) replaces `MarkerId`;
  - plus `seenAtMillis` (host clock, a recorded input on `Sensed`, after C2's `anchorMillis`
    precedent), `trueCount`, `reportedCount` and `ageJitterMillis`, both **rolled once at capture**
    under a new `SeedPurpose.Occupancy` keyed on the observation id (gdd:484, gdd:492; game-
    architecture.md:820-833).
- `GameState.observations`: the latest per room, plus the history O7 needs, cleared at meeting end
  (gdd:1050).
- Capture: every `Sensed` and every check-in captures one `Observation` for the sensor's room, with
  `trueCount` = the placed living seats there.
- Error, from gdd:487-493 and F-007:
  - a false negative biased toward under-counting occupied rooms;
  - a false positive **only into an adjacent room** of the same storey (`Floor.neighboursOf` over
    `GameState.plan`).
  - Rates are provisional constants. The roll reads the observation id only.
- F-007's **hold**: a seat lost to detection stays in its room's count for `HOLD_MILLIS`. This is
  O4's expiry seen from the Terminal.
- **`ObservationView`** becomes `(roomIndex, reportedCount, seenAtJittered)`. Here `seenAtJittered =
  seenAtMillis + ageJitterMillis`, so the phone **never learns an exact scan time**. The phone
  buckets `houseNow() − seenAtJittered` into fresh, stale or cold (D-209 inks), which is
  architecture.md:833's `render(o, now)`.
  - A room with no observation since the shift began is drawn `0` in the cold ink. "Zero and
    no-reading are the same fact" (`PanelModel.kt:2426-2433`, gdd:524).
- `Effect.TerminalReading(seat)` goes to **the reader only** (`readerHolding()`):
  - a full snapshot when `TerminalAnswered(reading = true)` is emitted;
  - then on every new observation, throttled to one a beat, until the lock frees (D-203's four
    ways).
- `RoundMessage.TerminalReading(viewsHex, here)`: per room, 2 hex for the count and 12 hex for the
  jittered time, plus `here` (the terminal room's index, so the outline needs no plan flag that
  would tell every phone where the station is). Fixed width for the home.
- **Schema row `TERMINAL_READING to LIVING`** (both living classes, never narrowed: the station is
  open to either role, `EmitSchema.kt:384-388`). Addressed to the reader alone.
- Static: the phone already overlays `StaticNoise` on the plan while the house says a jam is on
  (`ScreensWork.kt:804`). Readings keep flowing under it (gdd:495, *the real counts faintly
  beneath*).
- UI: `PanelVals.terminalCounts` comes from `PanelState.reading`, with ink per band. `TermLive`
  draws `KitLivePlan(plan, counts, here)`.

**Leak/parity (the core of this unit).**
- **A living phone never gets ground truth:** the only thing a living phone is sent is
  `ObservationView`, whose `internal` constructor is fed by `toView()` alone and has no field for
  `trueCount`.
- The reader's frames are a function of sensing and the injected error. Neither reads a role.
- Error rates are identical by construction (a single stream keyed on the observation id). The
  `DifferentialTest` role swap must give identical `TerminalReading` bytes.
- **F-007's re-verification is required before this ships (EP:256).** Run the hunt round with a
  Revoke at a sensor's room and without one, 200 seeds each, and compare the distributions of 2→1
  decrements a reader would see within 10 s. Record the numbers in the D-entry.

**Frame count and size.**
- The reader gets frames other living phones do not. That is reader identity, which is
  role-independent and physically visible (gdd:526, *"go look"*). **Residual, accepted, stated in
  the D-entry.** Cover would be a blank to every living phone each beat, as D-263 does.
- The size is fixed for the home. No Boolean, no optional field.

**Tests (real path).**
- `ObservationTest` (core):
  - the same observation renders identically twice;
  - a phantom never lands in a non-adjacent room or a stairs room;
  - the injected-rate distribution is equal across a role swap, over many seeds;
  - the view has no member holding a true count (constructor parameter list).
- `TerminalReadingWiringTest` (app; five phones over loopback):
  - the reader gets a snapshot on scanning T;
  - a second scanner gets `TermBusy` and **no reading frames**;
  - every non-reader living socket gets no reading frames;
  - reading frames stop at each of D-203's four frees;
  - ghosts get none;
  - every reading frame is the same length.
- `TerminalReadingFrameSizeTest`: an Insider reader and a Resident reader with the same sensing give
  identical bytes.
- `StaticRenderTest`: noise over real counts.
- Render `TermLive` in all three bands.
- Injection: hand the reader `trueCount` through a test-only path and confirm the error-presence
  test fails; drop the reader filter and confirm the non-reader assertion fails.

### O7. Timelapse (model, core, app, ui) — the build-order cut #2 candidate (architecture.md:1037)

**Changes.**
- History: `GameState.observations` in `[floor, now]`, where the floor is the start of the current
  shift (gdd:509). Plus `GameState.jams: List<window>` since the floor, because `staticUntil` keeps
  only the current jam.
- Intents `StartTimelapse` and `StopTimelapse` ride the D-263 request slot. The rules hold a
  **station** cooldown (1.7 #7):
  - cooldown == history duration;
  - it starts at playback end;
  - an early cancel shortens it linearly by the unwatched fraction, measured on the house's clock
    at arrival (about 1 s of slot latency, D-263; accepted);
  - oldest→newest; plays once (gdd:501-515).
  - A refusal while cooling down answers with the same message shape (rule 1).
- `Effect.TimelapsePlayback(seat = reader)`: **20 fixed frames**, one per playback second, each the
  per-room views at that history moment, banded as at that moment. A frame inside a jam window
  carries a jam digit. Frames before the floor are blank, so the message is **one width whatever
  the history length**.
- One `RoundMessage.TimelapsePlayback(framesHex)`. Schema row **`LIVING`**, addressed to the reader.
- UI: `TimelapseScreen` plays the 20 frames against `houseNow()` from the push's stamp (D-239's
  house-clock precedent). Its bar and `-m:ss` come from that; noise is drawn on jam frames.
  - The numeral animation follows Q4.

**Leak/parity.** As O6: the same view type, the reader only, a fixed width. Jam frames say *when*,
never *who* (gdd:517).

**Tests.**
- `TimelapseRulesTest`:
  - the gdd:513 blind spot (a trigger at T sees `[T−120, T]`, the next allowed at T+140 sees
    `[T+20, T+140]`);
  - the cooldown scales to a short post-meeting history;
  - a cancel at 10 s cuts it by half;
  - the floor resets at meeting end;
  - a 10 s jam gives about 1.7 s of jam frames.
- Wiring over loopback: reader only, one frame width.
- PNG renders at the start, middle and a jam frame.
- Injection: start the cooldown at the trigger instead and confirm the blind-spot test fails.

### O8. Phones (owner; physical devices — the Simulator has no BLE)

- RSSI calibration for `ROOM_RSSI_FLOOR`: two phones, same room versus through a wall, several grips
  and models (unit 5's rig).
- A three-phone walk: a performer in the kitchen, one person beside them, one in the next room.
  Check the couch and the Terminal against what was physically true. Bleed should be *plausible*,
  not absent.
- An allocation check, since sensing adds work on the BLE and effect threads (rule 7, 0.5 MB/s).
- A radio sniff (0.6c) that no new frame differs by role.

---

## 4. The editor's starting plan

**Empty: one storey, no rooms, no cards.** That is what MAP A NEW HOME already does
(`HomeEditor.kt:563-574`).

The docs say so:
- gdd:822 and EP:152: *"start with Floor 0 (renameable), add more as you go. Nothing fixed"*;
- gdd:895: *"The host paints **Floor 0**"*;
- D-125: *"The app never has an opinion about what a home is."*

No document asks for a template. A shipped bungalow is a fixture with the same failure D-257
removed from the round: rooms the host does not have.

The only change is the default in `FlowModel`, `LocalEditor` and `Screen` (unit O1). The bungalow
stays a named harness fixture (`bungalow()`, `SavedHomesModel.sample()`), which tests and renders
ask for explicitly.

---

## 5. Owner decisions needed

**Q1. May the home's plan reach phones, and which?** D-186 and EP:292 say no client. M11 (gdd:482)
and M12 (gdd:546) need a drawn plan on the reader's phone and every ghost's.
- **(a) Every seat at arming, identical.**
- (b) The reader on claiming the station, plus ghosts at phase 3.
- (c) Ghosts only, with the Terminal drawing a room list.

**Recommend (a).** D-186's harm was an *Insider-only* row (role-asymmetric) carrying the plan for a
picker that no longer exists. A plan with **no cards, no terminal and no stations**, sent byte-for-
byte to every seat, carries only what every player sees by walking the house, and it adds no role
signal. (b) also crosses to living phones, so D-186 is amended either way, and (b) adds a large
reader-only frame plus reconnect cases. (c) keeps D-186 literally and guts M11's *"your own room
outlined"*.

This needs a D-entry that amends D-186's sentence and EP:292. `Destination.kt:27-29` stands, since
card→room still never crosses.

**Q2. Confirm what "true occupancy" means for ghosts.** gdd:546 says *true*. D-136 says the
spectator map is inference with bleed.
- **Recommend:** ghosts see the house's own placements (D-136: sightings while performing, bleed,
  holes, expiry), with **no injected error and no staleness bands**, live. The house cannot know
  more than that (gdd:767-769), and D-136 rejects a perfect couch map.

**Q3. Storeys on the Terminal and the couch.** The ghost sees *every storey*. The Terminal's
coverage is unstated, and the strip is *"a reading, not a control"*.
- **Recommend:** both draw **every storey, stacked and scaled to fit the frame**, with no tabs or
  swipe (D-175's *"nothing … quicker than reading"* spirit). `FloorStrip` is retired, or becomes a
  label per stacked storey.
- The alternative, the station's storey only on the Terminal, makes a two-storey home's Terminal
  blind upstairs, which gdd:1181 (*"sees where crowds are"*) does not suggest.

**Q4. F-008's numeral count-up/down** (gdd:996, EP:261) against D-202/rule 5 (*no local
animation*).
- **Recommend:** no animation; the numeral changes in place. D-202 is later and is the project-wide
  rule, and a tween is light nobody authored.

**Q5. Confirm the provisional values and whose they are.**
- `ROOM_RSSI_FLOOR` −75 dBm;
- placement expiry 45 s;
- check-in hold 10 s;
- F-007 hold 5 s;
- band edges 15 s / 45 s with ±5 s jitter;
- false-negative 15% (biased to occupied rooms) and false-positive 5% (adjacent only);
- sweep-on-scan 3 s.

All are `[ASSUMPTION]` in the GDD (A-7, A-8). **Recommend:** build them as named provisional
constants that playtest owns, and record them in the O4 and O6 D-entries. Only `ROOM_RSSI_FLOOR`
needs a phone before it means anything (O8).

*Read as specified, not asked:*
- the Terminal reader is a sensor while reading (gdd:476 + :777);
- the timelapse cooldown belongs to the station (gdd:499);
- ghosts get no outline;
- the couch blind zone is gone (D-155 over gdd:789/839, EP 4.10/8.15, which need a sweep);
- a room with no data reads `0` cold.

If the owner disagrees with any of these, each is a one-line change in its unit.
