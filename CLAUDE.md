# BikersThrill — instructions for Claude

## Before any code change
1. **Read `SPEC.md` first.** It is the source of truth for what is built: roles, verification flow,
   Firestore data model, security rules, code map and versioning. Don't contradict it silently.

## With every code change
2. **Update `SPEC.md` in the same change**: features, data model, rules summary, code map, known gaps,
   and add a line to the §9 Change log: `<VERSION_NAME> (<VERSION_CODE>) — <date> — <summary>`.
   Take the version from `app/version.properties` *after* building.
3. **Versioning is automatic.** Any change under `app/src/main`, `app/build.gradle.kts` or
   `gradle/libs.versions.toml` bumps `VERSION_CODE` and the patch of `VERSION_NAME` on the next build.
   Never edit `SOURCE_HASH`, never lower `VERSION_CODE`, don't bump by hand (except major/minor of
   `VERSION_NAME` when the user asks for one).
4. **Build to verify** and to apply the version bump:
   `./gradlew :app:assembleDebug` (release: `./gradlew :app:bundleRelease`). Report the new version.
5. **Firestore rules**: if data access changes, update `firestore.rules` and deploy with
   `firebase deploy --only firestore:rules` (folder is linked to `support.satyadigital@gmail.com`).
   Ask before deploying.
6. **Super admin email** lives in two places — `Roles.SUPER_ADMIN_EMAILS` (`data/Models.kt`) and
   `superAdminEmail()` (`firestore.rules`). Change both together, then redeploy rules.

## Conventions
- Kotlin + Jetpack Compose (Material 3); Firestore only through `data/Repositories.kt`.
- Model classes use `var` fields with defaults; computed properties need `@get:Exclude`.
- Firestore queries stay single-field (no composite indexes); sort/filter client-side.
- Package `com.satya.biker`; never commit `keystore.properties`, `*.jks`.
