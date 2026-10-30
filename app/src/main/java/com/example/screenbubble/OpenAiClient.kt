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
    // One shared client so TLS connections are reused: every call after the first is much faster.
    private val http get() = shared

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

    /**
     * Streaming chat: forwards text deltas while the model talks and accumulates tool calls.
     * Returns the same message shape as [chat].
     */
    fun chatStream(
        messages: JSONArray,
        tools: JSONArray?,
        maxTokens: Int,
        onDelta: (String) -> Unit,
        onTool: () -> Unit
    ): JSONObject {
        val body = JSONObject().put("model", model).put("messages", messages).put("max_tokens", maxTokens).put("stream", true)
        if (tools != null && tools.length() > 0) body.put("tools", tools).put("tool_choice", "auto")
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                val msg = try { JSONObject(text).optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
                throw RuntimeException("OpenAI ${response.code}: ${msg ?: text.take(200)}")
            }
            val src = response.body!!.source()
            val text = StringBuilder()
            val ids = java.util.TreeMap<Int, String>()
            val names = java.util.TreeMap<Int, StringBuilder>()
            val args = java.util.TreeMap<Int, StringBuilder>()
            var toolsSeen = false
            while (true) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.substring(5).trim()
                if (data == "[DONE]") break
                val j = try { JSONObject(data) } catch (e: Exception) { continue }
                val d = j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: continue
                if (d.has("content") && !d.isNull("content")) {
                    val t = d.getString("content")
                    if (t.isNotEmpty()) { text.append(t); if (!toolsSeen) onDelta(t) }
                }
                val tcs = d.optJSONArray("tool_calls")
                if (tcs != null) for (k in 0 until tcs.length()) {
                    if (!toolsSeen) { toolsSeen = true; onTool() }
                    val tc = tcs.getJSONObject(k)
                    val idx = tc.optInt("index", 0)
                    if (tc.has("id") && !tc.isNull("id")) ids[idx] = tc.getString("id")
                    val fn = tc.optJSONObject("function")
                    if (fn != null) {
                        if (fn.has("name") && !fn.isNull("name")) names.getOrPut(idx) { StringBuilder() }.append(fn.getString("name"))
                        if (fn.has("arguments") && !fn.isNull("arguments")) args.getOrPut(idx) { StringBuilder() }.append(fn.getString("arguments"))
                    }
                }
            }
            val msg = JSONObject().put("role", "assistant").put("content", text.toString())
            if (names.isNotEmpty()) {
                val calls = JSONArray()
                for (idx in names.keys) {
                    calls.put(
                        JSONObject().put("id", ids[idx] ?: "call_$idx").put("type", "function").put(
                            "function", JSONObject().put("name", names[idx].toString()).put("arguments", args[idx]?.toString() ?: "{}")
                        )
                    )
                }
                msg.put("tool_calls", calls)
            }
            return msg
        }
    }

    /** Extracts lasting facts about the user from an exchange (use with the mini model). */
    fun extractFacts(user: String, answer: String): List<String> {
        val messages = JSONArray()
            .put(
                JSONObject().put("role", "system").put(
                    "content",
                    "Du plockar ut VARAKTIGA fakta om användaren ur ett utbyte med en assistent: personer användaren skriver till (namn och relation), " +
                        "önskad ton eller språk per person, vanor och preferenser. Skriv varje fakta som en kort svensk mening. " +
                        "Ta ALDRIG med lösenord, kortnummer, personnummer, hälsa, ekonomi eller annat känsligt, och inget tillfälligt. " +
                        "Svara med JSON {\"facts\":[\"...\"]}. Inget nytt: {\"facts\":[]}."
                )
            )
            .put(JSONObject().put("role", "user").put("content", "Användare: ${user.take(800)}\nAssistent: ${answer.take(600)}"))
        val raw = contentOf(chat(messages, null, 300, json = true, temperature = 0.0))
        val arr = JSONObject(raw).optJSONArray("facts") ?: return emptyList()
        return (0 until minOf(arr.length(), 4)).map { arr.optString(it) }
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
        var raw = contentOf(chat(messages, null, 900, json = true, temperature = 0.0))
        if (unsure(raw)) {
            // Second, critical pass only when the first answer was uncertain.
            messages.put(JSONObject().put("role", "assistant").put("content", raw))
            messages.put(JSONObject().put("role", "user").put("content",
                "Granska ditt svar kritiskt fråga för fråga mot bilden. Räkna om och rätta det som är fel. Svara med samma JSON-format."))
            try { raw = contentOf(chat(messages, null, 1100, json = true, temperature = 0.0)) } catch (_: Exception) {}
        }
        return format(raw)
    }

    private fun unsure(raw: String): Boolean = try {
        val arr = JSONObject(raw).optJSONArray("answers")
        var low = false
        if (arr != null) for (i in 0 until arr.length()) if (arr.getJSONObject(i).optDouble("confidence", 1.0) < 0.85) low = true
        low
    } catch (_: Exception) { false }

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

    /** Fast reply suggestions in the user's own style. Returns an empty list when no reply is expected. */
    fun suggestReplies(screenTail: String, styleBlock: String, kind: String = "chatt"): List<String> {\n        val system = "Du föreslår svar åt användaren i en $kind. Du får texten på skärmen (uppifrån och ned, den innehåller också appens knappar och rubriker). " +
            "Avgör om det SENASTE meddelandet i konversationen kommer från den andra personen och väntar på svar. " +
            "Om det senaste meddelandet är användarens eget, om det inte är en konversation eller om inget svar behövs: needs_reply=false. För mejl ska svaren passa mejlets formalitetsnivå och gärna ha lämplig hälsning när det passar. " +
            "Annars ge exakt 3 korta förslag på svar med olika inriktning, till exempel kort och enkelt, varmare, och med en motfråga. " +
            "Skriv dem EXAKT som användaren skriver, med samma längd, ton, slang, förkortningar, emojis och skiljetecken.$styleBlock\n" +
            "Samma språk som konversationen. Högst 140 tecken per förslag. " +
            "Svara bara med JSON: {\"needs_reply\":true,\"suggestions\":[\"...\",\"...\",\"...\"]}"
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", "Text på skärmen:\n$screenTail"))
        val raw = contentOf(chat(messages, null, 170, json = true, temperature = 0.7))
        val j = JSONObject(raw)
        if (!j.optBoolean("needs_reply", false)) return emptyList()
        val arr = j.optJSONArray("suggestions") ?: return emptyList()
        return (0 until minOf(arr.length(), 3)).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }
    }

    /** Improves an in-progress message without changing its intent; returned text replaces the draft only after a tap. */
    fun suggestDraft(draft: String, screenContext: String, styleBlock: String, kind: String): List<String> {
        val system = "Du hjälper användaren formulera ett meddelande som hen redan håller på att skriva i en $kind. " +
            "Läs sammanhanget och utkastet. Föreslå EN förbättrad, naturlig version som behåller exakt samma avsikt och fakta. " +
            "Om utkastet är ofärdigt får du slutföra meningen på ett rimligt sätt utan att hitta på löften, fakta eller detaljer. " +
            "Matcha mottagarens ton, formalitetsnivå och språk. Härma användarens skrivstil från stilproven.$styleBlock " +
            "Svara ENDAST med den färdiga meddelandetexten, utan citattecken, rubrik eller förklaring. " +
            "Om utkastet är för kort eller inte ser ut som ett meddelande, returnera exakt [SKIP]."
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", "Kontext från konversationen:\n$screenContext\n\nAnvändarens pågående utkast:\n$draft"))
        val result = contentOf(chat(messages, null, 220, temperature = 0.35)).trim()
        if (result.isBlank() || result == "[SKIP]" || result.equals(draft.trim(), true)) return emptyList()
        return listOf(result.take(500))
    }

    /** Summarises how the user writes, from samples of their own sent messages. */
    fun styleProfile(samples: List<String>): String {
        val messages = JSONArray()
            .put(
                JSONObject().put("role", "system").put(
                    "content",
                    "Du analyserar hur en person skriver i chattar. Beskriv kort (max 450 tecken, på svenska, som torra punkter i en mening vardera) " +
                        "hur användaren skriver: vilket språk, typisk längd, ton, hälsningar och avslut, förkortningar och slang, emojis, " +
                        "skiljetecken, stor/liten bokstav, och vanliga fraser. Hitta inte på något som inte syns i proven."
                )
            )
            .put(JSONObject().put("role", "user").put("content", samples.joinToString("\n") { "- " + it.replace("\n", " ") }))
        return contentOf(chat(messages, null, 260, temperature = 0.2))
    }

    companion object {
        private val shared: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(75, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(4, 5, TimeUnit.MINUTES))
            .build()

        private const val QUICK_SYSTEM =
            "Du löser frågor, quiz och uppgifter som syns på en telefonskärm. Du får en skärmbild och ibland texten från skärmen. " +
                "Bilden visar vad som faktiskt syns (inklusive foton, diagram och bildalternativ); texten hjälper dig läsa exakt.\n" +
                "Gör så här: 1) Hitta alla frågor och deras svarsalternativ. " +
                "2) Gäller frågan en bild, eller är alternativen bilder: titta noga på bilden/bilderna och jämför dem med frågan innan du väljer. " +
                "3) Tänk igenom varje fråga noggrant och räkna matte steg för steg i fältet \"reasoning\". " +
                "4) Välj det alternativ som är rätt.\n" +
                "Svara ENDAST med JSON: {\"reasoning\":\"kort tankegång\",\"answers\":[{\"q\":\"frågans nummer, t.ex. 1\"," +
                "\"option\":\"alternativets bokstav eller siffra, t.ex. B eller 3\",\"answer\":\"svaret i klartext, kort, t.ex. Paris\",\"confidence\":0.0-1.0}]}\n" +
                "Regler: Ett objekt per fråga, i den ordning de står. \"option\" ska ALLTID fyllas i när frågan har alternativ. " +
                "Saknar alternativen bokstav/siffra: räkna dem uppifrån och ned (eller vänster till höger) som 1, 2, 3 och skriv numret. " +
                "Vid bildalternativ: beskriv den rätta bilden med några ord i \"answer\". " +
                "Flera rätta alternativ: skriv alla i \"option\", t.ex. \"A, C\". " +
                "Fråga utan alternativ: lämna \"option\" tom och skriv svaret i \"answer\". " +
                "Om ingen fråga syns: answers är en tom lista. Svara på samma språk som frågorna."
    }
}
