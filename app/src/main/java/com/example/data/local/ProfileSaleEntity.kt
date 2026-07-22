package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_sales")
data class ProfileSaleEntity(
    @PrimaryKey val id: String,
    val userId: String, // Partition data per user
    val accountId: String,
    val platformName: String,
    val accountEmail: String,
    val accountPassword: String = "",
    val profileName: String,
    val profilePin: String,
    val clientName: String,
    val clientPhone: String,
    val salePrice: Double,
    val costPrice: Double,
    val saleDate: Long,
    val expirationDate: Long,
    val paymentStatus: String, // "PAID", "PENDING"
    val renewalStatus: String, // "ACTIVE", "RENEWED", "CANCELLED"
    val saleType: String = "PROFILE", // "PROFILE" (Perfil Individual) or "FULL_ACCOUNT" (Cuenta Completa)
    val supplierName: String = "",
    val supplierPhone: String = "",
    val reminderSent: Boolean = false,
    val notes: String = ""
)
