#!/usr/bin/env bash
# Proves each architectural guard FAILS when violated.
#
# `./gradlew check` passing only shows the guards did not fire. It cannot distinguish a guard
# that found nothing from a guard that is asleep — and one of these was asleep when written: the
# ui/:core boundary reported clean while ui depended on core, because Gradle renders a project
# identity as `project ':core'` with quotes and the comparison built it without them. It passed
# green and meant nothing.
#
# So this introduces each violation deliberately and asserts the build rejects it. Run it after
# touching anything in buildSrc/.
#
# Prior art is NOT tested here — `.git/hooks/pre-commit` owns that rule, and a probe file
# containing the words it blocks could not be committed anyway.
# NOT pipefail: every command here is EXPECTED to fail, and with pipefail a pipeline inherits
# gradlew's non-zero exit even when the grep matched. The first version of this script reported
# all five guards asleep while all five were working correctly.
set -u

cd "$(dirname "$0")"
export PATH="$HOME/.local/share/mise/shims:$PATH"

PROBE="model/src/commonMain/kotlin/home/someoneshome/model/_Probe.kt"
pass=0; fail=0

expect_failure() { # description, task, expected-message
    local out rc
    out="$(./gradlew "$2" 2>&1)"; rc=$?
    # BOTH conditions. Matching the message alone is not enough: a guard downgraded from
    # `throw GradleException` to `logger.warn` still prints its message word for word, and this
    # script would have recorded PASS while the build sailed through green. The whole claim here
    # is that the guard FAILS the build, so the exit code is the assertion.
    if [ "$rc" -ne 0 ] && printf '%s' "$out" | grep -q "$3"; then
        echo "  PASS  $1"; pass=$((pass + 1))
    elif [ "$rc" -eq 0 ]; then
        echo "  FAIL  $1 — build SUCCEEDED; the guard did not fail the build"; fail=$((fail + 1))
    else
        echo "  FAIL  $1 — build failed but not with the expected message"; fail=$((fail + 1))
    fi
}

# Refuse to run against a tree that is already failing.
#
# This is not paranoia. An earlier version restored with `git checkout` on untracked files, which
# silently did nothing and left a deliberate violation in core/build.gradle.kts. The next run
# then backed THAT up as its pristine copy and faithfully restored the violation afterwards. A
# backup taken from a poisoned tree launders the poison.
if ! ./gradlew check -q >/dev/null 2>&1; then
    echo "refusing to run: ./gradlew check is already failing." >&2
    echo "Fix the tree first — otherwise the backups below capture the breakage." >&2
    exit 1
fi

# File backups, not `git checkout` — these files may be untracked, and a restore that silently
# does nothing would leave a deliberate violation sitting in the tree.
BAK="$(mktemp -d)"
cp core/build.gradle.kts "$BAK/core"
cp ui/build.gradle.kts "$BAK/ui"
restore() {
    rm -f "$PROBE"
    cp "$BAK/core" core/build.gradle.kts
    cp "$BAK/ui" ui/build.gradle.kts
    rm -rf "$BAK"
    # Prove the tree handed back is actually clean, rather than assuming the copies landed.
    if ! ./gradlew check -q >/dev/null 2>&1; then
        echo "WARNING: tree is NOT clean after restore — inspect before committing." >&2
    fi
}
trap restore EXIT

echo "1/5  vocabulary lint — a mechanic synonym in code"
printf 'package home.someoneshome.model\nval sabotageCount = 2\n' > "$PROBE"
expect_failure "mechanic synonym rejected" ":model:vocabularyLint" "use Egress"
rm -f "$PROBE"

echo "2/5  vocabulary lint — death framing"
printf 'package home.someoneshome.model\nval victimSeat = 3\n' > "$PROBE"
expect_failure "death framing rejected" ":model:vocabularyLint" "revoked player"
rm -f "$PROBE"

echo "3/5  redaction lint — @Serializable without @ClientFacing"
cat > "$PROBE" <<'KT'
package home.someoneshome.model
import kotlinx.serialization.Serializable
@Serializable
data class Observation(val trueCount: Int)
KT
expect_failure "unmarked wire type rejected" ":model:redactionLint" "not @ClientFacing"
rm -f "$PROBE"

echo "4/5  boundary — coroutines reaching core"
python3 - <<'PY'
import pathlib
f = pathlib.Path("core/build.gradle.kts")
f.write_text(f.read_text().replace(
    'implementation(project(":model"))',
    'implementation(project(":model"))\n        implementation(libs.kotlinx.coroutines.core)'))
PY
expect_failure "core stays pure" ":core:boundaryCheck" "Module boundary violated in 'core'"
cp "$BAK/core" core/build.gradle.kts

echo "5/5  boundary — :core reaching ui"
python3 - <<'PY'
import pathlib
f = pathlib.Path("ui/build.gradle.kts")
f.write_text(f.read_text().replace(
    'implementation(project(":model"))',
    'implementation(project(":model"))\n        implementation(project(":core"))'))
PY
expect_failure "ui cannot see the rules" ":ui:boundaryCheck" "Module boundary violated in 'ui'"
cp "$BAK/ui" ui/build.gradle.kts

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
