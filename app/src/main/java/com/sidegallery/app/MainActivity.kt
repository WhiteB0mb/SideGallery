package com.sidegallery.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.sidegallery.app.ui.theme.SideGalleryTheme
import com.sidegallery.app.R
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingFolderForMediaImport: GalleryFolder? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.addFolder(uri)
            Toast.makeText(this, "Folder added to SideGallery!", Toast.LENGTH_SHORT).show()
        }
    }

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            val target = pendingFolderForMediaImport
            viewModel.importMedia(this, uris, target)
            Toast.makeText(this, "Importing ${uris.size} file(s)...", Toast.LENGTH_SHORT).show()
            pendingFolderForMediaImport = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.sidegallery.app.ACTION_OPEN_PICKER" || intent?.action == "com.example.ACTION_OPEN_PICKER") {
            mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            SideGalleryTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(this)) }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                            canDrawOverlays = Settings.canDrawOverlays(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()
                var showOnboardingManual by remember { mutableStateOf(false) }

                val shouldShowOnboarding = !hasCompletedOnboarding || showOnboardingManual
                var inAppSimulatorOpen by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            if (!shouldShowOnboarding) {
                                @OptIn(ExperimentalMaterial3Api::class)
                                TopAppBar(
                                    title = {
                                        Text(
                                            "SideGallery",
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    actions = {
                                        IconButton(onClick = { inAppSimulatorOpen = true }) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircleOutline,
                                                contentDescription = "Test In-App Preview"
                                            )
                                        }
                                        IconButton(onClick = { showOnboardingManual = true }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                                contentDescription = "Setup Guide"
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
                        }
                    ) { innerPadding ->
                        if (shouldShowOnboarding) {
                            OnboardingScreen(
                                viewModel = viewModel,
                                canDrawOverlays = canDrawOverlays,
                                modifier = Modifier.padding(innerPadding),
                                onRequestOverlayPermission = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                    startActivity(intent)
                                },
                                onCheckPermission = {
                                    canDrawOverlays = Settings.canDrawOverlays(this@MainActivity)
                                },
                                onPickFolder = {
                                    folderPicker.launch(null)
                                },
                                onPickMedia = {
                                    pendingFolderForMediaImport = null
                                    mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                                onPickMediaForFolder = { folder ->
                                    pendingFolderForMediaImport = folder
                                    mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                                onToggleService = { enable ->
                                    if (enable) {
                                        val intent = Intent(this@MainActivity, OverlayService::class.java)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            startForegroundService(intent)
                                        } else {
                                            startService(intent)
                                        }
                                    } else {
                                        val intent = Intent(this@MainActivity, OverlayService::class.java).apply {
                                            action = OverlayService.ACTION_STOP
                                        }
                                        startService(intent)
                                    }
                                },
                                onOpenPreviewSimulator = {
                                    inAppSimulatorOpen = true
                                },
                                onFinishOnboarding = {
                                    viewModel.completeOnboarding()
                                    showOnboardingManual = false
                                }
                            )
                        } else {
                            MainScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding),
                                onRequestOverlayPermission = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                    startActivity(intent)
                                },
                                onPickFolder = {
                                    folderPicker.launch(null)
                                },
                                onPickMediaForFolder = { folder ->
                                    pendingFolderForMediaImport = folder
                                    mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                                onToggleService = { enable ->
                                    if (enable) {
                                        val intent = Intent(this@MainActivity, OverlayService::class.java)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            startForegroundService(intent)
                                        } else {
                                            startService(intent)
                                        }
                                    } else {
                                        val intent = Intent(this@MainActivity, OverlayService::class.java).apply {
                                            action = OverlayService.ACTION_STOP
                                        }
                                        startService(intent)
                                    }
                                },
                                onOpenGuide = {
                                    showOnboardingManual = true
                                },
                                onOpenPreviewSimulator = {
                                    inAppSimulatorOpen = true
                                },
                                canDrawOverlays = canDrawOverlays,
                                onCheckPermission = {
                                    canDrawOverlays = Settings.canDrawOverlays(this@MainActivity)
                                }
                            )
                        }
                    }

                    // In-App Interactive Simulator Overlay
                    InAppInteractiveSimulatorOverlay(
                        viewModel = viewModel,
                        isOpen = inAppSimulatorOpen,
                        onClose = { inAppSimulatorOpen = false },
                        onPickMedia = {
                            pendingFolderForMediaImport = null
                            mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    canDrawOverlays: Boolean,
    modifier: Modifier = Modifier,
    onRequestOverlayPermission: () -> Unit,
    onCheckPermission: () -> Unit = {},
    onPickFolder: () -> Unit,
    onPickMedia: () -> Unit,
    onPickMediaForFolder: (GalleryFolder) -> Unit = {},
    onToggleService: (Boolean) -> Unit,
    onOpenPreviewSimulator: () -> Unit,
    onFinishOnboarding: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 5

    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val triggerType by viewModel.triggerType.collectAsState()
    val panelSide by viewModel.panelSide.collectAsState()
    val isServiceRunning by OverlayService.isRunning.collectAsState()

    var isIgnoringBatteryOptimizations by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onCheckPermission()
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pm.isIgnoringBatteryOptimizations(context.packageName)
                } else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Step Progress Indicator
            LinearProgressIndicator(
                progress = { (currentStep + 1) / totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )

            when (currentStep) {
                // Step 0: Welcome
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher),
                            contentDescription = "SideGallery Logo",
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )

                        Text(
                            text = "Welcome to SideGallery",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Access your favorite memes, screenshots, GIFs, and videos instantly anywhere on your phone with a quick swipe or bubble tap.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                FeatureBullet(
                                    icon = Icons.Default.FlashOn,
                                    title = "Instant 1-Tap Copy & Share",
                                    desc = "Tap any image or video to copy it to clipboard or long-press to pin, share, or delete."
                                )
                                FeatureBullet(
                                    icon = Icons.Default.FolderSpecial,
                                    title = "Multi-Folder & Pinned Tab",
                                    desc = "Organize memes and media across folders and swipe between them effortlessly."
                                )
                                FeatureBullet(
                                    icon = Icons.Default.PlayCircleFilled,
                                    title = "Images, GIFs & Videos",
                                    desc = "Supports pictures, animated GIFs, and video clips seamlessly."
                                )
                            }
                        }
                    }
                }

                // Step 1: Permissions
                1 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Required Permissions",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "SideGallery needs permission to display over other apps and run reliably in the background without being closed by the system.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (canDrawOverlays)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "1. Display over other apps",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusBadge(
                                        text = if (canDrawOverlays) "✓ GRANTED" else "REQUIRED",
                                        isPositive = canDrawOverlays
                                    )
                                }
                                Text("Allows SideGallery to open the floating trigger bar or bubble over any active app.", style = MaterialTheme.typography.bodySmall)
                                if (!canDrawOverlays) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = onRequestOverlayPermission,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Grant Permission")
                                            }
                                            OutlinedButton(
                                                onClick = onCheckPermission
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                            }
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            OutlinedButton(
                                                onClick = {
                                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    }
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("App Info (Allow Restricted Access)")
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            "Permission active! Ready for next step.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF16A34A),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "2. Battery Optimization",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusBadge(
                                        text = if (isIgnoringBatteryOptimizations) "✓ OPTIMAL" else "RECOMMENDED",
                                        isPositive = isIgnoringBatteryOptimizations
                                    )
                                }
                                Text("Prevents the system from closing the background overlay when your phone is low on RAM.", style = MaterialTheme.typography.bodySmall)
                                if (!isIgnoringBatteryOptimizations && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Set to Unrestricted")
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 2: Choose Folder
                2 -> {
                    val userFolders = folders.filter { !it.isSpecialPinned }
                    var selectedTargetFolderId by remember(userFolders) {
                        mutableStateOf(userFolders.firstOrNull()?.id ?: "")
                    }
                    val targetFolder = userFolders.find { it.id == selectedTargetFolderId } ?: userFolders.firstOrNull()

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Select Media Folder",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose or create folders on your phone where your images, memes, GIFs, or videos are stored.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (userFolders.isNotEmpty())
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        if (userFolders.isNotEmpty()) "${userFolders.size} Folder(s) Connected" else "No Folder Selected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (userFolders.isNotEmpty()) {
                                    Text(
                                        text = "Target folder for importing:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    userFolders.forEach { f ->
                                        val isSelectedTarget = (f.id == targetFolder?.id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelectedTarget)
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else
                                                        Color.Transparent
                                                )
                                                .clickable { selectedTargetFolderId = f.id }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                RadioButton(
                                                    selected = isSelectedTarget,
                                                    onClick = { selectedTargetFolderId = f.id }
                                                )
                                                Text(
                                                    text = f.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelectedTarget) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }

                                            IconButton(
                                                onClick = { onPickMediaForFolder(f) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AddPhotoAlternate,
                                                    contentDescription = "Import to ${f.name}",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = onPickFolder,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (userFolders.isNotEmpty()) "Add Another Folder (+)" else "Select / Create Folder")
                                }
                            }
                        }

                        if (userFolders.isNotEmpty() && targetFolder != null) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Import Media (Optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Destination folder: ${targetFolder.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    OutlinedButton(
                                        onClick = { onPickMediaForFolder(targetFolder) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Import into \"${targetFolder.name}\" (+)")
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 3: Trigger & Position
                3 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "How do you want to open it?",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Customize your trigger and carousel scrolling direction below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TriggerChoiceCard(
                                title = "Edge Swipe",
                                desc = "Swipe from top screen border",
                                icon = Icons.Default.Swipe,
                                isSelected = triggerType == TriggerType.EDGE_SWIPE,
                                onClick = { viewModel.setTriggerType(TriggerType.EDGE_SWIPE) },
                                modifier = Modifier.weight(1f)
                            )
                            TriggerChoiceCard(
                                title = "Floating Bubble",
                                desc = "Draggable circle icon",
                                icon = Icons.Default.Circle,
                                isSelected = triggerType == TriggerType.FLOATING_BUTTON,
                                onClick = { viewModel.setTriggerType(TriggerType.FLOATING_BUTTON) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = onOpenPreviewSimulator,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Test In-App Live Simulator")
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Screen Side", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = panelSide == PanelSide.RIGHT,
                                        onClick = { viewModel.setPanelSide(PanelSide.RIGHT) },
                                        label = { Text("Right Edge") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = panelSide == PanelSide.LEFT,
                                        onClick = { viewModel.setPanelSide(PanelSide.LEFT) },
                                        label = { Text("Left Edge") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Step 4: Final Step
                4 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(72.dp)
                        )

                        Text(
                            text = "You're All Set!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Your setup is complete. Enable the overlay now and start using SideGallery anywhere on your phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Enable SideGallery Overlay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            if (isServiceRunning) "Running in background" else "Inactive",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isServiceRunning) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isServiceRunning,
                                        onCheckedChange = { onToggleService(it) },
                                        enabled = canDrawOverlays && folders.any { !it.isSpecialPinned }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Nav Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = { currentStep-- },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    Spacer(Modifier.width(6.dp))
                    Text("Back")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (currentStep < totalSteps - 1) {
                val isNextEnabled = when (currentStep) {
                    1 -> canDrawOverlays
                    2 -> folders.any { !it.isSpecialPinned }
                    else -> true
                }

                Button(
                    onClick = { currentStep++ },
                    enabled = isNextEnabled,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(if (currentStep == 0) "Get Started" else "Next Step")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                }
            } else {
                Button(
                    onClick = onFinishOnboarding,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Done, contentDescription = "Finish")
                    Spacer(Modifier.width(6.dp))
                    Text("Go to Dashboard")
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onRequestOverlayPermission: () -> Unit,
    onPickFolder: () -> Unit,
    onPickMediaForFolder: (GalleryFolder?) -> Unit,
    onToggleService: (Boolean) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenPreviewSimulator: () -> Unit,
    canDrawOverlays: Boolean,
    onCheckPermission: () -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    val currentFolderIndex by viewModel.currentFolderIndex.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val triggerType by viewModel.triggerType.collectAsState()
    val panelSide by viewModel.panelSide.collectAsState()
    val scrollDirection by viewModel.scrollDirection.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val isServiceRunning by OverlayService.isRunning.collectAsState()
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Dialog state for renaming folder
    var folderToRename by remember { mutableStateOf<GalleryFolder?>(null) }
    var renameInput by remember { mutableStateOf("") }

    // Dialog state for removing folder
    var folderToRemove by remember { mutableStateOf<GalleryFolder?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onCheckPermission()
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pm.isIgnoringBatteryOptimizations(context.packageName)
                } else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Status & Service Master Switch Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Overlay Service", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isServiceRunning) "● Active in Background" else "○ Inactive",
                            color = if (isServiceRunning) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { onToggleService(it) },
                        enabled = canDrawOverlays && folders.any { !it.isSpecialPinned }
                    )
                }

                if (!canDrawOverlays || !folders.any { !it.isSpecialPinned }) {
                    Text(
                        "Please select at least one media folder and grant overlay permission to enable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Live In-App Simulator Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Text("Interactive Live Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Test your sidebar layout, multi-folder switching, and 1-tap clipboard copying right inside the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onOpenPreviewSimulator,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.OpenInFull, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Live Interactive Simulator")
                }
            }
        }

        // 1. Folders Management Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. Media Folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${folders.size} folder(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }

                Text(
                    text = "Add and manage multiple folders. Swipe or tap arrows inside the sidebar to switch between them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // List of Folders
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.forEachIndexed { index, folder ->
                        val isSelected = currentFolderIndex == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.selectFolder(index) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (folder.isSpecialPinned) Icons.Default.Star else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (folder.isSpecialPinned) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (folder.isSpecialPinned) {
                                            Text("All pinned items across folders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            Text(if (isSelected) "Active folder" else "Tap to view", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!folder.isSpecialPinned) {
                                        // Add media (+) button
                                        IconButton(
                                            onClick = { onPickMediaForFolder(folder) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Add Media", tint = MaterialTheme.colorScheme.primary)
                                        }

                                        // Rename button
                                        IconButton(
                                            onClick = {
                                                folderToRename = folder
                                                renameInput = folder.name
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        // Remove folder from sidebar list button (if more than 1 regular folder)
                                        if (folders.count { !it.isSpecialPinned } > 1) {
                                            IconButton(
                                                onClick = { folderToRemove = folder },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove from Sidebar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Add New Folder Button
                OutlinedButton(
                    onClick = onPickFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add New Folder (+)")
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                HorizontalDivider()

                // Sorting
                Text("Sort Items In Folder", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val sortOptionsList = listOf(
                    Triple(SortOption.DATE_NEWEST, "Date: Newest", Icons.Default.Schedule),
                    Triple(SortOption.DATE_OLDEST, "Date: Oldest", Icons.Default.History),
                    Triple(SortOption.NAME_ASC, "Name: A → Z", Icons.Default.SortByAlpha),
                    Triple(SortOption.NAME_DESC, "Name: Z → A", Icons.Default.Sort),
                    Triple(SortOption.SIZE_LARGEST, "Size: Largest", Icons.Default.Storage),
                    Triple(SortOption.SIZE_SMALLEST, "Size: Smallest", Icons.Default.Compress)
                )
                
                var expanded by remember { mutableStateOf(false) }
                val selectedOption = sortOptionsList.find { it.first == sortOption } ?: sortOptionsList[0]
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(selectedOption.third, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedOption.second, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        sortOptionsList.forEach { (opt, label, icon) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    viewModel.setSortOption(opt)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. Trigger & Gestures Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("2. Trigger & Gestures", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Text("Trigger Style", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = triggerType == TriggerType.EDGE_SWIPE,
                        onClick = { viewModel.setTriggerType(TriggerType.EDGE_SWIPE) },
                        label = { Text("Edge Swipe") }
                    )
                    FilterChip(
                        selected = triggerType == TriggerType.FLOATING_BUTTON,
                        onClick = { viewModel.setTriggerType(TriggerType.FLOATING_BUTTON) },
                        label = { Text("Floating Bubble") }
                    )
                }

                Text("Screen Side", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = panelSide == PanelSide.LEFT,
                        onClick = { viewModel.setPanelSide(PanelSide.LEFT) },
                        label = { Text("Left Edge") }
                    )
                    FilterChip(
                        selected = panelSide == PanelSide.RIGHT,
                        onClick = { viewModel.setPanelSide(PanelSide.RIGHT) },
                        label = { Text("Right Edge") }
                    )
                }

                if (triggerType == TriggerType.EDGE_SWIPE) {
                    HorizontalDivider()

                    val swipeHeightPercent by viewModel.swipeHeightPercent.collectAsState()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Edge Swipe Trigger Reach (${swipeHeightPercent}% of screen)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Swipeable height at the top screen border to open the sidebar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = swipeHeightPercent.toFloat(),
                            onValueChange = { viewModel.setSwipeHeightPercent(it.toInt(), showGuide = true) },
                            valueRange = 10f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(100 to "100%", 75 to "75%", 50 to "50%", 25 to "25%").forEach { (value, label) ->
                                AssistChip(
                                    onClick = { viewModel.setSwipeHeightPercent(value, showGuide = true) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                    colors = if (swipeHeightPercent == value)
                                        AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    else
                                        AssistChipDefaults.assistChipColors(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.triggerGuidePreview(2500L) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Preview Guide",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview Trigger Area (3s)")
                        }
                    }
                }

                HorizontalDivider()

                val hideInLandscape by viewModel.hideInLandscape.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-hide in landscape", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Only hides the trigger during landscape orientation if enabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = hideInLandscape,
                        onCheckedChange = { viewModel.setHideInLandscape(it) }
                    )
                }

                HorizontalDivider()

                Text("Carousel Scroll Direction", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DirectionChoiceTile(
                        title = "Top to Bottom",
                        desc = "Standard downward scrolling",
                        icon = Icons.Default.ArrowDownward,
                        isSelected = scrollDirection == ScrollDirection.TOP_TO_BOTTOM,
                        onClick = { viewModel.setScrollDirection(ScrollDirection.TOP_TO_BOTTOM) }
                    )
                    DirectionChoiceTile(
                        title = "Bottom to Top",
                        desc = "Inverted upward scrolling",
                        icon = Icons.Default.ArrowUpward,
                        isSelected = scrollDirection == ScrollDirection.BOTTOM_TO_TOP,
                        onClick = { viewModel.setScrollDirection(ScrollDirection.BOTTOM_TO_TOP) }
                    )
                }
            }
        }

        // 3. Sidebar Dimensions & Appearance Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3. Sidebar Dimensions & Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                val panelHeightPercent by viewModel.panelHeightPercent.collectAsState()
                val panelWidthPercent by viewModel.panelWidthPercent.collectAsState()
                val panelOpacityPercent by viewModel.panelOpacityPercent.collectAsState()

                // Panel Vertical Height
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Panel Vertical Height (${panelHeightPercent}% of screen)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Slider(
                        value = panelHeightPercent.toFloat(),
                        onValueChange = { viewModel.setPanelHeightPercent(it.toInt()) },
                        valueRange = 20f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // Panel Width
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Panel Width (${panelWidthPercent}% of screen)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Slider(
                        value = panelWidthPercent.toFloat(),
                        onValueChange = { viewModel.setPanelWidthPercent(it.toInt()) },
                        valueRange = 20f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // Background Opacity
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Background Opacity (${panelOpacityPercent}%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Slider(
                        value = panelOpacityPercent.toFloat(),
                        onValueChange = { viewModel.setPanelOpacityPercent(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // Grid Columns
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Grid Columns: $gridColumns", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Slider(
                        value = gridColumns.toFloat(),
                        onValueChange = { viewModel.setGridColumns(it.toInt()) },
                        valueRange = 1f..4f,
                        steps = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Permissions Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("4. Permissions Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Overlay Permission:", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(
                        text = if (canDrawOverlays) "✓ Granted" else "✕ Missing",
                        isPositive = canDrawOverlays
                    )
                }
                if (!canDrawOverlays) {
                    Button(onClick = onRequestOverlayPermission, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant Overlay Permission")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Battery Optimization:", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(
                        text = if (isIgnoringBatteryOptimizations) "✓ Optimal" else "Restricted",
                        isPositive = isIgnoringBatteryOptimizations
                    )
                }
            }
        }

        // Replay Setup Guide Button
        OutlinedButton(
            onClick = onOpenGuide,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Re-open Setup Walkthrough Guide")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Rename Folder Dialog
    folderToRename?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text("Rename Folder") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameFolder(folder.id, renameInput)
                        }
                        folderToRename = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Remove Folder from Sidebar Dialog
    folderToRemove?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = { Text("Remove from Sidebar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remove \"${folder.name}\" from SideGallery's folders list?")
                    Text(
                        "📁 No files on your device will be deleted. The original folder and all its photos/videos stay safe on your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeFolder(folder.id)
                        folderToRemove = null
                    }
                ) {
                    Text("Remove from Sidebar")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * In-App Interactive Simulator & Live Preview Overlay.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InAppInteractiveSimulatorOverlay(
    viewModel: MainViewModel,
    isOpen: Boolean,
    onClose: () -> Unit,
    onPickMedia: () -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    val currentFolderIndex by viewModel.currentFolderIndex.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val panelSide by viewModel.panelSide.collectAsState()
    val panelWidthPercent by viewModel.panelWidthPercent.collectAsState()
    val panelHeightPercent by viewModel.panelHeightPercent.collectAsState()
    val panelOpacityPercent by viewModel.panelOpacityPercent.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val scrollDirection by viewModel.scrollDirection.collectAsState()
    val images by viewModel.images.collectAsState()
    val swipeHeightPercent by viewModel.swipeHeightPercent.collectAsState()
    val guidePreviewUntil by viewModel.guidePreviewUntil.collectAsState()
    val isServiceRunning by OverlayService.isRunning.collectAsState()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isGuideActive by remember { mutableStateOf(false) }
    var activeContextItem by remember { mutableStateOf<GalleryItem?>(null) }
    var itemToDeleteConfirm by remember { mutableStateOf<GalleryItem?>(null) }

    LaunchedEffect(guidePreviewUntil) {
        while (System.currentTimeMillis() < guidePreviewUntil) {
            isGuideActive = true
            kotlinx.coroutines.delay(100)
        }
        isGuideActive = false
    }

    if (isGuideActive && !isOpen && !isServiceRunning) {
        val guideWidth = 20.dp
        Box(modifier = Modifier.fillMaxSize()) {
            val align = if (panelSide == PanelSide.LEFT) Alignment.TopStart else Alignment.TopEnd
            val shape = if (panelSide == PanelSide.LEFT)
                RoundedCornerShape(topEnd = 0.dp, bottomEnd = 16.dp)
            else
                RoundedCornerShape(topStart = 0.dp, bottomStart = 16.dp)

            Box(
                modifier = Modifier
                    .fillMaxHeight((swipeHeightPercent.coerceIn(10, 100)) / 100f)
                    .width(guideWidth)
                    .align(align)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.85f),
                                Color(0xFF0284C7).copy(alpha = 0.65f)
                            )
                        )
                    )
                    .border(2.dp, Color(0xFFBAE6FD), shape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${swipeHeightPercent}%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { if (panelSide == PanelSide.LEFT) -it else it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { if (panelSide == PanelSide.LEFT) -it else it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onClose() }
        ) {
            val widthFraction = (panelWidthPercent.coerceIn(20, 100)) / 100f
            val heightFraction = (panelHeightPercent.coerceIn(20, 100)) / 100f
            val opacityAlpha = (panelOpacityPercent.coerceIn(0, 100)) / 100f

            Surface(
                modifier = Modifier
                    .fillMaxHeight(heightFraction)
                    .fillMaxWidth(widthFraction)
                    .align(if (panelSide == PanelSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = opacityAlpha * 0.5f),
                        if (panelSide == PanelSide.LEFT) RoundedCornerShape(bottomEnd = 16.dp) else RoundedCornerShape(bottomStart = 16.dp)
                    )
                    .clip(
                        if (panelSide == PanelSide.LEFT)
                            RoundedCornerShape(bottomEnd = 16.dp)
                        else
                            RoundedCornerShape(bottomStart = 16.dp)
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        activeContextItem = null
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = opacityAlpha),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Folder Bar
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (folders.size > 1) viewModel.nextFolder()
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val isPinned = currentFolder?.isSpecialPinned == true
                                    Icon(
                                        imageVector = if (isPinned) Icons.Default.Star else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isPinned) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
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
                                        text = "${currentFolderIndex + 1}/${folders.size} (Swipe to switch)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    )
                                }
                            }

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

                    // Content Grid with Horizontal Swipe Navigation between folders
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(folders.size) {
                                var accX = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { accX = 0f },
                                    onDragEnd = {
                                        if (accX > 60f) {
                                            activeContextItem = null
                                            viewModel.previousFolder()
                                        } else if (accX < -60f) {
                                            activeContextItem = null
                                            viewModel.nextFolder()
                                        }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    accX += dragAmount
                                }
                            }
                    ) {
                        if (images.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = if (currentFolder?.isSpecialPinned == true) Icons.Default.BookmarkBorder else Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (currentFolder?.isSpecialPinned == true) "No pinned media yet.\nLong-press any media to pin it here!" else "No media in this folder",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (currentFolder?.isSpecialPinned != true) {
                                        Button(onClick = onPickMedia) {
                                            Text("Import Media")
                                        }
                                    }
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
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .combinedClickable(
                                                onClick = {
                                                    if (activeContextItem != null) {
                                                        activeContextItem = null
                                                    } else {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                        scope.launch {
                                                            ClipboardUtils.copyMediaToClipboard(context, item.uri)
                                                            Toast.makeText(context, "Copied to clipboard! (Simulator)", Toast.LENGTH_SHORT).show()
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
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

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

                        // Floating Context Bar on Long Press
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
                                        IconButton(
                                            onClick = {
                                                ClipboardUtils.shareMedia(context, selectedItem.uri)
                                                activeContextItem = null
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
                                        IconButton(
                                            onClick = {
                                                ClipboardUtils.openMedia(context, selectedItem.uri)
                                                activeContextItem = null
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

                        // Delete Confirmation Card
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
                                            text = "Are you sure you want to delete \"${deletingItem.name}\"?",
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

                    // Bottom info bar
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Tap to copy • Long-press for menu",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = onPickMedia,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Import (+)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPositive) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = if (isPositive) Color(0xFF166534) else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun DirectionChoiceTile(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = null
            )
        }
    }
}

@Composable
fun FeatureBullet(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TriggerChoiceCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.height(100.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
