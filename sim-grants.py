#!/usr/bin/env python3
"""Give a simulator the grants a host would have given, so a rehearsal can reach a lobby.

    ./sim-grants.py notifications <udid> ...   # devices SHUT DOWN: SpringBoard reads this at boot
    ./sim-grants.py privacy      <udid> ...    # devices BOOTED and the app installed

WHAT THIS IS. D-161 puts a gate in front of every seat: a phone cannot host and cannot join until
the OS has answered for the whole permission set. On a real phone the host answered those modals
weeks ago, standing in a lit room with a thumb. **A simulator driven by `simctl` has no thumb**, and
two of the five prompts are SpringBoard alerts that nothing on this side of the glass can dismiss.

So this administers the same grants out of band, the way `xcrun simctl privacy` is meant to be used,
and it is the exact sibling of `LobbyCheat.seedTheHome`: the *walk* is stood in for, and only the
walk. Nothing here touches the app, its seam or its reading of the OS. After this runs the app still
asks `AVCaptureDevice`, `CMMotionActivityManager`, `CBCentralManager` and `UNUserNotificationCenter`
itself and still prints whatever they say — see the `[perms]` lines in any seat log, which are the
evidence that a grant is really there rather than this script's claim that it should be.

⚠️ WHAT IT DOES NOT MAKE TRUE. A granted row is not a working sensor. The simulator still has no
BLE, no camera, no torch and no haptics, and `rehearse.sh`'s own header says nothing about contact,
light or a buzz may ever be signed off from here. This only removes a gate that is standing in front
of the wire and the screen, which are the two things a simulator does carry honestly.

THE TWO HALVES, because the OS gives them different doors:

  1. `xcrun simctl privacy <udid> grant all|camera` — Apple's own supported command. Covers CAMERA
     and MOTION & FITNESS. Bluetooth and Local Network need nothing: a simulator has no radio, so
     the seam reads NOT ON THIS PHONE, and the local network is the Mac's own and is never gated.

  2. NOTIFICATIONS has no `privacy` service and no CLI at all. What it has is SpringBoard's own
     per-app record in `BulletinBoard/VersionedSectionInfo.plist`, which is where an answered alert
     is written down. This copies a **real** record — one an actual thumb produced on some
     simulator on this Mac — onto the devices that have none. It forges nothing: the record is
     genuine, and `UNUserNotificationCenter` on the receiving device then reports authorised
     because it genuinely is. If no donor exists this says so and grants nothing, and the round
     will refuse at the gate naming NOTIFICATIONS, which is the honest outcome.

     ⚠️ Undocumented, and the first thing to suspect when a rehearsal starts refusing on that row
     after an Xcode upgrade. The failure is loud — the app's own `[perms] NOTIFICATIONS → …` line
     is what says whether it took.
"""

# macOS ships a python3 old enough that `bytes | None` in an annotation is a TypeError at import
# time, and the whole script failed before it granted anything — with `rehearse.sh` carrying on to a
# lobby that then needed five residents and only ever had one. Annotations are strings from here.
from __future__ import annotations

import pathlib
import plistlib
import subprocess
import sys

BUNDLE = "home.someoneshome.app"
DEVICES = pathlib.Path.home() / "Library/Developer/CoreSimulator/Devices"
SECTION_INFO = "data/Library/BulletinBoard/VersionedSectionInfo.plist"


def privacy(udid: str) -> None:
    """The supported half. `all` first, then camera by name — the two are not the same set."""
    for service in ("all", "camera"):
        subprocess.run(
            ["xcrun", "simctl", "privacy", udid, "grant", service, BUNDLE],
            capture_output=True, check=False,
        )


def notification_record() -> bytes | None:
    """A real answered-alert record for this bundle, from any simulator on this Mac that has one."""
    for plist in sorted(DEVICES.glob(f"*/{SECTION_INFO}")):
        try:
            sections = plistlib.loads(plist.read_bytes()).get("sectionInfo", {})
        except (OSError, plistlib.InvalidFileException):
            continue
        if BUNDLE in sections:
            return sections[BUNDLE]
    return None


def give_notifications(udid: str, record: bytes) -> str:
    """Write the record onto one device. The device must be shut down; SpringBoard reads it at boot."""
    plist = DEVICES / udid / SECTION_INFO
    if not plist.exists():
        return "no bulletin store on this device — it has never been booted"
    content = plistlib.loads(plist.read_bytes())
    sections = content.setdefault("sectionInfo", {})
    if BUNDLE in sections:
        return "already answered on this device"
    sections[BUNDLE] = record
    plist.write_bytes(plistlib.dumps(content, fmt=plistlib.FMT_BINARY))
    return "notification alert answered out of band"


def main(what: str, udids: list[str]) -> int:
    if what == "privacy":
        for udid in udids:
            privacy(udid)
            print(f"[sim-grants] {udid} — camera and motion granted")
        return 0

    record = notification_record()
    if record is None:
        print(
            "[sim-grants] NO ANSWERED NOTIFICATION ALERT EXISTS ON THIS MAC — nothing to copy.\n"
            "[sim-grants] Answer the alert by hand once on any simulator and every run after this\n"
            "[sim-grants] one is covered. Until then the gate will refuse naming NOTIFICATIONS.",
        )
        return 0
    for udid in udids:
        print(f"[sim-grants] {udid} — {give_notifications(udid, record)}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] not in ("notifications", "privacy"):
        print(__doc__)
        raise SystemExit(2)
    raise SystemExit(main(sys.argv[1], sys.argv[2:]))
