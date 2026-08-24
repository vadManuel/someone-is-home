#!/usr/bin/env python3
"""Pick the two seats worth looking at and copy their beat shots into the repo.

    ./rehearse-shots.py /tmp/someones-home-rehearsal/run1 _bmad-output/overnight/shots5

`rehearse.sh` photographs **all five** seats at every beat, deliberately: which seat ends up on the
couch is the draw's business and nothing in the driver can predict it. This picks the pair a person
would actually want to see afterwards — **one living seat and one out seat** — and names the files
after the beat rather than after the simulator.

The out seat is found the way everything else in this rehearsal is found: off the seat's own ledger,
by the screen it stood on. A seat whose reading ever carried `OUT REVOKED` or `OUT RESTRAINED` left
the round, and the first one that did is the couch this run is illustrated from.
"""
import os
import re
import shutil
import sys

STAMP = re.compile(r"^\[screen\] @(\d+) (.*)$")


OUT_SCREENS = ("ON REVOKED", "ON RESTRAINED", "ON GHOST2", "ON GHOST3", "ON GHOSTMEETING")


def out_seats(root):
    """Seats that left the round, and how many samples they spent out — off their own SCREENS.

    Not off `PanelState.outBy`, and the difference is this rehearsal's own finding: a Revoked seat
    reaches every out screen there is and its panel never carries a cause (E-R2-4). A picker that
    asked for the cause would skip the one seat that spent the whole round on the couch.
    """
    found = []
    for i in range(5):
        path = os.path.join(root, "seat-%d.log" % i)
        if not os.path.exists(path):
            continue
        spent = 0
        with open(path, errors="replace") as handle:
            for line in handle:
                m = STAMP.match(line.rstrip("\n"))
                if m and any(x in m.group(2) for x in OUT_SCREENS):
                    spent += 1
        if spent:
            found.append((i, spent))
    return sorted(found, key=lambda x: -x[1])


def screens_at_beats(root):
    """What each seat's ledger says it was showing at each beat the host announced."""
    lines, beats, out = {}, [], {}
    for i in range(5):
        path = os.path.join(root, "seat-%d.log" % i)
        lines[i] = []
        if not os.path.exists(path):
            continue
        for line in open(path, errors="replace"):
            m = STAMP.match(line.rstrip("\n"))
            if not m:
                continue
            stamp, body = int(m.group(1)), m.group(2)
            if body.startswith("BEAT "):
                name, body = body[5:].split(" — ", 1)
                if i == 0:
                    beats.append((name, stamp))
            lines[i].append((stamp, body))
    for name, stamp in beats:
        for i in range(5):
            seen = [b for s, b in lines[i] if s <= stamp]
            if seen:
                out[(name, i)] = seen[-1].split(" · ")[0].replace("ON ", "")
    return out


def main(root, dest):
    shots = os.path.join(root, "shots")
    if not os.path.isdir(shots):
        print("no shots under %s" % root)
        return
    out = out_seats(root)
    ghost = out[0][0] if out else None
    gone = {i for i, _ in out}
    # A seat that never left the round, so the pair is a real ghost and a real player rather than
    # two people who both ended up on the couch.
    living = next((i for i in range(5) if i not in gone), next(i for i in range(5) if i != ghost))
    print("out seats (index, samples spent out): %s · illustrating from living seat%s and out seat%s"
          % (out or "none", living, ghost))

    os.makedirs(dest, exist_ok=True)
    # **In the order the round happened**, off the driver's own beats file, so the numbering is the
    # evening's shape rather than the alphabet's.
    order = ["00-lobby"]
    beats = os.path.join(root, "beats.txt")
    if os.path.exists(beats):
        for line in open(beats):
            name = line.split("\t")[0].strip()
            if name and name not in order and os.path.exists(
                    os.path.join(shots, "%s--seat0.png" % name)):
                order.append(name)
    order.append("99-new-round")
    for name in sorted(os.listdir(shots)):
        beat = name.split("--seat")[0]
        if beat not in order:
            order.append(beat)

    # **Each shot is named for the beat AND for the screen the ledger says that seat was on**, and
    # the second half is not decoration. Five simulators sharing one Mac's cores do not all draw at
    # once: a client can be seconds behind the house, and a file called `vote` showing a discussion
    # would be this project's own favourite kind of lie — a label nobody can check against the
    # thing it labels. Where the two disagree, the log is the finer instrument and the name says so.
    at = screens_at_beats(root)
    copied = 0
    for n, beat in enumerate(order):
        for role, seat in (("living", living), ("out", ghost)):
            if seat is None:
                continue
            src = os.path.join(shots, "%s--seat%d.png" % (beat, seat))
            if not os.path.exists(src):
                continue
            slug = beat.lower().replace("_", "-").replace("00-", "").replace("99-", "")
            screen = at.get((beat, seat), "").lower()
            dst = os.path.join(dest, "%02d-%s-%s%s.png"
                               % (n, slug, role, "-on-" + screen if screen else ""))
            shutil.copyfile(src, dst)
            copied += 1
    print("%d shot(s) into %s" % (copied, dest))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "_bmad-output/overnight/shots5")
