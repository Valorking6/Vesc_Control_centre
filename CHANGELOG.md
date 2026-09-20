# 📋 Changelog & Version History - VESC Control Centre

All notable changes, release updates, and technical feature additions to the VESC Control Centre project are documented in this file.

---

## 🚀 What's New in v4.2

- [x] **Qwen2.5-1.5B Model Filename Target:** Updated `ModelProvisioner.kt` and `VescService.kt` model filename targets to `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm` for exact 1-to-1 match with HuggingFace/LiteRT-LM model bundles.

---

## 🚀 What's New in v4.1

- [x] **Global Singleton ChatEngine Manager:** Converted `ChatEngine.kt` to manage a global `@Volatile var instance: ChatEngine?` thread-safe singleton with `ChatEngine.initializeGlobal(...)` and `isReady` state flags, ensuring state persistence across background service coroutine scopes.

---

## 🚀 What's New in v4.0

- [x] **Qwenliter Target Filename Alignment:** Updated `ModelProvisioner.kt` and `VescService.kt` model filename targets to `Qwenliter.litertlm` for character-for-character matching with local LiteRT-LM model assets.

---

## 🚀 What's New in v3.9

- [x] **Explicit .litertlm Provisioner Filter:** Updated `ModelProvisioner.kt` target filename strictly to `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm` and restricted fallback file searches exclusively to `.litertlm` extensions.

---

## 🚀 What's New in v3.8

- [x] **Lateinit ChatEngine Initialization & Service Lifecycle:** Refactored `VescService.kt` to declare `chatEngine` as a `lateinit var`, initialize on service boot in `serviceScope`, and execute `onWakeWordTriggered(transcript)` with `::chatEngine.isInitialized` initialization checks.

---

## 🚀 What's New in v3.7

- [x] **Asynchronous ChatEngine Startup & Greeting:** Updated `VescService.kt` initialization routine to load `ChatEngine` on `Dispatchers.IO` background scope and immediately stream a startup greeting ("System online. Greet the rider briefly.") upon successful model loading.

---

## 🚀 What's New in v3.6

- [x] **Android 12+ Permission Safety Guards:** Wrapped GPS (`startGpsUpdates`), BLE (`initAndConnect`), and wake-word recording (`startListening`) in explicit runtime permission checks (`ACCESS_FINE_LOCATION`, `BLUETOOTH_CONNECT`, `RECORD_AUDIO`) to prevent foreground service process crashes upon feature invocation.

---

## 🚀 What's New in v3.5

- [x] **GPU-Accelerated LiteRT-LM & Model Provisioner:** Updated `ChatEngine.kt` and created `ModelProvisioner.kt` to auto-discover Qwen task bundles on external files dir (`Android/data/com.example.vesccontrolcentre/files/`) and initialize with `Backend.GPU()`, with graceful fallback to `Backend.CPU()`.
- [x] **Native OpenCL / VNDK Manifest Declarations:** Added `<uses-native-library android:name="libOpenCL.so" android:required="false" />` and `libvndksupport.so` in `AndroidManifest.xml` for hardware-accelerated GPU inference.

---

## 🚀 What's New in v3.4

