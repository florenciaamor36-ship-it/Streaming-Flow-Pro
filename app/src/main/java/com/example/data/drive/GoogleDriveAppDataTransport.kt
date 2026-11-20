package com.example.data.drive

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Small, fakeable Drive v3 boundary. Access tokens are passed per call and never persisted/logged. */
interface DriveAppDataTransport {
    suspend fun findSnapshots(accessToken: String): List<DriveRemoteFile>
    suspend fun read(accessToken: String, fileId: String): String
    suspend fun create(accessToken: String, json: String): DriveRemoteFile
    suspend fun update(accessToken: String, file: DriveRemoteFile, json: String): DriveRemoteFile
}

data class DriveRemoteFile(val id: String, val etag: String?, val version: String?)

class GoogleDriveAppDataTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS).build(),
) : DriveAppDataTransport {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listAdapter = moshi.adapter(DriveFileList::class.java)
    private val fileAdapter = moshi.adapter(DriveFile::class.java)
    private val metadataAdapter = moshi.adapter(DriveCreateMetadata::class.java)
    private val boundary = "streamflow-${System.nanoTime()}"

    override suspend fun findSnapshots(accessToken: String): List<DriveRemoteFile> = withContext(Dispatchers.IO) {
        val q = "name='stream-flow-pro-data-v1.json' and 'appDataFolder' in parents and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&spaces=appDataFolder&fields=files(id,name,version)"
        val response = execute(Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build())
        val entries = listAdapter.fromJson(response)?.files.orEmpty()
        entries.map { entry ->
            val meta = getMetadata(accessToken, entry.id)
            DriveRemoteFile(entry.id, meta.first, meta.second ?: entry.version)
        }
    }

    override suspend fun read(accessToken: String, fileId: String): String = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("https://www.googleapis.com/drive/v3/files/${encodePath(fileId)}?alt=media")
            .header("Authorization", "Bearer $accessToken").get().build())
    }

    override suspend fun create(accessToken: String, json: String): DriveRemoteFile = withContext(Dispatchers.IO) {
        val metadata = metadataAdapter.toJson(DriveCreateMetadata("stream-flow-pro-data-v1.json", listOf("appDataFolder")))
        val body = multipart(metadata, json)
        val response = executeResponse(Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,version")
            .header("Authorization", "Bearer $accessToken").post(body).build())
        val created = response.use { requireNotNull(fileAdapter.fromJson(it.body?.string().orEmpty())) { "Drive returned no file metadata" } }
        val meta = getMetadata(accessToken, created.id)
        DriveRemoteFile(created.id, meta.first, meta.second ?: created.version)
    }

    override suspend fun update(accessToken: String, file: DriveRemoteFile, json: String): DriveRemoteFile = withContext(Dispatchers.IO) {
        val body = multipart(null, json)
        val builder = Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files/${encodePath(file.id)}?uploadType=multipart&fields=id,version")
            .header("Authorization", "Bearer $accessToken")
        file.etag?.let { builder.header("If-Match", it) } ?: throw IOException("Drive file has no ETag; refusing an unconditional overwrite")
        val response = executeResponse(builder.patch(body).build())
        val updated = response.use { requireNotNull(fileAdapter.fromJson(it.body?.string().orEmpty())) { "Drive returned no file metadata" } }
        val meta = getMetadata(accessToken, updated.id)
        DriveRemoteFile(updated.id, meta.first, meta.second ?: updated.version)
    }

    private suspend fun getMetadata(accessToken: String, id: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://www.googleapis.com/drive/v3/files/${encodePath(id)}?fields=id,version")
            .header("Authorization", "Bearer $accessToken").get().build()
        executeResponse(request).use { response ->
            val file = fileAdapter.fromJson(response.body?.string().orEmpty())
            response.header("ETag") to file?.version
        }
    }

    private fun multipart(metadata: String?, json: String): okhttp3.RequestBody {
        val text = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata ?: "{}")
            append("\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json)
            append("\r\n--$boundary--\r\n")
        }
        return text.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
    }

    private fun execute(request: Request): String = executeResponse(request).use { it.body?.string().orEmpty() }
    private fun executeResponse(request: Request): okhttp3.Response {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException(if (code == 412) "Drive snapshot changed remotely; sync again to resolve the conflict" else "Google Drive request failed (HTTP $code)")
        }
        return response
    }

    private fun encodePath(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private data class DriveFileList(val files: List<DriveFile> = emptyList())
    private data class DriveFile(val id: String, val version: String? = null)
    private data class DriveCreateMetadata(val name: String, val parents: List<String>)
}
