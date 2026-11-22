#!/usr/bin/env bash
# Five seats, one house, a whole round — and a picture of every beat.
#
#   ./rehearse.sh [run-name] [seed]
#
# WHAT THIS IS. P3 played the first complete round in this game's existence with the authority on a
# physical phone and four Mac instances joining it. This is the same evening with **no phone in it
# at all**: five iOS Simulator instances on one Mac, one of them hosting, all five finding each
# other over the Mac's own loopback and playing the round over the real websocket transport.
#
# WHAT A SIMULATOR CANNOT DO IS STILL TRUE. No BLE, no torch, no camera, no haptics — so nothing
# about contact, light or a buzz may ever be signed off from here, and this script signs off none
# of it. What it verifies is the two things the simulator does carry honestly: **the wire**, which
# is ordinary TCP and identical to the wire between two phones, and **the screen**, which is the
# real composition at real device aspect. Every stand-in in the round names itself in the log.
#
# THE OUTPUT IS THREE THINGS, all under $OUT:
#   seat-*.log     every line each of the five processes printed, one file per seat
#   shots/*.png    a screenshot of all five seats at every beat the script announced
#   beats.txt      the beats, in order, with the tick each was seen at
#
# The assertions are NOT here. `rehearse-check.py` reads the logs afterwards and says what the five
# screens were at each beat — a script that both drives a round and judges it would agree with
# itself. See there.
# `set -u` is deliberately NOT on: macOS ships bash 3.2, where an empty array element and an
# expansion of an empty array both trip it, and two of the tables below are deliberately sparse —
# not every seat walks to a node and only one presses STOP NOW.
set -o pipefail
# **Run this from a COPY, never from the file you are editing.** Bash reads a script by byte offset
# as it goes, so editing a long-running driver makes the already-started instance resume mid-token
# — P3 recorded this and it happened again here, as `line 205: ALLY: command not found` out of a
# `wait_beat TALLY` that was correct on disk. $REPO is how a copy in /tmp still finds the repo.
#
#   cp rehearse.sh /tmp/r.sh && REPO=$PWD bash /tmp/r.sh run1
cd "${REPO:-$(dirname "$0")}"

# The Bash tool's shell does not pick up mise; the user's terminal does.
export PATH="$HOME/.local/share/mise/shims:$PATH"

RUN="${1:-run1}"
SEED="${2:-20260822}"
VARIANT=playtest
CONFIG=Playtest
BUNDLE_ID="home.someoneshome.app"
DERIVED="${APP_DERIVED_DATA:-/tmp/someones-home-dd}"
OUT="/tmp/someones-home-rehearsal/$RUN"
HOME_NAME="THE BUNGALOW"

# THE FIVE SEATS, pinned by device name rather than by whatever `simctl list booted` happens to
# print first. A rehearsal whose seat order changes between runs is a rehearsal whose per-seat
# tables cannot be compared, and comparing them across two seeds is half of what this is for.
#
# **Seats of its own** (D-314), "Rehearsal Seat 0" to "Rehearsal Seat 4", created on first use the
# way tools/sim-hunt/hunt.sh creates its own. It asked for the stock "iPhone 17 Pro" to "iPhone 17"
# by name, so a Mac without an "iPhone 17 Pro Max" stopped before building, and "iPhone 17 Pro" is
# the one Gradle's iOS test runs boot, so two runs shared a device. Each seat keeps a different
# device type for the five aspects, and falls back to an iPhone 17 where this Xcode has no such type.
SEAT_DEVICES=("Rehearsal Seat 0" "Rehearsal Seat 1" "Rehearsal Seat 2" "Rehearsal Seat 3" "Rehearsal Seat 4")
SEAT_TYPES=(iPhone-17-Pro iPhone-17-Pro-Max iPhone-17e iPhone-Air iPhone-17)
SEAT_NAMES=(HOSTPHONE MARCUS PRIYA ELLIOT NOOR)
# Which published Egress node each bench walks to when the house catches fire, ALTERNATING so that
# no node's walkers are adjacent in the seat order. A Revoke and a Restrain take two people out
# before the Egress and nothing here can predict which two — and a first pass that paired seats 0,1
# on one node and 2,3 on the other found both of one node's walkers on the couch and a crisis with
# nobody at one end of it. Everybody walks; a seat that is out is refused in silence.
SEAT_NODES=(0 1 0 1 0)
# One seat presses STOP NOW on the first work a scan opens instead of finishing it.
SEAT_STOPS=("" "" "" "" 1)
# The seat that gets stopped and relaunched mid-shift, to prove the re-assertion (D-097).
GAP_SEAT=3

