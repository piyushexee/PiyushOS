package com.piyushos.app.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder

class ProjectionService : Service() {

    companion object {
        const val EXTRA_DATA = "data"
        const val EXTRA_CODE = "code"
        @Volatile var ready = false
            private set
        private var vDisplay: VirtualDisplay? = null
        private var reader: ImageReader? = null
        private var projection: MediaProjection? = null

        /** Screen ka screenshot leta hai (Bitmap), nahi mil paye to null. */
        fun capture(): Bitmap? {
            val r = reader ?: return null
            val img = r.acquireLatestImage() ?: return null
            return try {
                val plane = img.planes[0]
                val buffer = plane.buffer
                val w = img.width
                val h = img.height
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                val rowPadding = rowStride - pixelStride * w
                val bmp = Bitmap.createBitmap(
                    w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888
                )
                bmp.copyPixelsFromBuffer(buffer)
                img.close()
                if (bmp.width == w) bmp else Bitmap.createBitmap(bmp, 0, 0, w, h)
            } catch (e: Exception) {
                img.close()
                null
            }
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel("projection") == null) {
            nm.createNotificationChannel(
                NotificationChannel("projection", "PiyushOS screen capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, "projection")
            .setContentTitle("PiyushOS")
            .setContentText("Screen capture taiyaar (sirf aapke apne server)")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        if (data != null) {
            try {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projection = mgr.getMediaProjection(code, data)
                val dm = resources.displayMetrics
                reader = ImageReader.newInstance(
                    dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2
                )
                vDisplay = projection?.createVirtualDisplay(
                    "PiyushOS",
                    dm.widthPixels,
                    dm.heightPixels,
                    dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader!!.surface,
                    null,
                    null
                )
                ready = true
            } catch (e: Exception) {
                ready = false
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { vDisplay?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        ready = false
        super.onDestroy()
    }
}
