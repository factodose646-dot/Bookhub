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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.data.BookCatalogQueries
import com.example.model.BookCategory
import com.example.ui.BookHubUiState
import com.example.ui.components.BookListRowCard
import com.example.ui.components.CategoryArtworkBadge
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold400
import com.example.ui.theme.Ink400
import com.example.ui.theme.Ink500
import com.example.ui.theme.Ink900

@Composable
fun CategoriesScreen(
    uiState: BookHubUiState,
    onCategorySelected: (BookCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSearch by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(listOf(Emerald950, Emerald900)),
            ),
    ) {
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
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Cream50,
                    )
                    Text(
                        text = "${uiState.categories.size} collections • ${uiState.publishedBooks.size} PDF books",
                        style = MaterialTheme.typography.bodySmall,
                        color = Emerald100.copy(alpha = 0.78f),
                    )
                }

                IconButton(
                    onClick = {
                        showSearch = !showSearch
                        if (!showSearch) onClearSearch()
                    },
                    modifier = Modifier.testTag("categories_search_button"),
                ) {
                    Icon(
                        imageVector = if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Search categories or books",
                        tint = Cream50,
                    )
                }
            }

            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = uiState.categorySearchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Search categories or book titles...",
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
                        if (uiState.categorySearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSearch,
                                modifier = Modifier.testTag("categories_clear_search"),
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
                        .testTag("categories_search_input"),
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Cream100,
        ) {
            val query = uiState.categorySearchQuery.trim()
            val filteredCategories = uiState.categories.filter {
                query.isBlank() ||
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
            val matchingBooks = if (query.isNotBlank()) {
                BookCatalogQueries.filterByQuery(uiState.publishedBooks, query)
            } else {
                emptyList()
            }

            if (query.isNotBlank() && filteredCategories.isEmpty() && matchingBooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = Cream50,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MenuBook,
                                contentDescription = null,
                                tint = Emerald700,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Categories or Books Found",
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink900,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try searching another category or book title.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink500,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onClearSearch,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald700,
                                    contentColor = Cream50,
                                ),
                            ) {
                                Text("Clear Search")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (filteredCategories.isNotEmpty()) {
                        items(filteredCategories, key = { it.id }) { category ->
                            CategoryListCard(
                                category = category,
                                bookCount = uiState.publishedBooks.count { it.category.id == category.id },
                                onClick = { onCategorySelected(category) },
                            )
                        }
                    }

                    if (matchingBooks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Matching Books (${matchingBooks.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Ink900,
                            )
                        }
                        items(matchingBooks, key = { "cat_match_${it.id}" }) { book ->
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

@Composable
private fun CategoryListCard(
    category: BookCategory,
    bookCount: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_card_${category.id}")
            .clickable(onClick = onClick),
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
            CategoryArtworkBadge(
                category = category,
                size = 64.dp,
                cornerRadius = 14.dp,
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Ink900,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (bookCount == 1) "1 PDF Book" else "$bookCount PDF Books",
                    style = MaterialTheme.typography.labelMedium,
                    color = Emerald700,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Explore ${category.displayName}",
                tint = Ink400,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
