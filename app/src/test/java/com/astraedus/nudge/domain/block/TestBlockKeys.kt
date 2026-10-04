package com.astraedus.nudge.domain.block

/**
 * The two decision fingerprints ([BlockLaunchGate.decisionFingerprint]) the tests need by name.
 *
 * Shared rather than re-spelled per file, and DERIVED from the production function rather than
 * written out as a string literal, for the reason `docs/testing-strategy.md` rule (b) gives: a
 * hand-typed `"com.x|mode=DELAY"` would be a second definition of the format, and it would go on
 * passing after the real one changed shape.
 */

/** A plain DELAY block for [pkg] — what most of these tests mean by "a block". */
internal fun delayKey(pkg: String): String =
    BlockLaunchGate.decisionFingerprint(attributedPackage = pkg, blockMode = "DELAY")

/** The daily-limit hard block for [pkg] — the OTHER half of issue #50's pair. */
internal fun dailyLimitKey(pkg: String): String = BlockLaunchGate.decisionFingerprint(
    attributedPackage = pkg,
    blockMode = "HARD_BLOCK",
    dailyLimited = true
)
