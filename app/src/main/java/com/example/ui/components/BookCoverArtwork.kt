package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.EmojiObjects
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Book
import com.example.model.BookCategory
import com.example.model.CoverArtworkStyle
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold400
import com.example.ui.theme.PlayfairDisplayFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private object LocalCoverBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun load(
        context: Context,
        localPath: String?,
        remoteUrl: String?,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!localPath.isNullOrBlank()) {
            val cleanLocal = localPath.removePrefix("file://")
            decodeLocalFile(cleanLocal)?.let { return@withContext it }
        }
        val cleanRemote = remoteUrl?.trim().takeIf {
            !it.isNullOrBlank() && (it.startsWith("https://", true) || it.startsWith("http://", true))
        } ?: return@withContext null

        cache.get(cleanRemote)?.let { if (!it.isRecycled) return@withContext it.asImageBitmap() }

        val coversCacheDir = File(context.cacheDir, "bookhub_covers").apply { mkdirs() }
        val fileHash = "${cleanRemote.hashCode().toUInt().toString(16)}_${cleanRemote.length}"
        val cachedFile = File(coversCacheDir, "cover_$fileHash.img")
        if (cachedFile.exists() && cachedFile.length() > 128L) {
            decodeLocalFile(cachedFile.absolutePath, cacheKey = cleanRemote)?.let { return@withContext it }
            cachedFile.delete()
        }

        val tempFile = File(coversCacheDir, "cover_$fileHash.tmp")
        try {
            val req = Request.Builder().url(cleanRemote).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val contentType = resp.header("Content-Type").orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) {
                    return@withContext null
                }
                val stream = resp.body?.byteStream() ?: return@withContext null
                stream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val decoded = decodeLocalFile(tempFile.absolutePath, cacheKey = cleanRemote)
            if (decoded != null) {
                if (cachedFile.exists()) cachedFile.delete()
                if (!tempFile.renameTo(cachedFile)) {
                    tempFile.copyTo(cachedFile, overwrite = true)
                    tempFile.delete()
                }
                trimCoverDiskCache(coversCacheDir)
                decoded
            } else {
                tempFile.delete()
                null
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (_: Exception) {
            tempFile.delete()
            null
        }
    }

    private fun trimCoverDiskCache(cacheDir: File, maxFiles: Int = 60) {
        val files = cacheDir.listFiles()?.filter { it.isFile && it.name.endsWith(".img") } ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }
            .take(files.size - maxFiles)
            .forEach { it.delete() }
    }

    private fun decodeLocalFile(
        path: String,
        cacheKey: String = path,
    ): ImageBitmap? {
        cache.get(cacheKey)?.let { if (!it.isRecycled) return it.asImageBitmap() }
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var sample = 1
        while (options.outWidth / sample > 640 || options.outHeight / sample > 840) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
        cache.put(cacheKey, bmp)
        return bmp.asImageBitmap()
    }
}

