package com.ankitsaini.securevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ankitsaini.securevault.data.*
import com.ankitsaini.securevault.ui.viewmodel.*

// ==========================================
// 1. DASHBOARD SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAppList: () -> Unit,
    onNavigateToSecurityLog: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val dashboardData by viewModel.dashboardData.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Secure Vault", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = if (isServiceRunning) Color(0xFF10B981) else Color(0xFFF43F5E))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(if (isServiceRunning) "Protection Active" else "Protection Inactive", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Vault Engine Status", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = isServiceRunning, onCheckedChange = { viewModel.toggleService() })
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = onNavigateToAppList) { Icon(Icons.Default.Apps, ""); Spacer(Modifier.width(4.dp)); Text("Apps") }
                    Button(onClick = onNavigateToSecurityLog) { Icon(Icons.Default.History, ""); Spacer(Modifier.width(4.dp)); Text("Logs") }
                    Button(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, ""); Spacer(Modifier.width(4.dp)); Text("Settings") }
                }
            }
            item { Text("Protected Apps (${dashboardData.totalProtectedApps})", fontWeight = FontWeight.Bold) }
            items(dashboardData.protectedApps) { app ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(app.appName, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. APP LIST SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(onBackClick: () -> Unit, viewModel: AppListViewModel = hiltViewModel()) {
    val apps by viewModel.installedApps.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("App Protection") }, navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "") } }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(apps) { app ->
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, fontWeight = FontWeight.Medium)
                        if (app.isSystemApp) Text("System App", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = app.isProtected, onCheckedChange = { viewModel.toggleProtection(app.packageName, it) })
                }
                Divider()
            }
        }
    }
}

// ==========================================
// 3. SECURITY LOG SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityLogScreen(onBackClick: () -> Unit, viewModel: SecurityLogViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Logs") },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "") } },
                actions = { IconButton(onClick = { viewModel.clearLog() }) { Icon(Icons.Default.Delete, "") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(events) { event ->
                val isError = event.eventType.name.contains("FAILED")
                ListItem(
                    headlineContent = { Text(event.packageName) },
                    supportingContent = { Text(event.eventDetails ?: event.eventType.name) },
                    leadingContent = { Icon(if (isError) Icons.Default.Warning else Icons.Default.CheckCircle, "", tint = if (isError) Color.Red else Color.Green) }
                )
                Divider()
            }
        }
    }
}

// ==========================================
// 4. SETTINGS SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "") } }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { Text("Security", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp)) }
            item { ListItem(headlineContent = { Text("Set Master PIN") }, modifier = Modifier.clickable { showPinDialog = true }) }
            item { ListItem(headlineContent = { Text("Biometric Unlock") }, trailingContent = { Switch(checked = settings.biometricEnabled, onCheckedChange = { viewModel.toggleBiometric() }) }) }
            
            item { Text("Permissions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp)) }
            item { ListItem(headlineContent = { Text("Accessibility Service") }, supportingContent = { Text("Required for App Lock") }, trailingContent = { Icon(if (permissions.accessibilityGranted) Icons.Default.CheckCircle else Icons.Default.Warning, "", tint = if (permissions.accessibilityGranted) Color.Green else Color.Red) }, modifier = Modifier.clickable { viewModel.openAccessibilitySettings() }) }
            item { ListItem(headlineContent = { Text("Draw Over Apps") }, supportingContent = { Text("Required to show lock screen") }, trailingContent = { Icon(if (permissions.overlayGranted) Icons.Default.CheckCircle else Icons.Default.Warning, "", tint = if (permissions.overlayGranted) Color.Green else Color.Red) }, modifier = Modifier.clickable { viewModel.openOverlaySettings() }) }
        }

        if (showPinDialog) {
            var pin by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = { Text("Set Master PIN") },
                text = { OutlinedTextField(value = pin, onValueChange = { if(it.length <= 6) pin = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation()) },
                confirmButton = { Button(onClick = { viewModel.setMasterPin(pin); showPinDialog = false }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

// ==========================================
// 5. LOCK SCREEN (PIN PAD)
// ==========================================
@Composable
fun LockScreen(packageName: String, appName: String, lockType: LockType, onAuthenticated: () -> Unit, onCancel: () -> Unit, viewModel: AuthViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(packageName) { viewModel.setupAuthentication(packageName, lockType) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Unlock $appName", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        // PIN Dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(if (index < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.errorMessage != null) Text(uiState.errorMessage!!, color = Color.Red, modifier = Modifier.padding(8.dp))
        
        // Numpad
        val pad = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("","0","DEL"))
        pad.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                row.forEach { btn ->
                    if (btn.isEmpty()) Spacer(Modifier.size(72.dp))
                    else Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                        if (btn == "DEL" && pin.isNotEmpty()) pin = pin.dropLast(1)
                        else if (btn != "DEL" && pin.length < 4) {
                            pin += btn
                            if (pin.length == 4) {
                                viewModel.authenticateWithPin(pin)
                                pin = ""
                                onAuthenticated()
                            }
                        }
                    }, contentAlignment = Alignment.Center) { Text(btn, style = MaterialTheme.typography.headlineMedium) }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text("Cancel & Go Back") }
    }
}
