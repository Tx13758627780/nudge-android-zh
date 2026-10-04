package com.astraedus.nudge.ui.qr

import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanConfirmerTest {

    private val qr = DecodedCode("nudge-nuke:abc", BarcodeFormat.QR_CODE)
    private val ean = DecodedCode("4006381333931", BarcodeFormat.EAN_13)
    private val misread = DecodedCode("4006381333948", BarcodeFormat.EAN_13)

    @Test
    fun `a QR code is delivered on its first read`() {
        assertEquals(qr.text, ScanConfirmer().offer(qr))
    }

    @Test
    fun `a 1D read is held until a second read agrees`() {
        val confirmer = ScanConfirmer()
        assertNull(confirmer.offer(ean))
        assertEquals(ean.text, confirmer.offer(ean))
    }

    @Test
    fun `a disagreeing 1D read restarts the count, so a one-off misread is never delivered`() {
        val confirmer = ScanConfirmer()
        assertNull(confirmer.offer(ean))
        assertNull(confirmer.offer(misread))
        assertNull(confirmer.offer(ean))
        assertEquals(ean.text, confirmer.offer(ean))
    }

    @Test
    fun `nothing is delivered twice, whatever keeps arriving`() {
        val confirmer = ScanConfirmer()
        assertEquals(qr.text, confirmer.offer(qr))
        assertNull(confirmer.offer(qr))
        assertNull(confirmer.offer(ean))
        assertNull(confirmer.offer(ean))
    }

    @Test
    fun `blank text is ignored and does not reset a candidate`() {
        val confirmer = ScanConfirmer()
        assertNull(confirmer.offer(ean))
        assertNull(confirmer.offer(DecodedCode("  ", BarcodeFormat.CODE_128)))
        assertEquals(ean.text, confirmer.offer(ean))
    }

    /** Class invariant: every format the scanner reads has a defined confirmation rule. */
    @Test
    fun `only 2D formats skip confirmation`() {
        for (format in BarcodeFrameDecoder.SUPPORTED_FORMATS) {
            val expected = if (format == BarcodeFormat.QR_CODE || format == BarcodeFormat.DATA_MATRIX) 1 else ScanConfirmer.ONE_D_CONFIRMATIONS
            assertEquals("confirmations for $format", expected, ScanConfirmer.confirmationsFor(format))
        }
    }
}
