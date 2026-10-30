package com.example.screenbubble

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Quiet, event-driven screen observer. It only inspects local accessibility text after
 * meaningful UI changes and surfaces a card for high-confidence failure states.
 * It does not send text to a model or operate the device until the user taps the card.
 */
class ActiveHelp(private val svc: ReaderService) {
    private val main = Handler(Looper.getMainLooper())
    private var lastScreenSignature = ""
    private var lastShownSignature = ""
    private var lastShownAt = 0L
    private var dismissedUntil = 0L
    private var checks = 0

    private val inspect = Runnable { inspectScreen() }

    fun onEvent(event: AccessibilityEvent) {
        if (!Store.flag(svc, "active_help") || !svc.canShowActiveHelp()) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank() || pkg == svc.packageName || pkg == "com.android.systemui" || pkg.contains("inputmethod")) return
        if (Suggest.isCommunication(pkg)) return // Smart Reply already handles this context.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        main.removeCallbacks(inspect)
        main.postDelayed(inspect, 1400)
    }

    private fun inspectScreen() {
        if (!Store.flag(svc, "active_help") || !svc.canShowSuggestions()) return
        val pkg = svc.currentPkg()
        if (pkg.isBlank() || pkg == svc.packageName || pkg == "com.android.systemui" || pkg.contains("inputmethod")) return
        val screen = svc.readScreenText().take(5000)
        if (screen.isBlank()) return
        val signature = (pkg + "|" + screen.lowercase().replace(Regex("\\s+"), " ").take(1800)).hashCode().toString()
        if (signature == lastScreenSignature) checks++ else { lastScreenSignature = signature; checks = 1 }
        if (System.currentTimeMillis() < dismissedUntil || System.currentTimeMillis() - lastShownAt < 90_000) return
        val normalized = screen.lowercase()
        val download = Regex("(download|downloading|nedladd|hämtar|hämtning)").containsMatchIn(normalized)
        val strongFailure = listOf(
            "download failed", "couldn't download", "could not download", "failed to download",
            "nedladdningen misslyckades", "kunde inte ladda ner", "hämtningen misslyckades",
            "something went wrong", "not responding", "application isn't responding",
            "åtkomst nekad", "access denied", "permission denied", "kunde inte slutföra",
            "couldn't complete", "could not complete", "network error", "nätverksfel",
            "connection failed", "anslutningen misslyckades", "installation failed",
            "installationen misslyckades", "upload failed", "couldn't upload", "could not upload",
            "kunde inte ladda upp", "uppladdningen misslyckades", "file type not supported",
            "unsupported file type", "filtypen stöds inte", "invalid email address",
            "ogiltig e-postadress", "required field", "field is required", "obligatoriskt fält",
            "fältet är obligatoriskt", "server error", "form submission failed",
            "kunde inte skicka formuläret"
        ).any { normalized.contains(it) }
        val pairedFailure = download && listOf("failed", "error", "blocked", "misslyck", "fel", "kunde inte", "couldn't", "försök igen").any { normalized.contains(it) }
        if (!strongFailure && !pairedFailure) return
        // Require either an explicit failure phrase or the same failure state to persist across checks.
        if (!strongFailure && checks < 2) return
        if (signature == lastShownSignature) return
        lastShownSignature = signature
        lastShownAt = System.currentTimeMillis()
        val title = if (download) "Nedladdningen verkar ha problem" else "Det verkar ha uppstått ett problem"
        val detail = if (download) "Jag kan undersöka varför filen inte laddas ner och visa hur vi löser det." else "Jag kan undersöka vad som verkar ha fastnat och föreslå nästa steg."
        val prompt = "Active Help upptäckte ett möjligt problem i appen $pkg. Undersök den aktuella skärmen och förklara kort vad som verkar ha hänt och hur det kan lösas. Gör inga ändringar eller klicka inte på något ännu. Fråga först om jag vill att du tar över och försöker lösa problemet. Om det behövs en åtgärd, förklara vad du tänker göra innan du ber om godkännande."
        svc.showActiveHelp(title, detail, prompt)
    }

    fun dismiss() {
        dismissedUntil = System.currentTimeMillis() + 180_000
        svc.hideSuggestions()
    }
}
