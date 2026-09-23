package com.example.vesccontrolcentre.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ElevenLabsManager(private val context: Context) {

    companion object {
        private const val TAG = "ElevenLabsManager"
        private const val DEFAULT_VOICE_ID = "1hlpeD1ydbI2ow0Tt3EW"
    }

    private val client = OkHttpClient()
    private var currentAudioTrack: AudioTrack? = null

    suspend fun speakText(text: String, voiceId: String, apiKey: String, modelId: String = "eleven_flash_v2_5") {
        if (text.isBlank()) return
        if (apiKey.isBlank()) {
            Log.e(TAG, "ElevenLabs API key is missing!")
            return
        }

        val targetVoice = if (voiceId.isNotBlank()) voiceId else DEFAULT_VOICE_ID

        withContext(Dispatchers.IO) {
            try {
                // PCM 24kHz output format with max latency optimization (level 3)
                val url = "https://api.elevenlabs.io/v1/text-to-speech/$targetVoice?output_format=pcm_24000&optimize_streaming_latency=3"

                val jsonBody = JSONObject().apply {
                    put("text", text)
                    put("model_id", modelId)
                    put("voice_settings", JSONObject().apply {
                        put("stability", 0.5)
                        put("similarity_boost", 0.75)
                    })
                }

                val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("xi-api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("accept", "audio/pcm")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "ElevenLabs API error: ${response.code} - ${response.body?.string()}")
                        return@withContext
                    }

                    val sampleRate = 24000
                    val minBufSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )

                    val audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(maxOf(minBufSize, 8192) * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    currentAudioTrack = audioTrack
                    audioTrack.play()

                    try {
                        val inputStream = response.body?.byteStream() ?: return@withContext
                        val buffer = ByteArray(2048)
                        var bytesRead: Int
                        var totalBytesWritten = 0

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (bytesRead > 0) {
                                audioTrack.write(buffer, 0, bytesRead)
                                totalBytesWritten += bytesRead
                            }
                        }

                        // Wait until AudioTrack finishes playing all written frames before stopping
                        val totalFrames = totalBytesWritten / 2 // 16-bit PCM Mono = 2 bytes per frame
                        var lastPosition = -1
                        var stallCount = 0

                        while (audioTrack.playbackHeadPosition < totalFrames && audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            val currentPosition = audioTrack.playbackHeadPosition
                            if (currentPosition == lastPosition) {
                                stallCount++
                                if (stallCount > 50) break // Exit if position stops advancing (500ms timeout)
                            } else {
                                lastPosition = currentPosition
                                stallCount = 0
                            }
                            try {
                                Thread.sleep(10)
                            } catch (_: InterruptedException) {
                                break
                            }
                        }

                        try {
                            audioTrack.stop()
                        } catch (_: Exception) {}
                        audioTrack.flush()
                        audioTrack.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "AudioTrack playback error: ${e.message}", e)
                        try {
                            audioTrack.stop()
                            audioTrack.flush()
                            audioTrack.release()
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling ElevenLabs: ${e.message}", e)
            }
        }
    }

    fun stop() {
        try {
            currentAudioTrack?.stop()
            currentAudioTrack?.flush()
            currentAudioTrack?.release()
        } catch (_: Exception) {}
        currentAudioTrack = null
    }
}