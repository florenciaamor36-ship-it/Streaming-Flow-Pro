package com.example.data.drive

/** Pure policy for an optional, user-owned Drive appDataFolder backup.
 * No network, Android identity, Room, or Firebase calls belong in this class.
 */
data class LocalDriveState(
    val signedInEmail: String,
    val hasData: Boolean,
    val contentSha256: String,
    /** Last Drive revision this local snapshot was based on; null before first sync. */
    val baseRevision: String? = null,
    val baseSha256: String? = null,
)

data class RemoteDriveState(
    val ownerEmail: String,
    val schemaVersion: Int,
    val revision: String,
    val parentRevision: String?,
    val contentSha256: String,
)

enum class DriveSyncAction {
    DISABLED,
    ACCOUNT_MISMATCH,
    INITIAL_UPLOAD_REQUIRES_CONFIRMATION,
    INITIAL_DOWNLOAD_REQUIRES_CONFIRMATION,
    NO_CHANGES,
    UPLOAD_LOCAL,
    DOWNLOAD_REMOTE,
    CONFLICT_REQUIRES_USER_CHOICE,
    INCOMPATIBLE_SCHEMA,
}

/** Decisions are intentionally conservative: never silently overwrite divergent snapshots. */
object DriveSyncDecision {
    const val CURRENT_SCHEMA_VERSION = 1

    fun decide(
        local: LocalDriveState,
        remote: RemoteDriveState?,
        consented: Boolean,
        confirmInitialUpload: Boolean = false,
        confirmInitialDownload: Boolean = false,
    ): DriveSyncAction {
        if (!consented) return DriveSyncAction.DISABLED
        if (remote == null) {
            return if (local.hasData && confirmInitialUpload) DriveSyncAction.UPLOAD_LOCAL
            else DriveSyncAction.INITIAL_UPLOAD_REQUIRES_CONFIRMATION
        }
        if (!local.signedInEmail.equals(remote.ownerEmail, ignoreCase = true)) {
            return DriveSyncAction.ACCOUNT_MISMATCH
        }
        if (remote.schemaVersion != CURRENT_SCHEMA_VERSION) return DriveSyncAction.INCOMPATIBLE_SCHEMA
        if (!local.hasData && confirmInitialDownload) return DriveSyncAction.DOWNLOAD_REMOTE
        if (!local.hasData) return DriveSyncAction.INITIAL_DOWNLOAD_REQUIRES_CONFIRMATION

        val baseRevision = local.baseRevision ?: return DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE
        val baseDigest = local.baseSha256 ?: return DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE
        if (local.contentSha256 == remote.contentSha256) return DriveSyncAction.NO_CHANGES

        val localChanged = local.contentSha256 != baseDigest
        val remoteChanged = remote.contentSha256 != baseDigest
        if (baseRevision == remote.revision) {
            return if (localChanged && !remoteChanged) DriveSyncAction.UPLOAD_LOCAL
            else if (!localChanged && remoteChanged) DriveSyncAction.DOWNLOAD_REMOTE
            else DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE
        }
        // Remote has advanced beyond our base. A clean local copy can fast-forward;
        // divergent edits always require a user-selected backup/restore path.
        return if (!localChanged && remoteChanged) DriveSyncAction.DOWNLOAD_REMOTE
        else DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE
    }
}
