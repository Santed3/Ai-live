package com.example.screenbubble

/** Shared translations for the main Android screens, notes, dictation and assistant panel. */
object UiTextExtra {
    private val en = mapOf(
        "Din AI-assistent · alltid nära till hands · v\$ver" to "Your AI assistant · always within reach · v\$ver",
        "DITT INNEHÅLL" to "YOUR CONTENT", "Version 1.0.${nb!! - 100} är redo. Din data och inställningar behålls." to "Version 1.0.${nb!! - 100} is ready. Your data and settings will be kept.",
        "Allt viktigt är på." to "Everything important is on.", "Tre steg tar dig hela vägen." to "Three steps are all it takes.",
        "Tillåt begränsade inställningar" to "Allow restricted settings", "Android 13+: ⋮ i appinfo, välj \"Tillåt begränsade inställningar\"." to "Android 13+: tap ⋮ in App info and choose \"Allow restricted settings\".",
        "Slå på tjänsten" to "Enable the service", "Tillgänglighet → ScreenBubble → På." to "Accessibility → ScreenBubble → On.",
        "Tillåt mikrofon" to "Allow microphone", "För röststyrning och samtal." to "For voice control and conversations.",
        "Tillåt uppdateringar" to "Allow updates", "Så kan appen uppdatera sig själv." to "This allows the app to update itself.",
        "Använd" to "Use", "Tryck på sidopanelen eller den flytande symbolen för att öppna chatten. Tryck på … för fler funktioner." to "Tap the sidebar or floating bubble to open chat. Tap … for more features.",
        "Markera text i valfri app och välj \"Fråga ScreenBubble\" för att få den förklarad." to "Select text in any app and choose \"Ask ScreenBubble\" to have it explained.",
        "Välj hur du vill öppna ScreenBubble ovanpå andra appar." to "Choose how you want to open ScreenBubble over other apps.",
        "Svep åt vänster för att öppna menyn. Svep åt andra hållet för att öppna chatten." to "Swipe left to open the menu. Swipe the other way to open chat.",
        "Byt svepriktning" to "Change swipe direction", "Välj språk för menyer, inställningar och meddelanden." to "Choose the language for menus, settings and messages.",
        "När någon skriver till dig i en chatt får du förslag på svar direkt i sidebaren, skrivna som du skriver." to "When someone messages you, get reply suggestions in the sidebar written in your style.",
        "ScreenBubble granskar synlig skärmtext lokalt efter vanliga fel och fastnade steg. Inget skickas till AI förrän du trycker på förslaget. Den frågar alltid innan den tar över." to "ScreenBubble checks visible screen text locally for common errors and stuck steps. Nothing is sent to AI until you tap a suggestion. It always asks before taking over.",
        "Föreslå svar och förbättra utkast" to "Suggest replies and improve drafts",
        "När funktionen är på rättar ScreenBubble stavning och meningsbyggnad automatiskt efter en kort paus. Den är avstängd från början, ändrar aldrig lösenordsfält och skickar aldrig text." to "When enabled, ScreenBubble automatically corrects spelling and sentence structure after a short pause. It is off by default, never changes password fields and never sends text unless enabled.",
        "Lär dig hur jag skriver" to "Learn how I write",
        "Välj appar där Smart Reply får läsa sammanhang och föreslå svar eller förbättra ett utkast:" to "Choose apps where Smart Reply can read context and suggest replies or improve a draft:",
        "Rensa det jag lärt mig om min stil" to "Clear what I've learned about my style",
        "Hittills inlärda meddelanden: \$n." to "Messages learned so far: \$n.", "Inget inlärt än." to "Nothing learned yet.",
        "Välj hur ScreenBubble ska anpassa sig automatiskt efter appen du använder." to "Choose how ScreenBubble should adapt automatically to the app you're using.",
        "E-post och meddelanden" to "Email and messages", "Prioriterar sammanfattningar och naturliga svar i din skrivstil." to "Prioritizes summaries and natural replies in your writing style.",
        "Studier och lärande" to "Study and learning", "Förklarar stegvis och ger ledtrådar innan hela lösningen." to "Explains step by step and gives hints before the full solution.",
        "Skrivande och dokument" to "Writing and documents", "Hjälper med formulering, struktur, korrektur och dokument." to "Helps with wording, structure, proofreading and documents.",
        "Webb och research" to "Web and research", "Prioriterar källor, jämförelser och tydliga sammanfattningar." to "Prioritizes sources, comparisons and clear summaries.",
        "Knappar i menyn som kör din egen instruktion, t.ex. \"Svara artigt på svenska\"." to "Menu buttons that run your own instruction, e.g. \"Reply politely in English\".",
        "Alla dina dikteringar och egna anteckningar samlade på ett ställe." to "All your dictations and personal notes in one place.",
        "Öppna anteckningsbibliotek" to "Open notes library", "Du bestämmer vad ScreenBubble ska komma ihåg. Du kan granska och radera sparade uppgifter här." to "You decide what ScreenBubble remembers. Review and delete saved information here.",
        "Det här har jag lärt mig om dig:" to "This is what I've learned about you:",
        "När detta är på kan ScreenBubble spara användbara, långsiktiga fakta. Känsliga uppgifter filtreras bort. Du kan alltid säga ”kom ihåg …” eller ta bort sparade minnen." to "When enabled, ScreenBubble can save useful long-term facts. Sensitive information is filtered out. You can say “remember …” or delete saved memories at any time.",
        "Glöm allt" to "Forget all", "Så funkar det" to "How it works",
        "Assistenten: tryck på sidopanelen eller symbolen för att öppna chatten. Tryck på … för fler funktioner." to "Assistant: tap the sidebar or bubble to open chat. Tap … for more features.",
        "Hide-läge: bara en liten svart prick. Ett tryck ger ett kort svar, håll in för menyn, dubbeltryck stänger av." to "Hide mode: just a small black dot. Tap once for a short answer, press and hold for the menu, double-tap to turn it off.",
        "Skicka och andra känsliga åtgärder kräver alltid att du bekräftar." to "Sending and other sensitive actions always require your confirmation.",
        "Skärmens innehåll skickas till OpenAI när du ställer en fråga. Använd inte appen där hjälpmedel är förbjudna, till exempel på prov." to "Screen content is sent to OpenAI when you ask a question. Do not use the app where assistance tools are prohibited, such as during exams.",
        "Assistenten är redo när du behöver den. Öppna den direkt eller fortsätt med dina anteckningar." to "Your assistant is ready when you need it. Open it now or continue with your notes.",
        "Slutför installationen nedan så kan du få hjälp direkt ovanpå dina appar." to "Complete setup below to get help directly over your apps.",
        "Aktivera tillgänglighetstjänsten först" to "Enable the accessibility service first",
        "Låt oss göra appen redo." to "Let's get the app ready.", "Tre snabba steg. Sedan kan du använda ScreenBubble direkt ovanpå dina andra appar." to "Three quick steps. Then you can use ScreenBubble over your other apps.",
        "Vad kommer du använda ScreenBubble mest till?" to "What will you mainly use ScreenBubble for?",
        "Jag anpassar AI-läget och vilka verktyg som prioriteras. Du kan ändra allt senare." to "I'll tailor the AI mode and prioritize relevant tools. You can change everything later.",
        "Smart Reply och naturliga formuleringar" to "Smart Reply and natural wording",
        "Pedagogiska förklaringar och steg-för-steg" to "Clear explanations and step-by-step guidance",
        "Text, korrektur och dokument" to "Writing, proofreading and documents",
        "Sökning & research" to "Search and research", "Webbsökning, källor och sammanfattningar" to "Web search, sources and summaries",
        "Planering, appar och arbetsflöden" to "Planning, apps and workflows",
        "Gör klart installationen" to "Complete setup", "Öppna appinfo" to "Open app info",
        "Aktivera tillgänglighet" to "Enable accessibility", "Öppna tillgänglighet" to "Open accessibility",
        "Tillåt mikrofonen" to "Allow the microphone", "Behövs för Diktera och röststyrning. Mikrofonen används bara när du startar en röstfunktion." to "Required for Dictate and voice control. The microphone is only used when you start a voice feature.",
        "Du kan ändra utseende, röst och övriga inställningar när du är klar." to "You can change appearance, voice and other settings later.",
        "Slutför och öppna appen" to "Finish and open the app", "Slutför stegen ovan först." to "Complete the steps above first.",
        "Kunde inte söka just nu" to "Couldn't search right now", "Tillåt installation, gå tillbaka och tryck igen" to "Allow installation, go back and tap again",
        "Klart att installera. Bekräfta i rutan som visas." to "Ready to install. Confirm in the dialog shown.",
        "Diktera fritt. Jag gör texten tydlig." to "Dictate freely. I'll make the text clear.",
        "Anteckningen är sparad." to "Note saved.", "Börja skriva eller tryck på diktera…" to "Start typing or tap Dictate…",
        "Mikrofonbehörighet saknas. Tillåt mikrofonen i ScreenBubble-inställningarna." to "Microphone permission is missing. Allow microphone access in ScreenBubble settings.",
        "Taligenkänning finns inte på den här enheten." to "Speech recognition is not available on this device.",
        "Finskriver texten med AI…" to "Polishing the text with AI…", "Texten är klar. Fortsätt prata när du vill." to "The text is ready. Continue speaking whenever you like.",
        "Kunde inte starta mikrofonen. Försök igen." to "Couldn't start the microphone. Try again.",
        "Dikteringen är pausad. Texten sparas automatiskt." to "Dictation is paused. Your text is saved automatically.",
        "Mina anteckningar" to "My notes", "Dina tankar, samlade på ett ställe" to "Your thoughts, all in one place",
        "Fånga tankarna. Hitta dem igen." to "Capture your thoughts. Find them again.",
        "Sök i anteckningar…" to "Search notes…", "Dina anteckningar visas här" to "Your notes will appear here",
        "Inga träffar" to "No results", "Prova ett annat sökord." to "Try another search term.",
        "Anteckningen tas bort från den här enheten." to "The note will be deleted from this device.",
        "Fråga ScreenBubble" to "Ask ScreenBubble", "Slå på ScreenBubble i Tillgänglighet först" to "Enable ScreenBubble in Accessibility first",
        "Förslag på svar" to "Reply suggestions", "Analysera och hjälp mig  →" to "Analyse and help me  →",
        "Svara på meddelande" to "Reply to message", "Föreslå ett kort svar på meddelandet på skärmen." to "Suggest a short reply to the message on screen.",
        "Lös uppgiften" to "Solve the task", "AI-lägen" to "AI modes", "Röst" to "Voice",
        "Översätt den viktigaste texten på skärmen till svenska och behåll struktur och ton." to "Translate the key text on screen into English while preserving structure and tone.",
        "Förklara uppgiften pedagogiskt steg för steg. Ge ledtrådar innan hela lösningen och kontrollera min förståelse." to "Explain the task clearly, step by step. Give hints before the full solution and check my understanding.",
        "Hjälp mig skriva eller förbättra texten på skärmen. Matcha sammanhang och min skrivstil." to "Help me write or improve the text on screen. Match the context and my writing style.",
        "Undersök ämnet på skärmen, jämför tillförlitliga källor och sammanfatta med länkar." to "Research the topic on screen, compare reliable sources and summarize with links.",
        "Allmänt" to "General", "Aktivt läge: \$label" to "Active mode: \$label",
        "Gör texten kortare men behåll innebörden." to "Make the text shorter while preserving its meaning.",
        "Vänligare" to "Friendlier", "Skriv om i en varmare, vänligare ton." to "Rewrite in a warmer, friendlier tone.",
        "Översätt" to "Translate", "Översätt till engelska. Om texten redan är engelska: översätt till svenska." to "Translate into English. If the text is already English, translate it into Swedish.",
        "Skriv om med annan formulering men samma innebörd." to "Rephrase while keeping the same meaning.",
        "Längre" to "Longer", "Gör texten något mer utförlig och tydlig." to "Make the text a little more detailed and clear.",
        "Fråga eller säg vad jag ska göra…" to "Ask a question or tell me what to do…",
        "Jag ser din skärm och kan hjälpa dig direkt." to "I can see your screen and help right away.",
        "Sammanfatta det jag tittar på" to "Summarize what I'm looking at", "Sök i alla konversationer…" to "Search all conversations…",
        "Inga sparade konversationer än." to "No saved conversations yet.", "Inga träffar." to "No results.",
        "Tänker…" to "Thinking…", "Läser konversationen och skriver ett förslag…" to "Reading the conversation and drafting a suggestion…",
        "Skicka det här meddelandet?" to "Send this message?", "Ja, gör det" to "Yes, do it",
        "Tillåt mikrofonen i ScreenBubble-inställningarna först." to "Allow microphone access in ScreenBubble settings first.",
        "Diktering pågår. Tryck på ■ när du vill pausa." to "Dictation is active. Tap ■ to pause.",
        "Dikteringen är pausad." to "Dictation is paused.", "Taligenkänning finns inte på den här telefonen." to "Speech recognition is not available on this phone.",
        "Taligenkänningen avbröts. Tryck på mikrofonen för att försöka igen." to "Speech recognition stopped. Tap the microphone to try again.",
        "Texten är infogad. Fortsätter lyssna…" to "Text inserted. Continuing to listen…",
        "Ny version av ScreenBubble finns. Öppna appen för att uppdatera." to "A new version of ScreenBubble is available. Open the app to update.",
        "Hide-läge på\nHåll in pricken för sidebaren" to "Hide mode on\nPress and hold the dot for the sidebar",
        "Tryck i textfältet först" to "Tap a text field first", "Kunde inte läsa skärmen (skyddad skärm?)" to "Couldn't read the screen (protected screen?)",
        "Okänt fel" to "Unknown error", "Kunde inte starta appen." to "Couldn't start the app.",
        "Kunde inte öppna appen." to "Couldn't open the app.", "Kunde inte öppna adressen." to "Couldn't open the address.",
        "SMS-utkast öppnat. Användaren trycker själv på skicka." to "SMS draft opened. You press Send yourself.",
        "Kunde inte öppna SMS." to "Couldn't open SMS.", "Mejlutkast öppnat. Användaren trycker själv på skicka." to "Email draft opened. You press Send yourself.",
        "Kunde inte öppna mejl." to "Couldn't open email.", "Öppnade uppringaren." to "Opened the dialler.",
        "Kunde inte öppna uppringaren." to "Couldn't open the dialler.", "Alarm ställt på %02d:%02d." to "Alarm set for %02d:%02d.",
        "Kunde inte ställa alarm." to "Couldn't set the alarm.", "Timer startad på \$seconds sekunder." to "Timer started for \$seconds seconds.",
        "Kunde inte starta timer." to "Couldn't start the timer.", "Ingen app-skärm att trycka i." to "No app screen to tap.",
        "Textfältet är inte längre aktivt. Starta dikteringen igen." to "The text field is no longer active. Start dictation again.",
        "Diktering är avstängd i lösenordsfält." to "Dictation is disabled in password fields.",
        "Kunde inte skriva i fältet." to "Couldn't type in the field.", "Hittade inget textfält att skriva i." to "Couldn't find a text field to type in.",
        "Hittade inget textfält." to "No text field found.", "Kunde inte trycka Enter." to "Couldn't press Enter.",
        "Hittade ingen skicka-knapp. Tryck själv på skicka." to "Couldn't find a Send button. Press Send yourself.",
        "Inget går att scrolla här." to "There's nothing to scroll here.", "Kunde inte scrolla." to "Couldn't scroll.",
        "Kunde inte utföra \$action." to "Couldn't perform \$action."
    )
    private val de = mapOf(
        "Anteckningar" to "Notizen","Diktera fritt. Jag gör texten tydlig." to "Diktiere frei. Ich formuliere den Text klar.",
        "Spara" to "Speichern","Mina anteckningar" to "Meine Notizen","Dina tankar, samlade på ett ställe" to "Deine Gedanken an einem Ort",
        "Sök i anteckningar…" to "Notizen durchsuchen…","Inga träffar" to "Keine Ergebnisse","Prova ett annat sökord." to "Versuche einen anderen Suchbegriff.",
        "Fråga ScreenBubble" to "ScreenBubble fragen","Förslag på svar" to "Antwortvorschläge","Översätt" to "Übersetzen",
        "Allmänt" to "Allgemein","Vänligare" to "Freundlicher","Längre" to "Länger","Skicka" to "Senden","Stäng" to "Schließen",
        "Rensa" to "Löschen","Säker?" to "Bist du sicher?","Ja, gör det" to "Ja, ausführen","Nej" to "Nein",
        "Språk" to "Sprache","Välj språk" to "Sprache auswählen","Följ enhetens språk" to "Gerätesprache verwenden"
    )
    private val es = mapOf(
        "Anteckningar" to "Notas","Diktera fritt. Jag gör texten tydlig." to "Dicta libremente. Haré que el texto sea claro.",
        "Spara" to "Guardar","Mina anteckningar" to "Mis notas","Dina tankar, samlade på ett ställe" to "Tus ideas, en un solo lugar",
        "Sök i anteckningar…" to "Buscar notas…","Inga träffar" to "Sin resultados","Prova ett annat sökord." to "Prueba otro término.",
        "Fråga ScreenBubble" to "Preguntar a ScreenBubble","Förslag på svar" to "Sugerencias de respuesta","Översätt" to "Traducir",
        "Allmänt" to "General","Vänligare" to "Más amable","Längre" to "Más largo","Skicka" to "Enviar","Stäng" to "Cerrar",
        "Rensa" to "Borrar","Säker?" to "¿Estás seguro?","Ja, gör det" to "Sí, hacerlo","Nej" to "No",
        "Språk" to "Idioma","Välj språk" to "Elegir idioma","Följ enhetens språk" to "Seguir el idioma del dispositivo"
    )
    private val fr = mapOf(
        "Anteckningar" to "Notes","Diktera fritt. Jag gör texten tydlig." to "Dictez librement. Je rendrai le texte plus clair.",
        "Spara" to "Enregistrer","Mina anteckningar" to "Mes notes","Dina tankar, samlade på ett ställe" to "Vos idées réunies au même endroit",
        "Sök i anteckningar…" to "Rechercher dans les notes…","Inga träffar" to "Aucun résultat","Prova ett annat sökord." to "Essayez un autre terme.",
        "Fråga ScreenBubble" to "Demander à ScreenBubble","Förslag på svar" to "Suggestions de réponse","Översätt" to "Traduire",
        "Allmänt" to "Général","Vänligare" to "Plus aimable","Längre" to "Plus long","Skicka" to "Envoyer","Stäng" to "Fermer",
        "Rensa" to "Effacer","Säker?" to "Êtes-vous sûr ?","Ja, gör det" to "Oui, exécuter","Nej" to "Non",
        "Språk" to "Langue","Välj språk" to "Choisir la langue","Följ enhetens språk" to "Suivre la langue de l’appareil"
    )
    private val no = mapOf(
        "Anteckningar" to "Notater","Diktera fritt. Jag gör texten tydlig." to "Dikter fritt. Jeg gjør teksten tydelig.",
        "Spara" to "Lagre","Mina anteckningar" to "Mine notater","Dina tankar, samlade på ett ställe" to "Tankene dine samlet på ett sted",
        "Sök i anteckningar…" to "Søk i notater…","Inga träffar" to "Ingen treff","Prova ett annat sökord." to "Prøv et annet søkeord.",
        "Fråga ScreenBubble" to "Spør ScreenBubble","Förslag på svar" to "Svarforslag","Översätt" to "Oversett",
        "Allmänt" to "Generelt","Vänligare" to "Vennligere","Längre" to "Lengre","Skicka" to "Send","Stäng" to "Lukk",
        "Rensa" to "Tøm","Säker?" to "Sikker?","Ja, gör det" to "Ja, gjør det","Nej" to "Nei",
        "Språk" to "Språk","Välj språk" to "Velg språk","Följ enhetens språk" to "Følg enhetens språk"
    )
    private val da = mapOf(
        "Anteckningar" to "Noter","Diktera fritt. Jag gör texten tydlig." to "Diktér frit. Jeg gør teksten tydelig.",
        "Spara" to "Gem","Mina anteckningar" to "Mine noter","Dina tankar, samlade på ett ställe" to "Dine tanker samlet ét sted",
        "Sök i anteckningar…" to "Søg i noter…","Inga träffar" to "Ingen resultater","Prova ett annat sökord." to "Prøv et andet søgeord.",
        "Fråga ScreenBubble" to "Spørg ScreenBubble","Förslag på svar" to "Svarforslag","Översätt" to "Oversæt",
        "Allmänt" to "Generelt","Vänligare" to "Venligere","Längre" to "Længere","Skicka" to "Send","Stäng" to "Luk",
        "Rensa" to "Ryd","Säker?" to "Er du sikker?","Ja, gör det" to "Ja, gør det","Nej" to "Nej",
        "Språk" to "Sprog","Välj språk" to "Vælg sprog","Följ enhetens språk" to "Følg enhedens sprog"
    )
    private val fi = mapOf(
        "Anteckningar" to "Muistiinpanot","Diktera fritt. Jag gör texten tydlig." to "Sanele vapaasti. Teen tekstistä selkeän.",
        "Spara" to "Tallenna","Mina anteckningar" to "Omat muistiinpanot","Dina tankar, samlade på ett ställe" to "Ajatuksesi yhdessä paikassa",
        "Sök i anteckningar…" to "Hae muistiinpanoista…","Inga träffar" to "Ei tuloksia","Prova ett annat sökord." to "Kokeile toista hakusanaa.",
        "Fråga ScreenBubble" to "Kysy ScreenBubblelta","Förslag på svar" to "Vastaus ehdotukset","Översätt" to "Käännä",
        "Allmänt" to "Yleinen","Vänligare" to "Ystävällisempi","Längre" to "Pidempi","Skicka" to "Lähetä","Stäng" to "Sulje",
        "Rensa" to "Tyhjennä","Säker?" to "Oletko varma?","Ja, gör det" to "Kyllä, tee se","Nej" to "Ei",
        "Språk" to "Kieli","Välj språk" to "Valitse kieli","Följ enhetens språk" to "Käytä laitteen kieltä"
    )

