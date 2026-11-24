package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WidthFull
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Book
import com.example.model.BookmarkNote
import com.example.model.PdfFitMode
import com.example.pdf.PdfDocumentEngine
import com.example.pdf.PdfDocumentHandle
import com.example.pdf.PdfLoadError
import com.example.pdf.PdfLoadException
import com.example.pdf.PdfSourceResolver
import com.example.ui.components.PdfViewerPane
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.example.ui.theme.Ink500
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink900
import kotlin.math.roundToInt

private sealed interface PdfLoaderState {
    data class Loading(val progress: Float?) : PdfLoaderState
    data class Ready(val handle: PdfDocumentHandle) : PdfLoaderState
    data class Error(val error: PdfLoadError) : PdfLoaderState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    book: Book,
    initialTargetPage: Int?,
    bookBookmarks: List<BookmarkNote>,
    nightMode: Boolean,
    onBack: () -> Unit,
    onToggleNightMode: () -> Unit,
    onPageChanged: (currentPage: Int, totalPages: Int) -> Unit,
    onTogglePageBookmark: (pageNumber: Int) -> Unit,
    onRemoveBookmarkNote: (String) -> Unit,
    driveAccessTokenProvider: (() -> String?)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engine = remember(context, driveAccessTokenProvider) {
        PdfDocumentEngine(
            context = context.applicationContext,
            driveAccessTokenProvider = driveAccessTokenProvider,
        )
    }

    var loaderState by remember(book.id) { mutableStateOf<PdfLoaderState>(PdfLoaderState.Loading(null)) }
    var reloadNonce by remember(book.id) { mutableIntStateOf(0) }

    var currentPage by remember(book.id) {
        mutableIntStateOf((initialTargetPage ?: book.lastReadPage).coerceAtLeast(1))
    }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var fitMode by remember { mutableStateOf(PdfFitMode.FIT_WIDTH) }
    var isFullscreen by remember { mutableStateOf(false) }
    var pageJumpCommand by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var jumpToken by remember { mutableIntStateOf(0) }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInputText by remember { mutableStateOf("") }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    LaunchedEffect(book.id, reloadNonce) {
        loaderState = PdfLoaderState.Loading(null)
        val source = PdfSourceResolver.resolve(book)
        loaderState = try {
            val handle = engine.openDocument(
                bookId = book.id,
                source = source,
                onProgress = { pct ->
                    loaderState = PdfLoaderState.Loading(pct)
                },
            )
            val restoredPage = (initialTargetPage ?: book.lastReadPage)
                .coerceIn(1, handle.pageCount)
            currentPage = restoredPage
            onPageChanged(restoredPage, handle.pageCount)
            PdfLoaderState.Ready(handle)
        } catch (e: PdfLoadException) {
            PdfLoaderState.Error(e.error)
        } catch (e: Exception) {
            PdfLoaderState.Error(
                PdfLoadError.FileReadFailed(e.message ?: "Unexpected failure opening PDF."),
            )
        }
    }

    DisposableEffect(loaderState) {
        val currentHandle = (loaderState as? PdfLoaderState.Ready)?.handle
        onDispose {
            currentHandle?.close()
        }
    }

    fun jumpToPage(targetOneBased: Int, totalPages: Int) {
        val clamped = targetOneBased.coerceIn(1, totalPages)
        jumpToken += 1
        pageJumpCommand = clamped to jumpToken
        currentPage = clamped
        onPageChanged(clamped, totalPages)
    }

