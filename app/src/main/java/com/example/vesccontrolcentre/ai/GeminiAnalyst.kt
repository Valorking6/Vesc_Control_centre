package com.example.vesccontrolcentre.ai

import android.content.Context
import android.util.Log

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAnalyst(
    private val context: Context,
    private val onCommandReceived: (String) -> Unit,
    private val onSpeechResponse: (String) -> Unit
) {

    companion object {
        private const val TAG = "GeminiAnalyst"
    }

    private val generativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = "gemini-2.5-flash",
                systemInstruction = content {
                    text("You are Friday, a VESC scooter assistant. Acknowledge user commands conversationally in 1 short sentence, and ALWAYS output a JSON string like {\"action\": \"SWITCH_PROFILE\", \"target\": \"MAX_POWER\"} or {\"action\": \"SWITCH_PROFILE\", \"target\": \"CRAWL\"} or {\"action\": \"SWITCH_PROFILE\", \"target\": \"NORMAL\"} or {\"action\": \"SWITCH_PROFILE\", \"target\": \"LONG_RANGE\"} if the user requests a performance profile change.")
                }
            )
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 16000): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2 // 16-bit mono
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte(); header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte(); header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = 1; header[23] = 0
        header[24] = (sampleRate and 0xff).toByte(); header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte(); header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte(); header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2; header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte(); header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte(); header[43] = ((pcmData.size shr 24) and 0xff).toByte()
        return header + pcmData
    }

    suspend fun processVoiceCommand(pcmBytes: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing voice command with Gemini (bytes length: ${pcmBytes.size})...")

                val wavData = pcmToWav(pcmBytes)
                val promptContent = content {
                    inlineData(wavData, "audio/wav")
                    text("Listen to this audio command and execute the corresponding scooter action.")
                }

                val response = generativeModel.generateContent(promptContent)
                val responseText = response.text ?: ""
                Log.d(TAG, "Gemini response: $responseText")

                if (responseText.contains("{") && responseText.contains("}")) {
                    val jsonStart = responseText.indexOf("{")
                    val jsonEnd = responseText.lastIndexOf("}") + 1
                    if (jsonStart in 0 until jsonEnd) {
                        val jsonStr = responseText.substring(jsonStart, jsonEnd)
                        onCommandReceived(jsonStr)
                    }
                }

                val speechText = responseText.replace(Regex("\\{[^}]*\\}"), "").trim()
                if (speechText.isNotBlank()) {
                    onSpeechResponse(speechText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice command with Gemini: ${e.message}", e)
                val errorMessage = e.message ?: ""
                val userFeedback = when {
                    errorMessage.contains("402") || errorMessage.contains("prepayment", ignoreCase = true) -> {
                        "Gemini API prepayment credits depleted. Please check your AI Studio billing."
                    }
                    errorMessage.contains("429") || errorMessage.contains("quota", ignoreCase = true) -> {
                        "Gemini API quota exceeded. Please try again later."
                    }
                    errorMessage.contains("API_KEY") || errorMessage.contains("key", ignoreCase = true) -> {
                        "Invalid Gemini API key configured."
                    }
                    errorMessage.contains("404") || errorMessage.contains("NOT_FOUND", ignoreCase = true) -> {
                        "Gemini AI model endpoint not found. Please verify API key permissions."
                    }
                    else -> {
                        "Voice command error: ${e.localizedMessage ?: "Unable to process request."}"
                    }
                }
                onSpeechResponse(userFeedback)
            }
        }
    }
}
