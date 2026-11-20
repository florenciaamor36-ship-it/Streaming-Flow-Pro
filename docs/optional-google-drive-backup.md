# Optional per-user Google Drive backup: audit and safe implementation boundary

## What exists today (Android `main`, inspected source only)

- `AuthRepository` uses Firebase email/password when Firebase works, but on *any* authentication exception it fabricates a local `user_<email>` session and reports success. Guest sessions are local UUIDs. It has no Google Sign-In/account identity.
- `StreamingRepository` writes Room first, then makes best-effort Firestore writes. `FirebaseSyncManager` uses the app's Firebase client, with per-user Firestore paths; this audit made no Firestore calls or changes.
- Room tables are `streaming_accounts`, `profile_sales`, and `clients`, each with `userId`; their DAO APIs expose per-user reads and deletes. Entities include account passwords, profile passwords/PINs, client contact data, and commercial/financial details in plaintext. A cloud backup must treat these as sensitive.
- I found no web serializer/schema or documented cross-platform contract in the inspected Android source. Do not assume Room/Firebase field names are the web schema.

## Isolated change

`DriveSyncDecision` is a pure, network-free policy helper. It requires explicit consent, rejects a Drive owner email that differs from the selected signed-in email, checks schema version, fast-forwards only when one side is unchanged from its recorded base, and surfaces divergent edits for explicit user choice. Its JUnit tests cover consent, identity mismatch, initial confirmations, fast-forward, unchanged snapshots, and conflicts. It does not touch Firebase, Room, or Drive.

This deliberately stops short of claiming an operational sync: the repo currently has no Google account authorization flow or Drive API client, and no app OAuth configuration was provided. Do not add arbitrary OAuth client IDs or secrets.

## Recommended next implementation (once project setup/schema are supplied)

1. Add a separate opt-in control (default off) and a Google account selection/consent flow. Use Google's Android authorization flow requesting **only** `https://www.googleapis.com/auth/drive.appdata`; do not request Drive-wide access. Obtain the actual signed-in Google account email from the authorization result and require an exact case-insensitive match to the authenticated Firebase email. Disable sync for guests and locally fabricated sessions. Never use an administrator/service account or assistant account.
2. Use Drive `appDataFolder` with a single versioned manifest/snapshot. Serialize the complete per-user dataset in a stable canonical order; include schema version, content digest, opaque revision ID, parent revision, update time, and tombstones (or another durable deletion representation). A single atomic snapshot is more efficient and consistent than issuing a request per Room row. Keep a local base revision/digest. Upload a new immutable version, verify its checksum, then update a small head pointer; retain at least one previous version so interrupted writes can recover. Handle Drive revisions/ETags and retry transient/network failures with bounded backoff. Never sync automatically on sign-in before consent.
3. Run serialization and network IO off the main thread; serialize jobs per account. Room remains the offline source of truth. Sync on explicit user action and optionally constrained background work. An offline failure must preserve local edits and show status rather than block CRUD.
4. On first upload/download show dataset counts and require a separate confirmation. On conflict offer restore remote, keep local as a new backup, or export/merge after review; never silently replace data. Keep an auditable last-sync result and provide disconnect/revoke/delete-backup affordances. Account changes must stop jobs and not cross-copy datasets.
5. This payload includes credentials and personal data. `appDataFolder` is hidden from Drive UI, not encryption or a security boundary. Before production, define client-side encryption and recovery (e.g. user-held passphrase/key); do not upload plaintext account passwords/PINs by default. Avoid logging serialized data or OAuth tokens.
6. Agree a web/mobile schema contract first (entity fields, IDs, timestamps, deletion/tombstone semantics, migration rules and conflict model), add shared fixture tests, and only then enable cross-app restore. No web branch/repo/schema was present in this repository inspection.

## Google Cloud / app-owner setup still required

The app owner must configure the OAuth consent screen, app audience (internal vs external and test users as appropriate), package name and signing-certificate SHA fingerprints, and Android OAuth client in the owner's Google Cloud project. The Drive appData scope is sensitive/restricted classification must be checked against Google's current requirements; if the chosen audience/scope requires verification, the owner must submit and complete that review. Publishing/testing with external users may require verification. None of that can be completed by code alone. Do not embed a client secret in Android. Firebase Google sign-in and Drive OAuth are distinct setups.
