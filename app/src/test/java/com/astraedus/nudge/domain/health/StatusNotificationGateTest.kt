package com.astraedus.nudge.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer **L1, pure JVM unit**: a decision over values, with no Android type anywhere near it.
 *
 * The bug this pins ([#63](https://github.com/astraedus/nudge/issues/63)) is not a wrong value on a
 * screen — it is a call being made when it should not be, 267 times a night. That is still a
 * decision ("does this need posting?"), so it was extracted into [StatusNotificationGate] and
 * tested here rather than answered with another source-grep. `MonitorServiceContractTest` holds the
 * other half — that `NudgeMonitorService` actually consults it — because nothing on the JVM can see
 * a `NotificationManager` call.
 *
 * Every test below counts POSTS, which is the number the reporter measured with BetterBatteryStats.
 * A test asserting "the gate returned false" would pass on a build that ignored the gate.
 */
class StatusNotificationGateTest {

    private val active = ServiceHealth.ACTIVE.notificationCopy()
    private val stopped = ServiceHealth.STOPPED_BY_SYSTEM.notificationCopy()
    private val missing = ServiceHealth.PERMISSION_MISSING.notificationCopy()

    /**
     * The per-evaluation decision `NudgeMonitorService.publishHealth` makes, behind one seam so the
     * SAME driver can be run over the fixed rule and the pre-fix one. Without that, a
     * "counterfactual" is just a second hand-written number that agrees with the first.
     */
    private interface PostRule {
        fun shouldPost(copy: ServiceHealthCopy): Boolean
        fun onPosted(copy: ServiceHealthCopy)
    }

    private fun gated(): PostRule = object : PostRule {
        private val gate = StatusNotificationGate()
        override fun shouldPost(copy: ServiceHealthCopy) = gate.shouldPost(copy)
        override fun onPosted(copy: ServiceHealthCopy) = gate.onPosted(copy)
    }

    /** The rule as it shipped up to v1.18: build and `notify()` every tick, no question asked. */
    private fun preFix(): PostRule = object : PostRule {
        override fun shouldPost(copy: ServiceHealthCopy) = true
        override fun onPosted(copy: ServiceHealthCopy) = Unit
    }

    /** Runs a sequence of evaluations through [rule] and returns how many posts it produced. */
    private fun postsFor(rule: PostRule, vararg health: ServiceHealthCopy): Int {
        var posts = 0
        health.forEach { copy ->
            if (rule.shouldPost(copy)) {
                posts++
                rule.onPosted(copy)
            }
        }
        return posts
    }

    // --- The headline number ---------------------------------------------------------------------

    /**
     * The whole of the fix, as a number. 120 evaluations is what the old 30-second timer produced
     * in an hour; the phone was asleep for all of them and nothing had changed.
     */
    @Test
    fun `an hour of ticks with unchanged state posts nothing after the first`() {
        val rule = gated()

        val first = postsFor(rule, active)
        val rest = postsFor(rule, *Array(120) { active })

        assertEquals("the first evaluation has to put something on screen", 1, first)
        assertEquals(
            "120 unchanged evaluations must post ZERO times. Each post takes the " +
                "NotificationManagerService:post wakelock and breaks Deep Doze, for a " +
                "notification whose text is identical to the one already on screen.",
            0,
            rest
        )
    }

    /**
     * **The counterfactual**, over the same driver and the same sequence: the rule the service
     * actually shipped with posts once per evaluation. 121 against 1. Without this the zero above
     * could be measuring a driver that never posts at all.
     */
    @Test
    fun `counterfactual - the pre-fix rule posts on every one of those ticks`() {
        val sequence = Array(121) { active }

        assertEquals(
            "the pre-fix rule posted once per tick — that IS the defect, 267 of them in 10h24m " +
                "on the reporter's phone",
            121,
            postsFor(preFix(), *sequence)
        )
        assertEquals(
            "and the gated rule collapses the identical sequence to the one post that changed " +
                "something",
            1,
            postsFor(gated(), *sequence)
        )
    }

    // --- A real change still gets through --------------------------------------------------------

    /** A gate that never posts would ace the test above and reintroduce the original 2026-09-07 bug. */
    @Test
    fun `a state flip posts exactly once, then goes quiet again`() {
        val rule = gated()
        postsFor(rule, active)

        assertEquals(
            "the user has to be told blocking stopped - that is what this notification is FOR",
            1,
            postsFor(rule, stopped)
        )
        assertEquals(
            "and then it must go quiet again while the fault persists",
            0,
            postsFor(rule, *Array(50) { stopped })
        )
    }

    @Test
    fun `a flip back to healthy posts once more`() {
        assertEquals(
            "down, up, down: four distinct states in the run, four posts, nothing in between",
            4,
            postsFor(gated(), active, active, stopped, stopped, stopped, active, active, stopped)
        )
    }

    /**
     * Two different degraded states have different copy and different recoveries, so moving
     * between them is a change the user needs to see.
     */
    @Test
    fun `moving between two different faults posts, because the words differ`() {
        val rule = gated()
        postsFor(rule, missing)

        assertEquals(1, postsFor(rule, stopped))
        assertEquals(1, postsFor(rule, missing))
    }

    // --- Why the fingerprint is the copy ---------------------------------------------------------

    /**
     * The gate keys on what reaches the SCREEN, so two health values that happen to share copy are
     * one state as far as posting is concerned. Keying on the enum instead would post here for no
     * visible difference — and, worse, would miss a change if copy ever became dynamic.
     */
    @Test
    fun `two health states with identical copy count as no change`() {
        val rule = gated()
        val copy = ServiceHealthCopy("Nudge is active", "Blocking is on")
        val sameWordsDifferentInstance = ServiceHealthCopy("Nudge is active", "Blocking is on")

        assertEquals(1, postsFor(rule, copy))
        assertEquals(
            "identical words are identical to the user, whatever produced them",
            0,
            postsFor(rule, sameWordsDifferentInstance)
        )
    }

    /** The join character has to be one no title or body can contain, or two pairs can collide. */
    @Test
    fun `a title-body split cannot be faked by concatenation`() {
        val gate = StatusNotificationGate()
        gate.onPosted(ServiceHealthCopy("Nudge", "is active"))

        assertTrue(
            "\"Nudge\"/\"is active\" and \"Nudge is\"/\"active\" are different notifications and " +
                "must not fingerprint the same",
            gate.shouldPost(ServiceHealthCopy("Nudge is", "active"))
        )
    }

    // --- The failed-post case --------------------------------------------------------------------

    /**
     * `shouldPost` records nothing. The service records with `onPosted` only AFTER the platform
     * accepted the post, so a `notify()` that threw (a revoked `POST_NOTIFICATIONS` grant on some
     * OEM builds) leaves the gate still asking for it on the next evaluation, instead of believing
     * a notification is on screen that never arrived.
     */
    @Test
    fun `a post that never happened is retried`() {
        val gate = StatusNotificationGate()

        assertTrue(gate.shouldPost(active))
        assertTrue("asking twice must not be mistaken for posting once", gate.shouldPost(active))

        gate.onPosted(active)
        assertFalse(gate.shouldPost(active))
    }

    /**
     * A user swiping the ongoing notification away on Android 14+ is answered with silence until
     * something genuinely changes. This is the deliberate half of the reporter's second complaint
     * ("swipe it away and it is back within seconds"), so it is pinned rather than left to a
     * comment.
     */
    @Test
    fun `nothing in the gate can resurrect a dismissed notification on its own`() {
        val rule = gated()
        postsFor(rule, active)

        assertEquals(
            "a dismissal is invisible to us and must stay that way - re-posting would be the " +
                "every-few-seconds resurrection the bug report described",
            0,
            postsFor(rule, *Array(500) { active })
        )
        assertEquals(
            "it comes back on the next real state change, which is when it says something new",
            1,
            postsFor(rule, stopped)
        )
    }
}
