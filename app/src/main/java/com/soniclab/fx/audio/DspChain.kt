package com.soniclab.fx.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Real-time DSP chain for system-wide audio processing.
 * Runs on the audio thread inside GlobalAudioEffect.process().
 *
 * Chain order: auto-normalize → preamp → balance → EQ (10 bands) →
 *              bass/treble → AI enhance → spatial → reverb → limiter
 */
class DspChain {

    private var sampleRate = 44100
    private var channelCount = 2
    private var enabled = false

    // Processors
    private val autoNormalizer = AutoNormalizer()
    private val balanceProcessor = BalanceProcessor()
    private val eqFilters = Array(10) { Array(2) { BiquadFilter() } }
    private val bassFilters = Array(2) { BiquadFilter() }
    private val trebleFilters = Array(2) { BiquadFilter() }
    private val enhancer = ClassicEnhancer()
    private val spatial = SpatialProcessor()
    private val reverbL = ReverbChannel()
    private val reverbR = ReverbChannel()

    // Limiter
    private var limiterGain = 1f
    private val lookaheadBuffer = FloatArray(LOOKAHEAD_FRAMES)
    private var lookaheadPos = 0
    private var lookaheadFilled = false

    @Volatile var settings = FxSettings()

    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        rebuildFilters()
        reverbL.init(sampleRate)
        reverbR.init(sampleRate)
        spatial.configure(sampleRate)
    }

    fun setEnabled(enabled: Boolean) { this.enabled = enabled }

    fun process(samples: FloatArray): FloatArray {
        if (!enabled) return samples
        val s = settings
        val ch = channelCount.coerceAtMost(2)
        val frames = samples.size / ch

        // 1. Auto-normalization gain
        if (s.autoNormalize) {
            val gain = autoNormalizer.processGain(samples)
            if (gain != 1f) for (i in samples.indices) samples[i] *= gain
        }

        // 2. Preamp
        if (s.preampGainDb != 0f) {
            val g = preampGainLin
            for (i in samples.indices) samples[i] *= g
        }

        // 3. Balance
        balanceProcessor.balance = s.balance
        balanceProcessor.process(samples, ch)

        // 4. EQ bands
        for (i in samples.indices) {
            val c = (i % ch).coerceAtMost(1)
            var v = samples[i]
            for (b in 0 until FxSettings.BAND_COUNT) {
                if (s.eqBandGains[b] != 0f) v = eqFilters[b][c].process(v)
            }
            samples[i] = v
        }

        // 5. Bass / Treble
        for (i in samples.indices) {
            val c = (i % ch).coerceAtMost(1)
            if (s.bassGainDb != 0f) samples[i] = bassFilters[c].process(samples[i])
            if (s.trebleGainDb != 0f) samples[i] = trebleFilters[c].process(samples[i])
        }

        // 6. AI Enhance
        enhancer.enabled = s.enhanceEnabled
        if (s.enhanceEnabled) {
            enhancer.enhance(samples)
        }

        // 7. Spatial (3D/8D/Surround)
        spatial.spatial3d = s.spatial3d
        spatial.spatial8d = s.spatial8d
        spatial.surround = s.surround
        spatial.widthStrength = s.spatialWidth
        spatial.rotationSeconds = s.spatialRotation
        spatial.panDepth = s.spatialPanDepth
        if (s.spatial3d || s.spatial8d || s.surround) {
            spatial.process(samples, frames, ch)
        }

        // 8. Reverb
        if (s.reverbMix > 0f) {
            for (i in 0 until samples.size step ch) {
                if (ch >= 2) {
                    samples[i] = reverbL.process(samples[i], s.reverbMix, s.reverbRoomSize)
                    samples[i + 1] = reverbR.process(samples[i + 1], s.reverbMix, s.reverbRoomSize)
                } else {
                    samples[i] = reverbL.process(samples[i], s.reverbMix, s.reverbRoomSize)
                }
            }
        }

        // 9. Limiter (final safety)
        applyLimiter(samples)
        return samples
    }

    private fun applyLimiter(samples: FloatArray) {
        var futurePeak = 0f
        for (v in samples) futurePeak = max(futurePeak, abs(v))
        lookaheadBuffer[lookaheadPos] = futurePeak
        lookaheadPos = (lookaheadPos + 1) % LOOKAHEAD_FRAMES
        if (lookaheadPos == 0) lookaheadFilled = true

        var bufferPeak = 0f
        val count = if (lookaheadFilled) LOOKAHEAD_FRAMES else lookaheadPos
        for (i in 0 until count) bufferPeak = max(bufferPeak, lookaheadBuffer[i])

        val target = if (bufferPeak > LIMITER_THRESHOLD && bufferPeak > 0f) LIMITER_THRESHOLD / bufferPeak else 1f
        limiterGain = if (target < limiterGain) target else {
            val coef = 1f - exp(-1f / (RELEASE_MS / 1000f * sampleRate / samples.size.toFloat()))
            limiterGain + (target - limiterGain) * coef
        }
        if (limiterGain != 1f) {
            for (i in samples.indices) samples[i] *= limiterGain
        }
    }

    private fun rebuildFilters() {
        val fs = sampleRate
        val s = settings
        for (b in 0 until FxSettings.BAND_COUNT) {
            for (c in 0 until 2) {
                eqFilters[b][c].setPeaking(fs, FxSettings.bandFrequency(b), s.eqBandGains[b])
            }
        }
        for (c in 0 until 2) {
            bassFilters[c].setLowShelf(fs, BASS_FREQ, s.bassGainDb)
            trebleFilters[c].setHighShelf(fs, TREBLE_FREQ, s.trebleGainDb)
        }
    }

    private val preampGainLin: Float get() = 10f.pow(settings.preampGainDb / 20f)

    fun updateSettings(newSettings: FxSettings) {
        settings = newSettings
        rebuildFilters()
        enabled = newSettings.enabled
        autoNormalizer.enabled = newSettings.autoNormalize
        if (newSettings.enabled) {
            reverbL.init(sampleRate)
            reverbR.init(sampleRate)
            spatial.configure(sampleRate)
        }
    }

    fun reset() {
        limiterGain = 1f
        lookaheadPos = 0
        lookaheadFilled = false
        for (b in eqFilters) for (f in b) f.reset()
        for (f in bassFilters) f.reset()
        for (f in trebleFilters) f.reset()
        reverbL.reset(); reverbR.reset()
        enhancer.reset()
        autoNormalizer.reset()
    }

    private class ReverbChannel {
        private val combDelays = IntArray(4)
        private val combBuffers = Array(4) { FloatArray(1) }
        private val combPos = IntArray(4)
        private val combDamped = FloatArray(4)
        private val apDelays = IntArray(2)
        private val apBuffers = Array(2) { FloatArray(1) }
        private val apPos = IntArray(2)

        fun init(fs: Int) {
            val tunings = floatArrayOf(0.0297f, 0.0371f, 0.0411f, 0.0437f)
            for (i in 0 until 4) {
                combDelays[i] = (tunings[i] * fs).toInt().coerceAtLeast(1)
                if (combBuffers[i].size != combDelays[i]) combBuffers[i] = FloatArray(combDelays[i])
                combPos[i] = 0; combDamped[i] = 0f
            }
            val apTunings = floatArrayOf(0.0051f, 0.0017f)
            for (i in 0 until 2) {
                apDelays[i] = (apTunings[i] * fs).toInt().coerceAtLeast(1)
                if (apBuffers[i].size != apDelays[i]) apBuffers[i] = FloatArray(apDelays[i])
                apPos[i] = 0
            }
        }

        fun process(input: Float, mix: Float, roomSize: Float): Float {
            val feedback = 0.6f + roomSize.coerceIn(0f, 1f) * 0.22f
            var wet = 0f
            for (i in 0 until 4) {
                val delayed = combBuffers[i][combPos[i]]
                combDamped[i] += (delayed - combDamped[i]) * DAMPING
                combBuffers[i][combPos[i]] = input + combDamped[i] * feedback
                combPos[i] = (combPos[i] + 1) % combBuffers[i].size
                wet += delayed
            }
            for (i in 0 until 2) {
                val buffered = apBuffers[i][apPos[i]]
                val out = -wet + buffered
                apBuffers[i][apPos[i]] = wet + buffered * AP_FEEDBACK
                apPos[i] = (apPos[i] + 1) % apBuffers[i].size
                wet = out
            }
            return input * (1f - mix) + wet * mix * REVERB_GAIN
        }

        fun reset() {
            for (i in 0 until 4) { combBuffers[i].fill(0f); combPos[i] = 0; combDamped[i] = 0f }
            for (i in 0 until 2) { apBuffers[i].fill(0f); apPos[i] = 0 }
        }
    }

    companion object {
        private const val BASS_FREQ = 150f
        private const val TREBLE_FREQ = 3200f
        private const val DAMPING = 0.65f
        private const val AP_FEEDBACK = 0.5f
        private const val REVERB_GAIN = 0.3f
        private const val LIMITER_THRESHOLD = 0.90f
        private const val LOOKAHEAD_FRAMES = 220
        private const val RELEASE_MS = 120f
    }
}
