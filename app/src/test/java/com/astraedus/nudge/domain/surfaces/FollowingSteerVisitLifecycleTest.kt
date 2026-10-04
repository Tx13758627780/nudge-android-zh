package com.astraedus.nudge.domain.surfaces

import com.astraedus.nudge.domain.events.ForegroundSignal
import com.astraedus.nudge.domain.sitting.SittingEvent
import com.astraedus.nudge.domain.sitting.SittingTracker
import com.astraedus.nudge.domain.sitting.endedSitting
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue [#56](https://github.com/astraedus/nudge/issues/56): **the Following steer re-fired after a
 * display timeout, treating a screen blink as a new visit.**
 *
 * The repro, in the reporter's words: open Instagram, get steered to Following, deliberately switch
 * back to the Home feed, read for thirty seconds without touching the screen, let the display time
 * out, tap it back on. Two synthetic taps then dragged the user off the feed they had just chosen —
 * against 1.18.0's own promise, *"Nudge will not drag you out of it again until your next visit."*
 *
 * ## Why the test lives HERE, driving two real objects
 *
 * `docs/testing-strategy.md` rule (a): name the layer. This is **L1**, but not the usual L1 — the
 * defect is invisible to a test of either object alone, and both of them were already exhaustively
 * covered. [FollowingSteer] was correct: it has no opinion about screen-offs at all, and its 26
 * tests still pass on the broken build. [SittingTracker] was correct too, and since
 * [#54](https://github.com/astraedus/nudge/issues/54) it already knew that a blink is not a
 * departure. What was wrong was that **the steer was not asking it.** The service reset the steer
 * from its own `ACTION_SCREEN_OFF` receiver, so the app held two definitions of "the user left" —
 * the same shape as #36, #54 and #64, and the fourth time this subsystem has paid for it.
 *
 * So the unit under test is the WIRE, and the only honest way to test a wire is to put the two real
 * objects at its ends. [route] is that wire, and it is deliberately one line using production's own
 * [endedSitting] accessor — the same one `NudgeAccessibilityService.onSittingEvent` and
 * `PassthroughManager.revokeIfSittingEnded` call. A hand-rolled "did a sitting end" here would be a
 * third opinion, i.e. this bug again, written into its own regression test.
 *
 * L2 (recorded-event replay) would be the better layer and is the one gap: there is no capture of
 * an Instagram steer session under `app/src/test/resources/a11y-captures/`, and recording one needs
 * a bench device with Instagram signed in. Noted in the PR rather than skipped silently.
 */
class FollowingSteerVisitLifecycleTest {

    private val instagram = "com.instagram.android"
    private val chrome = "com.android.chrome"

    /**
     * Read from production, never retyped (rule (b)). The whole fix is that the steer's visit and
     * the sitting's return window are the SAME number, so a literal here could agree with its
     * author while the app used something else.
     */
    private val returnWindow = SittingTracker.PASSTHROUGH_RETURN_WINDOW_MS

    /**
     * The two real objects and the wire between them, exactly as the service wires it.
     *
     * @param resetOnScreenOffBroadcast the PRE-FIX wire, for the counterfactual: the steer used to
     *   be reset from the `ACTION_SCREEN_OFF` receiver itself (via `hideAllOverlays`), before
     *   anything had decided whether the absence was long enough to be a departure.
     */
    private class Phone(private val resetOnScreenOffBroadcast: Boolean = false) {
        val sitting = SittingTracker()
        val steer = FollowingSteer()

        /** THE WIRE. A departure verdict, whatever shape it arrives in, ends the steer's visit. */
        private fun route(event: SittingEvent) {
            if (event.endedSitting != null) steer.reset()
        }

        fun appWindow(packageName: String, nowMs: Long) =
            route(sitting.onSignal(ForegroundSignal.AppWindow(packageName), nowMs))

        fun pressHome(nowMs: Long) =
            route(sitting.onSignal(ForegroundSignal.Home("com.android.launcher"), nowMs))

        fun screenOff(nowMs: Long) {
            route(sitting.onScreenOff(nowMs))
            if (resetOnScreenOffBroadcast) steer.reset()
        }

        fun onHomeFeed(nowMs: Long): SteerAction =
            steer.onObservation(HostSurface.HOME_FEED, inHostApp = true, menuVisible = false, nowMs = nowMs)

        fun onFollowingFeed(nowMs: Long): SteerAction =
            steer.onObservation(HostSurface.FOLLOWING_FEED, inHostApp = true, menuVisible = false, nowMs = nowMs)

        /**
         * Arrive at Instagram and let the steer do its two taps, ending on the Following feed —
         * the state every scenario below starts from.
         */
        fun arriveAndGetSteered(packageName: String, nowMs: Long) {
            appWindow(packageName, nowMs)
            assertEquals(
                "a fresh arrival at the home feed must steer",
                SteerAction.OpenMenu,
                onHomeFeed(nowMs)
            )
            assertEquals(
                SteerAction.ClickFollowing,
                steer.resolvePending(menuVisible = true, nowMs = nowMs + 250L)
            )
            onFollowingFeed(nowMs + 500L)
        }
    }

    // --- the bug -------------------------------------------------------------------------------

    /**
     * THE REPORT. Steered, deliberately back on Home, screen blanks while reading, screen on.
     *
     * The user is in exactly the state 1.18.0 promised to leave alone, and the sitting agrees with
     * them: forty seconds dark is nowhere near the return window, so nothing ended.
     */
    @Test
    fun `a display timeout on the home feed does not re-steer`() {
        val phone = Phone()
        phone.arriveAndGetSteered(instagram, 0L)

        // The deliberate choice this whole feature promises to respect.
        assertEquals(
            "backing out to Home must not re-steer on its own",
            SteerAction.None,
            phone.onHomeFeed(1_000L)
        )

        // Reading, untouched, until Android blanks the display. The Pixel default is 30s.
        phone.screenOff(31_000L)
        // Tapped straight back on, same app still in front.
        phone.appWindow(instagram, 71_000L)

        assertEquals(
            "a screen that blinked for forty seconds is not a new visit — the steer must leave the " +
                "feed the user chose alone",
            SteerAction.None,
            phone.onHomeFeed(71_500L)
        )
    }

    /**
     * The counterfactual rule (d) asks for: the SAME sequence against the pre-fix wire, proving the
     * wire is what is load-bearing rather than something else in the sequence.
     */
    @Test
    fun `counterfactual - resetting from the screen-off broadcast re-steers on the same sequence`() {
        val phone = Phone(resetOnScreenOffBroadcast = true)
        phone.arriveAndGetSteered(instagram, 0L)
        assertEquals(SteerAction.None, phone.onHomeFeed(1_000L))

        phone.screenOff(31_000L)
        phone.appWindow(instagram, 71_000L)

        assertEquals(
            "this is the shipped bug: the broadcast itself cleared the steer's memory, so the very " +
                "next home-feed observation opened the dropdown again",
            SteerAction.OpenMenu,
            phone.onHomeFeed(71_500L)
        )
    }

    // --- what MUST still re-steer -------------------------------------------------------------

    /**
     * The other direction, and the reason the fix routes EVERY end cause through one reset rather
     * than special-casing `SCREEN_OFF` beside the arrival's departure report: going home and coming
     * back is the plainest "next visit" there is, and before this fix nothing reset the steer on it
     * at all.
     */
    @Test
    fun `pressing home and returning is a new visit and steers again`() {
        val phone = Phone()
        phone.arriveAndGetSteered(instagram, 0L)
        assertEquals(SteerAction.None, phone.onHomeFeed(1_000L))

        phone.pressHome(2_000L)
        phone.appWindow(instagram, 3_000L)

        assertEquals(
            "Home is the one unambiguous 'I am leaving' gesture the platform gives us",
            SteerAction.OpenMenu,
            phone.onHomeFeed(3_100L)
        )
    }

    /** A phone genuinely put down and picked up later: past the window, so a fresh visit. */
    @Test
    fun `a screen-off longer than the return window is a new visit and steers again`() {
        val phone = Phone()
        phone.arriveAndGetSteered(instagram, 0L)
        assertEquals(SteerAction.None, phone.onHomeFeed(1_000L))

        phone.screenOff(31_000L)
        val backAfterTheWindow = 31_000L + returnWindow + 1L
        phone.appWindow(instagram, backAfterTheWindow)

        assertEquals(
            "past the same window that already decides whether the delay is owed again, this is a " +
                "new visit by the app's one definition of the word",
            SteerAction.OpenMenu,
            phone.onHomeFeed(backAfterTheWindow + 100L)
        )
    }

    /** The boundary, from the other side: one millisecond short of the window is still one visit. */
    @Test
    fun `a screen-off one millisecond short of the window is still the same visit`() {
        val phone = Phone()
        phone.arriveAndGetSteered(instagram, 0L)
        assertEquals(SteerAction.None, phone.onHomeFeed(1_000L))

        phone.screenOff(31_000L)
        val backJustInside = 31_000L + returnWindow - 1L
        phone.appWindow(instagram, backJustInside)

        assertEquals(SteerAction.None, phone.onHomeFeed(backJustInside + 1L))
    }

    /** A real app switch past the window ends the visit too, by the same one verdict. */
    @Test
    fun `using another app past the window is a new visit and steers again`() {
        val phone = Phone()
        phone.arriveAndGetSteered(instagram, 0L)
        assertEquals(SteerAction.None, phone.onHomeFeed(1_000L))

        phone.appWindow(chrome, 2_000L)
        val backLater = 2_000L + returnWindow + 1L
        phone.appWindow(instagram, backLater)

        assertEquals(SteerAction.OpenMenu, phone.onHomeFeed(backLater + 100L))
    }

    /**
     * And the #28 case that must NOT re-steer: a photo picker, a share sheet or a permission dialog
     * is another app in front for a few seconds. The sitting has always said that is not a
     * departure; now the steer says it too.
     */
    @Test
    fun `a short sub-flow into another app is not a new visit`() {
        val phone = Phone()
        phone.arriveAndGetSteered(instagram, 0L)
        assertEquals(SteerAction.None, phone.onHomeFeed(1_000L))

        phone.appWindow("com.google.android.providers.media.module", 2_000L)
        phone.appWindow(instagram, 9_000L)

        assertEquals(SteerAction.None, phone.onHomeFeed(9_100L))
    }

    /**
     * The steer keeps its OWN in-app rule, which the sitting knows nothing about: another tab is a
     * real navigation away from the feed, and this fix must not have swallowed it.
     */
    @Test
    fun `visiting another tab still ends the visit without any sitting change`() {
        val phone = Phone()
        phone.arriveAndGetSteered(instagram, 0L)

        phone.steer.onObservation(HostSurface.OTHER_TAB, inHostApp = true, menuVisible = false, nowMs = 2_000L)

        assertEquals(
            "Explore, then back to Home, is a fresh arrival — the steer owns this one and the " +
                "sitting never hears about it",
            SteerAction.OpenMenu,
            phone.onHomeFeed(3_000L)
        )
    }
}
