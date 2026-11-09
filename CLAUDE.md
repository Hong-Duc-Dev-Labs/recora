# Recora

- Build and install: `./gradlew.bat installDebug` on Windows. Static checks: `./gradlew.bat lintDebug`.
- `lintDebug` currently fails on two older findings (`gradle.properties` JDK path escape, unused `Scaffold` padding in `AppShell.kt`). New lint errors are regressions.
- `gradle.properties` holds a machine-specific `org.gradle.java.home`. Do not copy it into other environments or CI.
- There is no automated test suite. Verify behavior on a real device with `adb`; the Quick Settings tile only receives `cmd statusbar click-tile` while the panel is open.
- Interface strings are Vietnamese and written directly in Kotlin. Keep new user-facing text Vietnamese.
- Do not reinstall the app while a recording runs; it leaves an unfinished `.pending-*` file in `Movies/Recora` that the gallery hides.
- Before changing the recording core, foreground service types, the tile start flow, or overlay touch handling, read `docs/system-architecture.md`. Each has a reason that is not visible in the code.
