package com.piyushos.app

import android.content.Context
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Built-in crash logger - PC/adb ke bina bhi crash samajhne ke liye.
 * Crash hua toh stack trace + device info save hota hai, aur agli baar app
 * kholne par POORA crash screen dikh jata hai (Copy / WhatsApp share button ke saath).
 */
object CrashLogger {
    private const val PREFS = "piyushos"
    private const val KEY = "last_crash_log"
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            val log = format(appContext, thread, ex)
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

    /** Explicit crash save (onCreate me catch hua to bhi yahi use hoga). */
    fun save(context: Context, thread: Thread, ex: Throwable) {
        try {
            val log = format(context.applicationContext, thread, ex)
            Log.e("PiyushOS_CRASH", log)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, log).apply()
        } catch (_: Exception) {
        }
    }

    private fun format(ctx: Context, thread: Thread, ex: Throwable): String {
        val sb = StringBuilder()
        sb.append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append("\n")
        sb.append("Thread: ").append(thread.name).append("\n")
        // device + app info (diagnosis ke liye bahut kaam aata hai)
        try {
            sb.append("Device: ").append(Build.MANUFACTURER).append(" ")
              .append(Build.MODEL).append(" | Android ")
              .append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            val pkg = ctx.packageName
            val ai = ctx.packageManager.getPackageInfo(pkg, 0)
            sb.append("App: ").append(pkg)
              .append(" v").append(ai.versionName).append(" (code ").append(ai.longVersionCode).append(")\n")
        } catch (_: Exception) {
        }
        sb.append("Exception: ").append(ex.javaClass.name).append(": ").append(ex.message).append("\n")
        for (tr in ex.stackTrace) sb.append("  at ").append(tr.toString()).append("\n")
        var cause = ex.cause
        var depth = 0
        while (cause != null && depth < 4) {
            sb.append("Caused by: ").append(cause.javaClass.name).append(": ").append(cause.message).append("\n")
            for (tr in cause.stackTrace) sb.append("  at ").append(tr.toString()).append("\n")
            cause = cause.cause
            depth++
        }
        return sb.toString()
    }

    fun last(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
