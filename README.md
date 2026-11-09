<div align="center">
  <img src="docs/logo.png" alt="Recora app icon" width="128" />
  <h1>Recora</h1>
  <p><b>Screen recorder for Android with a Material 3 Expressive interface</b></p>
  <p>
    <a href="https://github.com/Hong-Duc-Dev-Labs/recora/releases/latest"><img src="https://img.shields.io/github/v/release/Hong-Duc-Dev-Labs/recora?style=for-the-badge&color=E0577F" alt="Latest release" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-8E2C4E?style=for-the-badge" alt="MIT license" /></a>
    <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 10 or newer" />
  </p>
</div>

---

## ✨ Features

- 🎥 Screen recording with optional microphone audio, pause and resume, and a start countdown
- 🎛️ Floating control bar that you can drag anywhere, with a drawing overlay and a front-camera overlay
- ⚡ Quick Settings tile that starts and stops recording without opening the app
- 🎞️ Library with a preview, recording time, and swipe-to-reveal share and delete

The interface is available in English and Vietnamese and follows the device language. Android 13 and newer also list both under Settings > Apps > Recora > Language.

## 📥 Download

Get the latest APK from the [Releases](https://github.com/Hong-Duc-Dev-Labs/recora/releases/latest) page and open it on a device running Android 10 (API 29) or newer.

## 🛠️ Build and install

Requires JDK 17 and the Android SDK. The Gradle wrapper is included.

```bash
./gradlew installDebug      # build and install on the connected device
./gradlew lintDebug         # static checks
```

Windows uses `gradlew.bat`. `gradle.properties` pins a JDK path for the original author's machine, so
change or remove `org.gradle.java.home` before building elsewhere.

## 🔐 Permissions the app asks for

Screen capture (a system dialog on every recording), microphone, notifications, camera for the facecam,
and "display over other apps" for the floating bar.

## 📚 More

Why the code is shaped the way it is: [docs/system-architecture.md](docs/system-architecture.md).

## 📄 License

[MIT](LICENSE)
