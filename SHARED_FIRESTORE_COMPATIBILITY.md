# Shared Firestore compatibility: blocked pending an explicit contract

This note is a safety boundary, not a claim that Android and web are compatible. The repair branch was based on Android audit commit `8022a2d3382d9d173a84465a270158f9b0f13f71` and did not connect to Firebase or read, change, migrate, or delete live data.

## What is known

- Firebase project: `stream-flow-pro`.
- It currently has a web Firebase app registered; no Android Firebase app or Android `google-services.json` was present at audit time.
- The observed Firestore rules authorize owner access at `/usuarios/{userId}` and authenticated access to `/tokens_usados/{token}`. This does **not** establish access to child collections, nor permit the Android paths below.
- Android's existing `FirebaseSyncManager` writes `/users/{uid}/accounts/{id}`, `/users/{uid}/sales/{id}`, and `/users/{uid}/clients/{id}`. The top-level `users` path does not match the observed Spanish `usuarios` rule. Consequently those writes cannot be presumed authorized or compatible with the web app.
- Android's Room models include account/profile passwords, PINs, contact details and notes. No matching web schema or data-minimization review has been verified, so these records must not be copied into production Firestore by guesswork.

## Current behavior in this branch

Android now has allowlisted account/sale read/write projections at the shared candidate paths and an owner-gated fetch-to-Room path. These use the same candidate field names as web, keep account email/password and PINs local, and are covered by synthetic JVM mapper round-trip tests. The Android Firestore gate `BuildConfig.FIRESTORE_SYNC_ENABLED` remains hard-coded `false`. That fail-closed gate is tested and causes sync attempts to be reported as failed/not synced rather than silently pretending that local Room changes reached the cloud. This branch does not add a way to turn it on through secrets, Gradle properties, or Firebase configuration. It does not change Firestore rules or project/app configuration. Room schema, migration behavior and existing local records are untouched.

## Required contract before enabling sync

The web and Android maintainers must first agree on one canonical, UID-owned schema and field mapping, including stable document IDs, timestamps/versioning, deletion semantics, conflict policy, and whether any account credentials or PINs are excluded/encrypted. Then both clients must be tested against the same **non-production** Firebase project or Firestore emulator with owner-isolation rules. Rules must authorize only `request.auth.uid == userId` for every supported document operation, deny cross-user access, and separately review the tokens collection; client-side UID checks alone are not security rules.

After a reviewed contract is established, implement an explicit adapter for that exact schema, test serialization/deserialization and reconciliation on copied fixture data, and only then arrange an owner-approved production rollout. Do not run a broad import, overwrite, cleanup, migration, or delete against the existing database. Preserve a verified backup/export and test restore before any production data migration. Existing web documents should remain untouched during validation.

## Registration and local testing

When an owner chooses to register Android later, the exact current Android `applicationId` is `com.aistudio.controlstreaming.sales` (defined in `app/build.gradle.kts`). Register that package in Firebase project `stream-flow-pro`, download its generated config privately into `app/google-services.json` (the file is ignored by git), and confirm the generated Firebase project ID before use. This registration is not performed by this branch.

A safe first integration test uses the Firestore emulator or a separate disposable Firebase project with synthetic fixtures only: two test users; read/write their own fixtures; assert each cannot read/write the other's data; test Android/web round-trip with agreed fields; test retries, duplicate writes, conflicts and deletion semantics; verify the production project is not selected; discard the test project. No real customer/account passwords or production documents should be used.

## Field-level audit follow-up (2026-11-19)

A shared candidate contract and field comparison now lives in the web repo branch's `FIELD_LEVEL_CONTRACT.md`; it records that the client/customer fields can be mapped, but account and sale schemas differ in names, timestamps, relationships and contain credentials/PINs. Android now has an allowlisted client DTO mapping (`userId`, `name`, `phone`, optional `email`/`notes`, UTC ISO `createdAt`) and JVM tests. Account/sale writes now use allowlisted DTO projections and reads map those DTOs back into local Room entities while preserving existing local-only credentials. Android JVM mapper round-trip tests use synthetic data. The hard-coded-false gate remains off; no live Firestore integration is claimed. The web branch contains a Firestore Emulator Suite rule test using only synthetic users/data. It exercises candidate `/users/{uid}/...` rules, not production rules; production `/usuarios` and `/tokens_usados` rules/data remain untouched. See web branch `FIELD_LEVEL_CONTRACT.md` for execution steps and explicit limitations.

## Build fix note (2026-11-19)

The audit CI run at commit `c9366873d9be78d0d5e558a67c9eb156be86811f` failed during Kotlin compilation because `FirebaseSyncManager.kt` referenced the generated `BuildConfig` without importing its namespace-qualified class. The Android Gradle namespace is `com.example` while the installable application ID is `com.aistudio.controlstreaming.sales`; the fix imports `com.example.BuildConfig`. This changes no Firebase configuration, package ID, Firestore path, or sync enablement.


## Candidate account/sale mappings added (2026-11-19)

- Account web `platform`, `purchaseDate`, `renewalDate`, `totalProfiles`, and owner fields map to Android `platformName`, epoch-millis dates, `maxProfiles`, and `userId`. `providerName`, `cost`, and notes are retained. Account email/password, PINs, supplier phone, and individual profile status are excluded. Shared profile slots are synthesized for the web UI; web derives sold state from the sales relation.
- Sale `type`, `startDate`, `endDate`, `status`, `paymentStatus`, customer/platform/financial fields map to Android enums and epoch millis. Account password/email and profile PIN stay local-only. Android emits safe candidate docs; incoming docs preserve pre-existing local-only fields on update.
- Missing data not in the candidate contract is not reconstructed: supplier phone, payment method and protected credentials are blank/default on newly imported records. Product UX and ID collision/conflict/deletion reconciliation still need a dedicated review.
- Current rules file is an emulator-only fixture. The web repo contains a separate additive-only proposal; it must be merged against a fresh copy of actual rules and fully regression-tested before any owner deployment. No live rule/data/auth configuration was modified.
