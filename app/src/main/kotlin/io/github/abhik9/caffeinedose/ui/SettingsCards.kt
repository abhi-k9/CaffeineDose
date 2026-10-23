package io.github.abhik9.caffeinedose.ui

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.core.BatteryLimits
import io.github.abhik9.caffeinedose.core.CycleStep
import io.github.abhik9.caffeinedose.core.DurationSetting
import io.github.abhik9.caffeinedose.core.ordered
import io.github.abhik9.caffeinedose.settings.ThemeMode

@get:StringRes
private val DurationSetting.title: Int
    get() = when (this) {
        DurationSetting.INITIAL -> R.string.settings_initial
        DurationSetting.INCREMENT -> R.string.settings_increment
        DurationSetting.DECREMENT -> R.string.settings_decrement
    }

@get:StringRes
private val DurationSetting.description: Int
    get() = when (this) {
        DurationSetting.INITIAL -> R.string.settings_initial_description
        DurationSetting.INCREMENT -> R.string.settings_increment_description
        DurationSetting.DECREMENT -> R.string.settings_decrement_description
    }

@get:StringRes
private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

@Composable
internal fun DurationsCard(state: MainUiState, onEdit: (DurationSetting) -> Unit, modifier: Modifier = Modifier) = SettingsCard(modifier) {
    DurationSetting.entries.forEachIndexed { index, setting ->
        if (index > 0) SettingsDivider()
        val minutes = state.settings.minutes(setting)
        ListItem(
            headlineContent = { Text(stringResource(setting.title)) },
            supportingContent = { Text(stringResource(setting.description)) },
            trailingContent = {
                SettingValue(
                    when (setting) {
                        DurationSetting.INITIAL -> formatMinutes(minutes)
                        DurationSetting.INCREMENT -> stringResource(R.string.action_extend, minutes)
                        DurationSetting.DECREMENT -> stringResource(R.string.action_reduce, minutes)
                    },
                )
            },
            colors = settingsItemColors(),
            modifier = Modifier.clickable { onEdit(setting) },
        )
    }
}

@Composable
internal fun AppearanceCard(state: MainUiState, actions: MainActions, modifier: Modifier = Modifier) = SettingsCard(modifier) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.theme_title)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.settings.themeMode == mode,
                        onClick = { actions.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        label = { Text(stringResource(mode.label)) },
                    )
                }
            }
        },
        colors = settingsItemColors(),
    )
    if (state.dynamicColorAvailable) {
        SettingsDivider()
        SwitchItem(
            title = R.string.dynamic_color_title,
            description = R.string.dynamic_color_description,
            checked = state.settings.dynamicColor,
            onCheckedChange = actions::setDynamicColor,
        )
    }
}

@Composable
internal fun BehaviorCard(
    state: MainUiState,
    actions: MainActions,
    onAllowOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsCard(modifier) {
    SwitchItem(
        title = R.string.stop_on_screen_off_title,
        description = R.string.stop_on_screen_off_description,
        checked = state.settings.stopOnScreenOff,
        onCheckedChange = actions::setStopOnScreenOff,
    )
    SettingsDivider()
    // A system permission rather than a setting: both ways lead to its settings screen.
    SwitchItem(
        title = R.string.overlay_title,
        description = R.string.overlay_description,
        checked = state.overlayAllowed,
        onCheckedChange = { onAllowOverlay() },
    )
}

@Composable
internal fun DiagnosticsCard(
    state: MainUiState,
    actions: MainActions,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsCard(modifier) {
    SwitchItem(
        title = R.string.diagnostics_title,
        description = R.string.diagnostics_description,
        checked = state.settings.diagnosticsEnabled,
        onCheckedChange = actions::setDiagnosticsEnabled,
    )
    // Only once something has been recorded.
    if (state.diagnosticsLogBytes > 0) {
        val size = Formatter.formatShortFileSize(LocalContext.current, state.diagnosticsLogBytes)
        SettingsDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.diagnostics_export)) },
            supportingContent = { Text(stringResource(R.string.diagnostics_export_description, size)) },
            colors = settingsItemColors(),
            modifier = Modifier.clickable(onClick = onExport),
        )
        SettingsDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.diagnostics_clear)) },
            colors = settingsItemColors(),
            modifier = Modifier.clickable(onClick = actions::clearDiagnostics),
        )
    }
}

@Composable
internal fun AutomationCard(state: MainUiState, actions: MainActions, modifier: Modifier = Modifier) = SettingsCard(modifier) {
    SwitchItem(
        title = R.string.automation_title,
        description = R.string.automation_description,
        checked = state.settings.automationEnabled,
        onCheckedChange = actions::setAutomationEnabled,
    )
}

