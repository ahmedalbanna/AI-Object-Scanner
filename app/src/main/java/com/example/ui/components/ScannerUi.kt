package com.example.ui.components

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Gorgeous Cyan Slate / Cyber Obsidian Palette
val ObsidianBg = Color(0xFF0F1217)
val ObsidianSurface = Color(0xFF161B22)
val ObsidianCard = Color(0xFF21262D)
val NeonCyan = Color(0xFF00E5FF)
val ActiveTeal = Color(0xFF009688)
val DarkMuted = Color(0xFF8B949E)
val BorderColor = Color(0xFF30363D)

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
            viewModel.scanLocalImage(uri)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterCenterFocus,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "AI OBJECT SCANNER",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { activeTab = 2 }) {
                        Icon(
                            imageVector = if (customKey.isNotEmpty()) Icons.Default.VpnKey else Icons.Outlined.Key,
                            contentDescription = "API Keys",
                            tint = if (customKey.isNotEmpty()) NeonCyan else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ObsidianBg,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.border(1.dp, BorderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.DocumentScanner, contentDescription = "Scan") },
                    label = { Text("Scanner") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianBg,
                        selectedTextColor = NeonCyan,
                        indicatorColor = NeonCyan,
                        unselectedIconColor = DarkMuted,
                        unselectedTextColor = DarkMuted
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianBg,
                        selectedTextColor = NeonCyan,
                        indicatorColor = NeonCyan,
                        unselectedIconColor = DarkMuted,
                        unselectedTextColor = DarkMuted
                    )
                )
            }
        },
        containerColor = ObsidianBg,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Contents
            Crossfade(targetState = activeTab, label = "tab_switch") { tab ->
                when (tab) {
                    0 -> ScannerTab(
                        viewModel = viewModel,
                        hasCameraPermission = hasCameraPermission,
                        onRequestPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onGalleryPick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onReportGenerated = { report ->
                            selectedReportForDetails = report
                        }
                    )
                    1 -> HistoryTab(
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

            // Report Details Sheet overlay
            selectedReportForDetails?.let { report ->
                ReportDetailsModal(
                    report = report,
                    onDismiss = { selectedReportForDetails = null },
                    onDelete = {
                        viewModel.deleteReport(report)
                        selectedReportForDetails = null
                    }
                )
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
    onReportGenerated: (ScanReport) -> Unit
) {
    val context = LocalContext.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }

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
                    text = "Aesthetic Scanner",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Point camera, import image, or pick a demo object below to run an instant chemical-structural property analysis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkMuted
                )
            }
        }

        // Camera viewfinder card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        imageCapture = imageCapture,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Corner indicators to simulate laser scanner viewfinder bounds
                    ViewFinderOverlay(modifier = Modifier.fillMaxSize())

                    // Trigger Capture Button
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
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
                                            val savedBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                            if (savedBitmap != null) {
                                                viewModel.scanCapturedImage(savedBitmap)
                                            } else {
                                                Toast.makeText(context, "Error reading captured photo", Toast.LENGTH_SHORT).show()
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("ScannerUi", "Capture fails", exception)
                                        }
                                    }
                                )
                            },
                            containerColor = NeonCyan,
                            contentColor = ObsidianBg,
                            shape = CircleShape,
                            modifier = Modifier.shadow(8.dp, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "Capture Shot",
                                modifier = Modifier.size(28.dp)
                            )
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
                            tint = DarkMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera Access Required",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To enable standard live scanning, please grant camera permissions inside your app settings, or load a catalog sample.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg)
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Action Quick Utilities
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onGalleryPick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    border = BorderStroke(1.dp, NeonCyan),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Image", fontWeight = FontWeight.Bold)
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
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CATALOG SAMPLES FOR EMULATOR TESTING",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = Color.White
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
                            .background(ObsidianSurface)
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
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
                                    .background(ObsidianCard)
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
    Canvas(modifier = modifier) {
        val sizeVal = 32f
        val strokeW = 6f
        val padding = 20f

        // Top Left corner
        drawLine(
            color = NeonCyan,
            start = Offset(padding, padding),
            end = Offset(padding + sizeVal, padding),
            strokeWidth = strokeW
        )
        drawLine(
            color = NeonCyan,
            start = Offset(padding, padding),
            end = Offset(padding, padding + sizeVal),
            strokeWidth = strokeW
        )

        // Top Right corner
        drawLine(
            color = NeonCyan,
            start = Offset(size.width - padding, padding),
            end = Offset(size.width - padding - sizeVal, padding),
            strokeWidth = strokeW
        )
        drawLine(
            color = NeonCyan,
            start = Offset(size.width - padding, padding),
            end = Offset(size.width - padding, padding + sizeVal),
            strokeWidth = strokeW
        )

        // Bottom Left corner
        drawLine(
            color = NeonCyan,
            start = Offset(padding, size.height - padding),
            end = Offset(padding + sizeVal, size.height - padding),
            strokeWidth = strokeW
        )
        drawLine(
            color = NeonCyan,
            start = Offset(padding, size.height - padding),
            end = Offset(padding, size.height - padding - sizeVal),
            strokeWidth = strokeW
        )

        // Bottom Right corner
        drawLine(
            color = NeonCyan,
            start = Offset(size.width - padding, size.height - padding),
            end = Offset(size.width - padding - sizeVal, size.height - padding),
            strokeWidth = strokeW
        )
        drawLine(
            color = NeonCyan,
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
    reports: List<ScanReport>,
    onReportSelected: (ScanReport) -> Unit,
    onDeleteReport: (ScanReport) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortByDate by remember { mutableStateOf(true) } // true: Date, false: Name
    var sortAscending by remember { mutableStateOf(false) } // true: asc (Older / A-Z), false: desc (Newer / Z-A)

    val filteredAndSortedReports = remember(reports, searchQuery, sortByDate, sortAscending) {
        val filtered = if (searchQuery.isBlank()) {
            reports
        } else {
            reports.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true) ||
                it.primaryMaterial.contains(searchQuery, ignoreCase = true)
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
                tint = DarkMuted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanned Archives Empty",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Reports you generate via the Object Scanner will be archived securely right here inside local system storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkMuted,
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Scanned Archive History",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = Color.White
                    )

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name or category...", color = DarkMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = NeonCyan) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = DarkMuted)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = ObsidianSurface,
                            unfocusedContainerColor = ObsidianSurface
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

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
                                containerColor = if (sortByDate) NeonCyan.copy(alpha = 0.15f) else ObsidianSurface,
                                labelColor = if (sortByDate) NeonCyan else Color.White,
                                leadingIconContentColor = if (sortByDate) NeonCyan else DarkMuted
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (sortByDate) NeonCyan else BorderColor
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
                                containerColor = if (!sortByDate) NeonCyan.copy(alpha = 0.15f) else ObsidianSurface,
                                labelColor = if (!sortByDate) NeonCyan else Color.White,
                                leadingIconContentColor = if (!sortByDate) NeonCyan else DarkMuted
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (!sortByDate) NeonCyan else BorderColor
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurface)
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            .clickable { onReportSelected(report) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                        tint = DarkMuted,
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
                                            .background(NeonCyan.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = report.category.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                            color = NeonCyan
                                        )
                                    }
                                    Text(
                                        text = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(report.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DarkMuted
                                    )
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
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
            Text("Back to Scanner", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = NeonCyan)
        }

        Text(
            text = "System Key Settings",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, BorderColor),
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
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = DarkMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.setCustomApiKey(textState)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg),
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
// ANALYSIS REPORT MODAL COMPONENT
// ==========================================
@Composable
fun ReportDetailsModal(
    report: ScanReport,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .width(360.dp)
                .clickable(enabled = false) {} // block click propagation
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
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
                            // Online Asset URL
                            AsyncImage(
                                model = report.imageUrl,
                                contentDescription = report.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = DarkMuted, modifier = Modifier.size(48.dp))
                        }
                    }

                    // Floating Category tag label
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = report.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = NeonCyan)
                        )
                    }

                    // Gradient under title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.BottomStart)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, ObsidianBg),
                                    startY = 0f,
                                    endY = 120f
                                )
                            )
                    )
                }

                // Scrollable details column
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column {
                            Text(
                                text = report.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Analysis Timestamp: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(report.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkMuted
                            )
                        }
                    }

                    // Key properties grid layout (Custom badges)
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "DETERMINED PHYSICAL ATTRIBUTES",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = NeonCyan
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                PropertyBadge(Icons.Default.Widgets, "Primary Material", report.primaryMaterial, Modifier.weight(1f))
                                PropertyBadge(Icons.Default.Straighten, "Dimensions", report.dimensions, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                PropertyBadge(Icons.Default.Palette, "Color Spectrum", report.color, Modifier.weight(1f))
                                PropertyBadge(Icons.Default.FitnessCenter, "Weight Density", report.weight, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                PropertyBadge(Icons.Default.LocalMall, "Estimated Value", report.estimatedValue, Modifier.weight(1f))
                            }
                        }
                    }

                    // Insight narrative section
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "MOLECULAR & CHARACTERISTIC REPORT",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = NeonCyan
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianCard)
                                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = report.description,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Action controls footer bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurface)
                        .border(1.dp, BorderColor, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .background(Color(0x11FF5252), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close Report", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianCard)
            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold),
                    color = DarkMuted
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
