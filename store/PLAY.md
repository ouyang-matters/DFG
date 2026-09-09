# Play release checklist

## Ready in this repo

| Item | Where |
| --- | --- |
| App bundle (upload this) | `app/build/outputs/bundle/release/app-release.aab` |
| Signed APK (sideload / testing) | `dist/Rounds-1.0.apk` |
| Store icon, 512x512 | `store/play-icon-512.png` |
| Feature graphic, 1024x500 | `store/play-feature-1024x500.png` |
| Phone screenshots | `store/screenshots/` |
| Listing copy, 4 locales | `store/listing/` |
| Privacy policy text | `store/PRIVACY.md` |

Build both artifacts with:

```
./gradlew :app:bundleRelease :app:assembleRelease
```

## Technical state

- `targetSdk` 36, `compileSdk` 36, `minSdk` 26.
- R8 and resource shrinking on. The release APK is 1.9 MB.
- Localised for English, French, Simplified Chinese and Traditional Chinese,
  with `localeConfig` so users can switch language per app.
- Signed with `rounds-release.jks`, which is the **upload key**. Play App
  Signing will hold the distribution key. Keep the upload key backed up; losing
  it means asking Google to reset it.

## What you still have to do in Play Console

1. **Host the privacy policy.** A public URL is required. Publish
   `store/PRIVACY.md` somewhere (GitHub Pages works) and paste the link.
2. **Declare the exact alarm permission.** The app requests
   `SCHEDULE_EXACT_ALARM`, which triggers a declaration form. The honest
   justification: a reminder must arrive when the check window actually closes,
   because a late reminder for a window that has already ended is useless.
   If the declaration is rejected, remove the permission from the manifest and
   the app falls back to inexact alarms on its own. That path already works.
3. **Data safety form.** The app collects nothing and has no `INTERNET`
   permission, so answer "no data collected" and "no data shared". Mention that
   photos and entries stay in app private storage.
4. **Content rating questionnaire.** Nothing sensitive applies.
5. **Target audience.** Not directed at children.
6. **Countries and pricing.** Free.

## Decisions worth making before the first upload

These are permanent or expensive to change once published.

- **Application ID is `com.anan.dfg`.** It can never be changed after
  publishing, and `dfg` was only ever the working folder name. Something like
  `com.ouyangmatters.rounds` would read better forever. Changing it now costs
  one line; changing it later means a brand new listing.
- **Keystore password is `roundsapp`.** Fine for sideloading, weak for a key
  that has to last the lifetime of the app. Nothing has been uploaded yet, so
  regenerating it with a strong password is currently free.
- **App name "Rounds"** is not reserved. Check it is distinct enough on Play in
  your target countries.
