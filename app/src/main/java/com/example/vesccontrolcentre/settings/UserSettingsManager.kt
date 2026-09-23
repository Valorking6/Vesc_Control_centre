package com.example.vesccontrolcentre.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

class UserSettingsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secret_user_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _geminiApiKey = MutableStateFlow(sharedPrefs.getString("gemini_api_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _elevenLabsApiKey = MutableStateFlow(sharedPrefs.getString("elevenlabs_api_key", "") ?: "")
    val elevenLabsApiKey: StateFlow<String> = _elevenLabsApiKey.asStateFlow()

    private val _elevenLabsVoiceId = MutableStateFlow(sharedPrefs.getString("elevenlabs_voice_id", "QAmlwgbPtjxpk7u98Qs9") ?: "QAmlwgbPtjxpk7u98Qs9")
    val elevenLabsVoiceId: StateFlow<String> = _elevenLabsVoiceId.asStateFlow()

    private val _usePremiumVoice = MutableStateFlow(sharedPrefs.getBoolean("use_premium_voice", false))
    val usePremiumVoice: StateFlow<Boolean> = _usePremiumVoice.asStateFlow()

    private val _appTheme = MutableStateFlow(ThemeMode.valueOf(sharedPrefs.getString("app_theme", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name))
    val appTheme: StateFlow<ThemeMode> = _appTheme.asStateFlow()

    fun updateGeminiApiKey(key: String) {
        sharedPrefs.edit().putString("gemini_api_key", key).apply()
        _geminiApiKey.value = key
    }

    fun updateElevenLabsApiKey(key: String) {
        sharedPrefs.edit().putString("elevenlabs_api_key", key).apply()
        _elevenLabsApiKey.value = key
    }

    fun updateElevenLabsVoiceId(id: String) {
        sharedPrefs.edit().putString("elevenlabs_voice_id", id).apply()
        _elevenLabsVoiceId.value = id
    }

    fun updateUsePremiumVoice(usePremium: Boolean) {
        sharedPrefs.edit().putBoolean("use_premium_voice", usePremium).apply()
        _usePremiumVoice.value = usePremium
    }

    fun updateAppTheme(theme: ThemeMode) {
        sharedPrefs.edit().putString("app_theme", theme.name).apply()
        _appTheme.value = theme
    }
}
