package com.example.ui

import android.content.Context
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.BookCategory
import com.example.model.BookListingType
import com.example.ui.components.BookHubBottomBar
import com.example.ui.components.BottomNavTab
import com.example.ui.screens.AdminAddEditBookScreen
import com.example.ui.screens.AdminCategoriesScreen
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.AdminManageBooksScreen
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.BookDetailsScreen
import com.example.ui.screens.BookListingScreen
import com.example.ui.screens.BookmarksScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MyLibraryScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Gold400

object BookHubRoutes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val MY_LIBRARY = "my_library"
    const val PROFILE = "profile"
    const val BOOKMARKS = "bookmarks"
    const val ADMIN_LOGIN = "admin_login"
    const val ADMIN_PANEL = "admin_panel"
    const val ADMIN_ADD_BOOK = "admin_add_book"
    const val ADMIN_EDIT_BOOK = "admin_edit_book/{bookId}"
    const val ADMIN_MANAGE_BOOKS = "admin_manage_books"
    const val ADMIN_CATEGORIES = "admin_categories"
    const val BOOK_LISTING = "book_listing/{listingType}/{categoryId}"
    const val BOOK_DETAILS = "book_details/{bookId}"
    const val READER_PREVIEW = "reader_preview/{bookId}?page={page}"

    fun adminEditBook(bookId: String) = "admin_edit_book/$bookId"

    fun bookListing(
        listingType: BookListingType,
        category: BookCategory? = null,
    ): String = "book_listing/${listingType.id}/${category?.id ?: "none"}"

    fun bookDetails(bookId: String) = "book_details/$bookId"
    fun readerPreview(bookId: String, targetPage: Int? = null): String =
        if (targetPage != null && targetPage > 0) {
            "reader_preview/$bookId?page=$targetPage"
        } else {
            "reader_preview/$bookId?page=-1"
        }
}

