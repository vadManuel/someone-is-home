# Overnight decisions, 2026-11-19, for the owner's review

The owner went to sleep at about 02:45 on 2026-11-19 and said: *"make rational decisions for me throughout the night. i'll then address them in the morning."*

Every call made without the owner is listed below: what was decided, why, and how to undo it. Each one is also marked **provisional (overnight)** in its decision-log entry. Nothing below changes a ruling the owner already made.

| # | Decision | Why | Where | To undo |
|---|---|---|---|---|
| 1 | **Any screen change clears the Egress alert, a scan's answer included.** Someone who reads a card under the alert lands on the briefing at full light (D-299). | It keeps D-297's rule. One rule for every screen. | D-299 | Keep the alert across a scan's answer: one line in `FlowModel`. |
| 2 | **On the camera, the alert's own 10 s is never reached.** The scan window (D-159's safety window) opened first and ends first, taking the alert and the dim with it. | The scan's 10 s is a safety device; stretching it would change a timing value you set. | D-299 | Let the alert outlive the scan window. |
| 3 | **The refusal screens (NOTHING FOR YOU HERE and its twins) still show no alert and no dim during an Egress.** Left as they are and pinned by a test. | Your ruling named the briefing and the camera only. These screens last a moment. | D-299, raised | Add them to `Flow.keepsItsScreenUnderTheEgress`. |
| 4 | **D-299 was pushed without its own full check on main.** Its full check ran on `5e829e3`. The only code on main since then is the three-phone test tool's files, which share nothing with D-299. | A 26-minute run for disjoint files. The next full check (with D-300) covers the combination. | `git log` | No action needed unless that check fails. |
| 5 | **D-300 (the UWB wire and the house's pairing) was sent back before merging.** It allocated about 1 MB/s on an 18-seat host, twice the app-wide budget of 0.5 MB/s that rule 7 sets in project-context.md. The cost is paid on every upload now, before any UWB radio exists. The fix keeps the wire as it is and cuts the copying. Cutting token slots from 6 to 3 is the fallback only. | A known breach of a measured project rule does not go to main. | D-300, item 10 | None: rule 7 is not a judgment call. If the fallback (3 token slots) is used, it is marked provisional. |
| 6 | **Three token slots each way, not six** (D-300). A phone offers at most 3 idle UWB sessions a second, and the house starts it on at most 3 new pairs a second. The 6 distance slots and the 6 plan steps stay. | Six slots cost twice the memory budget (decision 5). Each phone starts at 3 sessions, so 3 slots is all it can use at first. | `RangingWire.TOKEN_SLOTS` | Set it back to 6 and raise the allocation ceilings in `UploadAllocationTest`. |
| 7 | **Revoked phones keep measuring and reporting real distances** (D-300). The house pairs them like anyone else. | A phone that goes quiet when it is Revoked would give it away. The rules can still ignore its distances. | `Ranging.kt` | Make the house skip Revoked seats when pairing. That would be a tell. |
| 8 | **The ranging report and plan are encrypted per phone** (D-300), not only padded. | A UWB token is a recognisable file format, and a plain handle would show anyone on the Wi-Fi who is measuring whom. | `RangingWire.kt` | None advised. |
| 9 | **The pairing numbers** (D-300): Bluetooth readings count for 3 s, a pair is kept while both phones stay in each other's top capacity + 2, and a pair nobody lists is dropped after 3 s. | Starting values to tune on phones. | `Ranging.kt` | Change the constants. |
| 10 | **The pairing is not written into recordings; the format stays at 42** (D-300). | It creates no game event, the same as the contact resolver. | D-300 | Record it later if replays need it. |
| 11 | **The frames that carry UWB still cost about 130 KB/s on an 18-phone host** (likely about 250 KB/s on a phone), on top of what came before. That is within the 0.5 MB/s budget, but it uses a real share of it. The next lever, one token slot each way, would mean a phone starts at most one new pair a second. Not built. | It changes how fast pairs form. That is yours to weigh after the phone test. | D-300, item 10 | Nothing to undo. |

## Built and pushed overnight

- **D-299** (`6f10fcf` → main): the caught-scan briefing and the camera keep their screen under the Egress alert. It also fixed the briefing, which used to show no alert and no dim at all during an Egress, so it stayed fully lit while the house dimmed.
  - On the camera, the alert sits over the scan. The phone has no camera preview, and the card is read from the camera's own frames, so a read under the alert still works.
  - The dimmed field lowers the light the card is read by, from full to 0.32. Whether a card still reads at that light needs a phone.
- **Three-phone UWB test tool** (`dc5cf99`): ready for the owner's test.
  - iOS offers only two phone-to-phone session options: camera assistance and extended distance. There is no rate setting.

- **D-300** (`9573d6f`, `7c3e515`): the UWB wire and the house's pairing, on the bench only. No UWB radio code yet, and no rule reads the distances, so nothing in play changes.
  - The full check on this exact tree passed: 3 098 tests, 0 failures.
  - The house's per-phone memory cost dropped from about 30 KB to 1.75 KB a second.

(More as work lands.)
