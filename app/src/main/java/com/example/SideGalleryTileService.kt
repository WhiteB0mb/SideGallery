package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class SideGalleryTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = OverlayService.isRunning.value
        if (isRunning) {
            val stopIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
            startService(stopIntent)
            Toast.makeText(this, "SideGallery overlay stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant overlay permission in SideGallery first", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndCollapse(intent)
                return
            }

            val prefs = getSharedPreferences(MainViewModel.PREFS_NAME, MODE_PRIVATE)
            val folderUriStr = prefs.getString("selected_folder_uri", null)
            val foldersJson = prefs.getString("gallery_folders_json", null)
            if (folderUriStr == null && (foldersJson == null || foldersJson == "[]")) {
                Toast.makeText(this, "Please select a media folder in SideGallery first", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndCollapse(intent)
                return
            }

            val startIntent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
            Toast.makeText(this, "SideGallery overlay started", Toast.LENGTH_SHORT).show()
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = OverlayService.isRunning.value
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "SideGallery"
        tile.updateTile()
    }
}
