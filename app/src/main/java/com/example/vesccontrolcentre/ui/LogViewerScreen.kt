package com.example.vesccontrolcentre.ui

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MenuAnchorType
import com.example.vesccontrolcentre.logging.LogFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.InputStreamReader
import java.io.BufferedReader

fun readLinesFromLogItem(context: Context, item: LogFileItem): List<String> {
    return try {
        if (item.file != null) {
            item.file.readLines()
        } else if (item.uri != null) {
            val inputStream = context.contentResolver.openInputStream(item.uri)
            inputStream?.bufferedReader()?.readLines() ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(logItem: LogFileItem, onBack: () -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var headers by remember { mutableStateOf<List<String>>(emptyList()) }
    var plotData by remember { mutableStateOf<List<Pair<Long, Float>>>(emptyList()) }
    var selectedMetric by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(logItem) {
        withContext(Dispatchers.IO) {
            try {
                val lines = readLinesFromLogItem(context, logItem)
                logLines = lines
                if (lines.isNotEmpty()) {
                    headers = lines.first().split(",").map { it.trim() }
                    
                    val speedIndex = headers.indexOfFirst { it.contains("Speed", ignoreCase = true) }
                    val defaultMetricIndex = if (speedIndex != -1) speedIndex else if (headers.size > 2) 2 else 0
                    
                    if (headers.isNotEmpty() && defaultMetricIndex < headers.size) {
                        selectedMetric = headers[defaultMetricIndex]
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedMetric) {
        if (selectedMetric.isNotEmpty() && logLines.isNotEmpty()) {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val parsedData = mutableListOf<Pair<Long, Float>>()
                    val headerList = logLines.first().split(",").map { it.trim() }
                    val metricIndex = headerList.indexOf(selectedMetric)
                    val timeIndex = 0

                    if (metricIndex != -1) {
                        for (i in 1 until logLines.size) {
                            val cols = logLines[i].split(",")
                            if (cols.size > metricIndex) {
                                val time = cols[timeIndex].toLongOrNull()
                                val value = cols[metricIndex].toFloatOrNull()
                                if (time != null && value != null) {
                                    parsedData.add(Pair(time, value))
                                }
                            }
                        }
                    }
                    plotData = parsedData
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Data Plotter", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(logItem.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))

            if (headers.isNotEmpty()) {
                val availableMetrics = headers.filterIndexed { index, name -> 
                    index > 1 && !name.contains("ISO", ignoreCase = true) 
                }

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMetric,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Metric to Plot") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        availableMetrics.forEach { metric ->
                            DropdownMenuItem(
                                text = { Text(metric, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    selectedMetric = metric
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            } else if (plotData.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No numerical data available for this metric.", color = Color.Gray)
                }
            } else {
                LineChart(plotData)
            }
        }
    }
}

@Composable
fun LineChart(data: List<Pair<Long, Float>>) {
    if (data.isEmpty()) return

    val minTime = data.minOf { it.first }
    val maxTime = data.maxOf { it.first }
    val minValue = data.minOf { it.second }.let { if (it > 0) 0f else it }
    val maxValue = data.maxOf { it.second }
    
    val rangeTime = (maxTime - minTime).coerceAtLeast(1L)
    val rangeValue = (maxValue - minValue).coerceAtLeast(0.1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .background(Color(0xFF121824), shape = RoundedCornerShape(8.dp))
            .padding(24.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Grid Lines & Y-Axis Labels
            val paintText = Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 24f
                textAlign = Paint.Align.RIGHT
            }

            val steps = 4
            for (i in 0..steps) {
                val fraction = i / steps.toFloat()
                val y = canvasHeight - (fraction * canvasHeight)
                val labelValue = minValue + (rangeValue * fraction)

                drawLine(
                    color = Color.DarkGray,
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1f
                )
                
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(Locale.US, "%.1f", labelValue),
                    -10f,
                    y + 8f,
                    paintText
                )
            }

            // Draw Data Path
            val path = Path()
            data.forEachIndexed { index, point ->
                val x = ((point.first - minTime).toFloat() / rangeTime) * canvasWidth
                val y = canvasHeight - (((point.second - minValue) / rangeValue) * canvasHeight)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(0xFF00E5FF),
                style = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // X-Axis Time Labels
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
            val startTimeStr = timeFormat.format(Date(minTime))
            val endTimeStr = timeFormat.format(Date(maxTime))

            paintText.textAlign = Paint.Align.LEFT
            drawContext.canvas.nativeCanvas.drawText(startTimeStr, 0f, canvasHeight + 35f, paintText)
            
            paintText.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(endTimeStr, canvasWidth, canvasHeight + 35f, paintText)
        }
    }
}
