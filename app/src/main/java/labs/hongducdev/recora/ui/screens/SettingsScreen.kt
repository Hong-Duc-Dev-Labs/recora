package labs.hongducdev.recora.ui.screens

import labs.hongducdev.recora.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import labs.hongducdev.recora.model.AudioSourceType
import labs.hongducdev.recora.service.RecordingController
import labs.hongducdev.recora.ui.components.PullTitleHeader
import labs.hongducdev.recora.ui.components.rememberPullTitleHeaderState
import labs.hongducdev.recora.ui.core.cards.IconToggleItem
import labs.hongducdev.recora.ui.core.containers.RoundedCardContainer
import labs.hongducdev.recora.ui.core.pickers.SegmentedPicker
import labs.hongducdev.recora.ui.core.sliders.ExpressiveSlider
import labs.hongducdev.recora.utils.HapticUtil
import labs.hongducdev.recora.utils.PreferenceManager

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onPitchBlackChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val headerState = rememberPullTitleHeaderState()
    val listState = rememberLazyListState()

    var resolutionKey by remember {
        mutableStateOf("${prefManager.resolutionWidth}x${prefManager.resolutionHeight}")
    }
    var fps by remember { mutableIntStateOf(prefManager.frameRate) }
    var bitRateMbps by remember { mutableFloatStateOf(prefManager.bitRate / 1_000_000f) }
    var audioSource by remember { mutableStateOf(prefManager.audioSource) }
    var countdown by remember { mutableIntStateOf(prefManager.countdownSeconds) }
    var floatingControls by remember { mutableStateOf(prefManager.enableFloatingControls) }
    var pitchBlack by remember { mutableStateOf(prefManager.pitchBlackTheme) }
    var hapticsEnabled by remember { mutableStateOf(prefManager.appHapticsEnabled) }

    fun updateConfig() {
        val (w, h) =
            when (resolutionKey) {
                "720x1280" -> 720 to 1280
                "480x854" -> 480 to 854
                else -> 1080 to 1920
            }
        prefManager.resolutionWidth = w
        prefManager.resolutionHeight = h
        prefManager.frameRate = fps
        prefManager.bitRate = (bitRateMbps * 1_000_000).toInt()
        prefManager.audioSource = audioSource
        prefManager.countdownSeconds = countdown
        prefManager.enableFloatingControls = floatingControls

        RecordingController.updateConfig(prefManager.getRecordingConfig())
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
                title = stringResource(R.string.tab_settings),
                caption = stringResource(R.string.settings_caption),
                state = headerState,
            )
        }

        // 1. Video Settings
        item {
            RoundedCardContainer(title = stringResource(R.string.video_quality)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.resolution),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    SegmentedPicker(
                        items =
                            listOf(
                                "1080x1920" to "1080p",
                                "720x1280" to "720p",
                                "480x854" to "480p",
                            ),
                        selectedItem = resolutionKey,
                        onItemSelected = {
                            resolutionKey = it
                            updateConfig()
                        },
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.fps_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    SegmentedPicker(
                        items =
                            listOf(
                                60 to "60 FPS",
                                30 to "30 FPS",
                            ),
                        selectedItem = fps,
                        onItemSelected = {
                            fps = it
                            updateConfig()
                        },
                    )

                    Spacer(Modifier.height(16.dp))

                    ExpressiveSlider(
                        label = stringResource(R.string.bitrate_label),
                        value = bitRateMbps,
                        range = 4f..24f,
                        valueFormatted = "${bitRateMbps.toInt()} Mbps",
                        onValueChange = {
                            bitRateMbps = it
                            updateConfig()
                        },
                    )
                }
            }
        }

        // 2. Audio Settings
        item {
            RoundedCardContainer(title = stringResource(R.string.audio)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.audio_source),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    SegmentedPicker(
                        items =
                            listOf(
                                AudioSourceType.MIC to stringResource(R.string.audio_mic),
                                AudioSourceType.MUTE to stringResource(R.string.audio_mute),
                            ),
                        selectedItem = audioSource,
                        onItemSelected = {
                            audioSource = it
                            updateConfig()
                        },
                    )
                }
            }
        }

        // 3. Controls & Overlay
        item {
            RoundedCardContainer(title = stringResource(R.string.controls)) {
                IconToggleItem(
                    title = stringResource(R.string.floating_bar),
                    description = stringResource(R.string.floating_bar_desc),
                    icon = Icons.Rounded.Widgets,
                    checked = floatingControls,
                    onCheckedChange = {
                        floatingControls = it
                        updateConfig()
                    },
                )

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.countdown),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    SegmentedPicker(
                        items =
                            listOf(
                                0 to stringResource(R.string.countdown_none),
                                3 to stringResource(R.string.countdown_3),
                                5 to stringResource(R.string.countdown_5),
                            ),
                        selectedItem = countdown,
                        onItemSelected = {
                            countdown = it
                            updateConfig()
                        },
                    )
                }
            }
        }

        // 4. Appearance & Haptics
        item {
            RoundedCardContainer(title = stringResource(R.string.appearance)) {
                IconToggleItem(
                    title = stringResource(R.string.pitch_black),
                    description = stringResource(R.string.pitch_black_desc),
                    icon = Icons.Rounded.DarkMode,
                    checked = pitchBlack,
                    onCheckedChange = {
                        pitchBlack = it
                        prefManager.pitchBlackTheme = it
                        onPitchBlackChange(it)
                    },
                )

                IconToggleItem(
                    title = stringResource(R.string.haptics),
                    description = stringResource(R.string.haptics_desc),
                    icon = Icons.Rounded.Vibration,
                    checked = hapticsEnabled,
                    onCheckedChange = {
                        hapticsEnabled = it
                        prefManager.appHapticsEnabled = it
                    },
                )
            }
        }
    }
}
