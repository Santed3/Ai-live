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

    // Text-only history (screenshots are not stored, to save tokens).
    private val history = JSONArray().apply { put(system(CHAT_SYSTEM)) }

    /** Normal chat turn with memory of earlier turns. */
    fun ask(question: String, screenshotBase64: String?): String {
        val answer = complete(history, userMessage(question, screenshotBase64), 800)
        history.put(JSONObject().put("role", "user").put("content", question))
        history.put(JSONObject().put("role", "assistant").put("content", answer))
        while (history.length() > 11) history.remove(1)
        return answer
    }

    /** Stateless one-shot request used for quick answers. */
    fun askOnce(prompt: String, screenshotBase64: String?): String {
        val base = JSONArray().put(system(QUICK_SYSTEM))
        return complete(base, userMessage(prompt, screenshotBase64), 400)
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
        private const val CHAT_SYSTEM =
            "Du är en hjälpsam AI-assistent som ligger som en flytande bubbla ovanpå användarens skärm. " +
                "När en skärmbild följer med, titta på den och svara på användarens fråga om det som syns. " +
                "Svara kort och tydligt, på samma språk som användaren skriver. Använd ingen markdown."

        private const val QUICK_SYSTEM =
            "Du får en skärmbild. Din uppgift är att snabbt och korrekt besvara frågor som syns på den. " +
                "Om skärmbilden innehåller en eller flera frågor, uppgifter eller flervalsfrågor/quiz: " +
                "svara med enbart rätt svar för varje fråga, så kort som möjligt, " +
                "till exempel '1. B – Paris' eller '2. 42'. Behåll frågornas numrering. " +
                "Lägg bara till en kort förklaring (max en mening) om frågan är öppen eller svaret annars blir otydligt. " +
                "Räkna noggrant på matte- och logikfrågor innan du svarar. " +
                "Om ingen fråga syns, beskriv med en mening vad som visas. " +
                "Svara på samma språk som frågorna. Använd ingen markdown."
    }
}
