#!/usr/bin/env python3
"""Compare stack-gate runs across result directories.

Reads the raw trial CSVs rather than the summaries, so the verdict is re-derived here and
does not depend on whichever build wrote the report. Several of this spike's reports were
written by builds whose headline metric was later found to be measuring nothing.

    ./compare.py                 # every run found under results/
    ./compare.py results/2026*   # specific directories
"""
import csv
import glob
import os
import sys

# Late = the draw took longer than one whole frame plus a margin for render time. Matches the
# in-app threshold so the two agree; see Report.kt.
LATE_MULTIPLIER = 1.15


def load(d, tag):
    trials = [r for r in csv.DictReader(open(f"{d}/trials-{tag}.csv")) if r["prewarm"] == "0"]
    try:
        gc = [int(g["stw_ns"]) / 1e6 for g in csv.DictReader(open(f"{d}/gc-{tag}.csv"))]
    except FileNotFoundError:
        gc = []
    return trials, gc


def summary_field(d, tag, needle):
    try:
        for line in open(f"{d}/summary-{tag}.txt"):
            if needle in line:
                return line.strip()
    except FileNotFoundError:
        pass
    return ""


def analyse(d, tag):
    trials, gc = load(d, tag)
    if not trials:
        return None

    # Frame interval as measured during that run, not assumed.
    line = summary_field(d, tag, "frame interval")
    interval = 8.335
    if "measured" in line:
        try:
            interval = float(line.split("measured")[1].strip().rstrip("ms"))
        except ValueError:
            pass

    draw = sorted(int(r["draw_latency_ns"]) / 1e6 for r in trials)
    late = [r for r in trials if int(r["draw_latency_ns"]) / 1e6 > interval * LATE_MULTIPLIER]
    late_gc = sum(1 for r in late if int(r["flags"]) & 2)
    gc = sorted(gc)

    mb = summary_field(d, tag, "MEASURED")
    rate = mb.split("MEASURED")[1].split("MB/s")[0].strip() if "MEASURED" in mb else "?"
    thermal = summary_field(d, tag, "thermal").replace("thermal", "").strip() or "-"

    def q(xs, p):
        return xs[min(int(len(xs) * p), len(xs) - 1)] if xs else 0.0

    # Every pull copies the whole Documents directory, so the same run reappears in each new
    # result folder. Fingerprint the actual trial data so repeats collapse instead of looking
    # like repeated measurements — which would be a fabricated sample size.
    fingerprint = (tag, len(trials), trials[0]["trigger_ns"], trials[-1]["trigger_ns"])

    return dict(
        tag=tag, n=len(trials), rate=rate, interval=interval, fingerprint=fingerprint,
        late=len(late), late_pct=100 * len(late) / len(trials), late_gc=late_gc,
        d50=q(draw, 0.50), d999=q(draw, 0.999), dmax=draw[-1],
        gcn=len(gc), gc99=q(gc, 0.99), gcmax=gc[-1] if gc else 0.0,
        thermal=thermal.split()[0] if thermal != "-" else "-",
    )


def main():
    dirs = sys.argv[1:] or sorted(glob.glob("results/*"))
    rows = []
    seen = set()
    for d in dirs:
        if not os.path.isdir(d):
            continue
        for path in sorted(glob.glob(f"{d}/trials-*.csv")):
            tag = os.path.basename(path)[len("trials-"):-len(".csv")]
            a = analyse(d, tag)
            if a and a["fingerprint"] not in seen:
                seen.add(a["fingerprint"])
                a["dir"] = os.path.basename(d)
                rows.append(a)

    if not rows:
        print("no results found")
        return

    hdr = (f"{'run':<20}{'when':<16}{'MB/s':>6}{'n':>7}{'late':>6}{'late%':>7}"
           f"{'lateGC':>7}{'d50':>7}{'d99.9':>7}{'dmax':>7}{'GCs':>6}{'gc99':>7}{'gcmax':>7}{'therm':>7}")
    print(hdr)
    print("-" * len(hdr))
    for r in rows:
        print(f"{r['tag']:<20}{r['dir'][:15]:<16}{r['rate']:>6}{r['n']:>7}{r['late']:>6}"
              f"{r['late_pct']:>6.2f}%{r['late_gc']:>7}{r['d50']:>7.2f}{r['d999']:>7.2f}"
              f"{r['dmax']:>7.2f}{r['gcn']:>6}{r['gc99']:>7.3f}{r['gcmax']:>7.3f}{r['thermal']:>7}")

    print()
    print("late    = draws exceeding one frame interval x %.2f" % LATE_MULTIPLIER)
    print("lateGC  = of those, how many had a collection in the trial window.")
    print("          late == lateGC means the collector; late >> lateGC means something else.")
    print("dmax    = worst trigger-to-drawn-black, ms. Compare against the frame interval.")
    print("gcmax   = worst stop-the-world, ms. Over one frame interval is the un-anonymised revoke.")
    print("therm   = thermal state; anything but 'nominal' means that run was throttled.")


if __name__ == "__main__":
    main()
