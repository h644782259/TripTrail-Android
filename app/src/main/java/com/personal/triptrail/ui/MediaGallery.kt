package com.personal.triptrail.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.triptrail.data.MediaKind
import com.personal.triptrail.data.MediaReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Composable
fun MediaGallery(media: List<MediaReference>, onDelete: ((String) -> Unit)? = null, horizontal: Boolean = false) {
    val sorted = remember(media) { media.sortedBy { it.sortOrder } }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val tile: @Composable (MediaReference, Modifier) -> Unit = { reference, modifier ->
        Box(modifier) {
            MediaThumbnail(reference, Modifier.fillMaxSize().clickable(onClickLabel = "查看大图") { selectedId = reference.id }, cornerRadius = 10.dp)
            if (onDelete != null) IconButton(
                onClick = { onDelete(reference.id) },
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(Color.Black.copy(alpha = .5f), CircleShape),
            ) { Icon(Icons.Default.Close, "移除", tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
    }
    if (horizontal) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sorted.forEach { tile(it, Modifier.size(86.dp)) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sorted.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { tile(it, Modifier.weight(1f).aspectRatio(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                }
            }
        }
    }
    selectedId?.let { id ->
        val index = sorted.indexOfFirst { it.id == id }
        if (index >= 0) MediaViewer(sorted, index) { selectedId = null }
    }
}

@Composable
private fun MediaViewer(media: List<MediaReference>, initialPage: Int, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { media.size })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { media[it].id }) { page ->
                val item = media[page]
                if (item.kind == MediaKind.VIDEO) {
                    if (pager.currentPage == page) key(item.id) {
                        AndroidView(
                            factory = { context -> VideoView(context).apply {
                                setVideoURI(Uri.parse(item.localUri))
                                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                                setOnPreparedListener { start() }
                            } },
                            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 56.dp),
                            onRelease = { it.stopPlayback() },
                        )
                    }
                } else {
                    val bitmap by rememberMediaBitmap(item.localUri, 2048)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (bitmap != null) Image(bitmap!!.asImageBitmap(), item.caption.ifBlank { "第 ${page + 1} 张图片" }, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Icon(Icons.Default.BrokenImage, "图片不可用", tint = Color.White)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${pager.currentPage + 1} / ${media.size}", Modifier.weight(1f), color = Color.White)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭大图", tint = Color.White) }
            }
            Text("左右滑动切换", Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp), color = Color.White)
        }
    }
}

private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}
private val imageDecodeSlots = Semaphore(2)

/** Decode off the UI thread and bound bitmap size for camera-resolution originals. */
@Composable
internal fun rememberMediaBitmap(uri: String, maxDimension: Int): State<Bitmap?> {
    val context = LocalContext.current
    val cacheKey = "$uri:$maxDimension"
    return produceState<Bitmap?>(thumbnailCache.get(cacheKey), uri, maxDimension) {
        value = withContext(Dispatchers.IO) {
            imageDecodeSlots.withPermit {
            thumbnailCache.get(cacheKey)?.let { return@withPermit it }
            runCatching {
                val source = Uri.parse(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > maxDimension) options.inSampleSize *= 2
                context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?.also { if (maxDimension <= 512) thumbnailCache.put(cacheKey, it) }
            }.getOrNull()
            }
        }
    }
}
