package com.astraedus.nudge.ui.qr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LuminanceFramesTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `pack strips the row padding a camera plane carries`() {
        // 3x2 frame, rows 5 bytes apart; 9s are padding. The last row may stop short of the stride.
        val plane = bytes(1, 2, 3, 9, 9, 4, 5, 6)
        assertArrayEquals(bytes(1, 2, 3, 4, 5, 6), LuminanceFrames.pack(plane, 3, 2, rowStride = 5))
    }

    @Test
    fun `pack honours a pixel stride above one`() {
        val plane = bytes(1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6)
        assertArrayEquals(bytes(1, 2, 3, 4, 5, 6), LuminanceFrames.pack(plane, 3, 2, rowStride = 6, pixelStride = 2))
    }

    @Test
    fun `an already packed plane is returned without a copy`() {
        val plane = bytes(1, 2, 3, 4, 5, 6)
        assertSame(plane, LuminanceFrames.pack(plane, 3, 2, rowStride = 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a stride narrower than the frame is rejected, not read out of bounds`() {
        LuminanceFrames.pack(bytes(1, 2, 3, 4), 3, 2, rowStride = 2)
    }

    @Test
    fun `rotate90 turns the frame clockwise`() {
        // 3 wide x 2 high:     rotated, 2 wide x 3 high:
        //   1 2 3                4 1
        //   4 5 6                5 2
        //                        6 3
        assertArrayEquals(bytes(4, 1, 5, 2, 6, 3), LuminanceFrames.rotate90(bytes(1, 2, 3, 4, 5, 6), 3, 2))
    }

    @Test
    fun `rotate90 into a reused buffer overwrites every byte of it`() {
        val out = ByteArray(6) { 99 }
        val result = LuminanceFrames.rotate90(bytes(1, 2, 3, 4, 5, 6), 3, 2, out)
        assertSame(out, result)
        assertArrayEquals(bytes(4, 1, 5, 2, 6, 3), out)
    }

    @Test
    fun `four quarter turns are the identity on a non-square frame`() {
        val w = 7
        val h = 4
        val frame = ByteArray(w * h) { it.toByte() }
        var current = frame
        var cw = w
        var ch = h
        repeat(4) {
            current = LuminanceFrames.rotate90(current, cw, ch)
            val t = cw; cw = ch; ch = t
        }
        assertArrayEquals(frame, current)
    }
}
