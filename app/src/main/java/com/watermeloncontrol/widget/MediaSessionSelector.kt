package com.watermeloncontrol.widget

object MediaSessionSelector {
    fun <T> select(
        sessions: List<T>?,
        isPlaying: (T) -> Boolean
    ): T? {
        if (sessions.isNullOrEmpty()) return null
        return sessions.firstOrNull(isPlaying) ?: sessions.first()
    }
}
