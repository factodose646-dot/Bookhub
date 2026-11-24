package com.example.model

import androidx.compose.ui.graphics.Color

data class BookCategory(
    val id: String,
    val displayName: String,
    val shortName: String,
    val description: String,
    val accentColor: Color,
    val secondaryColor: Color,
    val iconKey: String = ICON_OTHER,
    val sortOrder: Int = 0,
) {
    companion object {
        const val ICON_STORIES = "stories"
        const val ICON_SCIENCE = "science"
        const val ICON_FICTION = "fiction"
        const val ICON_HISTORY = "history"
        const val ICON_MOTIVATION = "motivation"
        const val ICON_TECH = "tech"
        const val ICON_ISLAMIC = "islamic"
        const val ICON_OTHER = "other"

        val STORIES_NOVELS = BookCategory(
            id = "stories_novels",
            displayName = "Stories & Novels",
            shortName = "Stories &\nNovels",
            description = "Literary classics, contemporary dramas, and gripping sagas",
            accentColor = Color(0xFFE07A2E),
            secondaryColor = Color(0xFF6B2D10),
            iconKey = ICON_STORIES,
            sortOrder = 0,
        )
        val SCIENCE_KNOWLEDGE = BookCategory(
            id = "science_knowledge",
            displayName = "Science & Knowledge",
            shortName = "Science &\nKnowledge",
            description = "Physics, astronomy, biology, and modern discoveries",
            accentColor = Color(0xFF1B76A8),
            secondaryColor = Color(0xFF0A324D),
            iconKey = ICON_SCIENCE,
            sortOrder = 1,
        )
        val FICTIONAL_STORIES = BookCategory(
            id = "fictional_stories",
            displayName = "Fictional Stories",
            shortName = "Fictional\nStories",
            description = "Worlds of adventure, mystery, and speculative imagination",
            accentColor = Color(0xFF176B4C),
            secondaryColor = Color(0xFF092E20),
            iconKey = ICON_FICTION,
            sortOrder = 2,
        )
        val HISTORY_BIOGRAPHY = BookCategory(
            id = "history_biography",
            displayName = "History & Biography",
            shortName = "History &\nBiography",
            description = "Civilizations, historical turning points, and remarkable lives",
            accentColor = Color(0xFF8C5327),
            secondaryColor = Color(0xFF3E210D),
            iconKey = ICON_HISTORY,
            sortOrder = 3,
        )
        val MOTIVATIONAL = BookCategory(
            id = "motivational",
            displayName = "Motivational Books",
            shortName = "Motivational\nBooks",
            description = "Habits, mindfulness, leadership, and personal clarity",
            accentColor = Color(0xFFB8437A),
            secondaryColor = Color(0xFF4F1732),
            iconKey = ICON_MOTIVATION,
            sortOrder = 4,
        )
        val TECH_EDUCATION = BookCategory(
            id = "tech_education",
            displayName = "Technology & Education",
            shortName = "Tech &\nEducation",
            description = "Software craft, mathematics, design systems, and study guides",
            accentColor = Color(0xFF4B56B2),
            secondaryColor = Color(0xFF1C2252),
            iconKey = ICON_TECH,
            sortOrder = 5,
        )
        val ISLAMIC_BOOKS = BookCategory(
            id = "islamic_books",
            displayName = "Islamic Books",
            shortName = "Islamic\nBooks",
            description = "Heritage, ethics, calligraphy, and classical scholarship",
            accentColor = Color(0xFF157A74),
            secondaryColor = Color(0xFF093835),
            iconKey = ICON_ISLAMIC,
            sortOrder = 6,
        )
        val OTHER_BOOKS = BookCategory(
            id = "other_books",
            displayName = "Other Books",
            shortName = "Other\nBooks",
            description = "Essays, travelogues, field notebooks, and curated guides",
            accentColor = Color(0xFFD1961F),
            secondaryColor = Color(0xFF523808),
            iconKey = ICON_OTHER,
            sortOrder = 7,
        )

        val entries: List<BookCategory> = listOf(
            STORIES_NOVELS,
            SCIENCE_KNOWLEDGE,
            FICTIONAL_STORIES,
            HISTORY_BIOGRAPHY,
            MOTIVATIONAL,
            TECH_EDUCATION,
            ISLAMIC_BOOKS,
            OTHER_BOOKS,
        )

        fun fromId(id: String): BookCategory? = entries.find { it.id == id }
    }
}

enum class CoverArtworkStyle {
    MOUNTAIN_HORIZON,
    COSMIC_ORBIT,
    FOREST_ARCH,
    CITADEL_NIGHT,
    SUNRISE_PEAK,
    CIRCUIT_MATRIX,
    GEOMETRIC_LANTERN,
    CLASSIC_FOLIO,
}

enum class BookSortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    A_TO_Z("A-Z"),
}

enum class BookListingType(val id: String, val title: String, val subtitle: String) {
    CATEGORY("category", "Category", "Browse PDF editions"),
    FEATURED("featured", "Featured Books", "Editor-curated highlights"),
    POPULAR("popular", "Popular Books", "Most read across BookHub"),
    RECENT("recent", "Recently Added", "Fresh PDF additions"),
    CONTINUE_READING("continue_reading", "Continue Reading", "Pick up where you left off"),
    ALL("all", "All Books", "Complete local catalog"),
    ;

    companion object {
        fun fromId(id: String): BookListingType = entries.find { it.id == id } ?: ALL
    }
}

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val category: BookCategory,
    val fileSizeMb: String,
    val pageCount: Int?,
    val rating: Float,
    val reviewCount: Int,
    val description: String?,
    val publishedYear: Int,
    val addedOrder: Long,
    val dateAdded: String? = null,
    val language: String,
    val coverStyle: CoverArtworkStyle?,
    val coverPrimary: Color,
    val coverSecondary: Color,
    val coverAccent: Color,
    val coverImagePath: String? = null,
    val coverUrl: String? = null,
    val coverDriveFileId: String? = null,
    val isFeatured: Boolean = false,
    val isPopular: Boolean = false,
    val isRecent: Boolean = false,
    val isPublished: Boolean = true,
    val isSyncedToCloud: Boolean = false,
    val readingProgress: Float = 0f,
    val lastReadPage: Int = 0,
    val lastOpenedOrder: Long = 0L,
    val isFavorite: Boolean = false,
    val isBookmarked: Boolean = false,
    val isInLibrary: Boolean = false,
    val isDownloadedPreview: Boolean = false,
    val pdfAssetPath: String? = null,
    val pdfSourceUrl: String? = null,
    val googleDriveFileId: String? = null,
    val localPdfBytes: Long = 0L,
    val isAdminUploaded: Boolean = false,
    val uploadTimestamp: Long = 0L,
    val sampleExcerpt: String?,
)

enum class PdfFitMode(val label: String) {
    FIT_WIDTH("Fit Width"),
    FIT_PAGE("Fit Page"),
    CUSTOM("Custom Zoom"),
}

data class FeaturedBannerItem(
    val id: String,
    val badge: String,
    val headline: String,
    val subtitle: String,
    val targetBookId: String,
    val gradientStart: Color,
    val gradientEnd: Color,
)

data class BookmarkNote(
    val id: String,
    val bookId: String,
    val pageNumber: Int,
    val chapterTitle: String,
    val notePreview: String,
    val savedDateLabel: String,
)

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timeLabel: String,
    val isUnread: Boolean = true,
)

enum class LibraryFilter(val label: String) {
    ALL("All Books"),
    FAVORITES("Favorites"),
    BOOKMARKS("Bookmarks"),
    HISTORY("Reading History"),
}
