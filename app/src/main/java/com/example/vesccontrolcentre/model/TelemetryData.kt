package com.example.vesccontrolcentre.model

data class TelemetryData(
    val mph: Float = 0f,
    val voltage: Float = 0f,
    val erpm: Float = 0f,
    val motorCurrent: Float = 0f,
    val batteryCurrent: Float = 0f,
    val dutyCycle: Float = 0f,
    val tempMosfet: Float = 0f,
    val tempMotor: Float = 0f,
    val wattHoursUsed: Float = 0f,
    val ampHoursCharged: Float = 0f,
    val tachometerAbs: Long = 0L,
    val faultCode: Int = 0,
    val faultText: String = "NO FAULT",
    val isConnected: Boolean = false,
    val statusText: String = "Disconnected"
)

fun getFaultString(faultCode: Int): String {
    return when (faultCode) {
        0 -> "NO FAULT"
        1 -> "OVER VOLTAGE"
        2 -> "UNDER VOLTAGE"
        3 -> "DRV FAULT"
        4 -> "ABS OVER CURRENT"
        5 -> "OVER TEMP FET"
        6 -> "OVER TEMP MOTOR"
        7 -> "GATE DRIVER OVER VOLT"
        8 -> "GATE DRIVER UNDER VOLT"
        9 -> "MCU UNDER VOLT"
        10 -> "WATCHDOG RESET"
        11 -> "ENCODER SPI"
        12 -> "ENCODER SINCOS"
        13 -> "MCU OVER TEMP"
        else -> "FAULT #$faultCode"
    }
}
