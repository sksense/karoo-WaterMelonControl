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

class WaterMelonControlService : KarooExtension("watermelon_control", "1.4.0") {

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
                                    old.revision == new.revision
                        }
                        .collect { state ->
                            val views = RemoteViews(context.packageName, R.layout.widget_playing_now)
                            views.setTextViewText(R.id.track_title, state.trackTitle)
                            views.setTextViewText(R.id.track_artist, state.trackArtist)

                            state.packageName?.let { pkg ->
                                val pendingIntent = actionPendingIntent(
                                    context = context,
                                    action = WidgetActionReceiver.ACTION_OPEN_APP,
                                    requestCode = 10,
                                    packageName = pkg
                                )
                                views.setClickPendingIntentForAll(
                                    pendingIntent,
                                    R.id.playing_root,
                                    R.id.track_title,
                                    R.id.track_artist
                                )
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

                views.setOnClickPendingIntent(
                    R.id.btn_vol_up,
                    actionPendingIntent(context, WidgetActionReceiver.ACTION_VOL_UP, 3)
                )
                views.setOnClickPendingIntent(
                    R.id.btn_vol_down,
                    actionPendingIntent(context, WidgetActionReceiver.ACTION_VOL_DOWN, 4)
                )
                emitter.updateView(views)
            }
        },

        // 3. Media Controls Widget
        object : DataTypeImpl("watermelon_control", "watermelon_media") {
            override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
                emitter.onNext(UpdateGraphicConfig(showHeader = true))
                val piPlayPause = actionPendingIntent(context, WidgetActionReceiver.ACTION_PLAY_PAUSE, 0)
                val piPrev = actionPendingIntent(context, WidgetActionReceiver.ACTION_PREV, 1)
                val piNext = actionPendingIntent(context, WidgetActionReceiver.ACTION_NEXT, 2)

                val job = CoroutineScope(Dispatchers.Main).launch {
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

    companion object {
        private fun actionPendingIntent(
            context: Context,
            action: String,
            requestCode: Int,
            packageName: String? = null
        ): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                this.action = action
                packageName?.let { putExtra(WidgetActionReceiver.EXTRA_PACKAGE_NAME, it) }
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun RemoteViews.setClickPendingIntentForAll(
            pendingIntent: PendingIntent,
            vararg viewIds: Int
        ) {
            viewIds.forEach { setOnClickPendingIntent(it, pendingIntent) }
        }
    }
}