- [x] **LiteRT-LM ChatEngine Streaming Integration:** Created `ChatEngine.kt` to handle LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android:0.16.1`) `Engine` and `Conversation` streaming flows (`sendMessageAsync`) with `Backend.CPU()`.

---

## 🚀 What's New in v3.3

- [x] **MediaPipe LlmInference Session Crash Fix:** Updated `LocalLlmEngine.kt` and `GeminiAnalyst.kt` with an in-character fallback mechanism (`generateFallbackResponse`) to catch lazy session initialization exceptions (`MediaPipeException` on `TfLitePrefillDecodeRunnerCalculator`) when model task bundles on `/data/local/tmp/` lack required TFLite LLM signatures.

---

## 🚀 What's New in v3.2

- [x] **Instant App Launch Foreground Service:** Updated `MainActivity.kt` to boot `VescService` in the foreground immediately upon app launch when permissions are granted, initializing background BLE connection listeners without requiring manual user interaction.

---

## 🚀 What's New in v3.1

- [x] **MediaPipe Tasks GenAI Upgrade:** Updated `com.google.mediapipe:tasks-genai` to `0.10.18` in `app/build.gradle.kts` for native STABLEHLO op support for Qwen 2.5 on-device LLM inference.

---

## 🚀 What's New in v3.0

- [x] **Dual SharedPreferences MAC Persistence:** Updated `MainScreen.kt` and `VescService.kt` (`getSavedMacAddress()`) to automatically query and persist `LAST_VESC_MAC` under `VescPrefs` alongside `mac_address` in `vesc_prefs`.

---

## 🚀 What's New in v2.9

- [x] **VescTelemetry Container Class:** Created `VescTelemetry.kt` to hold speed (km/h), battery percentage, voltage, motor/ESC temperatures, motor current, and fault status, providing a formatted prompt summary helper (`toPromptSummary()`).
- [x] **Dynamic Telemetry Prompt Injection:** Updated `LocalLlmEngine.kt` to inject live `VescTelemetry` snapshots directly into Qwen ChatML system prompts (`Current vehicle telemetry: [...]`).
- [x] **Real-Time Telemetry Caching:** Wired `VescService.kt` to update `latestTelemetry` at 10Hz as incoming BLE packets arrive, making live speed, battery, and thermals instantly accessible to local LLM queries.

---

## 🚀 What's New in v2.8

- [x] **TTS Synthesis-To-File & RVC Audio Pipeline:** Created `VescVoiceHandler.kt` to synthesize Android TTS strings to local cache WAV files, process raw PCM data through `RvcVoiceConverter` ONNX model (`Friday.onyx`), and play custom converted voice waveforms via `AudioTrack`.

---

## 🚀 What's New in v2.7

- [x] **RVC Voice Converter Engine:** Created `RvcVoiceConverter.kt` to load `Friday.onyx` directly from assets for ONNX-based Retrieval-based Voice Conversion (RVC) audio transformation.

---

## 🚀 What's New in v2.6

- [x] **Local Quantized Qwen / LiteRT-LM Engine:** Created `LocalLlmEngine.kt` to load local quantized models (`/data/local/tmp/model_quantized.tflite`) using MediaPipe Tasks GenAI with ChatML-formatted system prompts (`<|im_start|>system...<|im_end|>`) for ultra-fast, offline AI Co-Pilot responses.
- [x] **VescService Integration:** Wired `LocalLlmEngine` directly into `VescService` so that when the wake word is detected or an AI event fires, the engine generates in-character responses and announces them out loud via `VescVoiceAnnouncer`.

---

## 🚀 What's New in v2.5

- [x] **LiteRT / MediaPipe Tasks GenAI On-Device LLM:** Integrated `com.google.mediapipe:tasks-genai:0.10.14` in `GeminiAnalyst.kt` to load local quantized models (`/data/local/tmp/model.bin`) for high-speed, off-grid AI co-pilot telemetry analysis and dynamic connection greetings.

---

## 🚀 What's New in v2.4

- [x] **Bluetooth Hardware Recovery:** Registered a `BroadcastReceiver` in `VescService` listening for `BluetoothAdapter.ACTION_STATE_CHANGED`. When Bluetooth hardware transitions to `STATE_ON`, `VescService` re-instantiates the `BluetoothDevice` via its saved MAC address and calls `.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)` to instantly restore background listeners.
- [x] **Voice Announcer Instance Access:** Exposed `voiceAnnouncerInstance` on `VescService` for TTS voice selection and playback control in `MainScreen`.

---

## 🚀 What's New in v2.3

- [x] **Microphone Foreground Service Compliance:** Fixed silent background audio captures by explicitly wrapping `VescService` with Android 14 `FOREGROUND_SERVICE_TYPE_MICROPHONE` and requesting runtime `RECORD_AUDIO` permissions during the app's initial startup check.
- [x] **Background ONNX Tensor Safety:** Handled native `AudioRecord` background buffer starvation safely inside the 16kHz coroutine by explicitly dropping empty frames and gracefully logging hardware dropouts via `Log.e`.

---

## 🚀 What's New in v2.2

- [x] **AI Voice Selector UI:** Added a sleek, Material 3 dropdown selector to the **Settings** tab allowing you to choose your preferred AI Co-Pilot voice.
- [x] **Dynamic Voice Filtering:** Native `TextToSpeech` voices are automatically queried, filtered strictly for English (`en`), and intelligently formatted (e.g. `English (United States) - Female (Local)`).
- [x] **Instant Preview & Persistence:** Tapping a voice plays an immediate audio preview ("Voice selected"). The selection is saved natively to `SharedPreferences` and restores automatically across app restarts.

---

## 🚀 What's New in v2.1

- [x] **Smart Range Idle Guard:** Added strict guard logic to `calculateSmartRange()` in `VescService.kt` to bypass rolling average calculations when the scooter is idle (`speedMph <= 0.5f && motorAmps <= 0.1f`), rendering `-- mi` rather than calculating off stagnant baseline data.
- [x] **Tightened "Hey Friday" False-Wake Filters:** Hardened the ONNX background noise filter. The wake-word trigger now requires an explicitly strict `0.80f` confidence threshold for at least 3 consecutive $98 \times 32$ log-Mel inference loops before executing SpeechRecognizer.

---

## 🚀 What's New in v2.0

- [x] **Strict Confidence Threshold:** Raised the wake-word trigger threshold to strictly eliminate false activations from ambient background noise.
- [x] **L1 Consecutive Frames Filter:** Requires the model's prediction confidence to exceed the threshold for at least **3 consecutive inference frames** before triggering speech capture.
- [x] **L3 Cooldown Filter:** Mutes/ignores all ONNX model outputs for **2000ms** after a successful trigger to prevent rapid double-triggering.

---

## 🚀 What's New in v1.9

- [x] **Mel-Spectrogram Feature Extraction Pipeline:** Built a pure-Kotlin Mel-Spectrogram feature extractor (`extractMelSpectrogram`) in `OnnxWakeWordEngine.kt` with a 32-bin triangular Mel filterbank and 512-sample Hann DFT window.
- [x] **Rolling 1.0-Second Audio Ring Buffer:** Maintained a continuous 16,000-sample (1.0 second) rolling audio window fed by `AudioRecord` at 16kHz.
- [x] **3136-Element Tensor Shape Matching:** Converts raw audio frames into $98 \times 32$ log-Mel spectrogram feature matrices, matching `hey_friday.onnx`'s required tensor shape (`[1, 98, 32]`) and completely eliminating model dimension crashes.

---

## 🚀 What's New in v1.8

- [x] **"Hey Friday" Custom ONNX Model:** Integrated your custom `hey_friday.onnx` model and `model_info.json` directly into the app's `assets/` directory for local ONNX Runtime inference.
- [x] **Widget Visual Listening Indicator:** Updated all phone telemetry home screen widgets (2x1, 2x2, 4x2) to display a bright cyan `🎙️ Hey Friday` badge whenever the background wake-word engine is active.
- [x] **Live AI Co-Pilot Dashboard Card:** Restored the `🤖 AI TELEMETRY CO-PILOT` card on the main Live Telemetry Dashboard, displaying real-time power tips and fault diagnostics generated by local Gemini Nano.

---

## 🚀 What's New in v1.7

- [x] **Open-Source ONNX Runtime Wake-Word Engine:** Replaced commercial Picovoice Porcupine SDK with open-source ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android:1.18.0`) to avoid enterprise paywalls and keep the app 100% free & open source.
- [x] **Local Model Loading (`model.onnx`):** `OnnxWakeWordEngine.kt` loads custom wake-word ONNX models directly from the app's `assets/` directory.
- [x] **Real-Time 16kHz PCM Audio Stream:** Captures 16kHz mono PCM 16-bit audio via `AudioRecord` in a background coroutine, normalizing frames and feeding tensors to ONNX inference.
- [x] **Confidence Threshold Trigger:** Triggers audio beep and hands-free `SpeechRecognizer` hand-off whenever model inference confidence crosses 0.5f.

