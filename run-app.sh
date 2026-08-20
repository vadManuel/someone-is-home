#!/usr/bin/env bash
# Build the app, install it, launch it, and confirm it is still running.
#
#   ./run-app.sh            simulator (screenshots)
#   ./run-app.sh device     the phone plugged into this Mac
#
# WHAT THE SIMULATOR CAN AND CANNOT SHOW. It shows that the ported screens LAY OUT on a
# phone-shaped panel at phone density, which no desktop preview can: a Mac window has no notch,
# no Dynamic Island and no safe area, and all three change where things land.
#
# It shows NOTHING about the game. The Simulator has no BLE, no torch, no camera and no
# haptics, which is every input this game has. Nothing that touches those may be signed off
# from there — that needs physical devices, plural, in a dark room.
#
# THE DEVICE PATH DOES NOT SCREENSHOT. `devicectl` has no screenshot command, so it confirms the
# app is running and you look at the phone. That is the honest division: this script can tell
# you the app did not die, and only a person can tell you the screen is right.
set -euo pipefail
cd "$(dirname "$0")"

# The Bash tool's shell does not pick up mise; the user's terminal does.
export PATH="$HOME/.local/share/mise/shims:$PATH"

MODE="${1:-simulator}"
DEVICE="${APP_DEVICE:-iPhone 17 Pro}"
BUNDLE_ID="home.someoneshome.app"
DERIVED="${APP_DERIVED_DATA:-/tmp/someones-home-dd}"
SHOT="${APP_SCREENSHOT:-/tmp/someones-home.png}"

# The Team ID is the certificate's OU, not the parenthetical in its common name — those differ,
# and the common name's is a certificate identifier that signing will reject.
#   security find-certificate -c "Apple Development" -p | openssl x509 -noout -subject
TEAM="${SOMEONES_HOME_DEVELOPMENT_TEAM:-}"

# Asserts the pixel fonts made it into the bundle. Without them the app installs, launches, and
# dies on its first frame with MissingResourceException — and "BUILD SUCCEEDED" says nothing
# about it, because the fonts live in :ui's composeResources and a static framework does not
# carry them.
assert_fonts() {
    find "$1" -name 'Silkscreen-Regular.ttf' | grep -q . || {
        echo "the pixel fonts are not in the bundle; the app will die on its first frame" >&2
        exit 1
    }
}

if [ "$MODE" = "device" ]; then
    [ -n "$TEAM" ] || { echo "set SOMEONES_HOME_DEVELOPMENT_TEAM to your Team ID" >&2; exit 1; }

    # TWO DIFFERENT IDENTIFIERS. devicectl takes the CoreDevice UUID; xcodebuild -destination
    # takes the hardware UDID. They are not interchangeable, and a generic iOS destination signs
    # against whatever devices the team already had, so the install then fails with 0xe8008012.
    json="$(mktemp /tmp/sh-devices.XXXXXX.json)"
    xcrun devicectl list devices --json-output "$json" >/dev/null 2>&1 || true
    read -r core udid <<EOF
$(python3 -c "
import json
d = json.load(open('$json'))
for x in d['result']['devices']:
    if x.get('deviceProperties', {}).get('connectionState') != 'disconnected':
        print(x['identifier'], x['hardwareProperties']['udid'])
        break
")
EOF
    [ -n "${core:-}" ] || { echo "no connected device" >&2; exit 1; }

    echo "building for device…"
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme SomeonesHome -configuration Debug \
        -destination "id=$udid" -derivedDataPath "$DERIVED-device" \
        DEVELOPMENT_TEAM="$TEAM" -allowProvisioningUpdates build >/dev/null

    APP="$DERIVED-device/Build/Products/Debug-iphoneos/SomeonesHome.app"
    assert_fonts "$APP"

    xcrun devicectl device install app --device "$core" "$APP" >/dev/null
    xcrun devicectl device process launch --device "$core" "$BUNDLE_ID" >/dev/null

    python3 -c 'import time; time.sleep(5)'
    procs="$(xcrun devicectl device info processes --device "$core" 2>/dev/null || true)"
    case "$procs" in
        *SomeonesHome*) echo "running on the device. Look at the phone." ;;
        *) echo "the app is not running — it launched and died." >&2; exit 1 ;;
    esac
    exit 0
fi

udid="$(xcrun simctl list devices available \
    | awk -v name="$DEVICE" -F '[()]' '$0 ~ name "\\ \\(" { print $2; exit }')"
[ -n "$udid" ] || { echo "no available simulator named '$DEVICE'" >&2; exit 1; }

echo "building…"
xcodebuild -project iosApp/iosApp.xcodeproj -scheme SomeonesHome -configuration Debug \
    -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath "$DERIVED" build >/dev/null

APP="$DERIVED/Build/Products/Debug-iphonesimulator/SomeonesHome.app"

assert_fonts "$APP"

xcrun simctl boot "$udid" 2>/dev/null || true
xcrun simctl bootstatus "$udid" -b >/dev/null
xcrun simctl terminate "$udid" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install "$udid" "$APP"
xcrun simctl launch "$udid" "$BUNDLE_ID"

# A launch that crashed still prints a PID, so liveness is checked rather than assumed.
#
# POLLED, not a single look after a fixed sleep. The first version slept six seconds and
# checked once; it reported a dead app that was in fact running, with no crash report to back
# the claim up. A verification instrument that cries wolf gets ignored, and this one guards the
# failure that actually happened here — an app that builds, installs, launches and dies on its
# first frame.
#
# NO `| grep -q` HERE, and that is not style. `grep -q` exits the moment it matches, the
# producer takes SIGPIPE, and under `pipefail` the pipeline reports failure WHILE HAVING
# MATCHED. It is intermittent, because it depends on whether launchctl finished writing before
# grep let go — which is why the first version of this check passed once and then reported a
# running app as dead twice. `verify-guards.sh` documents this exact trap at the top of its
# own file; it cost that script a run where it called all five guards asleep while all five
# worked. Same repo, same mistake, six weeks apart.
alive=""
for _ in $(seq 1 20); do
    listing="$(xcrun simctl spawn "$udid" launchctl list 2>/dev/null || true)"
    case "$listing" in
        *"$BUNDLE_ID"*) alive=yes; break ;;
    esac
    python3 -c 'import time; time.sleep(1)'
done
[ -n "$alive" ] || {
    echo "the app is not running — it launched and died. Check:" >&2
    echo "  xcrun simctl launch --console-pty $udid $BUNDLE_ID" >&2
    exit 1
}

# Give the first frames time to land before the shutter.
python3 -c 'import time; time.sleep(3)'

xcrun simctl io "$udid" screenshot "$SHOT" 2>/dev/null
echo "running. screenshot: $SHOT"
