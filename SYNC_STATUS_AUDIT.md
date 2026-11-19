# Sync status follow-up (audit branch only)

This follow-up starts at `c688438ed071efaaf3bcc247503fd8d4d42aa8ab` and changes Android code/tests only. It does not touch the database schema or contents, Firebase project configuration, Firestore rules, credentials, APK/release workflows, or production deployments.

## Behavior added

- Firestore sync attempts publish a small state (`syncing` / `hasError`) consumed by the main UI. Errors use a fixed generic message; Firebase exception text and record data are not exposed in UI or logs.
- Firestore failures remain non-fatal to local Room writes, and now visibly warn that the change may not have reached the cloud. Guest/local-only records are intentionally skipped without a warning.
- Sync calls are serialized within the manager and coroutine cancellation is rethrown after progress is cleared.
- Unit tests cover state transitions, failure visibility, dismiss, and cancellation.

## Not solved / limitations

- This is a visible error indicator, **not** a durable offline queue and not a conflict-resolution protocol. A failure does not create a retry record; an eventual retry cannot safely reconstruct deletes after the Room row has been removed. Do not promise automatic retry or guaranteed cloud convergence.
- Firestore rules remain project-specific and are not deployed or supplied here. No Firebase emulator / project-backed rules test was run.
- No schema change, migration, backup/restore, import, overwrite, or deletion of local data is included. Room data remains device-local and unchanged by this patch.
