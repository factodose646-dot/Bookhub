package com.example.data

import android.content.Context
import com.example.model.BookmarkNote
import org.json.JSONArray
import org.json.JSONObject

data class ReadingProgressRecord(
    val bookId: String,
    val lastPage: Int,
    val totalPages: Int,
    val lastOpenedOrder: Long,
)

interface FavoritesPersistence {
    fun loadFavoriteIds(defaultIds: Set<String>): Set<String>
    fun saveFavoriteIds(ids: Set<String>)
}

interface ReaderStatePersistence {
    fun loadProgress(defaults: Map<String, ReadingProgressRecord>): Map<String, ReadingProgressRecord>
    fun saveProgress(records: Map<String, ReadingProgressRecord>)
    fun loadBookmarks(defaults: List<BookmarkNote>): List<BookmarkNote>
    fun saveBookmarks(bookmarks: List<BookmarkNote>)
    fun loadNightMode(): Boolean
    fun saveNightMode(enabled: Boolean)
}

class InMemoryFavoritesPersistence : FavoritesPersistence {
    private var storedIds: Set<String>? = null

    override fun loadFavoriteIds(defaultIds: Set<String>): Set<String> =
        storedIds ?: defaultIds.also { storedIds = it }

    override fun saveFavoriteIds(ids: Set<String>) {
        storedIds = ids
    }
}

class InMemoryReaderStatePersistence : ReaderStatePersistence {
    private var storedProgress: Map<String, ReadingProgressRecord>? = null
    private var storedBookmarks: List<BookmarkNote>? = null
    private var nightMode: Boolean = false

    override fun loadProgress(defaults: Map<String, ReadingProgressRecord>): Map<String, ReadingProgressRecord> =
        storedProgress ?: defaults.also { storedProgress = it }

    override fun saveProgress(records: Map<String, ReadingProgressRecord>) {
        storedProgress = records
    }

    override fun loadBookmarks(defaults: List<BookmarkNote>): List<BookmarkNote> =
        storedBookmarks ?: defaults.also { storedBookmarks = it }

    override fun saveBookmarks(bookmarks: List<BookmarkNote>) {
        storedBookmarks = bookmarks
    }

    override fun loadNightMode(): Boolean = nightMode

    override fun saveNightMode(enabled: Boolean) {
        nightMode = enabled
    }
}

class SharedPreferencesFavoritesPersistence(
    context: Context,
) : FavoritesPersistence {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadFavoriteIds(defaultIds: Set<String>): Set<String> {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            saveFavoriteIds(defaultIds)
            return defaultIds
        }
        return prefs.getStringSet(KEY_FAVORITE_IDS, emptySet())?.toSet() ?: emptySet()
    }

    override fun saveFavoriteIds(ids: Set<String>) {
        prefs.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putStringSet(KEY_FAVORITE_IDS, ids.toSet())
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "bookhub_local_prefs"
        const val KEY_INITIALIZED = "favorites_initialized"
        const val KEY_FAVORITE_IDS = "favorite_book_ids"
    }
}

class SharedPreferencesReaderStatePersistence(
    context: Context,
) : ReaderStatePersistence {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadProgress(defaults: Map<String, ReadingProgressRecord>): Map<String, ReadingProgressRecord> {
        if (!prefs.getBoolean(KEY_PROGRESS_INIT, false)) {
            saveProgress(defaults)
            return defaults
        }
        val raw = prefs.getString(KEY_PROGRESS_JSON, null) ?: return emptyMap()
        return try {
            val array = JSONArray(raw)
            buildMap {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val record = ReadingProgressRecord(
                        bookId = obj.getString("bookId"),
                        lastPage = obj.getInt("lastPage"),
                        totalPages = obj.getInt("totalPages"),
                        lastOpenedOrder = obj.getLong("lastOpenedOrder"),
                    )
                    put(record.bookId, record)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override fun saveProgress(records: Map<String, ReadingProgressRecord>) {
        val array = JSONArray()
        records.values.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("bookId", record.bookId)
                    put("lastPage", record.lastPage)
                    put("totalPages", record.totalPages)
                    put("lastOpenedOrder", record.lastOpenedOrder)
                },
            )
        }
        prefs.edit()
            .putBoolean(KEY_PROGRESS_INIT, true)
            .putString(KEY_PROGRESS_JSON, array.toString())
            .apply()
    }

    override fun loadBookmarks(defaults: List<BookmarkNote>): List<BookmarkNote> {
        if (!prefs.getBoolean(KEY_BOOKMARKS_INIT, false)) {
            saveBookmarks(defaults)
            return defaults
        }
        val raw = prefs.getString(KEY_BOOKMARKS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        BookmarkNote(
                            id = obj.getString("id"),
                            bookId = obj.getString("bookId"),
                            pageNumber = obj.getInt("pageNumber"),
                            chapterTitle = obj.getString("chapterTitle"),
                            notePreview = obj.getString("notePreview"),
                            savedDateLabel = obj.getString("savedDateLabel"),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun saveBookmarks(bookmarks: List<BookmarkNote>) {
        val array = JSONArray()
        bookmarks.forEach { bm ->
            array.put(
                JSONObject().apply {
                    put("id", bm.id)
                    put("bookId", bm.bookId)
                    put("pageNumber", bm.pageNumber)
                    put("chapterTitle", bm.chapterTitle)
                    put("notePreview", bm.notePreview)
                    put("savedDateLabel", bm.savedDateLabel)
                },
            )
        }
        prefs.edit()
            .putBoolean(KEY_BOOKMARKS_INIT, true)
            .putString(KEY_BOOKMARKS_JSON, array.toString())
            .apply()
    }

    override fun loadNightMode(): Boolean = prefs.getBoolean(KEY_NIGHT_MODE, false)

    override fun saveNightMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT_MODE, enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "bookhub_reader_prefs"
        const val KEY_PROGRESS_INIT = "progress_initialized"
        const val KEY_PROGRESS_JSON = "reading_progress_json"
        const val KEY_BOOKMARKS_INIT = "bookmarks_initialized"
        const val KEY_BOOKMARKS_JSON = "pdf_bookmarks_json"
        const val KEY_NIGHT_MODE = "reader_night_mode"
    }
}
