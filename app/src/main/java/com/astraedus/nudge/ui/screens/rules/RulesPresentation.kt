package com.astraedus.nudge.ui.screens.rules

import android.content.Context
import com.astraedus.nudge.R
import com.astraedus.nudge.data.db.entity.BlockRule

/** Labels are resolved when the screen renders, so changing the system language updates them. */
internal fun localizedRuleMode(context: Context, mode: String, delaySeconds: Int): String =
    when (mode) {
        "NONE" -> context.getString(R.string.rules_not_blocked)
        "HARD_BLOCK" -> context.getString(R.string.rules_hard_block)
        "DELAY" -> context.getString(R.string.rules_delay_summary, delaySeconds)
        "HOLD" -> context.getString(R.string.rules_hold_summary, delaySeconds)
        "BREATHING" -> context.getString(R.string.rules_breathing_summary, delaySeconds)
        else -> mode
    }

internal fun localizedRuleFeature(context: Context, feature: String): String =
    when (feature.trim()) {
        "REELS" -> context.getString(R.string.rules_feature_reels)
        "EXPLORE" -> context.getString(R.string.rules_feature_explore)
        "SHORTS" -> context.getString(R.string.rules_feature_shorts)
        "TIKTOK_FEED" -> context.getString(R.string.rules_feature_tiktok_feed)
        else -> feature.trim().lowercase().replaceFirstChar { it.uppercase() }
    }

internal fun localizedActiveRulesSummary(context: Context, rules: List<BlockRule>): String {
    val defaultRule = rules.find { it.inAppFeatures.isNullOrBlank() }
    val featureRules = rules.filter { !it.inAppFeatures.isNullOrBlank() }
    val parts = mutableListOf<String>()
    if (defaultRule != null) {
        parts += localizedRuleMode(context, defaultRule.mode, defaultRule.delaySeconds)
        defaultRule.dailyLimitMinutes?.let {
            parts += context.getString(R.string.rules_limit_summary, it)
        }
        if (defaultRule.scheduleDays != null || defaultRule.scheduleStartMinute != null) {
            parts += context.getString(R.string.rules_scheduled_summary)
        }
    }
    for (rule in featureRules) {
        val features = rule.inAppFeatures!!.split(",").filter { it.isNotBlank() }
            .joinToString(context.getString(R.string.rules_features_separator)) {
                localizedRuleFeature(context, it)
            }
        parts += context.getString(
            R.string.rules_feature_mode_summary,
            features,
            localizedRuleMode(context, rule.mode, rule.delaySeconds)
        )
    }
    return if (parts.isEmpty()) context.getString(R.string.rules_configured)
    else parts.joinToString(context.getString(R.string.rules_items_separator))
}

internal fun localizedEditorRuleSummary(context: Context, rule: BlockRule): String {
    val features = rule.inAppFeatures?.split(",")?.filter { it.isNotBlank() }.orEmpty()
    val featureLabel = if (features.isEmpty()) context.getString(R.string.rules_whole_app)
    else features.joinToString(context.getString(R.string.rules_extras_separator)) {
        localizedRuleFeature(context, it)
    }
    val extras = buildList {
        rule.dailyLimitMinutes?.let { add(context.getString(R.string.rules_daily_limit_summary, it)) }
        if (rule.scheduleDays != null) add(context.getString(R.string.rules_scheduled_summary))
        if (rule.grayscale) add(context.getString(R.string.rules_grayscale_summary))
        if (rule.showCounter) add(context.getString(R.string.rules_counter_summary))
        rule.autoKickAfter?.let { add(context.getString(R.string.rules_auto_kick_summary, it)) }
        rule.autoKickAfterMinutes?.let { add(context.getString(R.string.rules_auto_kick_time_summary, it)) }
        if (rule.showTimeRemaining) add(context.getString(R.string.rules_remaining_summary))
    }
    val extraText = if (extras.isEmpty()) "" else context.getString(
        R.string.rules_extras_summary,
        extras.joinToString(context.getString(R.string.rules_extras_separator))
    )
    return context.getString(
        R.string.rules_description_summary,
        featureLabel,
        localizedRuleMode(context, rule.mode, rule.delaySeconds),
        extraText
    )
}
