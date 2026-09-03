package com.personal.triptrail.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract

/** Uses the Android photo picker directly instead of the DocumentsUI download browser. */
class SystemImagePickerContract(
    private val multiple: Boolean = false,
    private val allowImagesAndVideos: Boolean = false,
) : ActivityResultContract<Unit, List<Uri>>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = if (allowImagesAndVideos) "*/*" else "image/*"
                if (multiple) {
                    putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 20)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }
        } else {
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = if (allowImagesAndVideos) "*/*" else "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null) return emptyList()
        val values = buildList {
            intent.data?.let(::add)
            intent.clipData?.let { clip -> (0 until clip.itemCount).mapTo(this) { clip.getItemAt(it).uri } }
        }
        return values.distinct()
    }
}
