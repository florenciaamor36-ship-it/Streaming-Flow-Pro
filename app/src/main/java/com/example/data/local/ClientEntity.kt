package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val userId: String, // Partition data per user
    val name: String,
    val phone: String,
    val email: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
