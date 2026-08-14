package com.ankitsaini.securevault.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ankitsaini.securevault.data.LockType
import com.ankitsaini.securevault.ui.screens.*
import dagger.hilt.android.AndroidEntryPoint

// ==========================================
// 1. THEME & COLORS
// ==========================================
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF10B981), // Emerald
    background = Color(0xFF020617),
    surface = Color(0xFF0F172A),
    error = Color(0xFFF43F5E)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF059669),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFE11D48)
)

@Composable
fun SecureVaultTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// ==========================================
// 2. NAVIGATION
// ==========================================
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object AppList : Screen("app_list")
    object SecurityLog : Screen("security_log")
    object Settings : Screen("settings")
}

@Composable
fun SecureVaultNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
        composable(Screen.Dashboard.route) { 
            DashboardScreen(
                onNavigateToAppList = { navController.navigate(Screen.AppList.route) }, 
                onNavigateToSecurityLog = { navController.navigate(Screen.SecurityLog.route) }, 
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            ) 
        }
        composable(Screen.AppList.route) { AppListScreen(onBackClick = { navController.popBackStack() }) }
        composable(Screen.SecurityLog.route) { SecurityLogScreen(onBackClick = { navController.popBackStack() }) }
        composable(Screen.Settings.route) { SettingsScreen(onBackClick = { navController.popBackStack() }) }
    }
}

// ==========================================
// 3. MAIN ACTIVITIES
// ==========================================
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecureVaultTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SecureVaultNavigation()
                }
            }
        }
    }
}

@AndroidEntryPoint
class LockScreenActivity : ComponentActivity() {
    companion object {
        fun createIntent(context: Context, packageName: String, appName: String, lockType: LockType): Intent {
            return Intent(context, LockScreenActivity::class.java).apply {
                putExtra("pkg", packageName)
                putExtra("app", appName)
                putExtra("lock", lockType.name)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        
        val pkg = intent.getStringExtra("pkg") ?: ""
        val app = intent.getStringExtra("app") ?: "App"
        val lock = LockType.valueOf(intent.getStringExtra("lock") ?: LockType.PIN.name)

        setContent {
            SecureVaultTheme {
                LockScreen(
                    packageName = pkg, 
                    appName = app, 
                    lockType = lock, 
                    onAuthenticated = { setResult(Activity.RESULT_OK); finish() }, 
                    onCancel = { handleCancel() }
                )
            }
        }
    }
    
    private fun handleCancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
        startActivity(Intent(Intent.ACTION_MAIN).apply { 
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK 
        })
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        handleCancel()
    }
}
