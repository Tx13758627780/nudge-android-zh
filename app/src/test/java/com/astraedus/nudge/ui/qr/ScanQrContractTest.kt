package com.astraedus.nudge.ui.qr

import android.app.Activity
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The contract's result mapping: text only on RESULT_OK with a non-empty payload, null otherwise. */
class ScanQrContractTest {

    private val contract = ScanQrContract()

    private fun resultIntent(text: String?): Intent = mockk {
        every { getStringExtra(QrScanActivity.EXTRA_RESULT) } returns text
    }

    @Test
    fun `ok with a payload returns the payload`() {
        assertEquals("nudge-nuke:abc", contract.parseResult(Activity.RESULT_OK, resultIntent("nudge-nuke:abc")))
    }

    @Test
    fun `cancel is null even if an intent carries text`() {
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, resultIntent("nudge-nuke:abc")))
    }

    @Test
    fun `ok with no intent, no extra or an empty extra is null`() {
        assertNull(contract.parseResult(Activity.RESULT_OK, null))
        assertNull(contract.parseResult(Activity.RESULT_OK, resultIntent(null)))
        assertNull(contract.parseResult(Activity.RESULT_OK, resultIntent("")))
    }
}
