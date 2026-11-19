#!/usr/bin/env bash
# Summarise a labelled window of the UWB spike's readings, from every phone's log.
#
#   tools/uwb-spike/sample.sh <label> [seconds]        live: wait N seconds (default 15), then
#                                                      summarise what the phones logged meanwhile
#   tools/uwb-spike/sample.sh --file <log> [<log>…]    after the fact: summarise whole log files
#
# Per direction (what one phone measured of one other): the reading count and rate (updates per
# second over the window, and between the first and last reading), the distance (median, nearest,
# farthest, 10th and 90th percentile), how many readings had no distance or no direction, the median
# azimuth when there was one, the longest gap between readings, and every session or app event
# (suspension, invalidation, removal, lock, background, MARK, LIMIT) in the window with its time.
#
# Two phones: only session 1's readings are counted (`[uwb] S1 UPDATE`), the session ranging with
# the other phone, and the output is what it always was. Three phones: every reading that ends in
# `peer=<letter>` is counted under that peer, so A sees B, A sees C, B sees A and so on come out
# separately, and each phone gets a total across its peers. Logs default to
# /tmp/someones-home-one-phone/phone.log (A), phoneB.log (B) and, when the latest run had three
# phones, phoneC.log (C), which run.sh links to the latest run; UWB_OUT moves them.
#
# Every window is also appended to $OUT/uwb-samples.txt, so the night's numbers are in one file.
set -o pipefail

OUT="${UWB_OUT:-${ONE_PHONE_OUT:-/tmp/someones-home-one-phone}}"
A="$OUT/phone.log"
B="$OUT/phoneB.log"
C="$OUT/phoneC.log"

