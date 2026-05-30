package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.data.ScanReport
import com.example.ui.ScanUiState
import com.example.ui.ScanViewModel
import com.example.ui.theme.*
import com.google.android.gms.location.LocationServices
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Gorgeous Cyan Slate / Cyber Obsidian Palette moved to Theme.kt or Color.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerUi(
    viewModel: ScanViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reports by viewModel.allReports.collectAsState()
    val state by viewModel.scanState.collectAsState()
    val customKey by viewModel.customApiKey.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = Scanner, 1 = History, 2 = Set Key
    var selectedReportForDetails by remember { mutableStateOf<ScanReport?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    fun captureLocation(onResult: (Double?, Double?) -> Unit) {
        if (!hasLocationPermission) {
            onResult(null, null)
            return
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                onResult(loc?.latitude, loc?.longitude)
            }.addOnFailureListener {
                onResult(null, null)
            }
        } catch (e: SecurityException) {
            onResult(null, null)
        }
    }

    // Check camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission denied. Use local picker or demo library.", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            captureLocation { lat, lon ->
                viewModel.scanLocalImages(listOf(uri), "", lat, lon)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = { /* Could open a side menu */ },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterCenterFocus,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "OBJECT AI",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { activeTab = 2 },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (customKey.isNotEmpty()) Icons.Default.VpnKey else Icons.Outlined.Key,
                            contentDescription = "API Keys",
                            tint = if (customKey.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.DocumentScanner, contentDescription = "Scan") },
                    label = { Text("Scanner") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Tab Contents
            Box(modifier = Modifier.padding(innerPadding)) {
                Crossfade(targetState = activeTab, label = "tab_switch") { tab ->
                    when (tab) {
                        0 -> ScannerTab(
                            viewModel = viewModel,
                            hasCameraPermission = hasCameraPermission,
                            onRequestPermission = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                locationPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            },
                            onGalleryPick = {
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onReportGenerated = { report ->
                                selectedReportForDetails = report
                            },
                            captureLocation = ::captureLocation
                        )
                        1 -> HistoryTab(
                            viewModel = viewModel,
                            reports = reports,
                            onReportSelected = { selectedReportForDetails = it },
                            onDeleteReport = { viewModel.deleteReport(it) }
                        )
                        2 -> SettingsTab(
                            viewModel = viewModel,
                            currentKey = customKey,
                            onBack = { activeTab = 0 }
                        )
                    }
                }

                // Report Details Full Screen overlay (Now inside innerPadding)
                AnimatedVisibility(
                    visible = selectedReportForDetails != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    selectedReportForDetails?.let { report ->
                        ReportDetailsScreen(
                            report = report,
                            viewModel = viewModel,
                            onDismiss = { selectedReportForDetails = null },
                            onDelete = {
                                viewModel.deleteReport(report)
                                selectedReportForDetails = null
                            }
                        )
                    }
                }
            }

            // Scanning Processing Overlay
            if (state is ScanUiState.Processing) {
                ProcessingOverlayDialog()
            }

            // Error Message Toast / Overlay
            if (state is ScanUiState.Error) {
                val errorMsg = (state as ScanUiState.Error).message
                ErrorOverlayDialog(
                    message = errorMsg,
                    onDismiss = { viewModel.resetState() },
                    onGoToApiKey = {
                        viewModel.resetState()
                        activeTab = 2
                    }
                )
            }

            // Success Transition Overlay (Launches the detail modal instantly)
            LaunchedEffect(state) {
                if (state is ScanUiState.Success) {
                    val report = (state as ScanUiState.Success).report
                    selectedReportForDetails = report
                    viewModel.resetState()
                }
            }
        }
    }
}

