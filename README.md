# Brave Audio v1.4.5 — Spectrum display calibration

## Fixes
- Changed rendering from the misleading -96..0 dB -> full-height mapping to an uncalibrated Visualizer-friendly fixed range (-48..-8 dB; gate below -50 dB).
- Near silence now draws close to zero instead of a half-filled column.
- Loud passages can use the available range without requiring a peak-normalization per frame.
- Bars and Curve share the same transfer function.
- Fewer-bar modes now calculate mean linear spectral power, rather than selecting each group's highest bin.
- Source FFT data are still derived from Android Visualizer; display-only changes do not manufacture FFT measurements.

### Note
Android's Visualizer FFT is device dependent and not a calibrated audio meter. The display thresholds are sensible defaults, not accurate absolute SPL or laboratory dBFS measurements. Changes in music output volume, effects and device implementation can alter the reading. The actual measured FFT magnitudes remain intact.

## Build
GitHub Actions workflow included (Gradle 8.13, JDK 17, Android SDK 36).