mkdir -p "$OUT/shots"
: > "$OUT/beats.txt"

say() { printf '%s\n' "$*" | tee -a "$OUT/driver.log"; }

udid_for() {
    xcrun simctl list devices available \
        | awk -v name="$1" -F '[()]' '$0 ~ "    " name " \\(" { print $2; exit }'
}

# The newest available iOS runtime, for a seat that has to be created.
newest_runtime() {
    xcrun simctl list runtimes -j | python3 -c "
import json, sys
r = [x for x in json.load(sys.stdin)['runtimes'] if x['platform'] == 'iOS' and x['isAvailable']]
print(sorted(r, key=lambda x: [int(n) for n in x['version'].split('.')])[-1]['identifier'] if r else '')
"
}

UDIDS=()
for i in "${!SEAT_DEVICES[@]}"; do
    dev="${SEAT_DEVICES[$i]}"
    u="$(udid_for "$dev")"
    if [ -z "$u" ]; then
        runtime="$(newest_runtime)"
        [ -n "$runtime" ] || { echo "no available iOS simulator runtime" >&2; exit 1; }
        u="$(xcrun simctl create "$dev" "com.apple.CoreSimulator.SimDeviceType.${SEAT_TYPES[$i]}" "$runtime" 2>/dev/null)" \
            || u="$(xcrun simctl create "$dev" "com.apple.CoreSimulator.SimDeviceType.iPhone-17" "$runtime")" \
            || { echo "could not create a simulator named '$dev'" >&2; exit 1; }
        echo "created '$dev' ($u)"
    fi
    UDIDS+=("$u")
done

say "=== BUILDING ($VARIANT) ==="
xcodebuild -project iosApp/iosApp.xcodeproj -scheme SomeonesHome -configuration "$CONFIG" \
    -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath "$DERIVED" build >"$OUT/build.log" 2>&1 || {
        echo "the build failed — see $OUT/build.log" >&2; tail -30 "$OUT/build.log" >&2; exit 1; }

APP="$DERIVED/Build/Products/$CONFIG-iphonesimulator/SomeonesHome.app"
# The pixel fonts ARE the interface, and a bundle without them installs, launches and fails on its
# first frame with BUILD SUCCEEDED printed above it. run-app.sh guards the same thing.
find "$APP" -name 'Silkscreen-Regular.ttf' | grep -q . || {
    echo "the pixel fonts are not in the bundle; the app will fail on its first frame" >&2; exit 1; }

# THE GRANTS A HOST WOULD HAVE GIVEN, given out of band — see sim-grants.py for the whole argument.
# D-161's gate refuses a seat to a phone the OS has not answered for, and a simulator has no thumb
# for a modal. This is `seedTheHome`'s sibling: the walk is stood in for and only the walk, and the
# app still asks the OS itself and still prints what it hears. The `simctl privacy` grant goes after
# the install, because it needs a booted device with the app on it. There is no notification half
# any more (D-326: no NOTIFICATIONS row); every seat still boots fresh, as it did.
say "=== THE GRANTS A HOST WOULD HAVE GIVEN ==="
for u in "${UDIDS[@]}"; do xcrun simctl shutdown "$u" 2>/dev/null || true; done

say "=== BOOTING AND INSTALLING ON ${#UDIDS[@]} SEATS ==="
for u in "${UDIDS[@]}"; do
    xcrun simctl boot "$u" 2>/dev/null || true
