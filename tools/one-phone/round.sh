#!/usr/bin/env bash
# One phone, Simulator filler seats, one round: the contact radio checked with a single iPhone.
#
#   tools/one-phone/round.sh check                  read-only: signing team, the phone, the sims, the peer
#   tools/one-phone/round.sh install                build the Playtest app and install it on the phone
#   tools/one-phone/round.sh start [options]        install, then host a round on the phone + 3 sims
#   tools/one-phone/round.sh stop                   end it: the app on the phone, the sims, the logs' writers
#
#   start options:
#     --seats 3|4         seats in the round, the phone included (default 4: the phone and 3 sims)
#     --no-install        skip the phone build and install (it is already on the phone)
#     --auto-lights-out   let the bench press LIGHTS OUT once every seat is in (default: you press it)
#     --by-hand           the phone plays no script: you arm, you call meetings (the sims still play)
#     --no-phone          dry run of the Mac half only: nothing on the phone. The filler seats are
#                         launched and must keep looking without giving up; then one more Simulator
#                         stands in for the phone as host, and the round is played to its first
#                         meeting: act 3's Revoke must land on seat 1 and the round must not end
#                         before the meeting
#
# THE PHONE HOSTS. It plays seat 0 and runs the house. The Simulators join it over mDNS on the Mac's
# network, as seats 1 to 3, and play by themselves (SOMEONES_HOME_ROUND=1): they walk their work
# orders and check in at every meeting. **They are wire-only filler.** A Simulator has no Bluetooth,
# so its radio reports Unsupported, which the house records as a device fault and names in no notice
# (D-187, D-223). Nothing about the radio may be signed off from a Simulator.
#
# FOUR SEATS BY DEFAULT (D-228). At three seats one Insider and one Revoke leave one plain Resident
# against one Insider, which is parity: the round ends at the Revoke, so the report and the meeting
# notice cannot be reached. At four the Revoke leaves two against one and the round goes on.
#
# THE SEED IS FIXED, so the Mac's BLE peer can derive any seat's token (tools/ble-peer). 20261150
# deals the Insider role to seat 0, the phone, at three seats and at four, so the bench's scripted
# Revoke (`RoundCheat.theRevoke`) lands on seat 1, a Simulator; `OnePhoneVectorsTest` pins that. The
# Mac then advertises as seat 1 and the phone's report of it is the check. (20261116, the first
# seed, dealt seat 2 at four seats, which would have Revoked the phone itself and stopped its scan.)
#
# Logs land in $OUT (default /tmp/someones-home-one-phone). Every `start` (and `install`) gets a run
# directory of its own, $OUT/runs/<date-time>/, and no later run deletes or overwrites it:
# phone.log, seat-1.log, seat-2.log and driver.log in $OUT are links to the latest run's, and
# $OUT/latest links to its directory. `start` stays in the foreground following the phone's lines
# that matter; Ctrl-C runs `stop`, which stops processes and touches no log.
#
# Signing: the Team ID is SOMEONES_HOME_DEVELOPMENT_TEAM, or else read off the one valid "Apple
# Development" certificate's OU, which is where run-app.sh says the Team ID lives. With no
# certificate, or several teams, this stops and says what to do. It never guesses one.
set -o pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO" || exit 1
# The Bash tool's shell does not pick up mise; the owner's terminal does. Harmless either way.
export PATH="$HOME/.local/share/mise/shims:$PATH"

PHONE_NAME="${APP_DEVICE_NAME:-vadManuel-Phone}"
SEED="${SOMEONES_HOME_ROUND_SEED:-20261150}"
BUNDLE_ID="home.someoneshome.app"
CONFIG=Playtest
DERIVED="${APP_DERIVED_DATA:-/tmp/someones-home-dd}"
OUT="${ONE_PHONE_OUT:-/tmp/someones-home-one-phone}"
# A home already stored on the phone under this name is hosted as it is and never overwritten.
HOME_NAME="${ONE_PHONE_HOME:-ONE PHONE}"
# Seats 1 to 3, in that order. A run of three seats uses the first two.
SIM_DEVICES=("iPhone 17" "iPhone 17 Pro" "iPhone 17e")
SIM_TYPES=("com.apple.CoreSimulator.SimDeviceType.iPhone-17" "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro" "com.apple.CoreSimulator.SimDeviceType.iPhone-17e")
# --no-phone only: the Simulator that stands in for the phone as host, seat 0.
STAND_IN_DEVICE="iPhone Air"
STAND_IN_TYPE="com.apple.CoreSimulator.SimDeviceType.iPhone-Air"
SEAT_NAMES=(PHONE SIMA SIMB SIMC)
# Seats in the round, the phone included. `start --seats` sets it.
SEATS=4

