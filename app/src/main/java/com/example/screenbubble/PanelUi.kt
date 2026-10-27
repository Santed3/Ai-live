package com.example.screenbubble

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The side panel: chat, context shortcuts, smart replies, voice. */
class PanelUi(private val svc: ReaderService) {
    private val main = Handler(Looper.getMainLooper())
    private val density = svc.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val BG = 0xF2101326.toInt()
    private val CARD = 0xFF1B1F36.toInt()
    private val ACCENT = 0xFF7CF5C8.toInt()
    private val PRIMARY = 0xFF6C47FF.toInt()
    private val TEXT = 0xFFECEFF8.toInt()
    private val MUTED = 0xFF9AA3BF.toInt()

    val root = LinearLayout(svc)
    private val chips = LinearLayout(svc)
    private val chat = LinearLayout(svc)
    private val scroll = ScrollView(svc)
    private val input = EditText(svc)
    private val draftCard = LinearLayout(svc)
    private val draftEdit = EditText(svc)
    private val confirmCard = LinearLayout(svc)
    private val confirmText = TextView(svc)
    private var statusView: TextView? = null
    private lateinit var micBtn: TextView
    private lateinit var speakBtn: TextView
    private lateinit var convoBtn: TextView

    @Volatile private var busy = false
    private var speakOn = Store.speak(svc)
    private var convo = false
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val ui = object : Agent.Ui {
        override fun status(t: String) {
            main.post { setStatus(t) }
        }

        override fun confirm(t: String): Boolean = askConfirm(t)
    }

    init {
        build()
    }

    // ------------------------------------------------------------------ build

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun pill(label: String, bg: Int = 0x26FFFFFF, fg: Int = TEXT, onClick: () -> Unit): TextView =
        TextView(svc).apply {
            text = label
            textSize = 13f
            setTextColor(fg)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(bg, 16)
            setOnClickListener { onClick() }
        }

    private fun lp(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    private fun hscroll(row: LinearLayout): HorizontalScrollView =
        HorizontalScrollView(svc).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }

    private fun build() {
        root.orientation = LinearLayout.VERTICAL
        root.background = rounded(BG, 22)
        root.setPadding(dp(12), dp(10), dp(12), dp(10))
        root.elevation = dp(10).toFloat()

        // header
        val header = LinearLayout(svc).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(svc).apply {
            text = "ScreenBubble"
            textSize = 16f
            setTextColor(TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }, lp(0, -2, weight = 1f))
        speakBtn = pill(if (speakOn) "🔊" else "🔇") { toggleSpeak() }
        convoBtn = pill("Samtal") { toggleConvo() }
        header.addView(speakBtn, lp(-2, -2, r = 6))
        header.addView(convoBtn, lp(-2, -2, r = 6))
        header.addView(pill("Ny") { newConversation() }, lp(-2, -2, r = 6))
        header.addView(pill("Dölj") { svc.setMode(ReaderService.Mode.HIDE) }, lp(-2, -2, r = 6))
        header.addView(pill("✕", 0x33FF5C5C.toInt()) { svc.shutDown() })
        root.addView(header)

        // chips
        chips.orientation = LinearLayout.HORIZONTAL
        root.addView(hscroll(chips), lp(-1, -2, t = 8))

        // chat
        chat.orientation = LinearLayout.VERTICAL
        scroll.addView(chat)
        root.addView(scroll, lp(-1, 0, t = 8, weight = 1f))

        // draft card (smart reply)
        draftCard.orientation = LinearLayout.VERTICAL
        draftCard.background = rounded(CARD, 14)
        draftCard.setPadding(dp(10), dp(8), dp(10), dp(8))
        draftCard.visibility = View.GONE
        draftCard.addView(TextView(svc).apply {
            text = "Förslag (redigera fritt)"
            textSize = 12f
            setTextColor(ACCENT)
        })
        draftEdit.setTextColor(TEXT)
        draftEdit.textSize = 14f
        draftEdit.maxLines = 6
        draftEdit.setBackgroundColor(Color.TRANSPARENT)
        draftEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        draftCard.addView(draftEdit, lp(-1, -2, t = 4))
        val tools = LinearLayout(svc)
        for ((label, instr) in listOf(
            "Kortare" to "Gör texten kortare men behåll innebörden.",
            "Professionell" to "Skriv om i en professionell, artig ton.",
            "Vänligare" to "Skriv om i en varmare, vänligare ton.",
            "Översätt" to "Översätt till engelska. Om texten redan är engelska: översätt till svenska.",
            "Skriv om" to "Skriv om med annan formulering men samma innebörd.",
            "Längre" to "Gör texten något mer utförlig och tydlig."
        )) tools.addView(pill(label) { transform(instr) }, lp(-2, -2, r = 6))
        draftCard.addView(hscroll(tools), lp(-1, -2, t = 6))
        val acts = LinearLayout(svc)
        acts.addView(pill("Kopiera", 0x26FFFFFF) { copyDraft() }, lp(-2, -2, r = 6))
        acts.addView(pill("Skriv in", 0x26FFFFFF) { insertDraft(false) }, lp(-2, -2, r = 6))
        acts.addView(pill("Skicka", PRIMARY) { insertDraft(true) }, lp(-2, -2, r = 6))
        acts.addView(pill("Stäng", 0x26FFFFFF) { draftCard.visibility = View.GONE })
        draftCard.addView(acts, lp(-1, -2, t = 6))
        root.addView(draftCard, lp(-1, -2, t = 8))

        // confirm card
        confirmCard.orientation = LinearLayout.VERTICAL
        confirmCard.background = rounded(0xFF3A2A12.toInt(), 14)
        confirmCard.setPadding(dp(10), dp(8), dp(10), dp(8))
        confirmCard.visibility = View.GONE
        confirmText.setTextColor(TEXT)
        confirmText.textSize = 14f
        confirmCard.addView(confirmText)
        root.addView(confirmCard, lp(-1, -2, t = 8))

        // input row
        val row = LinearLayout(svc).apply { gravity = Gravity.CENTER_VERTICAL }
        input.hint = "Fråga eller säg vad jag ska göra…"
        input.setHintTextColor(MUTED)
        input.setTextColor(TEXT)
        input.textSize = 15f
        input.maxLines = 3
        input.background = rounded(0x26FFFFFF, 18)
        input.setPadding(dp(14), dp(10), dp(14), dp(10))
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { sendFromInput(); true } else false
        }
        row.addView(input, lp(0, -2, weight = 1f))
        micBtn = pill("🎤") { listen() }
        row.addView(micBtn, lp(-2, -2, l = 6))
        row.addView(pill("➤", PRIMARY) { sendFromInput() }, lp(-2, -2, l = 6))
        root.addView(row, lp(-1, -2, t = 8))