done
for u in "${UDIDS[@]}"; do
    xcrun simctl bootstatus "$u" -b >/dev/null
    xcrun simctl terminate "$u" "$BUNDLE_ID" 2>/dev/null || true
    xcrun simctl install "$u" "$APP"
done
python3 "$PWD/sim-grants.py" privacy "${UDIDS[@]}" | tee -a "$OUT/driver.log"

launch_seat() {
    local i="$1"
    local u="${UDIDS[$i]}"
    local log="$OUT/seat-$i.log"
    local env=(
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_NAME=${SEAT_NAMES[$i]}"
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_LINE=SEAT ${i} WROTE THIS AND NOBODY MAY SEE IT"
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_FORGET=1"
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND=1"
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND_SEED=$SEED"
    )
    if [ "$i" -eq 0 ]; then
        env+=("SIMCTL_CHILD_SOMEONES_HOME_LOBBY_HOST=$HOME_NAME")
        env+=("SIMCTL_CHILD_SOMEONES_HOME_LOBBY_INSIDERS=0")
        env+=("SIMCTL_CHILD_SOMEONES_HOME_LOBBY_LIGHTS_OUT=1")
        # LIGHTS OUT once all five are seated (D-314). Without it the host pressed as soon as the
        # button was live, at three, and seats 3 and 4 were refused at the door: ROUND IN PROGRESS.
        env+=("SIMCTL_CHILD_SOMEONES_HOME_LOBBY_SEATS=${#UDIDS[@]}")
    else
        env+=("SIMCTL_CHILD_SOMEONES_HOME_LOBBY_JOIN=$HOME_NAME")
    fi
    [ -n "${SEAT_NODES[$i]}" ] && env+=("SIMCTL_CHILD_SOMEONES_HOME_ROUND_NODE=${SEAT_NODES[$i]}")
    [ -n "${SEAT_STOPS[$i]}" ] && env+=("SIMCTL_CHILD_SOMEONES_HOME_ROUND_STOP=1")
    # `--console-pty` streams the process's stdout here. APPENDED, never truncated: P3 lost the
    # markers of its own gap injection because `launch > file` holds a byte offset and overwrites
    # whatever an `echo >> file` wrote in the meantime. One writer per file is the rule; this is
    # the second-best thing, which is that both of them append.
    env "${env[@]}" xcrun simctl launch --console-pty "$u" "$BUNDLE_ID" >>"$log" 2>&1 &
    echo $! >> "$OUT/pids"
}

: > "$OUT/pids"
say "=== LAUNCHING THE HOST ==="
launch_seat 0
# The house has to be on the air before anybody looks for it. A client that starts first simply
# sits on JOIN saying STILL LOOKING, which is honest and slow.
sleep 6
say "=== LAUNCHING FOUR CLIENTS ==="
for i in 1 2 3 4; do launch_seat "$i"; sleep 2; done

shoot() {
    local tag="$1"
    for i in "${!UDIDS[@]}"; do
        xcrun simctl io "${UDIDS[$i]}" screenshot "$OUT/shots/$tag--seat$i.png" >/dev/null 2>&1 || true
    done
}

# THE BEATS, read off the host's own log. The script does not decide when a beat happened — the
# bench announces one when the round reaches it, and this waits for the announcement and takes the
# picture. A driver that screenshotted on a timer would be photographing its own guesses.
wait_beat() {
    local name="$1" limit="${2:-240}" waited=0
    while [ "$waited" -lt "$limit" ]; do
        if grep -q "BEAT $name " "$OUT/seat-0.log" 2>/dev/null; then
            printf '%s\t%s\n' "$name" "$(date +%s)" >> "$OUT/beats.txt"
            say "beat $name (+${waited}s)"
            shoot "$name"
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done
    say "beat $name NEVER ARRIVED after ${limit}s"
    printf '%s\tMISSING\n' "$name" >> "$OUT/beats.txt"
    shoot "$name-missing"
    return 1
}

say "=== THE LOBBY ==="
# The opening banner is on the arming push, so the first picture is the lobby with five lines in.
for _ in $(seq 1 60); do
    grep -q "5 JOINED" "$OUT/seat-0.log" 2>/dev/null && break
    sleep 2
