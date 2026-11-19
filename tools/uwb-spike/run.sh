#!/usr/bin/env bash
# The UWB ranging spike: two iPhones, Nearby Interaction, every reading logged. A measurement tool.
#
#   tools/uwb-spike/run.sh check                         read-only: the signing team and both phones
#   tools/uwb-spike/run.sh install                       build the Playtest app and install it on both
#   tools/uwb-spike/run.sh start [--no-install] [--sessions N] [--probe [A|B|C]] [--camera-assist]
#                                [--extended-distance] [--delegate-queue]
#                                                        launch the spike on both, relay the tokens,
#                                                        follow the [uwb] lines (Ctrl-C stops)
#   tools/uwb-spike/run.sh stop                          end it: the app on both phones, the relay
#
#   --sessions N          phone A holds N sessions instead of 1 (B always holds 1). Session 1 ranges
#                         with B; 2..N are a probe of how many sessions one phone will run at once.
#                         With three phones A's first 2 range, with B and C. README step 8.
#   --probe [A|B|C]       every phone (or just the one named) opens one more idle session every 2 s
#                         until iOS refuses one or it holds 20, then prints a LIMIT line.
#   --camera-assist       NINearbyPeerConfiguration.isCameraAssistanceEnabled, on every phone
#   --extended-distance   NINearbyPeerConfiguration.isExtendedDistanceMeasurementEnabled, every phone
#   --delegate-queue      the readings arrive on the spike's own queue instead of the main thread
#
# The phones: A is UWB_PHONE_A (default: APP_DEVICE_NAME, else vadManuel-Phone); B is UWB_PHONE_B,
# or else the one other phone connected to this Mac. Either may be part of the phone's name.
# UWB_PHONE_C names a third phone and makes it a three-phone run: every phone holds one live
# session per other phone, and B must then be named too, or be the only phone left over.
#
# WHAT THIS LAUNCHES. The Playtest build, with SOMEONES_HOME_UWB_SPIKE=1 in its environment. That
# switch hands the whole app to the spike screen (app/src/cheats/.../UwbSpike.kt) before anything
# of the game is built: no lobby, no Bluetooth radio, no house. Launched from the springboard, or
# by round.sh or run-app.sh, the same build is the ordinary playtest app. Release has no spike.
#
# HOW THE TOKENS CROSS. Each phone prints its NIDiscoveryToken (NSKeyedArchiver, base64) on a
# `[uwb] S1 TOKEN` console line. The relay here reads it from that phone's log and copies it into
# the OTHER phone's app container as tmp/uwb-peer-token.txt (`devicectl device copy to`), which the
# app polls twice a second. A new session on either phone prints a new token and is relayed again.
# With three phones a token belongs to one peer: phone X's `[uwb] S<n> TOKEN … peer=Y` line goes to
# phone Y as tmp/uwb-peer-token-X.txt, and only Y's session for X reads it.
#
# Logs land in $OUT (default /tmp/someones-home-one-phone), one directory per run under
# $OUT/runs/uwb-<date-time>/ that no later run touches. $OUT/phone.log (A), $OUT/phoneB.log (B) and
# $OUT/uwb-relay.log link to the latest run's; $OUT/latest links to its directory. sample.sh reads
# phone.log and phoneB.log, and phoneC.log (C) when the latest run had three phones.
#
# Signing: the Team ID is SOMEONES_HOME_DEVELOPMENT_TEAM, or else the OU of the one valid
# "Apple Development" certificate on this Mac (as round.sh). It never guesses between several.
set -o pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO" || exit 1
# The Bash tool's shell does not pick up mise; the owner's terminal does. Harmless either way.
export PATH="$HOME/.local/share/mise/shims:$PATH"

A_NAME="${UWB_PHONE_A:-${APP_DEVICE_NAME:-vadManuel-Phone}}"
B_NAME="${UWB_PHONE_B:-}"
C_NAME="${UWB_PHONE_C:-}"
# The phones in this run. Three only when UWB_PHONE_C is set; every two-phone path is unchanged.
LETTERS="A B"
[ -z "$C_NAME" ] || LETTERS="A B C"
BUNDLE_ID="home.someoneshome.app"
CONFIG=Playtest
DERIVED="${APP_DERIVED_DATA:-/tmp/someones-home-dd}"
OUT="${UWB_OUT:-${ONE_PHONE_OUT:-/tmp/someones-home-one-phone}}"
# Where the app looks, relative to its data container. UwbSpike.PEER_TOKEN_FILE, under tmp/.
PEER_TOKEN_DEST="tmp/uwb-peer-token.txt"
SESSIONS=1
# The opt-in switches: the phones that probe, and the session options. Empty: not passed at all.
PROBE_ON=""
CAMERA=""
EXTENDED=""
QUEUE=""

