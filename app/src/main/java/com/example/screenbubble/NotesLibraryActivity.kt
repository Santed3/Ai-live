package com.example.screenbubble

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Searchable local library for notes created through dictation or typing. */
class NotesLibraryActivity : Activity() {
    private val bg = 0xFF0B0E16.toInt()
    private val card = 0xFF171D2B.toInt()
    private val fg = 0xFFF2F4FC.toInt()
    private val muted = 0xFF9AA5BE.toInt()
    private val accent = 0xFF8DF5D0.toInt()
    private lateinit var list: LinearLayout
    private lateinit var search: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), 0x24FFFFFF)
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg)
            setPadding(dp(18), dp(24), dp(18), dp(18))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("‹", 32f, fg).apply {
            gravity = Gravity.CENTER; setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(42), dp(48)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(label("Mina anteckningar", 22f, fg, true))
        heading.addView(label("Sök, fortsätt eller skapa en ny anteckning", 12f, muted))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("＋ Ny", 14f, accent, true).apply {
            setPadding(dp(10), dp(12), dp(4), dp(12))
            setOnClickListener { startActivity(Intent(this@NotesLibraryActivity, NotesActivity::class.java)); finish() }
        })
        root.addView(header)
        search = EditText(this).apply {
            hint = "Sök i anteckningar…"; setHintTextColor(muted); setTextColor(fg); textSize = 14f
            setSingleLine(true); background = rounded(card, 13); setPadding(dp(14), dp(11), dp(14), dp(11))
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
            override fun afterTextChanged(s: Editable?) {}
        })
        setContentView(root)
        render()
    }

    private fun readNotes(): List<org.json.JSONObject> {
        val array = try { JSONArray(getSharedPreferences("prefs", MODE_PRIVATE).getString("notes", "[]")) } catch (_: Exception) { JSONArray() }
        val notes = mutableListOf<org.json.JSONObject>()
        for (i in 0 until array.length()) array.optJSONObject(i)?.let { if (it.optString("text").isNotBlank()) notes.add(it) }
        return notes.sortedByDescending { it.optLong("updated_at") }
    }

    private fun render() {
        list.removeAllViews()
        val query = search.text.toString().trim().lowercase(Locale.getDefault())
        val notes = readNotes().filter {
            query.isBlank() || it.optString("title").lowercase(Locale.getDefault()).contains(query) ||
                it.optString("text").lowercase(Locale.getDefault()).contains(query)
        }
        if (notes.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(18), dp(50), dp(18), dp(50))
            }
            empty.addView(label(if (query.isBlank()) "📝" else "⌕", 34f, accent).apply { gravity = Gravity.CENTER })
            empty.addView(label(if (query.isBlank()) "Dina anteckningar visas här" else "Inga träffar", 17f, fg, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(4)) })
            empty.addView(label(if (query.isBlank()) "Starta en diktering eller skapa en anteckning." else "Prova ett annat sökord.", 12f, muted).apply { gravity = Gravity.CENTER })
            list.addView(empty)
            return
        }
        val formatter = SimpleDateFormat("d MMM · HH:mm", Locale("sv", "SE"))
        notes.forEach { note ->
            val id = note.optString("id")
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = rounded(card, 16)
                setPadding(dp(15), dp(13), dp(13), dp(13))
                setOnClickListener {
                    startActivity(Intent(this@NotesLibraryActivity, NotesActivity::class.java).putExtra("note_id", id))
                    finish()
                }
            }
            val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            top.addView(label(note.optString("title", "Ny anteckning"), 15f, fg, true), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(label("⋯", 20f, muted).apply {
                setPadding(dp(10), 0, dp(4), 0)
                setOnClickListener {
                    AlertDialog.Builder(this@NotesLibraryActivity)
                        .setTitle("Ta bort anteckningen?")
                        .setMessage("Anteckningen tas bort från den här enheten.")
                        .setNegativeButton("Avbryt", null)
                        .setPositiveButton("Ta bort") { _, _ -> deleteNote(id); render() }
                        .show()
                }
            })
            item.addView(top)
            item.addView(label(note.optString("text").replace(Regex("\\s+"), " ").take(150), 12f, muted).apply { setPadding(0, dp(6), 0, dp(7)) })
            item.addView(label(formatter.format(Date(note.optLong("updated_at"))), 10f, accent))
            list.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
    }

    private fun deleteNote(id: String) {
        val old = try { JSONArray(getSharedPreferences("prefs", MODE_PRIVATE).getString("notes", "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray()
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("id") != id) next.put(item)
        }
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("notes", next.toString()).apply()
    }

    override fun onResume() { super.onResume(); if (::list.isInitialized) render() }
}
