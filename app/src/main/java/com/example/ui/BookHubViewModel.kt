package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AdminAuthGateway
import com.example.auth.AdminAuthResult
import com.example.auth.AdminAuthStatus
import com.example.auth.AdminCloudVerification
import com.example.auth.AdminSession
import com.example.auth.CloudAccountProfile
import com.example.auth.FirebaseAuthManager
import com.example.auth.GoogleSignInOutcome
import com.example.auth.InMemoryAdminAuthPersistence
import com.example.auth.LocalPasscodeAdminAuthGateway
import com.example.auth.authStateFlow
import com.example.auth.toCloudAccountProfile
import com.example.cloud.DriveAuthStatus
import com.example.cloud.DriveUploadedFile
import com.example.cloud.FirestoreBookDto
import com.example.cloud.FirestoreCatalogRepository
import com.example.cloud.FirestoreCategoryDto
import com.example.cloud.GoogleDriveGateway
import com.example.data.BookCatalogQueries
import com.example.data.BookCatalogRepository
import com.example.data.BookSampleData
import com.example.data.BookStorageGateway
import com.example.data.CatalogPersistence
import com.example.data.CoverImportResult
import com.example.data.FavoritesPersistence
import com.example.data.InMemoryCatalogPersistence
import com.example.data.InMemoryFavoritesPersistence
import com.example.data.InMemoryReaderStatePersistence
import com.example.data.LocalBookCatalogRepository
import com.example.data.LocalBookStorageGateway
import com.example.data.NoOpBookStorageGateway
import com.example.data.PdfImportResult
import com.example.data.ReaderStatePersistence
import com.example.data.ReadingProgressRecord
import com.example.model.Book
import com.example.model.BookCategory
import com.example.model.BookListingType
import com.example.model.BookSortOption
import com.example.model.BookmarkNote
import com.example.model.CoverArtworkStyle
import com.example.model.FeaturedBannerItem
import com.example.model.LibraryFilter
import com.example.model.NotificationItem
import com.example.pdf.PdfDocumentEngine
import com.example.pdf.PdfSourceResolver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PendingDriveBookAssets(
    val localPdfPath: String? = null,
    val pdfUploaded: DriveUploadedFile? = null,
    val localCoverPath: String? = null,
    val coverUploaded: DriveUploadedFile? = null,
)

object CatalogCloudMerger {
    fun mergeCloudCategories(
        existingCategories: List<BookCategory>,
        cloudDtos: List<FirestoreCategoryDto>,
        localBooks: List<Book>,
    ): List<BookCategory> {
        if (cloudDtos.isEmpty()) return existingCategories
        val cloudCats = cloudDtos.map { it.toDomain() }
        val cloudIds = cloudCats.map { it.id }.toSet()
        val referencedLocalOnlyIds = localBooks
            .filter { !it.isSyncedToCloud }
            .map { it.category.id }
            .toSet()
        val preservedLocalCats = existingCategories.filter {
            it.id !in cloudIds && it.id in referencedLocalOnlyIds
        }
        return (cloudCats + preservedLocalCats).sortedWith(
            compareBy<BookCategory> { it.sortOrder }.thenBy { it.displayName.lowercase() },
        )
    }

    fun mergeCloudBooksPreservingReaderState(
        existingBooks: List<Book>,
        cloudDtos: List<FirestoreBookDto>,
        categories: List<BookCategory>,
    ): List<Book> {
        val categoriesById = categories.associateBy { it.id }
        val existingById = existingBooks.associateBy { it.id }
        val cloudBooks = cloudDtos.map { dto ->
            val domain = dto.toDomain(categoriesById)
            val prev = existingById[domain.id]
            if (prev != null) {
                domain.copy(
                    isFavorite = prev.isFavorite,
                    isBookmarked = prev.isBookmarked,
                    isInLibrary = prev.isInLibrary,
                    lastReadPage = prev.lastReadPage,
                    readingProgress = prev.readingProgress,
                    lastOpenedOrder = prev.lastOpenedOrder,
                    pageCount = domain.pageCount ?: prev.pageCount,
                    pdfAssetPath = domain.pdfAssetPath ?: prev.pdfAssetPath,
                    coverImagePath = if (domain.coverUrl.isNullOrBlank()) prev.coverImagePath else null,
                )
            } else {
                domain
            }
        }
        val cloudIds = cloudBooks.map { it.id }.toSet()
        val preservedLocalOnly = existingBooks
            .filter { !it.isSyncedToCloud && it.id !in cloudIds }
            .map { localBook ->
                val latestCategory = categoriesById[localBook.category.id] ?: localBook.category
                if (latestCategory != localBook.category) localBook.copy(category = latestCategory) else localBook
            }
        return cloudBooks + preservedLocalOnly
    }
}

data class BookHubUiState(
    val categories: List<BookCategory> = BookCategory.entries,
    val books: List<Book> = BookSampleData.books,
    val featuredBanners: List<FeaturedBannerItem> = BookSampleData.featuredBanners,
    val bookmarkNotes: List<BookmarkNote> = BookSampleData.initialBookmarks,
    val notifications: List<NotificationItem> = BookSampleData.initialNotifications,
    val homeSearchQuery: String = "",
    val isHomeSearchActive: Boolean = false,
    val categorySearchQuery: String = "",
    val listingSearchQuery: String = "",
    val listingSortOption: BookSortOption = BookSortOption.NEWEST,
    val isListingGridLayout: Boolean = true,
    val librarySearchQuery: String = "",
    val libraryFilter: LibraryFilter = LibraryFilter.ALL,
    val readerNightMode: Boolean = false,
    val nextOpenSequence: Long = 200L,
    val adminSession: AdminSession? = null,
    val adminAuthStatus: AdminAuthStatus = AdminAuthStatus.NeedsFirstTimeSetup,
    val adminAuthError: String? = null,
    val statusBannerMessage: String? = null,
    val useCloudAdminAuth: Boolean = false,
    val cloudAccount: CloudAccountProfile? = null,
    val adminCloudVerification: AdminCloudVerification = AdminCloudVerification.NotSignedIn,
    val isAdminRoleChecking: Boolean = false,
    val isDriveAuthorized: Boolean = false,
    val isCloudSyncActive: Boolean = false,
    val cloudSyncError: String? = null,
    val cloudUploadProgress: Float? = null,
    val cloudUploadStage: String? = null,
    val isMigratingCatalog: Boolean = false,
) {
    val isAdminAuthenticated: Boolean
        get() = if (useCloudAdminAuth) {
            adminCloudVerification is AdminCloudVerification.Verified
        } else {
            adminSession != null && adminAuthStatus is AdminAuthStatus.Authenticated
        }

    val publishedBooks: List<Book>
        get() = books.filter { it.isPublished }

    val unreadNotificationCount: Int
        get() = notifications.count { it.isUnread }

    val featuredBooks: List<Book>
        get() = publishedBooks.filter { it.isFeatured }

    val continueReadingBooks: List<Book>
        get() = publishedBooks
            .filter { it.readingProgress > 0f || it.lastOpenedOrder > 0L }
            .sortedByDescending { it.lastOpenedOrder }

    val popularBooks: List<Book>
        get() = publishedBooks.filter { it.isPopular }

    val recentBooks: List<Book>
        get() = publishedBooks.filter { it.isRecent }
            .sortedByDescending { it.addedOrder }

    val recentlyAddedBooksForAdmin: List<Book>
        get() = books.sortedWith(
            compareByDescending<Book> { it.uploadTimestamp }
                .thenByDescending { it.addedOrder },
        )

    val bookmarkedBooks: List<Book>
        get() = publishedBooks.filter { it.isBookmarked }

    val favoriteBooks: List<Book>
        get() = publishedBooks.filter { it.isFavorite }

    val readingHistoryBooks: List<Book>
        get() = publishedBooks
            .filter { it.lastOpenedOrder > 0L || it.readingProgress > 0f }
            .sortedByDescending { it.lastOpenedOrder }

    val libraryBooks: List<Book>
        get() {
            val base = when (libraryFilter) {
                LibraryFilter.ALL -> publishedBooks.filter { it.isInLibrary }
                LibraryFilter.FAVORITES -> publishedBooks.filter { it.isFavorite }
                LibraryFilter.BOOKMARKS -> publishedBooks.filter { it.isBookmarked }
                LibraryFilter.HISTORY -> readingHistoryBooks
            }
            return BookCatalogQueries.filterByQuery(base, librarySearchQuery)
        }

    fun getBooksForListing(
        listingType: BookListingType,
        category: BookCategory?,
    ): List<Book> = BookCatalogQueries.filterForListing(
        books = publishedBooks,
        listingType = listingType,
        category = category,
        query = listingSearchQuery,
        sortOption = listingSortOption,
    )

    fun getRelatedBooks(book: Book): List<Book> =
        BookCatalogQueries.getRelatedBooks(publishedBooks, book)

    fun getBookmarksForBook(bookId: String): List<BookmarkNote> =
        bookmarkNotes.filter { it.bookId == bookId }.sortedBy { it.pageNumber }

    fun isPageBookmarked(bookId: String, pageNumber: Int): Boolean =
        bookmarkNotes.any { it.bookId == bookId && it.pageNumber == pageNumber }

    fun findCategoryById(id: String): BookCategory? = categories.find { it.id == id }

    val totalPdfFilesCount: Int
        get() = books.count { PdfSourceResolver.resolve(it) != null }

    val adminUploadedPdfBytes: Long
        get() = books.sumOf { it.localPdfBytes }

    val totalCatalogPages: Int
        get() = books.sumOf { it.pageCount ?: 180 }

    val totalCatalogSizeMb: Double
        get() = books.sumOf { parseSizeMb(it.fileSizeMb, it.localPdfBytes) }

    val cloudSyncedBooksCount: Int
        get() = books.count { it.isSyncedToCloud }

    val localUnsyncedBooksCount: Int
        get() = books.count { !it.isSyncedToCloud }

    private fun parseSizeMb(label: String, exactBytes: Long): Double {
        if (exactBytes > 0L) return exactBytes.toDouble() / (1024.0 * 1024.0)
        val clean = label.trim().uppercase(Locale.US)
        return when {
            clean.endsWith("KB") -> (clean.removeSuffix("KB").trim().toDoubleOrNull() ?: 0.0) / 1024.0
            clean.endsWith("MB") -> clean.removeSuffix("MB").trim().toDoubleOrNull() ?: 1.5
            else -> 1.5
        }
    }
}

