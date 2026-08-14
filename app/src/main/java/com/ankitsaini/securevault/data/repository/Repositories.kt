package com.ankitsaini.securevault.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.ankitsaini.securevault.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DashboardData(
    val protectedApps: List<ProtectedApp>,
    val recentEvents: List<SecurityEvent>,
    val failedAttempts: List<FailedAttemptRecord>,
    val totalProtectedApps: Int,
    val totalFailedAttempts: Int,
    val totalSecurityEvents: Int
)

@Singleton
class SecurityRepository @Inject constructor(
    private val protectedAppDao: ProtectedAppDao,
    private val securityEventDao: SecurityEventDao,
    private val failedAttemptDao: FailedAttemptDao,
    private val appPolicyDao: AppPolicyDao,
    private val settingsDao: SettingsDao
) {
    fun getAllProtectedApps(): Flow<List<ProtectedApp>> = protectedAppDao.getAllProtectedApps()
    fun getActiveProtectedApps(): Flow<List<ProtectedApp>> = protectedAppDao.getActiveProtectedApps()
    suspend fun getProtectedApp(packageName: String): ProtectedApp? = protectedAppDao.getProtectedAppByPackage(packageName)
    fun observeProtectedApp(packageName: String): Flow<ProtectedApp?> = protectedAppDao.observeProtectedAppByPackage(packageName)
    suspend fun addProtectedApp(app: ProtectedApp) = protectedAppDao.insertProtectedApp(app)
    suspend fun updateProtectedApp(app: ProtectedApp) = protectedAppDao.updateProtectedApp(app)
    suspend fun updateLockType(packageName: String, lockType: LockType) = protectedAppDao.updateLockType(packageName, lockType)
    suspend fun toggleProtection(packageName: String, isProtected: Boolean) = protectedAppDao.updateProtectionStatus(packageName, isProtected)
    suspend fun removeProtectedApp(packageName: String) = protectedAppDao.deleteByPackageName(packageName)
    
    fun getSecurityEvents(): Flow<List<SecurityEvent>> = securityEventDao.getAllEvents()
    fun getEventsForPackage(packageName: String): Flow<List<SecurityEvent>> = securityEventDao.getEventsForPackage(packageName)
    suspend fun logEvent(event: SecurityEvent): Long = securityEventDao.insertEvent(event)
    
    suspend fun logFailedUnlock(packageName: String, method: String): Long {
        val eventId = securityEventDao.insertEvent(SecurityEvent(packageName = packageName, eventType = EventType.UNLOCK_FAILED, eventDetails = "Failed $method attempt", wasSuccessful = false))
        failedAttemptDao.insertFailedAttempt(FailedAttemptRecord(packageName = packageName, attemptMethod = method))
        return eventId
    }
    
    suspend fun logSuccessfulUnlock(packageName: String, method: String) {
        securityEventDao.insertEvent(SecurityEvent(packageName = packageName, eventType = EventType.UNLOCK_SUCCESSFUL, eventDetails = "Successful $method unlock", wasSuccessful = true))
        protectedAppDao.updateLastAccessed(packageName)
    }
    
    fun getFailedAttempts(): Flow<List<FailedAttemptRecord>> = failedAttemptDao.getAllFailedAttempts()
    suspend fun getSetting(key: String): AppSettings? = settingsDao.getSetting(key)
    fun getAllSettings(): Flow<List<AppSettings>> = settingsDao.getAllSettings()
    suspend fun updateSetting(key: String, value: String) = settingsDao.updateSetting(key, value)
    
    fun getDashboardData(): Flow<DashboardData> {
        return combine(protectedAppDao.getAllProtectedApps(), securityEventDao.getAllEvents(), failedAttemptDao.getAllFailedAttempts()) { apps, events, attempts ->
            DashboardData(apps, events.take(10), attempts, apps.count { it.isProtected }, attempts.size, events.size)
        }
    }
}

@Singleton
class AppInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityRepository: SecurityRepository
) {
    data class InstalledAppInfo(val packageName: String, val appName: String, val icon: Drawable?, val isSystemApp: Boolean, val isProtected: Boolean = false)
    
    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        try {
            val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val protectedAppsList = mutableListOf<ProtectedApp>()
            securityRepository.getAllProtectedApps().collect { protectedAppsList.addAll(it) }
            val protectedPackages = protectedAppsList.filter { it.isProtected }.map { it.packageName }.toSet()
            
            packages.filter { it.packageName != context.packageName }.map { appInfo ->
                InstalledAppInfo(
                    appInfo.packageName,
                    try { packageManager.getApplicationLabel(appInfo).toString() } catch (e: Exception) { appInfo.packageName },
                    try { packageManager.getApplicationIcon(appInfo.packageName) } catch (e: Exception) { null },
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    protectedPackages.contains(appInfo.packageName)
                )
            }.sortedBy { it.appName.lowercase() }
        } catch (e: Exception) { emptyList() }
    }
    
    suspend fun getAppInfo(packageName: String): InstalledAppInfo? = withContext(Dispatchers.IO) {
        try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            InstalledAppInfo(packageName, packageManager.getApplicationLabel(appInfo).toString(), packageManager.getApplicationIcon(packageName), (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
        } catch (e: Exception) { null }
    }
    
    fun observeInstalledApps(): Flow<List<InstalledAppInfo>> = flow { emit(getInstalledApps()) }.flowOn(Dispatchers.IO)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SecureVaultDatabase {
        return SecureVaultDatabase.getDatabase(context, CoroutineScope(SupervisorJob()))
    }
    @Provides fun provideProtectedAppDao(database: SecureVaultDatabase) = database.protectedAppDao()
    @Provides fun provideSecurityEventDao(database: SecureVaultDatabase) = database.securityEventDao()
    @Provides fun provideFailedAttemptDao(database: SecureVaultDatabase) = database.failedAttemptDao()
    @Provides fun provideAppPolicyDao(database: SecureVaultDatabase) = database.appPolicyDao()
    @Provides fun provideSettingsDao(database: SecureVaultDatabase) = database.settingsDao()
}
