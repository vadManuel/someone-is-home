# Overnight decisions, 2026-11-19, for the owner's review

The owner went to sleep at about 02:45 on 2026-11-19 and said: *"make rational decisions for me throughout the night. i'll then address them in the morning."*

Every call made without the owner is listed below: what was decided, why, and how to undo it. Each one is also marked **provisional (overnight)** in its decision-log entry. Nothing below changes a ruling the owner already made.

| # | Decision | Why | Where | To undo |
|---|---|---|---|---|
| 1 | **Any screen change clears the Egress alert, a scan's answer included.** Someone who reads a card under the alert lands on the briefing at full light (D-299). | It keeps D-297's rule. One rule for every screen. | D-299 | Keep the alert across a scan's answer: one line in `FlowModel`. |
| 2 | **On the camera, the alert's own 10 s is never reached.** The scan window (D-159's safety window) opened first and ends first, taking the alert and the dim with it. | The scan's 10 s is a safety device; stretching it would change a timing value you set. | D-299 | Let the alert outlive the scan window. |
| 3 | **The refusal screens (NOTHING FOR YOU HERE and its twins) still show no alert and no dim during an Egress.** Left as they are and pinned by a test. | Your ruling named the briefing and the camera only. These screens last a moment. | D-299, raised | Add them to `Flow.keepsItsScreenUnderTheEgress`. |
| 4 | **D-299 was pushed without its own full check on main.** Its full check ran on `7e91206`. The only code on main since then is the three-phone test tool's files, which share nothing with D-299. | A 26-minute run for disjoint files. The next full check (with D-300) covers the combination. | `git log` | No action needed unless that check fails. |
| 5 | **D-300 (the UWB wire and the house's pairing) was sent back before merging.** It allocated about 1 MB/s on an 18-seat host, twice the app-wide budget of 0.5 MB/s that rule 7 sets in project-context.md. The cost is paid on every upload now, before any UWB radio exists. The fix keeps the wire as it is and cuts the copying. Cutting token slots from 6 to 3 is the fallback only. | A known breach of a measured project rule does not go to main. | D-300, item 10 | None: rule 7 is not a judgment call. If the fallback (3 token slots) is used, it is marked provisional. |
| 6 | **Three token slots each way, not six** (D-300). A phone offers at most 3 idle UWB sessions a second, and the house starts it on at most 3 new pairs a second. The 6 distance slots and the 6 plan steps stay. | Six slots cost twice the memory budget (decision 5). Each phone starts at 3 sessions, so 3 slots is all it can use at first. | `RangingWire.TOKEN_SLOTS` | Set it back to 6 and raise the allocation ceilings in `UploadAllocationTest`. |
| 7 | **Revoked phones keep measuring and reporting real distances** (D-300). The house pairs them like anyone else. | A phone that goes quiet when it is Revoked would give it away. The rules can still ignore its distances. | `Ranging.kt` | Make the house skip Revoked seats when pairing. That would be a tell. |
| 8 | **The ranging report and plan are encrypted per phone** (D-300), not only padded. | A UWB token is a recognisable file format, and a plain handle would show anyone on the Wi-Fi who is measuring whom. | `RangingWire.kt` | None advised. |
| 9 | **The pairing numbers** (D-300): Bluetooth readings count for 3 s, a pair is kept while both phones stay in each other's top capacity + 2, and a pair nobody lists is dropped after 3 s. | Starting values to tune on phones. | `Ranging.kt` | Change the constants. |
| 10 | **The pairing is not written into recordings; the format stays at 42** (D-300). | It creates no game event, the same as the contact resolver. | D-300 | Record it later if replays need it. |
| 11 | **The frames that carry UWB still cost about 130 KB/s on an 18-phone host** (likely about 250 KB/s on a phone), on top of what came before. That is within the 0.5 MB/s budget, but it uses a real share of it. The next lever, one token slot each way, would mean a phone starts at most one new pair a second. Not built. | It changes how fast pairs form. That is yours to weigh after the phone test. | D-300, item 10 | Nothing to undo. |
| 12 | **The game rules pick Bluetooth or UWB once per round, at arming** (D-301). They use UWB only when every phone says it can measure, and Bluetooth otherwise. The choice goes into the recording (format 43) and is never told to any phone. | Your phones have no UWB code yet, so tomorrow's retest runs on Bluetooth exactly as before. One phone that can't measure would otherwise blind the whole round. | `contactSourceFor` in `core/Arming.kt` | Once the iOS radio works, delete the Bluetooth branch. |
| 13 | **Each phone tells the house its UWB capacity in the lobby** (D-301), in a new lobby message. | Phones upload nothing before arming, and the choice is made at arming. | `LobbyBody.Ranging` | None. |
| 14 | **Report timing** (D-301): the 2 s hold allows half a second of slack. It breaks if the reader's phone misses an upload (1.5 s). No hold runs during a meeting or on a seat that has already been reported. If the held seat walks off while another Revoked seat is in reach, the hold restarts on that seat. | The phone uploads once a second, so 2 s can only be measured to the beat. | `Contacts.kt` | Change the constants. |
| 15 | **A Revoked phone's own distances are not read for anything** (D-301). It still measures and uploads. | A Revoked phone cannot touch, report or place anyone. | `Contacts.kt` | None advised. |
| 16 | **A phone at a card places someone in its room within 1.5 m** (D-301). | The plan's starting value; tune it on phones. | `SensingGate.ROOM_REACH_CM` | Change the constant. |
| 17 | **The circle on the reporting phone was not drawn.** The house already tells the phone when to fill it and when to cancel, but the ui has no circular progress element. Where it sits on screen, its ink and light, and whether it fills smoothly or steps once a second are your calls. | Light levels and new screen elements are yours. | D-301 | For you to design in the morning. |
| 18 | **Open problem in D-300's pairing, not fixed:** in a small round, a pair the house never picked may never be picked. A kept pair drops only when it falls past capacity + 2 in a phone's ranking, which never happens with 5 phones at capacity 3. An Insider then can never Revoke that peer, however long they hold. The benches use capacity 4 to avoid it. | Found by D-301's tests. Bluetooth rounds are unaffected. It must be fixed before UWB rounds are played. | `Ranging.kt` | Fixed in D-302 (decision 19). |
| 19 | **How the house fixes a missing pair** (D-302). Two phones that each rank the other first can push out a full phone's weakest pair. That pair must sit past the phone's entry rank and read at least 10 dB weaker in Bluetooth signal. In tests, a missing pair now forms within 3 seconds. | Without it, an Insider holding their phone against someone could never Revoke them. The 10 dB margin stops two pairs swapping back and forth (the signal at one spot varies by about 4 dB). | `DISPLACE_MARGIN_DB` in `Ranging.kt` | Change the margin. |
| 20 | **A spare UWB session the phone never started can be offered to a second peer** (D-302), one after the other, never at once. | Without this, a phone could run out of spares for the whole round. Whether iOS accepts it is checked when the iOS radio is built. If it doesn't, the phone retires that session and offers a fresh one. | `Ranging.kt` | Make the phone retire the session instead. |
| 21 | **Pairs survive the host's phone dropping off the air** (D-302). The pairing stops counting time while the house hears nothing, so after a gap over 2 s it resumes from where it was. | Phones keep their sessions open, so dropping every pair only blinded everyone for a few seconds. | `SILENCE_TICKS` | None advised. |
| 22 | **The test benches now measure 3 phones at once per phone, the real starting number** (D-302). This withdraws the 4 that D-301 used. | Tests should match the phones. | `SimulatedAir.BENCH_CAPACITY` | None. |
| 23 | **Still open:** if 4 or more phones are within about half a metre of each other, a touch can still go unmeasured. Each phone's pairs are all within 10 dB of each other, so nothing can push one out. | Accepted as a crowd missing a flyby, which U3 already accepts. Tune the margin on phones. | D-302 | Lower the margin. |
| 24 | **The iPhone UWB code is off by default** (D-303). Start the app with `SOMEONES_HOME_UWB=1 ./run-app.sh device playtest` to turn it on. With it off, a phone tells the house it can measure 0 peers, so every round plays on Bluetooth. | So that today's Bluetooth retests run exactly as before. | `Cheats.kt`, `run-app.sh` | Make it on by default once a UWB round has worked on phones. |
| 25 | **NEARBY DEVICES is a new row in the setup permissions, under BLUETOOTH** (D-303), drawn the same as every other row. To raise iOS's question, it briefly starts a UWB session. With no answer within 2 s, the row shows ASKED, which does not block. Every phone stops at PERMISSIONS on its first launch of this build until the row is answered. | You chose the label. D-161 says every permission is asked at setup, with the lights on. | D-303 | None. |
| 26 | **A hidden description text for that row**, "THE HOUSE MEASURES THE DISTANCE BETWEEN PHONES". No screen draws it, but the code needs one. | It paraphrases your approved iOS prompt text. | `DevicePermission` | Replace it with your words. |
| 27 | **The NameDrop step is gone** (D-303), as you ruled. CONTINUE goes straight to JOIN A HOME, and JOIN A HOME's back arrow to PERMISSIONS stays. | Your ruling. The back arrow is also the way back to the permissions. | D-303 | None. |
| 28 | **How the radio handles failures** (D-303). If you refuse UWB, it is your fault, not the phone's. The session limit and an unsupported phone are the phone's fault. An idle spare session that iOS closes is replaced quietly. A refused or unsupported phone measures 0 peers until the next round. A slot that fails 3 times in a row stays empty for the round. | Uses the existing fault report, which every phone sends at the same size. | `RangingPool`, `NearbyRadio.ios.kt` | Change the mapping. |
| 29 | **A spare session offered twice is used, not thrown away** (D-303, settles decision 20). The house only offers it again after the phone reports it still idle, so it has never run. | Simpler, with nothing wasted. | `RangingPool` | Retire it and open a fresh one. |
| 30 | **Found, not fixed: the PERMISSIONS screen runs off the bottom in its tallest state.** With a row refused, others still owed, and the Local Network caption showing, OPEN SETTINGS and REQUEST WHAT IS MISSING fall below the edge. Refusing the camera partway through the walk gets you there. Main already had this before tonight. | It needs a layout call: scroll, or tighter rows. | Perms screen | Your call. |
| 31 | **The GDD was brought in line with the UWB work** (D-304). Overnight calls are marked provisional in it. The GDD's "no revoke ping" rule and its round trace now record your D-290 ruling: both phones buzz Long. That buzz is touch only, so no bystander sees or hears it. | They contradicted your ruling. | `gdd.md` | None. |
| 32 | **End words removed from the GDD where they describe players, lamps or a Revoked phone.** "Nobody is eliminated", "never end", the lamp that "fails" at contact, "dark rectangle" and "Phone still defunct" now read revoked, dark or goes dark. **Left alone:** figures of speech such as "total silence", "mapping blind zone", "ghost idle time", "the app must be able to stop it" and "both prior attempts failed of calendar". | Your rule is about the game's story. A sweep of every figure of speech would reword about 25 design lines you never asked about. | `gdd.md` | Tell me if you want those swept too. |
| 33 | **GDD contradictions found but not fixed** (from D-304's pass). They need your call: | | | |
|  | 1. The report row says the gesture "cannot be used as a proximity radar" for Revoked players. Your filling circle tells the person holding it that a Revoked phone is within 30 cm, even through a wall. | | | |
|  | 2. "Sweep every 5 s" predates the once-a-second uploads. | | | |
|  | 3. The battery target counts only Bluetooth, not UWB sessions. | | | |
|  | 4. The GDD never mentions D-292's launch refusal of phones without UWB. | | | |

## Built and pushed overnight

- **D-299** (`6f10fcf` → main): the caught-scan briefing and the camera keep their screen under the Egress alert. It also fixed the briefing, which used to show no alert and no dim at all during an Egress, so it stayed fully lit while the house dimmed.
  - On the camera, the alert sits over the scan. The phone has no camera preview, and the card is read from the camera's own frames, so a read under the alert still works.
  - The dimmed field lowers the light the card is read by, from full to 0.32. Whether a card still reads at that light needs a phone.
- **Three-phone UWB test tool** (`48137f8`): ready for the owner's test.
  - iOS offers only two phone-to-phone session options: camera assistance and extended distance. There is no rate setting.

- **D-300** (`27614fd`, `ec1d695`): the UWB wire and the house's pairing, on the bench only. No UWB radio code yet, and no rule reads the distances, so nothing in play changes.
  - The full check on this exact tree passed: 3 098 tests, 0 failures.
  - The house's per-phone memory cost dropped from about 30 KB to 1.75 KB a second.

- **D-301** (`577fc01`): the game rules read UWB distance in a UWB round: a 10 cm touch, a 30 cm report held 2 s, and the map at 1.5 m. The simulated UWB radio now pauses on lock and recovers. Full check on this exact tree: 3 157 tests, 0 failures. Every new rule went red when broken on purpose.

- **D-302** (`27cfd60`): the pairing fixes above. The full check on this exact tree passed: 3 169 tests, 0 failures. I reran the core module's 411 JVM tests myself, and all passed.

- **D-303** (`1390625`): the iPhone UWB code (off by default), the NEARBY DEVICES row, and the NameDrop step removed. Full check on this exact tree: 3 194 tests, 0 failures. The Playtest build compiles for iPhone ("BUILD SUCCEEDED"). **Not seen on a phone:** whether sessions start, what iOS says at the session limit, and whether the row raises iOS's question. Part 3 of the phone-session checklist covers it.

- **D-304** (`5036e11`): the GDD pass.

(More as work lands.)

## Owner's review, 2026-11-19 morning

- **26, the hidden NEARBY DEVICES text:** keep it.
- **30, PERMISSIONS running off the bottom:** scroll the rows, with the buttons pinned at the bottom (D-305).
- **32, figures of speech:** sweep them all, from every file, and from history with a force push. Done in the current files, with one exception: the shell command that ends a process, where a script runs it, because it is the program's name. The lint's own list spells its word in two halves.
- **33.1, the circle as a radar:** accept it and reword. The GDD now says the circle confirms someone you are already beside, rather than finding anyone.
- **Still open:** 17 (how the report circle looks), and 33.2 to 33.4 (the 5 s sweep wording, the battery target, and the launch refusal in the GDD).

## Owner's review, 2026-11-20

- **17, the report circle:** a report runs only from page 1 or 2, unlocked (not on the lantern); Control Centre or the Egress alert over the page does not stop it. It takes over the screen with the circle alone, at the page's own light, filling smoothly on the phone's clock (D-307, building).
- **UWB runs only when the game needs it** (D-306, building): a pair is ranged only when Bluetooth says the phones are close and a Revoke, a report or the map could use it. Bluetooth runs the rest of the round.
- **33.2:** applied. The GDD's sweep is once a second while performing a Subroutine at a card.
- **33.4:** applied. The GDD's Platform section records D-292's launch refusal with the approved words.
- **33.3, battery:** waits for D-306, so the line describes UWB running only for close pairs that need it.
- **D-305's scroll hint:** none. Nothing that needs action is under the edge.

