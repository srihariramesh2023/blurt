# Blurt

Blurt is a native Android application built with Kotlin and Jetpack Compose.

## Project status

Initial scaffolding: a minimal, production-ready project that builds a debug APK
from the command line (no Android Studio required) and via GitHub Actions.

## Requirements

- JDK 17
- Android SDK (platform 35, build-tools 35.0.0). The build locates it via
  `local.properties` (`sdk.dir=...`) or the `ANDROID_HOME` environment variable.

## Build

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## CI

A GitHub Actions workflow (`.github/workflows/build.yml`) builds the debug APK
on every push to `main` and on pull requests, and uploads it as a build
artifact.
