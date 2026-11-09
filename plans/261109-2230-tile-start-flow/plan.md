# Plan: Improve the Quick Settings tile start flow

- **Mode**: `--improve` (adapt ideas from muhammadhaseebiqbal-dev/Screen-Recorder, MIT, branch `master`)
- **Source files studied**: `MediaProjectionPermissionActivity`, `TileRecordingPermissionActivity`, `ScreenRecordTileService`, `activity_countdown.xml`, manifest entries for the tile activity
- **Local files**: `RecordingStartActivity.kt`, `RecordingTileService.kt`, `AndroidManifest.xml`, `themes.xml`
- **Status**: Implemented and checked on a Galaxy device (Android 16)
- **Deviation**: if the user leaves during the countdown the start is cancelled with a message, instead of being deferred until the activity is resumed, because the activity is hidden from Recents and would otherwise wait forever.
- **Added after testing**: the recording service now declares the `microphone` foreground type; without it the log showed `App op 27 missing, silencing record` and tile-started videos had no sound.
- **Risk**: Low to medium (lifecycle timing around foreground service start)

## Outcome

Starting a recording from the tile feels like a native feature: a visible countdown, no crash, no silent failure, and nothing left in Recents.

## Findings (source versus local)

| Aspect | Source | Local now | Decision |
| --- | --- | --- | --- |
| Countdown | Large number on a transparent screen; hidden (alpha 0) before capture starts so it never appears in the video | One Toast per second, which can lag, queue and land in the first frames | Replace Toasts with a Compose number overlay, hide it before starting the service |
| Starting the foreground service | Only when the activity is resumed; otherwise defers and brings the task to front (avoids the Android 14+ background-start crash) | Starts after the delay even if the user pressed Home during the countdown | Start only while resumed; if not resumed, defer until `onResume` |
| Cancel during countdown | Not handled | Not handled | Cancel the countdown if the user presses Back or the activity is destroyed without a start |
| Microphone permission denied | Re-validates and keeps asking | Continues and starts with `MIC`, so the recorder fails silently | If still denied, show a message and record with `AudioSourceType.MUTE` |
| Overlay permission missing | Opens the overlay settings page | Toast, records without the floating bar | Keep the Toast, which is less disruptive for a tile tap |
| Task cleanup | `singleTask`, own task affinity, `autoRemoveFromRecents`, `finishAndRemoveTask()` | `singleInstance`, empty affinity, `excludeFromRecents` | Add `autoRemoveFromRecents`; call `finishAndRemoveTask()` |
| Tile appearance | Stop icon while recording | Same camera icon in both states | Add `ic_tile_stop` and switch icon by state |
| Duplicated permission and countdown code | Subclass shares one base activity | `MainActivity` and `RecordingStartActivity` each have their own | Out of scope for this plan; note as follow-up |

## Steps

1. **Countdown UI** (`RecordingStartActivity.kt`): use `ComponentActivity.setContent` with the existing Recora theme and a centered number; remove the touch-through flag only if the overlay should block input (decision below). Drive it with a `mutableIntStateOf`.
2. **Safe service start**: keep the projection result in fields. When the countdown ends, set the number to hidden, wait one frame, then start the service if the lifecycle is at least `RESUMED`; otherwise store a pending flag and start in `onResume`.
3. **Cancellation**: hold the countdown `Job`; cancel it in `onDestroy` and on Back. Do not start a recording if cancelled.
4. **Mic fallback**: after the permission result, if `RECORD_AUDIO` is denied and the audio source includes the mic, pass `config.copy(audioSource = MUTE)` and show one Toast.
5. **Manifest** (`AndroidManifest.xml`): add `android:autoRemoveFromRecents="true"` to `RecordingStartActivity`; call `finishAndRemoveTask()` instead of `finish()` when it is the task root.
6. **Tile icon** (`RecordingTileService.kt`, new `res/drawable/ic_tile_stop.xml`): use the stop icon while recording or paused.

## Validation

- Build: `./gradlew assembleDebug`.
- On the device: tap the tile with a 3 second countdown and confirm the number is visible, absent from the recorded video, and the recording starts.
- Press Home during the countdown: confirm no crash and the recording still starts or is cleanly cancelled.
- Deny the microphone permission: confirm the recording still starts, muted, with a message.
- Confirm no extra entry remains in Recents after the recording starts.
- Tile shows the stop icon during recording and the camera icon after stopping.

## Rollback

All changes are in four files plus one new drawable; revert the commit. No data or schema changes.

## Open decision

Should the countdown overlay block touches (a visible full-screen number, so a stray tap cannot disturb the countdown) or let touches pass through as now? Default in this plan: block, matching the source.
