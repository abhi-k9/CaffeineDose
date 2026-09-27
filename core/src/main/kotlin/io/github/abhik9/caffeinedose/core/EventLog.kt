package io.github.abhik9.caffeinedose.core

/**
 * Records what happens, for diagnostics.
 * Messages are lazy: implementations only build them when recording is enabled.
 */
fun interface EventLog {
    fun record(message: () -> String)

    companion object {
        /** Records nothing. */
        val NONE = EventLog { }
    }
}
