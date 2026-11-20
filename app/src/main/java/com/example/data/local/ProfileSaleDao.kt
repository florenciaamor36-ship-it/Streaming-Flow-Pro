package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileSaleDao {
    @Query("SELECT * FROM profile_sales WHERE userId = :userId ORDER BY expirationDate ASC")
    fun getSalesForUser(userId: String): Flow<List<ProfileSaleEntity>>

    @Query("SELECT * FROM profile_sales WHERE userId = :userId ORDER BY id ASC")
    suspend fun getAllForUser(userId: String): List<ProfileSaleEntity>

    @Query("SELECT * FROM profile_sales WHERE userId = :userId AND accountId = :accountId")
    fun getSalesByAccount(userId: String, accountId: String): Flow<List<ProfileSaleEntity>>

    @Query("SELECT * FROM profile_sales WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getSaleById(id: String, userId: String): ProfileSaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: ProfileSaleEntity)

    @Query("DELETE FROM profile_sales WHERE id = :id AND userId = :userId")
    suspend fun deleteSaleById(id: String, userId: String)

    @Query("UPDATE profile_sales SET paymentStatus = :status WHERE id = :id AND userId = :userId")
    suspend fun updatePaymentStatus(id: String, userId: String, status: String)

    @Query("UPDATE profile_sales SET expirationDate = :newExpiration, renewalStatus = 'RENEWED', reminderSent = 0 WHERE id = :id AND userId = :userId")
    suspend fun renewSale(id: String, userId: String, newExpiration: Long)

    @Query("DELETE FROM profile_sales WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
