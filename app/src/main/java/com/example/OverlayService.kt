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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.sidegallery.app.R
import com.sidegallery.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRunning.asStateFlow()
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
    private lateinit var viewModel: MainViewModel

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        currentOrientation.value = resources.configuration.orientation
        createNotificationChannel()
        
        // Uso di ic_launcher (o ic_launcher_foreground) garantito da R
        val notification = NotificationCompat.Builder(this, "sidegallery_channel")
            .setContentTitle("SideGallery is running")
            .setContentText("Sidebar overlay is active")
            .setSmallIcon(R.drawable.ic_launcher)
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
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        viewModel.loadImages()
        return START_STICKY
    }

    private var windowX = 0
    private var windowY = 100

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
                .components {
                    if (Build.VERSION.SDK_INT >= 28) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .memoryCache {
                    coil.memory.MemoryCache.Builder(context)
                        .maxSizePercent(0.15)
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

                MyApplicationTheme(darkTheme = darkTheme) {
                    OverlayContent(
                        viewModel = viewModel, 
                        imageLoader = imageLoader, 
                        orientation = currentOri,
                        onUpdateGravity = {},
                        onUpdateWindowParams = { expanded, triggerType, panelSide, shouldHide ->
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
                                        layoutParams.gravity = if (panelSide == PanelSide.LEFT) Gravity.START or Gravity.TOP else Gravity.END or Gravity.TOP
                                        layoutParams.width = (24 * resources.displayMetrics.density).toInt()
                                        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
                                        layoutParams.x = 0
                                        layoutParams.y = 0
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
        _isRunning.value = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        if (isOverlayAdded) {
            windowManager.removeView(composeView)
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
    onUpdateGravity: (PanelSide) -> Unit,
    onUpdateWindowParams: (Boolean, TriggerType, PanelSide, Boolean) -> Unit,
    onDragBubble: (Float, Float) -> Boolean,
    onCloseService: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val gridColumns by viewModel.gridColumns.collectAsState()
    val panelSide by viewModel.panelSide.collectAsState()
    val panelWidthState by viewModel.panelWidth.collectAsState()
    val triggerType by viewModel.triggerType.collectAsState()
    val hideInLandscape by viewModel.hideInLandscape.collectAsState()

    var isNearBottom by remember { mutableStateOf(false) }

    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    val shouldHide = isLandscape && hideInLandscape && !expanded

    fun toggleExpand(expand: Boolean) {
        onUpdateWindowParams(expand, triggerType, panelSide, shouldHide)
        expanded = expand
    }

    LaunchedEffect(expanded, triggerType, panelSide, shouldHide) {
        onUpdateWindowParams(expanded, triggerType, panelSide, shouldHide)
    }

    LaunchedEffect(itemToDelete) {
        if (itemToDelete != null) {
            kotlinx.coroutines.delay(3000)
            itemToDelete = null
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

    val widthFraction = when (panelWidthState) {
        PanelWidth.THIRD -> 3f
        PanelWidth.HALF -> 2f
        PanelWidth.TWO_THIRDS -> 1.5f
    }
    
    val expandedWidthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density / widthFraction).dp
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
            if (itemToDelete != null) {
                itemToDelete = null
            } else {
                toggleExpand(false)
            }
        }
    }

    if (expanded || isEdgeSwipe) {
        baseModifier = baseModifier.pointerInput(expanded, panelSide) {
            detectDragGestures(
                onDragStart = { 
                    lastInteractionTime = System.currentTimeMillis()
                    isIdle = false 
                }
            ) { change, dragAmount ->
                if (!expanded) {
                    val isOpening = if (panelSide == PanelSide.LEFT) dragAmount.x > 5 else dragAmount.x < -5
                    if (isOpening) {
                        toggleExpand(true)
                        change.consume()
                    }
                } else {
                    val isClosing = if (panelSide == PanelSide.LEFT) dragAmount.x < -5 else dragAmount.x > 5
                    if (isClosing) {
                        toggleExpand(false)
                        change.consume()
                    }
                }
            }
        }
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(modifier = baseModifier) {
        if (!expanded) {
            if (triggerType == TriggerType.FLOATING_BUTTON) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(if (isNearBottom) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
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
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onError)
                    } else {
                        Icon(imageVector = Icons.Default.Image, contentDescription = "Open Gallery", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .align(if (panelSide == PanelSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                        .background(Color.White.copy(alpha = 0.05f))
                )
            }
        } else {
            // Expanded Sidebar
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(expandedWidthDp)
                    .align(if (panelSide == PanelSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                    .border(
                        1.dp, 
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), 
                        if (panelSide == PanelSide.LEFT) RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .clip(if (panelSide == PanelSide.LEFT) RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { 
                        if (itemToDelete != null) {
                            itemToDelete = null
                        }
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val images by viewModel.images.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                action = "com.sidegallery.app.ACTION_OPEN_PICKER"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(intent)
                            toggleExpand(false)
                        }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Media", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { toggleExpand(false) }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close Sidebar", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (images.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No images found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(images, key = { it.uri.toString() }) { item ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Gray.copy(alpha = 0.3f))
                                        .combinedClickable(
                                            onClick = {
                                                if (itemToDelete != null) {
                                                    itemToDelete = null
                                                } else {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    scope.launch {
                                                        toggleExpand(false)
                                                        ClipboardUtils.copyImageToClipboard(context, item.uri)
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                itemToDelete = item.uri
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
                                    if (itemToDelete == item.uri) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.7f))
                                                .clickable {
                                                    viewModel.deleteImage(context, item.uri)
                                                    itemToDelete = null
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(36.dp))
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
}
