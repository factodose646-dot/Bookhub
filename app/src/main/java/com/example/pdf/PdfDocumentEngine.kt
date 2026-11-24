package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class PdfDocumentHandle(
    private val fileDescriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    val pageCount: Int = renderer.pageCount

    private val renderMutex = Mutex()
    private val pageRatios = mutableMapOf<Int, Float>()
    @Volatile
    private var isClosed = false

    // Bounded 28 MB bitmap cache to support large PDFs without OOM
    private val maxCacheKb = 28 * 1024
    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    suspend fun getPageAspectRatio(pageIndex: Int): Float = withContext(Dispatchers.IO) {
        if (isClosed) throw CancellationException("Document closed")
        renderMutex.withLock {
            if (isClosed) throw CancellationException("Document closed")
            pageRatios[pageIndex]?.let { return@withLock it }
            try {
                renderer.openPage(pageIndex).use { page ->
                    val ratio = if (page.height > 0) {
                        page.width.toFloat() / page.height.toFloat()
                    } else {
                        612f / 792f
                    }
                    pageRatios[pageIndex] = ratio
                    ratio
                }
            } catch (e: IllegalStateException) {
                throw CancellationException("Document closed", e)
            }
        }
    }

    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
    ): Bitmap = withContext(Dispatchers.IO) {
        if (isClosed) throw CancellationException("Document closed")
        val safeWidth = targetWidthPx.coerceIn(320, 2048)
        val cacheKey = "${pageIndex}_$safeWidth"
        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        renderMutex.withLock {
            if (isClosed) throw CancellationException("Document closed")
            bitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return@withLock cached
            }

            try {
                renderer.openPage(pageIndex).use { page ->
                    val aspectRatio = if (page.width > 0) {
                        page.height.toFloat() / page.width.toFloat()
                    } else {
                        792f / 612f
                    }
                    val targetHeight = (safeWidth * aspectRatio).toInt().coerceIn(320, 3200)
                    pageRatios[pageIndex] = if (targetHeight > 0) {
                        safeWidth.toFloat() / targetHeight.toFloat()
                    } else {
                        612f / 792f
                    }

                    val bitmap = Bitmap.createBitmap(safeWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    if (!isClosed) {
                        bitmapCache.put(cacheKey, bitmap)
                    }
                    bitmap
                }
            } catch (e: IllegalStateException) {
                throw CancellationException("Document closed", e)
            }
        }
    }

    override fun close() {
        isClosed = true
        bitmapCache.evictAll()
        try {
            renderer.close()
        } catch (_: Exception) {
        }
        try {
            fileDescriptor.close()
        } catch (_: Exception) {
        }
    }
}

