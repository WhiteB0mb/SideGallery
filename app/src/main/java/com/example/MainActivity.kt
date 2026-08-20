package com.example

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
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.ColorFilter
import androidx.compose.ui.res.painterResource

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.setFolderUri(uri)
            Toast.makeText(this, "Folder selected successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(this, uris)
            Toast.makeText(this, "Importing ${uris.size} files...", Toast.LENGTH_SHORT).show()
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
            MyApplicationTheme {
                var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
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
                                onPickFolder = { folderPicker.launch(null) },
                                onPickMedia = { mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                                onToggleService = { start -> toggleOverlayService(start) },
                                onOpenPreviewSimulator = { inAppSimulatorOpen = true },
                                onFinishOnboarding = {
                                    viewModel.completeOnboarding()
                                    showOnboardingManual = false
                                },
                                onCheckPermission = {
                                    canDrawOverlays = Settings.canDrawOverlays(this@MainActivity)
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
                                onPickFolder = { folderPicker.launch(null) },
                                onPickMedia = { mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                                onToggleService = { start -> toggleOverlayService(start) },
                                onOpenGuide = { showOnboardingManual = true },
                                onOpenPreviewSimulator = { inAppSimulatorOpen = true },
                                canDrawOverlays = canDrawOverlays,
                                onCheckPermission = {
                                    canDrawOverlays = Settings.canDrawOverlays(this@MainActivity)
                                }
                            )
                        }
                    }

                    // In-App Interactive Overlay Tester (Always available to test regardless of background overlay status)
                    InAppInteractiveSimulatorOverlay(
                        viewModel = viewModel,
                        isOpen = inAppSimulatorOpen,
                        onClose = { inAppSimulatorOpen = false },
                        onPickMedia = { mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                    )
                }
            }
        }
    }

    private fun toggleOverlayService(start: Boolean) {
        val intent = Intent(this, OverlayService::class.java)
        if (start) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(intent)
        }
    }
}

/**
 * Step-by-Step Interactive Onboarding Walkthrough for dumb-user friendly initial setup.
 */
