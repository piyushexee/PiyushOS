package com.piyushos.app

import android.os.Build
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SocketClient(
    private val onStatus: (Boolean) -> Unit,
    private val onMessage: (JSONObject) -> Unit,
    private val screenInfo: () -> Pair<Int, Int>,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    @Volatile private var shouldReconnect = false
    @Volatile private var host = ""
    @Volatile private var port = 0
    @Volatile private var token = ""

    fun connect(host: String, port: Int, token: String) {
        this.host = host
        this.port = port
        this.token = token
        shouldReconnect = true
        doConnect()
    }

    private fun doConnect() {
        if (host.isEmpty()) return
        val url = "ws://$host:$port/ws?token=$token"
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                main.post { onStatus(true) }
                val (w, h) = screenInfo()
                webSocket.send(
                    JSONObject()
                        .put("type", "hello")
                        .put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
                        .put("screen_w", w)
                        .put("screen_h", h)
                        .toString()
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val j = JSONObject(text)
                    main.post { onMessage(j) }
                } catch (_: Exception) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { onStatus(false) }
                if (shouldReconnect) main.postDelayed({ doConnect() }, 4000)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post { onStatus(false) }
                if (shouldReconnect) main.postDelayed({ doConnect() }, 4000)
            }
        })
    }

    fun send(obj: JSONObject) {
        ws?.send(obj.toString())
    }

    fun sendCmdAck(id: String, ok: Boolean, data: JSONObject) {
        send(JSONObject().put("type", "cmd_ack").put("id", id).put("ok", ok).put("data", data))
    }

    fun close() {
        shouldReconnect = false
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        ws = null
        main.post { onStatus(false) }
    }
}
