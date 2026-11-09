package labs.hongducdev.recora

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import labs.hongducdev.recora.model.AudioSourceType
import labs.hongducdev.recora.model.RecordingStatus
import labs.hongducdev.recora.service.RecordingController
import labs.hongducdev.recora.ui.app.EssentialsAppShell
import labs.hongducdev.recora.ui.app.ShellTab
import labs.hongducdev.recora.ui.screens.GalleryScreen
import labs.hongducdev.recora.ui.screens.RecordScreen
import labs.hongducdev.recora.ui.screens.SettingsScreen
import labs.hongducdev.recora.ui.theme.RecoraTheme
import labs.hongducdev.recora.utils.HapticUtil
import labs.hongducdev.recora.utils.PreferenceManager

class MainActivity : ComponentActivity() {
    private lateinit var prefManager: PreferenceManager
    private var pendingResultCode: Int = 0
    private var pendingResultData: Intent? = null

    private var countdownNumber = mutableIntStateOf(0)
    private var isCountingDown = mutableStateOf(false)
    private var showOverlayPermissionDialog = mutableStateOf(false)

    // Activity Result Launcher for MediaProjection
    private val projectionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                handleProjectionSuccess(result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, "Đã hủy cấp quyền quay màn hình", Toast.LENGTH_SHORT).show()
            }
        }

    // Permission Launcher for Audio & Notifications & Camera
    private val permissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { _ ->
            startCaptureRequest()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefManager = PreferenceManager(this)
        HapticUtil.isAppHapticsEnabled.value = prefManager.appHapticsEnabled
        RecordingController.updateConfig(prefManager.getRecordingConfig())

        setContent {
            val pitchBlackState = remember { mutableStateOf(prefManager.pitchBlackTheme) }

            RecoraTheme(pitchBlackTheme = pitchBlackState.value) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val tabs =
                        listOf(
                            ShellTab(
                                title = "Ghi hình",
                                icon = Icons.Rounded.Videocam,
                            ) { contentPadding ->
                                RecordScreen(
                                    contentPadding = contentPadding,
                                    onStartRecordingClick = { checkPermissionsAndStart() },
                                )
                            },
                            ShellTab(
                                title = "Thư viện",
                                icon = Icons.Rounded.VideoLibrary,
                            ) { contentPadding ->
                                GalleryScreen(contentPadding = contentPadding)
                            },
                            ShellTab(
                                title = "Cài đặt",
                                icon = Icons.Rounded.Settings,
                            ) { contentPadding ->
                                SettingsScreen(
                                    contentPadding = contentPadding,
                                    onPitchBlackChange = { pitchBlackState.value = it },
                                )
                            },
                        )

                    EssentialsAppShell(tabs = tabs)

                    // Countdown Overlay
                    if (isCountingDown.value) {
                        CountdownDisplay(countdown = countdownNumber.intValue)
                    }

                    // Overlay Permission Dialog
                    if (showOverlayPermissionDialog.value) {
                        OverlayDialog(
                            onDismiss = { showOverlayPermissionDialog.value = false },
                            onConfirm = {
                                showOverlayPermissionDialog.value = false
                                val intent =
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName"),
                                    )
                                startActivity(intent)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (prefManager.audioSource == AudioSourceType.MIC) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            }
        }

        if (prefManager.enableFloatingControls) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                showOverlayPermissionDialog.value = true
                return
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startCaptureRequest()
        }
    }

    private fun startCaptureRequest() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun handleProjectionSuccess(
        resultCode: Int,
        data: Intent,
    ) {
        val config = prefManager.getRecordingConfig()
        val countdown = config.countdownSeconds

        if (countdown > 0) {
            pendingResultCode = resultCode
            pendingResultData = data
            startCountdown(countdown, config)
        } else {
            RecordingController.startRecording(this, resultCode, data, config)
            moveTaskToBack(true)
        }
    }

    private fun startCountdown(
        seconds: Int,
        config: labs.hongducdev.recora.model.RecordingConfig,
    ) {
        isCountingDown.value = true
        countdownNumber.intValue = seconds

        val scope =
            (application as RecoraApplication).let {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
            }

        scope.launch {
            for (i in seconds downTo 1) {
                countdownNumber.intValue = i
                delay(1000)
            }
            isCountingDown.value = false
            pendingResultData?.let { data ->
                RecordingController.startRecording(this@MainActivity, pendingResultCode, data, config)
                moveTaskToBack(true)
            }
        }
    }
}

@Composable
private fun CountdownDisplay(countdown: Int) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = countdown.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun OverlayDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Cần quyền hiển thị trên ứng dụng khác",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "Để hiển thị nút nổi hình viên thuốc và bút vẽ trên màn hình khi quay, Recora cần quyền 'Hiển thị trên các ứng dụng khác'.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Mở cài đặt", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bỏ qua")
            }
        },
    )
}
