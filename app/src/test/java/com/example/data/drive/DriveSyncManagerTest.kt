package com.example.data.drive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ClientEntity
import com.example.data.local.ProfileSaleEntity
import com.example.data.local.StreamingAccountEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveSyncManagerTest {
    private lateinit var db: AppDatabase
    private lateinit var transport: FakeDriveTransport
    private lateinit var manager: DriveSyncManager

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        transport = FakeDriveTransport()
        manager = DriveSyncManager(context, db, transport)
        manager.setEnabled("firebase-uid", true)
        db.streamingAccountDao().insertAccount(StreamingAccountEntity("a1", "firebase-uid", "Netflix", "svc-login@example.com", "ACCOUNT_PASSWORD_SECRET", 5, 10.0, 1L, 2L))
        db.profileSaleDao().insertSale(ProfileSaleEntity("s1", "firebase-uid", "a1", "Netflix", "svc-login@example.com", "SALE_PASSWORD_SECRET", "Profile", "4321", "Client", "+549111", 20.0, 10.0, 1L, 2L, "PAID", "ACTIVE"))
        db.clientDao().insertClient(ClientEntity("c1", "firebase-uid", "Client", "+549111", "client@example.com", "PRIVATE_FREEFORM_NOTE", 3L))
    }

    @After fun tearDown() { db.close() }

    @Test fun explicitInitialBackupAndSubsequentLocalEditUseFakeDriveAndExcludeSecretsAndContacts() = runBlocking {
        val first = manager.confirmInitialUpload("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        assertTrue(first is DriveSyncResult.Complete)
        val uploaded = transport.json!!
        assertTrue(uploaded.contains("owner@example.com"))
        assertTrue(uploaded.contains("\"accounts\""))
        assertTrue(uploaded.contains("\"sales\""))
        assertTrue(uploaded.contains("\"clients\""))
        listOf("ACCOUNT_PASSWORD_SECRET", "SALE_PASSWORD_SECRET", "4321", "svc-login@example.com", "PRIVATE_FREEFORM_NOTE", "+549111", "client@example.com").forEach {
            assertFalse("Sensitive value leaked: $it", uploaded.contains(it))
        }
        val snapshot = DriveSnapshotCodec.decode(uploaded).data
        assertEquals("", snapshot.sales.single().clientPhone)
        assertEquals("", snapshot.clients.single().phone)
        assertEquals("", snapshot.clients.single().email)
        assertEquals("Client", snapshot.clients.single().name)
        assertEquals("Client", snapshot.sales.single().clientName)
        assertTrue("Client and sale records should remain in the shared snapshot", snapshot.sales.isNotEmpty() && snapshot.clients.isNotEmpty())
        assertTrue("Account records should remain in the shared snapshot", snapshot.accounts.isNotEmpty())
        assertFalse("Snapshot must not include credential-bearing entity property names", uploaded.contains("accountPassword"))
        assertFalse("Snapshot must not include PIN-bearing entity property names", uploaded.contains("profilePin"))

        db.streamingAccountDao().insertAccount(db.streamingAccountDao().getAccountById("a1", "firebase-uid")!!.copy(cost = 11.0))
        val next = manager.sync("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        assertEquals(DriveSyncAction.UPLOAD_LOCAL, (next as DriveSyncResult.Complete).action)
        assertEquals(11.0, DriveSnapshotCodec.decode(transport.json!!).data.accounts.single().cost, 0.0)
        assertEquals(1, transport.updateCalls)
    }

    @Test fun confirmedRestoreDoesNotRestoreClientContactFields() = runBlocking {
        db.streamingAccountDao().deleteAllForUser("firebase-uid")
        db.profileSaleDao().deleteAllForUser("firebase-uid")
        db.clientDao().deleteAllForUser("firebase-uid")
        transport.json = DriveSnapshotCodec.encode(DriveSnapshotCodec.create("owner@example.com", sampleData(), null))
        val result = manager.confirmInitialRestore("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        assertEquals(DriveSyncAction.DOWNLOAD_REMOTE, (result as DriveSyncResult.Complete).action)
        val client = db.clientDao().getClientById("remote-c", "firebase-uid")!!
        assertEquals("", client.phone)
        assertEquals("", client.email)
        assertEquals("Client", client.name)
        assertEquals("", db.profileSaleDao().getSaleById("remote-s", "firebase-uid")!!.clientPhone)
    }

    @Test fun confirmedRestoreMapsSharedRowsAndDoesNotRestoreCredentials() = runBlocking {
        db.streamingAccountDao().deleteAllForUser("firebase-uid")
        db.profileSaleDao().deleteAllForUser("firebase-uid")
        db.clientDao().deleteAllForUser("firebase-uid")
        transport.json = DriveSnapshotCodec.encode(DriveSnapshotCodec.create("owner@example.com", sampleData(), null))
        val restored = manager.confirmInitialRestore("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        assertEquals(DriveSyncAction.DOWNLOAD_REMOTE, (restored as DriveSyncResult.Complete).action)
        val account = db.streamingAccountDao().getAccountById("remote-a", "firebase-uid")!!
        assertEquals("", account.accountPassword)
        assertEquals("", account.accountEmail)
        val sale = db.profileSaleDao().getSaleById("remote-s", "firebase-uid")!!
        assertEquals("", sale.accountPassword)
        assertEquals("", sale.profilePin)
        assertEquals("Client", db.clientDao().getClientById("remote-c", "firebase-uid")!!.name)
    }

    @Test fun initialUploadRequiresSeparateUserConfirmation() = runBlocking {
        val pending = manager.sync("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        assertEquals(DriveSyncAction.INITIAL_UPLOAD_REQUIRES_CONFIRMATION, (pending as DriveSyncResult.Blocked).action)
        assertEquals(0, transport.createCalls)
        assertNull(transport.json)
    }

    @Test fun unconsentedFeatureCannotReachDrive() = runBlocking {
        manager.setEnabled("firebase-uid", false)
        val blocked = manager.confirmInitialUpload("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        assertEquals(DriveSyncAction.DISABLED, (blocked as DriveSyncResult.Blocked).action)
        assertEquals(0, transport.createCalls)
        assertEquals(0, transport.updateCalls)
    }

    @Test fun initialUploadDoesNotReplaceAnyExistingRemoteSnapshot() = runBlocking {
        transport.json = DriveSnapshotCodec.encode(DriveSnapshotCodec.create("owner@example.com", DriveSnapshotData(emptyList(), emptyList(), emptyList()), null))
        val result = manager.confirmInitialUpload("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        assertEquals(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, (result as DriveSyncResult.Blocked).action)
        assertEquals(0, transport.createCalls)
        assertEquals(0, transport.updateCalls)
    }

    @Test fun remoteChangeAfterLocalBaseRequiresConflictChoice() = runBlocking {
        manager.confirmInitialUpload("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        val existing = DriveSnapshotCodec.decode(transport.json!!)
        transport.json = DriveSnapshotCodec.encode(existing.copy(revision = "remote-race", parentRevision = existing.revision))
        val result = manager.sync("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        assertEquals(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, (result as DriveSyncResult.Blocked).action)
        assertEquals(0, transport.updateCalls)
    }

    @Test fun mismatchedIdentityAndUnbasedRemoteEditNeverOverwrite() = runBlocking {
        manager.confirmInitialUpload("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        val mismatch = manager.sync("firebase-uid", "owner@example.com", "other@example.com", "fake-token")
        assertEquals(DriveSyncAction.ACCOUNT_MISMATCH, (mismatch as DriveSyncResult.Blocked).action)
        db.streamingAccountDao().insertAccount(db.streamingAccountDao().getAccountById("a1", "firebase-uid")!!.copy(cost = 12.0))
        transport.json = DriveSnapshotCodec.encode(DriveSnapshotCodec.create("owner@example.com", DriveSnapshotData(emptyList(), emptyList(), emptyList()), "someone-else"))
        val conflict = manager.sync("firebase-uid", "owner@example.com", "owner@example.com", "fake-token")
        assertEquals(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, (conflict as DriveSyncResult.Blocked).action)
    }

    private fun sampleData() = DriveSnapshotData(
        listOf(SharedAccount("remote-a", "Disney+", 4, 8.0, 10L, 20L, "Provider")),
        listOf(SharedSale("remote-s", "remote-a", "Disney+", "Profile A", "Client", "", 15.0, 8.0, 11L, 21L, "PAID", "ACTIVE", "PROFILE", "Provider", false)),
        listOf(SharedClient("remote-c", "Client", "", "", 12L)),
    )

    private class FakeDriveTransport : DriveAppDataTransport {
        var json: String? = null
        var updateCalls = 0
        var createCalls = 0
        override suspend fun findSnapshots(accessToken: String) = if (json == null) emptyList() else listOf(DriveRemoteFile("file1", "etag-$updateCalls", "1"))
        override suspend fun read(accessToken: String, fileId: String) = json ?: error("Missing fake file")
        override suspend fun create(accessToken: String, json: String): DriveRemoteFile {
            check(this.json == null)
            this.json = json
            createCalls++
            return DriveRemoteFile("file1", "etag-$updateCalls", "1")
        }
        override suspend fun update(accessToken: String, file: DriveRemoteFile, json: String): DriveRemoteFile {
            check(file.id == "file1" && file.etag == "etag-$updateCalls") { "Unexpected stale ETag" }
            this.json = json
            updateCalls++
            return DriveRemoteFile("file1", "etag-$updateCalls", "${updateCalls + 1}")
        }
    }
}
