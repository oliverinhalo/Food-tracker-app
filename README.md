# Food Tracker

A native Android calorie tracker: photograph a meal, and the app identifies each food item,
estimates portions, and shows calories and macros — with an on-device first pass so results appear
instantly, and a cloud pass that refines them.

> **Status:** the full loop works — scan or pick a photo, correct anything the AI got wrong, log
> it, and see today and your history. Barcode scanning, manual entry, recents and offline
> re-analysis are in. The on-device model is deliberately not shipped; see
> [The on-device pass](#the-on-device-pass).

## Getting the app

Every release on the [Releases page](../../releases) has an installable APK attached. Download
`food-tracker-<version>.apk` on your phone and open it; you'll need to allow installing from
unknown sources the first time.

Pushes to the repository's default branch refresh a rolling `latest` pre-release, and tagging
`vX.Y.Z` cuts a proper versioned release. You can also run the **Release** workflow by hand from
the Actions tab and give it any version you like.

## Building it yourself

Requirements: JDK 17+, Android SDK with platform 37 and build-tools 37.0.0. Android Studio
installs both for you.

```bash
git clone <this repo>
cd Food-tracker-app
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

### Keys and where they go

Nothing secret is committed. There are two keys, and they are handled differently on purpose:

| Key | Where it goes | Why |
| --- | --- | --- |
| **Gemini (Google AI Studio)** | Entered in the app under **Settings → Gemini API key** | It is *your* key and your quota, so it belongs to the install, not the build. Stored in `EncryptedSharedPreferences` behind an Android Keystore AES256-GCM master key, and excluded from cloud backup and device transfer. |
| **USDA FoodData Central** | **Settings → USDA FoodData Central key**, or `local.properties` as `USDA_API_KEY=...`, or a `USDA_API_KEY` repository secret | USDA is where generic foods (rice, broccoli, chicken) get accurate calories. Release APKs are built by CI, which has no `local.properties`, so a runtime option exists too — otherwise the shipped app would silently have no USDA access. |

Get a free Gemini key at [aistudio.google.com](https://aistudio.google.com/apikey) and a free USDA
key at [fdc.nal.usda.gov](https://fdc.nal.usda.gov/api-key-signup.html).

The app works without a Gemini key — it just falls back to on-device results and tells you why.

### The on-device model

The TFLite food classifier is **not** committed (size and licensing). Drop a Food-101
MobileNet/EfficientNet-Lite model at:

```
app/src/main/assets/food_classifier.tflite
app/src/main/assets/food_labels.txt
```

Without it, the local pass reports itself unavailable and the pipeline goes straight to the cloud
pass. This is wired but inert until phase 3.

### Signing, and why updates keep your data

Android refuses to update an installed app whose signing key changed — the only way through is an
uninstall, which wipes app data including your saved Gemini key.

CI runners generate a fresh debug keystore on every run, so early builds were each signed by a
different key and every update demanded an uninstall. `signing/dev-release.jks` is committed so all
builds share one signature and updates install over the top.

That keystore's password is in `app/build.gradle.kts` and is therefore **not a secret** — anyone
can sign an APK claiming to be this app. That is an acceptable trade for a personal build
distributed through GitHub Releases, and it is why a real key belongs in repository secrets before
this is published anywhere that matters. To switch: set `RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` as repository secrets
and they take precedence. Locally, a gitignored `keystore.properties` does the same.

Note that switching keys is itself a key change, so the first build after switching needs one final
uninstall.

One subtlety worth knowing, because it silently defeated this once: GitHub Actions exports an
**unset secret as an empty string**, not as a missing variable. Code that checks for null takes the
empty path, skips the committed key, and falls back to debug signing without saying anything. Every
secret here is read as blank-means-absent, and the release job is verified by building with the
variables set to `""`.

## Architecture

Clean-architecture split across Gradle modules, MVVM in the UI layer, Hilt for DI, Coroutines and
Flow throughout. Dependencies point inward: `feature:*` → `domain:*` → `core:model`, and nothing in
`domain` imports Android.

```
app                     Application, MainActivity, NavHost, bottom-sheet host
core/model              pure domain types (nutrients, portions, detections)
core/common             dispatchers, time provider
core/ui                 theme (dynamic colour + dark mode) and shared components
core/network            OkHttp/Retrofit/JSON setup, retry + backoff, connectivity
core/datastore          settings DataStore and the encrypted key store
core/camera             CameraX plumbing and the barcode scanner
core/text               food-label similarity, shared by matching and merging
core/database           Room: cached foods, diary, portion corrections, offline queue
domain/recognition      recogniser interfaces, merge logic, pipeline orchestration
domain/nutrition        portion maths, density tables, portion learning
data/recognition        Gemini client, image compression, capture store
data/nutrition          USDA + Open Food Facts clients, cache-first repository
data/diary              meal logging, day totals, favourites
feature/capture         CameraX capture and permission states
feature/results         the results bottom sheet
feature/home            today's ring, macros and meals
feature/history         calendar-range trends and per-day meals
feature/settings        API keys, goals, units, local-only mode
build-logic             convention plugins shared by every module
```

### The recognition pipeline

`RecognitionOrchestrator` emits a `Flow<RecognitionEvent>` that improves over time, so the sheet can
render the moment anything is known:

1. **Provisional** — on-device results (phase 3; currently reports unavailable).
2. **Refined** — the Gemini pass, merged into whatever is already on screen.
3. **Degraded** — cloud unavailable (offline, rate-limited, no key, local-only); local results stand
   and the photo is queued for re-analysis.
4. **Failed** — nothing to show, with a retry.

Both passes start together rather than in sequence: waiting for the local pass before dialling out
would add its latency to the cloud result too.

**Some foods cannot be identified by looking**, however good the recogniser. A pie is a pastry
case: steak, chicken and apple pie are near-identical from outside and differ by hundreds of
calories. A sausage may be pork, chicken or vegan. Guessing silently is worse here than anywhere
else, because there is no reason for the user to doubt the answer.

So the recogniser is asked a second, separate question — not "what else might this be?" but "what
might this be *made of*?" — and the results sheet puts it at the top of the picker as
"What kind of pie?". `AmbiguousFoods` covers the same ground offline, for foods added by hand and
for when the cloud pass cannot be reached.

**Picking the right database row** is where the calories are won or lost. Search ranks by text
relevance, and taking the first hit is wrong by a lot: USDA answers "steamed broccoli" with
*"Corn, white, steamed"* at 386 kcal/100g (the real broccoli row, 35 kcal, is sixth) and "white
rice" with rice *flour* at 359 kcal where cooked rice is 130. `FoodMatcher` scores every candidate
instead — the food itself must be named, the preparation should agree, processed forms like flour
are penalised, and the energy has to be physically plausible for that category. When nothing
plausible matches it returns nothing, because no calories is better than confidently wrong ones.

**Merging** is the subtle part and lives in `RecognitionMerger` as a pure, unit-tested function.
It matches by bounding-box IoU where both sides have boxes and by food-label similarity otherwise;
a matched item keeps its existing id so the visible list never re-keys mid-edit; and anything the
user has touched outranks any recogniser.

**Model fallback.** The free tier returns `503 UNAVAILABLE` on the newest Flash models more often
than it rate-limits, so a single hardcoded model name makes the feature look broken at busy times.
`GeminiModels.FALLBACK_CHAIN` walks down to older Flash models on 503/404, and a 429 stops
immediately (it is per-key, so a different model won't help) and degrades to local results.

### Nutrition

USDA FoodData Central covers generic foods and Open Food Facts covers branded ones. Lookups are
cache-first, so a repeat food resolves without a network call and the app stays usable offline; a
miss queries both sources concurrently rather than in sequence.

Three things in this layer exist because the real APIs misbehave:

- **Energy units.** USDA Foundation and SR Legacy rows often carry energy only as kilojoules
  (nutrient 1062), not kilocalories. Reading only the kcal field leaves common staples with no
  calories at all.
- **Implausible rows.** Open Food Facts is crowd-sourced and routinely carries rows whose stated
  energy contradicts their own macros by an order of magnitude — usually kJ typed into the kcal
  field. Such a row would quietly wreck a day's total, so energy is cross-checked against Atwater
  factors and replaced when it cannot be right.
- **Two different hosts.** `world.openfoodfacts.org` serves barcode lookups reliably but returns
  503 for *text search* on both its v2 and legacy endpoints. Brand search therefore goes to
  `search.openfoodfacts.org`. Pointing both at the obvious host ships a brand search that is
  always empty.

Sodium is also normalised: USDA reports milligrams, Open Food Facts reports grams.

### The on-device pass

There is no on-device recognition in the shipped app, and that is a size decision rather than
unfinished work. The stack was built and measured: ML Kit object detection plus the TensorFlow Lite
runtime added roughly **25 MB of native libraries to a 3 MB app** (82 MB before trimming ABIs).

Object detection on its own finds *where* food is but cannot name it, so without a classifier model
— which can't be committed, for size and licensing reasons — all that weight buys is unnamed
placeholder rows that the cloud pass overwrites a second later. That is a bad trade for anyone
downloading the APK.

The architecture still supports it. `LocalFoodRecognizer` reporting unavailable is a supported
state, not a failure: the orchestrator skips straight to the cloud pass. To turn it back on, add
`com.google.mlkit:object-detection` and `org.tensorflow:tensorflow-lite` to `:data:recognition`,
restore the classifier, and drop a model at `app/src/main/assets/food_classifier.tflite` with a
matching `food_labels.txt`.

Barcode scanning uses the **Play Services** ML Kit variant, where the model lives in Google Play
Services rather than the APK — the bundled equivalent alone costs about 20 MB.

### Portions

Volume and count units are meaningless without knowing the food: a cup of spinach is ~30 g and a
cup of cooked rice is ~185 g. `FoodCategory` maps a label to one of twenty density profiles, with a
generic fallback, and a portion always carries its resolved mass so changing the display unit never
changes how much was logged.

**Learning.** Every portion you correct is recorded, and future estimates for that food are blended
toward your history — cautiously at first, more confidently as samples accumulate, and never
completely, since the camera is looking at *this* plate. Corrections are weighted by recency, and a
trivial nudge (150 g to 152 g) is ignored rather than diluting the signal.

## Your data, and getting it out

Everything lives in the app's private storage: your meals, your photos, the food cache and what the
app has learned about your portions. Nothing is uploaded, and there is no account.

Settings → **Your data** covers the three things that follow from that:

- **Export** writes your diary to a JSON file through the system picker. It holds meals, items,
  portions and their numbers; photos are left out because they dominate the size, and API keys are
  left out because a credential does not belong in a file you email yourself.
- **Import** restores one, additively. Meal ids are preserved, so importing the same file twice
  restores rather than doubles.
- **Delete all my data** erases every meal, photo, cached food, learned portion, preference and
  stored key, behind a confirmation. This is the deletion path Play's user-data policy requires.

[`PRIVACY.md`](PRIVACY.md) is the policy the app links to from Settings → About.
[`docs/play-store.md`](docs/play-store.md) has the release build, the upload-key setup and the
data-safety answers.

## Options

Settings covers the things worth differing on rather than the things worth guessing at:

| | |
| --- | --- |
| **Appearance** | Theme (system / light / dark), dynamic colour, haptics, metric or imperial |
| **Goals** | Daily calories and protein/carbs/fat targets, with a warning when the two disagree |
| **Scanning** | Photo quality (data saver / balanced / high detail), which Gemini model, retry-when-online, local-only mode |
| **Keys** | Gemini and USDA keys, each revealable and copyable before a reinstall |
| **Your data** | Export, import, erase |

Photo quality is the one with a real trade-off: higher detail uploads more and takes longer on a
slow connection without much changing what the recogniser sees, which is why *Balanced* is the
default rather than the maximum.

## Testing

```bash
./gradlew testDebugUnitTest          # portion maths, merge logic, API mapping, state reduction
./gradlew connectedAndroidTest       # UI flows (needs a device or emulator)
```

119 unit tests and 32 instrumented tests. The instrumented ones cover the results sheet, the food
picker, Home and History; they are written but have never been run here, because this environment
has no device or emulator.

Unit tests cover the parts where bugs are invisible rather than loud: per-100g → portion scaling,
unit round-tripping across every category, label similarity thresholds, every merge case (user edits
winning, occluded duplicates, alternatives de-duplication), the portion learner's confidence ramp,
and the API quirks above (kJ conversion, sodium units, implausible-energy correction).

Compose stability is checked rather than assumed:

```bash
./gradlew assembleRelease -Pfoodtracker.composeReports=true
```

This writes stability and recomposition reports per module. The domain models are immutable but
live in modules that deliberately have no Compose dependency, so they cannot be annotated —
`compose-stability.conf` tells the compiler instead. Without it every food row recomposed on any
state change. Anything listed in that file must stay genuinely immutable.

Two scripts check the live APIs, which unit tests cannot: they catch an endpoint disappearing or a
field being renamed upstream.

```bash
GEMINI_API_KEY=... python3 tools/check_gemini.py      # recognition request + schema
USDA_API_KEY=...   python3 tools/check_nutrition.py   # USDA + Open Food Facts

# The whole chain: photo -> identified foods -> matched rows -> a calorie total
GEMINI_API_KEY=... USDA_API_KEY=... python3 tools/check_pipeline.py
```

The last one matters most. The failures that made this app wrong were not in any single piece —
recognition was fine, the database call was fine — but in how they joined up, and they only became
visible once a real photo produced a real number. It fails if the plate does not come out at a
plausible calorie total, which is what "steamed broccoli resolving to steamed corn" looked like
from the outside.

## Roadmap

| Phase | Scope | Status |
| --- | --- | --- |
| 0 | Project skeleton, modules, DI, theme, CI, releases | ✅ |
| 1 | CameraX capture, Gemini detection, results bottom sheet | ✅ |
| 2 | USDA + Open Food Facts, Room cache, portion maths, diary, Home | ✅ |
| 3 | Offline re-analysis queue; local pass built then deliberately dropped on size | ✅ |
| 4 | Barcode scanning, food search, recents, manual entry, History | ✅ |
| 5 | Baseline profile, Compose stability, a11y pass | ✅ |
| 6 | Settings for everything, export/import/erase, Play Store readiness | ✅ |
