package com.example.vesccontrolcentre.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.vesccontrolcentre.R

class SoundToggleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_SOUND = "com.example.vesccontrolcentre.ACTION_TOGGLE_SOUND"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SoundToggleWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, SoundToggleWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE)
        val soundEnabled = prefs.getBoolean("engine_sound_enabled", false)

        for (appWidgetId in appWidgetIds) {
            updateWidgetView(context, appWidgetManager, appWidgetId, soundEnabled)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_SOUND) {
            val prefs = context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE)
            val currentState = prefs.getBoolean("engine_sound_enabled", false)
            val newState = !currentState

            prefs.edit().putBoolean("engine_sound_enabled", newState).apply()
            updateAllWidgets(context)
        }
    }

    private fun updateWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        soundEnabled: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_sound_toggle)

        if (soundEnabled) {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_active_normal)
            views.setImageViewResource(R.id.widget_icon, R.drawable.ic_sound_on)
            views.setTextViewText(R.id.widget_status, "ON")
            views.setTextColor(R.id.widget_status, Color.parseColor("#00E5FF"))
        } else {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_inactive)
            views.setImageViewResource(R.id.widget_icon, R.drawable.ic_sound_off)
            views.setTextViewText(R.id.widget_status, "OFF")
            views.setTextColor(R.id.widget_status, Color.parseColor("#FF5252"))
        }

        val intent = Intent(context, SoundToggleWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_SOUND
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
