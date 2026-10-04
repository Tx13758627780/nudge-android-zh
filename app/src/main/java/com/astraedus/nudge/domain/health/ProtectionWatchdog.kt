package com.astraedus.nudge.domain.health

/**
 * What has gone wrong, when something has. Each one has a DIFFERENT user recovery, which is the
 * whole reason they are separate values rather than one "protection is off" flag.
 */
enum class ProtectionFault {
    /**
     * Our service is not listed in `ENABLED_ACCESSIBILITY_SERVICES`. The user turned it off, or the
     * app was force-stopped (per AOSP the one event that actively strips the component). Recovery:
     * turn it on.
     */
    ACCESSIBILITY_DISABLED,

    /**
     * Our service is still LISTED as enabled but is not bound — the system killed its process and,
     * per AOSP, will never rebind it. Blocking is permanently dead while the toggle still reads
     * "on", in the system's own Accessibility screen as well as ours.
     *
     * Recovery is specifically **off and on again** (or a reboot): AOSP's `mCrashedServices` set is
     * cleared by a user toggle, an app update, an uninstall or a force-stop, and nothing else — its
     * own comment says the set exists so "users may toggle the on/off switch to retry". Telling
     * this user to "turn it on" would be useless: from where they are standing it already is on.
     */
    ACCESSIBILITY_CRASHED,

    /**
     * The foreground service is not running. Blocking still works (the accessibility binding is
     * what enforces), so this is not "protection has stopped" — it is the loss of the process
     * priority that keeps that binding alive, which matters most overnight, when the binding's own
     * `BIND_FOREGROUND_SERVICE_WHILE_AWAKE` protection has lapsed. We can restart it ourselves, so
     * it is only worth a notification once a restart has demonstrably failed to hold.
     */
    MONITOR_SERVICE_DEAD,

    /**
     * We tried to restart the foreground service and the platform refused the start outright.
     *
     * Since Android 12 an app may not start a foreground service from the background, and a
     * watchdog is a background caller by definition. The documented exemption list does not
     * include a bound accessibility service and does not include a WorkManager expedited job, so
     * neither of the two things Nudge actually runs in the background qualifies on its own: the
     * `SYSTEM_ALERT_WINDOW` ("display over other apps") grant is the only thing that has ever made
     * this start legal. Onboarding lets that permission be skipped, and on such a phone EVERY
     * restart we attempt is denied - the service stays dead, the watchdog cannot heal it, and the
     * only trace is a `system_server_wtf` entry in dropbox that no user will ever read. That
     * silence was the whole of GitHub issue #62; the caught exception was correct, and nothing
     * carried the refusal any further.
     *
     * **This gets more common, not less.** Android 16 narrows the `SYSTEM_ALERT_WINDOW` exemption
     * to apps that currently have a VISIBLE overlay window, so holding the permission stops being
     * sufficient there. A fault that was a misconfigured minority is on its way to being the
     * ordinary case, which is the argument for surfacing it at all rather than logging it. There
     * is also no API that can ask in advance whether a start would be allowed, so attempt and
     * catch is the only shape this can take, and this value is what carries the answer onward.
     *
     * Kept separate from [MONITOR_SERVICE_DEAD] for the usual reason: the recovery differs. That
     * fault sends the user to their phone's battery and autostart settings, which would do nothing
     * here. This one has a fix the user can actually perform, so it supersedes it on the cycle
     * where both are true.
     */
    MONITOR_START_BLOCKED
}

/** Everything the watchdog is allowed to look at, gathered by the caller. */
data class ProtectionSnapshot(
    /** The master toggle. False means the user deliberately turned monitoring off. */
    val globalEnabled: Boolean,
    /** INTENT: our component is in `ENABLED_ACCESSIBILITY_SERVICES`. Survives a crash. */
    val accessibilityGranted: Boolean,
    /** REALITY: our component is in the system's BOUND services list. Does not survive a crash. */
    val accessibilityConnected: Boolean,
    val monitorServiceRunning: Boolean,
    /** Was the previous check already degraded? Persisted, so it survives a process kill. */
    val wasDegradedLastCheck: Boolean,
    /** Epoch millis of the last alert we posted; 0 when we have never posted one. */
    val lastNotifiedAtMs: Long,
    val nowMs: Long
)

