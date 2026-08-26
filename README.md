# SonicLab FX

System-wide equalizer and audio effects for Android. Works on top of every app — YouTube, Spotify, games, anything that plays sound.

## What it does

SonicLab FX hooks into Android's audio output (session 0) and processes all audio before it hits your speakers or headphones. EQ, bass boost, reverb, limiter — applied globally, not just inside one app.

## Features

- 10-band parametric EQ (31 Hz – 16 kHz)
- Bass and treble tone control
- Preamp gain
- 3D / 8D spatial audio
- Stereo balance
- Reverb (room simulation)
- AI-style loudness enhancer
- Auto-normalization (targets -14 LUFS)
- Transparent peak limiter with lookahead
- Fully offline, no internet permission needed

## Requirements

- Android 8.0+
- Android 10+: needs [Shizuku](https://shizuku.rikka.app/) installed and running
- Rooted devices: works directly via `su`

## Building

```
./gradlew :app:assembleDebug
```

Debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

CI also builds automatically on every push to `main` via GitHub Actions.

## How it registers

The app tries these methods in order:

1. **Shizuku** — no root, just Shizuku running (recommended for Android 10+)
2. **Root** — direct shell access via `su`

On Android 9 and below, the native AudioEffect API works without either.

## License

Apache 2.0
