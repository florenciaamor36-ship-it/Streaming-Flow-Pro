package com.example.data.drive

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.ClientEntity
import com.example.data.local.ProfileSaleEntity
import com.example.data.local.StreamingAccountEntity

/** Safe mapping for the shared subset. Room-only credentials/freeform notes never leave device. */
class DriveRoomSnapshotStore(private val db: AppDatabase) {
    suspend fun export(userId: String): DriveSnapshotData {
        require(userId.isNotBlank())
        val accounts = db.streamingAccountDao().getAllForUser(userId).map {
            SharedAccount(it.id, it.platformName, it.maxProfiles, it.cost, it.purchaseDate, it.expirationDate, it.supplierName)
        }
        val sales = db.profileSaleDao().getAllForUser(userId).map {
            // Keep the v1 shape while deliberately omitting direct customer contact data.
            SharedSale(it.id, it.accountId, it.platformName, it.profileName, it.clientName, "",
                it.salePrice, it.costPrice, it.saleDate, it.expirationDate, it.paymentStatus, it.renewalStatus, it.saleType,
                it.supplierName, it.reminderSent)
        }
        val clients = db.clientDao().getAllForUser(userId).map {
            // Client name/ID remain useful for linking sales; phone and email are not backed up.
            SharedClient(it.id, it.name, "", "", it.createdAt)
        }
        return DriveSnapshotData(accounts, sales, clients)
    }

    /** Must only be called after an explicit restore confirmation and conflict decision. */
    suspend fun import(userId: String, data: DriveSnapshotData) {
        require(userId.isNotBlank())
        require(data.accounts.map { it.id }.distinct().size == data.accounts.size)
        require(data.sales.map { it.id }.distinct().size == data.sales.size)
        require(data.clients.map { it.id }.distinct().size == data.clients.size)
        db.withTransaction {
            val accountDao = db.streamingAccountDao()
            val saleDao = db.profileSaleDao()
            val clientDao = db.clientDao()
            val oldAccounts = accountDao.getAllForUser(userId).associateBy { it.id }
            val oldSales = saleDao.getAllForUser(userId).associateBy { it.id }
            val oldClients = clientDao.getAllForUser(userId).associateBy { it.id }
            accountDao.deleteAllForUser(userId)
            saleDao.deleteAllForUser(userId)
            clientDao.deleteAllForUser(userId)
            data.accounts.forEach { a ->
                accountDao.insertAccount(StreamingAccountEntity(a.id, userId, a.platformName, oldAccounts[a.id]?.accountEmail.orEmpty(),
                    oldAccounts[a.id]?.accountPassword.orEmpty(), a.maxProfiles, a.cost, a.purchaseDate, a.expirationDate,
                    a.supplierName, oldAccounts[a.id]?.supplierPhone.orEmpty(), oldAccounts[a.id]?.notes.orEmpty()))
            }
            data.sales.forEach { s ->
                saleDao.insertSale(ProfileSaleEntity(s.id, userId, s.accountId, s.platformName, oldSales[s.id]?.accountEmail.orEmpty(),
                    oldSales[s.id]?.accountPassword.orEmpty(), s.profileName, oldSales[s.id]?.profilePin.orEmpty(),
                    s.clientName, s.clientPhone, s.salePrice, s.costPrice, s.saleDate, s.expirationDate, s.paymentStatus,
                    s.renewalStatus, s.saleType, s.supplierName, oldSales[s.id]?.supplierPhone.orEmpty(), s.reminderSent,
                    oldSales[s.id]?.notes.orEmpty()))
            }
            data.clients.forEach { c -> clientDao.insertClient(ClientEntity(c.id, userId, c.name, c.phone, c.email,
                oldClients[c.id]?.notes.orEmpty(), c.createdAt)) }
        }
    }
}
