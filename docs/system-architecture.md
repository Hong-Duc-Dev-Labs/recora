# System architecture

Navigation and the reasons behind decisions that the code cannot explain by itself. Read the named files
for behavior.

## Where things live

Paths are relative to `app/src/main/java/labs/hongducdev/recora/`.

| Area | Owner |
| --- | --- |
| Recording lifecycle, notification, files | `service/ScreenRecordingService.kt` |
| Shared state and commands between UI and services | `service/RecordingController.kt` |
| Floating control bar | `service/FloatingControlService.kt` |
| Drawing and facecam overlays | `service/DrawingOverlayService.kt`, `service/FacecamOverlayService.kt` |
| Quick Settings tile and its start flow | `service/RecordingTileService.kt`, `RecordingStartActivity.kt` |
| Saved videos (MediaStore) | `utils/VideoStorageManager.kt` |
| Screens and shared UI | `MainActivity.kt`, `ui/` |

## Decisions

**There are two recording engines, chosen by the audio source.** `MediaRecorder` handles microphone-only and
silent recordings because it is far less code. It cannot capture the device's internal audio, so any source
that includes system audio switches to `CodecScreenRecorder` (`MediaCodec` plus `MediaMuxer`), which mixes
`AudioPlaybackCapture` with the optional microphone into one AAC track. Pause on that path drops samples
and shifts later timestamps by the paused time, because a codec has no pause. The audio source is still
fixed when recording starts, so there is no mid-recording mute. Capturing system audio also needs the
`RECORD_AUDIO` permission, and apps that opt out of playback capture stay silent. A comparison with another
open-source recorder is in `plans/reports/xia-261109-2230-screen-recorder-comparison.md`.

**State is a process-wide singleton, not persisted.** `RecordingController` holds `StateFlow`s that the
services and Compose UI observe. This works because every component runs in one process. A recording that
loses its process is gone; the video file is left unfinished (see Storage).

**The foreground service declares `mediaProjection|microphone`.** Without the microphone type Android
silences microphone capture once the app is in the background, and recordings always continue in the
background. The microphone type is requested at runtime only when the audio source uses the microphone and
the permission is granted, because declaring it without the permission throws.

**The tile cannot start recording by itself.** Screen-capture consent needs an activity, so the tile opens
`RecordingStartActivity`, a transparent activity in its own task that shows the system dialog and the
countdown without bringing the app forward. The consent dialog cannot be skipped. If the user leaves
during the countdown the start is cancelled, because a `mediaProjection` foreground service cannot be
started safely from the background.

**The floating bar is dragged by an intercepting container.** Compose consumes every touch that lands on
the pill, so a touch listener on the view never sees a drag. `DragContainer` in `FloatingControlService`
intercepts once movement passes the touch slop and leaves taps to the buttons. The lifecycle and saved-state
owners must be set on that container, because it is the window's root view and Compose looks them up there.

**Storage uses MediaStore under `Movies/Recora`.** New files are inserted pending and published when the
recording stops. A recording killed before it stops stays pending, so the gallery does not list it.
