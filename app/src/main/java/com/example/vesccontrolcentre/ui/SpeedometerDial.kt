package com.example.vesccontrolcentre.ui

import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerDial(
    speedMph: Float,
    unit: String = "MPH",
    maxSpeed: Float = 60f,
    modifier: Modifier = Modifier
) {
    val speedValue = if (unit.equals("KMH", ignoreCase = true)) speedMph * 1.60934f else speedMph
    val effectiveMaxSpeed = if (unit.equals("KMH", ignoreCase = true)) maxSpeed * 1.6f else maxSpeed

    val animatedSpeed by animateFloatAsState(
        targetValue = speedValue.coerceIn(0f, effectiveMaxSpeed),
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "speedAnimation"
    )

    val startAngle = 140f
    val sweepAngle = 260f

    val intSpeed = animatedSpeed.toInt().coerceIn(0, 999)
    val formatted3DigitSpeed = String.format(Locale.US, "%03d", intSpeed)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.2f)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val center = Offset(canvasWidth / 2f, canvasHeight / 1.8f)
            val radius = Math.min(canvasWidth, canvasHeight) / 2.3f
            val strokeWidth = 18.dp.toPx()

            val arcSize = Size(radius * 2, radius * 2)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)

            // Background Track Arc
            drawArc(
                color = Color(0xFF1E2838),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Progress Gradient Arc
            val currentFraction = (animatedSpeed / effectiveMaxSpeed).coerceIn(0f, 1f)
            val currentSweep = sweepAngle * currentFraction

            if (currentSweep > 0.5f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF00E5FF),
                            Color(0xFF00E676),
                            Color(0xFFFFD54F),
                            Color(0xFFFF5252)
                        ),
                        center = center
                    ),
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Dial Ticks
            val tickStep = 10
            val maxTicks = effectiveMaxSpeed.toInt()
            for (tickSpeed in 0..maxTicks step tickStep) {
                val tickFraction = tickSpeed / effectiveMaxSpeed
                val angleDeg = startAngle + (sweepAngle * tickFraction)
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val outerRadius = radius - (strokeWidth / 2f) - 6.dp.toPx()
                val innerRadius = outerRadius - 12.dp.toPx()

                val startX = center.x + outerRadius * cos(angleRad).toFloat()
                val startY = center.y + outerRadius * sin(angleRad).toFloat()
                val endX = center.x + innerRadius * cos(angleRad).toFloat()
                val endY = center.y + innerRadius * sin(angleRad).toFloat()

                drawLine(
                    color = Color.LightGray.copy(alpha = 0.6f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx()
                )

                // Tick text labels
                if (tickSpeed % 20 == 0) {
                    val labelRadius = innerRadius - 14.dp.toPx()
                    val labelX = center.x + labelRadius * cos(angleRad).toFloat()
                    val labelY = center.y + labelRadius * sin(angleRad).toFloat()

                    drawContext.canvas.nativeCanvas.drawText(
                        tickSpeed.toString(),
                        labelX,
                        labelY + 12f,
                        Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 28f
                            textAlign = Paint.Align.CENTER
                        }
                    )
                }
            }

            // Needle Indicator
            val needleAngleDeg = startAngle + (sweepAngle * currentFraction)
            val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
            val needleLength = radius - 10.dp.toPx()

            val needleEndX = center.x + needleLength * cos(needleAngleRad).toFloat()
            val needleEndY = center.y + needleLength * sin(needleAngleRad).toFloat()

            drawLine(
                color = Color(0xFFFF5252),
                start = center,
                end = Offset(needleEndX, needleEndY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Center Pin
            drawCircle(
                color = Color(0xFFFF5252),
                radius = 8.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = center
            )
        }

        // Center Digital 3-Digit Speed Text & Unit
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 36.dp)
        ) {
            Text(
                text = formatted3DigitSpeed,
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF)
            )
            Text(
                text = unit.uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF)
            )
        }
    }
}
