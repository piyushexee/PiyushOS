package com.piyushos.app.ai

import android.util.Base64
import com.piyushos.app.accessibility.PhoneControllerService
import com.piyushos.app.files.FileSaver
import com.piyushos.app.media.ProjectionService
import com.piyushos.app.media.ScreenOcr
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * GUI agent - kisi bhi app ke ANDAR kaam karwana (Astra-style).
 * Loop: PLAN → screen dekho (UI tree + screenshot + OCR) → AI decide → action → dobara dekho.
 * Python brain/tools/gui_tool.py ka port.
 */
class GuiAgent(
    private val apiKey: String,
    private val onProgress: (String) -> Unit,
) {

    private data class ScreenState(
        val tree: String,
        val imgB64: String?,
        val w: Int,
        val h: Int,
        val ocr: String,
    )

    private fun svc(): PhoneControllerService? = PhoneControllerService.instance

    private fun captureScreen(): ScreenState {
        val tree = svc()?.screenTree() ?: "[]"
        val bmp = ProjectionService.capture()
        if (bmp == null) return ScreenState(tree, null, 0, 0, "")
        val b64 = Base64.encodeToString(FileSaver.pngBytes(bmp), Base64.NO_WRAP)
        val w = bmp.width; val h = bmp.height
        val textNodes = Pattern.compile("\"t\":").matcher(tree).results().count()
        val ocr = if (textNodes < 6) ScreenOcr.ocr(bmp) else ""
        bmp.recycle()
        return ScreenState(tree, b64, w, h, ocr)
    }

    private fun makePlan(task: String): String {
        return try {
            val msgs = JSONArray()
                .put(JSONObject().put("role", "system").put("content", PLAN_SYSTEM))
                .put(JSONObject().put("role", "user").put("content", "TASK: $task"))
            val raw = NimClient.raw(apiKey, msgs)
            raw.trim().ifEmpty { "" }
        } catch (e: Exception) {
            ""
        }
    }

    private val validActions = setOf(
        "tap", "tap_xy", "long_press", "double_tap", "type", "swipe",
        "back", "home", "wait", "done", "fail"
    )

    private val jsonPattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL)

    private fun parseAction(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val m = jsonPattern.matcher(raw)
        if (!m.find()) return null
        val obj = try {
            JSONObject(m.group())
        } catch (e: Exception) {
            return null
        }
        return if (validActions.contains(obj.optString("action"))) obj else null
    }

    private fun elCenter(tree: String, idx: Int): Pair<Int, Int>? {
        return try {
            val els = JSONArray(tree)
            for (i in 0 until els.length()) {
                val el = els.getJSONObject(i)
                if (el.optInt("i", -1) == idx) {
                    val b = el.optJSONArray("b")
                    if (b != null && b.length() == 4) {
                        return ((b.getInt(0) + b.getInt(2)) / 2) to ((b.getInt(1) + b.getInt(3)) / 2)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Main entry: app kholo (optional), plan banao, steps chalao. */
    fun run(app: String, task: String, maxStepsIn: Int = 10): JSONObject {
        if (task.isBlank()) throw IllegalArgumentException("task chahiye - kya karna hai detail me likho")
        val maxSteps = maxStepsIn.coerceIn(3, 20)

        // 1) app kholo (agar bola hai)
        if (app.isNotBlank()) {
            val s = svc() ?: throw IllegalStateException("Accessibility service on nahi hai — app me 'Accessibility ON' dabao")
            if (!s.openApp(app.trim())) {
                throw IllegalStateException("App '$app' phone par nahi khul payi")
            }
            Thread.sleep(2500)
        }
        if (svc() == null) {
            throw IllegalStateException("Accessibility service on nahi hai — app me 'Accessibility ON' dabao")
        }

        // 2) plan
        val plan = makePlan(task)
        onProgress("📋 Plan: ${plan.lineSequence().firstOrNull()?.take(80) ?: "kaam shuru"}")

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", GUI_SYSTEM))
            .put(
                JSONObject().put("role", "user")
                    .put("content", "TASK: $task\nPLAN:\n${plan.ifEmpty { "(directly execute)" }}\nShuru karo - pehle screen dekho.")
            )

        for (step in 0 until maxSteps) {
            val n = step + 1

            // 3) screen
            val screen = captureScreen()
            if (screen.tree == "[]" && screen.imgB64 == null) {
                val reason = if (ProjectionService.ready) {
                    "screen nahi dikh rahi - Accessibility service on karo (Settings > Accessibility > PiyushOS)"
                } else {
                    "screen capture ready nahi: ${ProjectionService.lastError ?: "unknown"} - app me 'Screenshot ON' dobara dabao"
                }
                return JSONObject().put("error", reason)
                    .put("summary", "GUI kaam nahi ho paya: $reason")
            }

            var prompt = (
                "TASK: $task\nPLAN:\n${plan.ifEmpty { "(none)" }}\n" +
                "STEP $n/$maxSteps\nScreen size: ${if (screen.w > 0) screen.w else "?"}x${if (screen.h > 0) screen.h else "?"} px\n" +
                "SCREEN: ${screen.tree.take(6000)}"
                )
            if (screen.ocr.isNotEmpty()) prompt += "\nOCR (screen ka text): ${screen.ocr.take(2000)}"

            val userMsg = if (screen.imgB64 != null) {
                NimClient.visionMessage(prompt, screen.imgB64)
            } else {
                JSONObject().put("role", "user").put("content", prompt)
            }
            messages.put(userMsg)

            // 4) AI decide
            val raw: String
            try {
                raw = NimClient.raw(apiKey, messages)
            } catch (e: Exception) {
                return JSONObject().put("error", "GUI AI call fail: ${e.message}")
                    .put("summary", "GUI kaam nahi ho paya: ${e.message}")
            }
            val action = parseAction(raw)
            messages.put(
                JSONObject().put("role", "assistant")
                    .put("content", if (action != null) action.toString() else raw)
            )
            if (action == null) {
                messages.put(
                    JSONObject().put("role", "user")
                        .put("content", "Invalid reply. Reply with ONLY the JSON action object.")
                )
                continue
            }
            val a = action.optString("action")

            // 5) done/fail?
            if (a == "done") {
                return JSONObject()
                    .put("summary", "GUI kaam poora ($n steps): ${action.optString("result", "")}")
                    .put("result", action.optString("result", "")).put("steps", n)
            }
            if (a == "fail") {
                return JSONObject()
                    .put("summary", "GUI kaam nahi ho paya: ${action.optString("reason", "")}")
                    .put("error", action.optString("reason", "")).put("steps", n)
            }

            // 6) action
            val s = svc() ?: return JSONObject().put("error", "Accessibility service band ho gayi").put("summary", "GUI kaam nahi ho paya")
            onProgress("📱 Step $n: $a")
            try {
                when {
                    a in setOf("tap", "long_press", "double_tap") && action.has("target") -> {
                        val center = elCenter(screen.tree, action.optInt("target", -1))
                            ?: throw IllegalStateException("Element ${action.optInt("target")} screen me nahi mila")
                        val (cx, cy) = center
                        when (a) {
                            "tap" -> s.tap(cx, cy)
                            "long_press" -> s.longPress(cx, cy)
                            "double_tap" -> s.doubleTap(cx, cy)
                        }
                    }
                    a == "tap_xy" -> s.tap(action.optInt("x", 0), action.optInt("y", 0))
                    a == "type" -> {
                        if (!s.typeText(action.optString("text", ""))) {
                            throw IllegalStateException("Text box focus nahi mila - pehle field par tap karo")
                        }
                    }
                    a == "swipe" -> s.swipe(action.optString("direction", "up"), 50)
                    a == "back" -> s.back()
                    a == "home" -> s.home()
                    a == "wait" -> Thread.sleep(2500)
                    else -> { /* unknown */ }
                }
            } catch (e: Exception) {
                messages.put(
                    JSONObject().put("role", "user")
                        .put("content", "Action fail hua: ${e.message}. Screen waise hi hai. Ab kya karna hai?")
                )
                continue
            }
            Thread.sleep(1500)
        }

        return JSONObject()
            .put("summary", "GUI task $maxSteps steps me poora nahi hua")
            .put("error", "steps khatam ho gaye").put("steps", maxSteps)
    }

    companion object {
        private val PLAN_SYSTEM =
            "You are a phone-task planner. Break the user's task into 3-10 short, concrete steps that a phone GUI operator can follow (one tap/type/decision per step).\n" +
            "Reply with ONLY a numbered list, one step per line, in Hinglish. No intro, no outro."

        private val GUI_SYSTEM = """You are an expert Android GUI operator. You control a real phone by looking at the screen state. You are given a PLAN and you execute it step by step, verifying after every action.

INPUT each step:
- PLAN: the numbered step list
- TASK: the original task
- SCREEN: JSON array of on-screen elements. Each element:
    i = index, t = text/label, b = [x1,y1,x2,y2] in pixels,
    c = clickable, e = editable, s = scrollable, cl = class, r = id
- Sometimes also a photo of the screen and/or OCR text.

Reply with ONLY one JSON object, no other words:
{"action":"tap","target":<i>}                    - tap element i from SCREEN
{"action":"tap_xy","x":<px>,"y":<px>}            - tap a pixel (only if visible in photo but not in SCREEN)
{"action":"long_press","target":<i>}             - long press element i
{"action":"double_tap","target":<i>}             - double tap element i
{"action":"type","text":"..."}                   - type into the focused text field
{"action":"swipe","direction":"up|down|left|right"}
{"action":"back"}
{"action":"home"}
{"action":"wait"}
{"action":"done","result":"<what was accomplished, in Hinglish>"}
{"action":"fail","reason":"<why it failed, in Hinglish>"}

RULES:
- Only ONE action per step.
- Tap the element whose text best matches the current PLAN step. Never tap unrelated things.
- If you just opened an app and SCREEN is empty, do {"action":"wait"}.
- If the person/item you need is not visible, swipe up and look again (max 3 times), then fail if still not found.
- For typing: first tap the editable field (e=true), then type.
- After EVERY action the next SCREEN tells you the result. If the screen did NOT change as expected, try a different approach (different element, swipe, back, tap_xy from photo).
- When the TASK is verifiably done (message sent, search result visible, item selected, screen shows the result), return done.
- If you tried 2-3 alternatives for the same sub-goal and still cannot proceed, return fail.

APP CHEAT SHEET (known layouts - use as hints, trust the actual SCREEN over these):
- WhatsApp: magnifier/search icon at top. Below = chat list (each row: name + last message, t= shows name). Tap a row to open chat. In a chat: bottom input box is e=true with mic+emoji icons; Send button (paper plane) appears inside that row after text is typed.
- Instagram: top = search bar (e=true). Bottom bar = Home, Search, Reels, Shop, Profile (person icon, far right). Tap a profile row to open it.
- YouTube: magnifier icon top-right; type in search; tap a result.
- Gmail: compose button at bottom-right (+ or pencil). In compose: "To" field top, big body area e=true, Send at top.
- Google Maps: search bar at top (e=true); tap a result; navigation buttons at bottom.
- Chrome: omnibox/address bar at top (e=true).
- PowerPoint (mobile): "+" or "New" to create → pick a template (first one is fine) → tap a slide to edit; tap text placeholder then type; "+ Add slide" / plus icon to add slides.
- Settings: search bar at top; category list below; tap a category."""
    }
}
