package com.soniclab.fx.audio

import android.content.Context
import android.content.SharedPreferences

data class FxSettings(
    val enabled: Boolean = false,
    val eqBandGains: FloatArray = FloatArray(BAND_COUNT),
    val bassGainDb: Float = 0f,
    val trebleGainDb: Float = 0f,
    val reverbMix: Float = 0f,
    val reverbRoomSize: Float = 0.5f,
    val preampGainDb: Float = 0f,
) {
    companion object {
        const val BAND_COUNT = 10
        private const val PREFS_NAME = "soniclab_fx_settings"
        private val BAND_FREQS = floatArrayOf(
            31.25f, 62.5f, 125f, 250f, 500f,
            1000f, 2000f, 4000f, 8000f, 16000f
        )

        fun load(context: Context): FxSettings {
            val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val gains = FloatArray(BAND_COUNT) { b -> p.getFloat("eq_$b", 0f) }
            return FxSettings(
                enabled = p.getBoolean("enabled", false),
                eqBandGains = gains,
                bassGainDb = p.getFloat("bass", 0f),
                trebleGainDb = p.getFloat("treble", 0f),
                reverbMix = p.getFloat("reverb_mix", 0f),
                reverbRoomSize = p.getFloat("reverb_room", 0.5f),
                preampGainDb = p.getFloat("preamp", 0f),
            )
        }

        fun bandFrequency(index: Int): Float = BAND_FREQS[index]
    }

    fun save(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit().apply {
            putBoolean("enabled", enabled)
            eqBandGains.forEachIndexed { i, g -> putFloat("eq_$i", g) }
            putFloat("bass", bassGainDb)
            putFloat("treble", trebleGainDb)
            putFloat("reverb_mix", reverbMix)
            putFloat("reverb_room", reverbRoomSize)
            putFloat("preamp", preampGainDb)
            apply()
        }
    }
}
