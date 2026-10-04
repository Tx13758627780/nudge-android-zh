package com.astraedus.nudge.ui.screens.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraedus.nudge.R
import com.astraedus.nudge.domain.model.BlockMode
import com.astraedus.nudge.ui.components.localizedBlockModeDescription
import com.astraedus.nudge.ui.components.localizedBlockModeLabel
import com.astraedus.nudge.domain.model.FeatureMode
import com.astraedus.nudge.ui.components.CustomTimeDialog
import com.astraedus.nudge.ui.components.MinutesField
import com.astraedus.nudge.ui.components.StrictModeChallengeHost
import com.astraedus.nudge.ui.components.localizedMinutesDisplay
import com.astraedus.nudge.ui.hasGrayscalePermission
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedAppConfigScreen(
    viewModel: UnifiedAppConfigViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val challenge by viewModel.challenge.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    StrictModeChallengeHost(
        challenge = challenge,
        onVerify = viewModel::verifyChallenge,
        onCancel = viewModel::cancelChallenge
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.appName.ifEmpty { state.packageName })
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.config_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save) {
                        Text(stringResource(R.string.config_save))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ═══ MASTER TOGGLE ═══
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.config_enabled),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::setEnabled
                )
            }

            HorizontalDivider()

            // ═══ ALWAYS ACTIVE ═══
            SectionHeader(stringResource(R.string.config_always_active))

            // Daily Time Limit
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.config_daily_limit),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        InfoButton(
                            stringResource(R.string.config_daily_limit_help) +
                                if (state.webDomainEnabled) stringResource(R.string.config_daily_limit_web_help) else ""
                        )
                    }
                    Switch(
                        checked = state.dailyLimitEnabled,
                        onCheckedChange = viewModel::setDailyLimitEnabled
                    )
                }

                if (state.dailyLimitEnabled) {
                    val dailyPresets = remember { listOf(15, 30, 60, 120) }
                    var showDailyLimitDialog by remember { mutableStateOf(false) }
                    val isCustomDaily = state.dailyLimitMinutes !in dailyPresets

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dailyPresets.forEach { minutes ->
                            FilterChip(
                                selected = state.dailyLimitMinutes == minutes,
                                onClick = { viewModel.setDailyLimitMinutes(minutes) },
                                label = { Text(localizedMinutesDisplay(minutes)) }
                            )
                        }
                        FilterChip(
                            selected = isCustomDaily,
                            onClick = { showDailyLimitDialog = true },
                            label = {
                                Text(
                                    if (isCustomDaily) localizedMinutesDisplay(state.dailyLimitMinutes)
                                    else stringResource(R.string.config_custom)
                                )
                            }
                        )
                    }

                    if (showDailyLimitDialog) {
                        CustomTimeDialog(
                            title = stringResource(R.string.config_custom_daily_limit),
                            unit = stringResource(R.string.config_minutes),
                            currentValue = state.dailyLimitMinutes,
                            min = 1,
                            max = 480,
                            onConfirm = { minutes ->
                                viewModel.setDailyLimitMinutes(minutes)
                                showDailyLimitDialog = false
                            },
                            onDismiss = { showDailyLimitDialog = false }
                        )
                    }

                    // Show time remaining sub-toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(R.string.config_show_remaining),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            InfoButton(
                                stringResource(R.string.config_remaining_help)
                            )
                        }
                        Switch(
                            checked = state.showTimeRemaining,
                            onCheckedChange = viewModel::setShowTimeRemaining
                        )
                    }
                }
            }

            // Interaction counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(R.string.config_interaction_counter),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    InfoButton(
                        stringResource(R.string.config_counter_help)
                    )
                }
                Switch(
                    checked = state.showCounter,
                    onCheckedChange = viewModel::setShowCounter
                )
            }

            // Grayscale
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.config_grayscale),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        InfoButton(
                            stringResource(R.string.config_grayscale_help)
                        )
                    }
                    Text(
                        // Honest about the limitation rather than promising a setting that
                        // silently does nothing: grayscale rides inside a BlockDecision.Block, so
                        // with the app itself unblocked only a feature override can trigger it.
                        // See BlockMode.NONE.
                        if (state.blocksWholeApp) {
                            stringResource(R.string.config_grayscale_permission)
                        } else {
                            stringResource(R.string.config_grayscale_feature_permission)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.grayscale,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasGrayscalePermission(context)) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.config_grayscale_setup)
                                )
                            }
                        } else {
                            viewModel.setGrayscale(enabled)
                        }
                    }
                )
            }

            // Web domain blocking
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.config_block_web),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        InfoButton(
                            stringResource(R.string.config_web_help)
                        )
                    }
                    Switch(
                        checked = state.webDomainEnabled,
                        onCheckedChange = viewModel::setWebDomainEnabled
                    )
                }

                if (state.webDomainEnabled) {
                    OutlinedTextField(
                        value = state.webDomains,
                        onValueChange = viewModel::setWebDomains,
                        label = { Text(stringResource(R.string.config_domains)) },
                        placeholder = { Text(stringResource(R.string.config_domains_example)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.config_subdomains_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Websites enforce independently of the app-level mode (issue #21). While the
                    // app itself is blocked they simply follow its mode; when it is not, there is
                    // no app-level mode to follow, so the website mode is chosen here instead of
                    // silently enforcing nothing (which is what used to happen).
                    if (state.blocksWholeApp) {
                        Text(
                            stringResource(R.string.config_web_mode_inherit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            stringResource(R.string.config_web_mode),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            BLOCKING_MODES.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = state.webBlockMode == mode,
                                    onClick = { viewModel.setWebBlockMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = BLOCKING_MODES.size
                                    )
                                ) {
                                    Text(localizedBlockModeLabel(mode))
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.config_web_still_blocked, state.appName, localizedBlockModeDescription(state.webBlockMode)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            // ═══ DEFAULT BEHAVIOR ═══
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionHeader(stringResource(R.string.config_default_behavior))
                InfoButton(stringResource(R.string.config_default_behavior_help))
            }

            // Whether the app itself is gated at all. Off => the app-level rule is BlockMode.NONE,
            // so the app opens freely and only the feature overrides below apply. This is the
            // switch that makes "block only Shorts, leave YouTube alone" expressible.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.config_block_whole_app), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (state.blocksWholeApp) {
                            stringResource(R.string.config_app_blocked, state.appName)
                        } else if (state.supportsFeatures) {
                            stringResource(R.string.config_app_features_only, state.appName)
                        } else {
                            stringResource(R.string.config_app_daily_only, state.appName)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.blocksWholeApp,
                    onCheckedChange = { viewModel.setBlocksWholeApp(it) }
                )
            }

            // Block mode segmented button — only meaningful when the app itself is blocked.
            if (state.blocksWholeApp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        BLOCKING_MODES.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.defaultMode == mode,
                                onClick = { viewModel.setDefaultMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = BLOCKING_MODES.size
                                ),
                                // No check icon: with four modes in the row its 18dp + 8dp would
                                // cost more than a quarter of the width left for "Hard Block", and
                                // the selected segment is already unmistakable from its colour.
                                icon = {}
                            ) {
                                Text(localizedBlockModeLabel(mode), maxLines = 1)
                            }
                        }
                    }

                    Text(
                        localizedBlockModeDescription(state.defaultMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Delay duration (if applicable). One `delaySeconds` is stored per rule and it feeds
            // whichever delay is live — the app's, or (with whole-app blocking off) the website
            // one — so the control has to stay reachable in the web-only case too, else a web
            // DELAY would be permanently stuck at whatever was last saved.
            if (state.showDelayDuration) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(state.delayDurationLabelRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    val delayPresets = remember { listOf(5, 15, 30, 60) }
                    var showDelayDialog by remember { mutableStateOf(false) }
                    val isCustomDelay = state.defaultDelaySeconds !in delayPresets

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        delayPresets.forEach { seconds ->
                            FilterChip(
                                selected = state.defaultDelaySeconds == seconds,
                                onClick = { viewModel.setDefaultDelaySeconds(seconds) },
                                label = { Text(stringResource(R.string.config_seconds_short, seconds)) }
                            )
                        }
                        FilterChip(
                            selected = isCustomDelay,
                            onClick = { showDelayDialog = true },
                            label = {
                                Text(if (isCustomDelay) stringResource(R.string.config_seconds_short, state.defaultDelaySeconds) else stringResource(R.string.config_custom))
                            }
                        )
                    }

                    if (showDelayDialog) {
                        CustomTimeDialog(
                            title = stringResource(R.string.config_custom_duration, stringResource(state.delayDurationLabelRes)),
                            unit = stringResource(R.string.config_seconds),
                            currentValue = state.defaultDelaySeconds,
                            min = 1,
                            max = 300,
                            onConfirm = { seconds ->
                                viewModel.setDefaultDelaySeconds(seconds)
                                showDelayDialog = false
                            },
                            onDismiss = { showDelayDialog = false }
                        )
                    }
                }
            }

            // Auto-kick
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.config_auto_kick),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        InfoButton(
                            stringResource(R.string.config_auto_kick_help) +
                                if (state.webDomainEnabled) stringResource(R.string.config_auto_kick_web_help) else ""
                        )
                    }
                    Switch(
                        checked = state.defaultAutoKickEnabled,
                        onCheckedChange = viewModel::setDefaultAutoKickEnabled
                    )
                }

                if (state.defaultAutoKickEnabled) {
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.config_auto_kick_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.config_after_interactions),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = state.defaultAutoKickByInteractions,
                                onCheckedChange = viewModel::setDefaultAutoKickByInteractions
                            )
                        }

                        if (state.defaultAutoKickByInteractions) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(R.string.config_interaction_count, state.defaultAutoKickAfter),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                    value = state.defaultAutoKickAfter.toFloat(),
                                    onValueChange = { viewModel.setDefaultAutoKickAfter(it.toInt()) },
                                    valueRange = 5f..100f,
                                    steps = 18,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(5.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(100.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        MinutesField(
                            value = state.defaultAutoKickAfterMinutesText,
                            onValueChange = viewModel::setDefaultAutoKickAfterMinutesText,
                            labelText = stringResource(R.string.config_auto_kick_time),
                            supportingText = stringResource(R.string.config_auto_kick_time_help)
                        )

                        MinutesField(
                            value = state.defaultAutoKickCooldownMinutesText,
                            onValueChange = viewModel::setDefaultAutoKickCooldownMinutesText,
                            labelText = stringResource(R.string.config_cooldown),
                            supportingText = stringResource(R.string.config_cooldown_help)
                        )
                    }
                }
            }

            // ═══ FEATURE OVERRIDES ═══
            if (state.supportsFeatures) {
                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SectionHeader(stringResource(R.string.config_feature_rules))
                    InfoButton(stringResource(R.string.config_feature_rules_help))
                }

                state.availableFeatures.forEach { feature ->
                    FeatureOverrideCard(
                        featureName = stringResource(feature.displayNameRes),
                        override = state.featureOverrides[feature.key] ?: FeatureOverride(),
                        onUpdate = { viewModel.setFeatureOverride(feature.key, it) }
                    )
                }

                // Tab Vanish -- covers the in-app tab (e.g. Instagram's Reels tab) while a rule
                // covering that feature resolves to a hard block, so the icon disappears rather
                // than merely refusing to open. Visibility asks the registry (supportsTabVanish),
                // never a hardcoded package check.
                if (state.supportsTabVanish) {
                    val vanishLabel = stringResource(state.vanishableFeatureLabelRes ?: R.string.config_feature_reels)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.config_hide_tab, vanishLabel), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.config_hide_tab_help, vanishLabel),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.tabVanish,
                            onCheckedChange = viewModel::setTabVanish
                        )
                    }
                }

                // Following steer -- experimental, default off. Opens Instagram's Following feed
                // instead of Home. Visibility asks the registry (supportsFollowingSteer).
                if (state.supportsFollowingSteer) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.config_following),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.config_following_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.followingSteer,
                            onCheckedChange = viewModel::setFollowingSteer
                        )
                    }
                }
            }

            HorizontalDivider()

            // ═══ SCHEDULED OVERRIDE ═══
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionHeader(stringResource(R.string.config_scheduled_override))
                InfoButton(stringResource(R.string.config_schedule_help))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.config_enable_schedule),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = state.scheduledOverrideEnabled,
                    onCheckedChange = viewModel::setScheduledOverrideEnabled
                )
            }

            if (state.scheduledOverrideEnabled) {
                // Day selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.config_active_days),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val dayLabels = remember {
                            listOf(
                                1 to R.string.config_monday,
                                2 to R.string.config_tuesday,
                                3 to R.string.config_wednesday,
                                4 to R.string.config_thursday,
                                5 to R.string.config_friday,
                                6 to R.string.config_saturday,
                                7 to R.string.config_sunday
                            )
                        }
                        dayLabels.forEach { (day, labelRes) ->
                            FilterChip(
                                selected = day in state.scheduleDays,
                                onClick = {
                                    val newDays = state.scheduleDays.toMutableSet()
                                    if (day in newDays) newDays.remove(day) else newDays.add(day)
                                    viewModel.setScheduleDays(newDays)
                                },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                    }

                    // Start time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.config_start_time), style = MaterialTheme.typography.bodyMedium)
                        TimeSelector(
                            hour = state.scheduleStartHour,
                            minute = state.scheduleStartMinute,
                            onTimeSelected = { h, m -> viewModel.setScheduleStartTime(h, m) }
                        )
                    }

                    // End time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.config_end_time), style = MaterialTheme.typography.bodyMedium)
                        TimeSelector(
                            hour = state.scheduleEndHour,
                            minute = state.scheduleEndMinute,
                            onTimeSelected = { h, m -> viewModel.setScheduleEndTime(h, m) }
                        )
                    }

                    // Scheduled mode
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        BLOCKING_MODES.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.scheduledMode == mode,
                                onClick = { viewModel.setScheduledMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = BLOCKING_MODES.size
                                )
                            ) {
                                Text(localizedBlockModeLabel(mode))
                            }
                        }
                    }

                    // Scheduled delay duration
                    if (state.scheduledMode != BlockMode.HARD_BLOCK) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 15, 30, 60).forEach { seconds ->
                                FilterChip(
                                    selected = state.scheduledDelaySeconds == seconds,
                                    onClick = { viewModel.setScheduledDelaySeconds(seconds) },
                                    label = { Text(stringResource(R.string.config_seconds_short, seconds)) }
                                )
                            }
                        }
                    }

                    // Scheduled feature overrides
                    if (state.supportsFeatures) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.config_scheduled_features),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        state.availableFeatures.forEach { feature ->
                            FeatureOverrideCard(
                                featureName = stringResource(feature.displayNameRes),
                                override = state.scheduledFeatureOverrides[feature.key]
                                    ?: FeatureOverride(),
                                onUpdate = { viewModel.setScheduledFeatureOverride(feature.key, it) }
                            )
                        }
                    }
                }
            }

            // ═══ DANGER ZONE ═══
            if (state.hasExistingRules) {
                HorizontalDivider()
                OutlinedButton(
                    onClick = viewModel::showDeleteConfirmation,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.config_remove_all))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Delete confirmation dialog
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirmation,
            title = { Text(stringResource(R.string.config_remove_title)) },
            text = {
                Text(
                    stringResource(R.string.config_remove_message, state.appName.ifEmpty { state.packageName })
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissDeleteConfirmation()
                        viewModel.deleteAllRules()
                    }
                ) {
                    Text(stringResource(R.string.config_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirmation) {
                    Text(stringResource(R.string.config_cancel))
                }
            }
        )
    }
}

