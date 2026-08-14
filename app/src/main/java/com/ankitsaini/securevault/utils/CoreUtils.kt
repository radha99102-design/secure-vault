package com.ankitsaini.securevault.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

object Constants {
    const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"
    const val PREF_AUTO_START = "auto_start_on_boot"
    const val PREF_INTRUDER_PHOTO = "intruder_photo_enabled"
    const val PREF_MAX_FAILED_ATTEMPTS = "max_failed_attempts"
    const val PREF_RELOCK_TIMEOUT = "relock_timeout"
    const val PREF_STEALTH_MODE = "stealth_mode"
    const val KEY_FAKE_CRASH = "fake_crash_on_failed"
    const val PREF_MASTER_PIN = "master_pin_hash"
    const val PREF_MASTER_PATTERN = "master_pattern_hash"
    const val KEY_LAST_BACKUP = "last_backup_time"
}

object DateUtils {
    fun getRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (60 * 1000)
        return if (minutes < 1) "Just now" else if (minutes < 60) "$minutes mins ago" else "${minutes / 60} hours ago"
    }
    fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    fun formatDateTime(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context, "secure_vault_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getString(key: String, def: String? = null): String? = prefs.getString(key, def)
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getBoolean(key: String, def: Boolean = false): Boolean = prefs.getBoolean(key, def)
    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    fun getInt(key: String, def: Int = 0): Int = prefs.getInt(key, def)
    fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    fun clear() = prefs.edit().clear().apply()
}

@Singleton
class AppUtils @Inject constructor(@ApplicationContext private val context: Context) {
    data class DeviceInfo(val manufacturer: String, val model: String, val androidVersion: String)
    fun getAppVersionName(): String = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
    fun getAppVersionCode(): Int = try { context.packageManager.getPackageInfo(context.packageName, 0).versionCode } catch (e: Exception) { 1 }
    fun getDeviceInfo(): DeviceInfo = DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE)
}

@Singleton
class FileManager @Inject constructor(@ApplicationContext private val context: Context) {
    fun createBackupFile(data: String): String? {
        return try {
            val dir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
            val file = File(dir, "backup_${System.currentTimeMillis()}.json")
            FileOutputStream(file).use { it.write(data.toByteArray()) }
            file.absolutePath
        } catch (e: Exception) { null }
    }
    fun getFilesInDirectory(directory: String): List<File> {
        val dir = File(context.filesDir, directory)
        return if (dir.exists()) dir.listFiles()?.toList() ?: emptyList() else emptyList()
    }
    fun clearTempFiles() {
        File(context.filesDir, "temp").apply { if (exists()) deleteRecursively() }
    }
}
