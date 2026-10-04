package com.astraedus.nudge.domain.nuke

import com.astraedus.nudge.domain.lock.StrictModeEscapeGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L1: what is nuked, what can never be, when Nuke can be armed, and which changes weaken it.
 */
class NukePolicyTest {

    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val calculator = "com.android.calculator2"
    private val key = NukeKey.hashOrNull("the-key")

    private fun state(
        active: Boolean = true,
        packages: Set<String> = setOf(instagram, youtube),
        keyHash: String? = key
    ) = NukeState(active = active, packages = packages, keyHash = keyHash)

    // --- the enforcement question ------------------------------------------------------------

    @Test
    fun `a listed app is nuked while Nuke is on`() {
        assertTrue(NukePolicy.isNuked(instagram, state(), emptySet()))
        assertTrue(NukePolicy.isNuked(youtube, state(), emptySet()))
    }

    @Test
    fun `an app that is not on the list is never nuked`() {
        assertFalse(NukePolicy.isNuked(calculator, state(), emptySet()))
    }

    @Test
    fun `nothing is nuked while Nuke is off`() {
        assertFalse(NukePolicy.isNuked(instagram, state(active = false), emptySet()))
        assertFalse(NukePolicy.isNuked(youtube, state(active = false), emptySet()))
    }

    @Test
    fun `a blank package is never nuked`() {
        assertFalse(NukePolicy.isNuked("", state(packages = setOf("")), emptySet()))
    }

    // --- the safety floor --------------------------------------------------------------------

    @Test
    fun `no static floor package is ever nuked, even when stored and Nuke is on`() {
        val everything = state(packages = NukePolicy.STATIC_FLOOR)
        NukePolicy.STATIC_FLOOR.forEach { pkg ->
            assertFalse("$pkg must never be nuked", NukePolicy.isNuked(pkg, everything, emptySet()))
        }
        assertTrue(NukePolicy.enforceable(NukePolicy.STATIC_FLOOR, emptySet()).isEmpty())
    }

    @Test
    fun `the floor covers the phone, the launcher, system UI, settings and keyboards`() {
        listOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.inputmethod.latin",
            "android"
        ).forEach { assertTrue("$it must be on the floor", it in NukePolicy.STATIC_FLOOR) }
        // Every OS surface Nudge can be switched off from is on the floor too.
        assertTrue(NukePolicy.STATIC_FLOOR.containsAll(StrictModeEscapeGuard.SETTINGS_PACKAGES))
    }

    @Test
    fun `this device's launcher, dialer and keyboard are protected like the static ones`() {
        val oemLauncher = "com.oem.home"
        val thirdPartyKeyboard = "org.futo.inputmethod.latin"
        val nudge = "dev.astraedus.nudge"
        val deviceFloor = setOf(oemLauncher, thirdPartyKeyboard, nudge)
        val stored = state(packages = setOf(oemLauncher, thirdPartyKeyboard, nudge, instagram))

        assertFalse(NukePolicy.isNuked(oemLauncher, stored, deviceFloor))
        assertFalse(NukePolicy.isNuked(thirdPartyKeyboard, stored, deviceFloor))
        assertFalse(NukePolicy.isNuked(nudge, stored, deviceFloor))
        assertTrue(NukePolicy.isNuked(instagram, stored, deviceFloor))
        assertEquals(setOf(instagram), NukePolicy.enforceable(stored.packages, deviceFloor))
    }

    // --- arming ------------------------------------------------------------------------------

    @Test
    fun `arming needs a key and a non-empty list`() {
        assertNull(NukePolicy.armBlocker(state(active = false), emptySet()))
        assertTrue(NukePolicy.canArm(state(active = false), emptySet()))
    }

    @Test
    fun `no key, no Nuke`() {
        assertEquals(
            NukePolicy.ArmBlocker.NO_KEY,
            NukePolicy.armBlocker(state(active = false, keyHash = null), emptySet())
        )
    }

    @Test
    fun `an empty list cannot be armed`() {
        assertEquals(
            NukePolicy.ArmBlocker.EMPTY_LIST,
            NukePolicy.armBlocker(state(active = false, packages = emptySet()), emptySet())
        )
    }

    @Test
    fun `a list of only floor packages counts as empty`() {
        val onlyFloor = state(active = false, packages = setOf("com.android.dialer", "com.oem.home"))
        assertEquals(
            NukePolicy.ArmBlocker.EMPTY_LIST,
            NukePolicy.armBlocker(onlyFloor, setOf("com.oem.home"))
        )
    }

    // --- weakening, both directions -----------------------------------------------------------

    @Test
    fun `nothing weakens Nuke while it is off`() {
        val off = state(active = false)
        assertFalse(NukePolicy.isWeakening(off, off.copy(packages = emptySet())))
        assertFalse(NukePolicy.isWeakening(off, off.copy(keyHash = null)))
        assertFalse(NukePolicy.isWeakening(off, off.copy(keyHash = NukeKey.hashOrNull("other"))))
        assertFalse(NukePolicy.isWeakening(off, off))
    }

    @Test
    fun `turning Nuke off weakens it, turning it on does not`() {
        assertTrue(NukePolicy.isWeakening(state(active = true), state(active = false)))
        assertFalse(NukePolicy.isWeakening(state(active = false), state(active = true)))
    }

    @Test
    fun `removing an app while on weakens, adding one does not`() {
        val on = state()
        assertTrue(NukePolicy.isWeakening(on, on.copy(packages = setOf(instagram))))
        assertTrue(NukePolicy.isWeakening(on, on.copy(packages = emptySet())))
        assertFalse(NukePolicy.isWeakening(on, on.copy(packages = on.packages + calculator)))
    }

    @Test
    fun `swapping one app for another while on still weakens`() {
        val on = state()
        assertTrue(NukePolicy.isWeakening(on, on.copy(packages = setOf(instagram, calculator))))
    }

    @Test
    fun `changing or removing the key while on weakens`() {
        val on = state()
        assertTrue(NukePolicy.isWeakening(on, on.copy(keyHash = NukeKey.hashOrNull("new key"))))
        assertTrue(NukePolicy.isWeakening(on, on.copy(keyHash = null)))
    }

    @Test
    fun `an unchanged state is not weakening`() {
        assertFalse(NukePolicy.isWeakening(state(), state()))
    }

    @Test
    fun `the master toggle costs the key only while Nuke is on`() {
        assertTrue(NukePolicy.masterToggleOffRequiresUnlock(state(active = true)))
        assertFalse(NukePolicy.masterToggleOffRequiresUnlock(state(active = false)))
    }
}
