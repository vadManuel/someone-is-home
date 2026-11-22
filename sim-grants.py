#!/usr/bin/env python3
"""Give a simulator the grants a host would have given, so a rehearsal can reach a lobby.

    ./sim-grants.py privacy <udid> ...    # devices BOOTED and the app installed

WHAT THIS IS. D-161 puts a gate in front of every seat: a phone cannot host and cannot join until
the OS has answered for the whole permission set. On a real phone the host answered those modals
weeks ago, standing in a lit room with a thumb. **A simulator driven by `simctl` has no thumb.**

So this administers the same grants out of band, the way `xcrun simctl privacy` is meant to be used,
and it is the exact sibling of `LobbyCheat.seedTheHome`: the *walk* is stood in for, and only the
walk. Nothing here touches the app, its seam or its reading of the OS. After this runs the app still
asks `AVCaptureDevice`, `CMMotionActivityManager` and `CBCentralManager` itself and still prints
whatever they say — see the `[perms]` lines in any seat log, which are the evidence that a grant is
really there rather than this script's claim that it should be.

⚠️ WHAT IT DOES NOT MAKE TRUE. A granted row is not a working sensor. The simulator still has no
BLE, no camera, no torch and no haptics, and `rehearse.sh`'s own header says nothing about contact,
light or a buzz may ever be signed off from here. This only removes a gate that is standing in front
of the wire and the screen, which are the two things a simulator does carry honestly.

WHAT IT GRANTS. `xcrun simctl privacy <udid> grant all|camera` — Apple's own supported command.
Covers CAMERA and MOTION & FITNESS. Bluetooth and NEARBY DEVICES need nothing: a simulator has no
radio and no ranging, so the seam reads NOT ON THIS PHONE (D-314), and the local network is the
Mac's own and is never gated. There is no NOTIFICATIONS row to answer (D-326): the app posts no iOS
notification.
"""

from __future__ import annotations

import subprocess
import sys

BUNDLE = "home.someoneshome.app"


def privacy(udid: str) -> None:
    """The supported grant. `all` first, then camera by name — the two are not the same set."""
    for service in ("all", "camera"):
        subprocess.run(
            ["xcrun", "simctl", "privacy", udid, "grant", service, BUNDLE],
            capture_output=True, check=False,
        )


def main(udids: list[str]) -> int:
    for udid in udids:
        privacy(udid)
        print(f"[sim-grants] {udid} — camera and motion granted")
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] != "privacy":
        print(__doc__)
        raise SystemExit(2)
    raise SystemExit(main(sys.argv[2:]))
