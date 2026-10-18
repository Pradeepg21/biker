# BikersThrill — Technical Spec (source of truth)

> **Read this file before changing any code, and update it in the same change.**
> Product vision / long-term roadmap lives in the "BikersThrill — Product Spec" doc
> (https://claude.ai/artifact/6TydwE9u8N62R4K6F8KQRG). This file describes **what is actually built**.

| | |
|---|---|
| App name | BikersThrill — "Stay connected" |
| Package / applicationId | `com.satya.biker` |
| Firebase project | `satya-digital-34053` (account `support.satyadigital@gmail.com`) |
| Super admin | `deep.pkg@gmail.com` |
| Stack | Kotlin, Jetpack Compose (Material 3), Firebase Auth (email/password + Google) + Firestore + FCM, Credential Manager, Play Services Location, osmdroid (OpenStreetMap) |
| SDK | minSdk 26, target/compile 37, Java 17, AGP 9.3.3, Kotlin 2.4.10 |
| Current version | see `app/version.properties` (auto-managed) |

---

## 1. Users, roles and the verification gate

| Status (`users.status`) | Rider sees | How it gets there |
|---|---|---|
| `INCOMPLETE` | Verification wizard | Set on registration |
| `PENDING` | "Verification in progress" screen | Rider submits wizard |
| `APPROVED` | Full rider app | Admin approves (or super admin grants admin) |
| `REJECTED` | Wizard again, with admin's reason banner | Admin rejects with reason |
| `SUSPENDED` | "Account suspended" screen | Admin suspends approved rider |

Roles (`users.role`): `rider` (default) or `admin`.
- **Super admin** = account whose email is in `Roles.SUPER_ADMIN_EMAILS` (`data/Models.kt`) **and**
  `superAdminEmail()` (`firestore.rules`). Always treated as admin; on sign-in the app writes
  `role=admin, status=APPROVED` (`UserRepository.ensureSuperAdmin`). Nobody else can change or
  remove the super admin. Keep the email identical in both files.
- **Admin**: approve / reject / suspend / reinstate riders, view every user's full profile and KYC
  documents, groups and SOS history, live SOS board, CSV export, and **make other users admin or
  remove admin access** (any admin, any user except the super admin and themselves).
- **One login screen for everyone** (no Admin tab). After sign-in the app routes by account:
  `isAdmin` → admin console, otherwise by verification status.

Routing lives in `MainActivity.kt` (`Session` → `screenKey`): signed out → `AuthFlow`; admin →
`AdminApp`; otherwise by status as in the table.

## 2. Features (as built)

### Registration & verification (`ui/auth`, `ui/verify`)
- Register: full name, mobile, email, password (+confirm). Forgot-password email.
- **Continue with Google** (login and register screens): Credential Manager
  `GetSignInWithGoogleOption` with `R.string.default_web_client_id` → Firebase `GoogleAuthProvider`
  (`AuthRepository.signInWithGoogle`). First Google sign-in creates `users/{uid}` (role rider,
  INCOMPLETE, name/email from Google) → same verification wizard. Cancelling the picker is silent.
  Requires the Google provider enabled in Firebase Auth (it is) and every signing key's SHA-1
  registered on the Firebase Android app (debug, upload and Play App Signing keys).
- Wizard (5 steps, saves on each "Save & continue"):
  1. Basic details — name, mobile, DOB (date picker), gender, blood group, address, city, state, PIN
  2. ID & licence — ID type (Aadhaar/PAN/Passport/Voter ID), ID number, DL number, DL expiry, experience
  3. Bike & safety — make, model, registration no., 1–3 emergency contacts, medical notes
  4. Documents — ID front*, ID back, DL front*, DL back, selfie* (camera or gallery; *required)
  5. Review + declaration checkbox → submit (`status=PENDING`)
- Validation: `ProfileValidation` in `ui/verify/ProfileForms.kt`.

### Admin console (`ui/admin/AdminScreens.kt`)
- Stat tiles (Pending, Verified, Active SOS). Tabs: Pending, Verified, Rejected, Suspended,
  Incomplete, **All users**, **Admins**, Live SOS. Search by name/phone/email/licence/bike no.
- User profile screen: selfie + zoomable documents, all personal/ID/bike/emergency/medical data,
  groups (role, size, code), SOS history with map links, account & audit info.
- Actions: Approve, Reject (preset or custom reason), Suspend (reason), Reinstate,
  **Make admin / Remove admin access** (any admin; hidden for self and the super admin).
- Top bar: **Export CSV** of all users (share sheet), sign out.

### Rider app (`ui/rider`)
- **Home**: active SOS cards (own SOS has "I'm safe – cancel"), ride card (Start/End ride, distance,
  speed, top speed, time), **hold-2-seconds SOS** button + confirm, quick help (Call 112, hospital,
  police, fuel, mechanic, share location), emergency contacts with call, emergency card.
- **Groups**: create (creator = Captain, 6-digit unique code), join by code, share invite text.
  Group screen tabs: **Chat** (text, quick messages, location messages, system & SOS messages),
  **Live map** (osmdroid pins per rider, speed, battery, distance from captain, "Behind" > 3 km,
  "Stopped?" > 5 min stale, SOS pins), **Riders** (roles Captain/Sweep/Member, call, captain can
  change roles/remove), **Rides**. Group SOS button, leave (or delete if owner). Max 50 riders.
- **Rides**: plan (title, start, destination, meeting point, date/time, distance, difficulty, max
  riders, public or one group, notes); tabs Upcoming / My rides / Past; detail with join/leave,
  participants, open meeting point, delete (creator); share text with Google Maps route link.
- **Profile**: details, edit bike & emergency contacts (identity fields locked after approval),
  safety settings, sign out, **Delete account**, version shown from `BuildConfig`.
- **Delete account** (Play requirement; `DeleteAccountDialog` in `ProfileScreens.kt`, also on the
  pending/suspended screens): needs a sign-in within the last 4 min (else signs out and asks to sign in
  again). `UserRepository.deleteAccount`: approved riders leave groups (sole-member groups owned by them
  are deleted), their rides are deleted, they're removed from joined rides, their SOS docs deleted; then
  `locations/{uid}`, `kyc/{uid}`, `users/{uid}` and finally the Auth user. Group chat messages stay.
  The super admin can't delete itself. Web instructions: `docs/delete-account.html`.

### Safety (`service/`)
- `RideService` (foreground, type location): location every ~10 s → `locations/{uid}`, ride stats
  (`RideService.stats` StateFlow), watches SOS in the rider's groups, notification with SOS / End ride.
- **Crash detection**: accelerometer impact > threshold g (Low 6 / Medium 4.5 / High 3.5) then ~4 s
  mostly still and speed < 8 km/h → `CrashAlertActivity` (full-screen intent, alarm, vibration,
  30 s countdown, "I'M OK" / "SEND SOS NOW") → auto SOS. 60 s cooldown.
- `SosManager.trigger`: current location → `sos/{id}` with `groupIds` of all rider's groups + red SOS
  chat message in each group. **No SMS permission**: `SosManager.textContacts` opens the rider's SMS
  app (`Intents.composeSms`, `ACTION_SENDTO smsto:a;b`) with the SOS text for all emergency contacts
  when "Text emergency contacts" is on (Home SOS, group SOS and the crash screen).
- `SosNotifier`: high-priority alarm notification for other riders' SOS (once per alert per process).
- `SafetySettings` (SharedPreferences "safety"): crash detection on/off, sensitivity, SMS fallback.
  Safety settings also shows "Allow full-screen alerts" on Android 14+ when
  `NotificationManager.canUseFullScreenIntent()` is false (opens the system setting).
- `BikerMessagingService`: stores FCM token in `users.fcmToken`, shows pushed notifications
  (`data.type == "SOS"` uses the SOS channel). No server sender exists yet.
- Notification channels (`BikerApp`): `sos_alerts` (alarm, bypass DND), `ride_tracking`, `general`.

## 3. Data model (Firestore)

| Collection | Doc id | Key fields | Written by |
|---|---|---|---|
| `users` | uid | uid, email, role, status, rejectionReason, fullName, phone, dateOfBirth, gender, bloodGroup, address, city, state, pincode, idType, idNumber, licenseNumber, licenseExpiry, bikeMake, bikeModel, bikeRegNumber, emergencyContacts[{name,relation,phone}], medicalNotes, ridingExperienceYears, fcmToken, createdAt, submittedAt, reviewedAt, reviewedBy | rider (own, limited), admins |
| `kyc` | uid | idFront, idBack, licenseFront, licenseBack, selfie (Blob JPEG ≤ ~170 KB each), updatedAt | rider while INCOMPLETE/REJECTED |
| `groups` | auto | name, description, code, ownerId, memberIds[], members{uid:{uid,name,phone,bike,bloodGroup,role}}, createdAt, lastMessage, lastMessageAt | approved riders |
| `groups/{id}/messages` | auto | senderId, senderName, text, type (TEXT/QUICK/SOS/LOCATION/SYSTEM), lat?, lng?, createdAt | group members |
| `locations` | uid | uid, name, bike, lat, lng, speedKmh, battery, sharing, updatedAt | owner |
| `sos` | auto | uid, name, phone, bloodGroup, bikeRegNumber, lat, lng, reason, groupIds[], active, createdAt, resolvedAt | approved rider (own) |
| `rides` | auto | title, description, startPoint, destination, meetingPoint, startTime, distanceKm, difficulty, maxRiders, visibility (PUBLIC/GROUP), groupId, groupName, createdBy, creatorName, participantIds[], participantNames{}, createdAt | approved riders |

Models: `data/Models.kt` (all fields `var` with defaults for Firestore mapping; computed getters use
`@get:Exclude`). All Firestore access goes through the objects in `data/Repositories.kt`.
Queries are single-field only (no composite indexes needed); sorting/filtering is client-side.

## 4. Security rules (`firestore.rules`) — summary
- Only `APPROVED` users (or admins) can read/write groups, messages, locations, SOS, rides.
- Riders: can't change their role, can't self-approve (only INCOMPLETE/REJECTED → PENDING), can't
  touch review fields, identity fields locked once APPROVED. Users docs readable by owner + admins only.
- Admins: update any user, including granting/removing admin, except the super admin's doc.
  Super admin: anything. Users docs can be deleted by the super admin or by the owner (not the super admin's own).
- KYC docs: owner (while not approved) + admins; the owner can always delete their own.
- SOS docs: owner can delete their own (account deletion).
- **Deploy after every rules change:** `firebase deploy --only firestore:rules` (project folder is
  linked to `support.satyadigital@gmail.com` via `firebase login:use`).

## 5. Code map

```
app/src/main/java/com/satya/biker/
  BikerApp.kt                 Application: osmdroid UA, notification channels, debug emulator wiring
  MainActivity.kt             SessionViewModel, root routing, super-admin bootstrap, FCM token
  data/Models.kt              data classes, enums, roles, constants (quick messages, blood groups…)
  data/Repositories.kt        Auth/User/Admin/Group/Location/Sos/Ride repositories (Firestore)
  service/RideService.kt      foreground tracking + crash detection + group SOS watcher
  service/CrashAlertActivity.kt  30 s countdown screen
  service/Safety.kt           SafetySettings, SosManager, SosNotifier
  service/BikerMessagingService.kt  FCM
  ui/theme/Theme.kt           colours (BrandOrange, SosRed…), typography
  ui/components/Components.kt shared composables (fields, buttons, cards, chips, images)
  ui/auth/AuthScreens.kt      single login (email + Google), register, reset password
  ui/verify/ProfileForms.kt   form sections + validation + DateField
  ui/verify/VerificationScreens.kt  wizard, document upload, pending/suspended screens
  ui/admin/AdminScreens.kt    admin console, user profile/review, role management, CSV export
  ui/rider/RiderApp.kt        rider nav graph, bottom tabs, Home, SOS button, ride card
  ui/rider/GroupScreens.kt    groups list, group detail (chat/map/riders/rides)
  ui/rider/RideScreens.kt     rides list, create ride, ride detail, share text
  ui/rider/ProfileScreens.kt  profile, edit profile, safety settings
  ui/rider/OsmMap.kt          osmdroid Compose wrapper + pin bitmaps
  util/Utils.kt               image compression, location, SMS composer, intents, formatting
app/src/debug/                debug-only manifest + network_security_config (cleartext for emulators)
res/drawable/ic_launcher_*    adaptive launcher icon (orange gradient + bike + signal); master in store/src/icon.svg
firestore.rules, firebase.json, .firebaserc   Firebase CLI deployment (rules, hosting, emulators)
docs/                         website (GitHub Pages; also Firebase Hosting public dir): index, privacy.html,
                              delete-account.html → https://pradeepg21.github.io/biker/
store/                        Play listing: PLAY_STORE_LISTING.md (texts + all Console answers),
                              graphics/ (512 icon, 1024x500 feature graphic), screenshots/ (phone,
                              tablet-7, tablet-10), raw/ (unframed captures), src/ (generators below)
app/version.properties        auto-managed version (see §7)
```

## 6. Permissions
INTERNET, NETWORK_STATE, FINE/COARSE location, FOREGROUND_SERVICE(+_LOCATION), POST_NOTIFICATIONS,
USE_FULL_SCREEN_INTENT, VIBRATE, WAKE_LOCK, HIGH_SAMPLING_RATE_SENSORS. Camera via
`TakePicture` + FileProvider (`res/xml/file_paths.xml`: `captures/`, `exports/`).
**No SEND_SMS** (restricted on Google Play; SOS texts go through the SMS app).
**No advertising ID**: the manifest strips `AD_ID`, `ACCESS_ADSERVICES_AD_ID` and
`ACCESS_ADSERVICES_ATTRIBUTION` (merged in by Firebase Analytics, `tools:node="remove"`) and sets
`google_analytics_adid_collection_enabled=false`; Play Console's Advertising ID answer is **No**. Play Console needs
the foreground-service (location) and full-screen-intent declarations — answers in
`store/PLAY_STORE_LISTING.md`.

## 7. Versioning & release
- `app/version.properties`: `VERSION_CODE`, `VERSION_NAME`, `SOURCE_HASH`.
- On every Gradle build/sync, `app/build.gradle.kts` hashes `app/src/main/**`, `app/build.gradle.kts`
  and `gradle/libs.versions.toml` (line endings normalised). If the hash differs from `SOURCE_HASH`,
  **VERSION_CODE += 1 and the patch of VERSION_NAME += 1**, and the file is rewritten. No change → no bump.
- So: change code → build → the APK/AAB already carries a new, higher version. Never edit
  `SOURCE_HASH` or lower `VERSION_CODE`. For a minor/major release, edit `VERSION_NAME` (e.g. `1.1.0`)
  by hand; later code changes continue from there (`1.1.1`, …).
- Release build: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
  Signed automatically when `keystore.properties` exists in the project root (git-ignored):
  ```
  storeFile=upload-keystore.jks
  storePassword=…
  keyAlias=upload
  keyPassword=…
  ```
  Create the key once: `keytool -genkeypair -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`
  and back it up.

### Store assets & demo screenshots
- Listing text, Data safety, content rating, app access, permission declarations, closed-testing
  steps and ASO notes: `store/PLAY_STORE_LISTING.md`.
- Privacy policy / account deletion pages: `docs/`, served by GitHub Pages from `main` /docs of
  https://github.com/Pradeepg21/biker → `https://pradeepg21.github.io/biker/privacy.html` and
  `/delete-account.html` (these URLs are in the Play listing). Pushing to `main` updates them.
  Firebase Hosting can serve the same folder (`firebase deploy --only hosting`, ask first).
- Foreground-service declaration video: `docs/video/bikersthrill-location-demo.mp4` (page
  `docs/location-demo.html`), recorded on the emulator with demo data and cut/captioned with ffmpeg.
- Re-shooting screenshots with demo data (never touches production):
  1. `firebase emulators:start --only auth,firestore` (needs Java 21: use Android Studio's `jbr`).
  2. `python -I store/src/seed_demo.py` (demo riders, groups, chat, rides, live locations;
     `--sos` / `--clear-sos` toggles an SOS; `store/src/refresh_locs.py` refreshes positions).
  3. `./gradlew :app:assembleDebug -PuseEmulators=true -PemulatorHost=<PC LAN IP>` — on this PC's
     emulator apps can't reach 10.0.2.2, the LAN IP works. Sign in `arjun@demo.bikersthrill.app` /
     `Demo@1234` (admin: `admin@demo.bikersthrill.app`).
  4. Capture with adb (`wm size 1600x2560` / `1200x1920`, `wm density 320` for tablets), save to
     `store/raw/…`, then `python -I store/src/make_screenshots.py` frames them (headless Chrome).
  Icon/feature graphic: `store/src/icon.svg`, `store/src/feature.html` via `store/src/render.sh`.

## 8. Known gaps / next steps
- FCM push when app is closed needs a Cloud Function (Blaze) sending to members' `fcmToken` on new `sos` docs.
- Not built from product spec: offline maps & turn-by-turn, fuel range, weather, expense splitter,
  push-to-talk, ride journal, badges, clubs/events, Hindi localisation.
- Any group member can update the group doc (rules don't restrict role changes to the captain).

## 9. Change log
Add one line per change (newest first): `version — date — summary`.

- 1.0.6 (7) — 2026-10-17 — SOS/emergency-contact wording matches the SMS-app flow.
- 1.0.7 (8) — 2026-10-18 — Removed advertising-ID permissions (Play declaration: No); Analytics ad-ID collection off.
- 1.0.6 (7) — 2026-10-18 — Code pushed to GitHub (Pradeepg21/biker); website moved to `docs/` for GitHub Pages privacy policy; listing text tuned for search; FGS location demo video on GitHub Pages.
- 1.0.5 (6) — 2026-10-17 — Debug-only `FIREBASE_EMULATOR_HOST` build field (`-PemulatorHost`).
- 1.0.4 (5) — 2026-10-17 — Play readiness: removed SEND_SMS (SOS opens SMS app), in-app Delete account (+ rules: owner can delete users/kyc/sos docs — not yet deployed), full-screen-intent permission prompt on Android 14+, new adaptive launcher icon, debug-only Firebase emulator switch; store/ listing, graphics & screenshots; hosting/ privacy policy & deletion page.

- 1.0.3 (4) — 2026-10-17 — Super admin email corrected to deep.pkg@gmail.com (was typo gmai.com); rules redeployed.
- 1.0.2 (3) — 2026-10-17 — Removed Rider/Admin login tabs (admins auto-detected); any admin can now make/remove admins (rules deployed).
- 1.0.1 (2) — 2026-10-17 — Continue with Google sign-in (login, register, admin tab); Google profile created on first sign-in.
- 1.0.0 (1) — 2026-10-17 — Auto versioning, release signing hook, SPEC.md/CLAUDE.md; version shown in Profile.
- (pre-1.0.0) — 2026-10-17 — Super admin `deep.pkg@gmail.com`; admin All users/Admins tabs, full user profile with groups & SOS history, make/remove admin, CSV export; rules updated & deployed.
- (pre-1.0.0) — 2026-10-16 — Initial app: auth, rider KYC wizard, admin approval, groups (chat/map/roles), rides, SOS, crash detection, ride tracking, Firestore rules.
