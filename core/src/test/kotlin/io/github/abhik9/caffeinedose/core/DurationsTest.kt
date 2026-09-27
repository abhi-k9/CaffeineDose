package io.github.abhik9.caffeinedose.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class DurationsTest {

    @Test
    fun `missing duration`() = assertNull(durationOfSeconds(0))

    @Test
    fun `seconds are converted`() {
        assertEquals(10.minutes, durationOfSeconds(600))
        assertEquals((-1).minutes, durationOfSeconds(-60))
    }

    @Test
    fun `seconds are clamped instead of overflowing`() {
        assertEquals(MAX_TIMER_DURATION, durationOfSeconds(Long.MAX_VALUE))
        assertEquals(-MAX_TIMER_DURATION, durationOfSeconds(Long.MIN_VALUE))
    }

    @ParameterizedTest
    @CsvSource("1, 1", "60000, 1", "60001, 2", "1741000, 30", "0, 1", "-5000, 1")
    fun `minutes are rounded up, at least one`(millis: Long, expected: Int) = assertEquals(expected, ceilMinutes(millis.milliseconds))

    @Test
    fun `minutes of huge durations don't overflow`() = assertEquals(24 * 60, ceilMinutes(Long.MAX_VALUE.milliseconds))

    @ParameterizedTest
    @CsvSource("0, 0:00", "1, 0:01", "59000, 0:59", "59001, 1:00", "1745000, 29:05", "3600000, 1:00:00", "7384000, 2:03:04", "-5000, 0:00")
    fun `countdown is rounded up to the second`(
        millis: Long,
        expected: String,
    ) = assertEquals(expected, formatCountdown(millis.milliseconds))

    @Test
    fun `settings are sanitized`() {
        assertEquals(1, DurationSetting.INCREMENT.sanitize(0))
        assertEquals(120, DurationSetting.INCREMENT.sanitize(Int.MAX_VALUE))
        assertEquals(TimerSettings(30.minutes, 10.minutes, 10.minutes), TimerSettings.DEFAULT)
        assertEquals(
            TimerSettings(12.hours, 1.minutes, 1.minutes),
            TimerSettings.ofMinutes {
                if (it ==
                    DurationSetting.INITIAL
                ) {
                    10_000
                } else {
                    -1
                }
            },
        )
    }
}