mkdir -p "$OUT"

# Where `say` writes. `start` points it at /dev/null while it stops the previous run's processes.
DRIVER_LOG="$OUT/driver.log"
say() { printf '%s\n' "$*" | tee -a "$DRIVER_LOG"; }
fail() { printf '\n%s\n' "$*" | tee -a "$DRIVER_LOG" >&2; exit 1; }
nap() { python3 -c "import time; time.sleep($1)"; }

# ---------------------------------------------------------------------------------------------
# Every run's logs, kept

RUN=""
# The followed logs, linked from $OUT to the latest run's own copies.
# seat-0.log is the --no-phone stand-in host's; with the phone it stays empty.
LINKED_LOGS=(phone.log seat-0.log seat-1.log seat-2.log seat-3.log driver.log)
# Loose files an older version of this script wrote straight into $OUT and overwrote every run.
LOOSE_LOGS=(phone.log seat-1.log seat-2.log driver.log build-phone.log build-sims.log install-phone.log grants.txt)

begin_run() {
    local stamp f moved=""
    stamp="$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$OUT/runs"
    # Logs an older script left loose are moved into a directory of their own, never removed.
    for f in "${LOOSE_LOGS[@]}"; do
        if [ -f "$OUT/$f" ] && [ ! -L "$OUT/$f" ]; then
            mkdir -p "$OUT/runs/before-$stamp"
            mv "$OUT/$f" "$OUT/runs/before-$stamp/$f"
            moved=1
        fi
    done
    RUN="$OUT/runs/$stamp"
    # Two runs in one second keep both: the second gets a suffix.
    local n=1
    while [ -e "$RUN" ]; do RUN="$OUT/runs/$stamp-$n"; n=$((n + 1)); done
    mkdir -p "$RUN"
    for f in "${LINKED_LOGS[@]}"; do
        : >"$RUN/$f"
        ln -sfn "runs/$(basename "$RUN")/$f" "$OUT/$f"
    done
    ln -sfn "runs/$(basename "$RUN")" "$OUT/latest"
    DRIVER_LOG="$OUT/driver.log"
    [ -z "$moved" ] || say "an earlier run's loose logs were moved to $OUT/runs/before-$stamp"
}

# ---------------------------------------------------------------------------------------------
# The phone

PHONE_CORE=""
PHONE_UDID=""
find_phone() {
    local json
    json="$(mktemp "$OUT/devices.XXXXXX")"
    xcrun devicectl list devices --json-output "$json" >/dev/null 2>&1 || true
    local found
    found="$(python3 - "$json" "$PHONE_NAME" <<'EOF'
import json, sys
d = json.load(open(sys.argv[1]))
for x in d.get('result', {}).get('devices', []):
    props = x.get('deviceProperties', {})
    if props.get('name') != sys.argv[2]:
        continue
    tunnel = x.get('connectionProperties', {}).get('tunnelState', '-')
    print(x['identifier'], x['hardwareProperties']['udid'], tunnel, props.get('developerModeStatus', '-'))
    break
EOF
)"
    rm -f "$json"
    [ -n "$found" ] || fail "No phone named '$PHONE_NAME' is paired with this Mac. Plug it in, unlock it, and check \`xcrun devicectl list devices\`. Another phone: APP_DEVICE_NAME=<its name>."
    PHONE_CORE="$(echo "$found" | awk '{print $1}')"
    PHONE_UDID="$(echo "$found" | awk '{print $2}')"
    local tunnel devmode
    tunnel="$(echo "$found" | awk '{print $3}')"
    devmode="$(echo "$found" | awk '{print $4}')"
    say "phone: $PHONE_NAME · CoreDevice $PHONE_CORE · UDID $PHONE_UDID · link $tunnel · developer mode $devmode"
    [ "$tunnel" != "unavailable" ] || fail "'$PHONE_NAME' is paired but unreachable. Unlock it and plug it into this Mac (or put it on this Mac's Wi-Fi), then run again."
    [ "$devmode" = "enabled" ] || [ "$devmode" = "-" ] || fail "Developer Mode is off on '$PHONE_NAME': Settings → Privacy & Security → Developer Mode → on (the phone restarts)."
}

