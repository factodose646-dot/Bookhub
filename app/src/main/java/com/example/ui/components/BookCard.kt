package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Book
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream200
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold400
import com.example.ui.theme.Gold500
import com.example.ui.theme.Ink400
import com.example.ui.theme.Ink500
import com.example.ui.theme.Ink900
import com.example.ui.theme.PlayfairDisplayFamily
import kotlin.math.roundToInt

@Composable
fun BookHubBrandTitle(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Emerald800,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = Gold400,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Cream50, fontWeight = FontWeight.Bold)) {
                    append("Book")
                }
                withStyle(SpanStyle(color = Gold400, fontWeight = FontWeight.Bold)) {
                    append("Hub")
                }
            },
            fontFamily = PlayfairDisplayFamily,
            fontSize = 22.sp,
        )
    }
}

@Composable
fun BookCoverCard(
    book: Book,
    onClick: () -> Unit,
    onToggleBookmark: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(156.dp)
            .testTag("book_card_${book.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Cream50),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box {
                BookCoverArtwork(
                    book = book,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp),
                    cornerRadius = 14.dp,
                )
                if (onToggleBookmark != null) {
                    Surface(
                        color = Emerald950.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(30.dp)
                            .clickable(onClick = onToggleBookmark),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (book.isBookmarked) {
                                    Icons.Rounded.Bookmark
                                } else {
                                    Icons.Rounded.BookmarkBorder
                                },
                                contentDescription = "Bookmark ${book.title}",
                                tint = if (book.isBookmarked) Gold400 else Cream50,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = book.category.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = Emerald700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink900,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = Ink400,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = book.fileSizeMb,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Ink500,
                    )
                }

                if (book.pageCount != null) {
                    Text(
                        text = "${book.pageCount}p",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink500,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Gold500,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${book.rating}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink500,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueReadingCard(
    book: Book,
    onClick: () -> Unit,
    onResumeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressPercent = (book.readingProgress * 100).roundToInt()
    Card(
        modifier = modifier
            .width(292.dp)
            .testTag("continue_card_${book.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Emerald900),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
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
                    .width(68.dp)
                    .height(94.dp),
                cornerRadius = 10.dp,
                showPdfBadge = false,
                compact = true,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.category.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Cream50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (book.pageCount != null) {
                        "Page ${book.lastReadPage} of ${book.pageCount} • ${book.fileSizeMb}"
                    } else {
                        "${book.fileSizeMb} PDF"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Emerald100.copy(alpha = 0.8f),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { book.readingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Gold400,
                    trackColor = Emerald800,
                    strokeCap = StrokeCap.Round,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$progressPercent% completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = Emerald100,
                    )
                    Surface(
                        color = Gold400,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onResumeClick),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Emerald950,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Read",
                                style = MaterialTheme.typography.labelSmall,
                                color = Emerald950,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookListRowCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Cream50),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                    .width(64.dp)
                    .height(88.dp),
                cornerRadius = 10.dp,
                compact = true,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = Emerald100,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = book.category.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Emerald900,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink500,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = book.fileSizeMb,
                        style = MaterialTheme.typography.labelSmall,
                        color = Emerald700,
                    )
                    if (book.pageCount != null) {
                        Text(
                            text = "• ${book.pageCount} Pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink500,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Gold500,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${book.rating}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink900,
                        )
                    }
                }
            }
        }
    }
}
