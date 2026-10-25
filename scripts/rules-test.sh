#!/usr/bin/env bash
# Run the Firestore security rules tests (rules-test/) against a throwaway Firestore emulator.
#
#   scripts/rules-test.sh
#
# Needs Node.js and a JDK 21+ for the emulator. Uses a "demo-" project id, so no Firebase login,
# no .firebaserc and no google-services.json are required; nothing reaches a real project.
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -d /opt/homebrew/opt/openjdk@21 ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [[ ! -d rules-test/node_modules ]]; then
  npm ci --prefix rules-test
fi

exec rules-test/node_modules/.bin/firebase emulators:exec \
  --only firestore \
  --project demo-foodko-rules \
  "npm test --prefix rules-test"
