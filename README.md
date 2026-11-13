# Brave Audio

Modern Android audio player written in Kotlin + Jetpack Compose.

## Features

- Audio playback with AndroidX Media3 / ExoPlayer
- Background playback through `MediaSessionService`
- System media notification / lock-screen controls through Media3
- Media library stored with Room
- Pick a folder with Android Storage Access Framework (SAF)
- Persistent folder access and recursive rescanning
- Open an individual audio file from the picker or Android's “Open with” flow
- Search by title, artist or album
- Edit library display metadata (title, artist, album) and remove entries
- Create, rename and delete playlists
- Add library tracks to playlists
- 6-band equalizer UI mapped to the closest hardware/software EQ bands available on the device
- Real-time FFT spectrum visualization
- Dark / light theme
- Modern Material 3 UI
- GitHub Actions build

## Common formats

Media3 and Android platform decoders cover common formats such as MP3, AAC/M4A,
OGG/Vorbis, Opus, FLAC, WAV/PCM and AMR. Actual codec/container support may vary
slightly by Android device because some decoders are provided by the platform.

## Build locally

Requirements:

- JDK 17
- Android SDK Platform 36
- Gradle 8.13 (or use the GitHub Action)

```bash
gradle :app:assembleDebug
```

APK:

`app/build/outputs/apk/debug/app-debug.apk`

## Build in GitHub

1. Create an empty GitHub repository.
2. Upload/push the contents of this project.
3. Open **Actions**.
4. Run **Android APK** or push to `main`.
5. Download the `BraveAudio-debug-apk` artifact.

## Notes about permissions

- `POST_NOTIFICATIONS`: Android 13+ media notification visibility.
- `RECORD_AUDIO`: required by Android's `Visualizer` effect to capture the playback FFT.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: background playback.
- The app intentionally does **not** request broad storage access. Folder/file access is granted by the user through SAF.

## Package

`org.widelogic.braveaudio`

Change `applicationId` and `namespace` in `app/build.gradle.kts` if needed.

## v1.0.3 dependency fix

Uses Activity Compose 1.12.4 and Lifecycle 2.10.0 instead of unresolved versions. Retains the v1.0.2 self-contained Android SDK GitHub Actions workflow. Build needs confirmation in GitHub Actions.


## v1.0.4 build compatibility fix

Pins Compose BOM to 2025.09.01, AndroidX Core to 1.17.0, Activity to 1.11.0,
and Lifecycle to 2.9.4, to avoid Android API 37 / AGP 9.1 requirements.
Build configuration stays at Android API 36 and AGP 8.13.2.
The project has not yet been verified by a successful CI build.

## v1.0.5 Kotlin compilation fixes

- Add missing `androidx.activity.compose.setContent` import.
- Use ExoPlayer `mediaMetadata` property instead of unavailable `currentMediaMetadata`.
- Add Room junction index to address kapt warning.

The Android GitHub Actions build has not been verified as successful yet.
