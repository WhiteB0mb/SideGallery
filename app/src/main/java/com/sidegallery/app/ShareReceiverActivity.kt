package com.sidegallery.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.sidegallery.app.ui.theme.SideGalleryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStream
import java.io.OutputStream

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = extractUrisFromIntent(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, "No media found to save", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val folders = loadConfiguredFolders()
        if (folders.isEmpty()) {
            Toast.makeText(this, "No folders set up in SideGallery yet", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // If only 1 regular folder exists, directly save without prompting
        val validFolders = folders.filter { !it.isSpecialPinned }
        if (validFolders.size == 1) {
            saveUrisToFolder(uris, validFolders[0])
            return
        }

        // Multiple folders: show dialog to pick folder
        setContent {
            SideGalleryTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp)
                            .clickable(enabled = false) {},
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Save ${uris.size} item(s) to SideGallery",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(onClick = { finish() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                                }
                            }

                            Text(
                                "Choose destination folder:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(validFolders) { folder ->
                                    Surface(
                                        onClick = { saveUrisToFolder(uris, folder) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Text(
                                                text = folder.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractUrisFromIntent(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val list = mutableListOf<Uri>()

        if (Intent.ACTION_SEND == intent.action) {
            val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (streamUri != null) {
                list.add(streamUri)
            } else if (intent.data != null) {
                list.add(intent.data!!)
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == intent.action) {
            val streamUris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (streamUris != null) {
                list.addAll(streamUris.filterNotNull())
            }
        }
        return list
    }

    private fun loadConfiguredFolders(): List<GalleryFolder> {
        val prefs = getSharedPreferences(MainViewModel.PREFS_NAME, MODE_PRIVATE)
        val jsonStr = prefs.getString("gallery_folders_json", null)
        val list = mutableListOf<GalleryFolder>()
        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        GalleryFolder(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            uriString = obj.getString("uriString"),
                            isSpecialPinned = obj.optBoolean("isSpecialPinned", false)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (list.isEmpty()) {
            val single = prefs.getString("selected_folder_uri", null)
            if (single != null) {
                list.add(GalleryFolder(id = "default", name = "Default", uriString = single))
            }
        }
        return list
    }

    private fun saveUrisToFolder(uris: List<Uri>, folder: GalleryFolder) {
        lifecycleScope.launch(Dispatchers.IO) {
            var count = 0
            try {
                val targetTreeUri = Uri.parse(folder.uriString)
                val targetDoc = DocumentFile.fromTreeUri(this@ShareReceiverActivity, targetTreeUri)
                if (targetDoc != null && targetDoc.canWrite()) {
                    for (srcUri in uris) {
                        try {
                            val mimeType = contentResolver.getType(srcUri) ?: "image/jpeg"
                            val ext = when {
                                mimeType.contains("gif") -> ".gif"
                                mimeType.contains("png") -> ".png"
                                mimeType.contains("webp") -> ".webp"
                                mimeType.contains("mp4") -> ".mp4"
                                mimeType.contains("webm") -> ".webm"
                                mimeType.contains("mkv") -> ".mkv"
                                mimeType.contains("mov") -> ".mov"
                                mimeType.startsWith("video/") -> ".mp4"
                                else -> ".jpg"
                            }
                            val fileName = "share_${System.currentTimeMillis()}_$count$ext"
                            val newFile = targetDoc.createFile(mimeType, fileName)
                            if (newFile != null) {
                                val inStream: InputStream? = contentResolver.openInputStream(srcUri)
                                val outStream: OutputStream? = contentResolver.openOutputStream(newFile.uri)
                                if (inStream != null && outStream != null) {
                                    inStream.copyTo(outStream)
                                    inStream.close()
                                    outStream.close()
                                    count++
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                if (count > 0) {
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "Saved $count item(s) to \"${folder.name}\" in SideGallery!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "Failed to save media to folder",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                finish()
            }
        }
    }
}
