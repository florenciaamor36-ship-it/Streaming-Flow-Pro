package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streaming_accounts")
data class StreamingAccountEntity(
    @PrimaryKey val id: String,
    val userId: String, // Partition data per user
    val platformName: String,
    val accountEmail: String,
    val accountPassword: String,
    val maxProfiles: Int,
    val cost: Double,
    val purchaseDate: Long,
    val expirationDate: Long,
    val supplierName: String = "",
    val supplierPhone: String = "",
    val notes: String = ""
)
