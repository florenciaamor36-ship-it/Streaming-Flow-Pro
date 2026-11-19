# Shared Firestore compatibility: blocked pending an explicit contract

This note is a safety boundary, not a claim that Android and web are compatible. The repair branch was based on Android audit commit `8022a2d3382d9d173a84465a270158f9b0f13f71` and did not connect to Firebase or read, change, migrate, or delete live data.

## What is known

- Firebase project: `stream-flow-pro`.
- It currently has a web Firebase app registered; no Android Firebase app or Android `google-services.json` was present at audit time.
- The observed Firestore rules authorize owner access at `/usuarios/{userId}` and authenticated access to `/tokens_usados/{token}`. This does **not** establish access to child collections, nor permit the Android paths below.
- Android's existing `FirebaseSyncManager` writes `/users/{uid}/accounts/{id}`, `/users/{uid}/sales/{id}`, and `/users/{uid}/clients/{id}`. The top-level `users` path does not match the observed Spanish `usuarios` rule. Consequently those writes cannot be presumed authorized or compatible with the web app.
- Android's Room models include account/profile passwords, PINs, contact details and notes. No matching web schema or data-minimization review has been verified, so these records must not be copied into production Firestore by guesswork.

## Current behavior in this branch

All Android Firestore mutations are behind `BuildConfig.FIRESTORE_SYNC_ENABLED`, which is hard-coded `false`. That fail-closed gate is tested and causes sync attempts to be reported as failed/not synced rather than silently pretending that local Room changes reached the cloud. This branch does not add a way to turn it on through secrets, Gradle properties, or Firebase configuration. It does not change Firestore rules or project/app configuration. Room schema, migration behavior and existing local records are untouched.

## Required contract before enabling sync

The web and Android maintainers must first agree on one canonical, UID-owned schema and field mapping, including stable document IDs, timestamps/versioning, deletion semantics, conflict policy, and whether any account credentials or PINs are excluded/encrypted. Then both clients must be tested against the same **non-production** Firebase project or Firestore emulator with owner-isolation rules. Rules must authorize only `request.auth.uid == userId` for every supported document operation, deny cross-user access, and separately review the tokens collection; client-side UID checks alone are not security rules.

After a reviewed contract is established, implement an explicit adapter for that exact schema, test serialization/deserialization and reconciliation on copied fixture data, and only then arrange an owner-approved production rollout. Do not run a broad import, overwrite, cleanup, migration, or delete against the existing database. Preserve a verified backup/export and test restore before any production data migration. Existing web documents should remain untouched during validation.

## Registration and local testing

When an owner chooses to register Android later, the exact current Android `applicationId` is `com.aistudio.controlstreaming.sales` (defined in `app/build.gradle.kts`). Register that package in Firebase project `stream-flow-pro`, download its generated config privately into `app/google-services.json` (the file is ignored by git), and confirm the generated Firebase project ID before use. This registration is not performed by this branch.

A safe first integration test uses the Firestore emulator or a separate disposable Firebase project with synthetic fixtures only: two test users; read/write their own fixtures; assert each cannot read/write the other's data; test Android/web round-trip with agreed fields; test retries, duplicate writes, conflicts and deletion semantics; verify the production project is not selected; discard the test project. No real customer/account passwords or production documents should be used.
