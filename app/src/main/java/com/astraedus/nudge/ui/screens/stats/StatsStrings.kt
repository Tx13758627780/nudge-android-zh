package com.astraedus.nudge.ui.screens.stats

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.astraedus.nudge.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Locale-aware presentation of pure calculator output. Calculator contracts remain platform-free. */
fun Resources.statsDuration(value: String): String {
    if (value == "< 1m") return getString(R.string.stats_under_minute)
    return DURATION_COMPONENT.replace(value) { match ->
        val unit = when (match.groupValues[2]) {
            "h" -> R.string.stats_hours
            "m" -> R.string.stats_minutes
            else -> R.string.stats_seconds
        }
        getString(unit, match.groupValues[1].toLong())
    }
}

fun Resources.statsHour(hour: Int): String {
    if (hour !in 0..23) return hour.toString()
    if (configuration.locales[0].language == "zh") {
        return getString(R.string.stats_hour_24, hour)
    }
    val clockHour = if (hour % 12 == 0) 12 else hour % 12
    return getString(if (hour < 12) R.string.stats_hour_am else R.string.stats_hour_pm, clockHour)
}

fun Resources.statsWeekday(index: Int, full: Boolean = false): String {
    val labels = if (full) FULL_WEEKDAY_LABELS else WEEKDAY_LABELS
    return labels.getOrNull(index)?.let { getString(it) } ?: ""
}

fun Resources.statsDayLabel(value: String): String {
    val index = ENGLISH_WEEKDAYS.indexOf(value)
    return if (index >= 0) statsWeekday(index) else value
}

fun Resources.statsDateLabel(value: String): String = when (value) {
    "Today" -> getString(R.string.stats_today)
    "Yesterday" -> getString(R.string.stats_yesterday)
    "Last 7 days" -> getString(R.string.stats_last_seven_days)
    else -> value
}

fun Resources.statsWeekLabel(value: String): String = when (value) {
    "This wk" -> getString(R.string.stats_this_week)
    else -> WEEKS_AGO.matchEntire(value)?.groupValues?.get(1)?.toIntOrNull()
        ?.let { getString(R.string.stats_weeks_ago, it) } ?: value
}

@Composable
private fun statsResources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}

@Composable
fun statsDuration(value: String): String = statsResources().statsDuration(value)

@Composable
fun statsHourLabel(hour: Int): String = statsResources().statsHour(hour)

@Composable
fun statsWeekdayLabel(index: Int, full: Boolean = false): String =
    statsResources().statsWeekday(index, full)

@Composable
fun statsDayLabel(value: String): String = statsResources().statsDayLabel(value)

@Composable
fun statsDateLabel(value: String, date: LocalDate? = null): String {
    val resources = statsResources()
    if (date == null) return resources.statsDateLabel(value)
    val today = LocalDate.now()
    return when (date) {
        today -> resources.getString(R.string.stats_today)
        today.minusDays(1) -> resources.getString(R.string.stats_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern(
            resources.getString(R.string.stats_date_pattern), resources.configuration.locales[0]
        ))
    }
}

@Composable
fun statsDateRange(value: String, start: LocalDate?, end: LocalDate?): String {
    val resources = statsResources()
    if (start == null || end == null) return resources.statsDateLabel(value)
    if (end == LocalDate.now()) return resources.getString(R.string.stats_last_seven_days)
    val formatter = DateTimeFormatter.ofPattern(
        resources.getString(R.string.stats_date_short_pattern), resources.configuration.locales[0]
    )
    return resources.getString(R.string.stats_date_range, start.format(formatter), end.format(formatter))
}

@Composable
fun statsWeekLabel(value: String): String = statsResources().statsWeekLabel(value)

@Composable
fun statsAppLabel(packageName: String, resolvedLabel: String): String =
    if (packageName == InsightsCalculator.WEB_PSEUDO_PACKAGE) {
        statsResources().getString(R.string.stats_websites)
    } else resolvedLabel

@Composable
fun statsModeLabel(mode: String): String = statsResources().getString(
    when (mode) {
        "HARD_BLOCK" -> R.string.stats_mode_hard
        "DELAY" -> R.string.stats_mode_delay
        "HOLD" -> R.string.stats_mode_hold
        "BREATHING" -> R.string.stats_mode_breathing
        else -> R.string.stats_mode_other
    }
)

private val DURATION_COMPONENT = Regex("(\\d+)([hms])")
private val WEEKS_AGO = Regex("(\\d+)w ago")
private val ENGLISH_WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val WEEKDAY_LABELS = listOf(
    R.string.stats_weekday_mon, R.string.stats_weekday_tue, R.string.stats_weekday_wed,
    R.string.stats_weekday_thu, R.string.stats_weekday_fri, R.string.stats_weekday_sat,
    R.string.stats_weekday_sun
)
private val FULL_WEEKDAY_LABELS = listOf(
    R.string.stats_weekday_full_mon, R.string.stats_weekday_full_tue, R.string.stats_weekday_full_wed,
    R.string.stats_weekday_full_thu, R.string.stats_weekday_full_fri, R.string.stats_weekday_full_sat,
    R.string.stats_weekday_full_sun
)
