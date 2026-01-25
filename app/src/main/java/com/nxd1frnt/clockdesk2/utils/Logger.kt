package com.nxd1frnt.clockdesk2.utils

import android.util.Log

object Logger {
    @Volatile
    var isLoggingEnabled: Boolean = false

    inline fun d(tag: String, message: () -> String) {
        if (isLoggingEnabled) {
            Log.d(tag, message())
        }
    }

    inline fun w(tag: String, message: () -> String) {
        if (isLoggingEnabled) {
            Log.w(tag, message())
        }
    }

    inline fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        Log.e(tag, message(), throwable)
    }

    inline fun i(tag: String, message: () -> String) {
        if (isLoggingEnabled) {
            Log.i(tag, message())
        }
    }
}