package com.watermeloncontrol.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.watermeloncontrol.widget.ACTION_PLAY_PAUSE" -> {
                if (!WaterMelonControlListener.playPause()) {
                    WaterMelonControlListener.sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
            }

            "com.watermeloncontrol.widget.ACTION_NEXT" -> {
                if (!WaterMelonControlListener.next()) {
                    WaterMelonControlListener.sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            }

            "com.watermeloncontrol.widget.ACTION_PREV" -> {
                if (!WaterMelonControlListener.prev()) {
                    WaterMelonControlListener.sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
            }

            "com.watermeloncontrol.widget.ACTION_VOL_UP" -> {
                WaterMelonControlListener.volumeUp()
            }

            "com.watermeloncontrol.widget.ACTION_VOL_DOWN" -> {
                WaterMelonControlListener.volumeDown()
            }

            "com.watermeloncontrol.widget.ACTION_OPEN_APP" -> {
                val pkg = intent.getStringExtra("package_name") ?: return
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}