/** The whole of what the worker is allowed to do, decided in one place. */
data class WatchdogDecision(
    val startMonitorService: Boolean,
    /** Non-null means post this alert now. Null means stay quiet. */
    val notifyOf: ProtectionFault?,
    val dismissNotification: Boolean,
    /** Persisted for the next run's [ProtectionSnapshot.wasDegradedLastCheck]. */
    val degradedNow: Boolean
)

/**
 * Decides, from one snapshot, whether protection has silently died and whether to say so.
 *
 * Pure on purpose. The failure this exists for — a phone quietly killing Nudge at 3am and the OS
 * never rebinding it — is not reproducible in a test on a device, so the decision has to be a
 * function of values a JVM test can hand it. `ProtectionWatchdogWorker` gathers the values and
 * carries out the verdict; it holds no policy of its own.
 *
 * Three rules the copy depends on:
 *
 * - **Never nag a user who opted out.** A false "Nudge has stopped blocking" for someone who turned
 *   the master toggle off themselves is the fastest way to teach people to swipe our notifications
 *   away, which would cost us the one channel that reaches them when it is real.
 * - **Say only what is true.** A dead foreground service is not stopped blocking, so it does not
 *   claim to be, and it gets fixed silently first.
 * - **Name the right recovery.** "Enabled but dead" and "not enabled" look identical to the user
 *   and need opposite instructions, so they stay separate faults all the way to the copy.
 */
object ProtectionWatchdog {

    /**
     * Minimum gap between two alerts. The OEM research is explicit about this: a phone that keeps
     * killing us would otherwise produce an alert every 15 minutes for as long as it stays broken,
     * and a notification the user learns to dismiss is worth less than no notification at all.
     */
    const val NOTIFICATION_COOLDOWN_MS: Long = 12L * 60L * 60L * 1000L

    fun decide(snapshot: ProtectionSnapshot): WatchdogDecision {
        // The user turned monitoring off. Nothing here is a fault, and a stale alert left in the
        // shade from before they turned it off would read as one.
        if (!snapshot.globalEnabled) return quiet()

        val fault = when {
            !snapshot.accessibilityGranted -> ProtectionFault.ACCESSIBILITY_DISABLED
            !snapshot.accessibilityConnected -> ProtectionFault.ACCESSIBILITY_CRASHED
            !snapshot.monitorServiceRunning -> ProtectionFault.MONITOR_SERVICE_DEAD
            else -> null
        } ?: return quiet()

        // Worth doing whichever fault fired: a dead foreground service is both a fault in its own
        // right and the process priority the accessibility binding wants back overnight.
        val startMonitorService = !snapshot.monitorServiceRunning

        val worthSaying = when (fault) {
            // Unambiguous — AOSP only drops the component on a force-stop or a user toggle — and we
            // cannot re-grant it, so waiting a cycle buys nothing and costs the user 15 more
            // minutes of unblocked scrolling.
            ProtectionFault.ACCESSIBILITY_DISABLED -> true

            // Granted-but-not-bound is normally permanent (AOSP never retries a crashed service),
            // but it is ALSO what a service legitimately mid-bind looks like: `mBindingServices`
            // is not `mBoundServices`, so a check that lands in the seconds after a boot or an app
            // update sees the same thing. One confirming cycle separates the two at a cost of 15
            // minutes on a failure that otherwise lasts all night — worth it, because a false
            // "your phone broke Nudge" is exactly the alert that gets the channel muted.
            ProtectionFault.ACCESSIBILITY_CRASHED -> snapshot.wasDegradedLastCheck

            // We restarted it last cycle and it is dead again, so the restart did not hold —
            // that is a phone actively shutting us down, which the user can act on.
            ProtectionFault.MONITOR_SERVICE_DEAD -> snapshot.wasDegradedLastCheck

            // Unreachable from here, and deliberately spelled out rather than swept into an
            // `else`. Whether the platform will REFUSE a restart is only knowable once one has
            // been attempted, which is [faultToReport]'s half of the policy; nothing in a
            // snapshot can imply it. Keeping the `when` exhaustive is what forces the next fault
            // anyone adds to be thought about here too, instead of defaulting to silence.
            ProtectionFault.MONITOR_START_BLOCKED -> false
        }

        return WatchdogDecision(
            startMonitorService = startMonitorService,
            notifyOf = if (worthSaying && cooledDown(snapshot)) fault else null,
            dismissNotification = false,
            degradedNow = true
        )
    }

