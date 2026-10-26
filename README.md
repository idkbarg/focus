# Focus

Focus is a lightweight native Android Pomodoro app built with Java and Material Components (Material 2). It supports Android 5.0 (API 21) and newer.

## Features

- Adjustable focus timer (15, 25, 30, 45, 60 minutes, or a custom duration), with pause/resume and a completion entry saved to that day's note.
- Persian-first Material 2 interface with bundled Vazirmatn typography, right-to-left layout, Jalali and Gregorian dates, and a Telegram X-inspired blue visual style.
- Extensible Quick access tiles for the built-in calculator, Notes, Music, PDFs, exam timer, reminders, and apps you choose from the app list. Long-press an added app tile to remove it.
- Home screen timer widget with a live countdown on Android 7.0+ and a start/pause control.
- Optional focus guard: choose distracting apps and enable the Focus accessibility service in Android Settings. While a Pomodoro is running, opening a selected app returns to the home screen. The guard is opt-in and only observes app/window changes needed for that behavior.
- Editable daily notes show their complete contents and include checkable tasks with deadlines and configurable reminder lead times.
- A Reminders page collects task deadlines and standalone date/time reminders; an independent exam countdown timer supports pause/resume and completion notifications.
- Local reminders are restored after device restart. Android 13+ asks for notification permission when a reminder or timer is first scheduled; alarms use battery-friendly inexact scheduling.
- Daily tasks are shown on the home screen, and dates are presented in both Jalali and Gregorian calendars.
- Music library playback, audio file picker, simple playback controls, and generated looping white noise.
- A compact mini player stays available above the app navigation, with previous, play/pause, and next controls.
- PDF library using Android's document picker, landscape reader with page rotation, progress saved per document, hideable controls, page seeking, and a +5 minute timer control.
- PDF files are staged as seekable app-cache files before rendering, and all PDFium renderer work (including cover thumbnails) is serialized on one worker on newer Android versions. Android 7 uses the in-app reader's legacy synchronous rendering path to preserve compatibility with the previously working implementation.
- A diagnostics report can be shared from Pomodoro settings. It records PDF open/render stages and uncaught Java exceptions without including the selected document's URI.
- Music album art and PDF cover thumbnails are loaded asynchronously and cached on-device; the music and PDF library lists are also saved for later launches. Original documents stay in their selected locations.
- Music and PDF libraries build their rows in batches of 40, so large device libraries do not inflate the view tree or queue every thumbnail at once.
- Tapping the home-screen date opens a date picker and the selected day's note.
- The launcher app name is **Focus**; the in-app interface remains Persian.
- Last-read PDF and player controls on the timer screen.

PDFs and picked audio use Android's system document picker. Music library access is requested only when you choose **Load device music library**; denying it still leaves the audio picker available. PDF page text is rendered as an image and is not selectable.

Vazirmatn is distributed under the SIL Open Font License 1.1; its license is included at `app/src/main/assets/fonts/OFL.txt`.

## Build

Install Android SDK platform 35 and Android SDK build-tools, then run:

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow in `.github/workflows/android-apk.yml` builds this APK on pushes, pull requests, and manual runs, and uploads it as the `focus-debug-apk` artifact.
