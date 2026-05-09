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
import kotlinx.coroutines.launch

class WaterMelonControlService : KarooExtension("watermelon_control", "1.2.1") {

    override val types: List<DataTypeImpl> = listOf(
        // 1. Playing Now Widget
        object : DataTypeImpl("watermelon_control", "watermelon_playing_now") {
            override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
                emitter.onNext(UpdateGraphicConfig(showHeader = true))
                val job = CoroutineScope(Dispatchers.Main).launch {
                    WaterMelonControlListener.mediaState.collect { state ->
                        val views = RemoteViews(context.packageName, R.layout.widget_playing_now)
                        views.setTextViewText(R.id.track_title, state.trackTitle)
                        views.setTextViewText(R.id.track_artist, state.trackArtist)

                        val pi = state.sessionActivity ?: state.packageName?.let { pkg ->
                            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                                action = "com.watermeloncontrol.widget.ACTION_OPEN_APP"
                                putExtra("package_name", pkg)
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

                val volUpPi = PendingIntent.getBroadcast(
                    context, 3,
                    Intent(context, WidgetActionReceiver::class.java).apply {
                        action = "com.watermeloncontrol.widget.ACTION_VOL_UP"
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val volDownPi = PendingIntent.getBroadcast(
                    context, 4,
                    Intent(context, WidgetActionReceiver::class.java).apply {
                        action = "com.watermeloncontrol.widget.ACTION_VOL_DOWN"
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                views.setOnClickPendingIntent(R.id.btn_vol_up, volUpPi)
                views.setOnClickPendingIntent(R.id.btn_vol_down, volDownPi)
                emitter.updateView(views)
            }
        },

        // 3. Media Controls Widget
        object : DataTypeImpl("watermelon_control", "watermelon_media") {
            override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
                emitter.onNext(UpdateGraphicConfig(showHeader = true))
                val job = CoroutineScope(Dispatchers.Main).launch {
                    WaterMelonControlListener.mediaState.collect { state ->
                        val views = RemoteViews(context.packageName, R.layout.widget_media)

                        val playPauseRes = if (state.isPlaying) {
                            android.R.drawable.ic_media_pause
                        } else {
                            android.R.drawable.ic_media_play
                        }
                        views.setImageViewResource(R.id.btn_play_pause, playPauseRes)

                        val playPausePi = PendingIntent.getBroadcast(
                            context, 0,
                            Intent(context, WidgetActionReceiver::class.java).apply {
                                action = "com.watermeloncontrol.widget.ACTION_PLAY_PAUSE"
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val prevPi = PendingIntent.getBroadcast(
                            context, 1,
                            Intent(context, WidgetActionReceiver::class.java).apply {
                                action = "com.watermeloncontrol.widget.ACTION_PREV"
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val nextPi = PendingIntent.getBroadcast(
                            context, 2,
                            Intent(context, WidgetActionReceiver::class.java).apply {
                                action = "com.watermeloncontrol.widget.ACTION_NEXT"
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        views.setOnClickPendingIntent(R.id.btn_play_pause, playPausePi)
                        views.setOnClickPendingIntent(R.id.btn_prev, prevPi)
                        views.setOnClickPendingIntent(R.id.btn_next, nextPi)

                        emitter.updateView(views)
                    }
                }
                emitter.setCancellable { job.cancel() }
            }
        }
    )
}
