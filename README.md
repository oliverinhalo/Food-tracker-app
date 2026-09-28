# Food Tracker

A native Android calorie tracker: photograph a meal, and the app identifies each food item,
estimates portions, and shows calories and macros — with an on-device first pass so results appear
instantly, and a cloud pass that refines them.

> **Status:** phases 0–1 complete (project skeleton, camera capture, Gemini detection, results
> bottom sheet). Nutrition lookup, the diary, the on-device model and brand/barcode support land in
> later phases — see [Roadmap](#roadmap).

## Getting the app

Every release on the [Releases page](../../releases) has an installable APK attached. Download
`food-tracker-<version>.apk` on your phone and open it; you'll need to allow installing from
unknown sources the first time. Pushes to `main` refresh a rolling `latest` pre-release, and
tagging `vX.Y.Z` cuts a proper versioned release.

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
| **USDA FoodData Central** | `local.properties` as `USDA_API_KEY=...` | Shipped with the build, used from phase 2 onward. `local.properties` is gitignored. |

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

### Signing releases

CI signs with repository secrets when they exist, and falls back to debug signing otherwise (still
installable, but Android won't upgrade across a signing-key change). To sign properly, set
`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` as repository secrets. Locally, a `keystore.properties` at the repo root
(gitignored) does the same job.

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
domain/recognition      recogniser interfaces, merge logic, pipeline orchestration
data/recognition        Gemini client, image compression, capture store
feature/capture         CameraX capture and permission states
feature/results         the results bottom sheet
feature/home            today's ring and macros
feature/settings        API key, goal, units, local-only mode
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

**Merging** is the subtle part and lives in `RecognitionMerger` as a pure, unit-tested function.
It matches by bounding-box IoU where both sides have boxes and by food-label similarity otherwise;
a matched item keeps its existing id so the visible list never re-keys mid-edit; and anything the
user has touched outranks any recogniser.

**Model fallback.** The free tier returns `503 UNAVAILABLE` on the newest Flash models more often
than it rate-limits, so a single hardcoded model name makes the feature look broken at busy times.
`GeminiModels.FALLBACK_CHAIN` walks down to older Flash models on 503/404, and a 429 stops
immediately (it is per-key, so a different model won't help) and degrades to local results.

## Testing

```bash
./gradlew testDebugUnitTest          # portion maths, merge logic, state reduction
./gradlew connectedAndroidTest       # bottom-sheet flow (needs a device or emulator)
```

Unit tests cover the parts where bugs are invisible rather than loud: the per-100g → portion
scaling, unit round-tripping, label similarity thresholds, and every merge case (user edits winning,
occluded duplicates, alternatives de-duplication).

## Roadmap

| Phase | Scope | Status |
| --- | --- | --- |
| 0 | Project skeleton, modules, DI, theme, CI, releases | ✅ |
| 1 | CameraX capture, Gemini detection, results bottom sheet | ✅ |
| 2 | USDA + Open Food Facts, Room cache, portion maths, diary, Home | ⏳ |
| 3 | TFLite + ML Kit local pass, offline queue, Gemini Nano | ⏳ |
| 4 | Brands, barcode scanning, favourites, portion learning, History | ⏳ |
| 5 | Baseline profile, shared-element transitions, a11y pass, polish | ⏳ |