class PdfDocumentEngine(
    private val context: Context,
    private val driveAccessTokenProvider: (() -> String?)? = null,
) {
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun isCachedOffline(bookId: String, source: PdfSource?): Boolean {
        val cacheDir = File(context.cacheDir, "bookhub_pdfs")
        return when (source) {
            is PdfSource.LocalAsset -> true
            is PdfSource.LocalFile -> File(source.filePath).let { it.exists() && it.length() > 8L && isValidPdfHeader(it) }
            is PdfSource.RemoteHttps -> {
                val f = File(cacheDir, "${bookId}_${source.url.hashCode().toUInt().toString(16)}.pdf")
                val legacy = File(cacheDir, "${bookId}_${source.url.hashCode()}.pdf")
                (f.exists() && f.length() > 8L && isValidPdfHeader(f)) ||
                    (legacy.exists() && legacy.length() > 8L && isValidPdfHeader(legacy))
            }
            is PdfSource.GoogleDriveRef -> {
                val f = driveCacheFile(source.fileId)
                f.exists() && f.length() > 8L && isValidPdfHeader(f)
            }
            null -> false
        }
    }

    suspend fun prefetchPdfForOffline(
        bookId: String,
        source: PdfSource?,
        onProgress: (Float?) -> Unit = {},
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val file = resolveToReadableFile(bookId, source, onProgress)
            Result.success(file.length())
        } catch (e: CancellationException) {
            throw e
        } catch (e: PdfLoadException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun openDocument(
        bookId: String,
        source: PdfSource?,
        onProgress: (Float?) -> Unit = {},
    ): PdfDocumentHandle = withContext(Dispatchers.IO) {
        val pdfFile = resolveToReadableFile(bookId, source, onProgress)

        try {
            val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            if (renderer.pageCount <= 0) {
                renderer.close()
                descriptor.close()
                throw PdfLoadException(PdfLoadError.InvalidPdfFormat)
            }
            PdfDocumentHandle(descriptor, renderer)
        } catch (e: PdfLoadException) {
            throw e
        } catch (e: SecurityException) {
            throw PdfLoadException(PdfLoadError.InvalidPdfFormat)
        } catch (e: IOException) {
            throw PdfLoadException(PdfLoadError.InvalidPdfFormat)
        } catch (e: Exception) {
            throw PdfLoadException(
                PdfLoadError.FileReadFailed(e.message ?: "Unexpected error while opening PDF."),
            )
        }
    }

    private fun resolveToReadableFile(
        bookId: String,
        source: PdfSource?,
        onProgress: (Float?) -> Unit,
    ): File {
        if (source == null) {
            throw PdfLoadException(PdfLoadError.MissingSource)
        }

        return when (source) {
            is PdfSource.GoogleDriveRef -> {
                downloadDrivePdfToCache(source.fileId, source.originalUrl, onProgress)
            }
            is PdfSource.LocalAsset -> {
                onProgress(null)
                copyAssetToCache(bookId, source.assetPath)
            }
            is PdfSource.LocalFile -> {
                onProgress(null)
                val file = File(source.filePath)
                if (!file.exists() || !file.canRead()) {
                    throw PdfLoadException(PdfLoadError.MissingSource)
                }
                validateFileHeader(file)
                file
            }
            is PdfSource.RemoteHttps -> {
                downloadRemotePdfToCache(bookId, source.url, onProgress)
            }
        }
    }

    private fun driveCacheFile(fileId: String): File {
        val cacheDir = File(context.cacheDir, "bookhub_pdfs").apply { mkdirs() }
        val safeId = fileId.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
        return File(cacheDir, "drive_${safeId}.pdf")
    }

    private fun copyAssetToCache(bookId: String, assetPath: String): File {
        val cacheDir = File(context.cacheDir, "bookhub_pdfs").apply { mkdirs() }
        val sanitizedKey = "${bookId}_${assetPath.hashCode().toUInt().toString(16)}"
        val targetFile = File(cacheDir, "$sanitizedKey.pdf")
        if (targetFile.exists()) {
            if (targetFile.length() > 8L && isValidPdfHeader(targetFile)) {
                return targetFile
            }
            targetFile.delete()
        }

        val tempFile = File(cacheDir, "$sanitizedKey.tmp")
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            validateFileHeader(tempFile)
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return targetFile
        } catch (e: PdfLoadException) {
            tempFile.delete()
            throw e
        } catch (e: IOException) {
            tempFile.delete()
            throw PdfLoadException(PdfLoadError.MissingSource)
        }
    }

    private fun downloadDrivePdfToCache(
        fileId: String,
        originalUrl: String,
        onProgress: (Float?) -> Unit,
    ): File {
        if (fileId.isBlank() || fileId == "unknown_drive_ref") {
            throw PdfLoadException(PdfLoadError.GoogleDriveNotConnected)
        }
        val targetFile = driveCacheFile(fileId)
        if (targetFile.exists()) {
            if (targetFile.length() > 8L && isValidPdfHeader(targetFile)) {
                return targetFile
            }
            targetFile.delete()
        }

        val candidates = buildList {
            val token = driveAccessTokenProvider?.invoke()?.takeIf { it.isNotBlank() }
            if (token != null) {
                add(
                    Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                        .header("Authorization", "Bearer $token")
                        .build(),
                )
            }
            add(
                Request.Builder()
                    .url("https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t")
                    .build(),
            )
            add(
                Request.Builder()
                    .url("https://drive.google.com/uc?export=download&confirm=t&id=$fileId")
                    .build(),
            )
            if (originalUrl.startsWith("https://", ignoreCase = true) &&
                originalUrl.contains("export=download", ignoreCase = true)
            ) {
                add(Request.Builder().url(originalUrl).build())
            }
        }

        var lastError: PdfLoadException? = null
        for (req in candidates) {
            try {
                return executePdfDownload(
                    request = req,
                    targetFile = targetFile,
                    allowHtmlConfirmFollowUp = true,
                    onProgress = onProgress,
                )
            } catch (e: PdfLoadException) {
                lastError = e
                // If network is down, fail fast instead of retrying all endpoints unnecessarily
                if (e.error is PdfLoadError.NetworkUnavailable) throw e
            }
        }
        throw lastError ?: PdfLoadException(
            PdfLoadError.GoogleDriveAccessDenied(
                "Could not access this Google Drive PDF. Ensure the file is shared for reading or try again.",
            ),
        )
    }

    private fun downloadRemotePdfToCache(
        bookId: String,
        url: String,
        onProgress: (Float?) -> Unit,
    ): File {
        val cacheDir = File(context.cacheDir, "bookhub_pdfs").apply { mkdirs() }
        val sanitizedKey = "${bookId}_${url.hashCode().toUInt().toString(16)}"
        val targetFile = File(cacheDir, "$sanitizedKey.pdf")
        if (targetFile.exists()) {
            if (targetFile.length() > 8L && isValidPdfHeader(targetFile)) {
                return targetFile
            }
            targetFile.delete()
        }

        val request = Request.Builder().url(url).build()
        return executePdfDownload(
            request = request,
            targetFile = targetFile,
            allowHtmlConfirmFollowUp = false,
            onProgress = onProgress,
        )
    }

    private fun executePdfDownload(
        request: Request,
        targetFile: File,
        allowHtmlConfirmFollowUp: Boolean,
        onProgress: (Float?) -> Unit,
    ): File {
        val tempFile = File(targetFile.parentFile, "${targetFile.nameWithoutExtension}.tmp")
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw PdfLoadException(
                        PdfLoadError.GoogleDriveAccessDenied(
                            "Google Drive refused access (HTTP ${response.code}). Verify the book is published with link access.",
                        ),
                    )
                }
                if (!response.isSuccessful) {
                    throw PdfLoadException(
                        PdfLoadError.RemoteLoadFailed(response.code, response.message),
                    )
                }
                val body = response.body
                    ?: throw PdfLoadException(PdfLoadError.RemoteLoadFailed(response.code, "Empty response body"))

                val contentLength = body.contentLength()
                var totalBytesRead = 0L
                val buffer = ByteArray(16 * 1024)
                var headerChecked = false

                body.byteStream().use { input ->
                    val firstRead = input.read(buffer)
                    if (firstRead <= 0) {
                        throw PdfLoadException(PdfLoadError.InvalidPdfFormat)
                    }
                    val firstChunk = buffer.copyOf(firstRead)
                    headerChecked = true

                    if (!PdfValidator.hasValidPdfHeader(firstChunk)) {
                        if (allowHtmlConfirmFollowUp) {
                            val snippetBuilder = StringBuilder()
                            snippetBuilder.append(firstChunk.decodeToString())
                            val probeBuf = ByteArray(8192)
                            while (snippetBuilder.length < 65_536) {
                                val r = input.read(probeBuf)
                                if (r <= 0) break
                                snippetBuilder.append(probeBuf.copyOf(r).decodeToString())
                            }
                            val confirmUrl = extractDriveConfirmUrl(snippetBuilder.toString())
                            if (confirmUrl != null) {
                                return executePdfDownload(
                                    request = Request.Builder().url(confirmUrl).build(),
                                    targetFile = targetFile,
                                    allowHtmlConfirmFollowUp = false,
                                    onProgress = onProgress,
                                )
                            }
                        }
                        throw PdfLoadException(PdfLoadError.InvalidPdfFormat)
                    }

                    FileOutputStream(tempFile).use { output ->
                        output.write(firstChunk)
                        totalBytesRead += firstRead
                        if (contentLength > 0) {
                            onProgress((totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                        } else {
                            onProgress(null)
                        }

                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalBytesRead += read
                            if (contentLength > 0) {
                                onProgress((totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                            } else {
                                onProgress(null)
                            }
                        }
                    }
                }
                if (!headerChecked) {
                    throw PdfLoadException(PdfLoadError.InvalidPdfFormat)
                }
            }
            validateFileHeader(tempFile)
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return targetFile
        } catch (e: PdfLoadException) {
            tempFile.delete()
            throw e
        } catch (e: IOException) {
            tempFile.delete()
            throw PdfLoadException(PdfLoadError.NetworkUnavailable)
        }
    }

    private fun extractDriveConfirmUrl(html: String): String? {
        val actionMatch = Regex("""action="(https://drive\.usercontent\.google\.com/download[^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1)
        if (actionMatch != null) {
            val base = actionMatch.replace("&amp;", "&")
            val idVal = Regex("""name="id"\s+value="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
            val confirmVal = Regex("""name="confirm"\s+value="([^"]+)"""").find(html)?.groupValues?.getOrNull(1) ?: "t"
            val uuidVal = Regex("""name="uuid"\s+value="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
            if (!idVal.isNullOrBlank()) {
                return buildString {
                    append(base)
                    append(if ('?' in base) '&' else '?')
                    append("id=").append(idVal)
                    append("&export=download&confirm=").append(confirmVal)
                    if (!uuidVal.isNullOrBlank()) append("&uuid=").append(uuidVal)
                }
            }
        }
        return null
    }

    private fun isValidPdfHeader(file: File): Boolean = try {
        val header = ByteArray(1024)
        val bytesRead = file.inputStream().use { it.read(header) }
        bytesRead > 0 && PdfValidator.hasValidPdfHeader(header.copyOf(bytesRead))
    } catch (_: Exception) {
        false
    }

    private fun validateFileHeader(file: File) {
        if (!isValidPdfHeader(file)) {
            throw PdfLoadException(PdfLoadError.InvalidPdfFormat)
        }
    }
}
