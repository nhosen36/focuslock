# Release process: from code change to users

Everything here is a one-time setup, then a repeatable cycle. I couldn't run any of it for you: it needs your Play Console account, your signing key and your GitHub repository.

## A. One-time setup

### 1. Accounts
1. Google Play Console developer account (one-time US\$25). Personal accounts created after 13 Nov 2023 must run a **closed test with at least 12 testers opted in for 14 days** before Production unlocks. Start recruiting testers early.
2. GitHub account and an empty repository (private is fine).
3. Google AdMob account, for ads (used only after the launch period).

### 2. Pick your permanent app id
`applicationId` in `app/build.gradle.kts` is `com.focuslock.app`. Change it to one you own (for example `com.yourname.focuslock`) **before the first upload**. It can never change afterwards. Also update `packageName` in `.github/workflows/release.yml`.

### 3. Create the upload key (run once, keep it safe)
Install a JDK (Android Studio includes one), then:
```
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
Back up `upload.jks` and its passwords somewhere safe (password manager plus an offline copy). Enrol in **Play App Signing** when you create the app. Then Google holds the real signing key, and if you ever lose the upload key you can ask Google to reset it. Never commit the key (`.gitignore` already blocks it).

### 4. Play Console: create the app
- Create app, choose **App** and **Free** (the app stays free to download; the paid part is an in-app purchase).
- Complete everything in `PLAY_CONSOLE_CHECKLIST.md`.
- **Monetise, Subscriptions**: create ONE subscription with product id `focuslock_pro`, then add two base plans: `monthly` (auto-renewing, every 1 month, US\$1.99) and `yearly` (auto-renewing, every 1 year, US\$14.99). Activate both. The ids must match exactly. Optional: add a 3 or 7-day free-trial offer to `yearly`.
- Pricing is one global price. Play Console converts it to each local currency automatically. Leave it that way and don't edit per-country prices.

### 5. Service account for automatic uploads
1. Play Console, Setup, API access, create a service account (follows the link to Google Cloud). Create a JSON key.
2. Back in Play Console, invite that service account as a user with permission to **release to testing tracks** and **release to production**.
3. Keep the JSON file secret.

### 6. GitHub secrets (repo, Settings, Secrets and variables, Actions)
| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 upload.jks` (on Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.jks"))`) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | the full contents of the service account JSON |

### 7. Host the privacy policy and launch switch (GitHub Pages)
1. Edit `docs/privacy.html` (date and email).
2. Repo Settings, Pages, deploy from branch `main`, folder `/docs`.
3. Your URLs become `https://YOURNAME.github.io/REPO/privacy.html` and `.../config.json`.
4. Put them in `RemoteFlags.CONFIG_URL` and `RemoteFlags.PRIVACY_URL` in `Monetization.kt`. Use the same privacy URL in the Play Console.

### 8. Replace the test ad ids
In `Ads.kt` and `AndroidManifest.xml`, replace Google's test ad ids with your AdMob app id and banner unit id. Do this before launch, never click your own live ads.

### 9. Local tooling
Install Android Studio (JDK 17 and the Android SDK, including platform 36). Open the project and let Gradle sync. Generate the wrapper once with `gradle wrapper --gradle-version 8.11.1` (or use Studio's sync) and commit `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`. Install Git and push the project to your GitHub repo.

## B. The release cycle

```
code change -> pull request -> CI (lint + build) -> merge to main
            -> bump VERSION, add notes -> git tag vX.Y.Z -> GitHub Action
            -> signed .aab uploaded to Internal testing
            -> you test on a real phone -> promote -> staged rollout to Production
```

1. Work on a branch, open a pull request. `ci.yml` runs lint and a debug build.
2. Merge to `main`. Edit `distribution/whatsnew/whatsnew-en-US` (500 characters max) and `VERSION`.
3. Tag and push:
   ```
   git tag v1.0.1
   git push origin v1.0.1
   ```
   `release.yml` builds a signed bundle and uploads it to the **Internal testing** track. `versionCode` is the GitHub run number, so it always increases. `versionName` is the tag.
4. Install it from the internal testing link and run the checklist in `TESTING.md`.
5. Promote it, either in Play Console (Internal testing, Promote release), or via the Actions tab, Run workflow, track `production`, rollout `0.1` (10%).
6. Watch **Android vitals** (crashes, ANRs) for a day. Then raise the rollout to 50%, then 100% in Play Console.
7. Problem? In Play Console, **Halt rollout**. Fix, tag a new version, repeat. You can't roll back to an older versionCode; you ship a fixed newer one.

First upload note: for a brand-new app Google requires the first release to be created in Play Console. Upload the first `.aab` (download it from the Actions run artifacts) to the Internal track by hand, then automation works from then on.

The release job also uploads `mapping.txt`, so crash stack traces are readable in Play Console.

## C. The free plan and the subscription

The app is free to download. Free users get **120 focus minutes every calendar month** and see small ads on Stats, Awards and Style. After that they can't start new sessions until the 1st, unless they subscribe to Pro (**US\$1.99 per month or US\$14.99 per year**) for unlimited focus time and no ads. Full reasoning and numbers are in the pricing model document.

Everything is controlled by `docs/config.json`, hosted on GitHub Pages. No app update is needed to change it:

| Setting | Meaning |
|---|---|
| `monetization_enabled: true` | Normal state: free allowance and ads for free users, Pro for subscribers. |
| `monetization_enabled: false` | Promo or launch period: everyone gets unlimited free use and no ads. |
| `free_minutes_per_month` | The free allowance (120). Test other values and compare conversion and retention. |

To change it, edit `docs/config.json`, commit and push to `main`. GitHub Pages updates within a few minutes, and apps pick it up the next time they open. If the file can't be reached, the app uses the normal free plan.

Why a subscription rather than a paid app? Google Play doesn't allow turning a free app into a paid one, and a subscription lets people try the app first, which suits a habit app.

**Rules to follow (Google Play):** show the price and the renewal terms before purchase (the paywall does), let users cancel in Google Play (the paywall links to it), and never block emergency calls or leave a user stuck inside the app.

## D. Money
- Play's service fee on subscriptions is **15% from the first dollar**, so a US\$1.99 monthly payment nets about US\$1.69 and a US\$14.99 yearly payment about US\$12.74, before local taxes. Tax handling (VAT and similar) varies by country.
- Set up your **payments profile** and tax info in Play Console, or payouts will be blocked.

## E. Routine upkeep
- **Every August:** Google raises the minimum target API level. Bump `compileSdk` and `targetSdk`, test, and ship an update, or the app can be hidden from new users.
- Keep Billing Library, AdMob and UMP updated. Google retires old billing versions about once a year.
- Reply to reviews. Check Android vitals weekly.
- Re-check `PLAY_CONSOLE_CHECKLIST.md` whenever you add a permission, an SDK or a data type.
