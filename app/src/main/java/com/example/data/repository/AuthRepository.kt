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

/** Only a Firebase UID confirmed by the currently authenticated Firebase user is an account session. */
internal fun verifiedFirebaseSession(
    operationUid: String?,
    email: String?,
    currentUid: String?
): UserSession? {
    if (operationUid.isNullOrBlank() || operationUid != currentUid) return null
    return UserSession(userId = operationUid, email = email, isGuest = false)
}

class AuthRepository(context: Context) {
    private val tag = "AuthRepository"
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "Firebase Auth is unavailable")
            null
        }
    }

    private val _userSession = MutableStateFlow<UserSession?>(loadVerifiedSession())
    val userSession: StateFlow<UserSession?> = _userSession.asStateFlow()

    private fun loadVerifiedSession(): UserSession? {
        val user = firebaseAuth?.currentUser
        if (user != null) {
            return verifiedFirebaseSession(user.uid, user.email, user.uid)
        }

        // Guest access is a separate, explicit local-only state. Legacy guest sessions are
        // accepted only when their old marker and guest-prefixed ID are both present.
        val guestId = prefs.getString(KEY_GUEST_ID, null)
        val legacyId = prefs.getString("user_id", null)
        val legacyGuest = prefs.getBoolean("is_guest", false) && legacyId?.startsWith("guest_") == true
        val guestActive = prefs.getBoolean(KEY_GUEST_ACTIVE, false) || legacyGuest
        if (guestActive) {
            val id = guestId ?: legacyId ?: return null
            if (!id.startsWith("guest_")) return null
            prefs.edit().putString(KEY_GUEST_ID, id).putBoolean(KEY_GUEST_ACTIVE, true).apply()
            return UserSession(userId = id, email = "Invitado Local", isGuest = true)
        }
        // Old locally fabricated email sessions are never authenticated or restored.
        return null
    }

    private fun authUnavailable(): Result<UserSession> =
        Result.failure(IllegalStateException("El servicio de autenticación no está disponible. Probá más tarde."))

    suspend fun signInWithEmail(email: String, pass: String): Result<UserSession> {
        val auth = firebaseAuth ?: return authUnavailable()
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user
            val session = verifiedFirebaseSession(user?.uid, user?.email, auth.currentUser?.uid)
                ?: return Result.failure(IllegalStateException("Firebase no confirmó una sesión válida."))
            prefs.edit().putBoolean(KEY_GUEST_ACTIVE, false).apply()
            _userSession.value = session
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerWithEmail(email: String, pass: String): Result<UserSession> {
        val auth = firebaseAuth ?: return authUnavailable()
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user
            val session = verifiedFirebaseSession(user?.uid, user?.email, auth.currentUser?.uid)
                ?: return Result.failure(IllegalStateException("Firebase no confirmó una sesión válida."))
            prefs.edit().putBoolean(KEY_GUEST_ACTIVE, false).apply()
            _userSession.value = session
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signInAsGuest(): UserSession {
        // Do not let an authenticated Firebase account remain active while using a local guest partition.
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Firebase sign-out before guest mode failed")
        }
        val guestId = prefs.getString(KEY_GUEST_ID, null)
            ?.takeIf { it.startsWith("guest_") }
            ?: "guest_${UUID.randomUUID()}"
        prefs.edit()
            .putString(KEY_GUEST_ID, guestId)
            .putBoolean(KEY_GUEST_ACTIVE, true)
            .remove("user_id")
            .remove("user_email")
            .remove("is_guest")
            .apply()
        return UserSession(userId = guestId, email = "Invitado Local", isGuest = true).also {
            _userSession.value = it
        }
    }

    fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Firebase sign-out failed")
        } finally {
            // Retain guest data and its stable partition ID, but never restore a logged-out guest session.
            prefs.edit()
                .putBoolean(KEY_GUEST_ACTIVE, false)
                .remove("user_id")
                .remove("user_email")
                .remove("is_guest")
                .apply()
            _userSession.value = null
        }
    }

    private companion object {
        const val KEY_GUEST_ID = "guest_id"
        const val KEY_GUEST_ACTIVE = "guest_active"
    }
}
