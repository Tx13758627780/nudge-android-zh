package com.astraedus.nudge.ui.qr

/**
 * Pure byte-array helpers for the camera's luminance (Y) plane. No android imports, so every
 * transformation the scanner applies to a frame is a JVM test away from being checked.
 */
internal object LuminanceFrames {

    /**
     * Copies [width] x [height] luminance bytes out of a plane whose rows are [rowStride] bytes
     * apart. Camera planes pad each row for alignment (a 1280-wide frame commonly has a 1344 or
     * 1536 stride), and a decoder handed the padded buffer as if it were packed reads every row
     * after the first shifted sideways, so the image shears into noise.
     *
     * [pixelStride] is 1 for the Y plane of every YUV_420_888 frame the platform documents, but
     * handling >1 costs nothing and means a vendor that interleaves does not silently shear too.
     */
    fun pack(
        plane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int = 1
    ): ByteArray {
        require(width > 0 && height > 0) { "frame must be non-empty, was ${width}x$height" }
        require(pixelStride >= 1) { "pixelStride must be >= 1, was $pixelStride" }
        require(rowStride >= (width - 1) * pixelStride + 1) {
            "rowStride $rowStride too small for width $width at pixelStride $pixelStride"
        }
        if (rowStride == width && pixelStride == 1 && plane.size == width * height) return plane

        val out = ByteArray(width * height)
        for (y in 0 until height) {
            val rowStart = y * rowStride
            if (pixelStride == 1) {
                System.arraycopy(plane, rowStart, out, y * width, width)
            } else {
                for (x in 0 until width) out[y * width + x] = plane[rowStart + x * pixelStride]
            }
        }
        return out
    }

    /**
     * Rotates a packed [width] x [height] frame 90 degrees clockwise. The result is
     * [height] x [width]: pixel (x, y) moves to (height - 1 - y, x).
     *
     * Why the scanner needs this at all: zxing's 1D readers scan ROWS, and a camera sensor is
     * usually mounted landscape, so a barcode the user holds level in a portrait viewfinder lands
     * in the raw frame as vertical bars that no row crosses. zxing can rotate for itself, but only
     * a luminance source that supports rotation, and `PlanarYUVLuminanceSource` does not.
     */
    fun rotate90(
        frame: ByteArray,
        width: Int,
        height: Int,
        out: ByteArray = ByteArray(width * height)
    ): ByteArray {
        require(frame.size >= width * height && out.size >= width * height) {
            "frame (${frame.size}) and out (${out.size}) need ${width * height} bytes for ${width}x$height"
        }
        for (y in 0 until height) {
            val srcRow = y * width
            val dstX = height - 1 - y
            for (x in 0 until width) {
                out[x * height + dstX] = frame[srcRow + x]
            }
        }
        return out
    }
}
