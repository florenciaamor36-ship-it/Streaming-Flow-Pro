package com.example.data.drive

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.MessageDigest
import java.util.UUID
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.text.SimpleDateFormat

/** Shared v1 interchange format. Credential-bearing entity properties are intentionally absent. */
@JsonClass(generateAdapter = true)
data class DriveSnapshotData(
    val accounts: List<SharedAccount>,
    val sales: List<SharedSale>,
    val clients: List<SharedClient>,
)

@JsonClass(generateAdapter = true)
data class SharedAccount(
    val id: String, val platformName: String,
    val maxProfiles: Int, val cost: Double, val purchaseDate: Long, val expirationDate: Long,
    val supplierName: String,
)

@JsonClass(generateAdapter = true)
data class SharedSale(
    val id: String, val accountId: String, val platformName: String,
    val profileName: String, val clientName: String, val clientPhone: String,
    val salePrice: Double, val costPrice: Double, val saleDate: Long, val expirationDate: Long,
    val paymentStatus: String, val renewalStatus: String, val saleType: String,
    val supplierName: String, val reminderSent: Boolean,
)

@JsonClass(generateAdapter = true)
data class SharedClient(
    val id: String, val name: String, val phone: String, val email: String, val createdAt: Long,
)

@JsonClass(generateAdapter = true)
data class DriveSnapshotEnvelope(
    val schemaVersion: Int,
    val app: String,
    val writerId: String,
    val ownerEmail: String,
    val revision: String,
    val parentRevision: String?,
    val updatedAt: String,
    val contentSha256: String,
    val data: DriveSnapshotData,
)

object DriveSnapshotCodec {
    const val SCHEMA_VERSION = 1
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val dataAdapter = moshi.adapter(DriveSnapshotData::class.java)
    private val envelopeAdapter = moshi.adapter(DriveSnapshotEnvelope::class.java)

    fun canonical(data: DriveSnapshotData): String {
        val ordered = data.copy(
            accounts = data.accounts.sortedBy { it.id },
            sales = data.sales.sortedBy { it.id },
            clients = data.clients.sortedBy { it.id },
        )
        return dataAdapter.toJson(ordered)
    }

    fun digest(data: DriveSnapshotData): String = sha256(canonical(data))

    fun create(ownerEmail: String, data: DriveSnapshotData, parentRevision: String?): DriveSnapshotEnvelope {
        require(ownerEmail.isNotBlank())
        require(data.accounts.none { it.id.isBlank() } && data.sales.none { it.id.isBlank() } && data.clients.none { it.id.isBlank() })
        val ordered = data.copy(accounts = data.accounts.sortedBy { it.id }, sales = data.sales.sortedBy { it.id }, clients = data.clients.sortedBy { it.id })
        val writerId = UUID.randomUUID().toString()
        val updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        return DriveSnapshotEnvelope(SCHEMA_VERSION, "stream-flow-pro", writerId, ownerEmail, writerId, parentRevision,
            updatedAt, digest(ordered), ordered)
    }

    fun encode(envelope: DriveSnapshotEnvelope): String = envelopeAdapter.toJson(envelope)

    fun decode(json: String): DriveSnapshotEnvelope {
        val value = requireNotNull(envelopeAdapter.fromJson(json)) { "Snapshot JSON is empty or invalid" }
        require(value.schemaVersion == SCHEMA_VERSION) { "Unsupported snapshot schema ${value.schemaVersion}" }
        require(value.app == "stream-flow-pro") { "Snapshot belongs to another app" }
        require(value.ownerEmail.isNotBlank() && value.writerId.isNotBlank() && value.revision.isNotBlank() && value.updatedAt.isNotBlank()) { "Snapshot metadata is incomplete" }
        require(value.contentSha256 == digest(value.data)) { "Snapshot checksum does not match its data" }
        require(value.data.accounts.map { it.id }.distinct().size == value.data.accounts.size) { "Duplicate account IDs" }
        require(value.data.sales.map { it.id }.distinct().size == value.data.sales.size) { "Duplicate sale IDs" }
        require(value.data.clients.map { it.id }.distinct().size == value.data.clients.size) { "Duplicate client IDs" }
        return value.copy(data = value.data.copy(accounts = value.data.accounts.sortedBy { it.id }, sales = value.data.sales.sortedBy { it.id }, clients = value.data.clients.sortedBy { it.id }))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
