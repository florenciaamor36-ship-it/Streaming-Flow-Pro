package com.example.data.remote

import com.example.data.local.ClientEntity
import com.example.data.local.ProfileSaleEntity
import com.example.data.local.StreamingAccountEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Allowlisted V1 mapping to the web Customer document in users/{uid}/clients/{id}. */
internal fun ClientEntity.toSharedFirestoreDocument(): Map<String, Any> = buildMap {
    put("userId", userId)
    put("name", name)
    put("phone", phone)
    if (email.isNotBlank()) put("email", email)
    if (notes.isNotBlank()) put("notes", notes)
    put("createdAt", Date(createdAt).toUtcIsoString())
}

private fun Date.toUtcIsoString(): String = SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    Locale.US
).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(this)


/** Safe V1 account mapping; login/password, PINs, profile state and supplier phone stay local. */
internal fun StreamingAccountEntity.toSharedFirestoreDocument(): Map<String, Any> = buildMap {
    put("userId", userId)
    put("platform", platformName)
    put("providerName", supplierName)
    put("cost", cost)
    put("totalProfiles", maxProfiles)
    put("isFullAccount", maxProfiles <= 1)
    put("purchaseDate", Date(purchaseDate).toUtcIsoString().take(10))
    put("renewalDate", Date(expirationDate).toUtcIsoString().take(10))
    if (notes.isNotBlank()) put("notes", notes)
}

/** Missing protected fields are intentionally blank, never fabricated from the shared document. */
internal fun sharedDocumentToAccount(id: String, data: Map<String, Any?>): StreamingAccountEntity = StreamingAccountEntity(
    id = id,
    userId = data["userId"] as? String ?: "",
    platformName = data["platform"] as? String ?: "",
    accountEmail = "",
    accountPassword = "",
    maxProfiles = (data["totalProfiles"] as? Number)?.toInt() ?: 0,
    cost = (data["cost"] as? Number)?.toDouble() ?: 0.0,
    purchaseDate = (data["purchaseDate"] as? String).toUtcEpochMillis(),
    expirationDate = (data["renewalDate"] as? String).toUtcEpochMillis(),
    supplierName = data["providerName"] as? String ?: "",
    supplierPhone = "",
    notes = data["notes"] as? String ?: ""
)

/** Safe V1 sale mapping; account login/password and profile PIN are deliberately excluded. */
internal fun ProfileSaleEntity.toSharedFirestoreDocument(): Map<String, Any> = buildMap {
    put("userId", userId)
    put("type", if (saleType.equals("FULL_ACCOUNT", true)) "full_account" else "profile")
    put("accountId", accountId)
    put("profileName", profileName)
    put("customerId", "phone:$clientPhone")
    put("customerName", clientName)
    put("customerPhone", clientPhone)
    put("platform", platformName)
    put("salePrice", salePrice)
    put("costPrice", costPrice)
    put("profit", salePrice - costPrice)
    put("startDate", Date(saleDate).toUtcIsoString().take(10))
    put("endDate", Date(expirationDate).toUtcIsoString().take(10))
    put("status", when (renewalStatus.uppercase(Locale.US)) {
        "CANCELLED" -> "cancelled"
        "RENEWED" -> "renewed"
        else -> "active"
    })
    put("paymentStatus", if (paymentStatus.equals("PAID", true)) "paid" else "pending")
    put("paymentMethod", "")
    if (notes.isNotBlank()) put("notes", notes)
}

/** Safe import counterpart. Sensitive app-required credential properties remain empty. */
internal fun sharedDocumentToSale(id: String, data: Map<String, Any?>): ProfileSaleEntity = ProfileSaleEntity(
    id = id,
    userId = data["userId"] as? String ?: "",
    accountId = data["accountId"] as? String ?: "",
    platformName = data["platform"] as? String ?: "",
    accountEmail = "",
    accountPassword = "",
    profileName = data["profileName"] as? String ?: "",
    profilePin = "",
    clientName = data["customerName"] as? String ?: "",
    clientPhone = data["customerPhone"] as? String ?: "",
    salePrice = (data["salePrice"] as? Number)?.toDouble() ?: 0.0,
    costPrice = (data["costPrice"] as? Number)?.toDouble() ?: 0.0,
    saleDate = (data["startDate"] as? String).toUtcEpochMillis(),
    expirationDate = (data["endDate"] as? String).toUtcEpochMillis(),
    paymentStatus = if ((data["paymentStatus"] as? String).equals("paid", true)) "PAID" else "PENDING",
    renewalStatus = when ((data["status"] as? String)?.lowercase(Locale.US)) {
        "cancelled" -> "CANCELLED"
        "renewed" -> "RENEWED"
        else -> "ACTIVE"
    },
    saleType = if ((data["type"] as? String).equals("full_account", true)) "FULL_ACCOUNT" else "PROFILE",
    notes = data["notes"] as? String ?: ""
)

private fun String?.toUtcEpochMillis(): Long {
    if (this.isNullOrBlank()) return 0L
    return try {
        val pattern = if (length == 10) "yyyy-MM-dd" else "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(this)?.time ?: 0L
    } catch (_: Exception) { 0L }
}
