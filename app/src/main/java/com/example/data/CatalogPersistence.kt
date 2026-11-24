package com.example.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.model.Book
import com.example.model.BookCategory
import com.example.model.CoverArtworkStyle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent catalog metadata contract for Phase 5 local books & categories.
 * Designed so Phase 6 can plug in Firebase Firestore without altering UI layers.
 */
interface CatalogPersistence {
    fun loadCategories(defaults: List<BookCategory>): List<BookCategory>
    fun saveCategories(categories: List<BookCategory>)
    fun loadBooks(defaults: List<Book>, categories: List<BookCategory>): List<Book>
    fun saveBooks(books: List<Book>)
}

class InMemoryCatalogPersistence : CatalogPersistence {
    private var storedCategories: List<BookCategory>? = null
    private var storedBooks: List<Book>? = null

    override fun loadCategories(defaults: List<BookCategory>): List<BookCategory> =
        storedCategories ?: defaults.also { storedCategories = it }

    override fun saveCategories(categories: List<BookCategory>) {
        storedCategories = categories
    }

    override fun loadBooks(defaults: List<Book>, categories: List<BookCategory>): List<Book> {
        val list = storedBooks ?: defaults.also { storedBooks = it }
        val byId = categories.associateBy { it.id }
        return list.map { book ->
            val liveCat = byId[book.category.id]
            if (liveCat != null) book.copy(category = liveCat) else book
        }
    }

    override fun saveBooks(books: List<Book>) {
        storedBooks = books
    }
}

