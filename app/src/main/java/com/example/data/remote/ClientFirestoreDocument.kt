package com.example.data.remote

import com.example.data.local.ClientEntity
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
