package com.soniclab.fx.audio

import kotlin.math.min

/**
 * Stereo balance: [-1..1], -1 = full left, 0 = center, +1 = full right.
 * Passthrough for mono.
 */
class BalanceProcessor {
    @Volatile var balance: Float = 0f

    fun process(samples: FloatArray, channelCount: Int): FloatArray {
        if (balance == 0f || channelCount < 2) return samples
        val gainL = min(1f, 1f - balance).coerceIn(0f, 1f)
        val gainR = min(1f, 1f + balance).coerceIn(0f, 1f)
        var channel = 0
        for (i in samples.indices) {
            samples[i] *= if (channel++ % 2 == 0) gainL else gainR
        }
        return samples
    }
}
