package com.example.ioskeyboard

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Rewrites text using the Anthropic (Claude) Messages API. Call from a background thread. */
object AiClient {

    private val instructions = mapOf(
        "professional" to "Rewrite the text in a professional, polished tone suitable for work or business communication. Keep the meaning.",
        "casual" to "Rewrite the text in a relaxed, friendly, casual tone like a message to a friend. Keep the meaning.",
        "longer" to "Expand the text into a longer, fuller version with more detail and smoother flow. Keep the meaning and do not invent facts.",
        "funnier" to "Rewrite the text to be funnier, with light, good-natured humor. Keep the meaning.",
        "shorter" to "Rewrite the text to be shorter and more concise. Keep the meaning.",
        "fix" to "Fix spelling, grammar and punctuation only. Keep the wording and tone as close as possible to the original."
    )

    fun rewrite(apiKey: String, model: String, text: String, mode: String): Result<String> = runCatching {
        val system = "You are a writing assistant inside a phone keyboard. " +
                (instructions[mode] ?: instructions.getValue("fix")) +
                " Reply in the same language as the input. Output ONLY the rewritten text: " +
                "no quotes, no preamble, no explanations."

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))

        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(resp).getJSONObject("error").getString("message") }.getOrNull()
            error(msg ?: "HTTP $code")
        }

        val content = JSONObject(resp).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.getString("text"))
        }
        sb.toString().trim().ifEmpty { error("Empty response") }
    }
}
