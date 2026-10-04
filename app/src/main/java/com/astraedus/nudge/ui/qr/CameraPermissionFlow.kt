package com.astraedus.nudge.ui.qr

/** What the scanner screen is showing. */
internal enum class ScanScreenState {
    /** The system permission dialog is up (or about to be); the rationale line shows behind it. */
    REQUESTING,

    /** The user said no before; explain why before asking again, behind an explicit button. */
    RATIONALE,

    /**
     * The system will not show the dialog any more. Only Settings can grant it, so say so, with a
     * button there, instead of closing the scanner with no explanation.
     */
    BLOCKED,

    /** Camera granted: preview + analysis running. */
    SCANNING,

    /** Terminal: the scanner returns null to the caller. */
    CANCELLED
}

/**
 * The camera-permission decisions, pulled out of the activity so they are plain JVM tests.
 *
 * Inputs are the two facts Android gives us: whether the permission is granted, and
 * `shouldShowRequestPermissionRationale` (true only after exactly the kind of denial the system is
 * still willing to ask about again).
 */
internal object CameraPermissionFlow {

    /** Where the screen starts when the scanner opens. */
    fun initial(granted: Boolean, shouldShowRationale: Boolean): ScanScreenState = when {
        granted -> ScanScreenState.SCANNING
        shouldShowRationale -> ScanScreenState.RATIONALE
        else -> ScanScreenState.REQUESTING
    }

    /**
     * After the system dialog answers.
     *
     * Denied WITH rationale still available is an ordinary "no": respect it and return null.
     * Denied with NO rationale means the system is no longer asking: either the user chose "don't
     * ask again" (or denied twice on Android 11+), or it was already off and the request returned
     * instantly without a dialog. From the user's side that last case is "I tapped Scan and the
     * screen blinked shut", so it gets the Settings explanation. The one false positive is a
     * first-ever dialog dismissed by tapping outside it, where the Settings screen is merely a
     * longer route to the same grant.
     */
    fun afterRequest(granted: Boolean, shouldShowRationale: Boolean): ScanScreenState = when {
        granted -> ScanScreenState.SCANNING
        shouldShowRationale -> ScanScreenState.CANCELLED
        else -> ScanScreenState.BLOCKED
    }

    /**
     * Coming back to the screen (e.g. from Settings): a grant made there starts the scanner. Only
     * from the two waiting states; a REQUESTING screen has a dialog answer on its way, and that
     * answer, not this, decides.
     */
    fun onResume(current: ScanScreenState, granted: Boolean): ScanScreenState =
        if (granted && (current == ScanScreenState.BLOCKED || current == ScanScreenState.RATIONALE)) {
            ScanScreenState.SCANNING
        } else {
            current
        }
}
