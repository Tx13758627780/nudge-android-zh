package com.astraedus.nudge

import com.astraedus.nudge.util.CrashSafeScopeTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.JUnitCore

/**
 * The regression test for [#53](https://github.com/astraedus/nudge/issues/53), at the only
 * altitude where the defect is visible: **between** two test classes.
 *
 * `InterventionsViewModelTest` was not broken and neither, in isolation, was `CrashSafeScopeTest`
 * — each passes on its own, which is exactly why the failure looked like a flake and was "fixed"
 * by re-running the job. The defect lives in what one class leaves behind for the next one, so the
 * test has to run a whole class and then look at the process state it left.
 *
 * It does that literally: run the real offender through `JUnitCore` and ask kotlinx's global
 * collector whether anything is still sitting in it. Before the fix this fails; the `@After` drain
 * in `CrashSafeScopeTest` is what makes it pass.
 */
class GlobalCollectorNotLeakedTest {

    @Test
    fun `the class that deliberately leaves an exception unhandled leaves nothing behind`() {
        // Arm kotlinx's collector (its first runTest in a fork enables it) and start from empty,
        // so what we measure after the run below is what that run itself left.
        LeakedCoroutineExceptions.drain()

        val result = JUnitCore.runClasses(CrashSafeScopeTest::class.java)
        assertTrue(
            "precondition: the offender must pass on its own -- ${result.failures}",
            result.wasSuccessful()
        )

        assertFalse(
            "CrashSafeScopeTest left an unhandled coroutine exception in " +
                "kotlinx-coroutines-test's process-global ExceptionCollector. Nothing fails here; " +
                "the NEXT runTest anywhere in this JVM fork fails instead, with " +
                "UncaughtExceptionsBeforeTest, and which test that is depends only on Gradle's " +
                "class order -- which is why #53 hit InterventionsViewModelTest on the release " +
                "variant and nothing at all on the debug one. See LeakedCoroutineExceptions",
            LeakedCoroutineExceptions.drain()
        )
    }
}
