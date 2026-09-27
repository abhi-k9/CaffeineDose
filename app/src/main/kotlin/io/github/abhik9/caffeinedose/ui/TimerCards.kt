package io.github.abhik9.caffeinedose.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.core.DurationSetting
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.core.ceilMinutes
import io.github.abhik9.caffeinedose.core.formatCountdown
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val PRESETS_MINUTES = listOf(5, 15, 30, 60, 120, 240)

/**
 * Remaining time of [timer], ticking every second, aligned on its deadline so that the displayed seconds are exact.
 * @param elapsedNow the `elapsedRealtime` clock.
 */
@Composable
private fun rememberRemaining(timer: Timer, elapsedNow: () -> Long): Duration {
    val remaining by produceState((timer.deadline - elapsedNow()).milliseconds, timer.deadline) {
        while (true) {
            value = (timer.deadline - elapsedNow()).milliseconds
            val untilNextSecond = value.inWholeMilliseconds % 1_000
            delay(if (untilNextSecond > 0) untilNextSecond.milliseconds else 1.seconds)
        }
    }
    return remaining
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimerCard(state: MainUiState, actions: MainActions, elapsedNow: () -> Long, modifier: Modifier = Modifier) = Card(
    modifier = modifier,
    colors = if (state.timer != null) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    },
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val timer = state.timer
        if (timer == null) {
            Text(stringResource(R.string.timer_idle_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.timer_idle_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        val remaining = rememberRemaining(timer, elapsedNow)
        val remainingDescription = stringResource(R.string.timer_remaining_description, formatMinutes(ceilMinutes(remaining)))
        val context = LocalContext.current
        val endsAt = remember(timer.endsAt) { DateFormat.getTimeFormat(context).format(Date(timer.endsAt)) }
        val increment = state.settings.minutes(DurationSetting.INCREMENT)
        val decrement = state.settings.minutes(DurationSetting.DECREMENT)

        Text(stringResource(R.string.timer_remaining), style = MaterialTheme.typography.labelLarge)
        Text(
            text = formatCountdown(remaining),
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            // Don't read every tick out loud.
            modifier = Modifier.semantics { contentDescription = remainingDescription },
        )
        Text(stringResource(R.string.timer_ends_at, endsAt), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = actions::reduce, enabled = remaining > decrement.minutes) {
                Text(stringResource(R.string.action_reduce, decrement))
            }
            FilledTonalButton(onClick = actions::extend) { Text(stringResource(R.string.action_extend, increment)) }
            OutlinedButton(onClick = actions::stop) { Text(stringResource(R.string.action_stop)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StartTimerCard(state: MainUiState, actions: MainActions, elapsedNow: () -> Long, modifier: Modifier = Modifier) = Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val running = state.timer != null
        Text(
            text = stringResource(if (running) R.string.start_timer_title_running else R.string.start_timer_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        // Prefilled with the remaining time (rounded up to the minute) when running, otherwise the default duration.
        val initial = remember {
            state.timer?.let { ceilMinutes((it.deadline - elapsedNow()).milliseconds) } ?: state.settings.minutes(DurationSetting.INITIAL)
        }
        val picker = rememberTimePickerState(initialHour = (initial / 60).coerceAtMost(23), initialMinute = initial % 60, is24Hour = true)
        val minutes = picker.hour * 60 + picker.minute
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS_MINUTES.forEach { preset ->
                FilterChip(
                    selected = minutes == preset,
                    onClick = {
                        picker.hour = preset / 60
                        picker.minute = preset % 60
                    },
                    label = { Text(formatMinutes(preset)) },
                )
            }
        }
        TimeInput(state = picker, modifier = Modifier.align(Alignment.CenterHorizontally))
        Button(onClick = { actions.start(minutes) }, enabled = minutes > 0, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (running) R.string.action_set else R.string.action_start, formatMinutes(minutes)))
        }
    }
}
