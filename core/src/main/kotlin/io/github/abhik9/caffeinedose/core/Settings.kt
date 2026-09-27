package io.github.abhik9.caffeinedose.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * User configurable durations, in whole minutes.
 */
enum class DurationSetting(val defaultMinutes: Int, val range: IntRange) {
    /** Duration of a timer started without an explicit duration, e.g. from the Quick Settings tile. */
    INITIAL(defaultMinutes = 30, range = 1..12 * 60),

    /** Step of the "extend" action. */
    INCREMENT(defaultMinutes = 10, range = 1..120),

    /** Step of the "reduce" action. */
    DECREMENT(defaultMinutes = 10, range = 1..120),
    ;

    /** Brings a stored or user provided value back into [range]. */
    fun sanitize(minutes: Int): Int = minutes.coerceIn(range)
}

/**
 * Snapshot of the durations used by [AwakeTimer].
 */
data class TimerSettings(val initial: Duration, val increment: Duration, val decrement: Duration) {
    companion object {
        fun ofMinutes(minutes: (DurationSetting) -> Int) = TimerSettings(
            initial = DurationSetting.INITIAL.sanitize(minutes(DurationSetting.INITIAL)).minutes,
            increment = DurationSetting.INCREMENT.sanitize(minutes(DurationSetting.INCREMENT)).minutes,
            decrement = DurationSetting.DECREMENT.sanitize(minutes(DurationSetting.DECREMENT)).minutes,
        )

        val DEFAULT = ofMinutes { it.defaultMinutes }
    }
}
