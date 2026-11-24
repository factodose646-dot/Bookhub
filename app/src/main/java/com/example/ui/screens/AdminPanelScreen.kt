package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AddToDrive
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AdminCloudVerification
import com.example.cloud.DriveAuthStatus
import com.example.data.LocalBookStorageGateway
import com.example.ui.BookHubUiState
import com.example.ui.components.BookCoverArtwork
import com.example.ui.theme.AdminCoral
import com.example.ui.theme.AdminGoldBg
import com.example.ui.theme.AdminGoldInk
import com.example.ui.theme.AdminLavenderBg
import com.example.ui.theme.AdminLavenderInk
import com.example.ui.theme.AdminMintBg
import com.example.ui.theme.AdminMintInk
import com.example.ui.theme.AdminSkyBg
import com.example.ui.theme.AdminSkyInk
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
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink900
import com.example.ui.theme.PlayfairDisplayFamily
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    uiState: BookHubUiState,
    onBack: () -> Unit,
    onOpenAddBook: () -> Unit,
    onOpenManageBooks: () -> Unit,
    onOpenManageCategories: () -> Unit,
    onEditBook: (String) -> Unit,
    onReadBook: (String) -> Unit,
    onChangePasscode: (current: String, next: String, confirm: String) -> Boolean,
    onLogoutAdmin: () -> Unit,
    onRequestDriveAuthorization: (suspend (Context) -> DriveAuthStatus)? = null,
    onDriveConsentResult: ((Context, Intent?) -> DriveAuthStatus)? = null,
    onMigrateLocalCatalogToCloud: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activityContext = remember(context) { context.findPanelActivity() ?: context }
    val scope = rememberCoroutineScope()

    var showSecuritySheet by remember { mutableStateOf(false) }
    var currentPasscode by remember { mutableStateOf("") }
    var newPasscode by remember { mutableStateOf("") }
    var confirmNewPasscode by remember { mutableStateOf("") }
    var isConnectingDrive by remember { mutableStateOf(false) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        onDriveConsentResult?.invoke(activityContext, result.data)
        isConnectingDrive = false
    }

    val verifiedCloudAdmin = uiState.adminCloudVerification as? AdminCloudVerification.Verified
    val session = uiState.adminSession
    val adminHeaderLabel = when {
        verifiedCloudAdmin != null -> "Verified • ${verifiedCloudAdmin.profile.displayName}"
        session != null -> "Authorized • ${session.adminName}"
        else -> null
    }

    val uploadedCount = uiState.books.count { it.isAdminUploaded }
    val draftCount = uiState.books.count { !it.isPublished }
    val uploadedBytesFormatted = LocalBookStorageGateway.formatBytesLabel(uiState.adminUploadedPdfBytes)
    val totalCatalogFormatted = String.format(Locale.US, "%.1f MB", uiState.totalCatalogSizeMb)
    val recentBooks = remember(uiState.books) {
        uiState.recentlyAddedBooksForAdmin.take(5)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(listOf(Emerald950, Emerald900)),
            )
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
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
                modifier = Modifier.testTag("admin_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = Cream50,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Gold400.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = Gold400,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Admin Panel",
                        style = MaterialTheme.typography.titleLarge,
                        color = Cream50,
                    )
                    if (adminHeaderLabel != null) {
                        Text(
                            text = adminHeaderLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Gold400,
                        )
                    }
                }
            }

            IconButton(
                onClick = onLogoutAdmin,
                modifier = Modifier.testTag("admin_logout_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Sign out of Admin Panel",
                    tint = Gold400,
                )
            }
        }

        // Quick Action: Add New Book CTA
        Button(
            onClick = onOpenAddBook,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold400,
                contentColor = Emerald950,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp)
                .testTag("admin_quick_add_book"),
        ) {
            Icon(
                imageVector = Icons.Rounded.AddCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add New PDF Book",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2x2 Action Cards Grid
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AdminActionCard(
                    title = "Upload\nNew Book",
                    icon = Icons.Rounded.CloudUpload,
                    backgroundColor = AdminSkyBg,
                    inkColor = AdminSkyInk,
                    iconContainerColor = Color(0xFF42A5F5),
                    testTag = "admin_card_upload",
                    onClick = onOpenAddBook,
                    modifier = Modifier.weight(1f),
                )
                AdminActionCard(
                    title = "Manage\nBooks",
                    icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    backgroundColor = AdminLavenderBg,
                    inkColor = AdminLavenderInk,
                    iconContainerColor = Color(0xFF7E57C2),
                    testTag = "admin_card_manage",
                    onClick = onOpenManageBooks,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AdminActionCard(
                    title = "Categories\nManagement",
                    icon = Icons.Rounded.Category,
                    backgroundColor = AdminMintBg,
                    inkColor = AdminMintInk,
                    iconContainerColor = Emerald600,
                    testTag = "admin_card_categories",
                    onClick = onOpenManageCategories,
                    modifier = Modifier.weight(1f),
                )
                AdminActionCard(
                    title = "Admin\nSecurity",
                    icon = Icons.Rounded.Settings,
                    backgroundColor = AdminGoldBg,
                    inkColor = AdminGoldInk,
                    iconContainerColor = Color(0xFFE09F24),
                    testTag = "admin_card_settings",
                    onClick = { showSecuritySheet = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cloud & Google Drive Status Card
        if (uiState.useCloudAdminAuth) {
            Surface(
                color = Emerald850,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Gold400.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = Gold400,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cloud & Google Drive",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Cream50,
                            )
                        }
                        Surface(
                            color = if (uiState.isDriveAuthorized) Emerald700 else Emerald800,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = if (uiState.isDriveAuthorized) "Drive Connected" else "Drive Pending",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isDriveAuthorized) Cream50 else Gold400,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Firestore: ${if (uiState.isCloudSyncActive) "Live Sync Active" else "Connecting..."} • ${uiState.cloudSyncedBooksCount} synced / ${uiState.localUnsyncedBooksCount} local-only",
                        style = MaterialTheme.typography.bodySmall,
                        color = Emerald100.copy(alpha = 0.85f),
                    )
                    if (!uiState.cloudSyncError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.cloudSyncError,
                            style = MaterialTheme.typography.bodySmall,
                            color = AdminCoral,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (onRequestDriveAuthorization != null && !isConnectingDrive) {
                                    isConnectingDrive = true
                                    scope.launch {
                                        val status = onRequestDriveAuthorization(activityContext)
                                        val sender = status.pendingConsentIntentSender
                                        if (sender != null) {
                                            driveConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                        } else {
                                            isConnectingDrive = false
                                        }
                                    }
                                }
                            },
                            enabled = !isConnectingDrive,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Gold400.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("admin_connect_drive_button"),
                        ) {
                            if (isConnectingDrive) {
                                CircularProgressIndicator(
                                    color = Gold400,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Icon(Icons.Rounded.AddToDrive, null, tint = Gold400, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isDriveAuthorized) "Refresh Drive" else "Connect Drive",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Cream50,
                                    maxLines = 1,
                                )
                            }
                        }

                        Button(
                            onClick = onMigrateLocalCatalogToCloud,
                            enabled = !uiState.isMigratingCatalog,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald700,
                                contentColor = Cream50,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("admin_sync_catalog_button"),
                        ) {
                            if (uiState.isMigratingCatalog) {
                                CircularProgressIndicator(
                                    color = Cream50,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Icon(Icons.Rounded.CloudSync, null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sync Catalog",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Dashboard Statistics Card
        Surface(
            color = Emerald850,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Catalog Statistics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Cream50,
                )
                Text(
                    text = "${uiState.publishedBooks.size} published • $draftCount drafts • $uploadedCount managed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Emerald100.copy(alpha = 0.72f),
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AdminStatBox(
                        icon = Icons.Rounded.LibraryBooks,
                        value = "${uiState.books.size}",
                        label = "Total Books",
                        modifier = Modifier.weight(1f),
                    )
                    AdminStatBox(
                        icon = Icons.Rounded.Category,
                        value = "${uiState.categories.size}",
                        label = "Categories",
                        modifier = Modifier.weight(1f),
                    )
                    AdminStatBox(
                        icon = Icons.Rounded.PictureAsPdf,
                        value = "${uiState.totalPdfFilesCount}",
                        label = "PDF Files",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // PDF Storage Used Card
        Surface(
            color = Emerald850,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = Emerald800,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = Gold400,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PDF Storage Used",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Cream50,
                        )
                        Text(
                            text = if (uiState.useCloudAdminAuth) {
                                "Google Drive storage + on-device offline reading cache"
                            } else {
                                "Local persistent storage"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Emerald100.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Managed PDFs: $uploadedBytesFormatted",
                        style = MaterialTheme.typography.labelMedium,
                        color = Cream50,
                    )
                    Text(
                        text = "Catalog Total: $totalCatalogFormatted",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold400,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (uiState.totalCatalogSizeMb / 500.0).toFloat().coerceIn(0.04f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Gold400,
                    trackColor = Emerald950,
                    strokeCap = StrokeCap.Round,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.FolderZip,
                            contentDescription = null,
                            tint = Emerald100,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Total Pages: ${uiState.totalCatalogPages}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Emerald100,
                        )
                    }
                    Text(
                        text = if (uiState.isCloudSyncActive) "Cloud Sync: Live" else "Cloud Sync: Ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gold400,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Recently Added Books
        Surface(
            color = Emerald850,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Recently Added Books",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Cream50,
                        )
                        Text(
                            text = "Tap Edit to update details or Read to inspect PDF",
                            style = MaterialTheme.typography.bodySmall,
                            color = Emerald100.copy(alpha = 0.7f),
                        )
                    }
                    TextButton(onClick = onOpenManageBooks) {
                        Text("View All", color = Gold400)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                recentBooks.forEach { book ->
                    Surface(
                        color = Emerald900,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onEditBook(book.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BookCoverArtwork(
                                book = book,
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(58.dp),
                                cornerRadius = 8.dp,
                                showPdfBadge = false,
                                compact = true,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Cream50,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${book.category.displayName} • ${book.fileSizeMb}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Emerald100.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val statusMeta = buildString {
                                    append(if (book.isPublished) "Published" else "Draft")
                                    if (!book.googleDriveFileId.isNullOrBlank()) append(" • Drive")
                                    if (!book.dateAdded.isNullOrBlank()) append(" • ${book.dateAdded}")
                                }
                                Text(
                                    text = statusMeta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (book.isPublished) Gold400 else AdminCoral,
                                )
                            }
                            IconButton(onClick = { onReadBook(book.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.MenuBook,
                                    contentDescription = "Read ${book.title}",
                                    tint = Cream50,
                                )
                            }
                            IconButton(onClick = { onEditBook(book.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit ${book.title}",
                                    tint = Gold400,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showSecuritySheet) {
        ModalBottomSheet(
            onDismissRequest = { showSecuritySheet = false },
            containerColor = Cream50,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Admin Security & Access",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink900,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.useCloudAdminAuth) {
                    Surface(
                        color = Cream100,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Firebase Verified Administrator",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Emerald700,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            verifiedCloudAdmin?.let { verified ->
                                Text(
                                    text = "${verified.profile.displayName} (${verified.profile.email ?: verified.profile.uid})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink900,
                                )
                                Text(
                                    text = "Verification: ${verified.sourceLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Ink700,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Firestore Security Rules reject non-admin writes and hide unpublished drafts from readers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink500,
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Cream100,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "On-device salted PBKDF2 passcodes and session verification are active in offline mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink700,
                            modifier = Modifier.padding(12.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Update Admin Passcode",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Ink900,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = currentPasscode,
                        onValueChange = { currentPasscode = it },
                        label = { Text("Current Passcode") },
                        leadingIcon = { Icon(Icons.Rounded.Key, null, tint = Emerald700) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPasscode,
                        onValueChange = { newPasscode = it },
                        label = { Text("New Passcode (6+ chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmNewPasscode,
                        onValueChange = { confirmNewPasscode = it },
                        label = { Text("Confirm New Passcode") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val updated = onChangePasscode(currentPasscode, newPasscode, confirmNewPasscode)
                            if (updated) {
                                currentPasscode = ""
                                newPasscode = ""
                                confirmNewPasscode = ""
                                showSecuritySheet = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald700,
                            contentColor = Cream50,
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Save New Passcode")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        showSecuritySheet = false
                        onLogoutAdmin()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink900,
                        contentColor = Cream50,
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out of Admin Session")
                }

                Spacer(modifier = Modifier.height(22.dp))
            }
        }
    }
}

private tailrec fun Context.findPanelActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPanelActivity()
    else -> null
}

@Composable
private fun AdminActionCard(
    title: String,
    icon: ImageVector,
    backgroundColor: Color,
    inkColor: Color,
    iconContainerColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .height(132.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                color = iconContainerColor,
                shape = CircleShape,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Cream50,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = inkColor,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun AdminStatBox(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Emerald800,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Gold400,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontFamily = PlayfairDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Cream50,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Emerald100.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
