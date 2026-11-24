package com.example.cloud

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.model.Book
import com.example.model.BookCategory
import com.example.model.CoverArtworkStyle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Keep
@IgnoreExtraProperties
data class FirestoreCategoryDto(
    @DocumentId val id: String = "",
    val name: String = "",
    val shortName: String? = null,
    val description: String? = null,
    val icon: String = BookCategory.ICON_OTHER,
    val color: Long = 0xFFD1961FL,
    val secondaryColor: Long = 0xFF523808L,
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toDomain(): BookCategory = BookCategory(
        id = id,
        displayName = name.ifBlank { "Category" },
        shortName = shortName?.takeIf { it.isNotBlank() } ?: name.ifBlank { "Category" },
        description = description.orEmpty(),
        accentColor = Color(color.toInt()),
        secondaryColor = Color(secondaryColor.toInt()),
        iconKey = icon.ifBlank { BookCategory.ICON_OTHER },
        sortOrder = sortOrder.toInt(),
    )

    companion object {
        fun toCreateMap(category: BookCategory): Map<String, Any> {
            val map = hashMapOf<String, Any?>(
                "name" to category.displayName.trim().take(80),
                "shortName" to category.shortName.trim().take(80),
                "description" to category.description.trim().take(400).ifEmpty { null },
                "icon" to category.iconKey.take(40).ifBlank { BookCategory.ICON_OTHER },
                "color" to category.accentColor.toArgb().toLong(),
                "secondaryColor" to category.secondaryColor.toArgb().toLong(),
                "sortOrder" to category.sortOrder.coerceIn(0, 1000).toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            @Suppress("UNCHECKED_CAST")
            return map.filterValues { it != null } as Map<String, Any>
        }

        fun toUpdateMap(category: BookCategory): Map<String, Any> {
            val map = hashMapOf<String, Any?>(
                "name" to category.displayName.trim().take(80),
                "shortName" to category.shortName.trim().take(80),
                "description" to category.description.trim().take(400),
                "icon" to category.iconKey.take(40).ifBlank { BookCategory.ICON_OTHER },
                "color" to category.accentColor.toArgb().toLong(),
                "secondaryColor" to category.secondaryColor.toArgb().toLong(),
                "sortOrder" to category.sortOrder.coerceIn(0, 1000).toLong(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            @Suppress("UNCHECKED_CAST")
            return map.filterValues { it != null } as Map<String, Any>
        }
    }
}

@Keep
@IgnoreExtraProperties
data class FirestoreBookDto(
    @DocumentId val id: String = "",
    val title: String = "",
    val author: String = "Independent Author",
    val description: String? = null,
    val categoryId: String = "",
    val coverUrl: String? = null,
    val driveFileId: String? = null,
    val coverDriveFileId: String? = null,
    val pdfUrl: String? = null,
    val pdfAssetPath: String? = null,
    val fileSize: String = "0 KB",
    val fileSizeBytes: Long = 0L,
    val pageCount: Long? = null,
    val language: String = "English",
    val publishedYear: Long = 2026L,
    val addedOrder: Long = 0L,
    val dateAdded: String? = null,
    val coverStyle: String? = null,
    val coverPrimaryArgb: Long = 0xFF0B312BL,
    val coverSecondaryArgb: Long = 0xFF1B5B6EL,
    val coverAccentArgb: Long = 0xFFF2C94CL,
    val featured: Boolean = false,
    val popular: Boolean = false,
    val recent: Boolean = false,
    val published: Boolean = true,
    val rating: Double = 5.0,
    val reviewCount: Long = 0L,
    val sampleExcerpt: String? = null,
    val createdByUid: String = "",
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toDomain(categoriesById: Map<String, BookCategory>): Book {
        val resolvedCategory = categoriesById[categoryId]
            ?: BookCategory.fromId(categoryId)
            ?: categoriesById.values.firstOrNull()
            ?: BookCategory.OTHER_BOOKS

        val safeCoverStyle = coverStyle?.let { raw ->
            CoverArtworkStyle.entries.firstOrNull { it.name == raw }
        }
        val effectiveCreatedMillis = createdAt?.toDate()?.time ?: 0L
        val formattedDate = dateAdded?.takeIf { it.isNotBlank() }
            ?: createdAt?.let {
                SimpleDateFormat("MMM dd, yyyy", Locale.US).format(it.toDate())
            }

        // Preserve local asset fallback only when no cloud file overrides it, or keep both safely
        val cleanPdfUrl = pdfUrl?.takeIf { it.isNotBlank() && !it.startsWith("file://") }

        return Book(
            id = id,
            title = title.ifBlank { "Untitled Book" },
            author = author.ifBlank { "Independent Author" },
            category = resolvedCategory,
            fileSizeMb = fileSize.ifBlank { "0 KB" },
            pageCount = pageCount?.toInt()?.takeIf { it > 0 },
            rating = rating.toFloat().coerceIn(0f, 5f),
            reviewCount = reviewCount.toInt().coerceAtLeast(0),
            description = description?.takeIf { it.isNotBlank() },
            publishedYear = publishedYear.toInt().coerceIn(1000, 2200),
            addedOrder = if (addedOrder > 0L) addedOrder else effectiveCreatedMillis,
            dateAdded = formattedDate,
            language = language.ifBlank { "English" },
            coverStyle = safeCoverStyle ?: CoverArtworkStyle.CLASSIC_FOLIO,
            coverPrimary = Color(coverPrimaryArgb.toInt()),
            coverSecondary = Color(coverSecondaryArgb.toInt()),
            coverAccent = Color(coverAccentArgb.toInt()),
            coverImagePath = null,
            coverUrl = coverUrl?.takeIf { it.isNotBlank() },
            coverDriveFileId = coverDriveFileId?.takeIf { it.isNotBlank() },
            isFeatured = featured,
            isPopular = popular,
            isRecent = recent,
            isPublished = published,
            isSyncedToCloud = true,
            pdfAssetPath = pdfAssetPath?.takeIf { it.isNotBlank() },
            pdfSourceUrl = cleanPdfUrl,
            googleDriveFileId = driveFileId?.takeIf { it.isNotBlank() },
            localPdfBytes = fileSizeBytes.coerceAtLeast(0L),
            isAdminUploaded = true,
            uploadTimestamp = updatedAt?.toDate()?.time ?: effectiveCreatedMillis,
            sampleExcerpt = sampleExcerpt?.takeIf { it.isNotBlank() },
        )
    }

    companion object {
        fun toCreateMap(book: Book, adminUid: String): Map<String, Any> {
            val cloudPdfUrl = book.pdfSourceUrl?.takeIf {
                it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
            }
            val map = hashMapOf<String, Any?>(
                "title" to book.title.trim().take(200).ifEmpty { "Untitled Book" },
                "author" to book.author.trim().take(120).ifEmpty { "Independent Author" },
                "description" to book.description?.trim()?.take(4000)?.ifEmpty { null },
                "categoryId" to book.category.id,
                "coverUrl" to book.coverUrl?.trim()?.take(1000)?.ifEmpty { null },
                "driveFileId" to book.googleDriveFileId?.trim()?.take(200)?.ifEmpty { null },
                "coverDriveFileId" to book.coverDriveFileId?.trim()?.take(200)?.ifEmpty { null },
                "pdfUrl" to cloudPdfUrl?.take(1000),
                "pdfAssetPath" to book.pdfAssetPath?.trim()?.take(240)?.ifEmpty { null },
                "fileSize" to book.fileSizeMb.trim().take(40).ifEmpty { "0 KB" },
                "fileSizeBytes" to book.localPdfBytes.coerceAtLeast(0L),
                "pageCount" to book.pageCount?.coerceIn(0, 50000)?.toLong(),
                "language" to book.language.trim().take(50).ifEmpty { "English" },
                "publishedYear" to book.publishedYear.coerceIn(1000, 2200).toLong(),
                "addedOrder" to book.addedOrder.coerceAtLeast(0L),
                "dateAdded" to (book.dateAdded?.trim()?.take(40)
                    ?: SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())),
                "coverStyle" to book.coverStyle?.name,
                "coverPrimaryArgb" to book.coverPrimary.toArgb().toLong(),
                "coverSecondaryArgb" to book.coverSecondary.toArgb().toLong(),
                "coverAccentArgb" to book.coverAccent.toArgb().toLong(),
                "featured" to book.isFeatured,
                "popular" to book.isPopular,
                "recent" to book.isRecent,
                "published" to book.isPublished,
                "rating" to book.rating.toDouble().coerceIn(0.0, 5.0),
                "reviewCount" to book.reviewCount.coerceAtLeast(0).toLong(),
                "sampleExcerpt" to book.sampleExcerpt?.trim()?.take(500)?.ifEmpty { null },
                "createdByUid" to adminUid,
                "updatedByUid" to adminUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            @Suppress("UNCHECKED_CAST")
            return map.filterValues { it != null } as Map<String, Any>
        }

        fun toUpdateMap(book: Book, adminUid: String): Map<String, Any> {
            val cloudPdfUrl = book.pdfSourceUrl?.takeIf {
                it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
            }
            val map = hashMapOf<String, Any>(
                "title" to book.title.trim().take(200).ifEmpty { "Untitled Book" },
                "author" to book.author.trim().take(120).ifEmpty { "Independent Author" },
                "description" to (book.description?.trim()?.take(4000) ?: ""),
                "categoryId" to book.category.id,
                "coverUrl" to (book.coverUrl?.trim()?.take(1000)?.ifEmpty { null } ?: FieldValue.delete()),
                "driveFileId" to (book.googleDriveFileId?.trim()?.take(200)?.ifEmpty { null } ?: FieldValue.delete()),
                "coverDriveFileId" to (book.coverDriveFileId?.trim()?.take(200)?.ifEmpty { null } ?: FieldValue.delete()),
                "pdfUrl" to (cloudPdfUrl?.take(1000) ?: FieldValue.delete()),
                "pdfAssetPath" to (book.pdfAssetPath?.trim()?.take(240)?.ifEmpty { null } ?: FieldValue.delete()),
                "fileSize" to book.fileSizeMb.trim().take(40).ifEmpty { "0 KB" },
                "fileSizeBytes" to book.localPdfBytes.coerceAtLeast(0L),
                "pageCount" to (book.pageCount?.coerceIn(0, 50000)?.toLong() ?: FieldValue.delete()),
                "language" to book.language.trim().take(50).ifEmpty { "English" },
                "publishedYear" to book.publishedYear.coerceIn(1000, 2200).toLong(),
                "addedOrder" to book.addedOrder.coerceAtLeast(0L),
                "dateAdded" to (book.dateAdded?.trim()?.take(40)?.ifEmpty { null } ?: FieldValue.delete()),
                "coverStyle" to (book.coverStyle?.name ?: FieldValue.delete()),
                "coverPrimaryArgb" to book.coverPrimary.toArgb().toLong(),
                "coverSecondaryArgb" to book.coverSecondary.toArgb().toLong(),
                "coverAccentArgb" to book.coverAccent.toArgb().toLong(),
                "featured" to book.isFeatured,
                "popular" to book.isPopular,
                "recent" to book.isRecent,
                "published" to book.isPublished,
                "rating" to book.rating.toDouble().coerceIn(0.0, 5.0),
                "reviewCount" to book.reviewCount.coerceAtLeast(0).toLong(),
                "sampleExcerpt" to (book.sampleExcerpt?.trim()?.take(500)?.ifEmpty { null } ?: FieldValue.delete()),
                "updatedByUid" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            return map
        }
    }
}
