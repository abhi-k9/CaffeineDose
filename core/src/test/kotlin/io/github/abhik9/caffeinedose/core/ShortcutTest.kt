package io.github.abhik9.caffeinedose.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ShortcutTest {

    @Test
    fun `a started timer is shown`() {
        val timer = Timer(deadline = 1_000L, endsAt = 2_000L)
        assertEquals(ShortcutReaction.Show(timer), StartResult.Started(timer).shortcutReaction())
    }

    @Test
    fun `a stopped timer shows the idle state`() {
        assertEquals(ShortcutReaction.Show(null), StartResult.Stopped.shortcutReaction())
    }

    @Test
    fun `a missing requirement is resolved`() {
        assertEquals(
            ShortcutReaction.Resolve(Requirement.NOTIFICATIONS),
            StartResult.Blocked(Requirement.NOTIFICATIONS).shortcutReaction(),
        )
    }

    @Test
    fun `a refused start is retried from the foreground`() {
        assertEquals(ShortcutReaction.StartFromForeground, StartResult.Refused.shortcutReaction())
    }
}
