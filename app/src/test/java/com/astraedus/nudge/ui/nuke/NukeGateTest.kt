package com.astraedus.nudge.ui.nuke

import com.astraedus.nudge.domain.nuke.NukeEmergencyCode
import com.astraedus.nudge.domain.nuke.NukeKey
import com.astraedus.nudge.domain.nuke.NukeState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L1: the gate every Nuke-weakening action goes through. Two ways past it (the key, the emergency
 * code), and nothing else runs the stashed action.
 */
class NukeGateTest {

    private val token = NukeKey.generateToken()
    private var state = NukeState(active = true, packages = setOf("a.b"), keyHash = NukeKey.hashOrNull(token))
    private var ran = 0

    private val gate = NukeGate(currentState = { state })

    private suspend fun stash() = gate.run("End Nuke", weaken = true) { ran++ }

    @Test
    fun `a change that does not weaken runs at once with no dialog`() = runTest {
        gate.run("Add an app", weaken = false) { ran++ }
        assertEquals(1, ran)
        assertNull(gate.unlock.value)
    }

    @Test
    fun `a weakening change waits behind the dialog`() = runTest {
        stash()
        assertEquals(0, ran)
        assertEquals("End Nuke", gate.unlock.value?.prompt)
        assertNull(gate.unlock.value?.emergencyTarget)
    }

    @Test
    fun `the paired key runs it`() = runTest {
        stash()
        assertTrue(gate.onScanned(token))
        assertEquals(1, ran)
        assertNull(gate.unlock.value)
    }

    @Test
    fun `any other code does not, and says so`() = runTest {
        stash()
        assertFalse(gate.onScanned("4006381333931"))
        assertEquals(0, ran)
        assertEquals(NukeGate.WRONG_KEY_MESSAGE, gate.unlock.value?.error)
    }

    @Test
    fun `a cancelled scan changes nothing and says nothing`() = runTest {
        stash()
        assertFalse(gate.onScanned(null))
        assertFalse(gate.onScanned(""))
        assertEquals(0, ran)
        assertNull(gate.unlock.value?.error)
        assertNotNull(gate.unlock.value)
    }

    @Test
    fun `the key is read live, so a re-paired key is the one that counts`() = runTest {
        stash()
        val newToken = NukeKey.generateToken()
        state = state.copy(keyHash = NukeKey.hashOrNull(newToken))
        assertFalse(gate.onScanned(token))
        assertTrue(gate.onScanned(newToken))
        assertEquals(1, ran)
    }

    @Test
    fun `the emergency code is 64 fresh characters per attempt`() = runTest {
        stash()
        gate.useEmergencyCode()
        val first = gate.unlock.value?.emergencyTarget!!
        assertEquals(NukeEmergencyCode.LENGTH, first.length)
        gate.backToChoice()
        assertNull(gate.unlock.value?.emergencyTarget)
        gate.useEmergencyCode()
        val second = gate.unlock.value?.emergencyTarget!!
        assertNotEquals(first, second)
    }

    @Test
    fun `the emergency code runs it only when typed exactly`() = runTest {
        stash()
        gate.useEmergencyCode()
        val code = gate.unlock.value?.emergencyTarget!!
        assertFalse(gate.verifyEmergency(code.dropLast(1)))
        assertFalse(gate.verifyEmergency(code.lowercase().takeIf { it != code } ?: "nope"))
        assertEquals(0, ran)
        assertTrue(gate.verifyEmergency(code))
        assertEquals(1, ran)
        assertNull(gate.unlock.value)
    }

    @Test
    fun `the emergency code works without a key being scannable`() = runTest {
        state = state.copy(keyHash = null)
        gate.requireEmergencyCode("End Nuke without your code") { ran++ }
        val code = gate.unlock.value?.emergencyTarget!!
        assertTrue(gate.verifyEmergency(code))
        assertEquals(1, ran)
    }

    @Test
    fun `backing out means the action never runs`() = runTest {
        stash()
        gate.cancel()
        assertNull(gate.unlock.value)
        assertFalse(gate.onScanned(token))
        assertFalse(gate.verifyEmergency("anything"))
        assertEquals(0, ran)
    }
}
