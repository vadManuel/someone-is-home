#!/usr/bin/env bash
# Drive the real app on a simulator with a mock of the human using it.
#
#   ./e2e.sh                    the whole suite, once, on the default simulator
#   ./e2e.sh --repeat 5         five consecutive runs; any failure fails the script
#   ./e2e.sh --only <TestName>  one test by name
#   ./e2e.sh --diagnose         the geometry probe (see FRAME_LIFT in HumanMock.swift)
#   E2E_DEVICE="iPhone Air" ./e2e.sh
#
# THIS IS THE SLOW SUITE AND IT IS NOT IN `check`, BY DESIGN. It builds an app, boots a
# simulator, installs, launches and drives it; a full pass is minutes where the unit suites are
# seconds. It is the release gate, run deliberately, and `gradlew check` stays fast enough to run
# constantly. `./gradlew e2e` is the same thing from Gradle and is likewise not wired into check.
#
# WHAT IT CAN AND CANNOT PROVE. The Simulator has no BLE, no torch, no camera and no haptics, so
# this drives the walk up to the perimeter and the shapes of the controls, and it signs off no
# part of the game itself. The permissions gate is *supposed* to refuse here and one of the tests
# asserts that it does. Read run-app.sh's header; none of it stopped being true because the
# driving got automatic.
set -euo pipefail
cd "$(dirname "$0")"

# The Bash tool's shell does not pick up mise; the user's terminal does.
export PATH="$HOME/.local/share/mise/shims:$PATH"

DEVICE="${E2E_DEVICE:-iPhone 17 Pro}"
DERIVED="${E2E_DERIVED_DATA:-/tmp/someones-home-dd-e2e}"
SCHEME=SomeonesHomeE2E
REPEAT=1
ONLY=""
DIAGNOSE=0

while [ $# -gt 0 ]; do
    case "$1" in
        --repeat) REPEAT="$2"; shift 2 ;;
        --only) ONLY="$2"; shift 2 ;;
        --diagnose) DIAGNOSE=1; ONLY=testDiagnoseTheGeometry; shift ;;
        *) echo "unknown argument '$1'" >&2; exit 1 ;;
    esac
done

args=(
    -project iosApp/iosApp.xcodeproj
    -scheme "$SCHEME"
    -configuration Debug
    -sdk iphonesimulator
    -destination "platform=iOS Simulator,name=$DEVICE"
    -derivedDataPath "$DERIVED"
)
[ -n "$ONLY" ] && args+=(-only-testing:"SomeonesHomeUITests/HumanMockTests/$ONLY")
[ "$DIAGNOSE" -eq 1 ] && export TEST_RUNNER_E2E_DIAGNOSE=1

# Built once, then run N times. `build-for-testing` and `test-without-building` are what make a
# repeat run measure the app rather than the compiler — and flakiness is the number the repeat
# exists to produce, so the thing being repeated has to be the same bits every time.
echo "building for testing (${DEVICE})…"
xcodebuild "${args[@]}" build-for-testing >/dev/null

failed=0
for run in $(seq 1 "$REPEAT"); do
    echo "run ${run} of ${REPEAT}…"
    log="$(mktemp /tmp/someones-home-e2e.XXXXXX.log)"
    if xcodebuild "${args[@]}" test-without-building >"$log" 2>&1; then
        # NOT read off the exit code alone. This project has been lied to by an exit code before;
        # the count of passing cases is read out of the run's own output and printed, so a suite
        # that ran zero tests cannot report success.
        cases="$(grep -c "' passed (" "$log" || true)"
        echo "  passed — $cases test case(s)"
        [ "$cases" -gt 0 ] || { echo "  a green run with no test cases in it" >&2; failed=1; }
    else
        echo "  FAILED — $log" >&2
        grep -E "error:|XCTAssert" "$log" | head -10 >&2 || true
        failed=1
    fi
    grep -E "^=== " "$log" || true
    rm -f "$log"
done

[ "$failed" -eq 0 ] || { echo "e2e: not green." >&2; exit 1; }
echo "e2e: $REPEAT/$REPEAT green."
