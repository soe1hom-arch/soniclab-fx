package com.soniclab.fx.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soniclab.fx.audio.DspChain
import com.soniclab.fx.audio.EffectRegistrationManager
import com.soniclab.fx.audio.FxSettings
import com.soniclab.fx.audio.SpatialProcessor
import com.soniclab.fx.service.FxOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FxViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private val dspChain = DspChain()
    private val regManager = EffectRegistrationManager(context)

    private val _settings = MutableStateFlow(FxSettings())
    val settings: StateFlow<FxSettings> = _settings.asStateFlow()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    data class Status(
        val registered: Boolean = false,
        val method: String = "None",
        val message: String = "Not registered",
        val isActive: Boolean = false,
        val shizukuAvailable: Boolean = false,
        val isRooted: Boolean = false,
    )

    init {
        _settings.value = FxSettings.load(context)
        checkCapabilities()
    }

    fun toggleEnabled() {
        val s = _settings.value.copy(enabled = !_settings.value.enabled)
        applySettings(s)
    }

    // EQ
    fun updateEqBand(band: Int, gainDb: Float) {
        val gains = _settings.value.eqBandGains.copyOf()
        gains[band] = gainDb.coerceIn(-15f, 15f)
        applySettings(_settings.value.copy(eqBandGains = gains))
    }
    fun resetEq() {
        applySettings(_settings.value.copy(eqBandGains = FloatArray(FxSettings.BAND_COUNT)))
    }

    // Tone
    fun updateBass(gainDb: Float) = applySettings(_settings.value.copy(bassGainDb = gainDb.coerceIn(-12f, 12f)))
    fun updateTreble(gainDb: Float) = applySettings(_settings.value.copy(trebleGainDb = gainDb.coerceIn(-12f, 12f)))

    // Preamp
    fun updatePreamp(gainDb: Float) = applySettings(_settings.value.copy(preampGainDb = gainDb.coerceIn(-12f, 12f)))

    // Balance
    fun updateBalance(balance: Float) = applySettings(_settings.value.copy(balance = balance.coerceIn(-1f, 1f)))

    // Reverb
    fun updateReverbMix(mix: Float) = applySettings(_settings.value.copy(reverbMix = mix.coerceIn(0f, 1f)))
    fun updateReverbRoom(size: Float) = applySettings(_settings.value.copy(reverbRoomSize = size.coerceIn(0f, 1f)))

    // Enhance
    fun toggleEnhance() = applySettings(_settings.value.copy(enhanceEnabled = !_settings.value.enhanceEnabled))

    // Auto-normalize
    fun toggleAutoNormalize() = applySettings(_settings.value.copy(autoNormalize = !_settings.value.autoNormalize))

    // Spatial
    fun setSpatialMode(mode: Int) {
        val s = _settings.value.copy(
            spatialMode = mode,
            spatial3d = mode == SpatialProcessor.MODE_3D || mode == SpatialProcessor.MODE_3D_8D || mode == SpatialProcessor.MODE_SURROUND,
            spatial8d = mode == SpatialProcessor.MODE_8D || mode == SpatialProcessor.MODE_3D_8D,
            surround = mode == SpatialProcessor.MODE_SURROUND,
        )
        applySettings(s)
    }
    fun updateSpatialWidth(w: Float) = applySettings(_settings.value.copy(spatialWidth = w.coerceIn(0f, 1f)))
    fun updateSpatialRotation(r: Float) = applySettings(_settings.value.copy(spatialRotation = r.coerceIn(4f, 60f)))
    fun updateSpatialPanDepth(d: Float) = applySettings(_settings.value.copy(spatialPanDepth = d.coerceIn(0.1f, 1f)))

    // Registration
    fun registerEffect() {
        viewModelScope.launch {
            _status.value = _status.value.copy(message = "Registering...")
            val result = regManager.register(dspChain)
            _status.value = _status.value.copy(
                registered = result.success,
                method = result.method.name,
                message = result.message
            )
            if (result.success) startService()
        }
    }
    fun unregisterEffect() {
        regManager.unregister()
        stopService()
        _status.value = _status.value.copy(registered = false, method = "None", message = "Unregistered")
    }

    private fun applySettings(s: FxSettings) {
        _settings.value = s
        s.save(context)
        dspChain.updateSettings(s)
        updateServiceSettings(s)
    }

    private fun startService() {
        context.startForegroundService(Intent(context, FxOverlayService::class.java))
        _status.value = _status.value.copy(isActive = true)
    }
    private fun stopService() {
        context.startService(Intent(context, FxOverlayService::class.java).apply { action = FxOverlayService.ACTION_STOP })
        _status.value = _status.value.copy(isActive = false)
    }
    private fun updateServiceSettings(s: FxSettings) {
        context.startService(Intent(context, FxOverlayService::class.java).apply {
            action = FxOverlayService.ACTION_UPDATE_SETTINGS
            putExtra(FxOverlayService.EXTRA_SETTINGS, s)
        })
    }
    private fun checkCapabilities() {
        viewModelScope.launch {
            _status.value = _status.value.copy(
                shizukuAvailable = com.soniclab.fx.util.ShizukuHelper.isAvailable(context),
                isRooted = com.soniclab.fx.util.RootHelper.isRooted()
            )
        }
    }

    override fun onCleared() { regManager.unregister(); super.onCleared() }
}
