# FocusLock

Calls-only focus app for Android. Launcher-based lock (Home role + Do Not Disturb), no Accessibility, overlay or device-admin permissions.

## Start here
1. Install Android Studio. Open this folder and let Gradle sync (JDK 17, Android SDK 36).
2. Run on a real phone.
3. Read `docs/RELEASE.md` for the full ship-and-update process and `docs/PLAY_CONSOLE_CHECKLIST.md` for compliance.

## What is in the app
- Focus sessions: timer, Home app, Do Not Disturb, Phone/Contacts/Emergency only, puzzle exit
- Music: one Play/Pause button, random tracks from `app/src/main/assets/music/` (add licensed audio)
- Stats: weekly and monthly reports, streaks, share cards, opt-in Sunday reminder
- Awards: 1h to 1,000h and streak milestones
- Style: accent color and background photo (dark only)
- Revenue: free plan with 120 focus minutes a month and small ads, then Pro subscription (US$1.99 per month or US$14.99 per year) via Play Billing

## Settings you control
`docs/config.json` (hosted on GitHub Pages) sets the free allowance and can switch monetisation off for a promo period. See `docs/RELEASE.md`, section C.

## Before the first upload (must do)
- Change `applicationId` in `app/build.gradle.kts` and `packageName` in `.github/workflows/release.yml`.
- Replace the test AdMob ids (`Ads.kt`, `AndroidManifest.xml`) and set the URLs in `Monetization.kt`.
- Edit `docs/privacy.html` (date, email).
- Add licensed music to `app/src/main/assets/music/`.

## Not in v1 (planned)
QR-scan and step challenges (would add camera and activity-recognition permissions), friend focus rooms, scheduled sessions.
