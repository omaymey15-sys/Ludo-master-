package com.ludomasterpro

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val writer = StringWriter()
            throwable.printStackTrace(PrintWriter(writer))

            val crashInfo = """
                Time: ${System.currentTimeMillis()}
                
                Thread: ${thread.name}
                
                Error:
                ${writer}
            """.trimIndent()

            val file = File(context.getExternalFilesDir(null), "crashlog.txt")
            file.writeText(crashInfo)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        defaultHandler?.uncaughtException(thread, throwable)
    }
}
