package com.watermeloncontrol.widget

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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

    companion object {
        private const val TAG = "WaterMelonControl"
        private val _mediaState = MutableStateFlow(MediaState())
        val mediaState = _mediaState.asStateFlow()

        private var mediaController: MediaController? = null

        fun playPause(context: Context) {
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
            val controller = mediaController
            if (controller == null) {
                sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                return
            }

            controller.transportControls.skipToNext()
            mediaCommandRefreshStatic()
        }

        fun prev(context: Context) {
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
                // Current session stopped/paused, check if another app is playing
                val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val componentName = ComponentName(this@WaterMelonControlListener, WaterMelonControlListener::class.java)
                updateController(mediaSessionManager.getActiveSessions(componentName))
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
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionsChangedListener, componentName)
            updateController(mediaSessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            Log.e(TAG, "NotificationListener lacks permission to access MediaSessionManager")
        }
        logKaroo2Diagnostics("onListenerConnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        logKaroo2Diagnostics("onNotificationPosted", sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        logKaroo2Diagnostics("onNotificationRemoved", sbn)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        debouncedRefreshStateStatic = {}
        mediaCommandRefreshStatic = {}
        refreshJob?.cancel()
        settleRefreshJob?.cancel()
        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        mediaController?.unregisterCallback(callback)
        mediaController = null
        _mediaState.update { MediaState() }

        logKaroo2Diagnostics("onListenerDisconnected")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, WaterMelonControlListener::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        debouncedRefreshStateStatic = {}
        mediaCommandRefreshStatic = {}
        serviceScope.cancel()
        mediaController?.unregisterCallback(callback)
        mediaController = null
        _mediaState.update { MediaState() }
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
            refreshState()
            logKaroo2Diagnostics("mediaCommandSettleRefresh")
        }
    }

    private fun refreshState() {
        val controller = mediaController
        if (controller == null) {
            _mediaState.update { current ->
                MediaState(
                    trackTitle = "No Media",
                    isPlaying = false,
                    revision = current.revision + 1
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
                revision = current.revision + 1
            )
        }
    }

    private fun logKaroo2Diagnostics(event: String, sbn: StatusBarNotification? = null) {
        if (!BuildConfig.ENABLE_KAROO2_DIAGNOSTICS || Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) return

        Log.i(TAG, "Karoo2Diagnostics event=$event sdk=${Build.VERSION.SDK_INT}")
        sbn?.let { logNotificationDiagnostics("eventNotification", it) }

        try {
            val activeNotifications = getActiveNotifications()
            Log.i(TAG, "Karoo2Diagnostics activeNotifications=${activeNotifications.size}")
            activeNotifications.forEach { logNotificationDiagnostics("activeNotification", it) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Karoo2Diagnostics getActiveNotifications failed", e)
        }

        try {
            val componentName = ComponentName(this, WaterMelonControlListener::class.java)
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            Log.i(TAG, "Karoo2Diagnostics activeSessions=${controllers.size}")
            controllers.forEachIndexed { index, controller ->
                logControllerDiagnostics(index, controller)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Karoo2Diagnostics getActiveSessions failed", e)
        }
    }

    private fun logNotificationDiagnostics(source: String, sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(separator = " | ") { sanitizeForLog(it) }
        val hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        val keys = extras.keySet().sorted().joinToString(separator = ",")

        Log.i(
            TAG,
            "Karoo2Diagnostics $source package=${sbn.packageName} " +
                    "category=${notification.category ?: "<null>"} " +
                    "title=${sanitizeForLog(extras.getCharSequence(Notification.EXTRA_TITLE))} " +
                    "text=${sanitizeForLog(extras.getCharSequence(Notification.EXTRA_TEXT))} " +
                    "subText=${sanitizeForLog(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))} " +
                    "bigText=${sanitizeForLog(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))} " +
                    "textLines=${textLines ?: "<null>"} " +
                    "hasMediaSession=$hasMediaSession " +
                    "keys=$keys"
        )
    }

    private fun logControllerDiagnostics(index: Int, controller: MediaController) {
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        Log.i(
            TAG,
            "Karoo2Diagnostics controller[$index] package=${controller.packageName} " +
                    "state=${playbackState?.state ?: PlaybackState.STATE_NONE} " +
                    "title=${sanitizeForLog(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))} " +
                    "artist=${sanitizeForLog(metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST))}"
        )
    }

    private fun sanitizeForLog(value: CharSequence?): String {
        return value
            ?.toString()
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?: "<null>"
    }
}

