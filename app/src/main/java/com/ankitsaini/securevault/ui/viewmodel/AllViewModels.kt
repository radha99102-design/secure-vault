package com.ankitsaini.securevault.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ankitsaini.securevault.auth.AuthenticationManager
import com.ankitsaini.securevault.auth.PinHasher
import com.ankitsaini.securevault.auth.SessionManager
import com.ankitsaini.securevault.camera.CameraManager
import com.ankitsaini.securevault.data.*
import com.ankitsaini.securevault.data.repository.*
import com.ankitsaini.securevault.services.AppMonitorService
import com.ankitsaini.securevault.services.ServiceManager
import com.ankitsaini.securevault.utils.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(private val securityRepository: SecurityRepository) : ViewModel() {
    private val _dashboardData = MutableStateFlow(DashboardData(emptyList(), emptyList(), emptyList(), 0, 0, 0))
    val dashboardData: StateFlow<DashboardData> = _dashboardData.asStateFlow()
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    init {
        viewModelScope.launch { securityRepository.getDashboardData().collect { _dashboardData.value = it } }
        viewModelScope.launch { _isServiceRunning.value = AppMonitorService.isRunning }
    }
    fun toggleService() { _isServiceRunning.value = !_isServiceRunning.value }
}

@HiltViewModel
class AppListViewModel @Inject constructor(private val appInfoRepository: AppInfoRepository, private val securityRepository: SecurityRepository) : ViewModel() {
    private val _installedApps = MutableStateFlow<List<AppInfoRepository.InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfoRepository.InstalledAppInfo>> = _installedApps.asStateFlow()
    val searchQuery = MutableStateFlow("")

    init { loadApps() }
    private fun loadApps() { viewModelScope.launch { _installedApps.value = appInfoRepository.getInstalledApps() } }
    fun updateSearchQuery(query: String) { searchQuery.value = query }
    fun toggleProtection(packageName: String, isProtected: Boolean) {
        viewModelScope.launch {
            if (isProtected) {
                val appInfo = appInfoRepository.getAppInfo(packageName)
                if (appInfo != null) securityRepository.addProtectedApp(ProtectedApp(packageName, appInfo.appName))
            } else securityRepository.removeProtectedApp(packageName)
            loadApps()
        }
    }
}

@HiltViewModel
class SecurityLogViewModel @Inject constructor(private val securityRepository: SecurityRepository) : ViewModel() {
    private val _events = MutableStateFlow<List<SecurityEvent>>(emptyList())
    val events: StateFlow<List<SecurityEvent>> = _events.asStateFlow()
    init { viewModelScope.launch { securityRepository.getSecurityEvents().collect { _events.value = it } } }
    fun clearLog() { viewModelScope.launch { securityRepository.logEvent(SecurityEvent(packageName = "system", eventType = EventType.APP_PROTECTION_DISABLED, eventDetails = "Log cleared by user", wasSuccessful = true)) } }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val serviceManager: ServiceManager,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    data class AppSettings(val biometricEnabled: Boolean = false, val autoStartOnBoot: Boolean = true, val stealthMode: Boolean = false)
    val settings = MutableStateFlow(AppSettings())
    val permissions = MutableStateFlow(PermissionState())

    data class PermissionState(val accessibilityGranted: Boolean = false, val overlayGranted: Boolean = false)

    init { loadSettings(); checkPermissions() }
    private fun loadSettings() { settings.value = AppSettings(prefs.getBoolean(Constants.PREF_BIOMETRIC_ENABLED), prefs.getBoolean(Constants.PREF_AUTO_START), prefs.getBoolean(Constants.PREF_STEALTH_MODE)) }
    private fun checkPermissions() { permissions.value = PermissionState(serviceManager.isAccessibilityServiceEnabled(), serviceManager.canDrawOverlays()) }

    fun toggleBiometric() { val v = !settings.value.biometricEnabled; prefs.putBoolean(Constants.PREF_BIOMETRIC_ENABLED, v); settings.value = settings.value.copy(biometricEnabled = v) }
    fun toggleAutoStart() { val v = !settings.value.autoStartOnBoot; prefs.putBoolean(Constants.PREF_AUTO_START, v); settings.value = settings.value.copy(autoStartOnBoot = v) }
    fun toggleStealthMode() { val v = !settings.value.stealthMode; prefs.putBoolean(Constants.PREF_STEALTH_MODE, v); settings.value = settings.value.copy(stealthMode = v) }
    fun setMasterPin(pin: String) { val hash = PinHasher.hashPin(pin); sessionManager.setMasterPinHash(hash); prefs.putString(Constants.PREF_MASTER_PIN, hash) }
    
    fun openAccessibilitySettings() = serviceManager.openAccessibilitySettings()
    fun openOverlaySettings() = serviceManager.openOverlaySettings()
}

@HiltViewModel
class AuthViewModel @Inject constructor(private val authManager: AuthenticationManager, private val sessionManager: SessionManager, private val securityRepository: SecurityRepository) : ViewModel() {
    val uiState = MutableStateFlow(AuthUiState())
    private var currentPkg = ""
    
    data class AuthUiState(val isLoading: Boolean = false, val errorMessage: String? = null, val isLockedOut: Boolean = false)

    fun setupAuthentication(packageName: String, lockType: LockType) { currentPkg = packageName }
    
    fun authenticateWithPin(pin: String) {
        viewModelScope.launch {
            uiState.value = uiState.value.copy(isLoading = true)
            val app = securityRepository.getProtectedApp(currentPkg)
            val hash = app?.pinHash ?: sessionManager.getMasterPinHash()
            if (hash != null) {
                when (val res = authManager.verifyPin(currentPkg, pin, hash)) {
                    is AuthenticationManager.AuthResult.Success -> { sessionManager.unlockApp(currentPkg); uiState.value = uiState.value.copy(isLoading = false) }
                    is AuthenticationManager.AuthResult.Failure -> uiState.value = uiState.value.copy(isLoading = false, errorMessage = res.message)
                    is AuthenticationManager.AuthResult.LockedOut -> uiState.value = uiState.value.copy(isLoading = false, isLockedOut = true, errorMessage = "Too many failed attempts")
                }
            } else {
                uiState.value = uiState.value.copy(isLoading = false, errorMessage = "No PIN is set")
            }
        }
    }
}
