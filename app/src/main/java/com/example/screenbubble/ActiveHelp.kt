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
    // Keep only coarse workflow stages in memory. Never retain screen text or field values.
    private var workflowPackage = ""
    private var lastWorkflowStage = ""
    private val workflowTrail = ArrayDeque<String>()

    private val inspect = Runnable { inspectScreen() }

    private fun workflowStage(screen: String): String {
        val s = screen.lowercase()
        return when {
            listOf("download", "downloading", "nedladd", "hämtar", "hämtning").any { s.contains(it) } -> "nedladdning"
            listOf("upload", "uploading", "ladda upp", "uppladd", "bifoga fil").any { s.contains(it) } -> "uppladdning"
            listOf("checkout", "payment", "betalning", "kassa", "order summary", "slutför köp").any { s.contains(it) } -> "köp/betalning"
            listOf("sign in", "log in", "logga in", "lösenord", "password", "two-factor", "verification code").any { s.contains(it) } -> "inloggning"
            listOf("search", "sök", "results", "sökresultat", "filter").any { s.contains(it) } -> "sökning"
            listOf("new message", "compose", "skriv meddelande", "nytt meddelande", "recipient", "mottagare").any { s.contains(it) } -> "meddelande"
            listOf("submit", "send", "skicka", "submit form", "obligatoriskt fält", "required field").any { s.contains(it) } -> "formulär"
            listOf("calendar", "kalender", "schedule", "schemalägg", "date", "datum").any { s.contains(it) } -> "planering"
            listOf("save as", "spara som", "open file", "öppna fil", "choose file", "välj fil").any { s.contains(it) } -> "filhantering"
            listOf("settings", "inställningar", "preferences", "preferenser").any { s.contains(it) } -> "inställningar"
            else -> ""
        }
    }

    private fun recordWorkflow(pkg: String, screen: String) {
        if (pkg != workflowPackage) {
            workflowPackage = pkg
            lastWorkflowStage = ""
            workflowTrail.clear()
        }
        val stage = workflowStage(screen)
        if (stage.isBlank() || stage == lastWorkflowStage) return
        lastWorkflowStage = stage
        if (workflowTrail.lastOrNull() != stage) workflowTrail.addLast(stage)
        while (workflowTrail.size > 6) workflowTrail.removeFirst()
    }

    private fun workflowContext(): String =
        if (workflowTrail.isEmpty()) "" else workflowTrail.joinToString(" → ")

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
        recordWorkflow(pkg, screen)
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
            "kunde inte skicka formuläret",
            "session expired", "session has expired", "logga in igen", "sessionen har gått ut",
            "sync failed", "synchronization failed", "synkronisering misslyckades", "couldn't sync",
            "not enough storage", "disk full", "storage full", "ont om lagringsutrymme",
            "printer offline", "printer not responding", "skrivaren är offline",
            "service unavailable", "server unavailable", "temporarily unavailable", "tillfälligt otillgänglig",
            "too many attempts", "för många försök", "account locked", "kontot är låst",
            "can't connect", "could not connect", "kunde inte ansluta"
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
        val trail = workflowContext()
        val prompt = "Active Help upptäckte ett möjligt problem i appen $pkg. Undersök den aktuella skärmen och förklara kort vad som verkar ha hänt och hur det kan lösas. " +
            (if (trail.isBlank()) "" else "Observerade steg i det här arbetsflödet, i ordning: $trail. Använd dem för att förstå sammanhanget, men anta inte att varje steg lyckades. ") +
            "Gör inga ändringar eller klicka inte på något ännu. Fråga först om jag vill att du tar över och försöker lösa problemet. Om det behövs en åtgärd, beskriv planen och möjliga konsekvenser innan du ber om godkännande."
        svc.showActiveHelp(title, detail, prompt)
    }

    fun dismiss() {
        dismissedUntil = System.currentTimeMillis() + 180_000
        svc.hideSuggestions()
    }
}
