package com.sidegallery.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider

class TransparentPickerActivity : ComponentActivity() {

    private var targetFolderId: String? = null

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
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

        if (savedInstanceState == null) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }
}
