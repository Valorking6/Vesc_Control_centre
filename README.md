# ⚡ VESC Control Centre

A modern, feature-rich Android application designed for real-time telemetry monitoring, 1-tap profile switching, dual-motor CAN bus management, and ride logging for VESC-based electric vehicles (*E-Scooters, EUCs, E-Skateboards, Onewheels, and E-Bikes*).

---

## 🌟 Key Features

### 1. 📱 Unified Single-Connection BLE Architecture
* **Conflict-Free GATT Management:** Maintains a single persistent, thread-safe Bluetooth Low Energy (BLE) connection via the Nordic UART Service (`6e400001-b5a3-f393-e0a9-e50e24dcca9e`).
* **Interleaved Commands:** Seamlessly alternates continuous 250ms live telemetry polling (`COMM_GET_VALUES`) with profile modification commands without GATT collisions or disconnections.

---

### 2. ⚡ 1-Tap Profile Switcher (Powered by LispBM)
* **4 Customizable Profiles:**
  * 🐢 **Crawl Mode:** Smooth throttle & restricted torque for technical terrain or low-speed crawling.
  * ⚡ **Normal:** Balanced daily commute setup.
  * 🔋 **Long Range:** Optimized efficiency to conserve battery capacity.
  * 🚀 **Max Power:** Peak acceleration and full current/wattage output.
* **Custom Limit Editor:** Users can edit and persist custom values for:
  * **Motor Current Max ($A$)** (`l-current-max`)
  * **Battery Current Max ($A$)** (`l-in-current-max`)
  * **Max Power ($W$)** (`l-watt-max`)
* **LispBM Command Execution:** Profile changes generate and transmit LispBM REPL packets (`COMM_LISP_REPL_CMD` - byte `138`) directly to VESC controllers running LispBM firmware.

---

### 3. 📡 Dual VESC / CAN Bus Forwarding
* **Dual ESC Support:** Profile commands are executed on the master VESC (connected via BLE) and automatically forwarded to secondary motor controllers across the CAN bus.
* **Configurable Secondary CAN ID:** Target a specific secondary ESC (e.g., CAN ID `53`, `1`, `2`) or set CAN ID to **`255` for CAN Broadcast** to update all motor controllers on the CAN bus simultaneously.

```lisp
(progn 
  (conf-set 'l-current-max 85.0) 
  (conf-set 'l-in-current-max 28.0) 
  (conf-set 'l-watt-max 3500.0) 
  (can-cmd 53 "(progn (conf-set 'l-current-max 85.0) (conf-set 'l-in-current-max 28.0) (conf-set 'l-watt-max 3500.0))"))
```

---

### 4. ⏱️ Live Telemetry & Speedometer Dial
* **Comprehensive Metrics:**
  * **Speed** (3-digit digital readout or speedometer dial)
  * **Battery Voltage ($V$)** & **Duty Cycle ($\%$)**
  * **Motor Current ($A$)** & **Battery Current ($A$)**
  * **Controller MOSFET Temp ($^\circ\text{C}$)** & **Motor Temp ($^\circ\text{C}$)**
  * **ERPM**, **Energy Consumed ($Wh$)**, and **Regen Braking ($Ah$)**
  * **Live Diagnostic Fault Codes** (`OVER_TEMP`, `OVER_VOLTAGE`, `DRV_FAULT`, etc.)
* **Speedometer Dial Gauge:** Interactive 240° Canvas gauge with dynamic sweep gradient (Cyan $\rightarrow$ Green $\rightarrow$ Yellow $\rightarrow$ Red), tick marks, rotating needle indicator, and 3-digit speed readout.
* **Speed Unit Switch:** Toggle between **MPH** and **KM/H** ($1 \text{ MPH} = 1.60934 \text{ KM/H}$).

---

### 5. 📁 Dual Ride Logging System
* **Strava-Compatible GPX 1.1 Logger:** Logs GPS track points with ISO-8601 UTC timestamps and elevation. Automatically ignores `(0.0, 0.0)` uninitialized coordinates.
* **Raw CSV Telemetry Logger:** Records millisecond-accurate timestamped motor current, battery voltage, duty cycle, MOSFET temperatures, and energy consumption.
* **Scooter Shutdown Detection:** Automatically detects scooter power-off / BLE disconnects (`STATE_DISCONNECTED`), appends the final closing tags (`</trkseg></trk></gpx>`), flushes files to `Documents/`, updates lock screen notifications to *"VESC Disconnected - Ride logs saved"*, and pops up a visual Toast confirmation.

---

### 6. 🧩 Home Screen Widgets & Launcher Shortcuts
* **4 Profile Widgets (2x1):** Quick 1-tap profile switching from the home screen with real-time active status badges (`⚡ ACTIVE` vs `OFF`).
* **3 Telemetry Widget Sizes:**
  * **Compact (2x1):** Quick 2-metric status bar.
  * **Standard (2x2):** Speedometer gauge/digits + 4 customizable metrics.
  * **Full Dashboard (4x2):** Comprehensive live dashboard showing up to 8 customizable metrics + fault diagnostic alerts.
* **Widget Metrics Toggles:** Customize which VESC variables appear on home screen widgets via settings.

---

## 🛠️ Technology Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose & Material 3
* **Concurrency:** Kotlin Coroutines, `StateFlow`, `SharedFlow`, `Mutex`
* **Architecture:** Foreground Service (`VescService`), BLE Manager (`VescBleManager`), RemoteViews AppWidgets
* **Minimum SDK:** Android 8.0 (API 26+)
* **Target SDK:** Android 15 (API 35/37)

---

## 🛠️ Setup & Installation

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/your-username/VescControlcentre.git
   ```
2. **Open in Android Studio:** Open the project directory in Android Studio (2026.1+ recommended).
3. **Build APK:**
   ```bash
   ./gradlew assembleDebug
   ```
4. **Grant Permissions:** Launch the app and grant **Bluetooth Scan/Connect**, **Location** (for GPX tracking), and **Notification** permissions when prompted.
5. **Pair VESC:** Go to **Settings & Widgets** $\rightarrow$ **Scan VESC**, select your VESC Bluetooth module address, set motor pole pairs/wheel size, and start live telemetry!

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for details.
