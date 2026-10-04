package com.astraedus.nudge.domain.nuke

/**
 * "Is this package nuked right now?", asked by the evaluation use case.
 *
 * An interface so the use case stays testable without DataStore or PackageManager: the production
 * implementation (`service/NukeEnforcementSource`) reads the persisted [NukeState] and applies
 * [NukePolicy.isNuked] with this device's safety floor. The accessibility service does NOT go
 * through this on its synchronous hot path — it keeps its own volatile cache of the same answer,
 * collected off-main, exactly as it does for Strict Mode and the master toggle.
 */
fun interface NukeEnforcement {
    suspend fun isNuked(packageName: String): Boolean

    companion object {
        /** For callers and tests with no Nuke in play. */
        val NEVER = NukeEnforcement { false }
    }
}
