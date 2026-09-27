package io.github.abhik9.caffeinedose.core

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AwakeTimerTest {

    private val clock = FakeClock()
    private val keeper = FakeKeeper()
    private var settings = TimerSettings(initial = 30.minutes, increment = 10.minutes, decrement = 5.minutes)
    private var changes = 0
    private val timer = AwakeTimer(keeper, clock, { settings }, onChange = { changes++ })

    private fun StartResult?.started(): Timer = assertIs<StartResult.Started>(this).timer

    /** Remaining time of the held timer. */
    private fun held(): Duration? = keeper.held?.remaining(clock)

    @Nested
    inner class StartAndStop {
        @Test
        fun `start holds the screen`() {
            val started = timer.start(20.minutes).started()

            val millis = 20.minutes.inWholeMilliseconds
            assertEquals(Timer(deadline = clock.elapsed + millis, endsAt = clock.wall + millis), started)
            assertEquals(started, keeper.held)
            assertEquals(started, timer.current())
            assertEquals(1, changes)
        }

        @Test
        fun `start uses the default duration`() {
            timer.start()
            assertEquals(30.minutes, held())
        }

        @Test
        fun `start is capped`() {
            timer.start(Duration.INFINITE)
            assertEquals(MAX_TIMER_DURATION, held())
        }

        @Test
        fun `non positive duration stops the timer`() {
            timer.start()
            assertEquals(StartResult.Stopped, timer.start(Duration.ZERO))
            assertNull(keeper.held)
            assertEquals(1, keeper.releases)
        }

        @Test
        fun `stop releases the screen`() {
            timer.start()
            timer.stop()

            assertNull(keeper.held)
            assertNull(timer.current())
            assertEquals(2, changes)
        }

        @Test
        fun `toggle starts then stops`() {
            timer.toggle().started()
            assertEquals(StartResult.Stopped, timer.toggle())
            assertNull(keeper.held)
        }
    }

    @Nested
    inner class Requirements {
        @Test
        fun `nothing is missing`() = assertNull(timer.missingRequirement())

        @Test
        fun `notifications are required`() {
            keeper.available = false
            assertEquals(Requirement.NOTIFICATIONS, timer.missingRequirement())
        }

        @Test
        fun `start is blocked by a missing requirement`() {
            keeper.available = false
            assertEquals(StartResult.Blocked(Requirement.NOTIFICATIONS), timer.start())
            assertNull(keeper.held)
            assertEquals(0, changes)
        }

        @Test
        fun `start can be refused by the system`() {
            keeper.permitted = false
            assertEquals(StartResult.Refused, timer.start())
            assertNull(keeper.held)
            assertEquals(0, changes)
        }

        @Test
        fun `a refused adjustment keeps the running timer`() {
            val started = timer.start(20.minutes).started()
            keeper.permitted = false
            assertEquals(StartResult.Refused, timer.extend())
            assertEquals(started, keeper.held)
        }

        @Test
        fun `stop is never blocked`() {
            timer.start()
            keeper.available = false
            timer.stop()
            assertNull(keeper.held)
        }
    }

    @Nested
    inner class Adjustments {
        @Test
        fun `remaining time follows the monotonic clock`() {
            timer.start(20.minutes)
            clock.advance(5.minutes)
            clock.wall += 3_600_000 // wall clock changes don't matter
            assertEquals(15.minutes, timer.remaining())
        }

        @Test
        fun `extend adds the increment to the remaining time`() {
            timer.start(20.minutes)
            clock.advance(5.minutes)
            timer.extend().started()
            assertEquals(25.minutes, held())
        }

        @Test
        fun `extend is capped`() {
            timer.start(MAX_TIMER_DURATION)
            timer.extend()
            assertEquals(MAX_TIMER_DURATION, held())
        }

        @Test
        fun `reduce subtracts the decrement from the remaining time`() {
            timer.start(20.minutes)
            timer.reduce()
            assertEquals(15.minutes, held())
        }

        @Test
        fun `reduce never ends the timer`() {
            timer.start(3.minutes)
            timer.reduce().started()
            assertEquals(3.minutes, held())
            assertEquals(0, keeper.releases)
        }

        @Test
        fun `adjust can end the timer`() {
            timer.start(3.minutes)
            assertEquals(StartResult.Stopped, timer.adjust(-5.minutes))
            assertNull(keeper.held)
            assertEquals(1, keeper.releases)
        }

        @Test
        fun `adjustments are ignored without timer`() {
            assertNull(timer.extend())
            assertNull(timer.reduce())
            assertNull(timer.refresh())
            assertNull(keeper.held)
            assertEquals(0, changes)
        }

        @Test
        fun `adjustments are ignored once the deadline is reached`() {
            timer.start(1.minutes)
            clock.advance(1.minutes + 1.seconds)
            assertNull(timer.refresh())
            assertNull(timer.adjust(-5.minutes))
            assertEquals(0, keeper.releases)
        }

        @Test
        fun `refresh re-holds the running timer`() {
            timer.start(20.minutes)
            clock.advance(1.minutes)
            timer.refresh().started()
            assertEquals(19.minutes, held())
        }

        @Test
        fun `adjustments read the latest settings`() {
            timer.start(20.minutes)
            settings = settings.copy(increment = 1.minutes)
            timer.extend()
            assertEquals(21.minutes, held())
        }
    }

    @Nested
    inner class End {
        @Test
        fun `end releases the matching timer`() {
            val started = timer.start(20.minutes).started()
            assertTrue(timer.end(started.deadline))
            assertNull(keeper.held)
            assertEquals(2, changes)
        }

        @Test
        fun `a stale signal never ends a newer timer`() {
            val old = timer.start(1.minutes).started()
            clock.advance(10.seconds)
            val new = timer.extend().started()
            assertFalse(timer.end(old.deadline))
            assertEquals(new, keeper.held)
            assertEquals(0, keeper.releases)
        }

        @Test
        fun `end without timer does nothing`() {
            assertFalse(timer.end(123L))
            assertEquals(0, keeper.releases)
        }

        @Test
        fun `expire only once the deadline is reached`() {
            timer.start(20.minutes)
            clock.advance(20.minutes - 1.seconds)
            assertFalse(timer.expireIfDue())
            assertEquals(1.seconds, held())
            clock.advance(1.seconds)
            assertTrue(timer.expireIfDue())
            assertNull(keeper.held)
        }

        @Test
        fun `expire without timer does nothing`() = assertFalse(timer.expireIfDue())
    }

    @Nested
    inner class Commands {
        @Test
        fun `start with and without duration`() {
            timer.execute(TimerCommand.Start(10.minutes))
            assertEquals(10.minutes, held())
            timer.execute(TimerCommand.Start(null))
            assertEquals(30.minutes, held())
        }

        @Test
        fun `adjust, extend, reduce`() {
            timer.execute(TimerCommand.Start(20.minutes))
            timer.execute(TimerCommand.Adjust(-1.minutes))
            assertEquals(19.minutes, held())
            timer.execute(TimerCommand.Extend)
            assertEquals(29.minutes, held())
            timer.execute(TimerCommand.Reduce)
            assertEquals(24.minutes, held())
        }

        @Test
        fun `toggle and stop`() {
            timer.execute(TimerCommand.Toggle).started()
            assertEquals(StartResult.Stopped, timer.execute(TimerCommand.Stop))
            assertNull(keeper.held)
        }
    }
}
