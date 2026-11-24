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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BookCategory
import com.example.model.BookListingType
import com.example.model.BookSortOption
import com.example.ui.BookHubUiState
import com.example.ui.components.BookCoverCard
import com.example.ui.components.BookListRowCard
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream200
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold400
import com.example.ui.theme.Ink500
import com.example.ui.theme.Ink900

@Composable
fun BookListingScreen(
    uiState: BookHubUiState,
    listingType: BookListingType,
    category: BookCategory?,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectSort: (BookSortOption) -> Unit,
    onToggleLayout: () -> Unit,
    onBookClick: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSearch by remember { mutableStateOf(false) }

    val books = remember(
        uiState.books,
        listingType,
        category,
        uiState.listingSearchQuery,
        uiState.listingSortOption,
    ) {
        uiState.getBooksForListing(listingType, category)
    }

    val headerTitle = if (listingType == BookListingType.CATEGORY && category != null) {
        category.displayName
    } else {
        listingType.title
    }
    val headerSubtitle = if (listingType == BookListingType.CATEGORY && category != null) {
        category.description
    } else {
        listingType.subtitle
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(listOf(Emerald950, Emerald900)),
            ),
    ) {
        // Top Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("listing_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = Cream50,
                        )
                    }
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Cream50,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${books.size} PDF books • $headerSubtitle",
                            style = MaterialTheme.typography.bodySmall,
                            color = Emerald100.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) onClearSearch()
                        },
                        modifier = Modifier.testTag("listing_search_button"),
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search books",
                            tint = Cream50,
                        )
                    }

                    IconButton(
                        onClick = onToggleLayout,
                        modifier = Modifier.testTag("listing_layout_toggle"),
                    ) {
                        Icon(
                            imageVector = if (uiState.isListingGridLayout) {
                                Icons.AutoMirrored.Rounded.ViewList
                            } else {
                                Icons.Rounded.GridView
                            },
                            contentDescription = if (uiState.isListingGridLayout) {
                                "Switch to list layout"
                            } else {
                                "Switch to grid layout"
                            },
                            tint = Gold400,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = uiState.listingSearchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Search by title or category...",
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
                        if (uiState.listingSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSearch,
                                modifier = Modifier.testTag("listing_clear_search"),
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
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("listing_search_input"),
                )
            }
        }

        // Cream Surface Content Area
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
                // Sort Options Row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                tint = Emerald700,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sort:",
                                style = MaterialTheme.typography.labelMedium,
                                color = Ink500,
                            )
                        }
                    }

                    items(BookSortOption.entries) { option ->
                        val selected = uiState.listingSortOption == option
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectSort(option) },
                            label = {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Emerald700,
                                selectedLabelColor = Cream50,
                                containerColor = Cream50,
                                labelColor = Ink900,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = Cream200,
                                selectedBorderColor = Emerald700,
                            ),
                            modifier = Modifier.testTag("sort_chip_${option.name.lowercase()}"),
                        )
                    }
                }

                if (books.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            color = Cream50,
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Surface(
                                    color = Emerald100,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.size(64.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = Emerald700,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (uiState.listingSearchQuery.isNotBlank()) {
                                        "No Matching Books Found"
                                    } else {
                                        "No Books in This Collection Yet"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Ink900,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (uiState.listingSearchQuery.isNotBlank()) {
                                        "No titles or categories matched \"${uiState.listingSearchQuery}\"."
                                    } else {
                                        "Books added to ${headerTitle} will appear here automatically."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Ink500,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (uiState.listingSearchQuery.isNotBlank()) {
                                            onClearSearch()
                                        } else {
                                            onBack()
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Emerald700,
                                        contentColor = Cream50,
                                    ),
                                ) {
                                    Text(
                                        text = if (uiState.listingSearchQuery.isNotBlank()) {
                                            "Clear Search"
                                        } else {
                                            "Explore Other Categories"
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else if (uiState.isListingGridLayout) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 152.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(books, key = { it.id }) { book ->
                            BookCoverCard(
                                book = book,
                                onClick = { onBookClick(book.id) },
                                onToggleBookmark = { onToggleBookmark(book.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(books, key = { it.id }) { book ->
                            BookListRowCard(
                                book = book,
                                onClick = { onBookClick(book.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
