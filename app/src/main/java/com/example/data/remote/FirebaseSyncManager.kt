package com.example.data.remote

import com.example.BuildConfig
import android.util.Log
import com.example.data.local.ClientEntity
import com.example.data.local.ProfileSaleEntity
import com.example.data.local.StreamingAccountEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** UI-safe sync state: never contains Firebase exception text, credentials, or user data. */
data class FirebaseSyncStatus(
    val isSyncing: Boolean = false,
    val hasError: Boolean = false
)

enum class FirebaseSyncEvent { STARTED, SUCCEEDED, FAILED, CANCELLED, DISMISSED }

fun reduceFirebaseSyncStatus(
    current: FirebaseSyncStatus,
    event: FirebaseSyncEvent
): FirebaseSyncStatus = when (event) {
    FirebaseSyncEvent.STARTED -> current.copy(isSyncing = true)
    // Keep failures sticky until explicitly dismissed: another unrelated successful write must
    // not conceal a previous mutation that may still be missing from the cloud.
    FirebaseSyncEvent.SUCCEEDED -> current.copy(isSyncing = false)
    FirebaseSyncEvent.FAILED -> current.copy(isSyncing = false, hasError = true)
    FirebaseSyncEvent.CANCELLED -> current.copy(isSyncing = false)
    FirebaseSyncEvent.DISMISSED -> FirebaseSyncStatus()
}

class FirebaseSyncManager {
    private val tag = "FirebaseSyncManager"
    private val syncMutex = Mutex()
    private val _status = MutableStateFlow(FirebaseSyncStatus())
    val status: StateFlow<FirebaseSyncStatus> = _status.asStateFlow()

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            Log.w(tag, "Firebase Auth is unavailable")
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            Log.w(tag, "Firebase Firestore is unavailable")
            null
        }
    }

    fun dismissError() {
        _status.value = reduceFirebaseSyncStatus(_status.value, FirebaseSyncEvent.DISMISSED)
    }

    private suspend fun sync(userId: String, operation: suspend (FirebaseFirestore) -> Unit) {
        // Guest/local-only records are intentionally not synced and should not show an error.
        if (userId.startsWith("guest_")) return

        syncMutex.withLock {
            _status.value = reduceFirebaseSyncStatus(_status.value, FirebaseSyncEvent.STARTED)
            val db = firestore
            // No shared web/Android document contract or matching rules have been verified.
            // Keep every remote mutation fail-closed until reviewed integration is available.
            if (db == null || !canWriteSharedFirestore(
                    BuildConfig.FIRESTORE_SYNC_ENABLED,
                    auth?.currentUser?.uid,
                    userId
                )
            ) {
                _status.value = reduceFirebaseSyncStatus(_status.value, FirebaseSyncEvent.FAILED)
                return
            }
            try {
                operation(db)
                _status.value = reduceFirebaseSyncStatus(_status.value, FirebaseSyncEvent.SUCCEEDED)
            } catch (e: CancellationException) {
                _status.value = reduceFirebaseSyncStatus(_status.value, FirebaseSyncEvent.CANCELLED)
                throw e
            } catch (e: Exception) {
                // Do not put exception details in UI/logs: Firebase errors can contain project data.
                Log.e(tag, "Firestore sync operation failed")
                _status.value = reduceFirebaseSyncStatus(_status.value, FirebaseSyncEvent.FAILED)
            }
        }
    }

    suspend fun saveAccountToFirestore(account: StreamingAccountEntity) = sync(account.userId) { db ->
        db.collection("users").document(account.userId)
            .collection("accounts").document(account.id).set(account.toSharedFirestoreDocument()).await()
    }

    suspend fun deleteAccountFromFirestore(userId: String, accountId: String) = sync(userId) { db ->
        db.collection("users").document(userId)
            .collection("accounts").document(accountId).delete().await()
    }

    suspend fun saveSaleToFirestore(sale: ProfileSaleEntity) = sync(sale.userId) { db ->
        db.collection("users").document(sale.userId)
            .collection("sales").document(sale.id).set(sale.toSharedFirestoreDocument()).await()
    }

    suspend fun deleteSaleFromFirestore(userId: String, saleId: String) = sync(userId) { db ->
        db.collection("users").document(userId)
            .collection("sales").document(saleId).delete().await()
    }


    /** Read only owner-scoped V1 docs. Caller must merge local-only credentials before Room upsert. */
    suspend fun fetchSharedDocuments(userId: String): Pair<List<Pair<String, Map<String, Any?>>>, List<Pair<String, Map<String, Any?>>>>? {
        if (userId.startsWith("guest_")) return null
        val db = firestore ?: return null
        if (!canWriteSharedFirestore(BuildConfig.FIRESTORE_SYNC_ENABLED, auth?.currentUser?.uid, userId)) return null
        return try {
            val root = db.collection("users").document(userId)
            val accountDocs = root.collection("accounts").get().await().documents.map { doc ->
                doc.id to (doc.data ?: emptyMap())
            }
            val saleDocs = root.collection("sales").get().await().documents.map { doc ->
                doc.id to (doc.data ?: emptyMap())
            }
            accountDocs to saleDocs
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Log.e(tag, "Firestore sync operation failed")
            _status.value = reduceFirebaseSyncStatus(_status.value, FirebaseSyncEvent.FAILED)
            null
        }
    }

    suspend fun saveClientToFirestore(client: ClientEntity) = sync(client.userId) { db ->
        db.collection("users").document(client.userId)
            .collection("clients").document(client.id).set(client.toSharedFirestoreDocument()).await()
    }

    suspend fun deleteClientFromFirestore(userId: String, clientId: String) = sync(userId) { db ->
        db.collection("users").document(userId)
            .collection("clients").document(clientId).delete().await()
    }
}

/** Pure ownership guard, also covered by JVM tests. */
internal fun canSyncToFirebaseUser(authenticatedUid: String?, requestedUserId: String): Boolean =
    !authenticatedUid.isNullOrBlank() && authenticatedUid == requestedUserId

/** Remote mutations require explicit reviewed enablement as well as exact owner UID. */
internal fun canWriteSharedFirestore(
    syncEnabled: Boolean,
    authenticatedUid: String?,
    requestedUserId: String
): Boolean = syncEnabled && canSyncToFirebaseUser(authenticatedUid, requestedUserId)
