# Recora

- Build and install: `./gradlew.bat installDebug` on Windows. Static checks: `./gradlew.bat lintDebug`.
- `lintDebug` currently fails on two older findings (`gradle.properties` JDK path escape, unused `Scaffold` padding in `AppShell.kt`). New lint errors are regressions.
- `gradle.properties` holds a machine-specific `org.gradle.java.home`. Do not copy it into other environments or CI.
- There is no automated test suite. Verify behavior on a real device with `adb`; the Quick Settings tile only receives `cmd statusbar click-tile` while the panel is open.
- User-facing text lives in `res/values/strings.xml` (English, the default) and `res/values-vi/strings.xml` (Vietnamese). Add every new string to both files and to `res/xml/locales_config.xml` when adding a language; never hardcode text in Kotlin.
- Do not reinstall the app while a recording runs; it leaves an unfinished `.pending-*` file in `Movies/Recora` that the gallery hides.
- Before changing the recording core, foreground service types, the tile start flow, or overlay touch handling, read `docs/system-architecture.md`. Each has a reason that is not visible in the code.
