package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ClipboardUtils {
    suspend fun copyImageToClipboard(context: Context, sourceUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Create a dedicated folder in cache
                val sharedFolder = File(context.cacheDir, "shared_images")
                if (!sharedFolder.exists()) {
                    sharedFolder.mkdirs()
                }

                // Clean up old files to avoid bloat
                sharedFolder.listFiles()?.forEach { it.delete() }

                // 2. Determine file extension/name
                val mimeType = context.contentResolver.getType(sourceUri)
                val extension = when {
                    mimeType?.contains("gif") == true -> ".gif"
                    mimeType?.contains("webp") == true -> ".webp"
                    mimeType?.contains("jpeg") == true || mimeType?.contains("jpg") == true -> ".jpg"
                    else -> ".png"
                }
                val destFile = File(sharedFolder, "shared_image_${System.currentTimeMillis()}$extension")

                // 3. Copy the stream
                val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
                if (inputStream != null) {
                    val outputStream = FileOutputStream(destFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read image", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }

                // 4. Get FileProvider URI
                val authority = "${context.packageName}.fileprovider"
                val fileUri = FileProvider.getUriForFile(context, authority, destFile)

                // 5. Put in Clipboard
                withContext(Dispatchers.Main) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newUri(context.contentResolver, "Image", fileUri)
                    clipboard.setPrimaryClip(clip)
                    
                    // On some Android versions we need to grant URI permissions to the clipboard
                    // Note: setPrimaryClip handles some of this, but it's good practice.
                    Toast.makeText(context, "Immagine copiata!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Errore durante la copia", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
