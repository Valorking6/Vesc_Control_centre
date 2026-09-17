package com.example.vesccontrolcentre.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.vesccontrolcentre.service.VescService

class SyncMarkerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceContent(context)
        }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun GlanceContent(context: Context) {
        val dropIntent = Intent(context, VescService::class.java).apply {
            action = VescService.ACTION_DROP_SYNC_MARKER
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF121824))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📍 VESC SYNC",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF00E5FF)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Button(
                    text = "Drop Sync Marker",
                    onClick = actionStartService(dropIntent),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(Color(0xFF00E676)),
                        contentColor = ColorProvider(Color.Black)
                    ),
                    modifier = GlanceModifier.padding(top = 6.dp)
                )
            }
        }
    }
}

class SyncMarkerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SyncMarkerWidget()
}
