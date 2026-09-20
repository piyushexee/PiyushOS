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
    private val listening = mutableStateOf(false)
    private val crash = mutableStateOf<String?>(null)
    private val apiKey = mutableStateOf(load("nim_api_key", ""))

    private val connected: Boolean get() = apiKey.value.isNotBlank()

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var msgId = 0L
    private var brainBusy = false

    // Pura dimaag ab in-app hai (com.piyushos.app.ai.AgentBrain) — koi server nahi.
    // SocketClient abhi unused hai (repo me legacy ke liye rakha hai).

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

        // Agar pichli baar crash hua to POORA crash screen dikhao (plain views,
        // yeh kabhi crash nahi karega - Compose ke bina)
        val savedCrash = CrashLogger.last(this)
        if (savedCrash != null && intent.getStringExtra("retry") == null) {
            CrashRecovery.show(this, savedCrash) {
                val i = Intent(this, MainActivity::class.java).putExtra("retry", "1")
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                finish()
            }
            return
        }

        try {
            onCreateSafe()
        } catch (t: Throwable) {
            // Kuch bhi ho (Compose, TTS, koi library) - log save + crash screen
            CrashLogger.save(this, Thread.currentThread(), t)
            try {
                CrashRecovery.show(this, CrashLogger.last(this) ?: "Unknown crash: $t") {
                    val i = Intent(this, MainActivity::class.java).putExtra("retry", "1")
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                    finish()
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun onCreateSafe() {
        tts = try {
            TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN")
            }
        } catch (e: Throwable) {
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
                    connected = connected,
                    apiKey = apiKey.value, onApiKey = { apiKey.value = it },
                    messages = messages,
                    listening = listening.value,
                    crash = crash.value,
                    onCopyCrash = { copyCrashLog() },
                    onDismissCrash = {
                        CrashLogger.clear(this@MainActivity)
                        crash.value = null
                    },
                    onConnect = { connect() },
                    onDisconnect = {
                        apiKey.value = ""
                        save("nim_api_key", "")
                        addSystem("🔑 API key reset ho gayi.")
                    },
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

        addSystem("Namaste Piyush! 👋 Ek baar NVIDIA NIM ki API key daal ke 'DIMAAG ON KARO' dabao, phir Accessibility + Screenshot enable karo. Phir bolo: 'Ek PPT banao AI par'")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try { recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
    }

    // ---------------- dimaag on / chat (in-app brain) ----------------

    private fun connect() {
        val key = apiKey.value.trim()
        if (key.isEmpty()) {
            addSystem("⚠️ Pehle NVIDIA NIM API key daalo (nvapi- se shuru hoti hai)")
            return
        }
        save("nim_api_key", key)
        addSystem("🧠 Dimaag ON ho gaya! Ab bas bolo — PPT, Excel, 3D, kisi bhi app me kaam.")
    }

    private fun sendChat(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (!connected) {
            addSystem("⚠️ Pehle NVIDIA API key daalo aur 'DIMAAG ON KARO' dabao!")
            return
        }
        if (brainBusy) {
            addSystem("⏳ Pichla kaam abhi chal raha hai — thoda ruko...")
            return
        }
        addMsg(Role.USER, t)
        brainBusy = true
        val key = apiKey.value.trim()
        scope.launch(Dispatchers.IO) {
            try {
                com.piyushos.app.ai.AgentBrain(
                    context = this@MainActivity,
                    apiKey = key,
                    onProgress = { msg -> scope.launch { addSystem(msg) } },
                    onFile = { name, mime, file ->
                        scope.launch {
                            try {
                                val b64 = android.util.Base64.encodeToString(
                                    file.readBytes(), android.util.Base64.NO_WRAP
                                )
                                val uri = FileSaver.saveFile(this@MainActivity, name, mime, b64)
                                if (uri != null) {
                                    Notifier.notifyFile(this@MainActivity, uri, name, mime)
                                    addSystem("📄 $name save ho gaya → Downloads/PiyushOS folder")
                                } else {
                                    addSystem("⚠️ $name save nahi ho paya")
                                }
                            } catch (e: Exception) {
                                addSystem("⚠️ $name save me galti: ${e.message}")
                            }
                        }
                    },
                    onClipboard = { clip ->
                        scope.launch {
                            try {
                                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("PiyushOS", clip))
                                addSystem("📋 Text clipboard par copy ho gaya — jahan chaho wahan paste karo!")
                            } catch (_: Exception) {}
                        }
                    },
                    onFinal = { final ->
                        scope.launch {
                            addMsg(Role.AGENT, final)
                            if (final.length < 600) {
                                try {
                                    tts?.speak(final, TextToSpeech.QUEUE_FLUSH, null, "piyushos")
                                } catch (_: Exception) {}
                            }
                            brainBusy = false
                        }
                    },
                ).handleUser(t)
            } catch (e: Throwable) {
                scope.launch {
                    addMsg(Role.AGENT, "😵 AI se baat karne me problem aayi: ${e.message}")
                    brainBusy = false
                }
            }
        }
    }

    // ---------------- voice (STT) ----------------

    private fun startListening() {
        if (listening.value) {
            try { recognizer?.stopListening() } catch (_: Exception) {}
            listening.value = false
            return
        }
        if (!connected) {
            addSystem("⚠️ Pehle NVIDIA API key daalo aur 'DIMAAG ON KARO' dabao!")
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

    private fun load(key: String, def: String): String = try {
        getSharedPreferences("piyushos", MODE_PRIVATE).getString(key, def) ?: def
    } catch (t: Throwable) {
        // prefs corrupt ho toh reset karke default use karo (kabhi crash nahi)
        try {
            getSharedPreferences("piyushos", MODE_PRIVATE).edit().clear().commit()
        } catch (_: Throwable) {
        }
        def
    }

    private fun save(key: String, value: String) {
        try {
            getSharedPreferences("piyushos", MODE_PRIVATE).edit().putString(key, value).apply()
        } catch (_: Throwable) {
        }
    }
}
