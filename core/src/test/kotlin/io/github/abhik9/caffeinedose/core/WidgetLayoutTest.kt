package io.github.abhik9.caffeinedose.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetLayoutTest {

    @Test
    fun `a typical 1x1 widget shows the icon and the state`() {
        assertEquals(WidgetLayout.STACKED, WidgetLayout.forSize(widthDp = 73f, heightDp = 80f))
    }

    @Test
    fun `a widget too small for the state shows the icon alone`() {
        assertEquals(WidgetLayout.ICON, WidgetLayout.forSize(widthDp = 57f, heightDp = 50f))
        assertEquals(WidgetLayout.ICON, WidgetLayout.forSize(widthDp = 48f, heightDp = 100f))
    }

    @Test
    fun `a widget smaller than every layout shows the smallest`() {
        assertEquals(WidgetLayout.ICON, WidgetLayout.forSize(widthDp = 20f, heightDp = 20f))
    }

    @Test
    fun `a wide widget shows the app name next to the state`() {
        assertEquals(WidgetLayout.ROW, WidgetLayout.forSize(widthDp = 150f, heightDp = 60f))
        // Both fit: the larger one wins.
        assertEquals(WidgetLayout.ROW, WidgetLayout.forSize(widthDp = 160f, heightDp = 160f))
    }

    @Test
    fun `a tall narrow widget stacks the icon above the state`() {
        assertEquals(WidgetLayout.STACKED, WidgetLayout.forSize(widthDp = 73f, heightDp = 200f))
    }

    @Test
    fun `what each layout shows`() {
        assertFalse(WidgetLayout.ICON.showsState)
        assertFalse(WidgetLayout.ICON.showsTitle)
        assertTrue(WidgetLayout.STACKED.showsState)
        assertFalse(WidgetLayout.STACKED.showsTitle)
        assertTrue(WidgetLayout.ROW.showsState)
        assertTrue(WidgetLayout.ROW.showsTitle)
    }
}
