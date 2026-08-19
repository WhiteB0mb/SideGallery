package com.example

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.IntOffset

import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    
    // Lifecycle setup for Compose inside WindowManager
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
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "sidegallery_channel")
            .setContentTitle("SideGallery is running")
            .setContentText("Sidebar overlay is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        // Trigger a reload in case settings changed while service was running
        viewModel.loadImages()
        return START_STICKY
    }

    private var windowX = 0
    private var windowY = 100 // default y offset

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
                .build()

            setContent {
                MyApplicationTheme {
                    OverlayContent(
                        viewModel = viewModel, 
                        imageLoader = imageLoader, 
                        onUpdateGravity = { side ->
                            val newGravity = if (side == PanelSide.LEFT) Gravity.START or Gravity.TOP else Gravity.END or Gravity.TOP
                            if (layoutParams.gravity != newGravity) {
                                layoutParams.gravity = newGravity
                                try { windowManager.updateViewLayout(this@apply, layoutParams) } catch (e: Exception) {}
                            }
                        },
                        onUpdateWindowParams = { expanded, triggerType ->
                            if (expanded) {
                                layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                                layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
                                layoutParams.x = 0
                                layoutParams.y = 0
                            } else if (triggerType == TriggerType.FLOATING_BUTTON) {
                                layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                                layoutParams.x = windowX
                                layoutParams.y = windowY
                            } else {
                                layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                                layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
                                layoutParams.x = 0
                                layoutParams.y = 0
                            }
                            try { windowManager.updateViewLayout(this@apply, layoutParams) } catch (e: Exception) {}
                        },
                        onDragBubble = { dx, dy ->
                            val gravityStart = layoutParams.gravity and Gravity.START == Gravity.START
                            if (gravityStart) {
                                windowX += dx.toInt()
                            } else {
                                windowX -= dx.toInt()
                            }
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
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        if (isOverlayAdded) {
            windowManager.removeView(composeView)
            isOverlayAdded = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun OverlayContent(
    viewModel: MainViewModel, 
    imageLoader: ImageLoader, 
    onUpdateGravity: (PanelSide) -> Unit,
    onUpdateWindowParams: (Boolean, TriggerType) -> Unit,
    onDragBubble: (Float, Float) -> Boolean,
    onCloseService: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val gridColumns by viewModel.gridColumns.collectAsState()
    val panelSide by viewModel.panelSide.collectAsState()
    val panelWidthState by viewModel.panelWidth.collectAsState()
    val triggerType by viewModel.triggerType.collectAsState()

    var isNearBottom by remember { mutableStateOf(false) }

    LaunchedEffect(panelSide) {
        onUpdateGravity(panelSide)
    }

    LaunchedEffect(expanded, triggerType) {
        onUpdateWindowParams(expanded, triggerType)
    }

    val widthFraction = when (panelWidthState) {
        PanelWidth.THIRD -> 3f
        PanelWidth.HALF -> 2f
        PanelWidth.TWO_THIRDS -> 1.5f
    }
    
    val expandedWidthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density / widthFraction).dp
    
    val isEdgeSwipe = triggerType == TriggerType.EDGE_SWIPE

    var baseModifier = if (expanded) {
        Modifier.fillMaxHeight().width(expandedWidthDp)
    } else if (isEdgeSwipe) {
        Modifier.fillMaxHeight().width(10.dp)
    } else {
        Modifier.wrapContentSize()
    }

    baseModifier = baseModifier.background(Color.Transparent)

    if (expanded || isEdgeSwipe) {
        baseModifier = baseModifier.pointerInput(expanded, panelSide) {
            detectDragGestures { change, dragAmount ->
                if (!expanded) {
                    val isOpening = if (panelSide == PanelSide.LEFT) dragAmount.x > 10 else dragAmount.x < -10
                    if (isOpening) {
                        expanded = true
                        change.consume()
                    }
                } else {
                    val isClosing = if (panelSide == PanelSide.LEFT) dragAmount.x < -10 else dragAmount.x > 10
                    if (isClosing) {
                        expanded = false
                        change.consume()
                    }
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
                        .background(if (isNearBottom) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        .pointerInput(Unit) {
                            detectDragGestures(
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
                        .clickable { expanded = true },
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
                    .fillMaxSize()
                    .border(
                        1.dp, 
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), 
                        if (panelSide == PanelSide.LEFT) RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .clip(if (panelSide == PanelSide.LEFT) RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                val images by viewModel.images.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SideGallery", 
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        IconButton(onClick = onCloseService) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close App")
                        }
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (images.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No images found", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(images, key = { it.uri.toString() }) { item ->
                                AsyncImage(
                                    model = item.uri,
                                    imageLoader = imageLoader,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Gray.copy(alpha = 0.3f))
                                        .clickable {
                                            scope.launch {
                                                expanded = false // Close sidebar
                                                ClipboardUtils.copyImageToClipboard(context, item.uri)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
