package com.astraedus.nudge.ui.qr

import com.google.zxing.BarcodeFormat

/**
 * Turns a stream of per-frame decodes into AT MOST ONE delivered result.
 *
 * Two jobs, both about what the caller will do with the text: it is stored and later compared for
 * EXACT equality (scan this again to turn Nuke off). So:
 *
 *  1. **Deliver once.** The analyzer keeps producing decodes for the few hundred milliseconds it
 *     takes the activity to finish; without a latch the result would be delivered, and acted on,
 *     several times.
 *  2. **Do not trust a single 1D read.** A QR code carries Reed-Solomon error correction, so a read
 *     is either right or absent. A 1D barcode has at best one check digit, and a partial, skewed or
 *     glare-split read can decode as a DIFFERENT valid number. Registering that wrong number would
 *     leave the user holding a bottle that never matches. Requiring [ONE_D_CONFIRMATIONS] agreeing
 *     reads costs ~100 ms of a steady hand and removes that failure.
 *
 * Frames with no decode in between do not reset the count (a missed frame is normal); a
 * DISAGREEING read does.
 *
 * Not thread-safe by design: it lives on the single analysis thread.
 */
internal class ScanConfirmer {

    private var candidate: String? = null
    private var agreeing = 0
    private var delivered = false

    /** Returns the text to deliver, exactly once, or null when this read does not settle it. */
    fun offer(code: DecodedCode): String? {
        if (delivered || code.text.isBlank()) return null

        if (code.text == candidate) {
            agreeing++
        } else {
            candidate = code.text
            agreeing = 1
        }

        if (agreeing < confirmationsFor(code.format)) return null
        delivered = true
        return code.text
    }

    companion object {
        const val ONE_D_CONFIRMATIONS = 2

        private val TWO_D_FORMATS = setOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417
        )

        fun confirmationsFor(format: BarcodeFormat): Int =
            if (format in TWO_D_FORMATS) 1 else ONE_D_CONFIRMATIONS
    }
}