// ═══ Reusable composables ═══

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun InfoButton(explanation: String) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(R.string.config_more_info),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.config_got_it))
                }
            },
            text = {
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeatureOverrideCard(
    featureName: String,
    override: FeatureOverride,
    onUpdate: (FeatureOverride) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                featureName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            // Mode selector
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FeatureMode.entries.forEach { mode ->
                    FilterChip(
                        selected = override.mode == mode,
                        onClick = { onUpdate(override.copy(mode = mode)) },
                        label = {
                            Text(
                                featureModeLabel(mode),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            }

            // Expanded settings for every mode that spends a duration.
            if (override.mode.usesDuration) {
                // Delay duration chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { s ->
                        FilterChip(
                            selected = override.delaySeconds == s,
                            onClick = { onUpdate(override.copy(delaySeconds = s)) },
                            label = { Text(stringResource(R.string.config_seconds_short, s)) }
                        )
                    }
                }

                // Auto-kick toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.config_auto_kick), style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = override.autoKickEnabled,
                        onCheckedChange = { onUpdate(override.copy(autoKickEnabled = it)) }
                    )
                }

                if (override.autoKickEnabled) {
                    Text(
                        stringResource(R.string.config_scroll_count, override.autoKickAfter),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = override.autoKickAfter.toFloat(),
                        onValueChange = { onUpdate(override.copy(autoKickAfter = it.toInt())) },
                        valueRange = 5f..100f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MinutesField(
                        value = override.autoKickCooldownMinutesText,
                        onValueChange = { onUpdate(override.copy(autoKickCooldownMinutesText = it)) },
                        labelText = stringResource(R.string.config_cooldown)
                    )
                }
            }
        }
    }
}

