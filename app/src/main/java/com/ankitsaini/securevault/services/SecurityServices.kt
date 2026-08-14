package com.ankitsaini.securevault.services

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.ankitsaini.securevault.data.EventType
import com.ankitsaini.securevault.data.SecurityEvent
import com.ankitsaini.securevault.data.repository.SecurityRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceManager @Inject constructor(@ApplicationContext private val context: Context) {
    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "${context.packageName}/.services.AppLockAccessibilityService"
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabledServices.split(":").any { it.equals(expectedComponentName, ignoreCase = true) }
    }
    fun openAccessibilitySettings() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
    fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return enabledListeners.contains(context.packageName)
    }
    fun openNotificationListenerSettings() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)
    fun openOverlaySettings() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    fun requestIgnoreBatteryOptimizations() {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { 
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) 
        })
    }
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
    fun openUsageAccessSettings() {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

@AndroidEntryPoint
class AppMonitorService : Service() {
    companion object {
        var isRunning = false
            private set
        fun startService(context: Context) {
            val intent = Intent(context, AppMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stopService(context: Context) {
            context.stopService(Intent(context, AppMonitorService::class.java))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        acquireWakeLock()
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("app_monitor_service", "App Protection Service", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        
        // Setup empty pending intent to compile cleanly
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "app_monitor_service")
            .setContentTitle("Secure Vault")
            .setContentText("App protection is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1001, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SecureVault::AppMonitorWakeLock").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        wakeLock?.let { if (it.isHeld) it.release() }
    }
}

@AndroidEntryPoint
class AppLockAccessibilityService : AccessibilityService() {
    @Inject lateinit var securityRepository: SecurityRepository
    private var currentForegroundPackage: String? = null

    override fun onServiceConnected() {}
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        
        // Ignore system UI
        if (packageName == "com.android.systemui" || packageName == "com.android.launcher") return
        
        if (currentForegroundPackage != packageName) {
            currentForegroundPackage = packageName
            // Logic to trigger LockScreenActivity will be finalized in Phase 4
        }
    }
    
    override fun onInterrupt() {}
}

@AndroidEntryPoint
class NotificationFilterService : NotificationListenerService() {
    @Inject lateinit var securityRepository: SecurityRepository

    override fun onListenerConnected() {}
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName
        // Logic to mask notification will be finalized in Phase 4
    }
}