mkdir -p "$OUT"
RELAY_LOG="$OUT/uwb-relay.log"
say() { printf '%s\n' "$*" | tee -a "$RELAY_LOG"; }
fail() { printf '\n%s\n' "$*" | tee -a "$RELAY_LOG" >&2; exit 1; }
nap() { python3 -c "import time; time.sleep($1)"; }

# ---------------------------------------------------------------------------------------------
# Every run's logs, kept

RUN=""
begin_run() {
    local stamp f n=1
    stamp="$(date +%Y%m%d-%H%M%S)"
    RUN="$OUT/runs/uwb-$stamp"
    while [ -e "$RUN" ]; do RUN="$OUT/runs/uwb-$stamp-$n"; n=$((n + 1)); done
    mkdir -p "$RUN/relay"
    local logs="phone.log phoneB.log uwb-relay.log"
    if [ -n "$C_NAME" ]; then
        logs="$logs phoneC.log"
    elif [ -L "$OUT/phoneC.log" ]; then
        # A two-phone run after a three-phone one: sample.sh must not read the old run's C.
        rm -f "$OUT/phoneC.log"
    fi
    for f in $logs; do
        : >"$RUN/$f"
        # A loose file an older procedure tee'd straight into $OUT is moved aside, never removed.
        if [ -f "$OUT/$f" ] && [ ! -L "$OUT/$f" ]; then mv "$OUT/$f" "$RUN/before-$f"; fi
        ln -sfn "runs/$(basename "$RUN")/$f" "$OUT/$f"
    done
    ln -sfn "runs/$(basename "$RUN")" "$OUT/latest"
}

# ---------------------------------------------------------------------------------------------
# The phones

# Prints "<coredevice id> <udid> <tunnel> <devmode> <connection> <name>" for every phone paired
# with this Mac, the name with its spaces as underscores.
list_phones() {
    local json
    json="$(mktemp "$OUT/devices.XXXXXX")"
    xcrun devicectl list devices --json-output "$json" >/dev/null 2>&1 || true
    python3 - "$json" <<'EOF'
import json, sys
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    sys.exit(0)
for x in d.get('result', {}).get('devices', []):
    props = x.get('deviceProperties', {})
    hw = x.get('hardwareProperties', {})
    if hw.get('platform') not in (None, 'iOS'):
        continue
    tunnel = x.get('connectionProperties', {}).get('tunnelState', '-')
    print(x['identifier'], hw.get('udid', '-'), tunnel, props.get('developerModeStatus', '-'),
          props.get('connectionState', '-'), props.get('name', '').replace(' ', '_'))
EOF
    rm -f "$json"
}