// ==========================================
// SCANNER TAB IMPLEMENTATION
// ==========================================
@Composable
fun ScannerTab(
    viewModel: ScanViewModel,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onGalleryPick: () -> Unit,
    onReportGenerated: (ScanReport) -> Unit,
    captureLocation: ((Double?, Double?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }

    var gatheredImagePaths = remember { mutableStateListOf<String>() }
    var userNote by remember { mutableStateOf("") }

    val demoSamples = listOf(
        DemoUrl("https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=400", "Ceramic Mug", "Kitchen"),
        DemoUrl("https://images.unsplash.com/photo-1523275335684-37898b6baf30?q=80&w=400", "Smart Watch", "Tech"),
        DemoUrl("https://images.unsplash.com/photo-1614594975525-e45190c55d0b?q=80&w=400", "Plant House", "Botany"),
        DemoUrl("https://images.unsplash.com/photo-1542291026-7eec264c27ff?q=80&w=400", "Red Sneaker", "Apparel"),
        DemoUrl("https://images.unsplash.com/photo-1507473885765-e6ed057f782c?q=80&w=400", "Desk Lamp", "Household")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App intro
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Vision Analysis",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Capture objects from multiple angles for a precise structural and contextual analysis.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Camera viewfinder card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        RoundedCornerShape(24.dp)
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                var isCameraActive by remember { mutableStateOf(false) }

                if (hasCameraPermission) {
                    if (isCameraActive) {
                        CameraPreview(
                            imageCapture = imageCapture,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Corner indicators to simulate laser scanner viewfinder bounds
                        ViewFinderOverlay(modifier = Modifier.fillMaxSize())

                        // Trigger Capture Button & Stop Button
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            IconButton(onClick = { isCameraActive = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Camera", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            
                            FloatingActionButton(
                                onClick = {
                                    val photoFile = File(
                                        context.cacheDir,
                                        "scan_temp_${System.currentTimeMillis()}.jpg"
                                    )
                                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                                    imageCapture.takePicture(
                                        outputOptions,
                                        cameraExecutor,
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                gatheredImagePaths.add(photoFile.absolutePath)
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                Log.e("ScannerUi", "Capture fails", exception)
                                            }
                                        }
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = CircleShape,
                                modifier = Modifier.shadow(12.dp, CircleShape).testTag("capture_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = "Capture Shot",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    } else {
                        // Start Camera Placeholder
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { isCameraActive = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text("INITIALIZE SENSORS", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                } else {
                    // Educational Fallback Card
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera Access Required",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Captured Images Preview & Note
        if (gatheredImagePaths.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "GATHERED PERSPECTIVES (${gatheredImagePaths.size})",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(gatheredImagePaths) { path ->
                            Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))) {
                                AsyncImage(
                                    model = path,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { gatheredImagePaths.remove(path) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = userNote,
                        onValueChange = { userNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Add specific notes or suspected characteristics...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background
                        ),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 3
                    )

                    Button(
                        onClick = {
                            captureLocation { lat, lon ->
                                viewModel.scanCapturedPaths(gatheredImagePaths.toList(), userNote, lat, lon)
                                gatheredImagePaths.clear()
                                userNote = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("INITIATE AGGREGATE SCAN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Action Quick Utilities
        item {
            val galleryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickMultipleVisualMedia()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    captureLocation { lat, lon ->
                        viewModel.scanLocalImages(uris, "", lat, lon)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Batch Import", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Demo Library section header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CATALOG SAMPLES FOR EMULATOR TESTING",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Demo Items grid list
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
            ) {
                items(demoSamples) { sample ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.scanRemoteDemoImage(sample.url, sample.title)
                            }
                    ) {
                        Column {
                            // Object preview image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = sample.url,
                                    contentDescription = sample.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Category Tag label
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = sample.category,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = NeonCyan
                                    )
                                }
                            }
                            // Name & Analysis request button
                            Column(
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Text(
                                    text = sample.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Scan demo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeonCyan
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ViewFinder overlay corners draw
@Composable
fun ViewFinderOverlay(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val sizeVal = 32f
        val strokeW = 6f
        val padding = 20f

        // Top Left corner
        drawLine(
            color = primaryColor,
            start = Offset(padding, padding),
            end = Offset(padding + sizeVal, padding),
            strokeWidth = strokeW
        )
        drawLine(
            color = primaryColor,
            start = Offset(padding, padding),
            end = Offset(padding, padding + sizeVal),
            strokeWidth = strokeW
        )

        // Top Right corner
        drawLine(
            color = primaryColor,
            start = Offset(size.width - padding, padding),
            end = Offset(size.width - padding - sizeVal, padding),
            strokeWidth = strokeW
        )
        drawLine(
            color = primaryColor,
            start = Offset(size.width - padding, padding),
            end = Offset(size.width - padding, padding + sizeVal),
            strokeWidth = strokeW
        )

        // Bottom Left corner
        drawLine(
            color = primaryColor,
            start = Offset(padding, size.height - padding),
            end = Offset(padding + sizeVal, size.height - padding),
            strokeWidth = strokeW
        )
        drawLine(
            color = primaryColor,
            start = Offset(padding, size.height - padding),
            end = Offset(padding, size.height - padding - sizeVal),
            strokeWidth = strokeW
        )

        // Bottom Right corner
        drawLine(
            color = primaryColor,
            start = Offset(size.width - padding, size.height - padding),
            end = Offset(size.width - padding - sizeVal, size.height - padding),
            strokeWidth = strokeW
        )
        drawLine(
            color = primaryColor,
            start = Offset(size.width - padding, size.height - padding),
            end = Offset(size.width - padding, size.height - padding - sizeVal),
            strokeWidth = strokeW
        )
    }
}

// Data class representing demo samples
data class DemoUrl(val url: String, val title: String, val category: String)

// ==========================================
// HISTORY TAB IMPLEMENTATION
// ==========================================
@Composable
fun HistoryTab(
    viewModel: ScanViewModel,
    reports: List<ScanReport>,
    onReportSelected: (ScanReport) -> Unit,
    onDeleteReport: (ScanReport) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCollectionFilter by remember { mutableStateOf<String?>(null) }
    var sortByDate by remember { mutableStateOf(true) } // true: Date, false: Name
    var sortAscending by remember { mutableStateOf(false) } // true: asc (Older / A-Z), false: desc (Newer / Z-A)

    val allCollections = remember(reports) {
        reports.mapNotNull { it.collectionName }.distinct()
    }

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedReports = remember { mutableStateListOf<ScanReport>() }
    var showComparison by remember { mutableStateOf(false) }

    val filteredAndSortedReports = remember(reports, searchQuery, sortByDate, sortAscending, selectedCollectionFilter) {
        val filtered = if (searchQuery.isBlank() && selectedCollectionFilter == null) {
            reports
        } else {
            reports.filter {
                val matchesSearch = it.title.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true) ||
                        it.primaryMaterial.contains(searchQuery, ignoreCase = true) ||
                        it.userTags.contains(searchQuery, ignoreCase = true)
                
                val matchesCollection = selectedCollectionFilter == null || it.collectionName == selectedCollectionFilter
                
                matchesSearch && matchesCollection
            }
        }

        if (sortByDate) {
            if (sortAscending) {
                filtered.sortedBy { it.timestamp }
            } else {
                filtered.sortedByDescending { it.timestamp }
            }
        } else {
            if (sortAscending) {
                filtered.sortedBy { it.title.lowercase() }
            } else {
                filtered.sortedByDescending { it.title.lowercase() }
            }
        }
    }

    if (reports.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Pageview,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanned Archives Empty",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Reports you generate via the Object Scanner will be archived securely right here inside local system storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scanned Archive History",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    TextButton(
                        onClick = {
                            isSelectionMode = !isSelectionMode
                            if (!isSelectionMode) selectedReports.clear()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.Close else Icons.Default.LibraryAddCheck,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSelectionMode) "Cancel Select" else "Select to Compare")
                    }
                }
            }
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name or category...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Collection filters
                    if (allCollections.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCollectionFilter == null,
                                        onClick = { selectedCollectionFilter = null },
                                        label = { Text("All Scans") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = NeonCyan
                                        )
                                    )
                                }
                                items(allCollections) { coll ->
                                    FilterChip(
                                        selected = selectedCollectionFilter == coll,
                                        onClick = { selectedCollectionFilter = coll },
                                        label = { Text(coll) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = NeonCyan
                                        )
                                    )
                                }
                            }
                            
                            if (selectedCollectionFilter != null) {
                                IconButton(
                                    onClick = { 
                                        viewModel.shareCollection(selectedCollectionFilter!!, reports, context)
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share Collection", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    // Sorting controllers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sort by:",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkMuted
                        )

                        // Date sorting chip using AssistChip
                        AssistChip(
                            onClick = {
                                if (sortByDate) {
                                    sortAscending = !sortAscending
                                } else {
                                    sortByDate = true
                                    sortAscending = false // Newer first
                                }
                            },
                            label = {
                                Text(
                                    text = if (sortByDate) {
                                        if (sortAscending) "Date (Older)" else "Date (Newer)"
                                    } else "Date",
                                    fontWeight = if (sortByDate) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (sortByDate) {
                                        if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                                    } else Icons.Default.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (sortByDate) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                labelColor = if (sortByDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                leadingIconContentColor = if (sortByDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (sortByDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        // Name sorting chip using AssistChip
                        AssistChip(
                            onClick = {
                                if (!sortByDate) {
                                    sortAscending = !sortAscending
                                } else {
                                    sortByDate = false
                                    sortAscending = true // A-Z
                                }
                            },
                            label = {
                                Text(
                                    text = if (!sortByDate) {
                                        if (sortAscending) "Name (A-Z)" else "Name (Z-A)"
                                    } else "Name",
                                    fontWeight = if (!sortByDate) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (!sortByDate) {
                                        if (sortAscending) Icons.Default.SortByAlpha else Icons.Default.ArrowDownward
                                    } else Icons.Default.SortByAlpha,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (!sortByDate) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                labelColor = if (!sortByDate) MaterialTheme.colorScheme.primary else Color.White,
                                leadingIconContentColor = if (!sortByDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (!sortByDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }

            if (filteredAndSortedReports.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                         Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = DarkMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No matching scans found",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try refining your search keyword",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkMuted
                        )
                    }
                }
            } else {
                items(filteredAndSortedReports) { report ->
                    val isSelected = selectedReports.any { it.id == report.id }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) NeonCyan.copy(alpha = 0.05f) else ObsidianSurface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) NeonCyan else BorderColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (isSelectionMode) {
                                    if (isSelected) {
                                        selectedReports.removeAll { it.id == report.id }
                                    } else {
                                        selectedReports.add(report)
                                    }
                                } else {
                                    onReportSelected(report)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedReports.add(report)
                                        else selectedReports.removeAll { it.id == report.id }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeonCyan,
                                        uncheckedColor = DarkMuted,
                                        checkmarkColor = ObsidianBg
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            // Thumbnail image view
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianCard)
                            ) {
                                if (report.imageUrl != null) {
                                    val file = File(report.imageUrl)
                                    if (file.exists()) {
                                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = report.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else {
                                        // Remote sample URL
                                        AsyncImage(
                                            model = report.imageUrl,
                                            contentDescription = report.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.BrokenImage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Details columns
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = report.category.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(report.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DarkMuted
                                    )
                                }
                                
                                if (report.collectionName != null || report.userRating > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (report.collectionName != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Folder, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(report.collectionName, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = NeonCyan)
                                            }
                                        }
                                        if (report.userRating > 0) {
                                            Row {
                                                repeat(report.userRating) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(10.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = report.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = report.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Trash option
                            IconButton(onClick = { onDeleteReport(report) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete entry",
                                    tint = Color(0xFFFF5252).copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Comparison FAB
    if (isSelectionMode && selectedReports.size >= 2) {
        Box(modifier = Modifier.fillMaxSize()) {
            ExtendedFloatingActionButton(
                onClick = { showComparison = true },
                icon = { Icon(Icons.Default.CompareArrows, contentDescription = null) },
                text = { Text("Compare (${selectedReports.size})") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }

    if (showComparison) {
        ComparisonDialog(
            reports = selectedReports.toList(),
            onDismiss = { showComparison = false }
        )
    }
}

// ==========================================
// API KEY CONFIGURATION SETTINGS TAB
// ==========================================
@Composable
fun SettingsTab(
    viewModel: ScanViewModel,
    currentKey: String,
    onBack: () -> Unit
) {
    var textState by remember { mutableStateOf(currentKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onBack() }
        ) {
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            Text("Back to Scanner", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        }

        Text(
            text = "System Key Settings",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure Custom Gemini API Key",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                Text(
                    text = "By default, this app attempts to load the API key securely injected at runtime from the AI Studio Workspace Secrets panel. If no key is present, you may paste a temporary key below. This key is stored encrypted on-device only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkMuted
                )

                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("Enter your AI Studio API key...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.setCustomApiKey(textState)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply & Lock Key", fontWeight = FontWeight.Bold)
                }

                if (currentKey.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            viewModel.setCustomApiKey("")
                            textState = ""
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset to Injected Workspace Key")
                    }
                }
            }
        }
    }
}

// ==========================================
// ANIMATED LASER SCANNER AND PREVIEWS
// ==========================================
@Composable
fun ScanLaserAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_offset"
    )

    Canvas(modifier = modifier) {
        val yPos = size.height * offsetY
        // Line Laser Laser
        drawLine(
            color = NeonCyan,
            start = Offset(0f, yPos),
            end = Offset(size.width, yPos),
            strokeWidth = 6f
        )
        // Sweep glow
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    NeonCyan.copy(alpha = 0.15f),
                    NeonCyan.copy(alpha = 0.35f),
                    NeonCyan.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                startY = yPos - 35f,
                endY = yPos + 35f
            ),
            topLeft = Offset(0f, yPos - 35f),
            size = Size(size.width, 70f)
        )
    }
}

// Loading analysis dialog Overlay
@Composable
fun ProcessingOverlayDialog() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, NeonCyan, RoundedCornerShape(16.dp))
                    .background(ObsidianBg)
            ) {
                // Background animated scanner laser line
                ScanLaserAnimation(modifier = Modifier.fillMaxSize())

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterCenterFocus,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "MOLECULAR SPECTRAL SCANNER",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Gemini AI is parsing image signatures to compile structural composition & physical property projections...",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(300.dp)
            )
        }
    }
}

// Error overlay screen
@Composable
fun ErrorOverlayDialog(
    message: String,
    onDismiss: () -> Unit,
    onGoToApiKey: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
            modifier = Modifier
                .width(330.dp)
                .clickable(enabled = false) {}, // prevent click-through
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(50.dp)
                )

                Text(
                    text = "Scan Extraction Fails",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkMuted,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dismiss")
                    }

                    if (message.contains("API key")) {
                        Button(
                            onClick = onGoToApiKey,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Setup Key", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ANALYSIS REPORT SCREEN COMPONENT
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailsScreen(
    report: ScanReport,
    viewModel: ScanViewModel,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var editedTags by remember { mutableStateOf(report.userTags) }
    var editedNotes by remember { mutableStateOf(report.userNotes) }
    var editedRating by remember { mutableIntStateOf(report.userRating) }
    var selectedCollection by remember { mutableStateOf(report.collectionName) }

    val allCollections by viewModel.allCollections.collectAsState(initial = emptyList())

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Enhanced Custom Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (isEditing) "EDIT SCAN" else report.title.uppercase(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = OrbitronFont
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (!isEditing) {
                    IconButton(onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Object AI Analysis: ${report.title}\n${report.description}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
                    }
                } else {
                    IconButton(onClick = { isEditing = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Header Image
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            val imageUrl = report.imageUrl
                            if (imageUrl != null) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = report.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BrokenImage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }

                            // Dynamic category badge
                            Surface(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(Alignment.BottomStart),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = report.category.uppercase(),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }

                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (isEditing) {
                                EditingLayout(
                                    editedTags = editedTags,
                                    onTagsChange = { editedTags = it },
                                    editedNotes = editedNotes,
                                    onNotesChange = { editedNotes = it },
                                    editedRating = editedRating,
                                    onRatingChange = { editedRating = it },
                                    selectedCollection = selectedCollection,
                                    onCollectionChange = { selectedCollection = it },
                                    allCollections = allCollections
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Button(
                                    onClick = {
                                        viewModel.updateReport(report.copy(
                                            userTags = editedTags,
                                            userNotes = editedNotes,
                                            userRating = editedRating,
                                            collectionName = selectedCollection
                                        ))
                                        isEditing = false
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("SAVE MODIFICATIONS", style = MaterialTheme.typography.labelLarge.copy(fontFamily = OrbitronFont))
                                }
                            } else {
                                ReadOnlyLayout(report, selectedCollection)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReadOnlyLayout(report: ScanReport, selectedCollection: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Title & Time
        Column {
            Text(
                text = report.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = OrbitronFont
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(report.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Details Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (report.userRating > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) { i ->
                        Icon(
                            imageVector = if (i < report.userRating) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (i < report.userRating) Color(0xFFFFD700) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (selectedCollection != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(selectedCollection, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Attributes Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeaderTitle("CHARACTERISTICS")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PropertyBadge(Icons.Default.Widgets, "Material", report.primaryMaterial, Modifier.weight(1f))
                PropertyBadge(Icons.Default.Straighten, "Size", report.dimensions, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PropertyBadge(Icons.Default.Palette, "Color", report.color, Modifier.weight(1f))
                PropertyBadge(Icons.Default.FitnessCenter, "Weight", report.weight, Modifier.weight(1f))
            }
            PropertyBadge(Icons.Default.LocalMall, "Estimated Value", report.estimatedValue, Modifier.fillMaxWidth())
        }

        // Analysis
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeaderTitle("AI DATA ANALYSIS")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = report.description,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp, fontFamily = OutfitFont),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Insight (Dynamic)
        if (report.dynamicDetail.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeaderTitle("CONTEXTUAL INTELLIGENCE")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = report.dynamicDetail,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 24.sp, 
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontFamily = OutfitFont
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeaderTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold, 
            letterSpacing = 1.2.sp,
            fontFamily = OrbitronFont
        ),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun EditingLayout(
    editedTags: String,
    onTagsChange: (String) -> Unit,
    editedNotes: String,
    onNotesChange: (String) -> Unit,
    editedRating: Int,
    onRatingChange: (Int) -> Unit,
    selectedCollection: String?,
    onCollectionChange: (String?) -> Unit,
    allCollections: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHeaderTitle("MODIFY METADATA")
        
        // Rating Selection
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SCAN ACCURACY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (i in 1..5) {
                        IconButton(onClick = { onRatingChange(i) }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = if (i <= editedRating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                contentDescription = null,
                                tint = if (i <= editedRating) Color(0xFFFFD700) else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
        
        // Collection selection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("COLLECTION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCollection == null,
                        onClick = { onCollectionChange(null) },
                        label = { Text("None") }
                    )
                }
                items(allCollections) { collection ->
                    FilterChip(
                        selected = selectedCollection == collection,
                        onClick = { onCollectionChange(collection) },
                        label = { Text(collection) }
                    )
                }
            }
        }
        
        OutlinedTextField(
            value = editedTags,
            onValueChange = onTagsChange,
            label = { Text("ID TAGS") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = editedNotes,
            onValueChange = onNotesChange,
            label = { Text("NOTES") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// Beautiful property badge component
@Composable
fun PropertyBadge(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonDialog(
    reports: List<ScanReport>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Side-by-Side Analysis",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Comparison Table
                Box(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        // Metrics Column
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(top = 116.dp)
                        ) {
                            MetricHeader("Category")
                            MetricHeader("Material")
                            MetricHeader("Dimensions")
                            MetricHeader("Color")
                            MetricHeader("Value")
                            MetricHeader("Weight")
                            MetricHeader("Context Detail")
                            MetricHeader("Trivia")
                        }

                        // Data Columns
                        reports.forEach { report ->
                            Column(
                                modifier = Modifier
                                    .width(200.dp)
                                    .fillMaxHeight()
                                    .border(0.5.dp, BorderColor),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Image & Title
                                Box(modifier = Modifier.size(100.dp).padding(8.dp).clip(RoundedCornerShape(8.dp))) {
                                    if (report.imageUrl != null) {
                                        val file = File(report.imageUrl)
                                        if (file.exists()) {
                                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        } else {
                                            AsyncImage(
                                                model = report.imageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = report.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                MetricValue(report.category)
                                MetricValue(report.primaryMaterial)
                                MetricValue(report.dimensions)
                                MetricValue(report.color)
                                MetricValue(report.estimatedValue)
                                MetricValue(report.weight)
                                MetricValue(report.dynamicDetail)
                                MetricValue(report.knowledgeBit)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun MetricHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MetricValue(value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(8.dp)
            .border(0.2.dp, BorderColor.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
