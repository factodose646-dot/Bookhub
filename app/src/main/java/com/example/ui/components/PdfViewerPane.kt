package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.PdfFitMode
import com.example.pdf.PdfDocumentHandle
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold400
import com.example.ui.theme.Ink500
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val NightModePdfColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.85f, 0f, 0f, 0f, 235f,
            0f, -0.82f, 0f, 0f, 242f,
            0f, 0f, -0.84f, 0f, 241f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

@Composable
fun PdfViewerPane(
    document: PdfDocumentHandle,
    initialPageOneBased: Int,
    pageJumpCommand: Pair<Int, Int>?,
    zoomScale: Float,
    fitMode: PdfFitMode,
    nightMode: Boolean,
    onCurrentPageChanged: (Int) -> Unit,
    onToggleFullscreenOrDoubleTapZoom: () -> Unit,
    onDoubleTapZoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialIndex = (initialPageOneBased - 1).coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
    val listState: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    LaunchedEffect(pageJumpCommand) {
        if (pageJumpCommand != null) {
            val targetIdx = (pageJumpCommand.first - 1).coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
            listState.animateScrollToItem(targetIdx)
        }
    }

    LaunchedEffect(listState, document.pageCount) {
        snapshotFlow { listState.layoutInfo }
            .map { info ->
                val visibleItems = info.visibleItemsInfo
                if (visibleItems.isEmpty()) {
                    1
                } else {
                    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    val centerItem = visibleItems.minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2
                        kotlin.math.abs(itemCenter - viewportCenter)
                    } ?: visibleItems.first()
                    (centerItem.index + 1).coerceIn(1, document.pageCount)
                }
            }
            .distinctUntilChanged()
            .collect { pageOneBased ->
                onCurrentPageChanged(pageOneBased)
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(if (nightMode) Emerald950 else Cream100)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleFullscreenOrDoubleTapZoom() },
                    onDoubleTap = { onDoubleTapZoom() },
                )
            }
            .testTag("pdf_viewer_pane"),
    ) {
        val density = LocalDensity.current
        val availableWidthDp = (maxWidth - 24.dp).coerceAtLeast(220.dp)
        val availableHeightDp = (maxHeight - 28.dp).coerceAtLeast(280.dp)
        val horizontalScrollState = rememberScrollState()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .let { base ->
                    if (zoomScale > 1.02f) {
                        base.horizontalScroll(horizontalScrollState)
                    } else {
                        base
                    }
                },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(
                count = document.pageCount,
                key = { index -> "pdf_page_$index" },
            ) { pageIndex ->
                PdfRenderedPageCard(
                    document = document,
                    pageIndex = pageIndex,
                    totalPages = document.pageCount,
                    availableWidthDp = availableWidthDp,
                    availableHeightDp = availableHeightDp,
                    zoomScale = zoomScale,
                    fitMode = fitMode,
                    nightMode = nightMode,
                    densityScale = density.density,
                )
            }
        }
    }
}

@Composable
private fun PdfRenderedPageCard(
    document: PdfDocumentHandle,
    pageIndex: Int,
    totalPages: Int,
    availableWidthDp: Dp,
    availableHeightDp: Dp,
    zoomScale: Float,
    fitMode: PdfFitMode,
    nightMode: Boolean,
    densityScale: Float,
) {
    var aspectRatio by remember(document, pageIndex) { mutableFloatStateOf(612f / 792f) }
    var renderedBitmap by remember(document, pageIndex) { mutableStateOf<Bitmap?>(null) }

    val baseWidthDp: Dp
    val baseHeightDp: Dp
    if (fitMode == PdfFitMode.FIT_PAGE) {
        val maxFitWidth = availableWidthDp.value
        val maxFitHeight = availableHeightDp.value
        val candidateHeightFromWidth = maxFitWidth / aspectRatio
        if (candidateHeightFromWidth <= maxFitHeight) {
            baseWidthDp = maxFitWidth.dp
            baseHeightDp = candidateHeightFromWidth.dp
        } else {
            baseHeightDp = maxFitHeight.dp
            baseWidthDp = (maxFitHeight * aspectRatio).dp
        }
    } else {
        baseWidthDp = availableWidthDp
        baseHeightDp = (availableWidthDp.value / aspectRatio).dp
    }

    val finalWidthDp = baseWidthDp * zoomScale
    val finalHeightDp = baseHeightDp * zoomScale

    // Render crisp bitmap sized for screen density & zoom (clamped inside PdfDocumentHandle)
    val renderTargetWidthPx = remember(finalWidthDp, densityScale) {
        val raw = (finalWidthDp.value * densityScale * 1.15f).roundToInt()
        // Quantize to 120px buckets to keep LRU cache hit rate high during small adjustments
        ((raw + 119) / 120) * 120
    }

    LaunchedEffect(document, pageIndex, renderTargetWidthPx) {
        aspectRatio = document.getPageAspectRatio(pageIndex)
        renderedBitmap = document.renderPage(pageIndex, renderTargetWidthPx)
    }

    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .width(finalWidthDp)
            .height(finalHeightDp)
            .shadow(elevation = if (nightMode) 2.dp else 6.dp, shape = shape)
            .clip(shape)
            .background(if (nightMode) Color(0xFF12211B) else Color.White)
            .testTag("pdf_page_${pageIndex + 1}"),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = renderedBitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1} of $totalPages",
                contentScale = ContentScale.FillBounds,
                colorFilter = if (nightMode) NightModePdfColorFilter else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = if (nightMode) Gold400 else Emerald700,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Rendering page ${pageIndex + 1}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (nightMode) Cream50 else Ink500,
                )
            }
        }

        Surface(
            color = if (nightMode) Emerald900.copy(alpha = 0.85f) else Emerald950.copy(alpha = 0.72f),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        ) {
            Text(
                text = "${pageIndex + 1} / $totalPages",
                style = MaterialTheme.typography.labelSmall,
                color = Cream50,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
