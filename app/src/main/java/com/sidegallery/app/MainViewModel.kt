package com.sidegallery.app

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
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class GalleryItem(
    val uri: Uri,
    val name: String,
    val dateModified: Long,
    val size: Long,
    val isGif: Boolean,
    val isVideo: Boolean = false,
    val isPinned: Boolean = false,
    val folderId: String = "",
    val tags: List<String> = emptyList()
)

data class GalleryFolder(
    val id: String,
    val name: String,
    val uriString: String,
    val isSpecialPinned: Boolean = false
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

    companion object {
        const val PREFS_NAME = "side_gallery_prefs"
        const val PINNED_FOLDER_ID = "special_pinned"
    }

    private val prefs: SharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val folderCache = java.util.concurrent.ConcurrentHashMap<String, List<GalleryItem>>()
    private var loadJob: kotlinx.coroutines.Job? = null

    private val _folders = MutableStateFlow<List<GalleryFolder>>(emptyList())
    val folders: StateFlow<List<GalleryFolder>> = _folders.asStateFlow()

    private val _currentFolderIndex = MutableStateFlow(0)
    val currentFolderIndex: StateFlow<Int> = _currentFolderIndex.asStateFlow()

    private val _currentFolder = MutableStateFlow<GalleryFolder?>(null)
    val currentFolder: StateFlow<GalleryFolder?> = _currentFolder.asStateFlow()

    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri.asStateFlow()

    private val _pinnedItemUris = MutableStateFlow<Set<String>>(emptySet())
    val pinnedItemUris: StateFlow<Set<String>> = _pinnedItemUris.asStateFlow()

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

    private val contentObservers = mutableListOf<ContentObserver>()

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
            "gallery_folders_json" -> reloadFoldersFromPrefs()
            "pinned_items_set" -> {
                _pinnedItemUris.value = sharedPreferences.getStringSet("pinned_items_set", emptySet()) ?: emptySet()
                loadImages()
            }
        }
    }

    init {
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
        _pinnedItemUris.value = prefs.getStringSet("pinned_items_set", emptySet()) ?: emptySet()

        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        reloadFoldersFromPrefs()
    }

    private fun reloadFoldersFromPrefs() {
        val jsonStr = prefs.getString("gallery_folders_json", null)
        val list = mutableListOf<GalleryFolder>()

        // 1. Always ensure Special Pinned folder is present at the beginning
        list.add(
            GalleryFolder(
                id = PINNED_FOLDER_ID,
                name = "Pinned",
                uriString = "",
                isSpecialPinned = true
            )
        )

        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val isSpecial = obj.optBoolean("isSpecialPinned", false)
                    if (!isSpecial) {
                        val rawName = obj.getString("name")
                        val cleanName = rawName.replace("⭐", "").replace("★", "").trim().ifBlank { "Media" }
                        list.add(
                            GalleryFolder(
                                id = obj.getString("id"),
                                name = cleanName,
                                uriString = obj.getString("uriString"),
                                isSpecialPinned = false
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Migration from legacy folder_uri if list has only the pinned folder
        if (list.size == 1) {
            val legacyUriStr = prefs.getString("folder_uri", null)
            if (legacyUriStr != null) {
                val defaultFolder = GalleryFolder(
                    id = UUID.randomUUID().toString(),
                    name = "Media",
                    uriString = legacyUriStr,
                    isSpecialPinned = false
                )
                list.add(defaultFolder)
                persistFolders(list)
            }
        }

        _folders.value = list

        val savedIndex = prefs.getInt("current_folder_index", if (list.size > 1) 1 else 0)
        val validIndex = savedIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        _currentFolderIndex.value = validIndex
        val curr = list.getOrNull(validIndex)
        _currentFolder.value = curr
        _selectedFolderUri.value = if (curr != null && !curr.isSpecialPinned && curr.uriString.isNotBlank()) Uri.parse(curr.uriString) else null

        registerAllObservers(list)
        loadImages(showSpinnerIfCached = true)
        preloadAllFolders()
    }

    private fun persistFolders(foldersList: List<GalleryFolder>) {
        val userFolders = foldersList.filter { !it.isSpecialPinned }
        val array = JSONArray()
        for (f in userFolders) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("name", f.name)
            obj.put("uriString", f.uriString)
            obj.put("isSpecialPinned", false)
            array.put(obj)
        }
        prefs.edit().putString("gallery_folders_json", array.toString()).apply()
    }

    private fun registerAllObservers(folderList: List<GalleryFolder>) {
        val resolver = getApplication<Application>().contentResolver
        for (obs in contentObservers) {
            try {
                resolver.unregisterContentObserver(obs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        contentObservers.clear()

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            private var lastTriggerTime = 0L
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime > 800L) {
                    lastTriggerTime = now
                    loadImages()
                }
            }
        }
        contentObservers.add(observer)

        // Only register observers on the specific configured folder URIs to minimize battery & background wakeups
        for (folder in folderList) {
            if (!folder.isSpecialPinned && folder.uriString.isNotBlank()) {
                try {
                    val parsed = Uri.parse(folder.uriString)
                    resolver.registerContentObserver(parsed, true, observer)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        val resolver = getApplication<Application>().contentResolver
        for (obs in contentObservers) {
            try {
                resolver.unregisterContentObserver(obs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        contentObservers.clear()
    }

    // --- Folder Operations ---

    fun addFolder(uri: Uri, customName: String? = null) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val name = if (!customName.isNullOrBlank()) {
            customName.trim()
        } else {
            val doc = DocumentFile.fromTreeUri(getApplication(), uri)
            doc?.name ?: "Folder ${_folders.value.count { !it.isSpecialPinned } + 1}"
        }

        addFolderWithDetails(name, uri.toString())
    }

    fun addFolderWithDetails(name: String, uriString: String) {
        val newFolder = GalleryFolder(
            id = UUID.randomUUID().toString(),
            name = name,
            uriString = uriString,
            isSpecialPinned = false
        )

        val updated = _folders.value.toMutableList()
        updated.add(newFolder)
        _folders.value = updated
        persistFolders(updated)

        // Select the newly added folder
        selectFolder(updated.size - 1)
        if (uriString.isNotBlank()) {
            prefs.edit().putString("folder_uri", uriString).apply()
        }
    }

    fun renameFolder(id: String, newName: String) {
        if (id == PINNED_FOLDER_ID || newName.isBlank()) return
        val updated = _folders.value.map {
            if (it.id == id) it.copy(name = newName.trim()) else it
        }
        _folders.value = updated
        persistFolders(updated)
        _currentFolder.value = updated.getOrNull(_currentFolderIndex.value)
    }

    fun removeFolder(id: String) {
        if (id == PINNED_FOLDER_ID) return
        val updated = _folders.value.filter { it.id != id }
        _folders.value = updated
        persistFolders(updated)

        val newIndex = _currentFolderIndex.value.coerceIn(0, (updated.size - 1).coerceAtLeast(0))
        selectFolder(newIndex)
    }

    fun selectFolder(index: Int) {
        val list = _folders.value
        if (list.isEmpty()) return
        val safeIndex = index.coerceIn(0, list.size - 1)
        _currentFolderIndex.value = safeIndex
        prefs.edit().putInt("current_folder_index", safeIndex).apply()

        val folder = list[safeIndex]
        _currentFolder.value = folder
        _selectedFolderUri.value = if (!folder.isSpecialPinned && folder.uriString.isNotBlank()) Uri.parse(folder.uriString) else null
        
        if (!folder.isSpecialPinned && folder.uriString.isNotBlank()) {
            prefs.edit().putString("folder_uri", folder.uriString).apply()
        }

        // Instant UI transition from cache!
        val cached = if (folder.isSpecialPinned) {
            val pinnedSet = _pinnedItemUris.value
            val allPinned = mutableListOf<GalleryItem>()
            for (f in list.filter { !it.isSpecialPinned }) {
                val fItems = folderCache[f.id] ?: emptyList()
                allPinned.addAll(fItems.filter { pinnedSet.contains(it.uri.toString()) })
            }
            allPinned
        } else {
            folderCache[folder.id]
        }

        if (cached != null) {
            _images.value = sortItemList(cached, _sortOption.value)
            _isLoading.value = false
        } else {
            // Clear immediately when switching so previous folder items never bleed into new folder!
            _images.value = emptyList()
            _isLoading.value = true
        }

        // Asynchronously refresh in background so data is always completely up to date
        loadImages(showSpinnerIfCached = false)
    }

    fun nextFolder() {
        val list = _folders.value
        if (list.size <= 1) return
        val nextIdx = (_currentFolderIndex.value + 1) % list.size
        selectFolder(nextIdx)
    }

    fun previousFolder() {
        val list = _folders.value
        if (list.size <= 1) return
        val prevIdx = if (_currentFolderIndex.value - 1 < 0) list.size - 1 else _currentFolderIndex.value - 1
        selectFolder(prevIdx)
    }

    // --- Pinned Items Management ---

    fun isItemPinned(item: GalleryItem): Boolean {
        return _pinnedItemUris.value.contains(item.uri.toString())
    }

    fun togglePin(item: GalleryItem) {
        val currentSet = _pinnedItemUris.value.toMutableSet()
        val key = item.uri.toString()
        val willBePinned = if (currentSet.contains(key)) {
            currentSet.remove(key)
            false
        } else {
            currentSet.add(key)
            true
        }
        _pinnedItemUris.value = currentSet
        prefs.edit().putStringSet("pinned_items_set", currentSet).apply()

        // Instantly update current list in memory
        val updatedCurrent = _images.value.map {
            if (it.uri == item.uri) it.copy(isPinned = willBePinned) else it
        }
        _images.value = sortItemList(updatedCurrent, _sortOption.value)

        // Also update folder cache
        folderCache.forEach { (folderId, items) ->
            folderCache[folderId] = items.map {
                if (it.uri == item.uri) it.copy(isPinned = willBePinned) else it
            }
        }
    }

    // --- Settings & UI Toggles ---

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
        addFolder(uri)
    }

    fun setSortOption(option: SortOption) {
        prefs.edit().putString("sort_option", option.name).apply()
        _sortOption.value = option
        applySorting()
    }

    // --- Loading & Querying (Images + Videos + Pinned) ---

    fun loadImages(showSpinnerIfCached: Boolean = false) {
        val currFolder = _currentFolder.value ?: return
        val targetFolderId = currFolder.id
        val pinnedSet = _pinnedItemUris.value
        val context = getApplication<Application>()
        
        val isCached = if (currFolder.isSpecialPinned) {
            folderCache.isNotEmpty()
        } else {
            folderCache.containsKey(currFolder.id)
        }

        if (!isCached || showSpinnerIfCached) {
            if (_images.value.isEmpty()) {
                _isLoading.value = true
            }
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loadedItems = withContext(Dispatchers.IO) {
                val results = mutableListOf<GalleryItem>()

                if (currFolder.isSpecialPinned) {
                    // Load pinned items from all user folders
                    val regularFolders = _folders.value.filter { !it.isSpecialPinned && it.uriString.isNotBlank() }
                    for (f in regularFolders) {
                        val parsedUri = Uri.parse(f.uriString)
                        val folderItems = queryMediaFromFolder(context, parsedUri, f.id, pinnedSet)
                        folderCache[f.id] = folderItems
                        results.addAll(folderItems.filter { it.isPinned })
                    }
                } else if (currFolder.uriString.isNotBlank()) {
                    val parsedUri = Uri.parse(currFolder.uriString)
                    val folderItems = queryMediaFromFolder(context, parsedUri, currFolder.id, pinnedSet)
                    folderCache[currFolder.id] = folderItems
                    results.addAll(folderItems)
                }

                results
            }

            // Only apply to UI if this is still the active folder!
            if (isActive && _currentFolder.value?.id == targetFolderId) {
                _images.value = sortItemList(loadedItems, _sortOption.value)
                _isLoading.value = false
            }
        }
    }

    fun refreshAllMedia() {
        folderCache.clear()
        _images.value = emptyList()
        loadImages(showSpinnerIfCached = true)
        preloadAllFolders()
    }

    fun preloadAllFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val pinnedSet = _pinnedItemUris.value
                val regularFolders = _folders.value.filter { !it.isSpecialPinned && it.uriString.isNotBlank() }
                for (f in regularFolders) {
                    val parsedUri = Uri.parse(f.uriString)
                    val folderItems = queryMediaFromFolder(context, parsedUri, f.id, pinnedSet)
                    folderCache[f.id] = folderItems
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun generateAutoTags(filename: String): List<String> {
        val nameWithoutExt = filename.substringBeforeLast('.')
        val clean = nameWithoutExt.replace(Regex("[_\\-+\\.,!@#$%^&*()\\[\\]{}|;:'\"<>/\\\\?`~]"), " ")
        return clean.split(Regex("\\s+"))
            .map { it.trim().lowercase() }
            .filter { it.length >= 2 && !it.startsWith("imported") && it.toLongOrNull() == null }
            .distinct()
    }

    private fun loadCustomTagsMap(): Map<String, List<String>> {
        val jsonStr = prefs.getString("media_tags_json", null) ?: return emptyMap()
        val map = mutableMapOf<String, List<String>>()
        try {
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = obj.optJSONArray(key)
                if (arr != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val t = arr.optString(i)?.trim()?.lowercase()
                        if (!t.isNullOrEmpty()) list.add(t)
                    }
                    map[key] = list
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun saveCustomTagsMap(map: Map<String, List<String>>) {
        try {
            val obj = JSONObject()
            for ((k, list) in map) {
                val arr = JSONArray()
                list.forEach { arr.put(it) }
                obj.put(k, arr)
            }
            prefs.edit().putString("media_tags_json", obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setItemTags(item: GalleryItem, newTags: List<String>) {
        val cleanTags = newTags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        val map = loadCustomTagsMap().toMutableMap()
        val uriKey = item.uri.toString()
        if (cleanTags.isEmpty()) {
            map.remove(uriKey)
        } else {
            map[uriKey] = cleanTags
        }
        saveCustomTagsMap(map)

        val effectiveTags = if (cleanTags.isEmpty()) generateAutoTags(item.name) else cleanTags
        _images.value = _images.value.map {
            if (it.uri == item.uri) it.copy(tags = effectiveTags) else it
        }

        folderCache.clear()
        prefs.edit().putLong("last_media_update", System.currentTimeMillis()).apply()
    }

    private fun queryMediaFromFolder(
        context: Context,
        folderUri: Uri,
        folderId: String,
        pinnedSet: Set<String>
    ): List<GalleryItem> {
        val items = mutableListOf<GalleryItem>()
        val customTagsMap = loadCustomTagsMap()
        var querySuccess = false

        // Fast batch cursor query via DocumentsContract
        try {
            val docId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, docId)
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
                    val lowerName = name.lowercase()

                    val isImageExt = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                            lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                            lowerName.endsWith(".webp")

                    val isVideoExt = lowerName.endsWith(".mp4") || lowerName.endsWith(".webm") ||
                            lowerName.endsWith(".mkv") || lowerName.endsWith(".mov") ||
                            lowerName.endsWith(".3gp") || lowerName.endsWith(".avi") ||
                            lowerName.endsWith(".ts")

                    if (mimeType == null) {
                        if (lowerName.endsWith(".gif")) mimeType = "image/gif"
                        else if (isImageExt) mimeType = "image/jpeg"
                        else if (isVideoExt) mimeType = "video/mp4"
                    }

                    val isImg = mimeType != null && (mimeType.startsWith("image/") || isImageExt)
                    val isVid = mimeType != null && (mimeType.startsWith("video/") || isVideoExt)

                    if (isImg || isVid) {
                        val itemDocId = if (idCol != -1) cursor.getString(idCol) else continue
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, itemDocId)
                        var mod = if (modCol != -1) cursor.getLong(modCol) else 0L
                        val match = Regex("imported_(\\d+)").find(name)
                        if (match != null) {
                            val timeFromName = match.groupValues[1].toLongOrNull()
                            if (timeFromName != null && timeFromName > 0) {
                                mod = timeFromName
                            }
                        } else if (mod == 0L) {
                            mod = System.currentTimeMillis()
                        }
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                        val isGif = mimeType == "image/gif" || lowerName.endsWith(".gif")
                        val isPinned = pinnedSet.contains(fileUri.toString())
                        val uriKey = fileUri.toString()
                        val itemTags = customTagsMap[uriKey] ?: generateAutoTags(name)

                        items.add(
                            GalleryItem(
                                uri = fileUri,
                                name = name,
                                dateModified = mod,
                                size = size,
                                isGif = isGif,
                                isVideo = isVid,
                                isPinned = isPinned,
                                folderId = folderId,
                                tags = itemTags
                            )
                        )
                    }
                }
                querySuccess = true
            }
        } catch (e: Exception) {
            querySuccess = false
        }

        // Fallback to DocumentFile
        if (!querySuccess) {
            items.clear()
            val documentFile = DocumentFile.fromTreeUri(context, folderUri)
            documentFile?.listFiles()?.forEach { file ->
                val mimeType = file.type ?: ""
                val name = file.name ?: ""
                val lowerName = name.lowercase()

                val isImageExt = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                        lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                        lowerName.endsWith(".webp")

                val isVideoExt = lowerName.endsWith(".mp4") || lowerName.endsWith(".webm") ||
                        lowerName.endsWith(".mkv") || lowerName.endsWith(".mov") ||
                        lowerName.endsWith(".3gp") || lowerName.endsWith(".avi") ||
                        lowerName.endsWith(".ts")

                val isImg = mimeType.startsWith("image/") || isImageExt
                val isVid = mimeType.startsWith("video/") || isVideoExt

                if (isImg || isVid) {
                    var mod = file.lastModified()
                    val match = Regex("imported_(\\d+)").find(name)
                    if (match != null) {
                        val timeFromName = match.groupValues[1].toLongOrNull()
                        if (timeFromName != null && timeFromName > 0) {
                            mod = timeFromName
                        }
                    } else if (mod == 0L) {
                        mod = System.currentTimeMillis()
                    }
                    val isGif = mimeType == "image/gif" || lowerName.endsWith(".gif")
                    val isPinned = pinnedSet.contains(file.uri.toString())
                    val uriKey = file.uri.toString()
                    val itemTags = customTagsMap[uriKey] ?: generateAutoTags(name)

                    items.add(
                        GalleryItem(
                            uri = file.uri,
                            name = name,
                            dateModified = mod,
                            size = file.length(),
                            isGif = isGif,
                            isVideo = isVid,
                            isPinned = isPinned,
                            folderId = folderId,
                            tags = itemTags
                        )
                    )
                }
            }
        }
        return items
    }

    fun importMedia(
        context: Context,
        uris: List<Uri>,
        targetFolder: GalleryFolder? = null,
        targetFolderId: String? = null
    ) {
        val folderToUse = targetFolder ?: run {
            if (targetFolderId != null) {
                _folders.value.find { it.id == targetFolderId }
            } else {
                _currentFolder.value
            }
        }
        if (folderToUse == null || folderToUse.isSpecialPinned || folderToUse.uriString.isBlank()) {
            return
        }
        val folderUri = Uri.parse(folderToUse.uriString)

        _isLoading.value = true
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
            var convertedGifsCount = 0
            var importedImagesCount = 0

            run {
                val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@run
                for (uri in uris) {
                    try {
                        var mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        var isVideo = mimeType.startsWith("video/")
                        var ext = "jpg"
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1) {
                                    val originalName = cursor.getString(nameIndex)
                                    if (originalName != null) {
                                        val lower = originalName.lowercase()
                                        when {
                                            lower.endsWith(".gif") -> { mimeType = "image/gif"; ext = "gif"; isVideo = false }
                                            lower.endsWith(".png") -> { mimeType = "image/png"; ext = "png"; isVideo = false }
                                            lower.endsWith(".webp") -> { mimeType = "image/webp"; ext = "webp"; isVideo = false }
                                            lower.endsWith(".mp4") -> { mimeType = "video/mp4"; ext = "mp4"; isVideo = true }
                                            lower.endsWith(".webm") -> { mimeType = "video/webm"; ext = "webm"; isVideo = true }
                                            lower.endsWith(".mkv") -> { mimeType = "video/x-matroska"; ext = "mkv"; isVideo = true }
                                            lower.endsWith(".mov") -> { mimeType = "video/quicktime"; ext = "mov"; isVideo = true }
                                            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> { mimeType = "image/jpeg"; ext = "jpg"; isVideo = false }
                                        }
                                    }
                                }
                            }
                        }

                        if (isVideo) {
                            // Convert video into animated GIF (taking up to 15 seconds)
                            val tempFile = File(context.cacheDir, "import_conv_${System.currentTimeMillis()}.gif")
                            val converted = GifConverter.convertVideoToGif(context, uri, tempFile, targetWidth = 320, fps = 8)
                            if (converted && tempFile.exists()) {
                                val fileName = "imported_${System.currentTimeMillis()}.gif"
                                val newFile = folder.createFile("image/gif", fileName)
                                if (newFile != null) {
                                    tempFile.inputStream().buffered().use { input ->
                                        context.contentResolver.openOutputStream(newFile.uri)?.buffered()?.use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    convertedGifsCount++
                                }
                                tempFile.delete()
                            } else {
                                // Fallback: save original video file if GIF conversion couldn't read stream
                                val fileName = "imported_${System.currentTimeMillis()}.$ext"
                                val newFile = folder.createFile(mimeType, fileName)
                                if (newFile != null) {
                                    context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                                        context.contentResolver.openOutputStream(newFile.uri)?.buffered()?.use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            }
                        } else {
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
                                importedImagesCount++
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (convertedGifsCount > 0) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Converted $convertedGifsCount video(s) into animated GIF(s)!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Invalidate folder cache so new media is immediately displayed
            folderCache.remove(folderToUse.id)
            prefs.edit().putLong("last_media_update", System.currentTimeMillis()).apply()
            loadImages(showSpinnerIfCached = true)
        }
    }

    fun importImages(context: Context, uris: List<Uri>) {
        importMedia(context, uris, null)
    }

    fun deleteItem(context: Context, item: GalleryItem, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val file = DocumentFile.fromSingleUri(context, item.uri)
                    if (file != null && file.exists()) {
                        file.delete()
                    } else false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            // Also remove from pinned set if was pinned
            val key = item.uri.toString()
            if (_pinnedItemUris.value.contains(key)) {
                val updatedPins = _pinnedItemUris.value.toMutableSet()
                updatedPins.remove(key)
                _pinnedItemUris.value = updatedPins
                prefs.edit().putStringSet("pinned_items_set", updatedPins).apply()
            }

            prefs.edit().putLong("last_media_update", System.currentTimeMillis()).apply()
            loadImages()
            onComplete?.invoke(success)
        }
    }

    fun deleteImage(context: Context, uri: Uri) {
        val item = _images.value.find { it.uri == uri }
        if (item != null) {
            deleteItem(context, item)
        } else {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val file = DocumentFile.fromSingleUri(context, uri)
                        file?.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                prefs.edit().putLong("last_media_update", System.currentTimeMillis()).apply()
                loadImages()
            }
        }
    }

    fun sortItemList(items: List<GalleryItem>, option: SortOption): List<GalleryItem> {
        val (pinned, unpinned) = items.partition { it.isPinned }

        val sortBlock: (GalleryItem) -> Comparable<*>? = when (option) {
            SortOption.NAME_ASC, SortOption.NAME_DESC -> { item -> item.name.lowercase() }
            SortOption.DATE_NEWEST, SortOption.DATE_OLDEST -> { item -> item.dateModified }
            SortOption.SIZE_LARGEST, SortOption.SIZE_SMALLEST -> { item -> item.size }
        }

        val isDesc = option == SortOption.NAME_DESC || option == SortOption.DATE_NEWEST || option == SortOption.SIZE_LARGEST

        val sortedPinned = if (isDesc) {
            @Suppress("UNCHECKED_CAST")
            pinned.sortedWith(compareByDescending(sortBlock as (GalleryItem) -> Comparable<Any>?))
        } else {
            @Suppress("UNCHECKED_CAST")
            pinned.sortedWith(compareBy(sortBlock as (GalleryItem) -> Comparable<Any>?))
        }

        val sortedUnpinned = if (isDesc) {
            @Suppress("UNCHECKED_CAST")
            unpinned.sortedWith(compareByDescending(sortBlock as (GalleryItem) -> Comparable<Any>?))
        } else {
            @Suppress("UNCHECKED_CAST")
            unpinned.sortedWith(compareBy(sortBlock as (GalleryItem) -> Comparable<Any>?))
        }

        return sortedPinned + sortedUnpinned
    }

    fun importVideoWithTrim(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        targetFolder: GalleryFolder? = null,
        targetFolderId: String? = null
    ) {
        val folderToUse = targetFolder ?: run {
            if (targetFolderId != null) {
                _folders.value.find { it.id == targetFolderId }
            } else {
                _currentFolder.value
            }
        }
        if (folderToUse == null || folderToUse.isSpecialPinned || folderToUse.uriString.isBlank()) {
            return
        }
        val folderUri = Uri.parse(folderToUse.uriString)

        _isLoading.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val folder = DocumentFile.fromTreeUri(context, folderUri)
                    if (folder != null) {
                        val tempFile = File(context.cacheDir, "import_trim_${System.currentTimeMillis()}.gif")
                        val converted = GifConverter.convertVideoToGif(
                            context = context,
                            videoUri = uri,
                            destFile = tempFile,
                            targetWidth = 320,
                            fps = 8,
                            startMs = startMs,
                            endMs = endMs
                        )
                        if (converted && tempFile.exists()) {
                            val fileName = "imported_${System.currentTimeMillis()}.gif"
                            val newFile = folder.createFile("image/gif", fileName)
                            if (newFile != null) {
                                tempFile.inputStream().buffered().use { input ->
                                    context.contentResolver.openOutputStream(newFile.uri)?.buffered().use { output ->
                                        if (output != null) input.copyTo(output)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Trimmed video converted to animated GIF!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            tempFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                folderCache.remove(folderToUse.id)
                prefs.edit().putLong("last_media_update", System.currentTimeMillis()).apply()
            }
            loadImages(showSpinnerIfCached = true)
        }
    }

    fun exportSettingsJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())

        val settingsObj = JSONObject()
        settingsObj.put("grid_columns", _gridColumns.value)
        settingsObj.put("trigger_type", _triggerType.value.name)
        settingsObj.put("scroll_direction", _scrollDirection.value.name)
        settingsObj.put("swipe_height_percent", _swipeHeightPercent.value)
        settingsObj.put("panel_side", _panelSide.value.name)
        settingsObj.put("panel_width", _panelWidth.value.name)
        settingsObj.put("panel_width_percent", _panelWidthPercent.value)
        settingsObj.put("panel_height_percent", _panelHeightPercent.value)
        settingsObj.put("panel_opacity_percent", _panelOpacityPercent.value)
        settingsObj.put("theme_mode", _themeMode.value.name)
        settingsObj.put("hide_in_landscape", _hideInLandscape.value)
        settingsObj.put("sort_option", _sortOption.value.name)
        root.put("settings", settingsObj)

        val userFolders = _folders.value.filter { !it.isSpecialPinned }
        val foldersArr = JSONArray()
        for (f in userFolders) {
            val fObj = JSONObject()
            fObj.put("id", f.id)
            fObj.put("name", f.name)
            fObj.put("uriString", f.uriString)
            foldersArr.put(fObj)
        }
        root.put("folders", foldersArr)

        val pinnedArr = JSONArray()
        for (p in _pinnedItemUris.value) {
            pinnedArr.put(p)
        }
        root.put("pinned_item_uris", pinnedArr)

        val customTags = loadCustomTagsMap()
        val tagsObj = JSONObject()
        for ((uriKey, tagList) in customTags) {
            val tArr = JSONArray()
            tagList.forEach { tArr.put(it) }
            tagsObj.put(uriKey, tArr)
        }
        root.put("media_tags", tagsObj)

        return root.toString(2)
    }

    fun exportSettingsToUri(context: Context, destinationUri: Uri): Boolean {
        return try {
            val json = exportSettingsJson()
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importSettingsFromUri(context: Context, sourceUri: Uri): Boolean {
        return try {
            val jsonStr = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return false
            importSettingsJson(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importSettingsJson(jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)
            val version = root.optInt("version", 1)

            val editor = prefs.edit()

            if (root.has("settings")) {
                val s = root.getJSONObject("settings")
                if (s.has("grid_columns")) editor.putInt("grid_columns", s.getInt("grid_columns"))
                if (s.has("trigger_type")) editor.putString("trigger_type", s.getString("trigger_type"))
                if (s.has("scroll_direction")) editor.putString("scroll_direction", s.getString("scroll_direction"))
                if (s.has("swipe_height_percent")) editor.putInt("swipe_height_percent", s.getInt("swipe_height_percent"))
                if (s.has("panel_side")) editor.putString("panel_side", s.getString("panel_side"))
                if (s.has("panel_width")) editor.putString("panel_width", s.getString("panel_width"))
                if (s.has("panel_width_percent")) editor.putInt("panel_width_percent", s.getInt("panel_width_percent"))
                if (s.has("panel_height_percent")) editor.putInt("panel_height_percent", s.getInt("panel_height_percent"))
                if (s.has("panel_opacity_percent")) editor.putInt("panel_opacity_percent", s.getInt("panel_opacity_percent"))
                if (s.has("theme_mode")) editor.putString("theme_mode", s.getString("theme_mode"))
                if (s.has("hide_in_landscape")) editor.putBoolean("hide_in_landscape", s.getBoolean("hide_in_landscape"))
                if (s.has("sort_option")) editor.putString("sort_option", s.getString("sort_option"))
            }

            if (root.has("folders")) {
                val arr = root.getJSONArray("folders")
                val foldersArr = JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val fObj = JSONObject()
                    fObj.put("id", item.optString("id", UUID.randomUUID().toString()))
                    fObj.put("name", item.optString("name", "Media"))
                    fObj.put("uriString", item.optString("uriString", ""))
                    fObj.put("isSpecialPinned", false)
                    foldersArr.put(fObj)
                }
                editor.putString("gallery_folders_json", foldersArr.toString())
            }

            if (root.has("pinned_item_uris")) {
                val arr = root.getJSONArray("pinned_item_uris")
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i)
                    if (!u.isNullOrEmpty()) set.add(u)
                }
                editor.putStringSet("pinned_items_set", set)
            }

            if (root.has("media_tags")) {
                val tagsObj = root.getJSONObject("media_tags")
                editor.putString("media_tags_json", tagsObj.toString())
            }

            editor.putLong("last_media_update", System.currentTimeMillis())
            editor.apply()

            // Reload all in-memory values from prefs
            _gridColumns.value = prefs.getInt("grid_columns", 2)
            _triggerType.value = TriggerType.valueOf(prefs.getString("trigger_type", TriggerType.EDGE_SWIPE.name) ?: TriggerType.EDGE_SWIPE.name)
            _scrollDirection.value = ScrollDirection.valueOf(prefs.getString("scroll_direction", ScrollDirection.TOP_TO_BOTTOM.name) ?: ScrollDirection.TOP_TO_BOTTOM.name)
            _swipeHeightPercent.value = prefs.getInt("swipe_height_percent", 70)
            _panelSide.value = PanelSide.valueOf(prefs.getString("panel_side", PanelSide.RIGHT.name) ?: PanelSide.RIGHT.name)
            _panelWidth.value = PanelWidth.valueOf(prefs.getString("panel_width", PanelWidth.THIRD.name) ?: PanelWidth.THIRD.name)
            _panelWidthPercent.value = prefs.getInt("panel_width_percent", 33)
            _panelHeightPercent.value = prefs.getInt("panel_height_percent", 100)
            _panelOpacityPercent.value = prefs.getInt("panel_opacity_percent", 95)
            _themeMode.value = ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
            _hideInLandscape.value = prefs.getBoolean("hide_in_landscape", false)
            _sortOption.value = SortOption.valueOf(prefs.getString("sort_option", SortOption.DATE_NEWEST.name) ?: SortOption.DATE_NEWEST.name)
            _pinnedItemUris.value = prefs.getStringSet("pinned_items_set", emptySet()) ?: emptySet()

            reloadFoldersFromPrefs()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun applySorting() {
        _images.value = sortItemList(_images.value, _sortOption.value)
    }
}
