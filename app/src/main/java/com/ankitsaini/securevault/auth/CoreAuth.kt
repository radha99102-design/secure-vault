package com.ankitsaini.securevault.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ankitsaini.securevault.data.LockType
import com.ankitsaini.securevault.data.repository.SecurityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

object PinHasher {
    fun hashPin(pin: String, salt: ByteArray? = null): String {
        val pinSalt = salt ?: ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), pinSalt, 10000, 256)).encoded
        return Base64.getEncoder().encodeToString(pinSalt) + ":" + Base64.getEncoder().encodeToString(hash)
    }
    fun verifyPin(pin: String, storedHash: String): Boolean = try {
        val parts = storedHash.split(":")
        MessageDigest.isEqual(hashPin(pin, Base64.getDecoder().decode(parts[0])).toByteArray(), storedHash.toByteArray())
    } catch (e: Exception) { false }
}

object PatternHasher {
    fun hashPattern(pattern: List<Int>, salt: ByteArray? = null): String = PinHasher.hashPin(pattern.joinToString(","), salt)
    fun verifyPattern(pattern: List<Int>, storedHash: String): Boolean = PinHasher.verifyPin(pattern.joinToString(","), storedHash)
}

@Singleton
class SessionManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
    private val _unlockedApps = MutableStateFlow<Set<String>>(emptySet())
    val unlockedApps: StateFlow<Set<String>> = _unlockedApps.asStateFlow()

    fun setMasterPinHash(pinHash: String) = prefs.edit().putString("master_pin", pinHash).apply()
    fun getMasterPinHash(): String? = prefs.getString("master_pin", null)
    fun setMasterPatternHash(patternHash: String) = prefs.edit().putString("master_pattern", patternHash).apply()
    fun getMasterPatternHash(): String? = prefs.getString("master_pattern", null)
    
    fun unlockApp(packageName: String) { _unlockedApps.value = _unlockedApps.value.toMutableSet().apply { add(packageName) } }
    fun isAppUnlocked(packageName: String): Boolean = _unlockedApps.value.contains(packageName)
    fun relockAllApps() { _unlockedApps.value = emptySet() }
    fun authenticateSession() { prefs.edit().putBoolean("is_auth", true).apply() }
    fun lockForDuration(durationMs: Long) { prefs.edit().putLong("locked_until", System.currentTimeMillis() + durationMs).apply() }
    fun clearAllSessionData() = prefs.edit().clear().apply()
}

@Singleton
class AuthenticationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityRepository: SecurityRepository
) {
    sealed class AuthState {
        object Idle : AuthState()
        object Authenticating : AuthState()
        data class Success(val packageName: String) : AuthState()
        data class Error(val message: String, val errorCode: Int? = null) : AuthState()
        data class Failed(val attemptsRemaining: Int) : AuthState()
        object LockedOut : AuthState()
    }
    
    sealed class AuthResult {
        data class Success(val packageName: String) : AuthResult()
        data class Failure(val message: String, val errorCode: Int? = null) : AuthResult()
        data class LockedOut(val retryAfterMillis: Long) : AuthResult()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    private var failedAttempts = 0

    fun isBiometricAvailable(): Boolean = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    suspend fun verifyPin(packageName: String, enteredPin: String, storedPinHash: String): AuthResult {
        return if (PinHasher.verifyPin(enteredPin, storedPinHash)) {
            failedAttempts = 0
            securityRepository.logSuccessfulUnlock(packageName, "PIN")
            AuthResult.Success(packageName)
        } else {
            failedAttempts++
            securityRepository.logFailedUnlock(packageName, "PIN")
            if (failedAttempts >= 3) AuthResult.LockedOut(30000) else AuthResult.Failure("Invalid PIN")
        }
    }

    suspend fun verifyPattern(packageName: String, enteredPattern: List<Int>, storedPatternHash: String): AuthResult {
        return if (PatternHasher.verifyPattern(enteredPattern, storedPatternHash)) {
            failedAttempts = 0
            securityRepository.logSuccessfulUnlock(packageName, "PATTERN")
            AuthResult.Success(packageName)
        } else {
            failedAttempts++
            securityRepository.logFailedUnlock(packageName, "PATTERN")
            if (failedAttempts >= 3) AuthResult.LockedOut(30000) else AuthResult.Failure("Invalid Pattern")
        }
    }
    
    fun resetFailedAttempts() { failedAttempts = 0; _authState.value = AuthState.Idle }
}
