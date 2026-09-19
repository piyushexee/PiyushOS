package com.piyushos.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class PhoneControllerService : AccessibilityService() {

    companion object {
        @Volatile var instance: PhoneControllerService? = null

        private val ALIASES = mapOf(
            "insta" to listOf("instagram"),
            "ig" to listOf("instagram"),
            "wa" to listOf("whatsapp"),
            "whats" to listOf("whatsapp"),
            "yt" to listOf("youtube"),
            "browser" to listOf("chrome", "brave", "edge"),
            "mail" to listOf("gmail"),
            "setting" to listOf("settings"),
            "g" to listOf("google"),
        )
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ---------------- gestures ----------------

    private fun path(x1: Float, y1: Float, x2: Float, y2: Float): Path =
        Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path(x1, y1, x2, y2), 0, durationMs)
        val gd = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gd, null, null)
    }

    fun tap(x: Int, y: Int) {
        gesture(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat(), 60)
    }

    fun doubleTap(x: Int, y: Int) {
        tap(x, y)
        try { Thread.sleep(90) } catch (_: Exception) {}
        tap(x, y)
    }

    fun longPress(x: Int, y: Int) {
        gesture(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat(), 600)
    }

    fun swipe(direction: String, amount: Int) {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val d = dm.heightPixels * (amount.coerceIn(10, 90)) / 100f
        val pts = when (direction) {
            "up" -> floatArrayOf(cx, cy + d / 2, cx, cy - d / 2)
            "down" -> floatArrayOf(cx, cy - d / 2, cx, cy + d / 2)
            "left" -> floatArrayOf(cx + d / 2, cy, cx - d / 2, cy)
            else -> floatArrayOf(cx - d / 2, cy, cx + d / 2, cy) // right
        }
        gesture(pts[0], pts[1], pts[2], pts[3], 350)
    }

    // ---------------- typing ----------------

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ---------------- screen tree (AI ko screen dikhane ke liye) ----------------

    /**
     * Current screen ka compact UI tree JSON:
     * [{"i":0,"t":"text","b":[x1,y1,x2,y2],"c":true,"e":true,"s":true,"cl":"EditText","r":"id"}]
     */
    fun screenTree(maxNodes: Int = 150): String {
        val root = rootInActiveWindow ?: return "[]"
        val sb = StringBuilder("[")
        var count = 0

        fun walk(n: AccessibilityNodeInfo) {
            if (count >= maxNodes) return
            val text = n.text?.toString()?.trim().orEmpty()
            val desc = n.contentDescription?.toString()?.trim().orEmpty()
            val t = if (text.isNotEmpty()) text else desc
            val cls = n.className?.toString().orEmpty().substringAfterLast('.')
            val rid = n.viewIdResourceName.orEmpty().substringAfterLast('/')
            val rect = Rect()
            n.getBoundsInScreen(rect)

            val interesting = t.isNotEmpty() || n.isClickable || n.isEditable || n.isScrollable
            if (interesting) {
                if (count > 0) sb.append(',')
                sb.append("{\"i\":").append(count)
                if (t.isNotEmpty()) {
                    sb.append(",\"t\":\"").append(t.replace("\"", "'").take(60)).append('"')
                }
                sb.append(",\"b\":[").append(rect.left).append(',')
                    .append(rect.top).append(',').append(rect.right).append(',').append(rect.bottom).append(']')
                if (n.isClickable) sb.append(",\"c\":true")
                if (n.isEditable) sb.append(",\"e\":true")
                if (n.isScrollable) sb.append(",\"s\":true")
                if (cls.isNotEmpty()) sb.append(",\"cl\":\"").append(cls).append('"')
                if (rid.isNotEmpty()) sb.append(",\"r\":\"").append(rid).append('"')
                sb.append('}')
                count++
            }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        walk(root)
        sb.append(']')
        root.recycle()
        return sb.toString()
    }

    // ---------------- global actions ----------------

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recentApps(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    // ---------------- open app (name se) ----------------

    fun openApp(appName: String): Boolean {
        val name = appName.trim().lowercase(Locale.ROOT)
        if (name.isEmpty()) return false
        val pm = packageManager

        // 1) exact package match
        val launch = pm.getLaunchIntentForPackage(name)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
            return true
        }

        // 2) aliases + label scan
        val candidates = mutableListOf(name)
        ALIASES[name]?.let { candidates.addAll(it) }
        val query = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        for (ri in query) {
            val label = ri.loadLabel(pm).toString().lowercase(Locale.ROOT)
            val matched = candidates.any { label == it || label.contains(it) }
            if (matched) {
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(ri.packageName, ri.name))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return true
            }
        }
        return false
    }
}
