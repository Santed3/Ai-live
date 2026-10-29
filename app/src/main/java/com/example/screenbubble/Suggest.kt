package com.example.screenbubble

data class Chip(val label: String, val prompt: String, val reply: Boolean = false)

/** Context-aware shortcuts based on the foreground app and the visible text. */
object Suggest {
    private val mail = listOf(
        "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider",
        "ch.protonmail.android", "com.yahoo.mobile.client.android.mail", "com.fsck.k9"
    )
    private val chat = listOf(
        "com.whatsapp", "com.google.android.apps.messaging", "com.samsung.android.messaging",
        "org.telegram.messenger", "com.facebook.orca", "com.instagram.android", "com.snapchat.android",
        "com.discord", "com.Slack", "com.microsoft.teams", "org.thoughtcrime.securesms", "com.android.mms"
    )
    private val browsers = listOf(
        "com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser",
        "com.microsoft.emmx", "com.brave.browser", "com.opera.browser"
    )

    fun isChat(pkg: String) = pkg in chat

    fun forScreen(pkg: String, text: String): List<Chip> {
        val out = ArrayList<Chip>()
        when {
            pkg in mail -> {
                out.add(Chip("Svara på mejl", "", reply = true))
                out.add(Chip("Sammanfatta mejlet", "Sammanfatta mejlet på skärmen i några punkter och säg vad som förväntas av mig."))
                out.add(Chip("Skriv nytt mejl", "Hjälp mig skriva ett nytt mejl. Fråga vad det ska handla om och till vem."))
            }
            pkg in chat -> {
                out.add(Chip("Föreslå svar", "", reply = true))
                out.add(Chip("Sammanfatta chatten", "Sammanfatta konversationen på skärmen kort."))
                out.add(Chip("Översätt", "Översätt de senaste meddelandena på skärmen till svenska (eller engelska om de redan är på svenska)."))
            }
            pkg in browsers -> {
                out.add(Chip("Sammanfatta sidan", "Sammanfatta sidan på skärmen i korta punkter."))
                out.add(Chip("Sök mer om detta", "Sök på webben efter mer information om ämnet på skärmen och sammanfatta vad du hittar."))
                out.add(Chip("Förklara", "Förklara enkelt det som står på skärmen."))
            }
        }
        if (text.contains('?') && text.length < 1500) {
            out.add(Chip("Svara på frågan", "Svara på frågan som syns på skärmen. Ge svaret tydligt."))
            out.add(Chip("Sök på Google", "Sök på webben efter svaret på frågan på skärmen och förklara det kort."))
        }
        if (out.size < 4) out.add(Chip("Förklara skärmen", "Förklara kort vad som visas på skärmen."))
        if (out.size < 4) out.add(Chip("Sammanfatta", "Sammanfatta texten på skärmen kort."))
        if (out.size < 5) out.add(Chip("Översätt", "Översätt texten på skärmen till svenska (eller engelska om den redan är svenska)."))
        if (out.size < 6) out.add(Chip("Skriv text", "Hjälp mig skriva en text utifrån det som visas på skärmen. Fråga om något saknas."))
        if (out.size < 7) out.add(Chip("Fixa", "Hitta fel i texten på skärmen (stavning, fakta, logik) och föreslå rättelser."))
        return out.distinctBy { it.label }.take(7)
    }
}
