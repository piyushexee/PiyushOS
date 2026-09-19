package com.piyushos.app

import android.app.Application

/**
 * Application class - app ki SABSE PEHLI chalne wali app code (providers ke baad).
 * Crash handler yahan install hota hai, isliye koi bhi Activity se pehle hua crash
 * bhi save hota hai.
 */
class PiyushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
