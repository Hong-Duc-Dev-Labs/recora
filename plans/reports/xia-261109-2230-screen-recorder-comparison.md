# Feature Comparison: Screen Recorder (muhammadhaseebiqbal-dev/Screen-Recorder)

Mode: `--compare` (analysis only, no code changed).
Source: https://github.com/muhammadhaseebiqbal-dev/Screen-Recorder, branch `master`, MIT license, last push 2026-10-18, 171 stars.
Source stack: Kotlin with Views/XML + Material Components, minSdk 26. Local stack: Kotlin with Jetpack Compose + Material 3 Expressive, minSdk 29.
Only repository metadata, README, CHANGELOG, manifest and selected sources were read; nothing was executed. Copying code requires keeping the MIT notice.

## Head-to-head

| Aspect | Source | Local (Recora) | Recommendation |
| --- | --- | --- | --- |
| Encoding | `MediaCodec` + `MediaMuxer`, H.264 or H.265, VBR | `MediaRecorder`, H.264 | Keep `MediaRecorder` until a feature needs more (see audio) |
| Audio | Mic and internal audio (`AudioPlaybackCapture`) mixed, limiter, live mute | Mic only, fixed at start | Internal audio and mute are the main gaps; both need a `MediaCodec` audio path |
| Pause | Detaches the virtual display surface; works on every API | `MediaRecorder.pause()` | Local is fine (minSdk 29) |
| Foreground service types | `mediaProjection\|microphone`, separate `camera` service | `mediaProjection` only; camera overlay is not a foreground service | Fix in Recora, see "Likely bugs" |
| Quick Settings tile | Transparent permission activity in its own task, `singleTask`, `excludeFromRecents`, `finishAndRemoveTask` | Same idea now (own task, transparent) | Matches; add `singleTask` and `finishAndRemoveTask` |
| Tile state | Broadcast on state change, stop/start labels and icons | StateFlow collection, shows timer | Local is richer; swap icon to a stop icon while recording |
| State model | `RecordingStateManager` singleton with pause-aware elapsed time | `RecordingController` StateFlow, `durationSeconds` ticked by a coroutine | Local ticker drifts and loses time if paused near a tick; consider a clock-based elapsed time |
| Rotation | Auto follows size changes via `onCapturedContentResize` | Fixed resolution | Optional; only matters for landscape apps |
| Power handling | Partial wake lock; optional pause on screen off | None | Add wake lock at least |
| Privileged extras | Shizuku/root: Show Taps, immersive, quieter permission flow | None | Skip, high complexity and trust cost |
| Storage | Custom storage location (SAF) | MediaStore `Movies/Recora` only | Optional |
| i18n | 12 languages | Hardcoded Vietnamese strings | Move strings to `strings.xml` before adding languages |
| Tests | Unit tests for pure logic (audio pump, limiter, frame layout, overlay bounds) | None | Add tests when pure logic appears |

## Likely bugs in Recora that the comparison exposed

1. **Camera overlay is not a foreground service.** The source's changelog (#25) fixed blank or frozen floating camera previews by "keeping camera access in a foreground service" with `foregroundServiceType="camera"`. `FacecamOverlayService` calls CameraX but never `startForeground` and the manifest has no camera type, so the facecam preview can go blank while the app is in the background. Not yet reproduced on the device.
2. **Microphone from a background service.** The source declares `FOREGROUND_SERVICE_MICROPHONE` and `mediaProjection|microphone`. Recora declares neither and records the mic from a `mediaProjection`-only service after the app moves to the background (`moveTaskToBack`, now also the tile flow). On Android 14+ this can yield silent audio. Not yet reproduced.
3. **Audio stalls.** Source #21 fixed intermittent audio capture stalls by draining PCM continuously, including while paused. Only relevant if Recora moves to `AudioRecord`.

## Worth learning (ranked by value for Recora)

1. Add the two missing foreground service types (items 1 and 2 above). Small manifest and service change, likely fixes real behavior.
2. Wake lock during recording, so the CPU stays awake with the screen off.
3. Tile polish: stop icon while recording, `singleTask` and `finishAndRemoveTask()` on the permission activity.
4. Pause-aware elapsed time from a monotonic clock instead of counting ticks.
5. A mic mute button and internal audio would need the `MediaCodec` path: `AudioRecord` for mic, `AudioPlaybackCaptureConfiguration` for system audio, a mixer with a limiter. This is the single large feature the source has that Recora does not, and it is a rewrite of the recording core, not a patch.

## Not worth taking

- Shizuku and root integration (complexity, trust, narrow audience).
- Views/XML and Material Components UI, which Recora already replaces with Compose.
- Donation and About screens, and the `INTERNET` and `WRITE_SETTINGS` permissions.

## Unresolved questions

- Do you want internal audio and mic mute? It decides whether the recording core moves from `MediaRecorder` to `MediaCodec`.
- Should the two likely bugs be reproduced on the device first, or fixed directly?
