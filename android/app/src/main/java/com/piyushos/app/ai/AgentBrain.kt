package com.piyushos.app.ai

import android.content.Context
import com.piyushos.app.accessibility.PhoneControllerService
import com.piyushos.app.ai.files.Model3D
import com.piyushos.app.ai.files.PptxWriter
import com.piyushos.app.ai.files.XlsxWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * In-app Brain - PiyushOS ka dimaag phone ke andar (koi Termux/server nahi).
 * Python brain/agent.py ka port: LLM + tools ka loop.
 *
 * handleUser() BLOCKING hai - Dispatchers.IO se call karna.
 */
class AgentBrain(
    private val context: Context,
    private val apiKey: String,
    private val onProgress: (String) -> Unit,
    private val onFile: (name: String, mime: String, file: File) -> Unit,
    private val onClipboard: (String) -> Unit,
    private val onFinal: (String) -> Unit,
) {

    // ---------- in-memory "last file" models (edit ke liye) ----------

    private data class PptModel(val topic: String, val slides: MutableList<PptxWriter.Slide>, val fileName: String)
    private data class SheetModel(val name: String, val headers: MutableList<Any?>, val rows: MutableList<MutableList<Any?>>)
    private data class XlsxModel(val title: String, val sheets: MutableList<SheetModel>)

    private var lastPpt: PptModel? = null
    private var lastXlsx: XlsxModel? = null
    private val history = ArrayList<JSONObject>()

    private val outDir: File = File(context.cacheDir, "piyushos_out").apply { mkdirs() }

    // ================= main loop =================

    fun handleUser(text: String) {
        var messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        for (m in history) messages.put(m)
        messages.put(JSONObject().put("role", "user").put("content", text))

        var visionOn = true

        for (step in 0 until MAX_STEPS) {
            // ---------- LLM call ----------
            val out: NimClient.Result
            try {
                out = NimClient.chat(apiKey, messages, TOOL_SPECS)
            } catch (e: Exception) {
                val hasImage = (0 until messages.length()).any {
                    messages.get(it) is JSONObject && (messages.getJSONObject(it).opt("content") is JSONArray)
                }
                if (visionOn && hasImage) {
                    // image messages hatao aur retry
                    val clean = JSONArray()
                    for (i in 0 until messages.length()) {
                        val m = messages.getJSONObject(i)
                        if (m.opt("content") is JSONArray) continue
                        clean.put(m)
                    }
                    messages = clean
                    visionOn = false
                    continue
                }
                onFinal("😵 AI se baat karne me problem aayi: ${e.message}")
                return
            }

            // ---------- Final answer? ----------
            if (out.toolCalls.isEmpty()) {
                val final = (out.content ?: "").trim().ifEmpty { "Ho gaya! ✅" }
                history.add(JSONObject().put("role", "user").put("content", text))
                history.add(JSONObject().put("role", "assistant").put("content", final))
                while (history.size > 12) history.removeAt(0)
                onFinal(final)
                return
            }

            // ---------- tool calls assistant message ----------
            val tcArr = JSONArray()
            for (tc in out.toolCalls) {
                tcArr.put(
                    JSONObject()
                        .put("id", tc.id)
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject().put("name", tc.name).put("arguments", tc.args.toString())
                        )
                )
            }
            messages.put(
                JSONObject().put("role", "assistant").put("content", out.content ?: "").put("tool_calls", tcArr)
            )

            // ---------- tools chalo ----------
            for (tc in out.toolCalls) {
                onProgress("⚙️ ${tc.name}...")
                val result: JSONObject = try {
                    executeTool(tc.name, tc.args)
                } catch (e: Exception) {
                    JSONObject().put("error", e.message ?: "Tool me galti")
                }

                var summary = result.toString()
                if (summary.length > 4000) summary = summary.take(4000)
                messages.put(
                    JSONObject().put("role", "tool").put("tool_call_id", tc.id).put("content", summary)
                )

                // bane hue files phone par
                val files = result.optJSONArray("files")
                if (files != null) {
                    for (i in 0 until files.length()) {
                        val p = files.getString(i)
                        val f = File(p)
                        if (!f.exists()) continue
                        val name = f.name
                        onProgress("📤 $name phone par bhej raha hu...")
                        try {
                            onFile(name, mimeFor(name), f)
                        } catch (e: Exception) {
                            onProgress("⚠️ $name save nahi ho paya: ${e.message}")
                        }
                    }
                }
                if (result.has("clipboard")) {
                    try {
                        onClipboard(result.getString("clipboard"))
                    } catch (e: Exception) {}
                }

                // screenshot -> LLM ko dikhao
                if (tc.name == "screenshot" && visionOn && result.has("image_b64")) {
                    messages.put(
                        NimClient.visionMessage(
                            "Yeh abhi ka phone screen ka screenshot hai. Isse dekh kar agla action socho.",
                            result.getString("image_b64")
                        )
                    )
                }
            }
        }
        onFinal("😅 Kaam 12 steps me nahi ho paya. Ek baar dobara bolo, thoda aur detail me.")
    }

    // ================= tool dispatch =================

    private fun executeTool(name: String, args: JSONObject): JSONObject = when (name) {
        "create_presentation" -> doCreatePpt(args.optString("topic", ""), args.optString("outline_json", ""))
        "create_spreadsheet" -> doCreateXlsx(args.optString("spec_json", ""))
        "create_3d_model" -> doCreate3d(args.optString("name", "model"), args.optString("shape", "rocket"), args.optString("color", "#4fc3f7"))
        "share_text" -> doShareText(args.optString("title", "text"), args.optString("content", ""))
        "gui_task" -> GuiAgent(apiKey, onProgress).run(
            args.optString("app", ""), args.optString("task", ""), args.optInt("max_steps", 10)
        )
        "edit_presentation" -> doEditPpt(args.optString("edits_json", ""))
        "edit_spreadsheet" -> doEditXlsx(args.optString("edits_json", ""))
        "open_app" -> deviceOpenApp(args.optString("app", ""))
        "tap" -> dev { PhoneControllerService.instance?.tap(args.optInt("x"), args.optInt("y")) }
            .put("summary", "(${args.optInt("x")}, ${args.optInt("y")}) par tap kar diya")
        "double_tap" -> dev { PhoneControllerService.instance?.doubleTap(args.optInt("x"), args.optInt("y")) }
            .put("summary", "(${args.optInt("x")}, ${args.optInt("y")}) par double tap kar diya")
        "long_press" -> dev { PhoneControllerService.instance?.longPress(args.optInt("x"), args.optInt("y")) }
            .put("summary", "(${args.optInt("x")}, ${args.optInt("y")}) par long press kar diya")
        "swipe" -> {
            val dir = args.optString("direction", "up").lowercase()
            if (dir !in setOf("up", "down", "left", "right")) throw IllegalArgumentException("direction up/down/left/right me se ek hona chahiye")
            dev { PhoneControllerService.instance?.swipe(dir, args.optInt("amount", 50).coerceIn(10, 90)) }
                .put("summary", "Screen $dir ${args.optInt("amount", 50)}% swipe kar diya")
        }
        "input_text" -> {
            val t = args.optString("text", "")
            val s = PhoneControllerService.instance
                ?: throw IllegalStateException("Accessibility service on nahi hai — app me 'Accessibility ON' dabao")
            if (!s.typeText(t)) throw IllegalStateException("Typing nahi kar paya (text box focus nahi mila)")
            JSONObject().put("summary", "Text type kar diya: ${t.take(60)}")
        }
        "back" -> dev { PhoneControllerService.instance?.back() }.put("summary", "Back button dabaya")
        "home" -> dev { PhoneControllerService.instance?.home() }.put("summary", "Home screen par aa gaye")
        "recent_apps" -> dev { PhoneControllerService.instance?.recentApps() }.put("summary", "Recent apps khol diye")
        "open_notifications" -> dev { PhoneControllerService.instance?.openNotifications() }.put("summary", "Notification shade khol di")
        "screenshot" -> deviceScreenshot()
        "wait" -> {
            val s = args.optDouble("seconds", 1.0).coerceIn(0.5, 10.0)
            Thread.sleep((s * 1000).toLong())
            JSONObject().put("summary", "${s}s wait kiya")
        }
        else -> throw IllegalArgumentException("Unknown tool: $name")
    }

    private fun dev(block: () -> Unit): JSONObject {
        if (PhoneControllerService.instance == null) {
            throw IllegalStateException("Accessibility service on nahi hai — app me 'Accessibility ON' dabao aur list me PiyushOS ko ON karo.")
        }
        block()
        return JSONObject()
    }

    private fun deviceOpenApp(app: String): JSONObject {
        if (app.isBlank()) throw IllegalArgumentException("app ka naam chahiye")
        val s = PhoneControllerService.instance
            ?: throw IllegalStateException("Accessibility service on nahi hai — app me 'Accessibility ON' dabao")
        if (!s.openApp(app)) throw IllegalStateException("App '$app' phone par nahi mili")
        return JSONObject().put("app", app).put("summary", "App '$app' khol di phone par")
    }

    private fun deviceScreenshot(): JSONObject {
        val bmp = com.piyushos.app.media.ProjectionService.capture()
        val ps = com.piyushos.app.media.ProjectionService
        if (bmp == null) {
            val msg = if (ps.ready) {
                "Screenshot frame abhi nahi mila (screen busy ho sakti hai) — 2-3 second baad dobara try karo"
            } else {
                "Screenshot permission sahi se ON nahi hui (${ps.lastError ?: "reason unknown"}) — user ko bolo app me 'Screenshot ON' dobara dabaye"
            }
            throw IllegalStateException(msg)
        }
        val b64 = android.util.Base64.encodeToString(com.piyushos.app.files.FileSaver.pngBytes(bmp), android.util.Base64.NO_WRAP)
        val w = bmp.width; val h = bmp.height
        bmp.recycle()
        return JSONObject()
            .put("image_b64", b64)
            .put("summary", "Screenshot capture kiya (screen ${w}x${h} px)")
    }

    // ================= file tools =================

    private fun doCreatePpt(topic: String, outlineJson: String): JSONObject {
        val arr = try {
            JSONArray(outlineJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("outline_json valid nahi: ${e.message}")
        }
        if (arr.length() == 0) throw IllegalArgumentException("outline_json me koi slide nahi")
        val slides = ArrayList<PptxWriter.Slide>()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val title = s.optString("title", if (i == 0) topic.ifEmpty { "Slide" } else "Slide ${i + 1}")
            val subtitle = if (s.has("subtitle") && !s.isNull("subtitle")) s.optString("subtitle") else null
            val bullets = ArrayList<String>()
            val b = s.optJSONArray("bullets")
            if (b != null) for (j in 0 until b.length()) bullets.add(b.getString(j))
            slides.add(PptxWriter.Slide(title, subtitle, bullets))
        }
        val stamp = OoxmlStamp.stamp()
        val name = "PPT_${OoxmlStamp.safeName(topic)}_$stamp.pptx"
        val f = File(outDir, name)
        PptxWriter.build(topic, slides, f)
        lastPpt = PptModel(topic, slides.toMutableList(), name)
        return JSONObject()
            .put("path", f.absolutePath).put("name", name).put("files", JSONArray().put(f.absolutePath))
            .put("slides", slides.size)
            .put("summary", "PPT ban gayi: $name (${slides.size} slides)")
    }

    private fun doCreateXlsx(specJson: String): JSONObject {
        val spec = try {
            JSONObject(specJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("spec_json valid nahi: ${e.message}")
        }
        val title = spec.optString("title", "Data")
        val sheetsArr = spec.optJSONArray("sheets") ?: JSONArray().put(JSONObject().put("name", "Sheet1"))
        val model = ArrayList<SheetModel>()
        for (i in 0 until sheetsArr.length()) {
            val s = sheetsArr.getJSONObject(i)
            val shName = s.optString("name", "Sheet ${i + 1}")
            val headers = ArrayList<Any?>()
            val h = s.optJSONArray("headers")
            if (h != null) for (j in 0 until h.length()) headers.add(h.getString(j))
            val rows = ArrayList<MutableList<Any?>>()
            val r = s.optJSONArray("rows")
            if (r != null) {
                for (j in 0 until r.length()) {
                    val rowArr = r.getJSONArray(j)
                    val row = ArrayList<Any?>()
                    for (k in 0 until rowArr.length()) row.add(jsonValue(rowArr.get(k)))
                    rows.add(row)
                }
            }
            model.add(SheetModel(shName, headers, rows))
        }
        val stamp = OoxmlStamp.stamp()
        val name = "Excel_${OoxmlStamp.safeName(title)}_$stamp.xlsx"
        val f = File(outDir, name)
        val ws = model.map { XlsxWriter.Sheet(it.name, it.headers.map { v -> v?.toString() ?: "" }, it.rows.map { r -> r.toList() }) }
        XlsxWriter.build(title, ws, f)
        lastXlsx = XlsxModel(title, model)
        return JSONObject()
            .put("path", f.absolutePath).put("name", name).put("files", JSONArray().put(f.absolutePath))
            .put("summary", "Excel ban gayi: $name (${model.size} sheets)")
    }

    private fun doCreate3d(name: String, shape: String, color: String): JSONObject {
        val r = Model3D.build(name, shape, color, outDir)
        val files = r["files"] as List<String>
        return JSONObject()
            .put("name", (r["name"] as String))
            .put("shape", r["shape"])
            .put("files", JSONArray(files.toTypedArray()))
            .put("summary", r["summary"] as String)
    }

    private fun doShareText(title: String, content: String): JSONObject {
        if (content.isBlank()) throw IllegalArgumentException("content khaali hai")
        val f = File(outDir, "${OoxmlStamp.safeName(title)}_${OoxmlStamp.stamp()}.txt")
        f.writeText(content)
        return JSONObject()
            .put("path", f.absolutePath).put("name", f.name)
            .put("files", JSONArray().put(f.absolutePath))
            .put("clipboard", content)
            .put("summary", "Text '$title' tayyaar hai - phone par file bhi bhejungi aur clipboard par copy karungi")
    }

    // ================= edit tools (model se regenerate) =================

    private fun doEditPpt(editsJson: String): JSONObject {
        val model = lastPpt ?: throw IllegalStateException("Pehle PPT banvao (create_presentation) ya file ka naam batao")
        val spec = try {
            JSONObject(editsJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("edits_json valid nahi: ${e.message}")
        }
        val edits = spec.optJSONArray("edits")
            ?: throw IllegalArgumentException("'edits' list khaali hai")
        if (edits.length() == 0) throw IllegalArgumentException("'edits' list khaali hai")

        val done = ArrayList<String>()
        for (i in 0 until minOf(edits.length(), 12)) {
            val e = edits.getJSONObject(i)
            val op = e.optString("op")
            when (op) {
                "new_title" -> {
                    val old = model.slides[0]
                    val newTitle = if (e.has("text") && !e.isNull("text")) e.getString("text") else old.title
                    val newSub = if (e.has("subtitle") && !e.isNull("subtitle")) e.getString("subtitle") else old.subtitle
                    model.slides[0] = PptxWriter.Slide(newTitle, newSub, old.bullets)
                    done.add("title slide")
                }
                "set_title" -> {
                    val idx = maxOf(0, e.optInt("slide", 2) - 1)
                    if (idx >= model.slides.size) throw IllegalArgumentException("Slide ${idx + 1} nahi hai")
                    val s = model.slides[idx]
                    model.slides[idx] = PptxWriter.Slide(e.optString("text", s.title), s.subtitle, s.bullets)
                    done.add("slide ${idx + 1} title")
                }
                "set_bullets" -> {
                    val idx = maxOf(0, e.optInt("slide", 2) - 1)
                    if (idx >= model.slides.size) throw IllegalArgumentException("Slide ${idx + 1} nahi hai")
                    val s = model.slides[idx]
                    val bullets = ArrayList<String>()
                    val b = e.optJSONArray("bullets")
                    if (b != null) for (j in 0 until b.length()) bullets.add(b.getString(j))
                    model.slides[idx] = PptxWriter.Slide(s.title, s.subtitle, bullets)
                    done.add("slide ${idx + 1} bullets")
                }
                "add_slide" -> {
                    val title = e.optString("title", "Nayi Slide")
                    val bullets = ArrayList<String>()
                    val b = e.optJSONArray("bullets")
                    if (b != null) for (j in 0 until b.length()) bullets.add(b.getString(j))
                    val at = minOf(maxOf(e.optInt("after", model.slides.size), 0), model.slides.size)
                    model.slides.add(at, PptxWriter.Slide(title, null, bullets))
                    done.add("new slide '$title'")
                }
                "delete_slide" -> {
                    val idx = maxOf(0, e.optInt("slide", 1) - 1)
                    if (idx >= model.slides.size) throw IllegalArgumentException("Slide ${idx + 1} nahi hai")
                    model.slides.removeAt(idx)
                    done.add("slide ${idx + 1} delete")
                }
                else -> throw IllegalArgumentException("Unknown op: $op")
            }
        }

        val stamp = OoxmlStamp.stamp()
        val stem = model.fileName.removeSuffix(".pptx").replaceFirst(Regex("_edited_.*"), "")
        val name = "${OoxmlStamp.safeName(stem)}_edited_$stamp.pptx"
        val f = File(outDir, name)
        PptxWriter.build(model.topic, model.slides, f)
        lastPpt = PptModel(model.topic, model.slides, name)
        return JSONObject()
            .put("path", f.absolutePath).put("name", name).put("files", JSONArray().put(f.absolutePath))
            .put("edits", JSONArray(done.toTypedArray()))
            .put("summary", "PPT edit ho gayi: ${done.joinToString(", ")} → $name")
    }

    private fun doEditXlsx(editsJson: String): JSONObject {
        val model = lastXlsx ?: throw IllegalStateException("Pehle Excel banvao (create_spreadsheet) ya file ka naam batao")
        val spec = try {
            JSONObject(editsJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("edits_json valid nahi: ${e.message}")
        }
        val edits = spec.optJSONArray("edits")
            ?: throw IllegalArgumentException("'edits' list khaali hai")
        if (edits.length() == 0) throw IllegalArgumentException("'edits' list khaali hai")

        val done = ArrayList<String>()
        for (i in 0 until minOf(edits.length(), 50)) {
            val e = edits.getJSONObject(i)
            val op = e.optString("op")
            val sheetName = if (e.has("sheet") && !e.isNull("sheet")) e.getString("sheet") else model.sheets.first().name
            val sh = model.sheets.find { it.name == sheetName }
                ?: throw IllegalArgumentException("Sheet '$sheetName' nahi mili: ${model.sheets.joinToString { it.name }}")

            when (op) {
                "set_cell", "set_header" -> {
                    val cell = e.optString("cell", "")
                    val ref = parseCellRef(cell) ?: throw IllegalArgumentException("Cell '$cell' valid nahi (e.g. B4)")
                    val (col, row) = ref
                    val value = if (e.has("value") && !e.isNull("value")) jsonValue(e.get("value")) else null
                    if (row == 1) {
                        while (sh.headers.size <= col) sh.headers.add("")
                        sh.headers[col] = value
                    } else {
                        val rIdx = row - 2
                        if (rIdx < sh.rows.size) {
                            val rowList = sh.rows[rIdx]
                            while (rowList.size <= col) rowList.add(null)
                            rowList[col] = value
                        }
                    }
                    done.add("$sheetName!$cell")
                }
                "add_row" -> {
                    val rowArr = e.optJSONArray("row") ?: JSONArray()
                    val row = ArrayList<Any?>()
                    for (j in 0 until rowArr.length()) row.add(jsonValue(rowArr.get(j)))
                    sh.rows.add(row)
                    done.add("$sheetName naya row")
                }
                "delete_row" -> {
                    val row = e.optInt("row", 2)
                    if (row == 1) {
                        sh.headers.clear()
                        done.add("$sheetName header delete")
                    } else {
                        val rIdx = row - 2
                        if (rIdx !in sh.rows.indices) throw IllegalArgumentException("Row $row nahi hai")
                        sh.rows.removeAt(rIdx)
                        done.add("$sheetName row $row delete")
                    }
                }
                else -> throw IllegalArgumentException("Unknown op: $op")
            }
        }

        val stamp = OoxmlStamp.stamp()
        val stem = model.sheets.firstOrNull()?.name ?: model.title
        val name = "Excel_${OoxmlStamp.safeName(stem)}_edited_$stamp.xlsx"
        val f = File(outDir, name)
        val ws = model.sheets.map { XlsxWriter.Sheet(it.name, it.headers.map { v -> v?.toString() ?: "" }, it.rows.map { r -> r.toList() }) }
        XlsxWriter.build(model.title, ws, f)
        return JSONObject()
            .put("path", f.absolutePath).put("name", name).put("files", JSONArray().put(f.absolutePath))
            .put("edits", JSONArray(done.toTypedArray()))
            .put("summary", "Excel edit ho gaya: ${done.size} changes → $name")
    }

    private fun parseCellRef(ref: String): Pair<Int, Int>? {
        val m = Regex("([A-Za-z]{1,3})([1-9][0-9]*)").matchEntire(ref.trim().uppercase()) ?: return null
        val col = m.groupValues[1].fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
        return col to m.groupValues[2].toInt()
    }

    private fun jsonValue(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is Boolean -> v
        is Number -> {
            val d = v.toDouble()
            if (d == Math.floor(d) && !d.isInfinite() && kotlin.math.abs(d) < 1e15) d.toLong() else d
        }
        else -> v.toString()
    }

    private fun mimeFor(name: String): String = when {
        name.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        name.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        name.endsWith(".glb") -> "model/gltf-binary"
        name.endsWith(".stl") -> "model/stl"
        name.endsWith(".txt") -> "text/plain"
        else -> "application/octet-stream"
    }

    companion object {
        const val MAX_STEPS = 12

        // small helper object so AgentBrain doesn't depend on Ooxml directly in name
        object OoxmlStamp {
            fun stamp(): String = com.piyushos.app.ai.files.Ooxml.stamp()
            fun safeName(s: String?): String = com.piyushos.app.ai.files.Ooxml.safeName(s ?: "file")
        }

        val SYSTEM_PROMPT = """Tu PiyushOS hai - Piyush ka personal AI butler jo uske Android phone ko PURA control karta hai, apps ke andar tak.

RULES:
1. Hamesha Hinglish me reply karo - simple, friendly, chhote sentences.
2. Jab bhi user se file ka kaam ho (PPT, Excel, 3D model, LinkedIn, resume, email, bio, kahani) - uska tool use karo, file phone par bhej do. Khud sirf chat me text mat do.
3. PPT ke liye outline_json me solid, informative bullet points likho (user ki bhasha ke hisaab se).

GUI KAAM (apps ke andar) - SABSE ZARURI:
4. KAI BAAR USER KOI BHI TASK KISI BHI APP ME DEGA. Rule: agar kaam kisi app ke SCREEN par hona hai (koi bhi app - WhatsApp, Instagram, YouTube, Gmail, Maps, Settings, Chrome, koi bhi), to HAMESHA gui_task use karo. Agent khud screen dekh kar samjhega kya karna hai - tujhe bas app ka naam + task dena hai. open_app sirf tab jab user ne sirf "kholo" bola ho.
5. gui_task ka TASK specific likho lekin HAMESHA complete sentence me: kaunsi app, kiska naam / kya cheez, exact text kya, kaunsa option chunein. E.g. "Rahul ko msg karo" -> "WhatsApp me 'Rahul' naam ka chat kholo aur message bhejo: <text>". User ne app nahi batayi ho to context se socho (msg bhejna -> WhatsApp, search -> YouTube/Maps, etc.) aur task me app ka naam khud likho.
6. Task mushkil ya lamba ho to chinta mat karo - gui_task ka plan bana kar step-by-step karta hai, screenshot dekh kar verify karta hai, aur galti par dobara try karta hai. Bas task saaf likhna.
6. PPT ka do tarika: (a) default - create_presentation se clean PPTX file banao (fast, professional); (b) agar user NE clearly kaha "phone ke PowerPoint app me banao" / "live banao" - tab gui_task(app="powerpoint", task="...") use karo jo app ke andar slides banayega. Dono options user ko bata do agar wo confuse ho.
7. PowerPoint app me live banate waqt task me har slide ka title + bullets explicitly likhna, warna app me galti se ban jayega.

EDITING / CUSTOMIZATION:
8. User bole "slide 2 ka title badlo", "ye bullet remove karo", "nayi slide add karo" - edit_presentation tool use karo.
9. User bole "B3 me 5000 daalo", "naya row add karo" - edit_spreadsheet use karo.
10. Edit ke baad user ko file ka naya naam bata do aur bata do ki phone par bhej di hai.

PHONE CONTROL (gui_task ke alawa):
11. Simple screen cheezein (screenshot dekhna, back, home, scroll) ke liye screenshot/tap/swipe tools use karo.
12. LinkedIn/resume jaisa content professional English me likho, lekin reply Hinglish me.
13. Agar Accessibility ya Screenshot permission on nahi hai to user ko bata do ki app me 'Accessibility ON' / 'Screenshot ON' dabana hai. File/text wale kaam (PPT, Excel, 3D, LinkedIn) aaj bhi kar sakte ho.
14. Kabhi bhi user ki galti na maaro; kaam chhota sa bhi ho to poora karo aur bata do.
15. gui_task ke dauraan alag-alag updates mat bhejo - wo khud progress bhejta hai.
16. Tool ka error aaye to user ko TOOL KE EXACT WORDS me batao - khud se reason GUESS mat karo. E.g. open_app fail ho to "app phone par nahi mili" mat bolo bina tool ke confirm kiye. Screenshot fail ho to user ko app me 'Screenshot ON' dobara dabane ko bolo (exact reason tool deta hai, usko hi batao).
"""

        val TOOL_SPECS: JSONArray by lazy { buildToolSpecs() }

        private fun buildToolSpecs(): JSONArray = JSONArray().apply {
            fun P(type: String, desc: String = "", hasEnum: Boolean = false) = Triple(type, desc, hasEnum)
            put(fn(
                "create_presentation",
                "PowerPoint PPT banata hai aur phone par bhejta hai",
                params(
                    "topic" to P("string", "PPT ka topic"),
                    "outline_json" to P(
                        "string",
                        "JSON array. Pehla slide: {\"title\": str, \"subtitle\": str}. " +
                            "Baaki slides: {\"title\": str, \"bullets\": [str]}. 4 se 12 slides, informative bullet points."
                    ),
                ),
                required("topic", "outline_json")
            ))
            put(fn(
                "create_spreadsheet",
                "Excel file banata hai aur phone par bhejta hai",
                params(
                    "spec_json" to P(
                        "string",
                        "JSON: {\"title\": str, \"sheets\": [{\"name\": str, \"headers\": [str], \"rows\": [[...]]}]}"
                    ),
                ),
                required("spec_json")
            ))
            put(fn(
                "create_3d_model",
                "3D model banata hai (GLB dekhne ke liye + STL 3D printing ke liye) aur phone par bhejta hai",
                params(
                    "name" to P("string", "Model ka naam (file name ke liye)"),
                    "shape" to P("string", "Model ki shape", true),
                    "color" to P("string", "e.g. red, blue, green ya hex #ff5252"),
                ),
                required("shape"),
                shapeEnum = arrayOf("rocket", "car", "house", "chair", "cube", "sphere", "cylinder", "cone", "torus", "combo")
            ))
            put(fn(
                "share_text",
                "Koi bhi likha hua text (LinkedIn profile, resume, email, bio, project description) phone par file ki tarah bhejta hai + clipboard par copy karta hai",
                params(
                    "title" to P("string", "Text ka naam, e.g. 'LinkedIn About Section'"),
                    "content" to P("string", "Pura text jo phone par bhejna hai"),
                ),
                required("title", "content")
            ))
            put(fn(
                "gui_task",
                "Kisi bhi app ke ANDAR kaam karwana (screen dekh ke navigate karta hai). Jaise: WhatsApp/Instagram me message bhejna, search karna, post banana, PowerPoint app me live slides banana, settings badalna. TASK bahut specific likho - kisko, kya text, kaunsa option.",
                params(
                    "app" to P("string", "Kaunsi app (whatsapp, instagram, powerpoint, youtube, gmail...). Agar app already open hai to khaali chhod do"),
                    "task" to P("string", "Poora kaam detail me. E.g. 'Rahul naam ka chat kholo aur unhe message bhejo: kal milte hain'"),
                    "max_steps" to P("integer", "Max steps (3-12, default 10)"),
                ),
                required("task")
            ))
            put(fn(
                "edit_presentation",
                "Pichli PPT me customization/edit: title badalna, bullets badalna, slide add/delete karna. User bole 'slide 2 badlo' to yeh tool use karo.",
                params(
                    "edits_json" to P(
                        "string",
                        "JSON: {\"edits\": [{\"op\":\"set_title\",\"slide\":2,\"text\":\"...\"}, " +
                            "{\"op\":\"set_bullets\",\"slide\":3,\"bullets\":[\"a\",\"b\"]}, " +
                            "{\"op\":\"add_slide\",\"after\":4,\"title\":\"...\",\"bullets\":[\"a\"]}, " +
                            "{\"op\":\"delete_slide\",\"slide\":5}, " +
                            "{\"op\":\"new_title\",\"text\":\"...\",\"subtitle\":\"...\"}]}"
                    ),
                ),
                required("edits_json")
            ))
            put(fn(
                "edit_spreadsheet",
                "Pichli Excel me edit: cell ka value badalna, row add/delete karna. User bole 'B3 me 5000 daalo' to yeh tool use karo.",
                params(
                    "edits_json" to P(
                        "string",
                        "JSON: {\"edits\": [{\"op\":\"set_cell\",\"sheet\":\"Budget\",\"cell\":\"B4\",\"value\":5000}, " +
                            "{\"op\":\"add_row\",\"sheet\":\"Budget\",\"row\":[\"New\",\"100\",\"90\",\"10\"]}, " +
                            "{\"op\":\"delete_row\",\"sheet\":\"Budget\",\"row\":3}]}"
                    ),
                ),
                required("edits_json")
            ))
            put(fn("open_app", "Phone par koi app sirf kholta hai (naam se, e.g. instagram, whatsapp, chrome). App ke ANDAR kaam ke liye gui_task use karo", params("app" to P("string")), required("app")))
            put(fn("tap", "Screen par pixels (x, y) par tap karta hai. Pehle screenshot dekho.", params("x" to P("integer"), "y" to P("integer")), required("x", "y")))
            put(fn("double_tap", "Pixels (x, y) par double tap", params("x" to P("integer"), "y" to P("integer")), required("x", "y")))
            put(fn("long_press", "Pixels (x, y) par long press", params("x" to P("integer"), "y" to P("integer")), required("x", "y")))
            put(fn("swipe", "Screen ko swipe karta hai", params("direction" to P("string", "", true), "amount" to P("integer", "10-90 (% of screen)")), required("direction"), swipeEnum = arrayOf("up", "down", "left", "right")))
            put(fn("input_text", "Jo bhi text box focus me hai wahan text type karta hai", params("text" to P("string")), required("text")))
            put(fn("back", "Phone ka Back button dabata hai"))
            put(fn("home", "Home screen par le jata hai"))
            put(fn("recent_apps", "Recent apps (multitasking) kholta hai"))
            put(fn("open_notifications", "Notification shade niche kheenchta hai"))
            put(fn("screenshot", "Screen ka screenshot leta hai taaki dekh sako ki abhi kya dikh raha hai"))
            put(fn("wait", "Kuch seconds wait karta hai (app load hone ke liye)", params("seconds" to P("number"))))
        }

        private fun fn(
            name: String,
            desc: String,
            params: Map<String, Triple<String, String, Boolean>> = emptyMap(),
            requiredArr: Array<out String> = emptyArray(),
            shapeEnum: Array<String>? = null,
            swipeEnum: Array<String>? = null,
        ): JSONObject {
            val props = JSONObject()
            for ((p, t) in params) {
                val (type, d, hasEnum) = t
                val pobj = JSONObject().put("type", type)
                if (d.isNotEmpty()) pobj.put("description", d)
                if (hasEnum) {
                    val enumArr = JSONArray()
                    (shapeEnum ?: swipeEnum)?.forEach { enumArr.put(it) }
                    pobj.put("enum", enumArr)
                }
                props.put(p, pobj)
            }
            val req = JSONArray()
            requiredArr.forEach { req.put(it) }
            val schema = JSONObject()
                .put("type", "object")
                .put("properties", props)
            if (req.length() > 0) schema.put("required", req)
            return JSONObject()
                .put("type", "function")
                .put(
                    "function",
                    JSONObject().put("name", name).put("description", desc).put("parameters", schema)
                )
        }

        private fun params(vararg p: Pair<String, Triple<String, String, Boolean>>) =
            p.associate { it.first to it.second }

        private fun required(vararg names: String) = names
    }
}
