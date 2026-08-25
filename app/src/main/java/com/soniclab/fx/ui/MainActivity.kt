package com.soniclab.fx.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = dynamicDarkColorScheme()) {
                FxScreen(viewModel = viewModel())
            }
        }
    }

    @Composable
    private fun dynamicDarkColorScheme(): ColorScheme {
        return darkColorScheme()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FxScreen(viewModel: FxViewModel) {
    val settings by viewModel.settings.collectAsState()
    val status by viewModel.status.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SonicLab FX", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status & Toggle
            StatusCard(status, settings.enabled, viewModel)

            // Preamp
            SliderSection(
                title = "Preamp",
                value = settings.preampGainDb,
                valueRange = -12f..12f,
                label = "${"%.1f".format(settings.preampGainDb)} dB",
                onValueChange = { viewModel.updatePreamp(it) }
            )

            // EQ Section
            Text("Equalizer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            for (b in 0 until com.soniclab.fx.audio.FxSettings.BAND_COUNT) {
                val freq = com.soniclab.fx.audio.FxSettings.bandFrequency(b)
                SliderSection(
                    title = formatFreq(freq),
                    value = settings.eqBandGains[b],
                    valueRange = -15f..15f,
                    label = "${"%.1f".format(settings.eqBandGains[b])} dB",
                    onValueChange = { viewModel.updateEqBand(b, it) }
                )
            }
            TextButton(onClick = { viewModel.resetEq() }) { Text("Reset EQ") }

            HorizontalDivider()

            // Bass / Treble
            Text("Tone", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            SliderSection("Bass", settings.bassGainDb, -12f..12f,
                "${"%.1f".format(settings.bassGainDb)} dB") { viewModel.updateBass(it) }
            SliderSection("Treble", settings.trebleGainDb, -12f..12f,
                "${"%.1f".format(settings.trebleGainDb)} dB") { viewModel.updateTreble(it) }

            HorizontalDivider()

            // Reverb
            Text("Reverb", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            SliderSection("Mix", settings.reverbMix, 0f..1f,
                "${"%.0f".format(settings.reverbMix * 100)}%") { viewModel.updateReverbMix(it) }
            SliderSection("Room Size", settings.reverbRoomSize, 0f..1f,
                "${"%.0f".format(settings.reverbRoomSize * 100)}%") { viewModel.updateReverbRoom(it) }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatusCard(status: FxViewModel.Status, enabled: Boolean, viewModel: FxViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (enabled) "ACTIVE" else "BYPASSED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Method: ${status.method}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (status.message.isNotEmpty()) {
                        Text(
                            status.message,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { viewModel.toggleEnabled() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!status.registered) {
                    Button(onClick = { viewModel.registerEffect() }) {
                        Text("Register Effect", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(onClick = { viewModel.unregisterEffect() }) {
                        Text("Unregister", fontSize = 12.sp)
                    }
                }
                // Capability chips
                if (status.shizukuAvailable) {
                    AssistChip(onClick = {}, label = { Text("Shizuku", fontSize = 10.sp) })
                }
                if (status.isRooted) {
                    AssistChip(onClick = {}, label = { Text("Root", fontSize = 10.sp) })
                }
            }
        }
    }
}

@Composable
fun SliderSection(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 14.sp)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatFreq(freq: Float): String {
    return if (freq >= 1000) "${"%.1f".format(freq / 1000)}kHz" else "${"%.0f".format(freq)}Hz"
}
