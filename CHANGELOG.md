# Changelog

## 1.0.0 — 30 September 2026

The first complete version. Everything below is in the app; nothing in it is a placeholder.

### Scanning a meal

- Photograph a plate, or pick a photo from your gallery, and get every item on it identified with
  an estimated portion, calories and macros.
- When looking at a photo genuinely cannot settle what something is, the app asks. A pie is a
  pastry case — steak, chicken and apple differ by hundreds of calories — so it asks which, rather
  than guessing silently. Fifteen such foods are covered without a network round trip.
- Every item offers alternatives, a search over the food databases, and a barcode scanner for
  branded products.
- Portions are adjustable by stepper, slider or unit (g, oz, piece, cup, tbsp, serving), and the
  app learns from your corrections: later estimates for a food you keep adjusting move toward what
  you actually eat.

### Nutrition

- USDA FoodData Central for generic foods, Open Food Facts for branded ones, cached locally so
  repeat lookups are instant and work offline.
- Candidate rows are scored rather than taken in order, with a plausibility check per food
  category. "Steamed broccoli" used to resolve to steamed corn at 386 kcal/100 g — an eleven-fold
  error — because the first search hit won.
- Energy is reconciled against the macros, so a row whose calories disagree with its own protein,
  carbs and fat is corrected rather than trusted.

### Your diary

- Home shows the day's ring, macros and meals; History shows the week, a calendar and trends.
- Meals can be edited or deleted from either screen.
- One-tap logging for the foods you log most, at the size you usually eat them.
- Manual entry for anything that cannot be photographed.

### Your data

- **Export** writes your diary to a JSON file through the system picker. **Import** restores one,
  with meal ids preserved so restoring the same file twice does not double a day's calories.
- **Delete all my data** erases every meal, photo, cached food, learned portion and stored key.
- Android Auto Backup is on for the meals and settings, so a reinstall or a new phone gets the
  diary back. Photos are excluded to stay inside the backup quota; API keys are excluded because
  they are bound to the device's keystore and could only restore as undecryptable bytes.

### Options

Theme, dynamic colour, haptics, metric or imperial, calorie and macro goals, photo quality, which
Gemini model to ask, whether offline scans are retried, and local-only mode — which stops any photo
leaving the device at all.

### Privacy

Your diary and photos stay on the device. A meal photo goes to Google's Gemini API only while a
scan is running. API keys are yours, entered in Settings, held in encrypted storage, and never
included in an export. There is no analytics, no crash reporting and no account. See
[`PRIVACY.md`](PRIVACY.md).

### Under the hood

- Kotlin, Jetpack Compose, Material 3, MVVM across ui/domain/data modules, Hilt, Room, DataStore,
  CameraX, OkHttp.
- 143 unit tests and 37 instrumented tests (the latter need a device; they are not run in CI). Lint clean on the release variant.
- 3.5 MB release APK. An earlier build reached 82 MB before the on-device classifier was measured
  and dropped; the accuracy it added did not justify seventy megabytes of native libraries.
- Signed with a stable key, so an update installs over the previous version instead of demanding
  an uninstall.
