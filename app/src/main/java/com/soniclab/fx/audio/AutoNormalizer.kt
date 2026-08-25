package com.soniclab.fx.audio

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * System-wide auto-normalization.
 * Tracks loudness via exponential moving average and applies ReplayGain-style
 * correction to reach the target LUFS level.
 */
class AutoNormalizer {

    @Volatile var enabled = false
    @Volatile var targetLufs = -14f

    private var currentGainDb = 0f
    private var emaLoudness = 0.1f

    fun processGain(samples: FloatArray): Float {
        if (!enabled) return 1f

        var sumSquares = 0.0
        for (v in samples) sumSquares += v.toDouble() * v
        val rms = sqrt(sumSquares / samples.size.coerceAtLeast(1)).toFloat()

        if (rms < 0.0001f) return 1f

        val rmsDb = 20f * log10(rms.coerceAtLeast(1e-9f))
        emaLoudness = emaLoudness * 0.95f + rmsDb * 0.05f

        val gainDb = (targetLufs - emaLoudness).coerceIn(-12f, 12f)
        currentGainDb = currentGainDb * 0.9f + gainDb * 0.1f

        return 10f.pow(currentGainDb / 20f)
    }

    fun reset() {
        currentGainDb = 0f
        emaLoudness = 0.1f
    }
}
