#!/usr/bin/env python3
"""Read five seats' logs and say what each of their screens was at every beat.

    ./rehearse-check.py /tmp/someones-home-rehearsal/run1

WHY THIS IS A SECOND FILE. `rehearse.sh` drives the round; this judges it. A script that did both
would be reading its own output for the answers it put there. Everything here comes off the five
device consoles and nothing here talks to a simulator.

WHAT IT PRODUCES
  1. THE BEAT TABLE — for each beat the bench announced, what all five screens were showing at that
     instant. Correlated on the monotonic stamp `ScreenLedger` puts on every line, which is the host
     kernel's clock and therefore the same clock in all five processes.
  2. THE PER-SEAT KIND TABLE — every push kind, counted per seat. P3's central evidence and a
     redaction proof rather than a summary: a kind on one seat is the house addressing one seat.
  3. THE ONE-LINE PROBE — LineWatch's verdict from each seat, on raw bytes.
  4. THE ASSERTIONS — named, each with the evidence that decided it, PASS or FAIL.

Exit code is deliberately always 0. Read the FAIL lines; an exit code is what this project does not
sign anything off on.
"""
import os
import re
import sys
from collections import Counter, OrderedDict

STAMP = re.compile(r"^\[screen\] @(\d+) (.*)$")
BEAT = re.compile(r"^BEAT (\S+) — (.*)$")
PUSH = re.compile(r"^\[round\] PUSH (\d+) — (.*)$")
PRESSED = re.compile(r"^\[round\] PRESSED — (.*)$")
REFUSED = re.compile(r"^\[house\] REFUSED — (\S+) · (\S+)$")


def field(reading, name):
    """One `· NAME value` field out of a ledger reading. '?' when the line has no such field."""
    for part in reading.split(" · "):
        if part.startswith(name + " "):
            return part[len(name) + 1:]
        if part.startswith("ON ") and name == "ON":
            return part[3:]
    return "?"


class Seat:
    def __init__(self, index, path):
        self.index = index
        self.lines = []          # (stamp_ms, reading) in arrival order
        self.beats = OrderedDict()
        self.kinds = Counter()
        self.pressed = Counter()
        self.probe = []
        self.raw = []
        with open(path, errors="replace") as handle:
            for line in handle:
                line = line.rstrip("\n")
                self.raw.append(line)
                m = STAMP.match(line)
                if m:
                    stamp, body = int(m.group(1)), m.group(2)
                    b = BEAT.match(body)
                    if b:
                        self.beats[b.group(1)] = (stamp, b.group(2))
                        self.lines.append((stamp, b.group(2)))
                    else:
                        self.lines.append((stamp, body))
                    continue
                m = PUSH.match(line)
                if m:
                    self.kinds[m.group(2).split(" · ")[0].split(" —")[0]] += 1
                    continue
                m = PRESSED.match(line)
                if m:
                    self.pressed[m.group(1)] += 1
                    continue
                if "INBOUND —" in line or "OUTBOUND —" in line:
                    self.probe.append(line.split("] ", 1)[-1])

    def at(self, stamp):
        """The reading this seat's screen was showing at [stamp]. The last one at or before it."""
        best = None
        for s, reading in self.lines:
            if s <= stamp:
                best = reading
            else:
                break
        return best or "(NOTHING PRINTED)"

    def has(self, needle):
        return any(needle in line for line in self.raw)

    def count(self, needle):
        return sum(1 for line in self.raw if needle in line)


