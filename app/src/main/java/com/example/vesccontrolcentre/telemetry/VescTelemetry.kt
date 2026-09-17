package com.example.vesccontrolcentre.telemetry

data class VescTelemetry(
    val speedKmh: Float = 0.0f,
    val batteryPercent: Int = 100,
    val voltage: Float = 0.0f,
    val motorTempC: Float = 0.0f,
    val fetTempC: Float = 0.0f,
    val currentAmps: Float = 0.0f,
    val fault: String = "None"
) {
    fun toPromptSummary(): String {
        return "Speed: ${"%.1f".format(speedKmh)} km/h, Battery: $batteryPercent% (${"%.1f".format(voltage)}V), Motor: ${"%.1f".format(motorTempC)}°C, ESC: ${"%.1f".format(fetTempC)}°C, Draw: ${"%.1f".format(currentAmps)}A, Fault: $fault"
    }
}
