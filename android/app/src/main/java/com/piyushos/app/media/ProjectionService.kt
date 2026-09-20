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
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class ProjectionService : Service() {

    companion object {
        const val EXTRA_DATA = "data"
        const val EXTRA_CODE = "code"

        @Volatile var ready = false
            private set

        /** Setup me kya galti hui (user ko dikhane ke liye) */
        @Volatile var lastError: String? = null
            private set

        private var vDisplay: VirtualDisplay? = null
        private var reader: ImageReader? = null
        private var projection: MediaProjection? = null

        /**
         * Screen ka screenshot (Bitmap), nahi mil paye to null.
         * Pehle frame ke liye thoda wait karta hai (auto-mirror display ko
         * frame push karne me ~1 sec lagta hai).
         */
        fun capture(): Bitmap? {
            val r = reader ?: return null
            var img = r.acquireLatestImage()
            var tries = 0
            while (img == null && tries < 5) {
                Thread.sleep(300)
                img = r.acquireLatestImage()
                tries++
            }
            if (img == null) return null
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

        private fun readData(intent: Intent?): Intent? {
            if (intent == null) return null
            return try {
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Android 14+ mandatory callback - system ne capture band kiya to clean state */
    private val mediaCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            ready = false
            lastError = "system ne screen capture band kar diya — app me 'Screenshot ON' dobara dabao"
            try {
                stopForeground(true)
                stopSelf()
            } catch (_: Exception) {
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
            .setContentText("Screen capture taiyaar")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        val data = readData(intent)
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        if (data == null) {
            // Android 12/13/14 par yahi silent failure hota tha - ab dikhaya jayega
            ready = false
            lastError = "screen capture data nahi mili (code=$code) — permission dobara do"
            return START_NOT_STICKY
        }
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mgr.getMediaProjection(code, data)
            if (proj == null) {
                ready = false
                lastError = "MediaProjection nahi ban paya (code=$code) — permission dobara do"
                return START_NOT_STICKY
            }
            // Android 14 (API 34) par mandatory: callback register karo createVirtualDisplay
            // se PEHLE, warna "Must register a callback before starting capture..." error
            try {
                proj.registerCallback(mediaCallback, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                // purane Android par yeh nahi chahiye
            }
            val dm = resources.displayMetrics
            val rd = ImageReader.newInstance(
                dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2
            )
            val vd = proj.createVirtualDisplay(
                "PiyushOS",
                dm.widthPixels, dm.heightPixels,
                dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                rd.surface,
                null,
                null
            )
            if (vd == null) {
                ready = false
                lastError = "Virtual display nahi ban payi"
                return START_NOT_STICKY
            }
            projection = proj
            reader = rd
            vDisplay = vd
            ready = true
            lastError = null
        } catch (e: Exception) {
            ready = false
            lastError = "setup me galti: ${e.message}"
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { vDisplay?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        vDisplay = null; reader = null; projection = null
        ready = false
        super.onDestroy()
    }
}
