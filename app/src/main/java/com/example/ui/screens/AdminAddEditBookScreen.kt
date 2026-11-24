package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddToDrive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cloud.DriveAuthStatus
import com.example.data.CoverImportResult
import com.example.data.PdfImportResult
import com.example.model.Book
import com.example.model.BookCategory
import com.example.model.CoverArtworkStyle
import com.example.ui.PendingDriveBookAssets
import com.example.ui.components.BookCoverArtwork
import com.example.ui.theme.AdminCoral
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream200
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold100
import com.example.ui.theme.Gold400
import com.example.ui.theme.Ink500
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink900
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAddEditBookScreen(
    categories: List<BookCategory>,
    existingBook: Book?,
    onBack: () -> Unit,
    onImportPdfUri: suspend (Uri) -> PdfImportResult,
    onImportCoverUri: suspend (Uri) -> CoverImportResult,
    onCleanupUnusedFile: (String?) -> Unit,
    onSaveNewBook: suspend (
        title: String,
        author: String,
        description: String,
        categoryId: String,
        pdfFilePath: String,
        pdfByteSize: Long,
        pdfFormattedSize: String,
        pdfPageCount: Int?,
        coverImagePath: String?,
        language: String,
        isFeatured: Boolean,
        isPopular: Boolean,
        isRecent: Boolean,
        isPublished: Boolean,
        pendingAssets: PendingDriveBookAssets,
        onPendingAssetsUpdated: (PendingDriveBookAssets) -> Unit,
    ) -> String?,
    onUpdateExistingBook: suspend (
        bookId: String,
        title: String,
        author: String,
        description: String,
        categoryId: String,
        newPdfFilePath: String?,
        newPdfByteSize: Long?,
        newPdfFormattedSize: String?,
        newPdfPageCount: Int?,
        newCoverImagePath: String?,
        removeCustomCover: Boolean,
        language: String,
        isFeatured: Boolean,
        isPopular: Boolean,
        isRecent: Boolean,
        isPublished: Boolean,
        pendingAssets: PendingDriveBookAssets,
        onPendingAssetsUpdated: (PendingDriveBookAssets) -> Unit,
    ) -> String?,
    useCloudMode: Boolean = false,
    isDriveAuthorized: Boolean = false,
    cloudUploadProgress: Float? = null,
    cloudUploadStage: String? = null,
    onRequestDriveAuthorization: (suspend (Context) -> DriveAuthStatus)? = null,
    onDriveConsentResult: ((Context, Intent?) -> DriveAuthStatus)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activityContext = remember(context) { context.findFormActivity() ?: context }
    val isEditMode = existingBook != null
    val scope = rememberCoroutineScope()

    var title by remember(existingBook?.id) { mutableStateOf(existingBook?.title ?: "") }
    var author by remember(existingBook?.id) { mutableStateOf(existingBook?.author ?: "") }
    var description by remember(existingBook?.id) { mutableStateOf(existingBook?.description ?: "") }
    var language by remember(existingBook?.id) { mutableStateOf(existingBook?.language ?: "English") }
    var selectedCategoryId by remember(existingBook?.id, categories) {
        mutableStateOf(
            existingBook?.category?.id
                ?: categories.firstOrNull()?.id
                ?: "",
        )
    }

    var isPublished by remember(existingBook?.id) { mutableStateOf(existingBook?.isPublished ?: true) }
    var isFeatured by remember(existingBook?.id) { mutableStateOf(existingBook?.isFeatured ?: false) }
    var isPopular by remember(existingBook?.id) { mutableStateOf(existingBook?.isPopular ?: false) }
    var isRecent by remember(existingBook?.id) { mutableStateOf(existingBook?.isRecent ?: true) }

    // Imported PDF state
    var pickedPdfPath by remember(existingBook?.id) { mutableStateOf<String?>(null) }
    var pickedPdfDisplayName by remember(existingBook?.id) { mutableStateOf<String?>(null) }
    var pickedPdfBytes by remember(existingBook?.id) { mutableLongStateOf(0L) }
    var pickedPdfFormattedSize by remember(existingBook?.id) { mutableStateOf<String?>(null) }
    var pickedPdfPageCount by remember(existingBook?.id) { mutableStateOf<Int?>(null) }

    // Imported Cover state
    var pickedCoverPath by remember(existingBook?.id) { mutableStateOf<String?>(null) }
    var removeCustomCover by remember(existingBook?.id) { mutableStateOf(false) }

    // Retry-safe pending Drive upload tracking (avoids duplicate Drive files if Firestore fails)
    var pendingDriveAssets by remember(existingBook?.id) { mutableStateOf(PendingDriveBookAssets()) }

    var isImportingPdf by remember { mutableStateOf(false) }
    var isImportingCover by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    suspend fun performSubmit() {
        if (isSubmitting) return
        isSubmitting = true
        formError = null
        val err = if (isEditMode && existingBook != null) {
            onUpdateExistingBook(
                existingBook.id,
                title,
                author,
                description,
                selectedCategoryId,
                pickedPdfPath,
                if (pickedPdfPath != null) pickedPdfBytes else null,
                pickedPdfFormattedSize,
                pickedPdfPageCount,
                pickedCoverPath,
                removeCustomCover,
                language,
                isFeatured,
                isPopular,
                isRecent,
                isPublished,
                pendingDriveAssets,
                { updated -> pendingDriveAssets = updated },
            )
        } else {
            onSaveNewBook(
                title,
                author,
                description,
                selectedCategoryId,
                pickedPdfPath.orEmpty(),
                pickedPdfBytes,
                pickedPdfFormattedSize.orEmpty(),
                pickedPdfPageCount,
                pickedCoverPath,
                language,
                isFeatured,
                isPopular,
                isRecent,
                isPublished,
                pendingDriveAssets,
                { updated -> pendingDriveAssets = updated },
            )
        }
        isSubmitting = false
        if (err == null) {
            onBack()
        } else {
            formError = err
        }
    }

    var continueSubmitAfterDriveAuth by remember { mutableStateOf(false) }
    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val status = onDriveConsentResult?.invoke(activityContext, result.data)
        if (status?.isAuthorized == true && continueSubmitAfterDriveAuth) {
            continueSubmitAfterDriveAuth = false
            scope.launch { performSubmit() }
        } else {
            continueSubmitAfterDriveAuth = false
            if (status != null && !status.isAuthorized && !status.errorMessage.isNullOrBlank()) {
                formError = status.errorMessage
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            isImportingPdf = true
            formError = null
            scope.launch {
                when (val result = onImportPdfUri(uri)) {
                    is PdfImportResult.Success -> {
                        if (pickedPdfPath != null && pickedPdfPath != result.filePath) {
                            onCleanupUnusedFile(pickedPdfPath)
                        }
                        pickedPdfPath = result.filePath
                        pickedPdfDisplayName = result.displayName
                        pickedPdfBytes = result.byteSize
                        pickedPdfFormattedSize = result.formattedSize
                        pickedPdfPageCount = result.pageCount
                        // Invalidate cached Drive PDF upload if a different file is selected
                        pendingDriveAssets = pendingDriveAssets.copy(localPdfPath = null, pdfUploaded = null)
                        if (title.isBlank()) {
                            title = result.displayName
                                .substringBeforeLast('.')
                                .replace('_', ' ')
                                .replace('-', ' ')
                                .trim()
                        }
                    }
                    is PdfImportResult.Error -> {
                        formError = result.message
                    }
                }
                isImportingPdf = false
            }
        }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            isImportingCover = true
            formError = null
            scope.launch {
                when (val result = onImportCoverUri(uri)) {
                    is CoverImportResult.Success -> {
                        if (pickedCoverPath != null && pickedCoverPath != result.filePath) {
                            onCleanupUnusedFile(pickedCoverPath)
                        }
                        pickedCoverPath = result.filePath
                        removeCustomCover = false
                        pendingDriveAssets = pendingDriveAssets.copy(localCoverPath = null, coverUploaded = null)
                    }
                    is CoverImportResult.Error -> {
                        formError = result.message
                    }
                }
                isImportingCover = false
            }
        }
    }

    fun handleCancelBack() {
        if (isSubmitting) return
        onCleanupUnusedFile(pickedPdfPath)
        onCleanupUnusedFile(pickedCoverPath)
        onBack()
    }

    val activeCategory = categories.find { it.id == selectedCategoryId } ?: categories.firstOrNull() ?: BookCategory.OTHER_BOOKS
    val effectiveCoverPath = when {
        removeCustomCover -> null
        pickedCoverPath != null -> pickedCoverPath
        else -> existingBook?.coverImagePath
    }
    val effectiveCoverUrl = when {
        removeCustomCover || pickedCoverPath != null -> null
        else -> existingBook?.coverUrl
    }
    val previewBook = Book(
        id = existingBook?.id ?: "preview_draft",
        title = title.ifBlank { "Untitled Book" },
        author = author.ifBlank { "Independent Author" },
        category = activeCategory,
        fileSizeMb = pickedPdfFormattedSize ?: existingBook?.fileSizeMb ?: "0 KB",
        pageCount = pickedPdfPageCount ?: existingBook?.pageCount,
        rating = existingBook?.rating ?: 5.0f,
        reviewCount = existingBook?.reviewCount ?: 0,
        description = description,
        publishedYear = existingBook?.publishedYear ?: 2026,
        addedOrder = existingBook?.addedOrder ?: 0L,
        dateAdded = existingBook?.dateAdded,
        language = language,
        coverStyle = existingBook?.coverStyle ?: CoverArtworkStyle.FOREST_ARCH,
        coverPrimary = activeCategory.secondaryColor,
        coverSecondary = activeCategory.accentColor,
        coverAccent = Gold400,
        coverImagePath = effectiveCoverPath,
        coverUrl = effectiveCoverUrl,
        sampleExcerpt = null,
    )

    val requiresDriveUpload = useCloudMode && (
        pickedPdfPath != null ||
            pickedCoverPath != null ||
            (existingBook != null && existingBook.googleDriveFileId.isNullOrBlank() &&
                existingBook.pdfSourceUrl?.startsWith("file://", true) == true)
        )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Emerald950, Emerald900)))
            .imePadding(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = ::handleCancelBack,
                enabled = !isSubmitting,
                modifier = Modifier.testTag("admin_form_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = Cream50,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = if (isEditMode) "Edit PDF Book" else "Add New Book",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Cream50,
                )
                Text(
                    text = if (useCloudMode) {
                        "Google Drive + Cloud Firestore publishing"
                    } else {
                        "Local persistent storage"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Emerald100.copy(alpha = 0.75f),
                )
            }
        }

        // Cream Form Surface
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Cream100,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 0. Google Drive Connection Banner when Cloud Mode is active & not yet authorized
                if (useCloudMode && !isDriveAuthorized) {
                    Surface(
                        color = Gold100,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Gold400),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AddToDrive, null, tint = Ink900, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google Drive Connection Required",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Ink900,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Grant drive.file access so BookHub can upload PDFs and covers into your \"BookHub Library\" Drive folder.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink700,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    if (onRequestDriveAuthorization != null) {
                                        scope.launch {
                                            val status = onRequestDriveAuthorization(activityContext)
                                            val sender = status.pendingConsentIntentSender
                                            if (sender != null) {
                                                continueSubmitAfterDriveAuth = false
                                                driveConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("admin_form_connect_drive"),
                            ) {
                                Icon(Icons.Rounded.AddToDrive, null, tint = Emerald700, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect Google Drive", color = Emerald700)
                            }
                        }
                    }
                }

                // 1. PDF File Selector Card
                Surface(
                    color = Cream50,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        1.dp,
                        if (pickedPdfPath != null) Emerald700 else Cream200,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Emerald700,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEditMode) "PDF Document File" else "Select PDF File *",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Ink900,
                                )
                            }
                            if (pickedPdfPath != null || existingBook?.googleDriveFileId != null) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "PDF attached",
                                    tint = Emerald700,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val currentPdfSummary = when {
                            pickedPdfPath != null ->
                                "${pickedPdfDisplayName ?: "Selected PDF"} • $pickedPdfFormattedSize • ${pickedPdfPageCount ?: "?"} Pages"
                            existingBook != null && !existingBook.googleDriveFileId.isNullOrBlank() ->
                                "Stored on Google Drive (${existingBook.fileSizeMb} • ${existingBook.pageCount ?: "N/A"} Pages)"
                            existingBook != null && (!existingBook.pdfSourceUrl.isNullOrBlank() || !existingBook.pdfAssetPath.isNullOrBlank()) ->
                                "Current PDF attached (${existingBook.fileSizeMb} • ${existingBook.pageCount ?: "N/A"} Pages)"
                            else -> "No PDF file selected yet. Pick a .pdf file from your device."
                        }

                        Text(
                            text = currentPdfSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pickedPdfPath != null) Emerald700 else Ink500,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            enabled = !isImportingPdf && !isSubmitting,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald700,
                                contentColor = Cream50,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_pick_pdf_button"),
                        ) {
                            if (isImportingPdf) {
                                CircularProgressIndicator(
                                    color = Cream50,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Validating PDF...")
                            } else {
                                Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isEditMode || pickedPdfPath != null) "Replace PDF File" else "Choose PDF from Device")
                            }
                        }
                    }
                }

                // 2. Book Cover Picker Card
                Surface(
                    color = Cream50,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Cream200),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookCoverArtwork(
                            book = previewBook,
                            modifier = Modifier
                                .width(78.dp)
                                .height(110.dp),
                            cornerRadius = 12.dp,
                            compact = true,
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Book Cover Image",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Ink900,
                            )
                            Text(
                                text = if (effectiveCoverPath != null || !effectiveCoverUrl.isNullOrBlank()) {
                                    "Custom cover image attached"
                                } else {
                                    "Optional • Generated emerald/gold cover used if omitted"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink500,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    coverPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                enabled = !isImportingCover && !isSubmitting,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("admin_pick_cover_button"),
                            ) {
                                if (isImportingCover) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.Rounded.Image, null, tint = Emerald700, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (effectiveCoverPath != null || !effectiveCoverUrl.isNullOrBlank()) {
                                            "Replace Cover"
                                        } else {
                                            "Select Cover Image"
                                        },
                                        color = Emerald700,
                                    )
                                }
                            }
                            if (effectiveCoverPath != null || !effectiveCoverUrl.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        onCleanupUnusedFile(pickedCoverPath)
                                        pickedCoverPath = null
                                        pendingDriveAssets = pendingDriveAssets.copy(localCoverPath = null, coverUploaded = null)
                                        removeCustomCover = true
                                    },
                                    enabled = !isSubmitting,
                                ) {
                                    Icon(Icons.Rounded.Close, null, tint = Ink500, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Use Generated Cover", color = Ink500)
                                }
                            }
                        }
                    }
                }

                // 3. Book Metadata Inputs
                Surface(
                    color = Cream50,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Cream200),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Book Information",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink900,
                        )

                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                formError = null
                            },
                            enabled = !isSubmitting,
                            label = { Text("Book Title *") },
                            placeholder = { Text("Enter complete book title") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = formFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_book_title_input"),
                        )

                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            enabled = !isSubmitting,
                            label = { Text("Author Name (Optional)") },
                            placeholder = { Text("e.g., Dr. Elena Vance") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = formFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_book_author_input"),
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            enabled = !isSubmitting,
                            label = { Text("Description") },
                            placeholder = { Text("Summarize what readers will learn or explore...") },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(14.dp),
                            colors = formFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_book_description_input"),
                        )

                        OutlinedTextField(
                            value = language,
                            onValueChange = { language = it },
                            enabled = !isSubmitting,
                            label = { Text("Language") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = formFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Auto-calculated PDF Metadata Preview Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                color = Cream100,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("PDF Size", style = MaterialTheme.typography.labelSmall, color = Ink500)
                                    Text(
                                        text = pickedPdfFormattedSize ?: existingBook?.fileSizeMb ?: "Auto",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Ink900,
                                    )
                                }
                            }
                            Surface(
                                color = Cream100,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Page Count", style = MaterialTheme.typography.labelSmall, color = Ink500)
                                    Text(
                                        text = (pickedPdfPageCount ?: existingBook?.pageCount)?.let { "$it Pages" } ?: "Auto",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Ink900,
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Category Selection (Required)
                Surface(
                    color = Cream50,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Cream200),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Select Category *",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink900,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            categories.forEach { cat ->
                                val selected = cat.id == selectedCategoryId
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (!isSubmitting) {
                                            selectedCategoryId = cat.id
                                            formError = null
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = cat.displayName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Emerald700,
                                        selectedLabelColor = Cream50,
                                        containerColor = Cream100,
                                        labelColor = Ink900,
                                    ),
                                )
                            }
                        }
                    }
                }

                // 5. Catalog Placement & Visibility Flags
                Surface(
                    color = Cream50,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Cream200),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Visibility & Placement",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink900,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PlacementSwitchRow(
                            title = "Published in Library",
                            subtitle = if (isPublished) {
                                "Visible to all readers & searchable"
                            } else {
                                "Saved as Draft (visible to Administrators only)"
                            },
                            checked = isPublished,
                            onCheckedChange = { if (!isSubmitting) isPublished = it },
                        )
                        PlacementSwitchRow(
                            title = "Recently Added",
                            subtitle = "Show in Home Recently Added shelf",
                            checked = isRecent,
                            onCheckedChange = { if (!isSubmitting) isRecent = it },
                        )
                        PlacementSwitchRow(
                            title = "Featured Spotlight",
                            subtitle = "Include in Featured Books listing",
                            checked = isFeatured,
                            onCheckedChange = { if (!isSubmitting) isFeatured = it },
                        )
                        PlacementSwitchRow(
                            title = "Popular Collection",
                            subtitle = "Highlight in Popular Books shelf",
                            checked = isPopular,
                            onCheckedChange = { if (!isSubmitting) isPopular = it },
                        )
                    }
                }

                // Transparent Google Drive Sharing Disclosure
                if (useCloudMode) {
                    Surface(
                        color = Emerald100.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Rounded.Info, null, tint = Emerald700, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPublished) {
                                    "Published Drive files are shared read-only via link so all BookHub readers can stream and cache PDFs without signing in to Google Drive."
                                } else {
                                    "Draft books keep Google Drive link sharing restricted until you publish them."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink900,
                            )
                        }
                    }
                }

                // Live Upload / Cloud Save Progress Card
                if (isSubmitting || cloudUploadStage != null) {
                    Surface(
                        color = Cream50,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Emerald700),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CloudUpload, null, tint = Emerald700, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cloudUploadStage ?: "Saving book...",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ink900,
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val pct = cloudUploadProgress
                            if (pct != null) {
                                LinearProgressIndicator(
                                    progress = { pct.coerceIn(0f, 1f) },
                                    color = Emerald700,
                                    trackColor = Cream200,
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = Emerald700,
                                    trackColor = Cream200,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                            }
                        }
                    }
                }

                if (!formError.isNullOrBlank()) {
                    Surface(
                        color = AdminCoral.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AdminCoral.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = formError!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = AdminCoral,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            if (requiresDriveUpload && !isDriveAuthorized && onRequestDriveAuthorization != null) {
                                val authStatus = onRequestDriveAuthorization(activityContext)
                                val sender = authStatus.pendingConsentIntentSender
                                if (sender != null) {
                                    continueSubmitAfterDriveAuth = true
                                    driveConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                    return@launch
                                } else if (!authStatus.isAuthorized) {
                                    formError = authStatus.errorMessage
                                        ?: "Please authorize Google Drive before publishing."
                                    return@launch
                                }
                            }
                            performSubmit()
                        }
                    },
                    enabled = !isImportingPdf && !isImportingCover && !isSubmitting,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald700,
                        contentColor = Cream50,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("admin_publish_book_button"),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = Cream50,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (useCloudMode) "Uploading & Syncing..." else "Saving...",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    } else {
                        Text(
                            text = when {
                                isEditMode -> "Save Book Changes"
                                !isPublished -> "Save Book as Draft"
                                useCloudMode -> "Upload to Drive & Publish"
                                else -> "Publish Book to Library"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private tailrec fun Context.findFormActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findFormActivity()
    else -> null
}

@Composable
private fun PlacementSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink900)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Ink500)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Emerald700),
        )
    }
}

@Composable
private fun formFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Cream100,
    unfocusedContainerColor = Cream100,
    focusedBorderColor = Emerald700,
    unfocusedBorderColor = Cream200,
    focusedTextColor = Ink900,
    unfocusedTextColor = Ink900,
    focusedLabelColor = Emerald700,
    unfocusedLabelColor = Ink700,
    cursorColor = Emerald700,
)
