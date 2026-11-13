## Version 1.1.1 — Kotlin compile fix

Added the missing `androidx.compose.foundation.verticalScroll` import in `MainActivity.kt` required at lines 275 and 538. No UI or audio features were removed. Build in GitHub Actions to verify the APK.

# Brave Audio — Android music player v1.1.0

Native Kotlin / Jetpack Compose / Media3 music player. Updated navy, cyan, white and turquoise interface based on the approved three-screen concept. The source package is ready to upload to GitHub.

## Features

- **Now Playing** — large turquoise play ring, play/pause, previous/next, interactive seek bar, Android media volume, shuffle, repeat all/one/off, favorites, waveform decoration and embedded album cover when available.
- **Library** — Songs, Albums, Artists, Playlists, Favorites. Search by title, artist or album, view embedded cover artwork, see track duration, play a song or an album/artist group.
- **Playlists** — create, rename, delete, add tracks from the song menu, play a playlist, inspect it and remove tracks.
- **Six-band equalizer** — vertical sliders at approximately 60, 170, 310, 1k, 3k and 12k Hz, Bass/Mid/Treble shortcut controls, Flat/Pop/Rock/EDM/Jazz presets, visualizer using the Android audio session.
- **File management** — choose a folder using the Android Storage Access Framework, persist access and recursively rescan on launch or demand; open individual songs using the picker or Android Open With; edit display metadata in the database (does not rewrite audio tags).
- **Playback** — Media3 ExoPlayer + MediaSessionService for background audio, notification/lock-screen controls where available.
- **Appearance** — navy/turquoise dark mode and light blue/white light mode, rounded cards, coherent controls and a dedicated mini player.
- **Audio formats** — MP3, AAC/M4A, Vorbis/OGG, Opus, FLAC, WAV, AMR and compatible device decoders/containers.

## Build on GitHub

1. Create an empty repository named `BraveAudioPlayer`.
2. Upload **all contents** of this ZIP as files at the repository root (including the hidden `.github/workflows/android.yml`). Do not upload the ZIP as the project itself.
3. Commit to `main`.
4. Open **Actions > Android APK** and select the latest run, or choose **Run workflow**.
5. Download artifact `BraveAudio-debug-apk` and extract `app-debug.apk`.

The workflow installs Android command-line tools, platform 36, Gradle 8.13 and JDK 17. The project uses Android Gradle Plugin 8.13.2 and the compatibility-pinned Compose BOM 2025.09.01. Minimum Android version: API 26 (Android 8.0).

## Technical notes / limitations

- Equalizer uses Android's `Equalizer` audio effect. Its band count, actual frequencies and gain range are **device-dependent**; six interface controls are mapped to nearest available hardware/software bands. If the device exposes fewer bands, controls can affect the same physical band. The displayed ±12 scale is a UI normalization, not a guarantee of 12 dB support.
- `RECORD_AUDIO` permission is requested only to activate Android's playback FFT analyzer. The application does not record or transmit microphone audio. On devices where a playback visualizer effect is unsupported, the spectrum will be blank.
- Folder rescanning is automatic when the application launches and after a folder is chosen. This is not a continuous filesystem watcher; use **Rescan folder** after changing files while the app stays open. Revoked/empty folders will not wipe the stored library.
- Playlist and favorite entries are persistent locally. Removing a track from the library does **not** delete the actual audio file.
- The app uses real embedded album artwork where present; otherwise it shows the turquoise music icon.
- This is a source release **not verified with a successful GitHub build in this environment**. Open the GitHub Actions log if a compile/runtime error occurs.

## Project

- Application ID: `org.widelogic.braveaudio`
- Version name: `1.1.0`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
