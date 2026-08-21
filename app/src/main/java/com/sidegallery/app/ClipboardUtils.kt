package com.sidegallery.app

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
    
    private suspend fun prepareCacheFile(context: Context, sourceUri: Uri): Pair<File, String>? {
        try {
            val sharedFolder = File(context.cacheDir, "shared_media")
            if (!sharedFolder.exists()) {
                sharedFolder.mkdirs()
            }

            // Clean up old files older than 5 minutes or to keep cache light
            sharedFolder.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > 300_000) {
                    file.delete()
                }
            }

            var mimeType = context.contentResolver.getType(sourceUri) ?: ""
            val uriStringLower = sourceUri.toString().lowercase()
            if (mimeType.isBlank()) {
                mimeType = when {
                    uriStringLower.endsWith(".gif") || uriStringLower.contains("gif") -> "image/gif"
                    uriStringLower.endsWith(".webp") || uriStringLower.contains("webp") -> "image/webp"
                    uriStringLower.endsWith(".png") || uriStringLower.contains("png") -> "image/png"
                    uriStringLower.endsWith(".mp4") || uriStringLower.contains("mp4") -> "video/mp4"
                    uriStringLower.endsWith(".webm") || uriStringLower.contains("webm") -> "video/webm"
                    uriStringLower.endsWith(".mkv") || uriStringLower.contains("mkv") -> "video/x-matroska"
                    uriStringLower.endsWith(".mov") || uriStringLower.contains("mov") -> "video/quicktime"
                    else -> "image/jpeg"
                }
            }

            val extension = when {
                mimeType.contains("gif") -> ".gif"
                mimeType.contains("webp") -> ".webp"
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
                mimeType.contains("png") -> ".png"
                mimeType.contains("mp4") -> ".mp4"
                mimeType.contains("webm") -> ".webm"
                mimeType.contains("mkv") -> ".mkv"
                mimeType.contains("mov") -> ".mov"
                mimeType.startsWith("video/") -> ".mp4"
                else -> ".jpg"
            }
            
            val isVideo = mimeType.startsWith("video/") || extension == ".mp4" || extension == ".webm" || extension == ".mov" || extension == ".mkv"
            
            // If it's a video, convert up to 15s into animated GIF so it pastes into all messaging apps
            if (isVideo) {
                val gifFile = File(sharedFolder, "gif_clip_${System.currentTimeMillis()}.gif")
                val success = GifConverter.convertVideoToGif(context, sourceUri, gifFile, targetWidth = 320, fps = 8)
                if (success && gifFile.exists()) {
                    return Pair(gifFile, "image/gif")
                }
            }

            val destFile = File(sharedFolder, "media_${System.currentTimeMillis()}$extension")
            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            if (inputStream != null) {
                inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val finalMime = if (mimeType.isNotBlank()) mimeType else if (extension == ".mp4") "video/mp4" else "image/jpeg"
                return Pair(destFile, finalMime)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun copyImageToClipboard(context: Context, sourceUri: Uri) {
        copyMediaToClipboard(context, sourceUri)
    }

    suspend fun copyMediaToClipboard(context: Context, sourceUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val cached = prepareCacheFile(context, sourceUri)
                if (cached == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read media file", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }

                val (destFile, mimeType) = cached
                val authority = "${context.packageName}.fileprovider"
                val fileUri = FileProvider.getUriForFile(context, authority, destFile)

                withContext(Dispatchers.Main) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val label = if (mimeType.startsWith("video/")) "Video" else "Image"
                    val clip = ClipData.newUri(context.contentResolver, label, fileUri)
                    clipboard.setPrimaryClip(clip)
                    
                    val toastMsg = when {
                        mimeType == "image/gif" -> "Copied to clipboard as animated GIF!"
                        mimeType.startsWith("video/") -> "Video copied! (Tip: use 'Share via' for long videos in chat apps)"
                        else -> "Image copied to clipboard!"
                    }
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to copy to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareMedia(context: Context, sourceUri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                val mimeType = context.contentResolver.getType(sourceUri) ?: "image/*"
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, sourceUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMedia(context: Context, sourceUri: Uri) {
        try {
            val mimeType = context.contentResolver.getType(sourceUri) ?: "*/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(sourceUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No app available to open file", Toast.LENGTH_SHORT).show()
        }
    }
}
