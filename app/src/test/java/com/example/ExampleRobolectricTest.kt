package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.auth.InMemoryAdminAuthPersistence
import com.example.auth.LocalPasscodeAdminAuthGateway
import com.example.data.BookSampleData
import com.example.data.InMemoryCatalogPersistence
import com.example.data.InMemoryFavoritesPersistence
import com.example.data.InMemoryReaderStatePersistence
import com.example.model.BookCategory
import com.example.pdf.PdfDocumentEngine
import com.example.pdf.PdfLoadError
import com.example.pdf.PdfLoadException
import com.example.pdf.PdfSource
import com.example.ui.BookHubApp
import com.example.ui.BookHubViewModel
import com.example.ui.screens.BookDetailsScreen
import com.example.ui.theme.BookHubTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createTestViewModel(
        context: Context = ApplicationProvider.getApplicationContext(),
    ): BookHubViewModel = BookHubViewModel(
        favoritesPersistence = InMemoryFavoritesPersistence(),
        readerPersistence = InMemoryReaderStatePersistence(),
        catalogPersistence = InMemoryCatalogPersistence(),
        adminAuthGateway = LocalPasscodeAdminAuthGateway(InMemoryAdminAuthPersistence()),
        applicationContext = context,
    )

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("BookHub", appName)
    }

    @Test
    fun `bottom navigation switches across Home Categories MyLibrary and Profile`() {
        val viewModel = createTestViewModel()

        composeTestRule.setContent {
            BookHubTheme {
                BookHubApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Featured Books").assertIsDisplayed()
        composeTestRule.onNodeWithText("Explore Categories").assertIsDisplayed()

        // Categories tab
        composeTestRule.onNodeWithTag("nav_categories").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Browse Categories").assertIsDisplayed()
        composeTestRule.onNodeWithText(BookCategory.SCIENCE_KNOWLEDGE.displayName).assertIsDisplayed()

        // My Library tab
        composeTestRule.onNodeWithTag("nav_library").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("My Library").assertIsDisplayed()
        composeTestRule.onNodeWithTag("library_filter_favorites").performClick()
        composeTestRule.waitForIdle()

        // Profile tab
        composeTestRule.onNodeWithTag("nav_profile").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_menu_admin").assertIsDisplayed()

        // Back to Home tab
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Featured Books").assertIsDisplayed()
    }

    @Test
    fun `home screen handles empty published catalog safely without crash`() {
        val viewModel = createTestViewModel()
        assertTrue(viewModel.setupFirstAdmin("Admin", "", "passcode123", "passcode123"))
        // Unpublish all books to simulate an empty cloud/public catalog
        viewModel.uiState.value.books.map { it.id }.forEach { bookId ->
            viewModel.deleteAdminBook(bookId)
        }
        assertTrue(viewModel.uiState.value.publishedBooks.isEmpty())

        composeTestRule.setContent {
            BookHubTheme {
                BookHubApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Home screen renders without throwing NoSuchElementException on empty featured carousel
        composeTestRule.onNodeWithText("Featured Books").assertIsDisplayed()
        composeTestRule.onNodeWithText("Explore Categories").assertIsDisplayed()
    }

    @Test
    fun `category listing search sort and book details actions work end to end`() {
        val viewModel = createTestViewModel()

        composeTestRule.setContent {
            BookHubTheme {
                BookHubApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Open search on Home and filter to "The Lost Horizon"
        composeTestRule.onNodeWithTag("home_search_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_search_input").performTextInput("Lost Horizon")
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("The Lost Horizon").onFirst().performClick()
        composeTestRule.waitForIdle()

        // Book Details screen opened
        composeTestRule.onNodeWithTag("details_read_now_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("details_favorite_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Go back and browse Categories -> Fiction listing with A-Z sort
        composeTestRule.onNodeWithTag("details_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_clear_search").performClick()
        composeTestRule.onNodeWithTag("home_search_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_categories").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(BookCategory.FICTIONAL_STORIES.displayName).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sort_chip_a_to_z").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("listing_layout_toggle").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `missing pdf book shows unavailable dialog on download tap`() {
        val placeholderBook = BookSampleData.books.first { it.id == "book_19" }

        composeTestRule.setContent {
            BookHubTheme {
                BookDetailsScreen(
                    book = placeholderBook,
                    relatedBooks = emptyList(),
                    onBack = {},
                    onReadNow = {},
                    onToggleFavorite = {},
                    onToggleBookmark = {},
                    onToggleLibrary = {},
                    onRelatedBookClick = {},
                    onRelatedBookToggleBookmark = {},
                    onSharePreview = {},
                    onShowMessage = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("details_download_pdf_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PDF Not Yet Attached").assertIsDisplayed()
    }

    @Test
    fun `admin panel gate blocks unauthenticated users and unlocks after setup`() {
        val viewModel = createTestViewModel()

        composeTestRule.setContent {
            BookHubTheme {
                BookHubApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_profile").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_menu_admin").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Admin Login screen should be displayed, not dashboard
        composeTestRule.onNodeWithText("Admin Access Setup").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_login_submit_button").assertIsDisplayed()

        // Complete first-time admin setup through the UI
        composeTestRule.onNodeWithTag("admin_passcode_input").performScrollTo().performTextInput("emerald99")
        composeTestRule.onNodeWithTag("admin_confirm_passcode_input").performScrollTo().performTextInput("emerald99")
        composeTestRule.onNodeWithTag("admin_login_submit_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Admin Dashboard is now accessible
        assertTrue(viewModel.uiState.value.isAdminAuthenticated)
        composeTestRule.onNodeWithTag("admin_quick_add_book").assertIsDisplayed()
    }

    @Test
    fun `pdf engine validates headers recovers corrupt cache and rejects missing or invalid sources`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = PdfDocumentEngine(context)

        // 1. Null/missing source returns MissingSource error
        var missingErr: PdfLoadError? = null
        try {
            engine.openDocument("missing_book", null)
        } catch (e: PdfLoadException) {
            missingErr = e.error
        }
        assertTrue(missingErr is PdfLoadError.MissingSource)

        // 2. Corrupt local file returns InvalidPdfFormat error
        val corruptFile = File(context.cacheDir, "test_corrupt.pdf").apply {
            writeText("<html>not a pdf</html>")
        }
        var invalidErr: PdfLoadError? = null
        try {
            engine.openDocument("corrupt_book", PdfSource.LocalFile(corruptFile.absolutePath))
        } catch (e: PdfLoadException) {
            invalidErr = e.error
        }
        corruptFile.delete()
        assertTrue(invalidErr is PdfLoadError.InvalidPdfFormat)

        // 3. Corrupt cached file for bundled asset is deleted and self-healed on prefetch
        val assetPath = "pdfs/the_lost_horizon.pdf"
        val cacheDir = File(context.cacheDir, "bookhub_pdfs").apply { mkdirs() }
        val expectedCacheFile = File(
            cacheDir,
            "book_1_${assetPath.hashCode().toUInt().toString(16)}.pdf",
        )
        expectedCacheFile.writeText("CORRUPTED_BYTES_NOT_A_PDF")
        assertFalse(engine.isCachedOffline("book_1", PdfSource.RemoteHttps("https://example.invalid/missing.pdf")))

        val prefetch = engine.prefetchPdfForOffline("book_1", PdfSource.LocalAsset(assetPath))
        assertTrue(prefetch.isSuccess)
        assertTrue((prefetch.getOrNull() ?: 0L) > 100L)
        assertTrue(expectedCacheFile.exists())
    }
}