@Composable
private fun SwitchItem(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = ListItem(
    headlineContent = { Text(stringResource(title)) },
    supportingContent = { Text(stringResource(description)) },
    trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    colors = settingsItemColors(),
    modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
)

/** The current value of a setting, at the end of its item. */
@Composable
private fun SettingValue(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

@Composable
internal fun ShortcutsCard(state: MainUiState, actions: MainActions, onEditSteps: () -> Unit, modifier: Modifier = Modifier) =
    SettingsCard(modifier) {
        SwitchItem(
            title = R.string.cycle_title,
            description = R.string.cycle_description,
            checked = state.settings.cycleEnabled,
            onCheckedChange = actions::setCycleEnabled,
        )
        if (state.settings.cycleEnabled) {
            SettingsDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.cycle_steps)) },
                supportingContent = { Text(state.settings.cycleSteps.map { cycleStepLabel(it) }.joinToString(" → ")) },
                colors = settingsItemColors(),
                modifier = Modifier.clickable(onClick = onEditSteps),
            )
        }
    }

@Composable
private fun cycleStepLabel(step: CycleStep): String = when (step) {
    is CycleStep.Minutes -> formatMinutes(step.minutes)
    CycleStep.WhileCharging -> stringResource(R.string.cycle_step_while_charging)
}

/** Saves a selection of steps, possibly empty, as [CycleStep.encode]d text. */
private val CycleStepsSaver = Saver<Set<CycleStep>, String>(
    save = { CycleStep.encode(it.toList()) },
    // decode() falls back to the default steps when there are none.
    restore = { text -> if (text.isEmpty()) emptySet() else CycleStep.decode(text).toSet() },
)

/** Chooses the steps of "Tap again for longer", among [CycleStep.CHOICES]: at least one. */
@Composable
internal fun CycleStepsDialog(initial: List<CycleStep>, onDismiss: () -> Unit, onConfirm: (List<CycleStep>) -> Unit) {
    // Kept across configuration changes, like the dialog itself.
    var selected by rememberSaveable(stateSaver = CycleStepsSaver) { mutableStateOf(initial.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cycle_steps)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CycleStep.CHOICES.forEach { step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = step in selected,
                                role = Role.Checkbox,
                                onValueChange = { checked -> selected = if (checked) selected + step else selected - step },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = step in selected, onCheckedChange = null)
                        Text(cycleStepLabel(step), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList().ordered()) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
internal fun BatteryCard(state: MainUiState, actions: MainActions, onEditLevel: () -> Unit, modifier: Modifier = Modifier) =
    SettingsCard(modifier) {
        SwitchItem(
            title = R.string.low_battery_title,
            description = R.string.low_battery_description,
            checked = state.settings.lowBatteryStop,
            onCheckedChange = actions::setLowBatteryStop,
        )
        if (state.settings.lowBatteryStop) {
            SettingsDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.low_battery_level)) },
                supportingContent = { Text(stringResource(R.string.low_battery_level_description)) },
                trailingContent = { SettingValue(formatPercent(state.settings.lowBatteryPercent)) },
                colors = settingsItemColors(),
                modifier = Modifier.clickable(onClick = onEditLevel),
            )
        }
        SettingsDivider()
        SwitchItem(
            title = R.string.battery_saver_title,
            description = R.string.battery_saver_description,
            checked = state.settings.stopOnBatterySaver,
            onCheckedChange = actions::setStopOnBatterySaver,
        )
    }

@Composable
internal fun MinutesDialog(setting: DurationSetting, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) = NumberDialog(
    title = stringResource(setting.title),
    initial = initial,
    range = setting.range,
    unit = stringResource(R.string.unit_minutes),
    rangeText = stringResource(R.string.settings_range, formatMinutes(setting.range.first), formatMinutes(setting.range.last)),
    onDismiss = onDismiss,
    onConfirm = onConfirm,
)

@Composable
internal fun BatteryLevelDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) = NumberDialog(
    title = stringResource(R.string.low_battery_level),
    initial = initial,
    range = BatteryLimits.PERCENT_RANGE,
    unit = stringResource(R.string.unit_percent),
    rangeText = stringResource(
        R.string.settings_range,
        formatPercent(BatteryLimits.PERCENT_RANGE.first),
        formatPercent(BatteryLimits.PERCENT_RANGE.last),
    ),
    onDismiss = onDismiss,
    onConfirm = onConfirm,
)

/** Asks for a whole number in [range], e.g. a duration in minutes. */
@Composable
private fun NumberDialog(
    title: String,
    initial: Int,
    range: IntRange,
    unit: String,
    rangeText: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()?.takeIf { it in range }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(4) },
                singleLine = true,
                suffix = { Text(unit) },
                isError = value == null,
                supportingText = { Text(rangeText) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { value?.let(onConfirm) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
