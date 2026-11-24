package com.example.cloud

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DriveAuthStatus(
    val isAuthorized: Boolean,
    val accessToken: String? = null,
    val pendingConsentIntentSender: IntentSender? = null,
    val errorMessage: String? = null,
)

data class DriveUploadedFile(
    val fileId: String,
    val fileName: String,
    val byteSize: Long,
    val downloadUrl: String,
    val viewUrl: String,
)

/**
 * Official Google Drive REST API v3 gateway backed by Google Play Services AuthorizationClient
 * (`https://www.googleapis.com/auth/drive.file`). Uses short-lived OAuth2 access tokens on device
 * without embedding any client secrets or refresh tokens.
 */
class GoogleDriveGateway(
    private val context: Context,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedFolderId: String? = null

    fun hasCachedToken(): Boolean = !cachedAccessToken.isNullOrBlank()

    fun getCachedTokenOrNull(): String? = cachedAccessToken

    fun clearSession() {
        cachedAccessToken = null
        cachedFolderId = null
    }

    suspend fun requestOrRefreshAccessToken(
        activityContext: Context = context,
    ): DriveAuthStatus = withContext(Dispatchers.IO) {
        try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(activityContext)
                .authorize(request)
                .await()

            val pendingIntent = result.pendingIntent
            if (result.hasResolution() && pendingIntent != null) {
                DriveAuthStatus(
                    isAuthorized = false,
                    pendingConsentIntentSender = pendingIntent.intentSender,
                )
            } else {
                val token = result.accessToken
                if (!token.isNullOrBlank()) {
                    cachedAccessToken = token
                    DriveAuthStatus(isAuthorized = true, accessToken = token)
                } else {
                    DriveAuthStatus(
                        isAuthorized = false,
                        errorMessage = "Google Drive did not return an OAuth access token.",
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            Log.w(TAG, "Drive authorization ApiException (${e.statusCode}): ${e.message}", e)
            DriveAuthStatus(
                isAuthorized = false,
                errorMessage = "Google Drive OAuth error (${e.statusCode}): ${e.localizedMessage ?: "Ensure Drive API & OAuth consent are enabled."}",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Drive authorization failed", e)
            DriveAuthStatus(
                isAuthorized = false,
                errorMessage = e.localizedMessage ?: "Failed to authorize Google Drive.",
            )
        }
    }

    fun handleAuthorizationResultIntent(
        activityContext: Context,
        intent: Intent?,
    ): DriveAuthStatus {
        return try {
            val authResult = Identity.getAuthorizationClient(activityContext)
                .getAuthorizationResultFromIntent(intent)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                cachedAccessToken = token
                DriveAuthStatus(isAuthorized = true, accessToken = token)
            } else {
                DriveAuthStatus(
                    isAuthorized = false,
                    errorMessage = "Google Drive consent was cancelled or no token was issued.",
                )
            }
        } catch (e: ApiException) {
            Log.w(TAG, "Drive consent intent failed (${e.statusCode})", e)
            DriveAuthStatus(
                isAuthorized = false,
                errorMessage = "Drive authorization cancelled or rejected (${e.statusCode}).",
            )
        } catch (e: Exception) {
            DriveAuthStatus(
                isAuthorized = false,
                errorMessage = e.localizedMessage ?: "Failed to complete Google Drive authorization.",
            )
        }
    }

    suspend fun uploadPdf(
        localFile: File,
        bookTitle: String,
        setPublicReadPermission: Boolean,
        onProgress: (Float) -> Unit,
    ): Result<DriveUploadedFile> {
        val safeTitle = bookTitle.trim().replace(Regex("""[^\w\-. ]+"""), "_").take(80).ifEmpty { "bookhub_book" }
        return uploadFileResumable(
            localFile = localFile,
            driveFileName = "$safeTitle.pdf",
            mimeType = "application/pdf",
            setPublicReadPermission = setPublicReadPermission,
            onProgress = onProgress,
        )
    }

    suspend fun uploadCoverImage(
        localFile: File,
        bookTitle: String,
        setPublicReadPermission: Boolean,
    ): Result<DriveUploadedFile> {
        val safeTitle = bookTitle.trim().replace(Regex("""[^\w\-. ]+"""), "_").take(80).ifEmpty { "bookhub_cover" }
        return uploadFileResumable(
            localFile = localFile,
            driveFileName = "${safeTitle}_cover.jpg",
            mimeType = "image/jpeg",
            setPublicReadPermission = setPublicReadPermission,
            onProgress = {},
        )
    }

    suspend fun setPublicReadAccess(
        fileId: String,
        allowAnyoneRead: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (fileId.isBlank()) return@withContext Result.success(Unit)
        val token = resolveValidToken().getOrElse { return@withContext Result.failure(it) }
        try {
            if (allowAnyoneRead) {
                grantAnyoneReaderPermission(fileId, token)
            } else {
                revokeAnyonePermissions(fileId, token)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(fileId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        if (fileId.isNullOrBlank()) return@withContext Result.success(Unit)
        val token = resolveValidToken().getOrElse { return@withContext Result.failure(it) }
        try {
            val req = Request.Builder()
                .url("$DRIVE_FILES_ENDPOINT/$fileId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 404) {
                    Result.success(Unit)
                } else {
                    if (resp.code == 401) cachedAccessToken = null
                    Result.failure(
                        IllegalStateException(
                            parseGoogleApiError(resp.code, resp.body?.string()),
                        ),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun uploadFileResumable(
        localFile: File,
        driveFileName: String,
        mimeType: String,
        setPublicReadPermission: Boolean,
        onProgress: (Float) -> Unit,
    ): Result<DriveUploadedFile> = withContext(Dispatchers.IO) {
        if (!localFile.exists() || !localFile.canRead()) {
            return@withContext Result.failure(IllegalArgumentException("Local file missing before upload."))
        }
        val initialToken = resolveValidToken().getOrElse { return@withContext Result.failure(it) }
        try {
            var token = initialToken
            var folderId = ensureLibraryFolder(token)
            val fileLength = localFile.length()

            fun buildInitRequest(authToken: String, targetFolderId: String?): Request {
                val metadataJson = JSONObject().apply {
                    put("name", driveFileName)
                    if (targetFolderId != null) {
                        put("parents", JSONArray().put(targetFolderId))
                    }
                    put("appProperties", JSONObject().put("source", "bookhub_android"))
                }
                return Request.Builder()
                    .url("$DRIVE_UPLOAD_ENDPOINT?uploadType=resumable&fields=id,name,size")
                    .header("Authorization", "Bearer $authToken")
                    .header("X-Upload-Content-Type", mimeType)
                    .header("X-Upload-Content-Length", fileLength.toString())
                    .post(metadataJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            }

            val uploadSessionUrl = httpClient.newCall(buildInitRequest(token, folderId)).execute().use { resp ->
                if (resp.code == 401) {
                    cachedAccessToken = null
                    val refreshedToken = resolveValidToken().getOrNull()
                    if (refreshedToken != null) {
                        token = refreshedToken
                        folderId = ensureLibraryFolder(token)
                        httpClient.newCall(buildInitRequest(token, folderId)).execute().use { retryResp ->
                            if (!retryResp.isSuccessful) {
                                if (retryResp.code == 401) cachedAccessToken = null
                                throw IllegalStateException(
                                    parseGoogleApiError(retryResp.code, retryResp.body?.string()),
                                )
                            }
                            retryResp.header("Location")
                                ?: throw IllegalStateException("Google Drive did not return a resumable upload URL.")
                        }
                    } else {
                        throw IllegalStateException(parseGoogleApiError(resp.code, resp.body?.string()))
                    }
                } else if (resp.code == 404 && folderId != null) {
                    // Cached BookHub Library folder was deleted on Drive; recreate and retry
                    cachedFolderId = null
                    folderId = ensureLibraryFolder(token)
                    httpClient.newCall(buildInitRequest(token, folderId)).execute().use { retryResp ->
                        if (!retryResp.isSuccessful) {
                            throw IllegalStateException(
                                parseGoogleApiError(retryResp.code, retryResp.body?.string()),
                            )
                        }
                        retryResp.header("Location")
                            ?: throw IllegalStateException("Google Drive did not return a resumable upload URL.")
                    }
                } else if (!resp.isSuccessful) {
                    throw IllegalStateException(
                        parseGoogleApiError(resp.code, resp.body?.string()),
                    )
                } else {
                    resp.header("Location")
                        ?: throw IllegalStateException("Google Drive did not return a resumable upload URL.")
                }
            }

            val streamingBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaType()
                override fun contentLength() = fileLength
                override fun writeTo(sink: BufferedSink) {
                    val buffer = ByteArray(32 * 1024)
                    var uploaded = 0L
                    FileInputStream(localFile).use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            uploaded += read
                            if (fileLength > 0L) {
                                onProgress((uploaded.toFloat() / fileLength.toFloat()).coerceIn(0f, 0.96f))
                            }
                        }
                    }
                }
            }

            val putReq = Request.Builder()
                .url(uploadSessionUrl)
                .put(streamingBody)
                .build()

            val uploadedJson = httpClient.newCall(putReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    if (resp.code == 401) cachedAccessToken = null
                    throw IllegalStateException(parseGoogleApiError(resp.code, raw))
                }
                JSONObject(raw)
            }

            val fileId = uploadedJson.getString("id")
            if (setPublicReadPermission) {
                grantAnyoneReaderPermission(fileId, token)
            }
            onProgress(1.0f)

            Result.success(
                DriveUploadedFile(
                    fileId = fileId,
                    fileName = uploadedJson.optString("name", driveFileName),
                    byteSize = uploadedJson.optLong("size", fileLength),
                    downloadUrl = buildDirectDownloadUrl(fileId),
                    viewUrl = buildDirectViewUrl(fileId),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Drive upload failed", e)
            Result.failure(e)
        }
    }

    private fun ensureLibraryFolder(token: String): String? {
        cachedFolderId?.let { return it }
        val query = URLEncoder.encode(
            "name='$LIBRARY_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false",
            "UTF-8",
        )
        val searchReq = Request.Builder()
            .url("$DRIVE_FILES_ENDPOINT?q=$query&fields=files(id,name)&pageSize=1")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(searchReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val files = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val found = files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
                    if (found != null) {
                        cachedFolderId = found
                        return found
                    }
                }
            } else if (resp.code == 401) {
                cachedAccessToken = null
            }
        }

        val createBody = JSONObject().apply {
            put("name", LIBRARY_FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val createReq = Request.Builder()
            .url("$DRIVE_FILES_ENDPOINT?fields=id")
            .header("Authorization", "Bearer $token")
            .post(createBody)
            .build()

        httpClient.newCall(createReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val created = JSONObject(resp.body?.string().orEmpty()).optString("id").takeIf { it.isNotBlank() }
                if (created != null) cachedFolderId = created
                return created
            }
        }
        return null
    }

    private fun grantAnyoneReaderPermission(fileId: String, token: String) {
        val permBody = JSONObject().apply {
            put("role", "reader")
            put("type", "anyone")
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val req = Request.Builder()
            .url("$DRIVE_FILES_ENDPOINT/$fileId/permissions")
            .header("Authorization", "Bearer $token")
            .post(permBody)
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401) cachedAccessToken = null
                throw IllegalStateException(
                    parseGoogleApiError(resp.code, resp.body?.string()),
                )
            }
        }
    }

    private fun revokeAnyonePermissions(fileId: String, token: String) {
        val listReq = Request.Builder()
            .url("$DRIVE_FILES_ENDPOINT/$fileId/permissions?fields=permissions(id,type)")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        val anyoneIds = mutableListOf<String>()
        httpClient.newCall(listReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val perms = JSONObject(resp.body?.string().orEmpty()).optJSONArray("permissions")
                if (perms != null) {
                    for (i in 0 until perms.length()) {
                        val obj = perms.getJSONObject(i)
                        if (obj.optString("type") == "anyone") {
                            obj.optString("id").takeIf { it.isNotBlank() }?.let { anyoneIds.add(it) }
                        }
                    }
                }
            } else if (resp.code == 401) {
                cachedAccessToken = null
            }
        }

        anyoneIds.forEach { permId ->
            val delReq = Request.Builder()
                .url("$DRIVE_FILES_ENDPOINT/$fileId/permissions/$permId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            httpClient.newCall(delReq).execute().close()
        }
    }

    private suspend fun resolveValidToken(): Result<String> {
        cachedAccessToken?.takeIf { it.isNotBlank() }?.let { return Result.success(it) }
        val refreshed = requestOrRefreshAccessToken(context)
        return if (refreshed.isAuthorized && !refreshed.accessToken.isNullOrBlank()) {
            Result.success(refreshed.accessToken)
        } else {
            Result.failure(
                IllegalStateException(
                    refreshed.errorMessage
                        ?: "Google Drive access not authorized yet. Tap 'Connect Google Drive' first.",
                ),
            )
        }
    }

    private fun parseGoogleApiError(httpCode: Int, rawBody: String?): String {
        if (!rawBody.isNullOrBlank()) {
            try {
                val err = JSONObject(rawBody).optJSONObject("error")
                val msg = err?.optString("message").orEmpty()
                val reason = err?.optJSONArray("errors")
                    ?.optJSONObject(0)
                    ?.optString("reason")
                    .orEmpty()
                if (reason == "accessNotConfigured" || msg.contains("has not been used", ignoreCase = true)) {
                    return "Google Drive API (drive.googleapis.com) is not enabled in the Firebase Cloud Console project yet. ($msg)"
                }
                if (msg.isNotBlank()) return "Google Drive HTTP $httpCode: $msg"
            } catch (_: Exception) {
            }
        }
        return "Google Drive request failed (HTTP $httpCode)."
    }

    companion object {
        private const val TAG = "BookHubDrive"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val LIBRARY_FOLDER_NAME = "BookHub Library"
        private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun buildDirectDownloadUrl(fileId: String): String =
            "https://drive.usercontent.google.com/download?id=$fileId&export=download"

        fun buildDirectViewUrl(fileId: String): String =
            "https://drive.usercontent.google.com/download?id=$fileId&export=view"
    }
}
