package io.github.abhik9.caffeinedose.core

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    @Nested
    inner class UntilUnplugged {
        private val keeper = FakeKeeper()
        private val clock = FakeClock()
        private val timer = AwakeTimer(keeper, clock, { TimerSettings.DEFAULT }, power = power)

        @Test
        fun `needs a charger`() {
            assertEquals(StartResult.Declined(PowerLimit.UNPLUGGED), timer.startUntilUnplugged())
            assertNull(keeper.held)
        }

        @Test
        fun `holds the screen at most for the longest timer`() {
            power.pluggedIn = true
            val started = assertIs<StartResult.Started>(timer.startUntilUnplugged()).timer
            assertTrue(started.untilUnplugged)
            assertEquals(MAX_TIMER_DURATION, timer.remaining())
        }

        @Test
        fun `ends once unplugged`() {
            power.pluggedIn = true
            timer.startUntilUnplugged()
            assertNull(timer.checkPower())
            power.pluggedIn = false
            assertEquals(PowerLimit.UNPLUGGED, timer.checkPower())
            assertNull(keeper.held)
        }

        @Test
        fun `has no time to adjust, it is only re-displayed`() {
            power.pluggedIn = true
            val started = assertIs<StartResult.Started>(timer.startUntilUnplugged()).timer
            clock.advance(5.minutes)
            assertEquals(StartResult.Started(started), timer.extend())
            assertEquals(StartResult.Started(started), timer.reduce())
            assertEquals(started, keeper.held)
        }

        @Test
        fun `a timed start replaces it`() {
            power.pluggedIn = true
            timer.startUntilUnplugged()
            val started = assertIs<StartResult.Started>(timer.start(10.minutes)).timer
            assertFalse(started.untilUnplugged)
        }

        @Test
        fun `is started by automation`() {
            power.pluggedIn = true
            assertIs<StartResult.Started>(timer.execute(TimerCommand.StartUntilUnplugged))
            assertTrue(keeper.held!!.untilUnplugged)
        }
    }

    @Test
    fun `a shortcut explains a declined start`() {
        assertEquals(ShortcutReaction.Explain(PowerLimit.BATTERY_SAVER), StartResult.Declined(PowerLimit.BATTERY_SAVER).shortcutReaction())
    }
}
