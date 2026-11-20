package com.example.data.drive

import android.content.Context
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest

sealed class DriveSyncResult {
    data class Complete(val action: DriveSyncAction, val accountCount: Int, val saleCount: Int, val clientCount: Int) : DriveSyncResult()
    data class Blocked(val action: DriveSyncAction, val detail: String) : DriveSyncResult()
}

/** Only called from the explicit Profile > Sync action; no automatic/background sync is registered. */
class DriveSyncManager(context: Context, db: AppDatabase, private val transport: DriveAppDataTransport = GoogleDriveAppDataTransport()) {
    private val appContext = context.applicationContext
    private val store = DriveRoomSnapshotStore(db)
    private val operationMutex = Mutex()
    private val prefs = appContext.getSharedPreferences("optional_drive_sync", Context.MODE_PRIVATE)

    fun isEnabled(userId: String): Boolean = userId.isNotBlank() && prefs.getBoolean(enabledKey(userId), false)
    fun setEnabled(userId: String, enabled: Boolean) { if (userId.isNotBlank()) prefs.edit().putBoolean(enabledKey(userId), enabled).apply() }

    suspend fun sync(userId: String, appEmail: String, googleEmail: String, accessToken: String): DriveSyncResult = operationMutex.withLock { withContext(Dispatchers.IO) {
        if (!isEnabled(userId)) return@withContext DriveSyncResult.Blocked(DriveSyncAction.DISABLED, "Enable optional Drive sync first")
        if (userId.isBlank() || appEmail.isBlank() || googleEmail.isBlank() || !appEmail.equals(googleEmail, true)) {
            return@withContext DriveSyncResult.Blocked(DriveSyncAction.ACCOUNT_MISMATCH, "Choose the Google account that exactly matches the app account")
        }
        val localData = store.export(userId)
        val localDigest = DriveSnapshotCodec.digest(localData)
        val found = transport.findSnapshots(accessToken)
        if (found.size > 1) return@withContext DriveSyncResult.Blocked(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, "More than one app snapshot exists; no file was changed")
        val remoteFile = found.singleOrNull()
        val baseKey = baseKey(userId, googleEmail)
        val baseRevision = prefs.getString("${baseKey}_revision", null)
        val baseDigest = prefs.getString("${baseKey}_digest", null)

        if (remoteFile == null) {
            val action = DriveSyncDecision.decide(LocalDriveState(appEmail, hasData(localData), localDigest, baseRevision, baseDigest), null, true)
            return@withContext DriveSyncResult.Blocked(action, "No Drive snapshot exists. Review the data counts, then confirm creating the first backup.")
        }

        val envelope = try { DriveSnapshotCodec.decode(transport.read(accessToken, remoteFile.id)) }
            catch (e: Exception) { return@withContext DriveSyncResult.Blocked(DriveSyncAction.INCOMPATIBLE_SCHEMA, e.message ?: "Drive snapshot is invalid") }
        val remote = RemoteDriveState(envelope.ownerEmail, envelope.schemaVersion, envelope.revision, envelope.parentRevision, envelope.contentSha256)
        val decision = DriveSyncDecision.decide(LocalDriveState(appEmail, hasData(localData), localDigest, baseRevision, baseDigest), remote, true)
        when (decision) {
            DriveSyncAction.NO_CHANGES -> {
                remember(baseKey, envelope.revision, envelope.contentSha256)
                DriveSyncResult.Complete(decision, localData.accounts.size, localData.sales.size, localData.clients.size)
            }
            DriveSyncAction.UPLOAD_LOCAL -> {
                val next = DriveSnapshotCodec.create(googleEmail, localData, envelope.revision)
                val updatedFile = try { transport.update(accessToken, remoteFile, DriveSnapshotCodec.encode(next)) }
                catch (e: IOException) { return@withContext DriveSyncResult.Blocked(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, e.message ?: "Remote changed during upload; retry to inspect") }
                val verified = try { DriveSnapshotCodec.decode(transport.read(accessToken, updatedFile.id)) }
                    catch (e: Exception) { return@withContext DriveSyncResult.Blocked(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, "Upload could not be verified; local data was kept: ${e.message}") }
                if (verified.revision != next.revision || verified.contentSha256 != next.contentSha256) return@withContext DriveSyncResult.Blocked(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, "Drive returned a different snapshot after upload; no base revision was recorded")
                remember(baseKey, next.revision, next.contentSha256)
                DriveSyncResult.Complete(decision, localData.accounts.size, localData.sales.size, localData.clients.size)
            }
            DriveSyncAction.DOWNLOAD_REMOTE -> {
                store.import(userId, envelope.data)
                remember(baseKey, envelope.revision, envelope.contentSha256)
                DriveSyncResult.Complete(decision, envelope.data.accounts.size, envelope.data.sales.size, envelope.data.clients.size)
            }
            else -> DriveSyncResult.Blocked(decision, when (decision) {
                DriveSyncAction.ACCOUNT_MISMATCH -> "Drive snapshot belongs to a different Google account; no data was changed"
                DriveSyncAction.INCOMPATIBLE_SCHEMA -> "This snapshot uses a schema version this app cannot safely import"
                DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE -> "Both local and Drive data may have changed. Nothing was overwritten; export/compare before choosing a copy."
                DriveSyncAction.INITIAL_DOWNLOAD_REQUIRES_CONFIRMATION -> "Drive has data but local restore requires a separate confirmation."
                DriveSyncAction.INITIAL_UPLOAD_REQUIRES_CONFIRMATION -> "The initial backup requires a separate confirmation."
                else -> "Sync is blocked to prevent data loss"
            })
        }
    }}

