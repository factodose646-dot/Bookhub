package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BookCategory
import com.example.ui.BookHubUiState
import com.example.ui.components.CategoryArtworkBadge
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

private data class CategoryPaletteOption(
    val accent: Color,
    val secondary: Color,
)

private val categoryPalettes = listOf(
    CategoryPaletteOption(Color(0xFF176B4C), Color(0xFF092E20)),
    CategoryPaletteOption(Color(0xFF1B76A8), Color(0xFF0A324D)),
    CategoryPaletteOption(Color(0xFFE07A2E), Color(0xFF6B2D10)),
    CategoryPaletteOption(Color(0xFF8C5327), Color(0xFF3E210D)),
    CategoryPaletteOption(Color(0xFFB8437A), Color(0xFF4F1732)),
    CategoryPaletteOption(Color(0xFF4B56B2), Color(0xFF1C2252)),
    CategoryPaletteOption(Color(0xFF157A74), Color(0xFF093835)),
    CategoryPaletteOption(Color(0xFFD1961F), Color(0xFF523808)),
)

private val categoryIconOptions = listOf(
    BookCategory.ICON_OTHER to "General",
    BookCategory.ICON_STORIES to "Stories",
    BookCategory.ICON_SCIENCE to "Science",
    BookCategory.ICON_FICTION to "Adventure",
    BookCategory.ICON_HISTORY to "History",
    BookCategory.ICON_MOTIVATION to "Insights",
    BookCategory.ICON_TECH to "Tech",
    BookCategory.ICON_ISLAMIC to "Heritage",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminCategoriesScreen(
    uiState: BookHubUiState,
    onBack: () -> Unit,
    onAddCategory: suspend (name: String, description: String, accent: Color, secondary: Color, iconKey: String) -> String?,
    onUpdateCategory: suspend (id: String, name: String, description: String, accent: Color, secondary: Color, iconKey: String) -> String?,
    onDeleteCategory: suspend (id: String, reassignTargetId: String?) -> String?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<BookCategory?>(null) }
    var categoryPendingDelete by remember { mutableStateOf<BookCategory?>(null) }

    var draftName by remember { mutableStateOf("") }
    var draftDescription by remember { mutableStateOf("") }
    var selectedPaletteIndex by remember { mutableIntStateOf(0) }
    var selectedIconKey by remember { mutableStateOf(BookCategory.ICON_OTHER) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var isSavingCategory by remember { mutableStateOf(false) }

    fun openAddDialog() {
        editingCategory = null
        draftName = ""
        draftDescription = ""
        selectedPaletteIndex = 0
        selectedIconKey = BookCategory.ICON_OTHER
        dialogError = null
        showAddEditDialog = true
    }

    fun openEditDialog(cat: BookCategory) {
        editingCategory = cat
        draftName = cat.displayName
        draftDescription = cat.description
        val idx = categoryPalettes.indexOfFirst { it.accent == cat.accentColor }
        selectedPaletteIndex = if (idx >= 0) idx else 0
        selectedIconKey = cat.iconKey
        dialogError = null
        showAddEditDialog = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Emerald950,
        floatingActionButton = {
            FloatingActionButton(
                onClick = ::openAddDialog,
                containerColor = Gold400,
                contentColor = Emerald950,
                modifier = Modifier.testTag("admin_categories_fab_add"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add category")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Brush.verticalGradient(listOf(Emerald950, Emerald900))),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("admin_categories_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Navigate back",
                        tint = Cream50,
                    )
                }
                Column {
                    Text(
                        text = "Categories Management",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Cream50,
                    )
                    Text(
                        text = "${uiState.categories.size} active categories",
                        style = MaterialTheme.typography.bodySmall,
                        color = Emerald100.copy(alpha = 0.78f),
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Cream100,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.categories, key = { it.id }) { category ->
                        val bookCount = uiState.books.count { it.category.id == category.id }
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Cream50),
                            border = BorderStroke(1.dp, Cream200),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryArtworkBadge(
                                    category = category,
                                    size = 50.dp,
                                    cornerRadius = 12.dp,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category.displayName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Ink900,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (bookCount == 1) "1 book" else "$bookCount books",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Emerald700,
                                    )
                                    if (category.description.isNotBlank()) {
                                        Text(
                                            text = category.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Ink500,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(onClick = { openEditDialog(category) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit ${category.displayName}",
                                        tint = Ink700,
                                    )
                                }
                                IconButton(
                                    onClick = { categoryPendingDelete = category },
                                    enabled = uiState.categories.size > 1,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete ${category.displayName}",
                                        tint = if (uiState.categories.size > 1) AdminCoral else Ink500.copy(alpha = 0.4f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Category Dialog
    if (showAddEditDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingCategory) showAddEditDialog = false },
            containerColor = Cream50,
            title = {
                Text(
                    text = if (editingCategory != null) "Edit Category" else "New Category",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink900,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = {
                            draftName = it
                            dialogError = null
                        },
                        enabled = !isSavingCategory,
                        label = { Text("Category Name *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_category_name_input"),
                    )
                    OutlinedTextField(
                        value = draftDescription,
                        onValueChange = { draftDescription = it },
                        enabled = !isSavingCategory,
                        label = { Text("Description") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Badge Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ink700,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categoryPalettes.forEachIndexed { idx, palette ->
                            val selected = idx == selectedPaletteIndex
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                                    .clickable(enabled = !isSavingCategory) { selectedPaletteIndex = idx },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Cream50,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "Icon Style",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ink700,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        categoryIconOptions.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedIconKey == key,
                                enabled = !isSavingCategory,
                                onClick = { selectedIconKey = key },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Emerald700,
                                    selectedLabelColor = Cream50,
                                ),
                            )
                        }
                    }
                    if (!dialogError.isNullOrBlank()) {
                        Text(
                            text = dialogError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = AdminCoral,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSavingCategory,
                    onClick = {
                        val palette = categoryPalettes[selectedPaletteIndex]
                        val editing = editingCategory
                        isSavingCategory = true
                        dialogError = null
                        scope.launch {
                            try {
                                val err = if (editing != null) {
                                    onUpdateCategory(
                                        editing.id,
                                        draftName,
                                        draftDescription,
                                        palette.accent,
                                        palette.secondary,
                                        selectedIconKey,
                                    )
                                } else {
                                    onAddCategory(
                                        draftName,
                                        draftDescription,
                                        palette.accent,
                                        palette.secondary,
                                        selectedIconKey,
                                    )
                                }
                                if (err == null) {
                                    showAddEditDialog = false
                                } else {
                                    dialogError = err
                                }
                            } finally {
                                isSavingCategory = false
                            }
                        }
                    },
                ) {
                    if (isSavingCategory) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Emerald700,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = when {
                            isSavingCategory -> "Saving..."
                            editingCategory != null -> "Save"
                            else -> "Create"
                        },
                        color = Emerald700,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSavingCategory,
                    onClick = { showAddEditDialog = false },
                ) {
                    Text("Cancel", color = Ink500)
                }
            },
        )
    }

    // Delete Category Confirmation + Safe Reassignment Dialog
    val targetDelete = categoryPendingDelete
    if (targetDelete != null) {
        val booksInTarget = uiState.books.count { it.category.id == targetDelete.id }
        val otherCategories = uiState.categories.filter { it.id != targetDelete.id }
        var reassignTargetId by remember(targetDelete.id) {
            mutableStateOf(otherCategories.firstOrNull()?.id)
        }
        var deleteError by remember(targetDelete.id) { mutableStateOf<String?>(null) }
        var isDeletingCategory by remember(targetDelete.id) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isDeletingCategory) categoryPendingDelete = null },
            containerColor = Cream50,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = AdminCoral,
                )
            },
            title = {
                Text(
                    text = "Delete \"${targetDelete.displayName}\"?",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink900,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (booksInTarget > 0) {
                        Surface(
                            color = Gold100,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "This category currently contains $booksInTarget book(s). Select another category below to safely reassign them before deletion.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink900,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Text(
                            text = "Move $booksInTarget book(s) to:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Ink700,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            otherCategories.forEach { option ->
                                FilterChip(
                                    selected = reassignTargetId == option.id,
                                    enabled = !isDeletingCategory,
                                    onClick = {
                                        reassignTargetId = option.id
                                        deleteError = null
                                    },
                                    label = { Text(option.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Emerald700,
                                        selectedLabelColor = Cream50,
                                    ),
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "This category has 0 books and can be safely removed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink700,
                        )
                    }
                    if (!deleteError.isNullOrBlank()) {
                        Text(
                            text = deleteError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = AdminCoral,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingCategory,
                    onClick = {
                        isDeletingCategory = true
                        deleteError = null
                        scope.launch {
                            try {
                                val err = onDeleteCategory(
                                    targetDelete.id,
                                    if (booksInTarget > 0) reassignTargetId else null,
                                )
                                if (err == null) {
                                    categoryPendingDelete = null
                                } else {
                                    deleteError = err
                                }
                            } finally {
                                isDeletingCategory = false
                            }
                        }
                    },
                ) {
                    if (isDeletingCategory) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AdminCoral,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = when {
                            isDeletingCategory -> "Deleting..."
                            booksInTarget > 0 -> "Move Books & Delete"
                            else -> "Delete Category"
                        },
                        color = AdminCoral,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingCategory,
                    onClick = { categoryPendingDelete = null },
                ) {
                    Text("Cancel", color = Ink500)
                }
            },
        )
    }
}
