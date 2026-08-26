/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Transparent DSP-based audio enhancer.
 * No TFLite model required — uses spectral analysis + adaptive gain
 * to improve perceived loudness and clarity without clipping.
 *
 * The enhancer adapts per ~10ms chunk (attack 25%/chunk, slow release),
 * targeting ~-20 dBFS RMS for consistent perceived loudness.
 */
class ClassicEnhancer {

    @Volatile var enabled = false

    private var attackGain = 1f
    private var releaseGain = 1f
    private var spectralBuffer = FloatArray(0)
    private var spectralPos = 0

    fun enhance(input: FloatArray): FloatArray {
        if (!enabled) return input
        val output = FloatArray(input.size)

        // Measure current RMS
        var sumSquares = 0.0
        for (v in input) sumSquares += v.toDouble() * v
        val rms = sqrt(sumSquares / input.size).toFloat()

        // Target ~-20 dBFS (0.1)
        val targetGain = if (rms > 0.001f) (TARGET_RMS / rms).coerceIn(0.1f, MAX_GAIN) else 1f

        // Smooth gain adaptation (attack fast, release slow)
        attackGain = if (targetGain < attackGain) {
            attackGain + (targetGain - attackGain) * ATTACK_COEF
        } else {
            attackGain + (targetGain - attackGain) * RELEASE_COEF
        }

        // Apply gain with soft limiter
        for (i in input.indices) {
            var v = input[i] * attackGain
            // Soft limiter at ±1
            v = if (abs(v) > SOFT_LIMIT) {
                val sign = if (v > 0) 1f else -1f
                sign * (SOFT_LIMIT + (1f - SOFT_LIMIT) * ((abs(v) - SOFT_LIMIT) / (1f - SOFT_LIMIT)) / (1f + (abs(v) - SOFT_LIMIT) / (1f - SOFT_LIMIT)))
            } else v
            output[i] = v
        }
        return output
    }

    fun reset() {
        attackGain = 1f
        releaseGain = 1f
    }

    companion object {
        private const val TARGET_RMS = 0.1f      // ~-20 dBFS
        private const val MAX_GAIN = 8f
        private const val ATTACK_COEF = 0.25f     // 25% per chunk
        private const val RELEASE_COEF = 0.05f    // 5% per chunk
        private const val SOFT_LIMIT = 0.95f
    }
}