TEAM=""
find_team() {
    if [ -n "${SOMEONES_HOME_DEVELOPMENT_TEAM:-}" ]; then
        TEAM="$SOMEONES_HOME_DEVELOPMENT_TEAM"
        say "team: $TEAM (from SOMEONES_HOME_DEVELOPMENT_TEAM)"
        return
    fi
    # The Team ID is the certificate's OU, not the parenthetical in its common name (run-app.sh).
    local teams
    teams="$(security find-identity -v -p codesigning 2>/dev/null \
        | sed -n 's/.*"\(Apple Development: .*\)"$/\1/p' \
        | while read -r identity; do
            security find-certificate -c "$identity" -p 2>/dev/null | openssl x509 -noout -subject 2>/dev/null \
                | sed -n 's/.*OU *= *\([A-Z0-9]*\).*/\1/p'
        done | sort -u)"
    local count
    count="$(printf '%s' "$teams" | grep -c .)"
    if [ "$count" -eq 0 ]; then
        fail "NO SIGNING IDENTITY ON THIS MAC. The owner must: open Xcode → Settings → Accounts, add the Apple ID, select its team and press Manage Certificates → + → Apple Development. Then run this again."
    elif [ "$count" -gt 1 ]; then
        fail "SEVERAL TEAMS HAVE A DEVELOPMENT CERTIFICATE HERE: $(echo $teams). Pick one and run again with SOMEONES_HOME_DEVELOPMENT_TEAM=<team ID>."
    fi
    TEAM="$teams"
    say "team: $TEAM (the OU of this Mac's only valid Apple Development certificate)"
}

install_phone() {
    [ -n "$RUN" ] || begin_run
    find_team
    find_phone
    say "=== BUILDING THE PLAYTEST APP FOR $PHONE_NAME (a few minutes the first time) ==="
    if ! xcodebuild -project iosApp/iosApp.xcodeproj -scheme SomeonesHome -configuration "$CONFIG" \
        -destination "id=$PHONE_UDID" -derivedDataPath "$DERIVED-device" \
        DEVELOPMENT_TEAM="$TEAM" -allowProvisioningUpdates -allowProvisioningDeviceRegistration \
        build >"$RUN/build-phone.log" 2>&1; then
        tail -25 "$RUN/build-phone.log" >&2
        if grep -qiE 'signing|provisioning|team|certificate|No Account' "$RUN/build-phone.log"; then
            fail "SIGNING FAILED (full log: $RUN/build-phone.log). The owner must: open iosApp/iosApp.xcodeproj in Xcode once, select the SomeonesHome target → Signing & Capabilities, pick team $TEAM, and let Xcode register $PHONE_NAME. Then run this again."
        fi
        fail "THE PHONE BUILD FAILED — see $RUN/build-phone.log"
    fi
    local app="$DERIVED-device/Build/Products/$CONFIG-iphoneos/SomeonesHome.app"
    find "$app" -name 'Silkscreen-Regular.ttf' | grep . >/dev/null \
        || fail "the pixel fonts are not in the bundle; the app would fail on its first frame"
    say "=== INSTALLING ON $PHONE_NAME ==="
    if ! xcrun devicectl device install app --device "$PHONE_CORE" "$app" >"$RUN/install-phone.log" 2>&1; then
        cat "$RUN/install-phone.log" >&2
        fail "THE INSTALL FAILED. On the phone: unlock it; Settings → Privacy & Security → Developer Mode must be on (it restarts the phone)."
    fi
    say "installed."
}

# ---------------------------------------------------------------------------------------------
# A locked phone
#
# A host phone that locks or sleeps is suspended, and its sockets go with it: the first round-B try
# logged `Websocket handler failed … ENOTCONN` for every seat, then `WILL NOT ARM — 0 SEAT(S)`. The
# OS reports a lock only as "a passcode is needed now", so a phone with no passcode cannot be seen
# to lock from here.

# Prints yes, no, or unknown.
phone_locked() {
    local json
    json="$(mktemp "$OUT/lock.XXXXXX")"
    xcrun devicectl device info lockState --device "$PHONE_CORE" --timeout 20 --json-output "$json" >/dev/null 2>&1 || true
    python3 -c '
import json, sys
try:
    v = json.load(open(sys.argv[1])).get("result", {}).get("passcodeRequired")
except Exception:
    v = None
print("unknown" if v is None else ("yes" if v else "no"))
' "$json"
    rm -f "$json"
}

LOCKED_HELP="Unlock it, and set Settings → Display & Brightness → Auto-Lock → Never for the scripted round: a host phone that locks drops every seat. Put Auto-Lock back afterwards."

