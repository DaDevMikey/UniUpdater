package com.universal.updater.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.universal.updater.R
import com.universal.updater.data.PrefManager
import com.universal.updater.data.model.RomUpdateInfo
import com.universal.updater.service.OtaDownloadService
import com.universal.updater.theme.ThemeTokens
import com.universal.updater.ui.components.MarkdownText
import com.universal.updater.ui.simulation.SimulationActivity
import com.universal.updater.utils.RootUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(context.applicationContext) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val appUpdateProgress by viewModel.appUpdateProgress.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadProgressState.collectAsStateWithLifecycle()
    val isRootAvailable by viewModel.isRootAvailable.collectAsStateWithLifecycle()
    val isSystemApp by viewModel.isSystemApp.collectAsStateWithLifecycle()
    val customJsonUrl by viewModel.customJsonUrl.collectAsStateWithLifecycle()
    val checkAppUpdates by viewModel.checkAppUpdates.collectAsStateWithLifecycle()
    val downloadOverWifi by viewModel.downloadOverWifi.collectAsStateWithLifecycle()
    val autoInstallRoot by viewModel.autoInstallRoot.collectAsStateWithLifecycle()

    var showInstructionsDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showRebootPrompt by remember { mutableStateOf(false) }
    var downloadFilePath by remember { mutableStateOf("") }
    var instructionRomName by remember { mutableStateOf("") }

    // Re-check settings when coming back (ON_RESUME)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Trigger update checks when screen is visible
    LaunchedEffect(uiState) {
        if (uiState is MainUiState.Idle) {
            viewModel.checkForUpdates()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = ThemeTokens.MidnightBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            OneUiLayout(
                uiState = uiState,
                appUpdateState = appUpdateState,
                appUpdateProgress = appUpdateProgress,
                onDownloadAppUpdate = { apkUrl -> viewModel.downloadAndInstallAppUpdate(context, apkUrl) },
                downloadState = downloadState,
                isRootAvailable = isRootAvailable,
                isSystemApp = isSystemApp,
                customJsonUrl = customJsonUrl,
                onCheckUpdates = { viewModel.checkForUpdates() },
                onDownload = { info -> viewModel.startOtaDownload(context, info) },
                onCancelDownload = { viewModel.cancelOtaDownload(context) },
                onInstall = { path, romName ->
                    if (isRootAvailable) {
                        viewModel.triggerRootInstall(
                            filePath = path,
                            onSuccess = {
                                Toast.makeText(context, "Rebooting to recovery to install update...", Toast.LENGTH_LONG).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        downloadFilePath = path
                        instructionRomName = romName
                        showInstructionsDialog = true
                    }
                },
                onOpenSettings = { showSettings = true }
            )

            // Settings Dialog (accessible via Gear icon)
            SettingsDialog(
                show = showSettings,
                onDismiss = { showSettings = false },
                viewModel = viewModel,
                isRootAvailable = isRootAvailable,
                checkAppUpdates = checkAppUpdates,
                downloadOverWifi = downloadOverWifi,
                autoInstallRoot = autoInstallRoot,
                onShowRebootPrompt = { showRebootPrompt = true }
            )

            // Reboot Required dialog
            if (showRebootPrompt) {
                AlertDialog(
                    onDismissRequest = { showRebootPrompt = false },
                    title = { Text("Reboot Required", fontWeight = FontWeight.Bold) },
                    text = { Text("UniUpdater successfully copied to system priv-app directory. Reboot your device now to apply system permissions.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showRebootPrompt = false
                                com.universal.updater.utils.RootUtils.reboot()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.AccentIndigo)
                        ) {
                            Text("Reboot Now")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRebootPrompt = false }) {
                            Text("Later", color = ThemeTokens.TextSecondary)
                        }
                    },
                    containerColor = ThemeTokens.CardSurface,
                    titleContentColor = ThemeTokens.TextPrimary,
                    textContentColor = ThemeTokens.TextSecondary
                )
            }

            // Manual Instructions Dialog
            if (showInstructionsDialog) {
                AlertDialog(
                    onDismissRequest = { showInstructionsDialog = false },
                    title = { Text("Manual Install Guide", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No Root access was found, or auto-install is disabled. You can flash the update manually in recovery:")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("1. The update ZIP file is stored in your internal storage at:")
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ThemeTokens.MidnightBackground),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = downloadFilePath,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(12.dp),
                                    color = ThemeTokens.AccentCyan
                                )
                            }
                            Text("2. Boot your phone into Custom Recovery (TWRP / OrangeFox / Lineage Recovery).")
                            Text("3. Locate the ZIP in recovery at:")
                            Text("/sdcard/Android/data/com.universal.updater/files/Download/ota_update.zip", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeTokens.TextPrimary)
                            Text("4. Flash the ZIP file and reboot system.")
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showInstructionsDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.AccentIndigo)
                        ) {
                            Text("Got It")
                        }
                    },
                    dismissButton = {
                        if (isRootAvailable) {
                            TextButton(
                                onClick = {
                                    showInstructionsDialog = false
                                    RootUtils.rebootRecovery()
                                }
                            ) {
                                Text("Reboot to Recovery", color = Color.Red)
                            }
                        }
                    },
                    containerColor = ThemeTokens.CardSurface,
                    titleContentColor = ThemeTokens.TextPrimary,
                    textContentColor = ThemeTokens.TextSecondary
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    viewModel: MainScreenViewModel,
    isRootAvailable: Boolean,
    checkAppUpdates: Boolean,
    downloadOverWifi: Boolean,
    autoInstallRoot: Boolean,
    onShowRebootPrompt: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    var isCheckingAppUpdates by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val customJsonUrl by viewModel.customJsonUrl.collectAsStateWithLifecycle()
    val updateCheckInterval by viewModel.updateCheckInterval.collectAsStateWithLifecycle()
    val isSystemApp by viewModel.isSystemApp.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Updater Settings", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("OTA Update Preferences", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeTokens.AccentCyan)

                // Auto-install updates (Root)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Install with Root", color = ThemeTokens.TextPrimary, fontSize = 14.sp)
                        Text(if (isRootAvailable) "Flash recovery automatically via su scripts" else "Root access unavailable", color = ThemeTokens.TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoInstallRoot && isRootAvailable,
                        enabled = isRootAvailable,
                        onCheckedChange = { viewModel.setAutoInstallRoot(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ThemeTokens.AccentCyan,
                            checkedTrackColor = ThemeTokens.AccentIndigo
                        )
                    )
                }

                // Download over Wi-Fi only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Download over Wi-Fi", color = ThemeTokens.TextPrimary, fontSize = 14.sp)
                        Text("Pause download on mobile networks", color = ThemeTokens.TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = downloadOverWifi,
                        onCheckedChange = { viewModel.setDownloadOverWifi(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ThemeTokens.AccentCyan,
                            checkedTrackColor = ThemeTokens.AccentIndigo
                        )
                    )
                }

                // Check for Updater app updates (Startup)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Check for App Updates", color = ThemeTokens.TextPrimary, fontSize = 14.sp)
                        Text("Search for Updater client releases on boot", color = ThemeTokens.TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = checkAppUpdates,
                        onCheckedChange = { viewModel.setCheckAppUpdates(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ThemeTokens.AccentCyan,
                            checkedTrackColor = ThemeTokens.AccentIndigo
                        )
                    )
                }

                // Check app updates button
                Button(
                    onClick = {
                        isCheckingAppUpdates = true
                        viewModel.checkAppUpdatesNow(context) { result ->
                            isCheckingAppUpdates = false
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.AccentIndigo),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isCheckingAppUpdates
                ) {
                    if (isCheckingAppUpdates) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Check for Updater Updates", fontSize = 13.sp)
                    }
                }

                val isAdvancedSettingsEnabled = LocalContext.current.resources.getBoolean(R.bool.enable_advanced_settings)
                if (isAdvancedSettingsEnabled) {
                    HorizontalDivider(color = ThemeTokens.DividerColor)

                    // Advanced Settings Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Advanced Settings", fontWeight = FontWeight.Bold, color = ThemeTokens.TextPrimary, fontSize = 14.sp)
                        Text(if (showAdvanced) "Hide" else "Show", color = ThemeTokens.AccentCyan, fontSize = 12.sp)
                    }

                    if (showAdvanced) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Update Server URL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeTokens.AccentCyan)
                            
                            var editingUrl by remember(customJsonUrl) { mutableStateOf(customJsonUrl) }
                            OutlinedTextField(
                                value = editingUrl,
                                onValueChange = {
                                    editingUrl = it
                                    viewModel.setCustomJsonUrl(it)
                                },
                                label = { Text("Update Server URL") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = ThemeTokens.TextPrimary,
                                    unfocusedTextColor = ThemeTokens.TextPrimary,
                                    focusedBorderColor = ThemeTokens.AccentIndigo,
                                    unfocusedBorderColor = ThemeTokens.DividerColor
                                )
                            )
                            
                            Button(
                                onClick = {
                                    editingUrl = viewModel.defaultJsonUrl
                                    viewModel.setCustomJsonUrl(viewModel.defaultJsonUrl)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Reset Server URL", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Check Updates Frequency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeTokens.AccentCyan)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val intervals = listOf("MANUAL", "DAILY", "WEEKLY")
                                intervals.forEach { interval ->
                                    val selected = updateCheckInterval == interval
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) ThemeTokens.AccentIndigo else ThemeTokens.MidnightBackground
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.setUpdateCheckInterval(interval) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                interval,
                                                color = if (selected) Color.White else ThemeTokens.TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = ThemeTokens.DividerColor)
                            Text("System App Privilege", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeTokens.AccentCyan)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Install as System App", color = ThemeTokens.TextPrimary, fontSize = 14.sp)
                                    Text(
                                        if (isSystemApp) "Running as a privileged System App" else "Running as standard User App",
                                        color = ThemeTokens.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.installAsSystem(
                                            context = context,
                                            onSuccess = {
                                                onDismiss()
                                                onShowRebootPrompt()
                                            },
                                            onError = { error ->
                                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    enabled = !isSystemApp,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSystemApp) Color.DarkGray else ThemeTokens.AccentIndigo
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (isSystemApp) "Installed" else "Install", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = ThemeTokens.DividerColor)

                // Credits section - centered column
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeTokens.MidnightBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "UniUpdater v1.0.1",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTokens.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Developed and maintained with ❤️ by",
                            fontSize = 10.sp,
                            color = ThemeTokens.TextSecondary
                        )
                        Text(
                            "DaDevMikey",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ThemeTokens.AccentCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Inspired by un1ca and LineageOS Updaters.",
                            fontSize = 9.sp,
                            color = ThemeTokens.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ThemeTokens.AccentIndigo, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = ThemeTokens.CardSurface,
        titleContentColor = ThemeTokens.TextPrimary,
        textContentColor = ThemeTokens.TextSecondary
    )
}

