package com.example.screenbubble

import android.content.Context
import java.util.Locale

/** Runtime translations for ScreenBubble's native Android interface. */
object UiText {
    private val translations = mapOf(
        "en" to mapOf(
            "Inställningar" to "Settings", "Kom igång" to "Get started", "Använd" to "Use",
            "Öppna menyn" to "Open menu", "Slå på tjänsten först" to "Enable the service first",
            "Tillbaka till sidebar" to "Back to sidebar", "Hide-läge" to "Hide mode",
            "Markera text i valfri app och välj \"Fråga ScreenBubble\" för att få den förklarad." to "Select text in any app and choose \"Ask ScreenBubble\" to have it explained.",
            "Assistentens utseende" to "Assistant appearance", "Välj hur du vill öppna ScreenBubble ovanpå andra appar." to "Choose how you want to open ScreenBubble over other apps.",
            "Sidopanel" to "Sidebar", "Flytande" to "Floating", "Svep" to "Swipe",
            "vänster" to "left", "höger" to "right", "Svep åt " to "Swipe ",
            " för att öppna menyn. Svep åt andra hållet för att öppna chatten." to " to open the menu. Swipe the other way to open chat.",
            "Byt svepriktning" to "Change swipe direction", "Språk" to "Language",
            "Följ enhetens språk" to "Follow device language", "Välj språk" to "Choose language",
            "Svenska" to "Swedish", "English" to "English", "Deutsch" to "German", "Español" to "Spanish", "Français" to "French", "Norsk" to "Norwegian", "Dansk" to "Danish", "Suomi" to "Finnish",
            "Smarta svar" to "Smart replies", "Active Help – proaktiv hjälp" to "Active Help – proactive assistance",
            "Föreslå svar och förbättra utkast" to "Suggest replies and improve drafts",
            "Clean Writing – rätta text automatiskt" to "Clean Writing – correct text automatically",
            "Lär dig hur jag skriver" to "Learn how I write", "Appprofiler" to "App profiles",
            "E-post och meddelanden" to "Email and messages", "Studier och lärande" to "Study and learning",
            "Skrivande och dokument" to "Writing and documents", "Webb och research" to "Web and research",
            "Egna snabbkommandon" to "Custom shortcuts", "＋ Nytt kommando" to "＋ New shortcut",
            "Anteckningar" to "Notes", "Öppna anteckningsbibliotek" to "Open notes library",
            "Minne" to "Memory", "Lär av våra samtal automatiskt" to "Learn from our conversations automatically",
            "Glöm allt" to "Forget all", "Senaste fel" to "Latest error", "Så funkar det" to "How it works",
            "Ny version finns" to "New version available", "Uppdatering" to "Update",
            "Slå på tjänsten" to "Enable service", "Tillgänglighet → ScreenBubble → På." to "Accessibility → ScreenBubble → On.",
            "Öppna" to "Open", "Tillåt mikrofon" to "Allow microphone", "För röststyrning och samtal." to "For voice control and calls.",
            "Tillåt uppdateringar" to "Allow updates", "Så kan appen uppdatera sig själv." to "This lets the app update itself.",
            "Tillåt" to "Allow", "Tryck på sidopanelen eller den flytande symbolen för att öppna chatten. Tryck på … för fler funktioner." to "Tap the sidebar or floating bubble to open chat. Tap … for more features.",
            "Föreslå svar" to "Suggest a reply", "Rensa" to "Clear", "Säker?" to "Sure?",
            "Hej! Vad vill du göra?" to "Hi! What would you like to do?",
            "Jag ser din skärm och kan hjälpa dig direkt." to "I can see your screen and help right away.",
            "Sök i alla konversationer…" to "Search all conversations…", "Lyssnar…" to "Listening…",
            "Ja, gör det" to "Yes, do it", "Nej" to "No", "Skicka det här meddelandet?" to "Send this message?",
            "Tryck i textfältet först" to "Tap a text field first", "Ny version av ScreenBubble finns. Öppna appen för att uppdatera." to "A new version of ScreenBubble is available. Open the app to update.",
            "Slutför och öppna appen" to "Finish and open app", "Slutför stegen ovan först." to "Complete the steps above first.",
            "Emoji (valfri)" to "Emoji (optional)", "Namn, t.ex. Svara artigt" to "Name, e.g. Reply politely",
            "Instruktion, t.ex. Skriv ett artigt svar på svenska till meddelandet på skärmen." to "Instruction, e.g. Write a polite reply to the message on screen.",
            "Använd skärmen" to "Use screen context", "Nytt snabbkommando" to "New shortcut",
            "Redigera snabbkommando" to "Edit shortcut", "Spara" to "Save", "Avbryt" to "Cancel",
            "Fyll i namn och instruktion" to "Enter a name and instruction",
            "Du bestämmer vad ScreenBubble ska komma ihåg. Du kan granska och radera sparade uppgifter här." to "You decide what ScreenBubble remembers. Review and delete saved items here.",
            "Det här har jag lärt mig om dig:" to "This is what I've learned about you:",
            "När detta är på kan ScreenBubble spara användbara, långsiktiga fakta. Känsliga uppgifter filtreras bort. Du kan alltid säga ”kom ihåg …” eller ta bort sparade minnen." to "When enabled, ScreenBubble can save useful long-term facts. Sensitive information is filtered out. You can say “remember …” or delete saved memories at any time.",
            "Glöm allt" to "Forget all"
        ),
        "de" to mapOf("Inställningar" to "Einstellungen","Kom igång" to "Erste Schritte","Använd" to "Verwenden","Öppna menyn" to "Menü öffnen","Slå på tjänsten först" to "Aktiviere zuerst den Dienst","Tillbaka till sidebar" to "Zurück zur Seitenleiste","Hide-läge" to "Ausblendmodus","Assistentens utseende" to "Darstellung des Assistenten","Sidopanel" to "Seitenleiste","Flytande" to "Schwebend","Svep" to "Wischen","Språk" to "Sprache","Följ enhetens språk" to "Gerätesprache verwenden","Välj språk" to "Sprache auswählen","Smarta svar" to "Intelligente Antworten","Appprofiler" to "App-Profile","Anteckningar" to "Notizen","Minne" to "Speicher","Rensa" to "Löschen","Säker?" to "Sicher?","Ja, gör det" to "Ja, ausführen","Nej" to "Nein","Skicka" to "Senden","Spara" to "Speichern","Avbryt" to "Abbrechen","Öppna" to "Öffnen","Tillåt" to "Erlauben","Lyssnar…" to "Hört zu…"),
        "es" to mapOf("Inställningar" to "Ajustes","Kom igång" to "Empezar","Använd" to "Usar","Öppna menyn" to "Abrir menú","Slå på tjänsten först" to "Activa primero el servicio","Tillbaka till sidebar" to "Volver al panel lateral","Hide-läge" to "Modo oculto","Assistentens utseende" to "Apariencia del asistente","Sidopanel" to "Panel lateral","Flytande" to "Flotante","Svep" to "Deslizar","Språk" to "Idioma","Följ enhetens språk" to "Seguir el idioma del dispositivo","Välj språk" to "Elegir idioma","Smarta svar" to "Respuestas inteligentes","Appprofiler" to "Perfiles de aplicaciones","Anteckningar" to "Notas","Minne" to "Memoria","Rensa" to "Borrar","Säker?" to "¿Seguro?","Ja, gör det" to "Sí, hacerlo","Nej" to "No","Skicka" to "Enviar","Spara" to "Guardar","Avbryt" to "Cancelar","Öppna" to "Abrir","Tillåt" to "Permitir","Lyssnar…" to "Escuchando…"),
        "fr" to mapOf("Inställningar" to "Paramètres","Kom igång" to "Bien démarrer","Använd" to "Utiliser","Öppna menyn" to "Ouvrir le menu","Slå på tjänsten först" to "Activez d’abord le service","Tillbaka till sidebar" to "Retour au panneau latéral","Hide-läge" to "Mode masqué","Assistentens utseende" to "Apparence de l’assistant","Sidopanel" to "Panneau latéral","Flytande" to "Flottant","Svep" to "Balayer","Språk" to "Langue","Följ enhetens språk" to "Suivre la langue de l’appareil","Välj språk" to "Choisir la langue","Smarta svar" to "Réponses intelligentes","Appprofiler" to "Profils d’applications","Anteckningar" to "Notes","Minne" to "Mémoire","Rensa" to "Effacer","Säker?" to "Confirmer ?","Ja, gör det" to "Oui, exécuter","Nej" to "Non","Skicka" to "Envoyer","Spara" to "Enregistrer","Avbryt" to "Annuler","Öppna" to "Ouvrir","Tillåt" to "Autoriser","Lyssnar…" to "Écoute…"),
        "no" to mapOf("Inställningar" to "Innstillinger","Kom igång" to "Kom i gang","Använd" to "Bruk","Öppna menyn" to "Åpne menyen","Slå på tjänsten först" to "Slå på tjenesten først","Tillbaka till sidebar" to "Tilbake til sidepanelet","Hide-läge" to "Skjult modus","Assistentens utseende" to "Assistentens utseende","Sidopanel" to "Sidepanel","Flytande" to "Flytende","Svep" to "Sveip","Språk" to "Språk","Följ enhetens språk" to "Følg enhetens språk","Välj språk" to "Velg språk","Smarta svar" to "Smarte svar","Appprofiler" to "App-profiler","Anteckningar" to "Notater","Minne" to "Minne","Rensa" to "Tøm","Säker?" to "Sikker?","Ja, gör det" to "Ja, gjør det","Nej" to "Nei","Skicka" to "Send","Spara" to "Lagre","Avbryt" to "Avbryt","Öppna" to "Åpne","Tillåt" to "Tillat","Lyssnar…" to "Lytter…"),
        "da" to mapOf("Inställningar" to "Indstillinger","Kom igång" to "Kom godt i gang","Använd" to "Brug","Öppna menyn" to "Åbn menuen","Slå på tjänsten först" to "Aktivér tjenesten først","Tillbaka till sidebar" to "Tilbage til sidepanelet","Hide-läge" to "Skjult tilstand","Assistentens utseende" to "Assistentens udseende","Sidopanel" to "Sidepanel","Flytande" to "Flydende","Svep" to "Swipe","Språk" to "Sprog","Följ enhetens språk" to "Følg enhedens sprog","Välj språk" to "Vælg sprog","Smarta svar" to "Smarte svar","Appprofiler" to "App-profiler","Anteckningar" to "Noter","Minne" to "Hukommelse","Rensa" to "Ryd","Säker?" to "Sikker?","Ja, gör det" to "Ja, gør det","Nej" to "Nej","Skicka" to "Send","Spara" to "Gem","Avbryt" to "Annuller","Öppna" to "Åbn","Tillåt" to "Tillad","Lyssnar…" to "Lytter…"),
        "fi" to mapOf("Inställningar" to "Asetukset","Kom igång" to "Aloita","Använd" to "Käytä","Öppna menyn" to "Avaa valikko","Slå på tjänsten först" to "Ota palvelu ensin käyttöön","Tillbaka till sidebar" to "Takaisin sivupaneeliin","Hide-läge" to "Piilotustila","Assistentens utseende" to "Avustajan ulkoasu","Sidopanel" to "Sivupaneeli","Flytande" to "Kelluva","Svep" to "Pyyhkäisy","Språk" to "Kieli","Följ enhetens språk" to "Käytä laitteen kieltä","Välj språk" to "Valitse kieli","Smarta svar" to "Älykkäät vastaukset","Appprofiler" to "Sovellusprofiilit","Anteckningar" to "Muistiinpanot","Minne" to "Muisti","Rensa" to "Tyhjennä","Säker?" to "Oletko varma?","Ja, gör det" to "Kyllä, tee se","Nej" to "Ei","Skicka" to "Lähetä","Spara" to "Tallenna","Avbryt" to "Peruuta","Öppna" to "Avaa","Tillåt" to "Salli","Lyssnar…" to "Kuunnellaan…")
    )

    fun language(context: Context): String {
        val saved = context.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("ui_language", "system") ?: "system"
        if (saved != "system") return saved
        val system = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (system in translations.keys || system == "sv") system else "en"
    }

    fun t(context: Context, value: String): String {
        val lang = language(context)
        if (lang == "sv") return value
        val map = translations[lang] ?: translations["en"].orEmpty()
        return UiTextExtra.t(lang, value)
    }

    fun save(context: Context, language: String) {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("ui_language", language).apply()
    }

    fun languageLabel(language: String): String = when(language) {
        "system" -> "Följ enhetens språk"; "sv" -> "Svenska"; "en" -> "English"; "de" -> "Deutsch"
        "es" -> "Español"; "fr" -> "Français"; "no" -> "Norsk"; "da" -> "Dansk"; "fi" -> "Suomi"; else -> "English"
    }
}