refuse_if_locked() {
    if [ "$(phone_locked)" = "yes" ]; then
        fail "THE PHONE IS LOCKED. $LOCKED_HELP Then run this again."
    fi
}

# In the background while `start` follows the log: says so, once per lock, when the phone locks.
watch_lock() {
    local was="no" now
    while true; do
        nap 15
        now="$(phone_locked)"
        if [ "$now" = "yes" ] && [ "$was" != "yes" ]; then
            say ""
            say "!!! THE PHONE IS LOCKED. The host's sockets stop with it and every seat drops."
            say "!!! $LOCKED_HELP"
            say "!!! Before LIGHTS OUT: Ctrl-C and start again."
            say ""
        elif [ "$now" = "no" ] && [ "$was" = "yes" ]; then
            say "!!! the phone is unlocked again"
        fi
        [ "$now" = "unknown" ] || was="$now"
    done
}

launch_phone() {
    refuse_if_locked
    say "=== LAUNCHING THE HOST ON $PHONE_NAME · SEED $SEED ==="
    local lights=()
    # The bench's script (`RoundCheat.theRound`) runs on the host only with ROUND set. --by-hand sets
    # ROUND_BY_HAND instead: no script, the round is the owner's, and it is still a bench launch. The
    # seed is honoured only beside one of the two (D-240): a phone launched with the seed alone, or
    # from the springboard, draws a secure one, so the README's Insider seat is never a real round's.
    local HOST_ROUND=("DEVICECTL_CHILD_SOMEONES_HOME_ROUND=1")
    [ -n "$BY_HAND" ] && HOST_ROUND=("DEVICECTL_CHILD_SOMEONES_HOME_ROUND_BY_HAND=1")
    # The bench waits for every seat, not the three that make the button live.
    [ -n "$AUTO_LIGHTS_OUT" ] && lights=("DEVICECTL_CHILD_SOMEONES_HOME_LOBBY_LIGHTS_OUT=1" "DEVICECTL_CHILD_SOMEONES_HOME_LOBBY_SEATS=$SEATS")
    # `--console` streams the app's stdout here and forwards a signal sent to devicectl to the app.
    env "DEVICECTL_CHILD_SOMEONES_HOME_LOBBY_NAME=${SEAT_NAMES[0]}" \
        "DEVICECTL_CHILD_SOMEONES_HOME_LOBBY_HOST=$HOME_NAME" \
        "DEVICECTL_CHILD_SOMEONES_HOME_LOBBY_LINE=SEAT 0 WROTE THIS AND NOBODY MAY SEE IT" \
        "DEVICECTL_CHILD_SOMEONES_HOME_LOBBY_FORGET=1" \
        "DEVICECTL_CHILD_SOMEONES_HOME_LOBBY_INSIDERS=0" \
        ${HOST_ROUND[@]+"${HOST_ROUND[@]}"} \
        "DEVICECTL_CHILD_SOMEONES_HOME_ROUND_SEED=$SEED" \
        ${lights[@]+"${lights[@]}"} \
        xcrun devicectl device process launch --console --terminate-existing \
            --device "$PHONE_CORE" "$BUNDLE_ID" >>"$OUT/phone.log" 2>&1 &
    echo $! >"$OUT/phone.pid"
    echo "$PHONE_CORE" >"$OUT/phone.core"
}

stop_phone() {
    [ -f "$OUT/phone.core" ] || return 0
    local core
    core="$(cat "$OUT/phone.core")"
    if [ -f "$OUT/phone.pid" ]; then
        kill -TERM "$(cat "$OUT/phone.pid")" 2>/dev/null && say "stopped the phone's console (the app is sent the same signal)"
    fi
    nap 2
    # And make sure: whatever is still running from the bundle on the phone is terminated by pid.
    local json pid
    json="$(mktemp "$OUT/procs.XXXXXX")"
    xcrun devicectl device info processes --device "$core" --json-output "$json" >/dev/null 2>&1 || true
    pid="$(python3 - "$json" <<'EOF' 2>/dev/null
import json, sys
d = json.load(open(sys.argv[1]))
for p in d.get('result', {}).get('runningProcesses', []):
    if 'SomeonesHome.app/' in p.get('executable', ''):
        print(p['processIdentifier'])
EOF
)"
    rm -f "$json"
    for p in $pid; do
        xcrun devicectl device process terminate --device "$core" --pid "$p" >/dev/null 2>&1 \
            && say "terminated the app on the phone (pid $p)"
    done
    rm -f "$OUT/phone.pid" "$OUT/phone.core"
}