// ================= SAMSUNG ONE UI INSPIRED LAYOUT =================
@Composable
fun OneUiLayout(
    uiState: MainUiState,
    appUpdateState: AppUpdateState,
    appUpdateProgress: Float?,
    onDownloadAppUpdate: (String) -> Unit,
    downloadState: OtaDownloadService.DownloadState,
    isRootAvailable: Boolean,
    isSystemApp: Boolean,
    customJsonUrl: String,
    onCheckUpdates: () -> Unit,
    onDownload: (RomUpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (String, String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Massive Collapsible-Style Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ThemeTokens.MidnightBackground)
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.rom_updater_title),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light,
                            color = ThemeTokens.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.updater_for_rom),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = ThemeTokens.AccentCyan
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.background(ThemeTokens.CardSurface, CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Open Settings Menu", tint = ThemeTokens.TextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val updateStatusText = when (uiState) {
                    is MainUiState.UpdateChecked -> {
                        if (uiState.isUpdateAvailable) "Update is available for download." else "Your software is up to date."
                    }
                    MainUiState.Checking -> "Checking for updates..."
                    else -> "Ready to check."
                }
                Text(
                    text = updateStatusText,
                    fontSize = 14.sp,
                    color = ThemeTokens.TextSecondary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ThemeTokens.OneUiPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (appUpdateState is AppUpdateState.UpdateAvailable) {
                AppUpdateCard(
                    update = appUpdateState,
                    progress = appUpdateProgress,
                    onDownloadUpdate = onDownloadAppUpdate
                )
            }

            when (uiState) {
                MainUiState.Checking -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ThemeTokens.OneUiAccent)
                    }
                }
                is MainUiState.UpdateChecked -> {
                    UpdateInfoSection(
                        state = uiState,
                        downloadState = downloadState,
                        isRootAvailable = isRootAvailable,
                        onDownload = onDownload,
                        onCancelDownload = onCancelDownload,
                        onInstall = onInstall,
                        onCheckUpdates = onCheckUpdates
                    )
                }
                is MainUiState.Error -> {
                    ErrorBox(message = uiState.message, onRetry = onCheckUpdates)
                }
                else -> {}
            }

            if (uiState is MainUiState.UpdateChecked) {
                SystemSpecsCard(
                    state = uiState,
                    isRootAvailable = isRootAvailable,
                    isSystemApp = isSystemApp,
                    customJsonUrl = customJsonUrl
                )
            }
        }
    }
}

