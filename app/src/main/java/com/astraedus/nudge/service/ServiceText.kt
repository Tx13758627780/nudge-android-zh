package com.astraedus.nudge.service

import android.content.Context
import com.astraedus.nudge.R
import com.astraedus.nudge.domain.health.ServiceHealth
import com.astraedus.nudge.domain.health.ServiceHealthCopy

/** Resolve copy before the notification gate, so the gate compares the words actually posted. */
internal fun ServiceHealth.localizedNotificationCopy(context: Context): ServiceHealthCopy {
    val (title, body) = when (this) {
        ServiceHealth.DISABLED -> R.string.service_disabled_title to R.string.service_disabled_body
        ServiceHealth.PERMISSION_MISSING -> R.string.service_degraded_title to R.string.service_permission_body
        ServiceHealth.STOPPED_BY_SYSTEM -> R.string.service_degraded_title to R.string.service_stopped_body
        ServiceHealth.ACTIVE -> R.string.service_active_title to R.string.service_active_body
    }
    return ServiceHealthCopy(context.getString(title), context.getString(body))
}

/** These are counter captions, never the identifiers used to detect another app's controls. */
internal fun localizedCounterLabel(context: Context, label: String): String {
    val id = when (label) {
        "items" -> R.string.service_count_items
        "taps" -> R.string.service_count_taps
        "scrolls" -> R.string.service_count_scrolls
        "reels" -> R.string.service_count_reels
        "shorts" -> R.string.service_count_shorts
        "videos" -> R.string.service_count_videos
        "explore" -> R.string.service_count_explore
        "for you" -> R.string.service_count_foryou
        else -> return label
    }
    return context.getString(id)
}

internal fun localizedCompactDuration(context: Context, ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> context.getString(R.string.service_duration_hours_minutes, hours, minutes)
        minutes > 0 -> context.getString(R.string.service_duration_minutes, minutes)
        else -> context.getString(R.string.service_duration_seconds, seconds)
    }
}