stop_lock_watch() {
    [ -f "$OUT/lockwatch.pid" ] || return 0
    kill "$(cat "$OUT/lockwatch.pid")" 2>/dev/null || true
    rm -f "$OUT/lockwatch.pid"
}

# ---------------------------------------------------------------------------------------------
# The Simulator seats

# Filler seats 1..$SEATS-1 are SIM_UDIDS[0..]; the --no-phone stand-in host is STAND_IN_UDID.
SIM_UDIDS=()
STAND_IN_UDID=""
udid_for() {
    xcrun simctl list devices available | awk -v name="$1" -F '[()]' '$0 ~ "    " name " \\(" { print $2; exit }'
}

# Prints the udid of the simulator named $1, creating it as type $2 when it is missing.
ensure_sim() {
    local name="$1" type="$2" u runtime
    u="$(udid_for "$name")"
    if [ -z "$u" ]; then
        runtime="$(xcrun simctl list runtimes -j | python3 -c "
import json, sys
r = [x for x in json.load(sys.stdin)['runtimes'] if x['platform'] == 'iOS' and x['isAvailable']]
print(sorted(r, key=lambda x: [int(n) for n in x['version'].split('.')])[-1]['identifier'] if r else '')
")"
        [ -n "$runtime" ] || fail "No iOS Simulator runtime is installed: \`xcodebuild -downloadPlatform iOS\` (several GB)."
        u="$(xcrun simctl create "$name" "$type" "$runtime")" || fail "could not create the simulator '$name'"
        say "created simulator '$name' ($u) on $runtime" >&2
    fi
    printf '%s\n' "$u"
}

ensure_sims() {
    local stand_in="$1" i u listed=""
    SIM_UDIDS=()
    STAND_IN_UDID=""
    for ((i = 0; i < SEATS - 1; i++)); do
        u="$(ensure_sim "${SIM_DEVICES[$i]}" "${SIM_TYPES[$i]}")" || exit 1
        SIM_UDIDS+=("$u")
        listed="$listed · seat $((i + 1)) ${SIM_DEVICES[$i]} $u"
    done
    if [ -n "$stand_in" ]; then
        STAND_IN_UDID="$(ensure_sim "$STAND_IN_DEVICE" "$STAND_IN_TYPE")" || exit 1
        listed="$listed · seat 0 (standing in for the phone) $STAND_IN_DEVICE $STAND_IN_UDID"
    fi
    all_sims >"$OUT/sims.udids"
    say "simulators:${listed}"
}

# Every simulator this run uses, one per line.
all_sims() {
    printf '%s\n' "${SIM_UDIDS[@]}"
    [ -z "$STAND_IN_UDID" ] || printf '%s\n' "$STAND_IN_UDID"
}

build_sims() {
    say "=== BUILDING THE PLAYTEST APP FOR THE SIMULATOR ==="
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme SomeonesHome -configuration "$CONFIG" \
        -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
        -derivedDataPath "$DERIVED" build >"$RUN/build-sims.log" 2>&1 \
        || { tail -25 "$RUN/build-sims.log" >&2; fail "the simulator build failed — see $RUN/build-sims.log"; }
    SIM_APP="$DERIVED/Build/Products/$CONFIG-iphonesimulator/SomeonesHome.app"
    find "$SIM_APP" -name 'Silkscreen-Regular.ttf' | grep . >/dev/null \
        || fail "the pixel fonts are not in the bundle; the app would fail on its first frame"
}

prepare_sims() {
    say "=== THE GRANTS A HOST WOULD HAVE GIVEN, AND THE INSTALL (see sim-grants.py) ==="
    local u sims=()
    while read -r u; do [ -z "$u" ] || sims+=("$u"); done < <(all_sims)
    # Every Simulator boots fresh. There is no notification grant to give any more (D-326).
    for u in "${sims[@]}"; do xcrun simctl shutdown "$u" 2>/dev/null || true; done
    for u in "${sims[@]}"; do xcrun simctl boot "$u" 2>/dev/null || true; done
    for u in "${sims[@]}"; do
        xcrun simctl bootstatus "$u" -b >/dev/null
        xcrun simctl terminate "$u" "$BUNDLE_ID" 2>/dev/null || true
        xcrun simctl install "$u" "$SIM_APP" || fail "could not install on simulator $u"
    done
    python3 "$REPO/sim-grants.py" privacy "${sims[@]}" | tee -a "$OUT/driver.log"
}

