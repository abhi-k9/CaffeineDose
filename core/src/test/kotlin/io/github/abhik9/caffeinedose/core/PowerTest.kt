package io.github.abhik9.caffeinedose.core

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class PowerTest {

    private val power = FakePower(pluggedIn = false, level = 50)

    @Nested
    inner class Limits {
        @Test
        fun `nothing is limited by default`() {
            power.level = 1
            power.batterySaver = true
            assertNull(BatteryLimits().limit(power))
        }

        @Test
        fun `low battery, at or below the chosen level`() {
            val limits = BatteryLimits(stopAtPercent = 15)
            power.level = 16
            assertNull(limits.limit(power))
            power.level = 15
            assertEquals(PowerLimit.LOW_BATTERY, limits.limit(power))
        }

        @Test
        fun `an unknown level never limits`() {
            power.level = null
            assertNull(BatteryLimits(stopAtPercent = 15).limit(power))
        }

        @Test
        fun `battery saver, when chosen`() {
            power.batterySaver = true
            assertNull(BatteryLimits(stopAtPercent = 15).limit(power))
            assertEquals(PowerLimit.BATTERY_SAVER, BatteryLimits(stopOnBatterySaver = true).limit(power))
        }

        @Test
        fun `nothing is limited while plugged in`() {
            power.pluggedIn = true
            power.level = 5
            power.batterySaver = true
            assertNull(BatteryLimits(stopAtPercent = 15, stopOnBatterySaver = true).limit(power))
        }
    }

    @Nested
    inner class RunningTimer {
        private val keeper = FakeKeeper()
        private var settings = TimerSettings(10.minutes, 10.minutes, 10.minutes, BatteryLimits(stopAtPercent = 15))
        private val timer = AwakeTimer(keeper, FakeClock(), { settings }, power = power)

        @Test
        fun `start is declined on low battery`() {
            power.level = 10
            assertEquals(StartResult.Declined(PowerLimit.LOW_BATTERY), timer.start())
            assertNull(keeper.held)
        }

        @Test
        fun `the running timer ends once the battery gets low`() {
            assertIs<StartResult.Started>(timer.start())
            assertNull(timer.checkPower())
            power.level = 15
            assertEquals(PowerLimit.LOW_BATTERY, timer.checkPower())
            assertNull(keeper.held)
        }

        @Test
        fun `nothing to end without timer`() {
            power.level = 5
            assertNull(timer.checkPower())
            assertEquals(0, keeper.releases)
        }
    }

    @Test
    fun `a shortcut explains a declined start`() {
        assertEquals(ShortcutReaction.Explain(PowerLimit.BATTERY_SAVER), StartResult.Declined(PowerLimit.BATTERY_SAVER).shortcutReaction())
    }
}
