#!/usr/bin/env bash
# One phone, two Simulator filler seats, one round: the contact radio checked with a single iPhone.
#
#   tools/one-phone/round.sh check                  read-only: signing team, the phone, the sims, the peer
#   tools/one-phone/round.sh install                build the Playtest app and install it on the phone
#   tools/one-phone/round.sh start [options]        install, then host a round on the phone + 2 sims
#   tools/one-phone/round.sh stop                   end it: the app on the phone, the sims, the logs' writers
#
#   start options:
#     --no-install        skip the phone build and install (it is already on the phone)
#     --auto-lights-out   let the bench press LIGHTS OUT once three are seated (default: you press it)
#     --by-hand           the phone plays no script: you arm, you call meetings (the sims still play)
#     --no-phone          dry run of the Mac half only: sims built, booted and launched, nothing on the phone
#
# THE PHONE HOSTS. It plays seat 0 and runs the house. The two Simulators join it over mDNS on the
# Mac's network, as seats 1 and 2, and play by themselves (SOMEONES_HOME_ROUND=1): they walk their
# work orders and check in at every meeting. **They are wire-only filler.** A Simulator has no
# Bluetooth, so its radio reports Unsupported, which the house records as a device fault and names
# in no notice (D-187, D-223). Nothing about the radio may be signed off from a Simulator.
#
# THE SEED IS FIXED, so the Mac's BLE peer can derive any seat's token (tools/ble-peer). At three
# seats, 20261116 deals the Insider role to seat 0, the phone, so the bench's scripted Revoke
# (`RoundCheat.theRevoke`) lands on seat 1, a Simulator; `OnePhoneVectorsTest` pins that. The Mac
# then advertises as seat 1 and the phone's report of it is the check. Another seed may Revoke the
# phone itself, which stops its scan.
#
# Logs land in $OUT (default /tmp/someones-home-one-phone): phone.log, seat-1.log, seat-2.log.
# `start` stays in the foreground following the phone's lines that matter; Ctrl-C runs `stop`.
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
SEED="${SOMEONES_HOME_ROUND_SEED:-20261116}"
BUNDLE_ID="home.someoneshome.app"
CONFIG=Playtest
DERIVED="${APP_DERIVED_DATA:-/tmp/someones-home-dd}"
OUT="${ONE_PHONE_OUT:-/tmp/someones-home-one-phone}"
# A home already stored on the phone under this name is hosted as it is and never overwritten.
HOME_NAME="${ONE_PHONE_HOME:-ONE PHONE}"
SIM_DEVICES=("iPhone 17" "iPhone 17 Pro")
SIM_TYPES=("com.apple.CoreSimulator.SimDeviceType.iPhone-17" "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro")
SEAT_NAMES=(PHONE SIMA SIMB)

mkdir -p "$OUT"

say() { printf '%s\n' "$*" | tee -a "$OUT/driver.log"; }
fail() { printf '\n%s\n' "$*" | tee -a "$OUT/driver.log" >&2; exit 1; }
nap() { python3 -c "import time; time.sleep($1)"; }

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
    find_team
    find_phone
    say "=== BUILDING THE PLAYTEST APP FOR $PHONE_NAME (a few minutes the first time) ==="
    if ! xcodebuild -project iosApp/iosApp.xcodeproj -scheme SomeonesHome -configuration "$CONFIG" \
        -destination "id=$PHONE_UDID" -derivedDataPath "$DERIVED-device" \
        DEVELOPMENT_TEAM="$TEAM" -allowProvisioningUpdates -allowProvisioningDeviceRegistration \
        build >"$OUT/build-phone.log" 2>&1; then
        tail -25 "$OUT/build-phone.log" >&2
        if grep -qiE 'signing|provisioning|team|certificate|No Account' "$OUT/build-phone.log"; then
            fail "SIGNING FAILED (full log: $OUT/build-phone.log). The owner must: open iosApp/iosApp.xcodeproj in Xcode once, select the SomeonesHome target → Signing & Capabilities, pick team $TEAM, and let Xcode register $PHONE_NAME. Then run this again."
        fi
        fail "THE PHONE BUILD FAILED — see $OUT/build-phone.log"
    fi
    local app="$DERIVED-device/Build/Products/$CONFIG-iphoneos/SomeonesHome.app"
    find "$app" -name 'Silkscreen-Regular.ttf' | grep . >/dev/null \
        || fail "the pixel fonts are not in the bundle; the app would fail on its first frame"
    say "=== INSTALLING ON $PHONE_NAME ==="
    if ! xcrun devicectl device install app --device "$PHONE_CORE" "$app" >"$OUT/install-phone.log" 2>&1; then
        cat "$OUT/install-phone.log" >&2
        fail "THE INSTALL FAILED. On the phone: unlock it; Settings → Privacy & Security → Developer Mode must be on (it restarts the phone)."
    fi
    say "installed."
}

