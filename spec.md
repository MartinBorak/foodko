# Foodko — Spec

Food intake and weight tracker for a household: private per-user history, shared food database.

> **Design decisions** (1–6 reviewed and approved; 7–8 are new, please review):
>
> 1. **Kotlin + Jetpack Compose + Material 3.** Today's standard Android SDK UI toolkit, no cross-platform layer.
> 2. **Firebase (Firestore + Firebase Auth with Google Sign-In) as the backend.** Data linked to the Google account, survives phone changes, no server to run, free tier covers a household forever. Needs a Firebase project created in your Google account (I'll walk you through it; \~10 minutes).
> 3. **Sign-in restricted to an allowlist of Google accounts.** Otherwise anyone with the APK could create an account in your project.
> 4. **Meals reference the food by id, nutrition is computed on the fly.** Editing a food (e.g. fixing a typo in kcal) retroactively corrects history.
> 5. **A daily kcal target per person**, optional, editable in settings; drawn as a reference line on the charts.
> 6. **Metric only** (kg, g). Decimal input accepts both `,` and `.`.
> 7. **Merging duplicate foods works through a redirect, not a direct rewrite of both histories.** The privacy rules mean a phone cannot read or write other users' meals, and there is no server-side code on the free plan. So a merge (a) rewrites *your* meals immediately, (b) marks the duplicate food as `mergedInto → survivor`, and (c) every other user's app rewrites their meals the next time it syncs. In between, every read already resolves the redirect, so everyone sees correct data at all times. See Flow 7.
> 8. **Foods are archived, not deleted.** For the same reason, a phone can't know whether the *other* user still has meals pointing at a food, so hard delete is unsafe. "Delete" hides the food from lists and the picker; existing meals keep working.

## 1. Summary

**One-liner:** An Android app for a household to log weight and meals and watch calorie and nutrition intake and weight trends over time, with private per-user history and a shared food database.

**Problem it solves:** Eating healthier and losing weight needs honest, low-friction tracking: what we ate, how much, and what it did to our weight. The app makes logging a meal take a few seconds and keeps all history in the cloud so a phone change loses nothing.

**Why existing options don't work:** Commercial trackers are ad-ridden, subscription-gated, push social features, and own the data. We want a minimal tool with our own food database and our own data.

## 2. Users


| User type | What they want to get done                        | How often / where                        |
| --------- | ------------------------------------------------- | ---------------------------------------- |
| Household member | Log meals and weight, see intake and weight trend | Several times a day, on their own Android phone |


Any number of allowlisted users (a household). All are equal; there is no admin role.

**Roles and permissions:**

- A user sees and edits **only their own** meals, weights and settings.
- The **food database is shared**: both users can add, edit and see all foods. No ownership checks on foods for the prototype (we trust each other).
- Only allowlisted Google accounts can sign in.

## 3. Key flows

### Flow 1: First launch / sign in

1. Open app → single "Sign in with Google" button.
2. Pick Google account → Firebase Auth creates/finds the user.
3. If the account is not on the allowlist → "No access" message, stay signed out.
4. Land on **Today**. All data syncs down from Firestore automatically. Stays signed in until explicitly signed out.

**Edge cases:** No network on first launch → show error, retry. On a replacement phone, step 4 restores everything with no extra steps.

### Flow 2: Log a meal (the most frequent action, must be fast)

1. On **Today**, tap the **+ Meal** button.
2. **Food picker** opens: search field with keyboard already open, list below shows *recently used foods* first, then all foods filtered by the typed text (case- and diacritics-insensitive substring match on label).
3. Tap a food → **Meal form**: food name, kcal/100g shown read-only; **weight in grams** (numeric field, focused); **timestamp** defaulting to now, tappable to change date and time. Live preview of resulting kcal / protein / carbs / fat / fibre for the entered weight.
4. Tap **Save** → back on Today, the meal appears in the list, daily totals update.

**Edge cases:**

- Food doesn't exist yet → "Create new food" entry at the bottom of the picker results → Flow 3, then return to step 3 with the new food preselected.
- Empty or zero weight → Save disabled.
- Offline → save goes to the local Firestore cache and syncs later; UI behaves identically.

### Flow 3: Add a food to the shared database

1. From **Foods** tab (**+ Food**) or from the food picker ("Create new food").
2. Form: **Label** (required, text), **kcal per 100 g** (required, float), **Carbs**, **Fat**, **Protein**, **Fibre** (optional floats, grams per 100 g), **Note** (optional free text: brand, shop, how it was cooked; shown under the label in the Foods list and the picker).
3. Save → food is in the shared database, visible to both users immediately.

**Edge cases:**

- Label already exists (case-insensitive) → warn, allow saving anyway (e.g. two brands of yoghurt), but show the existing one.
- Negative numbers → rejected. Macros summing to more than 100 g → warning, not blocked.

### Flow 3a: Work out a cooked dish's values from its ingredients

A one-shot calculator inside the food form; the result is an ordinary food, nothing else is stored
(decision 8).

1. In the **Food form**, tap **Calculate from ingredients…**.
2. **Ingredients** view: tap **Add ingredient** → the same food picker as for meals (recent, search,
   "Create new food"); enter the raw weight in grams of each ingredient; enter the **weight of the
   finished dish** (differs from the sum: water added or evaporated).
3. Live result: sum of the ingredients' nutrition scaled to 100 g of the finished dish, shown as
   kcal and the four macros, plus the raw total (grams and kcal) for a sanity check.
4. Tap **Use these values** → back in the form with kcal and macros filled in; edit or save as usual.

**Edge cases:**

- Any ingredient lacks a macro → that macro of the result is empty ("–"), not a silently low number;
  it can be typed in by hand.
- Back (arrow or system) steps picker → ingredients → form; the ingredient list survives until the
  form is left.
- Creating a new food from the ingredient picker opens a second food form on top; on save the new
  food is added as an ingredient.

### Flow 4: Log weight

1. **Weight** tab → **+ Weight** button (or the number field is right on the screen).
2. Enter weight in kg (float, one decimal is the norm), timestamp defaults to now, editable.
3. Save → appears in the history list and the graph updates.

**Edge cases:** Several entries on one day are allowed; the graph plots all of them. Obvious typos (e.g. &lt; 30 kg or &gt; 300 kg) → confirmation prompt.

### Flow 5: Review and edit history

1. **Today** has a date header with ← → arrows and a calendar picker, and a horizontal swipe also steps a day (right = previous, left = next, never past today); it shows any day, not just today. The day view lists meals in time order with grams and kcal per item and the day's totals at the top.
2. Tap a meal → the same Meal form as Flow 2, prefilled → change food, grams or timestamp → Save. Delete button in the form, behind a confirmation dialog.
3. Weight entries: tap in the Weight history list → edit value/timestamp or delete.
4. Foods: tap in the Foods list → edit any field. "Delete" archives the food: it disappears from the list and the picker, but meals that reference it (of either user) keep showing it and its nutrition. If any of *my* meals use it, the confirmation says how many. Archived foods can be shown via a filter in the Foods list and un-archived from the form.

### Flow 6: Check progress

1. **Stats** tab, with a range selector: 7 days / 30 days / 90 days / all.
2. See the charts and numbers listed in section 4 (Stats). Everything is per signed-in user only.

### Flow 7: Merge duplicate foods

Scenario: two users created "Skyr vanilkový" separately, with slightly different values.

1. **Foods** tab → long-press a food to enter selection mode → tick the duplicates (2 or more) → **Merge** action in the top bar.
2. **Merge screen**: the selected foods are listed with their values side by side. One is pre-selected as the **survivor** (the one most used in my own meals, ties broken by oldest). Tapping another row makes it the survivor.
3. Below is a normal Food form prefilled from the survivor, so the final label and values can be corrected in the same step.
4. **Save** performs, in one atomic Firestore batch:
   - survivor food updated with the form values;
   - every other selected food gets `mergedInto = survivorId` (it stays in the database as a redirect and is hidden everywhere);
   - all of *my* meals whose `foodId` is one of the merged foods are rewritten to `survivorId`;
   - `lastUsedFoodIds` in my settings is rewritten the same way.
5. **Self-healing on the other phone:** whenever the app syncs the foods collection and notices a food with `mergedInto`, it rewrites that user's own meals that still point at it (following chains, e.g. A→B→C) in a batch. Until that happens, every place that resolves a `foodId` follows `mergedInto` first, so her history already shows the survivor's label and values.

**Edge cases:**

- Merging a food that is itself already a redirect (possible if phones were offline) → chains are followed on read; the self-healing step flattens them.
- Merging while offline → the batch is queued; works once online. The other phone cannot see the merge until then.
- Undo merge → Later (would need a per-merge log). For the prototype, a wrong merge is fixed by creating the food again and re-pointing the few affected meals by hand.

## 4. Features and priority


| Feature                                                                                   | Priority     | Notes                                                                   |
| ----------------------------------------------------------------------------------------- | ------------ | ----------------------------------------------------------------------- |
| Google sign-in, allowlist of accounts                                                     | MVP          |                                                                         |
| All data stored in Firestore; full restore on a new phone                                 | MVP          |                                                                         |
| Offline logging with background sync                                                      | MVP          | Comes for free with Firestore's offline cache; just must not be broken. |
| Food database: add / edit / list / search                                                 | MVP          | Shared between both users.                                              |
| Log meal: food picker (recent + search), grams, timestamp                                 | MVP          | Target: under 10 seconds for a known food.                              |
| Day view with meals, per-item kcal, daily totals (kcal + 4 macros)                        | MVP          |                                                                         |
| Edit / delete any meal, weight entry; edit / archive any food                             | MVP          | Foods are archived, not deleted (decision 8).                           |
| Merge duplicate foods, history rewritten automatically                                    | MVP          | Redirect + self-healing (decision 7, Flow 7).                           |
| UI language switch: Slovak / English                                                      | MVP          | Stored in user settings, applied on launch. I'll draft the Slovak strings; you review them. |
| Weight: add entry, history list, line chart over time                                     | MVP          |                                                                         |
| Daily kcal target (optional, per user) shown as a line on charts and as "remaining today" | MVP          | One number in Settings. On Today: ring (green share eaten on a grey track, full red when over) + "eaten / target kcal" text, red when over. |
| **Stats** — daily kcal bar chart over the selected range with target line                 | MVP          |                                                                         |
| **Stats** — average daily kcal, protein, carbs, fat, fibre over the range                 | MVP          |                                                                         |
| **Stats** — macro split (% of kcal from protein / carbs / fat) for the range              | MVP          |                                                                         |
| **Stats** — weight chart with 7-day moving average and total change over range            | MVP          |                                                                         |
| **Stats** — daily bar chart per macro (carbs, fat, protein, fibre) with derived target line | MVP          | Done. Under "Energy from macros"; over = red, amber for protein/fibre.  |
| Sign out                                                                                  | MVP          |                                                                         |
| Quick re-log: long-press a meal on Today → "Log again" (same food, same grams, timestamp now) | MVP      | Done.                                                                   |
| Undo a merge                                                                              | Later        | Needs a merge log.                                                      |
| Macro targets derived from the kcal target, shown as rings on Today                       | MVP          | Done. Not editable: fixed energy shares, decision 9.                    |
| Food values from ingredients: raw weights + finished weight → per 100 g                   | MVP          | Done. One-shot calculator in the food form, result is a plain food (Flow 3a, decision 8). |
| Recipes (a food that remembers its ingredients and recomputes)                            | Rejected     | Decision 8: not wanted; the calculator above covers the use case.       |
| Import foods from an open database (Open Food Facts) or barcode scan                      | Later        | Big feature; the manual form is enough to start.                        |
| Reminders / notifications                                                                 | Nice-to-have |                                                                         |
| Export data to CSV                                                                        | Nice-to-have |                                                                         |
| Home-screen widget for quick logging                                                      | Nice-to-have |                                                                         |


## 5. Out of scope

- Any sharing between users: friends, feeds, comparing progress, seeing each other's data.
- Invitations, public sign-up; users are added to the allowlist by whoever runs the Firebase project.
- Google Play (or any store) release, in-app updates, crash reporting services.
- iOS, web, tablet layouts.
- Photos of meals, AI food recognition.
- Exercise / activity tracking, step counts, Google Fit / Health Connect integration.
- Imperial units.
- Portion presets ("1 slice", "1 cup"); everything is grams.

## 6. Data

Stored in Firestore. `uid` is the Firebase Auth user id.


| Entity           | Firestore path                   | Key fields                                                                                                                                                                                                                      | Relationships                            |
| ---------------- | -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| **Food**         | `foods/{foodId}`                 | `label` (string, required), `kcalPer100g` (float, required), `carbsPer100g`, `fatPer100g`, `proteinPer100g`, `fibrePer100g` (float, each optional/null), `note` (string, optional), `labelLower` (for search), `archived` (bool), `mergedInto` (foodId or null), `createdBy` (uid), `createdAt`, `updatedAt` | Global, shared. Referenced by MealEntry. A food with `mergedInto` set is a redirect and is hidden everywhere. |
| **MealEntry**    | `users/{uid}/meals/{mealId}`     | `foodId` (string), `grams` (float), `timestamp` (UTC instant), `createdAt`, `updatedAt`                                                                                                                                         | Belongs to one user, points to one Food. |
| **WeightEntry**  | `users/{uid}/weights/{weightId}` | `kg` (float), `timestamp` (UTC instant), `note` (optional string)                                                                                                                                                               | Belongs to one user.                     |
| **UserSettings** | `users/{uid}`                    | `displayName`, `language`, `dailyKcalTarget` (float, optional), `lastUsedFoodIds` (list, for the "recent" section)                                                                                                              | One per user.                            |


**Security rules (summary):**

- `foods/**`: read/write for any authenticated, allowlisted user.
- `users/{uid}/**`: read/write only when `request.auth.uid == uid`.
- Allowlist: a document per allowed e-mail in the `allowlist` collection (`allowlist/{email}`), maintained in the Firebase console and unreadable from the app. The rules check `exists()` on it, so the repo holds no personal data and a fork works with its own Firebase project.

**Existing data to import?** No. Fresh start. (If you have a spreadsheet of foods you already use, say so and I'll add a one-off import script.)

**Business rules:**

- Nutrition of a meal = food value per 100 g × grams / 100, computed at display time (not stored). Null macro on the food → that macro is shown as "–" for the meal; in daily totals and averages it counts as 0 and the day/period carries a visible "incomplete" marker.
- Averages in Stats are per *logged* day (days with at least one meal). Days with nothing logged are far more likely unlogged than fasted.
- A "day" is local-time midnight to midnight on the phone.
- **Numbers:** every numeric field (grams, kg, kcal, macros) accepts integers and decimals and is stored as a float. Display rule everywhere: a whole number is shown without a decimal part; otherwise trailing zeros are trimmed, max 1 decimal for computed macro grams (meal nutrition, totals, averages) and max 2 decimals for values as entered (food values, weight). Calories are always shown as whole numbers.
- Daily kcal target applies to every day; no per-weekday variation.
- Macro targets are derived from the kcal target, never stored: protein 20 %, carbs 50 %, fat 30 % of energy at 4 / 4 / 9 kcal per gram, fibre 14 g per 1000 kcal (`domain/Targets.kt`).
- Foods are never hard-deleted. `archived` hides a food from lists and the picker; `mergedInto` makes it a redirect. Resolving a `foodId` always follows `mergedInto` chains to the final food.
- Every place that resolves a food id for the signed-in user's own data (meals, `lastUsedFoodIds`) also rewrites it to the final id when it finds a redirect (self-healing, Flow 7).

## 7. Screens

Bottom navigation with four tabs, plus a floating **+** on Today.


| Screen                           | What's on it / what you can do there                                                                                                                                                                     |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Sign in**                      | Logo, one Google sign-in button, error text if not allowlisted.                                                                                                                                          |
| **Today** (tab)                  | Date header with ← → and calendar picker. Totals card: ring showing eaten kcal as a share of the target (green on grey; full red when over), "eaten / target kcal" and remaining/over text next to it (red when over), then a 2×2 grid of the same for carbs, fat / protein, fibre against targets derived from the kcal target (decision 9; over = amber for protein and fibre). Without a target: eaten kcal and plain macro grams. Meal list for that day (time, food label, grams, kcal), tap to edit. FAB **+ Meal**. |
| **Food picker**                  | Search field (auto-focused), "Recent" section, then filtered full list. "Create new food" row.                                                                                                           |
| **Meal form**                    | Selected food (tap to change), grams field, date/time field, live nutrition preview, Save, Delete (when editing).                                                                                        |
| **Foods** (tab)                  | Searchable list of all foods with kcal/100g. Tap to edit. Long-press to multi-select → **Merge**. Filter toggle to show archived foods. **+ Food**.                                                         |
| **Food form**                    | Label, kcal/100g, carbs, fat, protein, fibre, note, "Calculate from ingredients…", Save, Archive / Un-archive.                                                                                           |
| **Ingredients** (in Food form)   | Ingredient rows (food, grams, remove), Add ingredient (→ Food picker), weight of the finished dish, live per-100 g result, Use these values.                                                             |
| **Merge screen**                 | Selected foods side by side with a survivor radio button; Food form prefilled from the survivor; Save.                                                                                                   |
| **Weight** (tab)                 | Latest weight and change since first entry. Line chart (range selector). Inline "add weight" row. History list, tap to edit.                                                                             |
| **Stats** (tab)                  | Range selector (7 / 30 / 90 / all). Daily kcal bar chart with target line. Averages card. Macro split chart. Four daily bar charts, one per macro (carbs, fat, protein, fibre), with derived target lines. Weight chart with moving average.                                                           |
| **Settings** (from top-bar menu) | Display name, language, daily kcal target, signed-in account, Sign out.                                                                                                                                  |


**Look and feel:** Material 3 with dynamic colour (follows the phone's wallpaper theme), light and dark mode following the system. Dense but readable; big tap targets on the logging path. No onboarding, no illustrations.

## 8. Platform and tech

- **Platform:** Android only. `minSdk 33` (Android 13, for the built-in per-app language API used by the Slovak/English switch), `targetSdk` = latest stable. Tested on Pixel 9 Pro XL and Pixel 8a.
- **Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel + Kotlin Flows, Firebase Auth + Cloud Firestore (with offline persistence on), charts drawn with Compose `Canvas` (three simple charts, no charting library). Gradle with version catalog. No DI framework (manual wiring is enough at this size; repositories are interfaces so the ViewModels can be tested against in-memory fakes). Tests: JVM unit tests for the domain maths and for every ViewModel, a Firestore security rules test on the emulator, Android lint; all run in GitHub Actions on every push (decision 10). No instrumented/Compose UI tests.
- **Hosting / deployment:** Firebase project (Spark free plan) in your Google account. App installed over USB via Android Studio or `./gradlew installDebug`. Both phones get the same build from the same machine.
- **Authentication:** Google Sign-In through Firebase Auth. Allowlist of e-mails enforced in Firestore security rules (the app only shows a friendly message when refused).
- **Constraints:** No cross-platform frameworks. No paid services. The repo must contain no secrets: `google-services.json` is gitignored and documented in the README.

## 9. Integrations


| Service                         | What it's used for        | Account / key available?                                                |
| ------------------------------- | ------------------------- | ----------------------------------------------------------------------- |
| Firebase Auth (Google provider) | Sign-in, identity         | Needs a Firebase project — to be created by you, I'll give exact steps. |
| Cloud Firestore                 | All data storage and sync | Same project.                                                           |


No other integrations.

## 10. Non-functional requirements

- **Expected scale:** 2 users, a few thousand meal entries per year each, a few hundred foods. Everything fits in memory; no pagination needed.
- **Languages / localization:** UI in Slovak or English, switched in Settings (per-app locale, independent of the phone language). All strings live in resource files for both languages; I draft the Slovak, you review. Numbers and dates formatted per the chosen locale; decimal input accepts `,` and `.`.
- **Offline support:** Logging and browsing must work offline; sync when back online. Stats may use cached data.
- **Privacy / sensitive data:** Health data of real people. Only the allowlisted accounts can read it, each only their own; Firestore rules are the enforcement point and get a small rules test. No analytics, no crash reporting, no third-party SDKs beyond Firebase.
- **Accessibility:** System font scaling must not break layouts; otherwise default Compose behaviour.
- **Performance expectations:** Cold start to Today under 2 s on the Pixel 8a; logging a known food under 10 s including the tap on the FAB.

## 11. Definition of done for the MVP

- [x] Allowlisted users can sign in with their Google accounts on their own phones; an account not on the allowlist is refused.
- [x] I can add a food with label + kcal and optional macros, and other users see it in their picker without doing anything.
- [x] I can log a known food in under 10 seconds from the Today screen.
- [ ] The Today screen shows correct totals for the day (checked by hand against a calculator for one day).
- [ ] I can change the grams, time or food of a past meal, and delete one, and the totals update.
- [ ] I can log weight, see the history and a chart; editing and deleting entries works.
- [x] Stats show a daily kcal chart, averages, macro split and weight trend for 7/30/90/all.
- [ ] Logging a meal in airplane mode works and appears in Firestore once back online.
- [ ] Uninstalling the app and reinstalling it restores everything after sign-in.
- [x] Neither user can see the other's meals or weights (verified through the Firestore rules test).
- [ ] Merging duplicate foods rewrites my meals immediately; other users' meals show the survivor right away and are rewritten on their phones after the next sync.
- [x] Switching the language in Settings changes the whole UI to Slovak / English without a restart.

## 12. Resolved questions

Kept for the record; nothing is open right now.

1. **Snapshot nutrition into meals?** No. Meals reference the food; history recalculates when a food is edited.
2. **Kcal target in the MVP?** Yes.
3. **Other users' accounts:** each user links their own Google account.
4. **Decimal entry:** all numeric fields accept integers and decimals; whole numbers are displayed without a decimal part (see Business rules).
5. **Water / drinks:** ordinary foods, no special handling.
6. **Repo:** `git init` in `/Users/martin/work/foodko`; the Android project lives at the repo root.
7. **Duplicate foods:** merge feature added as MVP (Flow 7).
8. **Cooked dishes / recipes:** no recipes. The food form has a calculator that turns ingredients
   plus the finished weight into per-100 g values (Flow 3a); the result is a plain food and the
   ingredient list is not stored. Re-cooking a dish means a new food or editing the old one by hand.
9. **Macro targets:** computed from the kcal target with fixed energy shares (business rules), not
   entered per user. The reference ranges for the share of energy from each macro are the same
   for adults regardless of sex; what differs between people is the calorie budget, which the
   per-user kcal target already captures. "Over" is red for kcal, carbs and fat; amber for
   protein and fibre, where exceeding the target is rarely a problem.
10. **Testing and CI:** the automated checks are JVM unit tests (domain + ViewModels on in-memory
    repository fakes with a fixed clock), Firestore rules tests on the emulator, and Android lint,
    run by GitHub Actions on every push and pull request. No Compose UI or instrumented tests:
    they would need an Android emulator in CI and the screens are thin over the ViewModels; the
    mock build (`-PfoodkoMock=true`) is the way to look at the UI.
