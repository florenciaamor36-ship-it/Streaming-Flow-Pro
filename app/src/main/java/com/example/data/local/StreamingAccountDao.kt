package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamingAccountDao {
    @Query("SELECT * FROM streaming_accounts WHERE userId = :userId ORDER BY expirationDate ASC")
    fun getAccountsForUser(userId: String): Flow<List<StreamingAccountEntity>>

    @Query("SELECT * FROM streaming_accounts WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getAccountById(id: String, userId: String): StreamingAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: StreamingAccountEntity)

    @Query("DELETE FROM streaming_accounts WHERE id = :id AND userId = :userId")
    suspend fun deleteAccountById(id: String, userId: String)

    @Query("DELETE FROM streaming_accounts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
