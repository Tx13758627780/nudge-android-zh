package com.astraedus.nudge.ui.qr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContract

/**
 * Opens [QrScanActivity] and returns the decoded raw text of the first code it reads (a QR code,
 * or a retail 1D barcode such as EAN-13 / UPC / Code 128), or null when the user cancels, denies
 * the camera, or the device has no camera.
 *
 * Usage from Compose:
 * ```
 * val scan = rememberLauncherForActivityResult(ScanQrContract()) { text -> ... }
 * scan.launch(ScanQrContract.Request(title = "Scan your Nuke code"))
 * ```
 *
 * The same text for the same physical code, every time: the scanner's format set and hints are
 * fixed ([BarcodeFrameDecoder.SUPPORTED_FORMATS]), so a code registered through this contract and
 * scanned again later through it compares equal as a plain string.
 */
class ScanQrContract : ActivityResultContract<ScanQrContract.Request, String?>() {

    data class Request(val title: String, val subtitle: String? = null)

    override fun createIntent(context: Context, input: Request): Intent =
        Intent(context, QrScanActivity::class.java)
            .putExtra(QrScanActivity.EXTRA_TITLE, input.title)
            .putExtra(QrScanActivity.EXTRA_SUBTITLE, input.subtitle)

    /** No camera at all: answer null immediately instead of opening a screen that cannot work. */
    override fun getSynchronousResult(context: Context, input: Request): SynchronousResult<String?>? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            null
        } else {
            SynchronousResult(null)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) {
            intent?.getStringExtra(QrScanActivity.EXTRA_RESULT)?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
}
