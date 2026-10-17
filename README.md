# BikersThrill — Stay connected

Android app (Kotlin + Jetpack Compose + Firebase) for verified motorcycle riders: rider KYC with admin
approval, ride groups with chat and a live map, shared ride plans, SOS and crash detection.

Package: `com.satya.biker` · Firebase project: `satya-digital-34053`

## One-time Firebase setup

1. **Authentication** → Sign-in method → enable **Email/Password**.
2. **Firestore Database** → Create database (production mode, region `asia-south1` recommended).
3. **Deploy security rules** (they enforce that only APPROVED riders can use groups/rides/SOS):
   ```
   npm install -g firebase-tools
   firebase login
   firebase deploy --only firestore:rules
   ```
   Or paste `firestore.rules` into Firestore → Rules → Publish.
4. **Super admin** — `deep.pkg@gmail.com` (set in `Roles.SUPER_ADMIN_EMAILS` in `Models.kt` and
   `superAdminEmail()` in `firestore.rules`; change both together).
   1. Register that email in the app (or create it in Firebase console → Authentication → Add user).
   2. Sign in normally — the account becomes admin automatically and opens the admin console.
   3. To make someone else an admin: Admin console → **All users** → open the user → **Make admin**.
      Any admin can do this; nobody can change or remove the super admin.

No Cloud Storage or Blaze plan is required: KYC photos are compressed (~150 KB each) and stored in
`kyc/{uid}`, readable only by the rider and admins.

## How it works

| Flow | What happens |
|---|---|
| Register | Email, password, name, phone → account created with status `INCOMPLETE` |
| Verification | 5-step wizard: basic details → ID & licence → bike & emergency contacts → document photos (ID front/back, licence front/back, selfie) → review & declaration → status `PENDING` |
| Admin | Same login; admins are detected automatically → pending queue with search, zoomable documents, approve / reject (with reason) / suspend / reinstate, live SOS board |
| Approved rider | Home (SOS, ride tracking, quick help, emergency contacts), Groups, Rides, Profile |
| Rejected rider | Sees the admin's reason and can fix and resubmit |

### Rider features
- **Ride groups**: create (you become Captain), invite by 6-digit code or share sheet, roles
  Captain / Sweep / Member, group chat, quick messages ("Fuel stop", "Puncture", "Regroup"…),
  location messages, live group map (OpenStreetMap, no API key) with speed, battery, distance from
  captain and "Behind" / "Stopped?" flags.
- **Rides**: plan a ride (start, destination, meeting point, time, distance, difficulty, max riders),
  publish to everyone or to one group, join/leave, share via WhatsApp etc. with a Google Maps route link.
- **SOS**: hold-to-send button (2 s), group SOS button. Sends location, blood group and bike number to
  every group the rider is in (red chat message + loud alarm notification) and SMS to emergency contacts.
- **Ride tracking**: foreground service shares live location every ~10 s, tracks distance/speed and
  runs **crash detection** (impact + stillness → 30 s full-screen countdown with alarm → auto SOS).
- Quick help: call 112, nearby hospital / police / fuel / mechanic, share my location.

## Notes and next steps
- Push notifications to riders whose app is fully closed need a Cloud Function (Blaze plan) that
  sends FCM to group members' `fcmToken` on new `sos` documents. The app already stores tokens and
  handles incoming messages (`BikerMessagingService`). Without it, alerts arrive while the app is
  open or a ride is active.
- `SEND_SMS` and background location are sensitive Play Store permissions — prepare the safety
  use-case declaration before publishing.
- Spec modules not built yet: offline maps & turn-by-turn navigation, fuel range, weather along route,
  expense splitter, push-to-talk, ride journal/badges.