summarise() {
    # summarise <seconds or 0> <label> <name> <file> <from-line> <letter or ""> [<name> <file> <from-line> <letter>]…
    python3 -I - "$@" <<'EOF'
import os, re, sys

secs = float(sys.argv[1])
label = sys.argv[2]
rest = sys.argv[3:]

# The two-phone shape, with an optional ` peer=<letter or ->` after it in a three-phone run.
UPDATE = re.compile(r'\[uwb\] S([0-9]+) UPDATE t=([0-9.]+) d=(\S+) dir=(\S+) az=(\S+) hang=(\S+) vert=(\S+)(?: peer=(\S+))?')
EVENT = re.compile(r'\[uwb\] (S[0-9]+ (?:SUSPENDED|SUSPENSION-ENDED|INVALIDATED|REMOVED|NOT RESTARTING|RUN|CONFIGURED|STARTED|TOKEN|PEER TOKEN \S+|CONVERGENCE)|APP \S+|MARK \d+|LIMIT|PROBE START)(.*)')
PEERS = re.compile(r'\[uwb\] PEERS me=([A-Z])')
PEER = re.compile(r' peer=(\S+)')

def pct(xs, p):
    if not xs:
        return None
    k = (len(xs) - 1) * p
    lo, hi = int(k), min(int(k) + 1, len(xs) - 1)
    return xs[lo] + (xs[hi] - xs[lo]) * (k - lo)

def f(v, unit='', nd=3):
    return '-' if v is None else f'{v:.{nd}f}{unit}'

class Direction:
    """What one phone measured of one other: every reading and the events that belong to it."""
    def __init__(self, resumed):
        self.ts, self.ds, self.azs, self.nod, self.nodir, self.events = [], [], [], 0, 0, []
        self.last_d, self.resumed = None, resumed

def letter_of(path):
    m = re.fullmatch(r'phone([A-Z]?)\.log', os.path.basename(path))
    return (m.group(1) or 'A') if m else None

def report(title, st):
    """The two-phone script's block, unchanged; returns (n, first-to-last rate or None)."""
    n = len(st.ts)
    if n == 0:
        print(f'{title}: NO READINGS in this window ({st.lines} new log lines)')
        return 0, None
    st.ds.sort(); st.azs.sort()
    span = (st.ts[-1] - st.ts[0]) / 1000.0
    gaps = sorted(b - a for a, b in zip(st.ts, st.ts[1:]))
    rate_window = f'{n / secs:.1f}/s over the window, ' if secs else ''
    rate_span = f'{(n - 1) / span:.1f}/s first-to-last' if span > 0 else 'one reading'
    print(f'{title}: n={n} · {rate_window}{rate_span} · longest gap {f(gaps[-1] if gaps else None, "ms", 0)}'
          f' · median gap {f(pct(gaps, 0.5), "ms", 0)} · readings t={st.ts[0]:.0f}..{st.ts[-1]:.0f}')
    print(f'    distance m: median={f(pct(st.ds, .5))} nearest={f(st.ds[0] if st.ds else None)} '
          f'farthest={f(st.ds[-1] if st.ds else None)} p10={f(pct(st.ds, .1))} p90={f(pct(st.ds, .9))} '
          f'· no distance in {st.nod}/{n}')
    print(f'    direction: in {n - st.nodir}/{n} ({100.0 * (n - st.nodir) / n:.0f}%) '
          f'· azimuth median={f(pct(st.azs, .5), "°", 1)} p10={f(pct(st.azs, .1), "°", 1)} p90={f(pct(st.azs, .9), "°", 1)}')
    return n, ((n - 1) / span if span > 0 else None)

print(f'== {label}' + (f' ({secs:g}s)' if secs else '') + ' ==')
for i in range(0, len(rest), 4):
    name, path, start, me = rest[i], rest[i + 1], int(rest[i + 2]), rest[i + 3]
    try:
        lines = open(path, errors='replace').read().splitlines()[start:]
    except OSError as e:
        print(f'{name}: cannot read {path}: {e}')
        continue
    # None is the two-phone run's one direction (session 1, no peer field); a letter is a peer.
    dirs, phone_events, seq = {}, [], 0
    pending = None  # an app-wide return no direction has read through yet
    def direction(key):
        if key not in dirs:
            dirs[key] = Direction(pending)
        return dirs[key]
    for line in lines:
        m = UPDATE.search(line)
        if m:
            session, peer = m.group(1), m.group(8)
            if peer is None and session != '1':
                continue
            if peer == '-':
                continue  # an idle session: it ranges with nobody
            st = direction(peer)
            t, d, dr, az = float(m.group(2)), m.group(3), m.group(4), m.group(5)
            st.ts.append(t)
            if d == 'nil':
                st.nod += 1
            else:
                st.ds.append(float(d))
                st.last_d = d
            if st.resumed is not None:
                # The first reading after the session or the app came back: how long it took.
                seq += 1
                st.events.append((seq, m.group(2), f'first reading after {st.resumed[1]}, '
                                                    f'{t - float(st.resumed[0]):.0f} ms later (d={d})'))
                st.resumed = None
            if dr == 'nil':
                st.nodir += 1
            if az != 'nil':
                st.azs.append(float(az))
            continue
        pm = PEERS.search(line)
        if pm and not me:
            me = pm.group(1)
        e = EVENT.search(line)
        if e and not e.group(1).endswith('TOKEN'):
            tm = re.search(r't=([0-9.]+)', line)
            pk = PEER.search(e.group(2))
            what = re.sub(r'\s*t=[0-9.]+', '', e.group(1) + PEER.sub('', e.group(2))).strip()
            # The last distance before a MARK, or before the app lost the foreground to something
            # (NameDrop's sheet, a lock): the reading at that moment.
            if what.startswith('MARK') or 'RESIGN-ACTIVE' in what or 'LOCKING' in what:
                lasts = [(k, s.last_d) for k, s in dirs.items() if k is not None]
                if lasts:
                    what += ' (last readings before it: ' + ', '.join(f'{k} d={v or "none yet"}' for k, v in lasts) + ')'
                else:
                    last_d = dirs[None].last_d if None in dirs else None
                    what += f' (last reading before it: d={last_d or "none yet"})'
            seq += 1
            event = (seq, tm.group(1) if tm else '?', what)
            back = tm and ('SUSPENSION-ENDED' in what or what.startswith('APP ACTIVE'))
            if pk and pk.group(1) != '-':
                st = direction(pk.group(1))
                st.events.append(event)
                if back:
                    st.resumed = (tm.group(1), what.split(' (')[0])
            else:
                phone_events.append(event)
                if back:
                    # Every direction this phone measures comes back with the phone.
                    pending = (tm.group(1), what.split(' (')[0])
                    for s in dirs.values():
                        s.resumed = pending
    peers = [k for k in dirs if k is not None]
    if not peers:
        # Two phones: one block, exactly as this script always printed it.
        st = dirs.get(None) or Direction(None)
        st.lines = len(lines)
        report(name, st)
        for _, t, what in sorted(st.events + phone_events):
            print(f'    event t={t}: {what}')
        continue
    who = me or letter_of(path) or name
    total_n, total_rate = 0, 0.0
    for k in sorted(peers):
        st = dirs[k]
        st.lines = len(lines)
        n, rate = report(f'{who} sees {k}', st)
        total_n += n
        total_rate += rate or 0.0
        for _, t, what in st.events:
            print(f'    event t={t}: {what}')
    rate_window = f'{total_n / secs:.1f}/s over the window, ' if secs else ''
    print(f'{who} in all, across {len(peers)} peer(s): n={total_n} · {rate_window}{total_rate:.1f}/s first-to-last, summed')
    for _, t, what in phone_events:
        print(f'    {who} event t={t}: {what}')
EOF
}

if [ "${1:-}" = "--file" ]; then
    shift
    [ $# -gt 0 ] || { echo "usage: $0 --file <log> [<log>…]" >&2; exit 64; }
    args=()
    for f in "$@"; do args+=("$(basename "$f")" "$f" 0 ""); done
    summarise 0 "whole files" "${args[@]}"
    exit $?
fi

label="${1:-}"
secs="${2:-15}"
[ -n "$label" ] || { sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'; exit 64; }
case "$secs" in ''|*[!0-9]*) echo "seconds must be a whole number" >&2; exit 64 ;; esac
for f in "$A" "$B"; do [ -e "$f" ] || { echo "no log at $f — is run.sh start running?" >&2; exit 1; }; done

if [ -e "$C" ]; then
    # Three phones (run.sh links phoneC.log only for a three-phone run).
    a0=$(wc -l <"$A"); b0=$(wc -l <"$B"); c0=$(wc -l <"$C")
    echo "sampling '$label' for ${secs}s…" >&2
    python3 -c "import time; time.sleep($secs)"
    summarise "$secs" "$label" A "$A" "$a0" A B "$B" "$b0" B C "$C" "$c0" C | tee -a "$OUT/uwb-samples.txt"
    exit $?
fi

a0=$(wc -l <"$A"); b0=$(wc -l <"$B")
echo "sampling '$label' for ${secs}s…" >&2
python3 -c "import time; time.sleep($secs)"
summarise "$secs" "$label" "A sees B" "$A" "$a0" "" "B sees A" "$B" "$b0" "" | tee -a "$OUT/uwb-samples.txt"
