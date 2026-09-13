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
import com.example.vesccontrolcentre.activity.ApplyProfileActivity
import com.example.vesccontrolcentre.model.ProfileType

abstract class BaseProfileWidgetProvider(
    private val profileType: ProfileType,
    private val titleText: String,
    private val subtitleText: String,
    private val iconResId: Int,
    private val activeBgResId: Int
) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE)
        val activeProfileKey = prefs.getString("active_profile", "")

        for (appWidgetId in appWidgetIds) {
            updateWidgetView(context, appWidgetManager, appWidgetId, activeProfileKey)
        }
    }

    private fun updateWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        activeProfileKey: String?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_2x1_layout)

        val isActive = (activeProfileKey == profileType.key)

        views.setTextViewText(R.id.widget_title, titleText)
        views.setTextViewText(R.id.widget_subtitle, subtitleText)
        views.setImageViewResource(R.id.widget_icon, iconResId)

        if (isActive) {
            views.setInt(R.id.widget_root, "setBackgroundResource", activeBgResId)
            views.setInt(R.id.widget_badge, "setBackgroundResource", R.drawable.widget_badge_active)
            views.setTextViewText(R.id.widget_badge, "⚡ ACTIVE")
            views.setTextColor(R.id.widget_badge, Color.BLACK)
            views.setTextViewText(R.id.widget_hint, "CURRENTLY ACTIVE")
            views.setTextColor(R.id.widget_hint, Color.parseColor("#00E676"))
        } else {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_inactive)
            views.setInt(R.id.widget_badge, "setBackgroundResource", R.drawable.widget_badge_inactive)
            views.setTextViewText(R.id.widget_badge, "OFF")
            views.setTextColor(R.id.widget_badge, Color.parseColor("#A0A5B5"))
            views.setTextViewText(R.id.widget_hint, "TAP TO APPLY")
            views.setTextColor(R.id.widget_hint, Color.parseColor("#60687B"))
        }

        val intent = Intent(context, ApplyProfileActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("PROFILE", profileType.key)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            profileType.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            val providers = listOf(
                MaxPowerWidgetProvider::class.java,
                NormalWidgetProvider::class.java,
                LongRangeWidgetProvider::class.java,
                CrawlWidgetProvider::class.java
            )

            for (providerClass in providers) {
                val componentName = ComponentName(context, providerClass)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids.isNotEmpty()) {
                    val intent = Intent(context, providerClass).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    context.sendBroadcast(intent)
                }
            }
        }
    }
}