/**
 * Simple time selector using hour/minute FilterChips.
 * Cycles hour by +1 and minute in 15-minute increments on click.
 */
@Composable
private fun TimeSelector(
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = true,
            onClick = { onTimeSelected((hour + 1) % 24, minute) },
            label = { Text(stringResource(R.string.config_time_number, hour)) }
        )
        Text(stringResource(R.string.config_time_separator), style = MaterialTheme.typography.bodyLarge)
        FilterChip(
            selected = true,
            onClick = { onTimeSelected(hour, (minute + 15) % 60) },
            label = { Text(stringResource(R.string.config_time_number, minute)) }
        )
    }
}

/**
 * The modes that actually gate an app, in ascending severity.
 *
 * Deliberately NOT `BlockMode.entries`: [BlockMode.NONE] must never appear in a mode picker.
 * At app level it is expressed by the whole-app blocking switch, and for a SCHEDULED override
 * it would be an outright lie — a scheduled rule is additive, so a NONE scheduled rule adds
 * nothing rather than carving out an unblocked window during those hours.
 */
private val BLOCKING_MODES =
    listOf(BlockMode.HARD_BLOCK, BlockMode.DELAY, BlockMode.HOLD, BlockMode.BREATHING)

/**
 * The words on an in-app FEATURE override chip. Separate from [localizedBlockModeLabel] only because
 * [FeatureMode] carries [FeatureMode.INHERIT], which is not a block mode at all; the rest read the
 * same as the app-level picker on purpose.
 */
@Composable
private fun featureModeLabel(mode: FeatureMode): String = when (mode) {
    FeatureMode.INHERIT -> stringResource(R.string.config_mode_inherit)
    FeatureMode.BLOCK -> stringResource(R.string.config_mode_block)
    FeatureMode.DELAY -> stringResource(R.string.config_mode_delay)
    FeatureMode.HOLD -> stringResource(R.string.config_mode_hold)
    FeatureMode.BREATHING -> stringResource(R.string.config_mode_breathing)
}