class BookHubViewModel(
    private val repository: BookCatalogRepository = LocalBookCatalogRepository(),
    private val favoritesPersistence: FavoritesPersistence = InMemoryFavoritesPersistence(),
    private val readerPersistence: ReaderStatePersistence = InMemoryReaderStatePersistence(),
    private val catalogPersistence: CatalogPersistence = InMemoryCatalogPersistence(),
    private val storageGateway: BookStorageGateway = NoOpBookStorageGateway(),
    private val adminAuthGateway: AdminAuthGateway = LocalPasscodeAdminAuthGateway(InMemoryAdminAuthPersistence()),
    private val firestoreCatalogRepository: FirestoreCatalogRepository? = null,
    private val googleDriveGateway: GoogleDriveGateway? = null,
    private val firebaseAuth: FirebaseAuth? = null,
    private val firestoreDb: FirebaseFirestore? = null,
    private val applicationContext: Context? = null,
) : ViewModel() {

    private val isCloudConfigured: Boolean =
        firestoreCatalogRepository != null && firebaseAuth != null && firestoreDb != null

    private val _uiState = MutableStateFlow(createInitialUiState())
    val uiState: StateFlow<BookHubUiState> = _uiState.asStateFlow()

    private var authObserverJob: Job? = null
    private var categoriesListenerJob: Job? = null
    private var booksListenerJob: Job? = null
    private var observingAdminUnfilteredBooks: Boolean? = null

    init {
        if (isCloudConfigured) {
            startCloudIntegrations()
        }
    }

    private fun createInitialUiState(): BookHubUiState {
        val categories = catalogPersistence.loadCategories(BookCategory.entries)
        val rawBooks = catalogPersistence.loadBooks(repository.getInitialBooks(), categories)
        val defaultFavoriteIds = rawBooks.filter { it.isFavorite }.map { it.id }.toSet()
        val persistedFavoriteIds = favoritesPersistence.loadFavoriteIds(defaultFavoriteIds)

        val defaultProgress = rawBooks
            .filter { it.lastReadPage > 0 || it.lastOpenedOrder > 0L }
            .associate { book ->
                val total = book.pageCount ?: maxOf(1, book.lastReadPage)
                book.id to ReadingProgressRecord(
                    bookId = book.id,
                    lastPage = maxOf(1, book.lastReadPage),
                    totalPages = total,
                    lastOpenedOrder = book.lastOpenedOrder,
                )
            }
        val persistedProgress = readerPersistence.loadProgress(defaultProgress)
        val persistedBookmarks = readerPersistence.loadBookmarks(BookSampleData.initialBookmarks)
        val persistedNightMode = readerPersistence.loadNightMode()

        val maxSeq = persistedProgress.values.maxOfOrNull { it.lastOpenedOrder } ?: 199L
        val bookmarkedBookIds = persistedBookmarks.map { it.bookId }.toSet()

        val hydratedBooks = rawBooks.map { book ->
            val fav = book.id in persistedFavoriteIds
            val progressRecord = persistedProgress[book.id]
            val lastPage = progressRecord?.lastPage ?: 0
            val totalPages = progressRecord?.totalPages ?: book.pageCount
            val progressFraction = if (progressRecord != null && progressRecord.totalPages > 0) {
                (progressRecord.lastPage.toFloat() / progressRecord.totalPages.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val isBookmarked = book.id in bookmarkedBookIds
            book.copy(
                isFavorite = fav,
                isBookmarked = isBookmarked,
                pageCount = totalPages,
                lastReadPage = lastPage,
                readingProgress = progressFraction,
                lastOpenedOrder = progressRecord?.lastOpenedOrder ?: 0L,
                isInLibrary = fav || progressRecord != null || book.isInLibrary,
            )
        }

        val initialAuthStatus = adminAuthGateway.getStatus(null)
        val initialCloudAccount = firebaseAuth?.currentUser?.toCloudAccountProfile()

        return BookHubUiState(
            categories = categories,
            books = hydratedBooks,
            bookmarkNotes = persistedBookmarks,
            readerNightMode = persistedNightMode,
            nextOpenSequence = maxOf(200L, maxSeq + 1L),
            adminSession = null,
            adminAuthStatus = initialAuthStatus,
            useCloudAdminAuth = isCloudConfigured,
            cloudAccount = initialCloudAccount,
            isDriveAuthorized = googleDriveGateway?.hasCachedToken() == true,
        )
    }

    // region Cloud Auth, Realtime Sync & Drive Session

    private fun startCloudIntegrations() {
        startCategoriesListener()
        switchBooksListener(asAdmin = false)
        observeFirebaseAuthState()
    }

    private fun observeFirebaseAuthState() {
        val auth = firebaseAuth ?: return
        authObserverJob?.cancel()
        authObserverJob = viewModelScope.launch {
            auth.authStateFlow().collectLatest { user ->
                val profile = user?.toCloudAccountProfile()
                if (user == null) {
                    googleDriveGateway?.clearSession()
                    switchBooksListener(asAdmin = false)
                    _uiState.update {
                        it.copy(
                            cloudAccount = null,
                            adminCloudVerification = AdminCloudVerification.NotSignedIn,
                            isAdminRoleChecking = false,
                            isDriveAuthorized = false,
                            adminAuthError = null,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            cloudAccount = profile,
                            isAdminRoleChecking = true,
                            adminAuthError = null,
                        )
                    }
                    verifyCurrentAdminRole(forceTokenRefresh = false)
                }
            }
        }
    }

    private suspend fun verifyCurrentAdminRole(forceTokenRefresh: Boolean) {
        val db = firestoreDb ?: return
        val auth = firebaseAuth ?: return
        _uiState.update { it.copy(isAdminRoleChecking = true) }
        val verification = FirebaseAuthManager.verifyAdminAuthorization(
            db = db,
            auth = auth,
            forceTokenRefresh = forceTokenRefresh,
        )
        val isVerifiedAdmin = verification is AdminCloudVerification.Verified
        switchBooksListener(asAdmin = isVerifiedAdmin)
        _uiState.update {
            it.copy(
                adminCloudVerification = verification,
                isAdminRoleChecking = false,
                adminAuthError = (verification as? AdminCloudVerification.Denied)?.reason,
            )
        }
    }

    private fun startCategoriesListener() {
        val repo = firestoreCatalogRepository ?: return
        categoriesListenerJob?.cancel()
        categoriesListenerJob = viewModelScope.launch {
            try {
                repo.observeCategories().collect { cloudCategoryDtos ->
                    _uiState.update { state ->
                        val mergedCategories = CatalogCloudMerger.mergeCloudCategories(
                            existingCategories = state.categories,
                            cloudDtos = cloudCategoryDtos,
                            localBooks = state.books,
                        )
                        val catsById = mergedCategories.associateBy { it.id }
                        val relinkedBooks = state.books.map { book ->
                            val updatedCat = catsById[book.category.id]
                            if (updatedCat != null && updatedCat != book.category) {
                                book.copy(category = updatedCat)
                            } else {
                                book
                            }
                        }
                        if (mergedCategories != state.categories) {
                            catalogPersistence.saveCategories(mergedCategories)
                        }
                        if (relinkedBooks != state.books) {
                            catalogPersistence.saveBooks(relinkedBooks)
                        }
                        state.copy(
                            categories = mergedCategories,
                            books = relinkedBooks,
                            isCloudSyncActive = true,
                            cloudSyncError = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud category sync interrupted", e)
                _uiState.update {
                    it.copy(
                        isCloudSyncActive = false,
                        cloudSyncError = e.localizedMessage ?: "Cloud categories sync unavailable.",
                    )
                }
            }
        }
    }

    private fun switchBooksListener(asAdmin: Boolean) {
        val repo = firestoreCatalogRepository ?: return
        if (observingAdminUnfilteredBooks == asAdmin && booksListenerJob?.isActive == true) {
            return
        }
        observingAdminUnfilteredBooks = asAdmin
        booksListenerJob?.cancel()
        booksListenerJob = viewModelScope.launch {
            try {
                val flow = if (asAdmin) {
                    repo.observeAllBooksForAdmin()
                } else {
                    repo.observePublishedBooks()
                }
                flow.collect { cloudBookDtos ->
                    _uiState.update { state ->
                        val mergedBooks = CatalogCloudMerger.mergeCloudBooksPreservingReaderState(
                            existingBooks = state.books,
                            cloudDtos = cloudBookDtos,
                            categories = state.categories,
                        )
                        if (mergedBooks != state.books) {
                            catalogPersistence.saveBooks(mergedBooks)
                        }
                        state.copy(
                            books = mergedBooks,
                            isCloudSyncActive = true,
                            cloudSyncError = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud books sync interrupted (asAdmin=$asAdmin)", e)
                _uiState.update {
                    it.copy(
                        isCloudSyncActive = false,
                        cloudSyncError = e.localizedMessage ?: "Cloud books sync unavailable.",
                    )
                }
            }
        }
    }

    fun trySilentGoogleSignIn() {
        val ctx = applicationContext ?: return
        val auth = firebaseAuth ?: return
        viewModelScope.launch {
            val profile = FirebaseAuthManager.attemptSilentSignIn(ctx, auth)
            if (profile != null) {
                _uiState.update { it.copy(cloudAccount = profile) }
            }
        }
    }

    suspend fun signInWithGoogle(
        activityContext: Context,
        isAdminFlow: Boolean,
    ): GoogleSignInOutcome {
        val auth = firebaseAuth ?: return GoogleSignInOutcome.Failure("Firebase Auth is not initialized.")
        _uiState.update { it.copy(adminAuthError = null, isAdminRoleChecking = isAdminFlow) }
        return when (val outcome = FirebaseAuthManager.signInInteractiveWithGoogle(activityContext, auth)) {
            is GoogleSignInOutcome.Success -> {
                _uiState.update { it.copy(cloudAccount = outcome.profile) }
                if (isAdminFlow) {
                    verifyCurrentAdminRole(forceTokenRefresh = true)
                    if (_uiState.value.adminCloudVerification is AdminCloudVerification.Verified) {
                        _uiState.update {
                            it.copy(statusBannerMessage = "Signed in as Admin (${outcome.profile.displayName})")
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(statusBannerMessage = "Signed in as ${outcome.profile.displayName}")
                    }
                }
                outcome
            }
            is GoogleSignInOutcome.Cancelled -> {
                _uiState.update { it.copy(isAdminRoleChecking = false) }
                outcome
            }
            is GoogleSignInOutcome.Failure -> {
                _uiState.update {
                    it.copy(
                        isAdminRoleChecking = false,
                        adminAuthError = if (isAdminFlow) outcome.message else null,
                        statusBannerMessage = outcome.message,
                    )
                }
                outcome
            }
        }
    }

    fun reverifyCloudAdminStatus() {
        if (!isCloudConfigured) return
        viewModelScope.launch {
            verifyCurrentAdminRole(forceTokenRefresh = true)
        }
    }

    fun signOutCloudAccount() {
        val ctx = applicationContext
        val auth = firebaseAuth
        viewModelScope.launch {
            // Switch to public read-only stream before revoking credentials to avoid transient PERMISSION_DENIED noise
            switchBooksListener(asAdmin = false)
            googleDriveGateway?.clearSession()
            _uiState.update {
                it.copy(
                    cloudAccount = null,
                    adminCloudVerification = AdminCloudVerification.NotSignedIn,
                    isDriveAuthorized = false,
                    isAdminRoleChecking = false,
                    adminAuthError = null,
                    statusBannerMessage = "Signed out of Google account",
                )
            }
            if (ctx != null && auth != null) {
                FirebaseAuthManager.signOut(ctx, auth)
            }
        }
    }

    suspend fun requestDriveAuthorization(activityContext: Context): DriveAuthStatus {
        val gateway = googleDriveGateway
            ?: return DriveAuthStatus(isAuthorized = false, errorMessage = "Google Drive gateway unavailable.")
        val status = gateway.requestOrRefreshAccessToken(activityContext)
        _uiState.update { it.copy(isDriveAuthorized = status.isAuthorized) }
        if (!status.isAuthorized && status.pendingConsentIntentSender == null && !status.errorMessage.isNullOrBlank()) {
            _uiState.update { it.copy(statusBannerMessage = status.errorMessage) }
        }
        return status
    }

    fun handleDriveConsentIntentResult(
        activityContext: Context,
        intent: Intent?,
    ): DriveAuthStatus {
        val gateway = googleDriveGateway
            ?: return DriveAuthStatus(isAuthorized = false, errorMessage = "Google Drive gateway unavailable.")
        val status = gateway.handleAuthorizationResultIntent(activityContext, intent)
        _uiState.update {
            it.copy(
                isDriveAuthorized = status.isAuthorized,
                statusBannerMessage = if (status.isAuthorized) {
                    "Google Drive connected (drive.file scope)"
                } else {
                    status.errorMessage
                },
            )
        }
        return status
    }

    fun getCachedDriveAccessToken(): String? = googleDriveGateway?.getCachedTokenOrNull()

    suspend fun downloadBookForOfflineReading(bookId: String): Result<Long> {
        val ctx = applicationContext
            ?: return Result.failure(IllegalStateException("Application context unavailable."))
        val book = _uiState.value.books.find { it.id == bookId }
            ?: return Result.failure(IllegalStateException("Book not found."))
        val source = PdfSourceResolver.resolve(book)
            ?: return Result.failure(IllegalStateException("No PDF file is attached to this book."))
        val engine = PdfDocumentEngine(
            context = ctx,
            driveAccessTokenProvider = ::getCachedDriveAccessToken,
        )
        return engine.prefetchPdfForOffline(bookId, source)
    }

    // endregion

    private fun persistProgressFromBooks(books: List<Book>) {
        val records = books
            .filter { it.lastReadPage > 0 || it.lastOpenedOrder > 0L }
            .associate { book ->
                val total = book.pageCount ?: maxOf(1, book.lastReadPage)
                book.id to ReadingProgressRecord(
                    bookId = book.id,
                    lastPage = maxOf(1, book.lastReadPage),
                    totalPages = total,
                    lastOpenedOrder = book.lastOpenedOrder,
                )
            }
        readerPersistence.saveProgress(records)
    }

    private fun requireAuthorizedAdmin(): Boolean {
        if (isCloudConfigured) {
            val verified = _uiState.value.adminCloudVerification is AdminCloudVerification.Verified
            if (!verified) {
                _uiState.update {
                    it.copy(statusBannerMessage = "Verified Firebase Administrator account required.")
                }
            }
            return verified
        }
        val session = _uiState.value.adminSession
        val valid = adminAuthGateway.verifySession(session)
        if (!valid) {
            _uiState.update {
                it.copy(
                    adminSession = null,
                    adminAuthStatus = adminAuthGateway.getStatus(null),
                    statusBannerMessage = "Administrator sign-in required for this action.",
                )
            }
        }
        return valid
    }

    // region Local Passcode Admin Authentication (Offline / Test Compatibility)

    fun refreshAdminAuthStatus() {
        if (isCloudConfigured) {
            reverifyCloudAdminStatus()
            return
        }
        _uiState.update { state ->
            val validSession = if (adminAuthGateway.verifySession(state.adminSession)) {
                state.adminSession
            } else {
                null
            }
            state.copy(
                adminSession = validSession,
                adminAuthStatus = adminAuthGateway.getStatus(validSession),
            )
        }
    }

    fun setupFirstAdmin(
        adminName: String,
        adminEmail: String,
        passcode: String,
        confirmPasscode: String,
    ): Boolean {
        if (isCloudConfigured) return false
        if (passcode != confirmPasscode) {
            _uiState.update { it.copy(adminAuthError = "Passcodes do not match.") }
            return false
        }
        return when (val result = adminAuthGateway.setupFirstAdmin(adminName, adminEmail, passcode)) {
            is AdminAuthResult.Success -> {
                _uiState.update {
                    it.copy(
                        adminSession = result.session,
                        adminAuthStatus = AdminAuthStatus.Authenticated(result.session),
                        adminAuthError = null,
                        statusBannerMessage = "Administrator profile activated.",
                    )
                }
                true
            }
            is AdminAuthResult.Failure -> {
                _uiState.update {
                    it.copy(
                        adminAuthError = result.message,
                        adminAuthStatus = adminAuthGateway.getStatus(null),
                    )
                }
                false
            }
        }
    }

    fun loginAdmin(passcode: String): Boolean {
        if (isCloudConfigured) return false
        return when (val result = adminAuthGateway.authenticate(passcode)) {
            is AdminAuthResult.Success -> {
                _uiState.update {
                    it.copy(
                        adminSession = result.session,
                        adminAuthStatus = AdminAuthStatus.Authenticated(result.session),
                        adminAuthError = null,
                        statusBannerMessage = "Signed in as ${result.session.adminName}",
                    )
                }
                true
            }
            is AdminAuthResult.Failure -> {
                _uiState.update {
                    it.copy(
                        adminAuthError = result.message,
                        adminAuthStatus = adminAuthGateway.getStatus(null),
                    )
                }
                false
            }
        }
    }

    fun changeAdminPasscode(
        currentPasscode: String,
        newPasscode: String,
        confirmPasscode: String,
    ): Boolean {
        if (isCloudConfigured) return false
        if (!requireAuthorizedAdmin()) return false
        if (newPasscode != confirmPasscode) {
            _uiState.update { it.copy(statusBannerMessage = "New passcodes do not match.") }
            return false
        }
        return when (
            val result = adminAuthGateway.changePasscode(
                session = _uiState.value.adminSession,
                currentPasscode = currentPasscode,
                newPasscode = newPasscode,
            )
        ) {
            is AdminAuthResult.Success -> {
                _uiState.update {
                    it.copy(
                        adminSession = result.session,
                        adminAuthStatus = AdminAuthStatus.Authenticated(result.session),
                        statusBannerMessage = "Admin passcode updated successfully.",
                    )
                }
                true
            }
            is AdminAuthResult.Failure -> {
                _uiState.update { it.copy(statusBannerMessage = result.message) }
                false
            }
        }
    }

    fun logoutAdmin() {
        if (isCloudConfigured) {
            signOutCloudAccount()
            return
        }
        adminAuthGateway.logout(_uiState.value.adminSession)
        _uiState.update {
            it.copy(
                adminSession = null,
                adminAuthStatus = adminAuthGateway.getStatus(null),
                adminAuthError = null,
                statusBannerMessage = "Signed out of Admin Panel",
            )
        }
    }

    fun clearAdminAuthError() {
        _uiState.update { it.copy(adminAuthError = null) }
    }

    // endregion

    // region Admin Storage Imports & Cloud/Local Book Management (Protected)

    suspend fun importAdminPdfUri(uri: Uri): PdfImportResult {
        if (!requireAuthorizedAdmin()) {
            return PdfImportResult.Error("Administrator sign-in required.")
        }
        return storageGateway.importPdf(uri)
    }

    suspend fun importAdminCoverUri(uri: Uri): CoverImportResult {
        if (!requireAuthorizedAdmin()) {
            return CoverImportResult.Error("Administrator sign-in required.")
        }
        return storageGateway.importCoverImage(uri)
    }

    fun cleanupUnusedManagedFile(filePath: String?) {
        if (!requireAuthorizedAdmin()) return
        storageGateway.deleteManagedFile(filePath)
    }

    /**
     * Phase 6 Cloud Book Publish / Create (Google Drive upload + Firestore metadata sync).
     * Reuses `pendingAssets` on retry so failed Firestore writes do not duplicate Drive files.
     */
    suspend fun saveAdminBookCloud(
        title: String,
        author: String,
        description: String,
        categoryId: String,
        pdfFilePath: String,
        pdfByteSize: Long,
        pdfFormattedSize: String,
        pdfPageCount: Int?,
        coverImagePath: String?,
        language: String = "English",
        isFeatured: Boolean = false,
        isPopular: Boolean = false,
        isRecent: Boolean = true,
        isPublished: Boolean = true,
        pendingAssets: PendingDriveBookAssets = PendingDriveBookAssets(),
        onPendingAssetsUpdated: (PendingDriveBookAssets) -> Unit = {},
    ): String? {
        if (!isCloudConfigured || firestoreCatalogRepository == null || googleDriveGateway == null) {
            return addAdminBook(
                title = title,
                author = author,
                description = description,
                categoryId = categoryId,
                pdfFilePath = pdfFilePath,
                pdfByteSize = pdfByteSize,
                pdfFormattedSize = pdfFormattedSize,
                pdfPageCount = pdfPageCount,
                coverImagePath = coverImagePath,
                language = language,
                isFeatured = isFeatured,
                isPopular = isPopular,
                isRecent = isRecent,
                isPublished = isPublished,
            )
        }
        if (!requireAuthorizedAdmin()) return "Verified administrator sign-in required."

        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return "Book title is required."
        if (cleanTitle.length > 200) return "Book title must be 200 characters or fewer."
        if (pdfFilePath.isBlank()) return "Please select a valid PDF file."

        val targetCategory = _uiState.value.findCategoryById(categoryId)
            ?: return "Please select a valid category."

        val pdfLocalFile = File(pdfFilePath.removePrefix("file://"))
        if (!pdfLocalFile.exists() || !pdfLocalFile.canRead()) {
            return "Selected PDF file is no longer accessible. Please pick it again."
        }

        var workingAssets = pendingAssets
        try {
            val uploadedPdf = if (workingAssets.localPdfPath == pdfFilePath && workingAssets.pdfUploaded != null) {
                workingAssets.pdfUploaded.also {
                    googleDriveGateway.setPublicReadAccess(it.fileId, isPublished)
                }
            } else {
                _uiState.update {
                    it.copy(
                        cloudUploadStage = "Uploading PDF to Google Drive...",
                        cloudUploadProgress = 0.05f,
                    )
                }
                val pdfUploadResult = googleDriveGateway.uploadPdf(
                    localFile = pdfLocalFile,
                    bookTitle = cleanTitle,
                    setPublicReadPermission = isPublished,
                    onProgress = { pct ->
                        _uiState.update { it.copy(cloudUploadProgress = pct * 0.85f) }
                    },
                )
                val uploaded = pdfUploadResult.getOrElse { err ->
                    return err.localizedMessage ?: "Google Drive PDF upload failed."
                }
                workingAssets = workingAssets.copy(localPdfPath = pdfFilePath, pdfUploaded = uploaded)
                onPendingAssetsUpdated(workingAssets)
                uploaded
            }

            // Cache uploaded PDF immediately under Drive cache key so reader works offline right away
            seedDrivePdfCache(pdfLocalFile, uploadedPdf.fileId)

            val uploadedCover: DriveUploadedFile? = if (!coverImagePath.isNullOrBlank()) {
                val coverFile = File(coverImagePath.removePrefix("file://"))
                if (coverFile.exists() && coverFile.canRead()) {
                    if (workingAssets.localCoverPath == coverImagePath && workingAssets.coverUploaded != null) {
                        workingAssets.coverUploaded.also {
                            googleDriveGateway.setPublicReadAccess(it.fileId, isPublished)
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                cloudUploadStage = "Uploading cover image to Google Drive...",
                                cloudUploadProgress = 0.90f,
                            )
                        }
                        val coverRes = googleDriveGateway.uploadCoverImage(
                            localFile = coverFile,
                            bookTitle = cleanTitle,
                            setPublicReadPermission = isPublished,
                        )
                        val uploaded = coverRes.getOrElse { err ->
                            return err.localizedMessage ?: "Google Drive cover upload failed."
                        }
                        workingAssets = workingAssets.copy(localCoverPath = coverImagePath, coverUploaded = uploaded)
                        onPendingAssetsUpdated(workingAssets)
                        uploaded
                    }
                } else {
                    null
                }
            } else {
                null
            }

            _uiState.update {
                it.copy(
                    cloudUploadStage = "Saving catalog metadata to Cloud Firestore...",
                    cloudUploadProgress = 0.96f,
                )
            }

            val nowMillis = System.currentTimeMillis()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val dateLabel = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(nowMillis))
            val nextAddedOrder = (_uiState.value.books.maxOfOrNull { it.addedOrder } ?: 0L) + 1L
            val cleanAuthor = author.trim().take(120).ifEmpty { "Independent Author" }
            val cleanDescription = description.trim().take(4000).ifEmpty { null }
            val cleanLanguage = language.trim().take(50).ifEmpty { "English" }

            val newBook = Book(
                id = "book_cloud_${UUID.randomUUID().toString().take(12)}",
                title = cleanTitle,
                author = cleanAuthor,
                category = targetCategory,
                fileSizeMb = pdfFormattedSize.ifBlank { LocalBookStorageGateway.formatBytesLabel(pdfByteSize) },
                pageCount = pdfPageCount,
                rating = 5.0f,
                reviewCount = 0,
                description = cleanDescription,
                publishedYear = currentYear,
                addedOrder = nextAddedOrder,
                dateAdded = dateLabel,
                language = cleanLanguage,
                coverStyle = CoverArtworkStyle.entries[(nextAddedOrder.toInt()) % CoverArtworkStyle.entries.size],
                coverPrimary = targetCategory.secondaryColor,
                coverSecondary = targetCategory.accentColor,
                coverAccent = Color(0xFFF2C94C),
                coverImagePath = null,
                coverUrl = uploadedCover?.viewUrl,
                coverDriveFileId = uploadedCover?.fileId,
                isFeatured = isFeatured,
                isPopular = isPopular,
                isRecent = isRecent,
                isPublished = isPublished,
                isSyncedToCloud = true,
                pdfSourceUrl = uploadedPdf.downloadUrl,
                googleDriveFileId = uploadedPdf.fileId,
                localPdfBytes = pdfByteSize.coerceAtLeast(uploadedPdf.byteSize),
                isAdminUploaded = true,
                uploadTimestamp = nowMillis,
                sampleExcerpt = cleanDescription?.take(140),
            )

            val firestoreResult = firestoreCatalogRepository.saveBook(newBook, isNew = true)
            firestoreResult.getOrElse { err ->
                return "Uploaded to Google Drive, but Firestore save failed: ${err.localizedMessage ?: "Permission or network error"}. Tap again to retry without re-uploading."
            }

            // Cleanup local staging files once persisted in cloud + Drive cache
            storageGateway.deleteManagedFile(pdfFilePath)
            storageGateway.deleteManagedFile(coverImagePath)
            onPendingAssetsUpdated(PendingDriveBookAssets())

            _uiState.update { state ->
                val updatedBooks = if (state.books.any { it.id == newBook.id }) {
                    state.books.map { if (it.id == newBook.id) newBook else it }
                } else {
                    listOf(newBook) + state.books
                }
                catalogPersistence.saveBooks(updatedBooks)
                state.copy(
                    books = updatedBooks,
                    statusBannerMessage = if (isPublished) {
                        "Published \"$cleanTitle\" to Cloud"
                    } else {
                        "Saved \"$cleanTitle\" as Admin Draft"
                    },
                )
            }
            return null
        } finally {
            _uiState.update { it.copy(cloudUploadStage = null, cloudUploadProgress = null) }
        }
    }

    /**
     * Phase 6 Cloud Book Update (metadata edit, optional Drive PDF/Cover replacement & cleanup).
     */
    suspend fun updateAdminBookCloud(
        bookId: String,
        title: String,
        author: String,
        description: String,
        categoryId: String,
        newPdfFilePath: String? = null,
        newPdfByteSize: Long? = null,
        newPdfFormattedSize: String? = null,
        newPdfPageCount: Int? = null,
        newCoverImagePath: String? = null,
        removeCustomCover: Boolean = false,
        language: String = "English",
        isFeatured: Boolean,
        isPopular: Boolean,
        isRecent: Boolean,
        isPublished: Boolean,
        pendingAssets: PendingDriveBookAssets = PendingDriveBookAssets(),
        onPendingAssetsUpdated: (PendingDriveBookAssets) -> Unit = {},
    ): String? {
        if (!isCloudConfigured || firestoreCatalogRepository == null || googleDriveGateway == null) {
            return updateAdminBook(
                bookId = bookId,
                title = title,
                author = author,
                description = description,
                categoryId = categoryId,
                newPdfFilePath = newPdfFilePath,
                newPdfByteSize = newPdfByteSize,
                newPdfFormattedSize = newPdfFormattedSize,
                newPdfPageCount = newPdfPageCount,
                newCoverImagePath = newCoverImagePath,
                removeCustomCover = removeCustomCover,
                language = language,
                isFeatured = isFeatured,
                isPopular = isPopular,
                isRecent = isRecent,
                isPublished = isPublished,
            )
        }
        if (!requireAuthorizedAdmin()) return "Verified administrator sign-in required."

        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return "Book title is required."
        if (cleanTitle.length > 200) return "Book title must be 200 characters or fewer."

        val targetCategory = _uiState.value.findCategoryById(categoryId)
            ?: return "Please select a valid category."
        val existingBook = _uiState.value.books.find { it.id == bookId }
            ?: return "Book not found."

        var workingAssets = pendingAssets
        var oldDrivePdfToDelete: String? = null
        var oldDriveCoverToDelete: String? = null
        var oldLocalPdfToDelete: String? = null
        var oldLocalCoverToDelete: String? = null

        try {
            // Determine if a local PDF needs uploading to Drive (newly picked OR migrating local-only Phase 5 book)
            val localFileToUploadPath: String? = when {
                !newPdfFilePath.isNullOrBlank() -> newPdfFilePath
                existingBook.googleDriveFileId.isNullOrBlank() &&
                    existingBook.pdfSourceUrl?.startsWith("file://", ignoreCase = true) == true -> {
                    existingBook.pdfSourceUrl.removePrefix("file://")
                }
                else -> null
            }

            var updatedDriveFileId = existingBook.googleDriveFileId
            var updatedPdfUrl = existingBook.pdfSourceUrl
            var updatedPdfAsset = existingBook.pdfAssetPath
            var updatedBytes = existingBook.localPdfBytes
            var updatedSizeLabel = existingBook.fileSizeMb
            var updatedPageCount = existingBook.pageCount

            if (!localFileToUploadPath.isNullOrBlank()) {
                val pdfFile = File(localFileToUploadPath.removePrefix("file://"))
                if (pdfFile.exists() && pdfFile.canRead()) {
                    val uploadedPdf = if (workingAssets.localPdfPath == localFileToUploadPath && workingAssets.pdfUploaded != null) {
                        workingAssets.pdfUploaded.also {
                            googleDriveGateway.setPublicReadAccess(it.fileId, isPublished)
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                cloudUploadStage = "Uploading replacement PDF to Google Drive...",
                                cloudUploadProgress = 0.05f,
                            )
                        }
                        val uploaded = googleDriveGateway.uploadPdf(
                            localFile = pdfFile,
                            bookTitle = cleanTitle,
                            setPublicReadPermission = isPublished,
                            onProgress = { pct ->
                                _uiState.update { it.copy(cloudUploadProgress = pct * 0.85f) }
                            },
                        ).getOrElse { err ->
                            return err.localizedMessage ?: "Google Drive PDF upload failed."
                        }
                        workingAssets = workingAssets.copy(localPdfPath = localFileToUploadPath, pdfUploaded = uploaded)
                        onPendingAssetsUpdated(workingAssets)
                        uploaded
                    }

                    seedDrivePdfCache(pdfFile, uploadedPdf.fileId)
                    if (!existingBook.googleDriveFileId.isNullOrBlank() && existingBook.googleDriveFileId != uploadedPdf.fileId) {
                        oldDrivePdfToDelete = existingBook.googleDriveFileId
                    }
                    if (!newPdfFilePath.isNullOrBlank() && existingBook.pdfSourceUrl?.startsWith("file://") == true) {
                        oldLocalPdfToDelete = existingBook.pdfSourceUrl
                    }
                    updatedDriveFileId = uploadedPdf.fileId
                    updatedPdfUrl = uploadedPdf.downloadUrl
                    updatedPdfAsset = null
                    updatedBytes = (newPdfByteSize ?: existingBook.localPdfBytes).coerceAtLeast(uploadedPdf.byteSize)
                    updatedSizeLabel = newPdfFormattedSize ?: existingBook.fileSizeMb
                    updatedPageCount = newPdfPageCount ?: existingBook.pageCount
                }
            } else if (!existingBook.googleDriveFileId.isNullOrBlank() && existingBook.isPublished != isPublished) {
                googleDriveGateway.setPublicReadAccess(existingBook.googleDriveFileId, isPublished)
            }

            var updatedCoverUrl = existingBook.coverUrl
            var updatedCoverDriveId = existingBook.coverDriveFileId
            var updatedCoverPath = existingBook.coverImagePath

            if (removeCustomCover) {
                oldDriveCoverToDelete = existingBook.coverDriveFileId
                oldLocalCoverToDelete = existingBook.coverImagePath
                updatedCoverUrl = null
                updatedCoverDriveId = null
                updatedCoverPath = null
            } else if (!newCoverImagePath.isNullOrBlank()) {
                val coverFile = File(newCoverImagePath.removePrefix("file://"))
                if (coverFile.exists() && coverFile.canRead()) {
                    val uploadedCover = if (workingAssets.localCoverPath == newCoverImagePath && workingAssets.coverUploaded != null) {
                        workingAssets.coverUploaded.also {
                            googleDriveGateway.setPublicReadAccess(it.fileId, isPublished)
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                cloudUploadStage = "Uploading cover image to Google Drive...",
                                cloudUploadProgress = 0.90f,
                            )
                        }
                        val uploaded = googleDriveGateway.uploadCoverImage(
                            localFile = coverFile,
                            bookTitle = cleanTitle,
                            setPublicReadPermission = isPublished,
                        ).getOrElse { err ->
                            return err.localizedMessage ?: "Google Drive cover upload failed."
                        }
                        workingAssets = workingAssets.copy(localCoverPath = newCoverImagePath, coverUploaded = uploaded)
                        onPendingAssetsUpdated(workingAssets)
                        uploaded
                    }
                    if (!existingBook.coverDriveFileId.isNullOrBlank() && existingBook.coverDriveFileId != uploadedCover.fileId) {
                        oldDriveCoverToDelete = existingBook.coverDriveFileId
                    }
                    oldLocalCoverToDelete = existingBook.coverImagePath
                    updatedCoverUrl = uploadedCover.viewUrl
                    updatedCoverDriveId = uploadedCover.fileId
                    updatedCoverPath = null
                }
            } else if (!existingBook.coverDriveFileId.isNullOrBlank() && existingBook.isPublished != isPublished) {
                googleDriveGateway.setPublicReadAccess(existingBook.coverDriveFileId, isPublished)
            }

            _uiState.update {
                it.copy(
                    cloudUploadStage = "Updating Cloud Firestore record...",
                    cloudUploadProgress = 0.96f,
                )
            }

            val updatedBook = existingBook.copy(
                title = cleanTitle,
                author = author.trim().take(120).ifEmpty { "Independent Author" },
                description = description.trim().take(4000).ifEmpty { null },
                category = targetCategory,
                language = language.trim().take(50).ifEmpty { "English" },
                pdfAssetPath = updatedPdfAsset,
                pdfSourceUrl = updatedPdfUrl,
                googleDriveFileId = updatedDriveFileId,
                localPdfBytes = updatedBytes,
                fileSizeMb = updatedSizeLabel,
                pageCount = updatedPageCount,
                coverImagePath = updatedCoverPath,
                coverUrl = updatedCoverUrl,
                coverDriveFileId = updatedCoverDriveId,
                isFeatured = isFeatured,
                isPopular = isPopular,
                isRecent = isRecent,
                isPublished = isPublished,
                isSyncedToCloud = true,
                uploadTimestamp = System.currentTimeMillis(),
            )

            val saveRes = firestoreCatalogRepository.saveBook(
                book = updatedBook,
                isNew = !existingBook.isSyncedToCloud,
            )
            saveRes.getOrElse { err ->
                return "Cloud save failed: ${err.localizedMessage ?: "Check admin permissions and network."}"
            }

            // Cleanup obsolete Drive & local staging files strictly after Firestore commit succeeds
            if (oldDrivePdfToDelete != null) {
                googleDriveGateway.deleteFile(oldDrivePdfToDelete)
            }
            if (oldDriveCoverToDelete != null) {
                googleDriveGateway.deleteFile(oldDriveCoverToDelete)
            }
            storageGateway.deleteManagedFile(oldLocalPdfToDelete)
            storageGateway.deleteManagedFile(oldLocalCoverToDelete)
            if (!newPdfFilePath.isNullOrBlank()) storageGateway.deleteManagedFile(newPdfFilePath)
            if (!newCoverImagePath.isNullOrBlank()) storageGateway.deleteManagedFile(newCoverImagePath)
            onPendingAssetsUpdated(PendingDriveBookAssets())

            _uiState.update { state ->
                val updatedBooks = state.books.map { if (it.id == bookId) updatedBook else it }
                catalogPersistence.saveBooks(updatedBooks)
                state.copy(
                    books = updatedBooks,
                    statusBannerMessage = "Updated \"$cleanTitle\" in Cloud",
                )
            }
            return null
        } finally {
            _uiState.update { it.copy(cloudUploadStage = null, cloudUploadProgress = null) }
        }
    }

    suspend fun toggleBookPublishedCloud(bookId: String): String? {
        if (!requireAuthorizedAdmin()) return "Administrator sign-in required."
        val target = _uiState.value.books.find { it.id == bookId } ?: return "Book not found."
        val nextPublished = !target.isPublished

        if (isCloudConfigured && googleDriveGateway != null) {
            if (!target.googleDriveFileId.isNullOrBlank()) {
                val permRes = googleDriveGateway.setPublicReadAccess(target.googleDriveFileId, nextPublished)
                if (permRes.isFailure) {
                    return permRes.exceptionOrNull()?.localizedMessage
                        ?: "Connect Google Drive to change public link access."
                }
            }
            if (!target.coverDriveFileId.isNullOrBlank()) {
                googleDriveGateway.setPublicReadAccess(target.coverDriveFileId, nextPublished)
            }
        }

        if (isCloudConfigured && firestoreCatalogRepository != null) {
            val res = if (target.isSyncedToCloud) {
                firestoreCatalogRepository.setBookPublished(bookId, nextPublished)
            } else {
                firestoreCatalogRepository.saveBook(target.copy(isPublished = nextPublished, isSyncedToCloud = true), isNew = true)
            }
            res.getOrElse { err ->
                return err.localizedMessage ?: "Failed to update publish visibility in Firestore."
            }
        }

        _uiState.update { state ->
            val updated = state.books.map {
                if (it.id == bookId) {
                    it.copy(
                        isPublished = nextPublished,
                        isSyncedToCloud = it.isSyncedToCloud || isCloudConfigured,
                    )
                } else {
                    it
                }
            }
            catalogPersistence.saveBooks(updated)
            state.copy(
                books = updated,
                statusBannerMessage = if (nextPublished) {
                    "Published \"${target.title}\""
                } else {
                    "Moved \"${target.title}\" to Draft (Admin Only)"
                },
            )
        }
        return null
    }

    suspend fun deleteBookCloudAndLocal(bookId: String): Boolean {
        if (!requireAuthorizedAdmin()) return false
        val target = _uiState.value.books.find { it.id == bookId } ?: return false

        if (isCloudConfigured && target.isSyncedToCloud && firestoreCatalogRepository != null) {
            val firestoreDelete = firestoreCatalogRepository.deleteBook(bookId)
            if (firestoreDelete.isFailure) {
                _uiState.update {
                    it.copy(
                        statusBannerMessage = firestoreDelete.exceptionOrNull()?.localizedMessage
                            ?: "Could not delete book from Firestore.",
                    )
                }
                return false
            }
        }

        if (isCloudConfigured && googleDriveGateway != null) {
            if (!target.googleDriveFileId.isNullOrBlank()) {
                googleDriveGateway.deleteFile(target.googleDriveFileId)
            }
            if (!target.coverDriveFileId.isNullOrBlank()) {
                googleDriveGateway.deleteFile(target.coverDriveFileId)
            }
        }

        return removeBookLocallyAndCleanup(target)
    }

    fun migrateLocalCatalogToCloud() {
        val repo = firestoreCatalogRepository ?: return
        if (!requireAuthorizedAdmin()) return
        if (_uiState.value.isMigratingCatalog) return

        viewModelScope.launch {
            _uiState.update { it.copy(isMigratingCatalog = true) }
            try {
                val state = _uiState.value
                val result = repo.migrateLocalCatalogIdempotently(state.categories, state.books)
                result.onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isMigratingCatalog = false,
                            statusBannerMessage = "Synced ${summary.uploadedBooks} book(s) & ${summary.uploadedCategories} category(ies) (${summary.skippedExistingBooks} already in cloud).",
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isMigratingCatalog = false,
                            statusBannerMessage = err.localizedMessage ?: "Cloud catalog migration failed.",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _uiState.update { it.copy(isMigratingCatalog = false) }
            }
        }
    }

    private suspend fun seedDrivePdfCache(sourceFile: File, driveFileId: String) {
        val ctx = applicationContext ?: return
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(ctx.cacheDir, "bookhub_pdfs").apply { mkdirs() }
                val safeId = driveFileId.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
                val dest = File(cacheDir, "drive_${safeId}.pdf")
                if (!dest.exists()) {
                    sourceFile.copyTo(dest, overwrite = true)
                }
            } catch (_: Exception) {
            }
        }
    }

    // Synchronous local methods preserved cleanly for Phase 5 offline/unit test mode

    fun addAdminBook(
        title: String,
        author: String,
        description: String,
        categoryId: String,
        pdfFilePath: String,
        pdfByteSize: Long,
        pdfFormattedSize: String,
        pdfPageCount: Int?,
        coverImagePath: String?,
        language: String = "English",
        isFeatured: Boolean = false,
        isPopular: Boolean = false,
        isRecent: Boolean = true,
        isPublished: Boolean = true,
    ): String? {
        if (!requireAuthorizedAdmin()) return "Administrator authorization required."

        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return "Book title is required."
        if (pdfFilePath.isBlank()) return "Please select a valid PDF file."

        val targetCategory = _uiState.value.findCategoryById(categoryId)
            ?: return "Please select a valid category."

        val cleanAuthor = author.trim().ifEmpty { "Independent Author" }
        val cleanDescription = description.trim().ifEmpty { null }
        val cleanLanguage = language.trim().ifEmpty { "English" }
        val nowMillis = System.currentTimeMillis()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val dateLabel = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(nowMillis))
        val nextAddedOrder = (_uiState.value.books.maxOfOrNull { it.addedOrder } ?: 0L) + 1L

        val normalizedPdfUrl = if (pdfFilePath.startsWith("file://")) {
            pdfFilePath
        } else {
            "file://$pdfFilePath"
        }

        val newBook = Book(
            id = "book_admin_${UUID.randomUUID().toString().take(8)}",
            title = cleanTitle,
            author = cleanAuthor,
            category = targetCategory,
            fileSizeMb = pdfFormattedSize.ifBlank { LocalBookStorageGateway.formatBytesLabel(pdfByteSize) },
            pageCount = pdfPageCount,
            rating = 5.0f,
            reviewCount = 0,
            description = cleanDescription,
            publishedYear = currentYear,
            addedOrder = nextAddedOrder,
            dateAdded = dateLabel,
            language = cleanLanguage,
            coverStyle = CoverArtworkStyle.entries[(nextAddedOrder.toInt()) % CoverArtworkStyle.entries.size],
            coverPrimary = targetCategory.secondaryColor,
            coverSecondary = targetCategory.accentColor,
            coverAccent = Color(0xFFF2C94C),
            coverImagePath = coverImagePath,
            isFeatured = isFeatured,
            isPopular = isPopular,
            isRecent = isRecent,
            isPublished = isPublished,
            pdfSourceUrl = normalizedPdfUrl,
            localPdfBytes = pdfByteSize.coerceAtLeast(0L),
            isAdminUploaded = true,
            uploadTimestamp = nowMillis,
            sampleExcerpt = cleanDescription?.take(140),
        )

        _uiState.update { state ->
            val updatedBooks = listOf(newBook) + state.books
            catalogPersistence.saveBooks(updatedBooks)
            state.copy(
                books = updatedBooks,
                statusBannerMessage = "Published \"$cleanTitle\"",
            )
        }
        return null
    }

    fun updateAdminBook(
        bookId: String,
        title: String,
        author: String,
        description: String,
        categoryId: String,
        newPdfFilePath: String? = null,
        newPdfByteSize: Long? = null,
        newPdfFormattedSize: String? = null,
        newPdfPageCount: Int? = null,
        newCoverImagePath: String? = null,
        removeCustomCover: Boolean = false,
        language: String = "English",
        isFeatured: Boolean,
        isPopular: Boolean,
        isRecent: Boolean,
        isPublished: Boolean = true,
    ): String? {
        if (!requireAuthorizedAdmin()) return "Administrator authorization required."

        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return "Book title is required."

        val targetCategory = _uiState.value.findCategoryById(categoryId)
            ?: return "Please select a valid category."

        val existingBook = _uiState.value.books.find { it.id == bookId }
            ?: return "Book not found."

        var oldPdfToDelete: String? = null
        var oldCoverToDelete: String? = null

        val updatedPdfUrl: String?
        val updatedPdfAsset: String?
        val updatedBytes: Long
        val updatedSizeMb: String
        val updatedPageCount: Int?

        if (!newPdfFilePath.isNullOrBlank()) {
            val normalizedNewUrl = if (newPdfFilePath.startsWith("file://")) {
                newPdfFilePath
            } else {
                "file://$newPdfFilePath"
            }
            if (existingBook.pdfSourceUrl != normalizedNewUrl) {
                oldPdfToDelete = existingBook.pdfSourceUrl
            }
            updatedPdfUrl = normalizedNewUrl
            updatedPdfAsset = null
            updatedBytes = (newPdfByteSize ?: existingBook.localPdfBytes).coerceAtLeast(0L)
            updatedSizeMb = newPdfFormattedSize ?: existingBook.fileSizeMb
            updatedPageCount = newPdfPageCount ?: existingBook.pageCount
        } else {
            updatedPdfUrl = existingBook.pdfSourceUrl
            updatedPdfAsset = existingBook.pdfAssetPath
            updatedBytes = existingBook.localPdfBytes
            updatedSizeMb = existingBook.fileSizeMb
            updatedPageCount = existingBook.pageCount
        }

        val updatedCoverPath: String? = when {
            removeCustomCover -> {
                oldCoverToDelete = existingBook.coverImagePath
                null
            }
            !newCoverImagePath.isNullOrBlank() -> {
                if (existingBook.coverImagePath != newCoverImagePath) {
                    oldCoverToDelete = existingBook.coverImagePath
                }
                newCoverImagePath
            }
            else -> existingBook.coverImagePath
        }

        val updatedBook = existingBook.copy(
            title = cleanTitle,
            author = author.trim().ifEmpty { "Independent Author" },
            description = description.trim().ifEmpty { null },
            category = targetCategory,
            language = language.trim().ifEmpty { "English" },
            pdfAssetPath = updatedPdfAsset,
            pdfSourceUrl = updatedPdfUrl,
            localPdfBytes = updatedBytes,
            fileSizeMb = updatedSizeMb,
            pageCount = updatedPageCount,
            coverImagePath = updatedCoverPath,
            isFeatured = isFeatured,
            isPopular = isPopular,
            isRecent = isRecent,
            isPublished = isPublished,
        )

        _uiState.update { state ->
            val updatedBooks = state.books.map { if (it.id == bookId) updatedBook else it }
            catalogPersistence.saveBooks(updatedBooks)
            state.copy(
                books = updatedBooks,
                statusBannerMessage = "Updated \"$cleanTitle\"",
            )
        }

        storageGateway.deleteManagedFile(oldPdfToDelete)
        storageGateway.deleteManagedFile(oldCoverToDelete)
        return null
    }

    fun deleteAdminBook(bookId: String): Boolean {
        if (!requireAuthorizedAdmin()) return false
        val target = _uiState.value.books.find { it.id == bookId } ?: return false
        return removeBookLocallyAndCleanup(target)
    }

    private fun removeBookLocallyAndCleanup(target: Book): Boolean {
        _uiState.update { state ->
            val remainingBooks = state.books.filterNot { it.id == target.id }
            val remainingBookmarks = state.bookmarkNotes.filterNot { it.bookId == target.id }

            catalogPersistence.saveBooks(remainingBooks)
            readerPersistence.saveBookmarks(remainingBookmarks)
            persistProgressFromBooks(remainingBooks)
            favoritesPersistence.saveFavoriteIds(
                remainingBooks.filter { it.isFavorite }.map { it.id }.toSet(),
            )

            state.copy(
                books = remainingBooks,
                bookmarkNotes = remainingBookmarks,
                statusBannerMessage = "Deleted \"${target.title}\"",
            )
        }

        storageGateway.deleteManagedFile(target.pdfSourceUrl)
        storageGateway.deleteManagedFile(target.coverImagePath)
        return true
    }

    // endregion

    // region Admin Category Management (Protected, Cloud + Local)

    suspend fun addCategoryCloud(
        name: String,
        description: String,
        accentColor: Color,
        secondaryColor: Color,
        iconKey: String,
    ): String? {
        if (!isCloudConfigured || firestoreCatalogRepository == null) {
            return addCategory(name, description, accentColor, secondaryColor, iconKey)
        }
        if (!requireAuthorizedAdmin()) return "Administrator authorization required."

        val cleanName = name.trim()
        if (cleanName.isEmpty()) return "Category name is required."
        if (cleanName.length > 80) return "Category name must be 80 characters or fewer."
        if (_uiState.value.categories.any { it.displayName.equals(cleanName, ignoreCase = true) }) {
            return "A category named \"$cleanName\" already exists."
        }

        val cleanDesc = description.trim().take(400).ifEmpty { "Curated $cleanName PDF collection" }
        val shortName = formatShortCategoryName(cleanName)
        val newCategory = BookCategory(
            id = "cat_${UUID.randomUUID().toString().take(8)}",
            displayName = cleanName,
            shortName = shortName,
            description = cleanDesc,
            accentColor = accentColor,
            secondaryColor = secondaryColor,
            iconKey = iconKey,
            sortOrder = _uiState.value.categories.size,
        )

        val cloudRes = firestoreCatalogRepository.createOrUpdateCategory(newCategory, isNew = true)
        cloudRes.getOrElse { err ->
            return err.localizedMessage ?: "Could not save category to Firestore."
        }

        _uiState.update { state ->
            val updatedCategories = if (state.categories.any { it.id == newCategory.id }) {
                state.categories
            } else {
                state.categories + newCategory
            }
            catalogPersistence.saveCategories(updatedCategories)
            state.copy(
                categories = updatedCategories,
                statusBannerMessage = "Added category \"$cleanName\"",
            )
        }
        return null
    }

    suspend fun updateCategoryCloud(
        categoryId: String,
        name: String,
        description: String,
        accentColor: Color,
        secondaryColor: Color,
        iconKey: String,
    ): String? {
        if (!isCloudConfigured || firestoreCatalogRepository == null) {
            return updateCategory(categoryId, name, description, accentColor, secondaryColor, iconKey)
        }
        if (!requireAuthorizedAdmin()) return "Administrator authorization required."

        val cleanName = name.trim()
        if (cleanName.isEmpty()) return "Category name is required."
        if (cleanName.length > 80) return "Category name must be 80 characters or fewer."
        if (_uiState.value.categories.any {
                it.id != categoryId && it.displayName.equals(cleanName, ignoreCase = true)
            }
        ) {
            return "Another category named \"$cleanName\" already exists."
        }
        val existing = _uiState.value.findCategoryById(categoryId)
            ?: return "Category not found."

        val updatedCategory = existing.copy(
            displayName = cleanName,
            shortName = formatShortCategoryName(cleanName),
            description = description.trim().take(400).ifEmpty { existing.description },
            accentColor = accentColor,
            secondaryColor = secondaryColor,
            iconKey = iconKey,
        )

        val cloudRes = firestoreCatalogRepository.createOrUpdateCategory(updatedCategory, isNew = false)
        cloudRes.getOrElse { err ->
            return err.localizedMessage ?: "Could not update category in Firestore."
        }

        _uiState.update { state ->
            val updatedCategories = state.categories.map {
                if (it.id == categoryId) updatedCategory else it
            }
            val updatedBooks = state.books.map { book ->
                if (book.category.id == categoryId) book.copy(category = updatedCategory) else book
            }
            catalogPersistence.saveCategories(updatedCategories)
            catalogPersistence.saveBooks(updatedBooks)
            state.copy(
                categories = updatedCategories,
                books = updatedBooks,
                statusBannerMessage = "Updated category \"$cleanName\"",
            )
        }
        return null
    }

    suspend fun deleteCategoryCloud(
        categoryId: String,
        reassignTargetCategoryId: String? = null,
    ): String? {
        if (!isCloudConfigured || firestoreCatalogRepository == null) {
            return deleteCategory(categoryId, reassignTargetCategoryId)
        }
        if (!requireAuthorizedAdmin()) return "Administrator authorization required."

        val stateSnapshot = _uiState.value
        val target = stateSnapshot.findCategoryById(categoryId) ?: return "Category not found."
        if (stateSnapshot.categories.size <= 1) {
            return "At least one category must remain in BookHub."
        }

        val affectedBooks = stateSnapshot.books.filter { it.category.id == categoryId }
        val replacementCategory = if (affectedBooks.isNotEmpty()) {
            val targetId = reassignTargetCategoryId
            if (targetId.isNullOrBlank() || targetId == categoryId) {
                return "This category contains ${affectedBooks.size} book(s). Choose a category to move them into before deleting."
            }
            stateSnapshot.findCategoryById(targetId)
                ?: return "Selected reassignment category was not found."
        } else {
            null
        }

        val cloudSyncedAffectedIds = affectedBooks.filter { it.isSyncedToCloud }.map { it.id }
        val cloudRes = firestoreCatalogRepository.deleteCategoryAndReassign(
            categoryId = categoryId,
            reassignToCategory = replacementCategory,
            affectedBookIds = cloudSyncedAffectedIds,
        )
        cloudRes.getOrElse { err ->
            return err.localizedMessage ?: "Could not delete category in Firestore."
        }

        _uiState.update { state ->
            val remainingCategories = state.categories
                .filterNot { it.id == categoryId }
                .mapIndexed { idx, cat -> cat.copy(sortOrder = idx) }
            val updatedBooks = if (replacementCategory != null) {
                state.books.map { book ->
                    if (book.category.id == categoryId) book.copy(category = replacementCategory) else book
                }
            } else {
                state.books
            }
            catalogPersistence.saveCategories(remainingCategories)
            catalogPersistence.saveBooks(updatedBooks)

            val msg = if (replacementCategory != null) {
                "Moved ${affectedBooks.size} book(s) to ${replacementCategory.displayName} & deleted ${target.displayName}"
            } else {
                "Deleted category \"${target.displayName}\""
            }
            state.copy(
                categories = remainingCategories,
                books = updatedBooks,
                statusBannerMessage = msg,
            )
        }
        return null
    }

    fun addCategory(
        name: String,
        description: String,
        accentColor: Color,
        secondaryColor: Color,
        iconKey: String,
    ): String? {
        if (!requireAuthorizedAdmin()) return "Administrator authorization required."

        val cleanName = name.trim()
        if (cleanName.isEmpty()) return "Category name is required."
        if (_uiState.value.categories.any { it.displayName.equals(cleanName, ignoreCase = true) }) {
            return "A category named \"$cleanName\" already exists."
        }

        val cleanDesc = description.trim().ifEmpty { "Curated $cleanName PDF collection" }
        val shortName = formatShortCategoryName(cleanName)
        val newCategory = BookCategory(
            id = "cat_${UUID.randomUUID().toString().take(8)}",
            displayName = cleanName,
            shortName = shortName,
            description = cleanDesc,
            accentColor = accentColor,
            secondaryColor = secondaryColor,
            iconKey = iconKey,
            sortOrder = _uiState.value.categories.size,
        )

        _uiState.update { state ->
            val updatedCategories = state.categories + newCategory
            catalogPersistence.saveCategories(updatedCategories)
            state.copy(
                categories = updatedCategories,
                statusBannerMessage = "Added category \"$cleanName\"",
            )
        }
        return null
    }

    fun updateCategory(
        categoryId: String,
        name: String,
        description: String,
        accentColor: Color,
        secondaryColor: Color,
        iconKey: String,
    ): String? {
        if (!requireAuthorizedAdmin()) return "Administrator authorization required."

        val cleanName = name.trim()
        if (cleanName.isEmpty()) return "Category name is required."
        if (_uiState.value.categories.any {
                it.id != categoryId && it.displayName.equals(cleanName, ignoreCase = true)
            }
        ) {
            return "Another category named \"$cleanName\" already exists."
        }

        val existing = _uiState.value.findCategoryById(categoryId)
            ?: return "Category not found."

        val updatedCategory = existing.copy(
            displayName = cleanName,
            shortName = formatShortCategoryName(cleanName),
            description = description.trim().ifEmpty { existing.description },
            accentColor = accentColor,
            secondaryColor = secondaryColor,
            iconKey = iconKey,
        )

        _uiState.update { state ->
            val updatedCategories = state.categories.map {
                if (it.id == categoryId) updatedCategory else it
            }
            val updatedBooks = state.books.map { book ->
                if (book.category.id == categoryId) book.copy(category = updatedCategory) else book
            }
            catalogPersistence.saveCategories(updatedCategories)
            catalogPersistence.saveBooks(updatedBooks)
            state.copy(
                categories = updatedCategories,
                books = updatedBooks,
                statusBannerMessage = "Updated category \"$cleanName\"",
            )
        }
        return null
    }

    fun deleteCategory(
        categoryId: String,
        reassignTargetCategoryId: String? = null,
    ): String? {
        if (!requireAuthorizedAdmin()) return "Administrator authorization required."

        val stateSnapshot = _uiState.value
        val target = stateSnapshot.findCategoryById(categoryId)
            ?: return "Category not found."

        if (stateSnapshot.categories.size <= 1) {
            return "At least one category must remain in BookHub."
        }

        val affectedCount = stateSnapshot.books.count { it.category.id == categoryId }
        val replacementCategory = if (affectedCount > 0) {
            val targetId = reassignTargetCategoryId
            if (targetId.isNullOrBlank() || targetId == categoryId) {
                return "This category contains $affectedCount book(s). Choose a category to move them into before deleting."
            }
            stateSnapshot.findCategoryById(targetId)
                ?: return "Selected reassignment category was not found."
        } else {
            null
        }

        _uiState.update { state ->
            val remainingCategories = state.categories
                .filterNot { it.id == categoryId }
                .mapIndexed { idx, cat -> cat.copy(sortOrder = idx) }
            val updatedBooks = if (replacementCategory != null) {
                state.books.map { book ->
                    if (book.category.id == categoryId) book.copy(category = replacementCategory) else book
                }
            } else {
                state.books
            }
            catalogPersistence.saveCategories(remainingCategories)
            catalogPersistence.saveBooks(updatedBooks)

            val msg = if (replacementCategory != null) {
                "Moved $affectedCount book(s) to ${replacementCategory.displayName} & deleted ${target.displayName}"
            } else {
                "Deleted category \"${target.displayName}\""
            }
            state.copy(
                categories = remainingCategories,
                books = updatedBooks,
                statusBannerMessage = msg,
            )
        }
        return null
    }

    private fun formatShortCategoryName(fullName: String): String {
        val parts = fullName.split(" ").filter { it.isNotBlank() }
        return when {
            parts.size <= 1 -> fullName
            parts.size == 2 -> "${parts[0]}\n${parts[1]}"
            else -> "${parts[0]} ${parts[1]}\n${parts.drop(2).joinToString(" ")}"
        }
    }

    // endregion

    // region Reader & User Library Actions

    fun toggleHomeSearch() {
        _uiState.update {
            val next = !it.isHomeSearchActive
            it.copy(
                isHomeSearchActive = next,
                homeSearchQuery = if (!next) "" else it.homeSearchQuery,
            )
        }
    }

    fun updateHomeSearchQuery(query: String) {
        _uiState.update { it.copy(homeSearchQuery = query) }
    }

    fun clearHomeSearch() {
        _uiState.update { it.copy(homeSearchQuery = "") }
    }

    fun updateCategorySearchQuery(query: String) {
        _uiState.update { it.copy(categorySearchQuery = query) }
    }

    fun clearCategorySearch() {
        _uiState.update { it.copy(categorySearchQuery = "") }
    }

    fun resetListingControls() {
        _uiState.update {
            it.copy(
                listingSearchQuery = "",
                listingSortOption = BookSortOption.NEWEST,
            )
        }
    }

    fun updateListingSearchQuery(query: String) {
        _uiState.update { it.copy(listingSearchQuery = query) }
    }

    fun clearListingSearch() {
        _uiState.update { it.copy(listingSearchQuery = "") }
    }

    fun setListingSortOption(sortOption: BookSortOption) {
        _uiState.update { it.copy(listingSortOption = sortOption) }
    }

    fun toggleListingLayout() {
        _uiState.update { it.copy(isListingGridLayout = !it.isListingGridLayout) }
    }

    fun updateLibrarySearchQuery(query: String) {
        _uiState.update { it.copy(librarySearchQuery = query) }
    }

    fun clearLibrarySearch() {
        _uiState.update { it.copy(librarySearchQuery = "") }
    }

    fun setLibraryFilter(filter: LibraryFilter) {
        _uiState.update { it.copy(libraryFilter = filter) }
    }

    fun toggleFavorite(bookId: String) {
        _uiState.update { state ->
            var nowFavorite = false
            val updated = state.books.map { book ->
                if (book.id == bookId) {
                    nowFavorite = !book.isFavorite
                    book.copy(
                        isFavorite = nowFavorite,
                        isInLibrary = if (nowFavorite) true else book.isInLibrary,
                    )
                } else {
                    book
                }
            }
            favoritesPersistence.saveFavoriteIds(
                updated.filter { it.isFavorite }.map { it.id }.toSet(),
            )
            state.copy(
                books = updated,
                statusBannerMessage = if (nowFavorite) "Added to Favorites" else "Removed from Favorites",
            )
        }
    }

    fun toggleBookmark(bookId: String) {
        _uiState.update { state ->
            var toggledBook: Book? = null
            val updatedBooks = state.books.map { book ->
                if (book.id == bookId) {
                    val nextBook = book.copy(isBookmarked = !book.isBookmarked)
                    toggledBook = nextBook
                    nextBook
                } else {
                    book
                }
            }
            val updatedNotes = toggledBook?.let { book ->
                if (book.isBookmarked && state.bookmarkNotes.none { it.bookId == bookId }) {
                    val targetPage = maxOf(1, book.lastReadPage)
                    listOf(
                        BookmarkNote(
                            id = "bm_${book.id}_$targetPage",
                            bookId = book.id,
                            pageNumber = targetPage,
                            chapterTitle = "Page $targetPage • ${book.title}",
                            notePreview = "Bookmarked in ${book.category.displayName}",
                            savedDateLabel = "Just now",
                        ),
                    ) + state.bookmarkNotes
                } else if (!book.isBookmarked) {
                    state.bookmarkNotes.filterNot { it.bookId == bookId }
                } else {
                    state.bookmarkNotes
                }
            } ?: state.bookmarkNotes

            readerPersistence.saveBookmarks(updatedNotes)
            val message = if (toggledBook?.isBookmarked == true) {
                "Added to Bookmarks"
            } else {
                "Removed from Bookmarks"
            }
            state.copy(
                books = updatedBooks,
                bookmarkNotes = updatedNotes,
                statusBannerMessage = message,
            )
        }
    }

    fun togglePageBookmark(bookId: String, pageNumber: Int) {
        val safePage = maxOf(1, pageNumber)
        _uiState.update { state ->
            val targetBook = state.books.find { it.id == bookId } ?: return@update state
            val existing = state.bookmarkNotes.find { it.bookId == bookId && it.pageNumber == safePage }
            val updatedNotes = if (existing != null) {
                state.bookmarkNotes.filterNot { it.id == existing.id }
            } else {
                listOf(
                    BookmarkNote(
                        id = "bm_${bookId}_$safePage",
                        bookId = bookId,
                        pageNumber = safePage,
                        chapterTitle = "Page $safePage • ${targetBook.title}",
                        notePreview = "Saved PDF page $safePage",
                        savedDateLabel = "Just now",
                    ),
                ) + state.bookmarkNotes
            }
            val hasAnyForBook = updatedNotes.any { it.bookId == bookId }
            val updatedBooks = state.books.map { book ->
                if (book.id == bookId) book.copy(isBookmarked = hasAnyForBook) else book
            }
            readerPersistence.saveBookmarks(updatedNotes)
            state.copy(
                books = updatedBooks,
                bookmarkNotes = updatedNotes,
                statusBannerMessage = if (existing != null) {
                    "Removed bookmark on page $safePage"
                } else {
                    "Bookmarked page $safePage"
                },
            )
        }
    }

    fun toggleInLibrary(bookId: String) {
        _uiState.update { state ->
            var nowInLibrary = false
            val updated = state.books.map { book ->
                if (book.id == bookId) {
                    nowInLibrary = !book.isInLibrary
                    book.copy(isInLibrary = nowInLibrary)
                } else {
                    book
                }
            }
            state.copy(
                books = updated,
                statusBannerMessage = if (nowInLibrary) "Saved to My Library" else "Removed from My Library",
            )
        }
    }

    fun recordBookOpened(bookId: String) {
        _uiState.update { state ->
            val seq = state.nextOpenSequence
            val updatedBooks = state.books.map { book ->
                if (book.id == bookId) {
                    val nextPage = if (book.lastReadPage == 0) 1 else book.lastReadPage
                    val total = book.pageCount ?: maxOf(1, nextPage)
                    val nextProgress = (nextPage.toFloat() / total.toFloat()).coerceIn(0.05f, 1f)
                    book.copy(
                        lastOpenedOrder = seq,
                        lastReadPage = nextPage,
                        readingProgress = nextProgress,
                        isInLibrary = true,
                    )
                } else {
                    book
                }
            }
            persistProgressFromBooks(updatedBooks)
            state.copy(
                books = updatedBooks,
                nextOpenSequence = seq + 1,
            )
        }
    }

    fun updateBookReadingProgress(bookId: String, currentPage: Int, totalPages: Int) {
        if (totalPages <= 0) return
        val safePage = currentPage.coerceIn(1, totalPages)
        val fraction = (safePage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)

        _uiState.update { state ->
            val existing = state.books.find { it.id == bookId } ?: return@update state
            if (existing.lastReadPage == safePage &&
                existing.pageCount == totalPages &&
                existing.readingProgress == fraction &&
                existing.isInLibrary
            ) {
                return@update state
            }
            val seq = state.nextOpenSequence
            val updatedBooks = state.books.map { book ->
                if (book.id == bookId) {
                    book.copy(
                        lastReadPage = safePage,
                        pageCount = totalPages,
                        readingProgress = fraction,
                        lastOpenedOrder = seq,
                        isInLibrary = true,
                    )
                } else {
                    book
                }
            }
            persistProgressFromBooks(updatedBooks)
            state.copy(
                books = updatedBooks,
                nextOpenSequence = seq + 1,
            )
        }
    }

    fun clearReadingHistory() {
        _uiState.update { state ->
            val updatedBooks = state.books.map {
                it.copy(lastOpenedOrder = 0L, readingProgress = 0f, lastReadPage = 0)
            }
            readerPersistence.saveProgress(emptyMap())
            state.copy(
                books = updatedBooks,
                statusBannerMessage = "Reading history cleared",
            )
        }
    }

    fun removeBookmarkNote(noteId: String) {
        _uiState.update { state ->
            val target = state.bookmarkNotes.find { it.id == noteId }
            val remainingNotes = state.bookmarkNotes.filterNot { it.id == noteId }
            val updatedBooks = if (target != null && remainingNotes.none { it.bookId == target.bookId }) {
                state.books.map { if (it.id == target.bookId) it.copy(isBookmarked = false) else it }
            } else {
                state.books
            }
            readerPersistence.saveBookmarks(remainingNotes)
            state.copy(
                books = updatedBooks,
                bookmarkNotes = remainingNotes,
                statusBannerMessage = "Bookmark removed",
            )
        }
    }

    fun markAllNotificationsRead() {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map { it.copy(isUnread = false) },
            )
        }
    }

    fun toggleReaderNightMode() {
        _uiState.update {
            val next = !it.readerNightMode
            readerPersistence.saveNightMode(next)
            it.copy(readerNightMode = next)
        }
    }

    fun showStatusMessage(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    // endregion

    override fun onCleared() {
        authObserverJob?.cancel()
        categoriesListenerJob?.cancel()
        booksListenerJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "BookHubViewModel"
    }
}
