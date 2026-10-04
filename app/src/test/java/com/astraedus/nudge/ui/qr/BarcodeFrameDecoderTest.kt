package com.astraedus.nudge.ui.qr

import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner's decode path, exactly as it runs on the analysis thread, over synthetic camera
 * frames. The user-facing promise under test: a QR code we printed, or a barcode already on a
 * product in the house, reads back as the same text.
 */
class BarcodeFrameDecoderTest {

    private val decoder = BarcodeFrameDecoder()

    private fun decodeOneD(text: String, format: BarcodeFormat, sideways: Boolean, rotation: Int = 0): DecodedCode? {
        val code = QrTestFrames.render(text, format, 600, 160)
        return decoder.decode(QrTestFrames.frameWith(code, sideways = sideways), 1280, 720, rotation)
    }

    @Test
    fun `a level EAN-13 decodes to its 13 digits`() {
        assertEquals(DecodedCode("4006381333931", BarcodeFormat.EAN_13), decodeOneD("4006381333931", BarcodeFormat.EAN_13, sideways = false))
    }

    @Test
    fun `an EAN-13 lying sideways in the raw frame still decodes`() {
        // Portrait viewfinder over a landscape sensor: CameraX reports rotationDegrees = 90.
        assertEquals(
            DecodedCode("4006381333931", BarcodeFormat.EAN_13),
            decodeOneD("4006381333931", BarcodeFormat.EAN_13, sideways = true, rotation = 90)
        )
    }

    @Test
    fun `a sideways barcode decodes even when the reported rotation is wrong`() {
        assertEquals(
            DecodedCode("4006381333931", BarcodeFormat.EAN_13),
            decodeOneD("4006381333931", BarcodeFormat.EAN_13, sideways = true, rotation = 0)
        )
    }

    @Test
    fun `a level barcode decodes when the reported rotation says sideways`() {
        assertEquals(
            DecodedCode("4006381333931", BarcodeFormat.EAN_13),
            decodeOneD("4006381333931", BarcodeFormat.EAN_13, sideways = false, rotation = 270)
        )
    }

    /**
     * Counterfactual for the rotation fallback: without it, the sideways barcode is unreadable.
     * If this ever starts passing on its own (zxing learning to rotate a planar source), the
     * fallback is dead weight and can go; until then it is load-bearing.
     */
    @Test
    fun `without the rotation fallback a sideways barcode is NOT readable`() {
        val code = QrTestFrames.render("4006381333931", BarcodeFormat.EAN_13, 600, 160)
        val frame = QrTestFrames.frameWith(code, sideways = true)
        assertNull(decoder.decodeOrientation(frame, 1280, 720))
    }

    @Test
    fun `a UPC-A decodes to its 12 digits`() {
        assertEquals(DecodedCode("036000291452", BarcodeFormat.UPC_A), decodeOneD("036000291452", BarcodeFormat.UPC_A, sideways = false))
    }

    @Test
    fun `an EAN-8 decodes`() {
        assertEquals(DecodedCode("96385074", BarcodeFormat.EAN_8), decodeOneD("96385074", BarcodeFormat.EAN_8, sideways = false))
    }

    @Test
    fun `a Code 128 decodes with its letters intact`() {
        assertEquals(DecodedCode("NUDGE-128-ok", BarcodeFormat.CODE_128), decodeOneD("NUDGE-128-ok", BarcodeFormat.CODE_128, sideways = true, rotation = 90))
    }

    @Test
    fun `a QR code decodes in either orientation`() {
        val code = QrTestFrames.render("nudge-nuke:abc", BarcodeFormat.QR_CODE, 320, 320)
        assertEquals("nudge-nuke:abc", decoder.decode(QrTestFrames.frameWith(code), 1280, 720)?.text)
        assertEquals("nudge-nuke:abc", decoder.decode(QrTestFrames.frameWith(code, sideways = true), 1280, 720, 90)?.text)
    }

    @Test
    fun `a frame with nothing in it decodes to null, not an exception`() {
        assertNull(decoder.decode(ByteArray(1280 * 720) { QrTestFrames.BACKGROUND }, 1280, 720))
    }

    @Test
    fun `the decoder is reusable after a miss`() {
        assertNull(decoder.decode(ByteArray(1280 * 720) { QrTestFrames.BACKGROUND }, 1280, 720))
        assertEquals("96385074", decodeOneD("96385074", BarcodeFormat.EAN_8, sideways = false)?.text)
    }

    @Test
    fun `one decoder handles a change of frame size between sideways reads`() {
        val code = QrTestFrames.render("4006381333931", BarcodeFormat.EAN_13, 400, 120)
        val big = QrTestFrames.frameWith(code, sideways = true)
        val small = QrTestFrames.frameWith(code, frameWidth = 640, frameHeight = 480, left = 40, top = 20, sideways = true)
        assertEquals("4006381333931", decoder.decode(big, 1280, 720, 90)?.text)
        assertEquals("4006381333931", decoder.decode(small, 640, 480, 90)?.text)
        assertEquals("4006381333931", decoder.decode(big, 1280, 720, 90)?.text)
    }

    @Test
    fun `a row-padded camera plane decodes once packed`() {
        val width = 1280
        val height = 720
        val rowStride = 1344
        val packed = QrTestFrames.frameWith(QrTestFrames.render("4006381333931", BarcodeFormat.EAN_13, 600, 160))
        val padded = ByteArray(rowStride * height)
        for (y in 0 until height) System.arraycopy(packed, y * width, padded, y * rowStride, width)

        val frame = LuminanceFrames.pack(padded, width, height, rowStride)

        assertEquals("4006381333931", decoder.decode(frame, width, height)?.text)
    }

    /**
     * Class invariant over the format set: every symbology we read must carry a mandatory check.
     * ITF and Codabar do not, so a partial read decodes as a DIFFERENT plausible number, and a
     * registered wrong number is a code the user can never scan again.
     */
    @Test
    fun `no symbology without a mandatory check is accepted`() {
        val unchecked = setOf(BarcodeFormat.ITF, BarcodeFormat.CODABAR, BarcodeFormat.MAXICODE, BarcodeFormat.RSS_EXPANDED)
        assertTrue(BarcodeFrameDecoder.SUPPORTED_FORMATS.intersect(unchecked).isEmpty())
    }

    @Test
    fun `QR and the retail barcodes the feature promises are all in the format set`() {
        val promised = setOf(
            BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.CODE_128
        )
        assertTrue(BarcodeFrameDecoder.SUPPORTED_FORMATS.containsAll(promised))
    }

    @Test
    fun `an ITF barcode is not read at all`() {
        val code = QrTestFrames.render("12345678901231", BarcodeFormat.ITF, 600, 160)
        assertFalse(decoder.decode(QrTestFrames.frameWith(code), 1280, 720)?.format == BarcodeFormat.ITF)
    }
}
