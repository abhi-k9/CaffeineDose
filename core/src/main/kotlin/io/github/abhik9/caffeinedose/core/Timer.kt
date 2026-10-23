package io.github.abhik9.caffeinedose.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The two device clocks a timer relies on.
 */
interface DeviceClock {
    /** Wall clock time, in milliseconds since the epoch. Can jump (time zone, manual or network changes). */
    fun wallMillis(): Long

    /** Monotonic time since boot, in milliseconds, including deep sleep. */
    fun elapsedMillis(): Long
}

/**
 * A running timer.
 *
 * @property deadline end of the timer on the monotonic [DeviceClock.elapsedMillis] timeline: immune to wall clock
 * changes, it decides when the screen is released.
 * @property endsAt end of the timer on the [DeviceClock.wallMillis] timeline, for display purposes only.
 * @property untilUnplugged whether it ends once the charger is unplugged: its [deadline] is then only a safety net.
 */
data class Timer(val deadline: Long, val endsAt: Long, val untilUnplugged: Boolean = false) {
    fun remaining(clock: DeviceClock): Duration = (deadline - clock.elapsedMillis()).milliseconds
}
