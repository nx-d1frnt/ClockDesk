package com.nxd1frnt.clockdesk2

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.nxd1frnt.clockdesk2.ui.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter

class ClockDeskApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val processName = getCurrentProcessName(this)
        // Only set the uncaught exception handler in the main process, not the crash reporting process itself.
        if (!processName.endsWith(":crash")) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    launchCrashActivity(thread, throwable)
                } catch (e: Exception) {
                    Log.e("ClockDeskApp", "Failed to launch crash activity", e)
                    defaultHandler?.uncaughtException(thread, throwable)
                } finally {
                    Process.killProcess(Process.myPid())
                    System.exit(10)
                }
            }
        }
    }

    private fun launchCrashActivity(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val crashIntent = Intent(this, CrashActivity::class.java).apply {
            putExtra(CrashActivity.EXTRA_STACK_TRACE, stackTrace)
            putExtra(CrashActivity.EXTRA_THREAD_NAME, thread.name)
            putExtra(CrashActivity.EXTRA_EXCEPTION_CLASS, throwable.javaClass.name)
            putExtra(CrashActivity.EXTRA_EXCEPTION_MESSAGE, throwable.localizedMessage ?: throwable.message ?: "No message")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(crashIntent)
    }

    private fun getCurrentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        val pid = Process.myPid()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val processInfos = manager?.runningAppProcesses
        if (processInfos != null) {
            for (info in processInfos) {
                if (info.pid == pid) {
                    return info.processName
                }
            }
        }
        return context.packageName
    }
}
