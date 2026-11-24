# FaceLook Pause

> **Smart media play/pause controller powered by real-time ML Kit on-device gaze detection.**

An enterprise-grade, privacy-first Android application developed in modern Kotlin and Jetpack Compose/Views. Built with strict adherence to modern Android architecture, lifecycle awareness, and hardware sensor efficiency.

---

## 🚀 Key Features

- Real-time front camera face and eye gaze tracking using ML Kit Face Detection & CameraX.
- **Auto-Pause on Look-Away**: Instantly pauses video/audio when your head turns away for >1.5s.
- **Auto-Resume**: Seamlessly resumes playback the moment you look back at your screen.
- Dispatches standard MediaSession hardware key events (`KEYCODE_MEDIA_PLAY_PAUSE`).
- 100% on-device ML inference — zero frames or images ever leave memory or touch disk.

---

## 🛠️ Hardware & Sensor Architecture

* **Sensors & APIs:** `Front Camera (CameraX), ML Kit Face Mesh, AudioManager / MediaSession`
* **Declared Permissions:** `CAMERA, FOREGROUND_SERVICE, POST_NOTIFICATIONS`
* **Target SDK:** Android 14 (API 34)
* **Minimum SDK:** Android 8.0 (API 26+)
* **Architecture:** MVVM / Unidirectional Data Flow (UDF) with Coroutines & StateFlow

---

## 🔒 Privacy & Permission Transparency

This application is engineered under the **Zero Unnecessary Permissions** doctrine:
* **No Hidden Trackers:** Contains zero advertising SDKs or third-party behavioral analytics.
* **No Accessibility Privilege Misuse:** All background operations utilize sanctioned Android APIs (`WorkManager`, `ForegroundService`, `JobScheduler`).
* **Air-Gapped Operation:** Runs locally on your device hardware without requiring external cloud servers.

---

## 📦 Building and Running

### Prerequisites
* Android Studio Iguana / Jellyfish / Ladybug or newer
* Android SDK 34
* JDK 17+

### Build APK via CLI
```bash
# Clone the repository
git clone https://github.com/naik2571/FaceLook-Pause.git
cd FaceLook-Pause

# Build Debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Licensed under the [Apache License 2.0](LICENSE).
