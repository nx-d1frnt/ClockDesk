package com.nxd1frnt.clockdesk2

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller

class MainApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        installSecurityProvider()
    }

    private fun installSecurityProvider() {
        try {
            ProviderInstaller.installIfNeeded(applicationContext)
        } catch (e: GooglePlayServicesRepairableException) {
            Log.e("MainApplication", "Google Play Services is repairable", e)
        } catch (e: GooglePlayServicesNotAvailableException) {
            Log.e("MainApplication", "Google Play Services is not available", e)
        }
    }
}