class SharedPreferencesCatalogPersistence(
    context: Context,
) : CatalogPersistence {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadCategories(defaults: List<BookCategory>): List<BookCategory> {
        if (!prefs.getBoolean(KEY_CATEGORIES_INIT, false)) {
            saveCategories(defaults)
            return defaults
        }
        val raw = prefs.getString(KEY_CATEGORIES_JSON, null) ?: return defaults
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        BookCategory(
                            id = obj.getString("id"),
                            displayName = obj.getString("displayName"),
                            shortName = obj.getString("shortName"),
                            description = obj.optString("description", ""),
                            accentColor = Color(obj.getInt("accentColorArgb")),
                            secondaryColor = Color(obj.getInt("secondaryColorArgb")),
                            iconKey = obj.optString("iconKey", BookCategory.ICON_OTHER),
                            sortOrder = obj.optInt("sortOrder", i),
                        ),
                    )
                }
            }.sortedBy { it.sortOrder }.ifEmpty { defaults }
        } catch (_: Exception) {
            defaults
        }
    }

    override fun saveCategories(categories: List<BookCategory>) {
        val array = JSONArray()
        categories.forEachIndexed { idx, cat ->
            array.put(
                JSONObject().apply {
                    put("id", cat.id)
                    put("displayName", cat.displayName)
                    put("shortName", cat.shortName)
                    put("description", cat.description)
                    put("accentColorArgb", cat.accentColor.toArgb())
                    put("secondaryColorArgb", cat.secondaryColor.toArgb())
                    put("iconKey", cat.iconKey)
                    put("sortOrder", idx)
                },
            )
        }
        prefs.edit()
            .putBoolean(KEY_CATEGORIES_INIT, true)
            .putString(KEY_CATEGORIES_JSON, array.toString())
            .apply()
    }

    override fun loadBooks(defaults: List<Book>, categories: List<BookCategory>): List<Book> {
        if (!prefs.getBoolean(KEY_BOOKS_INIT, false)) {
            saveBooks(defaults)
            return defaults
        }
        val raw = prefs.getString(KEY_BOOKS_JSON, null) ?: return defaults
        val categoryMap = categories.associateBy { it.id }
        val fallbackCategory = categories.firstOrNull() ?: BookCategory.OTHER_BOOKS

        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val catId = obj.optString("categoryId", "")
                    val resolvedCat = categoryMap[catId]
                        ?: BookCategory.fromId(catId)
                        ?: fallbackCategory
                    val styleName = obj.optString("coverStyle", "")
                    val coverStyle = if (styleName.isNotEmpty()) {
                        CoverArtworkStyle.entries.find { it.name == styleName }
                    } else {
                        null
                    }
                    add(
                        Book(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            author = obj.optString("author", "Independent Author"),
                            category = resolvedCat,
                            fileSizeMb = obj.optString("fileSizeMb", "0.0 MB"),
                            pageCount = if (obj.has("pageCount") && !obj.isNull("pageCount")) {
                                obj.getInt("pageCount")
                            } else {
                                null
                            },
                            rating = obj.optDouble("rating", 4.8).toFloat(),
                            reviewCount = obj.optInt("reviewCount", 0),
                            description = obj.optNullableString("description"),
                            publishedYear = obj.optInt("publishedYear", 2026),
                            addedOrder = obj.optLong("addedOrder", 0L),
                            dateAdded = obj.optNullableString("dateAdded"),
                            language = obj.optString("language", "English"),
                            coverStyle = coverStyle,
                            coverPrimary = Color(obj.optInt("coverPrimaryArgb", 0xFF0B312B.toInt())),
                            coverSecondary = Color(obj.optInt("coverSecondaryArgb", 0xFF1B5B6E.toInt())),
                            coverAccent = Color(obj.optInt("coverAccentArgb", 0xFFF2C94C.toInt())),
                            coverImagePath = obj.optNullableString("coverImagePath"),
                            coverUrl = obj.optNullableString("coverUrl"),
                            coverDriveFileId = obj.optNullableString("coverDriveFileId"),
                            isFeatured = obj.optBoolean("isFeatured", false),
                            isPopular = obj.optBoolean("isPopular", false),
                            isRecent = obj.optBoolean("isRecent", false),
                            isPublished = obj.optBoolean("isPublished", true),
                            isSyncedToCloud = obj.optBoolean("isSyncedToCloud", false),
                            isDownloadedPreview = obj.optBoolean("isDownloadedPreview", false),
                            pdfAssetPath = obj.optNullableString("pdfAssetPath"),
                            pdfSourceUrl = obj.optNullableString("pdfSourceUrl"),
                            googleDriveFileId = obj.optNullableString("googleDriveFileId"),
                            localPdfBytes = obj.optLong("localPdfBytes", 0L),
                            isAdminUploaded = obj.optBoolean("isAdminUploaded", false),
                            uploadTimestamp = obj.optLong("uploadTimestamp", 0L),
                            sampleExcerpt = obj.optNullableString("sampleExcerpt"),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            defaults
        }
    }

    override fun saveBooks(books: List<Book>) {
        val array = JSONArray()
        books.forEach { book ->
            array.put(
                JSONObject().apply {
                    put("id", book.id)
                    put("title", book.title)
                    put("author", book.author)
                    put("categoryId", book.category.id)
                    put("fileSizeMb", book.fileSizeMb)
                    put("pageCount", book.pageCount ?: JSONObject.NULL)
                    put("rating", book.rating.toDouble())
                    put("reviewCount", book.reviewCount)
                    put("description", book.description ?: JSONObject.NULL)
                    put("publishedYear", book.publishedYear)
                    put("addedOrder", book.addedOrder)
                    put("dateAdded", book.dateAdded ?: JSONObject.NULL)
                    put("language", book.language)
                    put("coverStyle", book.coverStyle?.name ?: JSONObject.NULL)
                    put("coverPrimaryArgb", book.coverPrimary.toArgb())
                    put("coverSecondaryArgb", book.coverSecondary.toArgb())
                    put("coverAccentArgb", book.coverAccent.toArgb())
                    put("coverImagePath", book.coverImagePath ?: JSONObject.NULL)
                    put("coverUrl", book.coverUrl ?: JSONObject.NULL)
                    put("coverDriveFileId", book.coverDriveFileId ?: JSONObject.NULL)
                    put("isFeatured", book.isFeatured)
                    put("isPopular", book.isPopular)
                    put("isRecent", book.isRecent)
                    put("isPublished", book.isPublished)
                    put("isSyncedToCloud", book.isSyncedToCloud)
                    put("isDownloadedPreview", book.isDownloadedPreview)
                    put("pdfAssetPath", book.pdfAssetPath ?: JSONObject.NULL)
                    put("pdfSourceUrl", book.pdfSourceUrl ?: JSONObject.NULL)
                    put("googleDriveFileId", book.googleDriveFileId ?: JSONObject.NULL)
                    put("localPdfBytes", book.localPdfBytes)
                    put("isAdminUploaded", book.isAdminUploaded)
                    put("uploadTimestamp", book.uploadTimestamp)
                    put("sampleExcerpt", book.sampleExcerpt ?: JSONObject.NULL)
                },
            )
        }
        prefs.edit()
            .putBoolean(KEY_BOOKS_INIT, true)
            .putString(KEY_BOOKS_JSON, array.toString())
            .apply()
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private companion object {
        const val PREFS_NAME = "bookhub_catalog_prefs"
        const val KEY_CATEGORIES_INIT = "categories_initialized"
        const val KEY_CATEGORIES_JSON = "categories_json"
        const val KEY_BOOKS_INIT = "books_initialized"
        const val KEY_BOOKS_JSON = "books_json"
    }
}
