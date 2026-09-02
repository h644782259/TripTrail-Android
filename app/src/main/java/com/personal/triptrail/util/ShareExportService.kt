package com.personal.triptrail.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.personal.triptrail.data.MediaKind
import com.personal.triptrail.data.MediaReference
import java.io.File
import kotlin.math.max

data class SharePreviewItem(
    val id: String,
    val title: String,
    val time: String = "",
    val detail: String = "",
    val completed: Boolean = true,
    val statusText: String = "",
    val media: List<MediaReference> = emptyList(),
)

data class SharePreviewDay(
    val id: String,
    val heading: String,
    val date: String,
    val narrative: String = "",
    val items: List<SharePreviewItem> = emptyList(),
)

data class SharePreviewData(
    val title: String,
    val destination: String,
    val dateRange: String,
    val summary: String,
    val eyebrow: String,
    val scopeLabel: String,
    val scopeSummary: String,
    val cover: MediaReference? = null,
    val coverZoom: Double = 1.0,
    val coverOffsetX: Double = 0.0,
    val coverOffsetY: Double = 0.0,
    val days: List<SharePreviewDay>,
)

object ShareExportService {
    fun shareImage(context: Context, data: SharePreviewData) {
        val target = shareDirectory(context).resolve("${safeName(data.title)}-旅迹长图.png")
        val bitmap = renderImage(data)
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        share(context, target, "image/png", "分享精美长图")
    }

    fun shareImportable(context: Context, title: String, content: String) {
        val target = shareDirectory(context).resolve("${safeName(title)}.triptrail")
        target.writeText(content)
        share(context, target, "application/vnd.triptrail.journey", "发送可导入内容")
    }

