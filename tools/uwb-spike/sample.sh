#!/usr/bin/env bash
# Summarise a labelled window of the UWB spike's readings, from both phones' logs.
#
#   tools/uwb-spike/sample.sh <label> [seconds]        live: wait N seconds (default 15), then
#                                                      summarise what both phones logged meanwhile
#   tools/uwb-spike/sample.sh --file <log> [<log>…]    after the fact: summarise whole log files
#
# Per phone: the reading count and rate (updates per second over the window, and between the first
# and last reading), the distance (median, nearest, farthest, 10th and 90th percentile), how many
# readings had no distance or no direction, the median azimuth when there was one, the longest gap
# between readings, and every session or app event (suspension, invalidation, removal, lock,
# background, MARK) in the window with its time.
#
# Only session 1's readings are counted (`[uwb] S1 UPDATE`): that is the session ranging with the
# other phone. Logs default to /tmp/someones-home-one-phone/phone.log (A) and phoneB.log (B), which
# run.sh links to the latest run; UWB_OUT moves them.
#
# Every window is also appended to $OUT/uwb-samples.txt, so the night's numbers are in one file.
set -o pipefail

OUT="${UWB_OUT:-${ONE_PHONE_OUT:-/tmp/someones-home-one-phone}}"
A="$OUT/phone.log"
B="$OUT/phoneB.log"

summarise() {
    # summarise <seconds or 0> <label> <name> <file> <from-line> [<name> <file> <from-line>]…
    python3 -I - "$@" <<'EOF'
import re, sys

secs = float(sys.argv[1])
label = sys.argv[2]
rest = sys.argv[3:]

UPDATE = re.compile(r'\[uwb\] S1 UPDATE t=([0-9.]+) d=(\S+) dir=(\S+) az=(\S+) hang=(\S+) vert=(\S+)')
EVENT = re.compile(r'\[uwb\] (S[0-9]+ (?:SUSPENDED|SUSPENSION-ENDED|INVALIDATED|REMOVED|NOT RESTARTING|RUN|STARTED|TOKEN|PEER TOKEN \S+|CONVERGENCE)|APP \S+|MARK \d+)(.*)')

def pct(xs, p):
    if not xs:
        return None
    k = (len(xs) - 1) * p
    lo, hi = int(k), min(int(k) + 1, len(xs) - 1)
    return xs[lo] + (xs[hi] - xs[lo]) * (k - lo)

def f(v, unit='', nd=3):
    return '-' if v is None else f'{v:.{nd}f}{unit}'

print(f'== {label}' + (f' ({secs:g}s)' if secs else '') + ' ==')
for i in range(0, len(rest), 3):
    name, path, start = rest[i], rest[i + 1], int(rest[i + 2])
    try:
        lines = open(path, errors='replace').read().splitlines()[start:]
    except OSError as e:
        print(f'{name}: cannot read {path}: {e}')
        continue
    ts, ds, azs, nod, nodir, events = [], [], [], 0, 0, []
    last_d, resumed = None, None
    for line in lines:
        m = UPDATE.search(line)
        if m:
            t, d, dr, az = float(m.group(1)), m.group(2), m.group(3), m.group(4)
            ts.append(t)
            if d == 'nil':
                nod += 1
            else:
                ds.append(float(d))
                last_d = d
            if resumed is not None:
                # The first reading after the session or the app came back: how long it took.
                events.append((m.group(1), f'first reading after {resumed[1]}, '
                                           f'{t - float(resumed[0]):.0f} ms later (d={d})'))
                resumed = None
            if dr == 'nil':
                nodir += 1
            if az != 'nil':
                azs.append(float(az))
            continue
        e = EVENT.search(line)
        if e and not e.group(1).endswith('TOKEN'):
            tm = re.search(r't=([0-9.]+)', line)
            what = re.sub(r'\s*t=[0-9.]+', '', e.group(1) + e.group(2)).strip()
            # The last distance before a MARK, or before the app lost the foreground to something
            # (NameDrop's sheet, a lock): the reading at that moment.
            if what.startswith('MARK') or 'RESIGN-ACTIVE' in what or 'LOCKING' in what:
                what += f' (last reading before it: d={last_d or "none yet"})'
            events.append((tm.group(1) if tm else '?', what))
            if tm and ('SUSPENSION-ENDED' in what or what.startswith('APP ACTIVE')):
                resumed = (tm.group(1), what.split(' (')[0])
    n = len(ts)
    if n == 0:
        print(f'{name}: NO READINGS in this window ({len(lines)} new log lines)')
    else:
        ds.sort(); azs.sort()
        span = (ts[-1] - ts[0]) / 1000.0
        gaps = sorted(b - a for a, b in zip(ts, ts[1:]))
        rate_window = f'{n / secs:.1f}/s over the window, ' if secs else ''
        rate_span = f'{(n - 1) / span:.1f}/s first-to-last' if span > 0 else 'one reading'
        print(f'{name}: n={n} · {rate_window}{rate_span} · longest gap {f(gaps[-1] if gaps else None, "ms", 0)}'
              f' · median gap {f(pct(gaps, 0.5), "ms", 0)} · readings t={ts[0]:.0f}..{ts[-1]:.0f}')
        print(f'    distance m: median={f(pct(ds, .5))} nearest={f(ds[0] if ds else None)} '
              f'farthest={f(ds[-1] if ds else None)} p10={f(pct(ds, .1))} p90={f(pct(ds, .9))} '
              f'· no distance in {nod}/{n}')
        print(f'    direction: in {n - nodir}/{n} ({100.0 * (n - nodir) / n:.0f}%) '
              f'· azimuth median={f(pct(azs, .5), "°", 1)} p10={f(pct(azs, .1), "°", 1)} p90={f(pct(azs, .9), "°", 1)}')
    for t, what in events:
        print(f'    event t={t}: {what}')
EOF
}

if [ "${1:-}" = "--file" ]; then
    shift
    [ $# -gt 0 ] || { echo "usage: $0 --file <log> [<log>…]" >&2; exit 64; }
    args=()
    for f in "$@"; do args+=("$(basename "$f")" "$f" 0); done
    summarise 0 "whole files" "${args[@]}"
    exit $?
fi

label="${1:-}"
secs="${2:-15}"
[ -n "$label" ] || { sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'; exit 64; }
case "$secs" in ''|*[!0-9]*) echo "seconds must be a whole number" >&2; exit 64 ;; esac
for f in "$A" "$B"; do [ -e "$f" ] || { echo "no log at $f — is run.sh start running?" >&2; exit 1; }; done

a0=$(wc -l <"$A"); b0=$(wc -l <"$B")
echo "sampling '$label' for ${secs}s…" >&2
python3 -c "import time; time.sleep($secs)"
summarise "$secs" "$label" "A sees B" "$A" "$a0" "B sees A" "$B" "$b0" | tee -a "$OUT/uwb-samples.txt"
