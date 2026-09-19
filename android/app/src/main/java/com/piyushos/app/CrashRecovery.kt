package com.piyushos.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * POORA crash screen - bilkul plain Android views me (Compose NAHI).
 * Iska matlab: agar Compose ya koi bhi library crash kar rahi hai, to bhi yeh
 * screen kabhi crash nahi karegi — kyunki yeh sirf basic framework hi use karta hai.
 *
 * User ko crash log copy/share (WhatsApp) karke bhejne ka button milta hai,
 * bina PC/adb ke.
 */
object CrashRecovery {

    fun show(activity: Activity, log: String, onRetry: () -> Unit) {
        val pad = dp(activity, 16)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A0F14"))
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(activity).apply {
            text = "⚠️ Pichli baar app crash ho gaya"
            setTextColor(Color.parseColor("#EF5350"))
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, 0, 0, dp(activity, 6))
        }
        root.addView(title)

        val hint = TextView(activity).apply {
            text = "Neeche crash ka reason likha hai. 'Share (WhatsApp)' dabao aur log bhej do — fix turant mil jayega."
            setTextColor(Color.parseColor("#B9A0A6"))
            textSize = 13f
            setPadding(0, 0, 0, dp(activity, 10))
        }
        root.addView(hint)

        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.parseColor("#120A0D"))
        }
        val logView = TextView(activity).apply {
            text = log
            setTextColor(Color.parseColor("#E8B4BC"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10))
            isLongClickable = true // select + copy possible
        }
        scroll.addView(logView)
        root.addView(scroll)

        val row1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(activity, 10), 0, dp(activity, 6))
        }
        val shareBtn = Button(activity).apply {
            text = "📤 Share (WhatsApp)"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { shareLog(activity, log) }
        }
        val copyBtn = Button(activity).apply {
            text = "📋 Copy"
            setPadding(dp(activity, 6), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { copyLog(activity, log) }
        }
        row1.addView(shareBtn)
        row1.addView(copyBtn)
        root.addView(row1)

        val row2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val retryBtn = Button(activity).apply {
            text = "🔄 Dobara App Kholo"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                CrashLogger.clear(activity)
                onRetry()
            }
        }
        val clearBtn = Button(activity).apply {
            text = "⚙️ App Info"
            setPadding(dp(activity, 6), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                try {
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:" + activity.packageName)
                    }
                    activity.startActivity(i)
                } catch (_: Exception) {
                }
            }
        }
        row2.addView(retryBtn)
        row2.addView(clearBtn)
        root.addView(row2)

        activity.setContentView(root)
    }

    private fun copyLog(activity: Activity, log: String) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PiyushOS Crash Log", log))
        (activity as? android.app.Activity)?.let {
            android.widget.Toast
                .makeText(it, "Crash log copy ho gaya — paste karke bhej do", android.widget.Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun shareLog(activity: Activity, log: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PiyushOS Crash Log")
            putExtra(Intent.EXTRA_TEXT, log)
        }
        try {
            activity.startActivity(Intent.createChooser(share, "Crash log bhejo (WhatsApp/Telegram)"))
        } catch (e: Exception) {
            copyLog(activity, log)
        }
    }

    private fun dp(activity: Activity, v: Int): Int =
        (v * activity.resources.displayMetrics.density).toInt()
}
