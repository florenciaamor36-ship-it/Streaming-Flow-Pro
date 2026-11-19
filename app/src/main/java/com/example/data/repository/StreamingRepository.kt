package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.ClientEntity
import com.example.data.local.ProfileSaleEntity
import com.example.data.local.StreamingAccountEntity
import com.example.data.remote.FirebaseSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID

class StreamingRepository(
    private val db: AppDatabase,
    private val firebaseSync: FirebaseSyncManager = FirebaseSyncManager()
) {
    private val accountDao = db.streamingAccountDao()
    private val saleDao = db.profileSaleDao()
    private val clientDao = db.clientDao()


    /** Pull allowlisted shared records without overwriting credentials held in local Room. */
    suspend fun syncFromFirestore(userId: String) {
        val docs = firebaseSync.fetchSharedDocuments(userId) ?: return
        for ((id, data) in docs.first) {
            if (data["userId"] != userId) continue
            val remote = com.example.data.remote.sharedDocumentToAccount(id, data)
            val local = accountDao.getAccountById(id, userId)
            accountDao.insertAccount(remote.copy(
                accountEmail = local?.accountEmail ?: "",
                accountPassword = local?.accountPassword ?: "",
                supplierPhone = local?.supplierPhone ?: ""
            ))
        }
        for ((id, data) in docs.second) {
            if (data["userId"] != userId) continue
            val remote = com.example.data.remote.sharedDocumentToSale(id, data)
            val local = saleDao.getSaleById(id, userId)
            saleDao.insertSale(remote.copy(
                accountEmail = local?.accountEmail ?: "",
                accountPassword = local?.accountPassword ?: "",
                profilePin = local?.profilePin ?: "",
                supplierName = local?.supplierName ?: "",
                supplierPhone = local?.supplierPhone ?: "",
                reminderSent = local?.reminderSent ?: false
            ))
        }
    }

    // --- STREAMING ACCOUNTS ---
    fun getAccountsForUser(userId: String): Flow<List<StreamingAccountEntity>> {
        if (userId.isEmpty()) return emptyFlow()
        return accountDao.getAccountsForUser(userId)
    }

    suspend fun saveAccount(account: StreamingAccountEntity) {
        val currentOwner = accountDao.getAccountByIdAnyOwner(account.id)?.userId
        if (currentOwner != null && currentOwner != account.userId) return
        accountDao.insertAccount(account)
        firebaseSync.saveAccountToFirestore(account)
    }

    suspend fun deleteAccount(userId: String, accountId: String) {
        accountDao.deleteAccountById(accountId, userId)
        firebaseSync.deleteAccountFromFirestore(userId, accountId)
    }

    // --- PROFILE SALES ---
    fun getSalesForUser(userId: String): Flow<List<ProfileSaleEntity>> {
        if (userId.isEmpty()) return emptyFlow()
        return saleDao.getSalesForUser(userId)
    }

    suspend fun saveSale(sale: ProfileSaleEntity) {
        val currentOwner = saleDao.getSaleByIdAnyOwner(sale.id)?.userId
        if (currentOwner != null && currentOwner != sale.userId) return
        saleDao.insertSale(sale)
        firebaseSync.saveSaleToFirestore(sale)

        // Automatically ensure client is saved in client directory
        val existingClient = if (sale.clientPhone.isBlank()) null else clientDao.getClientByPhone(sale.clientPhone, sale.userId)
        if (existingClient == null && sale.clientName.isNotBlank()) {
            val newClient = ClientEntity(
                id = "client_${UUID.randomUUID()}",
                userId = sale.userId,
                name = sale.clientName,
                phone = sale.clientPhone
            )
            clientDao.insertClient(newClient)
            firebaseSync.saveClientToFirestore(newClient)
        }
    }

    suspend fun deleteSale(userId: String, saleId: String) {
        saleDao.deleteSaleById(saleId, userId)
        firebaseSync.deleteSaleFromFirestore(userId, saleId)
    }

    suspend fun updatePaymentStatus(userId: String, saleId: String, newStatus: String) {
        saleDao.updatePaymentStatus(saleId, userId, newStatus)
        val sale = saleDao.getSaleById(saleId, userId)
        if (sale != null) {
            firebaseSync.saveSaleToFirestore(sale)
        }
    }

    suspend fun renewSale(userId: String, saleId: String, newExpiration: Long) {
        saleDao.renewSale(saleId, userId, newExpiration)
        val sale = saleDao.getSaleById(saleId, userId)
        if (sale != null) {
            firebaseSync.saveSaleToFirestore(sale)
        }
    }

    // --- CLIENTS ---
    fun getClientsForUser(userId: String): Flow<List<ClientEntity>> {
        if (userId.isEmpty()) return emptyFlow()
        return clientDao.getClientsForUser(userId)
    }

    suspend fun saveClient(client: ClientEntity) {
        val currentOwner = clientDao.getClientByIdAnyOwner(client.id)?.userId
        if (currentOwner != null && currentOwner != client.userId) return
        clientDao.insertClient(client)
        firebaseSync.saveClientToFirestore(client)
    }

    suspend fun deleteClient(userId: String, clientId: String) {
        clientDao.deleteClientById(clientId, userId)
        firebaseSync.deleteClientFromFirestore(userId, clientId)
    }

}
