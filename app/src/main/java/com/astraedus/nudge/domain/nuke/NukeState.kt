package com.astraedus.nudge.domain.nuke

/**
 * Everything Nuke Mode persists, as one value.
 *
 * Nuke is a SECOND list, deliberately independent of block rules and groups: an app can be nuked
 * with no rule at all, and a nuked app's own rules are simply overridden while Nuke is on. See
 * `docs/architecture/nuke-mode.md`.
 *
 * v1 is ONE list and ONE key. The shape is kept so a later multi-profile version is additive: a
 * profile would be a named [NukeState] minus [active] (which stays global — only one Nuke can be on
 * at a time), and the current fields become "the default profile" without a migration.
 *
 * @property active whether Nuke is on right now. Survives reboot (it is persisted), and is only ever
 *   true while a key is paired — [com.astraedus.nudge.data.preferences.NudgePreferences] clears it
 *   whenever the key is removed.
 * @property packages the apps the user picked. RAW: may still contain a package the safety floor
 *   refuses, which is why every enforcement question goes through [NukePolicy] rather than reading
 *   this set directly.
 * @property keyHash lowercase hex SHA-256 of the paired key's payload, or null when no key is
 *   paired. The payload itself is never stored ([NukeKey]).
 * @property keyKind how the key was paired, for the screen's copy only. Never consulted by a gate.
 */
data class NukeState(
    val active: Boolean = false,
    val packages: Set<String> = emptySet(),
    val keyHash: String? = null,
    val keyKind: NukeKeyKind? = null
) {
    val hasKey: Boolean get() = !keyHash.isNullOrBlank()

    companion object {
        val OFF = NukeState()
    }
}

/** How the paired key came about. Display only. */
enum class NukeKeyKind {
    /** A `nudge-nuke:` token Nudge generated, shown as a QR, and the user scanned back once. */
    GENERATED_QR,

    /** Any QR or barcode the user already had (a product barcode works). */
    EXISTING_CODE;

    companion object {
        /** Fails soft: an unknown or missing stored name is "not recorded", never a crash. */
        fun fromNameOrNull(name: String?): NukeKeyKind? =
            entries.firstOrNull { it.name == name }
    }
}
