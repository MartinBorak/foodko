#!/usr/bin/env bash
# Everything CI runs (.github/workflows/ci.yml), locally, in one go:
# JVM unit tests, Android lint, Firestore rules tests.
#
#   scripts/check.sh            # all of it
#   scripts/check.sh --fast     # unit tests only
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew --quiet testDebugUnitTest
echo "unit tests: ok"

if [[ "${1:-}" == "--fast" ]]; then
  exit 0
fi

./gradlew --quiet lintDebug
echo "lint: ok (app/build/reports/lint-results-debug.html)"

scripts/rules-test.sh