A_CORE="" A_UDID="" B_CORE="" B_UDID="" C_CORE="" C_UDID=""
find_phones() {
    local all a b c=""
    all="$(list_phones)"
    a="$(printf '%s\n' "$all" | awk -v want="$(echo "$A_NAME" | tr '[:upper:] ' '[:lower:]_')" 'index(tolower($6), want) > 0')"
    [ "$(printf '%s' "$a" | grep -c .)" -eq 1 ] \
        || fail "Phone A: expected exactly one paired phone named like '$A_NAME', found: ${a:-none}. Set UWB_PHONE_A=<part of its name>. Paired: $(printf '%s\n' "$all" | awk '{print $6}' | tr '\n' ' ')"
    if [ -n "$C_NAME" ]; then
        c="$(printf '%s\n' "$all" | awk -v want="$(echo "$C_NAME" | tr '[:upper:] ' '[:lower:]_')" 'index(tolower($6), want) > 0')"
        [ "$(printf '%s' "$c" | grep -c .)" -eq 1 ] \
            || fail "Phone C: expected exactly one paired phone named like '$C_NAME', found: ${c:-none}. Set UWB_PHONE_C=<part of its name>. Paired: $(printf '%s\n' "$all" | awk '{print $6}' | tr '\n' ' ')"
    fi
    if [ -n "$B_NAME" ]; then
        b="$(printf '%s\n' "$all" | awk -v want="$(echo "$B_NAME" | tr '[:upper:] ' '[:lower:]_')" 'index(tolower($6), want) > 0')"
    else
        # The one other phone this Mac can reach (other than C, in a three-phone run).
        b="$(printf '%s\n' "$all" | awk -v a="$(echo "$a" | awk '{print $1}')" -v c="$(echo "$c" | awk '{print $1}')" '$1 != a && $1 != c && $3 != "unavailable" && $5 != "disconnected"')"
    fi
    [ "$(printf '%s' "$b" | grep -c .)" -eq 1 ] \
        || fail "Phone B: expected exactly one other reachable phone, found: ${b:-none}. Set UWB_PHONE_B=<part of its name>."
    local who line core udid tunnel devmode conn name
    for who in $LETTERS; do
        case "$who" in A) line="$a" ;; B) line="$b" ;; *) line="$c" ;; esac
        read -r core udid tunnel devmode conn name <<<"$line"
        say "phone $who: $name · CoreDevice $core · UDID $udid · link $tunnel · developer mode $devmode"
        [ "$tunnel" != "unavailable" ] || fail "Phone $who ($name) is paired but unreachable. Unlock it and plug it into this Mac, then run again."
        [ "$devmode" = "enabled" ] || [ "$devmode" = "-" ] || fail "Developer Mode is off on $name: Settings → Privacy & Security → Developer Mode → on (the phone restarts)."
        case "$who" in
            A) A_CORE="$core"; A_UDID="$udid" ;;
            B) B_CORE="$core"; B_UDID="$udid" ;;
            *) C_CORE="$core"; C_UDID="$udid" ;;
        esac
    done
    [ "$A_CORE" != "$B_CORE" ] || fail "Phone A and phone B are the same phone. Set UWB_PHONE_B."
    if [ -n "$C_NAME" ]; then
        [ "$C_CORE" != "$A_CORE" ] || fail "Phone A and phone C are the same phone. Set UWB_PHONE_C to the third phone."
        [ "$C_CORE" != "$B_CORE" ] || fail "Phone B and phone C are the same phone. Set UWB_PHONE_B and UWB_PHONE_C."
    fi
}

TEAM=""
find_team() {
    if [ -n "${SOMEONES_HOME_DEVELOPMENT_TEAM:-}" ]; then
        TEAM="$SOMEONES_HOME_DEVELOPMENT_TEAM"
        say "team: $TEAM (from SOMEONES_HOME_DEVELOPMENT_TEAM)"
        return
    fi
    local teams count
    teams="$(security find-identity -v -p codesigning 2>/dev/null \
        | sed -n 's/.*"\(Apple Development: .*\)"$/\1/p' \
        | while read -r identity; do
            security find-certificate -c "$identity" -p 2>/dev/null | openssl x509 -noout -subject 2>/dev/null \
                | sed -n 's/.*OU *= *\([A-Z0-9]*\).*/\1/p'
        done | sort -u)"
    count="$(printf '%s' "$teams" | grep -c .)"
    [ "$count" -ne 0 ] || fail "NO SIGNING IDENTITY ON THIS MAC. Xcode → Settings → Accounts: add the Apple ID, then Manage Certificates → + → Apple Development."
    [ "$count" -eq 1 ] || fail "SEVERAL TEAMS HAVE A DEVELOPMENT CERTIFICATE HERE: $(echo $teams). Run again with SOMEONES_HOME_DEVELOPMENT_TEAM=<team ID>."
    TEAM="$teams"
    say "team: $TEAM (the OU of this Mac's only valid Apple Development certificate)"
}

