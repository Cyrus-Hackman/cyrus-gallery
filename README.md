# Cyrus Gallery

A sleek, private, and unified Android gallery app combining on-device MediaStore photos and videos with Microsoft OneDrive cloud storage in a seamless chronological timeline. Built with 100% Kotlin, Jetpack Compose, Material 3, Hilt, Room, and Microsoft Graph.

---

## ✨ Features

- **Unified Chronological Timeline**: Merges local device media with cloud-stored OneDrive photos and videos into a single chronological feed grouped by month.
- **Full OneDrive Synchronization**:
  - Incremental, paged delta syncing using Microsoft Graph API that drains all pages in a single operation.
  - Progressive updates: photos and videos appear in real time as pages stream in.
  - Room metadata persistence: cloud items remain instantly available upon app restart without re-downloading.
- **Intelligent Fast Thumbnail Engine**:
  - Powered by Coil with custom `VideoFrameDecoder` for instant local video thumbnail generation.
  - Authenticated OkHttp interceptor dynamically attaches Microsoft OAuth Bearer tokens for secure Graph thumbnail fetching and on-disk caching.
  - Automatic downsampling (`360×360`) prevents large camera originals (12–48 MP) from causing stutter or memory bloat.
- **Immersive Media Viewer**:
  - Horizontal swipe pagination (`HorizontalPager`) across the entire photo and video library.
  - Pinch-to-zoom (up to 5x), pan gestures, and double-tap zoom/reset.
  - Embedded ExoPlayer for seamless HD/4K video playback.
  - EXIF metadata bottom sheet (camera model, aperture, shutter speed, ISO, focal length, resolution, and timestamp).
  - 3-way safe deletion dialog (Local Device / Cloud Cache Only / Permanent OneDrive).
- **Smooth Navigation & Scroll Retention**:
  - Overlay-based viewer architecture preserves the exact scroll position in the gallery grid upon returning.
  - Full Android system back-button and gesture support (`BackHandler`) across the viewer, album drill-down, and search.
- **Albums & Offline Favorites**:
  - Dynamic local BUCKET grouping and OneDrive folder discovery with in-app drill-down navigation.
  - Persistent offline favorites powered by Room database.
- **Granular Android Permissions**:
  - Full compatibility from Android 8.0 (API 26) up to Android 14+ (API 34+), including `READ_MEDIA_VISUAL_USER_SELECTED` partial access.

---

## 🏗 Architecture & Tech Stack

```
com.cyrus.gallery
├── data
│   ├── auth          # OneDrive MSAL Single-Account Auth & token management
│   ├── cache         # App-private managed storage, LRU cache eviction, file pinning
│   ├── database      # Room DB, DAOs, and entities (CloudMediaCacheEntity, FavoriteMediaEntity)
│   ├── remote.graph  # Microsoft Graph REST client (Delta sync, paged children, upload, delete)
│   ├── repository    # UnifiedGalleryRepository, LocalGalleryRepository, OneDriveRepository
│   └── sync          # WorkManager background sync workers (Hilt-assisted)
├── domain
│   ├── model         # MediaItem, Album, MediaGroup, CloudCachePolicy
│   └── repository    # GalleryRepository & CloudCacheRepository interfaces
└── ui
    ├── albums        # Albums grid & drill-down album view
    ├── components    # Reusable Compose components, empty states
    ├── favourites    # Starred media screen backed by Room
    ├── gallery       # Photos chronological grid, live search bar, thumbnail rendering
    ├── navigation    # Navigation destinations & bottom bar
    ├── onedrive      # Microsoft sign-in, sync status, and cloud items screen
    ├── permissions   # Accompanist permission gates (API 26 through 34+)
    ├── settings      # Cache policies, storage breakdown, manual eviction
    ├── theme         # Material 3 dark/light dynamic theme
    └── viewer        # Fullscreen HorizontalPager, ExoPlayer, Zoomable image, EXIF sheet
```

### Key Technologies
* **UI**: Jetpack Compose, Material 3, Navigation Compose
* **Dependency Injection**: Dagger Hilt 2.52 with Hilt Navigation Compose
* **Local Persistence**: Room 2.6.1 with KSP
* **Image & Video Loading**: Coil 2.7.0 (Compose + Video extension)
* **Cloud Auth & API**: Microsoft Authentication Library (MSAL) 5.4.0 + OkHttp 4.12.0
* **Media Playback & EXIF**: Jetpack Media3 (ExoPlayer 1.5.0) + ExifInterface 1.3.7
* **Background Processing**: AndroidX WorkManager 2.9.1 with HiltWorker

---

## ☁️ Cloud Integration

Cyrus Gallery connects securely with Microsoft OneDrive using the official Microsoft Authentication Library (MSAL) and Microsoft Graph REST API. All authentication flows run through Microsoft's secure OAuth 2.0 broker with user-consented delegated scopes (`User.Read`, `Files.Read`).

For developer setup and Entra ID configuration details, refer to the private [Microsoft Entra Setup Guide](docs/entra-app-registration.md).

---

## 🛠 Building the App

### Prerequisites
- JDK 17 (set in `JAVA_HOME`)
- Android SDK Platform 35 (`compileSdk = 35`, `minSdk = 26`)

### Build Commands (PowerShell)

Set your Java 17 path:
```powershell
$env:JAVA_HOME = "C:\Users\USER\Desktop\Cyrus-Gallery\.tooling\jdk17\jdk-17.0.21+9"
```

Run unit tests:
```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Build the debug APK:
```powershell
.\gradlew.bat :app:assembleDebug
```

The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

Install directly to a connected device or emulator:
```powershell
.\gradlew.bat :app:installDebug
# or
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Unit Testing

The project includes unit test coverage across data and domain layers:
- `OneDriveGraphClientTest`: Validates JSON parsing of Graph items and delta change separation.
- `UnifiedGalleryRepositoryTest`: Tests deduplication of local vs cloud items and chronological merging.
- `MediaGroupTest`: Verifies monthly grouping algorithms.
- `CloudCachePolicyTest`: Tests LRU eviction and pinning calculations.
- `DeleteTargetTest`: Tests local, cache-only, and permanent deletion paths.
- `FavoriteMediaEntityTest`: Tests Room favorite entity state.

Run all tests via:
```powershell
.\gradlew.bat test
```
