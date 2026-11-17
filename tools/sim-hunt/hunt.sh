#!/usr/bin/env bash
# A whole scripted round on 3 to 6 iOS Simulators, with every seat's log and a screenshot of every
# seat at every beat the bench announces, plus one every few seconds in between.
#
#   tools/sim-hunt/hunt.sh [--seats N] [--seed S] [--out DIR] [--nodes] [--no-build] [--tail SECS]
#   tools/sim-hunt/hunt.sh stop [--out DIR]
#
# WHAT THIS IS FOR: finding what a person would find broken on a screen, the wire, the flow and the
# house's rules: stuck screens, seats that disagree, fixture text, refusals, crashes. **The
# Simulators are wire-only seats.** No BLE, torch, camera or haptics, so nothing about the radio,
# the light or a touch is ever read off this (CLAUDE.md). Seat 0 hosts and plays the bench's
# script (`RoundCheat.theRound`); the others join it and play by themselves.
#
# It uses Simulators of its own ("Hunt Seat 0" ...), created on first use, and a derived-data
# folder of its own, so it never shares a device or a build with round.sh or rehearse.sh.
#
# Output in $OUT (default /tmp/someones-home-hunt/run-<date-time>):
#   seat-K.log           everything seat K printed
#   shots/NN-BEAT--seatK.png   every seat at every BEAT line in the host's log
#   periodic/TTTT--seatK.png   every seat every $EVERY seconds
#   beats.txt            the beats, in order, with the wall second each was seen
#
# NEW ROUND: the bench presses it on the host about 14 s after the reveal (RoundCheat's
# ENDING_NANOS); the guests go back by the house's push. This keeps capturing for --tail seconds
# after the ENDING beat (default 45), so the lobby the evening comes back to is photographed on
# every seat. With LOBBY_AGAIN every seat then hands its line over again and the host presses
# LIGHTS OUT again, so the second round arming is in the logs and the last pictures (D-252), and
# every seat plays that round as it played the first; its beats are named AGAIN-<beat> (D-259).
# A second round needs about a minute after the first one's ENDING to reach its first meeting,
# so pass --tail 150 or so to photograph it.
set -o pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO" || exit 1
export PATH="$HOME/.local/share/mise/shims:$PATH"

SEATS=4
SEED=20261150
NODES=""
BUILD=1
TAIL=45
EVERY=3
OUT=""
BUNDLE_ID="home.someoneshome.app"
CONFIG=Playtest
DERIVED="${HUNT_DERIVED_DATA:-/tmp/someones-home-hunt/dd}"
HOME_NAME="${HUNT_HOME:-HUNT HOME}"
SIM_TYPE="com.apple.CoreSimulator.SimDeviceType.iPhone-17"
# Not names any fixture carries, so a sample name on a housed screen stands out.
SEAT_NAMES=(KESTREL OSPREY WREN HERON FINCH PLOVER)

cmd="round"
[ "${1:-}" = "stop" ] && { cmd="stop"; shift; }
while [ $# -gt 0 ]; do
    case "$1" in
        --seats) SEATS="$2"; shift 2 ;;
        --seed) SEED="$2"; shift 2 ;;
        --out) OUT="$2"; shift 2 ;;
        --nodes) NODES=1; shift ;;
        --no-build) BUILD=""; shift ;;
        --tail) TAIL="$2"; shift 2 ;;
        *) echo "unknown option '$1' — see the top of $0" >&2; exit 64 ;;
    esac
done
case "$SEATS" in 3|4|5|6) ;; *) echo "--seats takes 3 to 6" >&2; exit 64 ;; esac
[ -n "$OUT" ] || OUT="/tmp/someones-home-hunt/run-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT/shots" "$OUT/periodic"

say() { printf '%s\n' "$*" | tee -a "$OUT/driver.log"; }
nap() { python3 -c "import time; time.sleep($1)"; }

udid_for() {
    xcrun simctl list devices available | awk -v name="$1" -F '[()]' '$0 ~ "    " name " \\(" { print $2; exit }'
}

UDIDS=()
for ((i = 0; i < SEATS; i++)); do
    u="$(udid_for "Hunt Seat $i")"
    if [ -z "$u" ] && [ "$cmd" = "round" ]; then
        runtime="$(xcrun simctl list runtimes -j | python3 -c "
import json, sys
r = [x for x in json.load(sys.stdin)['runtimes'] if x['platform'] == 'iOS' and x['isAvailable']]
print(sorted(r, key=lambda x: [int(n) for n in x['version'].split('.')])[-1]['identifier'] if r else '')
")"
        u="$(xcrun simctl create "Hunt Seat $i" "$SIM_TYPE" "$runtime")" || { echo "could not create a simulator" >&2; exit 1; }
        say "created 'Hunt Seat $i' ($u)"
    fi
    [ -n "$u" ] && UDIDS+=("$u")
done

stop_all() {
    [ -f "$OUT/pids" ] && while read -r p; do kill "$p" 2>/dev/null || true; done <"$OUT/pids"
    for u in "${UDIDS[@]}"; do
        xcrun simctl terminate "$u" "$BUNDLE_ID" 2>/dev/null || true
        xcrun simctl shutdown "$u" 2>/dev/null || true
    done
    say "stopped: the app and every Hunt Seat simulator"
}
if [ "$cmd" = "stop" ]; then stop_all; exit 0; fi

say "=== HUNT · $SEATS SEATS · SEED $SEED · $OUT ==="
if [ -n "$BUILD" ]; then
    say "=== BUILDING THE PLAYTEST APP FOR THE SIMULATOR ==="
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme SomeonesHome -configuration "$CONFIG" \
        -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
        -derivedDataPath "$DERIVED" build >"$OUT/build.log" 2>&1 \
        || { tail -25 "$OUT/build.log" >&2; say "THE BUILD FAILED — $OUT/build.log"; exit 1; }
