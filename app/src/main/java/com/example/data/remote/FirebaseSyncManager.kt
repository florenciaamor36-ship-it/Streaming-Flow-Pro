package com.example.data.remote

import android.util.Log
import com.example.data.local.ClientEntity
import com.example.data.local.ProfileSaleEntity
import com.example.data.local.StreamingAccountEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseSyncManager {
    private val tag = "FirebaseSyncManager"

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "Firebase Auth not available or missing configuration: ${e.message}")
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "Firebase Firestore not available or missing configuration: ${e.message}")
            null
        }
    }

    fun isFirebaseAvailable(): Boolean {
        return auth != null && firestore != null
    }

    private fun firestoreForVerifiedUser(userId: String): FirebaseFirestore? {
        // Never sync guest/local IDs or a caller-supplied UID different from Firebase Auth.
        if (!canSyncToFirebaseUser(auth?.currentUser?.uid, userId)) return null
        return firestore
    }

    // --- FIRESTORE USER PARTITION OPERATIONS ---

    suspend fun saveAccountToFirestore(account: StreamingAccountEntity) {
        val db = firestoreForVerifiedUser(account.userId) ?: return
        try {
            db.collection("users")
                .document(account.userId)
                .collection("accounts")
                .document(account.id)
                .set(account)
                .await()
            Log.d(tag, "Account saved to Firestore")
        } catch (e: Exception) {
            Log.e(tag, "Error saving account to Firestore")
        }
    }

    suspend fun deleteAccountFromFirestore(userId: String, accountId: String) {
        val db = firestoreForVerifiedUser(userId) ?: return
        try {
            db.collection("users")
                .document(userId)
                .collection("accounts")
                .document(accountId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error deleting account from Firestore")
        }
    }

    suspend fun saveSaleToFirestore(sale: ProfileSaleEntity) {
        val db = firestoreForVerifiedUser(sale.userId) ?: return
        try {
            db.collection("users")
                .document(sale.userId)
                .collection("sales")
                .document(sale.id)
                .set(sale)
                .await()
            Log.d(tag, "Sale saved to Firestore")
        } catch (e: Exception) {
            Log.e(tag, "Error saving sale to Firestore")
        }
    }

    suspend fun deleteSaleFromFirestore(userId: String, saleId: String) {
        val db = firestoreForVerifiedUser(userId) ?: return
        try {
            db.collection("users")
                .document(userId)
                .collection("sales")
                .document(saleId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error deleting sale from Firestore")
        }
    }

    suspend fun saveClientToFirestore(client: ClientEntity) {
        val db = firestoreForVerifiedUser(client.userId) ?: return
        try {
            db.collection("users")
                .document(client.userId)
                .collection("clients")
                .document(client.id)
                .set(client)
                .await()
            Log.d(tag, "Client saved to Firestore")
        } catch (e: Exception) {
            Log.e(tag, "Error saving client to Firestore")
        }
    }

    suspend fun deleteClientFromFirestore(userId: String, clientId: String) {
        val db = firestoreForVerifiedUser(userId) ?: return
        try {
            db.collection("users")
                .document(userId)
                .collection("clients")
                .document(clientId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error deleting client from Firestore")
        }
    }
}

/** Pure ownership guard, also covered by JVM tests. */
internal fun canSyncToFirebaseUser(authenticatedUid: String?, requestedUserId: String): Boolean =
    !authenticatedUid.isNullOrBlank() && authenticatedUid == requestedUserId
