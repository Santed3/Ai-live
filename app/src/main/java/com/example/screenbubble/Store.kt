package com.example.screenbubble

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small persistent store: conversation history, remembered facts, mode. */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

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
    }

    fun clearHistory(c: Context) {
        p(c).edit().putString("history", "[]").apply()
    }

    fun memory(c: Context): List<String> = try {
        val a = JSONArray(p(c).getString("memory", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    fun addMemory(c: Context, fact: String) {
        val list = memory(c).toMutableList()
        list.add(fact.take(300))
        val out = JSONArray()
        list.takeLast(30).forEach { out.put(it) }
        p(c).edit().putString("memory", out.toString()).apply()
    }

    fun mode(c: Context): String = p(c).getString("mode", "PANEL") ?: "PANEL"
    fun setMode(c: Context, m: String) {
        p(c).edit().putString("mode", m).apply()
    }

    fun speak(c: Context): Boolean = p(c).getBoolean("speak", false)
    fun setSpeak(c: Context, v: Boolean) {
        p(c).edit().putBoolean("speak", v).apply()
    }
}
