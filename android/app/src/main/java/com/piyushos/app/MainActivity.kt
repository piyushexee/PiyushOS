package com.piyushos.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.piyushos.app.accessibility.PhoneControllerService
import com.piyushos.app.files.FileSaver
import com.piyushos.app.files.Notifier
import com.piyushos.app.media.ProjectionService
import com.piyushos.app.media.ScreenOcr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

enum class Role { USER, AGENT, SYSTEM }
data class ChatMsg(val id: Long, val role: Role, val text: String)

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val messages = mutableStateListOf<ChatMsg>()
    private val connected = mutableStateOf(false)
    private val listening = mutableStateOf(false)
    private val crash = mutableStateOf<String?>(null)
    private val host = mutableStateOf(load("host", "127.0.0.1"))
    private val port = mutableStateOf(load("port", "8787"))
    private val token = mutableStateOf(load("token", "piyush123"))

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var msgId = 0L

    private val socket = SocketClient(
        onStatus = { connected.value = it },
        onMessage = { onSocketMessage(it) },
        screenInfo = {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        },
    )

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val intent = Intent(this, ProjectionService::class.java)
                intent.putExtra(ProjectionService.EXTRA_DATA, result.data)
                intent.putExtra(ProjectionService.EXTRA_CODE, result.resultCode)
                ContextCompat.startForegroundService(this, intent)
                addSystem("📸 Screenshot permission mil gayi!")
            } else {
                addSystem("⚠️ Screenshot permission nahi mili. Jab zaroorat ho tab 'Enable Screenshots' dobara dabao.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SABSE PEHLE: crash logger (taaki koi bhi crash save ho jaye)
        CrashLogger.install(this)

        tts = try {
            TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN")
            }
        } catch (e: Exception) {
            addSystem("⚠️ TTS engine nahi mila (voice replies nahi honge): ${e.message}")
            null
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ChatScreen(
                    connected = connected.value,
                    host = host.value, onHost = { host.value = it },
                    port = port.value, onPort = { port.value = it },
                    token = token.value, onToken = { token.value = it },
                    messages = messages,
                    listening = listening.value,
                    crash = crash.value,
                    onCopyCrash = { copyCrashLog() },
                    onDismissCrash = {
                        CrashLogger.clear(this@MainActivity)
                        crash.value = null
                    },
                    onConnect = { connect() },
                    onDisconnect = { socket.close() },
                    onSend = { sendChat(it) },
                    onMic = { startListening() },
                    onEnableAccessibility = {
                        addSystem("Settings khul gayi → 'Accessibility' → 'PiyushOS' → ON karo, phir wapas aana.")
                        try {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (e: Exception) {
                            addSystem("⚠️ Accessibility settings nahi khule: ${e.message}")
                        }
                    },
                    onEnableScreenshots = { requestScreenshots() },
                )
            }
        }
        // pichla crash check karo
        crash.value = CrashLogger.last(this)

        addSystem("Namaste Piyush! 👋 Pehle CONNECT dabao (server chal raha hona chahiye), phir Accessibility + Screenshot enable karo. Phir bolo: 'Ek PPT banao AI par'")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        socket.close()
        try { recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
    }

    // ---------------- connect / chat ----------------

    private fun connect() {
        save("host", host.value.trim())
        save("port", port.value.trim())
        save("token", token.value.trim())
        val p = port.value.toIntOrNull()
        if (p == null) {
            addSystem("⚠️ Port number sahi nahi hai (e.g. 8787)")
            return
        }
        addSystem("Connecting... ${host.value.trim()}:$p")
        socket.connect(host.value.trim(), p, token.value.trim())
    }

    private fun sendChat(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (!connected.value) {
            addSystem("⚠️ Pehle connect karo!")
            return
        }
        addMsg(Role.USER, t)
        socket.send(JSONObject().put("type", "chat").put("text", t))
    }

    // ---------------- socket messages ----------------

    private fun onSocketMessage(msg: JSONObject) {
        when (msg.optString("type")) {
            "chat" -> if (msg.optBoolean("done")) addMsg(Role.AGENT, msg.optString("message"))
            "progress" -> addMsg(Role.SYSTEM, msg.optString("text"))
            "tts" -> {
                val t = msg.optString("text")
                tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "piyushos")
            }
            "clipboard" -> {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PiyushOS", msg.optString("text")))
                addSystem("📋 Text clipboard par copy ho gaya — jahan chaho wahan paste karo!")
            }
            "file" -> handleFile(msg)
            "cmd" -> handleCmd(msg)
            "hello_ack" -> addSystem("✅ Connect ho gaya (${msg.optString("device_id")})")
            "error" -> addSystem("⚠️ ${msg.optString("message")}")
        }
    }

    private fun handleFile(msg: JSONObject) {
        val name = msg.optString("name")
        val mime = msg.optString("mime", "application/octet-stream")
        val b64 = msg.optString("b64")
        scope.launch {
            val uri = FileSaver.saveFile(this@MainActivity, name, mime, b64)
            addSystem("📄 $name save ho gaya → Downloads/PiyushOS folder")
            if (uri != null) Notifier.notifyFile(this@MainActivity, uri, name, mime)
            else addSystem("⚠️ $name save nahi ho paya")
        }
    }

    private fun handleCmd(msg: JSONObject) {
        val id = msg.optString("id")
        val action = msg.optString("action")
        val payload = msg.optJSONObject("payload") ?: JSONObject()
        val svc = PhoneControllerService.instance
        if (svc == null) {
            socket.sendCmdAck(
                id, false,
                JSONObject().put("error", "Accessibility service on nahi hai — app me 'Enable Accessibility' dabao aur list me PiyushOS ko ON karo.")
            )
            return
        }
        scope.launch {
            val data = JSONObject()
            var ok = true
            try {
                when (action) {
                    "open_app" -> {
                        val app = payload.optString("app")
                        if (svc.openApp(app)) data.put("opened", app)
                        else {
                            ok = false
                            data.put("error", "App '$app' phone par nahi mili")
                        }
                    }
                    "tap" -> svc.tap(payload.optInt("x"), payload.optInt("y"))
                    "double_tap" -> svc.doubleTap(payload.optInt("x"), payload.optInt("y"))
                    "long_press" -> svc.longPress(payload.optInt("x"), payload.optInt("y"))
                    "swipe" -> svc.swipe(payload.optString("direction", "up"), payload.optInt("amount", 50))
                    "input_text" -> {
                        val text = payload.optString("text")
                        if (!svc.typeText(text)) {
                            ok = false
                            data.put("error", "Text box focus nahi mila — pehle input field par tap karo")
                        }
                    }
                    "back" -> svc.back()
                    "home" -> svc.home()
                    "recent_apps" -> svc.recentApps()
                    "open_notifications" -> svc.openNotifications()
                    "screenshot" -> {
                        val bmp = ProjectionService.capture()
                        if (bmp == null) {
                            ok = false
                            data.put("error", "Screenshot permission nahi hai — app me 'Enable Screenshots' dabao")
                        } else {
                            data.put(
                                "image_b64",
                                android.util.Base64.encodeToString(FileSaver.pngBytes(bmp), android.util.Base64.NO_WRAP)
                            ).put("width", bmp.width).put("height", bmp.height)
                        }
                    }
                    "screen" -> {
                        // UI tree + screenshot (+ OCR agar tree sparse hai) — GUI agent ke liye
                        val tree = svc.screenTree()
                        data.put("tree", tree)
                        val bmp = ProjectionService.capture()
                        if (bmp != null) {
                            data.put(
                                "image_b64",
                                android.util.Base64.encodeToString(FileSaver.pngBytes(bmp), android.util.Base64.NO_WRAP)
                            ).put("width", bmp.width).put("height", bmp.height)
                            val textNodes = Regex("\"t\":").findAll(tree).count()
                            if (textNodes < 6) {
                                val ocr = ScreenOcr.ocr(bmp)
                                if (ocr.isNotEmpty()) data.put("ocr", ocr)
                            }
                        } else {
                            data.put("no_screenshot", true)
                        }
                    }
                    "wait" -> Thread.sleep((payload.optDouble("seconds", 1.0) * 1000).toLong())
                    else -> {
                        ok = false
                        data.put("error", "Unknown action: $action")
                    }
                }
            } catch (e: Exception) {
                ok = false
                data.put("error", e.message ?: "galti")
            }
            socket.sendCmdAck(id, ok, data)
        }
    }

    // ---------------- voice (STT) ----------------

    private fun startListening() {
        if (listening.value) {
            try { recognizer?.stopListening() } catch (_: Exception) {}
            listening.value = false
            return
        }
        if (!connected.value) {
            addSystem("⚠️ Pehle connect karo!")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        listening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) sendChat(text)
                        else addSystem("😅 Kuch sunai nahi diya — phir se bolo?")
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onError(error: Int) {
                        listening.value = false
                        addSystem("⚠️ Voice me galti (code $error) — type kar ke bhi de sakte ho")
                    }
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { listening.value = false }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening.value = true
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            listening.value = false
            addSystem("⚠️ Mic nahi khula: ${e.message}")
        }
    }

    private fun requestScreenshots() {
        if (ProjectionService.ready) {
            addSystem("📸 Screenshots already ready hai!")
            return
        }
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    // ---------------- helpers ----------------

    private fun copyCrashLog() {
        val log = CrashLogger.last(this) ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PiyushOS Crash Log", log))
        addSystem("📋 Crash log clipboard par copy ho gaya — paste karke bhej do!")
    }

    private fun addMsg(role: Role, text: String) {
        messages.add(ChatMsg(++msgId, role, text))
    }

    private fun addSystem(text: String) = addMsg(Role.SYSTEM, text)

    private fun load(key: String, def: String): String =
        getSharedPreferences("piyushos", MODE_PRIVATE).getString(key, def) ?: def

    private fun save(key: String, value: String) =
        getSharedPreferences("piyushos", MODE_PRIVATE).edit().putString(key, value).apply()
}
