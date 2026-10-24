# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Foodko: a private Android app for two people (Martin and his wife) to log meals and weight and see
intake/weight statistics. Kotlin + Jetpack Compose + Material 3, Firebase Auth (Google) + Cloud
Firestore. Not on any store; installed over USB on a Pixel 9 Pro XL and a Pixel 8a.

**`spec.md` is the source of truth** for behaviour, data model, screens and priorities. Read it
before changing behaviour; update it when a decision changes. Section 12 records resolved questions.

## Commands

No Android Studio on this machine; everything runs through the Gradle wrapper from the terminal.

```sh
./gradlew assembleDebug            # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug             # build + install on the USB-connected phone (adb)
./gradlew testDebugUnitTest        # all JVM unit tests
./gradlew testDebugUnitTest --tests 'app.foodko.domain.NutritionTest'   # one test class
./gradlew lintDebug                # Android lint, report in app/build/reports/lint-results-debug.html
adb devices                        # adb is on PATH via Homebrew command-line tools
```

Unit tests live under `app/src/test/kotlin` and target pure Kotlin code (nutrition and statistics
maths, number formatting, redirect resolution). There are no instrumented or UI tests.

Firestore security rules: `firestore.rules`, deployed with `firebase deploy --only firestore:rules`
(Firebase CLI; `.firebaserc` is gitignored, run `firebase use <project-id>` once per machine).
Access is granted by documents in the `allowlist` collection, keyed by e-mail address and
maintained in the Firebase console, so **no personal data lives in the repo**. Keep it that way.

### Releasing to the phones

```sh
scripts/release.sh "what changed"      # builds release APK, uploads to Firebase App Distribution
```

Both phones are testers in the App Distribution group `family` and get a notification with an
install link; no cable. The script refuses to run with uncommitted changes because
`versionCode` is the git commit count and `versionName` is `0.1.<count> (<sha>)`, computed in
`app/build.gradle.kts`. Needs `firebase login` once on this Mac. `installDebug` over USB remains
the quick path for iterating on one phone.

### Testing the UI locally (mock build)

Real code against the local Firebase emulators, fake accounts, seeded demo data; never touches
the real project.

```sh
scripts/emulators.sh                                   # terminal 1: Auth+Firestore emulators (JDK 21), allowlists Alice & Bob; UI at http://localhost:4000
$ANDROID_HOME/emulator/emulator -avd foodko &          # Pixel 8a AVD, Android 16 Play image
./gradlew installDebug -PfoodkoMock=true               # BuildConfig.MOCK = true
adb shell am start -n app.foodko/.MainActivity
adb exec-out screencap -p > /tmp/s.png                 # look at the screen
```

The mock sign-in screen offers Alice / Bob (allowlisted by the script) and Stranger (must be
refused by the rules). On first sign-in with an empty database `DemoSeeder` writes three weeks of
meals and weigh-ins. Emulator data is in-memory: every start is a clean slate. Mock wiring lives in
`FoodkoApp` (`Mock` object), `AuthRepository.signInMock`, `SignInScreen`, `UserSession.init`.
A plain `installDebug` (no property) talks to production Firebase and needs a real Google account.
Emulator quirks: `adb shell input text` drops spaces (use `%s`); the Back key is swallowed by
Gboard in hardware-keyboard mode, use the in-app back arrow.

## Toolchain quirks

- SDK comes from Homebrew `android-commandlinetools` (`ANDROID_HOME` is set in the shell), not
  from Android Studio. Install platforms/build-tools with
  `"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-37.2"`.
- `compileSdk = 37` + `compileSdkMinor = 2`: current AndroidX releases refuse to compile against
  36. Platform packages are versioned `android-37.0/37.1/37.2`, so keep both numbers in sync with an
  installed platform.
- AGP 9 has built-in Kotlin: there is **no** `org.jetbrains.kotlin.android` plugin, only
  `org.jetbrains.kotlin.plugin.compose`. Don't add KGP back.
- Release builds are signed with the **debug** keystore on purpose (`app/build.gradle.kts`), so one
  SHA-1 (`C8:23:03:61:…:DD:94`, from `~/.android/debug.keystore`) covers both build types in Firebase.
  Building from another machine means a different key, a reinstall on both phones, and a new
  fingerprint in the Firebase console.
- `app/google-services.json` is gitignored and must be downloaded from the Firebase console
  (project settings → Your apps → Foodko). Without it the Google services plugin fails the build.
- Versions are pinned in `gradle/libs.versions.toml`. Firebase KTX artifacts are gone; the
  `Firebase.firestore` / `firestoreSettings {}` style extensions live in the main artifacts.

## Architecture

Single module `:app`, package `app.foodko`. Manual dependency wiring (no Hilt/Koin): an
`AppContainer` created in `FoodkoApp` owns the repositories; ViewModels get them through a factory.

- `data/model` — Firestore document classes (kept by ProGuard rules; field names are the Firestore
  field names, don't rename casually).
- `data/` repositories — thin Firestore wrappers exposing `Flow`s via snapshot listeners. Firestore
  persistent cache is on, so writes work offline and the UI never waits for the network.
- `domain/` — pure Kotlin: nutrition maths, daily aggregation, statistics, food-redirect resolution.
  This is what the unit tests cover.
- `ui/<feature>` — one package per screen with its ViewModel; `ui/theme`, `ui/navigation`.

Firestore layout (see spec §6): `foods/{id}` is shared by both users; everything private is under
`users/{uid}` (`meals`, `weights`, settings fields on the user doc). Rules enforce that a user only
reads/writes their own subtree, which has two consequences that shape the code:

1. **Foods are never deleted**: `archived` hides, `mergedInto` redirects. Every food lookup must
   resolve `mergedInto` chains. Merging rewrites only the *current* user's meals; the other user's
   app rewrites its own meals when it next sees a redirect ("self-healing", spec Flow 7).
2. A phone cannot see the other user's data, so nothing may assume a global count of references.

Nutrition is never stored on meals; it is computed from the referenced food at display time.

UI language (English/Slovak) is switched with the platform per-app locale API (`LocaleManager`,
hence `minSdk 33`) and mirrored in the user's Firestore settings. All user-facing strings go in
`res/values/strings.xml` **and** `res/values-sk/strings.xml`; Martin reviews the Slovak.
