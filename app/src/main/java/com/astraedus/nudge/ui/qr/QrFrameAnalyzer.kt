package com.astraedus.nudge.ui.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * The thin CameraX adapter: copy the Y plane out of the frame, hand it to the pure
 * [BarcodeFrameDecoder], let [ScanConfirmer] decide whether this read settles it. Every decision
 * lives in those two JVM-tested classes; this only moves bytes and always closes the frame (an
 * unclosed [ImageProxy] stalls the analysis stream for good).
 *
 * Runs on CameraX's single analysis thread, so the decoder, confirmer and scratch buffer need no
 * locking. [onResult] is called from that thread, at most once.
 */
internal class QrFrameAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val decoder = BarcodeFrameDecoder()
    private val confirmer = ScanConfirmer()
    private var scratch = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val size = buffer.remaining()
            if (scratch.size != size) scratch = ByteArray(size)
            buffer.get(scratch)

            val frame = LuminanceFrames.pack(scratch, image.width, image.height, plane.rowStride, plane.pixelStride)
            val code = decoder.decode(frame, image.width, image.height, image.imageInfo.rotationDegrees) ?: return
            confirmer.offer(code)?.let(onResult)
        } finally {
            image.close()
        }
    }
}