launch_sim() {
    local seat="$1"
    local u="${SIM_UDIDS[$((seat - 1))]}"
    say "=== LAUNCHING SEAT $seat ON ${SIM_DEVICES[$((seat - 1))]} (WIRE-ONLY FILLER) ==="
    env "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_NAME=${SEAT_NAMES[$seat]}" \
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_JOIN=$HOME_NAME" \
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_LINE=SEAT $seat WROTE THIS AND NOBODY MAY SEE IT" \
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_FORGET=1" \
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND=1" \
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND_SEED=$SEED" \
        xcrun simctl launch --console-pty "$u" "$BUNDLE_ID" >>"$OUT/seat-$seat.log" 2>&1 &
    echo $! >>"$OUT/sims.pids"
}

# --no-phone only: a Simulator hosts as seat 0, plays the bench's script and presses LIGHTS OUT
# itself once every seat is in. It has no radio either, so it checks the wire and the script only.
launch_stand_in() {
    say "=== LAUNCHING THE STAND-IN HOST ON $STAND_IN_DEVICE AS SEAT 0 · SEED $SEED ==="
    env "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_NAME=${SEAT_NAMES[0]}" \
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_HOST=$HOME_NAME" \
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_LINE=SEAT 0 WROTE THIS AND NOBODY MAY SEE IT" \
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_FORGET=1" \
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_INSIDERS=0" \
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_LIGHTS_OUT=1" \
        "SIMCTL_CHILD_SOMEONES_HOME_LOBBY_SEATS=$SEATS" \
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND=1" \
        "SIMCTL_CHILD_SOMEONES_HOME_ROUND_SEED=$SEED" \
        xcrun simctl launch --console-pty "$STAND_IN_UDID" "$BUNDLE_ID" >>"$OUT/seat-0.log" 2>&1 &
    echo $! >>"$OUT/sims.pids"
}

stop_sims() {
    if [ -f "$OUT/sims.udids" ]; then
        local u
        while read -r u; do
            [ -n "$u" ] || continue
            xcrun simctl terminate "$u" "$BUNDLE_ID" 2>/dev/null || true
            xcrun simctl shutdown "$u" 2>/dev/null || true
        done <"$OUT/sims.udids"
        say "terminated the app and shut down the simulators"
    fi
    if [ -f "$OUT/sims.pids" ]; then
        local p
        while read -r p; do kill "$p" 2>/dev/null || true; done <"$OUT/sims.pids"
    fi
    rm -f "$OUT/sims.pids" "$OUT/sims.udids"
}

# ---------------------------------------------------------------------------------------------

# Wait until $1 holds a line matching $2, for at most $3 seconds. Says how long it took.
wait_for() {
    local file="$1" pattern="$2" limit="$3" what="$4" waited=0
    while [ "$waited" -lt "$limit" ]; do
        if grep -qE "$pattern" "$file" 2>/dev/null; then
            say "$what (+${waited}s)"
            return 0
        fi
        nap 1
        waited=$((waited + 1))
    done
    say "$what NEVER HAPPENED after ${limit}s — see $file"
    return 1
}

do_stop() {
    say "=== STOPPING ==="
    stop_lock_watch
    stop_phone
    stop_sims
    # Nothing here touches a log: every run's are kept under $OUT/runs/.
    if [ -L "$OUT/latest" ]; then say "logs kept in $OUT/$(readlink "$OUT/latest")"; else say "logs kept in $OUT"; fi
}

# The phone's lines that matter (ble-unit4-device-check.md), plus the bench's and the lobby's.
# The last three are how a host that locked or slept shows in its own log.
FOLLOW='\[radio\]|\[round\] (PUSH|WHAT THIS PHONE IS)|\[house\]|\[round-bench\]|\[lobby-bench\] (HOSTING|SETUP|SEATED|LIGHTS OUT|ARMED|NOT HOSTING|[0-9]+ JOINED)|Websocket handler failed|SEARCH FAILED|[Ll]ocked'

