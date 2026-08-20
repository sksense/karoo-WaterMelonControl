package com.watermeloncontrol.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSessionSelectorTest {
    private data class Session(val id: String, val playing: Boolean)

    @Test
    fun `playing session wins over earlier paused session`() {
        val sessions = listOf(
            Session("paused", playing = false),
            Session("playing", playing = true)
        )

        val selected = MediaSessionSelector.select(sessions) { it.playing }

        assertEquals("playing", selected?.id)
    }

    @Test
    fun `first session is stable fallback when none are playing`() {
        val sessions = listOf(
            Session("first", playing = false),
            Session("second", playing = false)
        )

        val selected = MediaSessionSelector.select(sessions) { it.playing }

        assertEquals("first", selected?.id)
    }

    @Test
    fun `empty and null session lists select nothing`() {
        assertNull(MediaSessionSelector.select(emptyList<Session>()) { it.playing })
        assertNull(MediaSessionSelector.select<Session>(null) { it.playing })
    }
}
