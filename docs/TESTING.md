# Test checklist (run on a real phone before every production rollout)

## Core
- [ ] First launch shows the disclosure screen; nothing is requested before "I agree".
- [ ] Set as Home app works; pressing Home with no session shows the app list.
- [ ] Grant Do Not Disturb works; the previous DND setting is restored after a session.
- [ ] 1-minute session: only Phone, Contacts, Emergency, End early (and music) are shown.
- [ ] Home, Back and Recents do not break the session; the timer survives killing the app.
- [ ] Emergency opens the dialer with the right number for your region.
- [ ] End early: 5 correct answers end it; one wrong answer restarts.
- [ ] Session that expires while the app was closed is closed out on next launch (DND restored).

## Music
- [ ] Play/Pause only, random order, no skip in the app, notification or lock screen.
- [ ] Music pauses when the session ends. Works with the screen off.

## Stats and awards
- [ ] Week and Month totals match the sessions you ran.
- [ ] Award popup appears on crossing a milestone; Share opens the share sheet with a card.
- [ ] Sunday reminder switch asks for the notification permission only when turned on.

## Style
- [ ] Accent presets and the custom slider update the whole app.
- [ ] Background photo applies, Darken works, Remove restores the dark background.

## Revenue (use a licence-tester account)
- [ ] Fresh install: Focus tab shows "120 of 120 free minutes left this month".
- [ ] Run a 5-minute session: the meter drops to 115 after it ends. A session longer than the minutes left is capped, and the screen says so.
- [ ] With 0 minutes left: Start opens the paywall ("You've used your 120 free minutes this month"). Not now returns to the Focus tab.
- [ ] While minutes are 0, the phone still works: Phone, Contacts, Emergency, the app list and the Home screen all behave normally.
- [ ] Paywall shows monthly and yearly prices from Play, the renewal terms, Restore purchase and Manage subscription.
- [ ] Subscribe (test card): paywall closes, the meter and ads disappear, Style says "Pro". Restore purchase works after reinstalling.
- [ ] Cancelled subscription stays Pro until it expires, then returns to the free plan.
- [ ] `monetization_enabled=false` in config.json: no meter, no ads, Style says "Free for everyone right now".
- [ ] Changing `free_minutes_per_month` changes the meter on the next app open.
- [ ] Ads appear only on Stats, Awards and Style. Never on Focus, Apps, the paywall, during a session, or next to Emergency.
- [ ] EEA/UK test device: consent form appears before the first ad.

## Devices
Samsung (One UI), Xiaomi (MIUI/HyperOS), Oppo or Vivo, a Pixel; Android 8, 12, 14, 16.
