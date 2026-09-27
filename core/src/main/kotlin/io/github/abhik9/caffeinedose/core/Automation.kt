package io.github.abhik9.caffeinedose.core

import kotlin.time.Duration

/**
 * Public automation API (Tasker, `adb`…), documented in the README.
 * Its inputs come from any app on the device: they are validated here, independently of the Android intent plumbing.
 */
object Automation {
    private const val PREFIX = "io.github.abhik9.caffeinedose.action."
    const val ACTION_START = PREFIX + "START"
    const val ACTION_STOP = PREFIX + "STOP"
    const val ACTION_TOGGLE = PREFIX + "TOGGLE"
    const val ACTION_UPDATE = PREFIX + "UPDATE"
    const val ACTION_INCREMENT = PREFIX + "INCREMENT"
    const val ACTION_DECREMENT = PREFIX + "DECREMENT"

    /** Duration in seconds, as a `long` or an `int` extra. */
    const val EXTRA_DURATION = "duration"

    /**
     * @param durationSeconds the [EXTRA_DURATION] extra, `null` when missing. It is clamped to ±[MAX_TIMER_DURATION].
     * @return the command to execute, or `null` for an unknown [action].
     */
    fun parse(action: String?, durationSeconds: Long?): TimerCommand? {
        val duration = durationSeconds?.let(::durationOfSeconds)
        return when (action) {
            ACTION_START -> TimerCommand.Start(duration)
            ACTION_STOP -> TimerCommand.Stop
            ACTION_TOGGLE -> TimerCommand.Toggle
            ACTION_UPDATE -> TimerCommand.Adjust(duration ?: Duration.ZERO)
            ACTION_INCREMENT -> TimerCommand.Extend
            ACTION_DECREMENT -> TimerCommand.Reduce
            else -> null
        }
    }
}
