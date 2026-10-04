package com.astraedus.nudge.ui.backup

import android.content.Context
import com.astraedus.nudge.R
import com.astraedus.nudge.domain.usecase.ImportOutcome
import com.astraedus.nudge.domain.usecase.ImportPreview

/** Presentation-only wording; backup JSON and the pure, JVM-tested import builders stay unchanged. */
internal fun buildLocalizedImportPreviewMessage(context: Context, preview: ImportPreview): String =
    buildString {
        val result = preview.result
        append(
            if (result.groups.isEmpty()) {
                context.getString(R.string.backup_preview_rules, result.rules.size)
            } else {
                context.getString(R.string.backup_preview_rules_groups, result.rules.size, result.groups.size)
            }
        )
        append("\n\n")
        append(context.getString(R.string.backup_duplicates_warning))
        if (result.history.isNotEmpty()) {
            append("\n\n")
            append(context.getString(R.string.backup_preview_history, result.history.size, preview.newHistoryCount))
        }
        if (result.invalidCount > 0) {
            append("\n\n")
            append(context.resources.getQuantityString(R.plurals.backup_preview_invalid_entries, result.invalidCount, result.invalidCount))
            appendLocalizedReasons(context, result.invalidReasons)
        }
        if (result.settings != null) {
            append("\n\n")
            append(context.getString(R.string.backup_settings_warning))
        }
        if (result.invalidHistoryCount > 0) {
            append("\n\n")
            append(context.resources.getQuantityString(R.plurals.backup_preview_invalid_history, result.invalidHistoryCount, result.invalidHistoryCount))
            appendLocalizedReasons(context, result.invalidHistoryReasons)
        }
        if (result.invalidSettingsCount > 0) {
            append("\n\n")
            append(context.resources.getQuantityString(R.plurals.backup_preview_invalid_settings, result.invalidSettingsCount, result.invalidSettingsCount))
            appendLocalizedReasons(context, result.invalidSettingsReasons)
        }
    }

internal fun buildLocalizedImportOutcomeMessage(context: Context, outcome: ImportOutcome): String =
    buildString {
        append(context.getString(R.string.backup_outcome_rules, outcome.importedCount))
        if (outcome.groupsCreated > 0) {
            append("\n")
            append(context.getString(R.string.backup_outcome_groups, outcome.groupsCreated))
        }
        if (outcome.duplicateCount > 0) {
            append("\n")
            append(context.getString(R.string.backup_outcome_duplicates, outcome.duplicateCount))
        }
        if (outcome.historyImportedCount > 0 || outcome.historyDuplicateCount > 0 || outcome.historyInvalidCount > 0) {
            append("\n\n")
            append(context.getString(R.string.backup_outcome_history, outcome.historyImportedCount))
            if (outcome.historyDuplicateCount > 0) {
                append("\n")
                append(context.getString(R.string.backup_outcome_history_duplicates, outcome.historyDuplicateCount))
            }
            if (outcome.historyInvalidCount > 0) {
                append("\n")
                append(context.getString(R.string.backup_outcome_history_invalid, outcome.historyInvalidCount))
            }
        }
        if (outcome.settingsApplied) {
            append("\n\n")
            append(context.getString(R.string.backup_outcome_settings))
        }
        if (outcome.settingsInvalidCount > 0) {
            append("\n")
            append(context.getString(R.string.backup_outcome_settings_invalid, outcome.settingsInvalidCount))
        }
        if (outcome.invalidCount > 0) {
            append("\n\n")
            append(context.getString(R.string.backup_outcome_invalid, outcome.invalidCount))
            appendLocalizedReasons(context, outcome.invalidReasons)
        }
    }

private fun StringBuilder.appendLocalizedReasons(context: Context, reasons: List<String>) {
    reasons.take(3).forEach { append("\n• ${localizedBackupMessage(context, it)}") }
    val extra = reasons.size - 3
    if (extra > 0) append("\n• ${context.getString(R.string.backup_more_reasons, extra.toString())}")
}

