package com.astraedus.nudge.ui.qr

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.astraedus.nudge.R
import java.io.File
import java.io.IOException

/**
 * Hands a generated QR image to the Android share sheet, which is how the user saves it to the
 * gallery, prints it, or sends it to themselves. Nudge has no INTERNET permission and keeps it that
 * way: the image leaves the device only through a target the user picks.
 *
 * The file goes through the app's ONE FileProvider (`${applicationId}.fileprovider`, whose
 * `cache-path` already covers the whole cache dir), under [SHARE_DIR]. A write or launch failure
 * is a short toast, never a crash: the caller is a button, and a QR it cannot share is still on
 * screen.
 */
object QrShare {

    internal const val SHARE_DIR = "qr"
    internal const val MIME_TYPE = "image/png"
    internal const val FALLBACK_FILE_NAME = "nudge-qr.png"
    private const val MAX_BASE_NAME = 64

    fun share(context: Context, bitmap: Bitmap, fileName: String, chooserTitle: String) {
        try {
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            val file = File(dir, safeFileName(fileName))
            val written = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!written) throw IOException("PNG encoder refused the bitmap")

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                // ClipData carries the read grant to the chosen target AND gives the chooser its
                // image preview; EXTRA_STREAM alone gets neither on current Android.
                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, chooserTitle)
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: IOException) {
            Toast.makeText(context, context.getString(R.string.qr_share_save_error), Toast.LENGTH_SHORT).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.qr_share_no_app), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * A caller-supplied name reduced to a single safe path segment ending in `.png`: no directory
     * separators (the provider would happily serve `../databases/...` if a name ever smuggled one
     * in), no leading dots, a bounded length, and a fallback when nothing usable is left.
     */
    internal fun safeFileName(requested: String): String {
        val trimmed = requested.trim()
        val base = (if (trimmed.endsWith(".png", ignoreCase = true)) trimmed.dropLast(4) else trimmed)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trimStart('.', '_', '-')
            .take(MAX_BASE_NAME)
            .trimEnd('.')
        return if (base.isEmpty() || base.all { it == '_' }) FALLBACK_FILE_NAME else "$base.png"
    }
}