# Built per phone, as run-app.sh does: -allowProvisioningUpdates registers THAT udid, and the second
# build is incremental (only signing runs again).
install_one() {
    local who="$1" core="$2" udid="$3"
    say "=== BUILDING THE PLAYTEST APP FOR PHONE $who (a few minutes the first time) ==="
    if ! xcodebuild -project iosApp/iosApp.xcodeproj -scheme SomeonesHome -configuration "$CONFIG" \
        -destination "id=$udid" -derivedDataPath "$DERIVED-device" \
        DEVELOPMENT_TEAM="$TEAM" -allowProvisioningUpdates -allowProvisioningDeviceRegistration \
        build >"$RUN/build-$who.log" 2>&1; then
        tail -25 "$RUN/build-$who.log" >&2
        fail "THE BUILD FOR PHONE $who FAILED — see $RUN/build-$who.log"
    fi
    local app="$DERIVED-device/Build/Products/$CONFIG-iphoneos/SomeonesHome.app"
    find "$app" -name 'Silkscreen-Regular.ttf' | grep . >/dev/null \
        || fail "the pixel fonts are not in the bundle; the app would fail on its first frame"
    /usr/libexec/PlistBuddy -c "Print :NSNearbyInteractionUsageDescription" "$app/Info.plist" >/dev/null 2>&1 \
        || fail "the built Info.plist has no NSNearbyInteractionUsageDescription; iOS would end the app at its first session. Was this built as Playtest?"
    say "=== INSTALLING ON PHONE $who ==="
    xcrun devicectl device install app --device "$core" "$app" >"$RUN/install-$who.log" 2>&1 \
        || { cat "$RUN/install-$who.log" >&2; fail "THE INSTALL ON PHONE $who FAILED. Unlock it; Developer Mode must be on."; }
}

do_install() {
    [ -n "$RUN" ] || begin_run
    find_team
    find_phones
    install_one A "$A_CORE" "$A_UDID"
    install_one B "$B_CORE" "$B_UDID"
    if [ -n "$C_NAME" ]; then
        install_one C "$C_CORE" "$C_UDID"
        say "installed on all three."
    else
        say "installed on both."
    fi
}

# ---------------------------------------------------------------------------------------------
# Launch, relay, stop

# The letters other than $1, comma-separated: the peers a phone holds a live session for.
peers_of() { printf '%s\n' $LETTERS | grep -vx "$1" | paste -sd, -; }

# Phone $1's log: phone.log for A, as it always was; phoneB.log, phoneC.log for the others.
log_of() { if [ "$1" = A ]; then echo "$RUN/phone.log"; else echo "$RUN/phone$1.log"; fi; }

