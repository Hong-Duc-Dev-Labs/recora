package labs.hongducdev.recora

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import labs.hongducdev.recora.model.AudioSourceType
import labs.hongducdev.recora.service.RecordingController
import labs.hongducdev.recora.utils.PreferenceManager

/**
 * Invisible host for the system screen-capture consent dialog, used by the Quick Settings tile
 * so recording can start without bringing the app to the foreground.
 */
class RecordingStartActivity : ComponentActivity() {
    private lateinit var prefManager: PreferenceManager

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                startAfterCountdown(result.resultCode, data)
            } else {
                Toast.makeText(this, "Đã hủy cấp quyền quay màn hình", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            requestProjection()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never swallow touches meant for the app underneath while the countdown runs.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        prefManager = PreferenceManager(this)
        RecordingController.updateConfig(prefManager.getRecordingConfig())
        if (savedInstanceState != null) return

        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (prefManager.audioSource == AudioSourceType.MIC && !isGranted(Manifest.permission.RECORD_AUDIO)) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }

        if (prefManager.enableFloatingControls && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Chưa cấp quyền hiển thị trên ứng dụng khác nên sẽ không có thanh điều khiển nổi", Toast.LENGTH_LONG).show()
        }

        if (missing.isNotEmpty()) permissionsLauncher.launch(missing.toTypedArray()) else requestProjection()
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startAfterCountdown(
        resultCode: Int,
        data: Intent,
    ) {
        val config = prefManager.getRecordingConfig()
        lifecycleScope.launch {
            for (i in config.countdownSeconds downTo 1) {
                Toast.makeText(this@RecordingStartActivity, i.toString(), Toast.LENGTH_SHORT).show()
                delay(1000)
            }
            RecordingController.startRecording(this@RecordingStartActivity, resultCode, data, config)
            finish()
        }
    }
}
