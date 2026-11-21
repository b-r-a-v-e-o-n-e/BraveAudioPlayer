# Brave Audio v1.4.0 — Real Spectrum, 10-Band EQ and Playback Recovery

Complete Kotlin/Jetpack Compose Android source project. Build on GitHub Actions with the included `.github/workflows/android.yml`.

## Changes since v1.3.3

### Real spectrum — no synthetic frequency curves
- Uses `android.media.audiofx.Visualizer` attached to the currently playing ExoPlayer audio session.
- Parses actual Android packed FFT real/imaginary data in `SpectrumMath.logBands`.
- Converts FFT bins to logarithmically spaced bands from approximately 30 Hz to 20 kHz, constrained by Nyquist frequency.
- Bars: 32 measured groups; Fine bars: 96 measured groups; Curve: 96 measured groups connected as a response line.
- All modes use the *same actual FFT data*. No random/mock data and no artificial secondary curve.
- Display is approximate **dBFS**, not sound-pressure dB (SPL), and depends on the device's 8-bit Visualizer implementation. Below FFT resolution, bands share the same source bin. For example, a 1024-point FFT at 44.1 kHz cannot distinguish frequencies below ~43 Hz.
- Visualizer is released when playback pauses or UI leaves Player, reducing native audio-effect resource pressure.

### 10-band EQ
- Frequencies: **31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz**.
- Android 9+ (API 28+) attempts 10-band `DynamicsProcessing` pre-EQ with per-band +/-12 dB.
- On devices without DynamicsProcessing, falls back to the actual native Android `Equalizer`; some devices expose fewer than 10 bands, so 10 sliders are combined into available native bands. The EQ screen shows which engine is in use.
- Existing user saved 6-band EQ presets are migrated to 10 control values by interpolation. New presets save all 10 values.

### Real-time volume boost
- Settings → Audio Gain: enable/disable plus **100–300%** boost.
- Uses Android `LoudnessEnhancer` attached to the ExoPlayer audio session.
- Choice and enable state persist across restarts; 100% is unamplified.
- Not a calibrated loudness normalizer, replay gain, or clip-proof compressor. High values can distort or clip. Start at 125–150% and reduce when needed.
- Some devices may not implement the effect; the UI reports when unavailable.

### Resume after long pause, sleep and track end
- Saves playback queue, active item and position in app preferences.
- Restores the queue in a new process, with `playWhenReady = false` until user taps Play.
- Resumes from idle/error state by preparing the player; at track end restarts from time zero.
- Playback service is reconnected when Activity returns to foreground.
- Attempts only one automatic retry for a playback error instead of looping endlessly.
- SAF permission persistence for single opened tracks, where the document provider allows it.
- Actual results after device deep sleep, process death and battery restrictions must be tested on the target Android device. Data may become inaccessible when a provider revokes permissions or a file is deleted.

## Previously included features
- Library scan and SAF folder import, playlists and direct adding of folders and audio files
- Clear playlist without deleting files, M3U import/export, favorites
- Now Playing, previous/next, pause/seek/shuffle/repeat, background playback via Media3
- Sleep timer, multiple themes, personalized About page and custom launcher icon

## Build

1. Upload all project contents, including `.github`, to the root of your GitHub repository.
2. Push/commit to `main` or use **Actions → Android APK → Run workflow**.
3. Download **BraveAudio-debug-apk** from the finished workflow artifacts.

Or run locally with JDK 17, Android SDK 36 and Gradle 8.13:

```bash
gradle --no-daemon :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Validation status

Source ZIP contents were checked. An actual Android Gradle compile and on-device audio tests could not be run in this environment. Confirm both build and runtime behavior through GitHub Actions and on a phone.

## v1.4.1 Build fix

Removed duplicate `androidx.media3.common.Player` import from `PlaybackService.kt`. Updated Android versionCode to 14 and versionName to 1.4.1. No DSP or UI modifications.


## v1.4.2 — Visualization performance and audio gain lifecycle

- Settings → Visualization Performance: **Low Realtime** (default, 256-point FFT, 32 calculated bands, max supported callback rate) or **High Fidelity** (device maximum FFT length, 96 bands, 75% supported callback rate). Preference survives restarts.
- Low mode minimizes FFT and Compose drawing workload; the exact callback frequency remains subject to the Android device and system.
- Bars and Curve render from the same real measured FFT data. Bars use a fixed, six-stop dBFS gradient instead of an abrupt cyan/yellow cutoff.
- PlaybackService no longer releases process-owned equalizer/gain when the service is recycled independently of the player. This prevents an avoidable loss of LoudnessEnhancer boost.
- The app itself doesn't decrease the system STREAM_MUSIC volume when the display locks. Android audio focus ducking, another app, headset controls, and manufacturer policies can still adjust audible output independently.
- Not compile-tested in this environment; build with the included GitHub Actions workflow.
