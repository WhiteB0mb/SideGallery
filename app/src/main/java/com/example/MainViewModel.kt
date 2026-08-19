package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryItem(
    val uri: Uri,
    val name: String,
    val dateModified: Long,
    val size: Long,
    val isGif: Boolean
)

enum class SortOption {
    NAME_ASC, NAME_DESC, DATE_NEWEST, DATE_OLDEST, SIZE_LARGEST, SIZE_SMALLEST
}

enum class TriggerType { EDGE_SWIPE, FLOATING_BUTTON }
enum class PanelSide { LEFT, RIGHT }
enum class PanelWidth { THIRD, HALF, TWO_THIRDS }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("side_gallery_prefs", Context.MODE_PRIVATE)

    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _hideInLandscape = MutableStateFlow(false)
    val hideInLandscape: StateFlow<Boolean> = _hideInLandscape.asStateFlow()

    private val _images = MutableStateFlow<List<GalleryItem>>(emptyList())
    val images: StateFlow<List<GalleryItem>> = _images.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()
    
    private val _gridColumns = MutableStateFlow(2)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _triggerType = MutableStateFlow(TriggerType.EDGE_SWIPE)
    val triggerType: StateFlow<TriggerType> = _triggerType.asStateFlow()

    private val _panelSide = MutableStateFlow(PanelSide.RIGHT)
    val panelSide: StateFlow<PanelSide> = _panelSide.asStateFlow()

    private val _panelWidth = MutableStateFlow(PanelWidth.THIRD)
    val panelWidth: StateFlow<PanelWidth> = _panelWidth.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "grid_columns") _gridColumns.value = sharedPreferences.getInt("grid_columns", 2)
        if (key == "trigger_type") _triggerType.value = TriggerType.valueOf(sharedPreferences.getString("trigger_type", TriggerType.EDGE_SWIPE.name) ?: TriggerType.EDGE_SWIPE.name)
        if (key == "panel_side") _panelSide.value = PanelSide.valueOf(sharedPreferences.getString("panel_side", PanelSide.RIGHT.name) ?: PanelSide.RIGHT.name)
        if (key == "panel_width") _panelWidth.value = PanelWidth.valueOf(sharedPreferences.getString("panel_width", PanelWidth.THIRD.name) ?: PanelWidth.THIRD.name)
        if (key == "theme_mode") _themeMode.value = ThemeMode.valueOf(sharedPreferences.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        if (key == "hide_in_landscape") _hideInLandscape.value = sharedPreferences.getBoolean("hide_in_landscape", false)
        if (key == "folder_uri") {
            val uriStr = sharedPreferences.getString("folder_uri", null)
            if (uriStr != null) {
                _selectedFolderUri.value = Uri.parse(uriStr)
                loadImages()
            }
        }
    }

    init {
        // Load saved URI and Sort Option
        val savedUriStr = prefs.getString("folder_uri", null)
        if (savedUriStr != null) {
            _selectedFolderUri.value = Uri.parse(savedUriStr)
        }
        
        val savedSort = prefs.getString("sort_option", SortOption.DATE_NEWEST.name)
        _sortOption.value = SortOption.valueOf(savedSort ?: SortOption.DATE_NEWEST.name)

        _gridColumns.value = prefs.getInt("grid_columns", 2)
        _triggerType.value = TriggerType.valueOf(prefs.getString("trigger_type", TriggerType.EDGE_SWIPE.name) ?: TriggerType.EDGE_SWIPE.name)
        _panelSide.value = PanelSide.valueOf(prefs.getString("panel_side", PanelSide.RIGHT.name) ?: PanelSide.RIGHT.name)
        _panelWidth.value = PanelWidth.valueOf(prefs.getString("panel_width", PanelWidth.THIRD.name) ?: PanelWidth.THIRD.name)
        _themeMode.value = ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        _hideInLandscape.value = prefs.getBoolean("hide_in_landscape", false)

        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        if (_selectedFolderUri.value != null) {
            loadImages()
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    fun setHideInLandscape(hide: Boolean) {
        prefs.edit().putBoolean("hide_in_landscape", hide).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setGridColumns(columns: Int) {
        prefs.edit().putInt("grid_columns", columns).apply()
    }

    fun setTriggerType(type: TriggerType) {
        prefs.edit().putString("trigger_type", type.name).apply()
    }
    
    fun setPanelSide(side: PanelSide) {
        prefs.edit().putString("panel_side", side.name).apply()
    }
    
    fun setPanelWidth(width: PanelWidth) {
        prefs.edit().putString("panel_width", width.name).apply()
    }

    fun setFolderUri(uri: Uri) {
        // Take persistable permission
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        prefs.edit().putString("folder_uri", uri.toString()).apply()
        _selectedFolderUri.value = uri
        loadImages()
    }

    fun setSortOption(option: SortOption) {
        prefs.edit().putString("sort_option", option.name).apply()
        _sortOption.value = option
        applySorting()
    }

    fun loadImages() {
        val uri = _selectedFolderUri.value ?: return
        
        _isLoading.value = true
        viewModelScope.launch {
            val loadedImages = withContext(Dispatchers.IO) {
                val documentFile = DocumentFile.fromTreeUri(getApplication(), uri)
                val items = mutableListOf<GalleryItem>()
                
                documentFile?.listFiles()?.forEach { file ->
                    val mimeType = file.type
                    if (mimeType != null && mimeType.startsWith("image/")) {
                        items.add(
                            GalleryItem(
                                uri = file.uri,
                                name = file.name ?: "",
                                dateModified = file.lastModified(),
                                size = file.length(),
                                isGif = mimeType == "image/gif"
                            )
                        )
                    }
                }
                items
            }
            _images.value = loadedImages
            applySorting()
            _isLoading.value = false
        }
    }

    fun importImages(context: Context, uris: List<Uri>) {
        val folderUri = _selectedFolderUri.value ?: return
        _isLoading.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext
                for (uri in uris) {
                    try {
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val ext = when {
                            mimeType.contains("gif") -> "gif"
                            mimeType.contains("png") -> "png"
                            mimeType.contains("webp") -> "webp"
                            else -> "jpg"
                        }
                        val fileName = "imported_${System.currentTimeMillis()}.$ext"
                        val newFile = folder.createFile(mimeType, fileName)
                        if (newFile != null) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            loadImages()
        }
    }

    fun deleteImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = DocumentFile.fromSingleUri(context, uri)
                    if (file != null && file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            loadImages()
        }
    }

    private fun applySorting() {
        val currentImages = _images.value
        val sorted = when (_sortOption.value) {
            SortOption.NAME_ASC -> currentImages.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> currentImages.sortedByDescending { it.name.lowercase() }
            SortOption.DATE_NEWEST -> currentImages.sortedByDescending { it.dateModified }
            SortOption.DATE_OLDEST -> currentImages.sortedBy { it.dateModified }
            SortOption.SIZE_LARGEST -> currentImages.sortedByDescending { it.size }
            SortOption.SIZE_SMALLEST -> currentImages.sortedBy { it.size }
        }
        _images.value = sorted
    }
}
