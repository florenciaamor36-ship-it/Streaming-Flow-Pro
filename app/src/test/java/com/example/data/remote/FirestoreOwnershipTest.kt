package com.example.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreOwnershipTest {
    @Test
    fun `only exact authenticated UID can target its Firestore partition`() {
        assertTrue(canSyncToFirebaseUser("uid-123", "uid-123"))
        assertFalse(canSyncToFirebaseUser(null, "guest_123"))
        assertFalse(canSyncToFirebaseUser("uid-123", "uid-other"))
        assertFalse(canSyncToFirebaseUser("uid-123", "guest_123"))
        assertFalse(canSyncToFirebaseUser("", ""))
    }
}
