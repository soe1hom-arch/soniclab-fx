/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.audio

import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time 3D / 8D spatial audio processor.
 * - 3D: mid/side stereo widening
 * - 8D: slow LFO-rotated pan with feedback echo
 * - Surround: 3D widening + room echo, no rotation
 * - 8D+Center: 8D rotation with center anchor (no harsh widening)
 */
class SpatialProcessor {

    var mode: Int = MODE_OFF
        set(value) {
            field = value
            when (value) {
                MODE_OFF -> { spatial3d = false; spatial8d = false; surround = false }
                MODE_3D -> { spatial3d = true; spatial8d = false; surround = false }
                MODE_8D -> { spatial3d = false; spatial8d = true; surround = false }
                MODE_3D_8D -> { spatial3d = true; spatial8d = true; surround = false }
                MODE_SURROUND -> { spatial3d = true; spatial8d = false; surround = true }
            }
        }

    @Volatile var spatial3d = false
    @Volatile var spatial8d = false
    @Volatile var surround = false
    @Volatile var widthStrength = 0.6f
    @Volatile var rotationSeconds = 8f
    @Volatile var panDepth = 0.6f

    private var phase = 0.0
    private var delayBufferL = FloatArray(0)
    private var delayBufferR = FloatArray(0)
    private var delayWritePos = 0
    private var delayReadOffset = 0
    private var sampleRate = 44100

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate
        resetDsp()
    }

    fun process(input: FloatArray, frames: Int, channelCount: Int): FloatArray {
        if (channelCount < 2) return input
        if (!spatial3d && !spatial8d && !surround) return input

        val out = input
        when {
            spatial3d && spatial8d -> process3D8D(input, out, frames)
            spatial8d -> process8D(input, out, frames)
            spatial3d && surround -> processSurround(input, out, frames, widen = true)
            surround -> processSurround(input, out, frames, widen = false)
            spatial3d -> process3D(input, out, frames)
        }
        // Soft clip to avoid harsh clipping
        for (i in out.indices) out[i] = softClip(out[i])
        return out
    }

    private fun process3D(input: FloatArray, out: FloatArray, frames: Int) {
        val width = 1f + widthStrength.coerceIn(0f, 1f) * WIDTH_MAX
        val midGain = 1f + (width - 1f) * PRESENCE_GUARD
        var i = 0
        repeat(frames) {
            val l = input[i]; val r = input[i + 1]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f
            out[i] = mid * midGain + side * width
            out[i + 1] = mid * midGain - side * width
            i += 2
        }
    }

    private fun process8D(input: FloatArray, out: FloatArray, frames: Int) {
        var i = 0
        repeat(frames) {
            val (gainL, gainR) = panGains()
            val l = input[i]; val r = input[i + 1]
            val readPos = (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size
            val echoL = delayBufferL[readPos]
            val echoR = delayBufferR[readPos]
            val outL = l * gainL + echoL * ECHO_FEEDBACK
            val outR = r * gainR + echoR * ECHO_FEEDBACK
            delayBufferL[delayWritePos] = outL
            delayBufferR[delayWritePos] = outR
            delayWritePos = (delayWritePos + 1) % delayBufferL.size
            out[i] = outL; out[i + 1] = outR
            i += 2
        }
        phase += phaseStep() * frames
    }

    private fun process3D8D(input: FloatArray, out: FloatArray, frames: Int) {
        val width = 1f + widthStrength.coerceIn(0f, 1f) * WIDTH_MAX
        var i = 0
        repeat(frames) {
            val (gainL, gainR) = panGains()
            val l = input[i]; val r = input[i + 1]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f
            val widL = mid + side * width
            val widR = mid - side * width
            val readPos = (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size
            val echoL = delayBufferL[readPos]
            val echoR = delayBufferR[readPos]
            val outL = widL * gainL + echoL * ECHO_FEEDBACK
            val outR = widR * gainR + echoR * ECHO_FEEDBACK
            delayBufferL[delayWritePos] = outL
            delayBufferR[delayWritePos] = outR
            delayWritePos = (delayWritePos + 1) % delayBufferL.size
            out[i] = outL; out[i + 1] = outR
            i += 2
        }
        phase += phaseStep() * frames
    }

    private fun processSurround(input: FloatArray, out: FloatArray, frames: Int, widen: Boolean) {
        val width = 1f + widthStrength.coerceIn(0f, 1f) * WIDTH_MAX
        val midGain = 1f + (width - 1f) * PRESENCE_GUARD
        var i = 0
        repeat(frames) {
            val l = input[i]; val r = input[i + 1]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f
            val widL = if (widen) mid * midGain + side * width else l
            val widR = if (widen) mid * midGain - side * width else r
            val readPos = (delayWritePos - delayReadOffset + delayBufferL.size) % delayBufferL.size
            val echoL = delayBufferL[readPos]
            val echoR = delayBufferR[readPos]
            val outL = widL + echoL * ECHO_FEEDBACK
            val outR = widR + echoR * ECHO_FEEDBACK
            delayBufferL[delayWritePos] = outL
            delayBufferR[delayWritePos] = outR
            delayWritePos = (delayWritePos + 1) % delayBufferL.size
            out[i] = outL; out[i + 1] = outR
            i += 2
        }
    }

    private fun panGains(): Pair<Float, Float> {
        val depth = panDepth.coerceIn(0.1f, 1f)
        val pan = sin(phase).toFloat()
        val theta = (pan * depth + 1f) * (PI * 0.25f).toFloat()
        return cos(theta) to sin(theta)
    }

    private fun phaseStep(): Double = (2.0 * PI) / (rotationSeconds.coerceIn(4f, 60f) * sampleRate)

    private fun softClip(v: Float): Float {
        val a = abs(v)
        if (a <= SOFT_CLIP_THRESHOLD) return v
        val overflow = (a - SOFT_CLIP_THRESHOLD) / (1f - SOFT_CLIP_THRESHOLD)
        val shaped = SOFT_CLIP_THRESHOLD + (1f - SOFT_CLIP_THRESHOLD) * (overflow / (1f + overflow))
        return if (v > 0f) shaped else -shaped
    }

    private fun resetDsp() {
        phase = 0.0
        delayWritePos = 0
        val delaySamples = (sampleRate * ECHO_DELAY_SECONDS).toInt().coerceAtLeast(1)
        delayBufferL = FloatArray(delaySamples)
        delayBufferR = FloatArray(delaySamples)
        delayReadOffset = delaySamples
    }

    companion object {
        const val MODE_OFF = 0
        const val MODE_3D = 1
        const val MODE_8D = 2
        const val MODE_3D_8D = 3
        const val MODE_SURROUND = 4
        private const val WIDTH_MAX = 1.8f
        private const val PRESENCE_GUARD = 0.35f
        private const val ECHO_DELAY_SECONDS = 0.38f
        private const val ECHO_FEEDBACK = 0.3f
        private const val SOFT_CLIP_THRESHOLD = 0.98f
    }
}
