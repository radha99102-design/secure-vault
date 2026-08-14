package com.ankitsaini.securevault.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsaini.securevault.data.model.*
import com.ankitsaini.securevault.ui.theme.VaultColors

@Composable
fun VaultDashboardScreen(
    onNavigateToApps: () -> Unit = {},
    onNavigateToIntruders: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    onSelectApp: (String) -> Unit = {},
    onLaunchSimulatedApp: (String) -> Unit = {}
) {
    // State management
    var isVaultServiceRunning by remember { mutableStateOf(true) }
    var appsList by remember {
        mutableStateOf(
            listOf(
                ProtectedApp(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    category = "Communication",
                    isProtected = true,
                    lockType = LockType.PIN,
                    iconBgColor = Color(0xFF059669), 
                    icon = Icons.Rounded.Chat
                ),
                ProtectedApp(
                    packageName = "com.google.android.apps.photos",
                    appName = "Google Photos",
                    category = "Media",
                    isProtected = true,
                    lockType = LockType.PATTERN,
                    iconBgColor = Color(0xFFD97706), 
                    icon = Icons.Rounded.Image
                ),
                ProtectedApp(
                    packageName = "com.chase.sig.android",
                    appName = "Chase Mobile Banking",
                    category = "Finance",
                    isProtected = true,
                    lockType = LockType.PIN,
                    iconBgColor = Color(0xFF1D4ED8), 
                    icon = Icons.Rounded.AccountBalance
                ),
                ProtectedApp(
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    category = "Social",
                    isProtected = true,
                    lockType = LockType.BIOMETRIC,
                    iconBgColor = Color(0xFFDB2777), 
                    icon = Icons.Rounded.CameraAlt
                ),
                ProtectedApp(
                    packageName = "com.google.android.gm",
                    appName = "Gmail",
                    category = "Communication",
                    isProtected = true,
                    lockType = LockType.PIN,
                    isSystemApp = true,
                    iconBgColor = Color(0xFFDC2626), 
                    icon = Icons.Rounded.Email
                )
            )
        )
    }

    val sampleEvents = remember {
        listOf(
            SecurityEvent(
                eventId = "1",
                appName = "Chase Mobile Banking",
                packageName = "com.chase.sig.android",
                eventType = EventType.UNLOCK_SUCCESSFUL,
                wasSuccessful = true,
                eventDetails = "Biometric fingerprint verified instantly",
                timeAgo = "3m ago"
            ),
            SecurityEvent(
                eventId = "2",
                appName = "WhatsApp",
                packageName = "com.whatsapp",
                eventType = EventType.INTRUDER_PHOTO_CAPTURED,
                wasSuccessful = false,
                eventDetails = "Intruder front camera capture triggered on 3 failed PIN attempts",
                timeAgo = "12m ago"
            )
        )
    }

    val protectedCount = appsList.count { it.isProtected }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultColors.Background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        item {
            MasterProtectionHeroCard(
                isServiceRunning = isVaultServiceRunning,
                protectedAppsCount = protectedCount,
                onToggleService = { isVaultServiceRunning = !isVaultServiceRunning }
            )
        }

        item {
            MetricsGrid(
                protectedCount = protectedCount,
                totalAppsCount = appsList.size,
                intruderSnapsCount = 3,
                failedAttemptsCount = 2,
                auditEventsCount = 28,
                onNavigateToApps = onNavigateToApps,
                onNavigateToIntruders = onNavigateToIntruders,
                onNavigateToLogs = onNavigateToLogs
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Protected Applications", color = VaultColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Apps guarded with instant lock triggers", color = VaultColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }

        items(appsList.size) { index ->
            val app = appsList[index]
            ProtectedAppCard(
                app = app,
                onSelect = { onSelectApp(app.packageName) },
                onLaunchSimulator = { onLaunchSimulatedApp(app.packageName) },
                onToggleProtection = {
                    appsList = appsList.toMutableList().also { list ->
                        list[index] = app.copy(isProtected = !app.isProtected)
                    }
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Recent Activity", color = VaultColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        items(sampleEvents.size) { index ->
            SecurityEventCard(event = sampleEvents[index])
        }
    }
}

@Composable
fun MasterProtectionHeroCard(isServiceRunning: Boolean, protectedAppsCount: Int, onToggleService: () -> Unit) {
    val gradientBrush = Brush.horizontalGradient(
        if (isServiceRunning) listOf(VaultColors.Emerald950, VaultColors.SurfaceDark) 
        else listOf(VaultColors.Rose950, VaultColors.SurfaceDark)
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .background(gradientBrush, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = if (isServiceRunning) "Device Security is Enforced" else "Protection is Inactive",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
            )
            Button(
                onClick = onToggleService,
                colors = ButtonDefaults.buttonColors(containerColor = if (isServiceRunning) VaultColors.Emerald600 else VaultColors.Rose600)
            ) {
                Text(text = if (isServiceRunning) "Vault is ON" else "Turn Shield ON", color = Color.White)
            }
        }
    }
}

@Composable
fun MetricsGrid(protectedCount: Int, totalAppsCount: Int, intruderSnapsCount: Int, failedAttemptsCount: Int, auditEventsCount: Int, onNavigateToApps: () -> Unit, onNavigateToIntruders: () -> Unit, onNavigateToLogs: () -> Unit) {
    // Basic representation logic to fit space
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("PROTECTED APPS", "$protectedCount", Icons.Rounded.Lock, VaultColors.Emerald400, Modifier.weight(1f))
        MetricCard("INTRUDER SNAPS", "$intruderSnapsCount", Icons.Rounded.CameraAlt, VaultColors.Amber400, Modifier.weight(1f))
    }
}

@Composable
fun MetricCard(title: String, count: String, icon: ImageVector, accentColor: Color, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceDark), modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = VaultColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(text = count, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun ProtectedAppCard(app: ProtectedApp, onSelect: () -> Unit, onLaunchSimulator: () -> Unit, onToggleProtection: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceDark), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.appName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(text = "${app.lockType.name} Lock", color = VaultColors.TextSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = onToggleProtection) {
                Icon(imageVector = if (app.isProtected) Icons.Rounded.Lock else Icons.Rounded.LockOpen, contentDescription = null, tint = if (app.isProtected) VaultColors.Emerald400 else VaultColors.TextMuted)
            }
        }
    }
}

@Composable
fun SecurityEventCard(event: SecurityEvent) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceDark), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = event.appName, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(text = event.eventDetails, color = VaultColors.TextSecondary, fontSize = 12.sp)
        }
    }
}
