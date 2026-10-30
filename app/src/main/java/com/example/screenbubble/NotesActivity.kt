package com.example.screenbubble

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Full-screen notes workspace with continuous, AI-polished dictation. */
class NotesActivity : Activity() {
    private val bg = 0xFF0B0E16.toInt()
    private val card = 0xFF171D2B.toInt()
    private val fg = 0xFFF2F4FC.toInt()
    private val muted = 0xFF9AA5BE.toInt()
    private val accent = 0xFF8DF5D0.toInt()
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private lateinit var dictationButton: TextView
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var processing = false
    private val main = Handler(Looper.getMainLooper())
    private var noteId = ""
    private var autoStartDictation = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), 0x24FFFFFF)
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoStartDictation = intent.getStringExtra("note_id").isNullOrBlank()
        noteId = intent.getStringExtra("note_id") ?: ("note_" + System.currentTimeMillis())
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(24), dp(18), dp(20))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("‹", 32f, fg).apply {
            gravity = Gravity.CENTER
            setOnClickListener { saveNote(); finish() }
        }, LinearLayout.LayoutParams(dp(42), dp(48)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(label("Anteckningar", 21f, fg, true))
        heading.addView(label("Diktera fritt. Jag gör texten tydlig.", 12f, muted))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("Alla", 14f, muted, true).apply {
            setPadding(dp(8), dp(10), dp(10), dp(10))
            setOnClickListener { saveNote(); startActivity(Intent(this@NotesActivity, NotesLibraryActivity::class.java)); finish() }
        })
        header.addView(label("Spara", 14f, accent, true).apply {
            setPadding(dp(8), dp(10), dp(4), dp(10))
            setOnClickListener { saveNote(); Toast.makeText(this@NotesActivity, "Anteckningen är sparad.", Toast.LENGTH_SHORT).show() }
        })
        root.addView(header)

        val tip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF171D31.toInt(), 16)
            setPadding(dp(13), dp(12), dp(13), dp(12))
        }
        tip.addView(label("✦", 22f, accent), LinearLayout.LayoutParams(dp(34), -2))
        tip.addView(label("ScreenBubble tar bort eh, öh och onödiga upprepningar, rättar språket och behåller det du faktiskt säger.", 12.5f, muted))
        root.addView(tip, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        editor = EditText(this).apply {
            hint = "Börja skriva eller tryck på diktera…"
            setHintTextColor(muted)
            setTextColor(fg)
            textSize = 16f
            gravity = Gravity.TOP
            minLines = 10
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = rounded(card, 18)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(editor) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(14) })
        status = label("Redo att lyssna.", 12f, muted).apply { setPadding(dp(3), dp(10), dp(3), dp(8)) }
        root.addView(status)
        dictationButton = label("●  Starta diktering", 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF8A6BFF.toInt(), 0xFF6241E8.toInt())).apply { cornerRadius = dp(15).toFloat() }
            setPadding(dp(14), dp(15), dp(14), dp(15))
            setOnClickListener { if (listening) stopListening() else startListening() }
        }
        root.addView(dictationButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(6) })
        setContentView(root)
        loadExistingNote()
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { saveNote() }
            override fun afterTextChanged(s: Editable?) {}
        })
        if (autoStartDictation) startListening()
    }

    private fun startListening() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            status.text = "Mikrofonbehörighet saknas. Tillåt mikrofonen i ScreenBubble-inställningarna."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Taligenkänning finns inte på den här enheten."
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        listening = true
                        status.text = "Lyssnar… Du kan prata naturligt."
                        dictationButton.text = "■  Stoppa diktering"
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        if (listening && !processing && error != SpeechRecognizer.ERROR_CLIENT) {
                            main.postDelayed({ if (listening && !processing) startListening() }, 400)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (raw.isBlank() || !listening) {
                            if (listening) main.postDelayed({ startListening() }, 300)
                            return
                        }
                        processing = true
                        status.text = "Finskriver texten med AI…"
                        Thread {
                            val polished = polish(raw)
                            main.post {
                                if (listening) {
                                    val prefix = if (editor.text.isNotEmpty() && !editor.text.last().isWhitespace()) " " else ""
                                    editor.append(prefix + polished)
                                    editor.setSelection(editor.text.length)
                                    saveNote()
                                    status.text = "Texten är klar. Fortsätt prata när du vill."
                                }
                                processing = false
                                if (listening) main.postDelayed({ startListening() }, 350)
                            }
                        }.start()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (partial.isNotBlank()) status.text = "Lyssnar: " + partial.take(70)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                r.startListening(intent)
            }
        } catch (_: Exception) {
            listening = false
            status.text = "Kunde inte starta mikrofonen. Försök igen."
        }
    }

    private fun stopListening() {
        listening = false
        try { recognizer?.stopListening() } catch (_: Exception) {}
        try { recognizer?.cancel() } catch (_: Exception) {}
        dictationButton.text = "●  Starta diktering"
        status.text = "Dikteringen är pausad. Texten sparas automatiskt."
    }

    private fun polish(raw: String): String {
        val fallback = raw.replace(Regex("(?i)(^|[\\s,])(?:eh+|öh+|öö+h|ee+h|mmm+)(?=[\\s,.;!?]|$)"), "$1")
            .replace(Regex("\\s+"), " ").trim()
        val key = Config.embeddedKey().ifEmpty { getSharedPreferences("prefs", MODE_PRIVATE).getString("api_key", "").orEmpty() }
        if (key.isBlank()) return fallback
        return try {
            OpenAiClient(key).textSimple(
                "Du är en noggrann redaktör för talad text. Skriv om transkriberingen till naturlig, korrekt text på samma språk. Ta bort utfyllnadsord som eh, öh, öööh, eeeh och onödiga upprepningar. Rätta stavning, grammatik och interpunktion och dela upp texten i stycken när det passar. Behåll talarens betydelse, avsikt, namn, siffror och fakta. Lägg inte till information. Svara endast med den färdiga texten.",
                raw, 900
            ).trim().ifBlank { fallback }
        } catch (_: Exception) { fallback }
    }

    private fun loadExistingNote() {
        val old = try { JSONArray(getSharedPreferences("prefs", MODE_PRIVATE).getString("notes", "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("id") == noteId) {
                editor.setText(item.optString("text"))
                editor.setSelection(editor.text.length)
                break
            }
        }
    }

    private fun saveNote() {
        if (!::editor.isInitialized) return
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val old = try { JSONArray(prefs.getString("notes", "[]")) } catch (_: Exception) { JSONArray() }
        val updated = JSONObject()
            .put("id", noteId)
            .put("title", editor.text.toString().lineSequence().firstOrNull()?.take(60).orEmpty().ifBlank { "Ny anteckning" })
            .put("text", editor.text.toString())
            .put("updated_at", System.currentTimeMillis())
        val next = JSONArray()
        var found = false
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("id") == noteId) {
                if (!found) next.put(updated)
                found = true
            } else next.put(item)
        }
        if (!found && editor.text.isNotBlank()) next.put(updated)
        prefs.edit().putString("notes", next.toString()).apply()
    }

    override fun onDestroy() {
        listening = false
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        saveNote()
        super.onBackPressed()
    }
}
