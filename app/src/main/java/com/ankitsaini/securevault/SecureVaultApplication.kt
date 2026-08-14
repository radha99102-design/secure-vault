package com.ankitsaini.securevault

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SecureVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
