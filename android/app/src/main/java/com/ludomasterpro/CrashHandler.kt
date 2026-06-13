package com.example.app

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

object CrashHandler {

    fun init(context: Context) {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            context.openFileOutput(
                "crash_log.txt",
                Context.MODE_PRIVATE
            ).use {
                it.write(sw.toString().toByteArray())
            }

            android.os.Process.killProcess(
                android.os.Process.myPid()
            )
        }
    }
}
