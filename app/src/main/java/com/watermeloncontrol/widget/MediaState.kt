package com.watermeloncontrol.widget

data class MediaState(
    val trackTitle: String = "No Media",
    val trackArtist: String = "",
    val isPlaying: Boolean = false,
    val packageName: String? = null,
    val revision: Long = 0L
)

data class SessionMediaSnapshot(
    val title: String?,
    val artist: String?,
    val isPlayingOrTransitioning: Boolean,
    val packageName: String
)

object MediaStateReducer {
    fun fromSession(
        current: MediaState,
        snapshot: SessionMediaSnapshot?,
        forceRedraw: Boolean = false
    ): MediaState {
        val revision = current.revision + if (forceRedraw) 1 else 0
        if (snapshot == null) {
            return MediaState(revision = revision)
        }

        return if (snapshot.isPlayingOrTransitioning) {
            MediaState(
                trackTitle = snapshot.title ?: "Unknown Track",
                trackArtist = snapshot.artist ?: "",
                isPlaying = true,
                packageName = snapshot.packageName,
                revision = revision
            )
        } else {
            MediaState(
                packageName = snapshot.packageName,
                revision = revision
            )
        }
    }

    fun fromExternalUpdate(
        current: MediaState,
        title: String? = null,
        artist: String? = null,
        isPlaying: Boolean? = null,
        packageName: String? = null
    ): MediaState {
        return current.copy(
            trackTitle = title?.takeIf { it.isNotBlank() } ?: current.trackTitle,
            trackArtist = artist ?: current.trackArtist,
            isPlaying = isPlaying ?: current.isPlaying,
            packageName = packageName ?: current.packageName
        )
    }
}
