package com.astraedus.nudge.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.astraedus.nudge.R

/**
 * The dashboard's Nuke tile. Always visible, always full width, next to the two stat tiles.
 *
 * Copy and colour swap on the three states a Nuke can be in: never set up, off (and armable or
 * not), and on. Only the ON state uses `errorContainer` -- it is meant to read as more serious
 * than the rest of the dashboard, since apps on this list have no daily pass and no delay.
 */
@Composable
fun NukeCard(
    summary: NukeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title: String
    val subtitle: String
    val onErrorState = summary.active

    when {
        summary.needsSetup -> {
            title = stringResource(R.string.home_nuke)
            subtitle = stringResource(R.string.home_nuke_setup_description)
        }
        summary.active -> {
            title = stringResource(R.string.home_nuke_on, pluralApps(summary.appCount))
            subtitle = stringResource(R.string.home_nuke_end_description)
        }
        summary.appCount == 0 || !summary.hasKey -> {
            title = stringResource(R.string.home_nuke_off)
            subtitle = stringResource(R.string.home_nuke_add_apps)
        }
        else -> {
            title = stringResource(R.string.home_nuke_off)
            subtitle = stringResource(R.string.home_nuke_ready, pluralApps(summary.appCount))
        }
    }

    val containerColor = if (onErrorState) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (onErrorState) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, onClickLabel = title)
            .semantics { contentDescription = "$title. $subtitle." },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (onErrorState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = contentColor
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun pluralApps(count: Int): String = if (count == 1) {
    stringResource(R.string.home_one_app)
} else {
    stringResource(R.string.home_apps_count, count)
}
