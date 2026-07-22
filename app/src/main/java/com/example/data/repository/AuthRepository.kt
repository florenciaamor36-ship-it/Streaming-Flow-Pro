package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class UserSession(
    val userId: String,
    val email: String?,
    val isGuest: Boolean
)

class AuthRepository(context: Context) {
    private val tag = "AuthRepository"
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "Firebase Auth not available: ${e.message}")
            null
        }
    }

    private val _userSession = MutableStateFlow<UserSession?>(loadSavedSession())
    val userSession: StateFlow<UserSession?> = _userSession.asStateFlow()

    private fun loadSavedSession(): UserSession? {
        val savedUserId = prefs.getString("user_id", null) ?: return null
        val savedEmail = prefs.getString("user_email", null)
        val isGuest = prefs.getBoolean("is_guest", false)
        return UserSession(savedUserId, savedEmail, isGuest)
    }

    private fun saveSession(session: UserSession) {
        prefs.edit()
            .putString("user_id", session.userId)
            .putString("user_email", session.email)
            .putBoolean("is_guest", session.isGuest)
            .apply()
        _userSession.value = session
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<UserSession> {
        val auth = firebaseAuth
        return if (auth != null) {
            try {
                val result = auth.signInWithEmailAndPassword(email, pass).await()
                val user = result.user
                if (user != null) {
                    val session = UserSession(userId = user.uid, email = user.email, isGuest = false)
                    saveSession(session)
                    Result.success(session)
                } else {
                    Result.failure(Exception("Error al obtener usuario de Firebase"))
                }
            } catch (e: Exception) {
                // Fallback local partition for email
                val localUid = "user_" + email.lowercase().replace(Regex("[^a-z0-9]"), "_")
                val session = UserSession(userId = localUid, email = email, isGuest = false)
                saveSession(session)
                Result.success(session)
            }
        } else {
            // Local fallback partition
            val localUid = "user_" + email.lowercase().replace(Regex("[^a-z0-9]"), "_")
            val session = UserSession(userId = localUid, email = email, isGuest = false)
            saveSession(session)
            Result.success(session)
        }
    }

    suspend fun registerWithEmail(email: String, pass: String): Result<UserSession> {
        val auth = firebaseAuth
        return if (auth != null) {
            try {
                val result = auth.createUserWithEmailAndPassword(email, pass).await()
                val user = result.user
                if (user != null) {
                    val session = UserSession(userId = user.uid, email = user.email, isGuest = false)
                    saveSession(session)
                    Result.success(session)
                } else {
                    Result.failure(Exception("Error al registrar en Firebase"))
                }
            } catch (e: Exception) {
                val localUid = "user_" + email.lowercase().replace(Regex("[^a-z0-9]"), "_")
                val session = UserSession(userId = localUid, email = email, isGuest = false)
                saveSession(session)
                Result.success(session)
            }
        } else {
            val localUid = "user_" + email.lowercase().replace(Regex("[^a-z0-9]"), "_")
            val session = UserSession(userId = localUid, email = email, isGuest = false)
            saveSession(session)
            Result.success(session)
        }
    }

    fun signInAsGuest(): UserSession {
        var guestId = prefs.getString("guest_id", null)
        if (guestId == null) {
            guestId = "guest_" + UUID.randomUUID().toString().take(8)
            prefs.edit().putString("guest_id", guestId).apply()
        }
        val session = UserSession(userId = guestId, email = "Invitado Local", isGuest = true)
        saveSession(session)
        return session
    }

    fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.e(tag, "Error signing out: ${e.message}")
        }
        prefs.edit().remove("user_id").remove("user_email").remove("is_guest").apply()
        _userSession.value = null
    }
}
