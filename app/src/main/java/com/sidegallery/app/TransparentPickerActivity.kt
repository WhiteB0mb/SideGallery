package com.sidegallery.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.sidegallery.app.ui.theme.SideGalleryTheme

class TransparentPickerActivity : ComponentActivity() {

    private var targetFolderId: String? = null
    private var pendingVideoUri by mutableStateOf<Uri?>(null)

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            val isSingleVideo = uris.size == 1 && isVideoUri(uris.first())
            if (isSingleVideo) {
                // Open Video Trimmer
                pendingVideoUri = uris.first()
            } else {
                importNormalUris(uris)
                finishWithTransition()
            }
        } else {
            finishWithTransition()
        }
    }

    private fun isVideoUri(uri: Uri): Boolean {
        val mime = contentResolver.getType(uri)
        return mime?.startsWith("video/") == true || uri.toString().lowercase().let {
            it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".mkv") || it.endsWith(".mov") || it.endsWith(".3gp")
        }
    }

    private fun importNormalUris(uris: List<Uri>) {
        val service = OverlayService.activeInstance
        if (service != null) {
            service.viewModel.importMedia(applicationContext, uris, targetFolderId = targetFolderId)
        } else {
            val vm = ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )[MainViewModel::class.java]
            vm.importMedia(applicationContext, uris, targetFolderId = targetFolderId)
        }
        Toast.makeText(this, "Importing ${uris.size} file(s)...", Toast.LENGTH_SHORT).show()
    }

    private fun importTrimmedVideo(uri: Uri, startMs: Long, endMs: Long) {
        val service = OverlayService.activeInstance
        if (service != null) {
            service.viewModel.importVideoWithTrim(applicationContext, uri, startMs, endMs, targetFolderId = targetFolderId)
        } else {
            val vm = ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )[MainViewModel::class.java]
            vm.importVideoWithTrim(applicationContext, uri, startMs, endMs, targetFolderId = targetFolderId)
        }
        Toast.makeText(this, "Processing trimmed video to GIF...", Toast.LENGTH_SHORT).show()
        finishWithTransition()
    }

    private fun finishWithTransition() {
        finish()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetFolderId = intent.getStringExtra("target_folder_id")
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        setContent {
            SideGalleryTheme {
                if (pendingVideoUri != null) {
                    VideoTrimmerDialog(
                        videoUri = pendingVideoUri!!,
                        onDismiss = {
                            pendingVideoUri = null
                            finishWithTransition()
                        },
                        onConfirmTrim = { startMs, endMs ->
                            val uri = pendingVideoUri!!
                            pendingVideoUri = null
                            importTrimmedVideo(uri, startMs, endMs)
                        }
                    )
                }
            }
        }

        if (savedInstanceState == null) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }
}
