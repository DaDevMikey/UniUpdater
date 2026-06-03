package com.example.uniupdater.ui.simulation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uniupdater.data.PrefManager
import com.example.uniupdater.theme.ThemeTokens
import com.example.uniupdater.theme.UniUpdaterTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class SimulationActivity : ComponentActivity() {

    private lateinit var prefManager: PrefManager
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = PrefManager(this)

        setContent {
            UniUpdaterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ThemeTokens.MidnightBackground
                ) {
                    SimulationScreen(
                        prefManager = prefManager,
                        onBack = { finish() },
                        onTestUrl = { url -> testOtaUrl(url) }
                    )
                }
            }
        }
    }

    private fun testOtaUrl(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val code = response.code
                val body = response.body?.string() ?: ""
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@SimulationActivity,
                            "Success! HTTP $code. Got JSON successfully.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@SimulationActivity,
                            "Failed! Server returned HTTP $code.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SimulationActivity,
                        "Error connecting to server: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationScreen(
    prefManager: PrefManager,
    onBack: () -> Unit,
    onTestUrl: (String) -> Unit
) {
    var isEnabled by remember { mutableStateOf(prefManager.isSimulationEnabled) }
    var deviceCodename by remember { mutableStateOf(prefManager.simDevice) }
    var buildVersion by remember { mutableStateOf(prefManager.simVersion) }
    var buildLatest by remember { mutableStateOf(prefManager.simLatest.toString()) }
    var jsonUrl by remember { mutableStateOf(prefManager.customJsonUrl) }

    var forceUpdateAvailable by remember { mutableStateOf(prefManager.forceUpdateAvailable) }
    var useLocalJsonMock by remember { mutableStateOf(prefManager.useLocalJsonMock) }
    var localJsonContent by remember { mutableStateOf(prefManager.localJsonContent) }

    var targetRomName by remember { mutableStateOf(prefManager.targetRomName) }
    var targetRomVersion by remember { mutableStateOf(prefManager.targetRomVersion) }
    var targetRomLatest by remember { mutableStateOf(prefManager.targetRomLatest.toString()) }
    var targetChangelog by remember { mutableStateOf(prefManager.targetChangelog) }
    var targetDownloadUrl by remember { mutableStateOf(prefManager.targetDownloadUrl) }
    var targetFileSize by remember { mutableStateOf(prefManager.targetFileSize) }
    var targetRomDate by remember { mutableStateOf(prefManager.targetRomDate) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ROM Dev Simulator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ThemeTokens.MidnightBackground,
                    titleContentColor = ThemeTokens.TextPrimary,
                    navigationIconContentColor = ThemeTokens.TextPrimary
                )
            )
        },
        containerColor = ThemeTokens.MidnightBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Warning Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2E1A1A))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Warning",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "These settings spoof device values to test OTA logic locally. Do not use in production.",
                    color = Color(0xFFFFD1D1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            // Main Toggle Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ThemeTokens.CardSurface)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Enable Simulation Mode",
                        color = ThemeTokens.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Override system build properties with mock values below.",
                        color = ThemeTokens.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        prefManager.isSimulationEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ThemeTokens.AccentCyan,
                        checkedTrackColor = ThemeTokens.AccentIndigo
                    )
                )
            }

            if (isEnabled) {
                // Switches for local offline mocks
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ThemeTokens.CardSurface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Force Update Available", color = ThemeTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Bypass build check rules and mock update available directly.", color = ThemeTokens.TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = forceUpdateAvailable,
                            onCheckedChange = {
                                forceUpdateAvailable = it
                                prefManager.forceUpdateAvailable = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ThemeTokens.AccentCyan,
                                checkedTrackColor = ThemeTokens.AccentIndigo
                            )
                        )
                    }

                    HorizontalDivider(color = ThemeTokens.DividerColor)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Use Local Mock JSON", color = ThemeTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Parse local raw JSON editor text instead of individual fields.", color = ThemeTokens.TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = useLocalJsonMock,
                            onCheckedChange = {
                                useLocalJsonMock = it
                                prefManager.useLocalJsonMock = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ThemeTokens.AccentCyan,
                                checkedTrackColor = ThemeTokens.AccentIndigo
                            )
                        )
                    }
                }

                Text(
                    "Simulated Device Info",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTokens.AccentCyan,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ThemeTokens.CardSurface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = deviceCodename,
                        onValueChange = {
                            deviceCodename = it
                            prefManager.simDevice = it
                        },
                        label = { Text("Simulated Codename (e.g. socrates)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeTokens.TextPrimary,
                            unfocusedTextColor = ThemeTokens.TextPrimary,
                            focusedBorderColor = ThemeTokens.AccentIndigo,
                            unfocusedBorderColor = ThemeTokens.DividerColor
                        )
                    )

                    OutlinedTextField(
                        value = buildVersion,
                        onValueChange = {
                            buildVersion = it
                            prefManager.simVersion = it
                        },
                        label = { Text("Simulated Rom Version (e.g. 1.0-Beta)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeTokens.TextPrimary,
                            unfocusedTextColor = ThemeTokens.TextPrimary,
                            focusedBorderColor = ThemeTokens.AccentIndigo,
                            unfocusedBorderColor = ThemeTokens.DividerColor
                        )
                    )

                    OutlinedTextField(
                        value = buildLatest,
                        onValueChange = {
                            buildLatest = it
                            val value = it.toLongOrNull() ?: 0L
                            prefManager.simLatest = value
                        },
                        label = { Text("Simulated Build Timestamp (e.g. 20260501)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeTokens.TextPrimary,
                            unfocusedTextColor = ThemeTokens.TextPrimary,
                            focusedBorderColor = ThemeTokens.AccentIndigo,
                            unfocusedBorderColor = ThemeTokens.DividerColor
                        )
                    )
                }

                if (useLocalJsonMock) {
                    Text(
                        "Local Raw JSON Configuration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.AccentCyan,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ThemeTokens.CardSurface)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = localJsonContent,
                            onValueChange = {
                                localJsonContent = it
                                prefManager.localJsonContent = it
                            },
                            label = { Text("Raw Mock JSON Editor") },
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeTokens.TextPrimary,
                                unfocusedTextColor = ThemeTokens.TextPrimary,
                                focusedBorderColor = ThemeTokens.AccentIndigo,
                                unfocusedBorderColor = ThemeTokens.DividerColor
                            )
                        )
                    }
                } else {
                    Text(
                        "Simulated Target Update Specs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.AccentCyan,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ThemeTokens.CardSurface)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = targetRomName,
                            onValueChange = {
                                targetRomName = it
                                prefManager.targetRomName = it
                            },
                            label = { Text("Target ROM Name (e.g. UniOS Simulated)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeTokens.TextPrimary,
                                unfocusedTextColor = ThemeTokens.TextPrimary,
                                focusedBorderColor = ThemeTokens.AccentIndigo,
                                unfocusedBorderColor = ThemeTokens.DividerColor
                            )
                        )

                        OutlinedTextField(
                            value = targetRomVersion,
                            onValueChange = {
                                targetRomVersion = it
                                prefManager.targetRomVersion = it
                            },
                            label = { Text("Target Version (e.g. v2.0-Beta)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeTokens.TextPrimary,
                                unfocusedTextColor = ThemeTokens.TextPrimary,
                                focusedBorderColor = ThemeTokens.AccentIndigo,
                                unfocusedBorderColor = ThemeTokens.DividerColor
                            )
                        )

                        OutlinedTextField(
                            value = targetRomLatest,
                            onValueChange = {
                                targetRomLatest = it
                                val value = it.toLongOrNull() ?: 0L
                                prefManager.targetRomLatest = value
                            },
                            label = { Text("Target Build Timestamp (e.g. 20260603)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeTokens.TextPrimary,
                                unfocusedTextColor = ThemeTokens.TextPrimary,
                                focusedBorderColor = ThemeTokens.AccentIndigo,
                                unfocusedBorderColor = ThemeTokens.DividerColor
                            )
                        )

                        OutlinedTextField(
                            value = targetFileSize,
                            onValueChange = {
                                targetFileSize = it
                                prefManager.targetFileSize = it
                            },
                            label = { Text("Target File Size (e.g. 15 MB)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeTokens.TextPrimary,
                                unfocusedTextColor = ThemeTokens.TextPrimary,
                                focusedBorderColor = ThemeTokens.AccentIndigo,
                                unfocusedBorderColor = ThemeTokens.DividerColor
                            )
                        )

                        OutlinedTextField(
                            value = targetRomDate,
                            onValueChange = {
                                targetRomDate = it
                                prefManager.targetRomDate = it
                            },
                            label = { Text("Target Release Date (e.g. June 3, 2026)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeTokens.TextPrimary,
                                unfocusedTextColor = ThemeTokens.TextPrimary,
                                focusedBorderColor = ThemeTokens.AccentIndigo,
                                unfocusedBorderColor = ThemeTokens.DividerColor
                            )
                        )

                        OutlinedTextField(
                            value = targetDownloadUrl,
                            onValueChange = {
                                targetDownloadUrl = it
                                prefManager.targetDownloadUrl = it
                            },
                            label = { Text("Target Download URL") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeTokens.TextPrimary,
                                unfocusedTextColor = ThemeTokens.TextPrimary,
                                focusedBorderColor = ThemeTokens.AccentIndigo,
                                unfocusedBorderColor = ThemeTokens.DividerColor
                            )
                        )

                        OutlinedTextField(
                            value = targetChangelog,
                            onValueChange = {
                                targetChangelog = it
                                prefManager.targetChangelog = it
                            },
                            label = { Text("Target Changelog (Markdown)") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeTokens.TextPrimary,
                                unfocusedTextColor = ThemeTokens.TextPrimary,
                                focusedBorderColor = ThemeTokens.AccentIndigo,
                                unfocusedBorderColor = ThemeTokens.DividerColor
                            )
                        )
                    }
                }

                Text(
                    "Custom OTA JSON URL",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTokens.AccentCyan,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ThemeTokens.CardSurface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = jsonUrl,
                        onValueChange = {
                            jsonUrl = it
                            prefManager.customJsonUrl = it
                        },
                        label = { Text("Custom OTA JSON URL") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeTokens.TextPrimary,
                            unfocusedTextColor = ThemeTokens.TextPrimary,
                            focusedBorderColor = ThemeTokens.AccentIndigo,
                            unfocusedBorderColor = ThemeTokens.DividerColor
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                jsonUrl = prefManager.defaultJsonUrl
                                prefManager.customJsonUrl = prefManager.defaultJsonUrl
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset URL")
                        }

                        Button(
                            onClick = { onTestUrl(jsonUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.AccentIndigo),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test URL")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
