package com.sidegallery.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditDialog(
    item: GalleryItem,
    onDismiss: () -> Unit,
    onSaveTags: (List<String>) -> Unit
) {
    var tagsInput by remember {
        mutableStateOf(item.tags.joinToString(", "))
    }

    // In-tree modal overlay: Safe for Service WindowManager overlays as well as Activities
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Prevent dismissing when clicking card content */ },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Label,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Edit Tags",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Media Preview Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = if (item.isGif) "Animated GIF" else if (item.isVideo) "Video" else "Image",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Current Tags Preview Chips
                val currentParsedTags = remember(tagsInput) {
                    tagsInput.split(",")
                        .map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }
                }

                if (currentParsedTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Tags (${currentParsedTags.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            currentParsedTags.forEach { tag ->
                                SuggestionChip(
                                    onClick = {
                                        // Remove tag on click
                                        val remaining = currentParsedTags.filter { it != tag }
                                        tagsInput = remaining.joinToString(", ")
                                    },
                                    label = { Text("#$tag", fontSize = 12.sp) },
                                    icon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Input Field
                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("meme, funny, reaction, cat") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Separate tags with commas. Saved in lowercase.")
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                // Actions
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
                            val cleanTags = tagsInput.split(",")
                                .map { it.trim().lowercase() }
                                .filter { it.isNotEmpty() }
                                .distinct()
                            onSaveTags(cleanTags)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Tags")
                    }
                }
            }
        }
    }
}

