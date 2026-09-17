package com.example.vesccontrolcentre.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import com.example.vesccontrolcentre.MainActivity
import com.example.vesccontrolcentre.R
import com.example.vesccontrolcentre.model.TelemetryData
import java.util.Locale

open class BaseTelemetryWidget(private val layoutResId: Int) : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_TELEMETRY = "com.example.vesccontrolcentre.ACTION_UPDATE_TELEMETRY"

        const val EXTRA_MPH = "extra_mph"
        const val EXTRA_VOLTAGE = "extra_voltage"
        const val EXTRA_ERPM = "extra_erpm"
        const val EXTRA_MOTOR_CURRENT = "extra_motor_current"
        const val EXTRA_BATTERY_CURRENT = "extra_battery_current"
        const val EXTRA_DUTY_CYCLE = "extra_duty_cycle"
        const val EXTRA_TEMP_MOSFET = "extra_temp_mosfet"
        const val EXTRA_TEMP_MOTOR = "extra_temp_motor"
        const val EXTRA_WATT_HOURS = "extra_watt_hours"
        const val EXTRA_AMP_HOURS_CHARGED = "extra_amp_hours_charged"
        const val EXTRA_FAULT_CODE = "extra_fault_code"
        const val EXTRA_FAULT_TEXT = "extra_fault_text"
        const val EXTRA_IS_CONNECTED = "extra_is_connected"
        const val EXTRA_ACTIVE_RIDE_DURATION_MS = "extra_active_ride_duration_ms"

        fun sendTelemetryBroadcast(context: Context, data: TelemetryData) {
            val classes = listOf(
                TelemetryWidget2x1::class.java,
                TelemetryWidget::class.java,
                TelemetryWidget2x2::class.java,
                TelemetryWidget4x2::class.java
            )

            for (cls in classes) {
                val intent = Intent(context, cls).apply {
                    action = ACTION_UPDATE_TELEMETRY
                    putExtra(EXTRA_MPH, data.mph)
                    putExtra(EXTRA_VOLTAGE, data.voltage)
                    putExtra(EXTRA_ERPM, data.erpm)
                    putExtra(EXTRA_MOTOR_CURRENT, data.motorCurrent)
                    putExtra(EXTRA_BATTERY_CURRENT, data.batteryCurrent)
                    putExtra(EXTRA_DUTY_CYCLE, data.dutyCycle)
                    putExtra(EXTRA_TEMP_MOSFET, data.tempMosfet)
                    putExtra(EXTRA_TEMP_MOTOR, data.tempMotor)
                    putExtra(EXTRA_WATT_HOURS, data.wattHoursUsed)
                    putExtra(EXTRA_AMP_HOURS_CHARGED, data.ampHoursCharged)
                    putExtra(EXTRA_FAULT_CODE, data.faultCode)
                    putExtra(EXTRA_FAULT_TEXT, data.faultText)
                    putExtra(EXTRA_IS_CONNECTED, data.isConnected)
                    putExtra(EXTRA_ACTIVE_RIDE_DURATION_MS, data.activeRideDurationMs)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val dummyData = TelemetryData()
        for (appWidgetId in appWidgetIds) {
            updateWidgetViews(context, appWidgetManager, appWidgetId, dummyData, layoutResId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_TELEMETRY) {
            val data = TelemetryData(
                mph = intent.getFloatExtra(EXTRA_MPH, 0f),
                voltage = intent.getFloatExtra(EXTRA_VOLTAGE, 0f),
                erpm = intent.getFloatExtra(EXTRA_ERPM, 0f),
                motorCurrent = intent.getFloatExtra(EXTRA_MOTOR_CURRENT, 0f),
                batteryCurrent = intent.getFloatExtra(EXTRA_BATTERY_CURRENT, 0f),
                dutyCycle = intent.getFloatExtra(EXTRA_DUTY_CYCLE, 0f),
                tempMosfet = intent.getFloatExtra(EXTRA_TEMP_MOSFET, 0f),
                tempMotor = intent.getFloatExtra(EXTRA_TEMP_MOTOR, 0f),
                wattHoursUsed = intent.getFloatExtra(EXTRA_WATT_HOURS, 0f),
                ampHoursCharged = intent.getFloatExtra(EXTRA_AMP_HOURS_CHARGED, 0f),
                faultCode = intent.getIntExtra(EXTRA_FAULT_CODE, 0),
                faultText = intent.getStringExtra(EXTRA_FAULT_TEXT) ?: "NO FAULT",
                activeRideDurationMs = intent.getLongExtra(EXTRA_ACTIVE_RIDE_DURATION_MS, 0L),
                isConnected = intent.getBooleanExtra(EXTRA_IS_CONNECTED, false)
            )

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, this::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateWidgetViews(context, appWidgetManager, appWidgetId, data, layoutResId)
            }
        }
    }

    protected fun updateWidgetViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        data: TelemetryData,
        layoutId: Int
    ) {
        val views = RemoteViews(context.packageName, layoutId)
        val prefs = context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE)

        val speedUnit = prefs.getString("speed_unit", "MPH") ?: "MPH"
        val speedometerMode = prefs.getString("speedometer_mode", "DIGITAL") ?: "DIGITAL"

        val isKmh = speedUnit.equals("KMH", ignoreCase = true)
        val speedVal = if (isKmh) data.mph * 1.60934f else data.mph
        val speedUnitLabel = if (isKmh) "KM/H" else "MPH"

        val speed3DigitStr = String.format(Locale.US, "%05.1f", speedVal)

        val showMph = prefs.getBoolean("metric_show_mph", true)
        val showVoltage = prefs.getBoolean("metric_show_voltage", true)
        val showErpm = prefs.getBoolean("metric_show_erpm", true)
        val showMotorCurrent = prefs.getBoolean("metric_show_motor_current", true)
        val showBatteryCurrent = prefs.getBoolean("metric_show_battery_current", true)
        val showDutyCycle = prefs.getBoolean("metric_show_duty_cycle", true)
        val showTempMosfet = prefs.getBoolean("metric_show_temp_mosfet", true)
        val showTempMotor = prefs.getBoolean("metric_show_temp_motor", true)
        val showWattHours = prefs.getBoolean("metric_show_watt_hours", true)
        val showAmpHoursCharged = prefs.getBoolean("metric_show_amp_hours_charged", true)
        val showFaultCodes = prefs.getBoolean("metric_show_fault_codes", true)

        if (data.isConnected) {
            views.setTextViewText(R.id.widget_status, "CONNECTED")
            views.setTextColor(R.id.widget_status, Color.parseColor("#00E676"))
        } else {
            views.setTextViewText(R.id.widget_status, "OFFLINE")
            views.setTextColor(R.id.widget_status, Color.parseColor("#FF5252"))
        }

        when (layoutId) {
            R.layout.widget_telemetry_2x1 -> {
                val t1 = if (showMph) "$speed3DigitStr $speedUnitLabel" else String.format(Locale.US, "%.1f V", data.voltage)
                val t2 = if (showMph && showVoltage) String.format(Locale.US, "%.1f V", data.voltage) else if (showMotorCurrent) String.format(Locale.US, "%.1f A", data.motorCurrent) else String.format(Locale.US, "%.0f°C", data.tempMosfet)
                views.setTextViewText(R.id.widget_metric_1, t1)
                views.setTextViewText(R.id.widget_metric_2, t2)
            }

            R.layout.widget_telemetry_2x2, R.layout.widget_telemetry_4x2 -> {
                if (speedometerMode == "DIAL") {
                    val maxSpeed = if (isKmh) 100f else 60f
                    val dialBitmap = renderSpeedometerDialBitmap(
                        widthPx = if (layoutId == R.layout.widget_telemetry_4x2) 480 else 380,
                        heightPx = if (layoutId == R.layout.widget_telemetry_4x2) 260 else 220,
                        speedValue = speedVal,
                        unitLabel = speedUnitLabel,
                        maxSpeed = maxSpeed
                    )
                    views.setImageViewBitmap(R.id.widget_dial_image, dialBitmap)
                    views.setViewVisibility(R.id.widget_dial_image, View.VISIBLE)
                    views.setViewVisibility(R.id.container_digital_speed, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_dial_image, View.GONE)
                    views.setViewVisibility(R.id.container_digital_speed, View.VISIBLE)

                    views.setTextViewText(R.id.widget_speed, speed3DigitStr)
                    views.setTextViewText(R.id.widget_speed_unit, speedUnitLabel)
                    views.setTextViewText(R.id.widget_voltage, String.format(Locale.US, "%.1f", data.voltage))
                }

                if (layoutId == R.layout.widget_telemetry_2x2) {
                    val sec = (data.activeRideDurationMs / 1000) % 60
                    val min = (data.activeRideDurationMs / (1000 * 60)) % 60
                    val hrs = (data.activeRideDurationMs / (1000 * 60 * 60))
                    val activeTimeStr = if (hrs > 0) String.format(Locale.US, "%d:%02d:%02d", hrs, min, sec) else String.format(Locale.US, "%02d:%02d", min, sec)

                    views.setTextViewText(
                        R.id.widget_detail_1,
                        if (showMotorCurrent) String.format(Locale.US, "Motor: %.1fA", data.motorCurrent) else ""
                    )
                    views.setTextViewText(
                        R.id.widget_detail_2,
                        if (showBatteryCurrent) String.format(Locale.US, "Batt: %.1fA", data.batteryCurrent) else ""
                    )
                    views.setTextViewText(
                        R.id.widget_detail_3,
                        if (showTempMosfet) String.format(Locale.US, "FET: %.1f°C", data.tempMosfet) else ""
                    )
                    views.setTextViewText(
                        R.id.widget_detail_4,
                        "Time: $activeTimeStr"
                    )

                    if (showFaultCodes && data.faultCode > 0) {
                        views.setViewVisibility(R.id.widget_fault_banner, View.VISIBLE)
                        views.setTextViewText(R.id.widget_fault_banner, "⚠️ FAULT: ${data.faultText}")
                    } else {
                        views.setViewVisibility(R.id.widget_fault_banner, View.GONE)
                    }
                } else {
                    val sec = (data.activeRideDurationMs / 1000) % 60
                    val min = (data.activeRideDurationMs / (1000 * 60)) % 60
                    val hrs = (data.activeRideDurationMs / (1000 * 60 * 60))
                    val activeTimeStr = if (hrs > 0) String.format(Locale.US, "%d:%02d:%02d", hrs, min, sec) else String.format(Locale.US, "%02d:%02d", min, sec)

                    views.setTextViewText(R.id.widget_m1, if (showMotorCurrent) String.format(Locale.US, "Motor: %.1fA", data.motorCurrent) else "")
                    views.setTextViewText(R.id.widget_m2, if (showBatteryCurrent) String.format(Locale.US, "Battery: %.1fA", data.batteryCurrent) else "")
                    views.setTextViewText(R.id.widget_m3, if (showDutyCycle) String.format(Locale.US, "Duty: %.1f%%", data.dutyCycle) else "")
                    views.setTextViewText(R.id.widget_m4, if (showTempMosfet) String.format(Locale.US, "MOSFET: %.1f°C", data.tempMosfet) else "")
                    views.setTextViewText(R.id.widget_m5, if (showTempMotor) String.format(Locale.US, "Motor: %.1f°C", data.tempMotor) else "")
                    views.setTextViewText(R.id.widget_m6, if (showErpm) String.format(Locale.US, "ERPM: %.0f", data.erpm) else "")
                    views.setTextViewText(R.id.widget_m7, if (showWattHours) String.format(Locale.US, "Energy: %.1fWh", data.wattHoursUsed) else "")
                    views.setTextViewText(R.id.widget_m8, "Time: $activeTimeStr")
                    views.setTextViewText(R.id.widget_fault_text, if (showFaultCodes) "Fault: ${data.faultText}" else "")
                }
            }
        }

        val mainIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun renderSpeedometerDialBitmap(
        widthPx: Int = 400,
        heightPx: Int = 240,
        speedValue: Float,
        unitLabel: String = "MPH",
        maxSpeed: Float = 60f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = widthPx / 2f
        val centerY = heightPx / 1.6f
        val radius = Math.min(widthPx, heightPx) / 2.2f
        val strokeW = 16f

        val paintArc = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
        }

        // Background Arc (140° to 400° -> 260° sweep)
        paintArc.color = Color.parseColor("#1E2838")
        val rectF = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawArc(rectF, 140f, 260f, false, paintArc)

        // Active Progress Arc
        val fraction = (speedValue / maxSpeed).coerceIn(0f, 1f)
        if (fraction > 0.005f) {
            val sweepAngle = 260f * fraction
            paintArc.shader = SweepGradient(
                centerX, centerY,
                intArrayOf(
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#00E676"),
                    Color.parseColor("#FFD54F"),
                    Color.parseColor("#FF5252")
                ),
                null
            )
            canvas.drawArc(rectF, 140f, sweepAngle, false, paintArc)
            paintArc.shader = null
        }

        // Ticks
        val paintTick = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#8A99AD")
            strokeWidth = 2.5f
        }
        val paintTickText = Paint().apply {
            isAntiAlias = true
            color = Color.GRAY
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }

        val step = 10
        val maxTicks = maxSpeed.toInt()
        for (tick in 0..maxTicks step step) {
            val f = tick / maxSpeed
            val angleDeg = 140f + (260f * f)
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val outerR = radius - (strokeW / 2f) - 4f
            val innerR = outerR - 12f

            val sx = (centerX + outerR * Math.cos(angleRad)).toFloat()
            val sy = (centerY + outerR * Math.sin(angleRad)).toFloat()
            val ex = (centerX + innerR * Math.cos(angleRad)).toFloat()
            val ey = (centerY + innerR * Math.sin(angleRad)).toFloat()

            canvas.drawLine(sx, sy, ex, ey, paintTick)

            if (tick % 20 == 0) {
                val labelR = innerR - 14f
                val lx = (centerX + labelR * Math.cos(angleRad)).toFloat()
                val ly = (centerY + labelR * Math.sin(angleRad)).toFloat()
                canvas.drawText(tick.toString(), lx, ly + 6f, paintTickText)
            }
        }

        // Needle Line
        val needleAngleDeg = 140f + (260f * fraction)
        val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
        val needleLen = radius - 8f

        val paintNeedle = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#FF5252")
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }

        val endX = (centerX + needleLen * Math.cos(needleAngleRad)).toFloat()
        val endY = (centerY + needleLen * Math.sin(needleAngleRad)).toFloat()

        canvas.drawLine(centerX, centerY, endX, endY, paintNeedle)

        // Center Pin
        val paintPin = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#FF5252")
        }
        canvas.drawCircle(centerX, centerY, 9f, paintPin)
        paintPin.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 3f, paintPin)

        // Center 3-Digit Digital Speed
        val intSpeed = speedValue.toInt().coerceIn(0, 999)
        val speedStr = String.format(Locale.US, "%03d", intSpeed)

        val paintSpeedText = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#00E5FF")
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val paintUnitText = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#00E5FF")
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(speedStr, centerX, centerY + 32f, paintSpeedText)
        canvas.drawText(unitLabel, centerX, centerY + 52f, paintUnitText)

        return bitmap
    }
}

class TelemetryWidget2x1 : BaseTelemetryWidget(R.layout.widget_telemetry_2x1)
class TelemetryWidget : BaseTelemetryWidget(R.layout.widget_telemetry_2x2)
class TelemetryWidget2x2 : BaseTelemetryWidget(R.layout.widget_telemetry_2x2)
class TelemetryWidget4x2 : BaseTelemetryWidget(R.layout.widget_telemetry_4x2)
