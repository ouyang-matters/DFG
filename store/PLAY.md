# Publishing Rounds on Google Play

## What is ready

| Item | Where |
| --- | --- |
| App bundle to upload | `app/build/outputs/bundle/release/app-release.aab` |
| Signed APK for sideloading | `dist/Rounds-1.0.apk` |
| Store icon, 512x512 | `store/play-icon-512.png` |
| Feature graphic, 1024x500 | `store/play-feature-1024x500.png` |
| Phone screenshots | `store/screenshots/` |
| Listing copy in 4 languages | `store/listing/` |
| Privacy policy | `docs/privacy.md` |

Rebuild both artifacts with:

```
./gradlew :app:bundleRelease :app:assembleRelease
```

App ID `io.github.ouyangmatters.rounds`, versionCode 1, targetSdk 36.
Permissions: notifications, boot completed, vibrate. No internet, no camera,
no exact alarms, no foreground service.

## Step 1. Developer account

Sign up at https://play.google.com/console with a one time USD 25 fee and
identity verification. Choose **personal** unless you have a registered
organisation with a D-U-N-S number.

Personal accounts created after November 2023 cannot publish to production
straight away. They must first run a closed test with at least 12 testers who
stay opted in for 14 days in a row. Plan for this: it is the longest part.

## Step 2. Host the privacy policy

Play needs a public URL. `docs/privacy.md` is already in the repo.

1. GitHub, repository `ouyang-matters/DFG`, Settings, Pages.
2. Source: deploy from branch `main`, folder `/docs`.
3. The policy appears at `https://ouyang-matters.github.io/DFG/privacy.html`.

Pages on a free plan requires the repository to be public. If you want to keep
it private, host the file anywhere else that is public.

## Step 3. Create the app

Play Console, Create app.

- App name: `Rounds`
- Default language: English (United States)
- App or game: App
- Free or paid: Free. This cannot be changed to paid later.

## Step 4. App content

Found under Policy, App content. Every item must be finished before any release.

| Section | Answer |
| --- | --- |
| Privacy policy | The Pages URL from step 2 |
| Ads | No ads |
| App access | All functionality is available without special access |
| Content rating | Fill the questionnaire as a utility app, answer no to everything. Expect Everyone / 3+ |
| Target audience | 18 and over, or 13 and over. Not children |
| Data safety | Collects no data, shares no data. Photos and entries never leave the device, which is what Google counts as collection |
| Health apps | No health features. The listing mentions OCD as a personal story, it makes no medical claim, keep it that way |
| Government, financial, news | No |

## Step 5. Store listing

Main store listing, default language English:

- Short and full description from `store/listing/en-US.txt`
- App icon `store/play-icon-512.png`
- Feature graphic `store/play-feature-1024x500.png`
- Phone screenshots from `store/screenshots/`, at least 2
- Category: Productivity
- Contact email: required, shown publicly

Then Manage translations, add French (France), Chinese (Simplified) and
Chinese (Traditional), and paste the matching file from `store/listing/`.

## Step 6. First upload, internal testing

Testing, Internal testing, Create new release.

1. Accept **Play App Signing**. Google keeps the key that signs what users
   install. `rounds-upload.jks` becomes your upload key only.
2. Upload `app-release.aab`.
3. Release name `1.0`, notes: `First release.`
4. Add yourself as a tester, open the opt in link on your phone, install from
   Play and check notifications and photos work.

## Step 7. Closed test, 12 testers for 14 days

Testing, Closed testing. Promote the same release, add at least 12 testers by
email or Google Group, and send them the opt in link. They must install and
stay opted in for 14 consecutive days. Ask them to actually use it: Google
also looks at whether testers engaged.

## Step 8. Production

After the 14 days, Dashboard, Apply for production. Answer the questions about
the test. Once granted, promote the release to Production and pick countries.
Review usually takes a few days for a new app.

## Every later update

1. Raise `versionCode` in `app/build.gradle.kts` by one, and `versionName`.
2. `./gradlew :app:bundleRelease`
3. Upload the new `.aab` to a track and promote it.

Back up `rounds-upload.jks` and `keystore.properties` together, outside this
machine. If the upload key is lost, Play support can reset it, but it takes
days.