        addMsg(false, "Hej! Fråga mig om skärmen, be mig göra något på telefonen, eller tryck på en genväg ovan.")
    }

    fun onShown() {
        refreshChips()
    }

    fun destroy() {
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
    }

    // ------------------------------------------------------------------ chat

    private fun addMsg(user: Boolean, text: String) {
        val tv = TextView(svc).apply {
            this.text = text.replace("**", "")
            textSize = 14.5f
            setTextColor(if (user) Color.WHITE else TEXT)
            setTextIsSelectable(true)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(if (user) PRIMARY else CARD, 14)
            maxWidth = dp(290)
        }
        val p = lp(-2, -2, t = 6)
        p.gravity = if (user) Gravity.END else Gravity.START
        chat.addView(tv, p)
        scrollDown()
    }

    private fun setStatus(t: String) {
        if (statusView == null) {
            statusView = TextView(svc).apply {
                textSize = 12.5f
                setTextColor(ACCENT)
                setPadding(dp(4), dp(4), dp(4), dp(2))
            }
            chat.addView(statusView, lp(-2, -2, t = 4))
        }
        statusView?.text = t
        scrollDown()
    }

    private fun clearStatus() {
        statusView?.let { chat.removeView(it) }
        statusView = null
    }

    private fun scrollDown() {
        scroll.post { scroll.smoothScrollTo(0, chat.height) }
    }

    private fun newConversation() {
        Store.clearHistory(svc)
        chat.removeAllViews()
        statusView = null
        draftCard.visibility = View.GONE
        addMsg(false, "Ny konversation. Vad vill du göra?")
        refreshChips()
    }

    private fun sendFromInput() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        send(t, false)
    }

    private fun noKey(): Boolean {
        if (svc.apiKey().isNotEmpty()) return false
        addMsg(false, "Ingen API-nyckel. Öppna ScreenBubble-appen och skriv in den.")
        return true
    }

    fun send(text: String, voice: Boolean) {
        if (busy || text.isBlank()) return
        if (noKey()) return
        busy = true
        addMsg(true, text)
        setStatus("Tänker…")
        svc.pool.execute {
            try {
                val (st, shot) = svc.captureScreen()
                val ans = Agent(svc, OpenAiClient(svc.apiKey()), svc, ui).run(text, st, shot)
                main.post {
                    clearStatus()
                    addMsg(false, ans)
                    if (speakOn || voice) speak(ans)
                }
            } catch (e: Exception) {
                main.post { clearStatus(); addMsg(false, "Fel: ${e.message}") }
            } finally {
                busy = false
            }
        }
    }

    // ---------------------------------------------------------------- chips

    fun refreshChips() {
        svc.pool.execute {
            val text = svc.readScreenText()
            val list = Suggest.forScreen(svc.currentPkg(), text)
            main.post {
                chips.removeAllViews()
                for (c in list) {
                    chips.addView(pill(c.label, 0x337C5CFF) {
                        if (c.reply) makeReply() else send(c.prompt, false)
                    }, lp(-2, -2, r = 6))
                }
            }
        }
    }

    // ---------------------------------------------------------- smart reply

    private fun makeReply() {
        if (busy || noKey()) return
        busy = true
        setStatus("Läser konversationen och skriver ett förslag…")
        svc.pool.execute {
            try {
                val (st, shot) = svc.captureScreen()
                val client = OpenAiClient(svc.apiKey())
                val reply = client.text(
                    "Du skriver svar åt användaren. Läs mejlet eller konversationen på skärmen (text + bild) och skriv ett naturligt, bra svar som användaren kan skicka. " +
                        "Matcha språk och ton i konversationen. Svara ENDAST med själva svarstexten: ingen inledning, inga citattecken, ingen förklaring.",
                    client.content("Text på skärmen:\n${st.take(6000)}", shot), 600
                )
                main.post { clearStatus(); showDraft(reply) }
            } catch (e: Exception) {
                main.post { clearStatus(); addMsg(false, "Fel: ${e.message}") }
            } finally {
                busy = false
            }
        }
    }

    private fun showDraft(text: String) {
        draftEdit.setText(text)
        draftCard.visibility = View.VISIBLE
        scrollDown()
    }

    private fun transform(instruction: String) {
        val cur = draftEdit.text.toString()
        if (cur.isBlank() || busy || noKey()) return
        busy = true
        setStatus("Skriver om…")
        svc.pool.execute {
            try {
                val out = OpenAiClient(svc.apiKey()).textSimple(
                    "Du redigerar text åt användaren. Svara ENDAST med den nya texten, utan inledning eller citattecken.",
                    "Instruktion: $instruction\n\nText:\n$cur", 600
                )
                main.post { clearStatus(); draftEdit.setText(out) }
            } catch (e: Exception) {
                main.post { clearStatus(); addMsg(false, "Fel: ${e.message}") }
            } finally {
                busy = false
            }
        }
    }

    private fun copyDraft() {
        val cm = svc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("svar", draftEdit.text.toString()))
        setStatus("Kopierat.")
        main.postDelayed({ clearStatus() }, 1500)
    }

    private fun insertDraft(send: Boolean) {
        val text = draftEdit.text.toString()
        if (text.isBlank()) return
        svc.pool.execute {
            if (send && !askConfirm("Skicka det här meddelandet?\n\n$text")) return@execute
            val r = svc.typeText(text, false)
            val full = if (send && r.startsWith("Skrev")) {
                Thread.sleep(400)
                r + " " + svc.tapSend()
            } else r
            main.post { setStatus(full); main.postDelayed({ clearStatus() }, 2500) }
        }
    }

    // -------------------------------------------------------------- confirm

    private fun askConfirm(t: String): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        main.post {
            confirmCard.removeAllViews()
            confirmText.text = t
            confirmCard.addView(confirmText)
            val r = LinearLayout(svc)
            r.addView(pill("Ja, gör det", PRIMARY) { ok = true; latch.countDown() }, lp(-2, -2, t = 6, r = 8))
            r.addView(pill("Nej") { ok = false; latch.countDown() }, lp(-2, -2, t = 6))
            confirmCard.addView(r)
            confirmCard.visibility = View.VISIBLE
            scrollDown()
        }
        latch.await(90, TimeUnit.SECONDS)
        main.post { confirmCard.visibility = View.GONE }
        return ok
    }

    // ---------------------------------------------------------------- voice

    private fun toggleSpeak() {
        speakOn = !speakOn
        Store.setSpeak(svc, speakOn)
        speakBtn.text = if (speakOn) "🔊" else "🔇"
        if (!speakOn) stopSpeaking()
    }

    private fun toggleConvo() {
        convo = !convo
        convoBtn.background = rounded(if (convo) PRIMARY else 0x26FFFFFF, 16)
        if (convo) {
            speakOn = true
            speakBtn.text = "🔊"
            listen()
        } else stopSpeaking()
    }

    private fun listen() {
        if (svc.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            addMsg(false, "Tillåt mikrofon: öppna ScreenBubble-appen och tryck på \"Tillåt mikrofon\".")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(svc)) {
            addMsg(false, "Taligenkänning finns inte på den här telefonen.")
            return
        }
        stopSpeaking()
        try { recognizer?.destroy() } catch (_: Exception) {}
        val r = SpeechRecognizer.createSpeechRecognizer(svc)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { micBtn.text = "●" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { micBtn.text = "🎤" }
            override fun onError(error: Int) {
                micBtn.text = "🎤"
                if (convo && error == SpeechRecognizer.ERROR_NO_MATCH) main.postDelayed({ if (convo) listen() }, 400)
            }
            override fun onResults(results: Bundle?) {
                micBtn.text = "🎤"
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank()) send(t, true)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank()) input.setText(t)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        r.startListening(i)
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(svc) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.getDefault()
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (convo) main.post { if (convo) listen() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
    }

    private fun speak(text: String) {
        ensureTts()
        val clean = text.replace("**", "").take(1500)
        if (ttsReady) {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "ans")
        } else {
            main.postDelayed({ if (ttsReady) tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "ans") }, 900)
        }
    }

    private fun stopSpeaking() {
        try { tts?.stop() } catch (_: Exception) {}
    }
}
