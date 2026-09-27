package io.github.abhik9.caffeinedose.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class EventLogTest {

    private class RecordingLog : EventLog {
        val messages = mutableListOf<String>()
        override fun record(message: () -> String) {
            messages += message()
        }
    }

    private val clock = FakeClock()
    private val keeper = FakeKeeper()
    private val log = RecordingLog()
    private val timer = AwakeTimer(keeper, clock, { TimerSettings.DEFAULT }, log = log)

    @Test
    fun `timer operations and decisions are recorded`() {
        val started = (timer.start(20.minutes) as StartResult.Started).timer
        timer.extend()
        clock.advance(5.minutes)
        timer.end(started.deadline) // stale: extending replaced the timer
        timer.stop()

        assertTrue(log.messages.first().startsWith("start(20m): Started"), log.messages.first())
        assertTrue(log.messages.any { it.startsWith("adjust(10m, mayEnd=true): remaining=20m") })
        assertTrue(log.messages.any { it.startsWith("end(${started.deadline})") })
        assertEquals("stop", log.messages.last())
    }

    @Test
    fun `blocked and refused starts are recorded`() {
        keeper.available = false
        timer.start(5.minutes)
        keeper.available = true
        keeper.permitted = false
        timer.start(5.minutes)
        assertEquals(listOf("start(5m): Blocked(requirement=NOTIFICATIONS)", "start(5m): Refused"), log.messages)
    }

    @Test
    fun `messages are never built without a log`() {
        var built = false
        EventLog.NONE.record {
            built = true
            ""
        }
        assertFalse(built)
    }
}
