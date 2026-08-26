/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Manages Android system audio effects (Equalizer, BassBoost, Virtualizer,
 * PresetReverb) on detected audio sessions.
 *
 * Maps SonicLab FX UI controls to system effect parameters.
 * These are the same effects used by Wavelet and Poweramp Equalizer.
 */
class SystemEffectManager {

    private val sessionEffects = mutableMapOf<Int, SessionEffects>()

    data class SessionEffects(
        val equalizer: Equalizer?,
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

    /**
     * Attach system effects to a given audio session.
     */
    fun attachToSession(sessionId: Int): Boolean {
        if (sessionEffects.containsKey(sessionId)) return true

        return try {
            val eq = Equalizer(0, sessionId)
            val bb = BassBoost(0, sessionId)
            val vz = Virtualizer(0, sessionId)
            val rv = PresetReverb(0, sessionId)

            eq.enabled = true
            bb.enabled = true
            vz.enabled = false  // Start disabled, user enables via UI
            rv.enabled = false

            sessionEffects[sessionId] = SessionEffects(eq, bb, vz, rv)
            Log.i(TAG, "Effects attached to session $sessionId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach effects to session $sessionId: ${e.message}")
            false
        }
    }

    /**
     * Detach effects from a session.
     */
    fun detachFromSession(sessionId: Int) {
        sessionEffects.remove(sessionId)?.release()
        Log.i(TAG, "Effects detached from session $sessionId")
    }

    /**
     * Apply FX settings to all active sessions.
     */
    fun applySettings(settings: FxSettings) {
        for ((sessionId, effects) in sessionEffects) {
            try {
                applyToSession(settings, effects)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply settings to session $sessionId: ${e.message}")
            }
        }
    }

    private fun applyToSession(settings: FxSettings, effects: SessionEffects) {
        // Equalizer: map our 10 bands to system EQ bands
        effects.equalizer?.let { eq ->
            val bands = eq.numberOfBands
            val minLevel = eq.bandLevelRange[0].toInt()
            val maxLevel = eq.bandLevelRange[1].toInt()

            for (b in 0 until minOf(settings.eqBandGains.size, bands)) {
                // Map our ±15dB to system range
                val level = (settings.eqBandGains[b] / 15f * maxLevel).toInt()
                    .coerceIn(minLevel, maxLevel)
                eq.setBandLevel(b.toShort(), level.toShort())
            }
        }

        // BassBoost: map our bass gain (0-12dB mapped to 0-1000)
        effects.bassBoost?.let { bb ->
            val strength = (settings.bassGainDb.coerceIn(0f, 12f) / 12f * 1000f).toInt()
            bb.setStrength(strength.toShort())
            bb.enabled = settings.bassGainDb > 0f
        }

        // Virtualizer: map our spatial/enhance to virtualizer strength
        effects.virtualizer?.let { vz ->
            val strength = if (settings.spatial3d || settings.enhanceEnabled) {
                (settings.spatialWidth * 1000).toInt().coerceIn(0, 1000)
            } else 0
            vz.setStrength(strength.toShort())
            vz.enabled = strength > 0
        }

        // PresetReverb: map our reverb mix to room presets
        effects.reverb?.let { rv ->
            if (settings.reverbMix > 0f) {
                val preset = when {
                    settings.reverbRoomSize < 0.3f -> PresetReverb.PRESET_SMALLROOM
                    settings.reverbRoomSize < 0.6f -> PresetReverb.PRESET_MEDIUMROOM
                    else -> PresetReverb.PRESET_LARGEROOM
                }
                rv.preset = preset.toShort()
                rv.enabled = true
            } else {
                rv.enabled = false
            }
        }
    }

    /**
     * Release all effects on all sessions.
     */
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
