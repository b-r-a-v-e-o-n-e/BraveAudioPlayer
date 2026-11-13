## Playlist editor v1.3.1

- Create a playlist in **Library → Playlists → New playlist**; the new playlist opens automatically.
- Inside a playlist: **Add folder** recursively scans a selected SAF folder and adds supported audio files to **that playlist**.
- **Add files** uses Android's multi-document picker to add one or more audio files to **that playlist**.
- Existing tracks are not duplicated. Selected documents and folders retain access permission when granted by the Android provider.
- **Clear all songs from playlist** removes only playlist membership. A confirmation dialog protects against accidental clearing; audio files and the global music library are untouched.
- The playlist's three-dot menu also contains **Clear songs**.
- This is a source-code build; run the included GitHub Actions workflow to compile an APK.

# Brave Audio Player v1.3.0

Android audio player in Kotlin + Jetpack Compose, compatible with Android 8.0+ (minSdk 26), targetSdk 36.

## New in 1.3.0

- Separate **Settings** tab (also accessible from the menu)
- **Sleep timer**: 15, 30, 45, 60 or 90 minutes; Cancel option; runs in `PlaybackService` instead of Activity and pauses at deadline
- **Custom six-band EQ presets**: Save, load, replace and delete named presets, persisted as JSON in app preferences
- **M3U playlist import/export** through Android Storage Access Framework; playlist entries matched against tracks already in the library
- **Media3 MediaSessionService** for system media controls / lock screen / compatible headsets; title/artist provided as metadata
- **Playback troubleshooting**: resilient Play/Pause (including idle, buffering, ended and error states), bounded retry on player errors, visible error code
- **96 FFT values** supporting fine bars; preserves Bars/Fine bars/Curve spectrum modes

## Existing features retained

- Five UI color schemes including yellow `#FCBF3E` for positive EQ levels
- Modern dark cyan/blue UI, custom launcher icon, author avatar/About
- Folder scanning, song/library search, albums, artists, favorites and playlists
- Media3 background playback, six-band device-compatible Android Equalizer
- Audio formats supported by device and Media3 decoders (MP3, AAC, FLAC, OGG, OPUS, WAV, etc.)

## Important compatibility notes

- `.m3u` exports use SAF `content://` references. On another device, such URIs may not resolve. Import attempts to match by track filenames and titles in the existing library. Files not found are skipped; the result reports number matched.
- Media notifications and lock-screen controls are handled by Android Media3 and may differ by device/Android version.
- The six EQ control points map onto the available native equalizer bands of the device; they may not be independent on devices supporting fewer physical bands.
- Spectrum capture uses Android `RECORD_AUDIO` permission for Visualizer. Graph values are relative visual levels, not calibrated sound-pressure dB.
- Timer is owned by playback service and may be reset if Android force-stops the app; the next service start restores an unexpired deadline.

## Build on GitHub

Upload all project files, **including the hidden `.github` folder**, to your repository. Push to `main` or open Actions -> Android APK -> Run workflow.

The workflow uses JDK 17, AGP 8.13.2, Gradle 8.13, compileSdk 36 and generates a debug APK artifact:

`app/build/outputs/apk/debug/app-debug.apk`

## Suggested test checklist

1. Add music using folder picker; play several different tracks.
2. Press Home, lock phone, and verify media notification/lock-screen/headset controls.
3. Run playback for an extended period, then try pause/resume, previous/next and end of queue.
4. Open Equalizer; save a custom preset, close/reopen app, and load it.
5. Set a 15-minute sleep timer; verify pause and Cancel.
6. Export a playlist as M3U, import it again, and review matched-track count.
7. Switch themes and spectrum modes; check About author information.

### Build status

The project archive is generated and statically checked, but **no complete Gradle/Android SDK compilation was possible in this container**. GitHub Actions must verify compilation. If a build fails, share the first `e:`/`error:` messages from `Build APK`.

## Additional notes

- This is an Android source release, **not a precompiled or device-tested APK**.
- Sleep timer is managed by the media playback service. It is not a guaranteed system alarm,
  particularly if Android force-stops the app.
- Exported M3U files contain original track URIs. On another phone, URI access may differ;
  the importer also attempts filename/title matching against the currently scanned library.
- MediaSessionService provides Android media controls; the exact notification display depends
  on Android version and system notification settings.
- The curve visualizer is an FFT-derived *relative* spectrum (not a calibrated dB meter).
- The six EQ UI bands map to supported platform equalizer bands, which vary by device.
- If playback stops again, capture `adb logcat -s BraveAudio ExoPlayerImpl MediaSessionService`
  and include the Android version / device model for diagnosis.

## v1.3.2 UI adjustments

- Moved Live Spectrum (Bars / Fine bars / Curve) into the Player playback control panel, directly above Previous / Play-Pause / Next.
- Reduced main circular Play button / art ring approximately 30% (236dp -> 165dp).
- Analyzer capture starts and stops with the Player screen; optional microphone/audio-capture permission button is shown there.
- The Equalizer screen now focuses on EQ bands and presets only.

## v1.3.3 compile fix

- Fixed missing `LocalContext.current` binding in `EqualizerScreen` after moving Live Spectrum into Player.
- Updated version to 1.3.3.
- No other functional changes in this patch.
