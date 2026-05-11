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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaState(
    val trackTitle: String = "No Media",
    val trackArtist: String = "",
    val isPlaying: Boolean = false,
    val packageName: String? = null,
    val sessionActivity: PendingIntent? = null
)

class WaterMelonControlListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var refreshJob: Job? = null
    private var actionRefreshJob: Job? = null

    companion object {
        private val _mediaState = MutableStateFlow(MediaState())
        val mediaState = _mediaState.asStateFlow()

        private var mediaController: MediaController? = null

        fun playPause(): Boolean {
            val controller = mediaController ?: return false
            if (_mediaState.value.isPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
            refreshAfterAction()
            return true
        }

        fun next(): Boolean {
            val controller = mediaController ?: return false
            controller.transportControls.skipToNext()
            refreshAfterAction()
            return true
        }

        fun prev(): Boolean {
            val controller = mediaController ?: return false
            controller.transportControls.skipToPrevious()
            refreshAfterAction()
            return true
        }

        fun volumeUp() {
            mediaController?.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        }

        fun volumeDown() {
            mediaController?.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        }

        // Static reference for companion object to refresh from widget button actions.
        var refreshAfterAction: () -> Unit = {}

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
            val s = state?.state ?: PlaybackState.STATE_NONE
            if (s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_NONE) {
                // Current session stopped/paused, check if another app is playing
                val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val componentName = ComponentName(this@WaterMelonControlListener, WaterMelonControlListener::class.java)
                updateController(mediaSessionManager.getActiveSessions(componentName))
            } else {
                refreshStateSoon()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            refreshStateNow()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshAfterAction = { refreshAfterControlAction() }
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
        refreshAfterAction = {}
        refreshJob?.cancel()
        actionRefreshJob?.cancel()
        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        mediaController?.unregisterCallback(callback)
        mediaController = null
        _mediaState.update { MediaState(trackTitle = "No Media", isPlaying = false) }
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshAfterAction = {}
        mediaController?.unregisterCallback(callback)
        mediaController = null
        serviceScope.cancel()
    }

    private fun updateController(controllers: List<MediaController>?) {
        val newController = controllers?.find { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers?.firstOrNull()

        if (newController?.sessionToken != mediaController?.sessionToken) {
            mediaController?.unregisterCallback(callback)
            mediaController = newController
            mediaController?.registerCallback(callback)
        }
        refreshStateNow()
    }

    private fun refreshStateSoon() {
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            delay(100)
            refreshState()
        }
    }

    private fun refreshStateNow() {
        refreshJob?.cancel()
        refreshState()
    }

    private fun refreshAfterControlAction() {
        refreshStateNow()
        actionRefreshJob?.cancel()
        actionRefreshJob = serviceScope.launch {
            delay(150)
            refreshState()
            delay(450)
            refreshState()
        }
    }

    private fun refreshState() {
        val controller = mediaController
        if (controller == null) {
            _mediaState.update { MediaState(trackTitle = "No Media", isPlaying = false) }
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState
        val state = playbackState?.state ?: PlaybackState.STATE_NONE

        val isTransitioning = state == PlaybackState.STATE_BUFFERING ||
                state == PlaybackState.STATE_SKIPPING_TO_NEXT ||
                state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS

        val isActuallyPlaying = state == PlaybackState.STATE_PLAYING
        val isPlayingOrTransitioning = isActuallyPlaying || isTransitioning

        _mediaState.update {
            MediaState(
                trackTitle = if (isPlayingOrTransitioning) (metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: "Unknown Track") else "No Media",
                trackArtist = if (isPlayingOrTransitioning) (metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: "") else "",
                isPlaying = isPlayingOrTransitioning,
                packageName = controller.packageName,
                sessionActivity = controller.sessionActivity
            )
        }
    }
}
