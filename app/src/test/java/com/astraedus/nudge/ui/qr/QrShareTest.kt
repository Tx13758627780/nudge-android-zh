package com.astraedus.nudge.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrShareTest {

    @Test
    fun `an ordinary name gains the png extension once`() {
        assertEquals("nudge-nuke-code.png", QrShare.safeFileName("nudge-nuke-code"))
        assertEquals("nudge-nuke-code.png", QrShare.safeFileName("nudge-nuke-code.png"))
        assertEquals("Code.png", QrShare.safeFileName("Code.PNG"))
    }

    @Test
    fun `spaces and punctuation become underscores`() {
        assertEquals("Nuke_code_2026.png", QrShare.safeFileName("Nuke code 2026"))
    }

    @Test
    fun `a path cannot escape the share directory`() {
        assertEquals("databases_nudge.db.png", QrShare.safeFileName("../../databases/nudge.db"))
    }

    @Test
    fun `nothing usable falls back to the default name`() {
        assertEquals(QrShare.FALLBACK_FILE_NAME, QrShare.safeFileName(""))
        assertEquals(QrShare.FALLBACK_FILE_NAME, QrShare.safeFileName("   "))
        assertEquals(QrShare.FALLBACK_FILE_NAME, QrShare.safeFileName("///"))
        assertEquals(QrShare.FALLBACK_FILE_NAME, QrShare.safeFileName("☕☕"))
        assertEquals(QrShare.FALLBACK_FILE_NAME, QrShare.safeFileName(".png"))
    }

    /** Class invariant over hostile names: always one safe segment ending in .png, bounded length. */
    @Test
    fun `every result is a single safe path segment`() {
        val hostile = listOf(
            "..", ".", "/etc/passwd", "a/b/c", "a\\b", "\u0000null", ".hidden", "-dash", "_under",
            "x".repeat(500), "name.", "name..png", "日本語.png", "  spaced  ", "a:b*c?d\"e<f>g|h"
        )
        val safe = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*\\.png$")
        for (name in hostile) {
            val result = QrShare.safeFileName(name)
            assertTrue("'$name' -> '$result'", safe.matches(result))
            assertTrue("'$name' -> '$result' too long", result.length <= 68)
        }
    }
}