    /**
     * The fault to actually report, once the verdict from [decide] has been carried out.
     *
     * [decide] cannot answer this on its own. Whether the platform will let us restart the
     * foreground service is only knowable AFTER `NudgeMonitorService.start()` has been attempted,
     * and the attempt is the caller's to make - so the refusal comes back here as a second pure
     * step rather than as a signal in [ProtectionSnapshot] that nothing could have filled in yet.
     * Keeping it pure keeps every rule below in one unit-tested place; the caller stays a carrier
     * of verdicts with no policy of its own.
     *
     * The rules, each with the reason it exists:
     *
     * - **Nothing was refused: the verdict stands unchanged.** This function is on the path of
     *   every single check, so the no-refusal case has to be byte-identical to the behaviour that
     *   shipped before it existed, or a fix for a permission almost nobody lacks would have
     *   rewritten the alert everyone else gets.
     * - **Never nag a user who opted out.** The master toggle being off means they chose this, and
     *   [decide] already refuses to speak for them; a refusal must not become a back door around
     *   that rule.
     * - **An accessibility fault outranks the refusal.** If the verdict was
     *   [ProtectionFault.ACCESSIBILITY_DISABLED] or [ProtectionFault.ACCESSIBILITY_CRASHED] then
     *   blocking is ACTUALLY dead, which is strictly worse than having lost the process priority
     *   that protects it, and each of those recoveries (re-grant, off-and-on again) brings the
     *   service back with it anyway.
     * - **One confirming cycle, exactly as [ProtectionFault.MONITOR_SERVICE_DEAD] has.**
     *   `NudgeMonitorService.isRunning` is an in-process flag, so the FIRST check after ANY process
     *   start reports the service dead and attempts a start. Alerting on that single sighting would
     *   fire on every boot, on every app update, and on every time the OS restarted us.
     * - **The same 12-hour cooldown.** A phone in this state refuses every restart, so without the
     *   shared clock this would be an alert every 15 minutes forever - and a notification the user
     *   learns to swipe away is worth less than none. One clock, one persisted timestamp: a second
     *   one would drift out of step with the first and quietly double the noise.
     */
    fun faultToReport(
        snapshot: ProtectionSnapshot,
        decision: WatchdogDecision,
        startRefused: Boolean
    ): ProtectionFault? {
        if (!startRefused) return decision.notifyOf
        if (!snapshot.globalEnabled) return null

        when (decision.notifyOf) {
            ProtectionFault.ACCESSIBILITY_DISABLED,
            ProtectionFault.ACCESSIBILITY_CRASHED -> return decision.notifyOf

            else -> Unit
        }

        if (!snapshot.wasDegradedLastCheck) return null
        if (!cooledDown(snapshot)) return null

        return ProtectionFault.MONITOR_START_BLOCKED
    }

    private fun quiet() = WatchdogDecision(
        startMonitorService = false,
        notifyOf = null,
        dismissNotification = true,
        degradedNow = false
    )

    private fun cooledDown(snapshot: ProtectionSnapshot): Boolean {
        if (snapshot.lastNotifiedAtMs <= 0L) return true
        // A clock moved backwards (timezone change, NTP correction, the user setting the date)
        // must not mute the alert until wall-clock catches up. Treat it as a reset.
        if (snapshot.nowMs < snapshot.lastNotifiedAtMs) return true
        return snapshot.nowMs - snapshot.lastNotifiedAtMs >= NOTIFICATION_COOLDOWN_MS
    }
}
