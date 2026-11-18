# Cyrus Gallery

Native Android gallery for a unified local and personal OneDrive media library, built with Kotlin and Jetpack Compose.

## Implementation status

The repository contains working foundations and feature implementations, but it is **not yet production-ready**. The statuses below describe code present in the repository; they do not imply that device testing or release validation has passed.

| Phase | Status | Notes |
| --- | --- | --- |
| 1. Foundation | Implemented; current build/run not verified | Compose app, navigation, themes, Gradle configuration, and Hilt setup are present. |
| 2. Local gallery | Partial | Media permissions, including Android 14+ selected-photo access, MediaStore discovery and observation, albums, chronological grouping, and thumbnails are implemented. Device validation remains; MediaStore queries on Android 11+ are currently limited to 5,000 items per collection. |
| 3. OneDrive | Partial; requires configuration | MSAL sign-in/session code, Graph requests, folder browsing, and delta support are present. Configure a real Entra app registration and credentials using [the setup guide](docs/entra-app-registration.md); the checked-in client ID is a placeholder. Sync does not yet persist its delta state or fully drain all result pages. |
| 4. Cache and unified library | Partial | Room cache records, private storage, WorkManager downloads, pinning, cache clearing, eviction logic, and local/cloud merging are present. Eviction is not scheduled, cloud metadata is not restored from Room on app restart, and the unified timeline is not paged. |
| 5. Gallery features | Partial | Viewer, image zoom, video playback, metadata, favorites, search, download-on-demand, uploads, and separately confirmed delete actions are present. Remote-only videos show a thumbnail until downloaded; uploads currently read the whole file into memory. |
| 6. Production readiness | Incomplete | JVM unit tests exist, but there are no Android instrumentation tests. Room/UI integration testing, performance profiling, Samsung-device validation, and production release signing remain. The current release build uses the debug signing configuration and is not suitable for distribution. |

OneDrive originals are intended to remain separate from local device media and to use app-private managed storage. See [the cache architecture](docs/cloud-cache-architecture.md).

## Requirements

- JDK 17
- Android SDK Platform 35 and matching build tools
- Android Studio Ladybug or newer (optional if using the Gradle wrapper)

OneDrive sign-in also requires an Entra app registration and a configured MSAL resource file. See [the Entra registration guide](docs/entra-app-registration.md).

## Build and test

From the repository root in PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

The release variant currently uses debug signing. Do not distribute it as a production build; configure a release keystore and complete the remaining production-readiness work first.
