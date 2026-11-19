package com.example.data.remote

import com.example.data.local.ClientEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ClientFirestoreDocumentTest {
    @Test
    fun `client uses web field names and ISO UTC timestamp without Android-only fields`() {
        val doc = ClientEntity(
            id = "synthetic-client",
            userId = "synthetic-uid",
            name = "Synthetic Customer",
            phone = "0000000000",
            email = "synthetic@example.invalid",
            notes = "fixture only",
            createdAt = 0L
        ).toSharedFirestoreDocument()

        assertEquals("synthetic-uid", doc["userId"])
        assertEquals("Synthetic Customer", doc["name"])
        assertEquals("0000000000", doc["phone"])
        assertEquals("synthetic@example.invalid", doc["email"])
        assertEquals("fixture only", doc["notes"])
        assertEquals("1970-01-01T00:00:00.000Z", doc["createdAt"])
        assertFalse(doc.containsKey("id")) // document ID is the Firestore document ID
        assertFalse(doc.containsKey("createdAtMillis"))
    }

    @Test
    fun `empty optional values are omitted and no account credentials or PIN can leak`() {
        val doc = ClientEntity(
            id = "synthetic-client", userId = "synthetic-uid", name = "Synthetic",
            phone = "0000000000", email = "", notes = "", createdAt = 1L
        ).toSharedFirestoreDocument()

        assertFalse(doc.containsKey("email"))
        assertFalse(doc.containsKey("notes"))
        assertFalse(doc.keys.any { it.contains("password", ignoreCase = true) })
        assertFalse(doc.keys.any { it.contains("pin", ignoreCase = true) })
    }
}
