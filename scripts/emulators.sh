#!/usr/bin/env bash
# Start the Firebase Auth + Firestore emulators for the mock build and allowlist the fake users.
#
#   scripts/emulators.sh            # foreground; Ctrl-C stops everything
#
# Pair with: ./gradlew installDebug -PfoodkoMock=true
# Emulator data is in-memory; every start is a clean slate (the app re-seeds demo data).
set -euo pipefail

cd "$(dirname "$0")/.."

# The Firestore emulator needs JDK 21+; Gradle keeps using whatever JAVA_HOME says otherwise.
if [[ -d /opt/homebrew/opt/openjdk@21 ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# The app sends the project id from google-services.json, and the emulator only applies the
# rules to that project, so use the same id here. Emulators never reach production regardless.
PROJECT="$(python3 -c 'import json; print(json.load(open("app/google-services.json"))["project_info"]["project_id"])')"
FIRESTORE="http://127.0.0.1:8080/v1/projects/$PROJECT/databases/(default)/documents"

firebase emulators:start --only auth,firestore --project "$PROJECT" &
EMULATOR_PID=$!
trap 'kill $EMULATOR_PID 2>/dev/null || true' EXIT INT TERM

for _ in $(seq 1 60); do
  curl -sf -o /dev/null "$FIRESTORE/allowlist" 2>/dev/null && break
  sleep 1
done

# Allowlist the fake users the mock sign-in screen offers (see Mock.users in FoodkoApp.kt).
for entry in "Alice:alice@example.com" "Bob:bob@example.com"; do
  name="${entry%%:*}"; email="${entry#*:}"
  curl -sf -o /dev/null -X PATCH -H "Authorization: Bearer owner" -H "Content-Type: application/json" \
    "$FIRESTORE/allowlist/$email" -d "{\"fields\":{\"name\":{\"stringValue\":\"$name\"}}}" \
    && echo "allowlisted $email"
done

wait $EMULATOR_PID
