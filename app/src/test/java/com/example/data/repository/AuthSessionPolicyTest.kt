package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthSessionPolicyTest {
    @Test
    fun `matching verified Firebase UID creates authenticated session`() {
        assertEquals(
            UserSession("firebase-uid", "user@example.invalid", false),
            verifiedFirebaseSession("firebase-uid", "user@example.invalid", "firebase-uid")
        )
    }

    @Test
    fun `missing or mismatched Firebase UID cannot create authenticated session`() {
        assertNull(verifiedFirebaseSession("locally-invented", "user@example.invalid", null))
        assertNull(verifiedFirebaseSession("first-user", null, "different-user"))
        assertNull(verifiedFirebaseSession("", null, ""))
    }
}