@Composable
fun BookHubApp(
    viewModel: BookHubViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusBannerMessage) {
        val message = uiState.statusBannerMessage
        if (message != null) {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
            viewModel.clearStatusMessage()
        }
    }

    val topLevelRoutes = remember {
        setOf(
            BookHubRoutes.HOME,
            BookHubRoutes.CATEGORIES,
            BookHubRoutes.MY_LIBRARY,
            BookHubRoutes.PROFILE,
        )
    }

    fun navigateToTab(tab: BottomNavTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openListing(listingType: BookListingType, category: BookCategory? = null) {
        viewModel.resetListingControls()
        navController.navigate(BookHubRoutes.bookListing(listingType, category))
    }

    fun openReader(bookId: String, targetPage: Int? = null) {
        viewModel.recordBookOpened(bookId)
        navController.navigate(BookHubRoutes.readerPreview(bookId, targetPage))
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Emerald900,
                    contentColor = Cream50,
                    actionColor = Gold400,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                BookHubBottomBar(
                    currentRoute = currentRoute,
                    onSelectTab = { tab -> navigateToTab(tab) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = BookHubRoutes.HOME,
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
                popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                popExitTransition = { fadeOut(animationSpec = tween(180)) },
            ) {
                composable(BookHubRoutes.HOME) {
                    HomeScreen(
                        uiState = uiState,
                        onToggleSearch = viewModel::toggleHomeSearch,
                        onSearchQueryChange = viewModel::updateHomeSearchQuery,
                        onClearSearch = viewModel::clearHomeSearch,
                        onBookClick = { bookId ->
                            navController.navigate(BookHubRoutes.bookDetails(bookId))
                        },
                        onReadNowClick = { bookId -> openReader(bookId) },
                        onCategoryClick = { category ->
                            openListing(BookListingType.CATEGORY, category)
                        },
                        onSeeAllCategories = {
                            navigateToTab(BottomNavTab.CATEGORIES)
                        },
                        onSeeAllListing = { listingType ->
                            openListing(listingType)
                        },
                        onToggleBookmark = viewModel::toggleBookmark,
                        onOpenBookmarks = {
                            navController.navigate(BookHubRoutes.BOOKMARKS)
                        },
                        onOpenLibrary = {
                            navigateToTab(BottomNavTab.MY_LIBRARY)
                        },
                        onOpenAdminPanel = {
                            navController.navigate(BookHubRoutes.ADMIN_PANEL)
                        },
                        onMarkNotificationsRead = viewModel::markAllNotificationsRead,
                    )
                }

                composable(BookHubRoutes.CATEGORIES) {
                    CategoriesScreen(
                        uiState = uiState,
                        onCategorySelected = { category ->
                            openListing(BookListingType.CATEGORY, category)
                        },
                        onSearchQueryChange = viewModel::updateCategorySearchQuery,
                        onClearSearch = viewModel::clearCategorySearch,
                        onBookClick = { bookId ->
                            navController.navigate(BookHubRoutes.bookDetails(bookId))
                        },
                    )
                }

                composable(
                    route = BookHubRoutes.BOOK_LISTING,
                    arguments = listOf(
                        navArgument("listingType") { type = NavType.StringType },
                        navArgument("categoryId") { type = NavType.StringType },
                    ),
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(260),
                        ) + fadeIn(tween(200))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(240),
                        ) + fadeOut(tween(180))
                    },
                ) { backStackEntry ->
                    val listingTypeId = backStackEntry.arguments?.getString("listingType") ?: "all"
                    val categoryId = backStackEntry.arguments?.getString("categoryId") ?: "none"
                    val listingType = BookListingType.fromId(listingTypeId)
                    val category = if (categoryId == "none") {
                        null
                    } else {
                        uiState.findCategoryById(categoryId) ?: BookCategory.fromId(categoryId)
                    }

                    BookListingScreen(
                        uiState = uiState,
                        listingType = listingType,
                        category = category,
                        onBack = { navController.popBackStack() },
                        onSearchQueryChange = viewModel::updateListingSearchQuery,
                        onClearSearch = viewModel::clearListingSearch,
                        onSelectSort = viewModel::setListingSortOption,
                        onToggleLayout = viewModel::toggleListingLayout,
                        onBookClick = { bookId ->
                            navController.navigate(BookHubRoutes.bookDetails(bookId))
                        },
                        onToggleBookmark = viewModel::toggleBookmark,
                    )
                }

                composable(BookHubRoutes.MY_LIBRARY) {
                    MyLibraryScreen(
                        uiState = uiState,
                        onSelectFilter = viewModel::setLibraryFilter,
                        onSearchQueryChange = viewModel::updateLibrarySearchQuery,
                        onClearSearch = viewModel::clearLibrarySearch,
                        onBookClick = { bookId ->
                            navController.navigate(BookHubRoutes.bookDetails(bookId))
                        },
                        onReadClick = { bookId -> openReader(bookId) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onToggleBookmark = viewModel::toggleBookmark,
                        onRemoveFromLibrary = viewModel::toggleInLibrary,
                        onClearHistory = viewModel::clearReadingHistory,
                        onOpenBookmarksScreen = {
                            navController.navigate(BookHubRoutes.BOOKMARKS)
                        },
                    )
                }

                composable(BookHubRoutes.PROFILE) {
                    ProfileScreen(
                        uiState = uiState,
                        onOpenMyLibrary = {
                            navigateToTab(BottomNavTab.MY_LIBRARY)
                        },
                        onOpenBookmarks = {
                            navController.navigate(BookHubRoutes.BOOKMARKS)
                        },
                        onOpenAdminPanel = {
                            navController.navigate(BookHubRoutes.ADMIN_PANEL)
                        },
                        onShowToastMessage = viewModel::showStatusMessage,
                        onSignInWithGoogle = { ctx ->
                            viewModel.signInWithGoogle(ctx, isAdminFlow = false)
                        },
                        onSignOutGoogle = viewModel::signOutCloudAccount,
                    )
                }

                composable(
                    route = BookHubRoutes.BOOK_DETAILS,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(260),
                        ) + fadeIn(tween(200))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(240),
                        ) + fadeOut(tween(180))
                    },
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                    val book = uiState.books.find { it.id == bookId }
                    if (book == null) {
                        LaunchedEffect(bookId) {
                            viewModel.showStatusMessage("This book is no longer available.")
                            navController.popBackStack()
                        }
                        return@composable
                    }
                    val relatedBooks = remember(uiState.books, book.id) {
                        uiState.getRelatedBooks(book)
                    }
                    BookDetailsScreen(
                        book = book,
                        relatedBooks = relatedBooks,
                        onBack = { navController.popBackStack() },
                        onReadNow = { openReader(book.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(book.id) },
                        onToggleBookmark = { viewModel.toggleBookmark(book.id) },
                        onToggleLibrary = { viewModel.toggleInLibrary(book.id) },
                        onRelatedBookClick = { relatedId ->
                            navController.navigate(BookHubRoutes.bookDetails(relatedId))
                        },
                        onRelatedBookToggleBookmark = viewModel::toggleBookmark,
                        onSharePreview = {
                            viewModel.showStatusMessage("Share sheet preview for \"${book.title}\"")
                        },
                        onShowMessage = viewModel::showStatusMessage,
                        onDownloadPdf = { viewModel.downloadBookForOfflineReading(book.id) },
                    )
                }

                composable(
                    route = BookHubRoutes.READER_PREVIEW,
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType },
                        navArgument("page") {
                            type = NavType.IntType
                            defaultValue = -1
                        },
                    ),
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = tween(260),
                        ) + fadeIn(tween(200))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(240),
                        ) + fadeOut(tween(180))
                    },
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                    val pageArg = backStackEntry.arguments?.getInt("page") ?: -1
                    val initialTargetPage = if (pageArg > 0) pageArg else null
                    val book = uiState.books.find { it.id == bookId }
                    if (book == null) {
                        LaunchedEffect(bookId) {
                            viewModel.showStatusMessage("This book is no longer available.")
                            navController.popBackStack()
                        }
                        return@composable
                    }
                    val bookBookmarks = uiState.getBookmarksForBook(book.id)

                    PdfReaderScreen(
                        book = book,
                        initialTargetPage = initialTargetPage,
                        bookBookmarks = bookBookmarks,
                        nightMode = uiState.readerNightMode,
                        onBack = { navController.popBackStack() },
                        onToggleNightMode = viewModel::toggleReaderNightMode,
                        onPageChanged = { currentPage, totalPages ->
                            viewModel.updateBookReadingProgress(book.id, currentPage, totalPages)
                        },
                        onTogglePageBookmark = { pageNumber ->
                            viewModel.togglePageBookmark(book.id, pageNumber)
                        },
                        onRemoveBookmarkNote = viewModel::removeBookmarkNote,
                        driveAccessTokenProvider = viewModel::getCachedDriveAccessToken,
                    )
                }

                composable(BookHubRoutes.BOOKMARKS) {
                    BookmarksScreen(
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onOpenBook = { bookId ->
                            navController.navigate(BookHubRoutes.bookDetails(bookId))
                        },
                        onReadBook = { bookId -> openReader(bookId) },
                        onJumpToBookmarkPage = { bookId, page -> openReader(bookId, page) },
                        onRemoveBookmarkBook = viewModel::toggleBookmark,
                        onRemoveBookmarkNote = viewModel::removeBookmarkNote,
                    )
                }

                composable(BookHubRoutes.ADMIN_LOGIN) {
                    RequireAdminAccess(
                        uiState = uiState,
                        onRefreshAuth = viewModel::refreshAdminAuthStatus,
                        onBack = { navController.popBackStack() },
                        onSetupFirstAdmin = viewModel::setupFirstAdmin,
                        onLoginAdmin = viewModel::loginAdmin,
                        onClearError = viewModel::clearAdminAuthError,
                        onSignInWithGoogleAdmin = { ctx ->
                            viewModel.signInWithGoogle(ctx, isAdminFlow = true)
                        },
                        onReverifyCloudAdmin = viewModel::reverifyCloudAdminStatus,
                        onSignOutCloudAccount = viewModel::signOutCloudAccount,
                        onShowMessage = viewModel::showStatusMessage,
                    ) {
                        LaunchedEffect(Unit) {
                            navController.navigate(BookHubRoutes.ADMIN_PANEL) {
                                popUpTo(BookHubRoutes.ADMIN_LOGIN) { inclusive = true }
                            }
                        }
                    }
                }

                composable(BookHubRoutes.ADMIN_PANEL) {
                    RequireAdminAccess(
                        uiState = uiState,
                        onRefreshAuth = viewModel::refreshAdminAuthStatus,
                        onBack = { navController.popBackStack() },
                        onSetupFirstAdmin = viewModel::setupFirstAdmin,
                        onLoginAdmin = viewModel::loginAdmin,
                        onClearError = viewModel::clearAdminAuthError,
                        onSignInWithGoogleAdmin = { ctx ->
                            viewModel.signInWithGoogle(ctx, isAdminFlow = true)
                        },
                        onReverifyCloudAdmin = viewModel::reverifyCloudAdminStatus,
                        onSignOutCloudAccount = viewModel::signOutCloudAccount,
                        onShowMessage = viewModel::showStatusMessage,
                    ) {
                        AdminPanelScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onOpenAddBook = {
                                navController.navigate(BookHubRoutes.ADMIN_ADD_BOOK)
                            },
                            onOpenManageBooks = {
                                navController.navigate(BookHubRoutes.ADMIN_MANAGE_BOOKS)
                            },
                            onOpenManageCategories = {
                                navController.navigate(BookHubRoutes.ADMIN_CATEGORIES)
                            },
                            onEditBook = { bookId ->
                                navController.navigate(BookHubRoutes.adminEditBook(bookId))
                            },
                            onReadBook = { bookId -> openReader(bookId) },
                            onChangePasscode = viewModel::changeAdminPasscode,
                            onLogoutAdmin = {
                                viewModel.logoutAdmin()
                                navController.popBackStack(BookHubRoutes.ADMIN_PANEL, inclusive = true)
                            },
                            onRequestDriveAuthorization = viewModel::requestDriveAuthorization,
                            onDriveConsentResult = viewModel::handleDriveConsentIntentResult,
                            onMigrateLocalCatalogToCloud = viewModel::migrateLocalCatalogToCloud,
                        )
                    }
                }

                composable(BookHubRoutes.ADMIN_ADD_BOOK) {
                    RequireAdminAccess(
                        uiState = uiState,
                        onRefreshAuth = viewModel::refreshAdminAuthStatus,
                        onBack = { navController.popBackStack() },
                        onSetupFirstAdmin = viewModel::setupFirstAdmin,
                        onLoginAdmin = viewModel::loginAdmin,
                        onClearError = viewModel::clearAdminAuthError,
                        onSignInWithGoogleAdmin = { ctx ->
                            viewModel.signInWithGoogle(ctx, isAdminFlow = true)
                        },
                        onReverifyCloudAdmin = viewModel::reverifyCloudAdminStatus,
                        onSignOutCloudAccount = viewModel::signOutCloudAccount,
                        onShowMessage = viewModel::showStatusMessage,
                    ) {
                        AdminAddEditBookScreen(
                            categories = uiState.categories,
                            existingBook = null,
                            onBack = { navController.popBackStack() },
                            onImportPdfUri = viewModel::importAdminPdfUri,
                            onImportCoverUri = viewModel::importAdminCoverUri,
                            onCleanupUnusedFile = viewModel::cleanupUnusedManagedFile,
                            onSaveNewBook = { title, author, desc, catId, pdfPath, pdfBytes, pdfSizeLabel, pdfPages, coverPath, lang, featured, popular, recent, published, pendingAssets, onPendingAssetsUpdated ->
                                viewModel.saveAdminBookCloud(
                                    title = title,
                                    author = author,
                                    description = desc,
                                    categoryId = catId,
                                    pdfFilePath = pdfPath,
                                    pdfByteSize = pdfBytes,
                                    pdfFormattedSize = pdfSizeLabel,
                                    pdfPageCount = pdfPages,
                                    coverImagePath = coverPath,
                                    language = lang,
                                    isFeatured = featured,
                                    isPopular = popular,
                                    isRecent = recent,
                                    isPublished = published,
                                    pendingAssets = pendingAssets,
                                    onPendingAssetsUpdated = onPendingAssetsUpdated,
                                )
                            },
                            onUpdateExistingBook = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
                                "Invalid operation for new book form."
                            },
                            useCloudMode = uiState.useCloudAdminAuth,
                            isDriveAuthorized = uiState.isDriveAuthorized,
                            cloudUploadProgress = uiState.cloudUploadProgress,
                            cloudUploadStage = uiState.cloudUploadStage,
                            onRequestDriveAuthorization = viewModel::requestDriveAuthorization,
                            onDriveConsentResult = viewModel::handleDriveConsentIntentResult,
                        )
                    }
                }

                composable(
                    route = BookHubRoutes.ADMIN_EDIT_BOOK,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                    RequireAdminAccess(
                        uiState = uiState,
                        onRefreshAuth = viewModel::refreshAdminAuthStatus,
                        onBack = { navController.popBackStack() },
                        onSetupFirstAdmin = viewModel::setupFirstAdmin,
                        onLoginAdmin = viewModel::loginAdmin,
                        onClearError = viewModel::clearAdminAuthError,
                        onSignInWithGoogleAdmin = { ctx ->
                            viewModel.signInWithGoogle(ctx, isAdminFlow = true)
                        },
                        onReverifyCloudAdmin = viewModel::reverifyCloudAdminStatus,
                        onSignOutCloudAccount = viewModel::signOutCloudAccount,
                        onShowMessage = viewModel::showStatusMessage,
                    ) {
                        val existingBook = uiState.books.find { it.id == bookId }
                        if (existingBook == null) {
                            LaunchedEffect(bookId) {
                                viewModel.showStatusMessage("Book no longer exists.")
                                navController.popBackStack()
                            }
                            return@RequireAdminAccess
                        }
                        AdminAddEditBookScreen(
                            categories = uiState.categories,
                            existingBook = existingBook,
                            onBack = { navController.popBackStack() },
                            onImportPdfUri = viewModel::importAdminPdfUri,
                            onImportCoverUri = viewModel::importAdminCoverUri,
                            onCleanupUnusedFile = viewModel::cleanupUnusedManagedFile,
                            onSaveNewBook = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
                                "Invalid operation for edit book form."
                            },
                            onUpdateExistingBook = { targetId, title, author, desc, catId, newPdfPath, newPdfBytes, newPdfSizeLabel, newPdfPages, newCoverPath, removeCover, lang, featured, popular, recent, published, pendingAssets, onPendingAssetsUpdated ->
                                viewModel.updateAdminBookCloud(
                                    bookId = targetId,
                                    title = title,
                                    author = author,
                                    description = desc,
                                    categoryId = catId,
                                    newPdfFilePath = newPdfPath,
                                    newPdfByteSize = newPdfBytes,
                                    newPdfFormattedSize = newPdfSizeLabel,
                                    newPdfPageCount = newPdfPages,
                                    newCoverImagePath = newCoverPath,
                                    removeCustomCover = removeCover,
                                    language = lang,
                                    isFeatured = featured,
                                    isPopular = popular,
                                    isRecent = recent,
                                    isPublished = published,
                                    pendingAssets = pendingAssets,
                                    onPendingAssetsUpdated = onPendingAssetsUpdated,
                                )
                            },
                            useCloudMode = uiState.useCloudAdminAuth,
                            isDriveAuthorized = uiState.isDriveAuthorized,
                            cloudUploadProgress = uiState.cloudUploadProgress,
                            cloudUploadStage = uiState.cloudUploadStage,
                            onRequestDriveAuthorization = viewModel::requestDriveAuthorization,
                            onDriveConsentResult = viewModel::handleDriveConsentIntentResult,
                        )
                    }
                }

                composable(BookHubRoutes.ADMIN_MANAGE_BOOKS) {
                    RequireAdminAccess(
                        uiState = uiState,
                        onRefreshAuth = viewModel::refreshAdminAuthStatus,
                        onBack = { navController.popBackStack() },
                        onSetupFirstAdmin = viewModel::setupFirstAdmin,
                        onLoginAdmin = viewModel::loginAdmin,
                        onClearError = viewModel::clearAdminAuthError,
                        onSignInWithGoogleAdmin = { ctx ->
                            viewModel.signInWithGoogle(ctx, isAdminFlow = true)
                        },
                        onReverifyCloudAdmin = viewModel::reverifyCloudAdminStatus,
                        onSignOutCloudAccount = viewModel::signOutCloudAccount,
                        onShowMessage = viewModel::showStatusMessage,
                    ) {
                        AdminManageBooksScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onAddBook = {
                                navController.navigate(BookHubRoutes.ADMIN_ADD_BOOK)
                            },
                            onEditBook = { bookId ->
                                navController.navigate(BookHubRoutes.adminEditBook(bookId))
                            },
                            onReadBook = { bookId -> openReader(bookId) },
                            onDeleteBook = { bookId ->
                                viewModel.deleteBookCloudAndLocal(bookId)
                            },
                            onTogglePublishBook = viewModel::toggleBookPublishedCloud,
                        )
                    }
                }

                composable(BookHubRoutes.ADMIN_CATEGORIES) {
                    RequireAdminAccess(
                        uiState = uiState,
                        onRefreshAuth = viewModel::refreshAdminAuthStatus,
                        onBack = { navController.popBackStack() },
                        onSetupFirstAdmin = viewModel::setupFirstAdmin,
                        onLoginAdmin = viewModel::loginAdmin,
                        onClearError = viewModel::clearAdminAuthError,
                        onSignInWithGoogleAdmin = { ctx ->
                            viewModel.signInWithGoogle(ctx, isAdminFlow = true)
                        },
                        onReverifyCloudAdmin = viewModel::reverifyCloudAdminStatus,
                        onSignOutCloudAccount = viewModel::signOutCloudAccount,
                        onShowMessage = viewModel::showStatusMessage,
                    ) {
                        AdminCategoriesScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onAddCategory = viewModel::addCategoryCloud,
                            onUpdateCategory = viewModel::updateCategoryCloud,
                            onDeleteCategory = viewModel::deleteCategoryCloud,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequireAdminAccess(
    uiState: BookHubUiState,
    onRefreshAuth: () -> Unit,
    onBack: () -> Unit,
    onSetupFirstAdmin: (name: String, email: String, passcode: String, confirm: String) -> Boolean,
    onLoginAdmin: (passcode: String) -> Boolean,
    onClearError: () -> Unit,
    onSignInWithGoogleAdmin: suspend (Context) -> Unit,
    onReverifyCloudAdmin: () -> Unit,
    onSignOutCloudAccount: () -> Unit,
    onShowMessage: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(uiState.adminSession, uiState.cloudAccount?.uid) {
        if (!uiState.useCloudAdminAuth || uiState.cloudAccount != null) {
            onRefreshAuth()
        }
    }
    if (uiState.isAdminAuthenticated) {
        content()
    } else {
        AdminLoginScreen(
            authStatus = uiState.adminAuthStatus,
            authError = uiState.adminAuthError,
            onBack = onBack,
            onSetupFirstAdmin = { name, email, passcode, confirm ->
                onSetupFirstAdmin(name, email, passcode, confirm)
            },
            onLoginAdmin = { passcode ->
                onLoginAdmin(passcode)
            },
            onClearError = onClearError,
            useCloudAuth = uiState.useCloudAdminAuth,
            cloudAccount = uiState.cloudAccount,
            adminCloudVerification = uiState.adminCloudVerification,
            isAdminRoleChecking = uiState.isAdminRoleChecking,
            onSignInWithGoogleAdmin = onSignInWithGoogleAdmin,
            onReverifyCloudAdmin = onReverifyCloudAdmin,
            onSignOutCloudAccount = onSignOutCloudAccount,
            onShowMessage = onShowMessage,
        )
    }
}