    val isCurrentPageBookmarked = bookBookmarks.any { it.pageNumber == currentPage }
    val readyHandle = (loaderState as? PdfLoaderState.Ready)?.handle
    val totalPages = readyHandle?.pageCount ?: (book.pageCount ?: 1)
    val progressPercent = ((currentPage.toFloat() / totalPages.coerceAtLeast(1).toFloat()) * 100f).roundToInt()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (nightMode) Emerald950 else Cream100),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Toolbar (hidden in Fullscreen)
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                Surface(
                    color = Emerald950,
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("reader_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Navigate back",
                                tint = Cream50,
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
                        ) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Cream50,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (readyHandle != null) {
                                Text(
                                    text = "Page $currentPage of $totalPages • $progressPercent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Gold400,
                                    maxLines = 1,
                                )
                            } else {
                                Text(
                                    text = book.category.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Emerald100.copy(alpha = 0.78f),
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (readyHandle != null) {
                                        onTogglePageBookmark(currentPage)
                                    }
                                },
                                enabled = readyHandle != null,
                                modifier = Modifier.testTag("reader_bookmark_button"),
                            ) {
                                Icon(
                                    imageVector = if (isCurrentPageBookmarked) {
                                        Icons.Rounded.Bookmark
                                    } else {
                                        Icons.Rounded.BookmarkBorder
                                    },
                                    contentDescription = if (isCurrentPageBookmarked) {
                                        "Remove bookmark on page $currentPage"
                                    } else {
                                        "Bookmark page $currentPage"
                                    },
                                    tint = if (isCurrentPageBookmarked) Gold400 else Cream50,
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.testTag("reader_more_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Reader options",
                                        tint = Cream50,
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    containerColor = Emerald900,
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Book Bookmarks (${bookBookmarks.size})",
                                                color = Cream50,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Bookmarks, null, tint = Gold400)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            showBookmarksSheet = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (nightMode) "Switch to Day Mode" else "Switch to Night Mode",
                                                color = Cream50,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (nightMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                                null,
                                                tint = Gold400,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onToggleNightMode()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fit Width", color = Cream50) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.WidthFull, null, tint = Emerald100)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            fitMode = PdfFitMode.FIT_WIDTH
                                            zoomScale = 1.0f
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fit Page", color = Cream50) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.FitScreen, null, tint = Emerald100)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            fitMode = PdfFitMode.FIT_PAGE
                                            zoomScale = 1.0f
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Reset Zoom (100%)", color = Cream50) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.RestartAlt, null, tint = Emerald100)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            zoomScale = 1.0f
                                            fitMode = PdfFitMode.FIT_WIDTH
                                        },
                                    )
                                    if (readyHandle != null) {
                                        DropdownMenuItem(
                                            text = { Text("Go to Page...", color = Cream50) },
                                            onClick = {
                                                showMoreMenu = false
                                                jumpInputText = currentPage.toString()
                                                showJumpDialog = true
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Main Reading Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (val state = loaderState) {
                    is PdfLoaderState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (nightMode) Emerald950 else Cream100)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = if (nightMode) Gold400 else Emerald700,
                                    modifier = Modifier.size(44.dp),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Opening PDF Document...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (nightMode) Cream50 else Ink900,
                                )
                                if (state.progress != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = Gold400,
                                        trackColor = Emerald800,
                                        strokeCap = StrokeCap.Round,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${(state.progress * 100).roundToInt()}% downloaded",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (nightMode) Emerald100 else Ink500,
                                    )
                                }
                            }
                        }
                    }

                    is PdfLoaderState.Error -> {
                        PdfErrorView(
                            error = state.error,
                            nightMode = nightMode,
                            onRetry = { reloadNonce += 1 },
                            onBack = onBack,
                        )
                    }

                    is PdfLoaderState.Ready -> {
                        PdfViewerPane(
                            document = state.handle,
                            initialPageOneBased = currentPage,
                            pageJumpCommand = pageJumpCommand,
                            zoomScale = zoomScale,
                            fitMode = fitMode,
                            nightMode = nightMode,
                            onCurrentPageChanged = { newPage ->
                                if (newPage != currentPage) {
                                    currentPage = newPage
                                    onPageChanged(newPage, state.handle.pageCount)
                                }
                            },
                            onToggleFullscreenOrDoubleTapZoom = {
                                if (isFullscreen) {
                                    isFullscreen = false
                                }
                            },
                            onDoubleTapZoom = {
                                if (zoomScale > 1.15f) {
                                    zoomScale = 1.0f
                                    fitMode = PdfFitMode.FIT_WIDTH
                                } else {
                                    zoomScale = 1.6f
                                    fitMode = PdfFitMode.CUSTOM
                                }
                            },
                        )
                    }
                }
            }

            // Bottom Controls (hidden in Fullscreen)
            AnimatedVisibility(
                visible = !isFullscreen && readyHandle != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Surface(
                    color = Emerald950,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        // Row 1: Page Navigation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { jumpToPage(currentPage - 1, totalPages) },
                                enabled = currentPage > 1,
                                modifier = Modifier.testTag("reader_prev_page"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Previous page",
                                    tint = if (currentPage > 1) Cream50 else Emerald800,
                                )
                            }

                            Surface(
                                color = Emerald800,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        jumpInputText = currentPage.toString()
                                        showJumpDialog = true
                                    }
                                    .testTag("reader_page_indicator"),
                            ) {
                                Text(
                                    text = "Page $currentPage / $totalPages",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Gold400,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                )
                            }

                            IconButton(
                                onClick = { jumpToPage(currentPage + 1, totalPages) },
                                enabled = currentPage < totalPages,
                                modifier = Modifier.testTag("reader_next_page"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Next page",
                                    tint = if (currentPage < totalPages) Cream50 else Emerald800,
                                )
                            }
                        }

                        // Row 2: Zoom, Fit Mode, Night Mode, Fullscreen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale - 0.25f).coerceIn(0.75f, 2.5f)
                                    fitMode = PdfFitMode.CUSTOM
                                },
                                modifier = Modifier.testTag("reader_zoom_out"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ZoomOut,
                                    contentDescription = "Zoom out",
                                    tint = Cream50,
                                )
                            }

                            Surface(
                                color = Emerald900,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        zoomScale = 1.0f
                                        fitMode = PdfFitMode.FIT_WIDTH
                                    }
                                    .testTag("reader_zoom_reset"),
                            ) {
                                Text(
                                    text = "${(zoomScale * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Emerald100,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }

                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale + 0.25f).coerceIn(0.75f, 2.5f)
                                    fitMode = PdfFitMode.CUSTOM
                                },
                                modifier = Modifier.testTag("reader_zoom_in"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ZoomIn,
                                    contentDescription = "Zoom in",
                                    tint = Cream50,
                                )
                            }

                            IconButton(
                                onClick = {
                                    fitMode = if (fitMode == PdfFitMode.FIT_PAGE) {
                                        PdfFitMode.FIT_WIDTH
                                    } else {
                                        PdfFitMode.FIT_PAGE
                                    }
                                    zoomScale = 1.0f
                                },
                                modifier = Modifier.testTag("reader_fit_toggle"),
                            ) {
                                Icon(
                                    imageVector = if (fitMode == PdfFitMode.FIT_PAGE) {
                                        Icons.Rounded.WidthFull
                                    } else {
                                        Icons.Rounded.FitScreen
                                    },
                                    contentDescription = "Toggle Fit Width / Fit Page",
                                    tint = if (fitMode == PdfFitMode.FIT_PAGE) Gold400 else Cream50,
                                )
                            }

                            IconButton(
                                onClick = onToggleNightMode,
                                modifier = Modifier.testTag("reader_night_toggle"),
                            ) {
                                Icon(
                                    imageVector = if (nightMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = "Toggle night mode",
                                    tint = if (nightMode) Gold400 else Cream50,
                                )
                            }

                            IconButton(
                                onClick = { isFullscreen = true },
                                modifier = Modifier.testTag("reader_fullscreen_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Fullscreen,
                                    contentDescription = "Enter fullscreen",
                                    tint = Cream50,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Exit Fullscreen Pill
        AnimatedVisibility(
            visible = isFullscreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(14.dp),
        ) {
            Surface(
                color = Emerald950.copy(alpha = 0.85f),
                shape = RoundedCornerShape(50),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { isFullscreen = false }
                    .testTag("reader_exit_fullscreen"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FullscreenExit,
                        contentDescription = "Exit fullscreen",
                        tint = Gold400,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$currentPage / $totalPages",
                        style = MaterialTheme.typography.labelMedium,
                        color = Cream50,
                    )
                }
            }
        }
    }

    // Book-Specific Bookmarks Bottom Sheet
    if (showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false },
            containerColor = Cream50,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Book Bookmarks",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink900,
                        )
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink500,
                        )
                    }
                    if (readyHandle != null) {
                        Button(
                            onClick = { onTogglePageBookmark(currentPage) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald700,
                                contentColor = Cream50,
                            ),
                        ) {
                            Text(
                                text = if (isCurrentPageBookmarked) {
                                    "Unbookmark p.$currentPage"
                                } else {
                                    "Bookmark p.$currentPage"
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (bookBookmarks.isEmpty()) {
                    Surface(
                        color = Cream100,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No saved page bookmarks for this book yet. Tap the bookmark icon in the top bar while reading to save a page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink500,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(bookBookmarks, key = { it.id }) { note ->
                            Surface(
                                color = Cream100,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        showBookmarksSheet = false
                                        jumpToPage(note.pageNumber, totalPages)
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        color = Emerald700,
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Text(
                                            text = "P.${note.pageNumber}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Cream50,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.chapterTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Ink900,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "Tap to jump • ${note.savedDateLabel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Ink500,
                                        )
                                    }
                                    IconButton(onClick = { onRemoveBookmarkNote(note.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.BookmarkRemove,
                                            contentDescription = "Remove bookmark",
                                            tint = Gold500,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Direct Page Jump Dialog
    if (showJumpDialog && readyHandle != null) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            containerColor = Cream50,
            title = {
                Text(
                    text = "Go to Page",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink900,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a page number between 1 and $totalPages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink500,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = jumpInputText,
                        onValueChange = { input ->
                            jumpInputText = input.filter { it.isDigit() }
                        },
                        label = { Text("Page Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = jumpInputText.toIntOrNull()
                        if (parsed != null) {
                            jumpToPage(parsed, totalPages)
                        }
                        showJumpDialog = false
                    },
                ) {
                    Text("Jump", color = Emerald700)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel", color = Ink500)
                }
            },
        )
    }
}

@Composable
private fun PdfErrorView(
    error: PdfLoadError,
    nightMode: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val canRetry = error is PdfLoadError.NetworkUnavailable ||
        error is PdfLoadError.RemoteLoadFailed ||
        error is PdfLoadError.GoogleDriveAccessDenied
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (nightMode) Emerald950 else Cream100)
            .padding(24.dp)
            .testTag("pdf_error_view"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = if (nightMode) Emerald900 else Cream50,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = if (nightMode) Emerald800 else Emerald100,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(68.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (error is PdfLoadError.MissingSource || error is PdfLoadError.GoogleDriveNotConnected) {
                                Icons.Rounded.CloudOff
                            } else {
                                Icons.Rounded.ErrorOutline
                            },
                            contentDescription = null,
                            tint = if (nightMode) Gold400 else Emerald700,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (nightMode) Cream50 else Ink900,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (nightMode) Emerald100.copy(alpha = 0.82f) else Ink700,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Go Back", color = if (nightMode) Cream50 else Ink900)
                    }
                    if (canRetry) {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald700,
                                contentColor = Cream50,
                            ),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