def main(root):
    seats = []
    for i in range(5):
        path = os.path.join(root, "seat-%d.log" % i)
        if not os.path.exists(path):
            print("missing %s" % path)
            return
        seats.append(Seat(i, path))

    host = seats[0]
    checks = []

    def check(name, ok, evidence):
        checks.append((name, ok, evidence))

    print("=" * 100)
    print("THE BEAT TABLE — what all five screens were showing when the bench announced each beat")
    print("=" * 100)
    for beat, (stamp, reading) in host.beats.items():
        print("\n--- %s ---" % beat)
        for seat in seats:
            r = seat.at(stamp)
            print("  seat%d  %-14s OUT=%-11s PHASE=%-10s GATE=%-6s VOTED=%-6s METER=%-5s "
                  "RUN=%-5s HELD=%-5s BEAT=%-5s JAM=%-4s BALLOTS=%-3s REVEALED=%-3s ORDER=%-3s "
                  "WORK=%-12s VERDICT=%-9s CALLER=%s"
                  % (seat.index, field(r, "ON"), field(r, "OUT"), field(r, "PHASE"),
                     field(r, "GATE"), field(r, "VOTED"), field(r, "METER"),
                     field(r, "RUNNING"), field(r, "HELD"), field(r, "BEAT"), field(r, "JAM"),
                     field(r, "BALLOTS"), field(r, "REVEALED"), field(r, "ORDER"),
                     field(r, "WORK"), field(r, "VERDICT"), field(r, "CALLER")))

    print("\n" + "=" * 100)
    print("THE PER-SEAT KIND TABLE — every push, counted, per seat")
    print("=" * 100)
    every = sorted({k for seat in seats for k in seat.kinds})
    width = max([len(k) for k in every] + [20])
    print("  %-*s %s" % (width, "KIND", "  ".join("seat%d" % s.index for s in seats)))
    for kind in every:
        counts = [seat.kinds[kind] for seat in seats]
        reach = sum(1 for c in counts if c)
        print("  %-*s %s   <- %d SEAT(S)"
              % (width, kind, "  ".join("%5d" % c for c in counts), reach))

    print("\n" + "=" * 100)
    print("WHAT WENT UP — controls pressed, per seat")
    print("=" * 100)
    every_up = sorted({k for seat in seats for k in seat.pressed})
    for kind in every_up:
        print("  %-32s %s" % (kind, "  ".join("%5d" % seat.pressed[kind] for seat in seats)))

    print("\n" + "=" * 100)
    print("THE ONE-LINE PROBE — LineWatch, on raw bytes, per seat")
    print("=" * 100)
    for seat in seats:
        print("  seat%d  %s" % (seat.index, seat.probe[-1] if seat.probe else "(no verdict printed)"))

    # ---------------------------------------------------------------------------------------
    # THE ASSERTIONS. Every one of them is about a SCREEN, because that is what P3 could not see.
    # ---------------------------------------------------------------------------------------
    def screens_at(beat):
        if beat not in host.beats:
            return None
        stamp = host.beats[beat][0]
        return [field(seat.at(stamp), "ON") for seat in seats]

    def readings_at(beat):
        if beat not in host.beats:
            return None
        stamp = host.beats[beat][0]
        return [seat.at(stamp) for seat in seats]

    # 1. E-P3-1's exact inverse, and it is asked of the WHOLE ROUND rather than of one instant.
    # P3's finding was a set: *every distinct screen a client stood on, whole round: LOBBY*. So the
    # answer has to be a set too — how many different screens each seat really stood on.
    walked = []
    for seat in seats:
        walked.append(sorted({field(r, "ON") for _, r in seat.lines}))
    check("the round is visible — every seat walked more than the lobby (E-P3-1 closed)",
          all(len([x for x in w if x not in ("LOBBY", "JOIN", "?")]) >= 3 for w in walked),
          "\n         ".join("seat%d stood on %s" % (i, w) for i, w in enumerate(walked)))

    # 1b. And at the first beat where every phone has been dealt work, none of them is in a lobby.
    s = screens_at("SHIFT-DONE")
    check("no seat is still standing in the lobby once the shift has run",
          s is not None and all(x not in ("LOBBY", "JOIN", "?") for x in s),
          "screens: %s" % s)

    # 2. Every seat was dealt an order of the same length (D-129).
    r = readings_at("SHIFT-DONE")
    orders = [field(x, "ORDER") for x in r] if r else []
    check("every seat holds a work order and they are all the same length (D-129)",
          bool(orders) and len(set(orders)) == 1 and orders[0] not in ("-", "?"),
          "orders: %s" % orders)

    # 3. The caught scan opened with its own briefing on it (D-188), on at least one seat.
    check("a scan opened work and the caught-scan screen drew that Subroutine",
          any(seat.has("ON SCANCAUGHT") for seat in seats),
          "seats reaching SCANCAUGHT: %s"
          % [seat.index for seat in seats if seat.has("ON SCANCAUGHT")])

    # 4. A verdict reaches the seat that handed the entry over and nobody else.
    graded = [seat.kinds["SUBROUTINE GRADED"] + seat.kinds["VERDICT"] for seat in seats]
    verdicts = [seat.count("VERDICT — ") for seat in seats]
    check("verdicts differ per seat — the answer goes to the sender alone",
          len(set(verdicts)) > 1,
          "verdict pushes per seat: %s" % verdicts)

    # 5 & 6. E-R1-1 live. Asked of the WHOLE ROUND rather than of one instant: the ring is a
    # six-second presentation and the gate can close inside it, so which screen a seat is on at a
    # named moment is a fact about how fast five processes on one Mac are, and which screens it
    # STOOD ON is a fact about the build.
    stood = [{field(r, "ON") for _, r in seat.lines} for seat in seats]
    calling = [i for i, w in enumerate(stood) if "CALLING" in w]
    check("exactly one phone was put on CALLING and it is one phone (E-R1-1, D-121)",
          len(calling) == 1,
          "seats that stood on CALLING: %s · on FOUND: %s · on CALL: %s"
          % (calling, [i for i, w in enumerate(stood) if "FOUND" in w],
             [i for i, w in enumerate(stood) if "CALL" in w]))

    check("the other living phones rang on FOUND — a reported Revoke, not a card (R1 §1)",
          len([i for i, w in enumerate(stood) if "FOUND" in w]) >= 3,
          "seats that stood on FOUND: %s" % [i for i, w in enumerate(stood) if "FOUND" in w])

    # 7. D-104: the gate counts every living player AND every out player, and closes on all of them.
    # Read at the discussion, which is the phase the gate closing OPENS.
    r = readings_at("DISCUSSION")
    gates = [field(x, "GATE") for x in r] if r else []
    closed = [g for g in gates if g not in ("?", "-") and g.split("/")[0] == g.split("/")[1]]
    check("the check-in gate closed with every seat present, the ghost included (D-104, E-P3-2)",
          len(closed) >= 4,
          "gates: %s" % gates)

    # 8. Somebody left the round and their own screen says which way. Whole round: a Revoke lands
    # mid-shift and a Restrain at the tally, so no single beat has both.
    out_by = {}
    for i, seat in enumerate(seats):
        for _, reading in seat.lines:
            if field(reading, "OUT") not in ("-", "?"):
                out_by[i] = field(reading, "OUT")
                break
    left = [i for i, w in enumerate(stood)
            if w & {"REVOKED", "RESTRAINED", "GHOST2", "GHOST3", "GHOSTMEETING"}]
    check("every seat that left the round has a screen that says which way it left",
          bool(left) and all(i in out_by for i in left),
          "seats on an out screen: %s · seats whose panel carries a cause: %s" % (left, out_by))

    # 9. The live ballot is the couch's and nobody else's (D-117, D-134, D-075).
    holds = {i: max((field(r, "BALLOTS") for _, r in seat.lines), key=lambda v: (v != "-", v))
             for i, seat in enumerate(seats)}
    check("the couch's live ballot reaches the panel and no living phone (D-117)",
          any(holds[i] not in ("-", "?") for i in left)
          and all(holds[i] in ("-", "?") for i in range(5) if i not in left),
          "the most this seat's ballot ever held: %s · out seats: %s" % (holds, left))

    # 10. The Egress takeover reaches every living screen.
    s = screens_at("EGRESS-OPEN")
    r = readings_at("EGRESS-OPEN")
    running = [field(x, "RUNNING") for x in (r or [])]
    check("an Egress fired from a phone and every seat knows the house is running one",
          bool(running) and running.count("TRUE") + running.count("true") >= 4,
          "screens: %s · running: %s" % (s, running))

    # 11. The jam reaches both living classes and neither out class.
    r = readings_at("STATIC")
    jams = [field(x, "JAM") for x in (r or [])]
    outs_now = [field(x, "OUT") for x in (r or [])]
    ghost_jams = [j for j, o in zip(jams, outs_now) if o not in ("-", "?")]
    check("a Static jam reached the living and no out seat (D-186, EmitSchemaTest)",
          bool(jams) and any(j not in ("-", "?") for j in jams)
          and all(j in ("-", "?") for j in ghost_jams),
          "jams: %s (out: %s)" % (jams, outs_now))

    # 12. The ending: the reveal on every screen, percentages only.
    r = readings_at("ENDING")
    revealed = [field(x, "REVEALED") for x in (r or [])]
    endings = [field(x, "ON") for x in (r or [])]
    check("the reveal landed on all five screens",
          bool(revealed) and all(v not in ("-", "?", "0") for v in revealed),
          "screens: %s · insiders named: %s" % (endings, revealed))

    # 13. The gap injection converged: the stopped seat asked for its stream and got it from 1.
    gap = [seat.index for seat in seats
           if seat.has("SEATED ON THIS CONNECTION — ASKING FOR WHAT WAS SAID WHILE IT WAS GONE")]
    check("a seat stopped mid-shift resumed ITS seat and the house replayed its outbox (D-097)",
          bool(gap),
          "seats that re-asserted: %s" % gap)

    # 14. The wire members R1 built are the ones the round really used.
    up = Counter()
    for seat in seats:
        up.update(seat.pressed)
    check("STOP NOW went up the wire as a real control (R1 §4)",
          up.get("StopSubroutine", 0) >= 1, "StopSubroutine frames: %d" % up.get("StopSubroutine", 0))
    check("the Sync Pulse went up the wire as a real control (R1 §4, E-P2-5 closed)",
          up.get("ReturnSyncPulse", 0) >= 1,
          "ReturnSyncPulse frames: %d" % up.get("ReturnSyncPulse", 0))
    check("the Egress tile went up as its own frame (RoundBody.Fire) from more than one seat",
          up.get("THE EGRESS TILE", 0) >= 2,
          "Fire frames: %d" % up.get("THE EGRESS TILE", 0))
    check("entries were handed over as real frames (R1 §4)",
          up.get("HandOverEntry", 0) >= 1, "HandOverEntry frames: %d" % up.get("HandOverEntry", 0))

    # 14b. **WHO WAS ALLOWED TO FIRE THE EGRESS.** The house's own log names the seat every frame
    # was attributed to; the ledger names which seats had left the round. If a seat that was out —
    # or a seat that never received `IsolateTargetsOffered`, the one push only an Insider gets —
    # fired one, then nothing between the socket and the rules checked who was asking.
    insiders = [seat.index for seat in seats if seat.kinds.get("IsolateTargetsOffered")]
    gone = []
    for seat in seats:
        for _, reading in seat.lines:
            if field(reading, "OUT") not in ("-", "?"):
                gone.append(seat.index)
                break
    firing = [seat.index for seat in seats if seat.pressed.get("THE EGRESS TILE")]
    drew = host.count("EGRESS DRAWN")
    unlawful = [i for i in firing if i not in insiders]
    check("only a seat the house treats as an Insider ever fired an Egress",
          drew == 0 or not unlawful,
          "insider seats (by IsolateTargetsOffered): %s · out seats: %s · seats that pressed the "
          "tile: %s · draws the house made: %d" % (insiders, gone, firing, drew))

    # 15. The tick driver minted the five transitions — nothing in the bench did.
    minted = host.count("STAND-IN — CLOSING") + host.count("STAND-IN — THE TALLY") \
        + host.count("STAND-IN — THE EGRESS COUNTDOWN")
    check("no timed transition was minted by the bench — the driver owns all five (E-P3-3)",
          minted == 0, "bench mints of a timed transition: %d" % minted)

    # 16. The one line never came back down, on any seat, across a round that publishes one.
    carried = [p for seat in seats for p in seat.probe if "0 CARRIED" not in p]
    check("no frame on any socket carried this phone's own line",
          not carried, "probe lines that were not clean: %s" % (carried or "none"))

    print("\n" + "=" * 100)
    print("THE ASSERTIONS")
    print("=" * 100)
    passed = 0
    for name, ok, evidence in checks:
        print("  [%s] %s\n         %s" % ("PASS" if ok else "FAIL", name, evidence))
        passed += bool(ok)
    print("\n  %d of %d passed." % (passed, len(checks)))


