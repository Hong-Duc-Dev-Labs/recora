package labs.hongducdev.recora

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import labs.hongducdev.recora.model.AudioSourceType
import labs.hongducdev.recora.model.RecordingConfig
import labs.hongducdev.recora.service.RecordingController
import labs.hongducdev.recora.ui.theme.RecoraTheme
import labs.hongducdev.recora.utils.PreferenceManager

/**
 * Transparent host for the system screen-capture consent dialog and the countdown, used by the
 * Quick Settings tile so recording can start without bringing the app to the foreground.
 */
class RecordingStartActivity : ComponentActivity() {
    private lateinit var prefManager: PreferenceManager
    private val countdownNumber = mutableIntStateOf(0)
    private var started = false

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                runCountdownThenStart(result.resultCode, data)
            } else {
                Toast.makeText(this, getString(R.string.projection_cancelled), Toast.LENGTH_SHORT).show()
                close()
            }
        }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            requestProjection()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = PreferenceManager(this)

        setContent {
            RecoraTheme(pitchBlackTheme = prefManager.pitchBlackTheme) {
                if (countdownNumber.intValue > 0) CountdownDisplay(countdown = countdownNumber.intValue)
            }
        }

        // Back during the permission dialog or countdown cancels the start.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            },
        )

        if (savedInstanceState != null) return

        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (usesMic(prefManager.audioSource) && !isGranted(Manifest.permission.RECORD_AUDIO)) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }

        if (prefManager.enableFloatingControls && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.overlay_missing_toast), Toast.LENGTH_LONG).show()
        }

        if (missing.isNotEmpty()) permissionsLauncher.launch(missing.toTypedArray()) else requestProjection()
    }

    // Capturing the device's own audio needs the microphone permission as well.
    private fun usesMic(source: AudioSourceType) = source != AudioSourceType.MUTE

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    /** Without microphone access the recorder would fail silently, so record without audio instead. */
    private fun resolveConfig(): RecordingConfig {
        val config = prefManager.getRecordingConfig()
        if (usesMic(config.audioSource) && !isGranted(Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, getString(R.string.mic_missing_toast), Toast.LENGTH_LONG).show()
            return config.copy(audioSource = AudioSourceType.MUTE)
        }
        return config
    }

    private fun runCountdownThenStart(
        resultCode: Int,
        data: Intent,
    ) {
        val config = resolveConfig()
        lifecycleScope.launch {
            for (i in config.countdownSeconds downTo 1) {
                countdownNumber.intValue = i
                delay(1000)
            }
            // Hide the number first so it never appears in the first recorded frames.
            countdownNumber.intValue = 0
            delay(150)

            // A foreground service of type mediaProjection cannot be started safely from the
            // background, so if the user left during the countdown the start is cancelled.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                Toast.makeText(this@RecordingStartActivity, getString(R.string.start_cancelled_left), Toast.LENGTH_SHORT).show()
                close()
                return@launch
            }
            started = true
            RecordingController.startRecording(this@RecordingStartActivity, resultCode, data, config)
            close()
        }
    }

    private fun close() {
        if (isTaskRoot) finishAndRemoveTask() else finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!started) countdownNumber.intValue = 0
    }
}
