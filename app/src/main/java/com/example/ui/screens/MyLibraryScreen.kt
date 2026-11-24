package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Book
import com.example.model.LibraryFilter
import com.example.ui.BookHubUiState
import com.example.ui.components.BookCoverArtwork
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald850
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold400

@Composable
fun MyLibraryScreen(
    uiState: BookHubUiState,
    onSelectFilter: (LibraryFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onBookClick: (String) -> Unit,
    onReadClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onRemoveFromLibrary: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenBookmarksScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSearch by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Emerald950, Emerald900),
                ),
            ),
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = "My Library",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Cream50,
                    )
                    Text(
                        text = "${uiState.libraryBooks.size} books in ${uiState.libraryFilter.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Emerald100.copy(alpha = 0.75f),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Emerald800,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .testTag("library_bookmarks_shortcut")
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenBookmarksScreen),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = null,
                                tint = Gold400,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Saved Notes",
                                style = MaterialTheme.typography.labelMedium,
                                color = Cream50,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) onClearSearch()
                        },
                        modifier = Modifier.testTag("library_search_button"),
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search my library",
                            tint = Cream50,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = uiState.librarySearchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Search by book title or category...",
                            color = Emerald100.copy(alpha = 0.7f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Gold400,
                        )
                    },
                    trailingIcon = {
                        if (uiState.librarySearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSearch,
                                modifier = Modifier.testTag("library_clear_search"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = Cream50,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Emerald800,
                        unfocusedContainerColor = Emerald800,
                        focusedBorderColor = Gold400,
                        unfocusedBorderColor = Emerald700,
                        focusedTextColor = Cream50,
                        unfocusedTextColor = Cream50,
                        cursorColor = Gold400,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .testTag("library_search_input"),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Four Library Filter Pills: All Books, Favorites, Bookmarks, Reading History
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LibraryFilter.entries) { filter ->
                    val selected = uiState.libraryFilter == filter
                    Surface(
                        color = if (selected) Emerald100 else Emerald800,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .testTag("library_filter_${filter.name.lowercase()}")
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectFilter(filter) },
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (selected) Emerald950 else Cream50,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }

        if (uiState.libraryFilter == LibraryFilter.HISTORY && uiState.libraryBooks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClearHistory) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = Gold400,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clear Reading History",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold400,
                    )
                }
            }
        }

        // Book List
        if (uiState.libraryBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = Emerald800,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.size(68.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.MenuBook,
                                contentDescription = null,
                                tint = Gold400,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (uiState.librarySearchQuery.isNotBlank()) {
                            "No matching books in ${uiState.libraryFilter.label}"
                        } else {
                            "No books in ${uiState.libraryFilter.label} yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Cream50,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            uiState.librarySearchQuery.isNotBlank() ->
                                "Try clearing your search or checking another tab."
                            uiState.libraryFilter == LibraryFilter.FAVORITES ->
                                "Tap the heart button on any book details page to add favorites."
                            uiState.libraryFilter == LibraryFilter.BOOKMARKS ->
                                "Bookmark any book to keep quick access here."
                            uiState.libraryFilter == LibraryFilter.HISTORY ->
                                "Books you open with Read Now appear here automatically."
                            else ->
                                "Explore Home or Categories to save books to your personal library."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Emerald100.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                    if (uiState.librarySearchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onClearSearch) {
                            Text("Clear Search", color = Gold400)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.libraryBooks, key = { it.id }) { book ->
                    LibraryDarkBookCard(
                        book = book,
                        showProgress = uiState.libraryFilter == LibraryFilter.HISTORY || book.readingProgress > 0f,
                        onOpenDetails = { onBookClick(book.id) },
                        onRead = { onReadClick(book.id) },
                        onToggleFavorite = { onToggleFavorite(book.id) },
                        onToggleBookmark = { onToggleBookmark(book.id) },
                        onRemove = { onRemoveFromLibrary(book.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryDarkBookCard(
    book: Book,
    showProgress: Boolean,
    onOpenDetails: () -> Unit,
    onRead: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleBookmark: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = Emerald850,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenDetails),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCoverArtwork(
                book = book,
                modifier = Modifier
                    .width(62.dp)
                    .height(86.dp),
                cornerRadius = 10.dp,
                showPdfBadge = false,
                compact = true,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Cream50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.category.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold400,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (book.pageCount != null) {
                        "${book.pageCount} Pages • ${book.fileSizeMb}"
                    } else {
                        "${book.fileSizeMb} PDF"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Emerald100.copy(alpha = 0.78f),
                )
                if (showProgress && book.readingProgress > 0f) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { book.readingProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Gold400,
                        trackColor = Emerald950,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = Emerald600,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRead),
            ) {
                Text(
                    text = "Read",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Cream50,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Book options",
                        tint = Emerald100,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = Emerald900,
                ) {
                    DropdownMenuItem(
                        text = { Text("Book Details", color = Cream50) },
                        onClick = {
                            menuExpanded = false
                            onOpenDetails()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (book.isFavorite) "Remove Favorite" else "Add to Favorites",
                                color = Cream50,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleFavorite()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (book.isBookmarked) "Remove Bookmark" else "Add to Bookmarks",
                                color = Cream50,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleBookmark()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Library", color = Gold400) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}
