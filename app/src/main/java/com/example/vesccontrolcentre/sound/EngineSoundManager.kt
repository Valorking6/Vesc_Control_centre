package com.example.vesccontrolcentre.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.annotation.RawRes
import kotlin.math.abs

class EngineSoundManager {

    companion object {
        private const val TAG = "EngineSoundManager"
        private const val DEFAULT_MAX_ERPM = 20000f
    }

    private var soundPool: SoundPool? = null
    private var soundId: Int = 0
    private var streamId: Int = 0
    private var isLoaded: Boolean = false
    private var isPlayingSound: Boolean = false
    private var currentRate: Float = 0.5f
    private var currentVolume: Float = 0.0f
    private var maxErpm: Float = DEFAULT_MAX_ERPM

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    fun startEngineSound(context: Context, @RawRes soundResId: Int, maxErpm: Float = DEFAULT_MAX_ERPM) {
        this.maxErpm = if (maxErpm > 0) maxErpm else DEFAULT_MAX_ERPM
        stopEngineSound()
        initSoundPool()

        isLoaded = false
        isPlayingSound = false
        currentVolume = 0.0f

        soundPool?.setOnLoadCompleteListener { sp, loadedSoundId, status ->
            if (status == 0 && loadedSoundId == soundId) {
                isLoaded = true
                Log.d(TAG, "Sound loaded successfully. Starting infinite loop...")
                streamId = sp.play(soundId, currentVolume, currentVolume, 1, -1, currentRate)
                if (streamId != 0) {
                    isPlayingSound = true
                    Log.d(TAG, "Stream started with ID: $streamId")
                } else {
                    Log.w(TAG, "Stream ID returned 0 on load complete. Will retry on updatePitchAndVolume.")
                }
            } else {
                Log.e(TAG, "Failed to load sound resource: $status")
            }
        }

        soundPool?.let {
            soundId = it.load(context.applicationContext, soundResId, 1)
        }
    }

    fun updatePitchAndVolume(erpm: Float, speedMph: Float, instantVolume: Boolean = false) {
        val absErpm = abs(erpm)
        val targetRate = (0.5f + (absErpm / maxErpm) * 1.5f).coerceIn(0.5f, 2.0f)
        currentRate = targetRate

        if (instantVolume) {
            currentVolume = ((speedMph - 2.0f) / 10.0f).coerceIn(0.0f, 1.0f)
        } else if (speedMph < 0.5f || absErpm == 0f) {
            currentVolume = 0.0f
        } else {
            val targetVolume = ((speedMph - 2.0f) / 10.0f).coerceIn(0.0f, 1.0f)
            if (targetVolume > currentVolume) {
                currentVolume += (targetVolume - currentVolume) * 0.15f
            } else {
                currentVolume += (targetVolume - currentVolume) * 0.50f
            }
        }

        val sp = soundPool ?: return

        if (isLoaded && streamId == 0 && soundId != 0) {
            streamId = sp.play(soundId, currentVolume, currentVolume, 1, -1, targetRate)
            if (streamId != 0) {
                isPlayingSound = true
                Log.d(TAG, "Stream playback initiated in updatePitchAndVolume with ID: $streamId")
            }
        }

        if (streamId != 0) {
            sp.setRate(streamId, targetRate)
            sp.setVolume(streamId, currentVolume, currentVolume)
        }
    }

    fun updatePitch(erpm: Float) {
        // Keep for backwards compatibility (e.g. preview slider where speed is not simulated)
        // Assume cruising speed (e.g., 20 MPH) for full volume and apply instantly
        updatePitchAndVolume(erpm, 20f, instantVolume = true)
    }

    fun stopEngineSound() {
        try {
            if (streamId != 0) {
                soundPool?.stop(streamId)
                streamId = 0
            }
            if (soundId != 0) {
                soundPool?.unload(soundId)
                soundId = 0
            }
            soundPool?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping engine sound: ${e.message}", e)
        } finally {
            soundPool = null
            isLoaded = false
            isPlayingSound = false
        }
    }

    fun isPlaying(): Boolean = isPlayingSound
}
