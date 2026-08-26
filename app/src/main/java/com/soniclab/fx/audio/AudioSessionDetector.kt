/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.audio

import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.util.Log

/**
 * Monitors active audio playback sessions via AudioManager callback.
 * When a new session starts playing, notifies the listener so effects
 * can be attached.
 */
class AudioSessionDetector(private val audioManager: AudioManager) {

    private var listener: SessionListener? = null
    private val trackedSessions = mutableSetOf<Int>()

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val activeSessions = configs
                .mapNotNull { config ->
                    val sessionId = getSessionId(config)
                    if (sessionId > 0) config.audioAttributes.contentType to sessionId else null
                }

            // Find newly started sessions
            val currentIds = activeSessions.map { it.second }.toSet()
            val newSessions = currentIds - trackedSessions
            val removedSessions = trackedSessions - currentIds

            for (id in newSessions) {
                Log.i(TAG, "New audio session: $id")
                listener?.onSessionStarted(id)
            }
            for (id in removedSessions) {
                Log.i(TAG, "Audio session ended: $id")
                listener?.onSessionEnded(id)
            }
            trackedSessions.clear()
            trackedSessions.addAll(currentIds)
        }
    }

    fun start(listener: SessionListener) {
        this.listener = listener
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)

        // Check currently active sessions
        val current = audioManager.activePlaybackConfigurations
        for (config in current) {
            val sessionId = getSessionId(config)
            if (sessionId > 0 && !trackedSessions.contains(sessionId)) {
                trackedSessions.add(sessionId)
                Log.i(TAG, "Existing audio session: $sessionId")
                listener.onSessionStarted(sessionId)
            }
        }
    }

    fun stop() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        for (id in trackedSessions) {
            listener?.onSessionEnded(id)
        }
        trackedSessions.clear()
        listener = null
    }

    fun getActiveSessions(): List<Int> = trackedSessions.toList()

    private fun getSessionId(config: AudioPlaybackConfiguration): Int {
        return try {
            val method = config.javaClass.getMethod("getAudioSessionId")
            method.invoke(config) as? Int ?: 0
        } catch (e: Exception) {
            0
        }
    }

    interface SessionListener {
        fun onSessionStarted(sessionId: Int)
        fun onSessionEnded(sessionId: Int)
    }

    companion object {
        private const val TAG = "AudioSessionDetector"
    }
}
