package com.astraedus.nudge.ui.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The generated code is going to be PRINTED and scanned months later to turn Nuke off. These tests
 * pin that our generation parameters produce something a scanner reads back byte-for-byte, with
 * the error correction and quiet zone that make a printed code survive.
 *
 * Everything runs on the matrix and the pixel array [QrCodeGenerator.generate] builds its Bitmap
 * from; the Bitmap constructor itself is an android stub on the JVM.
 */
class QrCodeGeneratorTest {

    /** The shape lane B's Nuke code uses: a fixed prefix plus 32 random bytes in base64url. */
    private val nukePayload: String = "nudge-nuke:" +
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { (it * 37 + 11).toByte() })

    @Test
    fun `the nuke payload shape is what the round trip is actually testing`() {
        assertEquals(43, nukePayload.removePrefix("nudge-nuke:").length)
    }

    @Test
    fun `generated pixels decode back to the exact payload with a plain QR reader`() {
        val matrix = QrCodeGenerator.encode(nukePayload, 1024)
        val pixels = QrCodeGenerator.toPixels(matrix)
        val source = PlanarYUVLuminanceSource(
            QrTestFrames.luminance(pixels), matrix.width, matrix.height, 0, 0, matrix.width, matrix.height, false
        )

        val result = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(DecodeHintType.TRY_HARDER to true)
        )

        assertEquals(nukePayload, result.text)
        assertEquals(BarcodeFormat.QR_CODE, result.barcodeFormat)
    }

    @Test
    fun `generated code decodes through the scanner's own decoder inside a camera-sized frame`() {
        // Small (as printed on a sticker, far from the lens) and off-centre in a grey frame.
        val matrix = QrCodeGenerator.encode(nukePayload, 300)
        val frame = QrTestFrames.frameWith(matrix, left = 700, top = 300)

        val decoded = BarcodeFrameDecoder().decode(frame, 1280, 720)

        assertEquals(DecodedCode(nukePayload, BarcodeFormat.QR_CODE), decoded)
    }

    @Test
    fun `error correction is at least M and is the declared level`() {
        val matrix = QrCodeGenerator.encode(nukePayload, 1024)
        val source = PlanarYUVLuminanceSource(
            QrTestFrames.luminance(QrCodeGenerator.toPixels(matrix)),
            matrix.width, matrix.height, 0, 0, matrix.width, matrix.height, false
        )
        val level = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
            .resultMetadata[ResultMetadataType.ERROR_CORRECTION_LEVEL]

        assertEquals(QrCodeGenerator.ERROR_CORRECTION.name, level)
        // zxing declares the levels in strength order: L, M, Q, H.
        assertTrue(
            "a printed code needs at least M",
            QrCodeGenerator.ERROR_CORRECTION.ordinal >= ErrorCorrectionLevel.M.ordinal
        )
    }

    @Test
    fun `the code keeps a quiet zone of at least four modules on every side`() {
        val sizePx = 1024
        val matrix = QrCodeGenerator.encode(nukePayload, sizePx)
        // Natural size = one pixel per module, margin included, with the production hints.
        val natural = QRCodeWriter().encode(
            nukePayload, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to QrCodeGenerator.ERROR_CORRECTION,
                EncodeHintType.MARGIN to QrCodeGenerator.QUIET_ZONE_MODULES
            )
        )
        val modulePx = sizePx / natural.width
        val (left, top, width, height) = matrix.enclosingRectangle.toList()
        val minQuiet = QrCodeGenerator.QUIET_ZONE_MODULES * modulePx

        assertTrue("QUIET_ZONE_MODULES must be the spec's 4", QrCodeGenerator.QUIET_ZONE_MODULES >= 4)
        assertTrue("left quiet zone $left < $minQuiet", left >= minQuiet)
        assertTrue("top quiet zone $top < $minQuiet", top >= minQuiet)
        assertTrue("right quiet zone", matrix.width - (left + width) >= minQuiet)
        assertTrue("bottom quiet zone", matrix.height - (top + height) >= minQuiet)
    }

    @Test
    fun `output is the requested square size`() {
        val matrix = QrCodeGenerator.encode(nukePayload, 1024)
        assertEquals(1024, matrix.width)
        assertEquals(1024, matrix.height)
    }

    @Test
    fun `a size below one pixel per module grows to fit instead of failing`() {
        val matrix = QrCodeGenerator.encode(nukePayload, 10)
        assertTrue(matrix.width > 10)
        assertEquals(matrix.width, matrix.height)
    }

    @Test
    fun `pixels are pure black on pure white and both are present`() {
        val pixels = QrCodeGenerator.toPixels(QrCodeGenerator.encode(nukePayload, 512))
        val colours = pixels.toSet()
        assertEquals(setOf(QrCodeGenerator.BLACK, QrCodeGenerator.WHITE), colours)
        assertEquals(0xFF000000.toInt(), QrCodeGenerator.BLACK)
        assertEquals(0xFFFFFFFF.toInt(), QrCodeGenerator.WHITE)
    }

    @Test
    fun `pixel corner is white because the quiet zone is`() {
        val matrix = QrCodeGenerator.encode(nukePayload, 512)
        val pixels = QrCodeGenerator.toPixels(matrix)
        assertEquals(QrCodeGenerator.WHITE, pixels[0])
        assertEquals(QrCodeGenerator.WHITE, pixels[pixels.size - 1])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty content is rejected`() {
        QrCodeGenerator.encode("", 1024)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive size is rejected`() {
        QrCodeGenerator.encode(nukePayload, 0)
    }

    @Test
    fun `non-ascii content survives the round trip`() {
        val text = "Nudge ☕ café"
        val matrix = QrCodeGenerator.encode(text, 600)
        val source = PlanarYUVLuminanceSource(
            QrTestFrames.luminance(QrCodeGenerator.toPixels(matrix)),
            matrix.width, matrix.height, 0, 0, matrix.width, matrix.height, false
        )
        assertEquals(text, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text)
    }
}
