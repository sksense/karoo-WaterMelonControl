package com.watermeloncontrol.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class WaterMelonControlService : KarooExtension("watermelon_control", "1.3.1-beta01") {

    override val types: List<DataTypeImpl> = listOf(
        // 1. Playing Now Widget
        object : DataTypeImpl("watermelon_control", "watermelon_playing_now") {
            override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
                emitter.onNext(UpdateGraphicConfig(showHeader = true))
                val job = CoroutineScope(Dispatchers.Main).launch {
                    WaterMelonControlListener.mediaState
                        .distinctUntilChanged { old, new ->
                            old.trackTitle == new.trackTitle &&
                                    old.trackArtist == new.trackArtist &&
                                    old.packageName == new.packageName &&
                                    old.sessionActivity == new.sessionActivity
                        }
                        .collect { state ->
                            val views = RemoteViews(context.packageName, R.layout.widget_playing_now)
                            views.setTextViewText(R.id.track_title, state.trackTitle)
                            views.setTextViewText(R.id.track_artist, state.trackArtist)

                            val pi = state.packageName?.let { pkg ->
                                val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                                    action = WidgetActionReceiver.ACTION_OPEN_APP
                                    putExtra(WidgetActionReceiver.EXTRA_PACKAGE_NAME, pkg)
                                }
                                PendingIntent.getBroadcast(
                                    context,
                                    10,
                                    intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                            }

                            if (pi != null) {
                                views.setOnClickPendingIntent(R.id.playing_root, pi)
                                views.setOnClickPendingIntent(R.id.track_title, pi)
                                views.setOnClickPendingIntent(R.id.track_artist, pi)
                            }
                            emitter.updateView(views)
                        }
                }
                emitter.setCancellable { job.cancel() }
            }
        },

        // 2. Volume Widget
        object : DataTypeImpl("watermelon_control", "watermelon_volume") {
            override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
                emitter.onNext(UpdateGraphicConfig(showHeader = true))
                val views = RemoteViews(context.packageName, R.layout.widget_volume)

                val createVolPi = { actionStr: String, reqCode: Int ->
                    PendingIntent.getBroadcast(
                        context, reqCode,
                        Intent(context, WidgetActionReceiver::class.java).apply { action = actionStr },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }

                views.setOnClickPendingIntent(
                    R.id.btn_vol_up,
                    createVolPi(WidgetActionReceiver.ACTION_VOL_UP, 3)
                )
                views.setOnClickPendingIntent(
                    R.id.btn_vol_down,
                    createVolPi(WidgetActionReceiver.ACTION_VOL_DOWN, 4)
                )
                emitter.updateView(views)
            }
        },

        // 3. Media Controls Widget
        object : DataTypeImpl("watermelon_control", "watermelon_media") {
            override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
                emitter.onNext(UpdateGraphicConfig(showHeader = true))
                val job = CoroutineScope(Dispatchers.Main).launch {
                    val createMediaPi = { actionStr: String, reqCode: Int ->
                        PendingIntent.getBroadcast(
                            context, reqCode,
                            Intent(context, WidgetActionReceiver::class.java).apply { action = actionStr },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }
                    val piPlayPause = createMediaPi(WidgetActionReceiver.ACTION_PLAY_PAUSE, 0)
                    val piPrev = createMediaPi(WidgetActionReceiver.ACTION_PREV, 1)
                    val piNext = createMediaPi(WidgetActionReceiver.ACTION_NEXT, 2)

                    WaterMelonControlListener.mediaState
                        .distinctUntilChanged { old, new -> old.isPlaying == new.isPlaying }
                        .collect { state ->
                            val views = RemoteViews(context.packageName, R.layout.widget_media)
                            val playPauseRes =
                                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                            views.setImageViewResource(R.id.btn_play_pause, playPauseRes)

                            views.setOnClickPendingIntent(R.id.btn_play_pause, piPlayPause)
                            views.setOnClickPendingIntent(R.id.btn_prev, piPrev)
                            views.setOnClickPendingIntent(R.id.btn_next, piNext)

                            emitter.updateView(views)
                        }
                }
                emitter.setCancellable { job.cancel() }
            }
        }
    )
}
