package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.pdf.PdfValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

sealed interface PdfImportResult {
    data class Success(
        val filePath: String,
        val displayName: String,
        val byteSize: Long,
        val formattedSize: String,
        val pageCount: Int,
    ) : PdfImportResult

    data class Error(val message: String) : PdfImportResult
}

sealed interface CoverImportResult {
    data class Success(val filePath: String) : CoverImportResult
    data class Error(val message: String) : CoverImportResult
}

/**
 * Modular book file storage abstraction for Phase 5 local internal storage.
 * Phase 6 can extend or replace this interface with Google Drive & Firebase Storage implementations.
 */
interface BookStorageGateway {
    suspend fun importPdf(uri: Uri): PdfImportResult
    suspend fun importCoverImage(uri: Uri): CoverImportResult
    fun deleteManagedFile(filePath: String?)
    fun calculateManagedPdfStorageBytes(): Long
    fun countManagedPdfFiles(): Int
}

class NoOpBookStorageGateway : BookStorageGateway {
    override suspend fun importPdf(uri: Uri): PdfImportResult =
        PdfImportResult.Error("File picker unavailable in unit test environment.")

    override suspend fun importCoverImage(uri: Uri): CoverImportResult =
        CoverImportResult.Error("Image picker unavailable in unit test environment.")

    override fun deleteManagedFile(filePath: String?) = Unit
    override fun calculateManagedPdfStorageBytes(): Long = 0L
    override fun countManagedPdfFiles(): Int = 0
}

class LocalBookStorageGateway(
    private val context: Context,
) : BookStorageGateway {

    private val pdfsDir: File
        get() = File(context.filesDir, "bookhub_pdfs").apply { mkdirs() }

    private val coversDir: File
        get() = File(context.filesDir, "bookhub_covers").apply { mkdirs() }

    override suspend fun importPdf(uri: Uri): PdfImportResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "uploaded_book.pdf"
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null &&
            !mimeType.equals("application/pdf", ignoreCase = true) &&
            !mimeType.equals("application/octet-stream", ignoreCase = true) &&
            !displayName.endsWith(".pdf", ignoreCase = true)
        ) {
            return@withContext PdfImportResult.Error(
                "Unsupported file type ($mimeType). Please choose a valid .pdf document.",
            )
        }

        val targetId = UUID.randomUUID().toString()
        val tempFile = File(pdfsDir, "$targetId.tmp")
        val finalFile = File(pdfsDir, "$targetId.pdf")

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext PdfImportResult.Error("Unable to open the selected file.")

            var totalBytes = 0L
            input.use { stream ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var firstChunkChecked = false
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        if (!firstChunkChecked) {
                            firstChunkChecked = true
                            if (!PdfValidator.hasValidPdfHeader(buffer.copyOf(read))) {
                                tempFile.delete()
                                return@withContext PdfImportResult.Error(
                                    "The selected file is not a valid PDF document (missing %PDF header).",
                                )
                            }
                        }
                        totalBytes += read
                        if (totalBytes > MAX_PDF_BYTES) {
                            tempFile.delete()
                            return@withContext PdfImportResult.Error(
                                "PDF exceeds the 150 MB local storage limit.",
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            if (totalBytes <= 0L) {
                tempFile.delete()
                return@withContext PdfImportResult.Error("The selected PDF file is empty.")
            }

            // Verify PDF can actually be rendered and extract exact page count
            val pageCount = extractPdfPageCount(tempFile)
            if (pageCount <= 0) {
                tempFile.delete()
                return@withContext PdfImportResult.Error(
                    "Unable to open PDF pages. The file may be corrupted or password-protected.",
                )
            }

            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            PdfImportResult.Success(
                filePath = finalFile.absolutePath,
                displayName = displayName,
                byteSize = totalBytes,
                formattedSize = formatBytesLabel(totalBytes),
                pageCount = pageCount,
            )
        } catch (e: SecurityException) {
            tempFile.delete()
            PdfImportResult.Error("Permission denied while reading the PDF file.")
        } catch (e: Exception) {
            tempFile.delete()
            PdfImportResult.Error(e.message ?: "Failed to import the selected PDF file.")
        }
    }

    override suspend fun importCoverImage(uri: Uri): CoverImportResult = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null && !mimeType.startsWith("image/", ignoreCase = true)) {
            return@withContext CoverImportResult.Error(
                "Invalid image format ($mimeType). Please pick a PNG or JPEG image.",
            )
        }

        val targetFile = File(coversDir, "${UUID.randomUUID()}.jpg")
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return@withContext CoverImportResult.Error("Unable to open the selected cover image.")

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return@withContext CoverImportResult.Error("Selected file is not a supported image.")
            }

            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                MAX_COVER_DIMENSION_PX,
                MAX_COVER_DIMENSION_PX,
            )
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext CoverImportResult.Error("Could not decode the selected cover image.")

            try {
                FileOutputStream(targetFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
                }
            } finally {
                bitmap.recycle()
            }

            CoverImportResult.Success(targetFile.absolutePath)
        } catch (e: Exception) {
            targetFile.delete()
            CoverImportResult.Error(e.message ?: "Failed to save cover image.")
        }
    }

    override fun deleteManagedFile(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        val cleanPath = filePath.removePrefix("file://")
        val target = File(cleanPath)
        if (!target.exists()) return
        try {
            val canonicalTarget = target.canonicalPath
            val pdfBase = pdfsDir.canonicalPath + File.separator
            val coverBase = coversDir.canonicalPath + File.separator
            if (canonicalTarget.startsWith(pdfBase) || canonicalTarget.startsWith(coverBase)) {
                target.delete()
            }
        } catch (_: Exception) {
        }
    }

    override fun calculateManagedPdfStorageBytes(): Long =
        pdfsDir.listFiles()?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            ?.sumOf { it.length() } ?: 0L

    override fun countManagedPdfFiles(): Int =
        pdfsDir.listFiles()?.count { it.isFile && it.extension.equals("pdf", ignoreCase = true) } ?: 0

    private fun extractPdfPageCount(file: File): Int {
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            renderer.pageCount
        } catch (_: Exception) {
            -1
        } finally {
            try {
                renderer?.close()
            } catch (_: Exception) {
            }
            try {
                descriptor?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
    } catch (_: Exception) {
        uri.lastPathSegment
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        private const val MAX_PDF_BYTES = 150L * 1024L * 1024L
        private const val MAX_COVER_DIMENSION_PX = 1080

        fun formatBytesLabel(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.2f MB", mb)
        }
    }
}
