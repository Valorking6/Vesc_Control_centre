package com.example.vesccontrolcentre.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

class OnnxWakeWordEngine(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {

    companion object {
        private const val TAG = "OnnxWakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_READ_SIZE = 1280 // 80ms chunk
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val REQUIRED_CONSECUTIVE_FRAMES = 1
        private const val COOLDOWN_DURATION_MS = 2000L

        private const val MEL_FRAMES = 76
        private const val MEL_BINS = 32
        private const val EMBEDDING_SIZE = 96
        private const val EMBEDDING_FRAMES = 16
    }

    private var ortEnv: OrtEnvironment? = null
    private var melSession: OrtSession? = null
    private var embeddingSession: OrtSession? = null
    private var classifierSession: OrtSession? = null

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isListening = false

    private var consecutiveHighConfidenceFrames = 0
    private var lastTriggerTimeMs = 0L

    // Rolling buffers (pre-filled with 0.0f)
    private val melBuffer = FloatArray(MEL_FRAMES * MEL_BINS) { 0.0f }
    private val embeddingBuffer = FloatArray(EMBEDDING_FRAMES * EMBEDDING_SIZE) { 0.0f }

    init {
        initOrt()
    }

    private fun initOrt() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val env = ortEnv ?: throw IllegalStateException("Failed to initialize OrtEnvironment")

            // Load Melspectrogram Model
            val melBytes = loadModelFromAssets(context, "melspectrogram.onnx")
            melSession = env.createSession(melBytes)
            Log.d(TAG, "Loaded melspectrogram.onnx")

            // Load Embedding Model
            val embBytes = loadModelFromAssets(context, "embedding_model.onnx")
            embeddingSession = env.createSession(embBytes)
            Log.d(TAG, "Loaded embedding_model.onnx")

            // Load Classifier Model
            val clfBytes = try {
                loadModelFromAssets(context, "hey_friday.onnx")
            } catch (e: Exception) {
                Log.w(TAG, "hey_friday.onnx not found, falling back to model.onnx")
                loadModelFromAssets(context, "model.onnx")
            }
            classifierSession = env.createSession(clfBytes)
            Log.d(TAG, "Loaded classifier ONNX model.")
            
        } catch (e: Exception) {
            Log.e("WakeWordInit", "Failed to load ONNX models: ${e.message}", e)
        }
    }

    private fun loadModelFromAssets(context: Context, fileName: String): ByteArray {
        return context.assets.open(fileName).use { inputStream ->
            inputStream.readBytes()
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isListening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission missing. Disabling wake-word engine.")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBufferSize, FRAME_READ_SIZE * 4)
            )

            audioRecord?.startRecording()
            isListening = true

            recordingJob = scope.launch {
                withContext(Dispatchers.Default) {
                    val pcmBuffer = ShortArray(FRAME_READ_SIZE)
                    val floatAudioBuffer = FloatArray(FRAME_READ_SIZE)

                    try {
                        while (isActive && isListening) {
                            val readCount = audioRecord?.read(pcmBuffer, 0, FRAME_READ_SIZE) ?: -1
                            if (readCount < 0) {
                                Log.e("AudioDebug", "AudioRecord read error: $readCount")
                                continue
                            }

                            if (readCount == FRAME_READ_SIZE) {
                                // Convert to FloatArray. OpenWakeWord models expect values in int16 range, so just toFloat()
                                for (i in 0 until readCount) {
                                    floatAudioBuffer[i] = pcmBuffer[i].toFloat()
                                }

                                val env = ortEnv
                                if (env == null || melSession == null || embeddingSession == null || classifierSession == null) {
                                    continue
                                }

                                // ---------------------------------------------------------
                                // STAGE 1: Melspectrogram (1280 samples -> 8x32 mel frames)
                                // ---------------------------------------------------------
                                val melShape = longArrayOf(1, FRAME_READ_SIZE.toLong())
                                val audioTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatAudioBuffer), melShape)
                                
                                val newMelFrames = try {
                                    audioTensor.use { t ->
                                        val inputs = mapOf(melSession!!.inputNames.first() to t)
                                        melSession!!.run(inputs).use { result ->
                                            val outTensor = result[0] as OnnxTensor
                                            val fb = outTensor.floatBuffer
                                            val arr = FloatArray(fb.remaining())
                                            fb.get(arr)
                                            arr
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("WakeWord", "ONNX Crash in Melspectrogram: ", e)
                                    null
                                }

                                if (newMelFrames == null || newMelFrames.isEmpty()) continue

                                // Slide melBuffer left by newMelFrames.size and append new frames
                                val newMelSize = newMelFrames.size
                                System.arraycopy(melBuffer, newMelSize, melBuffer, 0, melBuffer.size - newMelSize)
                                System.arraycopy(newMelFrames, 0, melBuffer, melBuffer.size - newMelSize, newMelSize)

                                // ---------------------------------------------------------
                                // STAGE 2: Embeddings (76x32 mel frames -> 1x96 embedding)
                                // ---------------------------------------------------------
                                // Apply linear rescale: (value / 10.0) + 2.0
                                val rescaledMel = FloatArray(melBuffer.size)
                                for (i in melBuffer.indices) {
                                    rescaledMel[i] = (melBuffer[i] / 10.0f) + 2.0f
                                }

                                val embShape = longArrayOf(1, MEL_FRAMES.toLong(), MEL_BINS.toLong(), 1)
                                val melTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(rescaledMel), embShape)

                                val newEmbedding = try {
                                    melTensor.use { t ->
                                        val inputs = mapOf(embeddingSession!!.inputNames.first() to t)
                                        embeddingSession!!.run(inputs).use { result ->
                                            val outTensor = result[0] as OnnxTensor
                                            val fb = outTensor.floatBuffer
                                            val arr = FloatArray(fb.remaining())
                                            fb.get(arr)
                                            arr
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("WakeWord", "ONNX Crash in Embedding Model: ", e)
                                    null
                                }

                                if (newEmbedding == null || newEmbedding.isEmpty()) continue

                                // Slide embeddingBuffer left by newEmbedding.size and append new embedding
                                val newEmbSize = newEmbedding.size
                                System.arraycopy(embeddingBuffer, newEmbSize, embeddingBuffer, 0, embeddingBuffer.size - newEmbSize)
                                System.arraycopy(newEmbedding, 0, embeddingBuffer, embeddingBuffer.size - newEmbSize, newEmbSize)

                                // ---------------------------------------------------------
                                // STAGE 3: Classifier (16x96 embeddings -> 1x1 confidence)
                                // ---------------------------------------------------------
                                val clfShape = longArrayOf(1, EMBEDDING_FRAMES.toLong(), EMBEDDING_SIZE.toLong())
                                val embTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(embeddingBuffer), clfShape)

                                val confidence = try {
                                    embTensor.use { t ->
                                        val inputs = mapOf(classifierSession!!.inputNames.first() to t)
                                        classifierSession!!.run(inputs).use { result ->
                                            val outTensor = result[0] as OnnxTensor
                                            outTensor.floatBuffer.get(0)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("WakeWord", "ONNX Crash in Classifier Model: ", e)
                                    0.0f
                                }

                                Log.d("WakeWord", "Confidence: $confidence")

                                // Trigger logic
                                val now = System.currentTimeMillis()

                                // L3 Cooldown Filter
                                if (now - lastTriggerTimeMs < COOLDOWN_DURATION_MS) {
                                    consecutiveHighConfidenceFrames = 0
                                    continue
                                }

                                // L1 Consecutive Frames Filter
                                if (confidence >= CONFIDENCE_THRESHOLD) {
                                    consecutiveHighConfidenceFrames++
                                    Log.d(TAG, "High confidence frame $consecutiveHighConfidenceFrames/$REQUIRED_CONSECUTIVE_FRAMES ($confidence)")

                                    if (consecutiveHighConfidenceFrames >= REQUIRED_CONSECUTIVE_FRAMES) {
                                        Log.d(TAG, "Wake word triggered! L1 & L3 passed. Confidence: $confidence")
                                        lastTriggerTimeMs = now
                                        consecutiveHighConfidenceFrames = 0
                                        
                                        // Continuous Listening: Do not stop or break.
                                        // Overwrite buffers with 0.0f to prevent immediate double-triggering
                                        melBuffer.fill(0.0f)
                                        embeddingBuffer.fill(0.0f)
                                        
                                        launch(Dispatchers.Main) {
                                            onWakeWordDetected()
                                        }
                                    }
                                } else {
                                    consecutiveHighConfidenceFrames = 0
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("WakeWord", "Audio Loop Crash: ", e)
                    } finally {
                        Log.d(TAG, "AudioRecord while loop exited. isListening=$isListening, isActive=$isActive")
                    }
                }
            }
            Log.d(TAG, "AudioRecord listening started at 16kHz.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission missing for AudioRecord: ${e.message}")
            stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord loop: ${e.message}", e)
            stopListening()
        }
    }

    fun stopListening() {
        isListening = false
        consecutiveHighConfidenceFrames = 0
        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }
    }

    fun release() {
        stopListening()
        try {
            melSession?.close()
            embeddingSession?.close()
            classifierSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ONNX sessions: ${e.message}")
        } finally {
            melSession = null
            embeddingSession = null
            classifierSession = null
            ortEnv = null
        }
    }
}