---

## 🚀 What's New in v1.6

- [x] **Unified VescService Lifecycle Integration:** Consolidated wake-word detection, SpeechRecognizer capture, and Gemini Nano intent parsing directly into `VescService.kt` so voice co-pilot features are strictly tied to the active foreground ride session.
- [x] **Combined Foreground Service Types:** Updated `AndroidManifest.xml` so `VescService` runs as `foregroundServiceType="connectedDevice|location|microphone"`, keeping microphone access alive in the background while riding.
- [x] **Post-Notification Initialization:** `OnnxWakeWordEngine`, `SpeechRecognizer`, and `GeminiAnalyst` initialize strictly after `startForeground()` posts the initial system notification.
- [x] **Graceful Teardown & Garbage Collection:** Updated `VescService.onDestroy()` to explicitly call `.release()` on `OnnxWakeWordEngine`, `.destroy()` on `SpeechRecognizer`, and `.shutdown()` on `VescVoiceAnnouncer`, setting all references to `null`.
- [x] **SecurityException Resilience:** Mid-ride microphone permission revocations or audio errors are caught gracefully, disabling voice co-pilot without interrupting the live BLE telemetry or logging loops.

---

## 🚀 What's New in v1.5

- [x] **Voice Capture & Audio Beep:** Plays a `ToneGenerator` beep upon wake word detection and launches Android's native `SpeechRecognizer` to capture spoken voice commands hands-free.
- [x] **Gemini Nano Intent Parsing:** Passes captured voice command strings to local Generative AI model (`GeminiAnalyst.kt`) to parse strict JSON intents (`SWITCH_PROFILE`, `RUN_DIAGNOSTIC`).
- [x] **Voice Feedback Out Loud:** Confirms profile switches or diagnostic statuses out loud via `VescVoiceAnnouncer.speak()` (e.g. *"Switching to Eco Profile"*).

