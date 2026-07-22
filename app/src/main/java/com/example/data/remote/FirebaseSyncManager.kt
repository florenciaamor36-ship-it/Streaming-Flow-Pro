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

    // --- FIRESTORE USER PARTITION OPERATIONS ---

    suspend fun saveAccountToFirestore(account: StreamingAccountEntity) {
        val db = firestore ?: return
        try {
            db.collection("users")
                .document(account.userId)
                .collection("accounts")
                .document(account.id)
                .set(account)
                .await()
            Log.d(tag, "Account saved to Firestore user partition: ${account.id}")
        } catch (e: Exception) {
            Log.e(tag, "Error saving account to Firestore: ${e.message}")
        }
    }

    suspend fun deleteAccountFromFirestore(userId: String, accountId: String) {
        val db = firestore ?: return
        try {
            db.collection("users")
                .document(userId)
                .collection("accounts")
                .document(accountId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error deleting account from Firestore: ${e.message}")
        }
    }

    suspend fun saveSaleToFirestore(sale: ProfileSaleEntity) {
        val db = firestore ?: return
        try {
            db.collection("users")
                .document(sale.userId)
                .collection("sales")
                .document(sale.id)
                .set(sale)
                .await()
            Log.d(tag, "Sale saved to Firestore user partition: ${sale.id}")
        } catch (e: Exception) {
            Log.e(tag, "Error saving sale to Firestore: ${e.message}")
        }
    }

    suspend fun deleteSaleFromFirestore(userId: String, saleId: String) {
        val db = firestore ?: return
        try {
            db.collection("users")
                .document(userId)
                .collection("sales")
                .document(saleId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error deleting sale from Firestore: ${e.message}")
        }
    }

    suspend fun saveClientToFirestore(client: ClientEntity) {
        val db = firestore ?: return
        try {
            db.collection("users")
                .document(client.userId)
                .collection("clients")
                .document(client.id)
                .set(client)
                .await()
            Log.d(tag, "Client saved to Firestore user partition: ${client.id}")
        } catch (e: Exception) {
            Log.e(tag, "Error saving client to Firestore: ${e.message}")
        }
    }

    suspend fun deleteClientFromFirestore(userId: String, clientId: String) {
        val db = firestore ?: return
        try {
            db.collection("users")
                .document(userId)
                .collection("clients")
                .document(clientId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Error deleting client from Firestore: ${e.message}")
        }
    }
}
