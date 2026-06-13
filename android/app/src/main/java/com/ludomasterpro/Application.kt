package com.ludomasterpro

import android.app.Application
import com.ludomasterpro.utils.CrashHandler

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialiser le CrashHandler
        CrashHandler.init(this)

        // Vérifier s'il y a un crash précédent au lancement
        checkForPreviousCrash()
    }

    private fun checkForPreviousCrash() {
        try {
            val lastCrash = CrashHandler.getLastCrashLog(this)
            if (lastCrash != null) {
                android.util.Log.w("AppStartup", "Previous crash detected:\n$lastCrash")
                // Tu peux envoyer ce log à ton serveur ou l'afficher en debug
            }
        } catch (e: Exception) {
            android.util.Log.e("AppStartup", "Error checking previous crash", e)
        }
    }
}
