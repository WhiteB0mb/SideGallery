package com.sidegallery.app

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTrimmerDialog(
    videoUri: Uri,
    onDismiss: () -> Unit,
    onConfirmTrim: (startMs: Long, endMs: Long) -> Unit
) {
    val context = LocalContext.current
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var startPositionSec by remember { mutableFloatStateOf(0f) }
    var endPositionSec by remember { mutableFloatStateOf(0f) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(videoUri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 15_000L
            totalDurationMs = duration
            val totalSec = duration / 1000f
            startPositionSec = 0f
            endPositionSec = totalSec.coerceAtMost(15.0f).coerceAtLeast(0.5f)
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
            totalDurationMs = 15_000L
            startPositionSec = 0f
            endPositionSec = 15.0f
            isInitialized = true
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Prevent dismissing on card click */ },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Trim Video Clip",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Select a segment (maximum 15 seconds) to convert into an animated GIF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                // Native VideoView Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(videoUri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                                videoViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isInitialized && totalDurationMs > 0) {
                    val totalSec = (totalDurationMs / 1000f).coerceAtLeast(1.0f)
                    val selectedDurationSec = (endPositionSec - startPositionSec).coerceAtLeast(0.1f)

                    // Timestamps display
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Start", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    formatSeconds(startPositionSec),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Duration (Max 15s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    String.format(Locale.US, "%.1fs", selectedDurationSec),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("End", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    formatSeconds(endPositionSec),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 2-handle Range Slider with strict 15s constraint
                    Column(modifier = Modifier.fillMaxWidth()) {
                        RangeSlider(
                            value = startPositionSec..endPositionSec,
                            onValueChange = { range ->
                                var newStart = range.start
                                var newEnd = range.endInclusive

                                // Ensure end - start <= 15 seconds
                                if (newEnd - newStart > 15.0f) {
                                    if (newEnd != endPositionSec) {
                                        // End handle moved
                                        newStart = (newEnd - 15.0f).coerceAtLeast(0f)
                                    } else {
                                        // Start handle moved
                                        newEnd = (newStart + 15.0f).coerceAtMost(totalSec)
                                    }
                                }

                                // Min interval 0.5s
                                if (newEnd - newStart < 0.5f) {
                                    if (newEnd != endPositionSec) {
                                        newEnd = (newStart + 0.5f).coerceAtMost(totalSec)
                                    } else {
                                        newStart = (newEnd - 0.5f).coerceAtLeast(0f)
                                    }
                                }

                                if (newStart != startPositionSec) {
                                    // Seek video preview to start position
                                    videoViewRef?.seekTo((newStart * 1000).toInt())
                                }

                                startPositionSec = newStart
                                endPositionSec = newEnd
                            },
                            valueRange = 0f..totalSec,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val startMs = (startPositionSec * 1000).toLong()
                            val endMs = (endPositionSec * 1000).toLong()
                            onConfirmTrim(startMs, endMs)
                        },
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Convert GIF")
                    }
                }
            }
        }
    }
}

private fun formatSeconds(sec: Float): String {
    val totalSec = sec.toInt()
    val tenths = ((sec - totalSec) * 10).toInt()
    val minutes = totalSec / 60
    val remSec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d.%d", minutes, remSec, tenths)
}