// ================= COMMON COMPOSABLE SUB-COMPONENTS =================

@Composable
fun UpdateInfoSection(
    state: MainUiState.UpdateChecked,
    downloadState: OtaDownloadService.DownloadState,
    isRootAvailable: Boolean,
    onDownload: (RomUpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (String, String) -> Unit,
    onCheckUpdates: () -> Unit
) {
    if (!state.isDeviceCompatible) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ThemeTokens.OneUiCornerRadius))
                .background(ThemeTokens.CardSurface),
            colors = CardDefaults.cardColors(containerColor = ThemeTokens.CardSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Info, contentDescription = "Incompatible", tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Incompatible Device",
                    color = ThemeTokens.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This ROM update package is for '${state.info.rom_device}', but your device codename is '${state.localDevice}'. Updating is locked to prevent bricking.",
                    color = ThemeTokens.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
        return
    }

    if (!state.isUpdateAvailable) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ThemeTokens.OneUiCornerRadius))
                .background(ThemeTokens.CardSurface),
            colors = CardDefaults.cardColors(containerColor = ThemeTokens.CardSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Up to date",
                    tint = ThemeTokens.OneUiAccent,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "System is up to date",
                    color = ThemeTokens.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last checked: ${getCurrentTimeString()}",
                    color = ThemeTokens.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCheckUpdates,
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.AccentIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Check for updates", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // UPDATE IS AVAILABLE!
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Banner Image Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(ThemeTokens.OneUiCornerRadius))
                .background(ThemeTokens.CardSurface)
        ) {
            AsyncImage(
                model = state.info.banner_img,
                contentDescription = "ROM Banner Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            // Overlaid Text
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = state.info.rom_name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Latest: ${state.info.rom_version}",
                    color = ThemeTokens.AccentCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Action controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ThemeTokens.OneUiCornerRadius))
                .background(ThemeTokens.CardSurface),
            colors = CardDefaults.cardColors(containerColor = ThemeTokens.CardSurface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Version details list
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Size", color = ThemeTokens.TextSecondary, fontSize = 12.sp)
                        Text(state.info.file_size, color = ThemeTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Release Date", color = ThemeTokens.TextSecondary, fontSize = 12.sp)
                        Text(state.info.rom_date ?: "Unknown", color = ThemeTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = ThemeTokens.DividerColor)
                Spacer(modifier = Modifier.height(16.dp))

                // Action download / install state machine
                when (downloadState) {
                    OtaDownloadService.DownloadState.Idle -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onDownload(state.info) },
                                colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.OneUiAccent),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Download Update (${state.info.file_size})", fontWeight = FontWeight.Bold)
                            }
                            
                            val localContext = LocalContext.current
                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "ROM Update: ${state.info.rom_name}")
                                        putExtra(Intent.EXTRA_TEXT, "New OTA Update for ${state.info.rom_name} (${state.info.rom_version}) is available!\nSize: ${state.info.file_size}\nRelease Date: ${state.info.rom_date}\n\nChangelog:\n${state.info.changelog_md}\n\nDownload Link: ${state.info.download_url}")
                                    }
                                    localContext.startActivity(Intent.createChooser(shareIntent, "Share Update Details"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Share", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    OtaDownloadService.DownloadState.Connecting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ThemeTokens.AccentCyan)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Connecting to server...", color = ThemeTokens.TextPrimary)
                        }
                    }
                    is OtaDownloadService.DownloadState.Downloading -> {
                        val percentage = (downloadState.progress * 100).toInt()
                        val speedStr = String.format("%.1f MB/s", downloadState.speedMbSeconds)
                        val etaStr = if (downloadState.etaSeconds > 0) {
                            val mins = downloadState.etaSeconds / 60
                            val secs = downloadState.etaSeconds % 60
                            "ETA: ${mins}m ${secs}s"
                        } else "Calculating ETA..."

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Downloading ($percentage%)", color = ThemeTokens.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(speedStr, color = ThemeTokens.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(etaStr, color = ThemeTokens.TextSecondary, fontSize = 12.sp)
                                }
                            }
                            LinearProgressIndicator(
                                progress = { downloadState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = ThemeTokens.OneUiAccent,
                                trackColor = ThemeTokens.DividerColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onCancelDownload,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel Download")
                            }
                        }
                    }
                    OtaDownloadService.DownloadState.Verifying -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ThemeTokens.AccentCyan)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Verifying package checksum...", color = ThemeTokens.TextPrimary)
                        }
                    }
                    is OtaDownloadService.DownloadState.Success -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onInstall(downloadState.fileAbsolutePath, state.info.rom_name) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (isRootAvailable) "Auto-Install Update (Root)" else "Install Update Manually",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            
                            val localContext = LocalContext.current
                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "ROM Update: ${state.info.rom_name}")
                                        putExtra(Intent.EXTRA_TEXT, "OTA Update for ${state.info.rom_name} (${state.info.rom_version}) is ready to flash!\nSize: ${state.info.file_size}\n\nChangelog:\n${state.info.changelog_md}\n\nDownload Link: ${state.info.download_url}")
                                    }
                                    localContext.startActivity(Intent.createChooser(shareIntent, "Share Update Details"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Share", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    is OtaDownloadService.DownloadState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Download failed: ${downloadState.errorMessage}", color = Color.Red, fontSize = 13.sp)
                            Button(
                                onClick = { onDownload(state.info) },
                                colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.OneUiAccent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry Download")
                            }
                        }
                    }
                }
            }
        }

        // Changelog Md Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ThemeTokens.OneUiCornerRadius))
                .background(ThemeTokens.CardSurface),
            colors = CardDefaults.cardColors(containerColor = ThemeTokens.CardSurface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "What's New",
                    color = ThemeTokens.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                HorizontalDivider(color = ThemeTokens.DividerColor)
                Spacer(modifier = Modifier.height(12.dp))
                MarkdownText(markdown = state.info.changelog_md)
            }
        }
    }
}

