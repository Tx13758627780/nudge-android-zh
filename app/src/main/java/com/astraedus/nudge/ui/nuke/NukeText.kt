package com.astraedus.nudge.ui.nuke

import android.content.Context
import com.astraedus.nudge.R

/**
 * Translate the built-in copy emitted by the pure Nuke gate and ViewModel at the UI boundary.
 * Their compatibility text stays JVM-testable and no scanned token, app name or custom text changes.
 */
internal fun localizedNukeText(context: Context, text: String): String {
    val resource = when (text) {
        "End Nuke" -> R.string.nuke_end
        "End Nuke without your code" -> R.string.nuke_end_without_code
        "Take this app off your Nuke list" -> R.string.nuke_remove_app
        "Replace your Nuke code" -> R.string.nuke_replace_code
        "Remove your Nuke code" -> R.string.nuke_remove_code
        "Turn off all blocking" -> R.string.nuke_master_off
        "That's not your Nuke code." -> R.string.nuke_wrong_code
        "Nuke is on." -> R.string.nuke_armed
        "Nuke is off." -> R.string.nuke_disarmed
        "Pair a Nuke code first." -> R.string.nuke_pair_first
        "Add at least one app to your Nuke list first." -> R.string.nuke_empty_list
        "Paired. Keep that code somewhere inconvenient." -> R.string.nuke_pair_success
        "Nuke code removed." -> R.string.nuke_code_removed
        "That's not the code on screen. Scan the one you just saved." -> R.string.nuke_wrong_saved_code
        else -> return text
    }
    return context.getString(resource)
}
