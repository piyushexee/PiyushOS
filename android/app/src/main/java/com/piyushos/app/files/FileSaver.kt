package com.piyushos.app.files

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.piyushos.app.ShareActivity
import java.io.ByteArrayOutputStream

object FileSaver {
    /** Base64 file ko Downloads/PiyushOS folder me save karta hai. */
    fun saveFile(context: Context, name: String, mime: String, b64: String): Uri? {
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PiyushOS")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            uri
        } catch (e: Exception) {
            null
        }
    }

    fun pngBytes(bmp: Bitmap): ByteArray {
        val os = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, os)
        return os.toByteArray()
    }
}

object Notifier {
    /** File aayi to notification dikhao + Share ka button do. */
    fun notifyFile(context: Context, uri: Uri, name: String, mime: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel("files") == null) {
                nm.createNotificationChannel(
                    NotificationChannel("files", "PiyushOS Files", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val shareIntent = Intent(context, ShareActivity::class.java)
                .putExtra("uri", uri.toString())
                .putExtra("mime", mime)
                .putExtra("name", name)
            val pi = PendingIntent.getActivity(
                context,
                name.hashCode(),
                shareIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(context, "files")
                .setContentTitle("PiyushOS")
                .setContentText("$name taiyaar hai — Share dabao")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setAutoCancel(true)
                .addAction(0, "Share", pi)
                .build()
            nm.notify(name.hashCode(), n)
        } catch (_: Exception) {
        }
    }
}
