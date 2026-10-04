package com.astraedus.nudge.ui.qr

import com.astraedus.nudge.ui.qr.ScanScreenState.BLOCKED
import com.astraedus.nudge.ui.qr.ScanScreenState.CANCELLED
import com.astraedus.nudge.ui.qr.ScanScreenState.RATIONALE
import com.astraedus.nudge.ui.qr.ScanScreenState.REQUESTING
import com.astraedus.nudge.ui.qr.ScanScreenState.SCANNING
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPermissionFlowTest {

    @Test
    fun `opening the scanner`() {
        assertEquals(SCANNING, CameraPermissionFlow.initial(granted = true, shouldShowRationale = false))
        assertEquals(SCANNING, CameraPermissionFlow.initial(granted = true, shouldShowRationale = true))
        assertEquals(REQUESTING, CameraPermissionFlow.initial(granted = false, shouldShowRationale = false))
        assertEquals(RATIONALE, CameraPermissionFlow.initial(granted = false, shouldShowRationale = true))
    }

    @Test
    fun `granted in the dialog starts scanning`() {
        assertEquals(SCANNING, CameraPermissionFlow.afterRequest(granted = true, shouldShowRationale = false))
    }

    @Test
    fun `an ordinary no returns null to the caller`() {
        assertEquals(CANCELLED, CameraPermissionFlow.afterRequest(granted = false, shouldShowRationale = true))
    }

    /**
     * The case that matters: the camera was already off for good, so the request returns "denied"
     * instantly with no dialog. Closing the scanner there would look like a crash; the user gets
     * the Settings explanation instead.
     */
    @Test
    fun `a no the system will not ask about again explains Settings instead of closing`() {
        assertEquals(BLOCKED, CameraPermissionFlow.afterRequest(granted = false, shouldShowRationale = false))
    }

    @Test
    fun `granting in Settings and coming back starts scanning`() {
        assertEquals(SCANNING, CameraPermissionFlow.onResume(BLOCKED, granted = true))
        assertEquals(SCANNING, CameraPermissionFlow.onResume(RATIONALE, granted = true))
    }

    @Test
    fun `coming back without a grant changes nothing`() {
        for (state in ScanScreenState.entries) {
            assertEquals(state, CameraPermissionFlow.onResume(state, granted = false))
        }
    }

    @Test
    fun `a resume while the dialog answer is in flight does not pre-empt it`() {
        assertEquals(REQUESTING, CameraPermissionFlow.onResume(REQUESTING, granted = true))
    }
}
