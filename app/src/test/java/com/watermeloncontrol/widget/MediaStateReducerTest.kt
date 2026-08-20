package com.watermeloncontrol.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStateReducerTest {
    @Test
    fun `playing session maps metadata into widget state`() {
        val result = MediaStateReducer.fromSession(
            current = MediaState(),
            snapshot = SessionMediaSnapshot(
                title = "Track",
                artist = "Artist",
                isPlayingOrTransitioning = true,
                packageName = "player.package"
            )
        )

        assertEquals("Track", result.trackTitle)
        assertEquals("Artist", result.trackArtist)
        assertTrue(result.isPlaying)
        assertEquals("player.package", result.packageName)
        assertEquals(0L, result.revision)
    }

    @Test
    fun `playing session without title uses unknown track`() {
        val result = MediaStateReducer.fromSession(
            MediaState(),
            SessionMediaSnapshot(null, null, true, "player.package")
        )

        assertEquals("Unknown Track", result.trackTitle)
        assertEquals("", result.trackArtist)
        assertTrue(result.isPlaying)
    }

    @Test
    fun `transitioning session remains playing instead of resetting to no media`() {
        val result = MediaStateReducer.fromSession(
            MediaState(trackTitle = "Previous", trackArtist = "Artist", isPlaying = true),
            SessionMediaSnapshot(null, null, true, "player.package")
        )

        assertEquals("Unknown Track", result.trackTitle)
        assertTrue(result.isPlaying)
        assertEquals("player.package", result.packageName)
    }

    @Test
    fun `inactive session maps to no media while retaining package`() {
        val result = MediaStateReducer.fromSession(
            MediaState(trackTitle = "Old", trackArtist = "Old Artist", isPlaying = true),
            SessionMediaSnapshot("Track", "Artist", false, "player.package")
        )

        assertEquals("No Media", result.trackTitle)
        assertEquals("", result.trackArtist)
        assertFalse(result.isPlaying)
        assertEquals("player.package", result.packageName)
    }

    @Test
    fun `missing session clears stale state`() {
        val result = MediaStateReducer.fromSession(
            MediaState("Old", "Artist", true, "player.package", revision = 4),
            snapshot = null
        )

        assertEquals(MediaState(revision = 4), result)
    }

    @Test
    fun `normal unchanged reduction produces equal state`() {
        val current = MediaState("Track", "Artist", true, "player.package", revision = 2)
        val result = MediaStateReducer.fromSession(
            current,
            SessionMediaSnapshot("Track", "Artist", true, "player.package")
        )

        assertEquals(current, result)
    }

    @Test
    fun `forced redraw increments revision once`() {
        val current = MediaState("Track", "Artist", true, "player.package", revision = 2)
        val result = MediaStateReducer.fromSession(
            current,
            SessionMediaSnapshot("Track", "Artist", true, "player.package"),
            forceRedraw = true
        )

        assertEquals(3L, result.revision)
        assertEquals(current.copy(revision = 3), result)
    }

    @Test
    fun `forced redraw without a session clears stale state and increments revision`() {
        val result = MediaStateReducer.fromSession(
            MediaState("Old", "Artist", true, "player.package", revision = 9),
            snapshot = null,
            forceRedraw = true
        )

        assertEquals(MediaState(revision = 10), result)
    }

    @Test
    fun `empty external update is equality stable`() {
        val current = MediaState("Track", "Artist", true, "player.package", revision = 4)

        val result = MediaStateReducer.fromExternalUpdate(current)

        assertEquals(current, result)
    }

    @Test
    fun `external update merges only supplied values`() {
        val current = MediaState("Track", "Artist", true, "player.package", revision = 7)
        val result = MediaStateReducer.fromExternalUpdate(
            current = current,
            title = "",
            artist = "New Artist",
            isPlaying = false
        )

        assertEquals("Track", result.trackTitle)
        assertEquals("New Artist", result.trackArtist)
        assertFalse(result.isPlaying)
        assertEquals("player.package", result.packageName)
        assertEquals(7L, result.revision)
    }
}
