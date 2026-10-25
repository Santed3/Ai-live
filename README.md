# ScreenBubble

Flytande AI-bubbla för Android som ser din skärm (OpenAI `gpt-4o`).

## Ladda ner
Senaste APK: https://github.com/Santed3/ai-live/releases/latest/download/ScreenBubble.apk

Installera på en Android-telefon (Android 11 eller nyare) och tillåt installation från okänd källa när Android frågar.

## Använda
- Tryck på bubblan: chatt. Håll in bubblan: snabbsvar på frågor och quiz på skärmen.
- Första gången: klistra in din OpenAI API-nyckel, ge "Visa över andra appar" och välj "Hela skärmen" vid skärmdelning.

## Bygget
Varje push till `main` bygger en ny APK (GitHub Actions) och lägger den under Releases.
Valfritt: lägg en secret `OPENAI_API_KEY` under Settings → Secrets and variables → Actions för att bygga in nyckeln i APK:n.
Obs: en inbyggd nyckel kan plockas ut av den som laddar ner appen, så sätt en månadsgräns hos OpenAI.
