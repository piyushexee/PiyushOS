package com.piyushos.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SABSE PEHLI chalne wali PiyushOS code (ContentProvider - Application se bhi pehle).
 *
 * Kaam:
 * 1. Crash handler install karo (taaki Application/Activity wala koi bhi crash save ho)
 * 2. "Boot canary" file likho (taaki pata chale ki app ki code shuru hui thi ya nahi)
 *
 * Is provider ke BAHAR crash ho (dex loading etc) to yeh bhi chalega -
 * agle launch par canary + crash log ka status diagnose karta hai.
 */
class CrashGuardProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        try {
            context?.let { ctx ->
                CrashLogger.install(ctx)
                try {
                    File(ctx.filesDir, "boot_canary.txt").writeText(
                        "PiyushOS boot ok at " +
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    )
                } catch (_: Exception) {
                }
            }
        } catch (_: Throwable) {
            // provider kabhi crash nahi kar sakta
        }
        return true
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
