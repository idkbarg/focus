# Focus repository instructions

## Build, test, and lint

- Use JDK 17 and Android SDK platform/build-tools 35. The project uses Gradle 8.9 through `./gradlew`.
- Build the debug APK with `./gradlew --no-daemon assembleDebug`; the output is `app/build/outputs/apk/debug/app-debug.apk`.
- Run Android lint with `./gradlew :app:lintDebug`.
- There are currently no test source files. When adding JVM tests, run the debug unit suite with `./gradlew :app:testDebugUnitTest` or one test class with `./gradlew :app:testDebugUnitTest --tests 'com.sinlavi.focus.YourTest'`. Instrumented tests, if added, run with `./gradlew :app:connectedDebugAndroidTest` on a connected device.
- CI builds with JDK 17 and SDK platform 35; see [the Android APK workflow](workflows/android-apk.yml). The project overview and SDK setup are in [README.md](../README.md).

## Architecture

- This is a single Android application module (`:app`), written in Java with package `com.sinlavi.focus`, Material Components (Material 2), minSdk 21, and target/compile SDK 35.
- `MainActivity` is the app shell and owns the programmatically constructed screens, navigation, Pomodoro and exam timers, notes/tasks, music player, and PDF reader. `showScreen` and the bottom-navigation menu switch between those screens; feature-specific UI and state are mostly implemented in this activity rather than separate view/controller layers.
- Android entry points are separate components: `FocusWidgetProvider` renders the home-screen widget and sends actions to `MainActivity`; `ReminderScheduler` and `ReminderReceiver` schedule, restore, and deliver alarms/notifications; `FocusGuardService` implements the opt-in accessibility-based app guard; and `AppDiagnostics` records local diagnostics.
- `focus_data` SharedPreferences is the persistence contract shared by the activity and these components. Notes use `note:<yyyy-MM-dd>`, tasks use `tasks:<yyyy-MM-dd>` JSON, and libraries/reminders and other feature state use preference keys (often JSON strings). Keep readers and writers—including widget, service, and reboot-restoration code—in sync when changing keys or stored shapes.
- Timer state uses `SystemClock.elapsedRealtime()` for live countdowns and wall-clock end timestamps for persistence/restoration and widget display. Preserve this distinction when changing timer behavior.
- Music/PDF library access uses Android document/media providers and persisted URI permissions where available. Keep provider I/O and media/PDF work off the UI thread; PDF rendering and cover work share a single executor on newer Android versions, while Android 7 uses its legacy synchronous page-render path.
- Most screen layouts are built in Java; XML resources supply the widget, navigation menu, and visual theme. Reuse the Material theme/components and existing `dp()`/view helpers when changing screens.

## Repository conventions

- The interface is Persian-first and right-to-left. `MainActivity.attachBaseContext` sets `fa-IR` and RTL, and Vazirmatn is bundled. Some visible strings are translated through `fa()`, while formatted strings are Android resources with entries in both `values/strings.xml` and `values-fa/strings.xml`; update the corresponding translation path when changing UI text.
- Background work in `MainActivity` uses dedicated single-thread executors for general I/O, persistence, and PDF rendering; post UI changes back to the main thread. Music and PDF lists reveal rows in batches of 40.
- When changing reminder data or lifecycle behavior, account for alarm rescheduling after boot and app replacement in `ReminderReceiver`/`ReminderScheduler`, not only the screen that edits reminders.
