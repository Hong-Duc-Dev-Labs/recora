package labs.hongducdev.recora.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import labs.hongducdev.recora.RecordingStartActivity
import labs.hongducdev.recora.R
import labs.hongducdev.recora.model.RecordingStatus

/** Quick Settings tile: starts a recording (via MainActivity, which owns the permission flow) or stops the current one. */
class RecordingTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope =
            CoroutineScope(Dispatchers.Main + Job()).also {
                it.launch {
                    RecordingController.sessionState.collect { renderTile() }
                }
            }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isRecordingActive()) {
            RecordingController.stopRecording(this)
        } else if (isLocked) {
            unlockAndRun { launchRecordingRequest() }
        } else {
            launchRecordingRequest()
        }
    }

    private fun isRecordingActive(): Boolean {
        val status = RecordingController.sessionState.value.status
        return status == RecordingStatus.RECORDING || status == RecordingStatus.PAUSED
    }

    private fun launchRecordingRequest() {
        val intent =
            Intent(this, RecordingStartActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    REQUEST_CODE_START,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun renderTile() {
        val tile = qsTile ?: return
        val state = RecordingController.sessionState.value
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_record)
        when (state.status) {
            RecordingStatus.RECORDING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Đang quay"
                tile.subtitle = state.formattedDuration
            }

            RecordingStatus.PAUSED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Tạm dừng"
                tile.subtitle = state.formattedDuration
            }

            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Quay màn hình"
                tile.subtitle = "Chạm để bắt đầu"
            }
        }
        tile.updateTile()
    }

    private companion object {
        const val REQUEST_CODE_START = 100
    }
}
