package com.example.data.remote

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
    FirebaseSyncEvent.SUCCEEDED -> FirebaseSyncStatus()
    FirebaseSyncEvent.FAILED -> FirebaseSyncStatus(isSyncing = false, hasError = true)
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
        val db = firestore
        if (db == null || !canSyncToFirebaseUser(auth?.currentUser?.uid, userId)) {
            _status.value = reduceFirebaseSyncStatus(_status.value, FirebaseSyncEvent.FAILED)
            return
        }

        syncMutex.withLock {
            _status.value = reduceFirebaseSyncStatus(_status.value, FirebaseSyncEvent.STARTED)
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
            .collection("accounts").document(account.id).set(account).await()
    }

    suspend fun deleteAccountFromFirestore(userId: String, accountId: String) = sync(userId) { db ->
        db.collection("users").document(userId)
            .collection("accounts").document(accountId).delete().await()
    }

    suspend fun saveSaleToFirestore(sale: ProfileSaleEntity) = sync(sale.userId) { db ->
        db.collection("users").document(sale.userId)
            .collection("sales").document(sale.id).set(sale).await()
    }

    suspend fun deleteSaleFromFirestore(userId: String, saleId: String) = sync(userId) { db ->
        db.collection("users").document(userId)
            .collection("sales").document(saleId).delete().await()
    }

    suspend fun saveClientToFirestore(client: ClientEntity) = sync(client.userId) { db ->
        db.collection("users").document(client.userId)
            .collection("clients").document(client.id).set(client).await()
    }

    suspend fun deleteClientFromFirestore(userId: String, clientId: String) = sync(userId) { db ->
        db.collection("users").document(userId)
            .collection("clients").document(clientId).delete().await()
    }
}

/** Pure ownership guard, also covered by JVM tests. */
internal fun canSyncToFirebaseUser(authenticatedUid: String?, requestedUserId: String): Boolean =
    !authenticatedUid.isNullOrBlank() && authenticatedUid == requestedUserId
