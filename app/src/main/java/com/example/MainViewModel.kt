package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
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
enum class ScrollDirection { TOP_TO_BOTTOM, BOTTOM_TO_TOP }
enum class PanelSide { LEFT, RIGHT }
enum class PanelWidth { THIRD, HALF, TWO_THIRDS }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("side_gallery_prefs", Context.MODE_PRIVATE)

    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(false)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

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

    private val _scrollDirection = MutableStateFlow(ScrollDirection.TOP_TO_BOTTOM)
    val scrollDirection: StateFlow<ScrollDirection> = _scrollDirection.asStateFlow()

    private val _swipeHeightPercent = MutableStateFlow(70)
    val swipeHeightPercent: StateFlow<Int> = _swipeHeightPercent.asStateFlow()

    private val _guidePreviewUntil = MutableStateFlow(0L)
    val guidePreviewUntil: StateFlow<Long> = _guidePreviewUntil.asStateFlow()

    private val _panelSide = MutableStateFlow(PanelSide.RIGHT)
    val panelSide: StateFlow<PanelSide> = _panelSide.asStateFlow()

    private val _panelWidth = MutableStateFlow(PanelWidth.THIRD)
    val panelWidth: StateFlow<PanelWidth> = _panelWidth.asStateFlow()

    private val _panelWidthPercent = MutableStateFlow(33)
    val panelWidthPercent: StateFlow<Int> = _panelWidthPercent.asStateFlow()

    private val _panelHeightPercent = MutableStateFlow(100)
    val panelHeightPercent: StateFlow<Int> = _panelHeightPercent.asStateFlow()

    private val _panelOpacityPercent = MutableStateFlow(95)
    val panelOpacityPercent: StateFlow<Int> = _panelOpacityPercent.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var contentObserver: ContentObserver? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            "has_completed_onboarding" -> _hasCompletedOnboarding.value = sharedPreferences.getBoolean("has_completed_onboarding", false)
            "grid_columns" -> _gridColumns.value = sharedPreferences.getInt("grid_columns", 2)
            "trigger_type" -> _triggerType.value = TriggerType.valueOf(sharedPreferences.getString("trigger_type", TriggerType.EDGE_SWIPE.name) ?: TriggerType.EDGE_SWIPE.name)
            "scroll_direction" -> _scrollDirection.value = ScrollDirection.valueOf(sharedPreferences.getString("scroll_direction", ScrollDirection.TOP_TO_BOTTOM.name) ?: ScrollDirection.TOP_TO_BOTTOM.name)
            "swipe_height_percent" -> _swipeHeightPercent.value = sharedPreferences.getInt("swipe_height_percent", 70)
            "guide_preview_until" -> _guidePreviewUntil.value = sharedPreferences.getLong("guide_preview_until", 0L)
            "panel_side" -> _panelSide.value = PanelSide.valueOf(sharedPreferences.getString("panel_side", PanelSide.RIGHT.name) ?: PanelSide.RIGHT.name)
            "panel_width" -> _panelWidth.value = PanelWidth.valueOf(sharedPreferences.getString("panel_width", PanelWidth.THIRD.name) ?: PanelWidth.THIRD.name)
            "panel_width_percent" -> _panelWidthPercent.value = sharedPreferences.getInt("panel_width_percent", 33)
            "panel_height_percent" -> _panelHeightPercent.value = sharedPreferences.getInt("panel_height_percent", 100)
            "panel_opacity_percent" -> _panelOpacityPercent.value = sharedPreferences.getInt("panel_opacity_percent", 95)
            "theme_mode" -> _themeMode.value = ThemeMode.valueOf(sharedPreferences.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
            "hide_in_landscape" -> _hideInLandscape.value = sharedPreferences.getBoolean("hide_in_landscape", false)
            "sort_option" -> {
                val opt = SortOption.valueOf(sharedPreferences.getString("sort_option", SortOption.DATE_NEWEST.name) ?: SortOption.DATE_NEWEST.name)
                _sortOption.value = opt
                applySorting()
            }
            "last_media_update" -> loadImages()
            "folder_uri" -> {
                val uriStr = sharedPreferences.getString("folder_uri", null)
                if (uriStr != null) {
                    val parsed = Uri.parse(uriStr)
                    _selectedFolderUri.value = parsed
                    registerContentObserver(parsed)
                    loadImages()
                }
            }
        }
    }

    init {
        val savedUriStr = prefs.getString("folder_uri", null)
        if (savedUriStr != null) {
            val parsed = Uri.parse(savedUriStr)
            _selectedFolderUri.value = parsed
            registerContentObserver(parsed)
        }
        
        _hasCompletedOnboarding.value = prefs.getBoolean("has_completed_onboarding", false)

        val savedSort = prefs.getString("sort_option", SortOption.DATE_NEWEST.name)
        _sortOption.value = SortOption.valueOf(savedSort ?: SortOption.DATE_NEWEST.name)

        _gridColumns.value = prefs.getInt("grid_columns", 2)
        _triggerType.value = TriggerType.valueOf(prefs.getString("trigger_type", TriggerType.EDGE_SWIPE.name) ?: TriggerType.EDGE_SWIPE.name)
        _scrollDirection.value = ScrollDirection.valueOf(prefs.getString("scroll_direction", ScrollDirection.TOP_TO_BOTTOM.name) ?: ScrollDirection.TOP_TO_BOTTOM.name)
        _swipeHeightPercent.value = prefs.getInt("swipe_height_percent", 70)
        _guidePreviewUntil.value = prefs.getLong("guide_preview_until", 0L)
        _panelSide.value = PanelSide.valueOf(prefs.getString("panel_side", PanelSide.RIGHT.name) ?: PanelSide.RIGHT.name)
        val initialWidthEnum = PanelWidth.valueOf(prefs.getString("panel_width", PanelWidth.THIRD.name) ?: PanelWidth.THIRD.name)
        _panelWidth.value = initialWidthEnum
        val defaultWidthPercent = when (initialWidthEnum) {
            PanelWidth.THIRD -> 33
            PanelWidth.HALF -> 50
            PanelWidth.TWO_THIRDS -> 66
        }
        _panelWidthPercent.value = prefs.getInt("panel_width_percent", defaultWidthPercent)
        _panelHeightPercent.value = prefs.getInt("panel_height_percent", 100)
        _panelOpacityPercent.value = prefs.getInt("panel_opacity_percent", 95)
        _themeMode.value = ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        _hideInLandscape.value = prefs.getBoolean("hide_in_landscape", false)

        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        if (_selectedFolderUri.value != null) {
            loadImages()
        }
    }

    private fun registerContentObserver(folderUri: Uri?) {
        contentObserver?.let {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        contentObserver = null

        if (folderUri == null) return

        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            private var lastTriggerTime = 0L
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime > 400L) { // Debounce rapid media updates
                    lastTriggerTime = now
                    loadImages()
                }
            }
        }

        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                folderUri,
                true,
                contentObserver!!
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        contentObserver?.let {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setHideInLandscape(hide: Boolean) {
        _hideInLandscape.value = hide
        prefs.edit().putBoolean("hide_in_landscape", hide).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setGridColumns(columns: Int) {
        _gridColumns.value = columns
        prefs.edit().putInt("grid_columns", columns).apply()
    }

    fun setTriggerType(type: TriggerType) {
        _triggerType.value = type
        prefs.edit().putString("trigger_type", type.name).apply()
        triggerGuidePreview(2500L)
    }

    fun setScrollDirection(direction: ScrollDirection) {
        _scrollDirection.value = direction
        prefs.edit().putString("scroll_direction", direction.name).apply()
    }

    fun completeOnboarding() {
        _hasCompletedOnboarding.value = true
        prefs.edit().putBoolean("has_completed_onboarding", true).apply()
    }

    fun resetOnboarding() {
        _hasCompletedOnboarding.value = false
        prefs.edit().putBoolean("has_completed_onboarding", false).apply()
    }

    fun setPanelHeightPercent(percent: Int) {
        val clamped = percent.coerceIn(20, 100)
        _panelHeightPercent.value = clamped
        prefs.edit().putInt("panel_height_percent", clamped).apply()
    }

    fun setPanelOpacityPercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _panelOpacityPercent.value = clamped
        prefs.edit().putInt("panel_opacity_percent", clamped).apply()
    }

    fun setSwipeHeightPercent(percent: Int, showGuide: Boolean = true) {
        val clamped = percent.coerceIn(10, 100)
        _swipeHeightPercent.value = clamped
        prefs.edit().putInt("swipe_height_percent", clamped).apply()
        if (showGuide) {
            triggerGuidePreview(2500L)
        }
    }

    fun triggerGuidePreview(durationMs: Long = 3000L) {
        val until = System.currentTimeMillis() + durationMs
        _guidePreviewUntil.value = until
        prefs.edit().putLong("guide_preview_until", until).apply()
    }
    
    fun setPanelSide(side: PanelSide) {
        _panelSide.value = side
        prefs.edit().putString("panel_side", side.name).apply()
        triggerGuidePreview(2500L)
    }
    
    fun setPanelWidth(width: PanelWidth) {
        _panelWidth.value = width
        val percent = when (width) {
            PanelWidth.THIRD -> 33
            PanelWidth.HALF -> 50
            PanelWidth.TWO_THIRDS -> 66
        }
        _panelWidthPercent.value = percent
        prefs.edit()
            .putString("panel_width", width.name)
            .putInt("panel_width_percent", percent)
            .apply()
    }

    fun setPanelWidthPercent(percent: Int) {
        val clamped = percent.coerceIn(20, 100)
        _panelWidthPercent.value = clamped
        val matchingEnum = when {
            clamped <= 40 -> PanelWidth.THIRD
            clamped <= 58 -> PanelWidth.HALF
            else -> PanelWidth.TWO_THIRDS
        }
        _panelWidth.value = matchingEnum
        prefs.edit()
            .putInt("panel_width_percent", clamped)
            .putString("panel_width", matchingEnum.name)
            .apply()
    }

    fun setFolderUri(uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        prefs.edit().putString("folder_uri", uri.toString()).apply()
        _selectedFolderUri.value = uri
        registerContentObserver(uri)
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
                val items = mutableListOf<GalleryItem>()
                val context = getApplication<Application>()
                var querySuccess = false

                // Fast batch cursor query via DocumentsContract
                try {
                    val docId = DocumentsContract.getTreeDocumentId(uri)
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        DocumentsContract.Document.COLUMN_SIZE
                    )

                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                        while (cursor.moveToNext()) {
                            var mimeType = if (mimeCol != -1) cursor.getString(mimeCol) else null
                            val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                            
                            val isImageByExtension = name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") || 
                                                     name.lowercase().endsWith(".png") || name.lowercase().endsWith(".gif") || 
                                                     name.lowercase().endsWith(".webp")
                            
                            if (mimeType == null || (!mimeType.startsWith("image/") && isImageByExtension)) {
                                mimeType = if (name.lowercase().endsWith(".gif")) "image/gif" else "image/jpeg"
                            }

                            if (mimeType != null && mimeType.startsWith("image/")) {
                                val itemDocId = if (idCol != -1) cursor.getString(idCol) else continue
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(uri, itemDocId)
                                val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                                var mod = if (modCol != -1) cursor.getLong(modCol) else 0L
                                val match = Regex("imported_(\\d+)").find(name)
                                if (match != null) {
                                    val timeFromName = match.groupValues[1].toLongOrNull()
                                    if (timeFromName != null && timeFromName > 0) {
                                        mod = timeFromName
                                    }
                                } else if (mod == 0L) {
                                    mod = System.currentTimeMillis() // Fallback so it appears at top if we can't read date
                                }
                                val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                                items.add(
                                    GalleryItem(
                                        uri = fileUri,
                                        name = name,
                                        dateModified = mod,
                                        size = size,
                                        isGif = mimeType == "image/gif"
                                    )
                                )
                            }
                        }
                        querySuccess = true
                    }
                } catch (e: Exception) {
                    querySuccess = false
                }

                // Fallback to DocumentFile if batch query is not supported by the document provider
                if (!querySuccess) {
                    items.clear()
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    documentFile?.listFiles()?.forEach { file ->
                        val mimeType = file.type
                        if (mimeType != null && mimeType.startsWith("image/")) {
                            val name = file.name ?: ""
                            var mod = file.lastModified()
                            val match = Regex("imported_(\\d+)").find(name)
                            if (match != null) {
                                val timeFromName = match.groupValues[1].toLongOrNull()
                                if (timeFromName != null && timeFromName > 0) {
                                    mod = timeFromName
                                }
                            } else if (mod == 0L) {
                                mod = System.currentTimeMillis() // Fallback so it appears at top
                            }
                            items.add(
                                GalleryItem(
                                    uri = file.uri,
                                    name = name,
                                    dateModified = mod,
                                    size = file.length(),
                                    isGif = mimeType == "image/gif"
                                )
                            )
                        }
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
                        var mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        var ext = "jpg"
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1) {
                                    val originalName = cursor.getString(nameIndex)
                                    if (originalName != null) {
                                        if (originalName.lowercase().endsWith(".gif")) {
                                            mimeType = "image/gif"
                                            ext = "gif"
                                        } else if (originalName.lowercase().endsWith(".png")) {
                                            mimeType = "image/png"
                                            ext = "png"
                                        } else if (originalName.lowercase().endsWith(".webp")) {
                                            mimeType = "image/webp"
                                            ext = "webp"
                                        } else if (originalName.lowercase().endsWith(".jpg") || originalName.lowercase().endsWith(".jpeg")) {
                                            mimeType = "image/jpeg"
                                            ext = "jpg"
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (ext == "jpg") {
                            ext = when {
                                mimeType.contains("gif") -> "gif"
                                mimeType.contains("png") -> "png"
                                mimeType.contains("webp") -> "webp"
                                else -> "jpg"
                            }
                        }
                        
                        val fileName = "imported_${System.currentTimeMillis()}.$ext"
                        val newFile = folder.createFile(mimeType, fileName)
                        if (newFile != null) {
                            context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                                context.contentResolver.openOutputStream(newFile.uri)?.buffered()?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            prefs.edit().putLong("last_media_update", System.currentTimeMillis()).apply()
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
            prefs.edit().putLong("last_media_update", System.currentTimeMillis()).apply()
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
