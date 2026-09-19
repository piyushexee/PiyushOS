package com.piyushos.app

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Built-in crash logger - PC/adb ke bina bhi crash samajhne ke liye.
 * Crash hua toh stack trace save hota hai, aur agli baar app kholne par
 * UI me dikh jata hai (Copy dabake bhej sakte ho).
 */
object CrashLogger {
    private const val PREFS = "piyushos"
    private const val KEY = "last_crash_log"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            val sb = StringBuilder()
            sb.append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append("\n")
            sb.append("Thread: ").append(thread.name).append("\n")
            sb.append(ex.javaClass.simpleName).append(": ").append(ex.message).append("\n")
            for (tr in ex.stackTrace) sb.append("  at ").append(tr.toString()).append("\n")
            var cause = ex.cause
            var depth = 0
            while (cause != null && depth < 4) {
                sb.append("Caused by: ").append(cause.javaClass.simpleName).append(": ").append(cause.message).append("\n")
                for (tr in cause.stackTrace) sb.append("  at ").append(tr.toString()).append("\n")
                cause = cause.cause
                depth++
            }
            val log = sb.toString()
            try {
                Log.e("PiyushOS_CRASH", log)
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, log).apply()
            } catch (_: Exception) {
            }
            // original handler ko pass karo (app waise hi band hoga)
            if (previous != null) previous.uncaughtException(thread, ex) else throw ex
        }
    }

    fun last(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
