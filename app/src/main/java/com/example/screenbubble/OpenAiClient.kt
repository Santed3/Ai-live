package com.example.screenbubble

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Minimal OpenAI client (chat, tools, vision, web search). Blocks: call from a background thread. */
class OpenAiClient(
    private val apiKey: String,
    private val model: String = "gpt-4o"
) {
    private val http = OkHttpClient.Builder()
        .callTimeout(75, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .build()

    private fun post(body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
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
        }
    }

    /** User message content: text plus optional screenshot. */
    fun content(text: String, shotBase64: String?): JSONArray {
        val c = JSONArray().put(JSONObject().put("type", "text").put("text", text))
        if (shotBase64 != null) {
            c.put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject().put("url", "data:image/jpeg;base64,$shotBase64").put("detail", "high")
                )
            )
        }
        return c
    }

    fun chat(
        messages: JSONArray,
        tools: JSONArray? = null,
        maxTokens: Int = 900,
        json: Boolean = false,
        temperature: Double? = null
    ): JSONObject {
        val body = JSONObject().put("model", model).put("messages", messages).put("max_tokens", maxTokens)
        if (tools != null && tools.length() > 0) body.put("tools", tools).put("tool_choice", "auto")
        if (json) body.put("response_format", JSONObject().put("type", "json_object"))
        if (temperature != null) body.put("temperature", temperature)
        return post(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
    }

    private fun contentOf(msg: JSONObject): String = if (msg.isNull("content")) "" else msg.getString("content").trim()

    /** Plain text completion. */
    fun text(system: String, user: JSONArray, maxTokens: Int = 700): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        return contentOf(chat(messages, null, maxTokens))
    }

    fun textSimple(system: String, user: String, maxTokens: Int = 700): String =
        text(system, JSONArray().put(JSONObject().put("type", "text").put("text", user)), maxTokens)

    /** Web search through OpenAI's search model. Returns answer plus sources. */
    fun search(query: String): String {
        val body = JSONObject()
            .put("model", "gpt-4o-search-preview")
            .put("web_search_options", JSONObject())
            .put(
                "messages", JSONArray()
                    .put(
                        JSONObject().put("role", "system").put(
                            "content",
                            "Sök på webben och svara kort och korrekt på användarens språk. Ge konkreta fakta och jämför källor när det behövs."
                        )
                    )
                    .put(JSONObject().put("role", "user").put("content", query))
            )
        val msg = post(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val answer = contentOf(msg)
        val sources = LinkedHashMap<String, String>()
        val ann = msg.optJSONArray("annotations")
        if (ann != null) {
            for (i in 0 until ann.length()) {
                val u = ann.getJSONObject(i).optJSONObject("url_citation") ?: continue
                val url = u.optString("url")
                if (url.isNotEmpty() && !sources.containsKey(url)) sources[url] = u.optString("title", url)
            }
        }
        val src = sources.entries.take(4).joinToString("\n") { "- ${it.value} (${it.key})" }
        return if (src.isEmpty()) answer else "$answer\n\nKällor:\n$src"
    }

    /**
     * Quick answer: returns only the correct answers, one per line, e.g. "1: B – Paris".
     */
    fun askOnce(screenText: String, screenshotBase64: String?): String {
        val prompt = "Lös frågorna som syns på skärmen. Svara med JSON enligt instruktionen.\n" +
            if (screenText.isNotBlank()) "Text på skärmen:\n$screenText" else "(Ingen läsbar text, använd bilden.)"
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", QUICK_SYSTEM))
            .put(JSONObject().put("role", "user").put("content", content(prompt, screenshotBase64)))
        val raw = contentOf(chat(messages, null, 900, json = true, temperature = 0.0))
        return format(raw)
    }

    private fun format(raw: String): String {
        return try {
            val arr = JSONObject(raw).optJSONArray("answers") ?: return "–"
            if (arr.length() == 0) return "–"
            val lines = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val q = o.optString("q").trim()
                val opt = o.optString("option").trim()
                val ans = o.optString("answer").trim()
                val core = when {
                    opt.isNotEmpty() && ans.isNotEmpty() && !ans.equals(opt, true) -> "$opt – $ans"
                    opt.isNotEmpty() -> opt
                    else -> ans
                }
                lines.add(if (q.isNotEmpty()) "$q: $core" else core)
            }
            lines.joinToString("\n")
        } catch (e: Exception) {
            raw.take(200)
        }
    }

    companion object {
        private const val QUICK_SYSTEM =
            "Du löser frågor, quiz och uppgifter som syns på en telefonskärm. Du får en skärmbild och ibland texten från skärmen. " +
                "Bilden visar vad som faktiskt syns (inklusive foton, diagram och bildalternativ); texten hjälper dig läsa exakt.\n" +
                "Gör så här: 1) Hitta alla frågor och deras svarsalternativ. " +
                "2) Gäller frågan en bild, eller är alternativen bilder: titta noga på bilden/bilderna och jämför dem med frågan innan du väljer. " +
                "3) Tänk igenom varje fråga noggrant och räkna matte steg för steg i fältet \"reasoning\". " +
                "4) Välj det alternativ som är rätt.\n" +
                "Svara ENDAST med JSON: {\"reasoning\":\"kort tankegång\",\"answers\":[{\"q\":\"frågans nummer, t.ex. 1\"," +
                "\"option\":\"alternativets bokstav eller siffra, t.ex. B eller 3\",\"answer\":\"svaret i klartext, kort, t.ex. Paris\"}]}\n" +
                "Regler: Ett objekt per fråga, i den ordning de står. \"option\" ska ALLTID fyllas i när frågan har alternativ. " +
                "Saknar alternativen bokstav/siffra: räkna dem uppifrån och ned (eller vänster till höger) som 1, 2, 3 och skriv numret. " +
                "Vid bildalternativ: beskriv den rätta bilden med några ord i \"answer\". " +
                "Flera rätta alternativ: skriv alla i \"option\", t.ex. \"A, C\". " +
                "Fråga utan alternativ: lämna \"option\" tom och skriv svaret i \"answer\". " +
                "Om ingen fråga syns: answers är en tom lista. Svara på samma språk som frågorna."
    }
}
