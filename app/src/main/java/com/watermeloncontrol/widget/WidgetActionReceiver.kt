package com.watermeloncontrol.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE -> WaterMelonControlListener.playPause(context)

            ACTION_NEXT -> WaterMelonControlListener.next(context)

            ACTION_PREV -> WaterMelonControlListener.prev(context)

            ACTION_VOL_UP -> WaterMelonControlListener.volumeUp(context)

            ACTION_VOL_DOWN -> WaterMelonControlListener.volumeDown(context)

            ACTION_OPEN_APP -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setPackage(pkg)
                    }.let { launcherIntent ->
                        context.packageManager.queryIntentActivities(launcherIntent, 0)
                            .firstOrNull()
                            ?.activityInfo
                            ?.let { activityInfo ->
                                Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_LAUNCHER)
                                    setClassName(activityInfo.packageName, activityInfo.name)
                                }
                            }
                    }

                if (launchIntent == null) {
                    Log.w("WaterMelonControl", "No launcher activity found for $pkg")
                    return
                }

                try {
                    context.startActivity(
                        launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        )
                    )
                } catch (e: RuntimeException) {
                    Log.w("WaterMelonControl", "Unable to open media app $pkg", e)
                }
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.watermeloncontrol.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.watermeloncontrol.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.watermeloncontrol.widget.ACTION_PREV"
        const val ACTION_VOL_UP = "com.watermeloncontrol.widget.ACTION_VOL_UP"
        const val ACTION_VOL_DOWN = "com.watermeloncontrol.widget.ACTION_VOL_DOWN"
        const val ACTION_OPEN_APP = "com.watermeloncontrol.widget.ACTION_OPEN_APP"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}
