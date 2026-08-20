package com.watermeloncontrol.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import androidx.core.content.IntentCompat

class Karoo2MediaBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) return

        when (intent.action) {
            ACTION_AVRCP_TRACK_EVENT,
            ACTION_AVRCP_PLAYBACK_STATUS_CHANGED -> handleAvrcpEvent(intent)

            ACTION_LEGACY_META_CHANGED,
            ACTION_LEGACY_PLAYSTATE_CHANGED,
            ACTION_LEGACY_PLAYBACK_COMPLETE -> handleLegacyMediaEvent(intent)
        }
    }

    private fun handleAvrcpEvent(intent: Intent) {
        val metadata = IntentCompat.getParcelableExtra(intent, EXTRA_AVRCP_METADATA, MediaMetadata::class.java)
        val playbackState = IntentCompat.getParcelableExtra(intent, EXTRA_AVRCP_PLAYBACK, PlaybackState::class.java)
        val state = playbackState?.state

        WaterMelonControlListener.updateExternalMediaState(
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            isPlaying = state?.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING },
            packageName = PACKAGE_BLUETOOTH_AVRCP
        )
    }

    private fun handleLegacyMediaEvent(intent: Intent) {
        val title = intent.getStringExtra("track")
            ?: intent.getStringExtra("title")
            ?: intent.getStringExtra("song")
        val artist = intent.getStringExtra("artist")
        val isPlaying = when (intent.action) {
            ACTION_LEGACY_PLAYBACK_COMPLETE -> false
            else -> if (intent.hasExtra("playing")) intent.getBooleanExtra("playing", false) else null
        }

        WaterMelonControlListener.updateExternalMediaState(
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            packageName = intent.`package`
        )
    }

    companion object {
        private const val PACKAGE_BLUETOOTH_AVRCP = "android.bluetooth.avrcp"

        const val ACTION_AVRCP_TRACK_EVENT =
            "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT"
        const val ACTION_AVRCP_PLAYBACK_STATUS_CHANGED =
            "android.bluetooth.avrcp-controller.profile.action.PLAYBACK_STATUS_CHANGED"
        private const val EXTRA_AVRCP_METADATA =
            "android.bluetooth.avrcp-controller.profile.extra.METADATA"
        private const val EXTRA_AVRCP_PLAYBACK =
            "android.bluetooth.avrcp-controller.profile.extra.PLAYBACK"

        const val ACTION_LEGACY_META_CHANGED = "com.android.music.metachanged"
        const val ACTION_LEGACY_PLAYSTATE_CHANGED = "com.android.music.playstatechanged"
        const val ACTION_LEGACY_PLAYBACK_COMPLETE = "com.android.music.playbackcomplete"
    }
}
