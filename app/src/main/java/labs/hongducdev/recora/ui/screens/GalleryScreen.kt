package labs.hongducdev.recora.ui.screens

import android.net.Uri
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import labs.hongducdev.recora.model.RecordedVideo
import labs.hongducdev.recora.ui.components.PullTitleHeader
import labs.hongducdev.recora.ui.components.rememberPullTitleHeaderState
import labs.hongducdev.recora.ui.theme.ExpressiveRadius
import labs.hongducdev.recora.utils.VideoStorageManager

@Composable
fun GalleryScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageManager = remember { VideoStorageManager(context) }
    val headerState = rememberPullTitleHeaderState()
    val listState = rememberLazyListState()

    var videos by remember { mutableStateOf<List<RecordedVideo>>(emptyList()) }
    var videoToDelete by remember { mutableStateOf<RecordedVideo?>(null) }
    var revealedVideoId by remember { mutableStateOf<Long?>(null) }

    fun refreshVideos() {
        scope.launch {
            videos = storageManager.queryRecordedVideos()
        }
    }

    LaunchedEffect(Unit) {
        refreshVideos()
    }

    // A recording started from the Quick Settings tile finishes while this screen may stay open.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshVideos() }

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PullTitleHeader(
                title = "Thư viện",
                caption = "${videos.size} video đã quay",
                state = headerState,
            )
        }

        if (videos.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(ExpressiveRadius.Card),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceBright),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VideocamOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Chưa có video nào",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Bắt đầu quay màn hình để lưu lại video đầu tiên của bạn",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(videos, key = { it.id }) { video ->
                VideoCardItem(
                    video = video,
                    revealed = revealedVideoId == video.id,
                    onRevealChange = { open -> revealedVideoId = if (open) video.id else null },
                    onPlay = {
                        try {
                            context.startActivity(storageManager.createPlayIntent(video.uri))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Không tìm thấy app phát video", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShare = {
                        try {
                            context.startActivity(storageManager.createShareIntent(video.uri))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Không thể chia sẻ video", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = {
                        videoToDelete = video
                    },
                )
            }
        }
    }

    // Delete Confirmation Dialog
    videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = {
                Text(
                    text = "Xoá video?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Bạn có chắc chắn muốn xoá video '${video.displayName}' không? Thao tác này không thể hoàn tác.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = storageManager.deleteVideo(video.uri)
                        if (deleted) {
                            Toast.makeText(context, "Đã xoá video", Toast.LENGTH_SHORT).show()
                            refreshVideos()
                        }
                        videoToDelete = null
                    },
                ) {
                    Text("Xoá", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("Huỷ")
                }
            },
        )
    }
}

@Composable
private fun VideoCardItem(
    video: RecordedVideo,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionsWidthPx = with(LocalDensity.current) { (ACTION_SIZE * 2 + ACTION_GAP * 3).toPx() }
    val offsetX = remember { Animatable(0f) }
    val settleSpec = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    // Another item opening (or this one closing) animates this card to its resting place.
    LaunchedEffect(revealed, actionsWidthPx) {
        offsetX.animateTo(if (revealed) -actionsWidthPx else 0f, settleSpec)
    }

    val dragState =
        rememberDraggableState { delta ->
            scope.launch { offsetX.snapTo((offsetX.value + delta).coerceIn(-actionsWidthPx, 0f)) }
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Actions sit behind the card and are uncovered by swiping it to the left.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ACTION_GAP),
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(end = ACTION_GAP),
        ) {
            Spacer(Modifier.weight(1f))
            SwipeAction(
                icon = Icons.Rounded.Share,
                description = "Chia sẻ",
                container = MaterialTheme.colorScheme.secondaryContainer,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {
                    onRevealChange(false)
                    onShare()
                },
            )
            SwipeAction(
                icon = Icons.Rounded.Delete,
                description = "Xoá",
                container = MaterialTheme.colorScheme.errorContainer,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {
                    onRevealChange(false)
                    onDelete()
                },
            )
        }

        Card(
            shape = RoundedCornerShape(ExpressiveRadius.Card),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { velocity ->
                            val open = velocity < -800f || (velocity <= 800f && offsetX.value < -actionsWidthPx / 2f)
                            onRevealChange(open)
                            scope.launch { offsetX.animateTo(if (open) -actionsWidthPx else 0f, settleSpec) }
                        },
                    ).clickable { if (revealed) onRevealChange(false) else onPlay() },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
            ) {
                VideoThumbnail(uri = video.uri)

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.displayName.removeSuffix(".mp4"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = video.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "•  ${video.formattedSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = video.formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val ACTION_SIZE = 52.dp
private val ACTION_GAP = 10.dp

@Composable
private fun SwipeAction(
    icon: ImageVector,
    description: String,
    container: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(ACTION_SIZE)
                .clip(CircleShape)
                .background(container)
                .clickable(onClick = onClick),
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Frame preview of the video; falls back to a plain play tile while loading or if no frame is available. */
@Composable
private fun VideoThumbnail(uri: Uri) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, uri) {
        value =
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(320, 320), null).asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = if (thumbnail != null) 0.45f else 0f)),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Phát video",
                tint = if (thumbnail != null) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
