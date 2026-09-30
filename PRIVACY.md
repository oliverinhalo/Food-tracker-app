# Privacy policy

**Food Tracker** · last updated 30 September 2026

Food Tracker is a calorie diary that runs on your phone. This policy describes what it does with
your information. It is short because the app collects very little.

## What stays on your device

Everything you log stays in the app's private storage on your phone:

- your meals, foods, portions and the totals derived from them
- the photos you take of your meals
- your daily calorie and macro goals, and your other settings
- a local cache of food nutrition data, so repeated lookups are instant and work offline
- the corrections you make to portion estimates, which the app uses to bias future estimates

None of this is sent to the developer. There are no analytics, no crash reporting, no advertising
identifiers and no third-party trackers in the app.

**Android Auto Backup** is switched on for your meals, foods and settings, so that reinstalling the
app or setting up a new phone does not lose your diary. That backup goes to *your own* Google
account, is governed by Google's backup terms, and is not visible to the developer. Your meal
photos are excluded from it, and so are your API keys, which are tied to the device they were
entered on and cannot be restored anywhere else. You can turn the whole thing off in
Android's Settings → Google → Backup.

## Your API keys

The app needs a Google AI Studio key to recognise food in a photo, and optionally a USDA
FoodData Central key for generic-food nutrition. You supply these yourself in Settings.

Keys are stored encrypted, using a key held in the Android Keystore, and are only ever sent to the
service they belong to. They are never included in an export, never logged, and never transmitted
to the developer.

## What leaves your device, and when

Only two things ever leave your phone, and only while you are using the relevant feature:

1. **A meal photo**, sent to Google's Gemini API when you scan a meal, so it can be identified.
   Google processes it under their own terms; see
   <https://ai.google.dev/gemini-api/terms> and <https://policies.google.com/privacy>.
   Turning on **Local-only mode** in Settings stops this entirely — no photo is ever sent.
2. **A food name or barcode**, sent to USDA FoodData Central
   (<https://fdc.nal.usda.gov>) and Open Food Facts (<https://openfoodfacts.org>) to look up
   nutrition. These requests carry the search term only, never your diary or your identity.

No account is required to use the app, and the app does not ask for your name, email address,
phone number, location or contacts.

## Children

Food Tracker is not directed at children under 13 and collects nothing from them, as it collects
nothing from anyone.

## Deleting your data

Settings → **Your data** → **Delete all my data** erases every meal, photo, cached food, learned
portion and stored API key from the device. Uninstalling the app also removes all of it. Because
nothing is held on a server by the developer, there is nothing else to request the deletion of. A
copy may remain in your own Google account's backup; Android's Settings → Google → Backup is where
you delete that.

You can take a copy with you first: Settings → **Your data** → **Export** writes your diary to a
JSON file you choose the location of. Exports contain your meals and their numbers; they
deliberately exclude your photos and your API keys.

## Changes

If this policy changes, the updated version will be published in this repository and the date at
the top will change.

## Contact

Open an issue at <https://github.com/oliverinhalo/food-tracker-app/issues>.
