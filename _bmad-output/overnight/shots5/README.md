# Rehearsal 2 — the round as five phones drew it

Thirty frames from one five-seat round (seed 20260822), two seats per beat: **seat 2, who played
the whole round**, and **seat 0, who was Revoked during the shift and spent the rest of it on the
couch**. Both are real iOS Simulator framebuffers at real device aspect. `rehearse.sh` shoots all
five seats at every beat; `rehearse-shots.py` picks the pair and names them.

⚠️ **THE PIXELS ARE ILLUSTRATIVE AND THE LEDGER IS THE INSTRUMENT.** Five simulators on one Mac do
not all keep drawing: a window the other four are covering can stop compositing, and
`simctl io screenshot` then returns the last frame that device drew — which in one case here was
ten minutes old. So a shot is *a frame that phone drew at or before this beat*, never proof of what
it was drawing at it. What each seat's screen really was at each beat is `ScreenLedger`'s, read out
of all five device consoles and correlated on a monotonic stamp; the table below is that, and where
a picture disagrees with it, the table is right.

| beat | seat0 (out) | seat1 | seat2 (living) | seat3 | seat4 |
|---|---|---|---|---|---|
| ARMED | ARMED | NOTIFY | NOTIFY | NOTIFY | NOTIFY |
| WORK-ORDER | ARMED | SCANBAD | SCANBAD | SCANCAUGHT | SCANCAUGHT |
| SHIFT-DONE | SCANCAUGHT | SCANBAD | SCANCAUGHT | SCANCAUGHT | SCANBAD |
| REVOKED | SCANCAUGHT | SCANBAD | SCANCAUGHT | SCANCAUGHT | SCANBAD |
| MEETING-RINGING | GHOST2 | CALLING | FOUND | FOUND | FOUND |
| CHECKIN | GHOST2 | CALLING | FOUND | FOUND | FOUND |
| GATE-CLOSED | GHOSTMEETING | NOTICE | NOTICE | NOTICE | NOTICE |
| DISCUSSION | GHOSTMEETING | NOTICE | NOTICE | NOTICE | NOTICE |
| VOTE | GHOSTMEETING | DISCUSSION | DISCUSSION | DISCUSSION | DISCUSSION |
| TALLY | GHOSTMEETING | VOTE | VOTE | VOTE | VOTE |
| LIGHTS-OUT-AGAIN | GHOST3 | RESTRAINED | VOTE | VOTE | VOTE |
| STATIC | GHOST3 | RESTRAINED | SCANCAUGHT | SCANBAD | SCANCAUGHT |
| EGRESS-OPEN | GHOST3 | RESTRAINED | SCANCAUGHT | SCANBAD | SCANCAUGHT |
| EGRESS-RESOLVED | GHOST3 | RESTRAINED | SCANCAUGHT | SCANBAD | SCANCAUGHT |
| ENDING | WININSIDERS | WININSIDERS | WININSIDERS | WININSIDERS | WININSIDERS |

Regenerate with:

```
./rehearse.sh run1 20260822          # five seats, one round, shots at every beat
./rehearse-check.py /tmp/someones-home-rehearsal/run1
./rehearse-shots.py /tmp/someones-home-rehearsal/run1 _bmad-output/overnight/shots5
```
