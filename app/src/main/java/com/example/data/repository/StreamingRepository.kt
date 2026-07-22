package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.ClientEntity
import com.example.data.local.ProfileSaleEntity
import com.example.data.local.StreamingAccountEntity
import com.example.data.remote.FirebaseSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class StreamingRepository(
    private val db: AppDatabase,
    private val firebaseSync: FirebaseSyncManager = FirebaseSyncManager()
) {
    private val accountDao = db.streamingAccountDao()
    private val saleDao = db.profileSaleDao()
    private val clientDao = db.clientDao()

    // --- STREAMING ACCOUNTS ---
    fun getAccountsForUser(userId: String): Flow<List<StreamingAccountEntity>> {
        if (userId.isEmpty()) return emptyFlow()
        return accountDao.getAccountsForUser(userId)
    }

    suspend fun saveAccount(account: StreamingAccountEntity) {
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
        saleDao.insertSale(sale)
        firebaseSync.saveSaleToFirestore(sale)

        // Automatically ensure client is saved in client directory
        val existingClient = clientDao.getClientById(sale.clientPhone, sale.userId)
        if (existingClient == null && sale.clientName.isNotBlank()) {
            val newClient = ClientEntity(
                id = sale.clientPhone.ifBlank { sale.clientName.lowercase().replace(" ", "_") },
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
        clientDao.insertClient(client)
        firebaseSync.saveClientToFirestore(client)
    }

    suspend fun deleteClient(userId: String, clientId: String) {
        clientDao.deleteClientById(clientId, userId)
        firebaseSync.deleteClientFromFirestore(userId, clientId)
    }

    // --- SAMPLE DEMO DATA POPULATION (FOR FIRST TIME USERS) ---
    suspend fun populateSampleDataIfEmpty(userId: String) {
        val currentAccounts = accountDao.getAccountById("sample_1", userId)
        if (currentAccounts == null) {
            val now = System.currentTimeMillis()
            val dayMs = 24 * 60 * 60 * 1000L

            val account1 = StreamingAccountEntity(
                id = "acc_netflix_1",
                userId = userId,
                platformName = "Netflix",
                accountEmail = "ventas.netflix@stream.com",
                accountPassword = "Pass1234#",
                maxProfiles = 5,
                cost = 12.0,
                purchaseDate = now - (15 * dayMs),
                expirationDate = now + (15 * dayMs)
            )

            val account2 = StreamingAccountEntity(
                id = "acc_disney_1",
                userId = userId,
                platformName = "Disney+",
                accountEmail = "cuentas.disney@stream.com",
                accountPassword = "Pass1234#",
                maxProfiles = 7,
                cost = 9.0,
                purchaseDate = now - (20 * dayMs),
                expirationDate = now + (10 * dayMs)
            )

            saveAccount(account1)
            saveAccount(account2)

            // Sales
            val sale1 = ProfileSaleEntity(
                id = "sale_1",
                userId = userId,
                accountId = account1.id,
                platformName = "Netflix",
                accountEmail = account1.accountEmail,
                profileName = "Perfil 1 - Carlos",
                profilePin = "1234",
                clientName = "Carlos Gómez",
                clientPhone = "+5491122334455",
                salePrice = 4.50,
                costPrice = 2.40,
                saleDate = now - (28 * dayMs),
                expirationDate = now + (2 * dayMs), // Expires in 2 days (WARN)
                paymentStatus = "PAID",
                renewalStatus = "ACTIVE"
            )

            val sale2 = ProfileSaleEntity(
                id = "sale_2",
                userId = userId,
                accountId = account1.id,
                platformName = "Netflix",
                accountEmail = account1.accountEmail,
                profileName = "Perfil 2 - Maria",
                profilePin = "5678",
                clientName = "María Rodríguez",
                clientPhone = "+5491133445566",
                salePrice = 4.50,
                costPrice = 2.40,
                saleDate = now - (30 * dayMs),
                expirationDate = now - (1 * dayMs), // Expired yesterday!
                paymentStatus = "PENDING",
                renewalStatus = "ACTIVE"
            )

            val sale3 = ProfileSaleEntity(
                id = "sale_3",
                userId = userId,
                accountId = account2.id,
                platformName = "Disney+",
                accountEmail = account2.accountEmail,
                profileName = "Perfil 1 - Juan",
                profilePin = "0000",
                clientName = "Juan Pérez",
                clientPhone = "+5491144556677",
                salePrice = 3.50,
                costPrice = 1.30,
                saleDate = now - (10 * dayMs),
                expirationDate = now + (20 * dayMs),
                paymentStatus = "PAID",
                renewalStatus = "ACTIVE"
            )

            saveSale(sale1)
            saveSale(sale2)
            saveSale(sale3)
        }
    }
}
