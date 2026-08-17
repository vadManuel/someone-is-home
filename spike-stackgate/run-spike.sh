#!/usr/bin/env bash
# Stack gate spike (story 1.7) — build, install, and retrieve results.
#
# Physical device only. The iOS Simulator cannot answer this question.
set -euo pipefail

cd "$(dirname "$0")"

# The Bash tool's shell does not pick up mise. The user's own terminal does; this makes the
# script work in both without editing anyone's shell config.
export PATH="$HOME/.local/share/mise/shims:$PATH"

BUNDLE_ID="home.someoneshome.stackgate"
DEVICE_NAME="${SPIKE_DEVICE_NAME:-vadManuel-Phone}"
CONFIG="${SPIKE_CONFIG:-Release}"
FRAMEWORK_DIR="shared/build/bin/iosArm64/releaseFramework"

die() { echo "error: $*" >&2; exit 1; }

# CoreDevice UUID — what devicectl install/copy take.
resolve_device() {
    xcrun devicectl list devices 2>/dev/null \
        | awk -v name="$DEVICE_NAME" '$1 == name { print $3; exit }'
}

# Hardware UDID — what xcodebuild -destination takes, and a DIFFERENT identifier from the one
# above. This must be a real device rather than 'generic/platform=iOS': -allowProvisioningUpdates
# only registers a device it was actually pointed at, so a generic destination signs against
# whatever devices the team already had and the install then fails with 0xe8008012.
resolve_device_udid() {
    if [[ -n "${SPIKE_DEVICE_UDID:-}" ]]; then
        echo "$SPIKE_DEVICE_UDID"
        return
    fi
    # Via a temp file, not /dev/stdout: devicectl writes its human-readable table to stdout
    # too, so the JSON comes back with a table glued to the front of it.
    local tmp
    tmp="$(mktemp /tmp/spike-devices.XXXXXX.json)"
    xcrun devicectl list devices --json-output "$tmp" >/dev/null 2>&1 || true
    python3 -c '
import json, sys
name = sys.argv[2]
try:
    devices = json.load(open(sys.argv[1]))["result"]["devices"]
except Exception:
    sys.exit(0)
for d in devices:
    if d.get("deviceProperties", {}).get("name") == name:
        print(d.get("hardwareProperties", {}).get("udid", ""))
        break
' "$tmp" "$DEVICE_NAME"
    rm -f "$tmp"
}

resolve_team() {
    if [[ -n "${SPIKE_DEVELOPMENT_TEAM:-}" ]]; then
        echo "$SPIKE_DEVELOPMENT_TEAM"
        return
    fi
    # The Team ID is the certificate's OU field.
    #
    # NOT the value in "Apple Development: Name (XXXXXXXXXX)" — that parenthesised string is
    # the individual's ID and is a different value entirely. Using it gets you a build that
    # signs and then fails at install with "No Account for Team", which reads like a missing
    # Xcode account rather than the wrong identifier.
    security find-certificate -c "Apple Development" -p 2>/dev/null \
        | openssl x509 -noout -subject 2>/dev/null \
        | sed -n 's/.*OU *= *\([A-Z0-9]*\).*/\1/p' \
        | head -1
}

check_jdk() {
    local v
    v="$(java -version 2>&1 | head -1)"
    # JDK 25 makes Kotlin silently fall back to JVM_24. A wrong-JDK build that succeeds is
    # worse than one that fails, so this refuses rather than warns.
    [[ "$v" == *'"21'* ]] || die "need JDK 21, got: $v"
}

cmd_framework() {
    check_jdk
    echo "==> building SpikeKit (release — a debug Kotlin/Native binary has different"
    echo "    allocation and inlining behaviour, so a debug pass would prove nothing)"
    ./gradlew linkReleaseFrameworkIosArm64
}

cmd_build() {
    cmd_framework
    [[ -d "$FRAMEWORK_DIR/SpikeKit.framework" ]] || die "framework missing at $FRAMEWORK_DIR"

    local team
    team="$(resolve_team)"
    [[ -n "$team" ]] || die "no code-signing identity found.
  Open Xcode > Settings > Accounts and add an Apple ID (a free one is enough), then re-run.
  Or set SPIKE_DEVELOPMENT_TEAM=<TEAMID>."

    local udid
    udid="$(resolve_device_udid)"
    [[ -n "$udid" ]] || die "could not resolve the hardware UDID for '$DEVICE_NAME'.
  Plug the phone in and check: xcrun devicectl list devices
  Or set SPIKE_DEVICE_UDID=<udid>."

    echo "==> building StackGate.app (team $team, device $udid)"
    xcodebuild \
        -project iosApp/iosApp.xcodeproj \
        -scheme StackGate \
        -configuration "$CONFIG" \
        -sdk iphoneos \
        -destination "id=$udid" \
        -derivedDataPath build/dd \
        -allowProvisioningUpdates \
        SPIKE_DEVELOPMENT_TEAM="$team" \
        build
    echo "==> $(pwd)/build/dd/Build/Products/$CONFIG-iphoneos/StackGate.app"
}

cmd_install() {
    local device
    device="$(resolve_device)"
    [[ -n "$device" ]] || die "device '$DEVICE_NAME' not found. xcrun devicectl list devices"
    local app="build/dd/Build/Products/$CONFIG-iphoneos/StackGate.app"
    [[ -d "$app" ]] || die "no app at $app — run '$0 build' first"
    echo "==> installing to $DEVICE_NAME ($device)"
    xcrun devicectl device install app --device "$device" "$app"
    echo
    echo "Launch StackGate on the phone and pick a run."
    echo "  VOLUME is the gate. Run VOLUME_CONTROL too — if the two tails match, the"
    echo "  pressure generator is a no-op and the GC half was never actually tested."
}

cmd_launch() {
    local device
    device="$(resolve_device)"
    [[ -n "$device" ]] || die "device '$DEVICE_NAME' not found"
    xcrun devicectl device process launch --device "$device" "$BUNDLE_ID"
}

cmd_pull() {
    local device out
    device="$(resolve_device)"
    [[ -n "$device" ]] || die "device '$DEVICE_NAME' not found"
    out="results/$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$out"
    echo "==> pulling results into $out"
    xcrun devicectl device copy from \
        --device "$device" \
        --domain-type appDataContainer \
        --domain-identifier "$BUNDLE_ID" \
        --source Documents \
        --destination "$out"
    find "$out" -type f | sort
}

case "${1:-}" in
    framework) cmd_framework ;;
    build)     cmd_build ;;
    install)   cmd_install ;;
    launch)    cmd_launch ;;
    pull)      cmd_pull ;;
    all)       cmd_build; cmd_install ;;
    *)
        cat <<EOF
usage: $0 <command>

  framework   build the Kotlin framework only (no signing needed)
  build       framework + signed StackGate.app
  install     install to $DEVICE_NAME
  all         build + install
  launch      launch the app on the device
  pull        copy the results CSV/JSON off the device into ./results/

env:
  SPIKE_DEVELOPMENT_TEAM   Apple team ID (auto-detected from the keychain otherwise)
  SPIKE_DEVICE_NAME        device name (default: vadManuel-Phone)
EOF
        exit 1
        ;;
esac
