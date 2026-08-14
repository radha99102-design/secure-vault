package com.ankitsaini.securevault.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ==========================================
// 1. ENUMS (Constants)
// ==========================================
enum class LockType { PIN, PATTERN, BIOMETRIC, NONE }
enum class EventType { 
    APP_LAUNCH_ATTEMPT, LOCK_SCREEN_SHOWN, UNLOCK_SUCCESSFUL, UNLOCK_FAILED, 
    INTRUDER_PHOTO_CAPTURED, LOCK_TYPE_CHANGED, APP_PROTECTION_ENABLED, 
    APP_PROTECTION_DISABLED, NOTIFICATION_MASKED, BIOMETRIC_AUTH_SUCCESS, 
    BIOMETRIC_AUTH_FAILED, PATTERN_ATTEMPT_FAILED, PIN_ATTEMPT_FAILED 
}
enum class MaskingLevel { NONE, PARTIAL, FULL }

// ==========================================
// 2. CONVERTERS (To save Enums in Database)
// ==========================================
class Converters {
    @TypeConverter fun fromLockType(value: LockType) = value.name
    @TypeConverter fun toLockType(value: String) = try { LockType.valueOf(value) } catch (e: Exception) { LockType.PIN }
    @TypeConverter fun fromEventType(value: EventType) = value.name
    @TypeConverter fun toEventType(value: String) = try { EventType.valueOf(value) } catch (e: Exception) { EventType.APP_LAUNCH_ATTEMPT }
    @TypeConverter fun fromMaskingLevel(value: MaskingLevel) = value.name
    @TypeConverter fun toMaskingLevel(value: String) = try { MaskingLevel.valueOf(value) } catch (e: Exception) { MaskingLevel.FULL }
}

// ==========================================
// 3. ENTITIES (Database Tables)
// ==========================================
@Entity(tableName = "protected_apps")
data class ProtectedApp(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "is_protected") val isProtected: Boolean = true,
    @ColumnInfo(name = "lock_type") val lockType: LockType = LockType.PIN,
    @ColumnInfo(name = "pin_hash") val pinHash: String? = null,
    @ColumnInfo(name = "pattern_hash") val patternHash: String? = null,
    @ColumnInfo(name = "use_biometric") val useBiometric: Boolean = false,
    @ColumnInfo(name = "lock_delay_minutes") val lockDelayMinutes: Int = 0,
    @ColumnInfo(name = "notification_masking") val notificationMasking: Boolean = true,
    @ColumnInfo(name = "max_failed_attempts") val maxFailedAttempts: Int = 3,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAt: Long? = null,
    @ColumnInfo(name = "app_icon") val appIcon: ByteArray? = null,
    @ColumnInfo(name = "is_system_app") val isSystemApp: Boolean = false,
    @ColumnInfo(name = "relock_on_screen_off") val relockOnScreenOff: Boolean = true,
    @ColumnInfo(name = "intruder_photo_enabled") val intruderPhotoEnabled: Boolean = true
)

@Entity(tableName = "security_events")
data class SecurityEvent(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "event_id") val eventId: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "event_type") val eventType: EventType,
    @ColumnInfo(name = "event_timestamp") val eventTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "event_details") val eventDetails: String? = null,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "was_successful") val wasSuccessful: Boolean = false
)

@Entity(tableName = "app_policies")
data class AppPolicy(
    @PrimaryKey @ColumnInfo(name = "policy_id") val policyId: String,
    @ColumnInfo(name = "policy_name") val policyName: String,
    @ColumnInfo(name = "default_lock_type") val defaultLockType: LockType = LockType.PIN,
    @ColumnInfo(name = "require_biometric_fallback") val requireBiometricFallback: Boolean = false,
    @ColumnInfo(name = "relock_timeout_seconds") val relockTimeoutSeconds: Int = 30,
    @ColumnInfo(name = "max_failed_attempts") val maxFailedAttempts: Int = 3,
    @ColumnInfo(name = "intruder_photo_enabled") val intruderPhotoEnabled: Boolean = true,
    @ColumnInfo(name = "notification_masking_level") val notificationMaskingLevel: MaskingLevel = MaskingLevel.FULL,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "failed_attempts")
data class FailedAttemptRecord(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "attempt_id") val attemptId: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "attempt_timestamp") val attemptTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "attempt_method") val attemptMethod: String,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "device_battery_level") val deviceBatteryLevel: Int? = null,
    @ColumnInfo(name = "device_location") val deviceLocation: String? = null
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey @ColumnInfo(name = "settings_key") val settingsKey: String,
    @ColumnInfo(name = "settings_value") val settingsValue: String,
    @ColumnInfo(name = "settings_type") val settingsType: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

