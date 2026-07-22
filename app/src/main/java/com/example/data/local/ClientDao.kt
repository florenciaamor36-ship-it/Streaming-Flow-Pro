package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients WHERE userId = :userId ORDER BY name ASC")
    fun getClientsForUser(userId: String): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getClientById(id: String, userId: String): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity)

    @Query("DELETE FROM clients WHERE id = :id AND userId = :userId")
    suspend fun deleteClientById(id: String, userId: String)

    @Query("DELETE FROM clients WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