@Composable
fun BookCoverArtwork(
    book: Book,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    showPdfBadge: Boolean = true,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val appContext = LocalContext.current.applicationContext
    val customCoverPath = book.coverImagePath
    val remoteCoverUrl = book.coverUrl
    val customCoverBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        customCoverPath,
        remoteCoverUrl,
    ) {
        value = if (!customCoverPath.isNullOrBlank() || !remoteCoverUrl.isNullOrBlank()) {
            LocalCoverBitmapCache.load(appContext, customCoverPath, remoteCoverUrl)
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(book.coverPrimary, book.coverSecondary),
                    start = Offset(0f, 0f),
                    end = Offset(400f, 700f),
                ),
            ),
    ) {
        if (customCoverBitmap != null) {
            Image(
                bitmap = customCoverBitmap!!,
                contentDescription = "${book.title} cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.52f),
                            ),
                        ),
                    ),
            )
        } else {
            // Custom Canvas illustration per CoverArtworkStyle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Spine shadow on the left edge for realistic physical book depth
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
                    startX = 0f,
                    endX = w * 0.12f,
                ),
                size = Size(w * 0.12f, h),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.08f, h),
                strokeWidth = 1.5f,
            )

            // Subtle luminous aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(book.coverAccent.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(w * 0.54f, h * 0.38f),
                    radius = w * 0.55f,
                ),
                center = Offset(w * 0.54f, h * 0.38f),
                radius = w * 0.55f,
            )

            when (book.coverStyle) {
                CoverArtworkStyle.MOUNTAIN_HORIZON -> {
                    // Glowing moon/sun
                    drawCircle(
                        color = book.coverAccent.copy(alpha = 0.85f),
                        radius = w * 0.11f,
                        center = Offset(w * 0.55f, h * 0.48f),
                    )
                    // Back mountain ridge
                    val backRidge = Path().apply {
                        moveTo(0f, h * 0.78f)
                        lineTo(w * 0.28f, h * 0.56f)
                        lineTo(w * 0.52f, h * 0.68f)
                        lineTo(w * 0.78f, h * 0.50f)
                        lineTo(w, h * 0.70f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(backRidge, color = Color.Black.copy(alpha = 0.32f))
                    // Front mountain silhouette
                    val frontRidge = Path().apply {
                        moveTo(0f, h * 0.88f)
                        lineTo(w * 0.35f, h * 0.68f)
                        lineTo(w * 0.68f, h * 0.80f)
                        lineTo(w, h * 0.65f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(frontRidge, color = Emerald950.copy(alpha = 0.65f))
                }

                CoverArtworkStyle.COSMIC_ORBIT -> {
                    val cx = w * 0.54f
                    val cy = h * 0.52f
                    drawCircle(
                        color = book.coverAccent,
                        radius = w * 0.08f,
                        center = Offset(cx, cy),
                    )
                    drawOval(
                        color = book.coverAccent.copy(alpha = 0.55f),
                        topLeft = Offset(cx - w * 0.32f, cy - h * 0.09f),
                        size = Size(w * 0.64f, h * 0.18f),
                        style = Stroke(width = 2.5f),
                    )
                    drawOval(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(cx - w * 0.16f, cy - h * 0.20f),
                        size = Size(w * 0.32f, h * 0.40f),
                        style = Stroke(width = 2f),
                    )
                    drawCircle(Color.White.copy(alpha = 0.7f), radius = 3f, center = Offset(w * 0.25f, h * 0.28f))
                    drawCircle(book.coverAccent, radius = 4f, center = Offset(w * 0.82f, h * 0.34f))
                    drawCircle(Color.White.copy(alpha = 0.5f), radius = 2.5f, center = Offset(w * 0.76f, h * 0.76f))
                }

                CoverArtworkStyle.FOREST_ARCH -> {
                    // Warm golden arch doorway
                    drawArc(
                        color = book.coverAccent.copy(alpha = 0.45f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.22f, h * 0.34f),
                        size = Size(w * 0.58f, h * 0.42f),
                        style = Stroke(width = 3f),
                    )
                    drawCircle(
                        color = book.coverAccent.copy(alpha = 0.75f),
                        radius = w * 0.09f,
                        center = Offset(w * 0.51f, h * 0.58f),
                    )
                    val hills = Path().apply {
                        moveTo(0f, h * 0.78f)
                        quadraticTo(w * 0.5f, h * 0.66f, w, h * 0.78f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(hills, color = Color.Black.copy(alpha = 0.4f))
                }

                CoverArtworkStyle.CITADEL_NIGHT -> {
                    // Stars and crescent accent + architectural dome silhouette
                    drawCircle(
                        color = book.coverAccent.copy(alpha = 0.85f),
                        radius = w * 0.07f,
                        center = Offset(w * 0.76f, h * 0.26f),
                    )
                    val skyline = Path().apply {
                        moveTo(0f, h * 0.82f)
                        lineTo(w * 0.18f, h * 0.82f)
                        lineTo(w * 0.22f, h * 0.62f)
                        lineTo(w * 0.28f, h * 0.54f)
                        lineTo(w * 0.34f, h * 0.62f)
                        lineTo(w * 0.45f, h * 0.62f)
                        lineTo(w * 0.52f, h * 0.48f)
                        lineTo(w * 0.59f, h * 0.62f)
                        lineTo(w * 0.75f, h * 0.62f)
                        lineTo(w * 0.78f, h * 0.72f)
                        lineTo(w, h * 0.72f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(skyline, color = Color.Black.copy(alpha = 0.45f))
                }

                CoverArtworkStyle.SUNRISE_PEAK -> {
                    val cx = w * 0.52f
                    val cy = h * 0.60f
                    for (angle in -150.. -30 step 24) {
                        val rad = Math.toRadians(angle.toDouble())
                        val endX = cx + (w * 0.42f * kotlin.math.cos(rad)).toFloat()
                        val endY = cy + (w * 0.42f * kotlin.math.sin(rad)).toFloat()
                        drawLine(
                            color = book.coverAccent.copy(alpha = 0.4f),
                            start = Offset(cx, cy),
                            end = Offset(endX, endY),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round,
                        )
                    }
                    drawCircle(
                        color = book.coverAccent.copy(alpha = 0.88f),
                        radius = w * 0.13f,
                        center = Offset(cx, cy),
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.35f),
                        topLeft = Offset(0f, h * 0.72f),
                        size = Size(w, h * 0.28f),
                    )
                }

                CoverArtworkStyle.CIRCUIT_MATRIX -> {
                    val nodeColor = book.coverAccent.copy(alpha = 0.65f)
                    drawLine(nodeColor, Offset(w * 0.2f, h * 0.42f), Offset(w * 0.5f, h * 0.42f), 2f)
                    drawLine(nodeColor, Offset(w * 0.5f, h * 0.42f), Offset(w * 0.65f, h * 0.56f), 2f)
                    drawLine(nodeColor, Offset(w * 0.65f, h * 0.56f), Offset(w * 0.88f, h * 0.56f), 2f)
                    drawLine(nodeColor, Offset(w * 0.32f, h * 0.68f), Offset(w * 0.56f, h * 0.68f), 2f)
                    drawLine(nodeColor, Offset(w * 0.56f, h * 0.68f), Offset(w * 0.65f, h * 0.56f), 2f)
                    listOf(
                        Offset(w * 0.2f, h * 0.42f),
                        Offset(w * 0.5f, h * 0.42f),
                        Offset(w * 0.65f, h * 0.56f),
                        Offset(w * 0.88f, h * 0.56f),
                        Offset(w * 0.32f, h * 0.68f),
                    ).forEach { center ->
                        drawCircle(book.coverAccent, radius = 4.5f, center = center)
                    }
                }

                CoverArtworkStyle.GEOMETRIC_LANTERN -> {
                    val cx = w * 0.54f
                    val cy = h * 0.54f
                    val r = w * 0.22f
                    drawRect(
                        color = book.coverAccent.copy(alpha = 0.5f),
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = 2.5f),
                    )
                    val diamond = Path().apply {
                        moveTo(cx, cy - r * 1.35f)
                        lineTo(cx + r * 1.35f, cy)
                        lineTo(cx, cy + r * 1.35f)
                        lineTo(cx - r * 1.35f, cy)
                        close()
                    }
                    drawPath(diamond, color = book.coverAccent.copy(alpha = 0.7f), style = Stroke(width = 2.5f))
                    drawCircle(book.coverAccent.copy(alpha = 0.85f), radius = r * 0.32f, center = Offset(cx, cy))
                }

                CoverArtworkStyle.CLASSIC_FOLIO -> {
                    drawRect(
                        color = book.coverAccent.copy(alpha = 0.45f),
                        topLeft = Offset(w * 0.14f, h * 0.10f),
                        size = Size(w * 0.78f, h * 0.80f),
                        style = Stroke(width = 2f),
                    )
                    drawCircle(
                        color = book.coverAccent.copy(alpha = 0.35f),
                        radius = w * 0.18f,
                        center = Offset(w * 0.53f, h * 0.55f),
                        style = Stroke(width = 2f),
                    )
                }

                null -> {
                    // Graceful missing cover placeholder pattern
                    drawRect(
                        color = book.coverAccent.copy(alpha = 0.22f),
                        topLeft = Offset(w * 0.16f, h * 0.14f),
                        size = Size(w * 0.72f, h * 0.72f),
                        style = Stroke(width = 1.5f),
                    )
                    drawCircle(
                        color = book.coverAccent.copy(alpha = 0.14f),
                        radius = w * 0.16f,
                        center = Offset(w * 0.52f, h * 0.42f),
                    )
                }
            }
        }
        }

        // Gold hairline border inside the cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 5.dp else 8.dp)
                .border(
                    width = 0.75.dp,
                    color = book.coverAccent.copy(alpha = 0.45f),
                    shape = RoundedCornerShape((cornerRadius.value - 4f).coerceAtLeast(4f).dp),
                ),
        )

        // Cover typography & PDF pill
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (compact) 10.dp else 14.dp,
                    end = if (compact) 8.dp else 12.dp,
                    top = if (compact) 8.dp else 12.dp,
                    bottom = if (compact) 8.dp else 12.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (showPdfBadge) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "PDF",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (compact) 8.sp else 9.sp,
                                letterSpacing = 0.8.sp,
                            ),
                            color = Gold400,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (book.coverStyle == null && customCoverBitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint = book.coverAccent.copy(alpha = 0.72f),
                        modifier = Modifier.size(if (compact) 24.dp else 38.dp),
                    )
                }
            }

            if (customCoverBitmap == null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = book.title,
                        fontFamily = PlayfairDisplayFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 11.sp else 15.sp,
                        lineHeight = if (compact) 13.sp else 18.sp,
                        color = Cream50,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!compact) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = book.author.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                letterSpacing = 1.1.sp,
                            ),
                            color = book.coverAccent.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

fun categoryIcon(category: BookCategory): ImageVector = when (category.iconKey) {
    BookCategory.ICON_STORIES -> Icons.Rounded.AutoStories
    BookCategory.ICON_SCIENCE -> Icons.Rounded.Science
    BookCategory.ICON_FICTION -> Icons.Rounded.Explore
    BookCategory.ICON_HISTORY -> Icons.Rounded.HistoryEdu
    BookCategory.ICON_MOTIVATION -> Icons.Rounded.EmojiObjects
    BookCategory.ICON_TECH -> Icons.Rounded.Code
    BookCategory.ICON_ISLAMIC -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.MenuBook
}

@Composable
fun CategoryArtworkBadge(
    category: BookCategory,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(category.accentColor, category.secondaryColor),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = this.size.minDimension * 0.42f,
                center = Offset(this.size.width * 0.8f, this.size.height * 0.2f),
            )
        }
        Icon(
            imageVector = categoryIcon(category),
            contentDescription = null,
            tint = Cream50,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}