// ==========================================
// 4. DAOs (Queries & Actions)
// ==========================================
@Dao
interface ProtectedAppDao {
    @Query("SELECT * FROM protected_apps ORDER BY app_name ASC") fun getAllProtectedApps(): Flow<List<ProtectedApp>>
    @Query("SELECT * FROM protected_apps WHERE package_name = :packageName LIMIT 1") suspend fun getProtectedAppByPackage(packageName: String): ProtectedApp?
    @Query("SELECT * FROM protected_apps WHERE package_name = :packageName LIMIT 1") fun observeProtectedAppByPackage(packageName: String): Flow<ProtectedApp?>
    @Query("SELECT * FROM protected_apps WHERE is_protected = 1 ORDER BY app_name ASC") fun getActiveProtectedApps(): Flow<List<ProtectedApp>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProtectedApp(protectedApp: ProtectedApp)
    @Update suspend fun updateProtectedApp(protectedApp: ProtectedApp)
    @Query("UPDATE protected_apps SET lock_type = :lockType, updated_at = :timestamp WHERE package_name = :packageName") suspend fun updateLockType(packageName: String, lockType: LockType, timestamp: Long = System.currentTimeMillis())
    @Query("UPDATE protected_apps SET is_protected = :isProtected, updated_at = :timestamp WHERE package_name = :packageName") suspend fun updateProtectionStatus(packageName: String, isProtected: Boolean, timestamp: Long = System.currentTimeMillis())
    @Query("DELETE FROM protected_apps WHERE package_name = :packageName") suspend fun deleteByPackageName(packageName: String)
    @Query("UPDATE protected_apps SET last_accessed_at = :timestamp WHERE package_name = :packageName") suspend fun updateLastAccessed(packageName: String, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface SecurityEventDao {
    @Query("SELECT * FROM security_events ORDER BY event_timestamp DESC") fun getAllEvents(): Flow<List<SecurityEvent>>
    @Query("SELECT * FROM security_events WHERE package_name = :packageName ORDER BY event_timestamp DESC LIMIT :limit") fun getEventsForPackage(packageName: String, limit: Int = 50): Flow<List<SecurityEvent>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvent(event: SecurityEvent): Long
}

@Dao
interface FailedAttemptDao {
    @Query("SELECT * FROM failed_attempts ORDER BY attempt_timestamp DESC") fun getAllFailedAttempts(): Flow<List<FailedAttemptRecord>>
    @Query("SELECT * FROM failed_attempts WHERE package_name = :packageName ORDER BY attempt_timestamp DESC LIMIT :limit") fun getFailedAttemptsForPackage(packageName: String, limit: Int = 50): Flow<List<FailedAttemptRecord>>
    @Query("SELECT COUNT(*) FROM failed_attempts WHERE package_name = :packageName AND attempt_timestamp >= :sinceTime") fun getFailedAttemptCountForPackage(packageName: String, sinceTime: Long): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFailedAttempt(record: FailedAttemptRecord): Long
}

@Dao
interface AppPolicyDao {
    @Query("SELECT * FROM app_policies ORDER BY created_at DESC") fun getAllPolicies(): Flow<List<AppPolicy>>
    @Query("SELECT * FROM app_policies WHERE policy_id = :policyId LIMIT 1") suspend fun getPolicyById(policyId: String): AppPolicy?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPolicy(policy: AppPolicy)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE settings_key = :key LIMIT 1") suspend fun getSetting(key: String): AppSettings?
    @Query("SELECT * FROM app_settings") fun getAllSettings(): Flow<List<AppSettings>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSetting(setting: AppSettings)
    @Query("UPDATE app_settings SET settings_value = :value, updated_at = :timestamp WHERE settings_key = :key") suspend fun updateSetting(key: String, value: String, timestamp: Long = System.currentTimeMillis())
}

// ==========================================
// 5. THE DATABASE CLASS
// ==========================================
@Database(
    entities = [ProtectedApp::class, SecurityEvent::class, FailedAttemptRecord::class, AppPolicy::class, AppSettings::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SecureVaultDatabase : RoomDatabase() {
    
    abstract fun protectedAppDao(): ProtectedAppDao
    abstract fun securityEventDao(): SecurityEventDao
    abstract fun failedAttemptDao(): FailedAttemptDao
    abstract fun appPolicyDao(): AppPolicyDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: SecureVaultDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): SecureVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SecureVaultDatabase::class.java,
                    "secure_vault_database"
                )
                .addCallback(DatabaseCallback(scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val scope: CoroutineScope) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        // Seed default policies and settings
                        database.appPolicyDao().insertPolicy(
                            AppPolicy("default_policy", "Default Security Policy")
                        )
                        database.settingsDao().insertSetting(AppSettings("global_security_enabled", "true", "boolean"))
                        database.settingsDao().insertSetting(AppSettings("intruder_photo_enabled", "true", "boolean"))
                        database.settingsDao().insertSetting(AppSettings("max_failed_attempts", "3", "int"))
                        database.settingsDao().insertSetting(AppSettings("relock_timeout_seconds", "30", "int"))
                    }
                }
            }
        }
    }
}
