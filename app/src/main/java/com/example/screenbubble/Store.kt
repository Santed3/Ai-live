package com.example.screenbubble

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Small persistent store: history, archive of all conversations, memory, style samples, custom commands, mode. */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- context history (last messages)

    fun history(c: Context): JSONArray = try {
        JSONArray(p(c).getString("history", "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    fun addHistory(c: Context, role: String, content: String) {
        val h = history(c)
        h.put(JSONObject().put("role", role).put("content", content.take(2000)))
        val out = JSONArray()
        for (i in maxOf(0, h.length() - 24) until h.length()) out.put(h.get(i))
        p(c).edit().putString("history", out.toString()).apply()
        archiveAppend(c, role, content)
    }

    /** Starts a new conversation: clears the model context and detaches from the archived one. */
    fun clearHistory(c: Context) {
        p(c).edit().putString("history", "[]").putString("conv", "").apply()
    }

    // ---------------------------------------------------------------- archive of all conversations

    private fun archiveFile(c: Context) = File(c.filesDir, "archive.json")

    @Synchronized
    private fun readArchive(c: Context): JSONArray = try {
        JSONArray(archiveFile(c).readText())
    } catch (e: Exception) {
        JSONArray()
    }

    @Synchronized
    private fun writeArchive(c: Context, a: JSONArray) {
        try { archiveFile(c).writeText(a.toString()) } catch (_: Exception) {}
    }

    private fun titleOf(t: String): String {
        val s = t.replace(Regex("\\s+"), " ").trim()
        return if (s.isEmpty()) "Ny konversation" else if (s.length > 48) s.take(47).trimEnd() + "…" else s
    }

    @Synchronized
    fun archiveAppend(c: Context, role: String, content: String) {
        val arr = readArchive(c)
        val now = System.currentTimeMillis()
        var id = p(c).getString("conv", "") ?: ""
        var existing: JSONObject? = null
        val others = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (id.isNotEmpty() && o.optString("id") == id) existing = o else others.put(o)
        }
        val conv: JSONObject
        if (existing != null) {
            conv = existing
        } else {
            id = now.toString() + (1000..9999).random()
            conv = JSONObject().put("id", id).put("title", "Ny konversation").put("ts", now).put("messages", JSONArray())
            p(c).edit().putString("conv", id).apply()
        }
        if (conv.optString("title") == "Ny konversation" && role == "user") conv.put("title", titleOf(content))
        val msgs = conv.getJSONArray("messages")
        msgs.put(JSONObject().put("role", role).put("content", content.take(3000)).put("ts", now))
        val keep = JSONArray()
        for (i in maxOf(0, msgs.length() - 80) until msgs.length()) keep.put(msgs.get(i))
        conv.put("messages", keep).put("ts", now)
        others.put(conv)
        val trimmed = JSONArray()
        for (i in maxOf(0, others.length() - 150) until others.length()) trimmed.put(others.get(i))
        writeArchive(c, trimmed)
    }

    class Hit(val id: String, val title: String, val ts: Long, val count: Int, val snippet: String)

    /** Every word must occur in the title or somewhere in the conversation. Newest first. */
    fun archiveSearch(c: Context, q: String): List<Hit> {
        val words = q.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val arr = readArchive(c)
        val out = ArrayList<Hit>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val msgs = o.optJSONArray("messages") ?: continue
            val texts = (0 until msgs.length()).map { msgs.getJSONObject(it).optString("content") }
            val hay = (o.optString("title") + "\n" + texts.joinToString("\n")).lowercase(Locale.ROOT)
            if (!words.all { hay.contains(it) }) continue
            var snippet = ""
            if (words.isNotEmpty()) {
                val t = texts.firstOrNull { it.lowercase(Locale.ROOT).contains(words[0]) }
                if (t != null) snippet = snippetOf(t, words[0])
            }
            out.add(Hit(o.optString("id"), o.optString("title"), o.optLong("ts"), texts.size, snippet))
        }
        out.sortByDescending { it.ts }
        return out.take(100)
    }

    private fun snippetOf(text: String, word: String): String {
        val t = text.replace(Regex("\\s+"), " ")
        val i = t.lowercase(Locale.ROOT).indexOf(word)
        if (i < 0) return t.take(80)
        val a = maxOf(0, i - 30)
        val b = minOf(t.length, i + word.length + 50)
        return (if (a > 0) "…" else "") + t.substring(a, b) + (if (b < t.length) "…" else "")
    }

    /** Loads an archived conversation as the current one. Returns its messages (role, content). */
    fun archiveOpen(c: Context, id: String): List<Pair<String, String>>? {
        val arr = readArchive(c)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) continue
            val msgs = o.getJSONArray("messages")
            val list = (0 until msgs.length()).map { msgs.getJSONObject(it).optString("role") to msgs.getJSONObject(it).optString("content") }
            val ctx = JSONArray()
            for ((r, t) in list.takeLast(24)) ctx.put(JSONObject().put("role", r).put("content", t.take(2000)))
            p(c).edit().putString("history", ctx.toString()).putString("conv", id).apply()
            return list
        }
        return null
    }

    @Synchronized
    fun archiveDelete(c: Context, id: String) {
        val arr = readArchive(c)
        val out = JSONArray()
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("id") != id) out.put(arr.get(i))
        writeArchive(c, out)
        if (p(c).getString("conv", "") == id) p(c).edit().putString("conv", "").apply()
    }

    @Synchronized
    fun archiveClear(c: Context) {
        writeArchive(c, JSONArray())
        p(c).edit().putString("conv", "").apply()
    }

    // ---------------------------------------------------------------- memory

    private val sensitiveRe = Regex(
        "(lösenord|password|passwd|pinkod|\\bpin\\b|personnummer|kortnummer|kreditkort|credit card|\\bcvv\\b|\\biban\\b|bankid|kontonummer|diagnos|sjukdom|sjuk\\b|medicin|recept|läkare|hälsa|ångest|depress|graviditet|religion|\\b\\d{9,}\\b|\\d{6}[-+]?\\d{4})",
        RegexOption.IGNORE_CASE
    )

    fun isSensitive(s: String) = sensitiveRe.containsMatchIn(s)
    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9åäö ]+"), " ").replace(Regex("\\s+"), " ").trim()

    fun memory(c: Context): List<String> = try {
        val a = JSONArray(p(c).getString("memory", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    fun setMemory(c: Context, list: List<String>) {
        val out = JSONArray()
        list.takeLast(40).forEach { out.put(it) }
        p(c).edit().putString("memory", out.toString()).apply()
    }

    /** Merges facts: short sentences, de-duplicated, max 40. `force` skips the sensitive filter (explicit "remember"). */
    fun mergeMemory(c: Context, incoming: List<String>, force: Boolean) {
        val out = memory(c).toMutableList()
        val norms = out.map { norm(it) }.toMutableList()
        for (raw in incoming) {
            val f = raw.replace(Regex("\\s+"), " ").trim().take(160)
            if (f.length < 6 || (!force && isSensitive(f))) continue
            val n = norm(f)
            if (n.isEmpty()) continue
            val d = norms.indexOfFirst { it == n || it.contains(n) || n.contains(it) }
            if (d >= 0) {
                if (n.length > norms[d].length) { out[d] = f; norms[d] = n }
                continue
            }
            out.add(f); norms.add(n)
        }
        setMemory(c, out)
    }

    fun addMemory(c: Context, fact: String) = mergeMemory(c, listOf(fact), true)

    /** "visa minne", "glöm 3", "glöm allt" handled locally. Returns the reply or null. */
    fun memoryCommand(c: Context, text: String): String? {
        val s = text.trim()
        val mem = memory(c)
        if (Regex("^(?:visa|lista|öppna)\\s+(?:mitt\\s+|ditt\\s+)?minne(?:t)?$", RegexOption.IGNORE_CASE).matches(s) ||
            Regex("^vad\\s+(?:minns|vet)\\s+du(?:\\s+om\\s+mig)?\\??$", RegexOption.IGNORE_CASE).matches(s)
        ) {
            if (mem.isEmpty()) return "Jag har inte lärt mig något om dig ännu. Jag lär mig av våra samtal, och du kan säga \"kom ihåg …\"."
            return "Det här minns jag (${mem.size}):\n" + mem.mapIndexed { i, m -> "${i + 1}. $m" }.joinToString("\n") +
                "\n\nSäg \"glöm 3\" eller \"glöm allt\" för att ta bort."
        }
        if (Regex("^glöm\\s+allt$", RegexOption.IGNORE_CASE).matches(s)) {
            setMemory(c, emptyList())
            return if (mem.isEmpty()) "Jag hade inget att glömma." else "Klart, jag har glömt allt (${mem.size} saker)."
        }
        Regex("^glöm\\s+(\\d+)$", RegexOption.IGNORE_CASE).find(s)?.let {
            val i = it.groupValues[1].toInt() - 1
            if (i < 0 || i >= mem.size) return "Det finns inget minne med nummer ${it.groupValues[1]}. Säg \"visa minne\" för listan."
            val next = mem.toMutableList()
            val gone = next.removeAt(i)
            setMemory(c, next)
            return "Glömde: $gone"
        }
        return null
    }

    // ---------------------------------------------------------------- style samples (drafts the user actually used)

    fun styleSamples(c: Context): List<String> = try {
        val a = JSONArray(p(c).getString("style", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    fun addStyleSample(c: Context, text: String) {
        val t = text.trim().take(400)
        if (t.length < 15 || isSensitive(t)) return
        val list = styleSamples(c).filter { it != t }.toMutableList()
        list.add(t)
        val out = JSONArray()
        list.takeLast(8).forEach { out.put(it) }
        p(c).edit().putString("style", out.toString()).apply()
    }

    fun styleBlock(c: Context): String {
        val s = styleSamples(c).takeLast(3)
        return if (s.isEmpty()) "" else "\nSå brukar användaren skriva (stilprov, härma tonen):\n" + s.joinToString("\n") { "- " + it.replace("\n", " ") }
    }

    // ---------------------------------------------------------------- how the user writes in chats (opt-in)

    fun flag(c: Context, key: String): Boolean = p(c).getBoolean(key, key == "learn_style")
    fun setFlag(c: Context, key: String, v: Boolean) { p(c).edit().putBoolean(key, v).apply() }

    fun smartReplyAppEnabled(c: Context, packageName: String): Boolean {
        val prefs = p(c)
        val key = "smart_reply_app_" + packageName
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else true
    }
    fun setSmartReplyAppEnabled(c: Context, packageName: String, enabled: Boolean) {
        p(c).edit().putBoolean("smart_reply_app_" + packageName, enabled).apply()
    }

    fun chatSamples(c: Context): List<String> = try {
        val a = JSONArray(p(c).getString("chat_samples", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    /** Remembers a message the user actually sent. Returns the total number of samples. */
    fun addChatSample(c: Context, text: String): Int {
        val t = text.trim().take(300)
        val list = chatSamples(c)
        if (t.length < 2 || isSensitive(t) || list.lastOrNull() == t) return list.size
        val out = JSONArray()
        (list + t).takeLast(120).forEach { out.put(it) }
        p(c).edit().putString("chat_samples", out.toString()).apply()
        return minOf(list.size + 1, 120)
    }

    fun styleProfile(c: Context): String = p(c).getString("style_profile", "") ?: ""
    fun setStyleProfile(c: Context, v: String) { p(c).edit().putString("style_profile", v.take(700)).apply() }
    fun profileMark(c: Context): Int = p(c).getInt("profile_mark", 0)
    fun setProfileMark(c: Context, n: Int) { p(c).edit().putInt("profile_mark", n).apply() }

    fun clearWritingStyle(c: Context) {
        p(c).edit().remove("chat_samples").remove("style_profile").remove("profile_mark").remove("style").apply()
    }

    // ---------------------------------------------------------------- custom quick commands

    class Cmd(val id: String, val emoji: String, val name: String, val prompt: String, val screen: Boolean)

    fun commands(c: Context): List<Cmd> = try {
        val a = JSONArray(p(c).getString("commands", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Cmd(o.optString("id"), o.optString("emoji", "⚡").ifEmpty { "⚡" }, o.optString("name"), o.optString("prompt"), o.optBoolean("screen", true))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveCommand(c: Context, cmd: Cmd) {
        val list = commands(c).toMutableList()
        val i = list.indexOfFirst { it.id == cmd.id }
        if (i >= 0) list[i] = cmd else list.add(cmd)
        writeCommands(c, list)
    }

    fun deleteCommand(c: Context, id: String) = writeCommands(c, commands(c).filter { it.id != id })

    private fun writeCommands(c: Context, list: List<Cmd>) {
        val a = JSONArray()
        list.take(24).forEach {
            a.put(JSONObject().put("id", it.id).put("emoji", it.emoji).put("name", it.name).put("prompt", it.prompt).put("screen", it.screen))
        }
        p(c).edit().putString("commands", a.toString()).apply()
    }

    // ---------------------------------------------------------------- mode and speech

    fun mode(c: Context): String = p(c).getString("mode", "BAR") ?: "BAR"
    fun setMode(c: Context, m: String) {
        p(c).edit().putString("mode", m).apply()
    }

    fun speak(c: Context): Boolean = p(c).getBoolean("speak", false)
    fun setSpeak(c: Context, v: Boolean) {
        p(c).edit().putBoolean("speak", v).apply()
    }
}
