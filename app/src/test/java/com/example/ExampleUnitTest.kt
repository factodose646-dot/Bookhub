package com.example

import androidx.compose.ui.graphics.Color
import com.example.auth.AdminAuthResult
import com.example.auth.AdminAuthStatus
import com.example.auth.InMemoryAdminAuthPersistence
import com.example.auth.LocalPasscodeAdminAuthGateway
import com.example.cloud.FirestoreBookDto
import com.example.cloud.FirestoreCategoryDto
import com.example.cloud.GoogleDriveGateway
import com.example.data.BookCatalogQueries
import com.example.data.BookSampleData
import com.example.data.InMemoryCatalogPersistence
import com.example.data.InMemoryFavoritesPersistence
import com.example.data.InMemoryReaderStatePersistence
import com.example.model.BookCategory
import com.example.model.BookListingType
import com.example.model.BookSortOption
import com.example.model.LibraryFilter
import com.example.pdf.PdfSource
import com.example.pdf.PdfSourceResolver
import com.example.pdf.PdfValidator
import com.example.ui.BookHubUiState
import com.example.ui.BookHubViewModel
import com.example.ui.CatalogCloudMerger
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun allEightCategories_arePresentAndHaveMultipleBooksForRelatedSection() {
        val books = BookSampleData.books
        assertEquals(8, BookCategory.entries.size)
        BookCategory.entries.forEach { category ->
            val count = books.count { it.category == category }
            assertTrue("Expected at least 2 sample books in ${category.displayName}", count >= 2)
        }
    }

    @Test
    fun sortingOptions_orderBooksCorrectly() {
        val books = BookSampleData.books
        val newest = BookCatalogQueries.sortBooks(books, BookSortOption.NEWEST)
        val oldest = BookCatalogQueries.sortBooks(books, BookSortOption.OLDEST)
        val az = BookCatalogQueries.sortBooks(books, BookSortOption.A_TO_Z)

        assertTrue(newest.first().publishedYear >= newest.last().publishedYear)
        assertTrue(oldest.first().publishedYear <= oldest.last().publishedYear)
        assertTrue(az.first().title.lowercase() <= az.last().title.lowercase())
        assertEquals("Alpine Trail Sketchbook", az.first().title)
    }

    @Test
    fun search_matchesByTitleAndCategory_andReturnsEmptyForUnknownQuery() {
        val books = BookSampleData.books

        val byTitle = BookCatalogQueries.filterByQuery(books, "Horizon")
        assertTrue(byTitle.any { it.title == "The Lost Horizon" })

        val byCategory = BookCatalogQueries.filterByQuery(books, "Science & Knowledge")
        assertTrue(byCategory.isNotEmpty())
        assertTrue(byCategory.all { it.category == BookCategory.SCIENCE_KNOWLEDGE })

        val none = BookCatalogQueries.filterByQuery(books, "NonexistentXYZ123")
        assertTrue(none.isEmpty())
    }

    @Test
    fun homeSectionListings_returnExpectedSubsets() {
        val books = BookSampleData.books

        val featured = BookCatalogQueries.filterForListing(
            books,
            BookListingType.FEATURED,
            null,
            "",
            BookSortOption.NEWEST,
        )
        assertTrue(featured.isNotEmpty())
        assertTrue(featured.all { it.isFeatured })

        val popular = BookCatalogQueries.filterForListing(
            books,
            BookListingType.POPULAR,
            null,
            "",
            BookSortOption.NEWEST,
        )
        assertTrue(popular.isNotEmpty())
        assertTrue(popular.all { it.isPopular })

        val recent = BookCatalogQueries.filterForListing(
            books,
            BookListingType.RECENT,
            null,
            "",
            BookSortOption.NEWEST,
        )
        assertTrue(recent.isNotEmpty())
        assertTrue(recent.all { it.isRecent })
    }

    @Test
    fun relatedBooks_matchCategoryAndExcludeCurrentBook() {
        val books = BookSampleData.books
        books.forEach { current ->
            val related = BookCatalogQueries.getRelatedBooks(books, current)
            assertTrue("Expected related books for ${current.title}", related.isNotEmpty())
            assertFalse(related.any { it.id == current.id })
            assertTrue(related.all { it.category == current.category })
        }
    }

    @Test
    fun missingBookFields_areRepresentedSafelyInSampleCatalog() {
        val placeholderBook = BookSampleData.books.first { it.id == "book_19" }
        assertNull(placeholderBook.description)
        assertNull(placeholderBook.coverStyle)
        assertNull(placeholderBook.pageCount)
        assertNull(placeholderBook.dateAdded)
        assertNull(placeholderBook.pdfSourceUrl)
        assertNull(placeholderBook.pdfAssetPath)
        assertNull(PdfSourceResolver.resolve(placeholderBook))
    }

    @Test
    fun favoritesPersistence_syncsAcrossViewModelsAndLibrary() {
        val sharedStore = InMemoryFavoritesPersistence()
        val vm1 = BookHubViewModel(favoritesPersistence = sharedStore)

        val targetId = "book_7"
        assertFalse(vm1.uiState.value.books.first { it.id == targetId }.isFavorite)
        vm1.toggleFavorite(targetId)
        assertTrue(vm1.uiState.value.books.first { it.id == targetId }.isFavorite)

        // New ViewModel instance backed by same local persistence must restore favorite status
        val vm2 = BookHubViewModel(favoritesPersistence = sharedStore)
        vm2.setLibraryFilter(LibraryFilter.FAVORITES)
        assertTrue(vm2.uiState.value.books.first { it.id == targetId }.isFavorite)
        assertTrue(vm2.uiState.value.libraryBooks.any { it.id == targetId })

        // Reading history & bookmarks check
        vm2.recordBookOpened(targetId)
        vm2.setLibraryFilter(LibraryFilter.HISTORY)
        assertEquals(targetId, vm2.uiState.value.libraryBooks.first().id)

        vm2.toggleBookmark(targetId)
        vm2.setLibraryFilter(LibraryFilter.BOOKMARKS)
        assertTrue(vm2.uiState.value.libraryBooks.any { it.id == targetId })

        vm2.clearReadingHistory()
        vm2.setLibraryFilter(LibraryFilter.HISTORY)
        assertTrue(vm2.uiState.value.libraryBooks.isEmpty())
    }

    @Test
    fun pdfSourceResolver_supportsLocalAssetRemoteHttpsLocalFileAndGoogleDriveRef() {
        val localAssetBook = BookSampleData.books.first { it.id == "book_1" }
        val resolvedAsset = PdfSourceResolver.resolve(localAssetBook)
        assertTrue(resolvedAsset is PdfSource.LocalAsset)
        assertEquals("pdfs/the_lost_horizon.pdf", (resolvedAsset as PdfSource.LocalAsset).assetPath)

        val remoteHttpsBook = localAssetBook.copy(
            id = "remote_1",
            pdfAssetPath = null,
            pdfSourceUrl = "https://example.org/sample.pdf",
        )
        assertEquals(
            PdfSource.RemoteHttps("https://example.org/sample.pdf"),
            PdfSourceResolver.resolve(remoteHttpsBook),
        )

        val localFileBook = localAssetBook.copy(
            id = "file_1",
            pdfAssetPath = null,
            pdfSourceUrl = "file:///storage/emulated/0/Download/book.pdf",
        )
        assertEquals(
            PdfSource.LocalFile("/storage/emulated/0/Download/book.pdf"),
            PdfSourceResolver.resolve(localFileBook),
        )

        val driveUrlBook = localAssetBook.copy(
            id = "drive_1",
            pdfAssetPath = null,
            pdfSourceUrl = "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQr/view?usp=sharing",
        )
        val resolvedDrive = PdfSourceResolver.resolve(driveUrlBook)
        assertTrue(resolvedDrive is PdfSource.GoogleDriveRef)
        assertEquals("1AbCdEfGhIjKlMnOpQr", (resolvedDrive as PdfSource.GoogleDriveRef).fileId)

        val driveOpenBook = driveUrlBook.copy(
            pdfSourceUrl = "https://drive.google.com/open?id=9XyZ_fileId123",
        )
        val resolvedOpenDrive = PdfSourceResolver.resolve(driveOpenBook)
        assertTrue(resolvedOpenDrive is PdfSource.GoogleDriveRef)
        assertEquals("9XyZ_fileId123", (resolvedOpenDrive as PdfSource.GoogleDriveRef).fileId)

        val driveIdBook = driveUrlBook.copy(
            pdfSourceUrl = null,
            googleDriveFileId = "explicitDriveId77",
        )
        val resolvedExplicitDrive = PdfSourceResolver.resolve(driveIdBook)
        assertTrue(resolvedExplicitDrive is PdfSource.GoogleDriveRef)
        assertEquals("explicitDriveId77", (resolvedExplicitDrive as PdfSource.GoogleDriveRef).fileId)
    }

    @Test
    fun pdfValidator_identifiesValidAndInvalidHeaders() {
        assertTrue(PdfValidator.hasValidPdfHeader("%PDF-1.4\n1 0 obj".toByteArray()))
        assertTrue(PdfValidator.hasValidPdfHeader("\u0000\u0000%PDF-1.7\n".toByteArray()))
        assertFalse(PdfValidator.hasValidPdfHeader("<html><body>404</body></html>".toByteArray()))
        assertFalse(PdfValidator.hasValidPdfHeader("PDF".toByteArray()))
    }

    @Test
    fun readerProgressBookmarksAndNightMode_persistAcrossViewModelRestarts() {
        val favStore = InMemoryFavoritesPersistence()
        val readerStore = InMemoryReaderStatePersistence()

        val vm1 = BookHubViewModel(
            favoritesPersistence = favStore,
            readerPersistence = readerStore,
        )

        // Update reading progress for book_4 (Quantum Light, 5 pages)
        vm1.updateBookReadingProgress(bookId = "book_4", currentPage = 4, totalPages = 5)
        // Toggle page bookmark on page 3 and page 4
        vm1.togglePageBookmark(bookId = "book_4", pageNumber = 3)
        vm1.togglePageBookmark(bookId = "book_4", pageNumber = 4)
        // Toggle night mode on
        vm1.toggleReaderNightMode()

        // Restart ViewModel with same persistence stores
        val vm2 = BookHubViewModel(
            favoritesPersistence = favStore,
            readerPersistence = readerStore,
        )

        val restoredBook = vm2.uiState.value.books.first { it.id == "book_4" }
        assertEquals(4, restoredBook.lastReadPage)
        assertEquals(5, restoredBook.pageCount)
        assertEquals(0.8f, restoredBook.readingProgress, 0.001f)
        assertTrue(vm2.uiState.value.continueReadingBooks.any { it.id == "book_4" })

        val book4Bookmarks = vm2.uiState.value.getBookmarksForBook("book_4")
        assertEquals(listOf(3, 4), book4Bookmarks.map { it.pageNumber })
        assertTrue(vm2.uiState.value.isPageBookmarked("book_4", 3))
        assertTrue(vm2.uiState.value.readerNightMode)

        // Removing a page bookmark updates both notes and persisted store
        vm2.togglePageBookmark(bookId = "book_4", pageNumber = 3)
        val vm3 = BookHubViewModel(
            favoritesPersistence = favStore,
            readerPersistence = readerStore,
        )
        assertEquals(listOf(4), vm3.uiState.value.getBookmarksForBook("book_4").map { it.pageNumber })
    }

    @Test
    fun adminAuth_enforcesSetupValidationCooldownAndMutationProtection() {
        val authPersistence = InMemoryAdminAuthPersistence()
        val gateway = LocalPasscodeAdminAuthGateway(authPersistence)
        val vm = BookHubViewModel(
            adminAuthGateway = gateway,
        )

        // Initially requires first-time admin setup & rejects all mutations
        assertTrue(vm.uiState.value.adminAuthStatus is AdminAuthStatus.NeedsFirstTimeSetup)
        assertFalse(vm.uiState.value.isAdminAuthenticated)

        val initialBookCount = vm.uiState.value.books.size
        val initialCategoryCount = vm.uiState.value.categories.size
        assertNotNull(
            vm.addAdminBook(
                title = "Unauthorized Book",
                author = "Nobody",
                description = "Blocked",
                categoryId = BookCategory.SCIENCE_KNOWLEDGE.id,
                pdfFilePath = "/tmp/fake.pdf",
                pdfByteSize = 1024L,
                pdfFormattedSize = "1 KB",
                pdfPageCount = 4,
                coverImagePath = null,
            ),
        )
        assertNotNull(
            vm.addCategory(
                name = "Blocked Category",
                description = "Should fail",
                accentColor = Color.Red,
                secondaryColor = Color.DarkGray,
                iconKey = BookCategory.ICON_OTHER,
            ),
        )
        assertFalse(vm.deleteAdminBook("book_1"))
        assertEquals(initialBookCount, vm.uiState.value.books.size)
        assertEquals(initialCategoryCount, vm.uiState.value.categories.size)

        // Rejects short passcode and mismatched confirm passcode
        assertFalse(vm.setupFirstAdmin("Admin", "admin@bookhub.local", "123", "123"))
        assertFalse(vm.setupFirstAdmin("Admin", "admin@bookhub.local", "emerald99", "mismatch99"))
        assertFalse(vm.uiState.value.isAdminAuthenticated)

        // Valid setup authenticates the admin
        assertTrue(vm.setupFirstAdmin("Chief Librarian", "admin@bookhub.local", "emerald99", "emerald99"))
        assertTrue(vm.uiState.value.isAdminAuthenticated)
        assertTrue(vm.uiState.value.adminAuthStatus is AdminAuthStatus.Authenticated)

        // Duplicate setup is rejected at gateway level
        assertTrue(gateway.setupFirstAdmin("Hacker", "", "other123") is AdminAuthResult.Failure)

        // Logout revokes session & locks mutations again
        vm.logoutAdmin()
        assertFalse(vm.uiState.value.isAdminAuthenticated)
        assertTrue(vm.uiState.value.adminAuthStatus is AdminAuthStatus.Locked)
        assertNotNull(
            vm.addCategory("Blocked After Logout", "Desc", Color.Blue, Color.Black, BookCategory.ICON_OTHER),
        )

        // Failed login attempts trigger 30s cooldown on 5th failure
        repeat(4) {
            assertTrue(gateway.authenticate("wrongpass", nowMillis = 1_000L) is AdminAuthResult.Failure)
        }
        val fifthFailure = gateway.authenticate("wrongpass", nowMillis = 1_000L) as AdminAuthResult.Failure
        assertEquals(30, fifthFailure.cooldownSeconds)

        // Even correct passcode is blocked while cooldown is active
        val duringLockout = gateway.authenticate("emerald99", nowMillis = 10_000L) as AdminAuthResult.Failure
        assertTrue(duringLockout.cooldownSeconds > 0)

        // Correct passcode works once cooldown expires
        val afterCooldown = gateway.authenticate("emerald99", nowMillis = 35_000L)
        assertTrue(afterCooldown is AdminAuthResult.Success)

        // ViewModel login + passcode change invalidates old passcode
        assertTrue(vm.loginAdmin("emerald99"))
        assertTrue(vm.changeAdminPasscode("emerald99", "goldpass88", "goldpass88"))
        vm.logoutAdmin()
        assertFalse(vm.loginAdmin("emerald99"))
        assertTrue(vm.loginAdmin("goldpass88"))
    }

    @Test
    fun adminBookCrud_updatesCatalogSearchReaderSourceAndPersistsAcrossRestarts() {
        val catalogStore = InMemoryCatalogPersistence()
        val favStore = InMemoryFavoritesPersistence()
        val readerStore = InMemoryReaderStatePersistence()
        val authStore = InMemoryAdminAuthPersistence()

        val vm1 = BookHubViewModel(
            favoritesPersistence = favStore,
            readerPersistence = readerStore,
            catalogPersistence = catalogStore,
            adminAuthGateway = LocalPasscodeAdminAuthGateway(authStore),
        )

        assertTrue(vm1.setupFirstAdmin("Librarian", "", "securePass1", "securePass1"))
        val createError = vm1.addAdminBook(
            title = "Atlas of Emerald Peaks",
            author = "Dr. Lin",
            description = "Geological field survey in PDF format.",
            categoryId = BookCategory.SCIENCE_KNOWLEDGE.id,
            pdfFilePath = "/data/user/0/com.example/files/bookhub_pdfs/atlas.pdf",
            pdfByteSize = 2_097_152L,
            pdfFormattedSize = "2.0 MB",
            pdfPageCount = 48,
            coverImagePath = "/data/user/0/com.example/files/bookhub_covers/atlas.jpg",
            isFeatured = true,
            isPopular = false,
            isRecent = true,
        )
        assertNull(createError)

        val createdBook = vm1.uiState.value.books.first { it.title == "Atlas of Emerald Peaks" }
        assertTrue(createdBook.isAdminUploaded)
        assertEquals(48, createdBook.pageCount)
        assertEquals(
            PdfSource.LocalFile("/data/user/0/com.example/files/bookhub_pdfs/atlas.pdf"),
            PdfSourceResolver.resolve(createdBook),
        )
        assertTrue(vm1.uiState.value.recentBooks.any { it.id == createdBook.id })
        assertTrue(BookCatalogQueries.filterByQuery(vm1.uiState.value.books, "Emerald Peaks").any { it.id == createdBook.id })
        assertTrue(
            vm1.uiState.value.getBooksForListing(BookListingType.CATEGORY, BookCategory.SCIENCE_KNOWLEDGE)
                .any { it.id == createdBook.id },
        )

        // User favorites, reads, and bookmarks the admin-uploaded book
        vm1.toggleFavorite(createdBook.id)
        vm1.updateBookReadingProgress(createdBook.id, currentPage = 12, totalPages = 48)
        vm1.togglePageBookmark(createdBook.id, pageNumber = 12)

        // Simulate app restart with new ViewModel backed by same persistence stores
        val vm2 = BookHubViewModel(
            favoritesPersistence = favStore,
            readerPersistence = readerStore,
            catalogPersistence = catalogStore,
            adminAuthGateway = LocalPasscodeAdminAuthGateway(authStore),
        )
        // New VM must require re-login for admin actions
        assertFalse(vm2.uiState.value.isAdminAuthenticated)
        assertTrue(vm2.uiState.value.adminAuthStatus is AdminAuthStatus.Locked)

        val restored = vm2.uiState.value.books.first { it.id == createdBook.id }
        assertTrue(restored.isFavorite)
        assertEquals(12, restored.lastReadPage)
        assertEquals(48, restored.pageCount)
        assertTrue(vm2.uiState.value.isPageBookmarked(createdBook.id, 12))

        // Log back in and edit metadata while preserving progress & favorites
        assertTrue(vm2.loginAdmin("securePass1"))
        val editError = vm2.updateAdminBook(
            bookId = createdBook.id,
            title = "Atlas of Emerald Peaks (2nd Ed.)",
            author = "Dr. Lin",
            description = "Revised expedition report.",
            categoryId = BookCategory.HISTORY_BIOGRAPHY.id,
            isFeatured = true,
            isPopular = true,
            isRecent = true,
        )
        assertNull(editError)
        val edited = vm2.uiState.value.books.first { it.id == createdBook.id }
        assertEquals("Atlas of Emerald Peaks (2nd Ed.)", edited.title)
        assertEquals(BookCategory.HISTORY_BIOGRAPHY.id, edited.category.id)
        assertTrue(edited.isFavorite)
        assertEquals(12, edited.lastReadPage)

        // Delete removes book and cleans up its bookmark notes
        assertTrue(vm2.deleteAdminBook(createdBook.id))
        assertFalse(vm2.uiState.value.books.any { it.id == createdBook.id })
        assertTrue(vm2.uiState.value.getBookmarksForBook(createdBook.id).isEmpty())
    }

    @Test
    fun adminCategoryCrud_enforcesSafeReassignmentAndPersistsAcrossRestarts() {
        val catalogStore = InMemoryCatalogPersistence()
        val authStore = InMemoryAdminAuthPersistence()

        val vm1 = BookHubViewModel(
            catalogPersistence = catalogStore,
            adminAuthGateway = LocalPasscodeAdminAuthGateway(authStore),
        )
        assertTrue(vm1.setupFirstAdmin("Admin", "", "passcode99", "passcode99"))

        // Add custom category
        assertNull(
            vm1.addCategory(
                name = "Design & Architecture",
                description = "Visual systems and blueprints",
                accentColor = Color(0xFF176B4C),
                secondaryColor = Color(0xFF092E20),
                iconKey = BookCategory.ICON_TECH,
            ),
        )
        val newCat = vm1.uiState.value.categories.first { it.displayName == "Design & Architecture" }

        // Add book into the new category
        assertNull(
            vm1.addAdminBook(
                title = "Grid Systems PDF",
                author = "K. Muller",
                description = "Layout principles.",
                categoryId = newCat.id,
                pdfFilePath = "/tmp/grids.pdf",
                pdfByteSize = 512_000L,
                pdfFormattedSize = "500 KB",
                pdfPageCount = 30,
                coverImagePath = null,
            ),
        )

        // Rename/update category and verify book's category stays synchronized
        assertNull(
            vm1.updateCategory(
                categoryId = newCat.id,
                name = "Architecture & Grids",
                description = "Updated description",
                accentColor = newCat.accentColor,
                secondaryColor = newCat.secondaryColor,
                iconKey = BookCategory.ICON_HISTORY,
            ),
        )
        val renamedBook = vm1.uiState.value.books.first { it.title == "Grid Systems PDF" }
        assertEquals("Architecture & Grids", renamedBook.category.displayName)

        // Deleting non-empty category without reassignment must fail safely
        val blockedDeleteError = vm1.deleteCategory(newCat.id, reassignTargetCategoryId = null)
        assertNotNull(blockedDeleteError)
        assertTrue(vm1.uiState.value.categories.any { it.id == newCat.id })

        // Deleting with valid reassignment target moves the book and removes the category
        val reassignError = vm1.deleteCategory(
            categoryId = newCat.id,
            reassignTargetCategoryId = BookCategory.TECH_EDUCATION.id,
        )
        assertNull(reassignError)
        assertFalse(vm1.uiState.value.categories.any { it.id == newCat.id })
        val reassignedBook = vm1.uiState.value.books.first { it.title == "Grid Systems PDF" }
        assertEquals(BookCategory.TECH_EDUCATION.id, reassignedBook.category.id)

        // Restart ViewModel and confirm updated categories & reassigned book persist
        val vm2 = BookHubViewModel(
            catalogPersistence = catalogStore,
            adminAuthGateway = LocalPasscodeAdminAuthGateway(authStore),
        )
        assertFalse(vm2.uiState.value.categories.any { it.id == newCat.id })
        assertEquals(
            BookCategory.TECH_EDUCATION.id,
            vm2.uiState.value.books.first { it.title == "Grid Systems PDF" }.category.id,
        )
    }

    @Test
    fun draftBooks_areExcludedFromReaderViewsButVisibleToAdmin() {
        val sample = BookSampleData.books.first { it.id == "book_1" }
        val draftBook = sample.copy(
            id = "book_draft_1",
            title = "Secret Upcoming Edition",
            isFeatured = true,
            isPopular = true,
            isRecent = true,
            isPublished = false,
            isFavorite = true,
            isBookmarked = true,
            isInLibrary = true,
        )
        val state = BookHubUiState(
            books = BookSampleData.books + draftBook,
        )

        assertFalse(state.publishedBooks.any { it.id == draftBook.id })
        assertFalse(state.featuredBooks.any { it.id == draftBook.id })
        assertFalse(state.popularBooks.any { it.id == draftBook.id })
        assertFalse(state.recentBooks.any { it.id == draftBook.id })
        assertFalse(state.favoriteBooks.any { it.id == draftBook.id })
        assertFalse(state.getBooksForListing(BookListingType.ALL, null).any { it.id == draftBook.id })
        assertFalse(state.getRelatedBooks(sample).any { it.id == draftBook.id })

        // Still exposed in unfiltered admin catalog
        assertTrue(state.books.any { it.id == draftBook.id })
        assertTrue(state.recentlyAddedBooksForAdmin.any { it.id == draftBook.id })
    }

    @Test
    fun catalogCloudMerger_preservesReaderStateAndUnsyncedBooks_whileDroppingRemovedCloudBooks() {
        val sample1 = BookSampleData.books.first { it.id == "book_1" }.copy(
            isFavorite = true,
            isBookmarked = true,
            lastReadPage = 7,
            readingProgress = 0.7f,
            lastOpenedOrder = 900L,
            isSyncedToCloud = false,
        )
        val syncedPreviously = BookSampleData.books.first { it.id == "book_2" }.copy(
            isFavorite = true,
            lastReadPage = 4,
            readingProgress = 0.4f,
            lastOpenedOrder = 850L,
            isSyncedToCloud = true,
        )
        val deletedCloudBook = BookSampleData.books.first { it.id == "book_3" }.copy(
            isSyncedToCloud = true,
        )

        val incomingDtos = listOf(
            FirestoreBookDto(
                id = "book_2",
                title = "Synced Updated Title",
                author = syncedPreviously.author,
                categoryId = syncedPreviously.category.id,
                driveFileId = "driveFileAbc123",
                pdfUrl = GoogleDriveGateway.buildDirectDownloadUrl("driveFileAbc123"),
                fileSize = "3.4 MB",
                fileSizeBytes = 3_565_158L,
                pageCount = 10L,
                published = true,
            ),
        )

        val merged = CatalogCloudMerger.mergeCloudBooksPreservingReaderState(
            existingBooks = listOf(sample1, syncedPreviously, deletedCloudBook),
            cloudDtos = incomingDtos,
            categories = BookCategory.entries,
        )

        val mergedBook2 = merged.first { it.id == "book_2" }
        assertEquals("Synced Updated Title", mergedBook2.title)
        assertTrue(mergedBook2.isFavorite)
        assertEquals(4, mergedBook2.lastReadPage)
        assertEquals(0.4f, mergedBook2.readingProgress, 0.001f)
        assertTrue(mergedBook2.isSyncedToCloud)
        assertEquals("driveFileAbc123", mergedBook2.googleDriveFileId)

        // Deleted cloud book removed, unsynced local book kept
        assertFalse(merged.any { it.id == "book_3" })
        assertTrue(merged.any { it.id == "book_1" })
    }

    @Test
    fun firestoreDtos_maintainRulesParityAndDeleteClearedOptionalFields() {
        val sample = BookSampleData.books.first { it.id == "book_1" }.copy(
            title = "A".repeat(250),
            author = "B".repeat(150),
            coverUrl = null,
            coverDriveFileId = null,
            googleDriveFileId = "drive123",
            pdfSourceUrl = GoogleDriveGateway.buildDirectDownloadUrl("drive123"),
            isFeatured = true,
            isPopular = false,
            isRecent = true,
            isPublished = false,
        )

        val createMap = FirestoreBookDto.toCreateMap(sample, adminUid = "admin_uid_1")
        assertFalse(createMap.containsKey("id"))
        assertEquals(200, (createMap["title"] as String).length)
        assertEquals(120, (createMap["author"] as String).length)
        assertEquals(true, createMap["featured"])
        assertEquals(false, createMap["popular"])
        assertEquals(true, createMap["recent"])
        assertEquals(false, createMap["published"])
        assertFalse(createMap.containsKey("isPublished"))
        assertFalse(createMap.containsKey("coverUrl"))

        val updateMap = FirestoreBookDto.toUpdateMap(sample, adminUid = "admin_uid_1")
        assertFalse(updateMap.containsKey("id"))
        assertFalse(updateMap.containsKey("createdAt"))
        assertFalse(updateMap.containsKey("createdByUid"))
        assertEquals(FieldValue.delete(), updateMap["coverUrl"])
        assertEquals(FieldValue.delete(), updateMap["coverDriveFileId"])

        val catMap = FirestoreCategoryDto.toCreateMap(BookCategory.SCIENCE_KNOWLEDGE)
        assertFalse(catMap.containsKey("id"))
        assertEquals(BookCategory.SCIENCE_KNOWLEDGE.displayName, catMap["name"])
    }

    @Test
    fun googleDriveUrlBuilders_formatExpectedUserContentUrls() {
        assertEquals(
            "https://drive.usercontent.google.com/download?id=abc_123&export=download",
            GoogleDriveGateway.buildDirectDownloadUrl("abc_123"),
        )
        assertEquals(
            "https://drive.usercontent.google.com/download?id=abc_123&export=view",
            GoogleDriveGateway.buildDirectViewUrl("abc_123"),
        )
    }

    @Test
    fun updateBookReadingProgress_ignoresDuplicatePageEmissionAndUnknownBookId() {
        val vm = BookHubViewModel()
        vm.updateBookReadingProgress("book_4", currentPage = 2, totalPages = 5)
        val seqAfterFirst = vm.uiState.value.nextOpenSequence

        // Re-emitting exact same page and total must not bump sequence or churn persistence
        vm.updateBookReadingProgress("book_4", currentPage = 2, totalPages = 5)
        assertEquals(seqAfterFirst, vm.uiState.value.nextOpenSequence)

        // Unknown bookId must leave state unchanged
        vm.updateBookReadingProgress("nonexistent_book_99", currentPage = 1, totalPages = 5)
        assertEquals(seqAfterFirst, vm.uiState.value.nextOpenSequence)
    }
}


