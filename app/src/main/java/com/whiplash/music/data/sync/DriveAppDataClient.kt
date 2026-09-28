package com.whiplash.music.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * The Google Drive REST calls sync needs, all inside the app's hidden
 * appDataFolder (scope drive.appdata): only Whiplash can see these files, and
 * it can't see anything else in the user's Drive.
 */
class DriveAppDataClient(private val http: OkHttpClient) {

    data class RemoteFile(val id: String, val version: Long)
    data class Profile(val name: String?, val email: String?, val photoUrl: String?)

    /** The access token was rejected (expired or revoked): get a fresh one and retry. */
    class Unauthorized : IOException("Drive rejected the access token")

    /** Any other failed request, with its HTTP status. */
    class DriveException(val code: Int, message: String) : IOException("Drive HTTP $code: $message")

    suspend fun find(token: String): RemoteFile? = io {
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name = '$FILE_NAME' and trashed = false")
            .addQueryParameter("fields", "files(id,version,modifiedTime)")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("pageSize", "10")
            .build()
        val json = call(Request.Builder().url(url).get(), token).json()
        val files = json.optJSONArray("files") ?: return@io null
        if (files.length() == 0) null else files.getJSONObject(0).toRemoteFile()
    }

    /** Current version of [id], or null if it no longer exists. */
    suspend fun meta(token: String, id: String): RemoteFile? = io {
        val url = "$API/files/$id".toHttpUrl().newBuilder().addQueryParameter("fields", "id,version").build()
        try {
            call(Request.Builder().url(url).get(), token).json().toRemoteFile()
        } catch (e: DriveException) {
            if (e.code == 404) null else throw e
        }
    }

    suspend fun download(token: String, id: String): ByteArray = io {
        val url = "$API/files/$id".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        call(Request.Builder().url(url).get(), token).use { it.body.bytes() }
    }

    suspend fun create(token: String, bytes: ByteArray): RemoteFile = io {
        val metadata = JSONObject()
            .put("name", FILE_NAME)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("mimeType", MIME)
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody(MIME.toMediaType()))
            .build()
        val url = "$UPLOAD/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,version")
            .build()
        call(Request.Builder().url(url).post(body), token).json().toRemoteFile()
    }

    suspend fun update(token: String, id: String, bytes: ByteArray): RemoteFile = io {
        val url = "$UPLOAD/files/$id".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "media")
            .addQueryParameter("fields", "id,version")
            .build()
        call(Request.Builder().url(url).patch(bytes.toRequestBody(MIME.toMediaType())), token).json().toRemoteFile()
    }

    /** Deletes every copy of the sync file (normally one). */
    suspend fun deleteAll(token: String) = io {
        while (true) {
            val file = find(token) ?: break
            try {
                call(Request.Builder().url("$API/files/${file.id}").delete(), token).close()
            } catch (e: DriveException) {
                if (e.code != 404) throw e
            }
        }
    }

    suspend fun profile(token: String): Profile = io {
        val url = "$API/about".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user(displayName,emailAddress,photoLink)")
            .build()
        val user = call(Request.Builder().url(url).get(), token).json().optJSONObject("user")
        Profile(
            name = user?.optString("displayName")?.takeIf { it.isNotBlank() },
            email = user?.optString("emailAddress")?.takeIf { it.isNotBlank() },
            photoUrl = user?.optString("photoLink")?.takeIf { it.isNotBlank() },
        )
    }

    private fun call(builder: Request.Builder, token: String): Response {
        val response = http.newCall(builder.header("Authorization", "Bearer $token").build()).execute()
        if (response.isSuccessful) return response
        val code = response.code
        val message = response.use { runCatching { it.body.string() }.getOrDefault("").take(300) }
        if (code == 401) throw Unauthorized()
        throw DriveException(code, message)
    }

    private fun Response.json(): JSONObject = use { JSONObject(it.body.string()) }

    private fun JSONObject.toRemoteFile() = RemoteFile(getString("id"), optString("version").toLongOrNull() ?: 0L)

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        const val FILE_NAME = "whiplash-library.json.gz"
        private const val MIME = "application/gzip"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}
