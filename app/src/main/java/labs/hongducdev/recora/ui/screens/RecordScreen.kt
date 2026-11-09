package labs.hongducdev.recora.ui.screens

import labs.hongducdev.recora.R
import androidx.compose.ui.res.stringResource
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import labs.hongducdev.recora.model.AudioSourceType
import labs.hongducdev.recora.model.RecordingStatus
import labs.hongducdev.recora.service.RecordingController
import labs.hongducdev.recora.ui.components.PullTitleHeader
import labs.hongducdev.recora.ui.components.rememberPullTitleHeaderState
import labs.hongducdev.recora.ui.core.cards.IconToggleItem
import labs.hongducdev.recora.ui.core.containers.RoundedCardContainer
import labs.hongducdev.recora.ui.theme.ExpressiveRadius
import labs.hongducdev.recora.utils.HapticUtil
import labs.hongducdev.recora.utils.PreferenceManager

@Composable
fun RecordScreen(
    contentPadding: PaddingValues,
    onStartRecordingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefManager = remember { PreferenceManager(context) }
    val headerState = rememberPullTitleHeaderState()
    val listState = rememberLazyListState()

    val sessionState by RecordingController.sessionState.collectAsState()
    val config by RecordingController.config.collectAsState()

    var micEnabled by remember {
        mutableStateOf(prefManager.audioSource == AudioSourceType.MIC)
    }
    var floatingControlsEnabled by remember {
        mutableStateOf(prefManager.enableFloatingControls)
    }

    val isRecording = sessionState.status == RecordingStatus.RECORDING
    val isPaused = sessionState.status == RecordingStatus.PAUSED

    val storageFreeGb =
        remember {
            try {
                val stat = StatFs(Environment.getDataDirectory().path)
                stat.availableBytes / (1024 * 1024 * 1024)
            } catch (_: Exception) {
                32L
            }
        }

    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(headerState.nestedScrollConnection),
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = 150.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PullTitleHeader(
                title = "Recora",
                caption = stringResource(R.string.record_caption),
                state = headerState,
            )
        }

        // Hero Card: Big Action & Current State
        item {
            Card(
                shape = RoundedCornerShape(ExpressiveRadius.Card),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Big Recording Pulse Button
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isRecording) 1.08f else 1f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(800),
                                repeatMode = RepeatMode.Reverse,
                            ),
                        label = "pulseScale",
                    )

                    val buttonBg by animateColorAsState(
                        targetValue =
                            when {
                                isRecording -> MaterialTheme.colorScheme.error
                                isPaused -> Color(0xFFE5A93C)
                                else -> MaterialTheme.colorScheme.primary
                            },
                        label = "btnColor",
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .scale(pulseScale)
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(buttonBg)
                                .clickable {
                                    HapticUtil.performVirtualKeyHaptic(view)
                                    when {
                                        isRecording || isPaused -> {
                                            RecordingController.stopRecording(context)
                                        }

                                        else -> {
                                            onStartRecordingClick()
                                        }
                                    }
                                },
                    ) {
                        Icon(
                            imageVector =
                                when {
                                    isRecording -> Icons.Rounded.Stop
                                    isPaused -> Icons.Rounded.Pause
                                    else -> Icons.Rounded.Videocam
                                },
                            contentDescription = stringResource(R.string.record_action),
                            tint = Color.White,
                            modifier = Modifier.size(52.dp),
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text =
                            when {
                                isRecording -> stringResource(R.string.record_status_recording, sessionState.formattedDuration)
                                isPaused -> stringResource(R.string.record_status_paused, sessionState.formattedDuration)
                                else -> stringResource(R.string.record_status_ready)
                            },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text =
                            when {
                                isRecording -> stringResource(R.string.record_hint_recording)
                                isPaused -> stringResource(R.string.record_hint_paused)
                                else -> stringResource(R.string.record_hint_idle, config.resolutionWidth, config.resolutionHeight, config.frameRate, storageFreeGb)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Quick Toggles
        item {
            RoundedCardContainer(title = stringResource(R.string.quick_controls)) {
                IconToggleItem(
                    title = stringResource(R.string.mic_title),
                    description = if (micEnabled) stringResource(R.string.mic_on) else stringResource(R.string.mic_off),
                    icon = Icons.Rounded.Mic,
                    checked = micEnabled,
                    onCheckedChange = { checked ->
                        micEnabled = checked
                        val newSource = if (checked) AudioSourceType.MIC else AudioSourceType.MUTE
                        prefManager.audioSource = newSource
                        RecordingController.updateConfig(
                            RecordingController.config.value.copy(audioSource = newSource),
                        )
                    },
                )

                IconToggleItem(
                    title = stringResource(R.string.float_title),
                    description = stringResource(R.string.float_desc),
                    icon = Icons.Rounded.Widgets,
                    checked = floatingControlsEnabled,
                    onCheckedChange = { checked ->
                        floatingControlsEnabled = checked
                        prefManager.enableFloatingControls = checked
                        RecordingController.updateConfig(
                            RecordingController.config.value.copy(enableFloatingControls = checked),
                        )
                    },
                )
            }
        }
    }
}
