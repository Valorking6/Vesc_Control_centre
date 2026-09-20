# 🤖 VESC Control Centre - AI Agent Guidelines & Operating Rules

This document outlines the operational rules, architectural conventions, and documentation standards for AI coding agents working on the **VESC Control Centre** codebase.

---

## 📌 Core Documentation & Release Conventions

1. **Version Numbering Rule:**
   * Version numbers follow a strictly incrementing 1-decimal sequence:
     * `v1.0` $\rightarrow$ `v1.1` $\rightarrow$ `v1.2` $\rightarrow$ ... $\rightarrow$ `v1.9` $\rightarrow$ `v2.0`
   * Every new feature release or significant update increments the minor version by `0.1`.
   * After `1.9`, the next version is `2.0` (not `1.10`).

2. **CHANGELOG.md Formatting Rules:**
   * Always format **Changelog / What's New** sections in `CHANGELOG.md` using **markdown checkboxes** (`- [x]`) and **technical bullet points**.
   * Example:
     ```markdown
     ## 🚀 What's New in v1.1
     - [x] **Feature Name:** Technical description of the implementation.
     ```

---

## 🏗️ Architecture & Domain Overview

* **Application Package:** `com.example.vesccontrolcentre`
* **UI Framework:** Jetpack Compose + Material 3 (`MainScreen.kt`, `LogViewerScreen.kt`, `SpeedometerDial.kt`)
* **Background Service (`VescService.kt`):** Foreground service with `connectedDevice|location|microphone` types. Handles BLE comms, audio simulation, GPX/CSV logging, Health Connect sync, and ongoing lock screen notifications.
* **BLE Communication (`VescBleManager.kt`):** Thread-safe Nordic UART Service (`6e400001-b5a3-f393-e0a9-e50e24dcca9e`). Interleaves `COMM_GET_VALUES` (0x04) and `COMM_GET_DECODED_ADC` (0x35) at 250ms intervals.
* **Profile Switching:** Transmits LispBM REPL commands (`COMM_LISP_REPL_CMD` - byte 138) to update `l-current-max`, `l-in-current-max`, and `l-watt-max` on master and secondary CAN bus ESCs.
* **Engine Sound Simulation (`EngineSoundManager.kt`):** Low-latency `SoundPool` pitch-shifted by ERPM with asymmetric 2-12 MPH volume lerp smoothing.
* **Health Connect (`HealthConnectManager.kt`):** Uses Jetpack Health Connect Client (`1.1.0`) to write `ExerciseSessionRecord` (Biking) with `ExerciseRoute` GPS tracks and total distance.
* **Wear OS (`MainActivity.kt` in `:wear`):** Wear OS companion app mirroring real-time Speed, Voltage, Active Ride Time, and Estimated Range directly on your smartwatch. Uses Google Play Services Wearable `DataClient`.
* **Voice Co-Pilot (`VescVoiceService.kt`, `OnnxWakeWordEngine.kt`, `GeminiAnalyst.kt`, `VescVoiceAnnouncer.kt`):** Hands-free voice command system powered by an open-source ONNX Runtime wake-word engine ("Hey Friday"), Gemini Nano intent parsing, and a customizable TTS announcer.

---

## 🛡️ File Modification Rules & IDE Sync

* **Memory Buffers:** Always use built-in IDE tools (`write_file`, `replace_file_content`, `multi_replace_file_content`) to modify files. **NEVER** use shell scripts (`sed`, `awk`, `echo >`, etc.) to edit source code.
* **Verification:** Run `:app:assembleDebug` via `gradle_build` to verify zero build errors before completing tasks.
