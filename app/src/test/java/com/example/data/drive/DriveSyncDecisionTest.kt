package com.example.data.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveSyncDecisionTest {
    private val local = LocalDriveState("owner@example.com", true, "local", "r1", "base")
    private val remote = RemoteDriveState("owner@example.com", 1, "r1", null, "base")

    @Test fun consentIsRequired() {
        assertEquals(DriveSyncAction.DISABLED, DriveSyncDecision.decide(local, remote, false))
    }

    @Test fun rejectsDifferentDriveAccount() {
        assertEquals(DriveSyncAction.ACCOUNT_MISMATCH, DriveSyncDecision.decide(
            local, remote.copy(ownerEmail = "other@example.com"), true))
    }

    @Test fun requiresConfirmationForFirstUploadAndRestore() {
        assertEquals(DriveSyncAction.INITIAL_UPLOAD_REQUIRES_CONFIRMATION,
            DriveSyncDecision.decide(local.copy(baseRevision = null, baseSha256 = null), null, true))
        assertEquals(DriveSyncAction.UPLOAD_LOCAL,
            DriveSyncDecision.decide(local.copy(baseRevision = null, baseSha256 = null), null, true, confirmInitialUpload = true))
        assertEquals(DriveSyncAction.INITIAL_DOWNLOAD_REQUIRES_CONFIRMATION,
            DriveSyncDecision.decide(local.copy(hasData = false), remote, true))
        assertEquals(DriveSyncAction.DOWNLOAD_REMOTE,
            DriveSyncDecision.decide(local.copy(hasData = false), remote, true, confirmInitialDownload = true))
    }

    @Test fun detectsNoChangesAndLocalOnlyEdit() {
        assertEquals(DriveSyncAction.NO_CHANGES,
            DriveSyncDecision.decide(local.copy(contentSha256 = "base"), remote, true))
        assertEquals(DriveSyncAction.UPLOAD_LOCAL,
            DriveSyncDecision.decide(local, remote, true))
    }

    @Test fun cleanLocalCopyFastForwardsRemoteChange() {
        val changedRemote = remote.copy(revision = "r2", parentRevision = "r1", contentSha256 = "remote")
        assertEquals(DriveSyncAction.DOWNLOAD_REMOTE,
            DriveSyncDecision.decide(local.copy(contentSha256 = "base"), changedRemote, true))
    }

    @Test fun divergentChangesRequireManualResolution() {
        val changedRemote = remote.copy(revision = "r2", parentRevision = "r1", contentSha256 = "remote")
        assertEquals(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE,
            DriveSyncDecision.decide(local, changedRemote, true))
    }

    @Test fun rejectsUnknownSchema() {
        assertEquals(DriveSyncAction.INCOMPATIBLE_SCHEMA,
            DriveSyncDecision.decide(local, remote.copy(schemaVersion = 42), true))
    }
}
