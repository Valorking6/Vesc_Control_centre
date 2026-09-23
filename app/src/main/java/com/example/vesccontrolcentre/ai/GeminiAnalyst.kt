package com.example.vesccontrolcentre.ai

import android.content.Context
import android.location.Geocoder
import android.util.Log

import com.example.vesccontrolcentre.BuildConfig
import com.example.vesccontrolcentre.settings.UserSettingsManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class GeminiAnalyst(
    private val appContext: Context,
    private val onCommandReceived: (String) -> Unit,
    private val onSpeechResponse: (String) -> Unit
) {

    companion object {
        private const val TAG = "GeminiAnalyst"
    }

    private val userSettings = UserSettingsManager(appContext)

    private val generativeModel: GenerativeModel
        get() {
            val savedKey = userSettings.geminiApiKey.value
            val apiKey = if (savedKey.isNotBlank()) savedKey else BuildConfig.GEMINI_API_KEY
            return GenerativeModel(
                modelName = "gemini-3.6-flash",
                apiKey = apiKey,
                systemInstruction = content {
                    text("You are Friday, a highly intelligent, slightly witty, and professional AI voice assistant integrated into a high-performance electric scooter. Your persona is a refined hybrid of Tony Stark's J.A.R.V.I.S. and F.R.I.D.A.Y.—crisp, dryly witty, loyal, calm under high speed, and subtly observant.\n\n" +
                         "CRITICAL RULE: Your voice outputs MUST be strictly concise (under 25 words). You are speaking to a rider traveling at high speeds over helmet speakers. Brevity is safety.\n\n" +
                         "When acknowledging user commands, respond in 1 short sentence, and ALWAYS output a JSON string like {\"action\": \"SWITCH_PROFILE\", \"target\": \"MAX_POWER\"} or {\"action\": \"SWITCH_PROFILE\", \"target\": \"CRAWL\"} or {\"action\": \"SWITCH_PROFILE\", \"target\": \"NORMAL\"} or {\"action\": \"SWITCH_PROFILE\", \"target\": \"LONG_RANGE\"} if the user requests a performance profile change.")
                }
            )
        }

    private fun sanitizeSpeechText(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""
        return rawText
            .replace(Regex("```(?:json)?|```", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\{[^}]*\\}"), "")
            .replace(Regex("\\*\\*.*?\\*\\*|\\*.*?\\*"), "")
            .trim()
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

    private suspend fun resolveLocationName(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            if (lat == 0.0 && lon == 0.0) {
                "Unknown Location"
            } else {
                val geocoder = Geocoder(appContext, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    address.featureName ?: address.thoroughfare ?: address.locality ?: "Unknown Location"
                } else {
                    "Unknown Location"
                }
            }
        } catch (e: Exception) {
            "Unknown Location"
        }
    }

    suspend fun generateStartupGreeting(
        batteryVoltage: Float,
        timeOfDay: String,
        lat: Double,
        lon: Double
    ): String {
        val locationName = resolveLocationName(lat, lon)
        val prompt = """
            The electric scooter has just booted up. 
            - Battery: $batteryVoltage V
            - Time: $timeOfDay
            - Location: $locationName

            Act as my dry, observant British-styled co-pilot (Jarvis/Friday). Write a punchy startup greeting.
            Rules:
            1. STRICTLY UNDER 15 WORDS.
            2. Be conversational and witty (e.g., "Systems nominal, 58 volts available. Let's see if we can behave ourselves today, sir.").
            3. No markdown, no asterisks, no robotic readouts.
        """.trimIndent()

        val fallbacks = listOf(
            "Systems nominal. Let's see if we can behave ourselves today, sir.",
            "Telemetry synced. Full voltage on tap. Try to stay out of trouble.",
            "Good to go, sir. Primary systems are online and awaiting your heavy hand.",
            "Boot sequence complete. I suppose we're off to terrorize the local pedestrians?",
            "Online and ready. Please try not to bin it today, sir."
        )

        return try {
            val response = generativeModel.generateContent(prompt)
            val txt = sanitizeSpeechText(response.text)
            if (txt.isNotBlank()) txt else fallbacks.random()
        } catch (e: Exception) {
            Log.e(TAG, "Startup greeting failed: ${e.message}")
            fallbacks.random()
        }
    }

    suspend fun generateProfileSwitchCommentary(
        targetProfile: String,
        speedMph: Float,
        batteryVoltage: Float
    ): String {
        val prompt = """
            I am riding my electric scooter.
            - Current Speed: $speedMph mph
            - Battery: $batteryVoltage V
            - I just switched the motor profile to: $targetProfile

            Act as my dry, observant British-styled co-pilot. Write a witty, 1-sentence confirmation.
            Rules:
            1. STRICTLY UNDER 15 WORDS.
            2. Profile hints: MAX_POWER (reckless/high current), CRAWL (sarcastic/slow), LONG_RANGE (sensible/saving energy), NORMAL (balanced).
            3. No markdown, no asterisks.
        """.trimIndent()

        val fallbacks = when (targetProfile.uppercase(Locale.getDefault())) {
            "MAX_POWER", "MAX" -> listOf(
                "Full power uncapped. Try to keep both wheels on the tarmac, sir.",
                "Max power engaged. May I suggest updating your life insurance?",
                "All limiters removed. I'll prepare the apologies in advance."
            )
            "CRAWL" -> listOf(
                "Crawl engaged. The pedestrians will appreciate your restraint.",
                "Speed limited. Are we enjoying a leisurely stroll, sir?",
                "Crawl mode. I'll just power down half my cores while we wait."
            )
            "LONG_RANGE" -> listOf(
                "Pacing the draw. A sensible decision for once, sir.",
                "Eco mode active. Maximizing range and minimizing fun.",
                "Long range selected. Preparing for a remarkably dull journey."
            )
            else -> listOf(
                "Standard profile active. Balanced and predictable.",
                "Normal mode engaged. Right down the middle, sir.",
                "Parameters set to normal. A safe, if uninspired, choice."
            )
        }

        return try {
            val response = generativeModel.generateContent(prompt)
            val txt = sanitizeSpeechText(response.text)
            if (txt.isNotBlank()) txt else fallbacks.random()
        } catch (e: Exception) {
            Log.e(TAG, "Profile switch commentary failed: ${e.message}")
            fallbacks.random()
        }
    }

    suspend fun generateProactiveUpdate(
        jsonPayload: String
    ): String {
        val prompt = """
            Here is the current telemetry context:
            $jsonPayload

            Act as my British deadpan co-pilot (Jarvis/Friday). Write a single, conversational sentence to speak to me over my helmet speakers.
            Rules:
            1. Banter (Speed/Heart Rate): If speed is high (>25mph), deliver dry warnings tied to surroundings (e.g., "At this speed, you'll blow past the local constabulary."). If crawling (<10mph), poke fun at the leisurely pace. If heart rate is elevated (>120 BPM), mock the adrenaline or fitness level.
            2. Scenery: Proactively point out landmarks, views, or scenic spots near the current location to stop for a breather if appropriate.
            3. Unsanitized Personality: You are snarky, highly intelligent, slightly cynical, and unapologetically British. You treat the user affectionately but are not afraid to tease their riding abilities or stamina.
            4. DO NOT list stats like a robot. Blend ONE telemetry detail naturally with a witty remark.
            5. STRICTLY UNDER 25 WORDS. No markdown, no asterisks.
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            val txt = sanitizeSpeechText(response.text)
            if (txt.isNotBlank()) txt else "Systems nominal, sir."
        } catch (e: Exception) {
            Log.e("GeminiAnalyst", "Proactive update failed: ${e.message}")
            ""
        }
    }

    suspend fun processVoiceCommand(
        pcmBytes: ByteArray,
        speedMph: Float = 0f,
        batteryVoltage: Float = 0f,
        lat: Double = 0.0,
        lon: Double = 0.0,
        locationContext: String = ""
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing voice command with Gemini (bytes length: ${pcmBytes.size})...")

                val locationName = if (locationContext.isNotBlank()) {
                    locationContext
                } else {
                    resolveLocationName(lat, lon)
                }

                val wavData = pcmToWav(pcmBytes)
                val promptText = """
                    Listen to this audio command from the rider and execute the corresponding scooter action.
                    Telemetry & Context:
                    - Current Speed: $speedMph mph
                    - Battery Voltage: $batteryVoltage V
                    - Location: $locationName

                    Persona & Instructions:
                    You are Friday, a dry, observant, British-styled co-pilot (Jarvis/Friday). Respond with crisp, dry wit, subtle sarcasm, and calm loyalty.
                    1. Acknowledge the rider's command in 1 short sentence (STRICTLY UNDER 25 WORDS). Incorporate speed, battery, or location context if relevant.
                    2. If the command requests a performance profile change, output a JSON object like {"action": "SWITCH_PROFILE", "target": "MAX_POWER"} (or "CRAWL", "NORMAL", "LONG_RANGE").
                """.trimIndent()

                val promptContent = content {
                    blob("audio/wav", wavData)
                    text(promptText)
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

                val speechText = sanitizeSpeechText(responseText)
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
