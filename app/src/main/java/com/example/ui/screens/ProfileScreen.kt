package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BookHubUiState
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald850
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold400
import com.example.ui.theme.Ink500
import com.example.ui.theme.Ink900
import com.example.ui.theme.PlayfairDisplayFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uiState: BookHubUiState,
    onOpenMyLibrary: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onShowToastMessage: (String) -> Unit,
    onSignInWithGoogle: (suspend (Context) -> Unit)? = null,
    onSignOutGoogle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activityContext = remember(context) { context.findProfileActivity() ?: context }
    val scope = rememberCoroutineScope()

    var isAuthBusy by remember { mutableStateOf(false) }
    var showPreferencesSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var readingRemindersEnabled by remember { mutableStateOf(true) }
    var autoNightReader by remember { mutableStateOf(uiState.readerNightMode) }

    val account = uiState.cloudAccount

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
        // Profile Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = Emerald800,
                        shape = CircleShape,
                        border = BorderStroke(2.dp, Gold400),
                        modifier = Modifier.size(62.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "Profile avatar",
                                tint = Cream50,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (account != null) "Signed In" else "Welcome to BookHub",
                            style = MaterialTheme.typography.labelMedium,
                            color = Gold400,
                        )
                        Text(
                            text = account?.displayName ?: "Guest Reader",
                            fontFamily = PlayfairDisplayFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Cream50,
                        )
                        Text(
                            text = when {
                                account != null && uiState.isAdminAuthenticated ->
                                    "${account.email ?: account.displayName} • Admin Verified"
                                account != null ->
                                    account.email ?: "Google Account Connected"
                                uiState.isCloudSyncActive ->
                                    "Cloud Catalog Connected • Guest Mode"
                                else ->
                                    "Browse & Read Offline PDFs"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Emerald100.copy(alpha = 0.78f),
                        )
                    }
                }

                IconButton(
                    onClick = { showPreferencesSheet = true },
                    modifier = Modifier.testTag("profile_settings_icon"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = Cream50,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Reading Stats
            Surface(
                color = Emerald850,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ProfileStatItem(
                        value = "${uiState.publishedBooks.count { it.isInLibrary }}",
                        label = "In Library",
                    )
                    ProfileStatItem(
                        value = "${uiState.bookmarkedBooks.size}",
                        label = "Bookmarks",
                    )
                    ProfileStatItem(
                        value = "${uiState.continueReadingBooks.size}",
                        label = "In Progress",
                    )
                }
            }
        }

        // Menu List Container
        Surface(
            color = Emerald850,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                ProfileMenuItem(
                    icon = Icons.Rounded.MenuBook,
                    title = "My Library",
                    subtitle = "Saved & in-progress PDFs",
                    onClick = onOpenMyLibrary,
                    testTag = "profile_menu_library",
                )
                ProfileDivider()
                ProfileMenuItem(
                    icon = Icons.Rounded.Bookmark,
                    title = "Bookmarks",
                    subtitle = "Favorite books & saved pages",
                    onClick = onOpenBookmarks,
                    testTag = "profile_menu_bookmarks",
                )
                ProfileDivider()
                ProfileMenuItem(
                    icon = Icons.Rounded.History,
                    title = "Reading History",
                    subtitle = "Recently opened titles",
                    onClick = { showHistorySheet = true },
                    testTag = "profile_menu_history",
                )
                ProfileDivider()
                ProfileMenuItem(
                    icon = if (uiState.isCloudSyncActive) Icons.Rounded.CloudDone else Icons.Rounded.Download,
                    title = "Cloud & Offline Catalog",
                    subtitle = "${uiState.cloudSyncedBooksCount} cloud • ${uiState.publishedBooks.size} available books",
                    onClick = onOpenMyLibrary,
                    testTag = "profile_menu_downloads",
                )
                ProfileDivider()
                ProfileMenuItem(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = "Admin Panel",
                    subtitle = "Google Drive uploads, Firestore sync & categories",
                    badgeText = if (uiState.isAdminAuthenticated) "Verified" else "Sign In",
                    highlightGold = true,
                    onClick = onOpenAdminPanel,
                    testTag = "profile_menu_admin",
                )
                ProfileDivider()
                ProfileMenuItem(
                    icon = Icons.Rounded.Settings,
                    title = "Settings",
                    subtitle = "Reader & display preferences",
                    onClick = { showPreferencesSheet = true },
                    testTag = "profile_menu_settings",
                )
                ProfileDivider()
                ProfileMenuItem(
                    icon = Icons.Rounded.Info,
                    title = "About BookHub",
                    subtitle = "Cloud PDF Library • Firebase & Drive Ready",
                    onClick = { showAboutDialog = true },
                    testTag = "profile_menu_about",
                )
                ProfileDivider()
                ProfileMenuItem(
                    icon = Icons.Rounded.Star,
                    title = "Rate BookHub",
                    subtitle = "Share feedback on the reading experience",
                    onClick = { onShowToastMessage("Thank you for reading with BookHub!") },
                    testTag = "profile_menu_rate",
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Real Google Sign-In / Sign-Out Action
        Button(
            onClick = {
                if (account != null) {
                    onSignOutGoogle()
                } else if (onSignInWithGoogle != null && !isAuthBusy) {
                    isAuthBusy = true
                    scope.launch {
                        onSignInWithGoogle(activityContext)
                        isAuthBusy = false
                    }
                } else {
                    onShowToastMessage("Google Sign-In is unavailable in offline test mode.")
                }
            },
            enabled = !isAuthBusy,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Emerald600,
                contentColor = Cream50,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp)
                .testTag("profile_logout_button"),
        ) {
            if (isAuthBusy) {
                CircularProgressIndicator(
                    color = Cream50,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connecting Google Account...", style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(
                    imageVector = if (account != null) {
                        Icons.AutoMirrored.Rounded.Logout
                    } else {
                        Icons.AutoMirrored.Rounded.Login
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (account != null) "Sign Out (${account.displayName})" else "Sign in with Google",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showPreferencesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPreferencesSheet = false },
            containerColor = Cream50,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Reader Preferences",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink900,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily Reading Reminders",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink900,
                        )
                        Text(
                            text = "Gentle evening prompt to resume your book",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink500,
                        )
                    }
                    Switch(
                        checked = readingRemindersEnabled,
                        onCheckedChange = { readingRemindersEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Emerald700),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Default Night Reader",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink900,
                        )
                        Text(
                            text = "Use dark emerald canvas in PDF reader",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink500,
                        )
                    }
                    Switch(
                        checked = autoNightReader,
                        onCheckedChange = { autoNightReader = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Emerald700),
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = Cream50,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Reading History",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink900,
                )
                Spacer(modifier = Modifier.height(12.dp))
                uiState.continueReadingBooks.forEach { book ->
                    Surface(
                        color = Cream100,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ink900,
                                )
                                Text(
                                    text = "Last read page ${book.lastReadPage} • ${book.category.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Ink500,
                                )
                            }
                            Text(
                                text = "${(book.readingProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = Emerald700,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = Cream50,
            title = {
                Text(
                    text = "About BookHub",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink900,
                )
            },
            text = {
                Text(
                    text = "BookHub is a modern PDF Books Library with real-time Cloud Firestore catalog sync, Google Drive PDF storage, offline reading cache, bookmarks, and an authenticated Administrator console.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink500,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = Emerald700)
                }
            },
        )
    }
}

private tailrec fun Context.findProfileActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findProfileActivity()
    else -> null
}

@Composable
private fun ProfileStatItem(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = PlayfairDisplayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Gold400,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Emerald100,
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    highlightGold: Boolean = false,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = if (highlightGold) Gold400.copy(alpha = 0.18f) else Emerald800,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlightGold) Gold400 else Cream50,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Cream50,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Emerald100.copy(alpha = 0.72f),
            )
        }

        if (badgeText != null) {
            Surface(
                color = Gold400,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Emerald950,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Emerald100.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(
        color = Emerald800,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
