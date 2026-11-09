package labs.hongducdev.recora.service

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import labs.hongducdev.recora.model.RecordingConfig
import labs.hongducdev.recora.model.RecordingSessionState
import labs.hongducdev.recora.model.RecordingStatus

object RecordingController {
    private val _sessionState = MutableStateFlow(RecordingSessionState())
    val sessionState: StateFlow<RecordingSessionState> = _sessionState.asStateFlow()

    private val _config = MutableStateFlow(RecordingConfig())
    val config: StateFlow<RecordingConfig> = _config.asStateFlow()

    // Flag for drawing tool active state
    val isDrawingOverlayActive = MutableStateFlow(false)

    // Flag for facecam active state
    val isFacecamActive = MutableStateFlow(false)

    fun updateSessionState(state: RecordingSessionState) {
        _sessionState.value = state
    }

    fun updateConfig(newConfig: RecordingConfig) {
        _config.value = newConfig
    }

    fun startRecording(
        context: Context,
        resultCode: Int,
        data: Intent,
        config: RecordingConfig,
    ) {
        _config.value = config
        val intent =
            Intent(context, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_START
                putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenRecordingService.EXTRA_DATA, data)
            }
        context.startForegroundService(intent)
    }

    fun pauseRecording(context: Context) {
        val intent =
            Intent(context, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_PAUSE
            }
        context.startService(intent)
    }

    fun resumeRecording(context: Context) {
        val intent =
            Intent(context, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_RESUME
            }
        context.startService(intent)
    }

    fun stopRecording(context: Context) {
        val intent =
            Intent(context, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_STOP
            }
        context.startService(intent)
    }

    fun toggleDrawing(context: Context) {
        val newState = !isDrawingOverlayActive.value
        isDrawingOverlayActive.value = newState
        val intent =
            Intent(context, DrawingOverlayService::class.java).apply {
                action = if (newState) DrawingOverlayService.ACTION_SHOW else DrawingOverlayService.ACTION_HIDE
            }
        context.startService(intent)
    }

    fun toggleFacecam(context: Context) {
        val newState = !isFacecamActive.value
        isFacecamActive.value = newState
        val intent =
            Intent(context, FacecamOverlayService::class.java).apply {
                action = if (newState) FacecamOverlayService.ACTION_SHOW else FacecamOverlayService.ACTION_HIDE
            }
        context.startService(intent)
    }
}
