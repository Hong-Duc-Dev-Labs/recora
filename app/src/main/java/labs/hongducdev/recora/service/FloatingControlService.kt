package labs.hongducdev.recora.service

import labs.hongducdev.recora.R
import androidx.compose.ui.res.stringResource
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.widget.FrameLayout
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import labs.hongducdev.recora.model.RecordingStatus
import labs.hongducdev.recora.ui.theme.ExpressiveRadius
import labs.hongducdev.recora.ui.theme.RecoraTheme
import kotlin.math.abs

private val PillContainer = Color(0xFFF9E4DF)
private val ButtonContainer = Color(0xFFFFDAD2)
private val ActiveContainer = Color(0xFF6F5750)
private val StopContainer = Color(0xFFBA1A1A)
private val OnPill = Color(0xFF2B1612)
private val RecordingDot = Color(0xFFE08A8A)
private val PausedDot = Color(0xFFF1B736)

class FloatingControlService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: DragContainer? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    private lateinit var layoutParams: WindowManager.LayoutParams

    // Touch dragging variables
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var snapAnimator: ValueAnimator? = null

    private val density get() = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlayView()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayView() {
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

        layoutParams =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = dp(24)
                    y = dp(96)
                }

        val composeView =
            ComposeView(this).apply {
                setContent {
                    RecoraTheme(pitchBlackTheme = true) {
                        FloatingPillContent()
                    }
                }
            }
        // Compose resolves these owners from the window's root view, which is the container.
        overlayView =
            DragContainer(this).apply {
                setViewTreeLifecycleOwner(overlayLifecycleOwner)
                setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
                addView(composeView)
            }

        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (abs(dx) > slop || abs(dy) > slop) {
                    isDragging = true
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    windowManager?.updateViewLayout(overlayView, layoutParams)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    snapToEdge()
                    return true
                }
            }
        }
        return false
    }

    /**
     * Compose consumes every touch that lands on the pill, so a listener on the view would never see
     * the drag. Intercepting once the finger moves past the touch slop lets the buttons keep their
     * clicks while a drag anywhere on the pill moves the window.
     */
    private inner class DragContainer(
        context: Context,
    ) : FrameLayout(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            handleTouch(ev)
            return isDragging
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            handleTouch(event)
            return true
        }
    }

    private fun snapToEdge() {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val viewWidth = overlayView?.width ?: dp(200)
        val margin = dp(8)

        val targetX =
            if (layoutParams.x + viewWidth / 2 < screenWidth / 2) {
                margin // Snap to left
            } else {
                screenWidth - viewWidth - margin // Snap to right
            }

        snapAnimator?.cancel()
        snapAnimator =
            ValueAnimator.ofInt(layoutParams.x, targetX).apply {
                duration = 250
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val view = overlayView ?: return@addUpdateListener
                    layoutParams.x = anim.animatedValue as Int
                    windowManager?.updateViewLayout(view, layoutParams)
                }
                start()
            }
    }

    @Composable
    private fun FloatingPillContent() {
        val sessionState by RecordingController.sessionState.collectAsState()
        val isDrawingActive by RecordingController.isDrawingOverlayActive.collectAsState()
        val isFacecamActive by RecordingController.isFacecamActive.collectAsState()
        val isPaused = sessionState.status == RecordingStatus.PAUSED

        Surface(
            shape = RoundedCornerShape(ExpressiveRadius.Pill),
            color = PillContainer,
            shadowElevation = 6.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isPaused) PausedDot else RecordingDot),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = sessionState.formattedDuration,
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = OnPill,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier =
                        Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(OnPill.copy(alpha = 0.2f)),
                )
                Spacer(Modifier.width(8.dp))

                PillButton(
                    icon = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    description = if (isPaused) stringResource(R.string.action_resume) else stringResource(R.string.action_pause),
                    onClick = {
                        if (isPaused) {
                            RecordingController.resumeRecording(this@FloatingControlService)
                        } else {
                            RecordingController.pauseRecording(this@FloatingControlService)
                        }
                    },
                )
                Spacer(Modifier.width(6.dp))
                PillButton(
                    icon = Icons.Rounded.PhotoCamera,
                    description = "Facecam",
                    selected = isFacecamActive,
                    onClick = { RecordingController.toggleFacecam(this@FloatingControlService) },
                )
                Spacer(Modifier.width(6.dp))
                PillButton(
                    icon = Icons.Rounded.Draw,
                    description = stringResource(R.string.action_draw),
                    selected = isDrawingActive,
                    onClick = { RecordingController.toggleDrawing(this@FloatingControlService) },
                )
                Spacer(Modifier.width(6.dp))
                PillButton(
                    icon = Icons.Rounded.Stop,
                    description = stringResource(R.string.action_stop),
                    container = StopContainer,
                    tint = Color.White,
                    onClick = { RecordingController.stopRecording(this@FloatingControlService) },
                )
            }
        }
    }

    @Composable
    private fun PillButton(
        icon: ImageVector,
        description: String,
        onClick: () -> Unit,
        selected: Boolean = false,
        container: Color = ButtonContainer,
        tint: Color = OnPill,
    ) {
        // Selected buttons morph from a circle into a rounded square (Material 3 Expressive).
        val corner by animateDpAsState(if (selected) 11.dp else 18.dp, label = "pillButtonCorner")
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(if (selected) ActiveContainer else container)
                    .clickable(role = Role.Button, onClick = onClick),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (selected) Color.White else tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        snapAnimator?.cancel()
        snapAnimator = null
        overlayLifecycleOwner?.onPause()
        overlayLifecycleOwner?.onDestroy()
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
