package com.ankitsaini.securevault.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class LockType {
    PIN, PATTERN, BIOMETRIC, NONE
}

enum class EventType {
    APP_LAUNCH_ATTEMPT,
    LOCK_SCREEN_SHOWN,
    UNLOCK_SUCCESSFUL,
    UNLOCK_FAILED,
    INTRUDER_PHOTO_CAPTURED,
    NOTIFICATION_MASKED
}

data class ProtectedApp(
    val packageName: String,
    val appName: String,
    val category: String,
    val isProtected: Boolean,
    val lockType: LockType,
    val useBiometric: Boolean = true,
    val notificationMasking: Boolean = true,
    val isSystemApp: Boolean = false,
    val iconBgColor: Color,
    val icon: ImageVector
)

data class SecurityEvent(
    val eventId: String,
    val appName: String,
    val packageName: String,
    val eventType: EventType,
    val wasSuccessful: Boolean,
    val eventDetails: String,
    val timeAgo: String
)
