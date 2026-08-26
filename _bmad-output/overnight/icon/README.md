# icon — the lit door, at every size it ships at

Every file here is output of `tools/appicon/AppIcon.java`, and every one of them can be regenerated
by `./tools/appicon/icon.sh`. Nothing here is shipped: the app reads
`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset`. These are the renders the small-size ruling was
made from, kept so the ruling can be looked at rather than argued about.

| file | what it is |
|---|---|
| `icon-<n>.png` | the composition at n×n, **as shipped** — scan-line pitch snapped to whole pixels, band dropped below a 2.4px period |
| `icon-<n>-raw.png` | the same size with the band left literal and proportional — the **before** |
| `small-sizes.png` | every size below 1024, at 8× nearest-neighbour beside its own 1:1, on black |
| `small-sizes-raw.png` | the same sheet, before |
| `springboard-playtest.png` | the Playtest build on the simulator's home screen, at half scale |
| `springboard-180-at-4x.png` | the 180 off that screenshot, magnified 4× — iOS's own mask, iOS's own compositing |

`icon-29.png` and `icon-76.png` are rendered but **not shipped**: they are iPad points and
`TARGETED_DEVICE_FAMILY` is 1. They are here because the study's stated bar for an icon is the
small-size read, and *"unreadable at 29"* is a fact about the drawing rather than about the iPad.

## What the before/after pair shows

At and below the 76 the proportional band has a dark unit under one device pixel. It stops being a
band and becomes a flat ~18% veil — visible in `small-sizes-raw.png` as a grey wash over the whole
tile, taking contrast off the two things a 29pt tile has to say: the door against the wall, and the
frame against the ground. Above the 76 the proportional pitch lands on fractional pixel rows, and
the rasteriser splits each band across two rows by a different ratio — a moiré rather than a
texture, plainest at the 180, which is the size on the owner's own phone.

The ruled version drops the band below the threshold and snaps the pitch above it to whole pixels,
three at a time so the one-in-three duty stays exact. The 180 comes out at 2 dark pixels in 6 — 7%
off the study's density, and no beat.
