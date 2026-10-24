# Foodko

Food intake and weight tracker for a household: each user has their own meals and weight,
everyone shares one food database. Android only, Kotlin + Jetpack Compose,
data in Firebase (Google sign-in + Firestore). See `spec.md` for what it does and why.

## Run your own instance

The repo contains no Firebase identity; each clone binds to its own project through one
gitignored file. To set up yours:

1. **Tools:** Android SDK with platform `android-37.2` and build-tools 37 (`sdkmanager` or
   Android Studio), JDK 17, Node.js with `npm i -g firebase-tools`.
2. **Firebase project** at console.firebase.google.com (free Spark plan is enough):
   - Authentication → enable the **Google** sign-in provider.
   - Firestore → create a database in production mode.
   - Project settings → Your apps → add an **Android** app with package name `app.foodko`.
     Add the SHA-1 of your debug keystore (without it Google sign-in fails):
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1`
   - Download `google-services.json` into `app/`.
3. **Allow your users:** in Firestore create a collection `allowlist` with one document per
   Google account, the document id being the e-mail address (e.g. `alice@example.com`); any
   field such as `name` will do. Only these accounts can use the app.
4. **Deploy the rules:** `firebase login`, `firebase use <your-project-id>`,
   `firebase deploy --only firestore:rules`.
5. `./gradlew installDebug` with a phone on USB, or see *Updating the phones* below.

Nothing else refers to a specific project: the release script reads the app id from
`google-services.json`, and the mock build (`scripts/emulators.sh`) uses generic example users.

## Build and install

```sh
./gradlew installDebug      # phone connected over USB with USB debugging on
./gradlew testDebugUnitTest
```

## Updating the phones

```sh
scripts/release.sh "what changed"
```

Builds a release APK and pushes it to the phones via Firebase App Distribution (tester group
`family`, override with `FOODKO_TESTER_GROUP`). Create the group and add testers once:
`firebase appdistribution:group:create "Family" family` and
`firebase appdistribution:testers:add a@example.com b@example.com --group-alias family`.
Version numbers come from git, so commit first.

## Testing without a phone or a Firebase project

`scripts/emulators.sh` starts local Auth and Firestore emulators and allowlists two fake users;
`./gradlew installDebug -PfoodkoMock=true` builds an app that signs in as them with one tap and
seeds demo data. See `CLAUDE.md` for the details.
