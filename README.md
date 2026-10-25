# ScreenBubble

En liten svart prick över alla Android-appar. Tryck på den så läses skärmens text (utan skärmdelning), och bara rätt svar visas en kort stund.

## Ladda ner
https://github.com/Santed3/Ai-live/releases/latest/download/ScreenBubble.apk

## Komma igång
1. Installera APK-filen (Android 11 eller nyare).
2. Klistra in din OpenAI API-nyckel i appen.
3. Android 13+: appinfo → ⋮ → "Tillåt begränsade inställningar".
4. Inställningar → Tillgänglighet → ScreenBubble → På.
5. Pricken dyker upp. Dra den dit du vill, tryck på den för ett svar.

## Hur det funkar
- Läser texten i det aktiva fönstret via tillgänglighetstjänsten. Skickar både skärmens text och en tyst skärmbild, så bilder och bildalternativ tolkas. Svaret visas som "1: B – Paris".
- Ett tryck = svar bredvid pricken i 5 s (+1,5 s per extra fråga, max 10 s). Dubbeltryck = pricken försvinner och tjänsten stängs av.
- Appar som skyddar sin skärm kan inte läsas.

## Bygget
Varje push till `main` bygger en APK (GitHub Actions) och lägger den under Releases.
Valfritt: secret `OPENAI_API_KEY` bygger in nyckeln i APK:n (kan då plockas ut av den som laddar ner appen).
