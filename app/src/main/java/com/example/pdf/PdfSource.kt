package com.example.pdf

import com.example.model.Book

sealed interface PdfSource {
    data class LocalAsset(val assetPath: String) : PdfSource
    data class LocalFile(val filePath: String) : PdfSource
    data class RemoteHttps(val url: String) : PdfSource
    data class GoogleDriveRef(val fileId: String, val originalUrl: String) : PdfSource
}

sealed class PdfLoadError(val title: String, val message: String) {
    data object MissingSource : PdfLoadError(
        title = "PDF File Not Available",
        message = "This book does not have a PDF file linked yet. Once a local or cloud PDF source is attached, you can read it here.",
    )

    data object InvalidPdfFormat : PdfLoadError(
        title = "Invalid PDF Document",
        message = "The selected file could not be opened as a valid PDF document. It may be corrupted or in an unsupported format.",
    )

    data object NetworkUnavailable : PdfLoadError(
        title = "Connection Interrupted",
        message = "Unable to load the remote PDF due to a network interruption. Check your internet connection and try again.",
    )

    data class RemoteLoadFailed(val statusCode: Int?, val detail: String) : PdfLoadError(
        title = "Unable to Load Remote PDF",
        message = if (statusCode != null) {
            "Server returned HTTP $statusCode while fetching the PDF ($detail)."
        } else {
            "Could not load the remote PDF file: $detail"
        },
    )

    data object GoogleDriveNotConnected : PdfLoadError(
        title = "Google Drive Unavailable",
        message = "This Google Drive PDF requires a valid File ID or internet connection to stream and cache.",
    )

    data class GoogleDriveAccessDenied(val detail: String) : PdfLoadError(
        title = "Google Drive Access Denied",
        message = detail,
    )

    data class FileReadFailed(val detail: String) : PdfLoadError(
        title = "Could Not Open PDF",
        message = detail,
    )
}

class PdfLoadException(val error: PdfLoadError) : Exception(error.message)

object PdfValidator {
    private val PDF_MAGIC = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte(), '-'.code.toByte())

    fun hasValidPdfHeader(bytes: ByteArray): Boolean {
        if (bytes.size < PDF_MAGIC.size) return false
        val scanLimit = minOf(bytes.size - PDF_MAGIC.size, 1024)
        for (start in 0..scanLimit) {
            var matched = true
            for (i in PDF_MAGIC.indices) {
                if (bytes[start + i] != PDF_MAGIC[i]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }
}

object PdfSourceResolver {
    private val DRIVE_FILE_REGEX = Regex("""drive\.google\.com/file/d/([a-zA-Z0-9_-]+)""")
    private val DRIVE_OPEN_REGEX = Regex("""drive\.google\.com/open\?id=([a-zA-Z0-9_-]+)""")
    private val DOCS_REGEX = Regex("""docs\.google\.com/.*/d/([a-zA-Z0-9_-]+)""")

    fun resolve(book: Book): PdfSource? {
        if (!book.googleDriveFileId.isNullOrBlank()) {
            return PdfSource.GoogleDriveRef(
                fileId = book.googleDriveFileId,
                originalUrl = book.pdfSourceUrl ?: "",
            )
        }

        val remote = book.pdfSourceUrl?.trim()
        if (!remote.isNullOrEmpty()) {
            extractGoogleDriveFileId(remote)?.let { driveId ->
                return PdfSource.GoogleDriveRef(fileId = driveId, originalUrl = remote)
            }
            if (remote.startsWith("https://", ignoreCase = true) ||
                remote.startsWith("http://", ignoreCase = true)
            ) {
                return PdfSource.RemoteHttps(remote)
            }
            if (remote.startsWith("file://", ignoreCase = true)) {
                return PdfSource.LocalFile(remote.removePrefix("file://"))
            }
        }

        val asset = book.pdfAssetPath?.trim()
        if (!asset.isNullOrEmpty()) {
            return PdfSource.LocalAsset(asset)
        }

        return null
    }

    fun extractGoogleDriveFileId(url: String): String? {
        DRIVE_FILE_REGEX.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        DRIVE_OPEN_REGEX.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        DOCS_REGEX.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        if (url.contains("drive.google.com", ignoreCase = true)) {
            return "unknown_drive_ref"
        }
        return null
    }
}
