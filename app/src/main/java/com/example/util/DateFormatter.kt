package com.example.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

enum class ExpirationStatus {
    EXPIRED,
    EXPIRING_TODAY,
    EXPIRING_SOON, // 1 to 3 days
    ACTIVE
}

object DateFormatter {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))

    fun formatDate(timestamp: Long): String {
        return if (timestamp <= 0) "N/A" else dateFormat.format(Date(timestamp))
    }

    fun getDaysRemaining(expirationTimestamp: Long): Long {
        val now = System.currentTimeMillis()
        val diffMs = expirationTimestamp - now
        return TimeUnit.MILLISECONDS.toDays(diffMs)
    }

    fun getExpirationStatus(expirationTimestamp: Long): ExpirationStatus {
        val days = getDaysRemaining(expirationTimestamp)
        return when {
            days < 0 -> ExpirationStatus.EXPIRED
            days == 0L -> ExpirationStatus.EXPIRING_TODAY
            days in 1..3 -> ExpirationStatus.EXPIRING_SOON
            else -> ExpirationStatus.ACTIVE
        }
    }

    fun getStatusLabel(expirationTimestamp: Long): String {
        val days = getDaysRemaining(expirationTimestamp)
        return when {
            days < 0 -> "Vencido (hace ${-days}d)"
            days == 0L -> "Vence Hoy"
            days == 1L -> "Vence Mañana"
            days in 2..3 -> "Vence en $days días"
            else -> "Vence el ${formatDate(expirationTimestamp)}"
        }
    }

    fun addDaysToCurrentTime(days: Int): Long {
        return System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
    }
}
