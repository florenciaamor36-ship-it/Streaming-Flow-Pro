package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseSyncStatusTest {
    @Test
    fun failedOperationIsVisible() {
        assertEquals(
            FirebaseSyncStatus(isSyncing = false, hasError = true),
            reduceFirebaseSyncStatus(FirebaseSyncStatus(), FirebaseSyncEvent.FAILED)
        )
    }

    @Test
    fun unrelatedSuccessDoesNotHideAnEarlierFailure() {
        val failed = reduceFirebaseSyncStatus(FirebaseSyncStatus(), FirebaseSyncEvent.FAILED)
        val started = reduceFirebaseSyncStatus(failed, FirebaseSyncEvent.STARTED)
        assertEquals(FirebaseSyncStatus(isSyncing = true, hasError = true), started)
        assertEquals(
            FirebaseSyncStatus(isSyncing = false, hasError = true),
            reduceFirebaseSyncStatus(started, FirebaseSyncEvent.SUCCEEDED)
        )
    }

    @Test
    fun cancellationStopsProgressWithoutHidingAnEarlierFailure() {
        val failed = FirebaseSyncStatus(isSyncing = true, hasError = true)
        assertEquals(
            FirebaseSyncStatus(isSyncing = false, hasError = true),
            reduceFirebaseSyncStatus(failed, FirebaseSyncEvent.CANCELLED)
        )
    }

    @Test
    fun userCanDismissFailureNotice() {
        assertEquals(
            FirebaseSyncStatus(),
            reduceFirebaseSyncStatus(FirebaseSyncStatus(hasError = true), FirebaseSyncEvent.DISMISSED)
        )
    }
}
