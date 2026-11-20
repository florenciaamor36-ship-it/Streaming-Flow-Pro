package com.example.data.drive

import org.junit.Assert.*
import org.junit.Test

class DriveSnapshotTest {
    @Test fun envelopeRoundTripsWithStableDigestAndVersionedOwnerMetadata() {
        val data = DriveSnapshotData(
            accounts = listOf(SharedAccount("a", "Netflix", 5, 20.0, 1L, 2L, "provider")),
            sales = emptyList(), clients = listOf(SharedClient("c", "Client", "+5491", "x@example.com", 3L)),
        )
        val envelope = DriveSnapshotCodec.create("owner@example.com", data, "previous")
        val decoded = DriveSnapshotCodec.decode(DriveSnapshotCodec.encode(envelope))
        assertEquals(1, decoded.schemaVersion)
        assertEquals("owner@example.com", decoded.ownerEmail)
        assertEquals("previous", decoded.parentRevision)
        assertEquals(envelope.contentSha256, decoded.contentSha256)
        assertEquals(64, decoded.contentSha256.length)
    }

    @Test fun rejectsTamperedSnapshot() {
        val envelope = DriveSnapshotCodec.create("owner@example.com", DriveSnapshotData(emptyList(), emptyList(), emptyList()), null)
        val corrupt = DriveSnapshotCodec.encode(envelope).replace("owner@example.com", "other@example.com")
        try { DriveSnapshotCodec.decode(corrupt); fail("Expected checksum/metadata mismatch") }
        catch (_: IllegalArgumentException) { }
    }
}
