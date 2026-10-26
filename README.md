# Focus

Focus is a lightweight native Android Pomodoro app built with Java and Material Components (Material 2). It supports Android 5.0 (API 21) and newer.

## Features

- Adjustable focus timer (15, 25, 30, 45, 60 minutes, or a custom duration), with pause/resume and a completion entry saved to that day's note.
- Optional focus guard: choose distracting apps and enable the Focus accessibility service in Android Settings. While a Pomodoro is running, opening a selected app returns to the home screen. The guard is opt-in and only observes app/window changes needed for that behavior.
- Editable daily notes, saved automatically on-device.
- Music library playback, audio file picker, simple playback controls, and generated looping white noise.
- PDF library using Android's document picker, landscape reader with page rotation, progress saved per document, hideable controls, and a +5 minute timer control.
- Last-read PDF and player controls on the timer screen.

PDFs and picked audio use Android's system document picker. Music library access is requested only when you choose **Load device music library**; denying it still leaves the audio picker available. PDF page text is rendered as an image and is not selectable.

## Build

Install Android SDK platform 35 and Android SDK build-tools, then run:

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow in `.github/workflows/android-apk.yml` builds this APK on pushes, pull requests, and manual runs, and uploads it as the `focus-debug-apk` artifact.