---

## 🚀 What's New in v1.4

- [x] **Video Telemetry Camera Sync Marker:** Added a prominent "Sync Marker" button in the Live Telemetry Dashboard that flashes the phone screen pure white for 250ms (serving as a optical alignment flash for DJI Osmo Action 4 / WOLFANG action cameras) and flags `Sync_Marker` as `TRUE` in the CSV log for exactly 1.0 second.
- [x] **Strict 10Hz (100ms) CSV Telemetry Frame Rate:** Converted CSV ride logging to a dedicated background coroutine executing at a strict 10Hz frame rate (`delay(100)`), delivering perfectly consistent timing for video telemetry overlays.
- [x] **Action Cam Header Alignment:** Renamed the main CSV timestamp column from `Timestamp_ms` to `utc (ms)` for immediate plug-and-play recognition in video overlay editing software.

---

## 🚀 What's New in v1.3

- [x] **Smart Range Prediction:** Calculates real-time rolling efficiency (`Wh/Mi = WhUsed / DistanceMiles`) based on 720Wh capacity and GPS movement deltas. Predicts remaining range (`estimatedRemainingMiles`) in real-time without static lookup tables.
- [x] **Home Screen Widget Range Display:** Updated home screen widgets (2x2 and 4x2) to render live estimated remaining distance (`Range: XX.X mi`).
- [x] **Wear OS Smart Range Mirroring:** Updated Wear OS DataLayer payload (`/telemetry`) to broadcast `estimatedRemainingMiles` directly to connected smartwatches.
- [x] **Smartwatch UI Upgrade:** Enhanced Wear OS watch interface (`MainActivity.kt`) with a dedicated `EST. RANGE` readout alongside Speed, Voltage, and Active Ride Duration.

---

## 🚀 What's New in v1.2

- [x] **Wear OS Live Telemetry Companion App (`:wear` module):** Real-time smartwatch telemetry mirroring for Wear OS smartwatches via Google Play Services Wearable Data Client.
- [x] **Data Broadcaster:** Phone app (`VescService.kt`) broadcasts urgent `/telemetry` payload DataMaps (`mph`, `voltage`, `activeRideDurationMs`, and `timestamp`) on every live tick.
- [x] **High-Contrast Watch UI:** Standalone Wear OS Compose interface (`MainActivity.kt`) displaying live speed (cyan), battery voltage (green), and auto-pausing active ride duration (white).
- [x] **Wearable Data Client Listener:** Automatic connection lifecycle management using `Wearable.getDataClient(this).addListener` on the watch.

---

## 🚀 What's New in v1.1

- [x] **Native Health Connect Syncing & GPS Route Mapping:** Automatically syncs completed scooter rides directly to Android's **Health Connect** datastore as `BIKING` exercise sessions with full `ExerciseRoute` GPS track points and total distance.
- [x] **Lock Screen Persistent Telemetry Dashboard:** Ongoing foreground notification (`NotificationCompat.VISIBILITY_PUBLIC`) rendered directly on the lock screen with a live line summary (`Speed: XX.X MPH | Battery: XX.X V | Amps: XX.X A | Time: MM:SS`).
- [x] **Auto-Pausing Active Ride Duration Timer:** Calculates moving time (`speedMph > 0.5f`) and automatically freezes when stationary, providing exact active duration on the UI dashboard, widgets, lock screen, and CSV exports.
- [x] **Dynamic Home Screen Widgets:** 1x1 Engine Sound Toggle widget, 4 Profile Switcher widgets (2x1), and multi-size Live Telemetry Widgets (2x1, 2x2, 4x2) with digital and dial gauge options.
- [x] **GPS Stationary Drift Filter ("Spiderwebbing" Removal):** Discards stationary GPS jitter points at red lights while preserving the exact stopping coordinate to render clean route maps on GPX and Health Connect.
- [x] **Interactive X/Y Telemetry Data Plotter:** In-app `.csv` log viewer (`LogViewerScreen.kt`) to graph and analyze Speed, Viewer/Analyst graphs comparing Speed, Voltage, Amps, Temperatures, G-Forces, Gyro Lean Angles, and ADC Throttle/Brake inputs.
- [x] **Interleaved BLE ADC Telemetry Polling:** Polls `COMM_GET_DECODED_ADC` (`0x35`) alongside standard telemetry to record real-time thumb input voltages.
