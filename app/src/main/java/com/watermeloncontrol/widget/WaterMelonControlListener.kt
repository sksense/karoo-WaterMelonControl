package com.watermeloncontrol.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaState(
    val trackTitle: String = "No Media",
    val trackArtist: String = "",
    val isPlaying: Boolean = false,
    val packageName: String? = null,
    val sessionActivity: PendingIntent? = null
)

class WaterMelonControlListener : NotificationListenerService() {

    companion object {
        private val _mediaState = MutableStateFlow(MediaState())
        val mediaState = _mediaState.asStateFlow()

        private var mediaController: MediaController? = null

        fun playPause() {
            val controller = mediaController ?: return
            if (_mediaState.value.isPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }

        fun next() {
            mediaController?.transportControls?.skipToNext()
        }

        fun prev() {
            mediaController?.transportControls?.skipToPrevious()
        }

        fun volumeUp() {
            mediaController?.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        }

        fun volumeDown() {
            mediaController?.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        }

        fun sendMediaButton(context: Context, keyCode: Int) {
            val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON)
            val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
            intent.putExtra(android.content.Intent.EXTRA_KEY_EVENT, event)
            context.sendOrderedBroadcast(intent, null)

            val eventUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
            val intentUp = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON)
            intentUp.putExtra(android.content.Intent.EXTRA_KEY_EVENT, eventUp)
            context.sendOrderedBroadcast(intentUp, null)
        }
    }

    private val activeSessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateController(controllers)
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            if (!isPlaying) {
                _mediaState.value = _mediaState.value.copy(
                    trackTitle = "No Media",
                    trackArtist = "",
                    isPlaying = false
                )
            } else {
                _mediaState.value = _mediaState.value.copy(
                    isPlaying = true
                )
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (_mediaState.value.isPlaying) {
                _mediaState.value = _mediaState.value.copy(
                    trackTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Track",
                    trackArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                )
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, WaterMelonControlListener::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionsChangedListener, componentName)
            updateController(mediaSessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            Log.e("WaterMelonControl", "NotificationListener lacks permission to access MediaSessionManager")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        mediaController?.unregisterCallback(callback)
    }

    private fun updateController(controllers: List<MediaController>?) {
        val newController = controllers?.find { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers?.firstOrNull()

        if (newController?.packageName != mediaController?.packageName) {
            mediaController?.unregisterCallback(callback)
            mediaController = newController
            mediaController?.registerCallback(callback)
        }

        refreshState()
    }

    private fun refreshState() {
        val metadata = mediaController?.metadata
        val playbackState = mediaController?.playbackState
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        _mediaState.value = MediaState(
            trackTitle = if (isPlaying) (metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: "Unknown Track") else "No Media",
            trackArtist = if (isPlaying) (metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: "Unknown Artist") else "",
            isPlaying = isPlaying,
            packageName = mediaController?.packageName,
            sessionActivity = mediaController?.sessionActivity
        )
    }
}
