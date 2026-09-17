# ⚡ VESC Control Centre (v1.1)

A modern, feature-rich Android application designed for real-time telemetry monitoring, 1-tap profile switching, dual-motor CAN bus management, Health Connect integration, and ride logging for VESC-based electric vehicles (*E-Scooters, EUCs, E-Skateboards, Onewheels, and E-Bikes*).

[![Watch the video](https://img.youtube.com/vi/58uABSbTUZc/maxresdefault.jpg)](https://www.youtube.com/watch?v=58uABSbTUZc)

---

> [!WARNING]
> **Safety & Usage Disclaimer**
> 
> * **Do Not Switch Profiles While Riding:** Never change power profiles while the vehicle is in motion. Sudden shifts in motor current limits or power delivery can cause unpredictable acceleration or braking, which may lead to an accident. **Always come to a complete stop before changing profiles.**
> * **Settings Reset on Power Cycle:** Profile changes sent via this app's LispBM commands are applied to the active session only. They do not overwrite your core configuration. Power cycling your VESC will clear these changes and reset all parameters back to your original saved defaults.

---

## 🚀 What's New in v1.1

- [x] **Native Health Connect Syncing & GPS Route Mapping:** Automatically syncs completed scooter rides directly to Android's **Health Connect** datastore as `BIKING` exercise sessions with full `ExerciseRoute` GPS track points and total distance.
- [x] **Lock Screen Persistent Telemetry Dashboard:** Ongoing foreground notification (`NotificationCompat.VISIBILITY_PUBLIC`) rendered directly on the lock screen with a live line summary (`Speed: XX.X MPH | Battery: XX.X V | Amps: XX.X A | Time: MM:SS`).
- [x] **Auto-Pausing Active Ride Duration Timer:** Calculates moving time (`speedMph > 0.5f`) and automatically freezes when stationary, providing exact active duration on the UI dashboard, widgets, lock screen, and CSV exports.
- [x] **Dynamic Home Screen Widgets:** 1x1 Engine Sound Toggle widget, 4 Profile Switcher widgets (2x1), and multi-size Live Telemetry Widgets (2x1, 2x2, 4x2) with digital and dial gauge options.
- [x] **GPS Stationary Drift Filter ("Spiderwebbing" Removal):** Discards stationary GPS jitter points at red lights while preserving the exact stopping coordinate to render clean route maps on GPX and Health Connect.
- [x] **Interactive X/Y Telemetry Data Plotter:** In-app `.csv` log viewer (`LogViewerScreen.kt`) to graph and analyze Speed, Voltage, Amps, Temperatures, G-Forces, Gyro Lean Angles, and ADC Throttle/Brake inputs.
- [x] **Interleaved BLE ADC Telemetry Polling:** Polls `COMM_GET_DECODED_ADC` (`0x35`) alongside standard telemetry to record real-time thumb input voltages.

---

## 📥 Installation & Setup

### 🚀 Option 1: Direct APK Download (Recommended)
1. Download the latest pre-built `VESC Control Centre v1.1.apk` directly from the [GitHub Releases](https://github.com/Valorking6/Vesc_Control_centre/releases).
2. Open the downloaded `.apk` file on your Android device (ensure *"Install from unknown sources"* is allowed in your browser/file manager settings).
3. Tap **Install** and open **VESC Control Centre**.

---

### 🛠️ Option 2: Build From Source
1. **Clone the Repository:**
   ```bash
   git clone https://github.com/Valorking6/Vesc_Control_centre.git
   ```
2. **Open in Android Studio:** Open the project directory in Android Studio (2026.1+ recommended).
3. **Build APK:**
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🏁 Quick Start Guide

1. **Grant Permissions:** Launch the app and grant **Bluetooth Scan/Connect**, **Location** (for GPX/Health Connect route tracking), **Notifications**, and optional **Health Connect** permissions when prompted.
2. **Pair VESC:** Go to **Settings & Widgets** $\rightarrow$ **Scan VESC**, select your VESC Bluetooth module address, set motor pole pairs & wheel size, and start live telemetry!
3. **Add Home Screen Widgets:** Long-press your phone's home screen $\rightarrow$ **Widgets** $\rightarrow$ **VESC Control Centre** to add profile buttons, telemetry widgets, and sound toggles.

---

## 🌟 Key Features

### 1. 🔊 Engine Sound Simulator
* **Dynamic Audio Synthesis:** Uses Android's low-latency `SoundPool` API to play seamless custom `.ogg` engine loops.
* **ERPM Pitch & Speed Volume Scaling:** Reads live ERPM telemetry to pitch-shift revs, with asymmetric volume smoothing (0.15 attack, 0.50 decay) across a 2 to 12 MPH speed range and immediate hard-stop muting.
* **Library of 11 Sound Loops:** Includes V8 Cylinder Engine, Electric Engine Whine (Standard & Louder), Sci-Fi Thruster, Hover Vehicle, Spacepod, and Turbo Diesel.

---

### 2. 📱 Unified Single-Connection BLE Architecture
* **Conflict-Free GATT Management:** Maintains a single persistent, thread-safe Bluetooth Low Energy (BLE) connection via the Nordic UART Service (`6e400001-b5a3-f393-e0a9-e50e24dcca9e`).
* **Interleaved Telemetry & ADC Polling:** Interleaves standard `COMM_GET_VALUES` telemetry polling with `COMM_GET_DECODED_ADC` (`0x35`) to capture exact raw throttle/brake inputs (`ADC1`/`ADC2`).

---

### 3. ⚡ 1-Tap Profile Switcher (Powered by LispBM)
* **4 Customizable Profiles:**
  * 🐢 **Crawl Mode:** Restricted torque for technical terrain or low-speed maneuvers.
  * ⚡ **Normal:** Balanced daily commute setup.
  * 🔋 **Long Range:** Optimized efficiency to conserve battery capacity.
  * 🚀 **Max Power:** Peak acceleration and full current/wattage output.
* **Custom Limit Editor:** Edit and persist custom values for Motor Current Max (`l-current-max`), Battery Current Max (`l-in-current-max`), and Max Power (`l-watt-max`).
* **LispBM REPL Packets:** Generates and transmits LispBM packets (`COMM_LISP_REPL_CMD` - byte `138`) directly to VESC controllers.

---

### 4. 📡 Dual VESC / CAN Bus Forwarding
* **Dual ESC Support:** Profile commands executed on the master VESC are automatically forwarded to secondary motor controllers across the CAN bus.
* **Configurable Secondary CAN ID:** Target a specific secondary ESC (e.g., CAN ID `53`, `1`, `2`) or set CAN ID to **`255` for CAN Broadcast** to update all motor controllers on the CAN bus simultaneously.

```lisp
(progn 
  (conf-set 'l-current-max 85.0) 
  (conf-set 'l-in-current-max 28.0) 
  (conf-set 'l-watt-max 3500.0) 
  (can-cmd 53 "(progn (conf-set 'l-current-max 85.0) (conf-set 'l-in-current-max 28.0) (conf-set 'l-watt-max 3500.0))"))
```

---

### 5. ⏱️ Live Telemetry & Speedometer Dial
* **Comprehensive Metrics:** Speed (Digital or 240° Analog Dial), Voltage, Duty Cycle, Motor & Battery Current, MOSFET & Motor Temps, ERPM, Watt-Hours Used, Regen Ah, and Fault Diagnostic Warnings.
* **Active Ride Duration Clock:** Live moving-time clock on the UI dashboard that automatically pauses when stopped.
* **Speed Unit Switch:** Toggle between **MPH** and **KM/H**.

---

### 6. 📁 Ride Logging, Storage Access & X/Y Data Plotter
* **Interactive Data Plotter (`LogViewerScreen`):** Open any `.csv` ride log directly in the app to plot interactive X/Y line charts comparing Speed, Voltage, Amps, Temperatures, G-Forces (`Accel_X/Y/Z`), Gyro Lean Angles (`Gyro_X/Y/Z`), and `ADC_Throttle`/`ADC_Brake` inputs.
* **Storage Location Options:** Save logs to *Public Documents (`/Documents/VESC_Logs/`)*, *App Private Sandbox*, or pick a custom folder using Android's *Storage Access Framework (SAF)*.
* **Strava-Compatible GPX 1.1 Logger:** Logs track points with `<speed>`, `<course>`, `<hdop>`, and `<sat>` tags.

---

### 7. 🧩 Widgets & Health Sync
* **1x1 Engine Sound Toggle Widget**
* **4 Profile Widgets (2x1)** with active state indicators.
* **3 Telemetry Widget Sizes (2x1, 2x2, 4x2)** with digital/dial gauge modes and live active ride duration.
* **Health Connect Auto-Sync** on scooter disconnection.

---

## 🛠️ Technology Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose & Material 3
* **Fitness Integration:** Android Health Connect API (`androidx.health.connect:connect-client`)
* **Concurrency:** Kotlin Coroutines, `StateFlow`, `SharedFlow`, `Mutex`
* **Architecture:** Foreground Service (`VescService`), BLE Manager (`VescBleManager`), RemoteViews AppWidgets
* **Minimum SDK:** Android 8.0 (API 26+)
* **Target SDK:** Android 15 (API 35/37)

---

## 📜 License

Distributed under the **GNU General Public License v3.0 (GPLv3)**. See [`LICENSE`](LICENSE) for details.