launch_one() {
    local who="$1" core="$2" log="$3" sessions="$4" extra=()
    # Only what was asked for is passed: a two-phone run with no switches launches as it always did.
    if [ -n "$C_NAME" ]; then
        extra+=("DEVICECTL_CHILD_SOMEONES_HOME_UWB_ME=$who" "DEVICECTL_CHILD_SOMEONES_HOME_UWB_PEERS=$(peers_of "$who")")
    fi
    case " $PROBE_ON " in *" $who "*) extra+=("DEVICECTL_CHILD_SOMEONES_HOME_UWB_PROBE=1") ;; esac
    [ -z "$CAMERA" ] || extra+=("DEVICECTL_CHILD_SOMEONES_HOME_UWB_CAMERA_ASSIST=1")
    [ -z "$EXTENDED" ] || extra+=("DEVICECTL_CHILD_SOMEONES_HOME_UWB_EXTENDED_DISTANCE=1")
    [ -z "$QUEUE" ] || extra+=("DEVICECTL_CHILD_SOMEONES_HOME_UWB_DELEGATE_QUEUE=1")
    say "=== LAUNCHING THE SPIKE ON PHONE $who ($sessions session(s)) ==="
    [ ${#extra[@]} -eq 0 ] || say "    with $(printf '%s\n' "${extra[@]}" | sed 's/^DEVICECTL_CHILD_SOMEONES_HOME_UWB_//' | paste -sd' ' -)"
    env "DEVICECTL_CHILD_SOMEONES_HOME_UWB_SPIKE=1" \
        "DEVICECTL_CHILD_SOMEONES_HOME_UWB_SESSIONS=$sessions" \
        ${extra[@]+"${extra[@]}"} \
        xcrun devicectl device process launch --console --terminate-existing \
            --device "$core" "$BUNDLE_ID" >>"$log" 2>&1 &
    echo $! >"$OUT/uwb-$who.pid"
    echo "$core" >"$OUT/uwb-$who.core"
}

# The latest session-1 token a log has printed, or nothing. A session with no token prints `nil`.
token_in() { grep -aoE '\[uwb\] S1 TOKEN [A-Za-z0-9+/=]+' "$1" 2>/dev/null | tail -1 | awk '$4 != "nil" {print $4}'; }
# How many session-1 tokens a log has printed: a new one means a new session or a new process.
tokens_in() { grep -acE '\[uwb\] S1 TOKEN ' "$1" 2>/dev/null; }

# Copies $2 (a token) into phone $1's container, as $3 (default the two-phone file). A fresh local
# file each time, so devicectl's "skip what has not been modified" can never skip a delivery.
deliver() {
    local to="$1" token="$2" dest="${3:-$PEER_TOKEN_DEST}" core n file
    core="$(cat "$OUT/uwb-$to.core")"
    n="$(date +%s)-$RANDOM"
    file="$(basename "$dest")"
    mkdir -p "$RUN/relay/$n"
    printf '%s\n' "$token" >"$RUN/relay/$n/$file"
    if xcrun devicectl device copy to --device "$core" --source "$RUN/relay/$n/$file" \
        --destination "$dest" --domain-type appDataContainer --domain-identifier "$BUNDLE_ID" \
        --timeout 30 >>"$RUN/relay/copy.log" 2>&1; then
        say "relay: token ${token:0:12}… → phone $to ($dest)"
        return 0
    fi
    say "relay: THE COPY TO PHONE $to FAILED — see $RUN/relay/copy.log"
    return 1
}

relay() {
    local logA="$RUN/phone.log" logB="$RUN/phoneB.log"
    local sentA="" sentB="" seenA=0 seenB=0 tA tB nA nB
    while true; do
        tA="$(token_in "$logA")"; tB="$(token_in "$logB")"
        nA="$(tokens_in "$logA")"; nB="$(tokens_in "$logB")"
        # A's token to B when A has a new one, or when B has started a new session (it may be a new
        # process, which deleted the file at launch). And the same the other way.
        if [ -n "$tA" ] && [ "$nB" -gt 0 ] && { [ "$tA" != "$sentA" ] || [ "$nB" -gt "$seenB" ]; }; then
            deliver B "$tA" && { sentA="$tA"; seenB="$nB"; }
        fi
        if [ -n "$tB" ] && [ "$nA" -gt 0 ] && { [ "$tB" != "$sentB" ] || [ "$nA" -gt "$seenA" ]; }; then
            deliver A "$tB" && { sentB="$tB"; seenA="$nA"; }
        fi
        nap 1
    done
}

# Three phones. The latest token log $1 printed for peer $2 (a `peer=<letter>` line), or nothing.
peer_token_in() { grep -aoE "\[uwb\] S[0-9]+ TOKEN [A-Za-z0-9+/=]+ .*peer=$2([^A-Za-z]|\$)" "$1" 2>/dev/null | tail -1 | awk '$4 != "nil" {print $4}'; }
# How many tokens log $1 has printed for peer $2: a new one means a new session or a new process.
peer_tokens_in() { grep -acE "\[uwb\] S[0-9]+ TOKEN .*peer=$2([^A-Za-z]|\$)" "$1" 2>/dev/null; }

# The relay for three phones: for every ordered pair, X's token for Y goes to Y as
# tmp/uwb-peer-token-X.txt, when it is new or when Y has started a new session for X. The same
# rule as the two-phone relay, once per pair. bash 3.2 has no maps, so each pair's state is a
# variable named after it (SENT_AB, SEEN_AB).
relay3() {
    local x y tok n sent seen
    while true; do
        for x in $LETTERS; do
            for y in $LETTERS; do
                [ "$x" != "$y" ] || continue
                tok="$(peer_token_in "$(log_of "$x")" "$y")"
                n="$(peer_tokens_in "$(log_of "$y")" "$x")"
                eval "sent=\"\${SENT_$x$y:-}\"; seen=\"\${SEEN_$x$y:-0}\""
                if [ -n "$tok" ] && [ "${n:-0}" -gt 0 ] && { [ "$tok" != "$sent" ] || [ "$n" -gt "$seen" ]; }; then
                    deliver "$y" "$tok" "tmp/uwb-peer-token-$x.txt" && eval "SENT_$x$y=\"\$tok\"; SEEN_$x$y=\"\$n\""
                fi
            done
        done
        nap 1
    done
}

stop_one() {
    local who="$1" core pid json
    [ -f "$OUT/uwb-$who.core" ] || return 0
    core="$(cat "$OUT/uwb-$who.core")"
    if [ -f "$OUT/uwb-$who.pid" ]; then
        kill -TERM "$(cat "$OUT/uwb-$who.pid")" 2>/dev/null && say "stopped phone $who's console (the app is sent the same signal)"
    fi
    nap 2
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
            && say "terminated the app on phone $who (pid $p)"
    done
    rm -f "$OUT/uwb-$who.pid" "$OUT/uwb-$who.core"
}

do_stop() {
    if [ -f "$OUT/uwb-relay.pid" ]; then
        kill "$(cat "$OUT/uwb-relay.pid")" 2>/dev/null || true
        rm -f "$OUT/uwb-relay.pid"
    fi
    stop_one A
    stop_one B
    # Nothing to do unless the last run had a phone C (stop_one returns at once without its file).
    stop_one C
    if [ -L "$OUT/latest" ]; then say "logs kept in $OUT/$(readlink "$OUT/latest")"; fi
}

# Wait until $1 holds a line matching $2, for at most $3 seconds.
wait_for() {
    local file="$1" pattern="$2" limit="$3" what="$4" waited=0
    while [ "$waited" -lt "$limit" ]; do
        if grep -aqE "$pattern" "$file" 2>/dev/null; then say "$what (+${waited}s)"; return 0; fi
        nap 1
        waited=$((waited + 1))
    done
    say "$what NEVER HAPPENED after ${limit}s — see $file"
    return 1
}

# Every [uwb] line but the per-reading ones (sample.sh is for those), from both phones.
FOLLOW='\[uwb\] (DEVICE|CAPABILITY|SESSIONS|PEERS|CONFIG|PROBE|LIMIT|S[0-9]+ (TOKEN|PEER|RUN|CONFIGURED|STARTED|REMOVED|SUSPENDED|SUSPENSION-ENDED|INVALIDATED|NOT RESTARTING|CONVERGENCE|TALLY|STALE)|APP|MARK)'

do_start() {
    local install=1
    while [ $# -gt 0 ]; do
        case "$1" in
            --no-install) install="" ;;
            --sessions)
                shift
                case "${1:-}" in [1-9]|1[0-6]) SESSIONS="$1" ;; *) fail "--sessions takes 1 to 16" ;; esac
                ;;
            --probe)
                # A letter after it probes that phone only; without one, every phone probes.
                case "${2:-}" in
                    A|B|C) PROBE_ON="$2"; shift ;;
                    *) PROBE_ON="$LETTERS" ;;
                esac
                ;;
            --camera-assist) CAMERA=1 ;;
            --extended-distance) EXTENDED=1 ;;
            --delegate-queue) QUEUE=1 ;;
            *) fail "unknown option '$1' — see the top of $0" ;;
        esac
        shift
    done
    if [ "$PROBE_ON" = C ] && [ -z "$C_NAME" ]; then fail "--probe C needs a phone C: set UWB_PHONE_C."; fi
    # The start-up lines echoed below. CONFIG only when a switch was given, so a plain two-phone
    # start prints what it always printed.
    local head='DEVICE|CAPABILITY'
    [ -z "$PROBE_ON$CAMERA$EXTENDED$QUEUE" ] || head='DEVICE|CAPABILITY|CONFIG'
    do_stop >/dev/null 2>&1
    begin_run
    say "=== UWB SPIKE · LOGS IN $RUN (linked from $OUT) ==="
    if [ -n "$install" ]; then do_install; else find_phones; fi
    if [ -n "$C_NAME" ]; then start_three; return; fi

    launch_one A "$A_CORE" "$RUN/phone.log" "$SESSIONS"
    launch_one B "$B_CORE" "$RUN/phoneB.log" 1
    wait_for "$RUN/phone.log" '\[uwb\] CAPABILITY' 60 "phone A is up" || fail "Phone A never printed its capability line. Unlocked? See $RUN/phone.log"
    wait_for "$RUN/phoneB.log" '\[uwb\] CAPABILITY' 60 "phone B is up" || fail "Phone B never printed its capability line. Unlocked? See $RUN/phoneB.log"
    grep -ahE "\[uwb\] ($head)" "$RUN/phone.log" | sed 's/^/A: /' | tee -a "$RELAY_LOG"
    grep -ahE "\[uwb\] ($head)" "$RUN/phoneB.log" | sed 's/^/B: /' | tee -a "$RELAY_LOG"

    relay &
    echo $! >"$OUT/uwb-relay.pid"
    say ""
    say "The first time on each phone, iOS asks to find nearby devices: press Allow on both."
    wait_for "$RUN/phoneB.log" '\[uwb\] S1 PEER TOKEN READ' 60 "phone B read A's token" \
        || say "!!! B never read a token. If the relay copied it, the path B polls is on its SESSIONS line: $(grep -ah 'SESSIONS' "$RUN/phoneB.log" | tail -1)"
    wait_for "$RUN/phone.log" '\[uwb\] S1 PEER TOKEN READ' 60 "phone A read B's token" \
        || say "!!! A never read a token — see $RUN/relay/copy.log"
    say ""
    say "Following both phones (readings are not shown; sample.sh summarises them). Ctrl-C stops everything."
    trap 'echo; do_stop; exit 0' INT TERM
    { tail -n 0 -f "$RUN/phone.log" | grep --line-buffered -aE "$FOLLOW" | sed -u 's/^/A: /' &
      tail -n 0 -f "$RUN/phoneB.log" | grep --line-buffered -aE "$FOLLOW" | sed -u 's/^/B: /' &
      wait; }
}