/** Translate known importer diagnostics without changing strings used by the parser or its tests. */
internal fun localizedBackupMessage(context: Context, message: String): String {
    val resource = when (message) {
        BackupViewModel.SAVE_SUCCESS_MESSAGE -> R.string.backup_save_success
        BackupViewModel.SAVE_FAILURE_MESSAGE -> R.string.backup_save_failure
        "Could not read that file." -> R.string.backup_read_failure
        "Invalid or missing version field" -> R.string.backup_invalid_version
        "Package name is blank" -> R.string.backup_blank_package
        "Group name is blank" -> R.string.backup_blank_group
        "Timestamp is not a positive epoch-millisecond value" -> R.string.backup_invalid_timestamp
        "could not be read" -> R.string.backup_unreadable
        "is not true or false" -> R.string.backup_not_boolean
        "is not text" -> R.string.backup_not_text
        "is not a number" -> R.string.backup_not_number
        else -> null
    }
    if (resource != null) return context.getString(resource)

    Regex("Export version (\\d+) is newer than supported \\((\\d+)\\)\\. Please update the app\\.")
        .matchEntire(message)?.let {
            return context.getString(R.string.backup_newer_version, it.groupValues[1], it.groupValues[2])
        }
    Regex("No valid rules or groups found\\. All (\\d+) entries were invalid:\\n([\\s\\S]*)")
        .matchEntire(message)?.let {
            val reasons = it.groupValues[2].lineSequence()
                .joinToString("\n") { reason -> localizedBackupMessage(context, reason) }
            return context.getString(R.string.backup_all_invalid, it.groupValues[1], reasons)
        }
    Regex("(Rule|Group|History event) (\\d+): ([\\s\\S]*)").matchEntire(message)?.let {
        val label = when (it.groupValues[1]) {
            "Rule" -> R.string.backup_reason_rule
            "Group" -> R.string.backup_reason_group
            else -> R.string.backup_reason_history
        }
        return context.getString(label, it.groupValues[2], localizedBackupMessage(context, it.groupValues[3]))
    }
    Regex("Setting \"([^\"]+)\": ([\\s\\S]*)").matchEntire(message)?.let {
        return context.getString(
            R.string.backup_reason_setting, it.groupValues[1], localizedBackupMessage(context, it.groupValues[2])
        )
    }
    Regex("\\.\\.\\.and (\\d+) more").matchEntire(message)?.let {
        return context.getString(R.string.backup_more_reasons, it.groupValues[1])
    }
    listOf(
        "Invalid JSON format: " to R.string.backup_invalid_json,
        "Invalid data: " to R.string.backup_invalid_data,
        "Unknown block mode: " to R.string.backup_unknown_mode,
        "unknown block mode: " to R.string.backup_unknown_mode,
        "No value for " to R.string.backup_missing_value
    ).forEach { (prefix, id) ->
        if (message.startsWith(prefix)) {
            val detail = message.removePrefix(prefix)
            return context.getString(
                id,
                if (id == R.string.backup_invalid_json || id == R.string.backup_invalid_data) {
                    localizedBackupMessage(context, detail)
                } else detail
            )
        }
    }
    Regex("challenge length (-?\\d+) is outside 1\\.\\.(\\d+)").matchEntire(message)?.let {
        return context.getString(R.string.backup_invalid_challenge_length, it.groupValues[1], it.groupValues[2])
    }
    Regex("\"([^\"]+)\" (.+)").matchEntire(message)?.let {
        val id = when (it.groupValues[2]) {
            "must be an array" -> R.string.backup_field_array
            "must be an object" -> R.string.backup_field_object
            "is missing or is not text" -> R.string.backup_field_missing_text
            "is missing or is not a number" -> R.string.backup_field_missing_number
            "is not true or false" -> R.string.backup_field_boolean
            "is not text" -> R.string.backup_field_text
            else -> null
        }
        if (id != null) return context.getString(id, it.groupValues[1])
    }
    // JSON libraries can supply arbitrary technical diagnostics. Keep those details intact.
    return context.getString(R.string.backup_unrecognized_diagnostic, message)
}
