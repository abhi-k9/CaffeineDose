package io.github.abhik9.caffeinedose.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AutomationTest {

    @Test
    fun `start with and without duration`() {
        assertEquals(TimerCommand.Start(10.minutes), Automation.parse(Automation.ACTION_START, 600))
        assertEquals(TimerCommand.Start(null), Automation.parse(Automation.ACTION_START, null))
        assertEquals(TimerCommand.Start(null), Automation.parse(Automation.ACTION_START, 0))
    }

    @Test
    fun `update defaults to a refresh`() {
        assertEquals(TimerCommand.Adjust((-1).minutes), Automation.parse(Automation.ACTION_UPDATE, -60))
        assertEquals(TimerCommand.Adjust(Duration.ZERO), Automation.parse(Automation.ACTION_UPDATE, null))
    }

    @Test
    fun `durations are clamped`() {
        assertEquals(TimerCommand.Start(MAX_TIMER_DURATION), Automation.parse(Automation.ACTION_START, Long.MAX_VALUE))
        assertEquals(TimerCommand.Adjust(-MAX_TIMER_DURATION), Automation.parse(Automation.ACTION_UPDATE, Long.MIN_VALUE))
    }

    @Test
    fun `other actions ignore the duration`() {
        assertEquals(TimerCommand.Stop, Automation.parse(Automation.ACTION_STOP, 60))
        assertEquals(TimerCommand.Toggle, Automation.parse(Automation.ACTION_TOGGLE, null))
        assertEquals(TimerCommand.Extend, Automation.parse(Automation.ACTION_INCREMENT, null))
        assertEquals(TimerCommand.Reduce, Automation.parse(Automation.ACTION_DECREMENT, null))
    }

    @Test
    fun `unknown actions are ignored`() {
        assertNull(Automation.parse(null, 60))
        assertNull(Automation.parse("", null))
        assertNull(Automation.parse("io.github.abhik9.caffeinedose.action.start", null))
        assertNull(Automation.parse("com.example.action.START", null))
    }
}
