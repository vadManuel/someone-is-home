#!/usr/bin/env bash
# Redraws the app icon into the asset catalog the iOS build consumes.
#
#   ./tools/appicon/icon.sh            the shipped set, plus the check renders
#   ./tools/appicon/icon.sh --raw-scanlines    the same, with the scan lines left literal and
#                                                  proportional at every size — the "before" of
#                                                  the pair beside the worklog
#
# The generated PNGs ARE COMMITTED. They are build output in the sense that this script makes
# them, and source in the sense that Xcode reads them and nothing in the Gradle build knows they
# exist — an uncommitted catalog is an app that ships with a white tile the first time somebody
# builds it on a clean clone. `AppIcon.java` is what makes them reviewable: the diff that matters
# is in the geometry, and the pixels are its output.
#
# Run it after ANY edit to AppIcon.java and commit both.
set -euo pipefail
cd "$(dirname "$0")/../.."

# The Bash tool's shell does not pick up mise; the user's terminal does. Same line as run-app.sh,
# and for the same reason.
export PATH="$HOME/.local/share/mise/shims:$PATH"

SET="iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
CHECKS="_bmad-output/overnight/icon"

# Single-file source-code launcher: no build step, no module, nothing to keep in sync. The JDK is
# already a hard requirement of this repo — the Gradle wrapper needs one.
java tools/appicon/AppIcon.java "$SET" --checks "$CHECKS" "$@"

echo "catalog:  $SET"
echo "checks:   $CHECKS"
