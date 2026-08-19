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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.setFolderUri(uri)
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
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
                        onToggleService = { start ->
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
                        },
                        canDrawOverlays = canDrawOverlays,
                        onCheckPermission = {
                            canDrawOverlays = Settings.canDrawOverlays(this@MainActivity)
                        }
                    )
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
    onPickMedia: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    canDrawOverlays: Boolean,
    onCheckPermission: () -> Unit
) {
    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val triggerType by viewModel.triggerType.collectAsState()
    val panelSide by viewModel.panelSide.collectAsState()
    val panelWidth by viewModel.panelWidth.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val isServiceRunning by OverlayService.isRunning.collectAsState()
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current

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
        Text("SideGallery Setup", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Folder & Media", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (selectedFolderUri != null) "Selected: $selectedFolderUri" else "No folder selected",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPickFolder, modifier = Modifier.weight(1f)) {
                        Text("Select / Create Folder")
                    }
                }
                if (selectedFolderUri != null) {
                    Button(onClick = onPickMedia, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                        Text("Add / Import Images (+)")
                    }
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Sorting Options", style = MaterialTheme.typography.titleMedium)
                
                Column(Modifier.selectableGroup()) {
                    SortOption.values().forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .selectable(
                                    selected = (option == sortOption),
                                    onClick = { viewModel.setSortOption(option) },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == sortOption),
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option.name.replace("_", " "))
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Layout & Personalization", style = MaterialTheme.typography.titleMedium)
                
                // Grid Columns
                Text("Grid Columns: $gridColumns", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = gridColumns.toFloat(),
                    onValueChange = { viewModel.setGridColumns(it.toInt()) },
                    valueRange = 1f..4f,
                    steps = 2
                )

                // Panel Side
                Text("Panel Side", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = panelSide == PanelSide.LEFT,
                        onClick = { viewModel.setPanelSide(PanelSide.LEFT) },
                        label = { Text("Left") }
                    )
                    FilterChip(
                        selected = panelSide == PanelSide.RIGHT,
                        onClick = { viewModel.setPanelSide(PanelSide.RIGHT) },
                        label = { Text("Right") }
                    )
                }

                // Panel Width
                Text("Panel Width", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = panelWidth == PanelWidth.THIRD,
                        onClick = { viewModel.setPanelWidth(PanelWidth.THIRD) },
                        label = { Text("1/3") }
                    )
                    FilterChip(
                        selected = panelWidth == PanelWidth.HALF,
                        onClick = { viewModel.setPanelWidth(PanelWidth.HALF) },
                        label = { Text("1/2") }
                    )
                    FilterChip(
                        selected = panelWidth == PanelWidth.TWO_THIRDS,
                        onClick = { viewModel.setPanelWidth(PanelWidth.TWO_THIRDS) },
                        label = { Text("2/3") }
                    )
                }
                
                // Trigger Type
                Text("Trigger Method", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = triggerType == TriggerType.EDGE_SWIPE,
                        onClick = { viewModel.setTriggerType(TriggerType.EDGE_SWIPE) },
                        label = { Text("Edge Swipe") }
                    )
                    FilterChip(
                        selected = triggerType == TriggerType.FLOATING_BUTTON,
                        onClick = { viewModel.setTriggerType(TriggerType.FLOATING_BUTTON) },
                        label = { Text("Floating Button") }
                    )
                }

                // If Edge Swipe is selected, show swipe height customization & live guide preview
                if (triggerType == TriggerType.EDGE_SWIPE) {
                    val swipeHeightPercent by viewModel.swipeHeightPercent.collectAsState()
                    var manualInput by remember(swipeHeightPercent) { mutableStateOf(swipeHeightPercent.toString()) }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Swipe Bar Height: ${swipeHeightPercent}% (From Top)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Starts from the top edge. Shorter heights keep the keyboard area completely free.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Slider from 10% to 100%
                            Slider(
                                value = swipeHeightPercent.toFloat(),
                                onValueChange = {
                                    viewModel.setSwipeHeightPercent(kotlin.math.round(it).toInt(), showGuide = true)
                                },
                                valueRange = 10f..100f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Quick Presets
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(100 to "100% (Full)", 75 to "75%", 50 to "50%", 25 to "25%").forEach { (value, label) ->
                                    AssistChip(
                                        onClick = { viewModel.setSwipeHeightPercent(value, showGuide = true) },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        colors = if (swipeHeightPercent == value)
                                            AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                        else
                                            AssistChipDefaults.assistChipColors()
                                    )
                                }
                            }

                            // Manual Number Input + Preview Button
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = manualInput,
                                    onValueChange = { input ->
                                        val filtered = input.filter { it.isDigit() }.take(3)
                                        manualInput = filtered
                                        val parsed = filtered.toIntOrNull()
                                        if (parsed != null && parsed in 10..100) {
                                            viewModel.setSwipeHeightPercent(parsed, showGuide = true)
                                        }
                                    },
                                    label = { Text("Exact %") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(96.dp)
                                )

                                OutlinedButton(
                                    onClick = { viewModel.triggerGuidePreview(3000L) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Preview Guide",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Preview Guide (3s)")
                                }
                            }
                        }
                    }
                }

                // Theme Mode
                val themeMode by viewModel.themeMode.collectAsState()
                Text("Overlay Theme", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                        label = { Text("System") }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                        label = { Text("Light") }
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                        label = { Text("Dark") }
                    )
                }

                // Hide in Landscape
                val hideInLandscape by viewModel.hideInLandscape.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-hide in landscape")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = hideInLandscape,
                        onCheckedChange = { viewModel.setHideInLandscape(it) }
                    )
                }
            }
        }

        // Card 4: Android 13+ Restricted Settings Guide (Sideloaded / APK Special Permissions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Android 13+ Restricted Settings (If permissions are grayed out)", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "If Android shows 'Restricted setting' or grays out the overlay/accessibility permission when opening the settings:\n" +
                                "1. Click 'Open App Info' below.\n" +
                                "2. Tap the 3 dots (⋮) in the top-right corner.\n" +
                                "3. Tap 'Allow restricted settings' (Consenti impostazioni con restrizioni).\n" +
                                "4. Authenticate (PIN/Fingerprint) and return here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("4. Permissions", style = MaterialTheme.typography.titleMedium)
                
                if (canDrawOverlays) {
                    Text("Overlay Permission: Granted", color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Required to show the sidebar.", color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRequestOverlayPermission) {
                        Text("Grant Overlay Permission")
                    }
                }
                
                if (isIgnoringBatteryOptimizations) {
                    Text("Battery Optimization: Unrestricted (Optimal)", color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Battery Optimization is restricted. The service might be killed by the system.", color = MaterialTheme.colorScheme.error)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }) {
                            Text("Disable Restrictions")
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("5. Start Service", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Sidebar Overlay")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = {
                            onToggleService(it)
                        },
                        enabled = canDrawOverlays && selectedFolderUri != null
                    )
                }
                
                if (isServiceRunning) {
                    Button(
                        onClick = {
                            val intent = Intent(context, OverlayService::class.java)
                            context.stopService(intent)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                                Toast.makeText(context, "Service Refreshed", Toast.LENGTH_SHORT).show()
                            }, 300)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh Service & Apply Changes")
                    }
                }
                
                if (!canDrawOverlays || selectedFolderUri == null) {
                    Text(
                        "Please select a folder and grant permission first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