launch_phone() {
    say "=== LAUNCHING THE HOST ON $PHONE_NAME · SEED $SEED ==="
    local lights=()
    # The bench's script (`RoundCheat.theRound`) runs on the host only with ROUND set. Without it the
    # round is the owner's; the seed is read at arming either way.
    local HOST_ROUND=("DEVICECTL_CHILD_SOMEONES_HOME_ROUND=1")
    [ -n "$BY_HAND" ] && HOST_ROUND=()
    [ -n "$AUTO_LIGHTS_OUT" ] && lights=("DEVICECTL_CHILD_SOMEONES_HOME_LOBBY_LIGHTS_OUT=1")
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

# ---------------------------------------------------------------------------------------------
# The two Simulator seats

SIM_UDIDS=()
udid_for() {
    xcrun simctl list devices available | awk -v name="$1" -F '[()]' '$0 ~ "    " name " \\(" { print $2; exit }'
}

ensure_sims() {
    local runtime
    runtime="$(xcrun simctl list runtimes -j | python3 -c "
import json, sys
r = [x for x in json.load(sys.stdin)['runtimes'] if x['platform'] == 'iOS' and x['isAvailable']]
print(sorted(r, key=lambda x: [int(n) for n in x['version'].split('.')])[-1]['identifier'] if r else '')
")"
    SIM_UDIDS=()
    local i
    for i in 0 1; do
        local u
        u="$(udid_for "${SIM_DEVICES[$i]}")"
        if [ -z "$u" ]; then
            [ -n "$runtime" ] || fail "No iOS Simulator runtime is installed: \`xcodebuild -downloadPlatform iOS\` (several GB)."
            u="$(xcrun simctl create "${SIM_DEVICES[$i]}" "${SIM_TYPES[$i]}" "$runtime")" \
                || fail "could not create the simulator '${SIM_DEVICES[$i]}'"
            say "created simulator '${SIM_DEVICES[$i]}' ($u) on $runtime"
        fi
        SIM_UDIDS+=("$u")
    done
    printf '%s\n' "${SIM_UDIDS[@]}" >"$OUT/sims.udids"
    say "simulators: ${SIM_DEVICES[0]} ${SIM_UDIDS[0]} · ${SIM_DEVICES[1]} ${SIM_UDIDS[1]}"
}

build_sims() {
    say "=== BUILDING THE PLAYTEST APP FOR THE SIMULATOR ==="
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme SomeonesHome -configuration "$CONFIG" \
        -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
        -derivedDataPath "$DERIVED" build >"$OUT/build-sims.log" 2>&1 \
        || { tail -25 "$OUT/build-sims.log" >&2; fail "the simulator build failed — see $OUT/build-sims.log"; }
    SIM_APP="$DERIVED/Build/Products/$CONFIG-iphonesimulator/SomeonesHome.app"
    find "$SIM_APP" -name 'Silkscreen-Regular.ttf' | grep . >/dev/null \
        || fail "the pixel fonts are not in the bundle; the app would fail on its first frame"
}

prepare_sims() {
    say "=== THE GRANTS A HOST WOULD HAVE GIVEN, AND THE INSTALL (see sim-grants.py) ==="
    local u
    for u in "${SIM_UDIDS[@]}"; do xcrun simctl shutdown "$u" 2>/dev/null || true; done
    python3 "$REPO/sim-grants.py" notifications "${SIM_UDIDS[@]}" | tee -a "$OUT/driver.log" | tee "$OUT/grants.txt"
    # A Simulator that has never answered the notification prompt is refused a seat, naming
    # NOTIFICATIONS. sim-grants copies an answer from any Simulator that has one; with none, a person
    # answers it once and every run after this is covered.
    if grep -q "NO ANSWERED NOTIFICATION ALERT" "$OUT/grants.txt"; then
        say ""
        say "!!! ONE-TIME SETUP: no Simulator on this Mac has answered the notification prompt."
        say "!!! When the Simulator window shows 'Someone's Home Would Like to Send You Notifications',"
        say "!!! press Allow. Then Ctrl-C and run start again: every run after that is covered."
        say ""
        open -a Simulator 2>/dev/null || true
    fi
    for u in "${SIM_UDIDS[@]}"; do xcrun simctl boot "$u" 2>/dev/null || true; done
    for u in "${SIM_UDIDS[@]}"; do
        xcrun simctl bootstatus "$u" -b >/dev/null
        xcrun simctl terminate "$u" "$BUNDLE_ID" 2>/dev/null || true
        xcrun simctl install "$u" "$SIM_APP" || fail "could not install on simulator $u"
    done
    python3 "$REPO/sim-grants.py" privacy "${SIM_UDIDS[@]}" | tee -a "$OUT/driver.log"
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

stop_sims() {
    if [ -f "$OUT/sims.udids" ]; then
        local u
        while read -r u; do
            [ -n "$u" ] || continue
            xcrun simctl terminate "$u" "$BUNDLE_ID" 2>/dev/null || true
            xcrun simctl shutdown "$u" 2>/dev/null || true
        done <"$OUT/sims.udids"
        say "terminated the app and shut down both simulators"
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
    stop_phone
    stop_sims
    say "logs kept in $OUT"
}

# The phone's lines that matter (ble-unit4-device-check.md), plus the bench's and the lobby's.
FOLLOW='\[radio\]|\[round\] (PUSH|WHAT THIS PHONE IS)|\[house\]|\[round-bench\]|\[lobby-bench\] (HOSTING|SETUP|SEATED|LIGHTS OUT|ARMED|NOT HOSTING|[0-9]+ JOINED)'

do_start() {
    local install=1 phone=1
    AUTO_LIGHTS_OUT=""
    BY_HAND=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --no-install) install="" ;;
            --auto-lights-out) AUTO_LIGHTS_OUT=1 ;;
            --no-phone) phone="" ;;
            --by-hand) BY_HAND=1 ;;
            *) fail "unknown option '$1' — see the top of $0" ;;
        esac
        shift
    done

    # A previous run's processes and logs go first, so every log starts at this run.
    do_stop >/dev/null 2>&1
    rm -f "$OUT"/phone.log "$OUT"/seat-*.log "$OUT"/driver.log
    : >"$OUT/driver.log"
    echo "$SEED" >"$OUT/seed.txt"
    say "=== ONE-PHONE ROUND · SEED $SEED · LOGS IN $OUT ==="

    if [ -n "$phone" ]; then
        if [ -n "$install" ]; then install_phone; else find_phone; fi
    else
        say "--no-phone: nothing is built for, installed on or launched on the phone"
    fi
    ensure_sims
    build_sims
    prepare_sims

    if [ -n "$phone" ]; then
        launch_phone
        # The first launch may stand on the OS's permission prompts; answer them on the phone.
        wait_for "$OUT/phone.log" "ON THE AIR" 120 "the phone's home is on the air" || {
            grep -E 'SETUP|NOT HOSTING|REFUSED' "$OUT/phone.log" | tail -5
            fail "THE PHONE NEVER HOSTED. If the log says SETUP INCOMPLETE, do the permissions sweep (README step 2) and run start again. If the phone was locked, unlock it and run start again."
        }
        launch_sim 1
        wait_for "$OUT/phone.log" "2 JOINED" 90 "seat 1 is seated" || fail "seat 1 never joined. Is this Mac on the phone's Wi-Fi? Does $OUT/seat-1.log say SETUP INCOMPLETE … NOTIFICATIONS (see the one-time setup above)?"
        launch_sim 2
        wait_for "$OUT/phone.log" "3 JOINED" 90 "seat 2 is seated" || fail "seat 2 never joined — see $OUT/seat-2.log"
    else
        launch_sim 1
        launch_sim 2
        wait_for "$OUT/seat-1.log" "STILL LOOKING|ATTACHING" 120 "seat 1 is looking for '$HOME_NAME'"
        wait_for "$OUT/seat-2.log" "STILL LOOKING|ATTACHING" 120 "seat 2 is looking for '$HOME_NAME'"
        say "--no-phone dry run done. Nothing hosts, so both seats keep looking. Stop with: $0 stop"
        return 0
    fi

    say ""
    say "THREE SEATED. ROUND SEED $SEED — the Mac's peer derives every seat's token from it:"
    say "  tools/ble-peer/.build/release/ble-peer sniff --round-seed $SEED --seats 3"
    say "  tools/ble-peer/.build/release/ble-peer advertise --round-seed $SEED --seat 1"
    if [ -n "$AUTO_LIGHTS_OUT" ]; then
        say "The bench presses LIGHTS OUT now."
    else
        say "Check README step 3 (no [radio] line yet), then press LIGHTS OUT on the phone."
    fi
    say "Following $OUT/phone.log. Ctrl-C stops everything (or: $0 stop)."
    say ""
    trap 'echo; do_stop; exit 0' INT TERM
    tail -n +1 -f "$OUT/phone.log" | grep --line-buffered -E "$FOLLOW"
}

do_check() {
    find_team
    find_phone
    local i u
    for i in 0 1; do
        u="$(udid_for "${SIM_DEVICES[$i]}")"
        say "simulator '${SIM_DEVICES[$i]}': ${u:-missing — start creates it}"
    done
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
    *) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 64 ;;
esac
