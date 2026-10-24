#!/usr/bin/env bash
# Build a release APK and push it to both phones through Firebase App Distribution.
#
#   scripts/release.sh "What changed in this build"
#
# Requirements (one-time): `firebase login` on this machine; testers in the "family" group.
# The release is signed with the debug keystore on purpose (see CLAUDE.md), so it installs
# over the existing app without an uninstall.
set -euo pipefail

cd "$(dirname "$0")/.."

NOTES="${1:-}"
if [[ -z "$NOTES" ]]; then
  echo "usage: scripts/release.sh \"release notes\"" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree has uncommitted changes; commit first so the version number means something." >&2
  exit 1
fi

# The Firebase Android app id of whichever project google-services.json belongs to.
APP_ID="$(python3 -c 'import json; print(json.load(open("app/google-services.json"))["client"][0]["client_info"]["mobilesdk_app_id"])')"
GROUP="${FOODKO_TESTER_GROUP:-family}"   # App Distribution tester group alias
APK="app/build/outputs/apk/release/app-release.apk"
VERSION="0.1.$(git rev-list --count HEAD) ($(git rev-parse --short HEAD))"

echo "Building release $VERSION …"
./gradlew --quiet assembleRelease

echo "Uploading to App Distribution …"
firebase appdistribution:distribute "$APK" \
  --app "$APP_ID" \
  --groups "$GROUP" \
  --release-notes "$VERSION — $NOTES"

echo "Done. Both phones get a notification/e-mail from Firebase App Distribution."
