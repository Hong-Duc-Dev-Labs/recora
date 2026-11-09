package labs.hongducdev.recora.service

import labs.hongducdev.recora.R
import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import labs.hongducdev.recora.ui.drawing.DrawingCanvasView
import labs.hongducdev.recora.ui.theme.RecoraTheme

class DrawingOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var rootLayout: FrameLayout? = null
    private var canvasView: DrawingCanvasView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        overlayLifecycleOwner =
            OverlayLifecycleOwner().apply {
                onCreate()
                onResume()
            }

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )

        rootLayout =
            FrameLayout(this).apply {
                setViewTreeLifecycleOwner(overlayLifecycleOwner)
                setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            }
        canvasView = DrawingCanvasView(this)
        rootLayout?.addView(
            canvasView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        // Palette tool bar on top
        val composeBar =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(overlayLifecycleOwner)
                setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

                setContent {
                    RecoraTheme(pitchBlackTheme = true) {
                        DrawingToolBar(
                            onColorSelected = { color ->
                                canvasView?.setColor(color)
                            },
                            onStrokeWidthSelected = { width ->
                                canvasView?.setStrokeWidth(width)
                            },
                            onUndo = {
                                canvasView?.undo()
                            },
                            onClear = {
                                canvasView?.clear()
                            },
                            onClose = {
                                RecordingController.isDrawingOverlayActive.value = false
                                stopSelf()
                            },
                        )
                    }
                }
            }

        val barParams =
            FrameLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = (64 * resources.displayMetrics.density).toInt()
                }

        composeBar.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            canvasView?.topBarRect = android.graphics.Rect(l, t, r, b)
        }

        rootLayout?.addView(composeBar, barParams)

        try {
            windowManager?.addView(rootLayout, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    @Composable
    private fun DrawingToolBar(
        onColorSelected: (Int) -> Unit,
        onStrokeWidthSelected: (Float) -> Unit,
        onUndo: () -> Unit,
        onClear: () -> Unit,
        onClose: () -> Unit,
    ) {
        val colors =
            listOf(
                android.graphics.Color.RED,
                android.graphics.Color.YELLOW,
                android.graphics.Color.GREEN,
                android.graphics.Color.CYAN,
                android.graphics.Color.WHITE,
            )

        var selectedColor by remember { mutableIntStateOf(android.graphics.Color.RED) }
        var selectedWidth by remember { mutableFloatStateOf(14f) }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            shadowElevation = 10.dp,
            modifier =
                Modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(28.dp),
                    ).padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                // Color dots
                colors.forEach { colorInt ->
                    val isSelected = colorInt == selectedColor
                    Box(
                        modifier =
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(colorInt))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                ).clickable {
                                    selectedColor = colorInt
                                    onColorSelected(colorInt)
                                },
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Undo
                IconButton(onClick = onUndo, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = stringResource(R.string.action_undo),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Clear
                IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.action_clear_all),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Done / Close
                IconButton(
                    onClick = onClose,
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.action_done),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RecordingController.isDrawingOverlayActive.value = false
        overlayLifecycleOwner?.onPause()
        overlayLifecycleOwner?.onDestroy()
        rootLayout?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        rootLayout = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "action_show_drawing"
        const val ACTION_HIDE = "action_hide_drawing"
    }
}
