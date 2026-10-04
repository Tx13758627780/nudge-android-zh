package com.astraedus.nudge.ui.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders a QR code the user will PRINT and later scan, possibly creased, taped to a wall, under a
 * desk lamp. Every parameter is chosen for that:
 *
 *  - error correction [ERROR_CORRECTION] (Q, ~25% recoverable), above the M floor, because a
 *    printed code gets folded and scuffed and the payload is short enough that Q costs nothing;
 *  - a [QUIET_ZONE_MODULES]-module white border, the size the QR spec requires, so the code still
 *    scans when printed on a busy page or cut out tight;
 *  - pure black on pure white, the contrast every printer and every scanner agrees on.
 *
 * The matrix and pixel steps are pure (zxing + an IntArray) and JVM-tested; only [generate] touches
 * the android [Bitmap], which a JVM test cannot construct.
 */
object QrCodeGenerator {

    internal val ERROR_CORRECTION = ErrorCorrectionLevel.Q
    internal const val QUIET_ZONE_MODULES = 4
    internal const val BLACK = 0xFF000000.toInt()
    internal const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * A square QR bitmap for [content]. The side is [sizePx], or the smallest size that fits one
     * pixel per module if [sizePx] is smaller than that (zxing never shrinks a code below it).
     */
    fun generate(content: String, sizePx: Int = 1024): Bitmap {
        val matrix = encode(content, sizePx)
        return Bitmap.createBitmap(toPixels(matrix), matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    internal fun encode(content: String, sizePx: Int): BitMatrix {
        require(content.isNotEmpty()) { "QR content must not be empty" }
        require(sizePx > 0) { "sizePx must be positive, was $sizePx" }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ERROR_CORRECTION,
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name()
        )
        return QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }

    /** Row-major ARGB pixels, [BLACK] for a set module and [WHITE] for everything else. */
    internal fun toPixels(matrix: BitMatrix): IntArray {
        val width = matrix.width
        val pixels = IntArray(width * matrix.height)
        for (y in 0 until matrix.height) {
            val row = y * width
            for (x in 0 until width) pixels[row + x] = if (matrix[x, y]) BLACK else WHITE
        }
        return pixels
    }
}