    suspend fun confirmInitialUpload(userId: String, appEmail: String, googleEmail: String, accessToken: String): DriveSyncResult = operationMutex.withLock { withContext(Dispatchers.IO) {
        if (!isEnabled(userId)) return@withContext DriveSyncResult.Blocked(DriveSyncAction.DISABLED, "Enable optional Drive sync first")
        if (userId.isBlank() || !appEmail.equals(googleEmail, true)) return@withContext DriveSyncResult.Blocked(DriveSyncAction.ACCOUNT_MISMATCH, "Choose the Google account that matches the app account")
        val local = store.export(userId)
        if (!hasData(local)) return@withContext DriveSyncResult.Blocked(DriveSyncAction.INITIAL_UPLOAD_REQUIRES_CONFIRMATION, "There is no local data to back up")
        val files = transport.findSnapshots(accessToken)
        if (files.isNotEmpty()) return@withContext DriveSyncResult.Blocked(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, "A Drive snapshot appeared since the last check; nothing was changed")
        val envelope = DriveSnapshotCodec.create(googleEmail, local, null)
        val created = transport.create(accessToken, DriveSnapshotCodec.encode(envelope))
        val verified = try { DriveSnapshotCodec.decode(transport.read(accessToken, created.id)) }
            catch (e: Exception) { return@withContext DriveSyncResult.Blocked(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, "Initial backup was created but could not be verified: ${e.message}") }
        if (verified.revision != envelope.revision || verified.contentSha256 != envelope.contentSha256) return@withContext DriveSyncResult.Blocked(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, "Initial backup verification failed; no local base was recorded")
        val baseKey = baseKey(userId, googleEmail)
        remember(baseKey, envelope.revision, envelope.contentSha256)
        DriveSyncResult.Complete(DriveSyncAction.UPLOAD_LOCAL, local.accounts.size, local.sales.size, local.clients.size)
    }}

    suspend fun confirmInitialRestore(userId: String, appEmail: String, googleEmail: String, accessToken: String): DriveSyncResult = operationMutex.withLock { withContext(Dispatchers.IO) {
        if (!isEnabled(userId)) return@withContext DriveSyncResult.Blocked(DriveSyncAction.DISABLED, "Enable optional Drive sync first")
        if (!appEmail.equals(googleEmail, true)) return@withContext DriveSyncResult.Blocked(DriveSyncAction.ACCOUNT_MISMATCH, "Choose the Google account that matches the app account")
        val files = transport.findSnapshots(accessToken)
        if (files.size != 1) return@withContext DriveSyncResult.Blocked(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, "Expected exactly one Drive snapshot; nothing was restored")
        val envelope = try { DriveSnapshotCodec.decode(transport.read(accessToken, files.single().id)) }
            catch (e: Exception) { return@withContext DriveSyncResult.Blocked(DriveSyncAction.INCOMPATIBLE_SCHEMA, e.message ?: "Invalid snapshot") }
        if (!envelope.ownerEmail.equals(googleEmail, true)) return@withContext DriveSyncResult.Blocked(DriveSyncAction.ACCOUNT_MISMATCH, "Snapshot owner does not match the selected Google account")
        val local = store.export(userId)
        if (hasData(local)) return@withContext DriveSyncResult.Blocked(DriveSyncAction.CONFLICT_REQUIRES_USER_CHOICE, "Local data is not empty; restore would replace rows")
        store.import(userId, envelope.data)
        val baseKey = baseKey(userId, googleEmail)
        remember(baseKey, envelope.revision, envelope.contentSha256)
        DriveSyncResult.Complete(DriveSyncAction.DOWNLOAD_REMOTE, envelope.data.accounts.size, envelope.data.sales.size, envelope.data.clients.size)
    }}

    private fun enabledKey(userId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "enabled_$digest"
    }

    private fun baseKey(userId: String, email: String): String {
        val userHash = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "base_${userHash}_${email.lowercase()}"
    }

    private fun remember(key: String, revision: String, digest: String) {
        prefs.edit().putString("${key}_revision", revision).putString("${key}_digest", digest).apply()
    }

    private fun hasData(data: DriveSnapshotData) = data.accounts.isNotEmpty() || data.sales.isNotEmpty() || data.clients.isNotEmpty()
}