done
shoot "00-lobby"

wait_beat ARMED 120
wait_beat WORK-ORDER 90

# THE GAP INJECTION, mid-shift, on one client (D-097, G3). Stopped and relaunched WITHOUT forget, so
# it presents its stored token, resumes that seat, and asks the house for everything said while it
# was gone. The whole outbox comes back from sequence 1.
say "=== GAP INJECTION ON SEAT $GAP_SEAT ==="
xcrun simctl terminate "${UDIDS[$GAP_SEAT]}" "$BUNDLE_ID" 2>/dev/null || true
echo "[driver] SEAT $GAP_SEAT WAS STOPPED MID-SHIFT" >> "$OUT/seat-$GAP_SEAT.log"
sleep 4
(
    i="$GAP_SEAT"; u="${UDIDS[$i]}"
    env "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_NAME=${SEAT_NAMES[$i]}" \
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_JOIN=$HOME_NAME" \
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND=1" \
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND_SEED=$SEED" \
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND_NODE=${SEAT_NODES[$i]}" \
        xcrun simctl launch --console-pty "$u" "$BUNDLE_ID" >>"$OUT/seat-$i.log" 2>&1
) &
echo $! >> "$OUT/pids"

wait_beat SHIFT-DONE 120
wait_beat REVOKED 180
wait_beat MEETING-RINGING 120
# CHECKIN, GATE-CLOSED, DISCUSSION, VOTE and TALLY are the meeting's own phases, announced by the
# bench as the HOUSE opens each one — so this list is the meeting's shape rather than the script's
# expectation of it. A phase the house never opens simply never gets a picture, and the timeouts are
# short because a beat that has not arrived within a phase's own length is a beat that will not.
# CHECKIN and GATE-CLOSED are announced by the bench but are NOT waited for here. On loopback five
# phones check in inside two seconds, so the gate opens and closes between two samples — the ring is
# photographed at MEETING-RINGING and the talk at DISCUSSION, and there is no separate instant at
# which a picture of the gate would show anything the first of those does not.
wait_beat DISCUSSION 150
wait_beat VOTE 120
wait_beat TALLY 120
wait_beat LIGHTS-OUT-AGAIN 150
# The jam before the crisis, and the order is the balance table's: every per-seat clock comes out of
# a meeting at half (D-184), and half of Static's cooldown is shorter than half of the Egress's.
wait_beat STATIC 240
wait_beat EGRESS-OPEN 240
wait_beat EGRESS-RESOLVED 180
# The Residents' bar is expected to be out of reach after a Revoke and a Restrain (E-P3-4 / E-L3-3,
# not fixed), so the round is expected to end on D-131's fourth route — an Egress nobody contains.
# Waited for rather than required: if the meter ever does become winnable this beat simply will not
# happen, and that is a result rather than a failure.
# A SHORT wait, and it is short on purpose. The round usually ends on the FIRST Egress — the one
# fired during the jam's act, which nobody contained — so there is often no second one to announce,
# and a long wait here delays the ending's own picture until every phone is back in the lobby.
wait_beat EGRESS-UNCONTAINED 45 || say "the round ended on the first Egress, not a second"
wait_beat ENDING 300

# Five phones stand on the reveal for a while before anybody hands the evening back — see
# RoundCheat's ENDING_NANOS — so this waits out that stand before photographing the wiped lobby.
sleep 22
shoot "99-new-round"

say "=== TERMINATING ==="
for u in "${UDIDS[@]}"; do xcrun simctl terminate "$u" "$BUNDLE_ID" 2>/dev/null || true; done
sleep 2
# The seats are this script's own, so it shuts them down too (D-314). REHEARSE_KEEP_SEATS=1 keeps
# them up for a look at the glass afterwards.
if [ -z "${REHEARSE_KEEP_SEATS:-}" ]; then
    for u in "${UDIDS[@]}"; do xcrun simctl shutdown "$u" 2>/dev/null || true; done
    say "the five Rehearsal Seat simulators are shut down"
fi
say "logs and shots: $OUT"
