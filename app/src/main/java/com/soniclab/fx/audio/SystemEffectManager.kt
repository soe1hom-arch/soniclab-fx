/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Manages Android system audio effects on detected audio sessions.
 *
 * Uses AudioEffect.setParameter() directly instead of typed wrappers
 * to avoid compileSdk compatibility issues.
 */
class SystemEffectManager {

    private val sessionEffects = mutableMapOf<Int, SessionEffects>()

    data class SessionEffects(
        val equalizer: AudioEffect?,
        val bassBoost: BassBoost?,
        val virtualizer: Virtualizer?,
        val reverb: PresetReverb?
    ) {
        fun release() {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            reverb?.release()
        }
    }

    fun attachToSession(sessionId: Int): Boolean {
        if (sessionEffects.containsKey(sessionId)) return true

        return try {
            // Use AudioEffect with built-in EQUALIZER UUID
            val eqType = java.UUID.fromString("0bed4300-ddd6-11db-8f34-0002a5d5c51b")
            val eq = AudioEffect(eqType, java.UUID(0, 0), 0, sessionId)

            val bb = BassBoost(0, sessionId)
            val vz = Virtualizer(0, sessionId)
            val rv = PresetReverb(0, sessionId)

            eq.enabled = true
            bb.enabled = true
            vz.enabled = false
            rv.enabled = false

            sessionEffects[sessionId] = SessionEffects(eq, bb, vz, rv)
            Log.i(TAG, "Effects attached to session $sessionId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach effects to session $sessionId: ${e.message}")
            false
        }
    }

    fun detachFromSession(sessionId: Int) {
        sessionEffects.remove(sessionId)?.release()
        Log.i(TAG, "Effects detached from session $sessionId")
    }

    fun applySettings(settings: FxSettings) {
        for ((_, effects) in sessionEffects) {
            try {
                applyToSession(settings, effects)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply settings: ${e.message}")
            }
        }
    }

    private fun applyToSession(settings: FxSettings, effects: SessionEffects) {
        // BassBoost: 0-1000 strength
        effects.bassBoost?.let { bb ->
            val strength = (settings.bassGainDb.coerceIn(0f, 12f) / 12f * 1000f).toInt()
            try {
                bb.setStrength(strength.toShort())
                bb.enabled = settings.bassGainDb > 0f
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost setStrength failed: ${e.message}")
            }
        }

        // Virtualizer: 0-1000 strength
        effects.virtualizer?.let { vz ->
            val strength = if (settings.spatial3d || settings.enhanceEnabled) {
                (settings.spatialWidth * 1000).toInt().coerceIn(0, 1000)
            } else 0
            try {
                vz.setStrength(strength.toShort())
                vz.enabled = strength > 0
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer setStrength failed: ${e.message}")
            }
        }

        // PresetReverb
        effects.reverb?.let { rv ->
            try {
                if (settings.reverbMix > 0f) {
                    val preset = when {
                        settings.reverbRoomSize < 0.3f -> PresetReverb.PRESET_SMALLROOM
                        settings.reverbRoomSize < 0.6f -> PresetReverb.PRESET_MEDIUMROOM
                        else -> PresetReverb.PRESET_LARGEROOM
                    }
                    rv.preset = preset
                    rv.enabled = true
                } else {
                    rv.enabled = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reverb failed: ${e.message}")
            }
        }

        // Equalizer via parameter IDs (bypass type-specific API)
        effects.equalizer?.let { eq ->
            try {
                // PARAM_EQ_BAND_LEVEL = 1, band indices 0-9, level in millibels
                for (b in 0 until minOf(settings.eqBandGains.size, 10)) {
                    val levelMb = (settings.eqBandGains[b] * 100).toInt().toShort()
                    eq.setParameter(1, b.toShort(), levelMb)
                }
                eq.enabled = true
            } catch (e: Exception) {
                Log.w(TAG, "Equalizer setParameter failed: ${e.message}")
            }
        }
    }

    fun releaseAll() {
        for ((_, effects) in sessionEffects) {
            effects.release()
        }
        sessionEffects.clear()
    }

    fun getAttachedSessions(): List<Int> = sessionEffects.keys.toList()

    companion object {
        private const val TAG = "SystemEffectManager"
    }
}
