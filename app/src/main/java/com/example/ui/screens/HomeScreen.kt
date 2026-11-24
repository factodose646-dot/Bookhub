package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BookCatalogQueries
import com.example.model.Book
import com.example.model.BookCategory
import com.example.model.BookListingType
import com.example.model.FeaturedBannerItem
import com.example.ui.BookHubUiState
import com.example.ui.components.BookCoverArtwork
import com.example.ui.components.BookCoverCard
import com.example.ui.components.BookHubBrandTitle
import com.example.ui.components.BookListRowCard
import com.example.ui.components.ContinueReadingCard
import com.example.ui.components.categoryIcon
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
import com.example.ui.theme.PlayfairDisplayFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: BookHubUiState,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onBookClick: (String) -> Unit,
    onReadNowClick: (String) -> Unit,
    onCategoryClick: (BookCategory) -> Unit,
    onSeeAllCategories: () -> Unit,
    onSeeAllListing: (BookListingType) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onMarkNotificationsRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showQuickMenuSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Emerald950, Emerald900),
                ),
            ),
    ) {
        // Dark Emerald Top Header
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
                IconButton(
                    onClick = { showQuickMenuSheet = true },
                    modifier = Modifier.testTag("home_menu_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Open menu",
                        tint = Cream50,
                    )
                }

                BookHubBrandTitle()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier.testTag("home_search_button"),
                    ) {
                        Icon(
                            imageVector = if (uiState.isHomeSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search books",
                            tint = Cream50,
                        )
                    }
                    IconButton(
                        onClick = {
                            showNotificationsSheet = true
                            onMarkNotificationsRead()
                        },
                        modifier = Modifier.testTag("home_notifications_button"),
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotificationCount > 0) {
                                    Badge(containerColor = Gold400, contentColor = Emerald950) {
                                        Text("${uiState.unreadNotificationCount}")
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = "Notifications",
                                tint = Cream50,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.isHomeSearchActive) {
                OutlinedTextField(
                    value = uiState.homeSearchQuery,
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
                        if (uiState.homeSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSearch,
                                modifier = Modifier.testTag("home_clear_search"),
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
                        .testTag("home_search_input"),
                )
            }
        }

        // Cream Surface Content Area
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Cream100,
        ) {
            val query = uiState.homeSearchQuery.trim()
            if (query.isNotEmpty()) {
                val filteredBooks = BookCatalogQueries.filterByQuery(uiState.publishedBooks, query)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${filteredBooks.size} results for \"$query\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink900,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onClearSearch) {
                                Text("Clear", color = Emerald700)
                            }
                        }
                    }
                    if (filteredBooks.isEmpty()) {
                        item {
                            Surface(
                                color = Cream50,
                                shape = RoundedCornerShape(18.dp),
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No matching books found",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Ink900,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Try searching another book title or category name.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Ink500,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
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
                        items(filteredBooks, key = { it.id }) { book ->
                            BookListRowCard(
                                book = book,
                                onClick = { onBookClick(book.id) },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // 1. Featured Books Section + Banner Carousel
                    item {
                        Column {
                            SectionHeader(
                                title = "Featured Books",
                                actionLabel = "See All",
                                onActionClick = { onSeeAllListing(BookListingType.FEATURED) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                testTag = "home_see_all_featured",
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FeaturedBooksBannerCarousel(
                                banners = uiState.featuredBanners,
                                books = uiState.publishedBooks,
                                onExploreBook = onBookClick,
                            )
                        }
                    }

                    // 2. Explore Categories
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SectionHeader(
                                title = "Explore Categories",
                                actionLabel = "See All",
                                onActionClick = onSeeAllCategories,
                                testTag = "home_see_all_categories",
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            CategoryGridPreview(
                                categories = uiState.categories,
                                onCategoryClick = onCategoryClick,
                            )
                        }
                    }

                    // 3. Continue Reading
                    if (uiState.continueReadingBooks.isNotEmpty()) {
                        item {
                            Column {
                                SectionHeader(
                                    title = "Continue Reading",
                                    actionLabel = "See All",
                                    onActionClick = { onSeeAllListing(BookListingType.CONTINUE_READING) },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    testTag = "home_see_all_continue",
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(uiState.continueReadingBooks, key = { it.id }) { book ->
                                        ContinueReadingCard(
                                            book = book,
                                            onClick = { onBookClick(book.id) },
                                            onResumeClick = { onReadNowClick(book.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Popular Books
                    item {
                        Column {
                            SectionHeader(
                                title = "Popular Books",
                                actionLabel = "See All",
                                onActionClick = { onSeeAllListing(BookListingType.POPULAR) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                testTag = "home_see_all_popular",
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(uiState.popularBooks, key = { it.id }) { book ->
                                    BookCoverCard(
                                        book = book,
                                        onClick = { onBookClick(book.id) },
                                        onToggleBookmark = { onToggleBookmark(book.id) },
                                    )
                                }
                            }
                        }
                    }

                    // 5. Recently Added Books
                    item {
                        Column {
                            SectionHeader(
                                title = "Recently Added Books",
                                actionLabel = "See All",
                                onActionClick = { onSeeAllListing(BookListingType.RECENT) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                testTag = "home_see_all_recent",
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(uiState.recentBooks, key = { it.id }) { book ->
                                    BookCoverCard(
                                        book = book,
                                        onClick = { onBookClick(book.id) },
                                        onToggleBookmark = { onToggleBookmark(book.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotificationsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotificationsSheet = false },
            containerColor = Cream50,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink900,
                )
                Spacer(modifier = Modifier.height(12.dp))
                uiState.notifications.forEach { item ->
                    Surface(
                        color = Cream100,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ink900,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = item.timeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Emerald700,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink500,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showQuickMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickMenuSheet = false },
            containerColor = Emerald950,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                BookHubBrandTitle()
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PDF Library & Quick Navigation",
                    style = MaterialTheme.typography.bodySmall,
                    color = Emerald100.copy(alpha = 0.75f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                QuickMenuRow(
                    icon = Icons.Rounded.Bookmark,
                    title = "Bookmarks",
                    subtitle = "${uiState.bookmarkedBooks.size} saved books & page notes",
                    onClick = {
                        showQuickMenuSheet = false
                        onOpenBookmarks()
                    },
                )
                QuickMenuRow(
                    icon = Icons.Rounded.MenuBook,
                    title = "My Library",
                    subtitle = "Continue reading & personal shelves",
                    onClick = {
                        showQuickMenuSheet = false
                        onOpenLibrary()
                    },
                )
                HorizontalDivider(
                    color = Emerald800,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                QuickMenuRow(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = "Admin Panel Dashboard",
                    subtitle = "Catalog stats, categories & UI preview tools",
                    highlightGold = true,
                    onClick = {
                        showQuickMenuSheet = false
                        onOpenAdminPanel()
                    },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun QuickMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    highlightGold: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = Emerald900,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (highlightGold) Gold400.copy(alpha = 0.2f) else Emerald800,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (highlightGold) Gold400 else Cream50,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Cream50,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Emerald100.copy(alpha = 0.75f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = if (highlightGold) Gold400 else Emerald100,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FeaturedBooksBannerCarousel(
    banners: List<FeaturedBannerItem>,
    books: List<Book>,
    onExploreBook: (String) -> Unit,
) {
    if (banners.isEmpty() || books.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { banners.size })

    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val banner = banners[page]
            val targetBook = books.find { it.id == banner.targetBookId } ?: books.firstOrNull() ?: return@HorizontalPager
            Surface(
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(186.dp)
                    .clickable { onExploreBook(targetBook.id) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(banner.gradientStart, banner.gradientEnd),
                            ),
                        ),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Gold400.copy(alpha = 0.22f), Color.Transparent),
                                center = Offset(size.width * 0.85f, size.height * 0.25f),
                                radius = size.width * 0.45f,
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.25f),
                            radius = size.width * 0.45f,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Surface(
                                color = Gold400.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = banner.badge,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 0.8.sp,
                                    ),
                                    color = Gold400,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = banner.headline,
                                fontFamily = PlayfairDisplayFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                lineHeight = 24.sp,
                                color = Cream50,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = banner.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Emerald100.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                color = Gold400,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onExploreBook(targetBook.id) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Explore Now",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Emerald950,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = Emerald950,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        BookCoverArtwork(
                            book = targetBook,
                            modifier = Modifier
                                .width(92.dp)
                                .height(132.dp),
                            cornerRadius = 12.dp,
                            compact = true,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(banners.size) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(if (active) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) Emerald700 else Cream200),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Ink900,
        )
        TextButton(
            onClick = onActionClick,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Emerald700,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = Emerald700,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun CategoryGridPreview(
    categories: List<BookCategory>,
    onCategoryClick: (BookCategory) -> Unit,
) {
    val rows = categories.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { category ->
                    Surface(
                        color = Cream50,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_cat_${category.id}")
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onCategoryClick(category) },
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(category.accentColor, category.secondaryColor),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = categoryIcon(category),
                                    contentDescription = category.displayName,
                                    tint = Cream50,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = category.shortName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                ),
                                color = Ink900,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                minLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                repeat((4 - rowItems.size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
