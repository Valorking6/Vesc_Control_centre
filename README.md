# ⚡ VESC Control Centre (v4.2)

A modern, feature-rich Android & Wear OS application designed for real-time telemetry monitoring, 1-tap profile switching, Wear OS watch mirroring, hands-free AI voice commands with "Friday", video telemetry sync, dual-motor CAN bus management, Health Connect integration, and ride logging for VESC-based electric vehicles (*E-Scooters, EUCs, E-Skateboards, Onewheels, and E-Bikes*).

[![Watch the video](https://img.youtube.com/vi/58uABSbTUZc/maxresdefault.jpg)](https://www.youtube.com/watch?v=58uABSbTUZc)

---

> [!WARNING]
> **Safety & Usage Disclaimer**
> 
> * **Do Not Switch Profiles While Riding:** Never change power profiles while the vehicle is in motion. Sudden shifts in motor current limits or power delivery can cause unpredictable acceleration or braking, which may lead to an accident. **Always come to a complete stop before changing profiles.**
> * **Settings Reset on Power Cycle:** Profile changes sent via this app's LispBM commands are applied to the active session only. They do not overwrite your core configuration. Power cycling your VESC will clear these changes and reset all parameters back to your original saved defaults.

---

## 📋 Version History & Changelog

For a complete breakdown of version history, technical feature additions, and release notes, see [CHANGELOG.md](CHANGELOG.md).

---

## 📥 Installation & Setup

### 🚀 Option 1: Direct APK Download (Recommended)
1. Download the latest pre-built `VESC Control Centre v2.2.apk` (Phone) and `wear-debug.apk` (Watch) directly from the [GitHub Releases](https://github.com/Valorking6/Vesc_Control_centre/releases).
2. Open the downloaded `.apk` file on your Android device (ensure *"Install from unknown sources"* is allowed in your browser/file manager settings).
3. Tap **Install** and open **VESC Control Centre**.

---

### 🛠️ Option 2: Build From Source
1. **Clone the Repository:**
   ```bash
   git clone https://github.com/Valorking6/Vesc_Control_centre.git
   ```
2. **Open in Android Studio:** Open the project directory in Android Studio (2026.1+ recommended).
3. **Build Phone & Watch APKs:**
   ```bash
   ./gradlew :app:assembleDebug :wear:assembleDebug
   ```

---

## 🏁 Quick Start Guide

1. **Grant Permissions:** Launch the app and grant **Bluetooth Scan/Connect**, **Location** (for GPX/Health Connect route tracking), **Notifications**, **Microphone**, and optional **Health Connect** permissions when prompted.
2. **Pair VESC:** Go to **Settings & Widgets** $\rightarrow$ **Scan VESC**, select your VESC Bluetooth module address, set motor pole pairs & wheel size, and start live telemetry!
3. **Hands-Free Voice Control:** Say "Hey Friday" to trigger the co-pilot, and speak commands like *"Switch to Max Power"* or *"Run Diagnostic"*.

---

## 🌟 Key Features

### 🎙️ 1. Hands-Free Open-Source Voice Command System ("Hey Friday")
* **Advanced "Friday" AI Co-Pilot:** "Friday" operates as your highly intelligent, dry, British co-pilot. She delivers proactive updates, witty banter, and processes location-aware telemetry using an unsanitized personality module that adapts to your riding speed, heart rate, and surroundings.
* **Custom ONNX Wake-Word Model (`hey_friday.onnx`):** On-device wake-word detection powered by `com.microsoft.onnxruntime:onnxruntime-android:1.18.0` loading your custom `hey_friday.onnx` model and `model_info.json` directly from app assets.
* **Dual-Model Text-To-Speech Routing:** Utilizes ultra-low latency `eleven_flash_v2_5` (75ms response) for instant UI button interactions, whilst routing proactive ride commentary to the high-fidelity `eleven_v3` model to output intricate, expressive inflections over helmet speakers.
* **16kHz Mel-Spectrogram Feature Extraction:** Converts rolling 1.0-second 16kHz PCM audio buffers into 3136-element ($98 \times 32$) log-Mel spectrogram feature matrices matching model input shape `[1, 98, 32]`.
* **Gemini Nano Intent Parser:** Passes recognized speech to local Generative AI model (`GeminiAnalyst.kt`) expecting strict JSON intent structures (`SWITCH_PROFILE`, `RUN_DIAGNOSTIC`).
* **Widget Visual Listening Indicator:** All telemetry widgets display a cyan `🎙️ Hey Friday` badge when the co-pilot is actively listening.

---

### 🎬 2. Action Camera Telemetry Sync
* **Optical Screen Flash Alignment:** Tapping the Sync Marker button in the app flashes the screen pure white for 250ms, giving your camera lens an indisputable visual marker frame.
* **CSV Sync Tag (`Sync_Marker`):** Sets `Sync_Marker = TRUE` in the CSV log for exactly 1.0 second upon triggering.
* **Strict 10Hz CSV Frame Rate:** Writes CSV data in a dedicated background coroutine at a perfectly constant 10Hz (100ms) interval.
* **`utc (ms)` Timestamp Header:** Native header naming for effortless import into telemetry video overlay software.

---

### 🔋 3. Smart Range Prediction
* **Dynamic Efficiency Algorithm:** Calculates rolling energy consumption ($Wh/\text{mi} = \frac{\text{WattHoursUsed}}{\text{DistanceMiles}}$) in real-time.
* **Remaining Distance Calculation:** Estimates remaining mileage ($\text{EstMiles} = \frac{720 - \text{WattHoursUsed}}{\text{Wh/mi}}$) based on your scooter's 720Wh pack, safely avoiding division by zero at startup.

---

### ⌚ 4. Wear OS Live Companion App
* **Wrist Telemetry Mirroring:** Live Wear OS Jetpack Compose app mirroring real-time Speed, Voltage, Active Profile, and Battery Percentage directly on your smartwatch.
* **Biometric Broadcaster (Watch $\rightarrow$ Phone):** Native integration with `Sensor.TYPE_HEART_RATE`. Once granted, Wear OS immediately streams rider heart rate (BPM) over the `/vesc/heart_rate` data layer path directly into the phone's telemetry stream for logging and AI banter.
* **Low-Latency Wearable Data Layer:** Uses Google Play Services Wearable API to bidirectionally route asynchronous JSON payloads and byte arrays securely between devices.

---

### 5. 🔊 Engine Sound Simulator
* **Dynamic Audio Synthesis:** Uses Android's low-latency `SoundPool` API to play seamless custom engine audio loops.
* **ERPM Pitch & Speed Volume Scaling:** Reads live ERPM telemetry to pitch-shift revs, with asymmetric volume smoothing (0.15 attack, 0.50 decay) across a 2 to 12 MPH speed range and immediate hard-stop muting.
* **Library of Sound Loops:** Features 16+ immersive profiles including Formula 1 Racing Engine (High & Low RPM), Dualtron X & Dualtron Thunder performance scooter motors, HyperX Electric Powertrain, V8 Cylinder Engine, Electric Engine Whine, Sci-Fi Thruster, and Spacepod.

---

### 🎬 6. Action Camera Telemetry Sync & Widget Pool
* **Audible Telemetry Sync:** Features an audible sync marker to cleanly align external action camera footage with ride logs.
* **Widget Pool Integration:** Fully integrated into the app's home screen widget pool for 1-tap quick access to sync markers and telemetry controls.

---

### 6. 📱 Unified Single-Connection BLE Architecture
* **Conflict-Free GATT Management:** Maintains a single persistent, thread-safe Bluetooth Low Energy (BLE) connection via the Nordic UART Service (`6e400001-b5a3-f393-e0a9-e50e24dcca9e`).
* **Interleaved Telemetry & ADC Polling:** Interleaves standard `COMM_GET_VALUES` telemetry polling with `COMM_GET_DECODED_ADC` (`0x35`) to capture exact raw throttle/brake inputs (`ADC1`/`ADC2`).

---

### 7. ⚡ 1-Tap Profile Switcher (Powered by LispBM)
* **4 Customizable Profiles:** Crawl, Normal, Long Range, and Max Power mode switching.
* **LispBM REPL Packets:** Transmits LispBM REPL commands (`COMM_LISP_REPL_CMD` - byte `138`) to master and secondary CAN bus VESC controllers.

---

### 8. 📁 Ride Logging, Storage Access & X/Y Data Plotter
* **Interactive Data Plotter (`LogViewerScreen`):** Open any `.csv` ride log directly in the app to plot interactive X/Y line charts comparing Speed, Viewer/Analyst graphs comparing Speed, Voltage, Amps, Temperatures, G-Forces, Gyro Lean Angles, and ADC Throttle/Brake inputs.
* **Strava-Compatible GPX 1.1 Logger:** Logs track points with `<speed>`, `<course>`, `<hdop>`, and `<sat>` tags with stationary drift filtering.

---

## 🛠️ Technology Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose, Material 3 & Wear OS Compose
* **Wake Word Engine:** ONNX Runtime Android SDK (`com.microsoft.onnxruntime:onnxruntime-android:1.18.0`)
* **Watch Connectivity:** Google Play Services Wearable API (`play-services-wearable:18.1.0`)
* **Fitness Integration:** Android Health Connect API (`androidx.health.connect:connect-client`)
* **Concurrency:** Kotlin Coroutines, `StateFlow`, `SharedFlow`, `Mutex`
* **Architecture:** Foreground Service (`VescService`), BLE Manager (`VescBleManager`), RemoteViews AppWidgets
* **Minimum SDK:** Android 8.0 (API 26+) / Wear OS 3.0+ (API 30+)
* **Target SDK:** Android 15 (API 35/37)

---

## 📜 License

Distributed under the **GNU General Public License v3.0 (GPLv3)**. See [`LICENSE`](LICENSE) for details.
