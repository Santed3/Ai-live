package com.example.screenbubble

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal OpenAI chat client with vision support.
 * Methods block, so call them from a background thread.
 */
class OpenAiClient(
    private val apiKey: String,
    private val model: String = "gpt-4o"
) {
    private val http = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Stateless one-shot request used for quick answers. */
    fun askOnce(prompt: String, screenshotBase64: String?): String {
        val base = JSONArray().put(system(QUICK_SYSTEM))
        return complete(base, userMessage(prompt, screenshotBase64), 120)
    }

    private fun system(text: String) = JSONObject().put("role", "system").put("content", text)

    private fun userMessage(text: String, screenshotBase64: String?): JSONObject {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", text))
        if (screenshotBase64 != null) {
            content.put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$screenshotBase64")
                    )
            )
        }
        return JSONObject().put("role", "user").put("content", content)
    }

    private fun complete(base: JSONArray, user: JSONObject, maxTokens: Int): String {
        val messages = JSONArray()
        for (i in 0 until base.length()) messages.put(base.get(i))
        messages.put(user)

        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", maxTokens)
            .toString()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = try {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                } catch (e: Exception) {
                    null
                }
                throw RuntimeException("OpenAI ${response.code}: ${msg ?: text.take(200)}")
            }
            return JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }

    companion object {
        private const val QUICK_SYSTEM =
            "Du får text från en skärm (ibland även en bild). Hitta frågan eller frågorna och svara med ENDAST rätt svar, så kort som möjligt. " +
                "Flervalsfråga: bara siffran eller bokstaven som anges i alternativen, till exempel 'B' eller '3'. " +
                "Flera frågor: 'a) C b) A' eller '1B 2D 3A' i samma format som frågorna. " +
                "Om alternativen saknar bokstav/siffra: skriv alternativets text. " +
                "Fyll-i/matte: bara resultatet. Aldrig förklaring, aldrig hela meningar, ingen markdown. " +
                "Tänk noggrant innan du svarar. Om ingen fråga syns, svara '–'."
    }
}
