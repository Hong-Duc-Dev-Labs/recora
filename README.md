# Recora

Screen recorder for Android with a Material 3 Expressive interface.

- Records the screen with optional microphone audio, pause and resume, and a start countdown.
- Floating control bar over other apps with pause, stop, a drawing overlay, and a front-camera overlay.
- Starts and stops from a Quick Settings tile without opening the app.
- Library of recordings with a preview, recording time, and swipe-to-reveal share and delete.

The interface text is Vietnamese.

## Requirements

- Android 10 (API 29) or newer on the device.
- JDK 17 and the Android SDK to build. The Gradle wrapper is included.

## Build and install

```bash
./gradlew installDebug      # build and install on the connected device
./gradlew lintDebug         # static checks
```

Windows uses `gradlew.bat`. `gradle.properties` pins a JDK path for the original author's machine, so
change or remove `org.gradle.java.home` before building elsewhere.

## Permissions the app asks for

Screen capture (a system dialog on every recording), microphone, notifications, camera for the facecam,
and "display over other apps" for the floating bar.

## More

Why the code is shaped the way it is: [docs/system-architecture.md](docs/system-architecture.md).

## License

[MIT](LICENSE)
