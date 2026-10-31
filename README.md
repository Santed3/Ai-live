# ScreenBubble

En liten svart prick över alla Android-appar. Tryck på den så läses skärmens text (utan skärmdelning), och bara rätt svar visas en kort stund.

## Ladda ner
https://github.com/Santed3/Ai-live/releases/latest/download/ScreenBubble.apk

Windows: https://github.com/Santed3/Ai-live/releases/latest/download/ScreenBubble-Setup.exe

På Windows finns **Diktera** bredvid mikrofonen. Den lyssnar i korta segment, finskriver talet och infogar det vid markören i det aktiva programmet. Tryck igen för att pausa.

### Windows utan SmartScreen-varning
Klistra in i PowerShell:

    irm https://raw.githubusercontent.com/Santed3/Ai-live/main/install-windows.ps1 | iex

Filen är inte kodsignerad (kräver betalt certifikat), så en nedladdad .exe kan visa "Windows har skyddat din dator": välj Mer info → Kör ändå.

## Komma igång

Android har en guidad installationsstart med tydliga steg för tillgänglighet och mikrofon. AI ansluts automatiskt via ScreenBubbles tjänst. När du är klar öppnas appens Hem-, Assistent- och Personligt-flikar.

Windows har en separat inställningscentral för utseende, funktioner, appar, genvägar och integritet.
1. Installera APK-filen (Android 11 eller nyare).
2. Android 13+: appinfo → ⋮ → "Tillåt begränsade inställningar".
3. Inställningar → Tillgänglighet → ScreenBubble → På.
4. Tillåt mikrofonen när Android frågar.
5. Slutför installationsguiden. Därefter dyker pricken upp. Dra den dit du vill och tryck på den för ett svar.
6. Välj **Diktera** för att skriva tal direkt i ett aktivt textfält. Om inget textfält är aktivt öppnas anteckningar. Dikteringen finskrivs automatiskt med AI.
7. Anteckningar sparas lokalt. Öppna **Mina anteckningar** för att söka, fortsätta eller ta bort dem.

## Hur det funkar
- Läser texten i det aktiva fönstret via tillgänglighetstjänsten. Skickar både skärmens text och en tyst skärmbild, så bilder och bildalternativ tolkas. Svaret visas som "1: B – Paris".
- Ett tryck = svar bredvid pricken i 5 s (+1,5 s per extra fråga, max 10 s). Dubbeltryck = pricken försvinner och tjänsten stängs av.
- Appar som skyddar sin skärm kan inte läsas.

## Bygget
Varje push till `main` bygger en APK (GitHub Actions) och lägger den under Releases.
Android och Windows använder ScreenBubbles gemensamma AI-backend i Supabase. OpenAI-nyckeln ligger endast på servern och finns inte i apparna. Varje installation identifieras separat och tjänsten begränsar onormalt hög eller automatiserad användning. Användaren behöver inte skapa ett OpenAI-konto eller ange en egen API-nyckel.
