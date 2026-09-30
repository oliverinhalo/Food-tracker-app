# Publishing to Google Play

Everything the console asks for, and where the answer comes from.

## Build

`bundleRelease` produces the `.aab` Play expects; `assembleRelease` produces the sideloadable APK
the GitHub release carries. A tagged release builds both (see `.github/workflows/release.yml`).

```
./gradlew bundleRelease -PversionName=1.0.0 -PversionCode=42
```

`versionCode` must increase on every upload. CI uses the commit count, which is monotonic.

## Signing

There are two different keys, and confusing them is how upgrades break:

| Key | Where it lives | What it is for |
| --- | --- | --- |
| Development key | `keystore/dev.jks`, committed, password `foodtracker` | Local builds and the rolling `latest` APK, so a sideloaded dev build upgrades in place. Public by design; it protects nothing. |
| Upload key | Never in the repository. `RELEASE_KEYSTORE_BASE64` and friends in GitHub secrets | Signs anything published to Play or tagged as a real release. |

Generate an upload key once:

```
keytool -genkeypair -v -keystore upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 upload.jks     # paste into the RELEASE_KEYSTORE_BASE64 secret
```

Then set `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. Enrol in
Play App Signing so a lost upload key can be reset without orphaning every install.

## Data safety declarations

Answers to the console's data safety form, all of them derived from [`PRIVACY.md`](../PRIVACY.md):

- **Does your app collect or share any of the required user data types?** Yes — photos.
- **Photos:** collected? No (never leaves the device except as a transient API request).
  Shared? **Yes**, with Google's Gemini API, for *app functionality* only. Not for analytics,
  advertising, personalisation or fraud prevention. Processing is ephemeral. Not required —
  Local-only mode turns it off.
- **Health and fitness data** (the diary): stored on-device only, neither collected nor shared.
- **Personal info, financial info, location, contacts, messages, files, device IDs:** none.
- **Is all user data encrypted in transit?** Yes — HTTPS only, enforced by
  `network_security_config.xml`.
- **Android Auto Backup** is enabled for the diary and settings (not photos, not keys). It writes
  to the user's own Google account, not to any developer-controlled server, so it is not
  developer data collection and does not change the answers above. It is disclosed in
  `PRIVACY.md` regardless.
- **Do you provide a way for users to request that their data is deleted?** Yes —
  Settings → Your data → Delete all my data, and uninstalling.

## Store listing checklist

- [ ] App icon, 512×512 PNG (source: `app/src/main/res/mipmap-*`)
- [ ] Feature graphic, 1024×500
- [ ] At least two phone screenshots: Home with a day logged, and the results sheet mid-edit
- [ ] Short description (≤80 chars): *Photograph a meal, get calories and macros in seconds.*
- [ ] Full description: what it does, that a free Google AI Studio key is required, that the diary
      stays on the device
- [ ] Privacy policy URL, pointing at the published `PRIVACY.md`
- [ ] Category: Health & Fitness. Content rating: Everyone
- [ ] Target audience: 13+ (no children's-policy obligations)

## Things Play will reject

- **A missing privacy policy URL** — required whenever the data safety form declares anything.
- **`targetSdk` below the current minimum.** Checked in `build-logic`; raise it there.
- **Health claims.** The listing must not imply the app diagnoses, treats or manages a condition.
  It estimates the calories in a photograph, which is what the description says.
- **Debug-signed uploads.** Play refuses them outright.
