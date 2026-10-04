package com.astraedus.nudge

import kotlinx.coroutines.test.runTest

/**
 * Takes an unhandled coroutine exception back out of the JVM-wide queue a test deliberately put
 * it in, so the NEXT test in the fork does not inherit it.
 *
 * ## The defect this exists for ([#53](https://github.com/astraedus/nudge/issues/53))
 *
 * `kotlinx-coroutines-test` registers `ExceptionCollector` as a **process-global**
 * `CoroutineExceptionHandler` service. Once any `runTest` has run in a fork it stays armed for the
 * life of that JVM, and from then on every coroutine exception that reaches
 * `handleCoroutineException` with no handler in its own context is added to a static list. When no
 * `runTest` is active there is nobody to hand it to, so it simply waits there — and the next
 * `runTest` to start, **anywhere in the fork**, throws `UncaughtExceptionsBeforeTest` at its first
 * line and fails a test that had nothing to do with it.
 *
 * That is a time bomb with a random victim: which test detonates it depends only on Gradle's class
 * order, which differs between the debug and release variants and between machines. `v1.18.0`
 * failed exactly one test on the tag build — `InterventionsViewModelTest` — on the identical commit
 * that had passed minutes earlier, while the leak was in `CrashSafeScopeTest`, three packages away.
 *
 * ## Why not "stop leaking"
 *
 * The leak is the point in the one place that does it. `CrashSafeScopeTest`'s counterfactual claims
 * that a bare `SupervisorJob()` scope lets a throwable reach the thread's default uncaught handler —
 * the path that kills the process and with it the accessibility service — and there is no way to
 * demonstrate that without the throwable actually travelling the global unhandled path. So the
 * exception must escape; it must just not still be lying there afterwards.
 *
 * ## The rule
 *
 * **A test that deliberately lets a coroutine exception go unhandled owns the cleanup.** Call
 * [drain] after it, ideally from `@After` so a later test added to the same class cannot forget.
 * `LeakedCoroutineExceptionsContractTest` pins the pairing.
 */
object LeakedCoroutineExceptions {

    /**
     * kotlinx's own internal signal. Matched by name rather than caught by type: the class is
     * `internal` to `kotlinx-coroutines-test`, and catching it by name means a visibility change
     * upstream cannot break the build.
     */
    private const val UNCAUGHT_BEFORE_TEST = "kotlinx.coroutines.test.UncaughtExceptionsBeforeTest"

    /**
     * Empties the collector, and says whether anything was in it.
     *
     * An empty `runTest` is the only public API that touches the queue: `TestScope.enter()` flushes
     * it and rethrows as `UncaughtExceptionsBeforeTest`, which is precisely the failure we are
     * intercepting here instead of letting an unrelated test take it.
     *
     * Calling this **before** a deliberate leak is useful too: it arms the collector (the first
     * `runTest` in a fork is what enables it), so the leak that follows is queued and therefore
     * observable no matter where the class lands in the run order.
     *
     * @return true if a leaked exception was found and removed.
     */
    fun drain(): Boolean =
        try {
            runTest { }
            false
        } catch (t: Throwable) {
            if (t::class.java.name != UNCAUGHT_BEFORE_TEST) throw t
            true
        }
}
