package com.personal.triptrail.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.InputStream

/** Decode original pixels and apply the EXIF transform exactly once, including mirrored images. */
internal object OrientedImageDecoder {
    fun decode(context: Context, uri: Uri, maxDimension: Int): Bitmap? =
        decode(maxDimension) { context.contentResolver.openInputStream(uri) }

    fun decodeFile(path: String, maxDimension: Int = 2048): Bitmap? =
        decode(maxDimension) { File(path).inputStream() }

    private fun decode(maxDimension: Int, open: () -> InputStream?): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > maxDimension) {
            options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        }
        val orientation = runCatching {
            open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }.getOrNull()
}
