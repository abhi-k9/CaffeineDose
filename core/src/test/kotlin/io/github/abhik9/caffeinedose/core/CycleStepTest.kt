package io.github.abhik9.caffeinedose.core

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CycleStepTest {

    private val steps = listOf(CycleStep.Minutes(15), CycleStep.Minutes(60), CycleStep.WhileCharging)
    private val timer = Timer(deadline = 1L, endsAt = 1L)

    @Nested
    inner class Storage {
        @Test
        fun `steps are stored as text`() {
            assertEquals("15,60,charging", CycleStep.encode(steps))
            assertEquals(steps, CycleStep.decode("15,60,charging"))
        }

        @Test
        fun `decoded steps are ordered, without duplicates`() {
            assertEquals(steps, CycleStep.decode("charging,60,15,60"))
        }

        @Test
        fun `invalid steps are dropped, and none at all gives the default ones`() {
            assertEquals(listOf(CycleStep.Minutes(15)), CycleStep.decode("15,7,abc,,-5"))
            assertEquals(CycleStep.DEFAULT, CycleStep.decode(null))
            assertEquals(CycleStep.DEFAULT, CycleStep.decode("1,2,3"))
        }
    }

    @Nested
    inner class NextStep {
        @Test
        fun `without timer, the first step`() {
            assertEquals(CycleStep.Minutes(15), nextCycleStep(steps.reversed(), null, Duration.ZERO, pluggedIn = false))
        }

        @Test
        fun `then the first step longer than what is left`() {
            assertEquals(CycleStep.Minutes(60), nextCycleStep(steps, timer, 15.minutes - 1.seconds, pluggedIn = false))
            // Rounded up to the minute: a timer that has run for a while restarts the step it shortened under.
            assertEquals(CycleStep.Minutes(15), nextCycleStep(steps, timer, 9.minutes + 30.seconds, pluggedIn = false))
        }

        @Test
        fun `while charging is the longest step, only with a charger`() {
            assertEquals(CycleStep.WhileCharging, nextCycleStep(steps, timer, 60.minutes, pluggedIn = true))
            assertNull(nextCycleStep(steps, timer, 60.minutes, pluggedIn = false))
        }

        @Test
        fun `past the longest step, the timer stops`() {
            assertNull(nextCycleStep(steps, timer.copy(untilUnplugged = true), 24.minutes, pluggedIn = true))
            assertNull(nextCycleStep(listOf(CycleStep.Minutes(15)), timer, 2.minutes * 60, pluggedIn = true))
        }

        @Test
        fun `while charging alone is started, to tell why it is declined`() {
            assertEquals(CycleStep.WhileCharging, nextCycleStep(listOf(CycleStep.WhileCharging), null, Duration.ZERO, pluggedIn = false))
        }
    }

    @Nested
    inner class Tap {
        private val keeper = FakeKeeper()
        private val clock = FakeClock()
        private val power = FakePower(pluggedIn = true)
        private var settings = TimerSettings.DEFAULT
        private val awake = AwakeTimer(keeper, clock, { settings }, power = power)

        @Test
        fun `toggles the default timer by default`() {
            assertIs<StartResult.Started>(awake.tap())
            assertEquals(TimerSettings.DEFAULT.initial, awake.remaining())
            assertEquals(StartResult.Stopped, awake.tap())
        }

        @Test
        fun `steps through longer timers, then stops`() {
            settings = TimerSettings.DEFAULT.copy(cycle = steps)
            awake.tap()
            assertEquals(15.minutes, awake.remaining())
            clock.advance(1.seconds)
            awake.tap()
            assertEquals(60.minutes, awake.remaining())
            awake.tap()
            assertTrue(keeper.held!!.untilUnplugged)
            assertEquals(StartResult.Stopped, awake.tap())
            assertNull(keeper.held)
        }
    }
}