do_start() {
    local install=1 phone=1
    AUTO_LIGHTS_OUT=""
    BY_HAND=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --seats)
                shift
                case "${1:-}" in 3|4) SEATS="$1" ;; *) fail "--seats takes 3 or 4 (seat 0 is the phone; the rest are Simulators)" ;; esac
                ;;
            --no-install) install="" ;;
            --auto-lights-out) AUTO_LIGHTS_OUT=1 ;;
            --no-phone) phone="" ;;
            --by-hand) BY_HAND=1 ;;
            *) fail "unknown option '$1' — see the top of $0" ;;
        esac
        shift
    done
    # The dry run hosts a home of its own, so a phone still on the air from an earlier run cannot
    # take its seats.
    [ -n "$phone" ] || HOME_NAME="${ONE_PHONE_HOME:-NO PHONE}"

    # A previous run's processes go first. Its logs stay where they are, in their own run directory.
    DRIVER_LOG=/dev/null do_stop >/dev/null 2>&1
    begin_run
    echo "$SEED" >"$RUN/seed.txt"
    say "=== ONE-PHONE ROUND · $SEATS SEATS · SEED $SEED · LOGS IN $RUN (linked from $OUT) ==="

    if [ -n "$phone" ]; then
        if [ -n "$install" ]; then install_phone; else find_phone; fi
        ensure_sims ""
    else
        say "--no-phone: nothing is built for, installed on or launched on the phone"
        ensure_sims stand-in
    fi
    build_sims
    prepare_sims

    local seat
    if [ -n "$phone" ]; then
        launch_phone
        # The first launch may stand on the OS's permission prompts; answer them on the phone.
        wait_for "$OUT/phone.log" "ON THE AIR" 120 "the phone's home is on the air" || {
            grep -E 'SETUP|NOT HOSTING|REFUSED' "$OUT/phone.log" | tail -5
            if grep -qi 'locked' "$OUT/phone.log" || [ "$(phone_locked)" = "yes" ]; then
                fail "THE PHONE WAS LOCKED, SO THE APP COULD NOT START. $LOCKED_HELP Then run start again."
            fi
            fail "THE PHONE NEVER HOSTED. If the log says SETUP INCOMPLETE, do the permissions sweep (README step 2) and run start again. If the phone was locked, unlock it and run start again."
        }
        for ((seat = 1; seat < SEATS; seat++)); do
            launch_sim "$seat"
            if [ "$seat" -eq 1 ]; then
                wait_for "$OUT/phone.log" "2 JOINED" 90 "seat 1 is seated" || fail "seat 1 never joined. Is this Mac on the phone's Wi-Fi? Does $OUT/seat-1.log say SETUP INCOMPLETE?"
            else
                wait_for "$OUT/phone.log" "$((seat + 1)) JOINED" 90 "seat $seat is seated" || fail "seat $seat never joined — see $OUT/seat-$seat.log"
            fi
        done
    else
        dry_run
        return $?
    fi

    say ""
    say "$SEATS SEATED. ROUND SEED $SEED — the Mac's peer derives every seat's token from it:"
    say "  tools/ble-peer/.build/release/ble-peer sniff --round-seed $SEED --seats $SEATS"
    say "  tools/ble-peer/.build/release/ble-peer advertise --round-seed $SEED --seat 1"
    if [ -n "$AUTO_LIGHTS_OUT" ]; then
        say "The bench presses LIGHTS OUT now."
    else
        say "Check README step 3 (no [radio] line yet), then press LIGHTS OUT on the phone."
    fi
    say "Following $OUT/phone.log. Ctrl-C stops everything (or: $0 stop)."
    say ""
    trap 'echo; do_stop; exit 0' INT TERM
    watch_lock &
    echo $! >"$OUT/lockwatch.pid"
    tail -n +1 -f "$OUT/phone.log" | grep --line-buffered -E "$FOLLOW"
}

# The line number of the first line of $1 matching $2, or nothing.
first_line() { grep -nE "$2" "$1" 2>/dev/null | head -1 | cut -d: -f1; }

