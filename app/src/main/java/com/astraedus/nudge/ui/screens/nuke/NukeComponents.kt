package com.astraedus.nudge.ui.screens.nuke

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.astraedus.nudge.R
import com.astraedus.nudge.domain.nuke.NukeKeyKind
import com.astraedus.nudge.domain.nuke.NukePolicy
import com.astraedus.nudge.ui.qr.QrCodeGenerator

/**
 * The one-time explainer shown until [NukeViewModel.dismissIntro] fires. Plain, a bit dry: this
 * is a serious feature (no daily pass, no delay) and the copy says so without theatrics.
 */
@Composable
fun NukeIntroCard(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.nuke_intro_list),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.nuke_intro_scan),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.nuke_intro_emergency),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.interaction_got_it))
            }
        }
    }
}

/**
 * The on/off status card. The primary surface for turning Nuke on and off, so the ON state uses
 * `errorContainer` -- deliberately more serious than the rest of the dashboard.
 */
@Composable
fun NukeStatusCard(
    state: NukeUiState,
    hasCamera: Boolean,
    onScanToggle: () -> Unit,
    onNukeNow: () -> Unit,
    onEmergencyEnd: () -> Unit
) {
    val containerColor = if (state.active) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (state.active) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (state.active) 2.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (state.active) stringResource(R.string.nuke_on_count, pluralStringResource(R.plurals.nuke_app_count, state.nukedCount, state.nukedCount)) else stringResource(R.string.nuke_off),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Spacer(Modifier.height(12.dp))

            if (state.active) {
                Button(
                    onClick = onScanToggle,
                    enabled = hasCamera,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.nuke_scan_end))
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onEmergencyEnd, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nuke_emergency_end))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.nuke_changes_need_code),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.75f)
                )
            } else {
                Button(
                    onClick = onScanToggle,
                    enabled = state.hasKey && hasCamera,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.nuke_scan_start))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNukeNow,
                    enabled = state.canArm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.nuke_now))
                }
                val hint = when (state.armBlocker) {
                    NukePolicy.ArmBlocker.NO_KEY -> stringResource(R.string.nuke_pair_first)
                    NukePolicy.ArmBlocker.EMPTY_LIST -> stringResource(R.string.nuke_add_first)
                    null -> null
                }
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * The paired-code card: pair a first code, or manage/replace/remove the one that's already there.
 */
@Composable
fun NukeKeyCard(
    state: NukeUiState,
    hasCamera: Boolean,
    onCreateQr: () -> Unit,
    onPairExisting: () -> Unit,
    onUnpair: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.nuke_your_code),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))

            if (!hasCamera) {
                // The scanner returns nothing on a phone without a camera, so pairing could only
                // ever silently fail. Say so instead. An already-paired phone keeps the emergency
                // code as its way out (the unlock dialog says the same).
                Text(
                    stringResource(R.string.nuke_no_camera_pairing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!state.hasKey) {
                Text(
                    stringResource(R.string.nuke_keep_code),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onCreateQr, enabled = hasCamera, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nuke_create_qr))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPairExisting, enabled = hasCamera, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nuke_existing_barcode))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.nuke_barcode_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    when (state.keyKind) {
                        NukeKeyKind.GENERATED_QR -> stringResource(R.string.nuke_paired_qr)
                        NukeKeyKind.EXISTING_CODE -> stringResource(R.string.nuke_paired_barcode)
                        null -> stringResource(R.string.nuke_paired)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.nuke_replace),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onCreateQr, enabled = hasCamera, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.nuke_new_qr))
                    }
                    OutlinedButton(onClick = onPairExisting, enabled = hasCamera, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.nuke_own_barcode))
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onUnpair, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.interaction_remove))
                }
            }
        }
    }
}

/**
 * Shown while [NukePairing.ShowingNewQr] is the pairing state. Stays up across the confirm scan
 * (state-driven, not owned by this composable), so the camera activity can come and go over it.
 *
 * The token never renders as text -- only as the QR itself -- so nobody reads it off screen by
 * accident before they've saved it somewhere.
 */
@Composable
fun NukePairingDialog(
    token: String,
    onSaveShare: () -> Unit,
    onConfirmScan: () -> Unit,
    onCancel: () -> Unit
) {
    val bitmap = remember(token) { QrCodeGenerator.generate(token, 768).asImageBitmap() }

    Dialog(onDismissRequest = onCancel) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.nuke_your_code),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.nuke_qr_description),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.nuke_save_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onSaveShare, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nuke_save_share))
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onConfirmScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nuke_confirm_saved))
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.interaction_cancel))
                }
            }
        }
    }
}
