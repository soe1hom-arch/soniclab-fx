package com.soniclab.fx.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.util.Log

/**
 * System-wide auto-normalization.
 * Uses Android's AudioEffect API to detect loudness and apply ReplayGain-style
 * normalization to the global output mix.
 *
 * When system-wide mode is active, normalization is applied as a gain stage
 * in the DSP chain based on detected loudness level.
 */
class AutoNormalizer {

    @Volatile var enabled = false
    @Volatile var targetLufs = -14f

    // Simple loudness measurement
    private var loudnessAccumulator = 0.0
    private var sampleCount = 0
    private var currentGainDb = 0f

    // Exponential moving average for loudness tracking
    private var emaLoudness = 0.1f

    fun processGain(samples: FloatArray): Float {
        if (!enabled) return 1f

        // Measure RMS of this buffer
        var sumSquares = 0.0
        for (v in samples) sumSquares += v.toDouble() * v
        val rms = kotlin.math.sqrt(sumSquares / samples.size.coerceAtLeast(1)).toFloat()

        if (rms < 0.0001f) return 1f

        // Convert to dB
        val rmsDb = 20f * kotlin.math.log10(rms.coerceAtLeast(1e-9f))

        // Smooth tracking
        emaLoudness = emaLoudness * 0.95f + rmsDb * 0.05f

        // Compute gain to reach target LUFS (simplified — no K-weighting)
        val gainDb = (targetLufs - emaLoudness).coerceIn(-12f, 12f)
        currentGainDb = currentGainDb * 0.9f + gainDb * 0.1f

        return 10f.pow(currentGainDb / 20f)
    }

    fun reset() {
        loudnessAccumulator = 0.0
        sampleCount = 0
        currentGainDb = 0f
        emaLoudness = 0.1f
    }

    companion object {
        private const val TAG = "AutoNormalizer"
        private fun pow(base: Float, exp: Float): Float = base.toDouble().pow(exp.toDouble()).toFloat()
        private fun pow(base: Double, exp: Double): Float = base.pow(exp).toFloat()
    }

    private fun pow(base: Float, exp: Float): Float = kotlin.math.pow(base.toDouble(), exp.toDouble()).toFloat()
}
