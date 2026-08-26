/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * RBJ biquad filter — supports peaking, low shelf, and high shelf.
 * Coefficients collapse to identity at 0 dB gain (zero coloration).
 */
class BiquadFilter {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f

    fun process(sample: Float): Float {
        val y = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = sample; y2 = y1; y1 = y
        return y
    }

    fun reset() { x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f }

    fun setPeaking(fs: Int, fc: Float, gainDb: Float, q: Float = 0.71f) {
        if (gainDb == 0f) { setIdentity(); return }
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * fc / fs
        val c = cos(w0)
        val alpha = sin(w0) / (2f * q)
        b0 = 1f + alpha * a; b1 = -2f * c; b2 = 1f - alpha * a
        val a0 = 1f + alpha / a; a1 = -2f * c; a2 = 1f - alpha / a
        val n = 1f / a0; b0 *= n; b1 *= n; b2 *= n; a1 *= n; a2 *= n
    }

    fun setLowShelf(fs: Int, fc: Float, gainDb: Float, q: Float = 0.71f) {
        if (gainDb == 0f) { setIdentity(); return }
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * fc / fs
        val c = cos(w0); val sq = sqrt(a); val alpha = sin(w0) / (2f * q)
        b0 = a * ((a + 1) - (a - 1) * c + 2f * sq * alpha)
        b1 = 2f * a * ((a - 1) - (a + 1) * c)
        b2 = a * ((a + 1) - (a - 1) * c - 2f * sq * alpha)
        val a0 = (a + 1) + (a - 1) * c + 2f * sq * alpha
        a1 = -2f * ((a - 1) + (a + 1) * c)
        a2 = (a + 1) + (a - 1) * c - 2f * sq * alpha
        val n = 1f / a0; b0 *= n; b1 *= n; b2 *= n; a1 *= n; a2 *= n
    }

    fun setHighShelf(fs: Int, fc: Float, gainDb: Float, q: Float = 0.71f) {
        if (gainDb == 0f) { setIdentity(); return }
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * fc / fs
        val c = cos(w0); val sq = sqrt(a); val alpha = sin(w0) / (2f * q)
        b0 = a * ((a + 1) + (a - 1) * c + 2f * sq * alpha)
        b1 = -2f * a * ((a - 1) + (a + 1) * c)
        b2 = a * ((a + 1) + (a - 1) * c - 2f * sq * alpha)
        val a0 = (a + 1) - (a - 1) * c + 2f * sq * alpha
        a1 = 2f * ((a - 1) - (a + 1) * c)
        a2 = (a + 1) - (a - 1) * c - 2f * sq * alpha
        val n = 1f / a0; b0 *= n; b1 *= n; b2 *= n; a1 *= n; a2 *= n
    }

    private fun setIdentity() { b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f }
}
