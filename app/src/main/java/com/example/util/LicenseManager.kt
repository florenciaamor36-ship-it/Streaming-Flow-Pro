package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

data class LicenseStatus(
    val isExpired: Boolean,
    val isTrial: Boolean,
    val daysRemaining: Long,
    val expirationDateStr: String,
    val statusText: String
)

object LicenseManager {
    private const val TAG = "LicenseManager"
    private const val PREFS_NAME = "stream_flow_license_prefs"
    private const val KEY_FIRST_INSTALL_TIME = "first_install_time"
    private const val KEY_LICENSE_EXPIRATION = "license_expiration_time"
    private const val KEY_REDEEMED_TOKENS = "redeemed_tokens_set"

    // Owner WhatsApp Number
    const val OWNER_WHATSAPP_NUMBER = "+5492235590910"

    // Anti-fraud secret key for signing tokens
    private const val SECRET_KEY = "STREAM_FLOW_PRO_SECRET_KEY_2026_ANTI_FRAUD"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore unavailable for license sync")
            null
        }
    }

    private const val KEY_LAST_KNOWN_TIME = "last_known_device_time"

    /**
     * Initializes trial period if first time.
     */
    fun initTrialIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        if (!prefs.contains(KEY_FIRST_INSTALL_TIME)) {
            prefs.edit().putLong(KEY_FIRST_INSTALL_TIME, now).apply()
        }
        if (!prefs.contains(KEY_LAST_KNOWN_TIME)) {
            prefs.edit().putLong(KEY_LAST_KNOWN_TIME, now).apply()
        }
    }

    /**
     * Syncs license with Firestore for logged in user (restores subscription on new device/reinstall).
     */
    suspend fun syncCloudLicense(context: Context, userId: String) {
        if (userId.isBlank() || userId.startsWith("guest_")) return
        val db = firestore ?: return
        try {
            val prefs = getPrefs(context)
            val localExp = prefs.getLong(KEY_LICENSE_EXPIRATION, 0L)
            val docRef = db.collection("users").document(userId)
            val snapshot = docRef.get().await()

            if (snapshot.exists()) {
                val cloudExp = snapshot.getLong("licenseExpirationTime") ?: 0L
                val cloudTokens = (snapshot.get("redeemedTokens") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                // Update local if cloud is newer
                if (cloudExp > localExp) {
                    val currentTokens = prefs.getStringSet(KEY_REDEEMED_TOKENS, emptySet())?.toMutableSet() ?: mutableSetOf()
                    currentTokens.addAll(cloudTokens)
                    prefs.edit()
                        .putLong(KEY_LICENSE_EXPIRATION, cloudExp)
                        .putStringSet(KEY_REDEEMED_TOKENS, currentTokens)
                        .apply()
                } else if (localExp > cloudExp) {
                    // Push local to cloud
                    val currentTokens = prefs.getStringSet(KEY_REDEEMED_TOKENS, emptySet())?.toList() ?: emptyList()
                    val data = mapOf(
                        "licenseExpirationTime" to localExp,
                        "redeemedTokens" to currentTokens,
                        "lastSync" to System.currentTimeMillis()
                    )
                    docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                }
            } else if (localExp > 0L) {
                // Initial upload to cloud
                val currentTokens = prefs.getStringSet(KEY_REDEEMED_TOKENS, emptySet())?.toList() ?: emptyList()
                val data = mapOf(
                    "licenseExpirationTime" to localExp,
                    "redeemedTokens" to currentTokens,
                    "lastSync" to System.currentTimeMillis()
                )
                docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing cloud license: ${e.message}")
        }
    }

    /**
     * Checks current license status (Trial vs Active Subscription vs Expired).
     */
    fun getLicenseStatus(context: Context): LicenseStatus {
        initTrialIfNeeded(context)
        val prefs = getPrefs(context)
        var now = System.currentTimeMillis()

        // Anti-clock manipulation check: if phone time was turned back
        val lastKnown = prefs.getLong(KEY_LAST_KNOWN_TIME, 0L)
        if (now < lastKnown) {
            now = lastKnown // Fraud detected: enforce monotonic time
        } else {
            prefs.edit().putLong(KEY_LAST_KNOWN_TIME, now).apply()
        }

        val licenseExp = prefs.getLong(KEY_LICENSE_EXPIRATION, 0L)
        val firstInstall = prefs.getLong(KEY_FIRST_INSTALL_TIME, now)

        // 1. Check if paid subscription is active
        if (licenseExp > now) {
            val days = ((licenseExp - now) / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
            val dateStr = DateFormatter.formatDate(licenseExp)
            return LicenseStatus(
                isExpired = false,
                isTrial = false,
                daysRemaining = days,
                expirationDateStr = dateStr,
                statusText = "Suscripción Activa ($days días restantes)"
            )
        }

        // 2. Check 3-day free trial
        val trialDurationMs = 3 * 24 * 60 * 60 * 1000L // 3 days
        val trialExp = firstInstall + trialDurationMs

        if (trialExp > now) {
            val days = ((trialExp - now) / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
            val dateStr = DateFormatter.formatDate(trialExp)
            return LicenseStatus(
                isExpired = false,
                isTrial = true,
                daysRemaining = days,
                expirationDateStr = dateStr,
                statusText = "Prueba Gratuita ($days días restantes)"
            )
        }

        // 3. Expired
        val dateStr = DateFormatter.formatDate(if (licenseExp > 0) licenseExp else trialExp)
        return LicenseStatus(
            isExpired = true,
            isTrial = false,
            daysRemaining = 0L,
            expirationDateStr = dateStr,
            statusText = "Suscripción Vencida ($5.000 ARS/mes)"
        )
    }

    /**
     * Generates a signed cryptographic token SFP-{Days}D-{Salt}-{Checksum}
     * Example: SFP-30D-K89A-7F22
     */
    fun generateToken(durationDays: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val salt = (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        val payload = "SFP-${durationDays}D-$salt"
        val checksum = computeChecksum(payload)
        return "$payload-$checksum"
    }

    /**
     * Computes anti-fraud checksum using SHA-256 digest + Secret Key
     */
    private fun computeChecksum(payload: String): String {
        val input = "$payload:$SECRET_KEY"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val hex = bytes.joinToString("") { "%02X".format(it) }
        return hex.take(4) // 4 character checksum
    }

    /**
     * Validates and redeems a token.
     */
    fun redeemToken(context: Context, rawToken: String, userId: String = ""): Result<Int> {
        val token = rawToken.trim().uppercase(Locale.ROOT)
        val parts = token.split("-")

        if (parts.size != 4 || parts[0] != "SFP") {
            return Result.failure(Exception("Formato de token inválido. Ejemplo: SFP-30D-XXXX-YYYY"))
        }

        val daysPart = parts[1] // e.g., "30D" or "60D"
        val salt = parts[2]
        val checksum = parts[3]

        if (!daysPart.endsWith("D")) {
            return Result.failure(Exception("Duración de token no válida"))
        }

        val daysStr = daysPart.dropLast(1)
        val durationDays = daysStr.toIntOrNull() ?: return Result.failure(Exception("Días de token no válidos"))

        // Recompute checksum for verification
        val expectedPayload = "SFP-${daysPart}-$salt"
        val expectedChecksum = computeChecksum(expectedPayload)

        if (checksum != expectedChecksum) {
            return Result.failure(Exception("Token inválido o manipulado (Error de firma anti-fraude)"))
        }

        // Anti-reuse check
        val prefs = getPrefs(context)
        val redeemedSet = prefs.getStringSet(KEY_REDEEMED_TOKENS, emptySet()) ?: emptySet()
        if (redeemedSet.contains(token)) {
            return Result.failure(Exception("Este token ya ha sido canjeado anteriormente"))
        }

        // Activate / Extend license
        val now = System.currentTimeMillis()
        val currentExp = prefs.getLong(KEY_LICENSE_EXPIRATION, 0L)
        val baseTime = if (currentExp > now) currentExp else now

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = baseTime
        calendar.add(Calendar.DAY_OF_YEAR, durationDays)
        val newExp = calendar.timeInMillis

        // Save new expiration and mark token used
        val newSet = redeemedSet.toMutableSet()
        newSet.add(token)

        prefs.edit()
            .putLong(KEY_LICENSE_EXPIRATION, newExp)
            .putStringSet(KEY_REDEEMED_TOKENS, newSet)
            .apply()

        // Push to cloud asynchronously
        if (userId.isNotBlank() && !userId.startsWith("guest_")) {
            val db = firestore
            if (db != null) {
                try {
                    val docRef = db.collection("users").document(userId)
                    val data = mapOf(
                        "licenseExpirationTime" to newExp,
                        "redeemedTokens" to newSet.toList(),
                        "lastSync" to System.currentTimeMillis()
                    )
                    docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud sync deferred: ${e.message}")
                }
            }
        }

        return Result.success(durationDays)
    }
}

