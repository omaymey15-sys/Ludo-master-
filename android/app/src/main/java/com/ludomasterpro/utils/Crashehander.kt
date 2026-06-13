package com.ludomasterpro.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
    private val crashLogDir: File

    companion object {
        private var instance: CrashHandler? = null

        fun init(context: Context) {
            if (instance == null) {
                instance = CrashHandler(context.applicationContext)
                Thread.setDefaultUncaughtExceptionHandler(instance)
            }
        }

        fun getLastCrashLog(context: Context): String? {
            val crashDir = File(context.filesDir, "crashes")
            if (!crashDir.exists()) return null

            val files = crashDir.listFiles { file -> file.name.endsWith(".log") }
            if (files.isNullOrEmpty()) return null

            return files.maxByOrNull { it.lastModified() }?.readText()
        }
    }

    init {
        crashLogDir = File(context.filesDir, "crashes")
        if (!crashLogDir.exists()) {
            crashLogDir.mkdirs()
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        handleException(throwable)
        // Délai pour sauvegarder le log avant de planter
        Thread.sleep(1000)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun handleException(throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = "crash_$timestamp.log"
            val crashFile = File(crashLogDir, fileName)

            val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(crashFile), "UTF-8"))

            writer.write("=== CRASH REPORT ===\n")
            writer.write("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            writer.write("App Version: ${getAppVersion()}\n")
            writer.write("Android Version: ${Build.VERSION.SDK_INT}\n")
            writer.write("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            writer.write("CPU ABI: ${Build.SUPPORTED_ABIS?.joinToString() ?: "Unknown"}\n")
            writer.write("====================\n\n")

            writer.write("Exception: ${throwable.javaClass.name}\n")
            writer.write("Message: ${throwable.message ?: "No message"}\n\n")
            writer.write("Stack Trace:\n")
            writer.write(getStackTraceString(throwable))

            // Log cause si existe
            var cause = throwable.cause
            while (cause != null) {
                writer.write("\n\nCaused by: ${cause.javaClass.name}\n")
                writer.write("Message: ${cause.message ?: "No message"}\n")
                writer.write(getStackTraceString(cause))
                cause = cause.cause
            }

            writer.close()

            android.util.Log.e("CrashHandler", "Crash log saved to: ${crashFile.absolutePath}")

        } catch (e: Exception) {
            android.util.Log.e("CrashHandler", "Failed to save crash log", e)
        }
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.close()
        return sw.toString()
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