    private val menu = mapOf(
        "Chatt" to "Chat", "Svara på frågan" to "Answer the question", "Svara på meddelande" to "Reply to message",
        "Lös uppgiften" to "Solve the task", "Verktyg" to "Tools", "Egna kommandon" to "Custom commands",
        "System" to "System", "Nytt kommando" to "New command", "Uppdatera" to "Update",
        "Minimera" to "Minimize", "Stäng" to "Close", "Svara" to "Reply", "Fler funktioner" to "More features",
        "Färre funktioner" to "Fewer features", "Förslag: " to "Suggestions: ", "på" to "on", "av" to "off",
        "Öppna menyn" to "Open menu", "Flytande" to "Floating", "Sidopanel" to "Sidebar",
        "Studier och lärande" to "Study and learning", "Skrivande och dokument" to "Writing and documents",
        "Webb och research" to "Web and research", "E-post och meddelanden" to "Email and messages"
    )
    private val deMenu = mapOf("Chatt" to "Chat","Svara på frågan" to "Frage beantworten","Svara på meddelande" to "Auf Nachricht antworten","Lös uppgiften" to "Aufgabe lösen","Verktyg" to "Werkzeuge","Egna kommandon" to "Eigene Befehle","Nytt kommando" to "Neuer Befehl","Uppdatera" to "Aktualisieren","Minimera" to "Minimieren","Stäng" to "Schließen","Fler funktioner" to "Weitere Funktionen","Färre funktioner" to "Weniger Funktionen","på" to "an","av" to "aus","Studier och lärande" to "Lernen","Skrivande och dokument" to "Schreiben und Dokumente","Webb och research" to "Web und Recherche","E-post och meddelanden" to "E-Mail und Nachrichten")
    private val esMenu = mapOf("Chatt" to "Chat","Svara på frågan" to "Responder a la pregunta","Svara på meddelande" to "Responder al mensaje","Lös uppgiften" to "Resolver la tarea","Verktyg" to "Herramientas","Egna kommandon" to "Comandos personalizados","Nytt kommando" to "Nuevo comando","Uppdatera" to "Actualizar","Minimera" to "Minimizar","Stäng" to "Cerrar","Fler funktioner" to "Más funciones","Färre funktioner" to "Menos funciones","på" to "activado","av" to "desactivado","Studier och lärande" to "Estudio y aprendizaje","Skrivande och dokument" to "Escritura y documentos","Webb och research" to "Web e investigación","E-post och meddelanden" to "Correo y mensajes")
    private val frMenu = mapOf("Chatt" to "Discussion","Svara på frågan" to "Répondre à la question","Svara på meddelande" to "Répondre au message","Lös uppgiften" to "Résoudre la tâche","Verktyg" to "Outils","Egna kommandon" to "Commandes personnalisées","Nytt kommando" to "Nouvelle commande","Uppdatera" to "Mettre à jour","Minimera" to "Réduire","Stäng" to "Fermer","Fler funktioner" to "Plus de fonctions","Färre funktioner" to "Moins de fonctions","på" to "activé","av" to "désactivé","Studier och lärande" to "Études et apprentissage","Skrivande och dokument" to "Rédaction et documents","Webb och research" to "Web et recherche","E-post och meddelanden" to "E-mails et messages")
    private val noMenu = mapOf("Chatt" to "Chat","Svara på frågan" to "Svar på spørsmålet","Svara på meddelande" to "Svar på melding","Lös uppgiften" to "Løs oppgaven","Verktyg" to "Verktøy","Egna kommandon" to "Egne kommandoer","Nytt kommando" to "Ny kommando","Uppdatera" to "Oppdater","Minimera" to "Minimer","Stäng" to "Lukk","Fler funktioner" to "Flere funksjoner","Färre funktioner" to "Færre funksjoner","på" to "på","av" to "av","Studier och lärande" to "Studier og læring","Skrivande och dokument" to "Skriving og dokumenter","Webb och research" to "Nett og research","E-post och meddelanden" to "E-post og meldinger")
    private val daMenu = mapOf("Chatt" to "Chat","Svara på frågan" to "Besvar spørgsmålet","Svara på meddelande" to "Besvar besked","Lös uppgiften" to "Løs opgaven","Verktyg" to "Værktøjer","Egna kommandon" to "Egne kommandoer","Nytt kommando" to "Ny kommando","Uppdatera" to "Opdater","Minimera" to "Minimer","Stäng" to "Luk","Fler funktioner" to "Flere funktioner","Färre funktioner" to "Færre funktioner","på" to "til","av" to "fra","Studier och lärande" to "Studier og læring","Skrivande och dokument" to "Skrivning og dokumenter","Webb och research" to "Web og research","E-post och meddelanden" to "E-mail og beskeder")
    private val fiMenu = mapOf("Chatt" to "Keskustelu","Svara på frågan" to "Vastaa kysymykseen","Svara på meddelande" to "Vastaa viestiin","Lös uppgiften" to "Ratkaise tehtävä","Verktyg" to "Työkalut","Egna kommandon" to "Omat komennot","Nytt kommando" to "Uusi komento","Uppdatera" to "Päivitä","Minimera" to "Pienennä","Stäng" to "Sulje","Fler funktioner" to "Lisää toimintoja","Färre funktioner" to "Vähemmän toimintoja","på" to "käytössä","av" to "pois käytöstä","Studier och lärande" to "Opiskelu ja oppiminen","Skrivande och dokument" to "Kirjoittaminen ja asiakirjat","Webb och research" to "Verkko ja tiedonhaku","E-post och meddelanden" to "Sähköposti ja viestit")

    fun t(language: String, value: String): String {
        val local = when (language) { "de" -> deMenu[value]; "es" -> esMenu[value]; "fr" -> frMenu[value]; "no" -> noMenu[value]; "da" -> daMenu[value]; "fi" -> fiMenu[value]; else -> null }
        if (local != null) return local
        if (language == "sv") return value
        if (language != "en" && menu[value] != null) return menu[value]!!

        return when (language) { "de" -> de[value]; "es" -> es[value]; "fr" -> fr[value]; "no" -> no[value]; "da" -> da[value]; "fi" -> fi[value]; else -> null } ?: en[value] ?: value
    }
}
