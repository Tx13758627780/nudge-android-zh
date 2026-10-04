package com.astraedus.nudge.ui.overlay

import android.content.Context
import com.astraedus.nudge.R

// Keep NudgeMessages' pool parsing pure. Only the built-in fallback uses translated resources;
// custom user messages are displayed exactly as supplied after the existing whitespace trimming.
internal fun localizedDelayTitles(context: Context): List<String> = listOf(
    R.string.overlay_delay_title_1,
    R.string.overlay_delay_title_2,
    R.string.overlay_delay_title_3,
    R.string.overlay_delay_title_4,
    R.string.overlay_delay_title_5,
).map { context.getString(it) }

internal fun localizedDelaySubtitles(context: Context): List<String> = listOf(
    R.string.overlay_delay_subtitle_1,
    R.string.overlay_delay_subtitle_2,
    R.string.overlay_delay_subtitle_3,
    R.string.overlay_delay_subtitle_4,
    R.string.overlay_delay_subtitle_5,
).map { context.getString(it) }

internal fun localizedHardBlockMessages(context: Context): List<String> = listOf(
    R.string.overlay_hard_message_1,
    R.string.overlay_hard_message_2,
    R.string.overlay_hard_message_3,
    R.string.overlay_hard_message_4,
    R.string.overlay_hard_message_5,
).map { context.getString(it) }
