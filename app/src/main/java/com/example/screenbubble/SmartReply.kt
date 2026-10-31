package com.example.screenbubble

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Context-aware Smart Reply and live draft refinement.
 * Suggestions are only inserted after a user tap; this class never sends a message.
 */
class SmartReply(private val svc: ReaderService) {
    private val main = Handler(Looper.getMainLooper())
    private val debounce = Runnable { generateReply() }
    private val draftDebounce = Runnable { generateDraft(pendingDraft) }

    @Volatile private var inFlight = false
    @Volatile private var pendingDraft = ""
    private var lastReplyHash = 0
    private var lastDraftHash = 0
    private var lastCall = 0L
    private var lastShownSignature = ""
    private var activePackage = ""

    // Learning tracker: only a cleared, previously typed message is treated as sent.
    private var prevLen = 0
    private var lastTyped = ""
    private var lastTypedAt = 0L

    @Volatile private var lastAcceptedDraft = ""

    fun markAccepted(text: String) {
        lastAcceptedDraft = text.trim()
        pendingDraft = text.trim()
        lastDraftHash = (svc.currentPkg() + "|" + text.trim()).hashCode()
    }

    private fun suggestOn() = Store.flag(svc, "smart_suggest")
    private fun learnOn() = Store.flag(svc, "learn_style")
    private fun allowed(pkg: String) = Suggest.isCommunication(pkg) && Store.smartReplyAppEnabled(svc, pkg)

    fun onEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg == svc.packageName) return
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            !pkg.contains("inputmethod") && pkg != "com.android.systemui") {
            if (activePackage.isNotEmpty() && activePackage != pkg) {
                pendingDraft = ""
                main.removeCallbacks(draftDebounce)
                main.removeCallbacks(debounce)
                svc.hideSuggestions()
                lastReplyHash = 0
                lastDraftHash = 0
                lastShownSignature = ""
                lastAcceptedDraft = ""
            }
            activePackage = pkg
            if (!allowed(pkg)) {
                pendingDraft = ""
                main.removeCallbacks(draftDebounce)
                main.removeCallbacks(debounce)
                svc.hideSuggestions()
                lastReplyHash = 0
                lastDraftHash = 0
                lastShownSignature = ""
            }
        }
        if (!allowed(pkg)) return

        if (e.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val src = e.source
            val validSource = src != null && src.isEditable && !src.isPassword
            val current = if (validSource) (src?.text?.toString() ?: e.text?.joinToString("") { it?.toString().orEmpty() }.orEmpty()) else svc.focusedInputText()
            val hint = validSource && src?.isShowingHintText == true
            if (learnOn()) track(current, hint)
            if (lastAcceptedDraft.isNotEmpty() && current.trim() == lastAcceptedDraft) return
            if (lastAcceptedDraft.isNotEmpty() && current.trim() != lastAcceptedDraft) lastAcceptedDraft = ""
            if (suggestOn() && !hint && current.trim().length >= 4) {
                pendingDraft = current
                main.removeCallbacks(debounce)
                main.removeCallbacks(draftDebounce)
                main.postDelayed(draftDebounce, 950)
            } else if (current.isBlank()) {
                pendingDraft = ""
                main.removeCallbacks(draftDebounce)
                svc.hideSuggestions()
                if (suggestOn()) {
                    main.removeCallbacks(debounce)
                    main.postDelayed(debounce, 650)
                }
            }
            return
        }

        if (suggestOn() && pendingDraft.isBlank() &&
            (e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
             e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            main.removeCallbacks(debounce)
            main.postDelayed(debounce, 550)
        }
    }

    private fun track(current: String, hint: Boolean) {
        val now = System.currentTimeMillis()
        val len = if (hint) 0 else current.length
        if (len > 0) {
            lastTyped = current
            lastTypedAt = now
        } else if (prevLen >= 2 && lastTyped.length >= 2 && now - lastTypedAt < 30_000) {
            val n = Store.addChatSample(svc, lastTyped)
            lastTyped = ""
            refreshProfile(n)
        }
        prevLen = len
    }

    /** Every 10 new, actually sent messages the writing-style profile is refreshed. */
    private fun refreshProfile(n: Int) {
        if (n < 8 || n - Store.profileMark(svc) < 10) return
        Store.setProfileMark(svc, n)
        svc.pool.execute {
            try {
                val p = OpenAiClient(svc, "gpt-4o-mini").styleProfile(Store.chatSamples(svc).takeLast(60))
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
        return sb.toString()
    }

    private fun canRun(pkg: String): Boolean =
        suggestOn() && svc.canShowSuggestions() && allowed(pkg)

    private fun generateDraft(draft: String) {
        val currentDraft = draft.trim()
        val pkg = svc.currentPkg()
        if (currentDraft.length < 4 || !canRun(pkg)) return
        if (inFlight) { main.postDelayed(draftDebounce, 500); return }
        val hash = (pkg + "|" + currentDraft).hashCode()
        if (hash == lastDraftHash) return
        val wait = 1100 - (System.currentTimeMillis() - lastCall)
        if (wait > 0) { main.postDelayed(draftDebounce, wait); return }
        inFlight = true
        main.post { svc.showSuggestionLoading() }
        svc.pool.execute {
            try {
                val context = svc.readScreenText(skipEditable = true).takeLast(1200)
                val result = OpenAiClient(svc, "gpt-4o-mini")
                    .suggestDraft(currentDraft, context, styleBlock(), Suggest.kind(pkg))
                lastDraftHash = hash
                lastCall = System.currentTimeMillis()
                val signature = result.joinToString("|").hashCode().toString()
                main.post {
                    if (pendingDraft.trim() != currentDraft || svc.currentPkg() != pkg) return@post
                    if (result.isEmpty()) svc.hideSuggestions()
                    else if (signature != lastShownSignature) {
                        lastShownSignature = signature
                        svc.showSuggestions(result, "Förslag på din text")
                    } else svc.hideSuggestions()
                }
            } catch (_: Exception) {
                main.post { svc.hideSuggestions() }
            } finally {
                inFlight = false
            }
        }
    }

    private fun generateReply() {
        val pkg = svc.currentPkg()
        if (!canRun(pkg) || pendingDraft.isNotBlank()) return
        if (inFlight) { main.postDelayed(debounce, 600); return }
        val wait = 1200 - (System.currentTimeMillis() - lastCall)
        if (wait > 0) { main.postDelayed(debounce, wait); return }
        inFlight = true
        svc.pool.execute {
            try {
                if (!allowed(svc.currentPkg())) return@execute
                val tail = svc.readScreenText(skipEditable = true).takeLast(1600)
                val hash = (pkg + "|" + tail).hashCode()
                if (tail.isBlank() || hash == lastReplyHash) return@execute
                main.post { svc.showSuggestionLoading() }
                val list = OpenAiClient(svc, "gpt-4o-mini")
                    .suggestReplies(tail, styleBlock(), Suggest.kind(pkg))
                lastReplyHash = hash
                lastCall = System.currentTimeMillis()
                val signature = list.joinToString("|").hashCode().toString()
                main.post {
                    if (svc.currentPkg() != pkg || pendingDraft.isNotBlank()) return@post
                    if (list.isEmpty()) svc.hideSuggestions()
                    else if (signature != lastShownSignature) {
                        lastShownSignature = signature
                        svc.showSuggestions(list, if (Suggest.kind(pkg) == "mejl") "Förslag på mejlsvar" else "Förslag på svar")
                    } else svc.hideSuggestions()
                }
            } catch (_: Exception) {
                main.post { svc.hideSuggestions() }
            } finally {
                inFlight = false
            }
        }
    }
}
