package com.ludomasterpro

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable
    ) {

        try {
            val crashDir = context.getExternalFilesDir(null)
            if (crashDir != null) {

                val logFile = File(crashDir, "crashlog.txt")

                val stackTrace = StringWriter()
                throwable.printStackTrace(
                    PrintWriter(stackTrace)
                )

                val timestamp = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

                val report = """
                    
==================== CRASH ====================
Date: $timestamp
Thread: ${thread.name}

Type: ${throwable.javaClass.name}
Message: ${throwable.message}

${stackTrace}

================================================

                """.trimIndent()

                FileWriter(logFile, true).use {
                    it.append(report)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        defaultHandler?.uncaughtException(
            thread,
            throwable
        )
    }
}