fi
APP="$DERIVED/Build/Products/$CONFIG-iphonesimulator/SomeonesHome.app"
find "$APP" -name 'Silkscreen-Regular.ttf' | grep -q . || { say "no pixel fonts in $APP"; exit 1; }

say "=== GRANTS AND INSTALL ==="
for u in "${UDIDS[@]}"; do xcrun simctl shutdown "$u" 2>/dev/null || true; done
for u in "${UDIDS[@]}"; do
    if [ ! -f "$HOME/Library/Developer/CoreSimulator/Devices/$u/data/Library/BulletinBoard/VersionedSectionInfo.plist" ]; then
        xcrun simctl boot "$u" 2>/dev/null || true
        xcrun simctl bootstatus "$u" -b >/dev/null
        xcrun simctl shutdown "$u" 2>/dev/null || true
    fi
done
python3 "$REPO/sim-grants.py" notifications "${UDIDS[@]}" | tee -a "$OUT/driver.log"
for u in "${UDIDS[@]}"; do xcrun simctl boot "$u" 2>/dev/null || true; done
for u in "${UDIDS[@]}"; do
    xcrun simctl bootstatus "$u" -b >/dev/null
    xcrun simctl terminate "$u" "$BUNDLE_ID" 2>/dev/null || true
    xcrun simctl install "$u" "$APP" || { say "install failed on $u"; exit 1; }
done
python3 "$REPO/sim-grants.py" privacy "${UDIDS[@]}" | tee -a "$OUT/driver.log"
printf '%s\n' "${UDIDS[@]}" >"$OUT/udids.txt"

: >"$OUT/pids"
: >"$OUT/beats.txt"
launch_seat() {
    local i="$1" u="${UDIDS[$1]}"
    local env=(
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_NAME=${SEAT_NAMES[$i]}"
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_LINE=SEAT $i WROTE THIS AND NOBODY MAY SEE IT"
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_FORGET=1"
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_AGAIN=1"
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND=1"
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND_SEED=$SEED"
    )
    if [ "$i" -eq 0 ]; then
        env+=("SIMCTL_CHILD_SOMEONES_HOME_LOBBY_HOST=$HOME_NAME"
              "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_INSIDERS=0"
              "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_LIGHTS_OUT=1"
              "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_SEATS=$SEATS")
    else
        env+=("SIMCTL_CHILD_SOMEONES_HOME_LOBBY_JOIN=$HOME_NAME")
    fi
    # Alternate Egress nodes so a crisis has walkers at both ends (rehearse.sh's SEAT_NODES).
    [ -n "$NODES" ] && env+=("SIMCTL_CHILD_SOMEONES_HOME_ROUND_NODE=$((i % 2))")
    env "${env[@]}" xcrun simctl launch --console-pty "$u" "$BUNDLE_ID" >>"$OUT/seat-$i.log" 2>&1 &
    echo $! >>"$OUT/pids"
}

# Every seat at once. Waits on the screenshots' own pids: a bare `wait` would also wait on the
# `simctl launch` processes started above, which never end.
shoot() {
    local dir="$1" tag="$2" i pids=()
    for i in "${!UDIDS[@]}"; do
        xcrun simctl io "${UDIDS[$i]}" screenshot "$OUT/$dir/$tag--seat$i.png" >/dev/null 2>&1 &
        pids+=($!)
    done
    wait "${pids[@]}"
}

say "=== LAUNCHING THE HOST, THEN $((SEATS - 1)) SEATS ==="
launch_seat 0
nap 6
for ((i = 1; i < SEATS; i++)); do launch_seat "$i"; nap 2; done
trap 'echo; stop_all; exit 0' INT TERM

host="$OUT/seat-0.log"
start=$(date +%s)
n=0
seen=0
ended_at=""
last_periodic=0
lobby_shot=""
while true; do
    now=$(date +%s)
    # The host presses LIGHTS OUT the moment the last seat is in, so the lobby is taken one seat
    # early: the room nearly full, the button live.
    if [ -z "$lobby_shot" ] && grep -q "\[lobby-bench\] $((SEATS - 1)) JOINED" "$host" 2>/dev/null; then
        shoot shots "00-LOBBY"; lobby_shot=1; say "shot: the lobby"
    fi
    count=$(grep -c "BEAT [A-Z-]* — " "$host" 2>/dev/null)
    count=${count:-0}
    while [ "$seen" -lt "$count" ]; do
        seen=$((seen + 1))
        name="$(grep -o "BEAT [A-Z-]* — " "$host" | sed -n "${seen}p" | awk '{print $2}')"
        n=$((n + 1))
        tag="$(printf '%02d-%s' "$n" "$name")"
        printf '%s\t%s\n' "$name" "$((now - start))" >>"$OUT/beats.txt"
        shoot shots "$tag"
        say "beat $name (+$((now - start))s)"
        [ "$name" = "ENDING" ] && [ -z "$ended_at" ] && ended_at=$now
    done
    if [ $((now - last_periodic)) -ge "$EVERY" ]; then
        shoot periodic "$(printf '%04d' $((now - start)))"
        last_periodic=$now
    fi
    if [ -n "$ended_at" ] && [ $((now - ended_at)) -ge "$TAIL" ]; then
        shoot shots "99-AFTER-NEW-ROUND"
        say "the ending plus ${TAIL}s: done. The Simulators are still up; stop with: $0 stop --seats $SEATS --out $OUT"
        break
    fi
    if [ $((now - start)) -gt 1500 ]; then
        say "TIMED OUT after 25 minutes without an ending + ${TAIL}s"
        shoot shots "99-TIMEOUT"
        break
    fi
    nap 1
done
