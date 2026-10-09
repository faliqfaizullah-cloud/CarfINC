package com.carfinc.app

import android.app.Application
import com.carfinc.app.data.Prefs
import com.carfinc.app.data.Store
import com.carfinc.app.util.Haptics

class CarfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.ctx = applicationContext
        Haptics.enabled = Prefs.haptics
    }
}
