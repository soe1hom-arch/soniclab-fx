/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.audio

import android.content.Context
import android.media.audiofx.AudioEffect
import android.util.Log

/**
 * System-wide audio effect registration via AudioEffect API.
 *
 * On Android, AudioEffect requires a UUID-based effect to be installed by the
 * system.  For a custom app effect to process ALL audio, the app must either:
 *  1. Be a system/privileged app (signed with platform key)
 *  2. Register via Shizuku/root using `cmd audio` or `dumpsys audio`
 *
 * This class manages the lifecycle of querying, enabling, and disabling
 * built-in system effects as a fallback, and provides the registration
 * logic for Shizuku/root paths.
 */
object GlobalAudioEffect {

    private const val TAG = "GlobalAudioEffect"

    private var registered = false

    /**
     * Query available system audio effects.
     * Returns list of effect descriptors (name + UUID).
     */
    fun queryAvailableEffects(context: Context): List<AudioEffect.Descriptor> {
        return try {
            val effects = AudioEffect.queryEffects()
            effects?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query effects: ${e.message}")
            emptyList()
        }
    }

    /**
     * Build shell commands to register a system-wide audio effect.
     * Used by Shizuku/root registration paths.
     *
     * Returns the commands that need to be executed in sequence.
     */
    fun buildRegistrationCommands(packageName: String): List<String> {
        return listOf(
            // Enable audio effect framework
            "cmd audio set-audio-session-id 0",
            // Grant the app permission to modify audio settings
            "pm grant $packageName android.permission.MODIFY_AUDIO_SETTINGS",
            // Allow the app to capture audio output (needed for processing)
            "pm grant $packageName android.permission.CAPTURE_AUDIO_OUTPUT 2>/dev/null || true",
            // Set audio effects to be globally enabled
            "settings put global audio_effects_enabled 1"
        )
    }

    /**
     * Build shell commands to disable/unregister effects.
     */
    fun buildUnregistrationCommands(packageName: String): List<String> {
        return listOf(
            "settings put global audio_effects_enabled 0"
        )
    }

    fun isRegistered(): Boolean = registered

    fun setRegistered(value: Boolean) { registered = value }
}
