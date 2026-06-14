package com.ludomasterpro

import android.app.Application

class LudoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(this)
        )
    }
}