    private fun share(context: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    internal fun renderImage(data: SharePreviewData): Bitmap {
        val width = 360f
        val height = estimateHeight(data)
        val density = 3f
        val bitmap = Bitmap.createBitmap((width * density).toInt(), (height * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

        drawCover(canvas, data, width, paint)
        paint.shader = LinearGradient(0f, 252f, 0f, height, PAPER_TOP, PAPER_BOTTOM, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 252f, width, height, paint)
        paint.shader = null

        var y = 272f
        if (data.summary.isNotBlank()) {
            val layout = textLayout(data.summary, 284, 14f, INK_SOFT, false, 3f, 4)
            val cardHeight = max(64f, layout.height + 28f)
            fillRoundRect(canvas, 20f, y, 340f, y + cardHeight, 16f, SUMMARY, paint)
            strokeRoundRect(canvas, 20f, y, 340f, y + cardHeight, 16f, LAKE_BORDER, paint)
            drawText(canvas, "“", 34f, y + 28f, 25f, LAKE_TEXT, true)
            drawLayout(canvas, layout, 52f, y + 14f)
            y += cardHeight + 18f
        }

        data.days.forEachIndexed { dayIndex, day ->
            val dayHeight = estimateDayHeight(day)
            fillRoundRect(canvas, 20f, y, 340f, y + dayHeight, 20f, DAY_PANEL, paint)
            strokeRoundRect(canvas, 20f, y, 340f, y + dayHeight, 20f, LAKE_BORDER, paint)
            fillRoundRect(canvas, 21f, y + 20f, 24f, y + dayHeight - 20f, 3f, LAKE_RAIL, paint)

            var innerY = y + 14f
            var titleX = 34f
            if (data.days.size > 1) {
                paint.color = LAKE
                canvas.drawCircle(52f, innerY + 18f, 18f, paint)
                drawCenteredText(canvas, "D${dayIndex + 1}", 52f, innerY + 22f, 12f, Color.WHITE, true)
                titleX = 80f
            }
            drawText(canvas, day.heading, titleX, innerY + 16f, 17f, INK, true)
            drawText(canvas, day.date, titleX, innerY + 35f, 11f, INK_MUTED, false)
            val count = "${day.items.size} 个片段"
            val countWidth = measureText(count, 10f, true) + 18f
            fillRoundRect(canvas, 326f - countWidth, innerY + 6f, 326f, innerY + 31f, 13f, TIME_BADGE, paint)
            drawText(canvas, count, 326f - countWidth + 9f, innerY + 23f, 10f, LAKE_TEXT, true)
            innerY += 50f

            if (day.narrative.isNotBlank()) {
                val narrative = textLayout(day.narrative, 290, 12f, INK_SOFT, false, 2f, 4)
                drawLayout(canvas, narrative, 34f, innerY)
                innerY += narrative.height + 12f
            }

            if (day.items.isEmpty()) {
                fillRoundRect(canvas, 34f, innerY, 326f, innerY + 46f, 14f, ITEM_TOP, paint)
                drawText(canvas, "这一天还没有具体内容", 47f, innerY + 28f, 12f, INK_MUTED, false)
            } else {
                day.items.forEachIndexed { itemIndex, item ->
                    val itemHeight = estimateItemHeight(item)
                    paint.shader = LinearGradient(34f, innerY, 326f, innerY + itemHeight, ITEM_TOP, ITEM_BOTTOM, Shader.TileMode.CLAMP)
                    canvas.drawRoundRect(RectF(34f, innerY, 326f, innerY + itemHeight), 16f, 16f, paint)
                    paint.shader = null
                    strokeRoundRect(canvas, 34f, innerY, 326f, innerY + itemHeight, 16f, ITEM_BORDER, paint)

                    paint.color = if (item.completed) SAGE else TIME_BADGE
                    canvas.drawCircle(59f, innerY + 25f, 12.5f, paint)
                    drawCenteredText(canvas, "${itemIndex + 1}", 59f, innerY + 29f, 11f, if (item.completed) Color.WHITE else LAKE_TEXT, true)
                    val timeWidth = if (item.time.isBlank()) 0f else measureText(item.time, 10f, true) + 16f
                    if (item.time.isNotBlank()) {
                        fillRoundRect(canvas, 310f - timeWidth, innerY + 13f, 310f, innerY + 38f, 13f, TIME_BADGE, paint)
                        drawText(canvas, item.time, 310f - timeWidth + 8f, innerY + 30f, 10f, LAKE_TEXT, true)
                    }
                    val titleWidth = (225f - timeWidth).toInt().coerceAtLeast(105)
                    val title = textLayout(item.title, titleWidth, 14f, INK, true, 0f, 2)
                    drawLayout(canvas, title, 80f, innerY + 15f)
                    var itemY = innerY + max(50f, title.height + 20f)
                    if (item.detail.isNotBlank()) {
                        val detail = textLayout(item.detail, 264, 12f, INK_SOFT, false, 2f, 4)
                        drawLayout(canvas, detail, 47f, itemY)
                        itemY += detail.height + 10f
                    }
                    val images = item.media.filter { it.kind == MediaKind.IMAGE }.take(4)
                    if (images.isNotEmpty()) drawPhotoGrid(canvas, images, 47f, itemY, 264f, paint, item.media.count { it.kind == MediaKind.IMAGE })
                    innerY += itemHeight + 10f
                }
            }
            y += dayHeight + 18f
        }

        drawText(canvas, "旅迹", 20f, y + 12f, 12f, INK, true)
        drawText(canvas, "把走过的路留下来", 20f, y + 29f, 10f, INK_MUTED, false)
        return bitmap
    }

    private fun drawCover(canvas: Canvas, data: SharePreviewData, width: Float, paint: Paint) {
        val rect = RectF(0f, 0f, width, 252f)
        val cover = decode(data.cover)
        if (cover != null) {
            drawCenterCrop(canvas, cover, rect, data.coverZoom.toFloat(), data.coverOffsetX.toFloat(), data.coverOffsetY.toFloat(), paint)
            cover.recycle()
        } else {
            paint.shader = LinearGradient(0f, 0f, width, 252f, INK, LAKE, Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint)
            paint.shader = null
            paint.color = Color.argb(20, 255, 255, 255)
            canvas.drawCircle(332f, -12f, 105f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 26f
            paint.color = Color.argb(25, 255, 255, 255)
            canvas.drawCircle(-34f, 220f, 72f, paint)
            paint.style = Paint.Style.FILL
        }
        paint.shader = LinearGradient(0f, 0f, 0f, 252f, intArrayOf(Color.argb(20, 0, 0, 0), Color.TRANSPARENT, Color.argb(184, 0, 0, 0)), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(rect, paint)
        paint.shader = null
        drawText(canvas, data.eyebrow, 22f, 32f, 10f, Color.argb(230, 255, 255, 255), true)
        val scopeWidth = measureText(data.scopeLabel, 10f, true) + 20f
        fillRoundRect(canvas, width - 22f - scopeWidth, 17f, width - 22f, 43f, 14f, Color.argb(65, 255, 255, 255), paint)
        drawText(canvas, data.scopeLabel, width - 12f - scopeWidth, 34f, 10f, Color.WHITE, true)
        val title = textLayout(data.title, 316, 30f, Color.WHITE, true, 2f, 2)
        drawLayout(canvas, title, 22f, 208f - title.height)
        drawText(canvas, "${data.destination.ifBlank { "目的地待定" }}  ·  ${data.dateRange}", 22f, 232f, 10f, Color.argb(230, 255, 255, 255), true)
    }

    private fun drawPhotoGrid(canvas: Canvas, media: List<MediaReference>, x: Float, y: Float, width: Float, paint: Paint, total: Int) {
        val columns = if (media.size == 1) 1 else 2
        val gap = 6f
        val cellWidth = (width - gap * (columns - 1)) / columns
        val cellHeight = if (columns == 1) cellWidth / 1.6f else cellWidth / 1.15f
        media.forEachIndexed { index, ref ->
            val left = x + (index % columns) * (cellWidth + gap)
            val top = y + (index / columns) * (cellHeight + gap)
            val rect = RectF(left, top, left + cellWidth, top + cellHeight)
            val bitmap = decode(ref)
            if (bitmap != null) {
                canvas.save()
                canvas.clipPath(Path().apply { addRoundRect(rect, 11f, 11f, Path.Direction.CW) })
                drawCenterCrop(canvas, bitmap, rect, 1f, 0f, 0f, paint)
                if (index == 3 && total > 4) {
                    paint.color = Color.argb(108, 0, 0, 0)
                    canvas.drawRect(rect, paint)
                    drawCenteredText(canvas, "+${total - 4}", rect.centerX(), rect.centerY() + 7f, 22f, Color.WHITE, true)
                }
                canvas.restore()
                bitmap.recycle()
            } else fillRoundRect(canvas, rect.left, rect.top, rect.right, rect.bottom, 11f, Color.rgb(224, 231, 226), paint)
        }
    }

    private fun estimateHeight(data: SharePreviewData): Float {
        var height = 272f
        if (data.summary.isNotBlank()) height += max(64f, textLayout(data.summary, 284, 14f, Color.BLACK, false, 3f, 4).height + 28f) + 18f
        height += data.days.sumOf { estimateDayHeight(it).toDouble() }.toFloat() + data.days.size * 18f
        return height + 55f
    }

    private fun estimateDayHeight(day: SharePreviewDay): Float {
        var height = 78f
        if (day.narrative.isNotBlank()) height += textLayout(day.narrative, 290, 12f, Color.BLACK, false, 2f, 4).height + 12f
        height += if (day.items.isEmpty()) 46f else day.items.sumOf { estimateItemHeight(it).toDouble() }.toFloat() + (day.items.size - 1).coerceAtLeast(0) * 10f
        return height + 14f
    }

    private fun estimateItemHeight(item: SharePreviewItem): Float {
        var height = 60f
        if (item.detail.isNotBlank()) height += textLayout(item.detail, 264, 12f, Color.BLACK, false, 2f, 4).height + 6f
        val count = item.media.count { it.kind == MediaKind.IMAGE }.coerceAtMost(4)
        if (count > 0) {
            val columns = if (count == 1) 1 else 2
            val cellWidth = (264f - if (columns == 2) 6f else 0f) / columns
            val cellHeight = if (columns == 1) cellWidth / 1.6f else cellWidth / 1.15f
            height += ((count + columns - 1) / columns) * cellHeight + ((count - 1) / columns) * 6f + 8f
        }
        return height
    }

    private fun decode(media: MediaReference?): Bitmap? = media?.localUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }?.let(BitmapFactory::decodeFile)

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, dest: RectF, zoom: Float, offsetX: Float, offsetY: Float, paint: Paint) {
        val safeZoom = zoom.coerceIn(1f, 4f)
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val destinationRatio = dest.width() / dest.height()
        var sourceWidth = bitmap.width.toFloat()
        var sourceHeight = bitmap.height.toFloat()
        if (sourceRatio > destinationRatio) sourceWidth = sourceHeight * destinationRatio else sourceHeight = sourceWidth / destinationRatio
        sourceWidth /= safeZoom
        sourceHeight /= safeZoom
        val maxX = (bitmap.width - sourceWidth) / 2f
        val maxY = (bitmap.height - sourceHeight) / 2f
        val centerX = bitmap.width / 2f + maxX * offsetX.coerceIn(-1f, 1f)
        val centerY = bitmap.height / 2f + maxY * offsetY.coerceIn(-1f, 1f)
        val source = Rect((centerX - sourceWidth / 2).toInt(), (centerY - sourceHeight / 2).toInt(), (centerX + sourceWidth / 2).toInt(), (centerY + sourceHeight / 2).toInt())
        canvas.drawBitmap(bitmap, source, dest, paint)
    }

    private fun textLayout(text: String, width: Int, size: Float, color: Int, bold: Boolean, spacing: Float, maxLines: Int): StaticLayout {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(spacing, 1f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        canvas.drawText(text, x, baseline, paint)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        canvas.drawText(text, x, baseline, paint)
    }

    private fun measureText(text: String, size: Float, bold: Boolean): Float = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }.measureText(text)

    private fun fillRoundRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
    }

    private fun strokeRoundRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = color
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun shareDirectory(context: Context) = File(context.cacheDir, "shares").apply { mkdirs() }
    private fun safeName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|]"), "-").ifBlank { "旅迹分享" }

    private const val INK = 0xFF1A3B36.toInt()
    private const val INK_SOFT = 0xFF455F59.toInt()
    private const val INK_MUTED = 0xFF657770.toInt()
    private const val LAKE = 0xFF4D9496.toInt()
    private const val LAKE_TEXT = 0xFF296E70.toInt()
    private const val SAGE = 0xFF6E9C7D.toInt()
    private const val PAPER_TOP = 0xFFF9F5E9.toInt()
    private const val PAPER_BOTTOM = 0xFFF2EEDB.toInt()
    private const val SUMMARY = 0xFFD8E6DB.toInt()
    private const val DAY_PANEL = 0xFFE6EBE2.toInt()
    private const val ITEM_TOP = 0xFFFBF8EF.toInt()
    private const val ITEM_BOTTOM = 0xFFF6F3E8.toInt()
    private const val TIME_BADGE = 0xFFD1E4E0.toInt()
    private const val LAKE_BORDER = 0x414D9496
    private const val LAKE_RAIL = 0x964D9496.toInt()
    private const val ITEM_BORDER = 0x344D9496
}