@Composable
fun SystemSpecsCard(
    state: MainUiState.UpdateChecked,
    isRootAvailable: Boolean,
    isSystemApp: Boolean,
    customJsonUrl: String
) {
    val context = LocalContext.current
    val prefManager = remember { PrefManager(context) }
    var devClicks by remember { mutableIntStateOf(0) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ThemeTokens.OneUiCornerRadius))
            .background(ThemeTokens.CardSurface),
        colors = CardDefaults.cardColors(containerColor = ThemeTokens.CardSurface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "System Information",
                color = ThemeTokens.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HorizontalDivider(color = ThemeTokens.DividerColor)
            Spacer(modifier = Modifier.height(12.dp))

            // Specs rows
            SpecRow("Device Model", state.localDevice)
            val isMockSettingsEnabled = LocalContext.current.resources.getBoolean(R.bool.enable_mock_settings)
            SpecRow(
                label = "ROM Build Version",
                value = state.localVersion,
                modifier = if (isMockSettingsEnabled) {
                    Modifier.clickable {
                        devClicks++
                        if (devClicks >= 7) {
                            devClicks = 0
                            context.startActivity(Intent(context, SimulationActivity::class.java))
                            Toast.makeText(context, "Developer simulation settings opened!", Toast.LENGTH_LONG).show()
                        } else if (devClicks > 2) {
                            Toast.makeText(
                                context,
                                "You are now ${7 - devClicks} steps away from ROM developer settings.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else Modifier
            )
            SpecRow("Build Timestamp", state.localBuildDate.toString())
            SpecRow("Root Access Available", if (isRootAvailable) "Yes (Granted)" else "No / Disallowed")
            SpecRow("System App Status", if (isSystemApp) "Yes (Privileged)" else "No (User App)")
            SpecRow("Active Update Server", customJsonUrl.substringAfter("https://").substringBefore("/"))
            SpecRow("Security Patch Level", android.os.Build.VERSION.SECURITY_PATCH)
            if (prefManager.isSimulationEnabled) {
                SpecRow("Simulation Mode Active", "Yes")
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ThemeTokens.TextSecondary, fontSize = 13.sp)
        Text(value, color = ThemeTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1A1A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Build, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Update Check Failed", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, color = Color(0xFFFFD1D1), fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.OneUiAccent)
            ) {
                Text("Retry Connection")
            }
        }
    }
}

@Composable
fun AppUpdateCard(
    update: AppUpdateState.UpdateAvailable,
    progress: Float?,
    onDownloadUpdate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ThemeTokens.OneUiCornerRadius))
            .background(ThemeTokens.CardSurface),
        colors = CardDefaults.cardColors(containerColor = ThemeTokens.CardSurface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "App Update Available",
                    tint = ThemeTokens.AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Updater Update Available",
                    color = ThemeTokens.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "New Version: ${update.version}",
                color = ThemeTokens.AccentCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = ThemeTokens.DividerColor)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Changelog:",
                color = ThemeTokens.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            MarkdownText(markdown = update.changelog)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (progress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val percentage = (progress * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Downloading update...", color = ThemeTokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("$percentage%", color = ThemeTokens.AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ThemeTokens.OneUiAccent,
                        trackColor = ThemeTokens.DividerColor
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (update.apkUrl != null) {
                        Button(
                            onClick = { onDownloadUpdate(update.apkUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.AccentIndigo),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Download & Install", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(update.downloadUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (update.apkUrl != null) Color.DarkGray else ThemeTokens.AccentIndigo),
                        modifier = if (update.apkUrl != null) Modifier.wrapContentSize() else Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (update.apkUrl != null) "View Release" else "Download & Update", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun getCurrentTimeString(): String {
    val sdf = SimpleDateFormat("MMM d, yyyy - hh:mm a", Locale.getDefault())
    return sdf.format(Date())
}
