package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Book
import com.example.pdf.PdfDocumentEngine
import com.example.pdf.PdfSourceResolver
import kotlinx.coroutines.launch
import com.example.ui.components.BookCoverArtwork
import com.example.ui.components.BookCoverCard
import com.example.ui.theme.AdminCoral
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream200
import com.example.ui.theme.Cream300
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold100
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.example.ui.theme.Ink400
import com.example.ui.theme.Ink500
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink900
import com.example.ui.theme.PlayfairDisplayFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDetailsScreen(
    book: Book,
    relatedBooks: List<Book>,
    onBack: () -> Unit,
    onReadNow: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleLibrary: () -> Unit,
    onRelatedBookClick: (String) -> Unit,
    onRelatedBookToggleBookmark: (String) -> Unit,
    onSharePreview: () -> Unit,
    onShowMessage: (String) -> Unit,
    onDownloadPdf: (suspend () -> Result<Long>)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDownloadUnavailableDialog by remember { mutableStateOf(false) }
    var isDownloadingOffline by remember { mutableStateOf(false) }
    val resolvedPdfSource = remember(book.id, book.pdfSourceUrl, book.pdfAssetPath, book.googleDriveFileId) {
        PdfSourceResolver.resolve(book)
    }
    var isCachedOffline by remember(book.id, resolvedPdfSource) {
        mutableStateOf(PdfDocumentEngine(context.applicationContext).isCachedOffline(book.id, resolvedPdfSource))
    }
    LaunchedEffect(book.id, resolvedPdfSource) {
        isCachedOffline = PdfDocumentEngine(context.applicationContext).isCachedOffline(book.id, resolvedPdfSource)
    }
    val hasPdfFile = resolvedPdfSource != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Emerald950, Emerald900),
                ),
            )
            .verticalScroll(rememberScrollState()),
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("details_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = Cream50,
                )
            }

            Text(
                text = "Book Details",
                style = MaterialTheme.typography.titleMedium,
                color = Cream50,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.testTag("details_header_bookmark_button"),
                ) {
                    Icon(
                        imageVector = if (book.isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (book.isBookmarked) "Remove bookmark" else "Add bookmark",
                        tint = if (book.isBookmarked) Gold400 else Cream50,
                    )
                }
                IconButton(
                    onClick = onSharePreview,
                    modifier = Modifier.testTag("details_share_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share book",
                        tint = Cream50,
                    )
                }
            }
        }

        // Large Hero Cover Showcase
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(280.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Gold400.copy(alpha = 0.24f),
                            book.coverSecondary.copy(alpha = 0.20f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension / 2f,
                    ),
                )
            }
            BookCoverArtwork(
                book = book,
                modifier = Modifier
                    .width(178.dp)
                    .height(252.dp)
                    .testTag("details_cover_artwork"),
                cornerRadius = 18.dp,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Curved Cream Details Sheet
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = Cream100,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Category Pill
                        Surface(
                            color = Emerald100,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("details_category_badge"),
                        ) {
                            Text(
                                text = book.category.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Emerald900,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Complete Book Title
                        Text(
                            text = book.title,
                            fontFamily = PlayfairDisplayFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            color = Ink900,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("details_book_title"),
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "by ${book.author} • ${book.publishedYear}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink500,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Metadata Pills: PDF File Size, Total Pages (when available), Date Added (when available), Language
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MetaPill(
                                icon = Icons.Rounded.Description,
                                text = "${book.fileSizeMb} PDF",
                            )
                            if (book.pageCount != null) {
                                MetaPill(
                                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                                    text = "${book.pageCount} Pages",
                                )
                            } else {
                                MetaPill(
                                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                                    text = "Pages N/A",
                                    muted = true,
                                )
                            }
                            if (!book.dateAdded.isNullOrBlank()) {
                                MetaPill(
                                    icon = Icons.Rounded.CalendarToday,
                                    text = "Added ${book.dateAdded}",
                                )
                            }
                            Surface(
                                color = Cream50,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Cream200),
                            ) {
                                Text(
                                    text = book.language,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Ink700,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Rating Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            repeat(5) { idx ->
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = if (idx < book.rating.toInt()) Gold500 else Cream300,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${book.rating} (${book.reviewCount} Reviews)",
                                style = MaterialTheme.typography.labelLarge,
                                color = Ink900,
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Book Description with Graceful Missing-Data Placeholder
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "About this Book",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Ink900,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!book.description.isNullOrBlank()) {
                                Text(
                                    text = book.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Ink700,
                                )
                            } else {
                                Surface(
                                    color = Cream50,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Cream200),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Info,
                                            contentDescription = null,
                                            tint = Ink400,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "No description is available for this edition yet.",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontStyle = FontStyle.Italic,
                                            ),
                                            color = Ink500,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action 1: READ NOW
                        Button(
                            onClick = onReadNow,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald700,
                                contentColor = Cream50,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("details_read_now_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Read Now",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action 2: ADD TO FAVORITES + Secondary Library Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = onToggleFavorite,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if (book.isFavorite) AdminCoral else Cream300),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (book.isFavorite) AdminCoral.copy(alpha = 0.12f) else Cream50,
                                    contentColor = Ink900,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("details_favorite_button"),
                            ) {
                                Icon(
                                    imageVector = if (book.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (book.isFavorite) AdminCoral else Ink700,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (book.isFavorite) "Favorited" else "Add to Favorites",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }

                            OutlinedButton(
                                onClick = onToggleLibrary,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if (book.isInLibrary) Emerald700 else Cream300),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (book.isInLibrary) Emerald100 else Cream50,
                                    contentColor = Ink900,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("details_library_button"),
                            ) {
                                Icon(
                                    imageVector = if (book.isInLibrary) Icons.Rounded.CheckCircle else Icons.Rounded.LibraryAdd,
                                    contentDescription = null,
                                    tint = if (book.isInLibrary) Emerald700 else Ink700,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (book.isInLibrary) "In Library" else "Save Library",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action 3: DOWNLOAD PDF (offline caching for Drive/HTTPS/local sources; unavailable dialog when no PDF attached)
                        OutlinedButton(
                            onClick = {
                                if (!hasPdfFile) {
                                    showDownloadUnavailableDialog = true
                                } else if (isCachedOffline) {
                                    onShowMessage("PDF is already cached offline for instant reading.")
                                } else if (!isDownloadingOffline) {
                                    isDownloadingOffline = true
                                    scope.launch {
                                        val result = if (onDownloadPdf != null) {
                                            onDownloadPdf()
                                        } else {
                                            PdfDocumentEngine(context.applicationContext)
                                                .prefetchPdfForOffline(book.id, resolvedPdfSource)
                                        }
                                        isDownloadingOffline = false
                                        result.onSuccess {
                                            isCachedOffline = true
                                            onShowMessage("Downloaded \"${book.title}\" for offline reading.")
                                        }.onFailure { err ->
                                            onShowMessage(err.localizedMessage ?: "Failed to download PDF.")
                                        }
                                    }
                                }
                            },
                            enabled = !isDownloadingOffline,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isCachedOffline && hasPdfFile) Emerald700 else Cream300),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isCachedOffline && hasPdfFile) Emerald100 else Cream50,
                                contentColor = if (hasPdfFile) Ink900 else Ink500,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("details_download_pdf_button"),
                        ) {
                            if (isDownloadingOffline) {
                                CircularProgressIndicator(
                                    color = Emerald700,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Downloading PDF...",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            } else {
                                Icon(
                                    imageVector = when {
                                        !hasPdfFile -> Icons.Rounded.CloudOff
                                        isCachedOffline -> Icons.Rounded.CheckCircle
                                        else -> Icons.Rounded.Download
                                    },
                                    contentDescription = null,
                                    tint = if (hasPdfFile) Emerald700 else Ink400,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        !hasPdfFile -> "Download PDF • Unavailable"
                                        isCachedOffline -> "Available Offline (${book.fileSizeMb})"
                                        else -> "Download PDF (${book.fileSizeMb})"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                if (!hasPdfFile) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Gold100,
                                        shape = RoundedCornerShape(50),
                                    ) {
                                        Text(
                                            text = "No File",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Ink700,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Related Books Section
                    Spacer(modifier = Modifier.height(26.dp))
                    HorizontalDivider(
                        color = Cream200,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                            Text(
                                text = "Related Books",
                                style = MaterialTheme.typography.titleLarge,
                                color = Ink900,
                            )
                            Text(
                                text = "More from ${book.category.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink500,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (relatedBooks.isEmpty()) {
                            Surface(
                                color = Cream50,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp),
                            ) {
                                Text(
                                    text = "No additional books in ${book.category.displayName} yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Ink500,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 22.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(relatedBooks, key = { it.id }) { related ->
                                    BookCoverCard(
                                        book = related,
                                        onClick = { onRelatedBookClick(related.id) },
                                        onToggleBookmark = { onRelatedBookToggleBookmark(related.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDownloadUnavailableDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadUnavailableDialog = false },
            containerColor = Cream50,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = Emerald700,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(
                    text = "PDF Download Unavailable",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink900,
                )
            },
            text = {
                Text(
                    text = "No downloadable PDF file is connected to \"${book.title}\" yet. Ask an administrator to upload a PDF file for this title.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink700,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDownloadUnavailableDialog = false }) {
                    Text("Got It", color = Emerald700)
                }
            },
        )
    }
}

@Composable
private fun MetaPill(
    icon: ImageVector,
    text: String,
    muted: Boolean = false,
) {
    Surface(
        color = Cream50,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Cream200),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (muted) Ink400 else Emerald700,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (muted) Ink500 else Ink900,
            )
        }
    }
}
