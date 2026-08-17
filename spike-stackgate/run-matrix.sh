#!/usr/bin/env bash
# Runs a sequence of stack-gate runs unattended, one fresh process each.
#
# Why a fresh process per run, rather than tapping through the app's own menu:
# Kotlin/Native schedules collections against heap-size thresholds, so a run that inherits a
# grown heap from the previous run collects at a different cadence. Comparing runs from one
# process makes RUN ORDER an uncontrolled variable in exactly the dose-response this spike
# exists to measure. Metal pipeline caches and Compose's internal caches carry over too, which
# is what makes COLD meaningless anywhere but first.
set -euo pipefail

cd "$(dirname "$0")"
export PATH="$HOME/.local/share/mise/shims:$PATH"

BUNDLE_ID="home.someoneshome.stackgate"
DEVICE_NAME="${SPIKE_DEVICE_NAME:-vadManuel-Phone}"

# Seconds between runs, to shed heat. A full matrix is hours of continuous rendering; an LTPO
# panel throttles when the phone gets hot and a throttled run is not comparable to a cool one.
# The app records thermal state in every report so this can be checked rather than assumed.
COOLDOWN="${SPIKE_COOLDOWN:-90}"

# Generous: VOLUME is ~23 min of trials. A run that exceeds this is stuck, not slow.
TIMEOUT="${SPIKE_TIMEOUT:-2700}"

die() { echo "error: $*" >&2; exit 1; }

resolve_device() {
    xcrun devicectl list devices 2>/dev/null \
        | awk -v name="$DEVICE_NAME" '$1 == name { print $3; exit }'
}

DEVICE="$(resolve_device)"
[[ -n "$DEVICE" ]] || die "device '$DEVICE_NAME' not found"

app_running() {
    local tmp
    tmp="$(mktemp /tmp/spike-proc.XXXXXX.json)"
    xcrun devicectl device info processes --device "$DEVICE" --json-output "$tmp" >/dev/null 2>&1 || true
    local found
    found="$(python3 -c '
import json, sys
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    print("unknown"); sys.exit(0)
procs = d.get("result", {}).get("runningProcesses", [])
print("yes" if any("StackGate" in str(p.get("executable","")) for p in procs) else "no")
' "$tmp")"
    rm -f "$tmp"
    [[ "$found" == "yes" ]]
}

run_one() {
    local label="$1" started elapsed
    echo
    echo "════ $label ════"
    started=$(date +%s)

    # --terminate-existing guarantees the fresh process; the app exits itself when done, which
    # is how completion is detected without polling for files.
    xcrun devicectl device process launch \
        --device "$DEVICE" \
        --terminate-existing \
        "$BUNDLE_ID" --run "$label" --exit-when-done >/dev/null 2>&1 \
        || die "failed to launch $label"

    sleep 10   # let it come up before we start asking whether it is gone
    while app_running; do
        elapsed=$(( $(date +%s) - started ))
        if (( elapsed > TIMEOUT )); then
            echo "  TIMED OUT after ${elapsed}s — leaving the app running for inspection"
            return 1
        fi
        printf "\r  running… %ds" "$elapsed"
        sleep 15
    done
    elapsed=$(( $(date +%s) - started ))
    printf "\r  done in %ds%-20s\n" "$elapsed" ""
}

RUNS=("$@")
if [[ ${#RUNS[@]} -eq 0 ]]; then
    # COLD first, always: it measures the first-run shader stall, and anything rendered before
    # it has already warmed the pipeline caches it is trying to observe.
    RUNS=(COLD VOLUME_CPU_CONTROL VOLUME_CRUSH VOLUME_HEAVY VOLUME VOLUME_CONTROL LONG_IDLE ALLOC_PROBE)
fi

echo "device   $DEVICE_NAME ($DEVICE)"
echo "runs     ${RUNS[*]}"
echo "cooldown ${COOLDOWN}s between runs"

FAILED=()
for i in "${!RUNS[@]}"; do
    run_one "${RUNS[$i]}" || FAILED+=("${RUNS[$i]}")
    if (( i < ${#RUNS[@]} - 1 )); then
        echo "  cooling ${COOLDOWN}s…"
        sleep "$COOLDOWN"
    fi
done

echo
echo "════ pulling results ════"
./run-spike.sh pull

if (( ${#FAILED[@]} )); then
    echo
    echo "runs that did not complete: ${FAILED[*]}"
fi
