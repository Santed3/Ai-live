package com.example.screenbubble

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.widget.ProgressBar
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
import android.view.animation.DecelerateInterpolator
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

    private val BG = 0xF50B0E16.toInt()
    private val CARD = 0xFF171D2B.toInt()
    private val ACCENT = 0xFF8DF5D0.toInt()
    private val PRIMARY = 0xFF7857FF.toInt()
    private val TEXT = 0xFFF2F4FC.toInt()
    private val MUTED = 0xFF9AA5BE.toInt()

    val root = LinearLayout(svc)
    private val chips = LinearLayout(svc)
    private val chat = LinearLayout(svc)
    private val scroll = ScrollView(svc)
    private val input = EditText(svc)
    private val draftCard = LinearLayout(svc)
    private val draftEdit = EditText(svc)
    private val confirmCard = LinearLayout(svc)
    private val confirmText = TextView(svc)
    private var statusView: View? = null
    private var statusText: TextView? = null
    private var live: TextView? = null
    private val liveText = StringBuilder()
    private var welcome: View? = null
    private val histView = LinearLayout(svc)
    private val histList = LinearLayout(svc)
    private val histSearch = EditText(svc)
    private var histSure = false
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

        override fun delta(t: String) {
            main.post {
                clearStatus()
                if (live == null) { live = addMsgView(false, ""); liveText.setLength(0) }
                liveText.append(t)
                live?.text = styled(liveText.toString())
                scrollDown()
            }
        }

        override fun deltaReset() {
            main.post {
                live?.let { chat.removeView(it) }
                live = null
                liveText.setLength(0)
            }
        }
    }

    init {
        build()
    }

    // ------------------------------------------------------------------ build

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, 0xFF111521.toInt())).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), 0x1FFFFFFF)
        }

    /** Short, consistent entrance motion for transient UI. */
    private fun reveal(view: View, distance: Int = 7, duration: Long = 210L) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = dp(distance).toFloat()
        view.animate().alpha(1f).translationY(0f)
            .setDuration(duration).setInterpolator(DecelerateInterpolator()).start()
    }

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
        root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xF51B2030.toInt(), 0xF50B0E16.toInt())).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), 0x24FFFFFF)
        }
        root.setPadding(dp(11), dp(9), dp(11), dp(9))
        root.elevation = dp(10).toFloat()

        // header
        val header = LinearLayout(svc).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(iconPill("…") { svc.setMode(ReaderService.Mode.MENU) }, lp(-2, -2, r = 2))
        header.addView(View(svc).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFB9A8FF.toInt(), 0xFF6C47FF.toInt())).apply { shape = GradientDrawable.OVAL }
        }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { setMargins(dp(2), 0, dp(7), 0) })
        header.addView(TextView(svc).apply {
            text = "ScreenBubble"
            textSize = 15f
            setTextColor(TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }, lp(0, -2, weight = 1f))
        header.addView(iconPill("🕘") { showHistory(true) }, lp(-2, -2))
        speakBtn = iconPill(if (speakOn) "🔊" else "🔇") { toggleSpeak() }
        convoBtn = iconPill("🎙") { toggleConvo() }
        header.addView(speakBtn, lp(-2, -2))
        header.addView(convoBtn, lp(-2, -2))
        header.addView(iconPill("＋") { newConversation() }, lp(-2, -2))
        header.addView(iconPill("✕") { svc.setMode(ReaderService.Mode.BAR) }, lp(-2, -2))
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

        buildHistory()
        showWelcome()
    }

    private fun iconPill(label: String, onClick: () -> Unit): TextView =
        TextView(svc).apply {
            text = label
            textSize = 15f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = rounded(0x00000000, 14)
            setOnClickListener { onClick() }
        }

    // ---------------------------------------------------------------- welcome card

    private fun showWelcome() {
        val box = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(14), 0, dp(6))
        }
        box.addView(TextView(svc).apply { text = "👋"; textSize = 28f; gravity = Gravity.CENTER })
        box.addView(TextView(svc).apply {
            text = "Hej! Vad vill du göra?"
            textSize = 16f
            setTextColor(TEXT)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, lp(-2, -2, t = 2))
        box.addView(TextView(svc).apply {
            text = "Jag ser din skärm och kan hjälpa dig direkt."
            textSize = 13f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
        }, lp(-2, -2, t = 2, b = 10))
        for ((label, prompt) in listOf(
            "✅  Svara på frågan på skärmen" to "Svara på frågan som syns på skärmen. Ge svaret tydligt.",
            "📄  Sammanfatta det jag tittar på" to "Sammanfatta det som syns på skärmen kort.",
            "🔎  Googla något" to ""
        )) {
            val b = pill(label, 0x14FFFFFF) {
                if (prompt.isEmpty()) { input.setText("google "); input.setSelection(7); input.requestFocus() } else send(prompt, false)
            }
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            box.addView(b, lp(-1, -2, t = 6))
        }
        welcome = box
        chat.addView(box)
    }

    private fun removeWelcome() {
        welcome?.let { chat.removeView(it) }
        welcome = null
    }

    // ---------------------------------------------------------------- history (all conversations, searchable)

    private fun buildHistory() {
        histView.orientation = LinearLayout.VERTICAL
        histView.visibility = View.GONE
        val top = LinearLayout(svc).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(iconPill("‹") { showHistory(false) }, lp(-2, -2, r = 4))
        histSearch.hint = "Sök i alla konversationer…"
        histSearch.setHintTextColor(MUTED)
        histSearch.setTextColor(TEXT)
        histSearch.textSize = 14f
        histSearch.maxLines = 1
        histSearch.inputType = InputType.TYPE_CLASS_TEXT
        histSearch.background = rounded(0x26FFFFFF, 16)
        histSearch.setPadding(dp(12), dp(8), dp(12), dp(8))
        histSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                main.removeCallbacks(histRun)
                main.postDelayed(histRun, 150)
            }
        })
        top.addView(histSearch, lp(0, -2, weight = 1f))
        val clear = pill("Rensa", 0x26FFFFFF) {}
        clear.setOnClickListener {
            if (!histSure) {
                histSure = true
                clear.text = "Säker?"
                main.postDelayed({ histSure = false; clear.text = "Rensa" }, 3000)
            } else {
                histSure = false
                clear.text = "Rensa"
                Store.archiveClear(svc)
                renderHistory()
            }
        }
        top.addView(clear, lp(-2, -2, l = 4))
        histView.addView(top)
        val sv = ScrollView(svc)
        histList.orientation = LinearLayout.VERTICAL
        sv.addView(histList)
        histView.addView(sv, lp(-1, 0, t = 8, weight = 1f))
        root.addView(histView, lp(-1, 0, t = 8, weight = 1f))
    }

    private val histRun = Runnable { renderHistory() }

    private fun showHistory(show: Boolean) {
        for (i in 1 until root.childCount) {
            val v = root.getChildAt(i)
            if (v === histView) continue
            if (show) v.visibility = View.GONE
            else if (v !== draftCard && v !== confirmCard) v.visibility = View.VISIBLE
        }
        histView.visibility = if (show) View.VISIBLE else View.GONE
        if (show) { histSearch.setText(""); renderHistory(); histSearch.requestFocus() }
    }

    private fun highlight(text: String, q: String): CharSequence {
        val sp = android.text.SpannableString(text)
        val low = text.lowercase(Locale.ROOT)
        for (w in q.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            var i = low.indexOf(w)
            while (i >= 0) {
                sp.setSpan(android.text.style.BackgroundColorSpan(0x557CF5C8), i, i + w.length, 0)
                i = low.indexOf(w, i + w.length)
            }
        }
        return sp
    }

    private fun dateLabel(ts: Long): String {
        val cal = java.util.Calendar.getInstance()
        val today = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
        cal.timeInMillis = ts
        val that = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
        val time = java.text.SimpleDateFormat("HH:mm", Locale("sv", "SE")).format(java.util.Date(ts))
        return when {
            that == today -> "Idag $time"
            that.second == today.second && that.first == today.first - 1 -> "Igår $time"
            else -> java.text.SimpleDateFormat("d MMM HH:mm", Locale("sv", "SE")).format(java.util.Date(ts))
        }
    }

    private fun renderHistory() {
        val q = histSearch.text.toString().trim()
        svc.pool.execute {
            val hits = Store.archiveSearch(svc, q)
            main.post {
                histList.removeAllViews()
                if (hits.isEmpty()) {
                    histList.addView(TextView(svc).apply {
                        text = if (q.isEmpty()) "Inga sparade konversationer än." else "Inga träffar."
                        setTextColor(MUTED); textSize = 13f; gravity = Gravity.CENTER
                        setPadding(0, dp(24), 0, 0)
                    }, lp(-1, -2))
                    return@post
                }
                for (h in hits) {
                    val row = LinearLayout(svc).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        background = rounded(0x0DFFFFFF, 14)
                        setPadding(dp(11), dp(9), dp(8), dp(9))
                        setOnClickListener { openConversation(h.id) }
                    }
                    val col = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
                    col.addView(TextView(svc).apply {
                        text = highlight(h.title, q); textSize = 13.5f; setTextColor(TEXT)
                        setTypeface(typeface, Typeface.BOLD); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    if (h.snippet.isNotEmpty()) col.addView(TextView(svc).apply {
                        text = highlight(h.snippet, q); textSize = 12f; setTextColor(MUTED); maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    col.addView(TextView(svc).apply {
                        text = dateLabel(h.ts) + " · " + h.count + " meddelanden"; textSize = 11f; setTextColor(MUTED)
                    })
                    row.addView(col, lp(0, -2, weight = 1f))
                    row.addView(iconPill("🗑") { Store.archiveDelete(svc, h.id); renderHistory() }, lp(-2, -2))
                    histList.addView(row, lp(-1, -2, b = 6))
                }
            }
        }
    }

    private fun openConversation(id: String) {
        svc.pool.execute {
            val msgs = Store.archiveOpen(svc, id)
            main.post {
                if (msgs == null) return@post
                chat.removeAllViews()
                statusView = null; statusText = null; live = null; welcome = null
                draftCard.visibility = View.GONE
                for ((role, text) in msgs) addMsgView(role == "user", text)
                showHistory(false)
            }
        }
    }

    fun onShown() {
        refreshChips()
    }

    fun startVoice() { listen() }

    /** Opens with a preset: send it right away, or put it in the input for the user to finish. */
    fun start(preset: String?, auto: Boolean) {
        if (preset.isNullOrBlank()) return
        if (auto) send(preset, false)
        else {
            input.setText(preset)
            input.setSelection(preset.length)
            input.requestFocus()
        }
    }

    fun destroy() {
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
    }

    // ------------------------------------------------------------------ chat

    private fun styled(text: String): android.text.SpannableStringBuilder {
        val sp = android.text.SpannableStringBuilder()
        var bold = false
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) { bold = !bold; i += 2; continue }
            val start = sp.length
            sp.append(text[i])
            if (bold) sp.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, start + 1, 0)
            i++
        }
        return sp
    }

    private fun addMsg(user: Boolean, text: String) {
        if (user) removeWelcome()
        addMsgView(user, text)
    }

    private fun addMsgView(user: Boolean, text: String): TextView {
        val big = dp(18).toFloat()
        val small = dp(4).toFloat()
        val tv = TextView(svc).apply {
            this.text = styled(text)
            textSize = 14.5f
            setLineSpacing(0f, 1.12f)
            setTextColor(if (user) Color.WHITE else TEXT)
            setTextIsSelectable(true)
            setPadding(dp(13), dp(9), dp(13), dp(9))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (user) intArrayOf(0xFF7A58FF.toInt(), 0xFF5A3BDC.toInt()) else intArrayOf(0x1AFFFFFF, 0x1AFFFFFF)
            ).apply {
                cornerRadii = if (user) floatArrayOf(big, big, big, big, small, small, big, big)
                else floatArrayOf(big, big, big, big, big, big, small, small)
            }
            maxWidth = dp(250)
            alpha = 0f
            translationY = dp(6).toFloat()
        }
        val p = lp(-2, -2, t = 7)
        p.gravity = if (user) Gravity.END else Gravity.START
        chat.addView(tv, p)
        tv.animate().alpha(1f).translationY(0f).setDuration(180).start()
        scrollDown()
        return tv
    }

    private fun setStatus(t: String) {
        if (statusView == null) {
            val row = LinearLayout(svc).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(0x14FFFFFF, 14)
                setPadding(dp(10), dp(5), dp(12), dp(5))
            }
            row.addView(ProgressBar(svc, null, android.R.attr.progressBarStyleSmall).apply {
                indeterminateTintList = ColorStateList.valueOf(ACCENT)
            }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(8) })
            val tv = TextView(svc).apply { textSize = 12.5f; setTextColor(MUTED) }
            row.addView(tv)
            statusText = tv
            statusView = row
            val p = lp(-2, -2, t = 6)
            p.gravity = Gravity.START
            chat.addView(row, p)
            reveal(row, 5, 180L)
        }
        statusText?.text = t
        scrollDown()
    }

    private fun clearStatus() {
        statusView?.let { chat.removeView(it) }
        statusView = null
        statusText = null
    }

    private fun scrollDown() {
        scroll.post { scroll.smoothScrollTo(0, chat.height) }
    }

    private fun newConversation() {
        Store.clearHistory(svc)
        chat.removeAllViews()
        statusView = null; statusText = null; live = null; welcome = null
        draftCard.visibility = View.GONE
        showWelcome()
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
        live = null
        addMsg(true, text)
        setStatus("Tänker…")
        svc.pool.execute {
            try {
                val (st, shot) = svc.captureScreen()
                val ans = Agent(svc, OpenAiClient(svc.apiKey()), svc, ui).run(text, st, shot)
                main.post {
                    clearStatus()
                    val l = live
                    if (l != null) { l.text = styled(ans); live = null } else addMsg(false, ans)
                    if (speakOn || voice) speak(ans)
                }
            } catch (e: Exception) {
                main.post { clearStatus(); live?.let { chat.removeView(it) }; live = null; addMsg(false, "Fel: ${e.message}") }
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
                        "Matcha språk och ton i konversationen och härma användarens stil om stilprov finns." + Store.styleBlock(svc) +
                        " Svara ENDAST med själva svarstexten: ingen inledning, inga citattecken, ingen förklaring.",
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
        reveal(draftCard, 8, 230L)
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
        Store.addStyleSample(svc, draftEdit.text.toString())
        val cm = svc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("svar", draftEdit.text.toString()))
        setStatus("Kopierat.")
        main.postDelayed({ clearStatus() }, 1500)
    }

    private fun insertDraft(send: Boolean) {
        val text = draftEdit.text.toString()
        if (text.isBlank()) return
        Store.addStyleSample(svc, text)
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
            reveal(confirmCard, 6, 190L)
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
        convoBtn.background = rounded(if (convo) PRIMARY else 0x00000000, 14)
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
