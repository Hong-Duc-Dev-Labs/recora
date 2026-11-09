package labs.hongducdev.recora.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import labs.hongducdev.recora.MainActivity
import labs.hongducdev.recora.R
import labs.hongducdev.recora.RecoraApplication
import labs.hongducdev.recora.model.AudioSourceType
import labs.hongducdev.recora.model.RecordingConfig
import labs.hongducdev.recora.model.RecordingSessionState
import labs.hongducdev.recora.model.RecordingStatus
import labs.hongducdev.recora.utils.VideoStorageManager

class ScreenRecordingService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputUri: Uri? = null
    private var pfd: ParcelFileDescriptor? = null

    private lateinit var storageManager: VideoStorageManager
    private var durationSeconds: Long = 0

    override fun onCreate() {
        super.onCreate()
        storageManager = VideoStorageManager(this)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_DATA)
                    }

                if (resultCode != 0 && data != null) {
                    startRecordingInternal(resultCode, data)
                }
            }

            ACTION_PAUSE -> {
                pauseRecording()
            }

            ACTION_RESUME -> {
                resumeRecording()
            }

            ACTION_STOP -> {
                stopRecording()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecordingInternal(
        resultCode: Int,
        data: Intent,
    ) {
        val config = RecordingController.config.value
        startForegroundNotification("00:00", isPaused = false)

        try {
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)

            outputUri = storageManager.createVideoOutputUri()
            if (outputUri == null) {
                stopSelf()
                return
            }

            pfd = contentResolver.openFileDescriptor(outputUri!!, "rw")
            if (pfd == null) {
                stopSelf()
                return
            }

            setupMediaRecorder(config, pfd!!)
            setupVirtualDisplay(config)

            mediaRecorder?.start()

            durationSeconds = 0
            RecordingController.updateSessionState(
                RecordingSessionState(
                    status = RecordingStatus.RECORDING,
                    durationSeconds = 0,
                    outputUriString = outputUri.toString(),
                ),
            )

            startTimer()

            if (config.enableFloatingControls) {
                val floatIntent = Intent(this, FloatingControlService::class.java)
                startService(floatIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            stopSelf()
        }
    }

    private fun setupMediaRecorder(
        config: RecordingConfig,
        pfd: ParcelFileDescriptor,
    ) {
        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

        if (config.audioSource == AudioSourceType.MIC) {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        }

        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (config.audioSource == AudioSourceType.MIC) {
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(128_000)
        }

        recorder.setVideoSize(config.resolutionWidth, config.resolutionHeight)
        recorder.setVideoFrameRate(config.frameRate)
        recorder.setVideoEncodingBitRate(config.bitRate)

        recorder.setOutputFile(pfd.fileDescriptor)
        recorder.prepare()
        mediaRecorder = recorder
    }

    private fun setupVirtualDisplay(config: RecordingConfig) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        mediaProjection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    stopRecording()
                }
            },
            null,
        )

        virtualDisplay =
            mediaProjection?.createVirtualDisplay(
                "RecoraVirtualDisplay",
                config.resolutionWidth,
                config.resolutionHeight,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null,
            )
    }

    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                timerJob?.cancel()
                val current = RecordingController.sessionState.value
                RecordingController.updateSessionState(current.copy(status = RecordingStatus.PAUSED))
                startForegroundNotification(current.formattedDuration, isPaused = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                val current = RecordingController.sessionState.value
                RecordingController.updateSessionState(current.copy(status = RecordingStatus.RECORDING))
                startForegroundNotification(current.formattedDuration, isPaused = false)
                startTimer()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        timerJob?.cancel()

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
            // Ignore if recording was too short
        }

        outputUri?.let { uri ->
            storageManager.completeVideoOutputUri(uri)
        }

        RecordingController.updateSessionState(
            RecordingSessionState(
                status = RecordingStatus.STOPPED,
                durationSeconds = durationSeconds,
                outputUriString = outputUri?.toString(),
            ),
        )

        // Stop overlay services
        stopService(Intent(this, FloatingControlService::class.java))
        stopService(Intent(this, DrawingOverlayService::class.java))
        stopService(Intent(this, FacecamOverlayService::class.java))

        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        try {
            pfd?.close()
        } catch (_: Exception) {
        }
        pfd = null
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob =
            serviceScope.launch {
                while (isActive) {
                    delay(1000)
                    durationSeconds++
                    val updated = RecordingController.sessionState.value.copy(durationSeconds = durationSeconds)
                    RecordingController.updateSessionState(updated)
                    startForegroundNotification(updated.formattedDuration, isPaused = false)
                }
            }
    }

    private fun startForegroundNotification(
        timeText: String,
        isPaused: Boolean,
    ) {
        val appIntent = Intent(this, MainActivity::class.java)
        val appPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val stopIntent =
            Intent(this, ScreenRecordingService::class.java).apply {
                action = ACTION_STOP
            }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val pauseResumeAction =
            if (isPaused) {
                val resumeIntent =
                    Intent(this, ScreenRecordingService::class.java).apply {
                        action = ACTION_RESUME
                    }
                val resumePendingIntent =
                    PendingIntent.getService(
                        this,
                        2,
                        resumeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                NotificationCompat.Action
                    .Builder(
                        android.R.drawable.ic_media_play,
                        "Tiếp tục",
                        resumePendingIntent,
                    ).build()
            } else {
                val pauseIntent =
                    Intent(this, ScreenRecordingService::class.java).apply {
                        action = ACTION_PAUSE
                    }
                val pausePendingIntent =
                    PendingIntent.getService(
                        this,
                        3,
                        pauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                NotificationCompat.Action
                    .Builder(
                        android.R.drawable.ic_media_pause,
                        "Tạm dừng",
                        pausePendingIntent,
                    ).build()
            }

        val notification =
            NotificationCompat
                .Builder(this, RecoraApplication.CHANNEL_RECORDING)
                .setContentTitle("Đang quay màn hình [$timeText]")
                .setContentText(if (isPaused) "Đang tạm dừng" else "Chạm để mở ứng dụng Recora")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(appPendingIntent)
                .setOngoing(true)
                .addAction(pauseResumeAction)
                .addAction(
                    android.R.drawable.ic_media_next,
                    "Dừng",
                    stopPendingIntent,
                ).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "action_start_recording"
        const val ACTION_PAUSE = "action_pause_recording"
        const val ACTION_RESUME = "action_resume_recording"
        const val ACTION_STOP = "action_stop_recording"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        const val NOTIFICATION_ID = 1001
    }
}
