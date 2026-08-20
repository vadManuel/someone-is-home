#!/usr/bin/env bash
# Build the app, install it on a simulator, launch it, and screenshot one screen.
#
# WHAT THIS CAN AND CANNOT SHOW. It shows that the ported screens LAY OUT on a phone-shaped
# panel at phone density, which no desktop preview can: a Mac window has no notch, no Dynamic
# Island and no safe area, and all three change where things land.
#
# It shows NOTHING about the game. The Simulator has no BLE, no torch, no camera and no
# haptics, which is every input this game has. Nothing that touches those may be signed off
# from here — that needs physical devices, plural, in a dark room.
set -euo pipefail
cd "$(dirname "$0")"

# The Bash tool's shell does not pick up mise; the user's terminal does.
export PATH="$HOME/.local/share/mise/shims:$PATH"

DEVICE="${APP_DEVICE:-iPhone 17 Pro}"
BUNDLE_ID="home.someoneshome.app"
DERIVED="${APP_DERIVED_DATA:-/tmp/someones-home-dd}"
SHOT="${APP_SCREENSHOT:-/tmp/someones-home.png}"

udid="$(xcrun simctl list devices available \
    | awk -v name="$DEVICE" -F '[()]' '$0 ~ name "\\ \\(" { print $2; exit }')"
[ -n "$udid" ] || { echo "no available simulator named '$DEVICE'" >&2; exit 1; }

echo "building…"
xcodebuild -project iosApp/iosApp.xcodeproj -scheme SomeonesHome -configuration Debug \
    -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath "$DERIVED" build >/dev/null

APP="$DERIVED/Build/Products/Debug-iphonesimulator/SomeonesHome.app"

# Assert the fonts made it in. Without this the app installs, launches, and dies on its first
# frame with MissingResourceException — and "BUILD SUCCEEDED" says nothing about it, because
# the fonts live in :ui's composeResources and a static framework does not carry them.
find "$APP" -name 'Silkscreen-Regular.ttf' | grep -q . || {
    echo "the pixel fonts are not in the bundle; the app will die on its first frame" >&2
    exit 1
}

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
