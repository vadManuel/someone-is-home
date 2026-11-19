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

## Built and pushed overnight

- **D-299** (`6f10fcf` → main): the caught-scan briefing and the camera keep their screen under the Egress alert. It also fixed the briefing, which used to show no alert and no dim at all during an Egress, so it stayed fully lit while the house dimmed.
  - On the camera, the alert sits over the scan. The phone has no camera preview, and the card is read from the camera's own frames, so a read under the alert still works.
  - The dimmed field lowers the light the card is read by, from full to 0.32. Whether a card still reads at that light needs a phone.
- **Three-phone UWB test tool** (`dc5cf99`): ready for the owner's test.
  - iOS offers only two phone-to-phone session options: camera assistance and extended distance. There is no rate setting.

(More as work lands.)
