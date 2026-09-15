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

        soundPool?.setOnLoadCompleteListener { sp, loadedSoundId, status ->
            if (status == 0 && loadedSoundId == soundId) {
                isLoaded = true
                Log.d(TAG, "Sound loaded successfully. Starting infinite loop...")
                streamId = sp.play(soundId, 1.0f, 1.0f, 1, -1, currentRate)
                if (streamId != 0) {
                    isPlayingSound = true
                    Log.d(TAG, "Stream started with ID: $streamId")
                } else {
                    Log.w(TAG, "Stream ID returned 0 on load complete. Will retry on updatePitch.")
                }
            } else {
                Log.e(TAG, "Failed to load sound resource: $status")
            }
        }

        soundPool?.let {
            soundId = it.load(context.applicationContext, soundResId, 1)
        }
    }

    fun updatePitch(erpm: Float) {
        val absErpm = abs(erpm)
        val targetRate = (0.5f + (absErpm / maxErpm) * 1.5f).coerceIn(0.5f, 2.0f)
        currentRate = targetRate

        val sp = soundPool ?: return

        if (isLoaded && streamId == 0 && soundId != 0) {
            streamId = sp.play(soundId, 1.0f, 1.0f, 1, -1, targetRate)
            if (streamId != 0) {
                isPlayingSound = true
                Log.d(TAG, "Stream playback initiated in updatePitch with ID: $streamId")
            }
        }

        if (streamId != 0) {
            sp.setRate(streamId, targetRate)
        }
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
