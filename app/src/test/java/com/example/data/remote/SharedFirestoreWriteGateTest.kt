package com.example.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedFirestoreWriteGateTest {
    @Test
    fun `sync stays disabled by default even for the authenticated owner`() {
        assertFalse(canWriteSharedFirestore(false, "owner-1", "owner-1"))
    }

    @Test
    fun `explicit enablement still requires exact authenticated owner`() {
        assertTrue(canWriteSharedFirestore(true, "owner-1", "owner-1"))
        assertFalse(canWriteSharedFirestore(true, null, "owner-1"))
        assertFalse(canWriteSharedFirestore(true, "owner-1", "owner-2"))
        assertFalse(canWriteSharedFirestore(true, "owner-1", "guest_123"))
    }
}