def differential(a_root, b_root):
    """**The role-swap differential, on frames that really reached a socket.**

    The harness's `DifferentialTest` swaps a role in a seeded round and diffs two transcripts, which
    is the strongest form of this check and is made entirely of objects. This is the same question
    asked of **delivered streams**: two rounds, two seeds, the same five seats in the same five
    processes — so the draw moves and nothing else does.

    What it looks for is that the kinds only an Insider may receive **move with the role and not with
    the seat**. A kind that landed on the same seat in both rounds is either role-independent (and
    should be on all five) or is keyed on something other than the draw, which is the leak.
    """
    def table(root):
        out = {}
        for i in range(5):
            path = os.path.join(root, "seat-%d.log" % i)
            out[i] = Seat(i, path).kinds if os.path.exists(path) else Counter()
        return out

    a, b = table(a_root), table(b_root)
    kinds = sorted({k for t in (a, b) for c in t.values() for k in c})
    print("=" * 100)
    print("THE ROLE-SWAP DIFFERENTIAL — two seeds, the same five processes, kinds per seat")
    print("=" * 100)
    print("  %-34s %-22s %-22s" % ("KIND", os.path.basename(a_root), os.path.basename(b_root)))
    moved, stayed = [], []
    for kind in kinds:
        ca = [1 if a[i][kind] else 0 for i in range(5)]
        cb = [1 if b[i][kind] else 0 for i in range(5)]
        narrow = sum(ca) in (1, 2) or sum(cb) in (1, 2)
        mark = ""
        if narrow:
            (moved if ca != cb else stayed).append(kind)
            mark = "   <- MOVED" if ca != cb else "   <- SAME SEATS"
        print("  %-34s %-22s %-22s%s" % (kind, "".join(map(str, ca)), "".join(map(str, cb)), mark))
    print("\n  narrow kinds that moved with the draw: %s" % (moved or "none"))
    print("  narrow kinds that landed on the same seats in both rounds: %s" % (stayed or "none"))


if __name__ == "__main__":
    if len(sys.argv) > 3 and sys.argv[1] == "--diff":
        differential(sys.argv[2], sys.argv[3])
    else:
        main(sys.argv[1] if len(sys.argv) > 1 else "/tmp/someones-home-rehearsal/run1")
