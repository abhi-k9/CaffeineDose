package io.github.abhik9.caffeinedose.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Upper bound of any timer, which also guards against overflows from externally provided durations. */
val MAX_TIMER_DURATION: Duration = 24.hours

/**
 * Converts an externally provided number of [seconds] (e.g. from an automation intent) to a [Duration] clamped to
 * ±[MAX_TIMER_DURATION].
 * @return `null` when the duration is missing (`0`).
 */
fun durationOfSeconds(seconds: Long): Duration? {
    if (seconds == 0L) return null
    val max = MAX_TIMER_DURATION.inWholeSeconds
    return seconds.coerceIn(-max, max).seconds
}

/**
 * Rounds [duration] up to the next whole minute, e.g. `29:01` → `30`. Always at least `1`.
 */
fun ceilMinutes(duration: Duration): Int {
    val millis = duration.inWholeMilliseconds.coerceIn(0, MAX_TIMER_DURATION.inWholeMilliseconds)
    val minute = 1.minutes.inWholeMilliseconds
    return ((millis + minute - 1) / minute).toInt().coerceAtLeast(1)
}

/**
 * Formats a countdown rounded up to the second: `H:MM:SS`, or `M:SS` under an hour. Negative values show `0:00`.
 */
fun formatCountdown(remaining: Duration): String {
    val millis = remaining.inWholeMilliseconds.coerceAtLeast(0)
    val second = 1.seconds.inWholeMilliseconds
    val totalSeconds = millis / second + if (millis % second > 0) 1 else 0
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}" else "$minutes:${seconds.twoDigits()}"
}

private fun Long.twoDigits() = toString().padStart(2, '0')
