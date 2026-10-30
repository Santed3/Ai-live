package com.example.screenbubble

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Proactive reply suggestions in chat apps (opt-in) and learning of how the user writes (opt-in).
 * Everything stays local except the on-screen chat text sent to OpenAI when suggestions are on.
 */
class SmartReply(private val svc: ReaderService) {
    private val main = Handler(Looper.getMainLooper())
    private val debounce = Runnable { generate() }

    @Volatile private var inFlight = false
    private var lastHash = 0
    private var lastCall = 0L

    // typing tracker for learning
    private var prevLen = 0
    private var lastTyped = ""
    private var lastTypedAt = 0L

    private fun suggestOn() = Store.flag(svc, "smart_suggest")
    private fun learnOn() = Store.flag(svc, "learn_style")

    fun onEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg == svc.packageName) return
        val chatApp = Suggest.isChat(pkg)
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !pkg.contains("inputmethod") && pkg != "com.android.systemui") {
            if (!chatApp) { svc.hideSuggestions(); lastHash = 0 }
        }
        if (!chatApp) return
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED && learnOn()) track(e)
        if (suggestOn() && (e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            main.removeCallbacks(debounce)
            main.postDelayed(debounce, 450)
        }
    }

    private fun track(e: AccessibilityEvent) {
        try {
            val src = e.source
            if (src != null && (src.isPassword || !src.isEditable)) return
            val text = e.text?.joinToString("") { it?.toString().orEmpty() }.orEmpty()
            val hint = src?.isShowingHintText == true
            val len = if (hint) 0 else text.length
            val now = System.currentTimeMillis()
            if (len > 0) {
                lastTyped = text
                lastTypedAt = now
            } else if (prevLen >= 2 && lastTyped.length >= 2 && now - lastTypedAt < 30_000) {
                // The field was emptied in one go: the message was almost certainly sent.
                val n = Store.addChatSample(svc, lastTyped)
                lastTyped = ""
                refreshProfile(n)
            }
            prevLen = len
        } catch (_: Exception) {}
    }

    /** Every 10 new samples the style profile is rebuilt in the background. */
    private fun refreshProfile(n: Int) {
        if (n < 8 || n - Store.profileMark(svc) < 10) return
        Store.setProfileMark(svc, n)
        val key = svc.apiKey()
        if (key.isEmpty()) return
        svc.pool.execute {
            try {
                val p = OpenAiClient(key, "gpt-4o-mini").styleProfile(Store.chatSamples(svc).takeLast(60))
                if (p.isNotBlank()) Store.setStyleProfile(svc, p)
            } catch (_: Exception) {}
        }
    }

    private fun styleBlock(): String {
        val sb = StringBuilder()
        val prof = Store.styleProfile(svc)
        if (prof.isNotBlank()) sb.append("\nSå skriver användaren: ").append(prof)
        val samples = Store.chatSamples(svc).takeLast(14)
        if (samples.isNotEmpty()) sb.append("\nRiktiga meddelanden användaren skickat:\n").append(samples.joinToString("\n") { "- " + it.replace("\n", " ") })
        val drafts = Store.styleSamples(svc).takeLast(3)
        if (drafts.isNotEmpty()) sb.append("\nGodkända utkast:\n").append(drafts.joinToString("\n") { "- " + it.replace("\n", " ") })
        val mem = Store.memory(svc).takeLast(12)
        if (mem.isNotEmpty()) sb.append("\nDet du vet om användaren och personerna runt hen:\n").append(mem.joinToString("\n") { "- $it" })
        return sb.toString()
    }

    private fun generate() {
        if (!suggestOn() || !svc.canShowSuggestions()) return
        if (inFlight) { main.postDelayed(debounce, 600); return }
        val key = svc.apiKey()
        if (key.isEmpty()) return
        val wait = 1200 - (System.currentTimeMillis() - lastCall)
        if (wait > 0) { main.postDelayed(debounce, wait); return }
        inFlight = true
        svc.pool.execute {
            try {
                if (!Suggest.isChat(svc.currentPkg())) return@execute
                // Editable fields are skipped so typing does not retrigger suggestions.
                val tail = svc.readScreenText(skipEditable = true).takeLast(1400)
                val h = tail.hashCode()
                if (tail.isBlank() || h == lastHash) return@execute
                lastHash = h
                lastCall = System.currentTimeMillis()
                main.post { svc.showSuggestionLoading() }
                val list = OpenAiClient(key, "gpt-4o-mini").suggestReplies(tail, styleBlock())
                main.post { if (list.isEmpty()) svc.hideSuggestions() else svc.showSuggestions(list) }
            } catch (_: Exception) {
                main.post { svc.hideSuggestions() }
            } finally {
                inFlight = false
            }
        }
    }
}
