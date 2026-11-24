package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Book
import com.example.model.BookmarkNote
import com.example.ui.BookHubUiState
import com.example.ui.components.BookCoverArtwork
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold100
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.example.ui.theme.Ink500
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink900

@Composable
fun BookmarksScreen(
    uiState: BookHubUiState,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onReadBook: (String) -> Unit,
    onJumpToBookmarkPage: (String, Int) -> Unit = { id, _ -> onReadBook(id) },
    onRemoveBookmarkBook: (String) -> Unit,
    onRemoveBookmarkNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(listOf(Emerald950, Emerald900)),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("bookmarks_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = Cream50,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Bookmarks",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Cream50,
                )
                Text(
                    text = "${uiState.bookmarkedBooks.size} saved books • ${uiState.bookmarkNotes.size} page markers",
                    style = MaterialTheme.typography.bodySmall,
                    color = Emerald100.copy(alpha = 0.78f),
                )
            }

            Surface(
                color = Emerald800,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = null,
                    tint = Gold400,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Cream100,
        ) {
            if (uiState.bookmarkedBooks.isEmpty() && uiState.bookmarkNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = Gold100,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = Gold500,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Bookmarks Yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink900,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the bookmark icon on any book card or details page to keep your favorites right here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink500,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.bookmarkedBooks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Bookmarked Books",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Ink900,
                            )
                        }
                        items(uiState.bookmarkedBooks, key = { "bm_book_${it.id}" }) { book ->
                            BookmarkedBookCard(
                                book = book,
                                onOpen = { onOpenBook(book.id) },
                                onRead = { onReadBook(book.id) },
                                onRemove = { onRemoveBookmarkBook(book.id) },
                            )
                        }
                    }

                    if (uiState.bookmarkNotes.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Saved Page Markers",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Ink900,
                            )
                        }
                        items(uiState.bookmarkNotes, key = { it.id }) { note ->
                            val parentBook = uiState.books.find { it.id == note.bookId }
                            BookmarkPageMarkerCard(
                                note = note,
                                bookTitle = parentBook?.title ?: "Saved Book",
                                onOpenBook = { onJumpToBookmarkPage(note.bookId, note.pageNumber) },
                                onRemove = { onRemoveBookmarkNote(note.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkedBookCard(
    book: Book,
    onOpen: () -> Unit,
    onRead: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Cream50),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                    .width(58.dp)
                    .height(82.dp),
                cornerRadius = 10.dp,
                compact = true,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.category.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Emerald700,
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Ink900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (book.pageCount != null) {
                        "${book.pageCount} Pages • ${book.fileSizeMb}"
                    } else {
                        book.fileSizeMb
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink500,
                )
            }
            Surface(
                color = Emerald700,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRead),
            ) {
                Text(
                    text = "Read",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cream50,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.BookmarkRemove,
                    contentDescription = "Remove bookmark",
                    tint = Gold500,
                )
            }
        }
    }
}

@Composable
private fun BookmarkPageMarkerCard(
    note: BookmarkNote,
    bookTitle: String,
    onOpenBook: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenBook),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Cream50),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = Gold100,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = Gold500,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Page ${note.pageNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink900,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.savedDateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink500,
                    )
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkRemove,
                            contentDescription = "Delete marker",
                            tint = Ink500,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.titleSmall,
                color = Ink900,
            )
            Text(
                text = note.chapterTitle,
                style = MaterialTheme.typography.labelMedium,
                color = Emerald700,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.notePreview,
                style = MaterialTheme.typography.bodySmall,
                color = Ink700,
            )
        }
    }
}
