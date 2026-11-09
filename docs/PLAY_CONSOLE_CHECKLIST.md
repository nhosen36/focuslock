# Google Play Console checklist (compliance)

Do these in Play Console, App content and Store presence. Wording below is a draft you can paste; check each item against the live form, because Google changes the questions.

## 1. Policy position
| Area | Our position |
|---|---|
| Permissions | No Accessibility, overlay (`SYSTEM_ALERT_WINDOW`), usage stats, device admin, `QUERY_ALL_PACKAGES`, location, contacts, camera, microphone or storage permissions. |
| Home app | A real launcher: it shows a normal app list when no session is running. The user opts in. |
| Exit | Emergency call and "End early" are always on screen. Quitting is made hard with puzzles, but never impossible. |
| Ads | Only on Stats, Awards and Style, only for free users. None on the Focus tab, the app list, or during sessions. |
| Billing | Google Play Billing only: one subscription (`focuslock_pro`) with a monthly and a yearly base plan. Price, renewal terms and a cancel link are shown before purchase. |
| Free limit | 120 free focus minutes per month. Running out only stops new sessions. Calls, emergency calling and normal phone use are never blocked, and a running session is never cut off. |
| Audience | 13 and older. Not designed for children. |
| Claims | No medical or "cures addiction" claims, and no "impossible to quit" claims. |

## 2. App content declarations
- **Privacy policy URL:** your hosted `privacy.html`.
- **Ads:** answer **Yes, contains ads** (even before ads switch on, since the code ships).
- **App access:** all functionality is available without login.
- **Target audience:** choose 13-15, 16-17, 18+. Do not choose under-13 (that triggers the Families policy).
- **Content rating:** answer the IARC questionnaire honestly: no violence, no user-generated content, no gambling. Expect an Everyone / 3+ rating.
- **News app / government / financial / health:** No.
- **Data safety:** see section 3.
- **Foreground service declaration:** type `mediaPlayback`. Use: "plays the user's chosen focus music with play/pause controls; stops when the user pauses." Provide a short video of tapping play and the notification control.
- **Advertising ID:** declare that the app uses it (for ads). The manifest already includes `com.google.android.gms.permission.AD_ID`.

## 3. Data safety form (draft answers)
The app itself has no server and no account. Ads and Billing SDKs are where data collection happens.

| Question | Answer |
|---|---|
| Does the app collect or share user data? | Yes (through the ads SDK) |
| Data types collected | **Device or other IDs** (advertising ID). The AdMob SDK may also collect **approximate location** (from IP), **app interactions** and **diagnostics**. Follow Google's AdMob data-disclosure guidance and tick what it lists. |
| Shared with third parties | Yes: Google AdMob (advertising) |
| Purpose | Advertising or marketing |
| Optional or required | Optional for users who buy Pro or are founding members (ads are not shown) |
| Encrypted in transit | Yes |
| Can users request deletion | Not applicable: no data is held by us. Local data is deleted on uninstall. |
| Focus history, settings, background image | Stay on device, not collected. |

Because ads are off during the launch phase, you can keep the AdMob entries from day one. The form describes what the app **can** do, not what it does today.

## 4. Store listing (draft)
- **App name (30 max):** `FocusLock: Study Focus Mode`
- **Short description (80 max):** `Calls-only focus sessions. Beat doomscrolling and build a study streak.`
- **Full description (draft):**

> Notifications and endless scrolling break your focus. FocusLock turns your phone into a calls-only screen while you work.
>
> HOW IT WORKS
> - Set a timer and start. Only Phone, Contacts and Emergency stay available.
> - Notifications are silenced with Do Not Disturb.
> - Want to quit early? Solve puzzles first. It takes effort by design.
> - Play calm focus music with a single button.
>
> TRACK YOUR PROGRESS
> - Weekly and monthly focus reports
> - Streaks and milestone awards, from your first hour to 1,000 hours
> - Share your progress cards
>
> MAKE IT YOURS
> - Dark design with your own accent color and background photo
>
> PRIVACY
> Your focus history stays on your phone. FocusLock never reads your messages or screen.
>
> NOTE: FocusLock asks to be your Home app and for Do Not Disturb access, only to run focus sessions. You can switch back any time in Settings. You can always make emergency calls.
>
> FREE AND PRO: The free plan gives you 120 focus minutes every month, with small ads on the Stats, Awards and Style screens (never during a session). FocusLock Pro is an optional subscription (monthly or yearly) for unlimited focus time and no ads. Cancel any time in Google Play.

- **Graphics:** ready in `docs/store/`: `icon-512.png` (512x512) and `feature-graphic-1024x500.png`. **Phone screenshots are still to do:** take at least 2 (ideally 4: Focus, a running session, Stats, Awards) on a real phone or a working emulator, in the app's dark style. Play needs at least 2.
- **Category:** Productivity. **Contact email** and **website** (your Pages URL) required.

## 5. Subscription product
- Subscription id `focuslock_pro`. Name: "FocusLock Pro". Benefits (Play asks for them): "Unlimited focus time", "No ads".
- Base plan `monthly`: auto-renewing, billing period 1 month, US\$1.99. Base plan `yearly`: auto-renewing, billing period 1 year, US\$14.99.
- Optional offer: a free trial on `yearly`.
- The app shows the price, the auto-renewal terms and a link to cancel before the user can buy (see `Paywall.kt`). Keep that wording if you edit it.
- Subscription terms must also appear in the store listing (the "FREE AND PRO" paragraph above does this).

## 6. Testing and release tracks
1. Upload to **Internal testing** first (up to 100 testers, immediate).
2. Personal account? Run a **Closed test** with 12+ testers for 14 days, then apply for Production access.
3. Add **licence testers** (Setup, License testing) to test purchases without paying.
4. Staged rollout for Production (10%, then 50%, then 100%).

## 7. Pre-submission checks
- [x] `applicationId` set to `com.dwit.focuslock`, matching `release.yml`.
- [ ] Test AdMob ids replaced, ad units created.
- [x] `RemoteFlags` URLs set to `https://nhosen36.github.io/focuslock/...`. Privacy policy updated with date and `dreamweaveritteam@gmail.com`.
- [ ] Audio files in `assets/music/` all have a licence record.
- [ ] App tested on Android 8 to 16, including a Samsung, a Xiaomi and an Oppo or Vivo phone.
- [x] Target API 36, Billing Library 8 or higher.
- [ ] Emergency call button works on a real SIM.
- [ ] Review the pre-launch report in Play Console and fix crashes.
