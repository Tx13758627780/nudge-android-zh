package com.astraedus.nudge.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugScanOverrideTest {

    @Test
    fun `unset or blank property means no override`() {
        assertNull(DebugScanOverride.normalize(null))
        assertNull(DebugScanOverride.normalize(""))
        assertNull(DebugScanOverride.normalize("\n"))
        assertNull(DebugScanOverride.normalize("   \n"))
    }

    @Test
    fun `getprop output is trimmed to the payload`() {
        assertEquals("nudge-nuke:abc", DebugScanOverride.normalize("nudge-nuke:abc\n"))
        assertEquals("5012345678900", DebugScanOverride.normalize("  5012345678900 \n"))
    }
}
