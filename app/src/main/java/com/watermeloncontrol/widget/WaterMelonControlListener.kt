package com.watermeloncontrol.widget

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.KeyEvent
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
    val revision: Long = 0L
)

class WaterMelonControlListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var refreshJob: Job? = null
    private var settleRefreshJob: Job? = null
    private var activeSessionsListenerRegistered = false

    companion object {
        private const val TAG = "WaterMelonControl"
        private val _mediaState = MutableStateFlow(MediaState())
        val mediaState = _mediaState.asStateFlow()

        private var mediaController: MediaController? = null

        fun playPause(context: Context) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                debouncedRefreshStateStatic()
                return
            }

            val controller = mediaController
            if (controller == null) {
                sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                return
            }

            if (_mediaState.value.isPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
            debouncedRefreshStateStatic()
        }

        fun next(context: Context) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                mediaCommandRefreshStatic()
                return
            }

            val controller = mediaController
            if (controller == null) {
                sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                return
            }

            controller.transportControls.skipToNext()
            mediaCommandRefreshStatic()
        }

        fun prev(context: Context) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                mediaCommandRefreshStatic()
                return
            }

            val controller = mediaController
            if (controller == null) {
                sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                return
            }

            controller.transportControls.skipToPrevious()
            mediaCommandRefreshStatic()
        }

        fun volumeUp(context: Context) {
            adjustMusicVolume(context, AudioManager.ADJUST_RAISE)
        }

        fun volumeDown(context: Context) {
            adjustMusicVolume(context, AudioManager.ADJUST_LOWER)
        }

        // Static references for companion object to trigger service refreshes
        var debouncedRefreshStateStatic: () -> Unit = {}
        var mediaCommandRefreshStatic: () -> Unit = {}

        private fun adjustMusicVolume(context: Context, direction: Int) {
            val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
        }

        fun updateExternalMediaState(
            title: String? = null,
            artist: String? = null,
            isPlaying: Boolean? = null,
            packageName: String? = null
        ) {
            _mediaState.update { current ->
                MediaState(
                    trackTitle = title?.takeIf { it.isNotBlank() } ?: current.trackTitle,
                    trackArtist = artist ?: current.trackArtist,
                    isPlaying = isPlaying ?: current.isPlaying,
                    packageName = packageName ?: current.packageName,
                    revision = current.revision
                )
            }
        }

        private fun sendMediaButton(context: Context, keyCode: Int) {
            val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private val activeSessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateController(controllers)
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val s = state?.state ?: PlaybackState.STATE_NONE
            if (s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_NONE) {
                // Current session stopped/paused, check if another app is playing.
                updateController(queryActiveSessions())
            } else {
                debouncedRefreshState()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            debouncedRefreshState()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        debouncedRefreshStateStatic = { debouncedRefreshState() }
        mediaCommandRefreshStatic = { refreshAfterMediaCommand() }
        try {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, WaterMelonControlListener::class.java)
            if (!activeSessionsListenerRegistered) {
                mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionsChangedListener, componentName)
                activeSessionsListenerRegistered = true
            }
            updateController(queryActiveSessions())
        } catch (e: SecurityException) {
            Log.e(TAG, "NotificationListener lacks permission to register for media sessions", e)
        }
    }

    private fun queryActiveSessions(): List<MediaController>? {
        return try {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, WaterMelonControlListener::class.java)
            mediaSessionManager.getActiveSessions(componentName)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification access was revoked while querying media sessions", e)
            null
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        cleanupMediaSession()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, WaterMelonControlListener::class.java))
        }
    }

    override fun onDestroy() {
        cleanupMediaSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun cleanupMediaSession() {
        debouncedRefreshStateStatic = {}
        mediaCommandRefreshStatic = {}
        refreshJob?.cancel()
        refreshJob = null
        settleRefreshJob?.cancel()
        settleRefreshJob = null

        if (activeSessionsListenerRegistered) {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
            activeSessionsListenerRegistered = false
        }

        mediaController?.unregisterCallback(callback)
        mediaController = null
        _mediaState.value = MediaState()
    }

    private fun updateController(controllers: List<MediaController>?) {
        val newController = controllers?.find { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers?.firstOrNull()

        if (newController?.sessionToken != mediaController?.sessionToken) {
            mediaController?.unregisterCallback(callback)
            mediaController = newController
            mediaController?.registerCallback(callback)
        }
        debouncedRefreshState()
    }

    private fun debouncedRefreshState() {
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            delay(200)
            refreshState()
        }
    }

    private fun refreshAfterMediaCommand() {
        debouncedRefreshState()
        settleRefreshJob?.cancel()
        settleRefreshJob = serviceScope.launch {
            delay(800)
            refreshState(forceRedraw = true)
        }
    }

    private fun refreshState(forceRedraw: Boolean = false) {
        val controller = mediaController
        if (controller == null) {
            _mediaState.update { current ->
                MediaState(
                    trackTitle = "No Media",
                    isPlaying = false,
                    revision = current.revision + if (forceRedraw) 1 else 0
                )
            }
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

        _mediaState.update { current ->
            MediaState(
                trackTitle = if (isPlayingOrTransitioning) (metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: "Unknown Track") else "No Media",
                trackArtist = if (isPlayingOrTransitioning) (metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: "") else "",
                isPlaying = isPlayingOrTransitioning,
                packageName = controller.packageName,
                revision = current.revision + if (forceRedraw) 1 else 0
            )
        }
    }
}