# --no-phone: the filler seats look for a house that is not there yet and must not give up; then a
# Simulator hosts in the phone's place and the round is played to the end of its first meeting.
dry_run() {
    local seat fails=0 host="$OUT/seat-0.log"
    for ((seat = 1; seat < SEATS; seat++)); do launch_sim "$seat"; done
    for ((seat = 1; seat < SEATS; seat++)); do
        wait_for "$OUT/seat-$seat.log" "STILL LOOKING|ATTACHING" 120 "seat $seat is looking for '$HOME_NAME'"
    done
    # Nothing hosts yet, so no lights go out: each seat's bench must still be waiting, not given up,
    # past the sixty seconds after which it used to give up (RoundCheat's awaitTheWorkOrder).
    say "waiting 75 s: a seat's bench must not give up before LIGHTS OUT"
    nap 75
    if grep -q "NO WORK ORDER ARRIVED" "$OUT"/seat-*.log; then
        say "FAIL — a seat's bench gave up before any LIGHTS OUT: $(grep -h "NO WORK ORDER ARRIVED" "$OUT"/seat-*.log | head -1)"
        fails=$((fails + 1))
    else
        say "PASS — no seat's bench has given up waiting for LIGHTS OUT"
    fi

    launch_stand_in
    wait_for "$host" "ON THE AIR" 120 "the stand-in's home is on the air" || fail "the stand-in never hosted — see $host"
    wait_for "$host" "\\[lobby-bench\\] $SEATS JOINED" 120 "all $SEATS seats are in" || fail "the room never filled — see $host and the seat logs"
    wait_for "$host" "\\[house\\] ARMED — $SEATS SEAT" 90 "the stand-in pressed LIGHTS OUT and the house armed $SEATS seats" \
        || fail "the house never armed — see $host"
    wait_for "$host" "WHAT THIS PHONE IS — INSIDER" 30 "seat 0 was dealt the Insider role" \
        || { say "FAIL — seed $SEED did not deal seat 0 the Insider role at $SEATS seats"; fails=$((fails + 1)); }
    wait_for "$host" "=== ACT 3" 300 "the bench reached act 3" || fail "the bench never reached act 3 — see $host"
    if wait_for "$host" "THE REVOKE LANDED ON SEAT|THE TOUCH REVOKED NOBODY" 180 "act 3's touch was ruled on"; then
        local landed
        landed="$(grep -hoE "THE REVOKE LANDED ON SEAT [0-9]+|THE TOUCH REVOKED NOBODY" "$host" | head -1)"
        if [ "$landed" = "THE REVOKE LANDED ON SEAT 1" ]; then say "PASS — $landed (a Simulator)"; else say "FAIL — $landed, not seat 1"; fails=$((fails + 1)); fi
    else
        fails=$((fails + 1))
    fi
    wait_for "$host" "BEAT MEETING-RINGING" 180 "a meeting was called" || fails=$((fails + 1))
    local ended ringing
    ended="$(first_line "$host" "THE ROUND ENDED")"
    ringing="$(first_line "$host" "BEAT MEETING-RINGING")"
    if [ -n "$ended" ] && { [ -z "$ringing" ] || [ "$ended" -lt "$ringing" ]; }; then
        say "FAIL — the round ended before the meeting: $(sed -n "${ended}p" "$host")"
        fails=$((fails + 1))
    else
        say "PASS — the round did not end before the meeting"
    fi
    grep -hE "FOUND A REVOKED PLAYER|NO MEETING CAME OF THE REPORT" "$host" | head -1 | sed 's/^/  /' | tee -a "$DRIVER_LOG"
    # The tally is the meeting's last phase. What the room decides there may end the round (a plain
    # Resident Restrained at four seats is parity), which is the rules and not an early ending.
    wait_for "$host" "BEAT TALLY|BEAT LIGHTS-OUT-AGAIN" 300 "the meeting ran to its tally (gate, talk, ballot, tally)" || fails=$((fails + 1))
    nap 5
    grep -hE "RESTRAINED — |THE ROUND ENDED" "$host" | head -2 | sed 's/^/  after the meeting: /' | tee -a "$DRIVER_LOG"
    say ""
    if [ "$fails" -eq 0 ]; then
        say "--no-phone dry run PASSED: $SEATS seats, seed $SEED. The Simulators are still playing. Stop with: $0 stop"
    else
        say "--no-phone dry run FAILED $fails check(s) — see $OUT/seat-*.log. Stop with: $0 stop"
    fi
    return "$fails"
}

do_check() {
    find_team
    find_phone
    local i u
    for i in "${!SIM_DEVICES[@]}"; do
        u="$(udid_for "${SIM_DEVICES[$i]}")"
        say "simulator '${SIM_DEVICES[$i]}' (seat $((i + 1))): ${u:-missing — start creates it}"
    done
    u="$(udid_for "$STAND_IN_DEVICE")"
    say "simulator '$STAND_IN_DEVICE' (--no-phone's stand-in host): ${u:-missing — start --no-phone creates it}"
    if [ -x "$REPO/tools/ble-peer/.build/release/ble-peer" ]; then
        say "ble-peer: built"
    else
        say "ble-peer: not built — (cd tools/ble-peer && swift build -c release)"
    fi
}

case "${1:-}" in
    check) do_check ;;
    install) shift; install_phone ;;
    start) shift; do_start "$@" ;;
    stop) do_stop ;;
    *) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 64 ;;
esac
