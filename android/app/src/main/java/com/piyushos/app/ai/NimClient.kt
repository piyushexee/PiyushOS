package com.piyushos.app.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * NVIDIA NIM (NIM) LLM client - OpenAI-compatible chat completions API.
 * Model: nvidia/nemotron-3-ultra-550b-a55b (user-spec).
 * In-app Brain ke liye - koi server/Termux nahi chahiye.
 */
object NimClient {

    const val MODEL = "nvidia/nemotron-3-ultra-550b-a55b"
    const val BASE_URL = "https://integrate.api.nvidia.com/v1/chat/completions"

    data class ToolCall(val id: String, val name: String, val args: JSONObject)
    data class Result(val content: String?, val toolCalls: List<ToolCall>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    /** Ek LLM turn. messages = OpenAI format JSONArray. tools optional. */
    fun chat(apiKey: String, messages: JSONArray, tools: JSONArray? = null): Result {
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
        if (tools != null && tools.length() > 0) {
            body.put("tools", tools)
            body.put("tool_choice", "auto")
        }
        val req = Request.Builder()
            .url(BASE_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", "piyushos-inapp/1.0")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw LlmException("NIM API error ${resp.code}: ${text.take(300)}")
            }
            val j = JSONObject(text)
            val msg = j.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val content: String? = if (msg.isNull("content")) null else msg.optString("content", null)
            val tcs = mutableListOf<ToolCall>()
            val arr = msg.optJSONArray("tool_calls")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val tc = arr.getJSONObject(i)
                    val fn = tc.getJSONObject("function")
                    val rawArgs = fn.optString("arguments", "{}")
                    val args: JSONObject = try {
                        JSONObject(rawArgs)
                    } catch (e: Exception) {
                        JSONObject().put("raw", rawArgs)
                    }
                    tcs.add(ToolCall(tc.optString("id", "call_$i"), fn.optString("name"), args))
                }
            }
            return Result(content, tcs)
        }
    }

    /** Simple text-in/text-out (GUI sub-loop ke liye). */
    fun raw(apiKey: String, messages: JSONArray): String {
        val r = chat(apiKey, messages, null)
        return r.content ?: ""
    }

    /** Base64 screenshot + text -> multimodal message (user role). */
    fun visionMessage(text: String, imageB64: String): JSONObject {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", text))
            .put(
                JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/png;base64,$imageB64"))
            )
        return JSONObject().put("role", "user").put("content", content)
    }

    class LlmException(msg: String) : Exception(msg)
}
