package com.astraedus.nudge.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astraedus.nudge.R

/**
 * The block Nuke Mode shows. Deliberately NOT [HardBlockContent] with a flag:
 *
 *  - It takes no emergency-pass parameters AT ALL, so the daily 2-minute pass cannot be rendered on
 *    it by any caller. Nuke's way out is the key or the 64-character code, and both live in the app,
 *    not on this screen. `NukeOverlayContractTest` pins that absence.
 *  - Its one button is the walk-away (stringResource(R.string.interaction_go_home)), wired by the activity to the same `navigateHome`
 *    every other block uses: the walk-away row, `GLOBAL_ACTION_HOME`, the issue #26 departure
 *    window. There is no completion path, exactly like a rule's hard block.
 *  - No custom message pool: the user's own hard-block messages are written for their rules, and
 *    "you set a limit on this" is the wrong thing to say about an app they nuked.
 */
@Composable
fun NukeBlockContent(
    packageName: String,
    appLabel: String?,
    onGoHome: () -> Unit
) {
    val name = appLabel ?: stringResource(R.string.overlay_this_app)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Full-bleed surface, inset content: same edge-to-edge rule as HardBlockContent.
                .safeDrawingPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.nuke_blocked),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.nuke_block_explanation, name),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            Button(onClick = onGoHome) {
                Text(stringResource(R.string.interaction_go_home))
            }
        }
    }
}
