package com.soniclab.fx.audio

import android.media.audiofx.AudioEffect
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * System-wide audio effect registered on the global output mix (session 0).
 *
 * Android AudioEffect API allows effects on session 0 starting from API 14,
 * but on Android 10+ the system restricts which apps can modify the global
 * mix.  Three registration strategies are attempted in order:
 *
 *  1. Direct registration (works on Android 9 and below, or system apps)
 *  2. Shizuku shell (works on Android 10+ without root via ADB-privileged shell)
 *  3. Root shell (works on any rooted device)
 *
 * Once registered, Android routes ALL audio through [process].
 */
class GlobalAudioEffect(
    private val onProcess: (FloatArray, Int, Int) -> FloatArray
) : AudioEffect(
    EFFECT_TYPE_NULL,   // type: let system pick (or use session 0 alias)
    EFFECT_TYPE_NULL,
    0,                  // priority
    AUDIO_SESSION_ID    // session 0 = global output mix
) {

    private var sampleRate = 44100
    private var channelCount = 2

    override fun onEnable() { Log.i(TAG, "Effect enabled") }
    override fun onDisable() { Log.i(TAG, "Effect disabled") }

    /**
     * Called by Android audio framework for each buffer of interleaved PCM.
     * This runs on the audio thread — must be lock-free and fast.
     */
    override fun process(
        inputBuffer: AudioEffect.InputBuffer,
        outputBuffer: AudioEffect.OutputBuffer
    ) {
        val input = inputBuffer.buffer
        val output = outputBuffer.buffer
        val byteCount = input.remaining()

        if (byteCount == 0) return

        // Decode interleaved float PCM
        val sampleCount = byteCount / 4
        val samples = FloatArray(sampleCount)
        input.order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) samples[i] = input.float

        val frames = sampleCount / channelCount
        val processed = onProcess(samples, frames, channelCount)

        // Encode back to float PCM
        output.order(ByteOrder.nativeOrder())
        for (v in processed) output.putFloat(v)
    }

    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
    }

    companion object {
        private const val TAG = "GlobalAudioEffect"
        private val AUDIO_SESSION_ID = 0  // global output mix
        private val EFFECT_TYPE_NULL = java.util.UUID(0x00000000L, 0x00000000L)
    }
}
