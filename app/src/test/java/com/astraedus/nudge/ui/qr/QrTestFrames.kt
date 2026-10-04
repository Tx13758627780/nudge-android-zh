package com.astraedus.nudge.ui.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * Builds synthetic camera frames for the decoder tests: a mid-grey sensor frame with a code drawn
 * into it, the way the analyzer sees one, rather than a bare code image zxing would find trivially.
 *
 * The rotation here is written independently of [LuminanceFrames.rotate90] on purpose, so a bug in
 * production's rotation cannot make a test frame agree with it.
 */
internal object QrTestFrames {

    const val BACKGROUND: Byte = 0x90.toByte()
    const val DARK: Byte = 0x18
    const val LIGHT: Byte = 0xF0.toByte()

    /** A 1D or 2D code rendered by zxing's own writer (quiet zone included). */
    fun render(text: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix =
        MultiFormatWriter().encode(text, format, width, height)

    /**
     * A [frameWidth] x [frameHeight] luminance frame with [code] drawn at ([left], [top]).
     * [sideways] draws it turned 90 degrees clockwise, as a level barcode appears in the raw frame
     * of a sensor mounted landscape under a portrait viewfinder.
     */
    fun frameWith(
        code: BitMatrix,
        frameWidth: Int = 1280,
        frameHeight: Int = 720,
        left: Int = 100,
        top: Int = 60,
        sideways: Boolean = false
    ): ByteArray {
        val frame = ByteArray(frameWidth * frameHeight) { BACKGROUND }
        val drawnWidth = if (sideways) code.height else code.width
        val drawnHeight = if (sideways) code.width else code.height
        require(left + drawnWidth <= frameWidth && top + drawnHeight <= frameHeight) {
            "code ${drawnWidth}x$drawnHeight does not fit at ($left,$top) in ${frameWidth}x$frameHeight"
        }
        for (cy in 0 until code.height) {
            for (cx in 0 until code.width) {
                // Clockwise quarter turn: code (cx, cy) lands at (codeHeight - 1 - cy, cx).
                val dx = if (sideways) code.height - 1 - cy else cx
                val dy = if (sideways) cx else cy
                frame[(top + dy) * frameWidth + left + dx] = if (code[cx, cy]) DARK else LIGHT
            }
        }
        return frame
    }

    /** Luminance of an ARGB pixel array, as a greyscale camera plane would carry it. */
    fun luminance(argb: IntArray): ByteArray = ByteArray(argb.size) { i ->
        val p = argb[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        ((r * 299 + g * 587 + b * 114) / 1000).toByte()
    }
}