@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    canDrawOverlays: Boolean,
    modifier: Modifier = Modifier,
    onRequestOverlayPermission: () -> Unit,
    onPickFolder: () -> Unit,
    onPickMedia: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    onOpenPreviewSimulator: () -> Unit,
    onFinishOnboarding: () -> Unit,
    onCheckPermission: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 5

    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val triggerType by viewModel.triggerType.collectAsState()
    val panelSide by viewModel.panelSide.collectAsState()
    val scrollDirection by viewModel.scrollDirection.collectAsState()
    val swipeHeightPercent by viewModel.swipeHeightPercent.collectAsState()
    val isServiceRunning by OverlayService.isRunning.collectAsState()

    val context = LocalContext.current
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(true) }

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
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Step Progress Indicator
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step ${currentStep + 1} of $totalSteps",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (currentStep > 0) {
                    TextButton(onClick = onFinishOnboarding) {
                        Text("Skip to Dashboard")
                    }
                }
            }

            // Step Indicator Dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until totalSteps) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (i <= currentStep) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        }

        // Main Content for the Current Step
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            when (currentStep) {
                // Step 0: Welcome & Concept
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(88.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher),
                                    contentDescription = "SideGallery",
                                    modifier = Modifier.size(48.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }

                        Text(
                            text = "Welcome to SideGallery!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Your instant side gallery overlay to quickly copy memes, screenshots, and photos to your clipboard without leaving your current app.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FeatureBullet(
                                    icon = Icons.Default.FlashOn,
                                    title = "Instant 1-Tap Copy",
                                    desc = "Touch any image in the sidebar to copy it immediately to clipboard."
                                )
                                FeatureBullet(
                                    icon = Icons.Default.BatterySaver,
                                    title = "Zero Battery Impact",
                                    desc = "0% CPU when idle; sleeps automatically until opened."
                                )
                                FeatureBullet(
                                    icon = Icons.Default.Security,
                                    title = "100% Offline & Private",
                                    desc = "Works entirely on your device with no internet connection required."
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
                            text = "Grant Required Permissions",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "SideGallery needs permission to draw over other apps so the sidebar can appear on your screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Overlay Permission Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (canDrawOverlays)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (canDrawOverlays) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (canDrawOverlays) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "1. Draw Over Other Apps",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusBadge(
                                        text = if (canDrawOverlays) "GRANTED" else "REQUIRED",
                                        isPositive = canDrawOverlays
                                    )
                                }

                                Text(
                                    text = if (canDrawOverlays)
                                        "Permission granted! SideGallery can now display on top of other applications."
                                    else
                                        "Tap the button below to enable 'Allow display over other apps' for SideGallery.",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                if (!canDrawOverlays) {
                                    Button(
                                        onClick = onRequestOverlayPermission,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Grant Overlay Permission")
                                    }

                                    // Guide for Android 13+ Restricted Settings
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    "⚠️ Is the setting greyed out / restricted?",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    "1. Go to your phone Settings > Apps > SideGallery\n2. Tap the 3 dots (⋮) in the top-right corner\n3. Tap 'Allow restricted settings'\n4. Return here and tap Grant Overlay Permission again.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                OutlinedButton(
                                                    onClick = {
                                                        try {
                                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                                data = Uri.parse("package:${context.packageName}")
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Could not open App Info", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Open SideGallery App Info (for 3 Dots ⋮)")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Battery Optimization Card
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            text = "Choose or create a folder on your phone where your images, memes, or screenshots will be loaded from.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedFolderUri != null)
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
                                        if (selectedFolderUri != null) "Folder Connected" else "No Folder Selected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (selectedFolderUri != null) {
                                    Text(
                                        text = selectedFolderUri.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = onPickFolder,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (selectedFolderUri != null) "Change Folder" else "Select / Create Folder")
                                }
                            }
                        }

                        if (selectedFolderUri != null) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Add Photos (Optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("You can import images right now into your chosen folder.", style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = onPickMedia, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Import Images (+)")
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 3: Trigger & Position (Top Anchored Bar & Carousel Scroll Direction)
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
                            text = "The trigger bar stays anchored at the top of your screen. Customize your trigger and carousel scrolling direction below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Trigger Type Selection
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

                        // In-App Interactive Test Button (Always Testable)
                        Button(
                            onClick = onOpenPreviewSimulator,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Test In-App Live Simulator")
                        }

                        if (triggerType == TriggerType.EDGE_SWIPE) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Edge Swipe Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                    // 1. Screen Side
                                    Text("1. Screen Side", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

                                    HorizontalDivider()

                                    // 2. Carousel Scroll Direction
                                    Text("2. Carousel Scroll Direction", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

                                    HorizontalDivider()

                                    // 3. Edge Swipe Trigger Reach
                                    val swipeHeightPercent by viewModel.swipeHeightPercent.collectAsState()
                                    Text("3. Edge Swipe Trigger Reach (${swipeHeightPercent}%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "Height of the swipeable trigger area at screen edge (starts from top).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = swipeHeightPercent.toFloat(),
                                        onValueChange = { viewModel.setSwipeHeightPercent(it.toInt(), showGuide = true) },
                                        valueRange = 10f..100f
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
                                        Icon(Icons.Default.Visibility, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Preview Trigger Area (3s)")
                                    }

                                    HorizontalDivider()

                                    // 4. Panel Dimensions (Height & Width)
                                    val panelHeightPercent by viewModel.panelHeightPercent.collectAsState()
                                    val panelWidthPercent by viewModel.panelWidthPercent.collectAsState()

                                    Text("4. Panel Vertical Height (${panelHeightPercent}%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Slider(
                                        value = panelHeightPercent.toFloat(),
                                        onValueChange = { viewModel.setPanelHeightPercent(it.toInt()) },
                                        valueRange = 20f..100f
                                    )

                                    HorizontalDivider()

                                    Text("5. Panel Width (${panelWidthPercent}%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Slider(
                                        value = panelWidthPercent.toFloat(),
                                        onValueChange = { viewModel.setPanelWidthPercent(it.toInt()) },
                                        valueRange = 20f..100f
                                    )
                                }
                            }
                        } else {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Floating Bubble Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                    // 1. Screen Side
                                    Text("1. Screen Side", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

                                    HorizontalDivider()

                                    // 2. Auto-hide in landscape
                                    val hideInLandscape by viewModel.hideInLandscape.collectAsState()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("2. Auto-hide in landscape", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text("Hides the floating bubble in fullscreen/video landscape mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Switch(
                                            checked = hideInLandscape,
                                            onCheckedChange = { viewModel.setHideInLandscape(it) }
                                        )
                                    }

                                    HorizontalDivider()

                                    // 3. Carousel Scroll Direction
                                    Text("3. Carousel Scroll Direction", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

                                    HorizontalDivider()

                                    // 4. Panel Dimensions (Height & Width)
                                    val panelHeightPercent by viewModel.panelHeightPercent.collectAsState()
                                    val panelWidthPercent by viewModel.panelWidthPercent.collectAsState()

                                    Text("4. Panel Vertical Height (${panelHeightPercent}%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Slider(
                                        value = panelHeightPercent.toFloat(),
                                        onValueChange = { viewModel.setPanelHeightPercent(it.toInt()) },
                                        valueRange = 20f..100f
                                    )

                                    HorizontalDivider()

                                    Text("5. Panel Width (${panelWidthPercent}%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Slider(
                                        value = panelWidthPercent.toFloat(),
                                        onValueChange = { viewModel.setPanelWidthPercent(it.toInt()) },
                                        valueRange = 20f..100f
                                    )
                                }
                            }
                        }
                    }
                }

                // Step 4: Final Step - Enable Service & Complete
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
                                        enabled = canDrawOverlays && selectedFolderUri != null
                                    )
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("💡 Quick Tips:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("• Swipe from the top edge or tap the bubble to open your gallery.", style = MaterialTheme.typography.bodySmall)
                                Text("• Tap any image to copy it instantly.", style = MaterialTheme.typography.bodySmall)
                                Text("• Long-press an image inside the sidebar to delete it.", style = MaterialTheme.typography.bodySmall)
                                Text("• Tap + at the bottom of the sidebar to import new photos anytime.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // Bottom Navigation Buttons (Back & Next / Finish)
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
                    2 -> selectedFolderUri != null
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

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onRequestOverlayPermission: () -> Unit,
    onPickFolder: () -> Unit,
    onPickMedia: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenPreviewSimulator: () -> Unit,
    canDrawOverlays: Boolean,
    onCheckPermission: () -> Unit
) {
    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val triggerType by viewModel.triggerType.collectAsState()
    val panelSide by viewModel.panelSide.collectAsState()
    val panelWidth by viewModel.panelWidth.collectAsState()
    val scrollDirection by viewModel.scrollDirection.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val images by viewModel.images.collectAsState()

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
                        enabled = canDrawOverlays && selectedFolderUri != null
                    )
                }

                if (!canDrawOverlays || selectedFolderUri == null) {
                    Text(
                        "Please select a folder and grant overlay permission below to enable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Live In-App Simulator Card (Always visible and usable to test inside the app!)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Text("Interactive In-App Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Test your sidebar layout, scrolling direction, and 1-tap clipboard copying right inside the app without needing background services enabled.",
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

        // 1. Folder & Media Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. Folder & Media", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (images.isNotEmpty()) {
                        Text("${images.size} items", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Text(
                    text = if (selectedFolderUri != null) "Selected: $selectedFolderUri" else "No folder selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPickFolder, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (selectedFolderUri != null) "Change Folder" else "Select Folder")
                    }
                }

                if (selectedFolderUri != null) {
                    Button(
                        onClick = onPickMedia,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add / Import Images (+)")
                    }
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    HorizontalDivider()

                // Sorting
                Text("Sort By", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

                // Trigger Style
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

                // Panel Side
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

                    // Trigger Reach (Edge Swipe only)
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

                if (triggerType == TriggerType.FLOATING_BUTTON) {
                    HorizontalDivider()

                    // Auto-hide in landscape (Only for Floating Bubble)
                    val hideInLandscape by viewModel.hideInLandscape.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-hide in landscape", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Hides the floating bubble in fullscreen/video landscape mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = hideInLandscape,
                            onCheckedChange = { viewModel.setHideInLandscape(it) }
                        )
                    }
                }

                HorizontalDivider()

                // Carousel Scroll Direction
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

        // 3. Panel Dimensions & Layout Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3. Sidebar Dimensions & Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                val panelHeightPercent by viewModel.panelHeightPercent.collectAsState()
                val panelWidthPercent by viewModel.panelWidthPercent.collectAsState()
                val panelOpacityPercent by viewModel.panelOpacityPercent.collectAsState()

                // 1. Panel Vertical Height
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Panel Vertical Height (${panelHeightPercent}% of screen)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "How much vertical space the open sidebar occupies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = panelHeightPercent.toFloat(),
                        onValueChange = { viewModel.setPanelHeightPercent(it.toInt()) },
                        valueRange = 20f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // 2. Panel Width
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

                // 3. Background Opacity
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Background Opacity (${panelOpacityPercent}%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (panelOpacityPercent == 0) "Transparent" else if (panelOpacityPercent == 100) "Solid" else "${panelOpacityPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "Transparency of the sidebar canvas background (0% = invisible, 100% = solid).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = panelOpacityPercent.toFloat(),
                        onValueChange = { viewModel.setPanelOpacityPercent(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0 to "0% (Clear)", 35 to "35%", 70 to "70%", 100 to "100%").forEach { (value, label) ->
                            AssistChip(
                                onClick = { viewModel.setPanelOpacityPercent(value) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                colors = if (panelOpacityPercent == value)
                                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                else
                                    AssistChipDefaults.assistChipColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 4. Grid Columns
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

        // Permissions & Troubleshooting Card
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open App Info", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open App Info (3 Dots ⋮ Menu)")
                    }
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
}
}

/**
 * In-App Interactive Simulator & Live Preview Overlay.
 * Allows dumb-user testing of the sidebar, carousel direction, and copy flow
 * completely inside the app, whether background overlay is running or not.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InAppInteractiveSimulatorOverlay(
    viewModel: MainViewModel,
    isOpen: Boolean,
    onClose: () -> Unit,
    onPickMedia: () -> Unit
) {
    val panelSide by viewModel.panelSide.collectAsState()
    val panelWidth by viewModel.panelWidth.collectAsState()
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

    LaunchedEffect(guidePreviewUntil) {
        while (System.currentTimeMillis() < guidePreviewUntil) {
            isGuideActive = true
            kotlinx.coroutines.delay(100)
        }
        isGuideActive = false
    }

    // Top-anchored guide trigger bar preview inside app (only when service is not running to avoid double bars)
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

    // Animated Slide-In Sidebar Simulator
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
                    ) {},
                color = MaterialTheme.colorScheme.surface.copy(alpha = opacityAlpha),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Preview, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Simulator Preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (scrollDirection == ScrollDirection.TOP_TO_BOTTOM) "Scroll: Top to Bottom ↓" else "Scroll: Bottom to Top ↑",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close Preview")
                            }
                        }
                    }

                    // Content Grid (Honoring ScrollDirection reverseLayout!)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (images.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("No images in selected folder", style = MaterialTheme.typography.bodyMedium)
                                    Button(onClick = onPickMedia) {
                                        Text("Import Test Images")
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns),
                                reverseLayout = scrollDirection == ScrollDirection.TOP_TO_BOTTOM,
                                contentPadding = PaddingValues(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(images, key = { it.uri.toString() }) { item ->
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .combinedClickable(
                                                onClick = {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    scope.launch {
                                                        ClipboardUtils.copyImageToClipboard(context, item.uri)
                                                        Toast.makeText(context, "Copied to clipboard! (Simulator)", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                    ) {
                                        AsyncImage(
                                            model = item.uri,
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
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
                                "Tap any image to copy",
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
