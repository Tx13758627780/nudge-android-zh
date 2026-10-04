package com.astraedus.nudge.domain.health

/**
 * Decides whether the ongoing "Nudge is active" notification actually needs posting again.
 *
 * ## The defect this exists for ([#63](https://github.com/astraedus/nudge/issues/63))
 *
 * A user on a Pixel 10 Pro read their own battery with BetterBatteryStats and found Nudge holding
 * the `NotificationManagerService:post:dev.astraedus.nudge` wakelock **267 times in 10h24m** — once
 * every 2.3 minutes, all night, keeping the phone out of Deep Doze for ~5 minutes of CPU time that
 * bought nothing. They also noticed the tell that names the cause exactly: swipe the permanent
 * notification away and it is back within seconds, with identical text.
 *
 * `NudgeMonitorService` re-posted that notification on **every health poll tick**, unconditionally,
 * whether or not a single character of it had changed. There is no platform-side dedup to save us
 * from that: `NotificationManagerService.enqueueNotificationInternal` takes its post wakelock before
 * it has looked at the content, so a byte-identical re-post costs exactly what a real one costs. The
 * only thing that can tell "nothing changed" is us.
 *
 * So this holds the fingerprint of what is currently on the user's screen, and the service posts
 * only when the next fingerprint differs. **A tick that changes nothing must cost nothing.**
 *
 * ## Why the fingerprint is the COPY and not the [ServiceHealth]
 *
 * What the user sees is the notification's text, and the mapping from health to text is
 * [notificationCopy], which is allowed to change. Fingerprinting the enum would go wrong the first
 * time two states share copy (a pointless post) or one state's copy becomes dynamic (a missed one).
 * The rule is about the SCREEN, so it is keyed on what reaches the screen.
 *
 * ## Why it is a small mutable object and not a pure function
 *
 * "Has this changed since last time" needs a last time. Keeping it here, with no Android types, is
 * what makes the counterfactual in `StatusNotificationGateTest` possible: N ticks of unchanged
 * state through the gate produce 0 posts, the same N ticks without it produce N.
 *
 * Deliberately NOT persisted. A new process re-posts through `startForeground` regardless — the
 * platform requires it — so a remembered fingerprint from a dead process could only ever suppress a
 * post that has to happen anyway.
 */
class StatusNotificationGate {

    private var lastPosted: String? = null

    /**
     * True when [copy] differs from what was last posted, i.e. when a post would actually change
     * something the user can see.
     *
     * A query, not a command: it records nothing. The caller records with [onPosted] **after** the
     * post has succeeded, so a `notify()` that throws (a revoked `POST_NOTIFICATIONS` grant on some
     * OEM builds) does not leave this believing something is on screen that is not.
     */
    fun shouldPost(copy: ServiceHealthCopy): Boolean = fingerprint(copy) != lastPosted

    /** Records what is now on screen. Call after every successful post, including `startForeground`. */
    fun onPosted(copy: ServiceHealthCopy) {
        lastPosted = fingerprint(copy)
    }

    // Deliberately no `reset()`. The only event that takes this notification down without us
    // asking is a user swipe on Android 14+, and the answer to that is silence until the state
    // genuinely changes — see the class doc on `NudgeMonitorService`. A service teardown does not
    // need one either: this object dies with the service, and the next process re-posts through
    // `startForeground` regardless.

    private companion object {
        /**
         * The separator is NUL, written as an escape. It cannot appear in either field, so
         * no title/body pair can collide with another by splitting differently across the
         * join. Write it escaped, never as a raw control byte: a literal NUL in a source
         * file makes the whole file read as binary and costs every future diff.
         */
        fun fingerprint(copy: ServiceHealthCopy): String = "${copy.title}\u0000${copy.body}"
    }
}
