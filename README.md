# SonicLab FX — System-Wide Audio Effects

[![Android Build](https://img.shields.io/badge/build-passing-brightgreen)]()

A system-wide audio equalizer and effects app for Android. Apply EQ, bass/treble, reverb, and limiter to **all audio** on your device — YouTube, Spotify, games, podcasts, everything.

## How It Works

SonicLab FX registers an audio effect on Android's global output mix (session 0). Once active, the Android audio framework routes ALL audio through the DSP chain before it reaches the speaker/headphones.

### Registration Methods

| Method | Android Version | Root Required | Notes |
|--------|----------------|---------------|-------|
| **Direct API** | ≤ 9 (Pie) | No | Works on older devices natively |
| **Shizuku** | 10+ | No | Requires Shizuku app (ADB-privileged shell) |
| **Root** | Any | Yes | Direct access via `su` shell |

## Features

- **10-band parametric EQ** (31.25 Hz – 16 kHz, ±15 dB)
- **Bass/Treble tone control** (±12 dB, RBJ shelf filters)
- **Preamp gain** (±12 dB)
- **Reverb** (room simulation, adjustable mix & room size)
- **Transparent peak limiter** (0.90 threshold, 5ms lookahead)
- **Fully offline** — no internet permission, no data collection
- **Material 3 dark UI** with Jetpack Compose

## Requirements

- Android 8.0+ (API 26)
- For Android 10+: [Shizuku](https://shizuku.rikka.app/) app installed and running
- For rooted devices: root access via Magisk/SuperSU

## Build

```bash
./gradlew :app:assembleDebug
```

## Architecture

```
app/
├── audio/
│   ├── BiquadFilter.kt        — RBJ biquad (peaking/shelf)
│   ├── DspChain.kt            — Full DSP pipeline
│   ├── EffectRegistrationManager.kt — Auto-selects registration method
│   ├── FxSettings.kt          — Persistent settings (SharedPreferences)
│   └── GlobalAudioEffect.kt   — AudioEffect API wrapper
├── service/
│   └── FxOverlayService.kt    — Foreground service
├── util/
│   ├── RootHelper.kt          — Root shell execution
│   └── ShizukuHelper.kt       — Shizuku integration
└── ui/
    ├── FxViewModel.kt         — State management
    └── MainActivity.kt        — Compose UI
```

## DSP Chain

```
Input → Preamp → 10-Band EQ → Bass Shelf → Treble Shelf → Reverb → Limiter → Output
```

All processing runs in 32-bit float. The limiter uses a 220-frame lookahead buffer (~5ms at 44.1kHz) to prevent transient clipping.

## License

Apache License 2.0
