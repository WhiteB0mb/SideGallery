package com.sidegallery.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.sidegallery.app.ui.theme.SideGalleryTheme
import com.sidegallery.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_STOP = "com.example.ACTION_STOP"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRunning.asStateFlow()
        var activeInstance: OverlayService? = null
            private set
    }

    private val currentOrientation = MutableStateFlow(Configuration.ORIENTATION_PORTRAIT)

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore
        get() = store
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private var isOverlayAdded = false
    lateinit var viewModel: MainViewModel
        private set
    private var windowX = 0
    private var windowY = 100

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        _isRunning.value = true
        currentOrientation.value = resources.configuration.orientation
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(this, "sidegallery_channel")
            .setContentTitle("SideGallery is active")
            .setContentText("Tap to open dashboard")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        viewModel = ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MainViewModel::class.java]

        setupOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentOrientation.value = newConfig.orientation
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "sidegallery_channel",
                "SideGallery Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        viewModel.loadImages()
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            
            val imageLoader = ImageLoader.Builder(context)
                .crossfade(true)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                    add(VideoFrameDecoder.Factory())
                }
                .memoryCache {
                    coil.memory.MemoryCache.Builder(context)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.05)
                        .build()
                }
                .build()

            setContent {
                val themeMode by viewModel.themeMode.collectAsState()
                val darkTheme = when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }

                val currentOri by currentOrientation.collectAsState()

                SideGalleryTheme(darkTheme = darkTheme) {
                    OverlayContent(
                        viewModel = viewModel, 
                        imageLoader = imageLoader, 
                        orientation = currentOri,
                        onUpdateWindowParams = { expanded, triggerType, panelSide, shouldHide, isGuideActive, swipeHeightPercent ->
                            if (shouldHide) {
                                composeView.visibility = View.GONE
                                layoutParams.width = 0
                                layoutParams.height = 0
                                layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            } else {
                                composeView.visibility = View.VISIBLE
                                layoutParams.flags = layoutParams.flags and (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv())
                                if (expanded) {
                                    layoutParams.gravity = Gravity.START or Gravity.TOP
                                    layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                                    layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
                                    layoutParams.x = 0
                                    layoutParams.y = 0
                                } else {
                                    if (triggerType == TriggerType.FLOATING_BUTTON) {
                                        layoutParams.gravity = Gravity.START or Gravity.TOP
                                        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                                        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                                        layoutParams.x = windowX
                                        layoutParams.y = windowY
                                    } else {
                                        val horizontalGravity = if (panelSide == PanelSide.LEFT) Gravity.START else Gravity.END
                                        layoutParams.gravity = horizontalGravity or Gravity.TOP
                                        val density = resources.displayMetrics.density
                                        layoutParams.width = if (isGuideActive) (24 * density).toInt() else (16 * density).toInt()
                                        val percent = (swipeHeightPercent.coerceIn(10, 100)) / 100f
                                        layoutParams.height = (resources.displayMetrics.heightPixels * percent).toInt()
                                        layoutParams.y = 0
                                        layoutParams.x = 0
                                    }
                                }
                            }
                            try { windowManager.updateViewLayout(this@apply, layoutParams) } catch (e: Exception) {}
                        },
                        onDragBubble = { dx, dy ->
                            windowX += dx.toInt()
                            windowY += dy.toInt()
                            layoutParams.x = windowX
                            layoutParams.y = windowY
                            try { windowManager.updateViewLayout(this@apply, layoutParams) } catch (e: Exception) {}
                            
                            val metrics = resources.displayMetrics
                            windowY > metrics.heightPixels - 400
                        },
                        onCloseService = {
                            stopSelf()
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
            isOverlayAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
        }
        _isRunning.value = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        if (isOverlayAdded) {
            try {
                windowManager.removeView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isOverlayAdded = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayContent(
    viewModel: MainViewModel, 
    imageLoader: ImageLoader, 
    orientation: Int,
    onUpdateWindowParams: (Boolean, TriggerType, PanelSide, Boolean, Boolean, Int) -> Unit,
    onDragBubble: (Float, Float) -> Boolean,
    onCloseService: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Floating Context Menu State for long-pressed item
    var activeContextItem by remember { mutableStateOf<GalleryItem?>(null) }
    var itemToDeleteConfirm by remember { mutableStateOf<GalleryItem?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    val folders by viewModel.folders.collectAsState()
    val currentFolderIndex by viewModel.currentFolderIndex.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val gridColumns by viewModel.gridColumns.collectAsState()
    val panelSide by viewModel.panelSide.collectAsState()
    val panelWidthPercent by viewModel.panelWidthPercent.collectAsState()
    val panelHeightPercent by viewModel.panelHeightPercent.collectAsState()
    val panelOpacityPercent by viewModel.panelOpacityPercent.collectAsState()
    val triggerType by viewModel.triggerType.collectAsState()
    val scrollDirection by viewModel.scrollDirection.collectAsState()
    val hideInLandscape by viewModel.hideInLandscape.collectAsState()
    val swipeHeightPercent by viewModel.swipeHeightPercent.collectAsState()
    val guidePreviewUntil by viewModel.guidePreviewUntil.collectAsState()

    var isGuideActive by remember { mutableStateOf(false) }

    LaunchedEffect(guidePreviewUntil) {
        val remaining = guidePreviewUntil - System.currentTimeMillis()
        if (remaining > 0) {
            isGuideActive = true
            kotlinx.coroutines.delay(remaining)
        }
        isGuideActive = false
    }

    var isNearBottom by remember { mutableStateOf(false) }

    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    val shouldHide = isLandscape && hideInLandscape && !expanded

    fun toggleExpand(expand: Boolean) {
        if (expand) {
            viewModel.loadImages()
        } else {
            activeContextItem = null
            itemToDeleteConfirm = null
        }
        onUpdateWindowParams(expand, triggerType, panelSide, shouldHide, isGuideActive, swipeHeightPercent)
        expanded = expand
    }

    LaunchedEffect(expanded, triggerType, panelSide, shouldHide, isGuideActive, panelWidthPercent, swipeHeightPercent, panelHeightPercent, orientation) {
        onUpdateWindowParams(expanded, triggerType, panelSide, shouldHide, isGuideActive, swipeHeightPercent)
    }

    // Auto-dismiss context menu after 4 seconds of idle
    LaunchedEffect(activeContextItem) {
        if (activeContextItem != null) {
            kotlinx.coroutines.delay(4000)
            activeContextItem = null
        }
    }

    var isIdle by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastInteractionTime, expanded) {
        if (!expanded) {
            kotlinx.coroutines.delay(3000)
            isIdle = true
        } else {
            isIdle = false
        }
    }

    val bubbleAlpha by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isIdle && !expanded) 0.35f else 1.0f)

    if (shouldHide) return

    val totalWidthDp = context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density
    val expandedWidthDp = (totalWidthDp * (panelWidthPercent.coerceIn(20, 100) / 100f)).dp
    val expandedHeightFraction = (panelHeightPercent.coerceIn(20, 100)) / 100f
    val isEdgeSwipe = triggerType == TriggerType.EDGE_SWIPE

    var baseModifier = if (expanded) {
        Modifier.fillMaxSize()
    } else if (isEdgeSwipe) {
        Modifier.fillMaxSize()
    } else {
        Modifier.wrapContentSize().alpha(bubbleAlpha)
    }

    baseModifier = baseModifier.background(Color.Transparent)

    if (expanded) {
        baseModifier = baseModifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null
        ) {
            if (activeContextItem != null) {
                activeContextItem = null
            } else if (itemToDeleteConfirm != null) {
                itemToDeleteConfirm = null
            } else {
                toggleExpand(false)
            }
        }
    }

    // Only allow opening edge swipe from the border when NOT expanded
    if (!expanded && isEdgeSwipe) {
        baseModifier = baseModifier.pointerInput(panelSide) {
            detectDragGestures(
                onDragStart = { 
                    lastInteractionTime = System.currentTimeMillis()
                    isIdle = false 
                }
            ) { change, dragAmount ->
                val isOpening = if (panelSide == PanelSide.LEFT) dragAmount.x > 8 else dragAmount.x < -8
                if (isOpening) {
                    toggleExpand(true)
                    change.consume()
                }
            }
        }
    }

    Box(modifier = baseModifier) {
        if (!expanded) {
            if (triggerType == TriggerType.FLOATING_BUTTON) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(if (isNearBottom) MaterialTheme.colorScheme.error.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { 
                                    lastInteractionTime = System.currentTimeMillis()
                                    isIdle = false 
                                },
                                onDragEnd = {
                                    if (isNearBottom) {
                                        onCloseService()
                                    }
                                    isNearBottom = false
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                isNearBottom = onDragBubble(dragAmount.x, dragAmount.y)
                            }
                        }
                        .clickable { 
                            lastInteractionTime = System.currentTimeMillis()
                            isIdle = false
                            toggleExpand(true) 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isNearBottom) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Service", tint = MaterialTheme.colorScheme.onError)
                    } else {
                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Open Gallery", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                if (isGuideActive) {
                    val guideShape = if (panelSide == PanelSide.LEFT) {
                        RoundedCornerShape(topEnd = 0.dp, bottomEnd = 16.dp)
                    } else {
                        RoundedCornerShape(topStart = 0.dp, bottomStart = 16.dp)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(guideShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF38BDF8).copy(alpha = 0.70f),
                                        Color(0xFF0284C7).copy(alpha = 0.45f)
                                    )
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                color = Color(0xFFBAE6FD).copy(alpha = 0.9f),
                                shape = guideShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = "${swipeHeightPercent}%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                    )
                }
            }
        } else {
            // Expanded Sidebar Panel
            val opacityAlpha = (panelOpacityPercent.coerceIn(0, 100)) / 100f
            Surface(
                modifier = Modifier
                    .fillMaxHeight(expandedHeightFraction)
                    .width(expandedWidthDp)
                    .align(if (panelSide == PanelSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .border(
                        1.dp, 
                        MaterialTheme.colorScheme.outline.copy(alpha = opacityAlpha * 0.5f), 
                        if (panelSide == PanelSide.LEFT) RoundedCornerShape(bottomEnd = 16.dp) else RoundedCornerShape(bottomStart = 16.dp)
                    )
                    .clip(if (panelSide == PanelSide.LEFT) RoundedCornerShape(bottomEnd = 16.dp) else RoundedCornerShape(bottomStart = 16.dp))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { 
                        if (activeContextItem != null) {
                            activeContextItem = null
                        }
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = opacityAlpha),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    
                    // 1. Dynamic Top Folder Switcher Bar
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Previous Folder Button
                            IconButton(
                                onClick = { 
                                    activeContextItem = null
                                    viewModel.previousFolder() 
                                },
                                enabled = folders.size > 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Folder",
                                    tint = if (folders.size > 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Current Folder Title & Index Indicator
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (folders.size > 1) {
                                            viewModel.nextFolder()
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val isPinnedFolder = currentFolder?.isSpecialPinned == true
                                    Icon(
                                        imageVector = if (isPinnedFolder) Icons.Default.Star else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isPinnedFolder) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = currentFolder?.name ?: "Media",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (folders.isNotEmpty()) {
                                    Text(
                                        text = "${currentFolderIndex + 1}/${folders.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            // Next Folder Button
                            IconButton(
                                onClick = { 
                                    activeContextItem = null
                                    viewModel.nextFolder() 
                                },
                                enabled = folders.size > 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Folder",
                                    tint = if (folders.size > 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 2. Media Grid with Horizontal Swipe Navigation between Folders
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(folders.size) {
                                var accumulatedX = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { accumulatedX = 0f },
                                    onDragEnd = {
                                        if (accumulatedX > 60f) {
                                            activeContextItem = null
                                            viewModel.previousFolder()
                                        } else if (accumulatedX < -60f) {
                                            activeContextItem = null
                                            viewModel.nextFolder()
                                        }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    accumulatedX += dragAmount
                                }
                            }
                    ) {
                        if (isLoading && images.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (images.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (currentFolder?.isSpecialPinned == true) Icons.Default.BookmarkBorder else Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = if (currentFolder?.isSpecialPinned == true) "No pinned media yet.\nLong-press any media to pin it here!" else "No media in this folder",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns),
                                reverseLayout = scrollDirection == ScrollDirection.TOP_TO_BOTTOM,
                                contentPadding = PaddingValues(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(images, key = { it.uri.toString() }) { item ->
                                    Box(
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .combinedClickable(
                                                onClick = {
                                                    if (activeContextItem != null) {
                                                        activeContextItem = null
                                                    } else {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                        scope.launch {
                                                            toggleExpand(false)
                                                            ClipboardUtils.copyMediaToClipboard(context, item.uri)
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    activeContextItem = item
                                                }
                                            )
                                    ) {
                                        AsyncImage(
                                            model = item.uri,
                                            imageLoader = imageLoader,
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Pin Badge (top-right)
                                        if (item.isPinned) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFFF59E0B),
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(3.dp)
                                                    .size(16.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = "Pinned",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // GIF / Video Badge (bottom-left)
                                        if (item.isGif || item.isVideo) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color.Black.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(3.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    if (item.isVideo) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(10.dp))
                                                    }
                                                    Text(
                                                        text = if (item.isGif) "GIF" else "VID",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Floating Context Action Bar on Long Press
                        if (activeContextItem != null) {
                            val selectedItem = activeContextItem!!
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        // 1. Pin / Unpin Action
                                        IconButton(
                                            onClick = {
                                                viewModel.togglePin(selectedItem)
                                                activeContextItem = null
                                            },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PushPin,
                                                contentDescription = if (selectedItem.isPinned) "Unpin" else "Pin",
                                                tint = if (selectedItem.isPinned) Color(0xFFF59E0B) else MaterialTheme.colorScheme.inverseOnSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // 2. Share Action
                                        IconButton(
                                            onClick = {
                                                ClipboardUtils.shareMedia(context, selectedItem.uri)
                                                activeContextItem = null
                                                toggleExpand(false)
                                            },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share",
                                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // 3. Open Action
                                        IconButton(
                                            onClick = {
                                                ClipboardUtils.openMedia(context, selectedItem.uri)
                                                activeContextItem = null
                                                toggleExpand(false)
                                            },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "Open in Viewer",
                                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // 4. Delete Action (Triggers Confirmation)
                                        IconButton(
                                            onClick = {
                                                itemToDeleteConfirm = selectedItem
                                                activeContextItem = null
                                            },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // 5. Close Context Bar
                                        IconButton(
                                            onClick = { activeContextItem = null },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Delete Confirmation Card Overlay
                        if (itemToDeleteConfirm != null) {
                            val deletingItem = itemToDeleteConfirm!!
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { itemToDeleteConfirm = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(0.95f)
                                        .clickable(enabled = false) {},
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "Delete File?",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Are you sure you want to delete \"${deletingItem.name}\"? This action cannot be undone.",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    viewModel.deleteItem(context, deletingItem)
                                                    itemToDeleteConfirm = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Delete File", fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = { itemToDeleteConfirm = null },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Cancel")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Bottom Action Bar (+, Refresh, Close)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                viewModel.refreshAllMedia()
                                toggleExpand(false)
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    toggleExpand(true)
                                }, 180)
                            }) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh & Reopen", tint = MaterialTheme.colorScheme.onSurface)
                            }

                            // Add Media to Current Folder
                            IconButton(onClick = {
                                val curr = currentFolder
                                val intent = Intent(context, TransparentPickerActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or 
                                            Intent.FLAG_ACTIVITY_NO_ANIMATION or 
                                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                                    if (curr != null && !curr.isSpecialPinned) {
                                        putExtra("target_folder_id", curr.id)
                                    }
                                }
                                context.startActivity(intent)
                                toggleExpand(false)
                            }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Media (+)", tint = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(onClick = { toggleExpand(false) }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close Sidebar", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}
