package com.astraedus.nudge.ui.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/** One decoded code: its raw text and the symbology it was read as. */
internal data class DecodedCode(val text: String, val format: BarcodeFormat)

/**
 * Decodes one camera frame's luminance plane with zxing. Pure Java underneath, so the whole decode
 * path the scanner runs on the device is the path the JVM tests run.
 *
 * NOT thread-safe ([MultiFormatReader] keeps per-decode state): one instance per analyzer thread,
 * which CameraX's single analysis executor already guarantees.
 */
internal class BarcodeFrameDecoder {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to SUPPORTED_FORMATS.toList(),
                // Spend more per frame on a harder search: a scanner looks at a few frames a
                // second, not thousands, and the frames are hand-held and badly lit.
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    private var rotated = ByteArray(0)

    /**
     * Decodes a packed [width] x [height] luminance frame. [rotationDegrees] is how far the frame
     * must turn clockwise to be upright as the user sees it (CameraX's `rotationDegrees`); it only
     * decides which orientation is tried FIRST.
     *
     * Both orientations are always tried on a miss, because a 1D barcode is only readable across its
     * bars and the user may hold it either way. A QR code decodes in any orientation, so for it the
     * second pass never runs.
     */
    fun decode(frame: ByteArray, width: Int, height: Int, rotationDegrees: Int = 0): DecodedCode? {
        val asIs = { decodeOrientation(frame, width, height) }
        val turned = {
            // Reused across frames: at ~1 MB a frame and several frames a second, a fresh array
            // per miss is steady garbage for the whole time the scanner is open.
            if (rotated.size != width * height) rotated = ByteArray(width * height)
            decodeOrientation(LuminanceFrames.rotate90(frame, width, height, rotated), height, width)
        }
        return if (rotationDegrees % 180 == 0) asIs() ?: turned() else turned() ?: asIs()
    }

    /** One zxing pass over [frame] exactly as given. Exposed so tests can prove the fallback matters. */
    fun decodeOrientation(frame: ByteArray, width: Int, height: Int): DecodedCode? {
        val source = PlanarYUVLuminanceSource(frame, width, height, 0, 0, width, height, false)
        return try {
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            val text = result.text
            if (text.isNullOrEmpty()) null else DecodedCode(text, result.barcodeFormat)
        } catch (_: ReaderException) {
            // NotFound / Checksum / Format: an ordinary frame with nothing readable in it.
            null
        } finally {
            reader.reset()
        }
    }

    companion object {
        /**
         * What the scanner reads. QR is the code Nudge prints; the retail symbologies are there so a
         * user can register a barcode already in their house (a shampoo bottle) instead of printing.
         *
         * Deliberately absent: ITF and Codabar. Neither carries a mandatory check digit, so a
         * partial read of an unrelated pattern decodes as a plausible wrong number, and a wrong
         * number here is a code the user can never scan again.
         */
        val SUPPORTED_FORMATS: Set<BarcodeFormat> = setOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93
        )
    }
}
