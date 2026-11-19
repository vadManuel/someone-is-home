# Overnight #4's close — the permissions screen, and the gate that stopped the round

**Twelve frames, and there is no beat set. That absence is the finding.** Every earlier set in this
directory photographs a round: `shots5/` is fifteen beats across five phones. **No round can be
armed on this build**, so there were no beats to photograph, and what is here instead is the three
states a phone really reaches tonight — plus the refusal that is where every evening now stops.

Four iOS Simulator seats, real device aspect, `Playtest` at `3f41958`.

| tag | what it is |
|---|---|
| `00-at-one-second` | one second after a plain launch, no switches at all |
| `01-at-seven-seconds` | the same four seats six seconds later |
| `02-gate-refused-on-join` | the same four seats launched the way **every bench in this repo launches them** |

## The two frames to put side by side

**`01-at-seven-seconds--seat1.png`** — the permissions screen, built last night and never looked at
by anybody. It reads the OS honestly and its gate line names **two** rows:

```
LOCAL NETWORK   TAP TO GRANT        THIS PHONE CANNOT TAKE A SEAT YET
CAMERA          GRANTED             LOCAL NETWORK · BLUETOOTH
BLUETOOTH       TAP TO GRANT
MOTION & FITNESS NOT ON THIS PHONE
NOTIFICATIONS   GRANTED
```

**`02-gate-refused-on-join--seat1.png`** — the *same simulator*, minutes later, launched with the
lobby bench's switches. Same phone, same grants, and the gate now names **all five**:

```
THIS PHONE CANNOT TAKE A SEAT YET
LOCAL NETWORK · CAMERA · BLUETOOTH · MOTION & FITNESS · NOTIFICATIONS
```

Two photographs of one device disagreeing about that device. **`PermsModel.states` opens at
`emptyMap()` and `refresh()` has exactly one caller in the whole app —
`LaunchedEffect(perms) { perms.refresh() }`, inside the permissions screen's own composable.** A
phone that reaches HOST or JOIN without ever composing that screen carries an empty map, every row
reads `Unasked`, and the gate refuses while naming rows the OS would have said were granted. Both
benches jump straight to `ScreenId.Join`, so both take exactly that route. See the worklog's
`## Summary — overnight #4`, §4.

## What else is visible in these frames, and was not looked for

- **The vertical blind zone is not only the lobby's.** N5b recorded it there; it is on `PERMS` and on
  `JOIN` too — better than half of a 6.9" panel is empty scan-lined ground on both.
- **`A SYSTEM PROMPT WILL APPEAR. / IT IS NOT PART OF THE HOUSE.`** is close to illegible over the
  banding, on the screen every evening starts on. That is the owner's own deferred scan-lines
  report (`ScanLines`, `SCAN_LINE_INK = rgba(0,0,0,.32)`), landing on new work.
- **`JOIN` draws no back chevron**, which is correct — it is the trail's root — and the `RESIDENT`
  field now carries its label, which is the E2E spike's accessibility fix, drawn.

⚠️ **THE PIXELS ARE ILLUSTRATIVE AND THE LOG IS THE INSTRUMENT** — `shots5`'s rule, unchanged.
Simulators on one Mac do not all keep drawing, and `simctl io screenshot` returns the last frame
that device drew. Seats 1, 2 and 3 are byte-identical between `00-` and `01-`: they had settled
before the first shot, not stalled. Seat 4 is the one that moved.

Regenerate with the plain-launch / bench-launch pair described above; there is no driver script for
this set, because there is no round for one to drive.
