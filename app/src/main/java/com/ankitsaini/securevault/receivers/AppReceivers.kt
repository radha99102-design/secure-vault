package com.ankitsaini.securevault.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ankitsaini.securevault.data.EventType
import com.ankitsaini.securevault.data.SecurityEvent
import com.ankitsaini.securevault.data.repository.SecurityRepository
import com.ankitsaini.securevault.services.AppMonitorService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var securityRepository: SecurityRepository
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            receiverScope.launch {
                try {
                    val autoStartSetting = securityRepository.getSetting("auto_start_on_boot")
                    val autoStart = autoStartSetting?.settingsValue?.toBoolean() ?: true
                    if (autoStart) {
                        AppMonitorService.startService(context)
                    }
                } catch (e: Exception) { }
            }
        }
    }
}

@AndroidEntryPoint
class PackageChangeReceiver : BroadcastReceiver() {
    @Inject lateinit var securityRepository: SecurityRepository
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        
        if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
            receiverScope.launch {
                try {
                    securityRepository.removeProtectedApp(packageName)
                    securityRepository.logEvent(SecurityEvent(packageName = packageName, eventType = EventType.APP_PROTECTION_DISABLED, eventDetails = "Protected app was uninstalled", wasSuccessful = true))
                } catch (e: Exception) { }
            }
        }
    }
}

@AndroidEntryPoint
class ScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            // Logic to relock apps will be connected in Phase 4
        }
    }
}
