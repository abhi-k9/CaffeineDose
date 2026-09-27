package io.github.abhik9.caffeinedose.core

import kotlin.time.Duration

class FakeClock(var wall: Long = 1_000_000L, var elapsed: Long = 50_000L) : DeviceClock {
    override fun wallMillis() = wall
    override fun elapsedMillis() = elapsed

    fun advance(duration: Duration) {
        wall += duration.inWholeMilliseconds
        elapsed += duration.inWholeMilliseconds
    }
}

class FakeKeeper(var available: Boolean = true, var permitted: Boolean = true) : ScreenKeeper {
    var held: Timer? = null
    var releases = 0

    override fun isAvailable() = available
    override fun current() = held

    override fun hold(timer: Timer): Boolean {
        if (permitted) held = timer
        return permitted
    }

    override fun release() {
        held = null
        releases++
    }
}
