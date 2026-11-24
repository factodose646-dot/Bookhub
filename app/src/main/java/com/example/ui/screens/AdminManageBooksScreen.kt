package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Book
import com.example.pdf.PdfSource
import com.example.pdf.PdfSourceResolver
import com.example.ui.BookHubUiState
import com.example.ui.components.BookCoverArtwork
import com.example.ui.theme.AdminCoral
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream200
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold100
import com.example.ui.theme.Gold400
import com.example.ui.theme.Ink500
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink900
import kotlinx.coroutines.launch

@Composable
fun AdminManageBooksScreen(
    uiState: BookHubUiState,
    onBack: () -> Unit,
    onAddBook: () -> Unit,
    onEditBook: (String) -> Unit,
    onReadBook: (String) -> Unit,
    onDeleteBook: suspend (String) -> Boolean,
    onTogglePublishBook: (suspend (String) -> String?)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var bookPendingDelete by remember { mutableStateOf<Book?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var togglingBookId by remember { mutableStateOf<String?>(null) }

    val filteredBooks = remember(uiState.books, searchQuery, selectedCategoryId) {
        val trimmed = searchQuery.trim()
        uiState.books
            .filter { book ->
                val matchesCat = selectedCategoryId == null || book.category.id == selectedCategoryId
                val matchesQuery = trimmed.isEmpty() ||
                    book.title.contains(trimmed, ignoreCase = true) ||
                    book.author.contains(trimmed, ignoreCase = true)
                matchesCat && matchesQuery
            }
            .sortedWith(
                compareByDescending<Book> { it.uploadTimestamp }
                    .thenByDescending { it.addedOrder },
            )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Emerald950,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddBook,
                containerColor = Gold400,
                contentColor = Emerald950,
                modifier = Modifier.testTag("admin_manage_fab_add"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add new book")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Brush.verticalGradient(listOf(Emerald950, Emerald900))),
        ) {
            // Top Bar + Search
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_manage_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = Cream50,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manage Books",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Cream50,
                        )
                        val publishedCount = filteredBooks.count { it.isPublished }
                        val draftCount = filteredBooks.size - publishedCount
                        Text(
                            text = if (draftCount > 0) {
                                "${filteredBooks.size} of ${uiState.books.size} books • $draftCount draft"
                            } else {
                                "${filteredBooks.size} of ${uiState.books.size} books"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Emerald100.copy(alpha = 0.78f),
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search books by title...", color = Emerald100.copy(alpha = 0.7f))
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null, tint = Gold400)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, "Clear search", tint = Cream50)
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
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("admin_manage_search_input"),
                )
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Cream100,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    // Category Filter Row
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategoryId == null,
                                onClick = { selectedCategoryId = null },
                                label = { Text("All (${uiState.books.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Emerald700,
                                    selectedLabelColor = Cream50,
                                    containerColor = Cream50,
                                    labelColor = Ink900,
                                ),
                            )
                        }
                        items(uiState.categories, key = { it.id }) { cat ->
                            val count = uiState.books.count { it.category.id == cat.id }
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = {
                                    selectedCategoryId = if (selectedCategoryId == cat.id) null else cat.id
                                },
                                label = { Text("${cat.displayName} ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Emerald700,
                                    selectedLabelColor = Cream50,
                                    containerColor = Cream50,
                                    labelColor = Ink900,
                                ),
                            )
                        }
                    }

                    if (filteredBooks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No books match the selected filter.",
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink500,
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(filteredBooks, key = { it.id }) { book ->
                                AdminManageBookCard(
                                    book = book,
                                    isTogglingPublish = togglingBookId == book.id,
                                    onEdit = { onEditBook(book.id) },
                                    onRead = { onReadBook(book.id) },
                                    onTogglePublish = onTogglePublishBook?.let { toggle ->
                                        {
                                            if (togglingBookId == null) {
                                                togglingBookId = book.id
                                                scope.launch {
                                                    try {
                                                        toggle(book.id)
                                                    } finally {
                                                        togglingBookId = null
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onDelete = { bookPendingDelete = book },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pending = bookPendingDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) bookPendingDelete = null },
            containerColor = Cream50,
            title = {
                Text(
                    text = "Delete Book?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink900,
                )
            },
            text = {
                Text(
                    text = if (pending.isSyncedToCloud || !pending.googleDriveFileId.isNullOrBlank()) {
                        "Are you sure you want to permanently delete \"${pending.title}\" from Cloud Firestore, Google Drive, bookmarks, and local storage?"
                    } else {
                        "Are you sure you want to permanently remove \"${pending.title}\"? Any imported PDF file, cover image, bookmarks, and reading progress for this book will also be removed."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink700,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            try {
                                onDeleteBook(pending.id)
                                bookPendingDelete = null
                            } finally {
                                isDeleting = false
                            }
                        }
                    },
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AdminCoral,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isDeleting) "Deleting..." else "Delete",
                        color = AdminCoral,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { bookPendingDelete = null },
                ) {
                    Text("Cancel", color = Ink500)
                }
            },
        )
    }
}

@Composable
private fun AdminManageBookCard(
    book: Book,
    isTogglingPublish: Boolean,
    onEdit: () -> Unit,
    onRead: () -> Unit,
    onTogglePublish: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val pdfLabel = when {
        !book.googleDriveFileId.isNullOrBlank() -> "Drive PDF"
        else -> when (PdfSourceResolver.resolve(book)) {
            is PdfSource.LocalFile -> "Uploaded PDF"
            is PdfSource.LocalAsset -> "Bundled PDF"
            is PdfSource.RemoteHttps -> "Remote PDF"
            is PdfSource.GoogleDriveRef -> "Drive PDF"
            null -> "No PDF"
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Cream50),
        border = BorderStroke(1.dp, Cream200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCoverArtwork(
                    book = book,
                    modifier = Modifier
                        .width(56.dp)
                        .height(80.dp),
                    cornerRadius = 10.dp,
                    compact = true,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = Emerald100,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = book.category.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Emerald900,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Surface(
                            color = Gold100,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = pdfLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Ink900,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Surface(
                            color = if (book.isPublished) Emerald100 else AdminCoral.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = if (book.isPublished) "Published" else "Draft",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (book.isPublished) Emerald900 else AdminCoral,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Ink900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = buildString {
                            append(book.author)
                            append(" • ")
                            append(book.fileSizeMb)
                            if (book.pageCount != null) append(" • ${book.pageCount}p")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(color = Cream200.copy(alpha = 0.7f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (onTogglePublish != null) {
                    TextButton(
                        enabled = !isTogglingPublish,
                        onClick = onTogglePublish,
                        modifier = Modifier.testTag("admin_manage_toggle_publish_${book.id}"),
                    ) {
                        if (isTogglingPublish) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Emerald700,
                            )
                        } else {
                            Icon(
                                imageVector = if (book.isPublished) Icons.Rounded.Public else Icons.Rounded.VisibilityOff,
                                contentDescription = if (book.isPublished) "Move to Draft" else "Publish to readers",
                                tint = if (book.isPublished) Emerald700 else AdminCoral,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (book.isPublished) "Public" else "Make Public",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (book.isPublished) Emerald700 else AdminCoral,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRead) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = "Open PDF Reader",
                            tint = Emerald700,
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit book",
                            tint = Ink700,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete book",
                            tint = AdminCoral,
                        )
                    }
                }
            }
        }
    }
}

