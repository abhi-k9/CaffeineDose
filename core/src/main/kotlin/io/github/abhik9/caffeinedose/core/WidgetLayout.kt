package io.github.abhik9.caffeinedose.core

/**
 * Layouts of the home screen widget, from the smallest. A widget shows the largest layout that fits its size, or the
 * smallest one when none does: the rule Android 12 and later apply to responsive widget layouts.
 */
enum class WidgetLayout(
    val minWidthDp: Float,
    val minHeightDp: Float,
    /** Whether it says how to start a timer, or when it ends. */
    val showsState: Boolean,
    /** Whether it shows the app name. */
    val showsTitle: Boolean,
) {
    /** The icon alone: the colors tell the state, and so does the content description. */
    ICON(minWidthDp = 40f, minHeightDp = 40f, showsState = false, showsTitle = false),

    /** The icon above the state: the default 1×1 widget, on most phones. */
    STACKED(minWidthDp = 56f, minHeightDp = 64f, showsState = true, showsTitle = false),

    /** The icon next to the app name and the state, once at least two cells wide. */
    ROW(minWidthDp = 140f, minHeightDp = 48f, showsState = true, showsTitle = true),
    ;

    private val area get() = minWidthDp * minHeightDp

    companion object {
        fun forSize(widthDp: Float, heightDp: Float): WidgetLayout =
            entries.filter { widthDp >= it.minWidthDp && heightDp >= it.minHeightDp }.maxByOrNull { it.area }
                ?: entries.minBy { it.area }
    }
}