# Three phones: each holds one live session per other phone (A also any --sessions extras), and
# the relay carries six tokens, one per ordered pair.
start_three() {
    local a_sessions="$SESSIONS" who x
    [ "$a_sessions" -ge 2 ] || a_sessions=2
    launch_one A "$A_CORE" "$RUN/phone.log" "$a_sessions"
    launch_one B "$B_CORE" "$RUN/phoneB.log" 2
    launch_one C "$C_CORE" "$RUN/phoneC.log" 2
    for who in $LETTERS; do
        wait_for "$(log_of "$who")" '\[uwb\] CAPABILITY' 60 "phone $who is up" \
            || fail "Phone $who never printed its capability line. Unlocked? See $(log_of "$who")"
    done
    for who in $LETTERS; do
        grep -ahE '\[uwb\] (DEVICE|CAPABILITY|PEERS|CONFIG)' "$(log_of "$who")" | sed "s/^/$who: /" | tee -a "$RELAY_LOG"
    done

    relay3 &
    echo $! >"$OUT/uwb-relay.pid"
    say ""
    say "The first time on each phone, iOS asks to find nearby devices: press Allow on all three."
    for who in $LETTERS; do
        for x in $LETTERS; do
            [ "$x" != "$who" ] || continue
            wait_for "$(log_of "$who")" "\[uwb\] S[0-9]+ PEER TOKEN READ .*peer=$x" 60 "phone $who read $x's token" \
                || say "!!! $who never read $x's token — see $RUN/relay/copy.log. $who polls: $(grep -ah 'SESSIONS' "$(log_of "$who")" | tail -1)"
        done
    done
    say ""
    say "Following all three phones (readings are not shown; sample.sh summarises them). Ctrl-C stops everything."
    trap 'echo; do_stop; exit 0' INT TERM
    { tail -n 0 -f "$RUN/phone.log" | grep --line-buffered -aE "$FOLLOW" | sed -u 's/^/A: /' &
      tail -n 0 -f "$RUN/phoneB.log" | grep --line-buffered -aE "$FOLLOW" | sed -u 's/^/B: /' &
      tail -n 0 -f "$RUN/phoneC.log" | grep --line-buffered -aE "$FOLLOW" | sed -u 's/^/C: /' &
      wait; }
}

# Read-only: no run directory is made and no log link moves.
do_check() {
    find_team
    find_phones
    if [ -n "$C_NAME" ]; then say "all three phones reachable. Next: $0 start"; else say "both phones reachable. Next: $0 start"; fi
}

case "${1:-}" in
    check) do_check ;;
    install) shift; do_install ;;
    start) shift; do_start "$@" ;;
    stop) do_stop ;;
    *) sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'; exit 64 ;;
esac
