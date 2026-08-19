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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    var isServiceRunning by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onCheckPermission()
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
                Text("1. Folder Selection", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (selectedFolderUri != null) "Selected: $selectedFolderUri" else "No folder selected",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onPickFolder) {
                    Text("Select Image Folder")
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
                
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pm.isIgnoringBatteryOptimizations(context.packageName)
                } else true
                
                if (isIgnoringBatteryOptimizations) {
                    Text("Battery Optimization: Unrestricted (Optimal)", color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Battery Optimization is restricted. The service might be killed by the system.", color = MaterialTheme.colorScheme.error)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
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
                            isServiceRunning = it
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
